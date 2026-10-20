package ru.ubt.familyshield.shared.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.model.WebCategory
import ru.ubt.familyshield.shared.model.WebFilterSettings

class DnsTest {
    private fun ipv4UdpPacket(src: ByteArray, dst: ByteArray, sport: Int, dport: Int, payload: ByteArray): ByteArray {
        val req = UdpPacket(4, dst, src, dport, sport, ByteArray(0))
        // Building a "response" to a reversed request yields a packet from src to dst.
        return IpPackets.buildUdpResponse(req, payload)
    }

    @Test
    fun parsesQueryAndBuildsBlockedResponseV4() {
        val query = DnsMessage.buildQuery(0x1234, "ads.Example.com", DnsMessage.TYPE_A)
        val client = byteArrayOf(10, 111, 222.toByte(), 2)
        val vpnDns = byteArrayOf(10, 111, 222.toByte(), 1)
        val packet = ipv4UdpPacket(client, vpnDns, 40000, 53, query)

        val udp = IpPackets.parseUdp(packet)!!
        assertEquals(53, udp.destinationPort)
        assertArrayEquals(client, udp.sourceAddress)
        val q = DnsMessage.parseQuestion(udp.payload)!!
        assertEquals("ads.example.com", q.name)
        assertEquals(DnsMessage.TYPE_A, q.type)

        val responsePayload = DnsMessage.blocked(udp.payload, q)
        val response = IpPackets.buildUdpResponse(udp, responsePayload)
        // IPv4 header checksum must verify to zero.
        assertEquals(0, IpPackets.checksum(response, 0, 20, 0))
        val parsed = IpPackets.parseUdp(response)!!
        assertArrayEquals(client, parsed.destinationAddress)
        assertEquals(40000, parsed.destinationPort)
        val (addresses, ttl) = DnsMessage.readAddresses(parsed.payload)!!
        assertEquals(1, addresses.size)
        assertArrayEquals(ByteArray(4), addresses[0])
        assertEquals(60, ttl)
        assertEquals(0x1234, IpPackets.u16(parsed.payload, 0))
        assertTrue(IpPackets.u16(parsed.payload, 2) and 0x8000 != 0)
    }

    @Test
    fun udpChecksumVerifiesWithPseudoHeader() {
        val query = DnsMessage.buildQuery(7, "example.org", DnsMessage.TYPE_AAAA)
        val packet = ipv4UdpPacket(byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 1), 5353, 53, query)
        val udpLen = packet.size - 20
        var pseudo = 0
        for (i in 12 until 20 step 2) pseudo += IpPackets.u16(packet, i)
        pseudo += 17 + udpLen
        assertEquals(0, IpPackets.checksum(packet, 20, udpLen, pseudo))
    }

    @Test
    fun ipv6RoundTrip() {
        val src = ByteArray(16) { if (it == 15) 2 else 0 }.also { it[0] = 0xfd.toByte() }
        val dst = ByteArray(16) { if (it == 15) 1 else 0 }.also { it[0] = 0xfd.toByte() }
        val query = DnsMessage.buildQuery(9, "tracker.test", DnsMessage.TYPE_AAAA)
        val packet = IpPackets.buildUdpResponse(UdpPacket(6, dst, src, 53, 1234, ByteArray(0)), query)
        val udp = IpPackets.parseUdp(packet)!!
        assertEquals(6, udp.ipVersion)
        val q = DnsMessage.parseQuestion(udp.payload)!!
        val resp = IpPackets.parseUdp(IpPackets.buildUdpResponse(udp, DnsMessage.blocked(udp.payload, q)))!!
        val (addresses, _) = DnsMessage.readAddresses(resp.payload)!!
        assertArrayEquals(ByteArray(16), addresses.single())
    }

    @Test
    fun nxdomainAndSynthesizedAnswers() {
        val query = DnsMessage.buildQuery(1, "www.google.ru", DnsMessage.TYPE_A)
        val q = DnsMessage.parseQuestion(query)!!
        val nx = DnsMessage.nxdomain(query, q)
        assertEquals(3, IpPackets.u16(nx, 2) and 0xF)
        val ip = byteArrayOf(216.toByte(), 239.toByte(), 38, 120)
        val ans = DnsMessage.answer(query, q, listOf(ip, ByteArray(16)), 300)
        val (addresses, ttl) = DnsMessage.readAddresses(ans)!!
        assertArrayEquals(ip, addresses.single())
        assertEquals(300, ttl)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(IpPackets.parseUdp(ByteArray(10)))
        assertNull(DnsMessage.parseQuestion(ByteArray(5)))
        val q = DnsMessage.buildQuery(1, "a.b", 1)
        q[12] = 0xC0.toByte(); q[13] = 12 // self-referencing compression pointer
        assertNull(DnsMessage.parseQuestion(q))
    }

    @Test
    fun hashedDomainSetMatchesSubdomainsAndSerializes() {
        val set = HashedDomainSet.of(listOf("0.0.0.0 casino.example", "||bet.test^", "# comment", "plain.org", "localhost"))
        assertEquals(3, set.size)
        assertTrue(set.matches("www.casino.example"))
        assertTrue(set.matches("bet.test"))
        assertTrue(!set.matches("example"))
        assertTrue(!set.matches("notcasino.example"))
        val copy = HashedDomainSet.fromBytes(set.toBytes())
        assertTrue(copy.matches("a.b.plain.org"))
    }

    @Test
    fun dnsPolicyDecisions() {
        val lists = mapOf(WebCategory.GAMBLING to HashedDomainSet.of(listOf("casino.example")))
        val settings = WebFilterSettings(
            enabled = true,
            categories = setOf(WebCategory.GAMBLING),
            blockedDomains = setOf("tiktok.com"),
            allowedDomains = setOf("school.casino.example"),
            safeSearch = true,
            youtubeRestricted = true,
        )
        val policy = DnsPolicy(settings, lists)
        assertTrue(policy.decide("www.casino.example") is DnsDecision.Block)
        assertTrue(policy.decide("school.casino.example") is DnsDecision.Allow)
        assertTrue(policy.decide("m.tiktok.com") is DnsDecision.Block)
        assertTrue(policy.decide("dns.google") is DnsDecision.Block)
        assertTrue(policy.decide(DnsBypass.FIREFOX_CANARY) is DnsDecision.Block)
        assertEquals(DnsDecision.Rewrite("forcesafesearch.google.com"), policy.decide("www.google.ru"))
        assertEquals(DnsDecision.Rewrite("restrictmoderate.youtube.com"), policy.decide("www.youtube.com"))
        assertTrue(policy.decide("example.org") is DnsDecision.Allow)
        assertTrue(DnsPolicy(settings.copy(enabled = false), lists).decide("www.casino.example") is DnsDecision.Allow)
        val strict = DnsPolicy(settings.copy(allowListOnly = true, allowedDomains = setOf("uchi.ru")), lists, setOf("api.family.test"))
        assertTrue(strict.decide("example.org") is DnsDecision.Block)
        assertTrue(strict.decide("uchi.ru") is DnsDecision.Allow)
        assertTrue(strict.decide("api.family.test") is DnsDecision.Allow)
    }

    @Test
    fun suffixes() {
        assertEquals(listOf("a.b.example.com", "b.example.com", "example.com"), domainSuffixes("A.b.Example.com.").toList())
        assertNotNull(HashedDomainSet.normalizeDomain("*.example.com"))
    }
}
