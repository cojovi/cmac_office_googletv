package com.cmac.opscommand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cmac.opscommand.data.DashboardState
import com.cmac.opscommand.data.Job
import com.cmac.opscommand.data.TimeUtil
import com.cmac.opscommand.ui.theme.Hud
import com.cmac.opscommand.ui.theme.data
import com.cmac.opscommand.ui.theme.hud
import com.cmac.opscommand.ui.theme.typeColor

/** Column widths from `.th-*` in the web stylesheet (design px). */
private const val W_NUM = 56
private const val W_ADDRESS = 340
private const val W_COMMUNITY = 200
private const val W_OFFICE = 160
private const val W_CREW = 200
private const val W_STATUS = 130
private const val ROW_H = 33

/**
 * Page 2 — Today's Active Assignments.
 *
 * The web version rendered all 381 rows into a container with `overflow:hidden`,
 * so everything past the first ~19 was simply unreachable on a TV. Here the
 * roster is paged, the page advances on its own, and the header states exactly
 * which slice is on screen.
 */
@Composable
fun JobsPage(
    state: DashboardState,
    subPage: Int,
    rowsPerPage: Int,
    onRowsMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {

        // ── Title bar ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudGlyph(color = Hud.Red, size = 16)
            Spacer(Modifier.width(12.dp))
            HudText("TODAY'S ACTIVE ASSIGNMENTS", hud(13f, FontWeight.SemiBold, 3f, Hud.Red))
            Spacer(Modifier.weight(1f))

            val total = state.jobs.size
            val pages = if (rowsPerPage > 0) ((total + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1) else 1
            if (pages > 1) {
                HudText(
                    text = "VIEW ${subPage + 1}/$pages",
                    style = hud(11f, FontWeight.SemiBold, 2f, Hud.Teal),
                )
                Spacer(Modifier.width(16.dp))
            }
            HudText("TOTAL: ", hud(11f, FontWeight.Normal, 2f, Hud.TextDim))
            HudText("$total", hud(11f, FontWeight.Bold, 2f, Color.White))
            HudText(" JOBS", hud(11f, FontWeight.Normal, 2f, Hud.TextDim))
            state.jobsUpdatedAt?.let {
                Spacer(Modifier.width(14.dp))
                HudText("AS OF " + TimeUtil.hhmm(it), hud(9f, FontWeight.Normal, 1f, Hud.TextMuted))
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Hud.Sep)
        )
        Spacer(Modifier.height(10.dp))

        // ── Table ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .tacticalCard()
        ) {
            TableHeader()

            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                // Tell the ViewModel how many rows actually fit, so paging maths
                // follow the real layout instead of a hardcoded guess. The 4 px
                // slack keeps the last row off the card's bottom bracket.
                val fit = ((maxHeight.value - 4f) / ROW_H).toInt().coerceAtLeast(1)
                onRowsMeasured(fit)

                when {
                    !state.jobsLoaded && state.jobs.isEmpty() ->
                        EmptyNotice("LOADING OPERATIONS DATA...", spacing = 4f, size = 12f)

                    state.jobs.isEmpty() ->
                        EmptyNotice("NO JOBS SCHEDULED FOR TODAY", spacing = 3f, size = 11f)

                    else -> {
                        val perPage = if (rowsPerPage > 0) rowsPerPage else fit
                        val start = (subPage * perPage).coerceIn(0, maxOf(0, state.jobs.size - 1))
                        val slice = state.jobs.drop(start).take(perPage)
                        Column(Modifier.fillMaxSize()) {
                            slice.forEachIndexed { i, job ->
                                JobRow(job = job, index = start + i)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF110000))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val st = hud(10f, FontWeight.SemiBold, 2.5f, Hud.Red)
        HudText("#", st, Modifier.width(W_NUM.dp))
        HudText("ADDRESS", st, Modifier.width(W_ADDRESS.dp))
        HudText("COMMUNITY", st, Modifier.width(W_COMMUNITY.dp))
        HudText("OFFICE", st, Modifier.width(W_OFFICE.dp))
        HudText("JOB TYPES", st, Modifier.weight(1f))
        HudText("CREW", st, Modifier.width(W_CREW.dp))
        HudText("STATUS", st, Modifier.width(W_STATUS.dp))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Hud.Red)
    )
}

@Composable
private fun JobRow(job: Job, index: Int) {
    val zebra = if (index % 2 == 0) Color(0x04FFFFFF) else Color(0x0ACC0000)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_H.dp)
            .background(zebra)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cell = data(14f, FontWeight.Normal, 0f, Color(0xFFD0D0D0))

        HudText(
            text = "%02d".format(index + 1),
            style = hud(11f, FontWeight.Normal, 1f, Hud.TextMuted),
            modifier = Modifier.width(W_NUM.dp),
        )
        HudText(job.address.ifBlank { "N/A" }, cell, Modifier.width(W_ADDRESS.dp))
        HudText(job.community.ifBlank { "—" }, cell, Modifier.width(W_COMMUNITY.dp))
        HudText(job.office.ifBlank { "—" }, cell, Modifier.width(W_OFFICE.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Show what fits; the count of anything further keeps the row honest.
            val shown = job.workOrderTypes.take(2)
            shown.forEachIndexed { i, t ->
                if (i > 0) Spacer(Modifier.width(4.dp))
                WoTag(t, typeColor(listOf(t)))
            }
            val extra = job.workOrderTypes.size - shown.size
            if (extra > 0) {
                Spacer(Modifier.width(4.dp))
                HudText("+$extra", hud(8f, FontWeight.Normal, 1f, Hud.TextMuted))
            }
        }

        HudText(
            text = job.crews.joinToString(", ").ifBlank { "—" },
            style = cell,
            modifier = Modifier.width(W_CREW.dp),
        )
        Box(Modifier.width(W_STATUS.dp)) {
            StatusBadge(job.stage)
        }
    }
}
