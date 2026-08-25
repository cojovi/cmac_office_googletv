package com.cmac.opscommand.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the CMAC_Office_dash Express API. Every field carries a
 * default so a partial or evolving payload degrades gracefully instead of
 * throwing — the dashboard must never go blank because one key moved.
 */

@Serializable
data class JobCounts(
    @SerialName("gutterCount") val gutters: Int = 0,
    @SerialName("reroofsCount") val reroofs: Int = 0,
    @SerialName("garageDoorsCount") val garageDoors: Int = 0,
    @SerialName("tractRoofingCount") val tractRoofing: Int = 0,
    @SerialName("totalJobs") val totalJobs: Int = 0,
    val updatedAt: String? = null,
) {
    companion object {
        val EMPTY = JobCounts()
    }
}

@Serializable
data class Job(
    @SerialName("job_number") val jobNumber: String = "",
    val address: String = "",
    val community: String = "",
    val office: String = "",
    val stage: String = "",
    @SerialName("work_order_types") val workOrderTypes: List<String> = emptyList(),
    val crews: List<String> = emptyList(),
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class JobsToday(
    val jobs: List<Job> = emptyList(),
    val total: Int = 0,
    val updatedAt: String? = null,
)

@Serializable
data class Mention(
    val user: String = "",
    val text: String = "",
    val timestamp: String? = null,
    val channel: String? = null,
    val permalink: String? = null,
)

@Serializable
data class MentionsResponse(
    val mentions: List<Mention> = emptyList(),
)

/** Envelope for the `/api/events` SSE channel. */
@Serializable
data class SseEnvelope(
    val type: String = "",
    val mention: Mention? = null,
    val data: JobCounts? = null,
)

/** Connection health shown in the header status strip. */
enum class LinkState { CONNECTING, LIVE, DOWN }
