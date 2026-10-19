package com.cyberghost.core

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
import java.nio.charset.StandardCharsets
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
    private val bossGroup = NioEventLoopGroup(1)
    private val workerGroup = NioEventLoopGroup(4)
    var mode: InterceptMode = InterceptMode.PASSTHROUGH
    val interceptQueue = ConcurrentLinkedQueue<PendingIntercept>()
    private val _packets = MutableSharedFlow<InterceptedPacket>(replay = 0, extraBufferCapacity = 2048)
    val packets: SharedFlow<InterceptedPacket> = _packets
    private val _pendingCount = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)
    val pendingCount: SharedFlow<Int> = _pendingCount

    fun start() {
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
    }

    fun stop() { bossGroup.shutdownGracefully(); workerGroup.shutdownGracefully() }

    suspend fun enqueueForIntercept(packet: InterceptedPacket): InterceptedPacket {
        _packets.tryEmit(packet)
        return when (mode) {
            InterceptMode.PASSTHROUGH -> packet
            InterceptMode.AUTO_INJECT -> packet
            InterceptMode.MANUAL -> {
                val deferred = CompletableDeferred<InterceptedPacket>()
                interceptQueue.add(PendingIntercept(packet, deferred))
                _pendingCount.tryEmit(interceptQueue.size)
                deferred.await()
            }
        }
    }

    fun forwardPacket(id: String, modified: InterceptedPacket) {
        val pending = interceptQueue.find { it.packet.id == id }
        pending?.let { interceptQueue.remove(it); it.deferred.complete(modified); _pendingCount.tryEmit(interceptQueue.size) }
    }

    fun dropPacket(id: String) {
        val pending = interceptQueue.find { it.packet.id == id }
        pending?.let { interceptQueue.remove(it); it.deferred.cancel(); _pendingCount.tryEmit(interceptQueue.size) }
    }
}

class FrontendHandler(
    private val proxy: InterceptProxy,
    private val certManager: CertManager,
    private val injector: ContentInjector
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        val clientAddr = ctx.channel().remoteAddress() as InetSocketAddress
        if (request.method() == HttpMethod.CONNECT) handleConnect(ctx, request, clientAddr)
        else handleHttp(ctx, request, clientAddr)
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
        val sslCtx = SslContextBuilder.forServer(certManager.getKeyManagerForHost(host)[0]).build()
        pipeline.addFirst("ssl", sslCtx.newHandler(ctx.alloc()))
        pipeline.addLast(HttpServerCodec())
        pipeline.addLast(HttpObjectAggregator(4_194_304))
        pipeline.addLast(MitmInterceptHandler(proxy, host, port, client, injector))
    }

    private fun handleHttp(ctx: ChannelHandlerContext, req: FullHttpRequest, client: InetSocketAddress) {
        val host = req.headers().get(HttpHeaderNames.HOST) ?: "unknown"
        val bodyBytes = ByteArray(req.content().readableBytes())
        req.content().getBytes(req.content().readerIndex(), bodyBytes)
        val packet = InterceptedPacket(
            sourceIp = client.address.hostAddress, sourcePort = client.port,
            direction = InterceptedPacket.Direction.REQUEST, method = req.method().name(),
            host = host, path = req.uri(), protocol = "HTTP",
            headers = req.headers().associate { it.key to it.value }.toMutableMap(),
            body = bodyBytes, isRequest = true
        )
        ctx.channel().eventLoop().execute {
            kotlinx.coroutines.runBlocking {
                val modified = proxy.enqueueForIntercept(packet)
                forwardModifiedRequest(ctx, modified, host, req)
            }
        }
    }

    private fun forwardModifiedRequest(ctx: ChannelHandlerContext, packet: InterceptedPacket, host: String, originalReq: FullHttpRequest) {
        val modifiedReq = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(packet.method ?: "GET"), packet.path, Unpooled.wrappedBuffer(packet.body))
        packet.headers.forEach { (k, v) -> modifiedReq.headers().set(k, v) }
        val hostParts = host.split(":")
        val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 80
        Bootstrap().group(ctx.channel().eventLoop()).channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(HttpClientCodec(), HttpObjectAggregator(4_194_304), ResponseInterceptHandler(ctx.channel(), proxy, injector, packet))
                }
            }).connect(hostParts[0], port).addListener { future ->
                if (future.isSuccess) (future as ChannelFuture).channel().writeAndFlush(modifiedReq) else ctx.close()
            }
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
        val bodyBytes = ByteArray(req.content().readableBytes())
        req.content().getBytes(req.content().readerIndex(), bodyBytes)
        val packet = InterceptedPacket(
            sourceIp = clientAddr.address.hostAddress, sourcePort = clientAddr.port,
            direction = InterceptedPacket.Direction.REQUEST, method = req.method().name(),
            host = targetHost, path = req.uri(), protocol = "HTTPS",
            headers = req.headers().associate { it.key to it.value }.toMutableMap(),
            body = bodyBytes, isRequest = true
        )
        ctx.channel().eventLoop().execute {
            kotlinx.coroutines.runBlocking {
                val modified = proxy.enqueueForIntercept(packet)
                forwardToTarget(ctx, modified, req)
            }
        }
    }

    private fun forwardToTarget(ctx: ChannelHandlerContext, packet: InterceptedPacket, originalReq: FullHttpRequest) {
        val outboundSsl = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
        val modifiedReq = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(packet.method ?: "GET"), packet.path, Unpooled.wrappedBuffer(packet.body))
        packet.headers.forEach { (k, v) -> modifiedReq.headers().set(k, v) }
        Bootstrap().group(ctx.channel().eventLoop()).channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(outboundSsl.newHandler(ch.alloc(), targetHost, targetPort), HttpClientCodec(), HttpObjectAggregator(4_194_304), ResponseInterceptHandler(ctx.channel(), proxy, injector, packet))
                }
            }).connect(targetHost, targetPort).addListener { future ->
                if (future.isSuccess) (future as ChannelFuture).channel().writeAndFlush(modifiedReq) else ctx.close()
            }
    }
}

class ResponseInterceptHandler(
    private val inboundChannel: Channel,
    private val proxy: InterceptProxy,
    private val injector: ContentInjector,
    private val requestPacket: InterceptedPacket
) : SimpleChannelInboundHandler<FullHttpResponse>() {

    override fun channelRead0(ctx: ChannelHandlerContext, response: FullHttpResponse) {
        val bodyBytes = ByteArray(response.content().readableBytes())
        response.content().getBytes(response.content().readerIndex(), bodyBytes)
        val responsePacket = InterceptedPacket(
            sourceIp = requestPacket.host, sourcePort = 0,
            direction = InterceptedPacket.Direction.RESPONSE, method = requestPacket.method,
            host = requestPacket.host, path = requestPacket.path, protocol = requestPacket.protocol,
            statusCode = response.status().code(),
            headers = response.headers().associate { it.key to it.value }.toMutableMap(),
            body = bodyBytes, isRequest = false
        )
        ctx.channel().eventLoop().execute {
            kotlinx.coroutines.runBlocking {
                var modified = proxy.enqueueForIntercept(responsePacket)
                if (proxy.mode == InterceptMode.AUTO_INJECT) modified = injector.inject(modified)
                val modifiedResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(modified.statusCode ?: 200), Unpooled.wrappedBuffer(modified.body))
                modified.headers.forEach { (k, v) -> modifiedResponse.headers().set(k, v) }
                modifiedResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, modified.body.size)
                inboundChannel.writeAndFlush(modifiedResponse).addListener(ChannelFutureListener.CLOSE)
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) { ctx.close(); inboundChannel.close() }
}
