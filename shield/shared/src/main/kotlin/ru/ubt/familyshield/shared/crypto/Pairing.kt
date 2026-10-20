package ru.ubt.familyshield.shared.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.model.MemberRole

/**
 * Secrets a new device needs to join a family. They never reach the server in clear text: the
 * guardian wraps them with a key derived from [PairingCode.secret], which travels only inside the
 * QR code (or is typed in by hand).
 */
@Serializable
data class FamilySecrets(
    val familyId: String,
    val epoch: Int,
    /** base64url AES-256 key. */
    val familyKey: String,
    val guardianDeviceId: String,
    /** base64url X.509 ECDSA P-256 public key of the inviting guardian. */
    val guardianPublicKey: String,
    val role: MemberRole,
    val memberName: String = "",
    val familyName: String = "",
)

object Crockford {
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun random(length: Int): String = buildString { repeat(length) { append(ALPHABET[Rng.int(32)]) } }

    /** Normalizes user input: upper-case, drops separators, maps look-alike letters. */
    fun normalize(input: String): String = buildString {
        for (c in input.uppercase()) {
            when (c) {
                'O' -> append('0')
                'I', 'L' -> append('1')
                'U' -> append('V')
                in ALPHABET -> append(c)
                else -> Unit
            }
        }
    }
}

data class PairingCode(val inviteId: String, val secret: String, val serverUrl: String? = null) {
    init {
        require(inviteId.length == INVITE_LENGTH && inviteId.all { it in Crockford.ALPHABET }) { "bad invite id" }
        require(secret.length == SECRET_LENGTH && secret.all { it in Crockford.ALPHABET }) { "bad secret" }
    }

    /** Human-typable form: XXXXX-XXXXX-XXXX-XXXX-XXXX-XXXX */
    fun displayCode(): String {
        val raw = inviteId + secret
        return listOf(raw.substring(0, 5), raw.substring(5, 10), raw.substring(10, 14), raw.substring(14, 18), raw.substring(18, 22), raw.substring(22, 26))
            .joinToString("-")
    }

    fun toUri(): String = buildString {
        append("familyshield://pair?i=").append(inviteId).append("&k=").append(secret)
        if (serverUrl != null) append("&s=").append(java.net.URLEncoder.encode(serverUrl, "UTF-8"))
    }

    fun wrappingKey(): ByteArray = Hkdf.derive(
        ikm = secret.toByteArray(Charsets.US_ASCII),
        salt = inviteId.toByteArray(Charsets.US_ASCII),
        info = "family-shield/pair/v1".toByteArray(Charsets.US_ASCII),
    )

    fun wrap(secrets: FamilySecrets): String =
        B64.encode(Aead.seal(wrappingKey(), ShieldJson.encodeToString(secrets).toByteArray(Charsets.UTF_8), aad()))

    fun unwrap(wrapped: String): FamilySecrets {
        val plain = Aead.open(wrappingKey(), B64.decode(wrapped), aad())
        return ShieldJson.decodeFromString(FamilySecrets.serializer(), plain.toString(Charsets.UTF_8))
    }

    private fun aad() = "fs-pair|$inviteId".toByteArray(Charsets.US_ASCII)

    companion object {
        const val INVITE_LENGTH = 10
        const val SECRET_LENGTH = 16

        fun newSecret(): String = Crockford.random(SECRET_LENGTH)

        /** Accepts a familyshield:// URI, an https link with the same query, or a typed code. */
        fun parse(input: String): PairingCode? {
            val text = input.trim()
            if (text.contains("i=") && text.contains("k=")) {
                val query = text.substringAfter('?', text.substringAfter('#', ""))
                val params = query.split('&').mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx <= 0) null else part.substring(0, idx) to java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                }.toMap()
                val invite = Crockford.normalize(params["i"].orEmpty())
                val secret = Crockford.normalize(params["k"].orEmpty())
                return runCatching { PairingCode(invite, secret, params["s"]?.takeIf { it.startsWith("https://") }) }.getOrNull()
            }
            val raw = Crockford.normalize(text)
            if (raw.length != INVITE_LENGTH + SECRET_LENGTH) return null
            return runCatching { PairingCode(raw.substring(0, INVITE_LENGTH), raw.substring(INVITE_LENGTH)) }.getOrNull()
        }
    }
}
