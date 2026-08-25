package com.cmac.opscommand.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cmac.opscommand.R

/**
 * Design tokens ported 1:1 from the web dashboard's `styles.css` `:root` block.
 * The whole layout is authored in a fixed 1920x1080 design space (see
 * [com.cmac.opscommand.ui.FixedStage]) so every number here is the same value
 * that appears in the original CSS.
 */
object Hud {
    val Bg = Color(0xFF080808)
    val BgCard = Color(0xFF0F0F0F)
    val BgCard2 = Color(0xFF111111)

    val Red = Color(0xFFCC0000)
    val RedBright = Color(0xFFFF3333)
    val RedDim = Color(0x33CC0000)      // rgba(204,0,0,0.2)
    val RedGlow = Color(0x80CC0000)     // rgba(204,0,0,0.5)

    val Teal = Color(0xFF00D4FF)
    val Orange = Color(0xFFFF8C00)
    val Green = Color(0xFF00FF88)
    val Magenta = Color(0xFFCC44FF)

    val Text = Color(0xFFF0F0F0)
    val TextDim = Color(0xFF888888)
    val TextMuted = Color(0xFF555555)

    val Border = Color(0x2ECC0000)      // rgba(204,0,0,0.18)
    val Sep = Color(0x59CC0000)         // rgba(204,0,0,0.35)

    /** Header gradient stops. */
    val HeaderA = Color(0xFF0A0000)
    val HeaderB = Color(0xFF100000)
    val HeaderC = Color(0xFF0A0A0A)

    val CardHeaderA = Color(0xFF1A0000)
    val CardHeaderB = Color(0xFF110000)
}

/**
 * Orbitron is shipped as a variable font (38 KB for every weight, versus ~600 KB
 * for four static cuts), so each weight is a named instance of the wght axis.
 */
@OptIn(ExperimentalTextApi::class)
private fun orbitron(weight: Int) = Font(
    resId = R.font.orbitron_variable,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val FontHud = FontFamily(
    orbitron(400),
    orbitron(600),
    orbitron(700),
    orbitron(900),
)

val FontData = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

/**
 * `font-family: var(--font-hud)` — Orbitron. `size`/`spacing` are CSS px.
 */
fun hud(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
    color: Color = Hud.Text,
) = TextStyle(
    fontFamily = FontHud,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    color = color,
)

/**
 * `font-family: var(--font-data)` — Rajdhani.
 */
fun data(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
    color: Color = Hud.Text,
    lineHeight: Float = 0f,
) = TextStyle(
    fontFamily = FontData,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    color = color,
    lineHeight = if (lineHeight > 0f) lineHeight.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
)

/** Colour used for a work-order pin / tag, matching the web map legend. */
@Composable
fun rememberTypeColor(types: List<String>): Color {
    return remember(types) { typeColor(types) }
}

/**
 * The web dashboard's map legend advertises five pin colours but its
 * `updateMapMarkers()` painted every marker the same red, so the legend never
 * matched the map. This restores the documented intent: pins and tags are
 * coloured by work-order type.
 */
fun typeColor(types: List<String>): Color {
    val joined = types.joinToString(" ").lowercase()
    return when {
        joined.contains("re-roof") || joined.contains("reroof") -> Hud.RedBright
        joined.contains("gutter") -> Hud.Teal
        joined.contains("door install") ||
            joined.contains("opener install") ||
            joined.contains("garage") -> Hud.Orange
        joined.contains("shingle") -> Hud.Green
        else -> Hud.Magenta
    }
}
