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
        scope.launch { fastPollLoop() }
        scope.launch { rosterPollLoop() }
        scope.launch { streamLoop() }
    }

    // -- REST polling ------------------------------------------------------

    /**
     * Cheap endpoints — counts and Slack mentions — on a short cadence.
     *
     * `/api/jobs/counts` only needs Bolt's paginated `/schedules`, a handful of
     * requests, so polling it often is safe and keeps the headline figures live.
     *
     * After a failure it retries far sooner than the steady-state interval:
     * waiting the full interval meant a brief server blip left "DATA STALE" on
     * the wall for minutes after the server was already answering again.
     */
    private suspend fun fastPollLoop() {
        var failures = 0
        while (true) {
            val ok = refreshFast()
            failures = if (ok) 0 else (failures + 1).coerceAtMost(4)

            val wait = if (failures == 0) {
                FAST_REFRESH_MS
            } else {
                (RETRY_BASE_MS shl (failures - 1)).coerceAtMost(FAST_REFRESH_MS)
            }
            // Wake early if something (e.g. the SSE stream reconnecting) tells
            // us the server is worth talking to again.
            withTimeoutOrNull(wait) { refreshRequests.receive() }
        }
    }

    /**
     * The job roster on a deliberately slow cadence.
     *
     * `/api/jobs/today` is enormously more expensive than it looks: on a cache
     * miss the server issues one Bolt detail request *and* one Mapbox geocode
     * per job. Measured against production that is ~359 upstream calls taking
     * over five minutes, and Bolt rate-limits (HTTP 429) well before it
     * finishes.
     *
     * The server caches for 5 minutes, so polling this every 5 minutes would
     * miss the cache every single time and re-trigger the whole fan-out —
     * thousands of Bolt calls an hour, keeping the account permanently
     * rate-limited. The web app avoided that only by accident: it fetched the
     * roster once per page load and never again, which is also why a TV left up
     * all day showed stale assignments.
     *
     * Thirty minutes is the compromise: comfortably inside the server's cache
     * behaviour, respectful of the upstream quota, and far fresher than "until
     * somebody reloads the browser". Today's assignments do not churn faster
     * than that.
     */
    private suspend fun rosterPollLoop() {
        var failures = 0
        while (true) {
            val ok = refreshRoster()
            failures = if (ok) 0 else (failures + 1).coerceAtMost(3)

            // Back off hard on failure — a 429 means the upstream is already
            // over budget and retrying quickly makes it strictly worse.
            val wait = if (failures == 0) {
                ROSTER_REFRESH_MS
            } else {
                (ROSTER_RETRY_BASE_MS shl (failures - 1)).coerceAtMost(ROSTER_REFRESH_MS)
            }
            delay(wait)
        }
    }

    /**
     * Counts + mentions, fetched concurrently, each publishing the moment it
     * lands rather than being batched behind the slower of the two.
     *
     * Concurrency is also what makes an outage visible promptly: run
     * sequentially, two connect timeouts mean the header would keep claiming
     * "DATA LIVE" long after the server had gone away.
     */
    private suspend fun refreshFast(): Boolean = coroutineScope {
        val countsOk = async {
            runCatching { api.jobCounts() }
                .onFailure { Log.w(TAG, "counts: ${it.message}") }
                .getOrNull()
                ?.also { c -> _state.update { it.copy(counts = c) } } != null
        }

        val mentionsOk = async {
            runCatching { api.mentions() }
                .onFailure { Log.w(TAG, "mentions: ${it.message}") }
                .getOrNull()
                ?.also { m -> mergeMentions(m.mentions) } != null
        }

        val anyOk = countsOk.await() or mentionsOk.await()
        publishDataState(anyOk)
        anyOk
    }

    /** The expensive roster fetch; see [rosterPollLoop] for the cadence rationale. */
    private suspend fun refreshRoster(): Boolean {
        val jobs = runCatching { api.jobsToday() }
            .onFailure { Log.w(TAG, "jobsToday: ${it.message}") }
            .getOrNull()
            ?: return false

        _state.update {
            it.copy(jobs = jobs.jobs, jobsUpdatedAt = jobs.updatedAt, jobsLoaded = true)
        }
        persistCache()
        return true
    }

    /**
     * Merge without letting a slow REST response clobber newer mentions that
     * arrived over SSE while it was in flight.
     */
    private fun mergeMentions(incoming: List<Mention>) {
        _state.update { cur ->
            val merged = (cur.mentions + incoming)
                .distinctBy { x -> x.user + "|" + x.timestamp + "|" + x.text }
                .sortedByDescending { x -> TimeUtil.sortKey(x.timestamp) }
                .take(60)
            cur.copy(mentions = merged)
        }
    }

    private suspend fun publishDataState(anyOk: Boolean) {
        _state.update {
            it.copy(
                dataState = if (anyOk) DataState.FRESH else DataState.STALE,
                lastError = if (anyOk) null else "SERVER UNREACHABLE",
            )
        }
        if (anyOk) persistCache()
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
                            // Only a genuine *recovery* warrants an extra fetch.
                            // The very first connect is CONNECTING -> LIVE, and
                            // the poll loops have already fired by then, so
                            // treating that as a recovery just duplicated both
                            // cheap requests a few seconds into every startup.
                            val recovered = _state.value.link == LinkState.DOWN
                            _state.update { it.copy(link = LinkState.LIVE) }
                            // The stream coming back is proof the server is
                            // reachable, so pull fresh REST data immediately
                            // instead of sitting on stale numbers until the
                            // next poll window.
                            if (recovered) refreshRequests.trySend(Unit)
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
        /** Counts + mentions: cheap upstream, safe to poll often. */
        const val FAST_REFRESH_MS = 5 * 60 * 1000L

        /** First retry after a failed fast refresh; doubles up to [FAST_REFRESH_MS]. */
        const val RETRY_BASE_MS = 20 * 1000L

        /**
         * Job roster: ~one Bolt request + one geocode *per job* on a cache miss.
         * Deliberately slow to stay inside the upstream rate limit — see
         * [rosterPollLoop].
         */
        const val ROSTER_REFRESH_MS = 30 * 60 * 1000L

        /**
         * First retry after a failed roster fetch. Starts high because the most
         * likely failure is HTTP 429, where retrying quickly is actively harmful.
         */
        const val ROSTER_RETRY_BASE_MS = 5 * 60 * 1000L
    }
}
