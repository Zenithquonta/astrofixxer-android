package org.astrofixxer.astro

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan

/** Hints, camera-offset and alignment maths of the camera plate-solve flow, and the picture conversions. All synthetic. */
class PlateSolveMathTest {
    private val lat = 28.6139
    private val lon = 77.2090
    private val time = java.time.Instant.parse("2026-09-28T16:00:00Z").toEpochMilli()

    private fun eyepiece(tel: Double, eye: Double, fov: Double?, width: Int = 1600, centre: Pair<Double, Double>? = 279.0 to 39.0,
                         aligned: Boolean = false, compass: Boolean = false) =
        PlateSolveHints.plan(PhonePlacement.EYEPIECE, tel, eye, fov, width, centre, aligned, compass)

    private fun run(p: SolvePlan) = (p as SolvePlan.Run).also { assertNotNull(it) }

    // ------------------------------------------------------------------------------------------------ hints

    @Test fun afocalScaleIsCameraFieldOverMagnification() {
        // 1200 mm telescope, 25 mm eyepiece: x48. A 65 degree phone camera sees 65/48 = 1.354 degrees across the eyepiece.
        val plan = run(eyepiece(1200.0, 25.0, 65.0, centre = 279.0 to 39.0, aligned = true))
        assertEquals(65.0 / 48, plan.fieldWidthDeg, 1e-9)
        val nominal = 65.0 / 48 / 1600
        // The range is +-35 % on the width: the TAN scale of such a small field equals the plain division to 0.1 %.
        assertEquals(nominal * 0.65, plan.hints.scaleMinDegPerPx, nominal * 1e-3)
        assertEquals(nominal * 1.35, plan.hints.scaleMaxDegPerPx, nominal * 1e-3)
        assertTrue(plan.deepStars)
        assertFalse(plan.assumedFov)
    }

    @Test fun cameraForwardUsesTheCameraFieldWithPlusMinus25Percent() {
        val plan = run(PlateSolveHints.plan(PhonePlacement.CAMERA_FORWARD, 1200.0, 25.0, 65.0, 1200, null, false, false))
        assertEquals(65.0, plan.fieldWidthDeg, 1e-9)
        // 65 degrees across 1200 px in the TAN model: tan(scale) * 1200 = 2 tan(32.5 degrees)
        val nominal = Math.toDegrees(atan(2 * tan(Math.toRadians(32.5)) / 1200))
        assertEquals(PlateSolveHints.scaleDegPerPx(65.0 * 0.75, 1200), plan.hints.scaleMinDegPerPx, 1e-12)
        assertEquals(PlateSolveHints.scaleDegPerPx(65.0 * 1.25, 1200), plan.hints.scaleMaxDegPerPx, 1e-12)
        assertEquals(nominal, PlateSolveHints.scaleDegPerPx(65.0, 1200), 1e-12)
        assertTrue("the range must contain the nominal scale", plan.hints.scaleMinDegPerPx < nominal && nominal < plan.hints.scaleMaxDegPerPx)
        assertFalse("a 65 degree field uses the bright catalogue", plan.deepStars)
        assertEquals(CentreHint.NONE, plan.centre)
        assertNull(plan.hints.raDeg)
    }

    @Test fun cameraForwardIgnoresTheEyepiece() {
        val a = run(PlateSolveHints.plan(PhonePlacement.CAMERA_FORWARD, 1200.0, 25.0, 70.0, 1000, null, false, false))
        val b = run(PlateSolveHints.plan(PhonePlacement.CAMERA_FORWARD, 400.0, 5.0, 70.0, 1000, null, false, false))
        assertEquals(a.hints.scaleMinDegPerPx, b.hints.scaleMinDegPerPx, 0.0)
        assertEquals(a.hints.scaleMaxDegPerPx, b.hints.scaleMaxDegPerPx, 0.0)
    }

    @Test fun unknownFieldOfViewUses65DegreesWithAWideRange() {
        val plan = run(PlateSolveHints.plan(PhonePlacement.CAMERA_FORWARD, 1200.0, 25.0, null, 1200, null, false, false))
        assertTrue(plan.assumedFov)
        assertEquals(65.0, plan.fieldWidthDeg, 1e-9)
        assertEquals(PlateSolveHints.scaleDegPerPx(65.0 * 0.5, 1200), plan.hints.scaleMinDegPerPx, 1e-12)
        assertEquals(PlateSolveHints.scaleDegPerPx(65.0 * 1.8, 1200), plan.hints.scaleMaxDegPerPx, 1e-12)
        // wide enough for 2x zoom and for an ultra-wide lens
        assertTrue(plan.hints.scaleMaxDegPerPx / plan.hints.scaleMinDegPerPx > 3.0)
        // a nonsense field of view counts as unknown
        assertTrue(run(PlateSolveHints.plan(PhonePlacement.CAMERA_FORWARD, 1200.0, 25.0, 0.0, 1200, null, false, false)).assumedFov)
    }

    @Test fun centreRadiusFollowsWhatIsKnown() {
        val centre = 279.0 to 39.0
        val aligned = run(eyepiece(1200.0, 25.0, 65.0, centre = centre, aligned = true, compass = true))
        assertEquals(CentreHint.ALIGNED, aligned.centre); assertEquals(10.0, aligned.hints.searchRadiusDeg, 0.0)
        assertEquals(279.0, aligned.hints.raDeg!!, 0.0); assertEquals(39.0, aligned.hints.decDeg!!, 0.0)
        val compass = run(eyepiece(1200.0, 25.0, 65.0, centre = centre, aligned = false, compass = true))
        assertEquals(CentreHint.COMPASS, compass.centre); assertEquals(30.0, compass.hints.searchRadiusDeg, 0.0)
        // No compass and not aligned: the sensors say nothing usable, and neither does a missing centre.
        assertEquals(CentreHint.NONE, PlateSolveHints.plan(PhonePlacement.CAMERA_FORWARD, 1200.0, 25.0, 65.0, 1200, centre, false, false).let { run(it).centre })
        assertEquals(CentreHint.NONE, run(PlateSolveHints.plan(PhonePlacement.CAMERA_FORWARD, 1200.0, 25.0, 65.0, 1200, null, true, true)).centre)
        val blind = run(PlateSolveHints.plan(PhonePlacement.CAMERA_FORWARD, 1200.0, 25.0, 65.0, 1200, centre, false, false))
        assertNull(blind.hints.raDeg); assertEquals(180.0, blind.hints.searchRadiusDeg, 0.0)
    }

    @Test fun narrowFieldsWithoutACentreAreRefused() {
        // 1.35 degrees, blind: refused. With a compass or an alignment it runs.
        val refused = eyepiece(1200.0, 25.0, 65.0, centre = null)
        assertTrue(refused is SolvePlan.Refused && refused.why == SolvePlan.Why.NO_CENTRE_HINT)
        val noSensors = eyepiece(1200.0, 25.0, 65.0, centre = 279.0 to 39.0, aligned = false, compass = false)
        assertTrue("no compass and not aligned is not a usable centre", noSensors is SolvePlan.Refused)
        assertTrue(eyepiece(1200.0, 25.0, 65.0, aligned = true) is SolvePlan.Run)
        assertTrue(eyepiece(1200.0, 25.0, 65.0, compass = true) is SolvePlan.Run)
        // Just under and just over 3 degrees: 65 / 22 = 2.95 (refused blind), 65 / 21 = 3.10 (runs blind).
        assertTrue(eyepiece(2200.0, 100.0, 65.0, centre = null) is SolvePlan.Refused)
        assertTrue(eyepiece(2100.0, 100.0, 65.0, centre = null) is SolvePlan.Run)
        // A wide field is fine blind.
        assertTrue(PlateSolveHints.plan(PhonePlacement.CAMERA_FORWARD, 1200.0, 25.0, 65.0, 1200, null, false, false) is SolvePlan.Run)
    }

    @Test fun starSourceFollowsTheFieldWidth() {
        assertTrue(run(eyepiece(1200.0, 25.0, 65.0, aligned = true)).deepStars)          // 1.4 degrees
        assertTrue(run(eyepiece(400.0, 25.0, 65.0, aligned = true)).deepStars)           // 4.1 degrees
        assertFalse(run(eyepiece(125.0, 25.0, 65.0, aligned = true)).deepStars)          // 13 degrees
        assertFalse("exactly 10 degrees is wide", run(eyepiece(162.5, 25.0, 65.0, aligned = true)).deepStars)
        assertTrue(run(eyepiece(165.0, 25.0, 65.0, aligned = true)).deepStars)           // 9.85 degrees
    }

    @Test fun tubeIsNeverSolved() {
        val p = PlateSolveHints.plan(PhonePlacement.TUBE, 1200.0, 25.0, 65.0, 1600, 279.0 to 39.0, true, true)
        assertTrue(p is SolvePlan.Refused && p.why == SolvePlan.Why.TUBE)
    }

    @Test fun lensFieldOfView() {
        // A typical phone main camera: 5.6 mm focal length over a 6.4 mm wide sensor side is 59 degrees; and the far side.
        assertEquals(59.5, PlateSolveHints.fovDeg(5.6, 6.4), 0.1)
        assertEquals(Math.toDegrees(2 * atan(0.5)), PlateSolveHints.fovDeg(4.0, 4.0), 1e-9)
    }

    // ------------------------------------------------------------------------------------------------ camera offset and alignment

    private fun solvedFrom(t: Truth) = SolveResult.Solved(t.raDeg, t.decDeg, t.rollDeg, t.scaleDegPerPx, t.mirrored, 20, 1.0, emptyList(), t.width, t.height)

    /** The sky position at picture pixel ([x], [y]) of [t], by Newton iteration on the independent forward projection [Truth.toPixel]. */
    private fun skyAt(t: Truth, x: Double, y: Double): Pair<Double, Double> {
        var ra = t.raDeg; var dec = t.decDeg
        repeat(12) {
            val p = t.toPixel(ra, dec)!!
            val e = 1e-4
            val pr = t.toPixel(ra + e / Math.cos(Math.toRadians(dec)), dec)!!
            val pd = t.toPixel(ra, dec + e)!!
            // Jacobian columns: d(pixel)/d(ra), d(pixel)/d(dec), in pixels per degree
            val j00 = (pr[0] - p[0]) / e; val j10 = (pr[1] - p[1]) / e
            val j01 = (pd[0] - p[0]) / e; val j11 = (pd[1] - p[1]) / e
            val det = j00 * j11 - j01 * j10
            val ex = x - p[0]; val ey = y - p[1]
            val dra = (ex * j11 - ey * j01) / det
            val ddec = (j00 * ey - j10 * ex) / det
            ra += dra / Math.cos(Math.toRadians(dec)); dec += ddec
        }
        return ra.mod(360.0) to dec
    }

    @Test fun solvedPixelConversionAgreesWithTheIndependentProjection() {
        val t = Truth(279.2, 38.8, 33.0, 0.05, false, 1200, 900)
        val s = solvedFrom(t)
        for ((x, y) in listOf(600.0 to 450.0, 200.0 to 100.0, 1000.0 to 800.0)) {
            val a = s.pixelToRaDec(x, y); val b = skyAt(t, x, y)
            assertTrue(SyntheticSky.separationDeg(a.first, a.second, b.first, b.second) * 3600 < 0.5)
        }
    }

    @Test fun cameraOffsetCalibratedOnOnePhotoPointsTheTelescopeOnAnother() {
        // The telescope sits at pixel (0.62, 0.37) of the camera picture, whatever the camera points at.
        val fx = 0.62; val fy = 0.37
        val w = 1200; val h = 900
        val scale = PlateSolveHints.scaleDegPerPx(60.0, w)
        // Photo 1: the user centred Vega in the eyepiece. Vega must therefore be at the offset pixel of picture 1.
        val vega = 279.2347 to 38.7837
        val c1 = skyAt(Truth(vega.first, vega.second, 25.0, scale, false, w, h), w / 2.0 - (fx - 0.5) * w, h / 2.0 - (fy - 0.5) * h)
        val t1 = Truth(c1.first, c1.second, 25.0, scale, false, w, h)
        val p = t1.toPixel(vega.first, vega.second)!!
        val offset = PhotoAlignment.offsetOf(solvedFrom(t1), vega.first, vega.second)
        assertNotNull(offset)
        assertEquals(p[0] / w, offset!!.first, 1e-6); assertEquals(p[1] / h, offset.second, 1e-6)
        assertEquals("the fractions came out where the picture was built to put Vega", fx, offset.first, 0.01)
        assertEquals(fy, offset.second, 0.01)
        // On photo 1 the telescope is on Vega.
        val on1 = PhotoAlignment.telescopeRaDec(solvedFrom(t1), PhonePlacement.CAMERA_FORWARD, offset)!!
        assertTrue(SyntheticSky.separationDeg(on1.first, on1.second, vega.first, vega.second) * 3600 < 2.0)

        // Photo 2: another part of the sky, rolled and mirrored the other way. The telescope is at the same pixel.
        for (mirrored in listOf(false, true)) {
            val t2 = Truth(291.0, 12.0, 200.0, scale, mirrored, w, h)
            val expected = skyAt(t2, offset.first * w, offset.second * h)
            val got = PhotoAlignment.telescopeRaDec(solvedFrom(t2), PhonePlacement.CAMERA_FORWARD, offset)!!
            assertTrue("mirrored=$mirrored: telescope off by ${SyntheticSky.separationDeg(got.first, got.second, expected.first, expected.second) * 3600} arcsec",
                SyntheticSky.separationDeg(got.first, got.second, expected.first, expected.second) * 3600 < 2.0)
            // and it is not the picture centre
            assertTrue(SyntheticSky.separationDeg(got.first, got.second, t2.raDeg, t2.decDeg) > 3.0)
        }
    }

    @Test fun offsetOfAStarOutsideThePictureIsNotLearned() {
        val t = Truth(279.0, 39.0, 0.0, PlateSolveHints.scaleDegPerPx(60.0, 1200), false, 1200, 900)
        val s = solvedFrom(t)
        assertNotNull(PhotoAlignment.offsetOf(s, 279.0, 39.0))
        assertNull("30 degrees away is off a 60x45 picture", PhotoAlignment.offsetOf(s, 279.0, 69.0))
        assertNull("behind the camera", PhotoAlignment.offsetOf(s, 99.0, -39.0))
    }

    @Test fun telescopePositionPerPlacement() {
        val t = Truth(279.0, 39.0, 70.0, PlateSolveHints.scaleDegPerPx(60.0, 1200), false, 1200, 900)
        val s = solvedFrom(t)
        val eye = PhotoAlignment.telescopeRaDec(s, PhonePlacement.EYEPIECE, null)!!
        assertEquals(279.0, eye.first, 1e-9); assertEquals(39.0, eye.second, 1e-9)
        assertNull("camera forward is unknown until the offset is calibrated", PhotoAlignment.telescopeRaDec(s, PhonePlacement.CAMERA_FORWARD, null))
        assertNull(PhotoAlignment.telescopeRaDec(s, PhonePlacement.TUBE, 0.5 to 0.5))
        val centre = PhotoAlignment.telescopeRaDec(s, PhonePlacement.CAMERA_FORWARD, 0.5 to 0.5)!!
        assertTrue(SyntheticSky.separationDeg(centre.first, centre.second, 279.0, 39.0) * 3600 < 0.01)
    }

    @Test fun calibrationLandsTheTelescopeAxisOnThePhotoPosition() {
        val axis = doubleArrayOf(0.0, 0.0, -1.0)
        val ra = 279.2347; val dec = 38.7837
        val truth = Pointing.rayFromPos(ra, dec, time, lat, lon)
        assertTrue("the test star must be up", truth[2] > 0.3)
        for ((roll, azError) in listOf(0.0 to 0.0, 37.0 to 20.0, 200.0 to -75.0, 90.0 to 170.0)) {
            // The phone points somewhere else than the sky says (a compass error), and is rolled about the axis.
            val device = deviceFor(axis, azimuthShift(truth, azError), roll)
            val cal = PhotoAlignment.calibrate(device, axis, ra, dec, time, lat, lon)
            assertNotNull("roll $roll error $azError", cal)
            val landed = Pointing.cameraRays(device, cal!!.matrix, axis)[2]
            assertTrue(Pointing.angleBetweenDeg(landed, truth) < PhotoAlignment.EXACT_DEG)
            assertArrayEquals(truth, cal.ray, 1e-12)
            assertArrayEquals(device, cal.sample.device, 0.0)
            // Moving the phone afterwards: the calibrated pointing follows it. A 10 degree turn about the vertical moves the
            // (uncorrected) axis by 2 asin(cos(alt) sin(5 degrees)), and a rotation keeps that angle.
            val moved = Pointing.matMul(Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), Math.toRadians(10.0)), device)
            val after = Pointing.cameraRays(moved, cal.matrix, axis)[2]
            val altRaw = Math.asin(Pointing.cameraRays(device, null, axis)[2][2])
            val expected = Math.toDegrees(2 * Math.asin(Math.cos(altRaw) * Math.sin(Math.toRadians(5.0))))
            assertEquals(expected, Pointing.angleBetweenDeg(after, truth), 0.001)
        }
    }

    @Test fun calibrationRefusesADirectionBelowTheHorizon() {
        val axis = doubleArrayOf(0.0, 0.0, -1.0)
        val ra = 279.2347; val dec = 38.7837
        // 12 hours later the same star is far below the horizon for the same place.
        val later = time + 12 * 3600_000L
        assertTrue(Pointing.rayFromPos(ra, dec, later, lat, lon)[2] < 0)
        assertNull(PhotoAlignment.calibrate(Pointing.IDENTITY, axis, ra, dec, later, lat, lon))
    }

    private fun azimuthShift(v: DoubleArray, deg: Double) =
        Pointing.mvec(Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), Math.toRadians(deg)), v)

    private fun deviceFor(axis: DoubleArray, w: DoubleArray, rollDeg: Double): DoubleArray {
        val c = Pointing.cross(axis, w)
        val len = Math.sqrt(Pointing.dot(c, c))
        val minimal = if (len < 1e-12) Pointing.IDENTITY
        else Pointing.axisAngleMatrix(doubleArrayOf(c[0] / len, c[1] / len, c[2] / len), Pointing.angleBetweenDeg(axis, w) * Math.PI / 180)
        return Pointing.matMul(Pointing.axisAngleMatrix(w, Math.toRadians(rollDeg)), minimal)
    }

    // ------------------------------------------------------------------------------------------------ pictures

    @Test fun yPlaneWithRowPaddingAndPixelStride() {
        // 6 x 4 picture, row stride 10, pixel stride 1; then a pixel stride 2 plane with the same values.
        val w = 6; val h = 4
        fun value(x: Int, y: Int) = (x * 40 + y * 3) and 0xFF
        val plain = ByteBuffer.allocate(10 * (h - 1) + w)
        for (y in 0 until h) for (x in 0 until w) plain.put(y * 10 + x, value(x, y).toByte())
        val a = Luminance.fromYPlane(plain, w, h, 10, 1, 0)
        assertEquals(w, a.width); assertEquals(h, a.height)
        for (y in 0 until h) for (x in 0 until w) assertEquals(value(x, y) / 255f, a.pixels[y * w + x], 1e-6f)
        val strided = ByteBuffer.allocate(16 * (h - 1) + (w - 1) * 2 + 1)
        for (y in 0 until h) for (x in 0 until w) strided.put(y * 16 + x * 2, value(x, y).toByte())
        val b = Luminance.fromYPlane(strided, w, h, 16, 2, 0)
        assertArrayEquals(a.pixels, b.pixels, 0f)
    }

    @Test fun quarterTurnsAreClockwise() {
        // [1 2 3]      [4 1]
        // [4 5 6]  ->  [5 2]   after one clockwise turn
        //              [6 3]
        val img = GrayImage(3, 2, floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f))
        val r1 = Luminance.rotated(img, 1)
        assertEquals(2, r1.width); assertEquals(3, r1.height)
        assertArrayEquals(floatArrayOf(4f, 1f, 5f, 2f, 6f, 3f), r1.pixels, 0f)
        val r2 = Luminance.rotated(img, 2)
        assertArrayEquals(floatArrayOf(6f, 5f, 4f, 3f, 2f, 1f), r2.pixels, 0f)
        val r3 = Luminance.rotated(img, 3)
        assertArrayEquals(floatArrayOf(3f, 6f, 2f, 5f, 1f, 4f), r3.pixels, 0f)
        assertArrayEquals(img.pixels, Luminance.rotated(Luminance.rotated(img, 1), 3).pixels, 0f)
        assertTrue(Luminance.rotated(img, 0) === img)
    }

    @Test fun bigPicturesAreShrunkKeepingTheMeanAndTheStars() {
        val w = 4000; val h = 3000
        val star = 1234 to 2222
        val out = Luminance.fromRows(w, h, 0) { y, row ->
            java.util.Arrays.fill(row, 0.2f)
            if (y == star.second) row[star.first] = 1.0f
        }
        assertTrue(maxOf(out.width, out.height) <= 1600)
        assertEquals("the aspect ratio stays", 4.0 / 3.0, out.width.toDouble() / out.height, 0.01)
        val mean = out.pixels.map { it.toDouble() }.average()
        // The one bright pixel adds 0.8 / 12e6 to the mean; the shrunken mean is 0.2 to well within 1e-4.
        assertEquals(0.2, mean, 1e-4)
        // The star is still the brightest thing, at the right place (within a pixel or two).
        val at = out.pixels.indices.maxByOrNull { out.pixels[it] }!!
        assertTrue(out.pixels[at] > 0.2f + 1e-4f)
        assertEquals(star.first * out.width / w.toDouble(), (at % out.width).toDouble(), 1.5)
        assertEquals(star.second * out.height / h.toDouble(), (at / out.width).toDouble(), 1.5)
    }

    @Test fun smallPicturesAreNotResampled() {
        val img = Luminance.fromRows(640, 480, 0) { y, row -> for (x in row.indices) row[x] = ((x + y) % 2).toFloat() }
        assertEquals(640, img.width); assertEquals(480, img.height)
        assertEquals(1f, img.pixels[1], 0f); assertEquals(0f, img.pixels[0], 0f)
    }

    @Test fun argbRowsBecomeLuma() {
        val out = FloatArray(4)
        Luminance.argbRow(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt()), out)
        assertEquals(0f, out[0], 1e-6f); assertEquals(1f, out[1], 1e-6f)
        assertEquals(0.299f, out[2], 1e-4f); assertEquals(0.114f, out[3], 1e-4f)
    }

    @Test fun fromRowsRotatesAfterShrinking() {
        val out = Luminance.fromRows(3000, 1000, 1) { _, row -> java.util.Arrays.fill(row, 0.5f) }
        assertTrue("turned a quarter: now tall", out.height > out.width)
        assertEquals(1600, out.height)
        assertTrue(out.pixels.all { abs(it - 0.5f) < 1e-5f })
    }
}
