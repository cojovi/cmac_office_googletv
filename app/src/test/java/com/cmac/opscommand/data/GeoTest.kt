package com.cmac.opscommand.data

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map pins are positioned by our own Mercator maths rather than by a map
 * SDK, so these tests pin down the projection, the bounds fit, and the
 * geocoding-stray filter.
 */
class GeoTest {

    private fun job(lat: Double, lng: Double) =
        GeoPoint(lat, lng, Job(jobNumber = "J", lat = lat, lng = lng))

    @Test
    fun `mercator round trips`() {
        for (lat in listOf(-60.0, -33.3, 0.0, 32.78, 45.5, 71.2)) {
            val back = Mercator.latOf(Mercator.uy(lat))
            assertTrue("lat $lat -> $back", abs(back - lat) < 1e-6)
        }
        for (lng in listOf(-179.0, -96.8, 0.0, 12.5, 178.9)) {
            val back = Mercator.lngOf(Mercator.ux(lng))
            assertTrue("lng $lng -> $back", abs(back - lng) < 1e-9)
        }
    }

    @Test
    fun `known mercator anchors`() {
        // Null island sits dead centre of the normalised world.
        assertEquals(0.5, Mercator.ux(0.0), 1e-12)
        assertEquals(0.5, Mercator.uy(0.0), 1e-12)
        // Dallas is west of the meridian and north of the equator.
        assertTrue(Mercator.ux(-96.80) < 0.5)
        assertTrue(Mercator.uy(32.78) < 0.5)
    }

    @Test
    fun `point at camera centre projects to zero offset`() {
        val cam = MapCamera(32.78, -96.80, 9.0)
        val (dx, dy) = Mercator.offsetPx(32.78, -96.80, cam)
        assertEquals(0f, dx, 1e-3f)
        assertEquals(0f, dy, 1e-3f)
    }

    @Test
    fun `offsets have the right sign`() {
        val cam = MapCamera(32.78, -96.80, 9.0)
        // East of centre => +x. North of centre => -y (screen y grows downward).
        val (dxE, _) = Mercator.offsetPx(32.78, -96.00, cam)
        val (_, dyN) = Mercator.offsetPx(33.50, -96.80, cam)
        assertTrue("east should be +x, was $dxE", dxE > 0f)
        assertTrue("north should be -y, was $dyN", dyN < 0f)
    }

    @Test
    fun `fitBounds frames every point inside the viewport`() {
        val pts = listOf(
            job(32.90, -97.10), job(32.60, -96.50),
            job(30.27, -97.74), job(29.76, -95.37),
        )
        val w = 1052.0
        val h = 889.0
        val pad = 80.0
        val cam = Geo.fitBounds(pts, w, h, pad, maxZoom = 14.0)!!

        for (p in pts) {
            val (dx, dy) = Mercator.offsetPx(p.lat, p.lng, cam)
            val x = w / 2 + dx
            val y = h / 2 + dy
            // Allow a hair of tolerance for the zoom clamp / rounding.
            assertTrue("x=$x outside padded viewport", x >= pad - 1 && x <= w - pad + 1)
            assertTrue("y=$y outside padded viewport", y >= pad - 1 && y <= h - pad + 1)
        }
    }

    @Test
    fun `single point uses a street level zoom`() {
        val cam = Geo.fitBounds(listOf(job(32.78, -96.80)), 1000.0, 800.0)!!
        assertEquals(12.0, cam.zoom, 1e-9)
        assertEquals(32.78, cam.lat, 1e-9)
    }

    @Test
    fun `fitBounds respects maxZoom for a tight cluster`() {
        // Two jobs a few hundred metres apart must not zoom to street level 20.
        val cam = Geo.fitBounds(
            listOf(job(32.7800, -96.8000), job(32.7810, -96.8010)),
            1000.0, 800.0, 80.0, maxZoom = 14.0,
        )!!
        assertTrue("zoom ${cam.zoom} should clamp to 14", cam.zoom <= 14.0 + 1e-9)
    }

    /** How many of [pts] land inside the frame for [cam]. */
    private fun visibleCount(pts: List<GeoPoint>, cam: MapCamera, w: Double, h: Double): Int =
        pts.count { p ->
            val (dx, dy) = Mercator.offsetPx(p.lat, p.lng, cam)
            val x = w / 2 + dx
            val y = h / 2 + dy
            x in 0.0..w && y in 0.0..h
        }

    /** A realistic day: dense DFW plus Austin / Houston / San Antonio. */
    private fun texasDay(): List<GeoPoint> = buildList {
        repeat(40) { add(job(32.70 + (it % 8) * 0.05, -96.95 + (it % 7) * 0.05)) }
        repeat(12) { add(job(30.20 + (it % 4) * 0.05, -97.80 + (it % 4) * 0.05)) }
        repeat(10) { add(job(29.70 + (it % 4) * 0.05, -95.42 + (it % 4) * 0.05)) }
        repeat(8) { add(job(29.38 + (it % 4) * 0.05, -98.54 + (it % 4) * 0.05)) }
    }

    private val strays = listOf(
        job(45.52, -122.68), // Portland
        job(25.76, -80.19),  // Miami
        job(43.65, -79.38),  // Toronto
        job(40.71, -74.01),  // New York
    )

    @Test
    fun `robust fit keeps the whole texas service area visible despite strays`() {
        val w = 1052.0
        val h = 889.0
        val real = texasDay()
        val cam = Geo.fitBoundsRobust(real + strays, w, h, 80.0, maxZoom = 14.0)!!

        // Every legitimate job must still be on screen — the point of the change
        // is that a handful of bad geocodes cannot push real work out of frame.
        assertEquals(real.size, visibleCount(real, cam, w, h))
    }

    @Test
    fun `robust fit is not dragged out to continental scale by strays`() {
        val w = 1052.0
        val h = 889.0
        val real = texasDay()
        val robust = Geo.fitBoundsRobust(real + strays, w, h, 80.0, maxZoom = 14.0)!!
        val naive = Geo.fitBounds(real + strays, w, h, 80.0, maxZoom = 14.0)!!

        // Higher zoom == tighter framing. The robust camera must be meaningfully
        // closer in than one that tries to include Portland and Miami.
        assertTrue(
            "robust ${robust.zoom} should be tighter than naive ${naive.zoom}",
            robust.zoom > naive.zoom + 1.0,
        )
    }

    @Test
    fun `strays are reported as off frame rather than silently dropped`() {
        val w = 1052.0
        val h = 889.0
        val all = texasDay() + strays
        val cam = Geo.fitBoundsRobust(all, w, h, 80.0, maxZoom = 14.0)!!
        // They are still in the dataset; they just fall outside the frame, which
        // is what the on-screen "N OF M PINS OUTSIDE FRAME" badge counts.
        assertEquals(0, visibleCount(strays, cam, w, h))
        assertEquals(all.size - strays.size, visibleCount(all, cam, w, h))
    }

    @Test
    fun `robust fit falls back to exact fit for small samples`() {
        // Below the sample threshold there is no way to tell a stray from a real
        // second market, so nothing is trimmed.
        val pts = listOf(job(32.78, -96.80), job(45.52, -122.68))
        val robust = Geo.fitBoundsRobust(pts, 1000.0, 800.0)!!
        val exact = Geo.fitBounds(pts, 1000.0, 800.0)!!
        assertEquals(exact.zoom, robust.zoom, 1e-9)
        assertEquals(exact.lat, robust.lat, 1e-9)
        assertEquals(exact.lng, robust.lng, 1e-9)
    }

    @Test
    fun `robust fit matches exact fit when the data is clean`() {
        val clean = texasDay()
        val w = 1052.0
        val h = 889.0
        val robust = Geo.fitBoundsRobust(clean, w, h, 80.0, maxZoom = 14.0)!!
        // With no strays, trimming 3% must not meaningfully move the camera.
        val exact = Geo.fitBounds(clean, w, h, 80.0, maxZoom = 14.0)!!
        assertTrue(abs(robust.zoom - exact.zoom) < 1.0)
        assertEquals(clean.size, visibleCount(clean, robust, w, h))
    }

    @Test
    fun `pointsOf rejects nulls and null island`() {
        val jobs = listOf(
            Job(jobNumber = "a", lat = 32.78, lng = -96.80),
            Job(jobNumber = "b", lat = null, lng = -96.80),
            Job(jobNumber = "c", lat = 32.78, lng = null),
            Job(jobNumber = "d", lat = 0.0, lng = 0.0),
            Job(jobNumber = "e", lat = 999.0, lng = -96.8),
        )
        val pts = Geo.pointsOf(jobs)
        assertEquals(1, pts.size)
        assertEquals("a", pts[0].job.jobNumber)
    }
}
