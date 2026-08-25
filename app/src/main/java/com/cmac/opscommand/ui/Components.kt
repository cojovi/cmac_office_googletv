package com.cmac.opscommand.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cmac.opscommand.ui.theme.Hud
import kotlin.math.min
import kotlin.math.roundToInt

/** The design space every layout in this app is authored in. */
const val DESIGN_W = 1920f
const val DESIGN_H = 1080f

/**
 * Pins the UI to a fixed 1920x1080 coordinate space, exactly like the web
 * dashboard's `html, body { width: 1920px; height: 1080px }`.
 *
 * Rather than scaling a rasterised layer (which softens text), this overrides
 * the density so that 1.dp == 1 design pixel. Text is therefore rendered
 * natively at the correct size and stays crisp on a 4K panel, while every
 * measurement in this codebase can be the same number as the original CSS.
 * `fontScale` is forced to 1 so a TV-level font-size preference can't break a
 * pixel-exact dashboard.
 */
@Composable
fun FixedStage(content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.Bg)
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        if (wPx <= 0f || hPx <= 0f) return@BoxWithConstraints
        val scale = min(wPx / DESIGN_W, hPx / DESIGN_H)

        CompositionLocalProvider(
            LocalDensity provides Density(density = scale, fontScale = 1f)
        ) {
            Box(
                modifier = Modifier
                    .size(DESIGN_W.dp, DESIGN_H.dp)
                    .align(Alignment.Center)
            ) { content() }
        }
    }
}

/** Text helper — `BasicText` keeps Material out of the APK entirely. */
@Composable
fun HudText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (align != null) style.copy(textAlign = align) else style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * `.card` — flat panel, 1 px red-tinted border, plus the 18x2 px corner
 * brackets from `.card::before` that give the whole dashboard its HUD framing.
 */
fun Modifier.tacticalCard(
    background: Color = Hud.BgCard,
    border: Color = Hud.Border,
    bracket: Color = Hud.Red,
    bracketLen: Float = 18f,
    bracketThick: Float = 2f,
): Modifier = this
    .background(background)
    .drawBehind {
        val bw = 1.dp.toPx()
        // 1 px border
        drawRect(color = border, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(bw))
        val len = bracketLen.dp.toPx()
        val th = bracketThick.dp.toPx()
        val w = size.width
        val h = size.height
        // top-left
        drawRect(bracket, Offset(0f, 0f), Size(len, th))
        drawRect(bracket, Offset(0f, 0f), Size(th, len))
        // top-right
        drawRect(bracket, Offset(w - len, 0f), Size(len, th))
        drawRect(bracket, Offset(w - th, 0f), Size(th, len))
        // bottom-left
        drawRect(bracket, Offset(0f, h - th), Size(len, th))
        drawRect(bracket, Offset(0f, h - len), Size(th, len))
        // bottom-right
        drawRect(bracket, Offset(w - len, h - th), Size(len, th))
        drawRect(bracket, Offset(w - th, h - len), Size(th, len))
    }

/** Two-corner variant used by `.comms-card::before` (top-left + bottom-right). */
fun Modifier.commsCard(
    background: Color = Hud.BgCard,
    border: Color = Hud.Border,
    bracket: Color = Hud.Red,
): Modifier = this
    .background(background)
    .drawBehind {
        val bw = 1.dp.toPx()
        drawRect(color = border, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(bw))
        val len = 12.dp.toPx()
        val th = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawRect(bracket, Offset(0f, 0f), Size(len, th))
        drawRect(bracket, Offset(0f, 0f), Size(th, len))
        drawRect(bracket, Offset(w - len, h - th), Size(len, th))
        drawRect(bracket, Offset(w - th, h - len), Size(th, len))
    }

/**
 * `.scanlines` — 1 px dark line every 4 px. Drawn once into the layer above the
 * content; on a TV panel this is what sells the CRT/HUD look.
 */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                val step = 4.dp.toPx()
                val thick = 1.dp.toPx()
                val c = Color(0x0F000000)
                var y = 0f
                while (y < size.height) {
                    drawRect(c, Offset(0f, y + step - thick), Size(size.width, thick))
                    y += step
                }
            }
    )
}

/** `.grid-bg` — faint 60 px red graph paper behind everything. */
@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val step = 60.dp.toPx()
                val thick = 1.dp.toPx()
                val c = Color(0x0ACC0000)
                var x = 0f
                while (x < size.width) {
                    drawRect(c, Offset(x, 0f), Size(thick, size.height)); x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawRect(c, Offset(0f, y), Size(size.width, thick)); y += step
                }
            }
    )
}

/** `.blink` (1 s hard step) and `.blink-slow` (2.5 s ease) as an alpha value. */
@Composable
fun rememberBlink(periodMs: Int = 1000, hard: Boolean = true, minAlpha: Float = 0f): Float {
    val transition = rememberInfiniteTransition(label = "blink")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = minAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blinkAlpha",
    )
    // step-end blinking is binary, not a fade
    return if (hard) (if (a > 0.5f) 1f else minAlpha) else a
}

/**
 * `animateNumber()` — count up to [target] over 1.4 s with the same
 * ease-out-cubic the web dashboard used, so the stat cards still "roll".
 */
@Composable
fun rememberCountUp(target: Int, durationMs: Int = 1400): Int {
    var from by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(target) {
        from = value
        val start = withFrameNanos { it }
        val span = durationMs * 1_000_000L
        while (true) {
            val now = withFrameNanos { it }
            val p = ((now - start).toFloat() / span).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            value = from + (target - from) * eased
            if (p >= 1f) break
        }
        value = target.toFloat()
    }
    return value.roundToInt()
}

/** Horizontal gradient used by `.header-sep`. */
val HeaderSepBrush: Brush
    get() = Brush.horizontalGradient(
        0f to Color.Transparent,
        0.2f to Hud.Red,
        0.8f to Hud.Red,
        1f to Color.Transparent,
    )
