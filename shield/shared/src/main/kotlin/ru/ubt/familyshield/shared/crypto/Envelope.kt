package ru.ubt.familyshield.shared.crypto

import kotlinx.serialization.encodeToString
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.protocol.InnerMessage
import java.nio.ByteBuffer
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Encrypted family message body: base64url( version:1 | epoch:4 | AES-GCM(nonce|ct|tag) ).
 * The associated data binds the ciphertext to the family, message id and claimed sender, so the
 * relay cannot replay a message under another id or attribute it to another device.
 */
object EnvelopeCodec {
    private const val VERSION: Byte = 1

    fun aad(familyId: String, messageId: String, senderDeviceId: String): ByteArray =
        "fs1|$familyId|$messageId|$senderDeviceId".toByteArray(Charsets.UTF_8)

    fun seal(
        message: InnerMessage,
        familyKey: ByteArray,
        epoch: Int,
        familyId: String,
        messageId: String,
        senderDeviceId: String,
    ): String {
        val plaintext = ShieldJson.encodeToString(message).toByteArray(Charsets.UTF_8)
        val sealed = Aead.seal(familyKey, plaintext, aad(familyId, messageId, senderDeviceId))
        val buf = ByteBuffer.allocate(1 + 4 + sealed.size)
        buf.put(VERSION).putInt(epoch).put(sealed)
        return B64.encode(buf.array())
    }

    /** Returns the key epoch the body was sealed with, without decrypting. */
    fun epochOf(body: String): Int {
        val bytes = B64.decode(body)
        if (bytes.size < 5 || bytes[0] != VERSION) throw CryptoException("unsupported envelope")
        return ByteBuffer.wrap(bytes, 1, 4).int
    }

    fun open(
        body: String,
        keyForEpoch: (Int) -> ByteArray?,
        familyId: String,
        messageId: String,
        senderDeviceId: String,
    ): InnerMessage {
        val bytes = B64.decode(body)
        if (bytes.size < 5 || bytes[0] != VERSION) throw CryptoException("unsupported envelope")
        val epoch = ByteBuffer.wrap(bytes, 1, 4).int
        val key = keyForEpoch(epoch) ?: throw CryptoException("no key for epoch $epoch")
        val plain = Aead.open(key, bytes.copyOfRange(5, bytes.size), aad(familyId, messageId, senderDeviceId))
        return ShieldJson.decodeFromString(InnerMessage.serializer(), plain.toString(Charsets.UTF_8))
    }

    private fun signedData(familyId: String, messageId: String, senderDeviceId: String, body: String): ByteArray =
        sha256(aad(familyId, messageId, senderDeviceId) + body.toByteArray(Charsets.UTF_8))

    fun sign(privateKey: PrivateKey, familyId: String, messageId: String, senderDeviceId: String, body: String): String =
        B64.encode(Signatures.sign(privateKey, signedData(familyId, messageId, senderDeviceId, body)))

    fun verify(
        publicKey: PublicKey,
        familyId: String,
        messageId: String,
        senderDeviceId: String,
        body: String,
        signature: String?,
    ): Boolean {
        if (signature.isNullOrEmpty()) return false
        val sig = runCatching { B64.decode(signature) }.getOrNull() ?: return false
        return Signatures.verify(publicKey, signedData(familyId, messageId, senderDeviceId, body), sig)
    }
}
