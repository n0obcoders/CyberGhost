package com.cyberghost.core

import android.util.Log
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

enum class InterceptMode { PASSTHROUGH, AUTO_INJECT, MANUAL }

data class InterceptedPacket(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Instant = Instant.now(),
    val sourceIp: String,
    val sourcePort: Int,
    val direction: Direction,
    val method: String?,
    val host: String,
    val path: String,
    val protocol: String,
    val statusCode: Int? = null,
    val headers: MutableMap<String, String>,
    var body: ByteArray,
    val isRequest: Boolean
) {
    enum class Direction { REQUEST, RESPONSE }
}

data class PendingIntercept(
    val packet: InterceptedPacket,
    val deferred: CompletableDeferred<InterceptedPacket>
)

class InterceptProxy(
    private val port: Int = 8080,
    private val certManager: CertManager,
    private val contentInjector: ContentInjector
) {
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null
    var mode: InterceptMode = InterceptMode.PASSTHROUGH
    val interceptQueue = ConcurrentLinkedQueue<PendingIntercept>()
    private val _packets = MutableSharedFlow<InterceptedPacket>(
        replay = 0, extraBufferCapacity = 2048
    )
    val packets: SharedFlow<InterceptedPacket> = _packets

    fun start() {
        try {
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(4)
            ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(
                            HttpServerCodec(),
                            HttpObjectAggregator(4_194_304),
                            ProxyFrontendHandler(
                                this@InterceptProxy,
                                certManager,
                                contentInjector
                            )
                        )
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .bind(port).sync()
            Log.i("CG2", "Proxy started on port $port")
        } catch (e: Exception) {
            Log.e("CG2", "Proxy failed to start: ${e.message}")
        }
    }

    fun stop() {
        try {
            bossGroup?.shutdownGracefully()
            workerGroup?.shutdownGracefully()
        } catch (_: Exception) {}
        bossGroup = null; workerGroup = null
    }

    fun processPacket(packet: InterceptedPacket): InterceptedPacket {
        _packets.tryEmit(packet)
        return packet
    }

    fun forwardPacket(id: String, modified: InterceptedPacket) {
        interceptQueue.find { it.packet.id == id }?.let {
            interceptQueue.remove(it)
            it.deferred.complete(modified)
        }
    }

    fun dropPacket(id: String) {
        interceptQueue.find { it.packet.id == id }?.let {
            interceptQueue.remove(it)
            it.deferred.cancel()
        }
    }
}

class ProxyFrontendHandler(
    private val proxy: InterceptProxy,
    private val certManager: CertManager,
    private val injector: ContentInjector
) : SimpleChannelInboundHandler<HttpObject>() {

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        if (msg !is HttpRequest) return
        try {
            val clientAddr = ctx.channel().remoteAddress() as? InetSocketAddress

            if (msg.method() == HttpMethod.CONNECT) {
                // HTTPS tunnel request
                handleConnect(ctx, msg, clientAddr)
            } else {
                // plain HTTP request
                handleHttp(ctx, msg, clientAddr)
            }
        } catch (e: Exception) {
            Log.e("CG2", "Frontend error", e)
            ctx.close()
        }
    }

    private fun handleConnect(
        ctx: ChannelHandlerContext,
        req: HttpRequest,
        clientAddr: InetSocketAddress?
    ) {
        val hostPort = req.uri().split(":")
        val host = hostPort[0]
        val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

        // send 200 Connection Established immediately
        val established = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus(200, "Connection Established")
        )
        established.headers().set(HttpHeaderNames.CONNECTION, "keep-alive")

        ctx.writeAndFlush(established).addListener { future ->
            if (!future.isSuccess) { ctx.close(); return@addListener }

            val pipeline = ctx.pipeline()

            // remove HTTP codec — we're now in tunnel mode
            pipeline.remove(HttpServerCodec::class.java)
            pipeline.remove(HttpObjectAggregator::class.java)
            pipeline.remove(this@ProxyFrontendHandler)

            // decide: MITM (decrypt) or tunnel (pass-through)
            if (proxy.mode != InterceptMode.PASSTHROUGH) {
                // MITM mode — intercept HTTPS content
                setupMitm(ctx, host, port, clientAddr)
            } else {
                // Passthrough mode — just log the connection, pass bytes through
                // This makes HTTPS work even without CA installed
                setupTunnel(ctx, host, port, clientAddr)
            }
        }
    }

    private fun setupMitm(
        ctx: ChannelHandlerContext,
        host: String,
        port: Int,
        clientAddr: InetSocketAddress?
    ) {
        try {
            val keyManagers = certManager.getKeyManagerForHost(host)
            val sslCtx = SslContextBuilder.forServer(keyManagers[0]).build()
            val pipeline = ctx.pipeline()
            pipeline.addFirst("ssl", sslCtx.newHandler(ctx.alloc()))
            pipeline.addLast(HttpServerCodec())
            pipeline.addLast(HttpObjectAggregator(4_194_304))
            pipeline.addLast(MitmHttpHandler(proxy, host, port, clientAddr, injector))
            Log.d("CG2", "MITM setup for $host")
        } catch (e: Exception) {
            Log.e("CG2", "MITM setup failed for $host", e)
            // fall back to tunnel so HTTPS still works
            setupTunnel(ctx, host, port, clientAddr)
        }
    }

    private fun setupTunnel(
        ctx: ChannelHandlerContext,
        host: String,
        port: Int,
        clientAddr: InetSocketAddress?
    ) {
        // log the CONNECT without decrypting
        val connPacket = InterceptedPacket(
            sourceIp = clientAddr?.address?.hostAddress ?: "unknown",
            sourcePort = clientAddr?.port ?: 0,
            direction = InterceptedPacket.Direction.REQUEST,
            method = "CONNECT",
            host = host,
            path = ":$port",
            protocol = "HTTPS",
            headers = mutableMapOf(),
            body = ByteArray(0),
            isRequest = true
        )
        proxy.processPacket(connPacket)

        // connect to real server and relay bytes bidirectionally
        Bootstrap()
            .group(ctx.channel().eventLoop())
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(RelayHandler(ctx.channel()))
                }
            })
            .connect(host, port)
            .addListener { f ->
                if (f.isSuccess) {
                    val outbound = (f as ChannelFuture).channel()
                    ctx.pipeline().addLast(RelayHandler(outbound))
                    Log.d("CG2", "Tunnel established: $host:$port")
                } else {
                    Log.w("CG2", "Tunnel failed: $host:$port")
                    ctx.close()
                }
            }
    }

    private fun handleHttp(
        ctx: ChannelHandlerContext,
        req: HttpRequest,
        clientAddr: InetSocketAddress?
    ) {
        val host = req.headers().get(HttpHeaderNames.HOST) ?: "unknown"

        // need full body — re-aggregate if needed
        val bodyBytes = if (req is FullHttpRequest) {
            ByteArray(req.content().readableBytes()).also {
                req.content().getBytes(req.content().readerIndex(), it)
            }
        } else ByteArray(0)

        val packet = InterceptedPacket(
            sourceIp = clientAddr?.address?.hostAddress ?: "unknown",
            sourcePort = clientAddr?.port ?: 0,
            direction = InterceptedPacket.Direction.REQUEST,
            method = req.method().name(),
            host = host,
            path = req.uri(),
            protocol = "HTTP",
            headers = req.headers().associate { it.key to it.value }.toMutableMap(),
            body = bodyBytes,
            isRequest = true
        )
        val modified = proxy.processPacket(packet)

        // inject if needed
        if (proxy.mode == InterceptMode.AUTO_INJECT) {
            injector.inspectRequest(modified)
        }

        forwardHttp(ctx, modified, host)
    }

    private fun forwardHttp(
        ctx: ChannelHandlerContext,
        packet: InterceptedPacket,
        host: String
    ) {
        try {
            val outReq = DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.valueOf(packet.method ?: "GET"),
                packet.path,
                Unpooled.wrappedBuffer(packet.body)
            )
            packet.headers.forEach { (k, v) -> outReq.headers().set(k, v) }
            outReq.headers().set(HttpHeaderNames.HOST, host)

            val hostParts = host.split(":")
            val realHost = hostParts[0]
            val realPort = hostParts.getOrNull(1)?.toIntOrNull() ?: 80

            Bootstrap()
                .group(ctx.channel().eventLoop())
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(
                            HttpClientCodec(),
                            HttpObjectAggregator(4_194_304),
                            HttpResponseRelayHandler(ctx.channel(), proxy, injector, packet)
                        )
                    }
                })
                .connect(realHost, realPort)
                .addListener { f ->
                    if (f.isSuccess) (f as ChannelFuture).channel().writeAndFlush(outReq)
                    else ctx.close()
                }
        } catch (e: Exception) {
            Log.e("CG2", "HTTP forward failed", e)
            ctx.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.d("CG2", "Frontend: ${cause.message}")
        ctx.close()
    }
}

/**
 * Raw byte relay — used for CONNECT tunnels in passthrough mode.
 * Copies bytes in both directions without touching them.
 * This is what makes HTTPS work without CA cert installed.
 */
class RelayHandler(private val peer: Channel) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (peer.isActive) {
            peer.writeAndFlush(msg)
        } else {
            io.netty.util.ReferenceCountUtil.release(msg)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        peer.close()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
        peer.close()
    }
}

/**
 * MITM HTTP handler — processes decrypted HTTPS requests.
 */
class MitmHttpHandler(
    private val proxy: InterceptProxy,
    private val targetHost: String,
    private val targetPort: Int,
    private val clientAddr: InetSocketAddress?,
    private val injector: ContentInjector
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        try {
            val bodyBytes = ByteArray(req.content().readableBytes())
            req.content().getBytes(req.content().readerIndex(), bodyBytes)

            val packet = InterceptedPacket(
                sourceIp = clientAddr?.address?.hostAddress ?: "unknown",
                sourcePort = clientAddr?.port ?: 0,
                direction = InterceptedPacket.Direction.REQUEST,
                method = req.method().name(),
                host = targetHost, path = req.uri(),
                protocol = "HTTPS",
                headers = req.headers().associate { it.key to it.value }.toMutableMap(),
                body = bodyBytes, isRequest = true
            )
            val modified = proxy.processPacket(packet)

            val outSsl = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build()

            val outReq = DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.valueOf(modified.method ?: "GET"),
                modified.path,
                Unpooled.wrappedBuffer(modified.body)
            )
            modified.headers.forEach { (k, v) -> outReq.headers().set(k, v) }
            outReq.headers().set(HttpHeaderNames.HOST, targetHost)

            Bootstrap().group(ctx.channel().eventLoop())
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(
                            outSsl.newHandler(ch.alloc(), targetHost, targetPort),
                            HttpClientCodec(),
                            HttpObjectAggregator(4_194_304),
                            HttpResponseRelayHandler(ctx.channel(), proxy, injector, modified)
                        )
                    }
                })
                .connect(targetHost, targetPort)
                .addListener { f ->
                    if (f.isSuccess) (f as ChannelFuture).channel().writeAndFlush(outReq)
                    else ctx.close()
                }
        } catch (e: Exception) {
            Log.e("CG2", "MITM handler error", e)
            ctx.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) { ctx.close() }
}

/**
 * Relays HTTP responses back to the browser,
 * optionally injecting content.
 */
class HttpResponseRelayHandler(
    private val inbound: Channel,
    private val proxy: InterceptProxy,
    private val injector: ContentInjector,
    private val requestPacket: InterceptedPacket
) : SimpleChannelInboundHandler<FullHttpResponse>() {

    override fun channelRead0(ctx: ChannelHandlerContext, response: FullHttpResponse) {
        try {
            val bodyBytes = ByteArray(response.content().readableBytes())
            response.content().getBytes(response.content().readerIndex(), bodyBytes)

            var responsePacket = InterceptedPacket(
                sourceIp = requestPacket.host, sourcePort = 0,
                direction = InterceptedPacket.Direction.RESPONSE,
                method = requestPacket.method,
                host = requestPacket.host, path = requestPacket.path,
                protocol = requestPacket.protocol,
                statusCode = response.status().code(),
                headers = response.headers().associate { it.key to it.value }.toMutableMap(),
                body = bodyBytes, isRequest = false
            )
            responsePacket = proxy.processPacket(responsePacket)

            if (proxy.mode == InterceptMode.AUTO_INJECT) {
                responsePacket = injector.inject(responsePacket)
            }

            val outResp = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(responsePacket.statusCode ?: 200),
                Unpooled.wrappedBuffer(responsePacket.body)
            )
            responsePacket.headers.forEach { (k, v) -> outResp.headers().set(k, v) }
            outResp.headers().set(HttpHeaderNames.CONTENT_LENGTH, responsePacket.body.size)
            // keep connection alive so browser can make more requests
            outResp.headers().set(HttpHeaderNames.CONNECTION, "keep-alive")

            inbound.writeAndFlush(outResp)
        } catch (e: Exception) {
            Log.e("CG2", "Response relay error", e)
            ctx.close(); inbound.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close(); inbound.close()
    }
}
