package ru.ubt.familyshield.core.family

import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ubt.familyshield.BuildConfig
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.net.ApiClient
import ru.ubt.familyshield.core.net.ApiException
import ru.ubt.familyshield.core.prefs.PendingInvite
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.core.security.GuardianSigner
import ru.ubt.familyshield.core.security.SecretBox
import ru.ubt.familyshield.shared.crypto.Aead
import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.crypto.Crockford
import ru.ubt.familyshield.shared.crypto.FamilySecrets
import ru.ubt.familyshield.shared.crypto.PairingCode
import ru.ubt.familyshield.shared.crypto.Signatures
import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.MemberProfile
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import java.security.PublicKey

fun interface MessageSender {
    suspend fun send(payload: FamilyPayload, audience: Audience, recipients: List<String>, urgent: Boolean, sealWithEpoch: Int?)
}

suspend fun MessageSender.send(
    payload: FamilyPayload,
    audience: Audience,
    recipients: List<String> = emptyList(),
    urgent: Boolean = false,
) = send(payload, audience, recipients, urgent, null)

/** Result of creating an invitation on the guardian device. */
data class Invitation(val code: PairingCode, val role: MemberRole, val memberName: String, val expiresAt: Long)

/**
 * Family membership and key material: registration, creating a family, inviting and joining
 * devices, member list and key rotation. Network calls go through [api]; outgoing family messages
 * go through [send] (the sync engine's outbox).
 */
class FamilyRepository(
    private val settings: SettingsStore,
    private val api: ApiClient,
    private val db: ShieldDatabase,
    private val box: SecretBox,
    private val signer: () -> GuardianSigner,
    private val sender: () -> MessageSender,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    val deviceId: String? get() = settings.current.deviceId

    /** Registers this installation with the relay once. */
    suspend fun ensureRegistered(): String = mutex.withLock {
        settings.current.deviceId?.takeIf { settings.current.deviceToken != null }?.let { return it }
        val r = api.register(BuildConfig.VERSION_NAME, "${Build.MANUFACTURER} ${Build.MODEL}".take(60))
        settings.setCredentials(r.deviceId, r.deviceToken)
        r.deviceId
    }

    fun myProfile(): MemberProfile {
        val s = settings.current
        return MemberProfile(
            deviceId = s.deviceId.orEmpty(),
            role = s.role ?: MemberRole.GUARDIAN,
            displayName = s.displayName.ifBlank { defaultName(s.role) },
            avatar = s.avatar,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".take(60),
            androidVersion = Build.VERSION.SDK_INT,
            appVersion = BuildConfig.VERSION_NAME,
            phone = s.phone.takeIf { it.isNotBlank() && s.role == MemberRole.GUARDIAN },
        )
    }

    private fun defaultName(role: MemberRole?) = when (role) {
        MemberRole.CHILD -> "Ребёнок"
        MemberRole.ELDER -> "Родитель"
        else -> "Взрослый"
    }

    private suspend fun upsertSelf() {
        val me = myProfile()
        val existing = db.members().get(me.deviceId)
        db.members().upsert(
            (existing ?: MemberEntity(deviceId = me.deviceId, role = me.role.name, joinedAt = clock())).copy(
                role = me.role.name,
                displayName = me.displayName,
                avatar = me.avatar,
                deviceModel = me.deviceModel,
                androidVersion = me.androidVersion,
                appVersion = me.appVersion,
                isSelf = true,
                guardianPublicKey = if (me.role == MemberRole.GUARDIAN) B64.encode(signer().publicKeyX509()) else null,
            ),
        )
    }

    /** Guardian: creates a new family with a fresh family key (epoch 1). */
    suspend fun createFamily(familyName: String) {
        ensureRegistered()
        settings.setRole(MemberRole.GUARDIAN)
        val familyId = try {
            api.createFamily().familyId
        } catch (e: ApiException) {
            if (e.code == "already_in_family") {
                api.leaveFamily()
                api.createFamily().familyId
            } else {
                throw e
            }
        }
        settings.putFamilyKey(1, Aead.newKey(), makeCurrent = true)
        settings.setFamily(familyId, familyName.trim().ifBlank { "Моя семья" }, clock())
        val me = deviceId!!
        settings.setTrustedGuardians(mapOf(me to B64.encode(signer().publicKeyX509())))
        upsertSelf()
    }

    /** Guardian: creates a single-use invitation for a new device. */
    suspend fun createInvite(role: MemberRole, memberName: String, ageBand: AgeBand? = null, ttlSeconds: Int = 1800): Invitation {
        val s = settings.current
        val familyId = s.familyId ?: error("Сначала создайте семью")
        val key = settings.familyKeys()[s.epoch] ?: error("Нет ключа семьи")
        val me = s.deviceId ?: error("Устройство не зарегистрировано")
        var lastError: Exception? = null
        repeat(3) {
            val code = PairingCode(Crockford.random(PairingCode.INVITE_LENGTH), PairingCode.newSecret(), s.serverUrl.takeIf { it != BuildConfig.SERVER_URL })
            val secrets = FamilySecrets(
                familyId = familyId,
                epoch = s.epoch,
                familyKey = B64.encode(key),
                guardianDeviceId = me,
                guardianPublicKey = B64.encode(signer().publicKeyX509()),
                role = role,
                memberName = memberName.trim(),
                familyName = s.familyName,
            )
            try {
                val response = api.createInvite(code.inviteId, role, code.wrap(secrets), ttlSeconds)
                settings.setPendingInvites(
                    (s.pendingInvites.filter { it.createdAt > clock() - 86_400_000L } + PendingInvite(code.inviteId, role, memberName.trim(), ageBand, clock())),
                )
                return Invitation(code, role, memberName, response.expiresAt)
            } catch (e: ApiException) {
                if (e.code != "invite_id_taken") throw e
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("invite failed")
    }

    /** Protected device or co-guardian: joins a family using the scanned or typed code. */
    suspend fun joinFamily(code: PairingCode, myName: String) {
        code.serverUrl?.let { settings.setServerUrl(it) }
        ensureRegistered()
        val accepted = api.acceptInvite(code.inviteId)
        val secrets = code.unwrap(accepted.wrappedSecrets)
        require(secrets.familyId == accepted.familyId) { "Код не соответствует семье" }
        settings.setRole(secrets.role)
        settings.setProfile(myName.ifBlank { secrets.memberName.ifBlank { defaultName(secrets.role) } }, settings.current.avatar)
        settings.putFamilyKey(secrets.epoch, B64.decode(secrets.familyKey), makeCurrent = true)
        settings.setFamily(secrets.familyId, secrets.familyName, clock())
        settings.setTrustedGuardians(mapOf(secrets.guardianDeviceId to secrets.guardianPublicKey))
        val deviceKey = Aead.newKey()
        settings.setDeviceKey(deviceKey)
        upsertSelf()
        db.members().upsert(
            MemberEntity(
                deviceId = secrets.guardianDeviceId,
                role = MemberRole.GUARDIAN.name,
                displayName = "Взрослый",
                joinedAt = clock(),
                guardianPublicKey = secrets.guardianPublicKey,
            ),
        )
        refreshMembers()
        val out = sender()
        out.send(FamilyPayload.Profile(myProfile()), Audience.ALL, urgent = true)
        out.send(FamilyPayload.DeviceKey(deviceId!!, B64.encode(deviceKey)), Audience.GUARDIANS)
        if (secrets.role == MemberRole.GUARDIAN) {
            // A co-guardian announces its key; trusted guardians endorse it for the protected devices.
            out.send(FamilyPayload.GuardianKey(deviceId!!, B64.encode(signer().publicKeyX509())), Audience.GUARDIANS, urgent = true)
        }
    }

    /** Syncs the member list with the server (authoritative for membership and roles). */
    suspend fun refreshMembers() {
        if (!settings.current.isPaired) return
        val response = api.members()
        val known = db.members().all().associateBy { it.deviceId }
        val ids = response.members.map { it.deviceId }.toSet()
        val pending = settings.current.pendingInvites
        for (m in response.members) {
            val existing = known[m.deviceId]
            val fallbackName = pending.firstOrNull { it.role == m.role && existing == null }?.memberName
            db.members().upsert(
                (existing ?: MemberEntity(deviceId = m.deviceId, role = m.role.name, displayName = fallbackName ?: defaultName(m.role))).copy(
                    role = m.role.name,
                    joinedAt = m.joinedAt,
                    lastSeenAt = m.lastSeenAt,
                    isSelf = m.deviceId == deviceId,
                ),
            )
        }
        known.keys.filter { it !in ids }.forEach { db.members().delete(it) }
    }

    /** Guardian: removes a member and rotates the family key so the removed device cannot read new messages. */
    suspend fun removeMember(memberId: String) {
        api.removeMember(memberId)
        db.members().delete(memberId)
        rotateFamilyKey()
    }

    suspend fun rotateFamilyKey() {
        val s = settings.current
        if (s.role != MemberRole.GUARDIAN) return
        val newEpoch = s.epoch + 1
        val newKey = Aead.newKey()
        val members = db.members().all().filter { !it.isSelf }
        val wrapped = members.mapNotNull { m ->
            val deviceKey = m.deviceKeySealed?.let { runCatching { box.open(it) }.getOrNull() } ?: return@mapNotNull null
            m.deviceId to B64.encode(Aead.seal(deviceKey, newKey, "fs-rotate|$newEpoch|${m.deviceId}".toByteArray()))
        }.toMap()
        // Sealed with the current key so recipients can open the envelope; afterwards we switch.
        sender().send(FamilyPayload.KeyRotation(newEpoch, wrapped), Audience.DEVICES, wrapped.keys.toList(), urgent = true, sealWithEpoch = s.epoch)
        settings.putFamilyKey(newEpoch, newKey, makeCurrent = true)
    }

    /** Protected/co-guardian side: unwraps a rotated key addressed to this device. */
    suspend fun acceptRotation(rotation: FamilyPayload.KeyRotation): Boolean {
        val me = deviceId ?: return false
        val wrapped = rotation.wrappedKeys[me] ?: return false
        val deviceKey = settings.deviceKey() ?: return false
        val key = runCatching { Aead.open(deviceKey, B64.decode(wrapped), "fs-rotate|${rotation.epoch}|$me".toByteArray()) }.getOrNull() ?: return false
        settings.putFamilyKey(rotation.epoch, key, makeCurrent = rotation.epoch > settings.current.epoch)
        return true
    }

    fun trustedGuardianKey(deviceId: String): PublicKey? =
        settings.current.trustedGuardians[deviceId]?.let { runCatching { Signatures.publicKeyFromX509(B64.decode(it)) }.getOrNull() }

    suspend fun trustGuardian(deviceId: String, publicKey: String) {
        settings.setTrustedGuardians(settings.current.trustedGuardians + (deviceId to publicKey))
        db.members().get(deviceId)?.let { db.members().upsert(it.copy(guardianPublicKey = publicKey)) }
    }

    /** Leaves the family on this device (server + local data). */
    suspend fun leaveFamily() {
        runCatching { api.leaveFamily() }
        settings.clearFamily()
        db.clearFamilyData()
    }

    /** Deletes this device and all its data on the server and locally. */
    suspend fun deleteEverything() {
        runCatching { api.deleteDevice() }
        db.clearFamilyData()
        db.events().clear()
        settings.wipe()
    }
}
