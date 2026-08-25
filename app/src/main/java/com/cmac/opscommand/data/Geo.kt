package com.cmac.opscommand.data

import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/** A geographic point with the job it belongs to. */
data class GeoPoint(val lat: Double, val lng: Double, val job: Job)

/** Camera for the Mapbox Static Images request. */
data class MapCamera(val lat: Double, val lng: Double, val zoom: Double)

/**
 * Web Mercator, using Mapbox's 512 px tile scheme so the maths line up exactly
 * with the zoom values the Static Images API expects.
 */
object Mercator {
    const val TILE = 512.0

    fun worldSize(zoom: Double) = TILE * Math.pow(2.0, zoom)

    /** Normalised x in [0,1]. */
    fun ux(lng: Double) = (lng + 180.0) / 360.0

    /** Normalised y in [0,1]. */
    fun uy(lat: Double): Double {
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = Math.toRadians(clamped)
        return (1.0 - asinh(tan(rad)) / Math.PI) / 2.0
    }

    fun lngOf(ux: Double) = ux * 360.0 - 180.0

    fun latOf(uy: Double): Double =
        Math.toDegrees(kotlin.math.atan(sinh(Math.PI * (1.0 - 2.0 * uy))))

    /**
     * Pixel offset of [lat]/[lng] from the image centre, for an image rendered
     * at [camera]. Valid because the static map is requested with pitch=0 and
     * bearing=0, which keeps the projection a plain Mercator plane.
     */
    fun offsetPx(lat: Double, lng: Double, camera: MapCamera): Pair<Float, Float> {
        val w = worldSize(camera.zoom)
        val dx = (ux(lng) - ux(camera.lng)) * w
        val dy = (uy(lat) - uy(camera.lat)) * w
        return dx.toFloat() to dy.toFloat()
    }
}

object Geo {
    /**
     * Choose a camera that frames every point inside [widthPx] x [heightPx],
     * mirroring the web app's `fitBounds(..., { padding: 80, maxZoom: 14 })`
     * and its single-point `flyTo({ zoom: 12 })` special case.
     */
    fun fitBounds(
        points: List<GeoPoint>,
        widthPx: Double,
        heightPx: Double,
        paddingPx: Double = 80.0,
        maxZoom: Double = 14.0,
        minZoom: Double = 1.0,
    ): MapCamera? {
        if (points.isEmpty()) return null
        if (points.size == 1) return MapCamera(points[0].lat, points[0].lng, 12.0)

        var minUx = Double.MAX_VALUE
        var maxUx = -Double.MAX_VALUE
        var minUy = Double.MAX_VALUE
        var maxUy = -Double.MAX_VALUE
        for (p in points) {
            val x = Mercator.ux(p.lng)
            val y = Mercator.uy(p.lat)
            minUx = min(minUx, x); maxUx = max(maxUx, x)
            minUy = min(minUy, y); maxUy = max(maxUy, y)
        }
        return cameraFor(minUx, maxUx, minUy, maxUy, widthPx, heightPx, paddingPx, maxZoom, minZoom)
    }

    /**
     * Frame the *bulk* of the day's work rather than its extremes.
     *
     * The backend geocodes a job from its street line alone whenever Bolt's
     * detail record omits city/state/zip, so a handful of jobs land in the wrong
     * state entirely — "729 Vineyard Way", a Dallas job, resolves to Florida
     * (see `wrong_address.png` in the web repo). Fitting to the outright min/max
     * then zooms out to the whole continent and every real pin collapses into an
     * unreadable blob, which is exactly what `map_error.png` shows.
     *
     * Framing to a percentile band keeps the camera on the real service area
     * while every pin is still drawn — nothing is hidden or silently dropped,
     * strays simply fall outside the frame and are reported as such. Compare
     * the alternative of deleting suspicious jobs outright, which would have
     * thrown away the legitimate Houston and San Antonio clusters: those sit
     * ~3.3 degrees from Dallas, further than any fixed "plausible" radius that
     * still catches an out-of-state stray.
     */
    fun fitBoundsRobust(
        points: List<GeoPoint>,
        widthPx: Double,
        heightPx: Double,
        paddingPx: Double = 80.0,
        maxZoom: Double = 14.0,
        minZoom: Double = 1.0,
        lowPct: Double = 0.03,
        highPct: Double = 0.97,
        minSampleSize: Int = 12,
    ): MapCamera? {
        if (points.isEmpty()) return null
        if (points.size == 1) return MapCamera(points[0].lat, points[0].lng, 12.0)
        // Too few points to tell a stray from a real second market.
        if (points.size < minSampleSize) {
            return fitBounds(points, widthPx, heightPx, paddingPx, maxZoom, minZoom)
        }

        val xs = points.map { Mercator.ux(it.lng) }.sorted()
        val ys = points.map { Mercator.uy(it.lat) }.sorted()

        return cameraFor(
            minUx = percentile(xs, lowPct),
            maxUx = percentile(xs, highPct),
            minUy = percentile(ys, lowPct),
            maxUy = percentile(ys, highPct),
            widthPx = widthPx,
            heightPx = heightPx,
            paddingPx = paddingPx,
            maxZoom = maxZoom,
            minZoom = minZoom,
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val idx = ((sorted.size - 1) * p.coerceIn(0.0, 1.0)).roundToInt()
        return sorted[idx]
    }

    private fun cameraFor(
        minUx: Double,
        maxUx: Double,
        minUy: Double,
        maxUy: Double,
        widthPx: Double,
        heightPx: Double,
        paddingPx: Double,
        maxZoom: Double,
        minZoom: Double,
    ): MapCamera {
        val availW = max(1.0, widthPx - 2 * paddingPx)
        val availH = max(1.0, heightPx - 2 * paddingPx)
        val spanX = maxUx - minUx
        val spanY = maxUy - minUy

        val zoom = if (spanX <= 1e-12 && spanY <= 1e-12) {
            12.0
        } else {
            val scaleX = if (spanX > 1e-12) availW / spanX else Double.MAX_VALUE
            val scaleY = if (spanY > 1e-12) availH / spanY else Double.MAX_VALUE
            val world = min(scaleX, scaleY)
            ln(world / Mercator.TILE) / ln(2.0)
        }.coerceIn(minZoom, maxZoom)

        return MapCamera(
            lat = Mercator.latOf((minUy + maxUy) / 2.0),
            lng = Mercator.lngOf((minUx + maxUx) / 2.0),
            zoom = zoom,
        )
    }

    /** Jobs that actually carry coordinates. */
    fun pointsOf(jobs: List<Job>): List<GeoPoint> = jobs.mapNotNull { j ->
        val la = j.lat
        val ln = j.lng
        if (la == null || ln == null) return@mapNotNull null
        if (la.isNaN() || ln.isNaN()) return@mapNotNull null
        if (abs(la) < 1e-9 && abs(ln) < 1e-9) return@mapNotNull null // 0,0 = failed geocode
        if (abs(la) > 90.0 || abs(ln) > 180.0) return@mapNotNull null
        GeoPoint(la, ln, j)
    }
}
