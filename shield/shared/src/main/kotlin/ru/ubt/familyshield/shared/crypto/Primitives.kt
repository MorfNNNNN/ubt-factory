package ru.ubt.familyshield.shared.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Standard JCA primitives only, so the same code runs on Android (API 26+) and on the server JVM. */
object B64 {
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val dec = Base64.getUrlDecoder()
    fun encode(bytes: ByteArray): String = enc.encodeToString(bytes)
    fun decode(text: String): ByteArray = dec.decode(text.trim().trimEnd('='))
}

object Rng {
    private val random = SecureRandom()
    fun bytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }
    fun int(bound: Int): Int = random.nextInt(bound)
}

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object Hkdf {
    private const val ALG = "HmacSHA256"

    fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        require(length in 1..255 * 32)
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val n = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, out, offset, n)
            offset += n
            counter++
        }
        return out
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALG)
        mac.init(SecretKeySpec(key, ALG))
        return mac.doFinal(data)
    }
}

/** AES-256-GCM with a random 96-bit nonce prepended to the ciphertext. */
object Aead {
    const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    fun newKey(): ByteArray = Rng.bytes(KEY_BYTES)

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 key required" }
        val nonce = Rng.bytes(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 key required" }
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) throw CryptoException("ciphertext too short")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES),
        )
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        } catch (e: AEADBadTagException) {
            throw CryptoException("authentication failed", e)
        }
    }
}

/** ECDSA P-256 signatures (available on every Android version and every JVM). */
object Signatures {
    private const val ALG = "SHA256withECDSA"

    fun generateKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }

    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray =
        Signature.getInstance(ALG).run {
            initSign(privateKey)
            update(data)
            sign()
        }

    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean = try {
        Signature.getInstance(ALG).run {
            initVerify(publicKey)
            update(data)
            verify(signature)
        }
    } catch (e: Exception) {
        false
    }

    fun publicKeyFromX509(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    fun privateKeyFromPkcs8(encoded: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(encoded))
}

fun sha256(data: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(data)

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Constant-time comparison for secrets. */
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = java.security.MessageDigest.isEqual(a, b)
