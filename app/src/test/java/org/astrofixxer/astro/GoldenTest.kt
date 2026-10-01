package org.astrofixxer.astro

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Compares the Kotlin port with values produced by the web app's own JS (tools/golden/golden_from_web.js). */
class GoldenTest {
    private val golden = JSONObject(javaClass.getResource("/golden.json")!!.readText())

    private fun JSONArray.doubles() = DoubleArray(length()) { getDouble(it) }
    private fun JSONObject.xyz() = doubleArrayOf(getDouble("x"), getDouble("y"), getDouble("z"))
    private fun assertVec(msg: String, expected: DoubleArray, actual: DoubleArray, tol: Double) {
        for (i in expected.indices) assertEquals("$msg[$i]", expected[i], actual[i], tol)
    }

    /**
     * The web app's rayFromPos verbatim: Earth Rotation Angle, no precession (J2000 coordinates used as if of date).
     * Pointing.rayFromPos now precesses to the date and uses GMST, with astropy as the reference (see
     * PrecessionTest), so it intentionally differs from these web golden values by up to ~0.2 degrees.
     * The golden values are still used where the maths under test is independent of that: alignment and bearings.
     */
    private fun webRay(ra: Double, dec: Double, timeMillis: Long, latDeg: Double, lonDeg: Double): DoubleArray {
        val d2r = PI / 180
        val tu = JulianDate.fromEpochMillis(timeMillis) - 2451545.0
        val h = 2 * PI * (0.7790572732640 + 1.00273781191135448 * tu) + lonDeg * d2r - ra * d2r
        val f = latDeg * d2r
        val de = dec * d2r
        val az = atan2(sin(h), cos(h) * sin(f) - tan(de) * cos(f))
        val alt = asin(sin(f) * sin(de) + cos(f) * cos(de) * cos(h))
        return doubleArrayOf(-sin(az) * cos(alt), -cos(az) * cos(alt), sin(alt))
    }

    private fun separationDeg(a: DoubleArray, b: DoubleArray): Double {
        val x = Pointing.cross(a, b)
        return atan2(sqrt(Pointing.dot(x, x)), Pointing.dot(a, b)) * 180 / PI
    }

    @Test fun reduceMatchesWebApp() {
        val cases = golden.getJSONArray("reduce")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val d = c.getJSONArray("date")
            val jd = JulianDate.fromGregorian(d.getInt(0), d.getInt(1), d.getInt(2), d.getInt(3), d.getInt(4), d.getDouble(5))
            assertEquals("jd", c.getDouble("jd"), jd, 1e-9)
            val r = ApparentPosition.reduce(c.getInt("body"), jd, c.getDouble("lat") * PI / 180, c.getDouble("lon") * PI / 180)
            val msg = "${c.getString("place")} ${d} body ${c.getInt("body")}"
            assertVec(msg, c.getJSONArray("result").doubles(), doubleArrayOf(r.raJ2000, r.decJ2000, r.ra, r.dec, r.az, r.alt), 1e-9)
        }
    }

    @Test fun starRaysMatchWebAppExceptForPrecession() {
        val cases = golden.getJSONArray("rays")
        var worst = 0.0
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val ra = c.getDouble("ra"); val de = c.getDouble("de"); val time = c.getLong("time")
            val lat = c.getDouble("lat"); val lon = c.getDouble("lon")
            val web = c.getJSONArray("ray").doubles()
            // The verbatim web formula is still reproduced exactly, so the golden file itself stays checked.
            assertVec("web ray $i", web, webRay(ra, de, time, lat, lon), 1e-12)
            // Documented, intentional difference: rayFromPos adds precession (the web app has none), <= 0.2 degrees.
            val diff = separationDeg(web, Pointing.rayFromPos(ra, de, time, lat, lon))
            worst = maxOf(worst, diff)
            assertEquals("ray $i vs web", 0.0, diff, 0.2)
        }
        println("worst rayFromPos difference from the web app: ${"%.3f".format(worst)} deg")
    }

    @Test fun rayToRaDecInvertsRayFromPos() {
        val cases = golden.getJSONArray("rays")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val time = c.getLong("time")
            val ray = Pointing.rayFromPos(c.getDouble("ra"), c.getDouble("de"), time, c.getDouble("lat"), c.getDouble("lon"))
            val (ra, dec) = Pointing.rayToRaDec(ray, time, c.getDouble("lat"), c.getDouble("lon"))
            assertEquals("dec $i", c.getDouble("de"), dec, 1e-9)
            if (kotlin.math.abs(dec) < 89.0) assertEquals("ra $i", 0.0, ((ra - c.getDouble("ra") + 180).mod(360.0) - 180), 1e-7)
        }
    }

    @Test fun rotationMatrixMatchesWebApp() {
        val cases = golden.getJSONArray("rotations")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val m = Pointing.rotationMatrix(c.getDouble("alpha"), c.getDouble("beta"), c.getDouble("gamma"))
            assertVec("rotation $i", c.getJSONArray("matrix").doubles(), m, 1e-12)
        }
    }

    @Test fun alignmentFlowMatchesWebApp() {
        val cases = golden.getJSONArray("alignment")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val time = c.getLong("time")
            val lat = c.getDouble("lat")
            val lon = c.getDouble("lon")
            val star = c.getJSONArray("star")
            val target = c.getJSONArray("target")

            val device = Pointing.rotationMatrix(c.getDouble("alpha"), c.getDouble("beta"), c.getDouble("gamma"))
            val uncorrected = Pointing.cameraRays(device)
            val expectedUnc = c.getJSONArray("uncorrected")
            for (k in 0..2) assertVec("uncorrected $i/$k", expectedUnc.getJSONArray(k).doubles(), uncorrected[k], 1e-12)

            // Web rays feed the exact-match checks (alignMatrix and bearing maths); see webRay for why.
            val starRay = webRay(star.getDouble(0), star.getDouble(1), time, lat, lon)
            val matrix = Pointing.alignMatrix(uncorrected, starRay)
            assertVec("align matrix $i", c.getJSONArray("matrix").doubles(), matrix, 1e-12)

            val corrected = Pointing.cameraRays(device, matrix)
            val starBearing = Pointing.bearing(starRay, corrected)
            assertVec("star bearing $i", c.getJSONObject("starBearing").xyz(), starBearing, 1e-12)
            assertVec("star centred $i", doubleArrayOf(0.0, 0.0, 1.0), starBearing, 1e-9)

            val targetRay = webRay(target.getDouble(0), target.getDouble(1), time, lat, lon)
            assertVec("target bearing $i", c.getJSONObject("targetBearing").xyz(), Pointing.bearing(targetRay, corrected), 1e-12)

            // Self-consistency with the real (precessed) rays: aligning on a star centres that star.
            val trueStar = Pointing.rayFromPos(star.getDouble(0), star.getDouble(1), time, lat, lon)
            val trueMatrix = Pointing.alignMatrix(uncorrected, trueStar)
            assertVec("precessed star centred $i", doubleArrayOf(0.0, 0.0, 1.0), Pointing.bearing(trueStar, Pointing.cameraRays(device, trueMatrix)), 1e-9)
        }
    }

    @Test fun deltaAltAzReachesZeroOnTargetAndMatchesSeparation() {
        val c = golden.getJSONArray("alignment").getJSONObject(0) // Vega -> M57
        val time = c.getLong("time")
        val star = c.getJSONArray("star")
        val target = c.getJSONArray("target")
        val s = Pointing.rayFromPos(star.getDouble(0), star.getDouble(1), time, c.getDouble("lat"), c.getDouble("lon"))
        val t = Pointing.rayFromPos(target.getDouble(0), target.getDouble(1), time, c.getDouble("lat"), c.getDouble("lon"))
        val (zeroAlt, zeroAz) = Pointing.deltaAltAz(t, t)
        assertEquals(0.0, zeroAlt, 1e-12)
        assertEquals(0.0, zeroAz, 1e-12)
        val (dAlt, _) = Pointing.deltaAltAz(s, t)
        val separation = acos(Pointing.dot(s, t)) * 180 / PI
        assertEquals(6.67, separation, 0.01)
        assert(kotlin.math.abs(dAlt) <= separation)
    }
}
