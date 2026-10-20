package com.cyberghost.core

object ProxySetupHelper {

    fun getSetupInstructions(gatewayIp: String, proxyPort: Int = 8080): String {
        return """
CyberGhost 2 — Proxy Setup

Gateway IP: $gatewayIp
Proxy Port: $proxyPort

── Android ──
1. Settings → WiFi → long press on "${"\"}CyberGhost\""} network
2. Modify network → Advanced options
3. Proxy → Manual
4. Hostname: $gatewayIp
5. Port: $proxyPort
6. Save

── iOS (iPhone/iPad) ──
1. Settings → WiFi → tap ⓘ next to the network
2. HTTP Proxy → Manual
3. Server: $gatewayIp
4. Port: $proxyPort
5. Save

── After proxy is set ──
• HTTP traffic: visible immediately in Traffic tab
• HTTPS traffic: install CA cert first
  (tap CA button in CyberGhost → share to target)

── CA Certificate Install ──
Android: Settings → Security → Install certificate → CA certificate
iOS: Settings → General → VPN & Device Management → install
After iOS install: Settings → General → About → Certificate Trust Settings → enable
        """.trimIndent()
    }

    fun getProxyString(gatewayIp: String, port: Int = 8080) = "$gatewayIp:$port"

    // QR code data for proxy auto-config — works on some Android versions
    fun getProxyQrData(gatewayIp: String, port: Int = 8080) =
        "WIFI_PROXY:$gatewayIp:$port"
}
