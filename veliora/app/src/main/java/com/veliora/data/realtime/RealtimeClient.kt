package com.veliora.data.realtime

import android.util.Log
import com.veliora.BuildConfig
import com.veliora.data.api.ApiClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.atomic.AtomicInteger

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
 * One authenticated SSE stream per organization.
 *
 * The Sofuu backend exposes live events at
 * GET /v1/organizations/{orgId}/events. OkHttp's SSE client can set the Clerk
 * Authorization header (unlike browser EventSource), so no query-string token
 * is used. The backend closes the stream when the JWT expires; a reconnect
 * fetches a fresh token through ApiClient and resumes from the next event.
 */
class RealtimeClient(
    private val orgId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<EventFrame>(extraBufferCapacity = 256)
    val events: SharedFlow<EventFrame> = _events

    private val json = Json { ignoreUnknownKeys = true }
    private val factory by lazy { EventSources.createFactory(ApiClient.sseClient) }
    private val attempt = AtomicInteger(0)

    @Volatile private var source: EventSource? = null
    @Volatile private var closed = false
    private var job: Job? = null

    fun connect() {
        if (job?.isActive == true) return
        closed = false
        job = scope.launch { connectLoop() }
    }

    private suspend fun connectLoop() {
        while (!closed) {
            val disconnected = CompletableDeferred<Unit>()
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL.trimEnd('/')}/v1/organizations/$orgId/events")
                .header("Accept", "text/event-stream")
                .build()

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    attempt.set(0)
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    if (data.isBlank()) return
                    runCatching { json.decodeFromString<EventFrame>(data) }
                        .onSuccess { _events.tryEmit(it) }
                        .onFailure { Log.w(TAG, "Ignoring malformed SSE frame", it) }
                }

                override fun onClosed(eventSource: EventSource) {
                    disconnected.complete(Unit)
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    if (response?.code == 401) {
                        Log.w(TAG, "SSE rejected the Clerk token; reconnecting after refresh")
                    } else if (t != null) {
                        Log.w(TAG, "SSE connection failed", t)
                    }
                    disconnected.complete(Unit)
                }
            }

            source = factory.newEventSource(request, listener)
            disconnected.await()
            source?.cancel()
            source = null

            if (closed) break
            val failures = attempt.incrementAndGet().coerceAtMost(6)
            delay((1_000L shl (failures - 1)).coerceAtMost(30_000L))
        }
    }

    fun close() {
        closed = true
        source?.cancel()
        source = null
        job?.cancel()
        job = null
    }

    private companion object {
        const val TAG = "RealtimeClient"
    }
}
