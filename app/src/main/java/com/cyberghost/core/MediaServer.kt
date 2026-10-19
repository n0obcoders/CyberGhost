package com.cyberghost.core

import android.content.Context
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedWriteHandler
import java.io.File
import java.net.URLConnection

class MediaServer(
    private val port: Int = 9090,
    private val context: Context,
    private val hookJs: String
) {
    private val bossGroup = NioEventLoopGroup(1)
    private val workerGroup = NioEventLoopGroup(2)
    val mediaDir: File = File(context.filesDir, "media").also { it.mkdirs() }

    fun start() {
        ServerBootstrap().group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(HttpServerCodec(), HttpObjectAggregator(65_536), ChunkedWriteHandler(), MediaHandler(mediaDir, hookJs))
                }
            }).bind(port).sync()
    }

    fun stop() { bossGroup.shutdownGracefully(); workerGroup.shutdownGracefully() }
    fun addMedia(name: String, data: ByteArray) { File(mediaDir, name).writeBytes(data) }
    fun listMedia(): List<String> = mediaDir.listFiles()?.map { it.name } ?: emptyList()
    fun removeMedia(name: String) { File(mediaDir, name).delete() }
}

class MediaHandler(
    private val mediaDir: File,
    private val hookJs: String
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        val path = request.uri().removePrefix("/").split("?").first()
        when {
            path == "hook.js" -> serveContent(ctx, hookJs.toByteArray(), "application/javascript", "hook.js")
            path == "list" -> {
                val files = mediaDir.listFiles()?.map { "\"${it.name}\"" } ?: emptyList()
                serveContent(ctx, "[${files.joinToString(",")}]".toByteArray(), "application/json", "list")
            }
            else -> {
                val file = File(mediaDir, path)
                if (file.exists() && file.parentFile == mediaDir) {
                    val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
                    serveContent(ctx, file.readBytes(), mime, file.name)
                } else serve404(ctx)
            }
        }
    }

    private fun serveContent(ctx: ChannelHandlerContext, data: ByteArray, mime: String, name: String) {
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(data))
        response.headers().apply {
            set(HttpHeaderNames.CONTENT_TYPE, mime); set(HttpHeaderNames.CONTENT_LENGTH, data.size)
            set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*"); set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
        }
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }

    private fun serve404(ctx: ChannelHandlerContext) {
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, Unpooled.wrappedBuffer("not found".toByteArray()))
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 9)
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }
}
