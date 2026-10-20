package com.cyberghost.core

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
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
    private val listenPort: Int = 53,
    private val upstreamDns: String = "8.8.8.8",
    var gatewayIp: String = "192.168.43.1",
    var transparentMode: Boolean = true
) {
    private val _queries = MutableSharedFlow<DnsQuery>(replay = 0, extraBufferCapacity = 512)
    val queries: SharedFlow<DnsQuery> = _queries
    private var socket: DatagramSocket? = null
    private var running = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val bypassDomains = setOf(
        "connectivitycheck.gstatic.com", "clients3.google.com",
        "play.googleapis.com", "android.clients.google.com",
        "mtalk.google.com", "captive.apple.com",
        "www.apple.com", "captivedetect.apple.com",
        "time.android.com", "ntp.ubuntu.com"
    )

    fun start() {
        running = true
        // try port 53 first, fall back to 5353
        for (port in listOf(listenPort, 5353, 15353)) {
            try {
                socket = DatagramSocket(port)
                Log.i("CG2", "DNS server on port $port, gateway=$gatewayIp")
                break
            } catch (e: Exception) {
                Log.w("CG2", "DNS: port $port failed: ${e.message}")
            }
        }
        if (socket == null) {
            Log.e("CG2", "DNS: all ports failed")
            running = false
            return
        }
        scope.launch {
            val buf = ByteArray(512)
            while (running) {
                try {
                    val inPacket = DatagramPacket(buf.clone(), buf.size)
                    socket?.receive(inPacket)
                    launch { handleQuery(inPacket) }
                } catch (_: Exception) { if (!running) break }
            }
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        scope.cancel()
    }

    private suspend fun handleQuery(inPacket: DatagramPacket) {
        try {
            val data = inPacket.data.copyOf(inPacket.length)
            val domain = parseDomainName(data) ?: "unparseable"
            val qType = parseQueryType(data) ?: "?"

            if (transparentMode && qType == "A" && !shouldBypass(domain)) {
                val spoofed = buildSpoofedResponse(data, inPacket.length, gatewayIp)
                socket?.send(DatagramPacket(spoofed, spoofed.size, inPacket.address, inPacket.port))
                _queries.tryEmit(DnsQuery(Instant.now(), inPacket.address.hostAddress ?: "?",
                    inPacket.port, domain, qType, "$gatewayIp [spoofed]"))
            } else {
                val upstream = DatagramSocket()
                upstream.soTimeout = 3000
                upstream.send(DatagramPacket(data, data.size, InetAddress.getByName(upstreamDns), 53))
                val responseBuf = ByteArray(512)
                val responsePacket = DatagramPacket(responseBuf, responseBuf.size)
                var resolvedIp: String? = null
                try {
                    upstream.receive(responsePacket)
                    resolvedIp = parseResponseIp(responsePacket.data, responsePacket.length)
                    socket?.send(DatagramPacket(responsePacket.data, responsePacket.length, inPacket.address, inPacket.port))
                } catch (_: Exception) {} finally { upstream.close() }
                _queries.tryEmit(DnsQuery(Instant.now(), inPacket.address.hostAddress ?: "?",
                    inPacket.port, domain, qType, resolvedIp))
            }
        } catch (e: Exception) {
            Log.e("CG2", "DNS query error", e)
        }
    }

    private fun shouldBypass(domain: String) = bypassDomains.any {
        domain.equals(it, ignoreCase = true) || domain.endsWith(".$it", ignoreCase = true)
    }

    private fun buildSpoofedResponse(query: ByteArray, queryLen: Int, ip: String): ByteArray {
        val buf = ByteBuffer.allocate(queryLen + 16)
        buf.put(query, 0, 2)
        buf.putShort(0x8180.toShort())
        buf.putShort(1); buf.putShort(1); buf.putShort(0); buf.putShort(0)
        var pos = 12
        while (pos < queryLen && query[pos].toInt() != 0) pos += (query[pos].toInt() and 0xFF) + 1
        pos += 5
        buf.put(query, 12, pos - 12)
        buf.putShort(0xC00C.toInt().toShort())
        buf.putShort(1); buf.putShort(1); buf.putInt(60); buf.putShort(4)
        ip.split(".").forEach { buf.put((it.toInt() and 0xFF).toByte()) }
        val result = ByteArray(buf.position()); buf.flip(); buf.get(result)
        return result
    }

    private fun parseDomainName(data: ByteArray): String? {
        if (data.size < 13) return null
        val sb = StringBuilder(); var pos = 12
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF; if (len == 0) break
            if (sb.isNotEmpty()) sb.append('.')
            pos++
            for (i in 0 until len) {
                if (pos + i >= data.size) return sb.toString()
                sb.append(data[pos + i].toInt().toChar())
            }
            pos += len
        }
        return sb.toString()
    }

    private fun parseQueryType(data: ByteArray): String? {
        var pos = 12
        while (pos < data.size && data[pos].toInt() != 0) pos += (data[pos].toInt() and 0xFF) + 1
        pos++
        if (pos + 2 > data.size) return null
        val tc = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        return when (tc) { 1->"A"; 28->"AAAA"; 5->"CNAME"; 15->"MX"; 2->"NS"; 12->"PTR"; 16->"TXT"; else->"TYPE$tc" }
    }

    private fun parseResponseIp(data: ByteArray, length: Int): String? {
        var pos = 12
        while (pos < length && data[pos].toInt() != 0) pos += (data[pos].toInt() and 0xFF) + 1
        pos += 5; if (pos + 12 > length) return null; pos += 2
        val aType = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF); pos += 8
        val rdLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF); pos += 2
        if (aType == 1 && rdLen == 4 && pos + 4 <= length)
            return "${data[pos].toInt() and 0xFF}.${data[pos+1].toInt() and 0xFF}.${data[pos+2].toInt() and 0xFF}.${data[pos+3].toInt() and 0xFF}"
        return null
    }
}
