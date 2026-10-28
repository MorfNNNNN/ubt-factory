package ru.ubt.familyshield.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import ru.ubt.familyshield.BuildConfig
import ru.ubt.familyshield.core.security.SecretBox
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.SafetyProgress
import ru.ubt.familyshield.shared.policy.ProtectionChangeRequest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shield_settings")

/** A pending invitation created by this guardian, so the joining device can be named immediately. */
@kotlinx.serialization.Serializable
data class PendingInvite(val inviteId: String, val role: MemberRole, val memberName: String, val ageBand: AgeBand? = null, val createdAt: Long)

data class AppSettings(
    val role: MemberRole? = null,
    val onboardingDone: Boolean = false,
    val displayName: String = "",
    val avatar: String = "",
    val serverUrl: String = BuildConfig.SERVER_URL,
    val deviceId: String? = null,
    val deviceToken: String? = null,
    val familyId: String? = null,
    val familyName: String = "",
    val epoch: Int = 0,
    val pairedAt: Long = 0,
    val syncCursor: Long = 0,
    val entitlementToken: String? = null,
    /** Server's entitlement key, pinned on first contact when the build has none baked in. */
    val entitlementPublicKey: String? = null,
    val childPolicy: ChildPolicy = ChildPolicy(),
    val elderPolicy: ElderPolicy = ElderPolicy(),
    /** deviceId -> X.509 public key (base64url) of guardians this device trusts. */
    val trustedGuardians: Map<String, String> = emptyMap(),
    val pushToken: String? = null,
    val protectionChange: ProtectionChangeRequest? = null,
    val manualCheckDate: String = "",
    val manualCheckCount: Int = 0,
    val threatNumbersVersion: Long = 0,
    val domainVersions: Map<String, Long> = emptyMap(),
    val pendingInvites: List<PendingInvite> = emptyList(),
    val ageBand: AgeBand = AgeBand.AGE_10_12,
    val devPremium: Boolean = false,
    val protectionPaused: Boolean = false,
    val lastCheckInAt: Long = 0,
    val lastStatusAt: Long = 0,
    val lastInventoryHash: Int = 0,
    val consentAcceptedAt: Long = 0,
    val reviewAskedAt: Long = 0,
    val launches: Int = 0,
    /** Own phone number (guardians share it with the family for SOS). */
    val phone: String = "",
    /** Elder phone: the family set up Simple Mode as the home screen. */
    val simpleHome: Boolean = false,
    /**
     * Chosen by the elder personally: switching protection off then waits for the cooling-off period
     * (or a guardian's confirmation), so a scammer on the phone cannot talk them into it.
     */
    val elderSelfProtect: Boolean = false,
    /** This member's «Школа безопасности» progress and protection checklist. */
    val learning: SafetyProgress = SafetyProgress(),
    /** Guardian: «Неделя семьи» notification on Sunday evenings. */
    val weeklyDigest: Boolean = true,
    /** ISO date of the Sunday the last weekly summary was sent for. */
    val lastDigestDate: String = "",
    /** Calendar reminders due before this moment have already been shown. */
    val calendarNotifiedUntil: Long = 0,
) {
    val isPaired: Boolean get() = familyId != null && deviceId != null
    val isGuardian: Boolean get() = role == MemberRole.GUARDIAN
    val isProtected: Boolean get() = role == MemberRole.CHILD || role == MemberRole.ELDER
}

/**
 * All persistent settings. Secrets (device token, family keys, device key) are sealed with the
 * Android Keystore before they touch disk. A hot [state] snapshot serves synchronous readers such
 * as accessibility callbacks.
 */
class SettingsStore(private val context: Context, private val box: SecretBox, scope: CoroutineScope) {
    private object K {
        val role = stringPreferencesKey("role")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val displayName = stringPreferencesKey("display_name")
        val avatar = stringPreferencesKey("avatar")
        val serverUrl = stringPreferencesKey("server_url")
        val deviceId = stringPreferencesKey("device_id")
        val deviceToken = stringPreferencesKey("device_token_sealed")
        val familyId = stringPreferencesKey("family_id")
        val familyName = stringPreferencesKey("family_name")
        val epoch = intPreferencesKey("epoch")
        val familyKeys = stringPreferencesKey("family_keys_sealed")
        val deviceKey = stringPreferencesKey("device_key_sealed")
        val pairedAt = longPreferencesKey("paired_at")
        val syncCursor = longPreferencesKey("sync_cursor")
        val entitlementToken = stringPreferencesKey("entitlement_token")
        val entitlementPublicKey = stringPreferencesKey("entitlement_public_key")
        val childPolicy = stringPreferencesKey("child_policy")
        val elderPolicy = stringPreferencesKey("elder_policy")
        val trustedGuardians = stringPreferencesKey("trusted_guardians")
        val pushToken = stringPreferencesKey("push_token")
        val protectionChange = stringPreferencesKey("protection_change")
        val manualCheckDate = stringPreferencesKey("manual_check_date")
        val manualCheckCount = intPreferencesKey("manual_check_count")
        val threatNumbersVersion = longPreferencesKey("threat_numbers_version")
        val domainVersions = stringPreferencesKey("domain_versions")
        val pendingInvites = stringPreferencesKey("pending_invites")
        val ageBand = stringPreferencesKey("age_band")
        val devPremium = booleanPreferencesKey("dev_premium")
        val protectionPaused = booleanPreferencesKey("protection_paused")
        val lastCheckInAt = longPreferencesKey("last_check_in_at")
        val lastStatusAt = longPreferencesKey("last_status_at")
        val lastInventoryHash = intPreferencesKey("last_inventory_hash")
        val consentAcceptedAt = longPreferencesKey("consent_accepted_at")
        val reviewAskedAt = longPreferencesKey("review_asked_at")
        val launches = intPreferencesKey("launches")
        val phone = stringPreferencesKey("phone")
        val simpleHome = booleanPreferencesKey("simple_home")
        val elderSelfProtect = booleanPreferencesKey("elder_self_protect")
        val learning = stringPreferencesKey("learning_progress")
        val weeklyDigest = booleanPreferencesKey("weekly_digest")
        val lastDigestDate = stringPreferencesKey("last_digest_date")
        val calendarNotifiedUntil = longPreferencesKey("calendar_notified_until")
    }

    private val store = context.dataStore

    private fun <T> decode(serializer: KSerializer<T>, json: String?, default: T): T =
        json?.let { runCatching { ShieldJson.decodeFromString(serializer, it) }.getOrNull() } ?: default

    private val stringMap = MapSerializer(String.serializer(), String.serializer())
    private val longMap = MapSerializer(String.serializer(), Long.serializer())
    private val pendingSerializer = kotlinx.serialization.builtins.ListSerializer(PendingInvite.serializer())

    private fun read(p: Preferences) = AppSettings(
        role = p[K.role]?.let { runCatching { MemberRole.valueOf(it) }.getOrNull() },
        onboardingDone = p[K.onboardingDone] ?: false,
        displayName = p[K.displayName].orEmpty(),
        avatar = p[K.avatar] ?: "",
        serverUrl = p[K.serverUrl]?.takeIf { it.isNotBlank() } ?: BuildConfig.SERVER_URL,
        deviceId = p[K.deviceId],
        deviceToken = p[K.deviceToken]?.let { runCatching { box.open(it).toString(Charsets.UTF_8) }.getOrNull() },
        familyId = p[K.familyId],
        familyName = p[K.familyName].orEmpty(),
        epoch = p[K.epoch] ?: 0,
        pairedAt = p[K.pairedAt] ?: 0,
        syncCursor = p[K.syncCursor] ?: 0,
        entitlementToken = p[K.entitlementToken],
        entitlementPublicKey = p[K.entitlementPublicKey],
        childPolicy = decode(ChildPolicy.serializer(), p[K.childPolicy], ChildPolicy()),
        elderPolicy = decode(ElderPolicy.serializer(), p[K.elderPolicy], ElderPolicy()),
        trustedGuardians = decode(stringMap, p[K.trustedGuardians], emptyMap()),
        pushToken = p[K.pushToken],
        protectionChange = p[K.protectionChange]?.let { runCatching { ShieldJson.decodeFromString(ProtectionChangeRequest.serializer(), it) }.getOrNull() },
        manualCheckDate = p[K.manualCheckDate].orEmpty(),
        manualCheckCount = p[K.manualCheckCount] ?: 0,
        threatNumbersVersion = p[K.threatNumbersVersion] ?: 0,
        domainVersions = decode(longMap, p[K.domainVersions], emptyMap()),
        pendingInvites = decode(pendingSerializer, p[K.pendingInvites], emptyList()),
        ageBand = p[K.ageBand]?.let { runCatching { AgeBand.valueOf(it) }.getOrNull() } ?: AgeBand.AGE_10_12,
        devPremium = BuildConfig.DEBUG && (p[K.devPremium] ?: false),
        protectionPaused = p[K.protectionPaused] ?: false,
        lastCheckInAt = p[K.lastCheckInAt] ?: 0,
        lastStatusAt = p[K.lastStatusAt] ?: 0,
        lastInventoryHash = p[K.lastInventoryHash] ?: 0,
        consentAcceptedAt = p[K.consentAcceptedAt] ?: 0,
        reviewAskedAt = p[K.reviewAskedAt] ?: 0,
        launches = p[K.launches] ?: 0,
        phone = p[K.phone].orEmpty(),
        simpleHome = p[K.simpleHome] ?: false,
        elderSelfProtect = p[K.elderSelfProtect] ?: false,
        learning = decode(SafetyProgress.serializer(), p[K.learning], SafetyProgress()),
        weeklyDigest = p[K.weeklyDigest] ?: true,
        lastDigestDate = p[K.lastDigestDate].orEmpty(),
        calendarNotifiedUntil = p[K.calendarNotifiedUntil] ?: 0,
    )

    private val mutableState = MutableStateFlow(runBlocking { read(store.data.first()) })

    /**
     * Settings as last written. Writes publish their result immediately, so a read right after a
     * write (read-modify-write in [update] callers) never sees the previous value.
     */
    val state: StateFlow<AppSettings> = mutableState.asStateFlow()

    val current: AppSettings get() = mutableState.value

    init {
        scope.launch { store.data.collect { mutableState.value = read(it) } }
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        mutableState.value = read(store.edit { block(it) })
    }

    suspend fun setRole(role: MemberRole) = update { it[K.role] = role.name }
    suspend fun setOnboardingDone(done: Boolean) = update { it[K.onboardingDone] = done }
    suspend fun setProfile(name: String, avatar: String) = update {
        it[K.displayName] = name.trim().take(40)
        it[K.avatar] = avatar
    }
    suspend fun setServerUrl(url: String?) = update { if (url.isNullOrBlank()) it.remove(K.serverUrl) else it[K.serverUrl] = url.trimEnd('/') }
    suspend fun setCredentials(deviceId: String, token: String) = update {
        it[K.deviceId] = deviceId
        it[K.deviceToken] = box.seal(token.toByteArray(Charsets.UTF_8))
    }
    suspend fun setFamily(familyId: String?, familyName: String, pairedAt: Long) = update {
        if (familyId == null) it.remove(K.familyId) else it[K.familyId] = familyId
        it[K.familyName] = familyName
        it[K.pairedAt] = pairedAt
    }
    suspend fun setSyncCursor(cursor: Long) = update { it[K.syncCursor] = cursor }
    suspend fun setEntitlementToken(token: String?) = update { if (token == null) it.remove(K.entitlementToken) else it[K.entitlementToken] = token }
    suspend fun setEntitlementPublicKey(key: String) = update { it[K.entitlementPublicKey] = key }
    suspend fun setChildPolicy(policy: ChildPolicy) = update { it[K.childPolicy] = ShieldJson.encodeToString(ChildPolicy.serializer(), policy) }
    suspend fun setElderPolicy(policy: ElderPolicy) = update { it[K.elderPolicy] = ShieldJson.encodeToString(ElderPolicy.serializer(), policy) }
    suspend fun setTrustedGuardians(map: Map<String, String>) = update { it[K.trustedGuardians] = ShieldJson.encodeToString(stringMap, map) }
    suspend fun setPushToken(token: String?) = update { if (token == null) it.remove(K.pushToken) else it[K.pushToken] = token }
    suspend fun setProtectionChange(request: ProtectionChangeRequest?) = update {
        if (request == null) it.remove(K.protectionChange) else it[K.protectionChange] = ShieldJson.encodeToString(ProtectionChangeRequest.serializer(), request)
    }
    suspend fun setManualChecks(date: String, count: Int) = update {
        it[K.manualCheckDate] = date
        it[K.manualCheckCount] = count
    }
    suspend fun setThreatNumbersVersion(v: Long) = update { it[K.threatNumbersVersion] = v }
    suspend fun setDomainVersion(category: String, v: Long) = update {
        val map = decode(longMap, it[K.domainVersions], emptyMap()).toMutableMap()
        map[category] = v
        it[K.domainVersions] = ShieldJson.encodeToString(longMap, map)
    }
    suspend fun setPendingInvites(list: List<PendingInvite>) = update { it[K.pendingInvites] = ShieldJson.encodeToString(pendingSerializer, list) }
    suspend fun setAgeBand(band: AgeBand) = update { it[K.ageBand] = band.name }
    suspend fun setDevPremium(on: Boolean) = update { it[K.devPremium] = on }
    suspend fun setProtectionPaused(paused: Boolean) = update { it[K.protectionPaused] = paused }
    suspend fun setLastCheckInAt(t: Long) = update { it[K.lastCheckInAt] = t }
    suspend fun setLastStatusAt(t: Long) = update { it[K.lastStatusAt] = t }
    suspend fun setLastInventoryHash(h: Int) = update { it[K.lastInventoryHash] = h }
    suspend fun setConsentAccepted(t: Long) = update { it[K.consentAcceptedAt] = t }
    suspend fun setReviewAskedAt(t: Long) = update { it[K.reviewAskedAt] = t }
    suspend fun incrementLaunches() = update { it[K.launches] = (it[K.launches] ?: 0) + 1 }
    suspend fun setPhone(phone: String) = update { it[K.phone] = phone.trim() }
    suspend fun setSimpleHome(on: Boolean) = update { it[K.simpleHome] = on }
    suspend fun setElderSelfProtect(on: Boolean) = update { it[K.elderSelfProtect] = on }
    suspend fun setLearning(progress: SafetyProgress) = update { it[K.learning] = ShieldJson.encodeToString(SafetyProgress.serializer(), progress) }
    suspend fun setWeeklyDigest(on: Boolean) = update { it[K.weeklyDigest] = on }
    suspend fun setLastDigestDate(date: String) = update { it[K.lastDigestDate] = date }
    suspend fun setCalendarNotifiedUntil(t: Long) = update { it[K.calendarNotifiedUntil] = t }

    // --- Key material (sealed) ---

    suspend fun familyKeys(): Map<Int, ByteArray> {
        val sealed = store.data.first()[K.familyKeys] ?: return emptyMap()
        val map = decode(stringMap, sealed, emptyMap())
        return map.mapNotNull { (epoch, value) -> runCatching { epoch.toInt() to box.open(value) }.getOrNull() }.toMap()
    }

    suspend fun putFamilyKey(epoch: Int, key: ByteArray, makeCurrent: Boolean) = update {
        val map = decode(stringMap, it[K.familyKeys], emptyMap()).toMutableMap()
        map[epoch.toString()] = box.seal(key)
        it[K.familyKeys] = ShieldJson.encodeToString(stringMap, map)
        if (makeCurrent) it[K.epoch] = epoch
    }

    suspend fun deviceKey(): ByteArray? = store.data.first()[K.deviceKey]?.let { runCatching { box.open(it) }.getOrNull() }

    suspend fun setDeviceKey(key: ByteArray) = update { it[K.deviceKey] = box.seal(key) }

    /** Leaves the family: drops keys, cursor, policies and pairing state but keeps the device registration. */
    suspend fun clearFamily() = update {
        listOf(K.familyId, K.familyKeys, K.deviceKey, K.epoch, K.syncCursor, K.trustedGuardians, K.childPolicy, K.elderPolicy,
            K.protectionChange, K.pendingInvites, K.pairedAt, K.familyName).forEach { k -> it.remove(k) }
    }

    /** Factory reset of the app's own data. */
    suspend fun wipe() = update { it.clear() }
}
