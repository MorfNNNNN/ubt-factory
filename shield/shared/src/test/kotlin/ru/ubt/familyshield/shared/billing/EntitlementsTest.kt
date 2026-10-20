package ru.ubt.familyshield.shared.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.crypto.Signatures

class EntitlementsTest {
    private val now = 1_000_000L

    @Test
    fun freeTierHasSafetyBasicsOnly() {
        val e = Entitlement.FREE
        assertTrue(e.has(Feature.SOS, now))
        assertTrue(e.has(Feature.MANUAL_CHECK, now))
        assertFalse(e.has(Feature.CALL_BLOCKING, now))
        assertFalse(e.has(Feature.APP_LIMITS, now))
        assertEquals(1, e.limits(now).maxProtectedDevices)
    }

    @Test
    fun subscriptionUnlocksAndExpires() {
        val e = Entitlement.forProduct(Products.PREMIUM_MONTH, expiresAt = now + 10, issuedAt = now, familyId = "f")
        assertTrue(e.has(Feature.CALL_BLOCKING, now))
        assertTrue(e.has(Feature.WEB_FILTER, now))
        assertFalse(e.has(Feature.REPORT_EXPORT, now))
        assertFalse(e.has(Feature.CALL_BLOCKING, now + 11))
        assertEquals(Tier.FREE, e.effectiveTier(now + 11))
    }

    @Test
    fun packsCoverTheirGroupForever() {
        val e = Entitlement.forProduct(Products.PACK_ELDER, expiresAt = now, issuedAt = now, familyId = null)
        assertNull(e.expiresAt)
        assertTrue(e.has(Feature.IN_CALL_GUARD, now * 1000))
        assertFalse(e.has(Feature.APP_LIMITS, now))
        assertFalse(e.has(Feature.LOCATION_HISTORY, now))
        assertEquals(2, e.limits(now).maxProtectedDevices)
    }

    @Test
    fun mergingKeepsHighestTierAndAllPacks() {
        val sub = Entitlement.forProduct(Products.FAMILY_MAX_YEAR, now + 100, now, "f")
        val pack = Entitlement.forProduct(Products.PACK_KIDS, null, now, "f")
        val merged = sub + pack
        assertEquals(Tier.FAMILY_MAX, merged.tier)
        assertEquals(setOf(Pack.KIDS_PRO), merged.packs)
        assertEquals(now + 100, merged.expiresAt)
        assertTrue(merged.has(Feature.REPORT_EXPORT, now))
        // After the subscription ends the kids pack still works.
        assertTrue(merged.has(Feature.APP_LIMITS, now + 1000))
        assertFalse(merged.has(Feature.IN_CALL_GUARD, now + 1000))
        val lifetime = Entitlement.forProduct(Products.PREMIUM_LIFETIME, now + 5, now, "f")
        assertNull((lifetime + Entitlement.forProduct(Products.PREMIUM_MONTH, now + 5, now, "f")).expiresAt)
    }

    @Test
    fun signedTokenRoundTripAndTamper() {
        val kp = Signatures.generateKeyPair()
        val e = Entitlement.forProduct(Products.PREMIUM_YEAR, now + 1000, now, "fam1")
        val token = EntitlementToken.issue(e, kp.private)
        assertEquals(e, EntitlementToken.verify(token, kp.public))
        val forged = EntitlementToken.issue(e.copy(tier = Tier.FAMILY_MAX), Signatures.generateKeyPair().private)
        assertNull(EntitlementToken.verify(forged, kp.public))
        val swapped = forged.substringBefore('.') + "." + token.substringAfter('.')
        assertNull(EntitlementToken.verify(swapped, kp.public))
        assertNull(EntitlementToken.verify("garbage", kp.public))
    }

    @Test
    fun productCatalogIsConsistent() {
        for (id in Products.all) assertTrue(id, Products.tierOf(id) != null || Products.packOf(id) != null)
        assertEquals(Products.all.size, Products.all.toSet().size)
    }
}
