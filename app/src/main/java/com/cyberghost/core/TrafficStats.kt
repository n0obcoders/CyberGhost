package com.cyberghost.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class DomainStat(
    val domain: String,
    var requestCount: Long = 0,
    var bytesTotal: Long = 0,
    var lastSeen: Long = System.currentTimeMillis()
)

data class ProtocolBreakdown(
    var httpCount: Long = 0,
    var httpsCount: Long = 0,
    var dnsCount: Long = 0
)

data class BandwidthPoint(
    val timestamp: Long,
    val bytesPerSecond: Long
)

class TrafficStats {
    private val domainStats = ConcurrentHashMap<String, DomainStat>()
    private val deviceBytes = ConcurrentHashMap<String, AtomicLong>()
    val totalBytes = AtomicLong(0)
    val totalPackets = AtomicLong(0)
    val protocol = ProtocolBreakdown()

    private val _bandwidthHistory = MutableStateFlow<List<BandwidthPoint>>(emptyList())
    val bandwidthHistory: StateFlow<List<BandwidthPoint>> = _bandwidthHistory

    private var lastBwCheck = System.currentTimeMillis()
    private var lastBwBytes = 0L

    fun recordPacket(packet: InterceptedPacket) {
        totalPackets.incrementAndGet()
        val bytes = packet.body.size.toLong()
        totalBytes.addAndGet(bytes)

        when (packet.protocol) {
            "HTTP" -> protocol.httpCount++
            "HTTPS" -> protocol.httpsCount++
        }

        val stat = domainStats.getOrPut(packet.host) { DomainStat(packet.host) }
        stat.requestCount++
        stat.bytesTotal += bytes
        stat.lastSeen = System.currentTimeMillis()

        deviceBytes.getOrPut(packet.sourceIp) { AtomicLong(0) }.addAndGet(bytes)

        updateBandwidth()
    }

    fun recordDns() { protocol.dnsCount++ }

    private fun updateBandwidth() {
        val now = System.currentTimeMillis()
        if (now - lastBwCheck >= 1000) {
            val currentBytes = totalBytes.get()
            val bps = currentBytes - lastBwBytes
            lastBwBytes = currentBytes
            lastBwCheck = now

            val history = _bandwidthHistory.value.toMutableList()
            history.add(BandwidthPoint(now, bps))
            if (history.size > 60) history.removeAt(0) // keep 60 seconds
            _bandwidthHistory.value = history
        }
    }

    fun topDomains(limit: Int = 10): List<DomainStat> {
        return domainStats.values.sortedByDescending { it.requestCount }.take(limit)
    }

    fun deviceTraffic(): Map<String, Long> {
        return deviceBytes.mapValues { it.value.get() }
    }

    fun reset() {
        domainStats.clear(); deviceBytes.clear()
        totalBytes.set(0); totalPackets.set(0)
        protocol.httpCount = 0; protocol.httpsCount = 0; protocol.dnsCount = 0
        _bandwidthHistory.value = emptyList()
        lastBwBytes = 0
    }
}
