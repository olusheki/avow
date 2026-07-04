package com.avow.app

import com.avow.app.vpn.DnsPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketTest {

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    /** Builds a DNS query message for [domain] with the given qtype (1=A, 28=AAAA). */
    private fun dnsQuery(domain: String, qType: Int = 1, id: Int = 0x1234): ByteArray {
        val labels = domain.split(".")
        val qname = ArrayList<Byte>()
        for (label in labels) {
            qname.add(label.length.toByte())
            for (c in label) qname.add(c.code.toByte())
        }
        qname.add(0)
        val msg = ArrayList<Byte>()
        msg.add(((id ushr 8) and 0xFF).toByte()); msg.add((id and 0xFF).toByte()) // id
        msg.add(0x01); msg.add(0x00)   // flags: RD
        msg.add(0x00); msg.add(0x01)   // QDCOUNT 1
        msg.add(0x00); msg.add(0x00)   // ANCOUNT
        msg.add(0x00); msg.add(0x00)   // NSCOUNT
        msg.add(0x00); msg.add(0x00)   // ARCOUNT
        msg.addAll(qname)
        msg.add(((qType ushr 8) and 0xFF).toByte()); msg.add((qType and 0xFF).toByte()) // QTYPE
        msg.add(0x00); msg.add(0x01)   // QCLASS IN
        return msg.toByteArray()
    }

    /** Wraps a DNS payload in an IPv4/UDP packet (src 10.0.0.1:33333 → dst 10.111.222.2:53). */
    private fun ipv4Udp(dns: ByteArray): ByteArray {
        val ihl = 20
        val udpLen = 8 + dns.size
        val total = ihl + udpLen
        val p = ByteArray(total)
        p[0] = 0x45; p[9] = DnsPacket.PROTO_UDP.toByte()
        p[2] = ((total ushr 8) and 0xFF).toByte(); p[3] = (total and 0xFF).toByte()
        // src 10.0.0.1
        p[12] = 10; p[13] = 0; p[14] = 0; p[15] = 1
        // dst 10.111.222.2
        p[16] = 10; p[17] = 111.toByte(); p[18] = 222.toByte(); p[19] = 2
        // UDP: src 33333, dst 53
        p[ihl] = ((33333 ushr 8) and 0xFF).toByte(); p[ihl + 1] = (33333 and 0xFF).toByte()
        p[ihl + 2] = 0; p[ihl + 3] = 53
        p[ihl + 4] = ((udpLen ushr 8) and 0xFF).toByte(); p[ihl + 5] = (udpLen and 0xFF).toByte()
        System.arraycopy(dns, 0, p, ihl + 8, dns.size)
        return p
    }

    @Test
    fun parsesDestPortAndDomain() {
        val packet = ipv4Udp(dnsQuery("instagram.com"))
        assertEquals(53, DnsPacket.udpDestPort(packet))
        assertEquals("instagram.com", DnsPacket.parseQuestionDomain(DnsPacket.dnsPayload(packet)))
    }

    @Test
    fun parsesSubdomain() {
        val packet = ipv4Udp(dnsQuery("www.Instagram.com"))
        assertEquals("www.instagram.com", DnsPacket.parseQuestionDomain(DnsPacket.dnsPayload(packet)))
    }

    @Test
    fun malformedOrEmptyReturnsNull() {
        assertNull(DnsPacket.parseQuestionDomain(ByteArray(5)))
    }

    @Test
    fun sinkholeResponseForAQueryAnswersZeroIp() {
        val query = dnsQuery("instagram.com", qType = 1, id = 0xABCD)
        val resp = DnsPacket.buildSinkholeResponse(query)
        assertEquals(0xABCD, u16(resp, 0))              // echoes the ID
        assertEquals(0x8180, u16(resp, 2))              // QR|RD|RA, RCODE 0
        assertEquals(1, u16(resp, 4))                   // QDCOUNT
        assertEquals(1, u16(resp, 6))                   // ANCOUNT
        // Last 4 bytes are the A RDATA = 0.0.0.0
        assertEquals(0, resp[resp.size - 1].toInt())
        assertEquals(0, resp[resp.size - 4].toInt())
    }

    @Test
    fun sinkholeResponseForAaaaHasNoAnswer() {
        val resp = DnsPacket.buildSinkholeResponse(dnsQuery("instagram.com", qType = 28))
        assertEquals(0, u16(resp, 6)) // ANCOUNT 0 (NODATA — no IPv6 fallback)
    }

    @Test
    fun buildUdpResponseSwapsEndpointsAndChecksumsAreValid() {
        val request = ipv4Udp(dnsQuery("instagram.com"))
        val dnsResp = DnsPacket.buildSinkholeResponse(DnsPacket.dnsPayload(request))
        val out = DnsPacket.buildUdpResponse(request, dnsResp)

        val ihl = DnsPacket.ipv4HeaderLength(out)
        // A valid IP header checksums to zero when recomputed over the whole header.
        assertEquals(0, DnsPacket.checksum(out, 0, ihl))

        // Source/dest IPs swapped vs the request.
        for (k in 0 until 4) {
            assertEquals(request[12 + k], out[16 + k]) // req src == resp dst
            assertEquals(request[16 + k], out[12 + k]) // req dst == resp src
        }
        // Ports swapped: response src port 53, dst port 33333.
        assertEquals(53, u16(out, ihl))
        assertEquals(33333, u16(out, ihl + 2))

        // Verify UDP checksum: rebuild pseudo-header and confirm it checksums to zero.
        val udpLen = u16(out, ihl + 4)
        val pseudo = ByteArray(12 + udpLen)
        System.arraycopy(out, 12, pseudo, 0, 4)
        System.arraycopy(out, 16, pseudo, 4, 4)
        pseudo[9] = DnsPacket.PROTO_UDP.toByte()
        pseudo[10] = ((udpLen ushr 8) and 0xFF).toByte(); pseudo[11] = (udpLen and 0xFF).toByte()
        System.arraycopy(out, ihl, pseudo, 12, udpLen)
        assertEquals(0, DnsPacket.checksum(pseudo, 0, pseudo.size))
    }

    @Test
    fun ignoresNonDnsPacket() {
        val packet = ipv4Udp(dnsQuery("instagram.com"))
        // Change UDP dest port to 443 → not DNS.
        val ihl = 20
        packet[ihl + 2] = ((443 ushr 8) and 0xFF).toByte(); packet[ihl + 3] = (443 and 0xFF).toByte()
        assertTrue(DnsPacket.udpDestPort(packet) != 53)
    }
}
