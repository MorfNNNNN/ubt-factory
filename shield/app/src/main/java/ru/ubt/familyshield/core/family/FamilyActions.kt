package ru.ubt.familyshield.core.family

import ru.ubt.familyshield.core.db.RequestEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.db.TaskEntity
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.model.FamilyTask
import ru.ubt.familyshield.shared.model.MemberRequest
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.shared.model.SafetyProgress
import ru.ubt.familyshield.shared.model.TaskState
import ru.ubt.familyshield.shared.model.TrustLevel
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import java.util.UUID

fun FamilyTask.toEntity() = TaskEntity(id, assigneeDeviceId, state.name, updatedAt, deleted, ShieldJson.encodeToString(FamilyTask.serializer(), this))

fun TaskEntity.task(): FamilyTask = ShieldJson.decodeFromString(FamilyTask.serializer(), json)

/** Things any family member does for themselves: finish a task, learn, tick off protections. */
class FamilyActions(
    private val settings: SettingsStore,
    private val db: ShieldDatabase,
    private val sender: () -> MessageSender,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun markTaskDone(taskId: String) {
        val task = db.tasks().get(taskId)?.task() ?: return
        val now = clock()
        db.tasks().upsert(task.copy(state = TaskState.DONE, doneAt = now, reviewedAt = null, updatedAt = now).toEntity())
        sender().send(FamilyPayload.TaskDone(taskId, now), Audience.GUARDIANS, urgent = true)
    }

    /** Keeps the best quiz score for [lessonId] and shares progress with the family. */
    suspend fun recordLesson(lessonId: String, score: Int) {
        val current = settings.current.learning
        val best = maxOf(current.lessons[lessonId] ?: 0, score.coerceIn(0, 3))
        val updated = current.copy(lessons = current.lessons + (lessonId to best))
        share(updated)
        // A guardian's own lessons count in «Неделя семьи» too; members' lessons arrive as Learning payloads.
        val me = settings.current.deviceId
        if (settings.current.isGuardian && me != null && updated.passedCount > current.passedCount) {
            db.stats().add(java.time.LocalDate.now().toString(), me, ru.ubt.familyshield.shared.content.WeekMetric.LESSON, updated.passedCount - current.passedCount)
        }
    }

    suspend fun setProtectionStep(stepId: String, done: Boolean) {
        val current = settings.current.learning
        share(current.copy(steps = if (done) current.steps + stepId else current.steps - stepId))
    }

    /** «Лестница доверия»: asks guardians for [level]. The request is kept here too, so the screen shows it is pending. */
    suspend fun askTrustLevel(level: TrustLevel) {
        val me = settings.current.deviceId ?: return
        val request = MemberRequest(UUID.randomUUID().toString(), RequestKind.TRUST_LEVEL, clock(), level = level)
        db.requests().upsert(RequestEntity(request.id, me, request.kind.name, ShieldJson.encodeToString(MemberRequest.serializer(), request), request.createdAt))
        sender().send(FamilyPayload.Request(request), Audience.GUARDIANS, urgent = true)
    }

    private suspend fun share(progress: SafetyProgress) {
        val stamped = progress.copy(updatedAt = clock())
        settings.setLearning(stamped)
        sender().send(FamilyPayload.Learning(stamped), Audience.GUARDIANS)
    }
}
