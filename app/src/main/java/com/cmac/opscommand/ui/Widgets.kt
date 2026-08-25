package com.cmac.opscommand.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cmac.opscommand.data.Mention
import com.cmac.opscommand.data.TimeUtil
import com.cmac.opscommand.ui.theme.Hud
import com.cmac.opscommand.ui.theme.data
import com.cmac.opscommand.ui.theme.hud

/** `.card-header-bar` */
@Composable
fun CardHeaderBar(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = Hud.Red,
    trailing: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Hud.CardHeaderA, Hud.CardHeaderB)))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        } else {
            HudGlyph(color = accent)
            Spacer(Modifier.width(8.dp))
        }
        HudText(
            text = title,
            style = hud(10f, FontWeight.SemiBold, 2f, accent),
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * Stand-in for the web dashboard's inline SVG section icons — a small bracketed
 * mark that keeps the header rhythm without shipping an icon library.
 */
@Composable
fun HudGlyph(color: Color = Hud.Red, size: Int = 12) {
    Box(
        Modifier
            .size(size.dp)
            .border(1.5.dp, color)
    )
}

/** `.status-pill` in the header status strip. */
@Composable
fun StatusPill(
    label: String,
    dotColor: Color?,
    active: Boolean = false,
    blinkPeriodMs: Int = 0,
) {
    Row(
        modifier = Modifier
            .background(if (active) Color(0x14CC0000) else Color.Transparent)
            .border(1.dp, if (active) Color(0x66CC0000) else Color(0x14FFFFFF))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            val alpha = if (blinkPeriodMs > 0) {
                rememberBlink(blinkPeriodMs, hard = blinkPeriodMs <= 1000, minAlpha = 0.3f)
            } else 1f
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(6.dp))
        }
        HudText(
            text = label,
            style = hud(9f, FontWeight.Normal, 2f, if (active) Color.White else Hud.TextDim),
        )
    }
}

/** Which glyph a stat card shows in its icon well. */
enum class StatIcon { HOUSE, ROOF, DOOR, TRACT }

/**
 * Simple vector glyphs standing in for the web app's inline SVG stat icons.
 * Drawn rather than shipped as assets so they inherit each card's accent colour.
 */
@Composable
private fun StatGlyph(kind: StatIcon, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(sw)
        when (kind) {
            // Gutter installs — house with an eaves line
            StatIcon.HOUSE -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.08f, h * 0.45f); lineTo(w * 0.5f, h * 0.10f)
                    lineTo(w * 0.92f, h * 0.45f)
                }
                drawPath(p, color, style = stroke)
                drawRect(
                    color, Offset(w * 0.18f, h * 0.45f),
                    androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.45f), style = stroke,
                )
                drawLine(color, Offset(w * 0.05f, h * 0.52f), Offset(w * 0.95f, h * 0.52f), sw)
            }
            // Re-roofs — a shingled cube
            StatIcon.ROOF -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.10f); lineTo(w * 0.92f, h * 0.32f)
                    lineTo(w * 0.92f, h * 0.72f); lineTo(w * 0.5f, h * 0.92f)
                    lineTo(w * 0.08f, h * 0.72f); lineTo(w * 0.08f, h * 0.32f)
                    close()
                }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(w * 0.5f, h * 0.92f), Offset(w * 0.5f, h * 0.5f), sw)
                drawLine(color, Offset(w * 0.08f, h * 0.32f), Offset(w * 0.5f, h * 0.5f), sw)
                drawLine(color, Offset(w * 0.92f, h * 0.32f), Offset(w * 0.5f, h * 0.5f), sw)
            }
            // Garage doors — panelled door
            StatIcon.DOOR -> {
                drawRect(
                    color, Offset(w * 0.08f, h * 0.25f),
                    androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.6f), style = stroke,
                )
                drawLine(color, Offset(w * 0.08f, h * 0.45f), Offset(w * 0.92f, h * 0.45f), sw)
                drawLine(color, Offset(w * 0.08f, h * 0.65f), Offset(w * 0.92f, h * 0.65f), sw)
            }
            // Tract roofing — a row of rooflines
            StatIcon.TRACT -> {
                for (i in 0..2) {
                    val x = w * (0.08f + i * 0.30f)
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, h * 0.62f)
                        lineTo(x + w * 0.13f, h * 0.32f)
                        lineTo(x + w * 0.26f, h * 0.62f)
                    }
                    drawPath(p, color, style = stroke)
                }
                drawLine(color, Offset(w * 0.05f, h * 0.80f), Offset(w * 0.95f, h * 0.80f), sw)
            }
        }
    }
}

/** `.stat-card` — icon well, label, and the glowing count-up number. */
@Composable
fun StatCard(
    label: String,
    value: Int,
    glow: Color,
    icon: StatIcon,
    modifier: Modifier = Modifier,
) {
    val shown = rememberCountUp(value)
    val transition = rememberInfiniteTransition(label = "statPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "statAlpha",
    )

    Row(
        modifier = modifier
            .tacticalCard(background = Hud.BgCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0x66000000))
                .border(1.dp, Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            StatGlyph(icon, glow)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            HudText(label, hud(9f, FontWeight.SemiBold, 2.5f, Hud.TextDim))
            Spacer(Modifier.height(2.dp))
            HudText(
                text = shown.toString(),
                style = hud(50f, FontWeight.Black, 0f, glow).copy(
                    shadow = Shadow(glow.copy(alpha = 0.75f), Offset.Zero, 22f),
                ),
                modifier = Modifier.alpha(pulse),
            )
        }
    }
}

/** `.status-badge` + `statusClass()` */
@Composable
fun StatusBadge(stage: String) {
    val s = stage.lowercase()
    val (fg, bg) = when {
        s.contains("progress") || s.contains("active") || s.contains("start") ->
            Hud.Green to Color(0x1F00FF88)
        s.contains("complet") -> Hud.TextDim to Color(0x1A787878)
        s.contains("dispatch") -> Hud.Orange to Color(0x1AFF8C00)
        else -> Hud.Teal to Color(0x1400D4FF)
    }
    Box(
        Modifier
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        HudText(
            text = stage.ifBlank { "SCHEDULED" }.uppercase(),
            style = hud(9f, FontWeight.SemiBold, 1.5f, fg),
        )
    }
}

/** `.wo-tag` — one work-order type chip, tinted by type. */
@Composable
fun WoTag(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.40f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        HudText(text, hud(8f, FontWeight.Normal, 1f, Color(0xFFCCCCCC)))
    }
}

/**
 * `.slack-msg` — the compact sidebar row on the overview board.
 *
 * Fresh (<3 h) rows get the green highlight box. They also get a hair of outer
 * spacing: back to back, adjacent boxes share an edge and the run reads as one
 * merged block rather than distinct messages.
 */
@Composable
fun SlackSidebarRow(m: Mention, modifier: Modifier = Modifier) {
    val fresh = TimeUtil.isFresh(m.timestamp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (fresh) 2.dp else 0.dp)
            .then(
                if (fresh) Modifier
                    .background(Color(0x0D00FF88))
                    .border(1.dp, Hud.Green.copy(alpha = 0.55f))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = if (fresh) 6.dp else 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.user, 26)
            Spacer(Modifier.width(8.dp))
            HudText(
                text = m.user.ifBlank { "UNKNOWN" },
                style = hud(10f, FontWeight.SemiBold, 1f, Hud.RedBright),
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            HudText(TimeUtil.hhmm(m.timestamp), hud(9f, FontWeight.Normal, 1f, Hud.TextMuted))
        }
        Spacer(Modifier.height(3.dp))
        HudText(
            text = m.text,
            style = data(13f, FontWeight.Normal, 0f, Color(0xFFCCCCCC)),
            modifier = Modifier.padding(start = 34.dp),
        )
        if (!m.channel.isNullOrBlank()) {
            HudText(
                text = "#${m.channel}",
                style = hud(8f, FontWeight.Normal, 1f, Hud.TextMuted),
                modifier = Modifier.padding(start = 34.dp),
            )
        }
    }
}

/** `.slack-avatar` / `.comms-avatar` — first initial on a red disc. */
@Composable
fun Avatar(user: String, diameter: Int) {
    Box(
        modifier = Modifier
            .size(diameter.dp)
            .clip(CircleShape)
            .background(Hud.Red),
        contentAlignment = Alignment.Center,
    ) {
        HudText(
            text = user.trim().take(1).uppercase().ifBlank { "?" },
            style = hud(diameter * 0.42f, FontWeight.Bold, 0f, Color.White),
            align = TextAlign.Center,
        )
    }
}

/** Centred "no data" / "awaiting" message shared by several panels. */
@Composable
fun EmptyNotice(text: String, spacing: Float = 3f, size: Float = 11f) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HudText(
            text = text,
            style = hud(size, FontWeight.Normal, spacing, Hud.TextMuted),
            align = TextAlign.Center,
            maxLines = 2,
        )
    }
}
