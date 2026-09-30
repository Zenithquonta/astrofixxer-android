package org.astrofixxer.astro

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * Plate solver tests on synthetic pictures. Everything here is synthetic: pictures are rendered from the very star
 * sources the solver searches (bright catalogue, deep Gaia-based list) with a Gaussian PSF, Poisson-like and read noise,
 * a sky gradient, hot pixels, missing and invented stars. Nothing has been checked against a real photograph.
 */
class PlateSolverTest {
    companion object {
        lateinit var bright: StarSource
        lateinit var deep: SolverStars
        val rows = ArrayList<String>()
        var falsePositives = 0
        var solves = 0

        // 60 degrees across 1200 px, as a TAN projection: focal length in pixels = 600 / tan(30 deg)
        val wideScale = Math.toDegrees(tan(Math.toRadians(30.0)) / 600.0)
        // 65 degree eyepiece at 48x: the frame width spans 65 / 48 degrees
        val eyeScale = 65.0 / 48 / 1600

        @BeforeClass @JvmStatic fun load() {
            val catalog = File("src/main/assets/sky_catalog.json.gz").inputStream().use { Catalog.load(it) }
            bright = CatalogStars(catalog)
            deep = File("src/main/assets/solver_stars.bin").inputStream().use { SolverStars.load(it) }
        }

        @AfterClass @JvmStatic fun summary() {
            println("=== plate solver summary: $solves solves, $falsePositives false positives ===")
            rows.forEach { println(it) }
        }

        fun randomSky(rnd: Random): DoubleArray {
            val z = rnd.nextDouble() * 2 - 1
            return doubleArrayOf(rnd.nextDouble() * 360, Math.toDegrees(asin(z)))
        }

        /** Point [distDeg] from (ra, dec) in a random direction. */
        fun offset(ra: Double, dec: Double, distDeg: Double, rnd: Random): DoubleArray {
            val d = Math.toRadians(distDeg); val phi = rnd.nextDouble() * 2 * PI
            val d0 = Math.toRadians(dec)
            val de = asin(sin(d0) * cos(d) + cos(d0) * sin(d) * cos(phi))
            val r = Math.toRadians(ra) + Math.atan2(sin(phi) * sin(d) * cos(d0), cos(d) - sin(d0) * sin(de))
            return doubleArrayOf(Math.toDegrees(r).mod(360.0), Math.toDegrees(de))
        }

        fun rollError(a: Double, b: Double) = abs((a - b + 540).mod(360.0) - 180)
    }

    private fun describe(r: SolveResult): String = when (r) {
        is SolveResult.Solved -> "Solved m=%d rms=%.2f\" fa=%.1e".format(r.matchedStars, r.rmsArcsec, r.falseAlarmProbability)
        is SolveResult.Failed -> "Failed ${r.reason} (${r.detectedStars} stars)"
    }

    /** Solves and, if the answer is Solved, checks it against the truth. Returns the result; counts false positives. */
    private fun solveAndCheck(
        name: String, truth: Truth, img: GrayImage, hints: SolveHints, source: StarSource, tolArcsec: Double,
        expectSolved: Boolean = true,
    ): SolveResult {
        val t0 = System.nanoTime()
        val r = PlateSolver.solve(img, hints, source)
        val ms = (System.nanoTime() - t0) / 1e6
        solves++
        var line = "%-44s %6.0f ms  %s".format(name, ms, describe(r))
        if (r is SolveResult.Solved) {
            val err = SyntheticSky.separationDeg(r.raDeg, r.decDeg, truth.raDeg, truth.decDeg) * 3600
            val rollErr = rollError(r.rollDeg, truth.rollDeg)
            line += "  centre err %.1f\"  roll err %.3f deg  scale err %.3f%%".format(err, rollErr, 100 * abs(r.scaleDegPerPx / truth.scaleDegPerPx - 1))
            rows.add(line); println(line)
            if (err > 10 * tolArcsec) falsePositives++
            assertTrue("$name: FALSE POSITIVE, centre off by $err arcsec", err <= 10 * tolArcsec)
            assertTrue("$name: expected a failure but got a solution", expectSolved)
            assertTrue("$name: centre off by $err arcsec (limit $tolArcsec)", err <= tolArcsec)
            assertTrue("$name: roll off by $rollErr deg", rollErr <= 0.5)
            assertEquals("$name: mirror flag", truth.mirrored, r.mirrored)
            // the reported pose must agree with its own matched stars
            for ((d, s) in r.stars) {
                val p = r.raDecToPixel(s.raDeg, s.decDeg)!!
                assertTrue("$name: matched star not where the pose puts it", hypot(p.first - d.x, p.second - d.y) < 8)
            }
        } else {
            rows.add(line); println(line)
            assertTrue("$name: expected a solution but got ${describe(r)}", !expectSolved)
        }
        return r
    }

    // ---------------------------------------------------------------------------------------------- wide fields

    private fun wideTruth(ra: Double, dec: Double, rnd: Random, mirrored: Boolean) =
        Truth(ra, dec, rnd.nextDouble() * 360, wideScale, mirrored, 1200, 900)

    private fun widePositions(): List<DoubleArray> {
        val rnd = Random(20260930)
        // the fifth is the north galactic pole, the sparsest region of the sky
        return List(4) { randomSky(rnd) } + listOf(doubleArrayOf(192.85948, 27.12825))
    }

    private fun wideCase(mode: String) {
        val rnd = Random(7)
        for ((k, pos) in widePositions().withIndex()) {
            val mirrored = mode == "mirrored"
            val truth = wideTruth(pos[0], pos[1], rnd, mirrored)
            val spec = RenderSpec(limitMag = 5.0, psfSigma = 1.0 + rnd.nextDouble())
            val img = SyntheticSky.render(truth, spec, bright, 100L + k)
            val hints = SolveHints(wideScale * 0.88, wideScale * 1.12)
            val hinted = if (mode == "hint") {
                val c = offset(pos[0], pos[1], 15.0, rnd)
                SolveHints(wideScale * 0.88, wideScale * 1.12, c[0], c[1], 25.0)
            } else hints
            solveAndCheck("wide 60x45 $mode #$k (%.0f,%.0f)".format(pos[0], pos[1]), truth, img, hinted, bright, 120.0)
        }
    }

    @Test fun wideFieldBlind() = wideCase("blind")
    @Test fun wideFieldHintOff15Degrees() = wideCase("hint")
    @Test fun wideFieldMirroredBlind() = wideCase("mirrored")

    /** Real lenses are not perfect TAN: 1 % and 2 % radial distortion at the corners must still solve, blind. */
    @Test fun wideFieldWithLensDistortion() {
        val rnd = Random(88)
        for ((k, d) in listOf(0.01, -0.01, 0.02, -0.02).withIndex()) {
            val pos = randomSky(rnd)
            val truth = Truth(pos[0], pos[1], rnd.nextDouble() * 360, wideScale, k % 2 == 1, 1200, 900, d)
            val img = SyntheticSky.render(truth, RenderSpec(limitMag = 5.0, psfSigma = 1.5), bright, 200L + k)
            solveAndCheck("wide, %.0f%% corner distortion #$k".format(d * 100), truth, img, SolveHints(wideScale * 0.88, wideScale * 1.12), bright, 120.0)
        }
    }

    /** A picture about 12 degrees across, blind, with the bright catalogue. */
    @Test fun twelveDegreeFieldBlind() {
        val rnd = Random(12)
        val s = 12.0 / 1200
        for (k in 0 until 2) {
            val pos = randomSky(rnd)
            val truth = Truth(pos[0], pos[1], rnd.nextDouble() * 360, s, k == 1, 1200, 900)
            val img = SyntheticSky.render(truth, RenderSpec(limitMag = 6.3, psfSigma = 1.5), bright, 300L + k)
            solveAndCheck("12 degree field blind #$k", truth, img, SolveHints(s * 0.9, s * 1.1), bright, 60.0)
        }
    }

    // ---------------------------------------------------------------------------------------------- medium field

    @Test fun mediumFieldWithHint() {
        val rnd = Random(5)
        val s = 5.0 / 1600
        for (k in 0 until 3) {
            val pos = randomSky(rnd)
            val truth = Truth(pos[0], pos[1], rnd.nextDouble() * 360, s, k == 2, 1600, 1200)
            val img = SyntheticSky.render(truth, RenderSpec(limitMag = 9.3, psfSigma = 1.0 + rnd.nextDouble()), deep, 400L + k)
            val c = offset(pos[0], pos[1], 1.5, rnd)
            solveAndCheck("medium 5 deg with hint #$k", truth, img, SolveHints(s * 0.9, s * 1.1, c[0], c[1], 3.0), deep, 20.0)
        }
    }

    // ---------------------------------------------------------------------------------------------- eyepiece

    /** A random field whose 0.5 degree circle holds between [lo] and [hi] catalogue stars (V <= 10.5). */
    private fun pickEyepieceField(rnd: Random, lo: Int, hi: Int): DoubleArray {
        while (true) {
            val p = randomSky(rnd)
            val n = deep.cone(p[0], p[1], 0.5, 10.5).size
            if (n in lo..hi) return p
        }
    }

    private fun eyepieceTruth(p: DoubleArray, roll: Double, mirrored: Boolean) = Truth(p[0], p[1], roll, eyeScale, mirrored, 1600, 1200)

    private fun eyepieceImage(truth: Truth, seed: Long, rnd: Random) = SyntheticSky.render(
        truth, RenderSpec(limitMag = 10.5, psfSigma = 1.2 + 0.6 * rnd.nextDouble(), circleRadiusPx = 0.5 / eyeScale), deep, seed)

    @Test fun eyepieceFieldWithHint() {
        val rnd = Random(2026)
        // (min, max) catalogue stars in the 1 degree circle, roll, mirrored
        val cases = listOf(
            Triple(12, 20, 0.0 to false), Triple(12, 20, 137.0 to false), Triple(12, 20, 20.0 to true),
            Triple(12, 20, 250.0 to true), Triple(8, 9, 75.0 to false), // the last is the sparse one
        )
        for ((k, c) in cases.withIndex()) {
            val p = pickEyepieceField(rnd, c.first, c.second)
            val truth = eyepieceTruth(p, c.third.first, c.third.second)
            val img = eyepieceImage(truth, 500L + k, rnd)
            val off = offset(p[0], p[1], 1.0, rnd)
            val hints = SolveHints(eyeScale * 0.9, eyeScale * 1.1, off[0], off[1], 2.0)
            val nCat = deep.cone(p[0], p[1], 0.5, 10.5).size
            solveAndCheck("eyepiece 1 deg #$k (%d cat stars, roll %.0f%s)".format(nCat, c.third.first, if (c.third.second) " mirrored" else ""),
                truth, img, hints, deep, 20.0)
        }
    }

    /** Fields with almost no catalogue stars may fail, but must never be solved wrongly. */
    @Test fun verySparseEyepieceFieldsFailOrAreRight() {
        val rnd = Random(99)
        val ngp = doubleArrayOf(192.85948, 27.12825)
        for (k in 0 until 3) {
            val p = if (k == 0) ngp else pickEyepieceField(rnd, 3, 6)
            val truth = eyepieceTruth(p, rnd.nextDouble() * 360, k == 1)
            val img = eyepieceImage(truth, 600L + k, rnd)
            val off = offset(p[0], p[1], 1.0, rnd)
            solveAndCheck("very sparse eyepiece #$k (%d cat stars)".format(deep.cone(p[0], p[1], 0.5, 10.5).size), truth, img,
                SolveHints(eyeScale * 0.9, eyeScale * 1.1, off[0], off[1], 2.0), deep, 20.0, expectSolved = false)
        }
    }

    // ---------------------------------------------------------------------------------------------- negatives

    private fun wideHints() = SolveHints(wideScale * 0.88, wideScale * 1.12)

    private fun expectFailure(name: String, r: SolveResult, reason: SolveResult.Reason) {
        assertTrue("$name: ${describe(r)}", r is SolveResult.Failed)
        assertEquals(name, reason, (r as SolveResult.Failed).reason)
    }

    private fun failCase(name: String, img: GrayImage, hints: SolveHints, source: StarSource, reason: SolveResult.Reason) {
        val t0 = System.nanoTime()
        val r = PlateSolver.solve(img, hints, source)
        val line = "%-44s %6.0f ms  %s".format(name, (System.nanoTime() - t0) / 1e6, describe(r))
        rows.add(line); println(line); solves++
        if (r is SolveResult.Solved) falsePositives++
        expectFailure(name, r, reason)
    }

    @Test fun pureNoiseIsTooFewStars() {
        val truth = Truth(10.0, 20.0, 0.0, wideScale, false, 1200, 900)
        val img = SyntheticSky.render(truth, RenderSpec(limitMag = 5.0, drawStars = false), bright, 1)
        failCase("pure noise (wide)", img, wideHints(), bright, SolveResult.Reason.TOO_FEW_STARS)
        val eye = SyntheticSky.render(Truth(10.0, 20.0, 0.0, eyeScale, false, 1600, 1200),
            RenderSpec(limitMag = 5.0, drawStars = false, circleRadiusPx = 590.0), deep, 2)
        failCase("pure noise (eyepiece circle)", eye, SolveHints(eyeScale * 0.9, eyeScale * 1.1, 10.0, 20.0, 2.0), deep, SolveResult.Reason.TOO_FEW_STARS)
    }

    @Test fun blankBlackIsTooFewStars() {
        failCase("blank black", GrayImage(1200, 900, FloatArray(1200 * 900)), wideHints(), bright, SolveResult.Reason.TOO_FEW_STARS)
    }

    @Test fun saturatedWhiteIsTooBright() {
        failCase("saturated white", GrayImage(1200, 900, FloatArray(1200 * 900) { 1f }), wideHints(), bright, SolveResult.Reason.IMAGE_TOO_BRIGHT)
        // a washed-out sky: very bright background with a few stars poking out
        val truth = Truth(83.8, -5.4, 0.0, wideScale, false, 1200, 900)
        val img = SyntheticSky.render(truth, RenderSpec(limitMag = 3.0, sky = 0.8, peakSnrAtLimit = 30.0), bright, 3)
        failCase("washed-out bright sky", img, wideHints(), bright, SolveResult.Reason.IMAGE_TOO_BRIGHT)
    }

    @Test fun trailedStarsAreReported() {
        val truth = Truth(83.8, -5.4, 20.0, wideScale, false, 1200, 900)
        val img = SyntheticSky.render(truth, RenderSpec(limitMag = 4.5, peakSnrAtLimit = 50.0, trailPx = 25.0), bright, 4)
        failCase("trailed stars (25 px streaks)", img, wideHints(), bright, SolveResult.Reason.STARS_TRAILED)
    }

    @Test fun wrongScaleHintNeverGivesAWrongAnswer() {
        val rnd = Random(31)
        val pos = randomSky(rnd)
        val truth = wideTruth(pos[0], pos[1], rnd, false)
        val img = SyntheticSky.render(truth, RenderSpec(limitMag = 5.0), bright, 5)
        for (factor in doubleArrayOf(3.0, 1.0 / 3)) {
            val s = wideScale * factor
            failCase("wide, scale hint x%.2f, blind".format(factor), img, SolveHints(s * 0.9, s * 1.1), bright, SolveResult.Reason.NO_MATCH)
            failCase("wide, scale hint x%.2f, hinted".format(factor), img,
                SolveHints(s * 0.9, s * 1.1, pos[0], pos[1], 10.0), bright, SolveResult.Reason.NO_MATCH)
        }
        val p = pickEyepieceField(rnd, 12, 20)
        val et = eyepieceTruth(p, 30.0, false)
        val eimg = eyepieceImage(et, 6, rnd)
        for (factor in doubleArrayOf(3.0, 1.0 / 3)) {
            val s = eyeScale * factor
            failCase("eyepiece, scale hint x%.2f".format(factor), eimg, SolveHints(s * 0.9, s * 1.1, p[0], p[1], 2.0), deep, SolveResult.Reason.NO_MATCH)
        }
    }

    @Test fun wrongAreaNeverGivesAWrongAnswer() {
        val rnd = Random(41)
        val pos = randomSky(rnd)
        val truth = wideTruth(pos[0], pos[1], rnd, false)
        val img = SyntheticSky.render(truth, RenderSpec(limitMag = 5.0), bright, 7)
        val far = offset(pos[0], pos[1], 70.0, rnd)
        failCase("wide, hint 70 deg away (radius 10)", img, SolveHints(wideScale * 0.88, wideScale * 1.12, far[0], far[1], 10.0), bright, SolveResult.Reason.NO_MATCH)
        val p = pickEyepieceField(rnd, 12, 20)
        val et = eyepieceTruth(p, 30.0, false)
        val eimg = eyepieceImage(et, 8, rnd)
        val off = offset(p[0], p[1], 5.0, rnd)
        failCase("eyepiece, hint 5 deg away (radius 2)", eimg, SolveHints(eyeScale * 0.9, eyeScale * 1.1, off[0], off[1], 2.0), deep, SolveResult.Reason.NO_MATCH)
    }

    /** Stars at random places (not the real sky) must never be "solved" against the real catalogue. */
    @Test fun randomStarFieldsAreNeverSolved() {
        val rnd = Random(51)
        for (k in 0 until 4) {
            // 300 random "catalogue" stars per 60x45 field, brightness like the real sky
            val fake = ListStars(List(6000) { SkyStar(rnd.nextDouble() * 360, Math.toDegrees(asin(rnd.nextDouble() * 2 - 1)), 1.0 + 4 * rnd.nextDouble().let { it * it }) })
            val pos = randomSky(rnd)
            val truth = wideTruth(pos[0], pos[1], rnd, k % 2 == 1)
            val img = SyntheticSky.render(truth, RenderSpec(limitMag = 5.0), fake, 700L + k)
            failCase("random stars, wide blind #$k", img, wideHints(), bright, SolveResult.Reason.NO_MATCH)
            val c = offset(pos[0], pos[1], 10.0, rnd)
            failCase("random stars, wide hinted #$k", img, SolveHints(wideScale * 0.88, wideScale * 1.12, c[0], c[1], 20.0), bright, SolveResult.Reason.NO_MATCH)
        }
        for (k in 0 until 3) {
            val p = randomSky(rnd)
            val fake = ListStars(List(40) { SkyStar(p[0] + (rnd.nextDouble() - 0.5), p[1] + (rnd.nextDouble() - 0.5), 8.0 + 2.5 * rnd.nextDouble()) })
            val truth = eyepieceTruth(p, 10.0, k == 1)
            val img = SyntheticSky.render(truth, RenderSpec(limitMag = 10.5, circleRadiusPx = 0.5 / eyeScale), fake, 800L + k)
            failCase("random stars, eyepiece #$k", img, SolveHints(eyeScale * 0.9, eyeScale * 1.1, p[0], p[1], 1.0), deep, SolveResult.Reason.NO_MATCH)
        }
    }

    // ---------------------------------------------------------------------------------------------- detection

    @Test fun detectionFindsStarsAndIgnoresHotPixels() {
        val truth = Truth(83.8, -5.4, 20.0, wideScale, false, 1200, 900)
        val spec = RenderSpec(limitMag = 5.0, hotPixels = 25, missingFraction = 0.0, fakeStars = 0, psfSigma = 1.3)
        val img = SyntheticSky.render(truth, spec, bright, 11)
        val t0 = System.nanoTime()
        val det = PlateSolver.detectStars(img)
        val ms = (System.nanoTime() - t0) / 1e6
        val expected = bright.cone(truth.raDeg, truth.decDeg, truth.halfDiagonalDeg(), 5.0).mapNotNull { s ->
            truth.toPixel(s.raDeg, s.decDeg)?.takeIf { it[0] in 6.0..1194.0 && it[1] in 6.0..894.0 }?.let { Pair(it, s.mag) }
        }
        var found = 0
        val errs = ArrayList<Double>()
        for ((p, mag) in expected) {
            val best = det.minByOrNull { hypot(it.x - p[0], it.y - p[1]) }!!
            val d = hypot(best.x - p[0], best.y - p[1])
            if (d < 3) { found++; errs.add(d) }
        }
        errs.sort()
        val line = "detection: %d catalogue stars in frame, %d found, median centroid error %.2f px, %d detections, %.0f ms (1200x900)".format(
            expected.size, found, errs[errs.size / 2], det.size, ms)
        rows.add(line); println(line)
        assertTrue(line, found >= 0.9 * expected.size)
        assertTrue(line, errs[errs.size / 2] < 0.25)
        // extra detections are limited: the 25 hot pixels must not show up
        assertTrue(line, det.size <= expected.size + 8)
        // brightest first
        for (i in 1 until det.size) assertTrue(det[i - 1].flux >= det[i].flux)
        assertTrue("stars are round", det.take(20).all { it.elongation < 1.5 })
    }

    @Test fun detectionTimingLargeImage() {
        val truth = Truth(150.0, 30.0, 0.0, eyeScale, false, 1600, 1200)
        val img = SyntheticSky.render(truth, RenderSpec(limitMag = 10.5, circleRadiusPx = 590.0), deep, 12)
        val t0 = System.nanoTime()
        val det = PlateSolver.detectStars(img)
        val ms = (System.nanoTime() - t0) / 1e6
        val line = "detection 1600x1200: %d stars in %.0f ms".format(det.size, ms)
        rows.add(line); println(line)
        assertTrue(line, ms < 2000)
    }

    // ---------------------------------------------------------------------------------------------- conventions

    /** Pixel -> sky values from astropy's WCS (TAN) with the CD matrix of the roll / mirror convention. */
    @Test fun conventionMatchesAstropyWcs() {
        class G(val ra: Double, val dec: Double, val roll: Double, val s: Double, val mir: Boolean, val w: Int, val h: Int, val pts: List<DoubleArray>)
        val golden = listOf(
            G(83.8, -5.4, 0.0, 0.05, false, 1200, 900, listOf(
                doubleArrayOf(0.0, 0.0, 110.689954, 14.38158), doubleArrayOf(1200.0, 900.0, 55.156249, -23.945219),
                doubleArrayOf(100.5, 700.25, 107.887897, -16.261187), doubleArrayOf(1000.0, 50.0, 65.051647, 13.134))),
            G(83.8, -5.4, 0.0, 0.05, true, 1200, 900, listOf(
                doubleArrayOf(0.0, 0.0, 56.910046, 14.38158), doubleArrayOf(1200.0, 900.0, 112.443751, -23.945219),
                doubleArrayOf(100.5, 700.25, 59.712103, -16.261187), doubleArrayOf(1000.0, 50.0, 102.548353, 13.134))),
            G(201.3, 54.9, 37.0, 0.0008, false, 1600, 1200, listOf(
                doubleArrayOf(0.0, 0.0, 202.690952, 54.890239), doubleArrayOf(1200.0, 900.0, 200.604374, 54.898922),
                doubleArrayOf(100.5, 700.25, 201.986434, 54.497236), doubleArrayOf(1000.0, 50.0, 201.540966, 55.347444))),
            G(201.3, 54.9, 205.0, 0.0008, true, 1600, 1200, listOf(
                doubleArrayOf(0.0, 0.0, 201.944638, 54.192812), doubleArrayOf(1200.0, 900.0, 200.969125, 55.2523),
                doubleArrayOf(100.5, 700.25, 202.237076, 54.732577), doubleArrayOf(1000.0, 50.0, 200.729134, 54.567504))),
            G(10.0, -80.0, 310.0, 0.0552, false, 1200, 900, listOf(
                doubleArrayOf(0.0, 0.0, 12.555017, -44.161509), doubleArrayOf(1200.0, 900.0, 194.203042, -64.129462),
                doubleArrayOf(100.5, 700.25, 62.136972, -56.563959), doubleArrayOf(300.0, 400.0, 29.424947, -64.557527))),
            G(150.0, 85.0, 120.0, 0.02, true, 1000, 800, listOf(
                doubleArrayOf(0.0, 0.0, 238.313845, 78.272264), doubleArrayOf(600.0, 450.0, 133.303123, 83.496128),
                doubleArrayOf(100.5, 700.25, 343.846711, 85.031703), doubleArrayOf(300.0, 400.0, 202.354545, 87.479447))),
        )
        for (g in golden) {
            val solved = SolveResult.Solved(g.ra, g.dec, g.roll, g.s, g.mir, 0, 0.0, emptyList(), g.w, g.h)
            val truth = Truth(g.ra, g.dec, g.roll, g.s, g.mir, g.w, g.h)
            for (p in g.pts) {
                val (ra, dec) = solved.pixelToRaDec(p[0], p[1])
                val e = SyntheticSky.separationDeg(ra, dec, p[2], p[3]) * 3600
                assertTrue("pixelToRaDec vs astropy: ${e} arcsec at (${p[0]}, ${p[1]}) for roll ${g.roll} mirrored ${g.mir}", e < 0.05)
                val back = solved.raDecToPixel(p[2], p[3])!!
                assertTrue("raDecToPixel round trip", hypot(back.first - p[0], back.second - p[1]) < 1e-3)
                val tp = truth.toPixel(p[2], p[3])!!
                assertTrue("test projection vs astropy", hypot(tp[0] - p[0], tp[1] - p[1]) < 1e-3)
            }
        }
    }

    /** North up / east left at roll 0; mirroring flips east; a roll of 90 puts east at the top. */
    @Test fun rollAndMirrorMeanWhatTheyShouldMean() {
        fun solved(roll: Double, mir: Boolean) = SolveResult.Solved(100.0, 20.0, roll, 0.01, mir, 0, 0.0, emptyList(), 1000, 800)
        val up = solved(0.0, false).pixelToRaDec(500.0, 300.0)
        assertTrue("up is north", up.second > 20.0 && abs(up.first - 100.0) < 1e-6)
        val right = solved(0.0, false).pixelToRaDec(700.0, 400.0)
        assertTrue("right is west (decreasing RA)", right.first < 100.0)
        val rightMirrored = solved(0.0, true).pixelToRaDec(700.0, 400.0)
        assertTrue("mirrored: right is east", rightMirrored.first > 100.0)
        val up90 = solved(90.0, false).pixelToRaDec(500.0, 300.0)
        assertTrue("roll 90: up is east", up90.first > 100.0 && abs(up90.second - 20.0) < 0.01)
        val up180 = solved(180.0, false).pixelToRaDec(500.0, 300.0)
        assertTrue("roll 180: up is south", up180.second < 20.0)
    }
}
