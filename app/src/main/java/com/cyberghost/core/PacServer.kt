package com.cyberghost.core

import android.util.Log
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import java.nio.charset.StandardCharsets

class PacServer(
    private val port: Int = 8889,
    private val gatewayIp: String,
    private val proxyPort: Int = 8080
) {
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null

    fun start() {
        try {
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(1)
            ServerBootstrap().group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(HttpServerCodec(), HttpObjectAggregator(65_536),
                            PacHandler(gatewayIp, proxyPort, port))
                    }
                }).bind(port).sync()
            Log.i("CG2", "PAC/Setup server on port $port")
        } catch (e: Exception) {
            Log.e("CG2", "PAC server failed: ${e.message}")
        }
    }

    fun stop() {
        try { bossGroup?.shutdownGracefully(); workerGroup?.shutdownGracefully() } catch (_: Exception) {}
        bossGroup = null; workerGroup = null
    }

    fun getPacUrl() = "http://$gatewayIp:$port/proxy.pac"
    fun getSetupUrl() = "http://$gatewayIp:$port/setup"
}

class PacHandler(
    private val gatewayIp: String,
    private val proxyPort: Int,
    private val pacPort: Int
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        val path = req.uri().split("?").first()
        val (content, mime) = when {
            path == "/proxy.pac" || path == "/" -> buildPac() to "application/x-ns-proxy-autoconfig"
            path == "/setup" -> buildSetupPage() to "text/html; charset=UTF-8"
            else -> buildPac() to "application/x-ns-proxy-autoconfig"
        }
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes))
        response.headers().apply {
            set(HttpHeaderNames.CONTENT_TYPE, mime)
            set(HttpHeaderNames.CONTENT_LENGTH, bytes.size)
            set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
            set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        }
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }

    private fun buildPac() = """
function FindProxyForURL(url, host) {
    if (host == "connectivitycheck.gstatic.com") return "DIRECT";
    if (host == "clients3.google.com") return "DIRECT";
    if (host == "play.googleapis.com") return "DIRECT";
    if (host == "captive.apple.com") return "DIRECT";
    if (host == "www.apple.com") return "DIRECT";
    if (host == "captivedetect.apple.com") return "DIRECT";
    if (host == "time.android.com") return "DIRECT";
    return "PROXY $gatewayIp:$proxyPort";
}""".trimIndent()

    private fun buildSetupPage() = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>WiFi Setup</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#0a0a0f;color:#e0e0ec;min-height:100vh;padding:24px}
.logo{text-align:center;margin-bottom:32px}
.logo h1{font-size:28px;font-weight:900;letter-spacing:2px}
.logo h1 span:first-child{color:#00ff41}
.logo h1 span:last-child{color:#00d4ff}
.logo p{color:#6b6b80;font-size:13px;margin-top:4px}
.card{background:#161622;border-radius:16px;padding:20px;margin-bottom:16px;border:1px solid rgba(255,255,255,0.05)}
.card h2{font-size:14px;font-weight:700;margin-bottom:16px;text-transform:uppercase;letter-spacing:1px}
.android h2{color:#00ff41}
.ios h2{color:#00d4ff}
.step{display:flex;align-items:flex-start;margin-bottom:14px}
.num{width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:bold;flex-shrink:0;margin-right:12px}
.android .num{background:rgba(0,255,65,0.15);color:#00ff41}
.ios .num{background:rgba(0,212,255,0.15);color:#00d4ff}
.step p{font-size:13px;color:#e0e0ec;line-height:1.5;padding-top:4px}
.step p code{background:rgba(255,255,255,0.1);padding:2px 6px;border-radius:4px;font-family:monospace;font-size:12px;color:#00ff41}
.pac-card{background:rgba(0,255,65,0.08);border:1px solid rgba(0,255,65,0.2);border-radius:16px;padding:20px;margin-bottom:16px;text-align:center}
.pac-url{font-family:monospace;font-size:13px;color:#00ff41;background:rgba(0,0,0,0.4);padding:12px;border-radius:8px;margin:12px 0;word-break:break-all}
.btn{display:inline-block;padding:12px 24px;border-radius:10px;font-size:13px;font-weight:700;text-decoration:none;margin:4px}
.btn-green{background:rgba(0,255,65,0.2);color:#00ff41;border:1px solid rgba(0,255,65,0.4)}
.btn-blue{background:rgba(0,212,255,0.2);color:#00d4ff;border:1px solid rgba(0,212,255,0.4)}
.info{color:#6b6b80;font-size:12px;line-height:1.6}
</style>
</head>
<body>
<div class="logo">
<h1><span>CYBER</span><span>GHOST</span></h1>
<p>Proxy Setup — follow steps for your device</p>
</div>

<div class="pac-card">
<p style="color:#6b6b80;font-size:12px;text-transform:uppercase;letter-spacing:1px">PAC Auto-Config URL</p>
<div class="pac-url">http://$gatewayIp:$pacPort/proxy.pac</div>
<p class="info">Use this URL in WiFi → Proxy → Auto-Config URL</p>
<br>
<a href="http://$gatewayIp:$pacPort/proxy.pac" class="btn btn-green">Test PAC File</a>
</div>

<div class="card android">
<h2>📱 Android Setup</h2>
<div class="step"><div class="num">1</div><p>Go to <strong>Settings → WiFi</strong></p></div>
<div class="step"><div class="num">2</div><p>Long press the connected network → <strong>Modify network</strong></p></div>
<div class="step"><div class="num">3</div><p>Tap <strong>Advanced options</strong> (or show advanced)</p></div>
<div class="step"><div class="num">4</div><p>Proxy → select <strong>Proxy Auto-Config</strong></p></div>
<div class="step"><div class="num">5</div><p>Paste: <code>http://$gatewayIp:$pacPort/proxy.pac</code></p></div>
<div class="step"><div class="num">6</div><p>Save → done. All traffic now routed through CyberGhost.</p></div>
</div>

<div class="card android">
<h2>⚡ Android Manual Proxy</h2>
<div class="step"><div class="num">1</div><p>Settings → WiFi → Long press network → Modify</p></div>
<div class="step"><div class="num">2</div><p>Advanced → Proxy → <strong>Manual</strong></p></div>
<div class="step"><div class="num">3</div><p>Host: <code>$gatewayIp</code></p></div>
<div class="step"><div class="num">4</div><p>Port: <code>$proxyPort</code></p></div>
<div class="step"><div class="num">5</div><p>Save</p></div>
</div>

<div class="card ios">
<h2>🍎 iPhone / iPad</h2>
<div class="step"><div class="num">1</div><p>Settings → WiFi → tap ⓘ next to network</p></div>
<div class="step"><div class="num">2</div><p>HTTP Proxy → <strong>Automatic</strong></p></div>
<div class="step"><div class="num">3</div><p>URL: <code>http://$gatewayIp:$pacPort/proxy.pac</code></p></div>
<div class="step"><div class="num">4</div><p>Tap off the field to save</p></div>
</div>

<div class="card">
<h2 style="color:#ffb000">🔐 HTTPS Decryption (optional)</h2>
<p class="info" style="margin-bottom:12px">Install the CA certificate to also decrypt HTTPS traffic:</p>
<div class="step"><div class="num" style="background:rgba(255,176,0,0.15);color:#ffb000">1</div><p>Export the CA cert from CyberGhost app (CA button)</p></div>
<div class="step"><div class="num" style="background:rgba(255,176,0,0.15);color:#ffb000">2</div><p><strong>Android:</strong> Settings → Security → Install cert → CA certificate</p></div>
<div class="step"><div class="num" style="background:rgba(255,176,0,0.15);color:#ffb000">3</div><p><strong>iOS:</strong> Open cert file → install → Settings → General → About → Certificate Trust Settings → enable</p></div>
</div>
</body>
</html>""".trimIndent()

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) { ctx.close() }
}
