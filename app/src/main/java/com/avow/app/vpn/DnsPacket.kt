package com.avow.app.vpn

/**
 * Pure helpers for the local DNS-filtering VPN: parse the queried domain out of a DNS request,
 * craft a "sinkhole" response for blocked domains, and wrap a DNS payload back into an IPv4/UDP
 * packet with correct checksums. These are the trickiest, most bug-prone parts of the VPN (a bad
 * checksum breaks DNS for the whole device), so they live here as testable pure functions with no
 * Android dependencies. [DomainVpnService] provides the socket/tunnel plumbing around them.
 *
 * Byte layout reference:
 *  - IPv4 header: verIHL(1) tos(1) totalLen(2) id(2) flags/frag(2) ttl(1) proto(1) hdrCksum(2)
 *    srcIP(4) dstIP(4)
 *  - UDP header: srcPort(2) dstPort(2) length(2) checksum(2)
 *  - DNS message: id(2) flags(2) qd(2) an(2) ns(2) ar(2) then questions (QNAME labels, QTYPE(2),
 *    QCLASS(2)).
 */
object DnsPacket {

    const val PROTO_UDP = 17
    const val DNS_PORT = 53
    private const val DNS_TYPE_A = 1

    private fun u16(b: ByteArray, i: Int): Int = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    private fun putU16(b: ByteArray, i: Int, v: Int) {
        b[i] = ((v ushr 8) and 0xFF).toByte()
        b[i + 1] = (v and 0xFF).toByte()
    }

    fun ipv4HeaderLength(packet: ByteArray): Int = (packet[0].toInt() and 0x0F) * 4
    fun ipv4Protocol(packet: ByteArray): Int = packet[9].toInt() and 0xFF

    /** UDP destination port of an IPv4/UDP packet, or -1 if it isn't IPv4/UDP or is too short. */
    fun udpDestPort(packet: ByteArray): Int {
        if (packet.isEmpty() || (packet[0].toInt() and 0xF0) != 0x40) return -1
        if (ipv4Protocol(packet) != PROTO_UDP) return -1
        val ihl = ipv4HeaderLength(packet)
        if (packet.size < ihl + 8) return -1
        return u16(packet, ihl + 2)
    }

    /** Returns the DNS message payload (after IPv4+UDP headers) of a UDP packet. */
    fun dnsPayload(packet: ByteArray): ByteArray {
        val ihl = ipv4HeaderLength(packet)
        val start = ihl + 8
        return packet.copyOfRange(start, packet.size)
    }

    /**
     * Parses the domain name from the first question of a DNS message. Returns it lowercased with no
     * trailing dot, or null if the message has no question or is malformed.
     */
    fun parseQuestionDomain(dns: ByteArray): String? {
        if (dns.size < 12) return null
        val qdCount = u16(dns, 4)
        if (qdCount < 1) return null
        val sb = StringBuilder()
        var i = 12
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) break
            // Compression pointers shouldn't appear in a question QNAME; bail if seen.
            if ((len and 0xC0) != 0) return null
            i++
            if (i + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (j in 0 until len) sb.append((dns[i + j].toInt() and 0xFF).toChar())
            i += len
        }
        return if (sb.isEmpty()) null else sb.toString().lowercase()
    }

    /**
     * Builds a DNS response that sinkholes the query: for an A question it answers 0.0.0.0 (the
     * connection then fails); for any other type it returns NOERROR with no answers (so there's no
     * IPv6/other fallback). The response echoes the query's ID and question.
     */
    fun buildSinkholeResponse(query: ByteArray): ByteArray {
        // Question section spans from offset 12 to just past QTYPE/QCLASS.
        var i = 12
        while (i < query.size && (query[i].toInt() and 0xFF) != 0) {
            i += (query[i].toInt() and 0xFF) + 1
        }
        val questionEnd = i + 1 + 4 // null label + QTYPE(2) + QCLASS(2)
        if (questionEnd > query.size) return query // malformed; echo back untouched
        val qType = u16(query, i + 1)
        val isA = qType == DNS_TYPE_A

        val questionLen = questionEnd - 12
        val answerLen = if (isA) 16 else 0 // ptr(2)+type(2)+class(2)+ttl(4)+rdlen(2)+rdata(4)
        val out = ByteArray(12 + questionLen + answerLen)

        // Header: copy ID; flags = QR|RD|RA, RCODE 0; QD=1; AN=(1 if A else 0).
        out[0] = query[0]; out[1] = query[1]
        putU16(out, 2, 0x8180)
        putU16(out, 4, 1)
        putU16(out, 6, if (isA) 1 else 0)
        // NS/AR already 0.
        System.arraycopy(query, 12, out, 12, questionLen)

        if (isA) {
            var o = 12 + questionLen
            out[o] = 0xC0.toByte(); out[o + 1] = 0x0C // name pointer to offset 12
            putU16(out, o + 2, DNS_TYPE_A)
            putU16(out, o + 4, 1)            // class IN
            out[o + 6] = 0; out[o + 7] = 0; out[o + 8] = 0; out[o + 9] = 60 // TTL 60s
            putU16(out, o + 10, 4)           // RDLENGTH
            out[o + 12] = 0; out[o + 13] = 0; out[o + 14] = 0; out[o + 15] = 0 // 0.0.0.0
        }
        return out
    }

    /** Standard IPv4/ICMP-style ones-complement checksum over [len] bytes at [start]. */
    fun checksum(data: ByteArray, start: Int, len: Int): Int {
        var sum = 0L
        var i = start
        var remaining = len
        while (remaining > 1) {
            sum += (((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)).toLong()
            i += 2
            remaining -= 2
        }
        if (remaining == 1) sum += ((data[i].toInt() and 0xFF) shl 8).toLong()
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    /**
     * Wraps [dnsResponse] into an IPv4/UDP packet that is the reply to [request]: source and
     * destination IPs and ports are swapped from the request, and both checksums are recomputed.
     */
    fun buildUdpResponse(request: ByteArray, dnsResponse: ByteArray): ByteArray {
        val ihl = ipv4HeaderLength(request)
        val totalLen = ihl + 8 + dnsResponse.size
        val out = ByteArray(totalLen)

        // IPv4 header: copy the request's header, then fix length/addresses/checksum.
        System.arraycopy(request, 0, out, 0, ihl)
        putU16(out, 2, totalLen)
        out[4] = 0; out[5] = 0            // id
        out[6] = 0; out[7] = 0            // flags/frag
        out[8] = 64                       // ttl
        // Swap src/dst IP (each 4 bytes at 12 and 16).
        for (k in 0 until 4) {
            val s = request[12 + k]; val d = request[16 + k]
            out[12 + k] = d; out[16 + k] = s
        }
        // IP header checksum.
        putU16(out, 10, 0)
        putU16(out, 10, checksum(out, 0, ihl))

        // UDP header: swap ports, set length, checksum.
        val srcPort = u16(request, ihl); val dstPort = u16(request, ihl + 2)
        val udpStart = ihl
        putU16(out, udpStart, dstPort)         // new src port = old dst
        putU16(out, udpStart + 2, srcPort)     // new dst port = old src
        val udpLen = 8 + dnsResponse.size
        putU16(out, udpStart + 4, udpLen)
        putU16(out, udpStart + 6, 0)
        System.arraycopy(dnsResponse, 0, out, udpStart + 8, dnsResponse.size)

        // UDP checksum over pseudo-header + UDP header + data.
        val pseudo = ByteArray(12 + udpLen)
        System.arraycopy(out, 12, pseudo, 0, 4)  // src IP
        System.arraycopy(out, 16, pseudo, 4, 4)  // dst IP
        pseudo[8] = 0; pseudo[9] = PROTO_UDP.toByte()
        putU16(pseudo, 10, udpLen)
        System.arraycopy(out, udpStart, pseudo, 12, udpLen)
        var udpCk = checksum(pseudo, 0, pseudo.size)
        if (udpCk == 0) udpCk = 0xFFFF // 0 means "no checksum"; use all-ones instead
        putU16(out, udpStart + 6, udpCk)
        return out
    }
}
