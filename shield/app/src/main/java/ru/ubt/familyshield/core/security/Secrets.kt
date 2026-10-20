package ru.ubt.familyshield.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ru.ubt.familyshield.shared.crypto.Aead
import ru.ubt.familyshield.shared.crypto.B64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets at rest (family keys, device token). */
interface SecretBox {
    fun seal(plain: ByteArray): String
    fun open(sealed: String): ByteArray
}

/** AES-256-GCM key that never leaves the Android Keystore (hardware-backed where available). */
class KeystoreSecretBox(private val alias: String = "fs_master_v1") : SecretBox {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun seal(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return B64.encode(cipher.iv + cipher.doFinal(plain))
    }

    override fun open(sealed: String): ByteArray {
        val bytes = B64.decode(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }
}

/** For tests and environments without a keystore. */
class MemorySecretBox(private val key: ByteArray = Aead.newKey()) : SecretBox {
    override fun seal(plain: ByteArray): String = B64.encode(Aead.seal(key, plain, ByteArray(0)))
    override fun open(sealed: String): ByteArray = Aead.open(key, B64.decode(sealed), ByteArray(0))
}

/** Guardian signing identity. Commands and policies are signed so a child cannot forge them. */
interface GuardianSigner {
    fun publicKeyX509(): ByteArray
    fun privateKey(): PrivateKey
}

class KeystoreGuardianSigner(private val alias: String = "fs_guardian_sign_v1") : GuardianSigner {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    @Synchronized
    private fun ensure() {
        if (keyStore.containsAlias(alias)) return
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        gen.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        gen.generateKeyPair()
    }

    override fun publicKeyX509(): ByteArray {
        ensure()
        return keyStore.getCertificate(alias).publicKey.encoded
    }

    override fun privateKey(): PrivateKey {
        ensure()
        return keyStore.getKey(alias, null) as PrivateKey
    }

    fun delete() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }
}

class MemoryGuardianSigner : GuardianSigner {
    private val pair = ru.ubt.familyshield.shared.crypto.Signatures.generateKeyPair()
    override fun publicKeyX509(): ByteArray = pair.public.encoded
    override fun privateKey(): PrivateKey = pair.private
}
