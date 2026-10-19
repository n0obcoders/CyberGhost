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
import java.time.Instant

/**
 * Transparent HTTP server on port 80.
 * When DNS spoofs all domains to gateway, browsers hit us directly.
 * We read the Host header and forward to the real server.
 */
class TransparentHttpServer(
    private val port: Int = 80,
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
            Log.i("CG2", "Transparent HTTP server on port $port")
        } catch (e: Exception) {
            Log.e("CG2", "Transparent HTTP failed to bind port $port", e)
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
            val host = req.headers().get(HttpHeaderNames.HOST) ?: return

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

            // forward to real server
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
                .addListener { future ->
                    if (future.isSuccess) {
                        (future as ChannelFuture).channel().writeAndFlush(outReq)
                    } else {
                        Log.e("CG2", "Transparent forward failed to $realHost:$realPort")
                        ctx.close()
                    }
                }
        } catch (e: Exception) {
            Log.e("CG2", "Transparent HTTP error", e)
            ctx.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.e("CG2", "Transparent HTTP exception", cause)
        ctx.close()
    }
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
 * Transparent HTTPS server on port 443.
 * Accepts TLS connections, uses SNI or first Client Hello to determine target,
 * does MITM with dynamic cert, forwards to real server.
 */
class TransparentHttpsServer(
    private val port: Int = 443,
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
            Log.i("CG2", "Transparent HTTPS server on port $port")
        } catch (e: Exception) {
            Log.e("CG2", "Transparent HTTPS failed to bind port $port", e)
        }
    }

    fun stop() {
        try { bossGroup?.shutdownGracefully(); workerGroup?.shutdownGracefully() } catch (_: Exception) {}
        bossGroup = null; workerGroup = null
    }
}

/**
 * Reads the TLS ClientHello to extract SNI hostname,
 * then sets up MITM with a cert for that host.
 */
class SniExtractor(
    private val certManager: CertManager,
    private val proxy: InterceptProxy,
    private val injector: ContentInjector
) : ChannelInboundHandlerAdapter() {

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg !is io.netty.buffer.ByteBuf) { ctx.fireChannelRead(msg); return }

        // need at least 5 bytes to check TLS record header
        if (msg.readableBytes() < 5) { ctx.fireChannelRead(msg); return }

        val readerIndex = msg.readerIndex()

        // check if TLS handshake (0x16 = handshake, 0x03 = SSL 3.0+)
        val contentType = msg.getByte(readerIndex).toInt() and 0xFF
        if (contentType != 0x16) { ctx.fireChannelRead(msg); return }

        // try to extract SNI
        val hostname = extractSni(msg) ?: "unknown"
        Log.d("CG2", "TLS SNI: $hostname")

        // remove ourselves, set up MITM pipeline
        val pipeline = ctx.pipeline()
        pipeline.remove(this)

        try {
            val keyManagers = certManager.getKeyManagerForHost(hostname)
            val sslCtx = SslContextBuilder.forServer(keyManagers[0]).build()
            pipeline.addLast("ssl", sslCtx.newHandler(ctx.alloc()))
            pipeline.addLast(HttpServerCodec())
            pipeline.addLast(HttpObjectAggregator(4_194_304))
            pipeline.addLast(TransparentMitmHandler(hostname, proxy, certManager, injector))
        } catch (e: Exception) {
            Log.e("CG2", "MITM setup failed for $hostname", e)
            ctx.close()
            msg.release()
            return
        }

        // re-fire the buffered bytes into the new pipeline
        ctx.fireChannelRead(msg)
    }

    private fun extractSni(buf: io.netty.buffer.ByteBuf): String? {
        try {
            val start = buf.readerIndex()
            // TLS record: type(1) + version(2) + length(2) + handshake
            if (buf.readableBytes() < 43) return null

            val recordLen = buf.getUnsignedShort(start + 3)
            if (buf.readableBytes() < 5 + recordLen) return null

            // handshake type should be ClientHello (1)
            val hsType = buf.getByte(start + 5).toInt() and 0xFF
            if (hsType != 1) return null

            // skip: hs_type(1) + hs_length(3) + client_version(2) + random(32) = 38
            var pos = start + 5 + 38

            // session ID
            if (pos >= start + 5 + recordLen) return null
            val sessionIdLen = buf.getByte(pos).toInt() and 0xFF
            pos += 1 + sessionIdLen

            // cipher suites
            if (pos + 2 > start + 5 + recordLen) return null
            val cipherLen = buf.getUnsignedShort(pos)
            pos += 2 + cipherLen

            // compression methods
            if (pos >= start + 5 + recordLen) return null
            val compLen = buf.getByte(pos).toInt() and 0xFF
            pos += 1 + compLen

            // extensions
            if (pos + 2 > start + 5 + recordLen) return null
            val extLen = buf.getUnsignedShort(pos)
            pos += 2

            val extEnd = pos + extLen
            while (pos + 4 <= extEnd) {
                val extType = buf.getUnsignedShort(pos)
                val extDataLen = buf.getUnsignedShort(pos + 2)
                pos += 4

                if (extType == 0) { // SNI extension
                    // SNI list length(2) + type(1) + name length(2)
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
                host = targetHost,
                path = req.uri(),
                protocol = "HTTPS",
                headers = req.headers().associate { it.key to it.value }.toMutableMap(),
                body = bodyBytes,
                isRequest = true
            )

            val modified = proxy.processPacket(packet)

            // connect to real server over TLS
            val outboundSsl = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build()

            val outReq = DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.valueOf(modified.method ?: "GET"),
                modified.path,
                Unpooled.wrappedBuffer(modified.body)
            )
            modified.headers.forEach { (k, v) -> outReq.headers().set(k, v) }
            // make sure Host header is set to real host
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
                .addListener { future ->
                    if (future.isSuccess) {
                        (future as ChannelFuture).channel().writeAndFlush(outReq)
                    } else {
                        Log.e("CG2", "MITM forward failed to $targetHost")
                        ctx.close()
                    }
                }
        } catch (e: Exception) {
            Log.e("CG2", "MITM handler error", e)
            ctx.close()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.e("CG2", "MITM exception", cause)
        ctx.close()
    }
}
