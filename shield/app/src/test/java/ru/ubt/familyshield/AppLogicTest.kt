package ru.ubt.familyshield

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ubt.familyshield.core.db.AlertEntity
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.OutboxEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.security.MemorySecretBox
import ru.ubt.familyshield.shared.model.MemberRole

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AppLogicTest {
    private lateinit var db: ShieldDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShieldDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun memorySecretBoxRoundTrips() {
        val box = MemorySecretBox()
        val sealed = box.seal("секрет".toByteArray())
        assertFalse(sealed.contains("секрет"))
        assertEquals("секрет", String(box.open(sealed)))
    }

    @Test
    fun alertsDeduplicateById() = runBlocking {
        val a = AlertEntity("a1", "dev", "SCAM_MESSAGE", "HIGH", "t", "b", createdAt = 1)
        assertTrue(db.alerts().insert(a) != -1L)
        assertEquals(-1L, db.alerts().insert(a))
        db.alerts().markAllRead()
    }

    @Test
    fun membersUpsertAndClear() = runBlocking {
        db.members().upsert(MemberEntity("d1", MemberRole.CHILD.name, "Маша", isSelf = false))
        db.members().upsert(MemberEntity("d1", MemberRole.CHILD.name, "Мария", isSelf = false))
        assertEquals("Мария", db.members().get("d1")?.displayName)
        assertEquals(1, db.members().all().size)
        db.clearFamilyData()
        assertTrue(db.members().all().isEmpty())
    }

    @Test
    fun outboxBackoffAndDelete() = runBlocking {
        db.outbox().insert(OutboxEntity("o1", "GUARDIANS", "", "{}", false, 0, 0, 0))
        assertEquals(1, db.outbox().due(100).size)
        db.outbox().backoff("o1", 1_000_000)
        assertTrue(db.outbox().due(100).isEmpty())
        db.outbox().delete("o1")
        assertTrue(db.outbox().due(2_000_000).isEmpty())
    }
}
