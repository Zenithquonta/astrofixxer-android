package org.astrofixxer.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class CatalogTest {
    companion object {
        lateinit var catalog: Catalog
        var loadMillis = 0L

        @BeforeClass @JvmStatic fun load() {
            val t0 = System.currentTimeMillis()
            catalog = File("src/main/assets/sky_catalog.json.gz").inputStream().use { Catalog.load(it) }
            loadMillis = System.currentTimeMillis() - t0
            println("Catalog: ${catalog.objects.size} objects loaded in $loadMillis ms")
        }
    }

    @Test fun loadsEverything() {
        assertTrue(catalog.objects.size > 100_000)
        assertEquals(88, catalog.constellations.getValue("modern").size)
        assertEquals(49, catalog.constellations.getValue("indian").size)
    }

    @Test fun searchesByNameIdAndIndianName() {
        assertEquals("M31", catalog.search("Andromeda Galaxy").first().name)
        assertEquals("M31", catalog.search("ngc 224").first().name)
        assertEquals("M31", catalog.search("m 031").first().name)
        assertEquals("M42", catalog.search("Orion Nebula").first().name)
        assertEquals("M45", catalog.search("Pleiades").first().name)
        assertEquals("Sirius", catalog.search("Lubdhaka").first().name)
        assertEquals("Sirius", catalog.search("HIP 32349").first().name)
        assertTrue(catalog.search("Andromeda").isNotEmpty())
        assertTrue(catalog.search("   ").isEmpty())
    }

    @Test fun nearFindsNeighboursAndRespectsRadiusAndMagnitude() {
        val m42 = catalog.search("M42").first()
        val around = catalog.near(m42.ra, m42.dec, 1.0)
        assertTrue(around.any { it.name == "M43" })
        assertTrue(around.none { it.name == "M31" })
        assertTrue(catalog.near(m42.ra, m42.dec, 1.0, magLimit = 5.0).all { (it.mag ?: -99.0) <= 5.0 })
        // Across RA 0/360 and near the pole.
        val polaris = catalog.search("Polaris").first()
        assertTrue(catalog.near(polaris.ra, polaris.dec, 2.0).any { it.name == "Polaris" })
        assertTrue(catalog.near(359.9, 0.0, 1.0).isNotEmpty() || catalog.near(0.1, 0.0, 1.0).isNotEmpty())
    }

    @Test fun constellationLinesUseStarsInTheCatalogue() {
        val orion = catalog.constellations.getValue("modern").first { it.id.endsWith(" Ori") }
        val hips = orion.lines.flatMap { it.toList() }.filter { it > 0 }
        assertTrue(hips.isNotEmpty())
        assertTrue(hips.count { it in catalog.starsByHip } > hips.size / 2)
    }
}
