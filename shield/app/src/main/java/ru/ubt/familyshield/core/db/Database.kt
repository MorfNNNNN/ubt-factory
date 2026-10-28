package ru.ubt.familyshield.core.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A family member as seen from this device (guardians see everyone; protected devices see guardians). */
@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val deviceId: String,
    val role: String,
    val displayName: String = "",
    val avatar: String = "",
    val birthYear: Int? = null,
    val deviceModel: String = "",
    val androidVersion: Int = 0,
    val appVersion: String = "",
    val joinedAt: Long = 0,
    val lastSeenAt: Long? = null,
    val isSelf: Boolean = false,
    val statusJson: String? = null,
    val statusAt: Long? = null,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val lastAccuracy: Float? = null,
    val lastLocationAt: Long? = null,
    val lastAddress: String? = null,
    /** Guardian-side copy of the policy last sent to this member (ChildPolicy or ElderPolicy JSON). */
    val policyJson: String? = null,
    val inventoryJson: String? = null,
    val securityJson: String? = null,
    /** Per-device key (sealed) used to deliver rotated family keys to this member only. */
    val deviceKeySealed: String? = null,
    val guardianPublicKey: String? = null,
    val lastCheckInAt: Long? = null,
    val todayMinutes: Int? = null,
    /** Phone shared in the member's profile (used for SOS and "call family"). */
    val phone: String? = null,
    /** JSON SafetyProgress the member shared («Школа безопасности», checklist). */
    val learningJson: String? = null,
)

@Entity(tableName = "alerts", indices = [Index("deviceId"), Index("createdAt")])
data class AlertEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val type: String,
    val severity: String,
    val title: String,
    val body: String,
    val excerpt: String? = null,
    val sourceApp: String? = null,
    val sourceAppLabel: String? = null,
    val categories: String = "",
    val createdAt: Long,
    val read: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
    val counterparty: String? = null,
)

@Entity(tableName = "locations", indices = [Index(value = ["deviceId", "time"])])
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val time: Long,
    val address: String? = null,
)

@Entity(tableName = "usage", primaryKeys = ["deviceId", "date"])
data class UsageEntity(
    val deviceId: String,
    val date: String,
    val totalMinutes: Int,
    val reportJson: String,
    val receivedAt: Long,
)

@Entity(tableName = "requests", indices = [Index("deviceId")])
data class RequestEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val kind: String,
    val json: String,
    val createdAt: Long,
    val status: String = STATUS_PENDING,
    val decidedAt: Long? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_DENIED = "DENIED"
    }
}

@Entity(tableName = "notes", indices = [Index("createdAt")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val senderDeviceId: String,
    val text: String,
    val quickReply: String? = null,
    val createdAt: Long,
    val mine: Boolean,
)

/** Messages waiting to be encrypted and posted (offline-first). */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    val audience: String,
    val recipients: String,
    val payloadJson: String,
    val urgent: Boolean,
    val createdAt: Long,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    /** Seal with this key epoch instead of the current one (key rotation announcements). */
    val epoch: Int? = null,
)

/** Local protection journal shown on the protected device itself ("Журнал защиты"). */
@Entity(tableName = "events", indices = [Index("createdAt")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val detail: String = "",
    val packageName: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "threat_numbers")
data class ThreatNumberEntity(
    @PrimaryKey val hash: String,
    val category: String,
    val reports: Int,
)

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY isSelf DESC, joinedAt ASC")
    fun observeAll(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE deviceId = :id")
    fun observe(id: String): Flow<MemberEntity?>

    @Query("SELECT * FROM members WHERE deviceId = :id")
    suspend fun get(id: String): MemberEntity?

    @Query("SELECT * FROM members")
    suspend fun all(): List<MemberEntity>

    @Upsert
    suspend fun upsert(member: MemberEntity)

    @Query("DELETE FROM members WHERE deviceId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM members")
    suspend fun clear()
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 300): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE deviceId = :deviceId ORDER BY createdAt DESC LIMIT :limit")
    fun observeFor(deviceId: String, limit: Int = 200): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE read = 0")
    fun observeUnread(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alert: AlertEntity): Long

    @Query("UPDATE alerts SET read = 1 WHERE read = 0")
    suspend fun markAllRead()

    @Query("UPDATE alerts SET read = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("DELETE FROM alerts WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM alerts")
    suspend fun clear()
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations WHERE deviceId = :deviceId AND time >= :since ORDER BY time DESC")
    fun observeSince(deviceId: String, since: Long): Flow<List<LocationEntity>>

    @Insert
    suspend fun insert(location: LocationEntity)

    @Query("DELETE FROM locations WHERE time < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM locations")
    suspend fun clear()
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage WHERE deviceId = :deviceId ORDER BY date DESC LIMIT :days")
    fun observeFor(deviceId: String, days: Int = 30): Flow<List<UsageEntity>>

    @Upsert
    suspend fun upsert(usage: UsageEntity)

    @Query("DELETE FROM usage WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("DELETE FROM usage")
    suspend fun clear()
}

@Dao
interface RequestDao {
    @Query("SELECT * FROM requests ORDER BY createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun observePending(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE id = :id")
    suspend fun get(id: String): RequestEntity?

    @Upsert
    suspend fun upsert(request: RequestEntity)

    @Query("DELETE FROM requests")
    suspend fun clear()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(note: NoteEntity)

    @Query("DELETE FROM notes")
    suspend fun clear()
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE nextAttemptAt <= :now ORDER BY createdAt ASC LIMIT 50")
    suspend fun due(now: Long): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OutboxEntity)

    @Query("UPDATE outbox SET attempts = attempts + 1, nextAttemptAt = :next WHERE id = :id")
    suspend fun backoff(id: String, next: Long)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM events WHERE type = :type AND createdAt >= :since")
    suspend fun countSince(type: String, since: Long): Int

    @Insert
    suspend fun insert(event: EventEntity)

    @Query("DELETE FROM events WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM events")
    suspend fun clear()
}

@Dao
interface ThreatNumberDao {
    @Query("SELECT * FROM threat_numbers WHERE hash = :hash")
    suspend fun find(hash: String): ThreatNumberEntity?

    @Upsert
    suspend fun upsertAll(items: List<ThreatNumberEntity>)

    @Query("DELETE FROM threat_numbers WHERE hash IN (:hashes)")
    suspend fun delete(hashes: List<String>)

    @Query("SELECT COUNT(*) FROM threat_numbers")
    suspend fun count(): Int
}

/** A child's task; [json] holds the full FamilyTask, the other columns serve queries. */
@Entity(tableName = "tasks", indices = [Index("assigneeDeviceId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val assigneeDeviceId: String,
    val state: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val json: String,
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND assigneeDeviceId = :deviceId ORDER BY updatedAt DESC")
    fun observeFor(deviceId: String): Flow<List<TaskEntity>>

    /** Includes removed tasks: their earned points still count. */
    @Query("SELECT * FROM tasks WHERE assigneeDeviceId = :deviceId")
    fun observeHistoryFor(deviceId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun clear()
}

@Database(
    entities = [
        MemberEntity::class, AlertEntity::class, LocationEntity::class, UsageEntity::class, RequestEntity::class,
        NoteEntity::class, OutboxEntity::class, EventEntity::class, ThreatNumberEntity::class, TaskEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ShieldDatabase : RoomDatabase() {
    abstract fun members(): MemberDao
    abstract fun alerts(): AlertDao
    abstract fun locations(): LocationDao
    abstract fun usage(): UsageDao
    abstract fun requests(): RequestDao
    abstract fun notes(): NoteDao
    abstract fun outbox(): OutboxDao
    abstract fun events(): EventDao
    abstract fun threatNumbers(): ThreatNumberDao
    abstract fun tasks(): TaskDao

    suspend fun clearFamilyData() {
        members().clear()
        alerts().clear()
        locations().clear()
        usage().clear()
        requests().clear()
        notes().clear()
        outbox().clear()
        tasks().clear()
    }

    companion object {
        fun create(context: Context): ShieldDatabase =
            Room.databaseBuilder(context, ShieldDatabase::class.java, "shield.db").build()

        fun inMemory(context: Context): ShieldDatabase =
            Room.inMemoryDatabaseBuilder(context, ShieldDatabase::class.java).allowMainThreadQueries().build()
    }
}
