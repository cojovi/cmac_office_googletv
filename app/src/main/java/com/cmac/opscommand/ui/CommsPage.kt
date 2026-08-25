package com.cmac.opscommand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cmac.opscommand.data.DashboardState
import com.cmac.opscommand.data.Mention
import com.cmac.opscommand.data.TimeUtil
import com.cmac.opscommand.ui.theme.Hud
import com.cmac.opscommand.ui.theme.data
import com.cmac.opscommand.ui.theme.hud

/**
 * Page 3 — Live Comms Center. Up to 12 messages as compact rows, matching
 * `renderSlackFull()`.
 */
@Composable
fun CommsPage(state: DashboardState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudGlyph(color = Hud.Red, size = 16)
            Spacer(Modifier.width(12.dp))
            HudText(
                "LIVE OPERATIONS FEED: TACTICAL COMMS & ALERTS",
                hud(13f, FontWeight.SemiBold, 3f, Hud.Red),
            )
            Spacer(Modifier.weight(1f))
            LiveBadge(state.link)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Hud.Sep))
        Spacer(Modifier.height(14.dp))

        if (state.mentions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PulseRing()
                    Spacer(Modifier.height(28.dp))
                    HudText(
                        "AWAITING INCOMING TRANSMISSIONS",
                        hud(16f, FontWeight.Normal, 6f, Hud.TextMuted),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                state.mentions.take(12).forEach { m -> CommsCard(m) }
            }
        }
    }
}

@Composable
private fun CommsCard(m: Mention) {
    val fresh = TimeUtil.isFresh(m.timestamp)
    val accent = if (fresh) Hud.Green else Hud.Red

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .commsCard(
                background = if (fresh) Color(0x0A00FF88) else Hud.BgCard,
                border = if (fresh) Hud.Green.copy(alpha = 0.6f) else Hud.Border,
                bracket = accent,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(m.user, 34)
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudText(
                    text = m.user.ifBlank { "UNKNOWN" },
                    style = hud(14f, FontWeight.SemiBold, 2f, Hud.RedBright),
                )
                if (!m.channel.isNullOrBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .background(Color(0x0DFFFFFF))
                            .border(1.dp, Color(0x14FFFFFF))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        HudText("#${m.channel}", hud(10f, FontWeight.Normal, 1f, Hud.TextMuted))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (fresh) {
                    Box(
                        Modifier
                            .background(Color(0x1F00FF88))
                            .border(1.dp, Hud.Green.copy(alpha = 0.6f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        HudText("<3H", hud(10f, FontWeight.Bold, 1.5f, Hud.Green))
                    }
                    Spacer(Modifier.width(10.dp))
                }
                HudText(
                    text = TimeUtil.full(m.timestamp),
                    style = hud(10f, FontWeight.Normal, 1f, Hud.TextMuted),
                )
            }
            Spacer(Modifier.height(3.dp))
            HudText(
                text = m.text,
                style = data(14f, FontWeight.Medium, 0f, Hud.Text),
                maxLines = 2,
            )
        }
    }
}

/** `.pulse-ring` on the empty state. */
@Composable
private fun PulseRing() {
    val alpha = rememberBlink(2000, hard = false, minAlpha = 0.25f)
    Box(
        Modifier
            .size(60.dp)
            .clip(CircleShape)
            .border(2.dp, Hud.Red.copy(alpha = alpha), CircleShape)
    )
}
