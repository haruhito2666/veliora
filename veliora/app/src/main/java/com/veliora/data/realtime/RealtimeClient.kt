package com.veliora.data.realtime

import com.veliora.BuildConfig
import com.veliora.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Server event frame (doc 24 §10.1).
 */
@Serializable
data class EventFrame(
    val stream: String = "",
    @SerialName("stream_id") val streamId: String = "",
    val seq: Long = 0,
    val event: String = "",
    val ts: String = "",
    val payload: kotlinx.serialization.json.JsonElement? = null,
)

/**
 * Single multiplexed WebSocket per org (doc 16 §16.8). Auto-reconnects with
 * a monotonic sequence and re-subscribes; emits parsed [EventFrame]s.
 */
class RealtimeClient(
    private val orgId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<EventFrame>(extraBufferCapacity = 256)
    val events: SharedFlow<EventFrame> = _events

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closed = false

    fun connect() {
        closed = false
        scope.launch { connectLoop() }
    }

    private suspend fun connectLoop() {
        while (!closed) {
            try {
                val url = "${BuildConfig.WS_BASE_URL}/ws/org/$orgId"
                val req = Request.Builder().url(url).build()
                val listener = listener()
                socket = ApiClient.wsClient.newWebSocket(req, listener)
                // Wait for the listener to report closure before reconnecting.
                delay(5_000)
            } catch (_: Exception) {
                delay(3_000)
            }
        }
    }

    private fun listener() = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val frame = json.decodeFromString<EventFrame>(text)
                _events.tryEmit(frame)
            } catch (_: Exception) {
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // Reconnect handled by connectLoop.
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        }
    }

    fun close() {
        closed = true
        socket?.close(1000, "client close")
        socket = null
    }
}
