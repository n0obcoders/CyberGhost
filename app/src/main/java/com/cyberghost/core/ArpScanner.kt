package com.cyberghost.core

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.time.Instant

data class ArpDevice(
    val ip: String,
    val mac: String,
    val vendor: String,
    val hostname: String?,
    var nickname: String? = null,
    val firstSeen: Instant = Instant.now(),
    var lastSeen: Instant = Instant.now(),
    var isAlive: Boolean = true,
    var requestCount: Long = 0,
    var bytesTransferred: Long = 0,
    var userAgent: String? = null,
    val hookedSessions: MutableList<String> = mutableListOf()
) {
    val isHooked: Boolean get() = hookedSessions.isNotEmpty()
    val displayName: String get() = nickname ?: hostname ?: ip
}

class ArpScanner {
    private val _devices = MutableStateFlow<List<ArpDevice>>(emptyList())
    val devices: StateFlow<List<ArpDevice>> = _devices

    private val deviceMap = mutableMapOf<String, ArpDevice>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var running = false

    fun start(subnet: String) {
        running = true
        scope.launch {
            while (running) {
                try {
                    scanArpTable(subnet)
                } catch (e: Exception) {
                    Log.e("CG2", "ARP scan error", e)
                }
                delay(3000) // scan every 3 seconds
            }
        }
        Log.i("CG2", "ARP scanner started on subnet $subnet")
    }

    fun stop() {
        running = false
        scope.cancel()
    }

    private suspend fun scanArpTable(subnet: String) = withContext(Dispatchers.IO) {
        val found = mutableSetOf<String>()

        // method 1: read /proc/net/arp directly — most reliable
        try {
            val reader = BufferedReader(FileReader("/proc/net/arp"))
            reader.readLine() // skip header
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.trim().split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val ip = parts[0]
                    val mac = parts[3]
                    val flags = parts[2]
                    // flags 0x2 = complete entry (device is reachable)
                    if (ip.startsWith(subnet) && mac != "00:00:00:00:00:00" && flags != "0x0") {
                        found.add(ip)
                        upsertDevice(ip, mac)
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("CG2", "ARP table read failed", e)
        }

        // method 2: ping sweep to trigger ARP entries for devices that haven't talked yet
        val pingJobs = (1..254).map { i ->
            async {
                val ip = "$subnet.$i"
                if (ip !in found) {
                    try {
                        val addr = InetAddress.getByName(ip)
                        if (addr.isReachable(200)) {
                            found.add(ip)
                            // re-read ARP after ping to get MAC
                            readArpForIp(ip)?.let { mac -> upsertDevice(ip, mac) }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        pingJobs.forEach { it.await() }

        // mark devices no longer in ARP as potentially gone
        deviceMap.keys.filter { it !in found && it.startsWith(subnet) }.forEach { ip ->
            deviceMap[ip]?.isAlive = false
        }

        _devices.value = deviceMap.values.filter { it.ip.startsWith(subnet) }
            .sortedBy { it.ip.split(".").last().toIntOrNull() ?: 999 }
    }

    private fun readArpForIp(ip: String): String? {
        return try {
            val reader = BufferedReader(FileReader("/proc/net/arp"))
            reader.readLine()
            var line: String?
            var result: String? = null
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.trim().split("\\s+".toRegex())
                if (parts.size >= 4 && parts[0] == ip && parts[3] != "00:00:00:00:00:00") {
                    result = parts[3]; break
                }
            }
            reader.close()
            result
        } catch (_: Exception) { null }
    }

    private fun upsertDevice(ip: String, mac: String) {
        val vendor = lookupVendor(mac)
        val existing = deviceMap[ip]
        if (existing != null) {
            existing.lastSeen = Instant.now()
            existing.isAlive = true
        } else {
            val hostname = try {
                val addr = InetAddress.getByName(ip)
                val name = addr.canonicalHostName
                if (name != ip) name else null
            } catch (_: Exception) { null }

            deviceMap[ip] = ArpDevice(ip = ip, mac = mac, vendor = vendor, hostname = hostname)
            Log.i("CG2", "New device: $ip ($mac) [$vendor]")
        }
    }

    fun updateTraffic(ip: String, userAgent: String?, bytes: Long) {
        deviceMap[ip]?.let {
            it.requestCount++
            it.bytesTransferred += bytes
            if (userAgent != null) it.userAgent = userAgent
            it.isAlive = true
            _devices.value = deviceMap.values.toList()
        }
    }

    fun setNickname(ip: String, name: String) {
        deviceMap[ip]?.nickname = name
        _devices.value = deviceMap.values.toList()
    }

    fun getDevice(ip: String): ArpDevice? = deviceMap[ip]

    fun markHooked(ip: String, sessionId: String) {
        deviceMap[ip]?.let {
            if (sessionId !in it.hookedSessions) it.hookedSessions.add(sessionId)
            _devices.value = deviceMap.values.toList()
        }
    }

    private fun lookupVendor(mac: String): String {
        val prefix = mac.uppercase().replace(":", "").take(6)
        return NetworkMapper.MAC_VENDORS[prefix] ?: NetworkMapper.MAC_VENDORS[prefix.take(6)] ?: "Unknown"
    }
}
