package org.astrofixxer.astro

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Checks Pointing.rayFromPos against astropy (tools/golden/precession_reference.py), the reference for precession. */
class PrecessionTest {
    private val d2r = PI / 180
    private val arcsec = 1.0 / 3600
    private val j2000Millis = 946728000000L // 2000-01-01T12:00:00 UTC

    private class Case(val star: String, val ra: Double, val dec: Double, val time: Long, val site: String, val lat: Double, val lon: Double, val ref: DoubleArray, val date: String)

    private val cases: List<Case> = run {
        val arr = JSONObject(javaClass.getResource("/precession_reference.json")!!.readText()).getJSONArray("cases")
        List(arr.length()) {
            val c = arr.getJSONObject(it)
            val alt = c.getDouble("altDeg") * d2r
            val az = c.getDouble("azDeg") * d2r
            Case(
                c.getString("star"), c.getDouble("ra"), c.getDouble("dec"), c.getLong("timeMillis"), c.getString("site"),
                c.getDouble("lat"), c.getDouble("lon"), doubleArrayOf(sin(az) * cos(alt), cos(az) * cos(alt), sin(alt)), c.getString("date"),
            )
        }
    }

    /** The formula used before precession was added: Earth Rotation Angle, J2000 coordinates taken as of date. */
    private fun oldRay(ra: Double, dec: Double, timeMillis: Long, latDeg: Double, lonDeg: Double): DoubleArray {
        val tu = JulianDate.fromEpochMillis(timeMillis) - 2451545.0
        val angle = 2 * PI * (0.7790572732640 + 1.00273781191135448 * tu)
        val h = angle + lonDeg * d2r - ra * d2r
        val f = latDeg * d2r
        val de = dec * d2r
        val az = atan2(sin(h), cos(h) * sin(f) - tan(de) * cos(f))
        val alt = asin(sin(f) * sin(de) + cos(f) * cos(de) * cos(h))
        return doubleArrayOf(-sin(az) * cos(alt), -cos(az) * cos(alt), sin(alt))
    }

    /** Angle between two unit vectors in arcseconds (atan2 form: acos loses precision below ~0.01"). */
    private fun sepArcsec(a: DoubleArray, b: DoubleArray): Double {
        val x = Pointing.cross(a, b)
        return atan2(sqrt(Pointing.dot(x, x)), Pointing.dot(a, b)) / d2r * 3600
    }

    private fun Case.label() = "$star $date $site"

    @Test fun referenceHasEnoughCases() {
        assertTrue("cases: ${cases.size}", cases.size >= 40)
        assertEquals(4, cases.map { it.date }.toSet().size)
        assertEquals(3, cases.map { it.site }.toSet().size)
        assertTrue(cases.map { it.star }.toSet().size >= 8)
    }

    @Test fun matchesAstropyWithinAMinute() {
        var worst = 0.0
        var worstCase = ""
        for (c in cases) {
            val sep = sepArcsec(c.ref, Pointing.rayFromPos(c.ra, c.dec, c.time, c.lat, c.lon))
            if (sep > worst) { worst = sep; worstCase = c.label() }
            assertTrue("${c.label()}: ${"%.1f".format(sep)}\" from astropy", sep < 60.0)
        }
        println("worst error vs astropy: ${"%.1f".format(worst)}\" ($worstCase), ${cases.size} cases")
    }

    @Test fun oldFormulaIsCaughtBySensitivity() {
        var worst = 0.0
        for (c in cases) worst = maxOf(worst, sepArcsec(c.ref, oldRay(c.ra, c.dec, c.time, c.lat, c.lon)))
        println("worst error of the old formula vs astropy: ${"%.1f".format(worst)}\"")
        assertTrue("old formula worst ${worst}\" should exceed 3'", worst > 180.0)
    }

    @Test fun noDoubleCountingAtJ2000() {
        val m = Pointing.precessionMatrix(0.0)
        for (i in 0 until 9) assertEquals("precession[$i]", Pointing.IDENTITY[i], m[i], 1e-9)
        for (c in cases) {
            val sep = sepArcsec(oldRay(c.ra, c.dec, j2000Millis, c.lat, c.lon), Pointing.rayFromPos(c.ra, c.dec, j2000Millis, c.lat, c.lon))
            // At J2000.0 ERA and GMST differ by well under an arcsecond, so nothing may be counted twice.
            assertTrue("${c.star} ${c.site}: ${sep}\" at J2000.0", sep < 1.0)
        }
    }

    @Test fun rayToRaDecRoundTripsForRandomInputs() {
        val rnd = Random(42)
        val times = longArrayOf(j2000Millis, 1790000000000L, 1922000000000L, 2223406800000L)
        val inputs = ArrayList<Pair<Double, Double>>()
        repeat(200) { inputs.add(Pair(rnd.nextDouble() * 360, rnd.nextDouble() * 180 - 90)) }
        inputs.add(Pair(10.0, 89.9)); inputs.add(Pair(200.0, -89.9)); inputs.add(Pair(359.99, 89.9)); inputs.add(Pair(0.0, -89.9))
        for ((ra, dec) in inputs) for (t in times) for ((lat, lon) in listOf(Pair(28.6, 77.2), Pair(-33.9, 151.2), Pair(69.65, 18.96))) {
            val (ra2, dec2) = Pointing.rayToRaDec(Pointing.rayFromPos(ra, dec, t, lat, lon), t, lat, lon)
            val a = doubleArrayOf(cos(dec * d2r) * cos(ra * d2r), cos(dec * d2r) * sin(ra * d2r), sin(dec * d2r))
            val b = doubleArrayOf(cos(dec2 * d2r) * cos(ra2 * d2r), cos(dec2 * d2r) * sin(ra2 * d2r), sin(dec2 * d2r))
            val sep = sepArcsec(a, b)
            assertTrue("($ra, $dec) at $t: round trip ${sep}\"", sep < 0.001)
            if (abs(dec) < 89.0) assertEquals("ra ($ra, $dec)", 0.0, (ra2 - ra + 180).mod(360.0) - 180, 0.001 * arcsec / cos(dec * d2r) + 1e-12)
        }
    }

    @Test fun ofDateTruePoleSitsAtLatitude() {
        for (lat in listOf(28.6139, -33.87, 69.65, 0.0)) for (ra in listOf(0.0, 123.4, 300.0)) for (t in listOf(j2000Millis, 1790000000000L)) {
            val ray = Pointing.rayFromPosOfDate(ra, 90.0, t, lat, 77.2)
            assertEquals("lat $lat ra $ra", lat, asin(ray[2]) / d2r, 1e-6)
        }
    }

    @Test fun cacheFollowsTimeChanges() {
        val a = Pointing.rayFromPos(100.0, 20.0, j2000Millis, 28.6, 77.2)
        Pointing.rayFromPos(100.0, 20.0, 2223406800000L, 28.6, 77.2)
        val again = Pointing.rayFromPos(100.0, 20.0, j2000Millis, 28.6, 77.2)
        for (i in 0..2) assertEquals(a[i], again[i], 0.0)
    }
}
