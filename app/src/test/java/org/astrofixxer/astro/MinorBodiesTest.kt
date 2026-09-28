package org.astrofixxer.astro

import org.astrofixxer.astro.vsop87.Vsop87LargeEarth
import org.astrofixxer.astro.vsop87.vsop87a_xsmall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class MinorBodiesTest {
    /** Builds a body from JPL "approximate positions of the planets" J2000 elements (Standish): a, e, I, L, ϖ, Ω. */
    private fun standish(name: String, a: Double, e: Double, i: Double, l: Double, lonPeri: Double, node: Double): MinorBody {
        val meanAnomaly = l - lonPeri
        val n = MinorBody.GAUSS_K / (a * sqrt(a)) * 180 / Math.PI
        return MinorBody(name, "test", a * (1 - e), e, i, node, lonPeri - node, 2451545.0 - meanAnomaly / n, 2451545.0, null, null)
    }

    private fun dist(a: DoubleArray, b: DoubleArray) =
        sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]))

    @Test fun keplerMatchesVsop87ForEarthMoonBarycentreAndMars() {
        val emb = standish("EMB", 1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0)
        val mars = standish("Mars", 1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891)
        for (jd in listOf(2451545.0, 2451545.0 + 200, 2451545.0 - 300)) {
            val t = (jd - 2451545.0) / 365250.0
            assertEquals("EMB at $jd", 0.0, dist(emb.eclipticPosition(jd), Vsop87LargeEarth.getEmb(t)), 2e-4)
            assertEquals("Mars at $jd", 0.0, dist(mars.eclipticPosition(jd), vsop87a_xsmall.getMars(t)), 5e-4)
        }
    }

    @Test fun eachOrbitTypeIsAtPerihelionDistanceAtPerihelionTime() {
        for (e in listOf(0.2, 0.97, 1.0, 1.3)) {
            val body = MinorBody("e=$e", "comet", 1.2, e, 10.0, 30.0, 50.0, 2461000.0, 2461000.0, 8.0, 4.0)
            val p = body.eclipticPosition(2461000.0)
            assertEquals("r at perihelion, e=$e", 1.2, sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]), 1e-9)
            val later = body.eclipticPosition(2461030.0)
            assertTrue("moves away after perihelion, e=$e", sqrt(later[0] * later[0] + later[1] * later[1] + later[2] * later[2]) > 1.2)
        }
    }

    @Test fun keplerSolversInvert() {
        for (e in listOf(0.0, 0.5, 0.95, 0.999)) for (m in listOf(-3.0, -0.1, 0.0, 0.2, 2.5)) {
            val ea = MinorBody.solveElliptic(m, e)
            assertEquals("elliptic e=$e m=$m", m, ea - e * kotlin.math.sin(ea), 1e-10)
        }
        for (e in listOf(1.01, 2.0, 5.0)) for (m in listOf(-10.0, 0.0, 0.5, 30.0)) {
            val ha = MinorBody.solveHyperbolic(m, e)
            assertEquals("hyperbolic e=$e m=$m", m, e * kotlin.math.sinh(ha) - ha, 1e-9)
        }
    }

    @Test fun bundledCatalogueLoadsAndGivesSanePlaces() {
        val bodies = MinorBody.parse(File("src/main/assets/minor_bodies.json").readText())
        assertTrue(bodies.count { it.isComet } > 50)
        val ceres = bodies.first { it.name == "Ceres" }
        val place = ceres.geocentric(2461313.5) // 2026-09-28
        assertTrue("Ceres heliocentric distance ${place.r}", place.r in 2.5..3.0)
        assertTrue("Ceres magnitude ${place.mag}", place.mag!! in 6.0..9.5)
        for (b in bodies) {
            val p = b.geocentric(2461313.5)
            assertTrue("${b.name} ra", p.ra in 0.0..(2 * Math.PI))
            assertTrue("${b.name} dec", p.dec in -Math.PI / 2..Math.PI / 2)
        }
    }
}
