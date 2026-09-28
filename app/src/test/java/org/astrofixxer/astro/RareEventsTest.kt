package org.astrofixxer.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class RareEventsTest {
    private fun jd(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) = JulianDate.fromGregorian(y, mo, d, h, mi, 0.0)

    @Test fun mercuryTransitOf2032November13() {
        val transits = Events.planetTransits(ApparentPosition.MERCURY, jd(2032, 11, 1), 30)
        assertEquals(1, transits.size)
        assertEquals("greatest transit ~08:54 UTC", jd(2032, 11, 13, 8, 54), transits[0].jd, 1.0 / 24)
    }

    @Test fun noMercuryOrVenusTransitIn2026() {
        assertTrue(Events.planetTransits(ApparentPosition.MERCURY, jd(2026, 1, 1), 365).isEmpty())
        assertTrue(Events.planetTransits(ApparentPosition.VENUS, jd(2026, 1, 1), 365).isEmpty())
    }

    @Test fun fullMoonDistancesAreRealistic() {
        val all = Events.supermoons(jd(2026, 1, 1), 365, maxKm = 1e9)
        assertEquals("2026 has 13 full moons (blue moon on May 31)", 13, all.size)
        assertTrue(all.all { (_, km) -> km in 355_000.0..407_000.0 })
        val supermoons = Events.supermoons(jd(2026, 1, 1), 365)
        assertTrue(supermoons.isNotEmpty() && supermoons.size < 5)
    }

    @Test fun occultationOfAStarPlacedOnTheMoonsPath() {
        val lat = 28.6139
        val lon = 77.2090
        val t0 = jd(2026, 10, 20, 15, 0)
        val moon = ApparentPosition.reduce(ApparentPosition.MOON, t0, lat * PI / 180, lon * PI / 180)
        val onPath = Triple("Test star", moon.raJ2000 * 180 / PI, moon.decJ2000 * 180 / PI)
        val farAway = Triple("Far star", moon.raJ2000 * 180 / PI, moon.decJ2000 * 180 / PI + 2.0)
        val found = Events.lunarOccultations(listOf(onPath, farAway), t0 - 0.25, 1, lat, lon)
        assertEquals(1, found.size)
        val o = found[0]
        assertEquals("Test star", o.name)
        assertTrue("disappears before t0", o.disappearJd < t0 && o.reappearJd > t0)
        val minutes = (o.reappearJd - o.disappearJd) * 1440
        assertTrue("central occultation lasts about an hour: $minutes min", minutes in 40.0..120.0)
    }

    @Test fun planetGatheringSpanIsComputed() {
        // Every day has some span; with minPlanets = 2 and a generous span there must be gatherings.
        val loose = Events.planetGatherings(jd(2026, 1, 1), 60, minPlanets = 2, spanDeg = 30.0)
        assertTrue(loose.isNotEmpty())
        assertTrue(loose.all { (_, span) -> span <= 30.0 })
    }
}
