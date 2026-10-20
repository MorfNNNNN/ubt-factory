package ru.ubt.familyshield.core.family

import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.NoteEntity
import ru.ubt.familyshield.core.db.RequestEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.policy.DefaultPolicies
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.BonusGrant
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.FamilyNote
import ru.ubt.familyshield.shared.model.MemberRequest
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.QuickReply
import ru.ubt.familyshield.shared.model.RequestDecision
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import java.time.LocalDate
import java.util.UUID

fun MemberEntity.childPolicy(): ChildPolicy =
    policyJson?.let { runCatching { ShieldJson.decodeFromString(ChildPolicy.serializer(), it) }.getOrNull() } ?: DefaultPolicies.forChild(AgeBand.AGE_10_12)

fun MemberEntity.elderPolicy(): ElderPolicy =
    policyJson?.let { runCatching { ShieldJson.decodeFromString(ElderPolicy.serializer(), it) }.getOrNull() } ?: ElderPolicy()

val MemberEntity.memberRole: MemberRole get() = runCatching { MemberRole.valueOf(role) }.getOrDefault(MemberRole.CHILD)

/**
 * Everything a guardian changes on a family member's phone. Policies are versioned by time and
 * signed by the sync engine, and also delivered to co-guardians so everyone sees the same rules.
 */
class GuardianActions(
    private val settings: SettingsStore,
    private val db: ShieldDatabase,
    private val sender: () -> MessageSender,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private suspend fun recipients(target: String): List<String> {
        val me = settings.current.deviceId
        val otherGuardians = db.members().all().filter { it.memberRole == MemberRole.GUARDIAN && it.deviceId != me }.map { it.deviceId }
        return (listOf(target) + otherGuardians).distinct()
    }

    suspend fun updateChild(memberId: String, transform: (ChildPolicy) -> ChildPolicy) {
        val member = db.members().get(memberId) ?: return
        val policy = transform(member.childPolicy()).copy(version = clock())
        db.members().upsert(member.copy(policyJson = ShieldJson.encodeToString(ChildPolicy.serializer(), policy)))
        sender().send(FamilyPayload.ChildPolicyUpdate(memberId, policy), Audience.DEVICES, recipients(memberId), urgent = true)
    }

    suspend fun updateElder(memberId: String, transform: (ElderPolicy) -> ElderPolicy) {
        val member = db.members().get(memberId) ?: return
        val policy = transform(member.elderPolicy()).copy(version = clock())
        db.members().upsert(member.copy(policyJson = ShieldJson.encodeToString(ElderPolicy.serializer(), policy)))
        sender().send(FamilyPayload.ElderPolicyUpdate(memberId, policy), Audience.DEVICES, recipients(memberId), urgent = true)
    }

    suspend fun command(memberId: String, command: Command) =
        sender().send(FamilyPayload.CommandIssued(memberId, command), Audience.DEVICES, listOf(memberId), urgent = true)

    suspend fun pause(memberId: String, untilMillis: Long?) = updateChild(memberId) { it.copy(pausedUntil = untilMillis ?: (clock() + 365L * 86_400_000L)) }

    suspend fun resume(memberId: String) = updateChild(memberId) { it.copy(pausedUntil = null) }

    suspend fun bonus(memberId: String, minutes: Int, reason: String = "") = updateChild(memberId) {
        it.copy(bonuses = it.bonuses.filter { b -> b.date >= LocalDate.now().minusDays(1).toString() } +
            BonusGrant(UUID.randomUUID().toString(), LocalDate.now().toString(), minutes, reason))
    }

    suspend fun decide(request: RequestEntity, approved: Boolean, minutes: Int? = null, note: String = "") {
        val r = ShieldJson.decodeFromString(MemberRequest.serializer(), request.json)
        if (approved) {
            when (r.kind) {
                RequestKind.MORE_TIME -> bonus(request.deviceId, minutes ?: r.minutes ?: 15, "Ответ на просьбу")
                RequestKind.APP_APPROVAL -> r.packageName?.let { pkg -> updateChild(request.deviceId) { it.copy(approvedPackages = it.approvedPackages + pkg) } }
                RequestKind.SITE_UNBLOCK -> r.domain?.let { d -> updateChild(request.deviceId) { it.copy(webFilter = it.webFilter.copy(allowedDomains = it.webFilter.allowedDomains + d)) } }
                RequestKind.PROTECTION_CHANGE -> command(request.deviceId, Command.ApproveProtectionChange(UUID.randomUUID().toString(), r.id))
            }
        } else if (r.kind == RequestKind.APP_APPROVAL && r.packageName != null) {
            updateChild(request.deviceId) {
                it.copy(appRules = it.appRules + (r.packageName!! to ru.ubt.familyshield.shared.model.AppRule(r.packageName!!, ru.ubt.familyshield.shared.model.AppMode.BLOCKED)))
            }
        }
        val decision = RequestDecision(r.id, approved, minutes ?: r.minutes, note, clock())
        db.requests().upsert(request.copy(status = if (approved) RequestEntity.STATUS_APPROVED else RequestEntity.STATUS_DENIED, decidedAt = clock()))
        sender().send(FamilyPayload.Decision(request.deviceId, decision), Audience.DEVICES, recipients(request.deviceId), urgent = true)
    }

    suspend fun note(text: String, quickReply: QuickReply? = null) {
        val note = FamilyNote(UUID.randomUUID().toString(), text.trim().take(1000), clock(), quickReply)
        db.notes().insert(NoteEntity(note.id, settings.current.deviceId.orEmpty(), note.text, quickReply?.name, note.createdAt, mine = true))
        sender().send(FamilyPayload.Note(note), Audience.ALL, urgent = true)
    }

    suspend fun renameMember(memberId: String, name: String, avatar: String) {
        db.members().get(memberId)?.let { db.members().upsert(it.copy(displayName = name.trim().ifBlank { it.displayName }, avatar = avatar)) }
    }
}
