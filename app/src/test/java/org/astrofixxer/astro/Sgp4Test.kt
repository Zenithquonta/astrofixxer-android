package org.astrofixxer.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class Sgp4Test {
    // Vallado et al. 2006 verification case 00005 (Vanguard 1), WGS-72, from the published tcppver.out.
    private val vanguard = Sgp4("VANGUARD 1",
        "1 00005U 58002B   00179.78495062  .00000023  00000-0  28098-4 0  4753",
        "2 00005  34.2682 348.7242 1859667 331.7664  19.3264 10.82419157413667")

    private fun assertVec(msg: String, expected: DoubleArray, actual: DoubleArray, tol: Double) {
        for (i in 0..2) assertEquals("$msg[$i]", expected[i], actual[i], tol)
    }

    @Test fun matchesValladoVerificationCase00005() {
        val (r0, v0) = vanguard.propagate(0.0)
        assertVec("r(0)", doubleArrayOf(7022.46529266, -1400.08296755, 0.03995155), r0, 1e-3)
        assertVec("v(0)", doubleArrayOf(1.893841015, 6.405893759, 4.534807250), v0, 1e-6)
        val (r360, v360) = vanguard.propagate(360.0)
        assertVec("r(360)", doubleArrayOf(-7154.03120202, -3783.17682504, -3536.19412294), r360, 1e-3)
        assertVec("v(360)", doubleArrayOf(4.741887409, -4.151817765, -2.093935425), v360, 1e-6)
    }

    @Test fun issOrbitLooksLikeTheIss() {
        // Classic ISS TLE (2008) used in many references.
        val iss = Sgp4.parse("""
            ISS (ZARYA)
            1 25544U 98067A   08264.51782528 -.00002182  00000-0 -11606-4 0  2927
            2 25544  51.6416 247.4627 0006703 130.5360 325.0288 15.72125391563537
        """.trimIndent()).single()
        assertEquals("25544", iss.catalogNumber)
        for (t in listOf(0.0, 45.0, 90.0, 600.0)) {
            val (r, v) = iss.propagate(t)
            val altitude = sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]) - Sgp4.RADIUS
            val speed = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            assertTrue("altitude $altitude km", altitude in 320.0..380.0)
            assertTrue("speed $speed km/s", speed in 7.5..7.9)
        }
        assertEquals(2008, java.time.Instant.ofEpochMilli(((iss.epochJd - 2440587.5) * 86400000).toLong()).atZone(java.time.ZoneOffset.UTC).year)
    }

    @Test fun topocentricGeometry() {
        // A point 400 km straight above the observer is at the zenith.
        val o = Satellites.observerEcef(28.6139, 77.2090)
        val n = sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2])
        val up = doubleArrayOf(o[0] * (1 + 400 / n), o[1] * (1 + 400 / n), o[2] * (1 + 400 / n))
        assertEquals(90.0, Satellites.altAz(up, 28.6139, 77.2090).first, 0.2)
        // A point far to the north is near the northern horizon.
        val (alt, az) = Satellites.altAz(doubleArrayOf(0.0, 0.0, 30000.0), 28.6139, 77.2090)
        assertTrue(az < 5 || az > 355)
        assertTrue(alt in 0.0..70.0)
    }

    @Test fun issPassesOverDelhiNearItsEpoch() {
        val iss = Sgp4("ISS (ZARYA)",
            "1 25544U 98067A   08264.51782528 -.00002182  00000-0 -11606-4 0  2927",
            "2 25544  51.6416 247.4627 0006703 130.5360 325.0288 15.72125391563537")
        val passes = Satellites.passes(iss, iss.epochJd, 1.0, 28.6139, 77.2090)
        assertTrue("passes in a day: ${passes.size}", passes.size in 2..8)
        for (p in passes) {
            val minutes = (p.endJd - p.startJd) * 1440
            assertTrue("pass length $minutes min", minutes in 0.3..8.0)
            assertTrue(p.maxAltDeg in 10.0..90.0)
            assertTrue(p.maxJd in p.startJd..p.endJd)
        }
    }

    @Test fun deepSpaceOrbitsAreSkipped() {
        // A geostationary TLE (period ~1436 min) is deep space and not supported by this port.
        val geo = Sgp4.parse("""
            GEO TEST
            1 28626U 05008A   08264.51782528  .00000000  00000-0  00000-0 0  9999
            2 28626   0.0100 100.0000 0001000  50.0000 250.0000  1.00270000 12345
        """.trimIndent())
        assertTrue(geo.isEmpty())
    }
}
