package com.cyberghost.core

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress
import java.net.NetworkInterface
import java.time.Instant

data class NetworkHost(
    val ip: String,
    val mac: String? = null,
    val vendor: String? = null,
    val hostname: String? = null,
    val isGateway: Boolean = false,
    val discoveredAt: Instant = Instant.now(),
    var isAlive: Boolean = true
)

class NetworkMapper {
    private val _hosts = MutableStateFlow<List<NetworkHost>>(emptyList())
    val hosts: StateFlow<List<NetworkHost>> = _hosts
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun scan(subnet: String = "192.168.43") {
        if (_scanning.value) return
        _scanning.value = true
        scope.launch {
            val found = mutableListOf<NetworkHost>()
            // gateway first
            found.add(NetworkHost(ip = "$subnet.1", isGateway = true, vendor = "Gateway (this device)"))

            val jobs = (2..254).map { i ->
                async {
                    val ip = "$subnet.$i"
                    try {
                        val addr = InetAddress.getByName(ip)
                        if (addr.isReachable(300)) {
                            val mac = readArpTable(ip)
                            val vendor = mac?.let { lookupVendor(it) }
                            val hostname = try { addr.canonicalHostName.takeIf { it != ip } } catch (_: Exception) { null }
                            NetworkHost(ip = ip, mac = mac, vendor = vendor, hostname = hostname)
                        } else null
                    } catch (_: Exception) { null }
                }
            }
            jobs.forEach { result ->
                result.await()?.let { found.add(it) }
            }
            _hosts.value = found.sortedBy { it.ip.split(".").last().toIntOrNull() ?: 0 }
            _scanning.value = false
            Log.i("CG2", "Network scan complete: ${found.size} hosts")
        }
    }

    private fun readArpTable(ip: String): String? {
        return try {
            val proc = Runtime.getRuntime().exec("cat /proc/net/arp")
            val lines = proc.inputStream.bufferedReader().readLines()
            proc.waitFor()
            lines.firstOrNull { it.startsWith(ip) || it.contains(" $ip ") }
                ?.split("\\s+".toRegex())?.getOrNull(3)
                ?.takeIf { it != "00:00:00:00:00:00" }
        } catch (_: Exception) { null }
    }

    private fun lookupVendor(mac: String): String {
        val prefix = mac.take(8).uppercase().replace(":", "")
        return MAC_VENDORS[prefix] ?: MAC_VENDORS[prefix.take(6)] ?: "Unknown"
    }

    companion object {
        // top ~50 MAC prefixes
        val MAC_VENDORS = mapOf(
            "00155D" to "Microsoft", "001A2B" to "Ayecom",
            "001E65" to "Intel", "002248" to "Microsoft",
            "0025AB" to "Amazon", "003EE1" to "Apple",
            "00505E" to "Cisco", "0050F2" to "Microsoft",
            "08002B" to "DEC", "080027" to "Oracle VBox",
            "0C771A" to "Samsung", "10683F" to "Samsung",
            "14109F" to "Apple", "18AF8F" to "Apple",
            "1C232C" to "Samsung", "1C9957" to "OnePlus",
            "244B03" to "Samsung", "28E347" to "Samsung",
            "2C3361" to "Apple", "2CF0A2" to "Samsung",
            "3013E0" to "Samsung", "3423BA" to "Samsung",
            "38F9D3" to "Apple", "3C0754" to "Apple",
            "403CFC" to "Apple", "44D884" to "Apple",
            "4C3275" to "Apple", "4C57CA" to "Apple",
            "502B73" to "Samsung", "5443B2" to "Apple",
            "5882A8" to "Microsoft", "58B035" to "Apple",
            "606D3C" to "Apple", "64A2F9" to "Apple",
            "6C709F" to "Samsung", "70700D" to "Apple",
            "7C5049" to "Apple", "7CC537" to "Apple",
            "842E27" to "Samsung", "848E0C" to "Apple",
            "88E87F" to "Apple", "8C7C92" to "Apple",
            "9027E4" to "Apple", "9803D8" to "Apple",
            "9CFC01" to "Samsung", "A00798" to "Samsung",
            "A45E60" to "Apple", "A4B197" to "Apple",
            "A860B6" to "Apple", "AC3743" to "HTC",
            "B0BE76" to "Apple", "B4F0AB" to "Apple",
            "B844D9" to "Apple", "BC3AEA" to "Apple",
            "BC6778" to "Apple", "C06394" to "Apple",
            "C82A14" to "Apple", "CC08E0" to "Apple",
            "D023DB" to "Apple", "D03311" to "Apple",
            "D0C5F3" to "Apple", "D4619D" to "Samsung",
            "D8BB2C" to "Apple", "DC0C5C" to "Apple",
            "DCA904" to "Apple", "E0B52D" to "Apple",
            "E49ADC" to "Xiaomi", "E4E749" to "Samsung",
            "E80688" to "Apple", "EC3586" to "Apple",
            "F02475" to "Apple", "F0B479" to "Apple",
            "F40F24" to "Apple", "F4F951" to "Apple",
            "F8E079" to "Motorola", "FC253F" to "Apple",
            "FCFC48" to "Apple", "D8A25E" to "Samsung",
            "B0A737" to "Roku", "00E04C" to "Realtek",
            "DC4427" to "Xiaomi", "34CE00" to "Xiaomi",
            "7C8BB5" to "Roku", "B827EB" to "Raspberry Pi",
            "DCA632" to "Raspberry Pi", "E45F01" to "Raspberry Pi"
        )
    }
}
