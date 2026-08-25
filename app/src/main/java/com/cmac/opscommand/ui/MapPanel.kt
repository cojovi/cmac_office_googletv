package com.cmac.opscommand.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.cmac.opscommand.BuildConfig
import com.cmac.opscommand.data.Geo
import com.cmac.opscommand.data.GeoPoint
import com.cmac.opscommand.data.Job
import com.cmac.opscommand.data.MapCamera
import com.cmac.opscommand.data.Mercator
import com.cmac.opscommand.ui.theme.Hud
import com.cmac.opscommand.ui.theme.hud
import com.cmac.opscommand.ui.theme.typeColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Mapbox caps a Static Images request at 1280 px per side. */
private const val STATIC_MAX = 1280.0

/**
 * The dispatch map.
 *
 * The web dashboard ran a full Mapbox GL renderer and then immediately disabled
 * scroll, drag, rotate, pitch, keyboard and double-click zoom — nobody touches a
 * TV above a register. So this uses the Static Images API instead: one cached
 * PNG rather than a live WebGL context, which on TV-class hardware removes a
 * continuously-rendering GL surface and its memory churn entirely.
 *
 * Pins are drawn over it in a single [Canvas] pass — one draw call for all ~380
 * markers, versus the web version's ~380 DOM nodes each running its own CSS
 * keyframe animation.
 */
@Composable
fun MapPanel(
    jobs: List<Job>,
    modifier: Modifier = Modifier,
) {
    val token = BuildConfig.MAPBOX_TOKEN.trim()

    BoxWithConstraints(modifier = modifier.background(Color(0xFF050505))) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        if (vw <= 1f || vh <= 1f) return@BoxWithConstraints

        // Work in design px so the numbers match the CSS layout.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val vwDp = with(density) { vw.toDp().value.toDouble() }
        val vhDp = with(density) { vh.toDp().value.toDouble() }

        if (token.isEmpty()) {
            MapPlaceholder("MAP UNAVAILABLE\nSET CMAC_MAPBOX_TOKEN")
            return@BoxWithConstraints
        }

        val all = remember(jobs) { Geo.pointsOf(jobs) }

        // Request at most 1280 px per side, preserving aspect so the returned
        // image is a uniform scale of the viewport and the pin projection stays
        // exact along both axes.
        val k = min(1.0, STATIC_MAX / max(vwDp, vhDp))
        val reqW = (vwDp * k).roundToInt().coerceAtLeast(1)
        val reqH = (vhDp * k).roundToInt().coerceAtLeast(1)

        val camera = remember(all, reqW, reqH) {
            Geo.fitBoundsRobust(
                points = all,
                widthPx = reqW.toDouble(),
                heightPx = reqH.toDouble(),
                paddingPx = 80.0 * k,
                maxZoom = 14.0,
            ) ?: MapCamera(39.8283, -98.5795, 3.2) // continental US, as the web app's initial view
        }

        val url = remember(camera, reqW, reqH, token) {
            staticMapUrl(camera, reqW, reqH, token)
        }

        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(url)
                .crossfade(400)
                .build(),
            contentDescription = "Territory and dispatch map",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )

        // Every pin is drawn; the layer reports how many landed outside the
        // frame so a geocoding problem stays visible instead of being silently
        // swallowed by the camera.
        var offFrame by remember(all, camera) { mutableIntStateOf(0) }

        PinLayer(
            points = all,
            camera = camera,
            imageScale = 1.0 / k,
            onOffFrameCount = { offFrame = it },
            modifier = Modifier.fillMaxSize(),
        )

        if (offFrame > 0) {
            HudText(
                text = "$offFrame OF ${all.size} PIN${if (all.size == 1) "" else "S"} OUTSIDE FRAME",
                style = hud(9f, FontWeight.SemiBold, 1.5f, Hud.Orange),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color(0xCC000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MapPlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HudText(
            text = text,
            style = hud(11f, FontWeight.Normal, 4f, Hud.TextMuted),
            maxLines = 3,
            align = TextAlign.Center,
        )
    }
}

/**
 * All pins in one canvas. A single shared clock drives every pulse; each pin
 * just reads it at a staggered phase, so 380 markers cost one animation.
 */
@Composable
private fun PinLayer(
    points: List<GeoPoint>,
    camera: MapCamera,
    imageScale: Double,
    onOffFrameCount: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val transition = rememberInfiniteTransition(label = "pins")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "pulse",
    )

    // Pre-project once per camera change rather than every frame.
    val projected = remember(points, camera, imageScale) {
        points.map { p ->
            val (dx, dy) = Mercator.offsetPx(p.lat, p.lng, camera)
            Triple(
                (dx * imageScale).toFloat(),
                (dy * imageScale).toFloat(),
                typeColor(p.job.workOrderTypes),
            )
        }
    }

    // Count how many pins fall outside the visible frame. Reported once per
    // camera change rather than per frame.
    val offFrame = remember(projected) { mutableIntStateOf(-1) }

    Canvas(modifier = modifier.clipToBounds()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = 6.dp.toPx()          // .map-marker is 14x14; trimmed slightly
        val ring = 1.5.dp.toPx()     // because 380 pins over one metro overlap
        val maxPulse = 14.dp.toPx()

        var outside = 0
        projected.forEachIndexed { i, (dx, dy, color) ->
            val x = cx + dx
            val y = cy + dy
            if (x < -maxPulse || y < -maxPulse ||
                x > size.width + maxPulse || y > size.height + maxPulse
            ) {
                outside++
                return@forEachIndexed
            }

            // Stagger so the field of pins ripples instead of strobing in unison.
            val local = (phase + (i % 6) / 6f) % 1f
            if (local < 0.7f) {
                val t = local / 0.7f
                drawCircle(
                    color = color.copy(alpha = 0.40f * (1f - t)),
                    radius = r + maxPulse * t,
                    center = Offset(x, y),
                )
            }
            drawCircle(color = color, radius = r, center = Offset(x, y))
            // A lighter shade of the pin's own colour, like the web marker's
            // #FF5555 ring on red — reads as a marker, not a soap bubble.
            drawCircle(
                color = lighten(color, 0.45f),
                radius = r,
                center = Offset(x, y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(ring),
            )
        }

        if (offFrame.value != outside) {
            offFrame.value = outside
            onOffFrameCount(outside)
        }
    }
}

/** Blend [c] toward white by [amount] — used for pin outlines. */
private fun lighten(c: Color, amount: Float): Color = Color(
    red = c.red + (1f - c.red) * amount,
    green = c.green + (1f - c.green) * amount,
    blue = c.blue + (1f - c.blue) * amount,
    alpha = 1f,
)

/**
 * `mapbox/dark-v11` at pitch 0 / bearing 0 — a plain Mercator plane, which is
 * what keeps [Mercator.offsetPx] exact. `logo`/`attribution` are disabled
 * because they are baked into the panel chrome instead.
 */
private fun staticMapUrl(camera: MapCamera, w: Int, h: Int, token: String): String {
    val lon = "%.6f".format(java.util.Locale.US, camera.lng)
    val lat = "%.6f".format(java.util.Locale.US, camera.lat)
    val z = "%.2f".format(java.util.Locale.US, camera.zoom)
    return "https://api.mapbox.com/styles/v1/mapbox/dark-v11/static/" +
        "$lon,$lat,$z,0,0/${w}x${h}@2x" +
        "?access_token=$token&attribution=false&logo=false"
}

/** `.map-legend` — now truthful, since pins are coloured by type. */
@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0000))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val items = listOf(
            "RE-ROOF" to Hud.RedBright,
            "GUTTER" to Hud.Teal,
            "GARAGE DOOR" to Hud.Orange,
            "TRACT / SHINGLES" to Hud.Green,
            "OTHER" to Hud.Magenta,
        )
        items.forEachIndexed { i, (label, color) ->
            if (i > 0) Spacer(Modifier.width(20.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            HudText(label, hud(9f, FontWeight.Normal, 1f, Hud.TextDim))
        }
    }
}
