package com.cj.tapblok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DnsPacketTest {
    @Test
    fun parsesUdpDnsQuestionDomain() {
        val packet = dnsQueryPacket("www.youtube.com")

        val query = DnsPacket.parseUdpDnsQuery(packet, packet.size)

        assertEquals("www.youtube.com", query?.questionDomain)
        assertEquals(53, query?.destinationPort)
    }

    @Test
    fun blockedResponseSwapsUdpPortsAndReturnsNxDomain() {
        val packet = dnsQueryPacket("www.youtube.com")

        val response = DnsPacket.buildBlockedResponsePacket(packet, packet.size)

        assertNotNull(response)
        response!!
        assertEquals(53, u16(response, 20))
        assertEquals(53000, u16(response, 22))
        assertEquals(0x81, u8(response, 30))
        assertEquals(0x83, u8(response, 31))
    }

    @Test
    fun parsesAndRespondsToIpv6UdpDnsQuestionDomain() {
        val packet = dnsQueryPacketV6("www.youtube.com")

        val query = DnsPacket.parseUdpDnsQueryV6(packet, packet.size)
        val response = DnsPacket.buildBlockedResponsePacketV6(packet, packet.size)

        assertEquals("www.youtube.com", query?.questionDomain)
        assertNotNull(response)
        response!!
        assertEquals(53, u16(response, 40))
        assertEquals(53000, u16(response, 42))
        assertEquals(0x81, u8(response, 50))
        assertEquals(0x83, u8(response, 51))
    }

    @Test
    fun tcpDnsPacketBuildsResetResponse() {
        val packet = tcpSynPacket(destinationPort = 53)

        val response = DnsPacket.buildTcpDnsResetPacket(packet, packet.size)

        assertNotNull(response)
        response!!
        assertEquals(53, u16(response, 20))
        assertEquals(53000, u16(response, 22))
        assertEquals(0x14, u8(response, 33))
    }

    private fun dnsQueryPacket(domain: String): ByteArray {
        val dnsPayload = dnsQueryPayload(domain)
        val totalLength = 20 + 8 + dnsPayload.size
        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        put16(packet, 2, totalLength)
        packet[8] = 64
        packet[9] = 17
        put32(packet, 12, 0xc000020a.toInt())
        put32(packet, 16, 0x0a6f0001)
        put16(packet, 20, 53000)
        put16(packet, 22, 53)
        put16(packet, 24, 8 + dnsPayload.size)
        dnsPayload.copyInto(packet, 28)
        return packet
    }

    private fun dnsQueryPayload(domain: String): ByteArray {
        val labels = domain.split(".")
        val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
        val payload = ByteArray(size)
        put16(payload, 0, 0x1234)
        put16(payload, 2, 0x0100)
        put16(payload, 4, 1)
        var cursor = 12
        labels.forEach { label ->
            payload[cursor++] = label.length.toByte()
            label.encodeToByteArray().copyInto(payload, cursor)
            cursor += label.length
        }
        payload[cursor++] = 0
        put16(payload, cursor, 1)
        put16(payload, cursor + 2, 1)
        return payload
    }

    private fun dnsQueryPacketV6(domain: String): ByteArray {
        val dnsPayload = dnsQueryPayload(domain)
        val udpLength = 8 + dnsPayload.size
        val packet = ByteArray(40 + udpLength)
        packet[0] = 0x60
        put16(packet, 4, udpLength)
        packet[6] = 17
        packet[7] = 64
        ipv6Address(0xfd, 0, 0, 0, 0, 0, 0, 2).copyInto(packet, 8)
        ipv6Address(0xfd, 0, 0, 0, 0, 0, 0, 1).copyInto(packet, 24)
        put16(packet, 40, 53000)
        put16(packet, 42, 53)
        put16(packet, 44, udpLength)
        dnsPayload.copyInto(packet, 48)
        return packet
    }

    private fun ipv6Address(vararg groups: Int): ByteArray {
        val address = ByteArray(16)
        groups.forEachIndexed { index, group ->
            put16(address, index * 2, group)
        }
        return address
    }

    private fun tcpSynPacket(destinationPort: Int): ByteArray {
        val totalLength = 20 + 20
        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        put16(packet, 2, totalLength)
        packet[8] = 64
        packet[9] = 6
        put32(packet, 12, 0xc000020a.toInt())
        put32(packet, 16, 0x0a6f0001)
        put16(packet, 20, 53000)
        put16(packet, 22, destinationPort)
        put32(packet, 24, 100)
        packet[32] = 0x50
        packet[33] = 0x02
        return packet
    }

    private fun u8(buffer: ByteArray, offset: Int): Int = buffer[offset].toInt() and 0xff

    private fun u16(buffer: ByteArray, offset: Int): Int =
        (u8(buffer, offset) shl 8) or u8(buffer, offset + 1)

    private fun put16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value ushr 8).toByte()
        buffer[offset + 1] = value.toByte()
    }

    private fun put32(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value ushr 24).toByte()
        buffer[offset + 1] = (value ushr 16).toByte()
        buffer[offset + 2] = (value ushr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }
}
