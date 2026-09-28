package org.astrofixxer.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks against well-known 2026 events (UTC). Tolerances are what the app needs, not what VSOP87 could do. */
class EventsTest {
    private fun jd(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) = JulianDate.fromGregorian(y, mo, d, h, mi, 0.0)
    private val minute = 1.0 / 1440

    @Test fun marchEquinox2026() {
        val t = Events.nextSolarLongitude(jd(2026, 3, 1), 0.0, ofDate = true)
        assertEquals("equinox 2026-03-20 14:46 UTC", jd(2026, 3, 20, 14, 46), t, 10 * minute)
        // Perseids peak at J2000 solar longitude 140.0: 2026-08-13 ~02:00 UTC in the importer's calendar.
        val perseids = Events.nextSolarLongitude(jd(2026, 8, 1), 140.0, ofDate = false)
        assertEquals("Perseids peak", jd(2026, 8, 13, 2, 0), perseids, 90 * minute)
    }

    @Test fun fullMoonAndTotalLunarEclipse2026March3() {
        val full = Events.nextPhase(jd(2026, 2, 25), 180.0)
        assertEquals("full moon 2026-03-03 11:38 UTC", jd(2026, 3, 3, 11, 38), full, 30 * minute)
        val eclipse = Events.lunarEclipse(full)
        assertNotNull(eclipse)
        assertEquals(Events.LunarEclipseType.TOTAL, eclipse!!.type)
        assertEquals("greatest eclipse ~11:33 UTC", jd(2026, 3, 3, 11, 33), eclipse.jdMax, 30 * minute)
    }

    @Test fun newMoonAndTotalSolarEclipse2026August12() {
        val newMoon = Events.nextPhase(jd(2026, 8, 5), 0.0)
        assertEquals("new moon 2026-08-12 17:37 UTC", jd(2026, 8, 12, 17, 37), newMoon, 30 * minute)
        val eclipse = Events.solarEclipse(newMoon)
        assertNotNull(eclipse)
        assertEquals(Events.SolarEclipseType.TOTAL, eclipse!!.type)
    }

    @Test fun ordinaryNewMoonHasNoEclipse() {
        // 2026-04-17 new moon: the Moon is far from its node, no eclipse.
        val newMoon = Events.nextPhase(jd(2026, 4, 10), 0.0)
        assertNull(Events.solarEclipse(newMoon))
        assertNull(Events.lunarEclipse(Events.nextPhase(jd(2026, 4, 25), 180.0)))
    }

    @Test fun moonIlluminationAtFullAndNew() {
        assertTrue(Events.moonIllumination(jd(2026, 3, 3, 11, 38)) > 0.99)
        assertTrue(Events.moonIllumination(jd(2026, 8, 12, 17, 37)) < 0.01)
    }

    @Test fun sunRisesAndSetsInDelhiAtSensibleTimes() {
        // 2026-09-28, New Delhi (IST = UTC+5:30): sunrise ~06:13, sunset ~18:08 local.
        val (rise, set) = Events.riseSet(ApparentPosition.SUN, jd(2026, 9, 28) - 5.5 / 24, 28.6139, 77.2090, -0.833)
        assertEquals("sunrise", jd(2026, 9, 28, 6, 13) - 5.5 / 24, rise!!, 10 * minute)
        assertEquals("sunset", jd(2026, 9, 28, 18, 8) - 5.5 / 24, set!!, 10 * minute)
    }

    @Test fun conjunctionSearchFindsTheScanMinimum() {
        val from = jd(2026, 1, 1)
        val found = Events.conjunctions(ApparentPosition.VENUS, ApparentPosition.JUPITER, from, 365, 5.0)
        for (c in found) {
            for (dt in listOf(-0.05, 0.05)) {
                val near = Math.toDegrees(Events.angle(Events.geocentric(c.bodyA, c.jd + dt), Events.geocentric(c.bodyB, c.jd + dt)))
                assertTrue("minimum at ${c.jd}", near >= c.separationDeg - 1e-9)
            }
        }
    }
}
