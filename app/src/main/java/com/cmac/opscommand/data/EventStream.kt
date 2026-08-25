package com.cmac.opscommand.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/** What the SSE channel can hand back to the repository. */
sealed interface StreamEvent {
    data object Connected : StreamEvent
    data object Disconnected : StreamEvent
    data class NewMention(val mention: Mention) : StreamEvent
    data class Counts(val counts: JobCounts) : StreamEvent
}

/**
 * `/api/events` as a cold [Flow]. The server pushes Slack mentions relayed off
 * its WebSocket plus a refreshed job-count snapshot every 5 minutes.
 *
 * Reconnection is handled by the collector (see DashboardRepository) so that
 * backoff policy lives in one place rather than being buried in a callback.
 */
fun eventStream(
    baseUrl: String,
    client: OkHttpClient,
): Flow<StreamEvent> = callbackFlow {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    val request = Request.Builder()
        .url(baseUrl.trimEnd('/') + "/api/events")
        .header("Accept", "text/event-stream")
        .header("Cache-Control", "no-cache")
        .build()

    val listener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            trySend(StreamEvent.Connected)
        }

        override fun onEvent(
            eventSource: EventSource,
            id: String?,
            type: String?,
            data: String,
        ) {
            val envelope = runCatching { json.decodeFromString<SseEnvelope>(data) }.getOrNull()
                ?: return
            when (envelope.type) {
                "mention" -> envelope.mention?.let { trySend(StreamEvent.NewMention(it)) }
                "jobCounts" -> envelope.data?.let { trySend(StreamEvent.Counts(it)) }
                // "connected" is already covered by onOpen
            }
        }

        override fun onClosed(eventSource: EventSource) {
            trySend(StreamEvent.Disconnected)
            close()
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            trySend(StreamEvent.Disconnected)
            close()
        }
    }

    val source = EventSources.createFactory(client).newEventSource(request, listener)
    awaitClose { source.cancel() }
}
