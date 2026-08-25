package com.cmac.opscommand.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The web dashboard leaned on JavaScript's very forgiving `new Date(value)` for
 * both Bolt's `updatedAt` and Slack's mention timestamps. `Instant.parse` is far
 * stricter, so this recreates that tolerance explicitly: ISO-8601 with or
 * without an offset, epoch millis, and Slack's `"1712345678.000200"` second.
 */
object TimeUtil {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val clockFmt = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val fullFmt = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

    fun parse(raw: String?): Instant? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null

        // ISO-8601 with offset / Z
        runCatching { return OffsetDateTime.parse(s).toInstant() }
        runCatching { return Instant.parse(s) }
        // ISO-8601 without offset — interpret in the TV's own zone
        runCatching { return LocalDateTime.parse(s).atZone(zone).toInstant() }

        // Numeric: epoch millis, or epoch seconds (Slack style, may be fractional)
        val num = s.toDoubleOrNull()
        if (num != null && num > 0) {
            return if (num > 1e11) Instant.ofEpochMilli(num.toLong())
            else Instant.ofEpochMilli((num * 1000.0).toLong())
        }
        return null
    }

    /** `HH:mm` — matches `fmtTime()` (`hour12: false`). */
    fun hhmm(raw: String?): String =
        parse(raw)?.atZone(zone)?.format(timeFmt).orEmpty()

    /** `MMM d, HH:mm` — matches `fmtTimeFull()`. */
    fun full(raw: String?): String =
        parse(raw)?.atZone(zone)?.format(fullFmt).orEmpty()

    fun clock(now: Instant): String = now.atZone(zone).format(clockFmt)

    /** `MON, APR 20, 2026` — matches the header's uppercased locale date. */
    fun headerDate(now: Instant): String =
        now.atZone(zone).format(dateFmt).uppercase(Locale.US)

    /** Mirrors `isNewMessage()`: anything inside the last three hours. */
    fun isFresh(raw: String?, withinMs: Long = 3 * 60 * 60 * 1000L): Boolean {
        val t = parse(raw) ?: return false
        val delta = System.currentTimeMillis() - t.toEpochMilli()
        return delta in 0..withinMs
    }

    /** Sort key for newest-first ordering; unparseable timestamps sink. */
    fun sortKey(raw: String?): Long = parse(raw)?.toEpochMilli() ?: Long.MIN_VALUE
}
