package ru.ubt.familyshield.shared.dns

/**
 * Minimal IPv4/IPv6 + UDP packet codec for the local DNS-filtering VPN. Only what is needed to
 * read DNS queries from the tun interface and write synthesized responses back.
 */
data class UdpPacket(
    val ipVersion: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

object IpPackets {
    const val PROTO_UDP = 17

    /** Parses a raw IP packet. Returns null for anything that is not a well-formed UDP datagram. */
    fun parseUdp(packet: ByteArray, length: Int = packet.size): UdpPacket? {
        if (length < 1) return null
        return when ((packet[0].toInt() ushr 4) and 0xF) {
            4 -> parseV4(packet, length)
            6 -> parseV6(packet, length)
            else -> null
        }
    }

    private fun parseV4(p: ByteArray, length: Int): UdpPacket? {
        if (length < 20) return null
        val ihl = (p[0].toInt() and 0xF) * 4
        if (ihl < 20 || length < ihl + 8) return null
        val totalLength = u16(p, 2)
        if (totalLength > length || totalLength < ihl + 8) return null
        if ((p[9].toInt() and 0xFF) != PROTO_UDP) return null
        val fragmentInfo = u16(p, 6)
        if ((fragmentInfo and 0x1FFF) != 0 || (fragmentInfo and 0x2000) != 0) return null // fragments are not supported
        val src = p.copyOfRange(12, 16)
        val dst = p.copyOfRange(16, 20)
        val udpLen = u16(p, ihl + 4)
        if (udpLen < 8 || ihl + udpLen > totalLength) return null
        return UdpPacket(4, src, dst, u16(p, ihl), u16(p, ihl + 2), p.copyOfRange(ihl + 8, ihl + udpLen))
    }

    private fun parseV6(p: ByteArray, length: Int): UdpPacket? {
        if (length < 48) return null
        val payloadLength = u16(p, 4)
        if (40 + payloadLength > length) return null
        if ((p[6].toInt() and 0xFF) != PROTO_UDP) return null // extension headers are not used for DNS
        val src = p.copyOfRange(8, 24)
        val dst = p.copyOfRange(24, 40)
        val udpLen = u16(p, 44)
        if (udpLen < 8 || udpLen > payloadLength) return null
        return UdpPacket(6, src, dst, u16(p, 40), u16(p, 42), p.copyOfRange(48, 40 + udpLen))
    }

    /** Builds a UDP response to [request], swapping addresses and ports. */
    fun buildUdpResponse(request: UdpPacket, payload: ByteArray): ByteArray =
        if (request.ipVersion == 4) buildV4(request, payload) else buildV6(request, payload)

    private fun buildV4(req: UdpPacket, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        val out = ByteArray(total)
        out[0] = 0x45
        put16(out, 2, total)
        put16(out, 6, 0x4000) // don't fragment
        out[8] = 64
        out[9] = PROTO_UDP.toByte()
        System.arraycopy(req.destinationAddress, 0, out, 12, 4)
        System.arraycopy(req.sourceAddress, 0, out, 16, 4)
        put16(out, 10, checksum(out, 0, 20, 0))
        put16(out, 20, req.destinationPort)
        put16(out, 22, req.sourcePort)
        put16(out, 24, udpLen)
        System.arraycopy(payload, 0, out, 28, payload.size)
        val pseudo = pseudoSum(req.destinationAddress, req.sourceAddress, udpLen)
        put16(out, 26, udpChecksum(checksum(out, 20, udpLen, pseudo)))
        return out
    }

    private fun buildV6(req: UdpPacket, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val out = ByteArray(40 + udpLen)
        out[0] = 0x60
        put16(out, 4, udpLen)
        out[6] = PROTO_UDP.toByte()
        out[7] = 64
        System.arraycopy(req.destinationAddress, 0, out, 8, 16)
        System.arraycopy(req.sourceAddress, 0, out, 24, 16)
        put16(out, 40, req.destinationPort)
        put16(out, 42, req.sourcePort)
        put16(out, 44, udpLen)
        System.arraycopy(payload, 0, out, 48, payload.size)
        val pseudo = pseudoSum(req.destinationAddress, req.sourceAddress, udpLen)
        put16(out, 46, udpChecksum(checksum(out, 40, udpLen, pseudo)))
        return out
    }

    private fun udpChecksum(sum: Int): Int = if (sum == 0) 0xFFFF else sum

    private fun pseudoSum(src: ByteArray, dst: ByteArray, udpLen: Int): Int {
        var sum = 0
        for (i in src.indices step 2) sum += ((src[i].toInt() and 0xFF) shl 8) or (src[i + 1].toInt() and 0xFF)
        for (i in dst.indices step 2) sum += ((dst[i].toInt() and 0xFF) shl 8) or (dst[i + 1].toInt() and 0xFF)
        sum += PROTO_UDP
        sum += udpLen
        return sum
    }

    /** One's-complement checksum over [len] bytes starting at [offset], seeded with [initial]. */
    fun checksum(buf: ByteArray, offset: Int, len: Int, initial: Int): Int {
        var sum = initial.toLong()
        var i = offset
        val end = offset + len
        while (i + 1 < end) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (buf[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    fun u16(b: ByteArray, off: Int): Int = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    fun put16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v ushr 8).toByte()
        b[off + 1] = v.toByte()
    }
}
