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

    suspend fun jobCounts(): JobCounts = get("/api/jobs/counts")

    suspend fun jobsToday(): JobsToday = get("/api/jobs/today")

    suspend fun mentions(): MentionsResponse = get("/api/slack/mentions")

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        require(configured) { "CMAC_SERVER_URL is not configured" }
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code} on $path")
            json.decodeFromString<T>(body)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // /api/jobs/today geocodes every job on a cold cache; on a 380-job
            // day that legitimately takes a while, so allow generous headroom.
            .readTimeout(90, TimeUnit.SECONDS)
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
