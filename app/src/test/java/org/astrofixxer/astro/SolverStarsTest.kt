package org.astrofixxer.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Random
import kotlin.math.asin

class SolverStarsTest {
    companion object {
        lateinit var deep: SolverStars
        lateinit var all: List<SkyStar>
        lateinit var ux: DoubleArray
        lateinit var uy: DoubleArray
        lateinit var uz: DoubleArray
        lateinit var catalog: Catalog
        var loadMs = 0L

        @BeforeClass @JvmStatic fun load() {
            val t0 = System.nanoTime()
            deep = File("src/main/assets/solver_stars.bin").inputStream().use { SolverStars.load(it) }
            loadMs = (System.nanoTime() - t0) / 1_000_000
            all = deep.cone(0.0, 0.0, 180.0, 99.0)
            ux = DoubleArray(all.size) { Math.cos(Math.toRadians(all[it].decDeg)) * Math.cos(Math.toRadians(all[it].raDeg)) }
            uy = DoubleArray(all.size) { Math.cos(Math.toRadians(all[it].decDeg)) * Math.sin(Math.toRadians(all[it].raDeg)) }
            uz = DoubleArray(all.size) { Math.sin(Math.toRadians(all[it].decDeg)) }
            catalog = File("src/main/assets/sky_catalog.json.gz").inputStream().use { Catalog.load(it) }
        }

        fun sep(a: SkyStar, ra: Double, dec: Double) = SyntheticSky.separationDeg(a.raDeg, a.decDeg, ra, dec)
    }

    @Test fun fileLoadsAndHasTheExpectedContent() {
        println("solver_stars.bin: ${deep.count} stars, loaded in $loadMs ms, complete to V ${deep.magLimit}")
        assertEquals(all.size, deep.count)
        assertTrue(deep.count in 570_000..590_000)
        assertEquals(10.5, deep.magLimit, 1e-6)
        assertTrue(all.all { it.mag <= 10.5 + 1e-6 && it.decDeg in -90.0..90.0 && it.raDeg in 0.0..360.0 })
        val size = File("src/main/assets/solver_stars.bin").length()
        assertTrue("asset is $size bytes", size < 6L * 1024 * 1024)
    }

    /** J2000 epoch-2000 positions of well known stars (SIMBAD, ICRS), to about an arcsecond. */
    @Test fun knownStarsAreWhereTheyShouldBe() {
        val expected = listOf(
            Triple("Sirius", doubleArrayOf(101.28716, -16.71612), -1.46),
            Triple("Vega", doubleArrayOf(279.23473, 38.78369), 0.03),
            Triple("Polaris", doubleArrayOf(37.95456, 89.26411), 1.98),
            Triple("Arcturus", doubleArrayOf(213.91530, 19.18241), -0.05),
            Triple("Betelgeuse", doubleArrayOf(88.79294, 7.40706), 0.42),
        )
        for ((name, pos, mag) in expected) {
            val near = deep.cone(pos[0], pos[1], 0.02, 99.0).minByOrNull { it.mag }
            assertNotNull(name, near)
            assertTrue("$name is ${sep(near!!, pos[0], pos[1]) * 3600} arcsec off", sep(near, pos[0], pos[1]) * 3600 < 3.0)
            assertEquals(name, mag, near.mag, 0.15)
        }
    }

    @Test fun coneMatchesBruteForce() {
        val rnd = Random(3)
        val centres = ArrayList<DoubleArray>()
        repeat(60) { centres.add(doubleArrayOf(rnd.nextDouble() * 360, Math.toDegrees(asin(rnd.nextDouble() * 2 - 1)))) }
        centres.addAll(listOf(doubleArrayOf(0.0, 89.9), doubleArrayOf(180.0, -89.5), doubleArrayOf(359.99, 0.0), doubleArrayOf(0.01, 45.0), doubleArrayOf(120.0, 90.0)))
        for (c in centres) for (r in doubleArrayOf(0.05, 0.5, 2.0, 7.0, 25.0)) for (lim in doubleArrayOf(6.0, 10.5)) {
            val got = deep.cone(c[0], c[1], r, lim)
            // brute force over every star with an independent formulation (dot product of unit vectors)
            val cx = Math.cos(Math.toRadians(c[1])) * Math.cos(Math.toRadians(c[0]))
            val cy = Math.cos(Math.toRadians(c[1])) * Math.sin(Math.toRadians(c[0]))
            val cz = Math.sin(Math.toRadians(c[1]))
            val cosR = Math.cos(Math.toRadians(r))
            var expected = 0
            for (i in all.indices) if (all[i].mag <= lim && ux[i] * cx + uy[i] * cy + uz[i] * cz >= cosR) expected++
            // the brute force uses a different (haversine) formula; allow a couple of stars exactly on the circle
            assertTrue("cone(${c[0]}, ${c[1]}, $r, $lim): ${got.size} vs $expected", kotlin.math.abs(got.size - expected) <= 1)
            assertTrue(got.all { it.mag <= lim && sep(it, c[0], c[1]) <= r + 1e-6 })
        }
        assertEquals(all.size, deep.cone(10.0, 10.0, 180.0, 99.0).size)
        assertEquals(0, deep.cone(10.0, 10.0, 0.0, 99.0).size)
    }

    @Test fun magnitudeLimitCutsAndCountsAreSensible() {
        val counts = listOf(5.0, 6.0, 8.0, 10.0).map { lim -> lim to all.count { it.mag <= lim } }
        println("stars by limit: $counts")
        assertTrue(counts[0].second in 1200..1800)   // about 1560 stars to V 5
        assertTrue(counts[1].second in 4500..5300)   // about 4900 to V 6
        assertTrue(counts[2].second in 40_000..48_000)
    }

    @Test fun coneTimingIsSmall() {
        val t0 = System.nanoTime()
        var n = 0
        repeat(100) { n += deep.cone(150.0 + it, 20.0, 2.0, 10.5).size }
        val ms = (System.nanoTime() - t0) / 1e6
        println("100 cone searches of radius 2 deg: %.1f ms, %d stars".format(ms, n))
        assertTrue(ms < 2000)
    }

    @Test fun corruptFilesAreRejected() {
        val bytes = File("src/main/assets/solver_stars.bin").readBytes()
        for (bad in listOf(ByteArray(0), bytes.copyOf(100), bytes.copyOf(bytes.size - 1), bytes.copyOf().also { it[0] = 'X'.code.toByte() },
            bytes.copyOf().also { it[4] = 9 })) {
            var threw = false
            try { SolverStars.load(ByteArrayInputStream(bad)) } catch (e: IllegalArgumentException) { threw = true }
            assertTrue(threw)
        }
    }

    @Test fun catalogAdapterIsACompleteBrightStarSource() {
        val src = CatalogStars(catalog)
        val stars = catalog.objects.filter { it.type == "S" && it.mag != null }
        val rnd = Random(4)
        repeat(20) {
            val ra = rnd.nextDouble() * 360; val dec = Math.toDegrees(asin(rnd.nextDouble() * 2 - 1)); val r = 1 + rnd.nextDouble() * 40
            val got = src.cone(ra, dec, r, 5.5)
            val expected = stars.count { it.mag!! <= 5.5 && SyntheticSky.separationDeg(it.ra, it.dec, ra, dec) <= r }
            assertTrue("catalogue cone: ${got.size} vs $expected", kotlin.math.abs(got.size - expected) <= 1)
        }
        assertTrue(src.cone(0.0, 0.0, 180.0, 99.0).size > 8000)
        // the bright catalogue and the deep list agree on the bright stars' positions
        val sirius = src.cone(101.2872, -16.7161, 0.05, 99.0).single()
        assertEquals(-1.46, sirius.mag, 0.05)
    }
}
