package ru.ubt.familyshield.shared.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.model.CheckIn
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import ru.ubt.familyshield.shared.protocol.InnerMessage

class CryptoTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun hkdfRfc5869TestCase1() {
        val okm = Hkdf.derive(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
        )
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.toHex())
    }

    @Test
    fun aeadRoundTripAndTamperDetection() {
        val key = Aead.newKey()
        val sealed = Aead.seal(key, "привет".toByteArray(), "aad".toByteArray())
        assertEquals("привет", String(Aead.open(key, sealed, "aad".toByteArray())))
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertTrue(runCatching { Aead.open(key, tampered, "aad".toByteArray()) }.isFailure)
        assertTrue(runCatching { Aead.open(key, sealed, "other".toByteArray()) }.isFailure)
    }

    @Test
    fun envelopeBindsSenderAndMessageId() {
        val key = Aead.newKey()
        val msg = InnerMessage(42, FamilyPayload.CheckInDone(CheckIn("c1", 42)))
        val body = EnvelopeCodec.seal(msg, key, epoch = 3, familyId = "fam", messageId = "m1", senderDeviceId = "dev1")
        assertEquals(3, EnvelopeCodec.epochOf(body))
        val opened = EnvelopeCodec.open(body, { if (it == 3) key else null }, "fam", "m1", "dev1")
        assertEquals(msg, opened)
        assertTrue(runCatching { EnvelopeCodec.open(body, { key }, "fam", "m1", "dev2") }.isFailure)
        assertTrue(runCatching { EnvelopeCodec.open(body, { key }, "fam", "m2", "dev1") }.isFailure)
        assertTrue(runCatching { EnvelopeCodec.open(body, { null }, "fam", "m1", "dev1") }.isFailure)
    }

    @Test
    fun guardianSignatures() {
        val kp = Signatures.generateKeyPair()
        val pub = Signatures.publicKeyFromX509(kp.public.encoded)
        val sig = EnvelopeCodec.sign(kp.private, "fam", "m1", "g1", "body")
        assertTrue(EnvelopeCodec.verify(pub, "fam", "m1", "g1", "body", sig))
        assertFalse(EnvelopeCodec.verify(pub, "fam", "m1", "g1", "body2", sig))
        assertFalse(EnvelopeCodec.verify(pub, "fam", "m1", "child", "body", sig))
        assertFalse(EnvelopeCodec.verify(pub, "fam", "m1", "g1", "body", null))
        val other = Signatures.generateKeyPair()
        assertFalse(EnvelopeCodec.verify(other.public, "fam", "m1", "g1", "body", sig))
        val restored = Signatures.privateKeyFromPkcs8(kp.private.encoded)
        assertTrue(EnvelopeCodec.verify(pub, "f", "i", "s", "b", EnvelopeCodec.sign(restored, "f", "i", "s", "b")))
    }

    @Test
    fun pairingCodeFormatsParsesAndWraps() {
        val code = PairingCode(Crockford.random(10), PairingCode.newSecret(), "https://api.example.ru")
        val display = code.displayCode()
        assertEquals(31, display.length)
        val typed = PairingCode.parse(display.lowercase().replace('0', 'o'))
        assertEquals(code.inviteId, typed?.inviteId)
        assertEquals(code.secret, typed?.secret)
        val fromUri = PairingCode.parse(code.toUri())!!
        assertEquals("https://api.example.ru", fromUri.serverUrl)

        val secrets = FamilySecrets("fam", 1, B64.encode(Aead.newKey()), "g1", "pub", MemberRole.CHILD, "Маша")
        val wrapped = code.wrap(secrets)
        assertEquals(secrets, fromUri.unwrap(wrapped))
        val wrongSecret = PairingCode(code.inviteId, PairingCode.newSecret())
        assertTrue(runCatching { wrongSecret.unwrap(wrapped) }.isFailure)
    }

    @Test
    fun pairingParseRejectsJunk() {
        assertNull(PairingCode.parse("hello"))
        assertNull(PairingCode.parse("familyshield://pair?i=SHORT&k=X"))
        assertNotNull(PairingCode.parse("https://family.example/pair#i=0123456789&k=ABCDEFGHJKMNPQRS"))
    }
}

class PinHashTest {
    @org.junit.Test
    fun pinHashVerifies() {
        val pin = ru.ubt.familyshield.shared.model.PinHash.create("2580")
        org.junit.Assert.assertTrue(pin.matches("2580"))
        org.junit.Assert.assertFalse(pin.matches("0000"))
    }
}
