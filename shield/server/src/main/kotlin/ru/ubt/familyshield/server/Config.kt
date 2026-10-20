package ru.ubt.familyshield.server

import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.crypto.Signatures
import java.security.KeyPair

/** Runtime configuration, read from environment variables (12-factor style). */
data class ServerConfig(
    val port: Int = 8080,
    val databaseUrl: String = "jdbc:h2:file:./data/shield;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    val databaseUser: String = "",
    val databasePassword: String = "",
    /** ECDSA P-256 key pair used to sign entitlement tokens. */
    val entitlementKeys: KeyPair,
    val packageName: String = "ru.ubt.familyshield",
    val rustoreKeyId: String? = null,
    /** Base64 PKCS#8 RSA private key from RuStore Console (API keys). */
    val rustorePrivateKey: String? = null,
    val rustorePushProjectId: String? = null,
    val rustorePushServiceToken: String? = null,
    /** Development only: trust purchases without RuStore verification. Never enable in production. */
    val allowUnverifiedPurchases: Boolean = false,
    val messageRetentionDays: Int = 30,
    val maxMessageBytes: Int = 256 * 1024,
    val numberReportThreshold: Int = 3,
    /** "CATEGORY=https://url;CATEGORY2=https://url2" — blocklists imported daily (check each list's license). */
    val threatFeeds: Map<String, String> = emptyMap(),
    val trustForwardedFor: Boolean = false,
) {
    val rustoreVerificationEnabled: Boolean get() = !rustoreKeyId.isNullOrBlank() && !rustorePrivateKey.isNullOrBlank()
    val pushEnabled: Boolean get() = !rustorePushProjectId.isNullOrBlank() && !rustorePushServiceToken.isNullOrBlank()

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            val keys = env["ENTITLEMENT_PRIVATE_KEY"]?.takeIf { it.isNotBlank() }?.let { loadKeyPair(it, env["ENTITLEMENT_PUBLIC_KEY"].orEmpty()) }
                ?: Signatures.generateKeyPair().also {
                    System.err.println(
                        "WARNING: ENTITLEMENT_PRIVATE_KEY is not set; using an ephemeral key. Tokens will not survive restarts.\n" +
                            "Public key for the app build (shield.entitlementPublicKey): ${B64.encode(it.public.encoded)}",
                    )
                }
            return ServerConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                databaseUrl = env["DATABASE_URL"] ?: "jdbc:h2:file:./data/shield;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                databaseUser = env["DATABASE_USER"].orEmpty(),
                databasePassword = env["DATABASE_PASSWORD"].orEmpty(),
                entitlementKeys = keys,
                packageName = env["APP_PACKAGE_NAME"] ?: "ru.ubt.familyshield",
                rustoreKeyId = env["RUSTORE_KEY_ID"],
                rustorePrivateKey = env["RUSTORE_PRIVATE_KEY"],
                rustorePushProjectId = env["RUSTORE_PUSH_PROJECT_ID"],
                rustorePushServiceToken = env["RUSTORE_PUSH_SERVICE_TOKEN"],
                allowUnverifiedPurchases = env["ALLOW_UNVERIFIED_PURCHASES"] == "true",
                messageRetentionDays = env["MESSAGE_RETENTION_DAYS"]?.toIntOrNull() ?: 30,
                threatFeeds = parseFeeds(env["THREAT_FEEDS"].orEmpty()),
                trustForwardedFor = env["TRUST_X_FORWARDED_FOR"] == "true",
            )
        }

        fun parseFeeds(spec: String): Map<String, String> = spec.split(';').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx).trim().uppercase() to part.substring(idx + 1).trim()
        }.filter { it.second.startsWith("https://") }.toMap()

        /** Loads the entitlement signing key pair (base64url PKCS#8 private, base64url X.509 public). */
        fun loadKeyPair(pkcs8: String, x509: String): KeyPair {
            val private = Signatures.privateKeyFromPkcs8(B64.decode(pkcs8))
            val public = Signatures.publicKeyFromX509(B64.decode(x509))
            val probe = "probe".toByteArray()
            require(Signatures.verify(public, probe, Signatures.sign(private, probe))) { "ENTITLEMENT_PUBLIC_KEY does not match ENTITLEMENT_PRIVATE_KEY" }
            return KeyPair(public, private)
        }
    }
}
