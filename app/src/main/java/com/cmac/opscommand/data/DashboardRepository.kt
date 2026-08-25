package com.cmac.opscommand.data

import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "CmacRepo"

/** Freshness of the REST-polled data, shown in the header status strip. */
enum class DataState { SYNCING, FRESH, STALE }

/** Everything the UI needs, in one immutable snapshot. */
data class DashboardState(
    val configured: Boolean = true,
    val counts: JobCounts = JobCounts.EMPTY,
    val jobs: List<Job> = emptyList(),
    val jobsUpdatedAt: String? = null,
    val mentions: List<Mention> = emptyList(),
    val link: LinkState = LinkState.CONNECTING,
    val jobsLoaded: Boolean = false,
    val dataState: DataState = DataState.SYNCING,
    val lastError: String? = null,
)

/** Shape persisted to disk so a cold boot paints real data immediately. */
@Serializable
private data class CachedSnapshot(
    val counts: JobCounts = JobCounts.EMPTY,
    val jobs: List<Job> = emptyList(),
    val jobsUpdatedAt: String? = null,
    val mentions: List<Mention> = emptyList(),
)

class DashboardRepository(
    private val api: CmacApi,
    private val baseUrl: String,
    cacheDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cacheFile = File(cacheDir, "dashboard_snapshot.json")
    private val streamClient = CmacApi.streamingClient()

    private val _state = MutableStateFlow(DashboardState(configured = api.configured))
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    /** Nudges [pollLoop] to refresh now rather than waiting out its interval. */
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    fun start(scope: CoroutineScope) {
        if (!api.configured) {
            _state.update { it.copy(configured = false, link = LinkState.DOWN) }
            return
        }
        scope.launch { restoreCache() }
        scope.launch { pollLoop() }
        scope.launch { streamLoop() }
    }

    // -- REST polling ------------------------------------------------------

    /**
     * The web app fetched the roster exactly once at page load, so a TV left up
     * all day kept yesterday's — or this morning's — assignments on screen until
     * someone reloaded the browser. Re-polling on the server's own 5-minute
     * cache TTL keeps the board honest without adding load.
     *
     * After a failure it retries far sooner than the steady-state interval:
     * waiting the full 5 minutes meant a brief server blip left "DATA STALE" on
     * the wall for minutes after the server was already answering again.
     */
    private suspend fun pollLoop() {
        var failures = 0
        while (true) {
            val ok = refreshAll()
            failures = if (ok) 0 else (failures + 1).coerceAtMost(4)

            val wait = if (failures == 0) {
                REFRESH_MS
            } else {
                (RETRY_BASE_MS shl (failures - 1)).coerceAtMost(REFRESH_MS)
            }
            // Wake early if something (e.g. the SSE stream reconnecting) tells
            // us the server is worth talking to again.
            withTimeoutOrNull(wait) { refreshRequests.receive() }
        }
    }

    /**
     * The three endpoints are independent, so they are fetched concurrently.
     * Beyond being faster, this is what makes an outage visible promptly: run
     * sequentially, three 10-second connect timeouts mean the header would keep
     * claiming "DATA LIVE" for half a minute after the server had gone away.
     */
    private suspend fun refreshAll(): Boolean = coroutineScope {
        val countsD = async {
            runCatching { api.jobCounts() }
                .onFailure { Log.w(TAG, "counts: ${it.message}") }.getOrNull()
        }
        val jobsD = async {
            runCatching { api.jobsToday() }
                .onFailure { Log.w(TAG, "jobsToday: ${it.message}") }.getOrNull()
        }
        val mentionsD = async {
            runCatching { api.mentions() }
                .onFailure { Log.w(TAG, "mentions: ${it.message}") }.getOrNull()
        }

        val counts = countsD.await()
        val jobs = jobsD.await()
        val mentions = mentionsD.await()
        val anyOk = counts != null || jobs != null || mentions != null

        _state.update { cur ->
            cur.copy(
                counts = counts ?: cur.counts,
                jobs = jobs?.jobs ?: cur.jobs,
                jobsUpdatedAt = jobs?.updatedAt ?: cur.jobsUpdatedAt,
                jobsLoaded = cur.jobsLoaded || jobs != null,
                mentions = mentions?.mentions?.sortedByDescending { m -> TimeUtil.sortKey(m.timestamp) }
                    ?: cur.mentions,
                dataState = if (anyOk) DataState.FRESH else DataState.STALE,
                lastError = if (anyOk) null else "SERVER UNREACHABLE",
            )
        }
        if (anyOk) persistCache()
        anyOk
    }

    // -- SSE ---------------------------------------------------------------

    /**
     * Reconnect policy lives here rather than in the flow so it is visible in
     * one place. Matches the web client's 6-second retry, with a mild backoff
     * so a server that stays down does not get hammered all night.
     */
    private suspend fun streamLoop() {
        var attempt = 0
        while (true) {
            try {
                eventStream(baseUrl, streamClient).collect { ev ->
                    attempt = 0
                    when (ev) {
                        StreamEvent.Connected -> {
                            val wasDown = _state.value.link != LinkState.LIVE
                            _state.update { it.copy(link = LinkState.LIVE) }
                            // The stream coming back is proof the server is
                            // reachable, so pull fresh REST data immediately
                            // instead of sitting on stale numbers until the
                            // next poll window.
                            if (wasDown) refreshRequests.trySend(Unit)
                        }

                        StreamEvent.Disconnected -> {
                            _state.update { it.copy(link = LinkState.DOWN) }
                            // The stream dropping is a hint that the server may
                            // be gone, not proof — the REST API could still be
                            // answering. Verify instead of assuming, so the
                            // header only claims "DATA STALE" once a real fetch
                            // has actually failed.
                            refreshRequests.trySend(Unit)
                        }

                        is StreamEvent.Counts ->
                            _state.update { it.copy(counts = ev.counts) }

                        is StreamEvent.NewMention -> _state.update { cur ->
                            // Newest first, de-duplicated, bounded like the web app's 60.
                            val merged = (listOf(ev.mention) + cur.mentions)
                                .distinctBy { m -> m.user + "|" + m.timestamp + "|" + m.text }
                                .sortedByDescending { m -> TimeUtil.sortKey(m.timestamp) }
                                .take(60)
                            cur.copy(mentions = merged)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "sse: ${t.message}")
            }
            _state.update { it.copy(link = LinkState.DOWN) }
            attempt = (attempt + 1).coerceAtMost(5)
            delay(6_000L * attempt)
        }
    }

    // -- Disk cache --------------------------------------------------------

    private suspend fun restoreCache() = withContext(Dispatchers.IO) {
        val snap = runCatching {
            if (!cacheFile.exists()) return@runCatching null
            json.decodeFromString<CachedSnapshot>(cacheFile.readText())
        }.getOrNull() ?: return@withContext

        _state.update { cur ->
            // Never let stale disk data overwrite something already fetched live.
            if (cur.jobsLoaded) cur
            else cur.copy(
                counts = if (cur.counts == JobCounts.EMPTY) snap.counts else cur.counts,
                jobs = cur.jobs.ifEmpty { snap.jobs },
                jobsUpdatedAt = cur.jobsUpdatedAt ?: snap.jobsUpdatedAt,
                mentions = cur.mentions.ifEmpty { snap.mentions },
            )
        }
    }

    private suspend fun persistCache() = withContext(Dispatchers.IO) {
        runCatching {
            val s = _state.value
            cacheFile.writeText(
                json.encodeToString(
                    CachedSnapshot(s.counts, s.jobs, s.jobsUpdatedAt, s.mentions)
                )
            )
        }.onFailure { Log.w(TAG, "cache write: ${it.message}") }
    }

    companion object {
        /** Matches the server's own CACHE_TTL, so we never poll into a cold cache. */
        const val REFRESH_MS = 5 * 60 * 1000L

        /** First retry delay after a failed refresh; doubles up to [REFRESH_MS]. */
        const val RETRY_BASE_MS = 20 * 1000L
    }
}
