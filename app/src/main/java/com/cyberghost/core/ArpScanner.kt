package com.cyberghost.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.File
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

class ArpScanner(private val context: Context) {

    private val _devices = MutableStateFlow<List<ArpDevice>>(emptyList())
    val devices: StateFlow<List<ArpDevice>> = _devices

    private val deviceMap = mutableMapOf<String, ArpDevice>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var running = false
    private var subnet = "192.168.43"

    fun start(subnet: String) {
        this.subnet = subnet
        running = true
        scope.launch {
            while (running) {
                try { scan() } catch (e: Exception) { Log.e("CG2", "Scan error", e) }
                delay(2000)
            }
        }
    }

    fun stop() { running = false; try { scope.cancel() } catch (_: Exception) {} }

    private suspend fun scan() = withContext(Dispatchers.IO) {
        val found = mutableSetOf<String>()

        // read /proc/net/arp
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val cols = line!!.trim().split("\\s+".toRegex())
                    if (cols.size >= 4) {
                        val ip = cols[0]; val mac = cols[3]
                        if (ip.startsWith(subnet) && mac != "00:00:00:00:00:00"
                            && mac.matches(Regex("[0-9a-fA-F:]{17}"))) {
                            found.add(ip); upsert(ip, mac)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // read dnsmasq leases
        listOf("/data/misc/dhcp/dnsmasq.leases",
               "/data/misc/dhcp/dnsmasq-subnet.conf",
               "/data/misc/wifi/hostapd.leases").forEach { path ->
            try {
                File(path).readLines().forEach { line ->
                    val cols = line.trim().split("\\s+".toRegex())
                    if (cols.size >= 3) {
                        val mac = cols[1]; val ip = cols[2]
                        if (ip.startsWith(subnet) && mac.matches(Regex("[0-9a-fA-F:]{17}"))) {
                            found.add(ip); upsert(ip, mac)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // parallel ping sweep — this is what actually works
        val jobs = (2..254).map { i ->
            async {
                val ip = "$subnet.$i"
                try {
                    if (InetAddress.getByName(ip).isReachable(200)) {
                        found.add(ip)
                        delay(30)
                        val mac = getMac(ip) ?: "unknown"
                        upsert(ip, mac)
                    }
                } catch (_: Exception) {}
            }
        }
        jobs.awaitAll()

        // mark missing devices
        deviceMap.keys.filter { it.startsWith(subnet) && it !in found }
            .forEach { deviceMap[it]?.isAlive = false }

        _devices.value = deviceMap.values
            .filter { it.ip.startsWith(subnet) }
            .sortedBy { it.ip.split(".").last().toIntOrNull() ?: 999 }

        if (_devices.value.isNotEmpty())
            Log.i("CG2", "Devices: ${_devices.value.map { it.ip }}")
    }

    private fun getMac(ip: String): String? = try {
        BufferedReader(FileReader("/proc/net/arp")).use { reader ->
            reader.readLine()
            var line: String?; var result: String? = null
            while (reader.readLine().also { line = it } != null) {
                val cols = line!!.trim().split("\\s+".toRegex())
                if (cols.size >= 4 && cols[0] == ip && cols[3] != "00:00:00:00:00:00") {
                    result = cols[3]; break
                }
            }
            result
        }
    } catch (_: Exception) { null }

    private fun upsert(ip: String, mac: String) {
        val existing = deviceMap[ip]
        if (existing != null) {
            existing.lastSeen = Instant.now(); existing.isAlive = true
            if (existing.mac == "unknown" && mac != "unknown")
                deviceMap[ip] = existing.copy(mac = mac, vendor = lookupVendor(mac))
        } else {
            val hostname = try {
                val name = InetAddress.getByName(ip).canonicalHostName
                if (name != ip) name else null
            } catch (_: Exception) { null }
            deviceMap[ip] = ArpDevice(ip = ip, mac = mac,
                vendor = lookupVendor(mac), hostname = hostname)
            Log.i("CG2", "New device: $ip [$mac] [${lookupVendor(mac)}]")
        }
    }

    fun updateTraffic(ip: String, ua: String?, bytes: Long) {
        deviceMap[ip]?.also { it.requestCount++; it.bytesTransferred += bytes; if (ua != null) it.userAgent = ua }
            ?: upsert(ip, getMac(ip) ?: "unknown")
        _devices.value = deviceMap.values.filter { it.ip.startsWith(subnet) }
            .sortedBy { it.ip.split(".").last().toIntOrNull() ?: 999 }
    }

    fun addManual(ip: String) {
        if (!deviceMap.containsKey(ip)) {
            upsert(ip, getMac(ip) ?: "unknown")
            _devices.value = deviceMap.values.filter { it.ip.startsWith(subnet) }
                .sortedBy { it.ip.split(".").last().toIntOrNull() ?: 999 }
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
        if (mac.length < 8) return "Unknown"
        val prefix = mac.uppercase().replace(":", "").take(6)
        return NetworkMapper.MAC_VENDORS[prefix] ?: "Unknown"
    }
}
