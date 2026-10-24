package com.owari.launcher.adblock

/**
 * Una consulta DNS que llegó por la VPN (paquete IPv4 + UDP al puerto 53).
 * Sabe armar el paquete de respuesta de vuelta hacia la app que preguntó.
 */
class DnsPacket private constructor(
    private val srcIp: ByteArray,
    private val dstIp: ByteArray,
    private val srcPort: Int,
    private val dstPort: Int,
    /** Solo la parte DNS, sin cabeceras IP/UDP. */
    val dns: ByteArray,
    /** Dominio consultado, por ejemplo "ads.ejemplo.com". */
    val name: String,
) {
    /** Envuelve una respuesta DNS en IPv4 + UDP, con origen y destino invertidos. */
    fun reply(answer: ByteArray): ByteArray {
        val udpLength = 8 + answer.size
        val total = 20 + udpLength
        val p = ByteArray(total)
        p[0] = 0x45 // IPv4, cabecera de 20 bytes
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[6] = 0x40 // no fragmentar
        p[8] = 64 // TTL
        p[9] = 17 // UDP
        System.arraycopy(dstIp, 0, p, 12, 4)
        System.arraycopy(srcIp, 0, p, 16, 4)
        val checksum = ipChecksum(p, 20)
        p[10] = (checksum shr 8).toByte(); p[11] = checksum.toByte()
        p[20] = (dstPort shr 8).toByte(); p[21] = dstPort.toByte()
        p[22] = (srcPort shr 8).toByte(); p[23] = srcPort.toByte()
        p[24] = (udpLength shr 8).toByte(); p[25] = udpLength.toByte()
        // Suma de verificación UDP en 0: permitido en IPv4.
        System.arraycopy(answer, 0, p, 28, answer.size)
        return p
    }

    companion object {
        fun parse(buffer: ByteArray, length: Int): DnsPacket? {
            if (length < 28 || (buffer[0].toInt() shr 4) != 4) return null
            val ihl = (buffer[0].toInt() and 0x0F) * 4
            if (buffer[9].toInt() != 17 || length < ihl + 8 + 12) return null
            val srcPort = u16(buffer, ihl)
            val dstPort = u16(buffer, ihl + 2)
            if (dstPort != 53) return null
            val dnsStart = ihl + 8
            val dns = buffer.copyOfRange(dnsStart, length)
            val name = readName(dns) ?: return null
            return DnsPacket(
                srcIp = buffer.copyOfRange(12, 16),
                dstIp = buffer.copyOfRange(16, 20),
                srcPort = srcPort,
                dstPort = dstPort,
                dns = dns,
                name = name,
            )
        }

        /** Respuesta "ese dominio no existe" (NXDOMAIN) para la misma pregunta. */
        fun blockedAnswer(query: ByteArray): ByteArray {
            val answer = query.copyOf()
            answer[2] = (0x80 or (query[2].toInt() and 0x01)).toByte() // es respuesta, conserva RD
            answer[3] = 0x83.toByte() // recursión disponible, código 3 = no existe
            // Sin respuestas ni registros extra; solo se conserva la pregunta.
            for (i in 6..11) answer[i] = 0
            return answer.copyOf(questionEnd(query))
        }

        private fun readName(dns: ByteArray): String? {
            if (u16(dns, 4) < 1) return null
            val labels = mutableListOf<String>()
            var i = 12
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF
                if (len == 0) return labels.joinToString(".")
                if (len > 63 || i + 1 + len > dns.size) return null
                labels += String(dns, i + 1, len, Charsets.US_ASCII)
                i += 1 + len
            }
            return null
        }

        /** Dónde termina la primera pregunta (nombre + tipo + clase). */
        private fun questionEnd(dns: ByteArray): Int {
            var i = 12
            while (i < dns.size && dns[i].toInt() != 0) i += 1 + (dns[i].toInt() and 0xFF)
            return minOf(i + 5, dns.size)
        }

        private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

        private fun ipChecksum(p: ByteArray, headerLength: Int): Int {
            var sum = 0
            var i = 0
            while (i < headerLength) {
                sum += u16(p, i)
                i += 2
            }
            while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
            return sum.inv() and 0xFFFF
        }
    }
}
