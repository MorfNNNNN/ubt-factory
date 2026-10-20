package ru.ubt.familyshield.core.sync

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ubt.familyshield.core.db.OutboxEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.family.MessageSender
import ru.ubt.familyshield.core.net.ApiClient
import ru.ubt.familyshield.core.net.ApiException
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.core.security.GuardianSigner
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.crypto.EnvelopeCodec
import ru.ubt.familyshield.shared.crypto.Signatures
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import ru.ubt.familyshield.shared.protocol.InnerMessage
import ru.ubt.familyshield.shared.protocol.MessageDto
import ru.ubt.familyshield.shared.protocol.PostMessageRequest
import java.io.IOException
import java.util.UUID

data class SyncResult(val sent: Int, val received: Int, val error: String? = null)

/** How an incoming message was signed. */
enum class SenderAuth {
    /** No or invalid signature. */
    NONE,

    /** Signed by a guardian this device explicitly trusts (the one who paired it or one it endorsed). */
    TRUSTED,

    /** Signed by a key recorded for a guardian member (guardian devices only). */
    MEMBER_GUARDIAN,

    /** Signed with the key the message itself announces (a new co-guardian introducing itself). */
    SELF_ANNOUNCED,
}

/** Receives decrypted, authenticated family messages. */
fun interface IncomingHandler {
    suspend fun handle(senderDeviceId: String, message: InnerMessage, auth: SenderAuth)
}

/**
 * Offline-first, end-to-end encrypted sync: outgoing payloads go to an outbox, are sealed with the
 * current family key just before posting, and incoming envelopes are opened, authenticated and
 * dispatched, then acknowledged so the relay deletes them.
 */
class SyncEngine(
    private val settings: SettingsStore,
    private val api: ApiClient,
    private val db: ShieldDatabase,
    private val signer: () -> GuardianSigner,
    private val handler: () -> IncomingHandler,
    private val scope: CoroutineScope,
    private val onNeedsRetry: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : MessageSender {
    private val mutex = Mutex()
    private val tag = "SyncEngine"

    override suspend fun send(payload: FamilyPayload, audience: Audience, recipients: List<String>, urgent: Boolean, sealWithEpoch: Int?) {
        if (!settings.current.isPaired) return
        db.outbox().insert(
            OutboxEntity(
                id = UUID.randomUUID().toString(),
                audience = audience.name,
                recipients = recipients.joinToString(","),
                payloadJson = ShieldJson.encodeToString(FamilyPayload.serializer(), payload),
                urgent = urgent,
                createdAt = clock(),
                epoch = sealWithEpoch,
            ),
        )
        scope.launch {
            val result = runCatching { flush() }
            if (result.isFailure || result.getOrNull() == false) onNeedsRetry()
        }
    }

    /** Posts due outbox items. Returns false when something must be retried later. */
    suspend fun flush(): Boolean = mutex.withLock { flushLocked() }

    private suspend fun flushLocked(): Boolean {
        val s = settings.current
        val familyId = s.familyId ?: return true
        val me = s.deviceId ?: return true
        val keys = settings.familyKeys()
        var ok = true
        for (item in db.outbox().due(clock())) {
            val epoch = item.epoch ?: s.epoch
            val key = keys[epoch]
            if (key == null) {
                db.outbox().delete(item.id)
                continue
            }
            val payload = runCatching { ShieldJson.decodeFromString(FamilyPayload.serializer(), item.payloadJson) }.getOrNull()
            if (payload == null) {
                db.outbox().delete(item.id)
                continue
            }
            val body = EnvelopeCodec.seal(InnerMessage(item.createdAt, payload), key, epoch, familyId, item.id, me)
            val signature = if (s.role == MemberRole.GUARDIAN) {
                runCatching { EnvelopeCodec.sign(signer().privateKey(), familyId, item.id, me, body) }.getOrNull()
            } else {
                null
            }
            try {
                api.postMessage(
                    PostMessageRequest(
                        id = item.id,
                        audience = Audience.valueOf(item.audience),
                        recipients = item.recipients.split(',').filter { it.isNotBlank() },
                        body = body,
                        signature = signature,
                        urgent = item.urgent,
                    ),
                )
                db.outbox().delete(item.id)
            } catch (e: ApiException) {
                if (e.status in 400..499 && e.status != 429 && e.status != 401) {
                    Log.w(tag, "Dropping outbox item: ${e.code}")
                    db.outbox().delete(item.id)
                } else {
                    ok = false
                    db.outbox().backoff(item.id, clock() + backoffMillis(item.attempts))
                }
            } catch (e: IOException) {
                ok = false
                db.outbox().backoff(item.id, clock() + backoffMillis(item.attempts))
                break
            }
        }
        return ok
    }

    private fun backoffMillis(attempts: Int): Long = (30_000L shl attempts.coerceAtMost(6)).coerceAtMost(30 * 60_000L)

    /** Pulls, decrypts, authenticates and dispatches incoming messages, then acknowledges them. */
    suspend fun pull(): Int = mutex.withLock { pullLocked() }

    private suspend fun pullLocked(): Int {
        val s = settings.current
        val familyId = s.familyId ?: return 0
        var cursor = s.syncCursor
        var received = 0
        while (true) {
            val page = api.pull(cursor)
            if (page.messages.isEmpty()) break
            val keys = settings.familyKeys()
            for (m in page.messages) {
                received++
                runCatching { dispatch(familyId, m, keys) }.onFailure { Log.w(tag, "Message ${m.id} ignored: ${it.message}") }
            }
            cursor = page.nextCursor
            settings.setSyncCursor(cursor)
            api.ack(cursor)
            if (!page.hasMore) break
        }
        return received
    }

    private suspend fun dispatch(familyId: String, m: MessageDto, keys: Map<Int, ByteArray>) {
        // Keys may change while dispatching (rotation), so re-read on a miss.
        val message = runCatching {
            EnvelopeCodec.open(m.body, { keys[it] }, familyId, m.id, m.senderDeviceId)
        }.getOrElse {
            val fresh = settings.familyKeys()
            EnvelopeCodec.open(m.body, { fresh[it] }, familyId, m.id, m.senderDeviceId)
        }
        handler().handle(m.senderDeviceId, message, authenticate(familyId, m, message.payload))
    }

    private suspend fun authenticate(familyId: String, m: MessageDto, payload: FamilyPayload): SenderAuth {
        if (m.signature.isNullOrEmpty()) return SenderAuth.NONE
        fun valid(encoded: String?): Boolean {
            encoded ?: return false
            val key = runCatching { Signatures.publicKeyFromX509(B64.decode(encoded)) }.getOrNull() ?: return false
            return EnvelopeCodec.verify(key, familyId, m.id, m.senderDeviceId, m.body, m.signature)
        }
        if (valid(settings.current.trustedGuardians[m.senderDeviceId])) return SenderAuth.TRUSTED
        if (settings.current.role == MemberRole.GUARDIAN) {
            val member = db.members().get(m.senderDeviceId)
            if (member?.role == MemberRole.GUARDIAN.name && valid(member.guardianPublicKey)) return SenderAuth.MEMBER_GUARDIAN
        }
        val announced = (payload as? FamilyPayload.GuardianKey)?.takeIf { it.deviceId == m.senderDeviceId }?.publicKey
        if (valid(announced)) return SenderAuth.SELF_ANNOUNCED
        return SenderAuth.NONE
    }

    suspend fun syncNow(): SyncResult {
        if (!settings.current.isPaired) return SyncResult(0, 0)
        return try {
            val sentOk = flush()
            val received = pull()
            if (!sentOk) onNeedsRetry()
            SyncResult(0, received)
        } catch (e: IOException) {
            onNeedsRetry()
            SyncResult(0, 0, e.message)
        }
    }
}
