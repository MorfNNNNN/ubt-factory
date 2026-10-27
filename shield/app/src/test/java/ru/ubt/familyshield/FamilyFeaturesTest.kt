package ru.ubt.familyshield

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.family.FamilyActions
import ru.ubt.familyshield.core.family.GuardianActions
import ru.ubt.familyshield.core.family.MessageSender
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.core.family.task
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.core.security.MemorySecretBox
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.TaskRepeat
import ru.ubt.familyshield.shared.model.TaskState
import ru.ubt.familyshield.shared.protocol.FamilyPayload

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FamilyFeaturesTest {
    private lateinit var db: ShieldDatabase
    private lateinit var settings: SettingsStore
    private val sent = mutableListOf<FamilyPayload>()
    private val sender = MessageSender { payload, _, _, _, _ -> sent += payload }

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        db = Room.inMemoryDatabaseBuilder(app, ShieldDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(app, MemorySecretBox(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        runBlocking {
            settings.setCredentials("mom", "token")
            settings.setRole(MemberRole.GUARDIAN)
            db.members().upsert(MemberEntity("kid", "CHILD", "Маша"))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun acceptedTaskGrantsBonusMinutes() = runBlocking {
        val guardian = GuardianActions(settings, db, { sender })
        val task = guardian.createTask("kid", "Сделать уроки", "homework", 20, 15, TaskRepeat.DAILY)
        assertTrue(sent.last() is FamilyPayload.TaskUpdate)

        FamilyActions(settings, db, { sender }).markTaskDone(task.id)
        assertEquals(TaskState.DONE, db.tasks().get(task.id)!!.task().state)
        assertTrue(sent.last() is FamilyPayload.TaskDone)

        guardian.reviewTask(task.id, approved = true)
        assertEquals(TaskState.APPROVED, db.tasks().get(task.id)!!.task().state)
        assertTrue(sent.any { it is FamilyPayload.TaskReview && it.approved })
        val bonus = db.members().get("kid")!!.childPolicy().bonuses.single()
        assertEquals(20, bonus.minutes)
    }

    @Test
    fun lessonKeepsBestScoreAndSharesProgress() = runBlocking {
        val actions = FamilyActions(settings, db, { sender })
        actions.recordLesson("bank_security", 3)
        actions.recordLesson("bank_security", 1)
        actions.setProtectionStep("credit_ban", true)
        val progress = settings.current.learning
        assertEquals(3, progress.lessons["bank_security"])
        assertTrue("credit_ban" in progress.steps)
        assertTrue(sent.last() is FamilyPayload.Learning)
    }
}
