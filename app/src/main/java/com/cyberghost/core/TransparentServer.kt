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
import java.net.InetSocketAddress

/**
 * Transparent HTTP server on port 8081.
 * DNS spoofs all domains to gateway — browsers connect here.
 * We read Host header and forward to real server.
 */
class TransparentHttpServer(
    private val port: Int = 8081,
    private val proxy: InterceptProxy,
    private val injector: ContentInjector
) {
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null

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
                            TransparentHttpHandler(proxy, injector)
                        )
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(port).sync()
            Log.i("CG2", "Transparent HTTP on port $port")
        } catch (e: Exception) {
            Log.e("CG2", "Transparent HTTP failed on port $port: ${e.message}")
        }
    }

    fun stop() {
        try { bossGroup?.shutdownGracefully(); workerGroup?.shutdownGracefully() } catch (_: Exception) {}
        bossGroup = null; workerGroup = null
    }
}

class TransparentHttpHandler(
    private val proxy: InterceptProxy,
    private val injector: ContentInjector
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        try {
            val clientAddr = ctx.channel().remoteAddress() as? InetSocketAddress ?: return
            val host = req.headers().get(HttpHeaderNames.HOST) ?: run { ctx.close(); return }
            val bodyBytes = ByteArray(req.content().readableBytes())
            req.content().getBytes(req.content().readerIndex(), bodyBytes)

            val packet = InterceptedPacket(
                sourceIp = clientAddr.address.hostAddress ?: "unknown",
                sourcePort = clientAddr.port,
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

            val hostParts = host.split(":")
            val realHost = hostParts[0]
            val realPort = hostParts.getOrNull(1)?.toIntOrNull() ?: 80

            val outReq = DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.valueOf(modified.method ?: "GET"),
                modified.path,
                Unpooled.wrappedBuffer(modified.body)
            )
            modified.headers.forEach { (k, v) -> outReq.headers().set(k, v) }
            outReq.headers().set(HttpHeaderNames.HOST, host)

            Bootstrap().group(ctx.channel().eventLoop())
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(
                            HttpClientCodec(),
                            HttpObjectAggregator(4_194_304),
                            TransparentResponseHandler(ctx.channel(), proxy, injector, modified)
                        )
                    }
                })
                .connect(realHost, realPort)
                .addListener { f ->
                    if (f.isSuccess) (f as ChannelFuture).channel().writeAndFlush(outReq)
                    else ctx.close()
                }
        } catch (e: Exception) {
            Log.e("CG2", "Transparent HTTP handler error", e)
            ctx.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) { ctx.close() }
}

class TransparentResponseHandler(
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
                direction = InterceptedPacket.Direction.RESPONSE,
                method = requestPacket.method,
                host = requestPacket.host, path = requestPacket.path,
                protocol = "HTTP",
                statusCode = response.status().code(),
                headers = response.headers().associate { it.key to it.value }.toMutableMap(),
                body = bodyBytes, isRequest = false
            )
            responsePacket = proxy.processPacket(responsePacket)
            if (proxy.mode == InterceptMode.AUTO_INJECT) responsePacket = injector.inject(responsePacket)

            val outResp = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(responsePacket.statusCode ?: 200),
                Unpooled.wrappedBuffer(responsePacket.body)
            )
            responsePacket.headers.forEach { (k, v) -> outResp.headers().set(k, v) }
            outResp.headers().set(HttpHeaderNames.CONTENT_LENGTH, responsePacket.body.size)
            inboundChannel.writeAndFlush(outResp).addListener(ChannelFutureListener.CLOSE)
        } catch (e: Exception) {
            Log.e("CG2", "Transparent response error", e)
            ctx.close(); inboundChannel.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close(); inboundChannel.close()
    }
}

/**
 * Transparent HTTPS server on port 8443.
 * SNI-based MITM — reads ClientHello to get hostname,
 * presents dynamic cert, forwards over real TLS.
 */
class TransparentHttpsServer(
    private val port: Int = 8443,
    private val proxy: InterceptProxy,
    private val certManager: CertManager,
    private val injector: ContentInjector
) {
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null

    fun start() {
        try {
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(2)
            ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(SniExtractor(certManager, proxy, injector))
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(port).sync()
            Log.i("CG2", "Transparent HTTPS on port $port")
        } catch (e: Exception) {
            Log.e("CG2", "Transparent HTTPS failed on port $port: ${e.message}")
        }
    }

    fun stop() {
        try { bossGroup?.shutdownGracefully(); workerGroup?.shutdownGracefully() } catch (_: Exception) {}
        bossGroup = null; workerGroup = null
    }
}

class SniExtractor(
    private val certManager: CertManager,
    private val proxy: InterceptProxy,
    private val injector: ContentInjector
) : ChannelInboundHandlerAdapter() {

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg !is io.netty.buffer.ByteBuf) { ctx.fireChannelRead(msg); return }
        if (msg.readableBytes() < 5) { ctx.fireChannelRead(msg); return }

        val hostname = extractSni(msg) ?: "unknown"
        Log.d("CG2", "SNI: $hostname")

        val pipeline = ctx.pipeline()
        pipeline.remove(this)

        try {
            val keyManagers = certManager.getKeyManagerForHost(hostname)
            val sslCtx = SslContextBuilder.forServer(keyManagers[0]).build()
            pipeline.addFirst("ssl", sslCtx.newHandler(ctx.alloc()))
            pipeline.addLast(HttpServerCodec())
            pipeline.addLast(HttpObjectAggregator(4_194_304))
            pipeline.addLast(TransparentMitmHandler(hostname, proxy, certManager, injector))
        } catch (e: Exception) {
            Log.e("CG2", "SNI MITM setup failed for $hostname", e)
            ctx.close(); msg.release(); return
        }
        ctx.fireChannelRead(msg)
    }

    private fun extractSni(buf: io.netty.buffer.ByteBuf): String? {
        try {
            val start = buf.readerIndex()
            if (buf.readableBytes() < 43) return null
            if (buf.getByte(start).toInt() and 0xFF != 0x16) return null
            if (buf.getByte(start + 5).toInt() and 0xFF != 1) return null
            var pos = start + 5 + 38
            if (pos >= buf.writerIndex()) return null
            val sessionIdLen = buf.getByte(pos).toInt() and 0xFF; pos += 1 + sessionIdLen
            if (pos + 2 > buf.writerIndex()) return null
            val cipherLen = buf.getUnsignedShort(pos); pos += 2 + cipherLen
            if (pos >= buf.writerIndex()) return null
            val compLen = buf.getByte(pos).toInt() and 0xFF; pos += 1 + compLen
            if (pos + 2 > buf.writerIndex()) return null
            val extLen = buf.getUnsignedShort(pos); pos += 2
            val extEnd = pos + extLen
            while (pos + 4 <= extEnd) {
                val extType = buf.getUnsignedShort(pos)
                val extDataLen = buf.getUnsignedShort(pos + 2); pos += 4
                if (extType == 0) {
                    if (pos + 5 > extEnd) return null
                    val nameLen = buf.getUnsignedShort(pos + 3)
                    if (pos + 5 + nameLen > extEnd) return null
                    val nameBytes = ByteArray(nameLen)
                    buf.getBytes(pos + 5, nameBytes)
                    return String(nameBytes)
                }
                pos += extDataLen
            }
        } catch (_: Exception) {}
        return null
    }
}

class TransparentMitmHandler(
    private val targetHost: String,
    private val proxy: InterceptProxy,
    private val certManager: CertManager,
    private val injector: ContentInjector
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        try {
            val clientAddr = ctx.channel().remoteAddress() as? InetSocketAddress
            val bodyBytes = ByteArray(req.content().readableBytes())
            req.content().getBytes(req.content().readerIndex(), bodyBytes)

            val packet = InterceptedPacket(
                sourceIp = clientAddr?.address?.hostAddress ?: "unknown",
                sourcePort = clientAddr?.port ?: 0,
                direction = InterceptedPacket.Direction.REQUEST,
                method = req.method().name(),
                host = targetHost, path = req.uri(), protocol = "HTTPS",
                headers = req.headers().associate { it.key to it.value }.toMutableMap(),
                body = bodyBytes, isRequest = true
            )
            val modified = proxy.processPacket(packet)

            val outboundSsl = SslContextBuilder.forClient()
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
                            outboundSsl.newHandler(ch.alloc(), targetHost, 443),
                            HttpClientCodec(),
                            HttpObjectAggregator(4_194_304),
                            TransparentResponseHandler(ctx.channel(), proxy, injector, modified)
                        )
                    }
                })
                .connect(targetHost, 443)
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
