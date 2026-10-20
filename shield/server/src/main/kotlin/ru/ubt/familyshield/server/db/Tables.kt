package ru.ubt.familyshield.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/** Devices are the only identity: no names, phones or e-mails are stored on the server. */
object Devices : Table("devices") {
    val id = varchar("id", 32)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val familyId = varchar("family_id", 32).nullable().index()
    val role = varchar("role", 16).nullable()
    val pushToken = varchar("push_token", 512).nullable()
    val appVersion = varchar("app_version", 32)
    val createdAt = long("created_at")
    val lastSeenAt = long("last_seen_at")
    override val primaryKey = PrimaryKey(id)
}

object Families : Table("families") {
    val id = varchar("id", 32)
    val createdBy = varchar("created_by", 32)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Invites : Table("invites") {
    val id = varchar("id", 16)
    val familyId = varchar("family_id", 32).index()
    val role = varchar("role", 16)
    /** Opaque to the server: encrypted with a key derived from the pairing secret it never sees. */
    val wrappedSecrets = text("wrapped_secrets")
    val createdBy = varchar("created_by", 32)
    val expiresAt = long("expires_at")
    val usedBy = varchar("used_by", 32).nullable()
    val usedAt = long("used_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Per-recipient mailbox of end-to-end encrypted envelopes; rows are deleted once acknowledged. */
object Messages : Table("messages") {
    val seq = long("seq").autoIncrement()
    val messageId = varchar("message_id", 64)
    val familyId = varchar("family_id", 32)
    val sender = varchar("sender", 32)
    val recipient = varchar("recipient", 32)
    val createdAt = long("created_at").index()
    val body = text("body")
    val signature = text("signature").nullable()
    override val primaryKey = PrimaryKey(seq)

    init {
        uniqueIndex("messages_recipient_id", recipient, messageId)
        index("messages_recipient_seq", false, recipient, seq)
    }
}

object Purchases : Table("purchases") {
    val purchaseId = varchar("purchase_id", 128)
    val familyId = varchar("family_id", 32).nullable().index()
    val deviceId = varchar("device_id", 32).index()
    val productId = varchar("product_id", 64)
    val expiresAt = long("expires_at").nullable()
    val verifiedAt = long("verified_at")
    val sandbox = bool("sandbox")
    val status = varchar("status", 32)
    override val primaryKey = PrimaryKey(purchaseId)
}

object ThreatNumbers : Table("threat_numbers") {
    val hash = varchar("hash", 16)
    val category = varchar("category", 32)
    val reports = integer("reports")
    val version = long("version").index()
    val published = bool("published")
    override val primaryKey = PrimaryKey(hash)
}

object NumberReports : Table("number_reports") {
    val reporter = varchar("reporter", 32)
    val hash = varchar("hash", 16)
    val category = varchar("category", 32)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(reporter, hash)
}

object ThreatDomains : Table("threat_domains") {
    val domain = varchar("domain", 253)
    val category = varchar("category", 32)
    val version = long("version").index()
    val removed = bool("removed")
    override val primaryKey = PrimaryKey(domain, category)
}

object Counters : Table("counters") {
    val name = varchar("name", 32)
    val value = long("value")
    override val primaryKey = PrimaryKey(name)
}

class Db(url: String, user: String, password: String) {
    val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        },
    )
    val database: Database = Database.connect(dataSource)

    init {
        transaction(database) {
            SchemaUtils.create(Devices, Families, Invites, Messages, Purchases, ThreatNumbers, NumberReports, ThreatDomains, Counters)
        }
    }

    suspend fun <T> tx(block: suspend org.jetbrains.exposed.sql.Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }

    fun close() = dataSource.close()
}
