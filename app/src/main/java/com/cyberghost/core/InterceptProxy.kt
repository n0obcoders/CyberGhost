package com.cyberghost.core

import android.util.Log
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
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
    private val _packets = MutableSharedFlow<InterceptedPacket>(replay = 0, extraBufferCapacity = 2048)
    val packets: SharedFlow<InterceptedPacket> = _packets

    fun start() {
        try {
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(2)
            ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(
                            HttpServerCodec(),
                            HttpObjectAggregator(4_194_304),
                            FrontendHandler(this@InterceptProxy, certManager, contentInjector)
                        )
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(port).sync()
            Log.i("CG2", "Proxy started on port $port")
        } catch (e: Exception) {
            Log.e("CG2", "Proxy failed to start on port $port", e)
        }
    }

    fun stop() {
        try { bossGroup?.shutdownGracefully(); workerGroup?.shutdownGracefully() } catch (_: Exception) {}
        bossGroup = null; workerGroup = null
    }

    fun emitPacket(packet: InterceptedPacket) {
        _packets.tryEmit(packet)
    }

    fun processPacket(packet: InterceptedPacket): InterceptedPacket {
        _packets.tryEmit(packet)
        return when (mode) {
            InterceptMode.PASSTHROUGH -> packet
            InterceptMode.AUTO_INJECT -> packet
            InterceptMode.MANUAL -> {
                // in manual mode, queue it — but don't block the Netty thread
                // just pass through for now, the UI polling will show history
                packet
            }
        }
    }

    fun forwardPacket(id: String, modified: InterceptedPacket) {
        val pending = interceptQueue.find { it.packet.id == id }
        pending?.let { interceptQueue.remove(it); it.deferred.complete(modified) }
    }

    fun dropPacket(id: String) {
        val pending = interceptQueue.find { it.packet.id == id }
        pending?.let { interceptQueue.remove(it); it.deferred.cancel() }
    }
}

class FrontendHandler(
    private val proxy: InterceptProxy,
    private val certManager: CertManager,
    private val injector: ContentInjector
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        try {
            val clientAddr = ctx.channel().remoteAddress() as? InetSocketAddress ?: return
            if (request.method() == HttpMethod.CONNECT) handleConnect(ctx, request, clientAddr)
            else handleHttp(ctx, request, clientAddr)
        } catch (e: Exception) {
            Log.e("CG2", "Frontend handler error", e)
            ctx.close()
        }
    }

    private fun handleConnect(ctx: ChannelHandlerContext, req: FullHttpRequest, client: InetSocketAddress) {
        val parts = req.uri().split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 443

        ctx.writeAndFlush(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus(200, "Connection Established")))

        val pipeline = ctx.pipeline()
        pipeline.remove(HttpServerCodec::class.java)
        pipeline.remove(HttpObjectAggregator::class.java)
        pipeline.remove(this)

        try {
            val keyManagers = certManager.getKeyManagerForHost(host)
            val sslCtx = SslContextBuilder.forServer(keyManagers[0]).build()
            pipeline.addFirst("ssl", sslCtx.newHandler(ctx.alloc()))
            pipeline.addLast(HttpServerCodec())
            pipeline.addLast(HttpObjectAggregator(4_194_304))
            pipeline.addLast(MitmInterceptHandler(proxy, host, port, client, injector))
        } catch (e: Exception) {
            Log.e("CG2", "SSL setup failed for $host", e)
            ctx.close()
        }
    }

    private fun handleHttp(ctx: ChannelHandlerContext, req: FullHttpRequest, client: InetSocketAddress) {
        val host = req.headers().get(HttpHeaderNames.HOST) ?: "unknown"
        val bodyBytes = ByteArray(req.content().readableBytes())
        req.content().getBytes(req.content().readerIndex(), bodyBytes)

        val packet = InterceptedPacket(
            sourceIp = client.address.hostAddress ?: "unknown", sourcePort = client.port,
            direction = InterceptedPacket.Direction.REQUEST, method = req.method().name(),
            host = host, path = req.uri(), protocol = "HTTP",
            headers = req.headers().associate { it.key to it.value }.toMutableMap(),
            body = bodyBytes, isRequest = true
        )

        val modified = proxy.processPacket(packet)
        forwardRequest(ctx, modified, host)
    }

    private fun forwardRequest(ctx: ChannelHandlerContext, packet: InterceptedPacket, host: String) {
        try {
            val modifiedReq = DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.valueOf(packet.method ?: "GET"),
                packet.path, Unpooled.wrappedBuffer(packet.body)
            )
            packet.headers.forEach { (k, v) -> modifiedReq.headers().set(k, v) }
            val hostParts = host.split(":")
            val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 80

            Bootstrap().group(ctx.channel().eventLoop()).channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(HttpClientCodec(), HttpObjectAggregator(4_194_304),
                            ResponseInterceptHandler(ctx.channel(), proxy, injector, packet))
                    }
                }).connect(hostParts[0], port).addListener { future ->
                    if (future.isSuccess) (future as ChannelFuture).channel().writeAndFlush(modifiedReq)
                    else ctx.close()
                }
        } catch (e: Exception) {
            Log.e("CG2", "Forward failed", e)
            ctx.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.e("CG2", "Frontend exception", cause)
        ctx.close()
    }
}

class MitmInterceptHandler(
    private val proxy: InterceptProxy,
    private val targetHost: String,
    private val targetPort: Int,
    private val clientAddr: InetSocketAddress,
    private val injector: ContentInjector
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        try {
            val bodyBytes = ByteArray(req.content().readableBytes())
            req.content().getBytes(req.content().readerIndex(), bodyBytes)

            val packet = InterceptedPacket(
                sourceIp = clientAddr.address.hostAddress ?: "unknown", sourcePort = clientAddr.port,
                direction = InterceptedPacket.Direction.REQUEST, method = req.method().name(),
                host = targetHost, path = req.uri(), protocol = "HTTPS",
                headers = req.headers().associate { it.key to it.value }.toMutableMap(),
                body = bodyBytes, isRequest = true
            )

            val modified = proxy.processPacket(packet)
            forwardToTarget(ctx, modified)
        } catch (e: Exception) {
            Log.e("CG2", "MITM handler error", e)
            ctx.close()
        }
    }

    private fun forwardToTarget(ctx: ChannelHandlerContext, packet: InterceptedPacket) {
        try {
            val outboundSsl = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
            val modifiedReq = DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.valueOf(packet.method ?: "GET"),
                packet.path, Unpooled.wrappedBuffer(packet.body)
            )
            packet.headers.forEach { (k, v) -> modifiedReq.headers().set(k, v) }

            Bootstrap().group(ctx.channel().eventLoop()).channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(
                            outboundSsl.newHandler(ch.alloc(), targetHost, targetPort),
                            HttpClientCodec(), HttpObjectAggregator(4_194_304),
                            ResponseInterceptHandler(ctx.channel(), proxy, injector, packet)
                        )
                    }
                }).connect(targetHost, targetPort).addListener { future ->
                    if (future.isSuccess) (future as ChannelFuture).channel().writeAndFlush(modifiedReq)
                    else ctx.close()
                }
        } catch (e: Exception) {
            Log.e("CG2", "Forward to target failed", e)
            ctx.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.e("CG2", "MITM exception", cause)
        ctx.close()
    }
}

class ResponseInterceptHandler(
    private val inboundChannel: Channel,
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
                direction = InterceptedPacket.Direction.RESPONSE, method = requestPacket.method,
                host = requestPacket.host, path = requestPacket.path, protocol = requestPacket.protocol,
                statusCode = response.status().code(),
                headers = response.headers().associate { it.key to it.value }.toMutableMap(),
                body = bodyBytes, isRequest = false
            )

            responsePacket = proxy.processPacket(responsePacket)

            if (proxy.mode == InterceptMode.AUTO_INJECT) {
                responsePacket = injector.inject(responsePacket)
            }

            val modifiedResponse = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(responsePacket.statusCode ?: 200),
                Unpooled.wrappedBuffer(responsePacket.body)
            )
            responsePacket.headers.forEach { (k, v) -> modifiedResponse.headers().set(k, v) }
            modifiedResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, responsePacket.body.size)
            inboundChannel.writeAndFlush(modifiedResponse).addListener(ChannelFutureListener.CLOSE)
        } catch (e: Exception) {
            Log.e("CG2", "Response handler error", e)
            ctx.close()
            inboundChannel.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close(); inboundChannel.close()
    }
}
