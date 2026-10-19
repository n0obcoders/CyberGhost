package com.cyberghost.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.Instant

data class DnsQuery(
    val timestamp: Instant,
    val sourceIp: String,
    val sourcePort: Int,
    val domain: String,
    val queryType: String,
    val resolvedIp: String?
)

class DnsCapture(
    private val listenPort: Int = 5353,
    private val upstreamDns: String = "8.8.8.8"
) {
    private val _queries = MutableSharedFlow<DnsQuery>(replay = 0, extraBufferCapacity = 512)
    val queries: SharedFlow<DnsQuery> = _queries
    private var socket: DatagramSocket? = null
    private var running = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        running = true
        socket = DatagramSocket(listenPort)
        scope.launch {
            val buf = ByteArray(512)
            while (running) {
                try {
                    val inPacket = DatagramPacket(buf, buf.size)
                    socket?.receive(inPacket)
                    launch { handleQuery(inPacket) }
                } catch (_: Exception) { if (!running) break }
            }
        }
    }

    fun stop() { running = false; socket?.close(); scope.cancel() }

    private suspend fun handleQuery(inPacket: DatagramPacket) {
        val data = inPacket.data.copyOf(inPacket.length)
        val domain = parseDomainName(data)
        val qType = parseQueryType(data)
        val upstream = DatagramSocket()
        upstream.soTimeout = 3000
        val fwdPacket = DatagramPacket(data, data.size, InetAddress.getByName(upstreamDns), 53)
        upstream.send(fwdPacket)
        val responseBuf = ByteArray(512)
        val responsePacket = DatagramPacket(responseBuf, responseBuf.size)
        var resolvedIp: String? = null
        try {
            upstream.receive(responsePacket)
            resolvedIp = parseResponseIp(responsePacket.data, responsePacket.length)
            val replyPacket = DatagramPacket(responsePacket.data, responsePacket.length, inPacket.address, inPacket.port)
            socket?.send(replyPacket)
        } catch (_: Exception) {} finally { upstream.close() }
        _queries.tryEmit(DnsQuery(Instant.now(), inPacket.address.hostAddress ?: "unknown", inPacket.port, domain ?: "unparseable", qType ?: "?", resolvedIp))
    }

    private fun parseDomainName(data: ByteArray): String? {
        if (data.size < 13) return null
        val sb = StringBuilder()
        var pos = 12
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) break
            if (sb.isNotEmpty()) sb.append('.')
            pos++
            for (i in 0 until len) { if (pos + i >= data.size) return sb.toString(); sb.append(data[pos + i].toInt().toChar()) }
            pos += len
        }
        return sb.toString()
    }

    private fun parseQueryType(data: ByteArray): String? {
        var pos = 12
        while (pos < data.size && data[pos].toInt() != 0) { pos += (data[pos].toInt() and 0xFF) + 1 }
        pos++
        if (pos + 2 > data.size) return null
        val typeCode = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        return when (typeCode) { 1 -> "A"; 28 -> "AAAA"; 5 -> "CNAME"; 15 -> "MX"; 2 -> "NS"; 12 -> "PTR"; 16 -> "TXT"; 6 -> "SOA"; 33 -> "SRV"; else -> "TYPE$typeCode" }
    }

    private fun parseResponseIp(data: ByteArray, length: Int): String? {
        var pos = 12
        while (pos < length && data[pos].toInt() != 0) { pos += (data[pos].toInt() and 0xFF) + 1 }
        pos += 5
        if (pos + 12 > length) return null
        pos += 2
        val aType = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 8
        val rdLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2
        if (aType == 1 && rdLen == 4 && pos + 4 <= length) {
            return "${data[pos].toInt() and 0xFF}.${data[pos+1].toInt() and 0xFF}.${data[pos+2].toInt() and 0xFF}.${data[pos+3].toInt() and 0xFF}"
        }
        return null
    }
}
