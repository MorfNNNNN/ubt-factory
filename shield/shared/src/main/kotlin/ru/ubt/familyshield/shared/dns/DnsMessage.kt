package ru.ubt.familyshield.shared.dns

import java.io.ByteArrayOutputStream

data class DnsQuestion(val id: Int, val flags: Int, val name: String, val type: Int, val qclass: Int, val questionEnd: Int)

/** Small RFC 1035 codec: parse the question of a query, synthesize answers, read A/AAAA from responses. */
object DnsMessage {
    const val TYPE_A = 1
    const val TYPE_CNAME = 5
    const val TYPE_AAAA = 28
    const val TYPE_HTTPS = 65
    const val CLASS_IN = 1
    const val RCODE_NXDOMAIN = 3
    const val RCODE_SERVFAIL = 2

    fun parseQuestion(msg: ByteArray): DnsQuestion? {
        if (msg.size < 12) return null
        val id = IpPackets.u16(msg, 0)
        val flags = IpPackets.u16(msg, 2)
        if (flags and 0x8000 != 0) return null // not a query
        if (IpPackets.u16(msg, 4) != 1) return null
        val (name, end) = readName(msg, 12) ?: return null
        if (end + 4 > msg.size) return null
        return DnsQuestion(id, flags, name, IpPackets.u16(msg, end), IpPackets.u16(msg, end + 2), end + 4)
    }

    /** Reads a (possibly compressed) domain name; returns the name and the offset after it. */
    fun readName(msg: ByteArray, start: Int): Pair<String, Int>? {
        val labels = ArrayList<String>()
        var pos = start
        var end = -1
        var jumps = 0
        while (true) {
            if (pos >= msg.size) return null
            val len = msg[pos].toInt() and 0xFF
            when {
                len == 0 -> {
                    if (end < 0) end = pos + 1
                    break
                }
                len and 0xC0 == 0xC0 -> {
                    if (pos + 1 >= msg.size || ++jumps > 16) return null
                    if (end < 0) end = pos + 2
                    pos = ((len and 0x3F) shl 8) or (msg[pos + 1].toInt() and 0xFF)
                }
                len > 63 -> return null
                else -> {
                    if (pos + 1 + len > msg.size) return null
                    labels += String(msg, pos + 1, len, Charsets.ISO_8859_1).lowercase()
                    pos += 1 + len
                }
            }
        }
        return labels.joinToString(".") to end
    }

    private fun header(out: ByteArrayOutputStream, id: Int, flags: Int, qd: Int, an: Int) {
        out.write(id ushr 8); out.write(id)
        out.write(flags ushr 8); out.write(flags)
        out.write(0); out.write(qd)
        out.write(an ushr 8); out.write(an)
        repeat(4) { out.write(0) }
    }

    private fun responseFlags(queryFlags: Int, rcode: Int): Int =
        0x8000 or (queryFlags and 0x7800) or (queryFlags and 0x0100) or 0x0080 or (rcode and 0xF)

    private fun writeQuestion(out: ByteArrayOutputStream, query: ByteArray, q: DnsQuestion) {
        out.write(query, 12, q.questionEnd - 12)
    }

    /** NXDOMAIN response echoing the question. */
    fun nxdomain(query: ByteArray, q: DnsQuestion): ByteArray {
        val out = ByteArrayOutputStream(q.questionEnd)
        header(out, q.id, responseFlags(q.flags, RCODE_NXDOMAIN), 1, 0)
        writeQuestion(out, query, q)
        return out.toByteArray()
    }

    fun servfail(query: ByteArray, q: DnsQuestion): ByteArray {
        val out = ByteArrayOutputStream(q.questionEnd)
        header(out, q.id, responseFlags(q.flags, RCODE_SERVFAIL), 1, 0)
        writeQuestion(out, query, q)
        return out.toByteArray()
    }

    /**
     * Blocked-domain response: 0.0.0.0 for A, :: for AAAA (so apps fail fast instead of retrying),
     * and an empty NOERROR answer for other record types.
     */
    fun blocked(query: ByteArray, q: DnsQuestion, ttl: Int = 60): ByteArray = when (q.type) {
        TYPE_A -> answer(query, q, listOf(ByteArray(4)), ttl)
        TYPE_AAAA -> answer(query, q, listOf(ByteArray(16)), ttl)
        else -> answer(query, q, emptyList(), ttl)
    }

    /** Synthesized answer with the given addresses (4 bytes = A, 16 bytes = AAAA) for the question name. */
    fun answer(query: ByteArray, q: DnsQuestion, addresses: List<ByteArray>, ttl: Int): ByteArray {
        val matching = addresses.filter { (q.type == TYPE_A && it.size == 4) || (q.type == TYPE_AAAA && it.size == 16) }
        val out = ByteArrayOutputStream(q.questionEnd + matching.size * 28)
        header(out, q.id, responseFlags(q.flags, 0), 1, matching.size)
        writeQuestion(out, query, q)
        for (addr in matching) {
            out.write(0xC0); out.write(0x0C) // pointer to the question name
            out.write(q.type ushr 8); out.write(q.type)
            out.write(0); out.write(CLASS_IN)
            out.write(ttl ushr 24); out.write(ttl ushr 16); out.write(ttl ushr 8); out.write(ttl)
            out.write(0); out.write(addr.size)
            out.write(addr, 0, addr.size)
        }
        return out.toByteArray()
    }

    fun buildQuery(id: Int, name: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream(32 + name.length)
        header(out, id, 0x0100, 1, 0)
        for (label in name.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.ISO_8859_1)
            out.write(bytes.size)
            out.write(bytes, 0, bytes.size)
        }
        out.write(0)
        out.write(type ushr 8); out.write(type)
        out.write(0); out.write(CLASS_IN)
        return out.toByteArray()
    }

    /** Extracts A/AAAA addresses and the minimum TTL from a response. */
    fun readAddresses(msg: ByteArray): Pair<List<ByteArray>, Int>? {
        if (msg.size < 12) return null
        val qd = IpPackets.u16(msg, 4)
        val an = IpPackets.u16(msg, 6)
        var pos = 12
        repeat(qd) {
            val (_, end) = readName(msg, pos) ?: return null
            pos = end + 4
        }
        val result = ArrayList<ByteArray>()
        var minTtl = Int.MAX_VALUE
        repeat(an) {
            val (_, end) = readName(msg, pos) ?: return null
            if (end + 10 > msg.size) return null
            val type = IpPackets.u16(msg, end)
            val ttl = ((msg[end + 4].toInt() and 0xFF) shl 24) or ((msg[end + 5].toInt() and 0xFF) shl 16) or
                ((msg[end + 6].toInt() and 0xFF) shl 8) or (msg[end + 7].toInt() and 0xFF)
            val rdLen = IpPackets.u16(msg, end + 8)
            val rdStart = end + 10
            if (rdStart + rdLen > msg.size) return null
            if ((type == TYPE_A && rdLen == 4) || (type == TYPE_AAAA && rdLen == 16)) {
                result += msg.copyOfRange(rdStart, rdStart + rdLen)
                minTtl = minOf(minTtl, ttl)
            }
            pos = rdStart + rdLen
        }
        return result to (if (minTtl == Int.MAX_VALUE) 300 else minTtl)
    }

    /** Rewrites the transaction id of a response so it can be relayed to the original client. */
    fun withId(msg: ByteArray, id: Int): ByteArray = msg.copyOf().also { IpPackets.put16(it, 0, id) }
}
