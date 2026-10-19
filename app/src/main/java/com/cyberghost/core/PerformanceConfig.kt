package com.cyberghost.core

import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
import io.netty.util.ResourceLeakDetector

object PerformanceConfig {
    fun apply() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED)
        System.setProperty("io.netty.allocator.type", "pooled")
        System.setProperty("io.netty.allocator.numDirectArenas", Runtime.getRuntime().availableProcessors().coerceAtMost(4).toString())
    }
    fun configureServer(b: io.netty.bootstrap.ServerBootstrap): io.netty.bootstrap.ServerBootstrap {
        return b.option(ChannelOption.SO_BACKLOG, 256).option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .childOption(ChannelOption.SO_KEEPALIVE, true).childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(32 * 1024, 64 * 1024))
    }
}
