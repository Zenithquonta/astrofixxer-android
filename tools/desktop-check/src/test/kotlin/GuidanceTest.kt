import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.ui.MountType
import org.astrofixxer.ui.SkyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos

/** Next-star guidance: the arrow and words, Close and On target, the details behind More, and that the view never changes them. */
@OptIn(ExperimentalTestApi::class)
class GuidanceTest {
    private val catalog get() = Fixtures.catalog
    private val vega get() = catalog.find("Vega")!!
    private fun button(label: String) = hasText(label) and hasClickAction()

    private fun alignedOnVega(): SkyState = Fixtures.state().also { s ->
        Fixtures.pointAt(s, vega)
        Fixtures.align(s, vega)
    }

    /** A target [dAz]° further clockwise and [dAlt]° higher than where the telescope points. */
    private fun offset(s: SkyState, dAz: Double, dAlt: Double): SkyObject {
        val f = s.telescopeCamera()[2]
        val az = Math.toDegrees(atan2(f[0], f[1])) + dAz
        val alt = Math.toDegrees(asin(f[2])) + dAlt
        val r = org.astrofixxer.ui.horizonRay(az, alt)
        val (ra, dec) = Pointing.rayToRaDec(r, s.timeMillis, s.lat, s.lon)
        return SkyObject("Test", ra, dec, null, "S")
    }

    /** The telescope's RA/Dec now, and a target [dRa] east and [dDec] north of it. */
    private fun offsetRaDec(st: SkyState, dRa: Double, dDec: Double): SkyObject {
        val (ra, dec) = Pointing.rayToRaDec(st.telescopeCamera()[2], st.timeMillis, st.lat, st.lon)
        return SkyObject("Test", (ra + dRa + 360) % 360, dec + dDec, null, "S")
    }

    @Test fun aTargetUpAndToTheRightGivesAnUpRightArrowAndWords() = phoneTest {
        val s = alignedOnVega()
        s.target = offset(s, 6.0, 3.0)
        val alt = Math.toDegrees(asin(s.telescopeCamera()[2][2]))
        val expectedRight = 6.0 * cos(Math.toRadians(alt + 1.5))
        val h = s.moveHint()!!
        assertFalse(h.equatorial)
        assertEquals(3.0, h.vertical, 0.02)
        assertEquals(expectedRight, h.horizontal, 0.02)
        assertTrue("arrow should point up-right, got ${h.arrowDeg}", h.arrowDeg in 22.5..67.5)
        assertEquals(Math.hypot(3.0, expectedRight), h.separationDeg, 0.1)
        setContent { AppScreen(s) }
        onNode(hasText("↗ Up %.1f° · Right %.1f°".format(h.vertical, h.horizontal))).assertExists()
        onNode(hasText("%.1f° to go".format(h.separationDeg))).assertExists()
        onNode(hasText("Move to Test")).assertExists()
        // The main screen is only the arrow, the words, the distance and the name (details are behind More).
        onNode(hasText("ΔAlt")).assertDoesNotExist()
        onNode(button("Check with another star")).assertDoesNotExist()
        screenshot("guidance-collapsed")
    }

    @Test fun otherDirectionsUseTheRightWords() = phoneTest {
        val s = alignedOnVega()
        setContent { AppScreen(s) }
        for ((dAz, dAlt, glyph, up, right) in listOf(
            Quad(-6.0, -3.0, "↙", "Down", "Left"), Quad(0.0, 4.0, "↑", "Up", null), Quad(8.0, 0.0, "→", null, "Right"), Quad(-8.0, 0.0, "←", null, "Left"),
        )) {
            s.target = offset(s, dAz, dAlt)
            val h = s.moveHint()!!
            val parts = listOfNotNull(up?.let { "$it %.1f°".format(kotlin.math.abs(h.vertical)) }, right?.let { "$it %.1f°".format(kotlin.math.abs(h.horizontal)) })
            waitForIdle()
            onNode(hasText("$glyph " + parts.joinToString(" · "))).assertExists()
        }
    }

    private data class Quad(val a: Double, val b: Double, val c: String, val d: String?, val e: String?)

    @Test fun closeAndOnTargetStates() = phoneTest {
        val s = alignedOnVega()
        val field = s.eyepieceFovDeg // 1.08°
        setContent { AppScreen(s) }
        s.target = offset(s, 0.0, 6.0)
        waitForIdle()
        onNode(hasText("Move to Test")).assertExists()
        s.target = offset(s, 0.0, 2.0) // inside three fields (3.25°), outside half a field
        waitForIdle()
        assertTrue(s.moveHint()!!.separationDeg < 3 * field)
        onNode(hasText("Close to Test")).assertExists()
        screenshot("guidance-close")
        s.target = offset(s, 0.0, 0.3) // inside half a field (0.54°)
        waitForIdle()
        onNode(hasText("On target: Test")).assertExists()
        onNode(androidx.compose.ui.test.hasContentDescription("On target")).assertExists() // the filled bullseye
        onNode(hasText("Up", substring = true)).assertDoesNotExist() // nothing left to move
        screenshot("guidance-on-target")
        s.night = true
        screenshot("guidance-on-target-night")
    }

    @Test fun eyepieceRotationAndMirroringNeverChangeGuidance() = phoneTest {
        val s = alignedOnVega()
        s.target = offset(s, 6.0, 3.0)
        setContent { AppScreen(s) }
        val g0 = s.guidance()!!
        val h0 = s.moveHint()!!
        val words0 = org.astrofixxer.ui.moveWords(h0)
        for (match in listOf(false, true)) for (rot in listOf(0, 90, 180, 270)) for (mirrored in listOf(false, true)) {
            s.matchEyepieceView = match
            s.setup = s.setup.copy(viewRotationDeg = rot, viewMirrored = mirrored)
            waitForIdle()
            val g = s.guidance()!!
            assertEquals(g0.first, g.first, 0.0); assertEquals(g0.second, g.second, 0.0); assertEquals(g0.third, g.third, 0.0)
            val h = s.moveHint()!!
            assertEquals(h0.vertical, h.vertical, 0.0); assertEquals(h0.horizontal, h.horizontal, 0.0)
            assertEquals(h0.arrowDeg, h.arrowDeg, 0.0); assertEquals(h0.separationDeg, h.separationDeg, 0.0)
            assertEquals(words0, org.astrofixxer.ui.moveWords(h))
            onNode(hasText(words0.single())).assertExists() // and the screen shows the same words
        }
        // The guidance is also unchanged for the equatorial words.
        s.setup = s.setup.copy(mount = MountType.EQUATORIAL)
        val eq0 = org.astrofixxer.ui.moveWords(s.moveHint()!!)
        s.setup = s.setup.copy(viewRotationDeg = 90, viewMirrored = true); s.matchEyepieceView = true
        assertEquals(eq0, org.astrofixxer.ui.moveWords(s.moveHint()!!))
    }

    @Test fun equatorialMountsGetRaAndDecWords() = phoneTest {
        val s = alignedOnVega().apply { setup = setup.copy(mount = MountType.EQUATORIAL) }
        s.target = offsetRaDec(s, 2.1, 1.3)
        setContent { AppScreen(s) }
        onNode(hasText("RA: east 2.1°")).assertExists()
        onNode(hasText("Dec: north 1.3°")).assertExists()
        val h = s.moveHint()!!
        assertTrue(h.equatorial)
        // Northern hemisphere, facing south: east is on the left, so east and north is up-left.
        assertTrue("east+north should be up-left, got ${h.arrowDeg}", h.arrowDeg in -90.0..0.0)
        screenshot("guidance-equatorial")
        s.target = offsetRaDec(s, -2.1, -1.3)
        waitForIdle()
        onNode(hasText("RA: west 2.1°")).assertExists()
        onNode(hasText("Dec: south 1.3°")).assertExists()
        assertTrue("west+south should be down-right, got ${s.moveHint()!!.arrowDeg}", s.moveHint()!!.arrowDeg in 90.0..180.0)
        // Southern hemisphere, facing north: east is on the right and north is down.
        val south = SkyState(Fixtures.start, -30.0, 25.0).apply { setupDone = true; setup = setup.copy(mount = MountType.EQUATORIAL) }
        south.device = Pointing.rotationMatrix(0.0, 40.0, 0.0)
        south.target = offsetRaDec(south, 2.1, 1.3)
        val a = south.moveHint()!!.arrowDeg
        assertTrue("south, east+north should be down-right, got $a", a in 90.0..180.0)
        assertEquals("RA: east 2.1°", org.astrofixxer.ui.moveWords(south.moveHint()!!)[0])
    }

    @Test fun moreHoldsTheDetailsAndTheCameraSlot() = phoneTest {
        val s = alignedOnVega()
        s.target = offset(s, 6.0, 3.0)
        val model = org.astrofixxer.ui.PlateSolveModel()
        setContent { AppScreen(s, host = FakeHost(), model = model) }
        onNode(button("Solve with camera")).assertDoesNotExist()
        onNode(button("More")).tap()
        onNode(hasText("ΔAlt")).assertExists()
        onNode(hasText("ΔAz")).assertExists()
        onNode(hasText("On Vega · Aligned ✓")).assertExists()
        onNode(button("Check with another star")).assertExists()
        onNode(hasText("Match eyepiece view")).assertExists()
        onNode(button("Rotate view 90°")).assertExists()
        onNode(button("Mirror view")).assertExists()
        screenshot("guidance-expanded")
        onNode(button("Rotate view 90°")).tap()
        assertEquals(90, s.setup.viewRotationDeg)
        onNode(button("Mirror view")).tap()
        assertTrue(s.setup.viewMirrored)
        onNode(button("Solve with camera")).tap()
        assertTrue("the camera slot opens the plate-solve flow", model.open)
        onNode(hasText("Centre the bright eyepiece circle", substring = true)).assertDoesNotExist() // the default setup is on the tube
        onNode(hasText("Solving is not possible with the phone flat on the tube")).assertExists()
        onNode(button("Cancel")).tap()
        assertFalse(model.open)
        onNode(button("Less")).tap()
        onNode(button("Check with another star")).assertDoesNotExist()
        s.night = true
        onNode(button("More")).tap()
        screenshot("guidance-expanded-night")
    }

    @Test fun withoutTheCameraSlotThereIsNoButton() = phoneTest {
        val s = alignedOnVega()
        s.target = offset(s, 6.0, 3.0)
        setContent { AppScreen(s) }
        onNode(button("More")).tap()
        onNode(button("Solve with camera")).assertDoesNotExist()
    }
}
