package com.cmac.opscommand.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing tests against the *real* payload shapes returned by the production
 * CMAC_Office_dash server (content sanitised, structure verbatim).
 *
 * These matter because the live API carries several fields the web client never
 * read — a Slack mention actually ships `type`, `user_id`, `channel_id`,
 * `raw_text` and a Slack-epoch `ts` alongside the fields used here — and a
 * strict parser would reject the whole response over any one of them.
 */
class ModelParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `parses a real slack mention with all its extra fields`() {
        // Exact key set observed from GET /api/slack/mentions in production.
        val raw = """
        {"mentions":[
          {"type":"mention","user":"Sample User","user_id":"U07H70D4YRW",
           "channel":"amaya_bot","channel_id":"C0A8112R59P",
           "text":"HAPPY CINCO DE MAYO","raw_text":"<@U0AU7UC6G1W> HAPPY CINCO DE MAYO",
           "ts":"1777979675.299179","timestamp":"2026-05-05T11:14:35.299Z",
           "permalink":"https://example.slack.com/archives/C0A8112R59P/p1777979675299179"}
        ]}
        """.trimIndent()

        val parsed = json.decodeFromString<MentionsResponse>(raw)
        assertEquals(1, parsed.mentions.size)
        val m = parsed.mentions[0]
        assertEquals("Sample User", m.user)
        assertEquals("HAPPY CINCO DE MAYO", m.text)
        assertEquals("amaya_bot", m.channel)
        assertTrue(m.permalink!!.startsWith("https://"))
        // The ISO timestamp must resolve; the Slack-epoch `ts` is ignored.
        assertTrue(TimeUtil.parse(m.timestamp) != null)
    }

    @Test
    fun `parses the real job counts payload`() {
        val raw = """
        {"gutterCount":38,"reroofsCount":0,"garageDoorsCount":3,
         "tractRoofingCount":54,"totalJobs":359,
         "updatedAt":"2026-08-26T01:41:50.353Z"}
        """.trimIndent()

        val c = json.decodeFromString<JobCounts>(raw)
        assertEquals(38, c.gutters)
        assertEquals(0, c.reroofs)
        assertEquals(3, c.garageDoors)
        assertEquals(54, c.tractRoofing)
        assertEquals(359, c.totalJobs)
        assertTrue(TimeUtil.parse(c.updatedAt) != null)
    }

    @Test
    fun `parses a job whose geocode failed`() {
        // The server emits nulls when Mapbox cannot resolve the address.
        val raw = """
        {"jobs":[
          {"job_number":"3298615","address":"714 Candace Dr.","community":"Coles Crossing",
           "office":"Dallas","stage":"Assigned","work_order_types":["Leak"],
           "crews":["Felix avalos","Luis Gutierrez"],"lat":null,"lng":null}
        ],"total":1,"updatedAt":"2026-08-26T01:41:50.353Z"}
        """.trimIndent()

        val r = json.decodeFromString<JobsToday>(raw)
        val j = r.jobs.single()
        assertNull(j.lat)
        assertNull(j.lng)
        assertEquals(2, j.crews.size)
        // A null-coordinate job must be excluded from the map, not plotted at 0,0.
        assertTrue(Geo.pointsOf(r.jobs).isEmpty())
    }

    @Test
    fun `survives a partial payload with fields missing entirely`() {
        // A dashboard must degrade, never blank out, if the API shape shifts.
        val counts = json.decodeFromString<JobCounts>("""{"totalJobs":12}""")
        assertEquals(12, counts.totalJobs)
        assertEquals(0, counts.gutters)
        assertNull(counts.updatedAt)

        val jobs = json.decodeFromString<JobsToday>("""{"jobs":[{"job_number":"1"}]}""")
        assertEquals("", jobs.jobs.single().address)
        assertTrue(jobs.jobs.single().workOrderTypes.isEmpty())

        val mentions = json.decodeFromString<MentionsResponse>("""{}""")
        assertTrue(mentions.mentions.isEmpty())
    }

    @Test
    fun `parses the sse envelopes the server broadcasts`() {
        val mention = json.decodeFromString<SseEnvelope>(
            """{"type":"mention","mention":{"user":"A","text":"hi","timestamp":"2026-08-26T01:00:00.000Z"}}"""
        )
        assertEquals("mention", mention.type)
        assertEquals("A", mention.mention?.user)

        val counts = json.decodeFromString<SseEnvelope>(
            """{"type":"jobCounts","data":{"totalJobs":359,"gutterCount":38}}"""
        )
        assertEquals("jobCounts", counts.type)
        assertEquals(359, counts.data?.totalJobs)

        // The handshake frame the server writes on connect.
        val hello = json.decodeFromString<SseEnvelope>("""{"type":"connected"}""")
        assertEquals("connected", hello.type)
        assertNull(hello.mention)
    }

    @Test
    fun `time util handles every timestamp format the server can emit`() {
        // ISO with Z (Bolt updatedAt / Slack timestamp)
        assertTrue(TimeUtil.parse("2026-08-26T01:41:50.353Z") != null)
        // Slack epoch-seconds with fraction (the `ts` field)
        assertTrue(TimeUtil.parse("1777979675.299179") != null)
        // Epoch millis
        assertTrue(TimeUtil.parse("1777979675299") != null)
        // Garbage and empties must not throw
        assertNull(TimeUtil.parse(null))
        assertNull(TimeUtil.parse(""))
        assertNull(TimeUtil.parse("not a date"))
    }
}
