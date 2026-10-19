package com.cyberghost.core

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.Instant

data class WebSocketFrame(
    val timestamp: Instant = Instant.now(),
    val sessionId: String,
    val direction: String, // CLIENT->SERVER or SERVER->CLIENT
    val host: String,
    val path: String,
    val opcode: String, // TEXT, BINARY, PING, PONG, CLOSE
    val payload: String,
    val payloadSize: Int
)

class WebSocketCapture {
    private val _frames = MutableSharedFlow<WebSocketFrame>(replay = 0, extraBufferCapacity = 1024)
    val frames: SharedFlow<WebSocketFrame> = _frames
    private val frameStore = mutableListOf<WebSocketFrame>()

    fun record(frame: WebSocketFrame) {
        frameStore.add(frame)
        if (frameStore.size > 2000) frameStore.removeAt(0)
        _frames.tryEmit(frame)
    }

    fun getAll(): List<WebSocketFrame> = frameStore.toList()
    fun clear() { frameStore.clear() }
    fun count(): Int = frameStore.size
}
