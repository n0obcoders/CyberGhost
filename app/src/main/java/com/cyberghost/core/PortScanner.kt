package com.cyberghost.core

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetSocketAddress
import java.net.Socket

data class PortScanResult(
    val ip: String,
    val openPorts: List<OpenPort>,
    val scanTime: Long
)

data class OpenPort(
    val port: Int,
    val service: String,
    val banner: String?
)

class PortScanner {
    private val _results = MutableStateFlow<Map<String, PortScanResult>>(emptyMap())
    val results: StateFlow<Map<String, PortScanResult>> = _results
    private val _scanning = MutableStateFlow<String?>(null)
    val scanning: StateFlow<String?> = _scanning

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val commonPorts = listOf(
        21, 22, 23, 25, 53, 80, 110, 135, 139, 143,
        443, 445, 3306, 3389, 5900, 8080, 8443, 8888
    )

    fun scan(ip: String, ports: List<Int> = commonPorts) {
        scope.launch {
            _scanning.value = ip
            val start = System.currentTimeMillis()
            val open = mutableListOf<OpenPort>()

            val jobs = ports.map { port ->
                async {
                    try {
                        val sock = Socket()
                        sock.connect(InetSocketAddress(ip, port), 300)
                        var banner: String? = null
                        try {
                            sock.soTimeout = 200
                            val buf = ByteArray(256)
                            val read = sock.inputStream.read(buf)
                            if (read > 0) banner = String(buf, 0, read).trim().take(80)
                        } catch (_: Exception) {}
                        sock.close()
                        OpenPort(port, getServiceName(port), banner)
                    } catch (_: Exception) { null }
                }
            }

            jobs.forEach { job ->
                job.await()?.let { open.add(it) }
            }

            val result = PortScanResult(ip, open.sortedBy { it.port }, System.currentTimeMillis() - start)
            _results.value = _results.value + (ip to result)
            _scanning.value = null
            Log.i("CG2", "Port scan $ip: ${open.size} open ports in ${result.scanTime}ms")
        }
    }

    fun getResult(ip: String) = _results.value[ip]

    private fun getServiceName(port: Int) = when (port) {
        21 -> "FTP"; 22 -> "SSH"; 23 -> "Telnet"; 25 -> "SMTP"
        53 -> "DNS"; 80 -> "HTTP"; 110 -> "POP3"; 135 -> "RPC"
        139 -> "NetBIOS"; 143 -> "IMAP"; 443 -> "HTTPS"; 445 -> "SMB"
        3306 -> "MySQL"; 3389 -> "RDP"; 5900 -> "VNC"
        8080 -> "HTTP-Alt"; 8443 -> "HTTPS-Alt"; 8888 -> "HTTP-Dev"
        else -> "Unknown"
    }
}
