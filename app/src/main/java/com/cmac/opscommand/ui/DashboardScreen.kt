package com.cmac.opscommand.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cmac.opscommand.data.DashboardState
import com.cmac.opscommand.data.DataState
import com.cmac.opscommand.data.LinkState
import com.cmac.opscommand.data.TimeUtil
import com.cmac.opscommand.ui.theme.Hud
import com.cmac.opscommand.ui.theme.hud

@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val state by vm.data.collectAsStateWithLifecycle()
    val shell by vm.shell.collectAsStateWithLifecycle()

    FixedStage {
        GridBackground()

        Column(Modifier.fillMaxSize()) {
            Header(state = state, locked = shell.locked, vm = vm)

            // `.header-sep`
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HeaderSepBrush)
            )

            // `.pages-container` — 965 px
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)
            ) {
                if (!state.configured) {
                    NotConfigured()
                } else {
                    Crossfade(
                        targetState = shell.page,
                        animationSpec = tween(900),
                        label = "page",
                    ) { page ->
                        when (page) {
                            Page.OVERVIEW -> OverviewPage(state)
                            Page.JOBS -> JobsPage(
                                state = state,
                                subPage = shell.jobsSubPage,
                                rowsPerPage = vm.jobsPerPage,
                                onRowsMeasured = vm::setJobsPerPage,
                            )
                            Page.COMMS -> CommsPage(state)
                        }
                    }
                }
            }

            PageDots(current = shell.page, locked = shell.locked)
        }

        ScanlineOverlay()
    }
}

/** `.header` — 72 px brand bar, status strip, lock state and clock. */
@Composable
private fun Header(state: DashboardState, locked: Boolean, vm: DashboardViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Brush.linearGradient(listOf(Hud.HeaderA, Hud.HeaderB, Hud.HeaderC)))
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CmacMark()
        Spacer(Modifier.width(16.dp))
        Column {
            HudText(
                text = "CMAC",
                style = hud(26f, FontWeight.Black, 4f, Color.White).copy(
                    shadow = Shadow(Hud.Red.copy(alpha = 0.85f), Offset.Zero, 18f),
                ),
            )
            Spacer(Modifier.height(2.dp))
            HudText("FIELD OPERATIONS COMMAND", hud(9f, FontWeight.Normal, 5f, Hud.Red))
        }

        Spacer(Modifier.weight(1f))

        // Status strip — driven by real link state rather than being decorative.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StatusPill("SYS ACTIVE", Hud.Red, active = true, blinkPeriodMs = 1000)
            StatusPill(
                label = when (state.dataState) {
                    DataState.FRESH -> "DATA LIVE"
                    DataState.SYNCING -> "DATA SYNCING"
                    DataState.STALE -> "DATA STALE"
                },
                dotColor = when (state.dataState) {
                    DataState.FRESH -> Hud.Green
                    DataState.SYNCING -> Hud.Orange
                    DataState.STALE -> Hud.RedBright
                },
                blinkPeriodMs = if (state.dataState == DataState.FRESH) 2500 else 0,
            )
            StatusPill(
                label = when (state.link) {
                    LinkState.LIVE -> "SLACK ONLINE"
                    LinkState.CONNECTING -> "SLACK LINKING"
                    LinkState.DOWN -> "SLACK OFFLINE"
                },
                dotColor = when (state.link) {
                    LinkState.LIVE -> Hud.Orange
                    LinkState.CONNECTING -> Hud.Orange
                    LinkState.DOWN -> Hud.TextMuted
                },
                blinkPeriodMs = if (state.link == LinkState.LIVE) 2500 else 0,
            )
            StatusPill(TimeUtil.headerDate(now), dotColor = null)
        }

        Spacer(Modifier.weight(1f))

        // `.lock-btn` — on a TV this is remote-driven (OK button), so it reads as
        // a state indicator with its key hint rather than a mouse target.
        Row(
            modifier = Modifier
                .background(if (locked) Color(0x2EFF4D4D) else Color(0x12FFFFFF))
                .border(1.dp, if (locked) Hud.RedBright else Color(0x40FFFFFF))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Orbitron has no ⊗ / ○ / ◄ glyphs, so state is drawn rather than
            // typed — a missing-glyph box on a wall display looks broken.
            Box(
                Modifier
                    .size(7.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (locked) Hud.RedBright else Hud.Green)
            )
            Spacer(Modifier.width(7.dp))
            HudText(
                text = if (locked) "LOCKED" else "AUTO",
                style = hud(10f, FontWeight.Bold, 1.5f, if (locked) Hud.RedBright else Color.White),
            )
            Spacer(Modifier.width(8.dp))
            HudText("OK", hud(8f, FontWeight.Normal, 1f, Hud.TextMuted))
        }

        Spacer(Modifier.width(16.dp))

        Column(horizontalAlignment = Alignment.End) {
            HudText(
                text = TimeUtil.clock(now),
                style = hud(32f, FontWeight.Bold, 3f, Color.White).copy(
                    shadow = Shadow(Hud.Red.copy(alpha = 0.7f), Offset.Zero, 22f),
                ),
            )
            Spacer(Modifier.height(2.dp))
            HudText("LOCAL TIME", hud(8f, FontWeight.Normal, 4f, Hud.Red), align = TextAlign.End)
        }
    }
}

/**
 * The CMAC house/roofline crest, redrawn as vector geometry from the web app's
 * inline SVG (a 56x48 polygon with roof trusses and a door).
 */
@Composable
private fun CmacMark() {
    androidx.compose.foundation.Canvas(modifier = Modifier.width(56.dp).height(48.dp)) {
        val sx = size.width / 56f
        val sy = size.height / 48f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        val stroke = 2.5f * sx
        val thin = 1f * sx

        // Roofline + walls outline: 28,2 54,22 46,22 46,46 10,46 10,22 2,22
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(p(28f, 2f).x, p(28f, 2f).y)
            lineTo(p(54f, 22f).x, p(54f, 22f).y)
            lineTo(p(46f, 22f).x, p(46f, 22f).y)
            lineTo(p(46f, 46f).x, p(46f, 46f).y)
            lineTo(p(10f, 46f).x, p(10f, 46f).y)
            lineTo(p(10f, 22f).x, p(10f, 22f).y)
            lineTo(p(2f, 22f).x, p(2f, 22f).y)
            close()
        }
        drawPath(
            path = path,
            color = Hud.Red,
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        // Interior trusses
        drawLine(Hud.Red.copy(alpha = 0.4f), p(20f, 46f), p(20f, 28f), thin)
        drawLine(Hud.Red.copy(alpha = 0.4f), p(36f, 46f), p(36f, 28f), thin)
        // Door
        drawRect(
            color = Hud.Red,
            topLeft = p(22f, 30f),
            size = androidx.compose.ui.geometry.Size(12f * sx, 16f * sy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * sx),
        )
    }
}

/** `.page-dots` — 42 px footer, with the active board widened. */
@Composable
private fun PageDots(current: Page, locked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x80000000)))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Page.entries.forEachIndexed { i, page ->
            if (i > 0) Spacer(Modifier.width(14.dp))
            val active = page == current
            Box(
                Modifier
                    .width(if (active) 52.dp else 30.dp)
                    .height(5.dp)
                    .background(if (active) Hud.Red else Color(0x33CC0000))
                    .border(1.dp, Color(0x4DCC0000))
            )
        }
        Spacer(Modifier.width(24.dp))
        HudText(
            text = if (locked) "LEFT / RIGHT: PAGE   ·   OK: RESUME" else "LEFT / RIGHT: PAGE   ·   OK: HOLD",
            style = hud(8f, FontWeight.Normal, 2f, Hud.TextMuted),
        )
    }
}

/** Shown when CMAC_SERVER_URL was never supplied at build time. */
@Composable
private fun NotConfigured() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.tacticalCard().padding(48.dp),
        ) {
            HudText("SERVER NOT CONFIGURED", hud(22f, FontWeight.Bold, 5f, Hud.RedBright))
            Spacer(Modifier.height(18.dp))
            HudText(
                "SET CMAC_SERVER_URL IN local.properties AND REBUILD",
                hud(12f, FontWeight.Normal, 3f, Hud.TextDim),
            )
            Spacer(Modifier.height(10.dp))
            HudText(
                "e.g.  CMAC_SERVER_URL=http://192.168.1.50:3000",
                hud(11f, FontWeight.Normal, 1f, Hud.TextMuted),
            )
        }
    }
}
