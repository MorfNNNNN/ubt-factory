package ru.ubt.familyshield.core.sync

import android.util.Log
import ru.ubt.familyshield.core.db.AlertEntity
import ru.ubt.familyshield.core.db.ChangeEntity
import ru.ubt.familyshield.core.db.LocationEntity
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.NoteEntity
import ru.ubt.familyshield.core.db.RequestEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.db.UsageEntity
import ru.ubt.familyshield.core.family.FamilyCalendar
import ru.ubt.familyshield.core.family.FamilyRepository
import ru.ubt.familyshield.core.family.FamilyWeek
import ru.ubt.familyshield.core.family.MessageSender
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.core.family.elderPolicy
import ru.ubt.familyshield.core.family.send
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.core.security.SecretBox
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.model.Alert
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.FamilyNote
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.RequestDecision
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.shared.policy.PolicyDiff
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import ru.ubt.familyshield.shared.model.TaskState
import ru.ubt.familyshield.core.family.task
import ru.ubt.familyshield.core.family.toEntity
import ru.ubt.familyshield.shared.protocol.InnerMessage

/** What a protected device does when its guardians change something. Implemented by the protection layer. */
interface DeviceController {
    suspend fun applyChildPolicy(policy: ChildPolicy)
    suspend fun applyElderPolicy(policy: ElderPolicy)
    suspend fun execute(command: Command)
    suspend fun onDecision(decision: RequestDecision)
    suspend fun onNote(from: String, note: FamilyNote)
}

/**
 * Routes incoming payloads. Guardians collect telemetry and alerts; protected devices apply
 * policies and commands — but only when signed by a guardian they trust.
 */
class MessageHandler(
    private val settings: SettingsStore,
    private val db: ShieldDatabase,
    private val family: FamilyRepository,
    private val notifier: Notifier,
    private val box: SecretBox,
    private val sender: () -> MessageSender,
    private val controller: () -> DeviceController,
    private val week: () -> FamilyWeek? = { null },
    private val calendar: () -> FamilyCalendar? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) : IncomingHandler {
    private val tag = "MessageHandler"

    private val me: String? get() = settings.current.deviceId
    private val isGuardian: Boolean get() = settings.current.role == MemberRole.GUARDIAN

    override suspend fun handle(senderDeviceId: String, message: InnerMessage, auth: SenderAuth) {
        val payload = message.payload
        if (payload.requiresGuardianSignature && !authorized(senderDeviceId, payload, auth)) {
            Log.w(tag, "Rejected unsigned ${payload::class.simpleName} from $senderDeviceId")
            return
        }
        when (payload) {
            is FamilyPayload.Profile -> onProfile(senderDeviceId, payload)
            is FamilyPayload.Status -> updateMember(senderDeviceId) {
                it.copy(statusJson = ShieldJson.encodeToString(ru.ubt.familyshield.shared.model.DeviceStatus.serializer(), payload.status),
                    statusAt = payload.status.reportedAt, lastSeenAt = maxOf(it.lastSeenAt ?: 0, payload.status.reportedAt),
                    todayMinutes = payload.status.screenTimeTodayMinutes ?: it.todayMinutes)
            }
            is FamilyPayload.Location -> {
                val f = payload.fix
                db.locations().insert(LocationEntity(deviceId = senderDeviceId, lat = f.lat, lon = f.lon, accuracy = f.accuracyMeters, time = f.time, address = f.address))
                updateMember(senderDeviceId) {
                    it.copy(lastLat = f.lat, lastLon = f.lon, lastAccuracy = f.accuracyMeters, lastLocationAt = f.time, lastAddress = f.address ?: it.lastAddress)
                }
            }
            is FamilyPayload.AlertRaised -> onAlert(senderDeviceId, payload.alert)
            is FamilyPayload.Usage -> {
                val r = payload.report
                db.usage().upsert(UsageEntity(senderDeviceId, r.date, r.totalMinutes, ShieldJson.encodeToString(ru.ubt.familyshield.shared.model.UsageReport.serializer(), r), clock()))
                updateMember(senderDeviceId) { it.copy(todayMinutes = r.totalMinutes) }
                if (isGuardian) week()?.screenTime(senderDeviceId, r.date, r.totalMinutes)
            }
            is FamilyPayload.Apps -> updateMember(senderDeviceId) {
                it.copy(inventoryJson = ShieldJson.encodeToString(ru.ubt.familyshield.shared.model.AppInventory.serializer(), payload.inventory))
            }
            is FamilyPayload.Security -> updateMember(senderDeviceId) {
                it.copy(securityJson = ShieldJson.encodeToString(ru.ubt.familyshield.shared.model.SecurityReport.serializer(), payload.report))
            }
            is FamilyPayload.Request -> onRequest(senderDeviceId, payload)
            is FamilyPayload.Note -> onNote(senderDeviceId, payload.note)
            is FamilyPayload.CheckInDone -> {
                updateMember(senderDeviceId) { it.copy(lastCheckInAt = payload.checkIn.createdAt) }
                // Daily check-ins stay quiet; an answer to «Отметься» is what the guardian is waiting for.
                if (isGuardian && payload.checkIn.mood == ru.ubt.familyshield.shared.model.CheckIn.MOOD_ANSWERED) {
                    notifier.memberAlert(
                        payload.checkIn.id, memberName(senderDeviceId), "«Я в порядке»",
                        "Ответ на вашу просьбу отметиться. Где сейчас — в карточке близкого.", AlertSeverity.INFO,
                    )
                }
                onAlert(
                    senderDeviceId,
                    Alert(payload.checkIn.id, AlertType.CHECK_IN_OK, title = "Отметка «Я в порядке»", body = "Близкий отметился, что у него всё хорошо",
                        createdAt = payload.checkIn.createdAt, location = payload.checkIn.location),
                    notify = false,
                )
            }
            is FamilyPayload.DeviceKey -> if (isGuardian && payload.deviceId == senderDeviceId) {
                updateMember(senderDeviceId) { it.copy(deviceKeySealed = box.seal(B64.decode(payload.key))) }
            }
            is FamilyPayload.GuardianKey -> onGuardianKey(senderDeviceId, payload, auth)
            is FamilyPayload.KeyRotation -> family.acceptRotation(payload)
            is FamilyPayload.EntitlementShare -> settings.setEntitlementToken(payload.token)
            is FamilyPayload.TaskUpdate -> {
                val task = payload.task
                if (isGuardian || task.assigneeDeviceId == me) db.tasks().upsert(task.toEntity())
            }
            is FamilyPayload.TaskDone -> onTaskDone(senderDeviceId, payload)
            is FamilyPayload.TaskReview -> db.tasks().get(payload.taskId)?.task()?.let { task ->
                val reviewed = payload.task?.takeIf { it.id == task.id && it.assigneeDeviceId == task.assigneeDeviceId }
                    ?: task.copy(state = if (payload.approved) TaskState.APPROVED else TaskState.DECLINED, reviewedAt = payload.reviewedAt, updatedAt = payload.reviewedAt)
                db.tasks().upsert(reviewed.toEntity())
                if (task.assigneeDeviceId == me) {
                    notifier.info(
                        ("task-review-" + task.id).hashCode(),
                        if (payload.approved) "Задание принято" else "Задание вернули",
                        if (payload.approved && task.rewardMinutes > 0) "«${task.title}» — +${task.rewardMinutes} мин" else "«${task.title}»",
                    )
                }
            }
            is FamilyPayload.Learning -> {
                val before = db.members().get(senderDeviceId)?.learningJson
                    ?.let { runCatching { ShieldJson.decodeFromString(ru.ubt.familyshield.shared.model.SafetyProgress.serializer(), it) }.getOrNull() }
                updateMember(senderDeviceId) {
                    it.copy(learningJson = ShieldJson.encodeToString(ru.ubt.familyshield.shared.model.SafetyProgress.serializer(), payload.progress))
                }
                if (isGuardian) week()?.lessonsPassed(senderDeviceId, payload.progress.passedCount - (before?.passedCount ?: 0))
            }
            is FamilyPayload.CalendarUpdate -> calendar()?.receive(payload.event)
            is FamilyPayload.ChildPolicyUpdate -> if (payload.targetDeviceId == me) {
                val before = settings.current.childPolicy
                if (payload.policy.version >= before.version) {
                    settings.setChildPolicy(payload.policy)
                    controller().applyChildPolicy(payload.policy)
                    logChanges(payload.targetDeviceId, senderDeviceId, PolicyDiff.child(before, payload.policy))
                }
            } else if (isGuardian) {
                val before = db.members().get(payload.targetDeviceId)?.childPolicy()
                updateMember(payload.targetDeviceId) { it.copy(policyJson = ShieldJson.encodeToString(ChildPolicy.serializer(), payload.policy)) }
                if (before != null) logChanges(payload.targetDeviceId, senderDeviceId, PolicyDiff.child(before, payload.policy))
            }
            is FamilyPayload.ElderPolicyUpdate -> if (payload.targetDeviceId == me) {
                val before = settings.current.elderPolicy
                if (payload.policy.version >= before.version) {
                    settings.setElderPolicy(payload.policy)
                    controller().applyElderPolicy(payload.policy)
                    logChanges(payload.targetDeviceId, senderDeviceId, PolicyDiff.elder(before, payload.policy))
                }
            } else if (isGuardian) {
                val before = db.members().get(payload.targetDeviceId)?.elderPolicy()
                updateMember(payload.targetDeviceId) { it.copy(policyJson = ShieldJson.encodeToString(ElderPolicy.serializer(), payload.policy)) }
                if (before != null) logChanges(payload.targetDeviceId, senderDeviceId, PolicyDiff.elder(before, payload.policy))
            }
            is FamilyPayload.CommandIssued -> if (payload.targetDeviceId == me) controller().execute(payload.command)
            is FamilyPayload.Decision -> if (payload.targetDeviceId == me || isGuardian) {
                // Both sides keep the outcome: guardians for the list, the asking phone to stop showing «ждём ответа».
                db.requests().get(payload.decision.requestId)?.let {
                    db.requests().upsert(it.copy(status = if (payload.decision.approved) RequestEntity.STATUS_APPROVED else RequestEntity.STATUS_DENIED, decidedAt = payload.decision.decidedAt))
                }
                if (payload.targetDeviceId == me) controller().onDecision(payload.decision)
            }
        }
    }

    private fun authorized(sender: String, payload: FamilyPayload, auth: SenderAuth): Boolean = when {
        auth == SenderAuth.TRUSTED -> true
        isGuardian && auth == SenderAuth.MEMBER_GUARDIAN -> true
        // Self-announced keys are judged in onGuardianKey against the server's member list.
        payload is FamilyPayload.GuardianKey && auth == SenderAuth.SELF_ANNOUNCED && isGuardian -> true
        else -> false
    }

    private suspend fun logChanges(memberId: String, actor: String, lines: List<String>) {
        if (lines.isEmpty()) return
        val now = clock()
        db.changes().insertAll(lines.map { ChangeEntity(memberDeviceId = memberId, actorDeviceId = actor, text = it, createdAt = now) })
    }

    private suspend fun updateMember(deviceId: String, block: (MemberEntity) -> MemberEntity) {
        val existing = db.members().get(deviceId) ?: MemberEntity(deviceId = deviceId, role = MemberRole.CHILD.name, joinedAt = clock())
        db.members().upsert(block(existing))
    }

    private suspend fun memberName(deviceId: String): String = db.members().get(deviceId)?.displayName?.ifBlank { null } ?: "Близкий"

    private suspend fun onProfile(sender: String, payload: FamilyPayload.Profile) {
        val p = payload.profile
        if (p.deviceId != sender) return
        val pendingAge = settings.current.pendingInvites.firstOrNull { it.role == p.role && it.memberName.equals(p.displayName, true) }?.ageBand
        updateMember(sender) {
            it.copy(
                role = it.role.takeIf { r -> r == MemberRole.GUARDIAN.name } ?: p.role.name,
                displayName = p.displayName,
                avatar = p.avatar,
                birthYear = p.birthYear ?: it.birthYear,
                deviceModel = p.deviceModel,
                androidVersion = p.androidVersion,
                appVersion = p.appVersion,
                phone = p.phone ?: it.phone,
            )
        }
        // A newly joined child gets the age-appropriate default policy right away.
        if (isGuardian && p.role == MemberRole.CHILD && db.members().get(sender)?.policyJson == null) {
            val policy = ru.ubt.familyshield.core.policy.DefaultPolicies.forChild(pendingAge ?: ru.ubt.familyshield.shared.model.AgeBand.AGE_10_12)
                .copy(version = clock())
            updateMember(sender) { it.copy(policyJson = ShieldJson.encodeToString(ChildPolicy.serializer(), policy)) }
            sender().send(FamilyPayload.ChildPolicyUpdate(sender, policy), Audience.DEVICES, listOf(sender), urgent = true)
        }
        if (isGuardian && p.role == MemberRole.ELDER && db.members().get(sender)?.policyJson == null) {
            val policy = ElderPolicy(version = clock())
            updateMember(sender) { it.copy(policyJson = ShieldJson.encodeToString(ElderPolicy.serializer(), policy)) }
            sender().send(FamilyPayload.ElderPolicyUpdate(sender, policy), Audience.DEVICES, listOf(sender), urgent = true)
        }
        if (isGuardian) {
            // Let the newcomer know who we are (name and avatar shown on its screens).
            sender().send(FamilyPayload.Profile(family.myProfile()), Audience.DEVICES, listOf(sender))
        }
    }

    private suspend fun onAlert(sender: String, alert: Alert, notify: Boolean = true) {
        val inserted = db.alerts().insert(
            AlertEntity(
                id = alert.id, deviceId = sender, type = alert.type.name, severity = alert.severity.name, title = alert.title, body = alert.body,
                excerpt = alert.excerpt, sourceApp = alert.sourceApp, sourceAppLabel = alert.sourceAppLabel, categories = alert.categories.joinToString(","),
                createdAt = alert.createdAt, lat = alert.location?.lat, lon = alert.location?.lon, counterparty = alert.counterparty,
            ),
        )
        if (inserted != -1L && isGuardian) week()?.countAlert(sender, alert.type, alert.severity, alert.createdAt)
        if (inserted != -1L && notify && isGuardian && alert.severity >= AlertSeverity.WARNING) {
            notifier.memberAlert(alert.id, memberName(sender), alert.title, alert.body, alert.severity)
        }
    }

    /** Only the assignee can finish its task; guardians get an event to confirm it. */
    private suspend fun onTaskDone(sender: String, payload: FamilyPayload.TaskDone) {
        val task = db.tasks().get(payload.taskId)?.task() ?: return
        if (task.assigneeDeviceId != sender) return
        db.tasks().upsert(task.copy(state = TaskState.DONE, doneAt = payload.doneAt, reviewedAt = null, updatedAt = payload.doneAt).toEntity())
        if (isGuardian) {
            val title = "${memberName(sender)}: задание выполнено"
            val body = "«${task.title}». Подтвердите, чтобы начислить ${task.rewardMinutes} мин."
            onAlert(sender, Alert(id = "task-${task.id}-${payload.doneAt}", type = AlertType.TASK_DONE, title = title, body = body, createdAt = payload.doneAt))
            notifier.info(("task-done-" + task.id).hashCode(), title, body)
        }
    }

    private suspend fun onRequest(sender: String, payload: FamilyPayload.Request) {
        val r = payload.request
        db.requests().upsert(
            RequestEntity(r.id, sender, r.kind.name, ShieldJson.encodeToString(ru.ubt.familyshield.shared.model.MemberRequest.serializer(), r), r.createdAt),
        )
        if (!isGuardian) return
        val name = memberName(sender)
        val text = when (r.kind) {
            RequestKind.MORE_TIME -> "$name просит ещё ${r.minutes ?: 15} мин" + r.reason.takeIf { it.isNotBlank() }?.let { ": «$it»" }.orEmpty()
            RequestKind.APP_APPROVAL -> "$name хочет пользоваться «${r.appLabel ?: r.packageName}»"
            RequestKind.SITE_UNBLOCK -> "$name просит открыть сайт ${r.domain}"
            RequestKind.PROTECTION_CHANGE -> "$name хочет отключить защиту. Без вашего одобрения это произойдёт только через сутки"
            RequestKind.REMOTE_ACCESS -> "$name хочет открыть «${r.appLabel ?: r.packageName}». Разрешайте, только если помогаете сами: мошенники просят установить такие программы"
            RequestKind.TRUST_LEVEL -> "$name просит уровень доверия «${r.level?.title ?: "выше"}»" + r.level?.perks?.let { p -> ": ${p.joinToString(", ").lowercase()}" }.orEmpty()
        }
        notifier.request(r.id, "Запрос: $name", text)
    }

    private suspend fun onNote(sender: String, note: FamilyNote) {
        db.notes().insert(NoteEntity(note.id, sender, note.text, note.quickReply?.name, note.createdAt, mine = false))
        val name = memberName(sender)
        if (isGuardian) notifier.note(note.id, name, note.text) else controller().onNote(name, note)
    }

    private suspend fun onGuardianKey(sender: String, payload: FamilyPayload.GuardianKey, auth: SenderAuth) {
        val alreadyTrusted = settings.current.trustedGuardians[payload.deviceId] == payload.publicKey
        if (alreadyTrusted) return
        if (!isGuardian) {
            // Protected devices only accept keys endorsed by a guardian they already trust.
            if (auth == SenderAuth.TRUSTED) family.trustGuardian(payload.deviceId, payload.publicKey)
            return
        }
        when (auth) {
            SenderAuth.TRUSTED, SenderAuth.MEMBER_GUARDIAN -> family.trustGuardian(payload.deviceId, payload.publicKey)
            SenderAuth.SELF_ANNOUNCED -> {
                // A new co-guardian: trust it only if the server lists it as a guardian, then endorse it.
                runCatching { family.refreshMembers() }
                val member = db.members().get(payload.deviceId)
                if (member?.role == MemberRole.GUARDIAN.name && payload.deviceId == sender) {
                    family.trustGuardian(payload.deviceId, payload.publicKey)
                    sender().send(payload, Audience.ALL, urgent = true)
                }
            }
            SenderAuth.NONE -> Unit
        }
    }
}
