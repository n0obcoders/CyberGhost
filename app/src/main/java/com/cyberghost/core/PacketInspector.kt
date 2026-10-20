package com.cyberghost.core

import org.json.JSONObject
import org.json.JSONArray
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class PacketAnalysis(
    val contentType: ContentCategory,
    val summary: String,
    val sensitiveFields: List<SensitiveField>,
    val imageData: String?,
    val jsonTree: String?,
    val formFields: Map<String, String>,
    val rawBody: String,
    val size: Int,
    val isEncrypted: Boolean,
    val authHeader: String?,
    val cookieCount: Int,
    val responseCode: Int?,
    val redirectUrl: String?
)

data class SensitiveField(
    val key: String,
    val value: String,
    val risk: RiskLevel
)

enum class RiskLevel { HIGH, MEDIUM, LOW }
enum class ContentCategory {
    LOGIN_FORM, JSON_API, IMAGE, HTML_PAGE, JAVASCRIPT,
    CSS, BINARY, TEXT, XML, COOKIE_SESSION, AUTH_HEADER,
    REDIRECT, EMPTY, UNKNOWN
}

object PacketInspector {

    private val highRiskKeys = setOf("password", "passwd", "pass", "pwd", "secret", "pin",
        "token", "api_key", "apikey", "access_token", "auth", "authorization",
        "ssn", "social", "credit_card", "card_number", "cvv", "expiry")

    private val mediumRiskKeys = setOf("username", "user", "email", "login", "phone",
        "mobile", "address", "dob", "birth", "account")

    fun analyze(packet: InterceptedPacket): PacketAnalysis {
        val body = try { String(packet.body, StandardCharsets.UTF_8) } catch (_: Exception) { "" }
        val ct = packet.headers["Content-Type"] ?: packet.headers["content-type"] ?: ""
        val auth = packet.headers["Authorization"] ?: packet.headers["authorization"]
        val cookie = packet.headers["Cookie"] ?: packet.headers["cookie"] ?: ""
        val cookieCount = if (cookie.isBlank()) 0 else cookie.split(";").size
        val location = packet.headers["Location"] ?: packet.headers["location"]
        val sensitive = mutableListOf<SensitiveField>()

        val category = when {
            packet.statusCode in 300..399 -> ContentCategory.REDIRECT
            body.isEmpty() && packet.body.isEmpty() -> ContentCategory.EMPTY
            ct.contains("image/") -> ContentCategory.IMAGE
            ct.contains("application/json") -> ContentCategory.JSON_API
            ct.contains("application/x-www-form-urlencoded") -> ContentCategory.LOGIN_FORM
            ct.contains("text/html") -> ContentCategory.HTML_PAGE
            ct.contains("text/javascript") || ct.contains("application/javascript") -> ContentCategory.JAVASCRIPT
            ct.contains("text/css") -> ContentCategory.CSS
            ct.contains("text/xml") || ct.contains("application/xml") -> ContentCategory.XML
            auth != null -> ContentCategory.AUTH_HEADER
            cookieCount > 2 -> ContentCategory.COOKIE_SESSION
            looksLikeForm(body) -> ContentCategory.LOGIN_FORM
            looksLikeJson(body) -> ContentCategory.JSON_API
            else -> ContentCategory.UNKNOWN
        }

        // extract sensitive fields
        when (category) {
            ContentCategory.LOGIN_FORM -> {
                parseFormFields(body).forEach { (k, v) ->
                    val risk = when {
                        highRiskKeys.any { k.lowercase().contains(it) } -> RiskLevel.HIGH
                        mediumRiskKeys.any { k.lowercase().contains(it) } -> RiskLevel.MEDIUM
                        else -> RiskLevel.LOW
                    }
                    if (risk != RiskLevel.LOW && v.isNotBlank()) {
                        sensitive.add(SensitiveField(k, v, risk))
                    }
                }
            }
            ContentCategory.JSON_API -> {
                flattenJson(body).forEach { (k, v) ->
                    val risk = when {
                        highRiskKeys.any { k.lowercase().contains(it) } -> RiskLevel.HIGH
                        mediumRiskKeys.any { k.lowercase().contains(it) } -> RiskLevel.MEDIUM
                        else -> RiskLevel.LOW
                    }
                    if (risk != RiskLevel.LOW && v.isNotBlank()) {
                        sensitive.add(SensitiveField(k, v, risk))
                    }
                }
            }
            else -> {}
        }

        auth?.let {
            val risk = if (it.startsWith("Basic", true) || it.startsWith("Bearer", true)) RiskLevel.HIGH else RiskLevel.MEDIUM
            sensitive.add(SensitiveField("Authorization", it.take(60), risk))
        }

        val summary = buildSummary(category, packet, body, sensitive, cookieCount)

        val imageData = if (ct.contains("image/") && packet.body.isNotEmpty()) {
            val mime = ct.substringBefore(";").trim()
            "data:$mime;base64,${android.util.Base64.encodeToString(packet.body, android.util.Base64.NO_WRAP)}"
        } else null

        val jsonTree = if (category == ContentCategory.JSON_API) prettyJson(body) else null

        return PacketAnalysis(
            contentType = category,
            summary = summary,
            sensitiveFields = sensitive,
            imageData = imageData,
            jsonTree = jsonTree,
            formFields = if (category == ContentCategory.LOGIN_FORM) parseFormFields(body) else emptyMap(),
            rawBody = body.take(4096),
            size = packet.body.size,
            isEncrypted = packet.protocol == "HTTPS",
            authHeader = auth,
            cookieCount = cookieCount,
            responseCode = packet.statusCode,
            redirectUrl = location
        )
    }

    private fun buildSummary(category: ContentCategory, packet: InterceptedPacket, body: String, sensitive: List<SensitiveField>, cookies: Int): String {
        return when (category) {
            ContentCategory.LOGIN_FORM -> {
                val hasPass = sensitive.any { it.risk == RiskLevel.HIGH }
                if (hasPass) "⚠️ Login form with password — ${sensitive.size} sensitive fields"
                else "Form submission — ${sensitive.size} fields"
            }
            ContentCategory.JSON_API -> {
                val hasAuth = sensitive.any { it.risk == RiskLevel.HIGH }
                if (hasAuth) "⚠️ API call with credentials — ${sensitive.size} sensitive fields"
                else "API ${packet.method ?: "call"} — ${body.length} bytes JSON"
            }
            ContentCategory.IMAGE -> "Image — ${packet.body.size / 1024}KB ${(packet.headers["Content-Type"] ?: "").substringAfter("image/").substringBefore(";")}"
            ContentCategory.HTML_PAGE -> "HTML page — ${body.length} chars"
            ContentCategory.JAVASCRIPT -> "JavaScript — ${body.length} chars"
            ContentCategory.CSS -> "Stylesheet — ${body.length} chars"
            ContentCategory.COOKIE_SESSION -> "Session cookies — $cookies cookies"
            ContentCategory.AUTH_HEADER -> "Authenticated request"
            ContentCategory.REDIRECT -> "Redirect → ${packet.headers["Location"] ?: "unknown"}"
            ContentCategory.EMPTY -> "Empty ${if (packet.isRequest) "request" else "response"}"
            ContentCategory.XML -> "XML data — ${body.length} chars"
            ContentCategory.BINARY -> "Binary data — ${packet.body.size} bytes"
            else -> "${packet.method ?: ""} ${packet.path.take(40)}"
        }
    }

    private fun looksLikeForm(body: String) = body.contains("=") && body.contains("&") && !body.contains("<")
    private fun looksLikeJson(body: String) = body.trimStart().startsWith("{") || body.trimStart().startsWith("[")

    private fun parseFormFields(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        body.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                try {
                    result[URLDecoder.decode(parts[0], "UTF-8")] = URLDecoder.decode(parts[1], "UTF-8")
                } catch (_: Exception) { result[parts[0]] = parts[1] }
            }
        }
        return result
    }

    private fun flattenJson(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            fun flatten(obj: JSONObject, prefix: String = "") {
                obj.keys().forEach { key ->
                    val full = if (prefix.isEmpty()) key else "$prefix.$key"
                    when (val v = obj.get(key)) {
                        is JSONObject -> flatten(v, full)
                        is String, is Number, is Boolean -> result[full] = v.toString()
                        else -> {}
                    }
                }
            }
            flatten(JSONObject(body))
        } catch (_: Exception) {}
        return result
    }

    private fun prettyJson(body: String): String {
        return try {
            if (body.trimStart().startsWith("[")) JSONArray(body).toString(2)
            else JSONObject(body).toString(2)
        } catch (_: Exception) { body.take(2000) }
    }
}
