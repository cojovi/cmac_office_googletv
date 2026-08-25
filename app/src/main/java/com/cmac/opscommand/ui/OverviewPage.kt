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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cmac.opscommand.data.DashboardState
import com.cmac.opscommand.ui.theme.Hud
import com.cmac.opscommand.ui.theme.hud

/**
 * Page 1 — Operations Overview.
 * 1054 px map column + 812 px stats/comms column, per the web app's 55/45 split.
 */
@Composable
fun OverviewPage(state: DashboardState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxSize()) {

        // ── Map column ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(1054.dp)
                .fillMaxHeight()
                .tacticalCard()
        ) {
            CardHeaderBar(
                title = "TODAY'S FIELD OPERATIONS: TERRITORY & DISPATCH MAP",
                trailing = {
                    val n = state.jobs.size
                    HudText(
                        text = "$n JOB${if (n == 1) "" else "S"} TODAY",
                        style = hud(9f, FontWeight.Normal, 1f, Hud.TextDim),
                    )
                },
            )
            MapPanel(
                jobs = state.jobs,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            MapLegend()
        }

        Spacer(Modifier.width(14.dp))

        // ── Stats + comms column ──────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

            TotalBanner(state)

            Spacer(Modifier.height(12.dp))

            // 2x2 stat grid, 280 px tall
            Column(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    StatCard("GUTTER INSTALLS", state.counts.gutters, Hud.Teal, StatIcon.HOUSE, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(10.dp))
                    StatCard("RE-ROOFS", state.counts.reroofs, Hud.RedBright, StatIcon.ROOF, Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    StatCard("GARAGE DOORS", state.counts.garageDoors, Hud.Orange, StatIcon.DOOR, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(10.dp))
                    StatCard("TRACT ROOFING", state.counts.tractRoofing, Hud.Green, StatIcon.TRACT, Modifier.weight(1f).fillMaxHeight())
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Slack live feed ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .tacticalCard()
            ) {
                CardHeaderBar(
                    title = "FIELD COMMUNICATIONS",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelPill("#SLACK-LIVE")
                            Spacer(Modifier.width(10.dp))
                            LiveBadge(state.link)
                        }
                    },
                )
                if (state.mentions.isEmpty()) {
                    EmptyNotice("AWAITING TRANSMISSIONS...", spacing = 3f, size = 10f)
                } else {
                    Column(modifier = Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                        // 8 latest, matching renderSlackSidebar()
                        state.mentions.take(8).forEach { m ->
                            SlackSidebarRow(m)
                        }
                    }
                }
            }
        }
    }
}

/** `.total-banner` — the headline "TOTAL HOMES TODAY" figure. */
@Composable
private fun TotalBanner(state: DashboardState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tacticalCard(background = Color.Transparent)
            .background(Brush.linearGradient(listOf(Hud.CardHeaderA, Color(0xFF0F0000))))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudText("TOTAL HOMES TODAY", hud(11f, FontWeight.Normal, 3f, Hud.Red))
        Spacer(Modifier.width(20.dp))
        HudText(
            text = rememberCountUp(state.counts.totalJobs).toString(),
            style = hud(48f, FontWeight.Black, 0f, Color.White).copy(
                shadow = Shadow(Hud.Red.copy(alpha = 0.9f), Offset.Zero, 26f),
            ),
        )
        Spacer(Modifier.weight(1f))
        HudText(
            text = state.counts.updatedAt
                ?.let { "UPDATED " + com.cmac.opscommand.data.TimeUtil.hhmm(it) }
                ?: "LOADING...",
            style = hud(8f, FontWeight.Normal, 1f, Hud.TextMuted),
        )
    }
}

@Composable
fun ChannelPill(text: String) {
    Box(
        Modifier
            .background(Color(0x26CC0000))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        HudText(text, hud(9f, FontWeight.Normal, 1f, Hud.Red))
    }
}

/** `.live-badge` — streaming indicator wired to the real SSE link state. */
@Composable
fun LiveBadge(link: com.cmac.opscommand.data.LinkState) {
    val (label, color) = when (link) {
        com.cmac.opscommand.data.LinkState.LIVE -> "STREAMING LIVE" to Hud.RedBright
        com.cmac.opscommand.data.LinkState.CONNECTING -> "CONNECTING" to Hud.Orange
        com.cmac.opscommand.data.LinkState.DOWN -> "LINK DOWN" to Hud.TextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (link == com.cmac.opscommand.data.LinkState.LIVE) {
            HudText(
                text = "●",
                style = hud(9f, FontWeight.Normal, 0f, color),
                modifier = Modifier.alpha(rememberBlink(1000, hard = true, minAlpha = 0.15f)),
            )
            Spacer(Modifier.width(5.dp))
        }
        HudText(label, hud(9f, FontWeight.Normal, 2f, color))
    }
}
