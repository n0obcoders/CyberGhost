package com.cyberghost.core

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface

object HotspotManager {

    fun getHotspotIp(context: Context): String {
        // try to find the hotspot interface IP directly
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name.lowercase()
                // hotspot interfaces: ap0, wlan0 in AP mode, swlan0, p2p0
                if ((name.contains("ap") || name.contains("wlan") || name.contains("swlan") || name.contains("p2p"))
                    && !iface.isLoopback && iface.isUp) {
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        val ip = addr.hostAddress ?: continue
                        // must be IPv4 and private
                        if (!addr.isLoopbackAddress && ip.contains('.') && !ip.startsWith("127.")) {
                            Log.i("CG2", "Hotspot IP found on ${iface.name}: $ip")
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CG2", "Interface scan failed", e)
        }

        // fallback: try WifiManager DhcpInfo
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wm.dhcpInfo
            if (dhcp != null && dhcp.serverAddress != 0) {
                val ip = intToIp(dhcp.serverAddress)
                Log.i("CG2", "Hotspot IP from DHCP: $ip")
                return ip
            }
        } catch (e: Exception) {
            Log.e("CG2", "DHCP fallback failed", e)
        }

        // last resort defaults
        Log.w("CG2", "Using fallback hotspot IP 192.168.43.1")
        return "192.168.43.1"
    }

    fun getSubnet(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else "192.168.43"
    }

    private fun intToIp(i: Int): String {
        return "${i and 0xFF}.${i shr 8 and 0xFF}.${i shr 16 and 0xFF}.${i shr 24 and 0xFF}"
    }
}
