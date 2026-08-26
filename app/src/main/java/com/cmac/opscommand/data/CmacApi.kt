package com.cmac.opscommand.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Thin client over the existing CMAC_Office_dash Express endpoints.
 *
 * The TV app is a native replacement for the browser, not a reimplementation of
 * the backend: Bolt credentials, Mapbox geocoding and the Slack WebSocket relay
 * all stay server-side exactly as they are today.
 */
class CmacApi(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val configured: Boolean get() = baseUrl.isNotBlank()

    /** Fast: only needs Bolt's /schedules, no per-job work. */
    suspend fun jobCounts(): JobCounts = get("/api/jobs/counts", QUICK_TIMEOUT_S)

    /**
     * Slow by nature. On a cold server cache this fans out to one Bolt detail
     * request *and* one Mapbox geocode per job; measured against production
     * (359 jobs) it ran past five minutes, and Bolt rate-limits hard enough
     * that server-side retries stretch it further.
     *
     * The browser this replaces used `fetch()`, which imposes no such deadline,
     * so it simply waited. A short client timeout here would abandon every cold
     * start and leave the board showing "DATA STALE" even though the server was
     * working normally — hence the deliberately generous ceiling.
     */
    suspend fun jobsToday(): JobsToday = get("/api/jobs/today", ROSTER_TIMEOUT_S)

    suspend fun mentions(): MentionsResponse = get("/api/slack/mentions", QUICK_TIMEOUT_S)

    private suspend inline fun <reified T> get(path: String, readTimeoutSeconds: Long): T =
        withContext(Dispatchers.IO) {
            require(configured) { "CMAC_SERVER_URL is not configured" }
            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .header("Accept", "application/json")
                .build()
            // Per-call timeout. newBuilder() shares the connection pool and
            // dispatcher, so this is cheap rather than a second client.
            val scoped = client.newBuilder()
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(readTimeoutSeconds + 30, TimeUnit.SECONDS)
                .build()
            scoped.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("HTTP ${resp.code} on $path")
                json.decodeFromString<T>(body)
            }
        }

    companion object {
        /** Endpoints that answer from cache or a single upstream call. */
        const val QUICK_TIMEOUT_S = 60L

        /** Cold-cache roster build; see [jobsToday]. */
        const val ROSTER_TIMEOUT_S = 600L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(QUICK_TIMEOUT_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** Long-lived client for SSE: streaming must never hit a read timeout. */
        fun streamingClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
