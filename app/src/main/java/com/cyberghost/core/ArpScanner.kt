package com.cyberghost.core

import android.content.Context
import android.net.wifi.WifiManager
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
                try {
                    discoverDevices()
                } catch (e: Exception) {
                    Log.e("CG2", "Device discovery error", e)
                }
                delay(2000)
            }
        }
        Log.i("CG2", "ArpScanner started on subnet $subnet")
    }

    fun stop() {
        running = false
        try { scope.cancel() } catch (_: Exception) {}
    }

    private suspend fun discoverDevices() = withContext(Dispatchers.IO) {
        val found = mutableSetOf<String>()

        // METHOD 1: read /proc/net/arp — catches anything that sent a packet
        readProcArp(found)

        // METHOD 2: scan /proc/net/arp entries from hostapd/dnsmasq leases
        readDhcpLeases(found)

        // METHOD 3: ping sweep in parallel to trigger ARP entries
        pingSweep(found)

        // refresh _devices
        _devices.value = deviceMap.values
            .filter { it.ip.startsWith(subnet) }
            .sortedBy { it.ip.split(".").last().toIntOrNull() ?: 999 }

        Log.d("CG2", "Devices found: ${_devices.value.size}")
    }

    private fun readProcArp(found: MutableSet<String>) {
        try {
            val reader = BufferedReader(FileReader("/proc/net/arp"))
            reader.readLine() // skip header
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val cols = line!!.trim().split("\\s+".toRegex())
                if (cols.size >= 4) {
                    val ip = cols[0]
                    val mac = cols[3]
                    if (ip.startsWith(subnet) &&
                        mac != "00:00:00:00:00:00" &&
                        mac.matches(Regex("[0-9a-fA-F:]{17}"))) {
                        found.add(ip)
                        upsertDevice(ip, mac, "arp")
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.d("CG2", "proc/net/arp: ${e.message}")
        }
    }

    private fun readDhcpLeases(found: MutableSet<String>) {
        // dnsmasq lease files — common locations on Android
        val leasePaths = listOf(
            "/data/misc/dhcp/dnsmasq.leases",
            "/data/misc/dhcp/dnsmasq-subnet.conf",
            "/data/misc/wifi/hostapd.leases",
            "/proc/net/wireless"
        )
        for (path in leasePaths) {
            try {
                val file = File(path)
                if (!file.exists()) continue
                file.readLines().forEach { line ->
                    // dnsmasq lease format: timestamp mac ip hostname clientid
                    val cols = line.trim().split("\\s+".toRegex())
                    if (cols.size >= 3) {
                        val mac = cols[1]
                        val ip = cols[2]
                        if (ip.startsWith(subnet) && mac.matches(Regex("[0-9a-fA-F:]{17}"))) {
                            found.add(ip)
                            upsertDevice(ip, mac, "dhcp")
                            Log.i("CG2", "DHCP lease: $ip $mac")
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun pingSweep(found: MutableSet<String>) {
        // only ping IPs we haven't seen yet, in parallel
        val unseenRange = (1..254).filter { i ->
            val ip = "$subnet.$i"
            ip !in found && ip != subnet + ".1" // skip gateway itself
        }.take(50) // limit to 50 at a time to avoid flooding

        val jobs = unseenRange.map { i ->
            scope.async {
                val ip = "$subnet.$i"
                try {
                    val addr = InetAddress.getByName(ip)
                    if (addr.isReachable(150)) {
                        found.add(ip)
                        // give ARP table time to populate then re-read
                        delay(50)
                        val mac = getMacFromArp(ip) ?: "unknown"
                        upsertDevice(ip, mac, "ping")
                        Log.i("CG2", "Ping found: $ip mac=$mac")
                    }
                } catch (_: Exception) {}
            }
        }
        jobs.forEach { try { it.await() } catch (_: Exception) {} }
    }

    private fun getMacFromArp(ip: String): String? {
        return try {
            val reader = BufferedReader(FileReader("/proc/net/arp"))
            reader.readLine()
            var line: String?
            var result: String? = null
            while (reader.readLine().also { line = it } != null) {
                val cols = line!!.trim().split("\\s+".toRegex())
                if (cols.size >= 4 && cols[0] == ip && cols[3] != "00:00:00:00:00:00") {
                    result = cols[3]; break
                }
            }
            reader.close()
            result
        } catch (_: Exception) { null }
    }

    private fun upsertDevice(ip: String, mac: String, source: String) {
        val vendor = lookupVendor(mac)
        val existing = deviceMap[ip]
        if (existing != null) {
            existing.lastSeen = Instant.now()
            existing.isAlive = true
            // update MAC if we now have a real one
            if (existing.mac == "unknown" && mac != "unknown") {
                deviceMap[ip] = existing.copy(mac = mac)
            }
        } else {
            val hostname = try {
                val addr = InetAddress.getByName(ip)
                val name = addr.canonicalHostName
                if (name != ip) name else null
            } catch (_: Exception) { null }

            deviceMap[ip] = ArpDevice(
                ip = ip, mac = mac, vendor = vendor, hostname = hostname
            )
            Log.i("CG2", "New device [$source]: $ip ($mac) [$vendor]")
        }
    }

    fun updateTraffic(ip: String, userAgent: String?, bytes: Long) {
        deviceMap[ip]?.also {
            it.requestCount++
            it.bytesTransferred += bytes
            if (userAgent != null) it.userAgent = userAgent
            it.isAlive = true
        } ?: run {
            // device sent traffic but wasn't in ARP yet — add it
            upsertDevice(ip, getMacFromArp(ip) ?: "unknown", "traffic")
            deviceMap[ip]?.also {
                it.requestCount = 1
                it.bytesTransferred = bytes
                if (userAgent != null) it.userAgent = userAgent
            }
        }
        _devices.value = deviceMap.values.filter { it.ip.startsWith(subnet) }
            .sortedBy { it.ip.split(".").last().toIntOrNull() ?: 999 }
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
        if (mac == "unknown" || mac.length < 8) return "Unknown"
        val prefix = mac.uppercase().replace(":", "").take(6)
        return NetworkMapper.MAC_VENDORS[prefix] ?: "Unknown"
    }
}
