package ru.ubt.familyshield.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import ru.ubt.familyshield.server.db.Db
import ru.ubt.familyshield.server.services.FeedImporter
import ru.ubt.familyshield.server.services.NoopPush
import ru.ubt.familyshield.server.services.RejectingVerifier
import ru.ubt.familyshield.server.services.RuStoreApiVerifier
import ru.ubt.familyshield.server.services.RuStorePushSender
import ru.ubt.familyshield.server.services.TrustingVerifier
import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.crypto.Signatures
import ru.ubt.familyshield.shared.model.WebCategory
import java.io.File

private val log = LoggerFactory.getLogger("FamilyShieldServer")

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "keygen" -> {
            val kp = Signatures.generateKeyPair()
            println("ENTITLEMENT_PRIVATE_KEY=${B64.encode(kp.private.encoded)}")
            println("ENTITLEMENT_PUBLIC_KEY=${B64.encode(kp.public.encoded)}")
            println("# Put ENTITLEMENT_PUBLIC_KEY into the app build as shield.entitlementPublicKey")
            return
        }
        "import-domains" -> {
            require(args.size >= 3) { "usage: import-domains <CATEGORY> <file> [--append]" }
            val config = ServerConfig.fromEnv()
            val db = Db(config.databaseUrl, config.databaseUser, config.databasePassword)
            val services = ShieldServices(config, db, RejectingVerifier(), NoopPush)
            val count = runBlocking {
                FeedImporter(services.threats).importFile(WebCategory.valueOf(args[1].uppercase()), File(args[2]), replace = "--append" !in args)
            }
            println("Imported $count new domains into ${args[1]}")
            db.close()
            return
        }
    }
    serve(ServerConfig.fromEnv())
}

fun serve(config: ServerConfig) {
    val db = Db(config.databaseUrl, config.databaseUser, config.databasePassword)
    val verifier = when {
        config.rustoreVerificationEnabled -> RuStoreApiVerifier(config.rustoreKeyId!!, config.rustorePrivateKey!!, config.packageName)
        config.allowUnverifiedPurchases -> TrustingVerifier().also { log.warn("ALLOW_UNVERIFIED_PURCHASES=true: purchases are NOT verified. Development only!") }
        else -> RejectingVerifier().also { log.warn("RuStore API keys are not configured: purchases cannot be verified") }
    }
    val push = if (config.pushEnabled) {
        RuStorePushSender(db, config.rustorePushProjectId!!, config.rustorePushServiceToken!!)
    } else {
        log.warn("RuStore push is not configured: devices will rely on periodic sync")
        NoopPush
    }
    val services = ShieldServices(config, db, verifier, push)

    val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    jobs.launch {
        while (true) {
            runCatching { services.messages.purgeOlderThan(config.messageRetentionDays) }
                .onSuccess { if (it > 0) log.info("Purged $it expired messages") }
                .onFailure { log.warn("Retention job failed", it) }
            delay(60 * 60 * 1000L)
        }
    }
    if (config.threatFeeds.isNotEmpty()) {
        jobs.launch {
            val importer = FeedImporter(services.threats)
            while (true) {
                importer.importAll(config.threatFeeds)
                delay(24 * 60 * 60 * 1000L)
            }
        }
    }

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { shieldModule(services) }.apply {
        Runtime.getRuntime().addShutdownHook(Thread { db.close() })
    }.start(wait = true)
}
