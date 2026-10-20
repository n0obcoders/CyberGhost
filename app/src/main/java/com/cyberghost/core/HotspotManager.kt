package com.cyberghost.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.NetworkInterface

object HotspotManager {

    fun getHotspotIp(context: Context): String {
        // method 1: scan network interfaces for hotspot IP
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            val candidates = mutableListOf<String>()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                // common hotspot interface names on Android
                val isHotspot = name.startsWith("ap") ||
                    name.startsWith("swlan") ||
                    name.startsWith("wlan") ||
                    name.startsWith("wigig") ||
                    name.startsWith("softap") ||
                    name.startsWith("p2p-wlan") ||
                    name == "rndis0" ||
                    name == "usb0"
                if (!isHotspot) continue

                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val ip = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && ip.contains('.') &&
                        (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                        Log.i("CG2", "Hotspot IP candidate: $ip on ${iface.name}")
                        candidates.add(ip)
                    }
                }
            }
            // prefer 192.168.43.x (standard Android hotspot)
            candidates.firstOrNull { it.startsWith("192.168.43.") }?.let { return it }
            candidates.firstOrNull { it.startsWith("192.168.") }?.let { return it }
            candidates.firstOrNull()?.let { return it }
        } catch (e: Exception) {
            Log.e("CG2", "Interface scan failed", e)
        }

        // method 2: WifiManager gateway address
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wm.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val ip = intToIp(dhcp.gateway)
                if (ip != "0.0.0.0") {
                    Log.i("CG2", "Hotspot IP from WifiManager gateway: $ip")
                    return ip
                }
            }
        } catch (e: Exception) {
            Log.e("CG2", "WifiManager fallback failed", e)
        }

        Log.w("CG2", "Using default hotspot IP 192.168.43.1")
        return "192.168.43.1"
    }

    fun getSubnet(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else "192.168.43"
    }

    private fun intToIp(i: Int): String =
        "${i and 0xFF}.${i shr 8 and 0xFF}.${i shr 16 and 0xFF}.${i shr 24 and 0xFF}"
}
