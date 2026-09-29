import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import org.astrofixxer.ui.formatClock
import org.astrofixxer.ui.visibilityTonight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Tonight's visibility is physically right, and Object Info shows it. */
@OptIn(ExperimentalTestApi::class)
class ObjectInfoTest {
    private val catalog get() = Fixtures.catalog
    private val lat = 28.6139
    private val lon = 77.2090

    @Test fun vegaPeaksAtTheTextbookAltitude() {
        val vega = catalog.find("Vega")!!
        val v = visibilityTonight(vega, Fixtures.start, lat, lon)
        val expected = 90 - abs(lat - vega.dec) // altitude at the meridian
        assertEquals(expected, v.maxAlt, 0.4) // sampled every 10 minutes
        assertNotNull(v.rise); assertNotNull(v.set)
        assertFalse(v.alwaysUp); assertFalse(v.neverUp)
    }

    @Test fun circumpolarAndNeverRising() {
        assertTrue("Polaris never sets from Delhi", visibilityTonight(catalog.find("Polaris")!!, Fixtures.start, lat, lon).alwaysUp)
        val sigmaOct = catalog.starsByHip[104382]!!
        assertTrue("σ Octantis never rises from Delhi", visibilityTonight(sigmaOct, Fixtures.start, lat, lon).neverUp)
    }

    @Test fun darkWindowMatchesAstronomicalTwilightInDelhi() {
        // Delhi, 28-29 Sep 2026: astronomical twilight ends about 19:15 and starts about 05:05 IST.
        val v = visibilityTonight(catalog.find("Vega")!!, Fixtures.start, lat, lon)
        val start = formatClock(v.darkStart!!)
        val end = formatClock(v.darkEnd!!)
        assertTrue("dark from $start", start in "18:55".."19:35")
        assertTrue("dark until $end", end in "04:45".."05:25")
    }

    @Test fun planetsAreTrackedAcrossTheNight() {
        val state = Fixtures.state()
        val saturn = org.astrofixxer.ui.solarSystem(state).first { it.obj.name == "Saturn" }.obj
        val v = visibilityTonight(saturn, Fixtures.start, lat, lon)
        assertTrue(v.maxAlt in 20.0..90.0)
    }

    @Test fun objectInfoOpensFromTheTargetCard() = phoneTest {
        val state = Fixtures.state().apply { target = catalog.find("M57") }
        setContent { AppScreen(state) }
        onNode(hasText("Ring Nebula") and hasClickAction()).tap()
        onNode(hasText("Constellation")).assertExists()
        onNode(hasText("Lyra")).assertExists()
        waitUntil(10_000) { onAllNodes(hasText("Highest", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasContentDescription("Altitude tonight")).assertExists()
        screenshot("info-m57")
        onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Eyepiece view"))
        screenshot("info-m57-eyepiece")
        assertTrue(BackButton.press())
        waitForIdle()
        onNode(hasText("Constellation")).assertDoesNotExist()
    }
}
