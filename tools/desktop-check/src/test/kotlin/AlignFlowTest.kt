import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.PhonePlacement
import org.astrofixxer.ui.PointingMode
import org.astrofixxer.ui.Projector
import org.astrofixxer.ui.SkyState
import org.astrofixxer.ui.MountType
import org.astrofixxer.ui.TelescopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.hypot

/** The alignment flow: tap a star, centre it, drag the map under the +, confirm. */
@OptIn(ExperimentalTestApi::class)
class AlignFlowTest {
    private val catalog get() = Fixtures.catalog
    private val vega get() = catalog.find("Vega")!!
    private fun button(label: String) = hasText(label) and hasClickAction()

    /** The phone's sensors pointing at [obj] but with the compass [errorDeg] off (the world turned about the vertical). */
    private fun pointWithCompassError(state: SkyState, obj: SkyObject, errorDeg: Double) {
        Fixtures.pointAt(state, obj)
        state.device = Pointing.matMul(Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), Math.toRadians(errorDeg)), state.device)
    }

    /** Where [obj] is drawn on the screen now, in pixels (null when behind the view). */
    private fun ComposeUiTest.px(state: SkyState, obj: SkyObject): Offset? {
        val size = onRoot().fetchSemanticsNode().size
        return Projector(state.camera(), size.width.toFloat(), size.height.toFloat(), state.fovDeg).project(state.ray(obj))
    }

    private fun ComposeUiTest.centre(): Offset = onRoot().fetchSemanticsNode().size.let { Offset(it.width / 2f, it.height / 2f) }

    private fun ComposeUiTest.dragMap(by: Offset) {
        val from = centre() + Offset(-by.x / 2, -by.y / 2 + 100f)
        onRoot().performTouchInput { swipe(from, from + by, 400) }
        waitForIdle()
    }

    private fun ComposeUiTest.tapAt(p: Offset) = onRoot().performTouchInput { click(p) }

    // ---------------------------------------------------------------- picking and centring

    @Test fun tappingAStarStartsCentringAndCalibratesNothing() = phoneTest {
        val state = Fixtures.state()
        Fixtures.pointAt(state, vega)
        setContent { AppScreen(state) }
        onNode(button("Align")).tap()
        assertEquals(AlignState.PICK_STAR, state.align)
        onNode(hasText("Tap the star you will centre in the telescope")).assertExists()
        tapAt(centre())
        assertEquals(AlignState.CENTER_STAR, state.align)
        assertEquals("Vega", state.centerStar?.name)
        assertNull("nothing is calibrated by a tap", state.alignMatrix)
        assertNull(state.alignedAtMillis)
        onNode(hasText("Centre Vega in the eyepiece by moving the telescope.")).assertExists()
        onNode(hasText("Drag the map to place Vega under the +")).assertExists()
        onNode(hasText("Step 1 moves the telescope. Step 2 only lines up the map on screen.")).assertExists()
        for (b in listOf("Confirm alignment", "Reset adjustment", "Cancel")) onNode(button(b)).assertExists()
        // With an older calibration, it stays exactly as it was until Confirm.
        state.cancelAlign()
        val altair = catalog.find("Altair")!!
        Fixtures.pointAt(state, altair)
        Fixtures.align(state, altair)
        Fixtures.pointAt(state, vega) // the calibration is now (nearly) the identity
        val old = state.alignMatrix!!.copyOf()
        onNode(button("Align")).tap()
        tapAt(px(state, vega)!!)
        assertEquals(AlignState.CENTER_STAR, state.align)
        assertTrue(old.contentEquals(state.alignMatrix!!))
        onNode(button("Cancel")).tap()
        assertEquals(AlignState.ALIGNED, state.align)
        assertTrue(old.contentEquals(state.alignMatrix!!))
    }

    @Test fun dragMovesTheMapWithTheFingerAndResetPutsItBack() = phoneTest {
        val state = Fixtures.state()
        pointWithCompassError(state, vega, 8.0)
        setContent { AppScreen(state) }
        onNode(button("Align")).tap()
        tapAt(px(state, vega)!!)
        assertEquals(AlignState.CENTER_STAR, state.align)
        val start = px(state, vega)!!
        val calibration = state.alignMatrix
        dragMap(Offset(100f, 0f))
        val right = px(state, vega)!!
        assertTrue("map should follow the finger ~100 px right, moved ${right.x - start.x}", right.x - start.x in 70f..105f)
        assertTrue("a turn about the vertical curves on the screen, but only a little", kotlin.math.abs(right.y - start.y) < 25f)
        dragMap(Offset(0f, 120f))
        val down = px(state, vega)!!
        assertTrue("map should follow the finger ~120 px down, moved ${down.y - right.y}", down.y - right.y in 85f..125f)
        dragMap(Offset(-60f, -60f))
        assertNotEquals(0.0, state.adjustAzDeg)
        assertEquals(calibration, state.alignMatrix) // dragging never calibrates by itself
        onNode(button("Reset adjustment")).tap()
        assertEquals(0.0, state.adjustAzDeg, 0.0)
        assertEquals(0.0, state.adjustAltDeg, 0.0)
        val back = px(state, vega)!!
        assertEquals(start.x, back.x, 1f)
        assertEquals(start.y, back.y, 1f)
    }

    private fun assertNotEquals(a: Double, b: Double) = assertTrue("$a should differ from $b", a != b)

    @Test fun confirmAlignsExactlyAndReportsTheCorrection() = phoneTest {
        val state = Fixtures.state()
        pointWithCompassError(state, vega, 8.0) // the compass is 8° off
        val before = state.telescopeCamera()[2].copyOf()
        val expected = Pointing.angleBetweenDeg(before, state.ray(vega))
        setContent { AppScreen(state) }
        onNode(button("Align")).tap()
        tapAt(px(state, vega)!!)
        val p = px(state, vega)!!
        dragMap(centre() - p) // line the star up under the +
        val near = px(state, vega)!!
        assertTrue("star should be near the + after the drag", hypot(near.x - centre().x, near.y - centre().y) < 40f)
        onNode(button("Confirm alignment")).tap()
        assertEquals(AlignState.ALIGNED, state.align)
        assertEquals("Vega", state.alignStar?.name)
        assertNotNull(state.alignMatrix)
        assertNull(state.centerStar)
        assertEquals(0.0, state.adjustAzDeg, 0.0)
        assertTrue("about 8° x cos(alt), got $expected", expected in 3.0..8.5)
        onNode(hasText("Aligned on Vega. Correction %.1f°.".format(expected))).assertExists()
        onNode(hasText("That's a large correction", substring = true)).assertDoesNotExist()
        screenshot("align-result")
        // The star is exactly under the + now: the calibration was computed, not eyeballed.
        val c = px(state, vega)!!
        assertEquals(centre().x, c.x, 1f)
        assertEquals(centre().y, c.y, 1f)
        assertTrue(Pointing.dot(state.telescopeCamera()[2], state.ray(vega)) > 1 - 1e-12)
        assertTrue(state.alignedAtMillis != null)
    }

    @Test fun dragsAfterConfirmNeverChangeTheCalibration() = phoneTest {
        val state = Fixtures.state()
        pointWithCompassError(state, vega, 5.0)
        Fixtures.align(state, vega)
        val calibration = state.alignMatrix!!.copyOf()
        val view = state.camera()[2].copyOf()
        setContent { AppScreen(state) }
        dragMap(Offset(200f, 100f))
        assertTrue(calibration.contentEquals(state.alignMatrix!!))
        assertTrue(view.contentEquals(state.camera()[2]))
        assertEquals(0.0, state.adjustAzDeg, 0.0)
        onNode(button("Compass")).tap() // Free look
        assertEquals(PointingMode.FREE, state.mode)
        dragMap(Offset(-200f, 60f))
        assertTrue(calibration.contentEquals(state.alignMatrix!!))
        onNode(button("Free look")).tap()
        assertEquals(PointingMode.COMPASS, state.mode)
        assertTrue(calibration.contentEquals(state.alignMatrix!!))
        assertEquals(0.0, state.adjustAltDeg, 0.0)
    }

    @Test fun retryGoesBackToCentringTheSameStar() = phoneTest {
        val state = Fixtures.state()
        pointWithCompassError(state, vega, 4.0)
        setContent { AppScreen(state) }
        onNode(button("Align")).tap()
        tapAt(px(state, vega)!!)
        onNode(button("Confirm alignment")).tap()
        onNode(button("Retry")).assertExists()
        val first = state.alignMatrix!!.copyOf()
        onNode(button("Retry")).tap()
        assertEquals(AlignState.CENTER_STAR, state.align)
        assertEquals("Vega", state.centerStar?.name)
        assertNull(state.alignResult)
        assertTrue(first.contentEquals(state.alignMatrix!!)) // still the old one until Confirm
        Fixtures.pointAt(state, vega) // the user re-centres it properly this time
        val correction = Pointing.angleBetweenDeg(state.telescopeCamera()[2], state.ray(vega)) // what the app was off by
        onNode(button("Confirm alignment")).tap()
        assertEquals(AlignState.ALIGNED, state.align)
        onNode(hasText("Aligned on Vega. Correction %.1f°.".format(correction))).assertExists()
        onNode(button("Done")).tap()
        onNode(button("Done")).assertDoesNotExist()
    }

    @Test fun largeCorrectionAsksWhetherTheStarWasCentred() = phoneTest {
        val state = Fixtures.state()
        pointWithCompassError(state, vega, 40.0)
        setContent { AppScreen(state) }
        assertTrue(state.beginCentering(vega)) // e.g. from the long-press menu
        onNode(button("Confirm alignment")).tap()
        val r = state.alignResult!!
        assertTrue(r.large)
        onNode(hasText("That's a large correction. Is Vega really centred in the eyepiece?")).assertExists()
        onNode(button("Retry")).assertExists()
        onNode(button("Done")).assertExists()
        assertEquals(AlignState.ALIGNED, state.align) // still aligned: the user decides
        screenshot("align-result-large")
    }

    @Test fun ineligibleObjectsAreExplainedAndPickingContinues() = phoneTest {
        val state = Fixtures.state()
        val m57 = catalog.find("M31")!! // a galaxy, bright enough to be drawn (and tapped) at this zoom
        Fixtures.pointAt(state, m57)
        setContent { AppScreen(state) }
        onNode(button("Align")).tap()
        tapAt(centre())
        assertEquals(AlignState.PICK_STAR, state.align)
        onNode(hasText("M31 is not a star or planet. Pick a bright star or a planet.")).assertExists()
        screenshot("align-pick-refused")
        // The Sun and Moon, and a star below the horizon, are refused too, each with its reason.
        val moving = org.astrofixxer.ui.solarSystem(state).associate { it.obj.name to it.obj }
        assertFalse(state.pickStar(moving.getValue("Sun")))
        assertTrue(state.alignNote!!.render().startsWith("Never point a telescope at the Sun"))
        assertFalse(state.pickStar(moving.getValue("Moon")))
        assertTrue(state.alignNote!!.render().contains("Moon is too big"))
        val below = catalog.objects.first { it.type == "S" && (it.mag ?: 9.0) < 3 && state.ray(it)[2] < -0.2 }
        assertFalse(state.pickStar(below))
        assertEquals("${below.name} is below the horizon. Pick a star that is up.", state.alignNote!!.render())
        assertEquals(AlignState.PICK_STAR, state.align)
        assertNull(state.centerStar)
        // A planet above the horizon is fine.
        val planet = moving.values.firstOrNull { it.type == "P" && it.name != "Sun" && it.name != "Moon" && state.ray(it)[2] > 0.1 }
        if (planet != null) assertTrue(state.canAlignOn(planet))
        // The menu offers "Align using this star" only for eligible objects.
        assertFalse(state.canAlignOn(m57))
        assertTrue(state.canAlignOn(vega))
    }

    @Test fun lowStarsGetAWarning() = phoneTest {
        val state = Fixtures.state()
        // Find a moment when a bright star is 2-8° up.
        var found: SkyObject? = null
        var t = Fixtures.start
        while (found == null && t < Fixtures.start + 86_400_000L) {
            state.timeMillis = t
            found = catalog.objects.firstOrNull { it.type == "S" && (it.mag ?: 9.0) < 3.5 && Math.toDegrees(asin(state.ray(it)[2])) in 2.0..8.0 }
            t += 1_800_000L
        }
        assertNotNull(found)
        setContent { AppScreen(state) }
        assertTrue(state.beginCentering(found!!))
        onNode(hasText("Low stars are harder to centre")).assertExists()
        assertEquals(AlignState.CENTER_STAR, state.align) // a warning, not a refusal
    }

    @Test fun confirmFailsAndChangesNothingWhenTheStarHasSet() = phoneTest {
        val state = Fixtures.state()
        Fixtures.pointAt(state, vega)
        Fixtures.align(state, vega)
        val old = state.alignMatrix!!.copyOf()
        val setter = catalog.objects.first {
            it.type == "S" && (it.mag ?: 9.0) < 2.5 && state.ray(it)[2] > 0.25 &&
                Pointing.rayFromPos(it.ra, it.dec, Fixtures.start + 43_200_000L, state.lat, state.lon)[2] < -0.2
        }
        setContent { AppScreen(state) }
        assertTrue(state.beginCentering(setter))
        state.shiftTime(43_200_000L) // twelve hours pass while the user fiddles
        waitForIdle()
        onNode(button("Confirm alignment")).tap()
        assertEquals(AlignState.CENTER_STAR, state.align)
        assertTrue(old.contentEquals(state.alignMatrix!!))
        assertNull(state.alignResult)
        onNode(hasText("${setter.name} has dropped below the horizon, so nothing was changed. Cancel and pick another star.")).assertExists()
    }

    // ---------------------------------------------------------------- no compass

    @Test fun aPhoneWithoutACompassFixesItsAzimuthByDragging() = phoneTest {
        val state = Fixtures.state().apply { hasCompass = false; fovDeg = 120.0 }
        pointWithCompassError(state, vega, 100.0) // arbitrary start azimuth: Vega is 100° away
        setContent { AppScreen(state) }
        assertEquals(PointingMode.COMPASS, state.mode)
        onNode(button("Align")).tap()
        onNode(hasText("This phone has no compass. Drag the map until the sky matches what you see.")).assertExists()
        var tries = 0
        while (px(state, vega).let { it == null || it.x !in 0f..945f || it.y !in 0f..2048f } && tries++ < 6) {
            onRoot().performTouchInput { swipe(Offset(900f, 900f), Offset(80f, 900f), 400) } // drag the sky left to look further east
            waitForIdle()
        }
        val p = px(state, vega)
        assertNotNull("Vega should have come into view after dragging", p)
        tapAt(p!!)
        assertEquals(AlignState.CENTER_STAR, state.align)
        onNode(button("Confirm alignment")).tap()
        assertEquals(AlignState.ALIGNED, state.align)
        // Vega is on the telescope axis now, although the start azimuth was 100° wrong.
        assertTrue(Pointing.dot(state.telescopeCamera()[2], state.ray(vega)) > 1 - 1e-12)
        assertFalse("100° is not a large correction on a phone with no compass", state.alignResult!!.large)
    }

    // ---------------------------------------------------------------- mounting changes

    @Test fun changingHowThePhoneSitsClearsTheAlignmentAndSaysSo() = phoneTest {
        val state = Fixtures.state()
        Fixtures.pointAt(state, vega)
        Fixtures.align(state, vega)
        setContent { AppScreen(state) }
        val notice = "The phone is mounted differently now, so the old alignment no longer fits. Align on a star again."
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        // Type, mount, rotation and mirror never break an alignment, and say nothing.
        onNode(button("Reflector")).performScrollTo().tap()
        onNode(button("Equatorial")).performScrollTo().tap()
        onNode(button("Rotate view 90°")).performScrollTo().tap()
        onNode(button("Mirror view")).performScrollTo().tap()
        assertEquals(TelescopeType.REFLECTOR, state.setup.type)
        assertEquals(MountType.EQUATORIAL, state.setup.mount)
        assertEquals(90, state.setup.viewRotationDeg)
        assertTrue(state.setup.viewMirrored)
        assertEquals(AlignState.ALIGNED, state.align)
        assertNotNull(state.alignMatrix)
        onNode(hasText(notice)).assertDoesNotExist()
        // Moving the phone does.
        onNode(button("Camera facing along the telescope")).performScrollTo().tap()
        assertEquals(PhonePlacement.CAMERA_FORWARD, state.setup.placement)
        assertNull(state.alignMatrix)
        assertEquals(AlignState.NOT_ALIGNED, state.align)
        assertNull(state.alignStar)
        onNode(hasText(notice)).assertExists()
        screenshot("align-mounting-changed")
        onNode(button("Later")).tap()
        onNode(hasText(notice)).assertDoesNotExist()
        assertEquals(AlignState.NOT_ALIGNED, state.align)
    }

    @Test fun choosingANewtonianAfterAStraightEyepieceClearsTheAlignmentAndSaysSo() = phoneTest {
        val state = Fixtures.state()
        state.setup = state.setup.copy(type = TelescopeType.REFRACTOR, placement = PhonePlacement.EYEPIECE, eyepieceAngle = org.astrofixxer.ui.EyepieceAngle.STRAIGHT)
        Fixtures.pointTelescopeAt(state, vega)
        Fixtures.align(state, vega)
        assertEquals(-1.0, state.setup.axis().vector[2], 0.0) // looking through the eyepiece
        setContent { AppScreen(state) }
        val notice = "The phone is mounted differently now, so the old alignment no longer fits. Align on a star again."
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        onNode(button("Straight")).performScrollTo().assertExists()
        onNode(button("Reflector")).performScrollTo().tap()
        // A Newtonian's eyepiece is on the side, so the phone's axis moved to an edge: the old alignment is void.
        assertEquals(TelescopeType.REFLECTOR, state.setup.type)
        assertEquals(org.astrofixxer.ui.EyepieceAngle.RIGHT_ANGLE, state.setup.effectiveEyepieceAngle)
        assertEquals(0.0, state.setup.axis().vector[2], 0.0)
        assertNull(state.alignMatrix)
        assertEquals(AlignState.NOT_ALIGNED, state.align)
        onNode(hasText(notice)).assertExists()
        onNode(button("Later")).tap()
        // Only the edge is asked now, and the choices that cannot exist on a Newtonian are gone.
        onNode(hasText("On a Newtonian the eyepiece is on the side, at right angles to the tube.")).performScrollTo().assertExists()
        onNode(hasText("Which edge points toward the front of the telescope?")).performScrollTo().assertExists()
        onNode(button("Straight")).assertDoesNotExist()
        onNode(button("Right angle")).assertDoesNotExist()
        // Back to a refractor: its own three choices return, with the earlier answer.
        onNode(button("Refractor")).performScrollTo().tap()
        onNode(button("Straight")).performScrollTo().assertExists()
        assertEquals(-1.0, state.setup.axis().vector[2], 0.0)
    }

    @Test fun choosingANewtonianWhenTheEyepieceWasAlreadyARightAngleKeepsTheAlignment() = phoneTest {
        val state = Fixtures.state()
        state.setup = state.setup.copy(type = TelescopeType.REFRACTOR, placement = PhonePlacement.EYEPIECE, eyepieceAngle = org.astrofixxer.ui.EyepieceAngle.RIGHT_ANGLE)
        Fixtures.pointTelescopeAt(state, vega)
        Fixtures.align(state, vega)
        setContent { AppScreen(state) }
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        onNode(button("Reflector")).performScrollTo().tap()
        assertEquals(AlignState.ALIGNED, state.align) // the axis did not change
        assertNotNull(state.alignMatrix)
        onNode(hasText("The phone is mounted differently now, so the old alignment no longer fits. Align on a star again.")).assertDoesNotExist()
    }

    @Test fun alignNowInTheMountingDialogStartsPicking() = phoneTest {
        val state = Fixtures.state()
        Fixtures.pointAt(state, vega)
        Fixtures.align(state, vega)
        setContent { AppScreen(state) }
        state.updateSetup(state.setup.copy(edge = org.astrofixxer.ui.PhoneEdge.LEFT))
        waitForIdle()
        onNode(button("Align now")).tap()
        assertEquals(AlignState.PICK_STAR, state.align)
        assertFalse(state.mountingChangedNotice)
        onNode(hasText("Tap the star you will centre in the telescope")).assertExists()
    }

    @Test fun aDifferentPhoneAxisIsUsedForPointing() = phoneTest {
        val state = Fixtures.state()
        state.updateSetup(state.setup.copy(placement = PhonePlacement.CAMERA_FORWARD)) // rear camera, -Z
        Fixtures.pointTelescopeAt(state, vega, rollDeg = 37.0)
        assertTrue(Pointing.dot(state.camera()[2], state.ray(vega)) > 1 - 1e-12)
        setContent { AppScreen(state) }
        val c = centre()
        val p = px(state, vega)!!
        assertEquals(c.x, p.x, 1f)
        assertEquals(c.y, p.y, 1f)
        Fixtures.align(state, vega)
        assertEquals(AlignState.ALIGNED, state.align)
    }

    // ---------------------------------------------------------------- checking with a second star

    @Test fun checkWithAnotherStarReportsTheErrorAndRefines() = phoneTest {
        val state = Fixtures.state()
        val altair = catalog.find("Altair")!!
        Fixtures.pointAt(state, vega)
        Fixtures.align(state, vega)
        pointWithCompassError(state, altair, 1.0) // the compass is now 1° off
        state.target = catalog.find("M57")
        setContent { AppScreen(state) }
        onNode(button("Check with another star")).assertDoesNotExist() // basic alignment has no extra steps
        onNode(button("Done")).assertDoesNotExist()
        onNode(button("More")).tap()
        onNode(button("Check with another star")).tap()
        assertEquals(AlignState.PICK_STAR, state.align)
        onNode(hasText("Tap a second star, at least 10° from the first, to check the alignment")).assertExists()
        val off = Pointing.angleBetweenDeg(state.telescopeCamera()[2], state.ray(altair))
        tapAt(px(state, altair)!!)
        assertEquals(AlignState.CENTER_STAR, state.align)
        val old = state.alignMatrix!!.copyOf()
        onNode(button("Confirm alignment")).tap()
        assertTrue("off by a degree or so, got $off", off in 0.3..2.0)
        onNode(hasText("Off by %.1f° at Altair".format(off))).assertExists()
        onNode(hasText("The alignment now uses both stars.")).assertExists()
        assertTrue(state.alignResult!!.refined)
        assertFalse(old.contentEquals(state.alignMatrix!!))
        assertTrue("the newest star is matched exactly", Pointing.dot(state.telescopeCamera()[2], state.ray(altair)) > 1 - 1e-12)
        screenshot("align-check-result")
    }

    @Test fun aSecondStarTooCloseIsReportedButNotUsedToRefine() = phoneTest {
        val state = Fixtures.state()
        Fixtures.pointAt(state, vega)
        Fixtures.align(state, vega)
        val close = catalog.objects.first {
            it.type == "S" && (it.mag ?: 9.0) < 5 && it.name != "Vega" && Pointing.angleBetweenDeg(state.ray(it), state.ray(vega)) in 2.0..8.0
        }
        setContent { AppScreen(state) }
        val old = state.alignMatrix!!.copyOf()
        state.startCheckWithAnotherStar()
        assertTrue(state.pickStar(close))
        Fixtures.pointAt(state, close)
        onNode(button("Confirm alignment")).tap()
        assertFalse(state.alignResult!!.refined)
        onNode(hasText("Not refined: the two stars are less than 10° apart", substring = true)).assertExists()
        assertTrue(old.contentEquals(state.alignMatrix!!))
        // Picking the star already aligned on is refused in a check.
        state.dismissAlignResult()
        state.startCheckWithAnotherStar()
        assertFalse(state.pickStar(close.takeIf { state.alignStar?.name == it.name } ?: vega).let { it && state.alignStar?.name == "Vega" })
    }

    // ---------------------------------------------------------------- persistence and the + marker

    @Test fun theCalibrationSurvivesARestartAndSaysHowOldItIs() = phoneTest {
        val a = Fixtures.state()
        pointWithCompassError(a, vega, 6.0)
        Fixtures.align(a, vega)
        val saved = a.settings()
        val b = Fixtures.state().apply { applySettings(saved) }
        assertEquals(AlignState.ALIGNED, b.align)
        assertTrue(a.alignMatrix!!.contentEquals(b.alignMatrix!!))
        assertEquals(a.alignedAtMillis, b.alignedAtMillis)
        assertEquals("Vega", b.alignStarName)
        b.restoreAlignStar(catalog)
        assertEquals("Vega", b.alignStar?.name)
        b.timeMillis = Fixtures.start + 3 * 3_600_000L
        b.target = catalog.find("M57")
        b.device = a.device
        setContent { AppScreen(b) }
        onNode(hasText("Aligned 3 h ago · re-align soon")).assertExists()
        // Damaged or non-rotation matrices are ignored rather than trusted.
        for (bad in listOf("1,2,3", "NaN,0,0,0,1,0,0,0,1", "2,0,0,0,2,0,0,0,2", "a,b,c,d,e,f,g,h,i", "")) {
            val c = Fixtures.state().apply { applySettings(mapOf("alignMatrix" to bad, "alignStar" to "Vega", "alignedAt" to "5")) }
            assertNull(bad, c.alignMatrix)
            assertEquals(AlignState.NOT_ALIGNED, c.align)
        }
        // Clearing the alignment clears what is saved.
        a.clearAlignment()
        assertEquals("", a.settings()["alignMatrix"])
    }

    @Test fun thePlusIsAtTheExactCentreWithAGapAndTheRightColour() = phoneTest {
        val state = Fixtures.state().apply {
            night = true; showConstellations = false; showDeepSky = false; showMilkyWay = false; showCardinals = false
            landscape = org.astrofixxer.ui.Landscape.NONE; showAtmosphere = false; fovDeg = 1.0
        }
        state.device = Pointing.rotationMatrix(-200.0, 70.0, 0.0)
        setContent { AppScreen(state) }
        waitForIdle()
        val img = onRoot().captureToImage().toPixelMap()
        val cx = img.width / 2
        val cy = img.height / 2
        fun red(x: Int, y: Int) = img[x, y].let { it.red > 0.75f && it.green < 0.3f }
        val dp = 2.625f
        val mid = (6 * dp).toInt() // between 3 dp and 9 dp
        assertTrue("right arm", red(cx + mid, cy)); assertTrue("left arm", red(cx - mid, cy))
        assertTrue("lower arm", red(cx, cy + mid)); assertTrue("upper arm", red(cx, cy - mid))
        assertFalse("gap in the middle", red(cx, cy))
        assertFalse("nothing beyond the arms", red(cx + (14 * dp).toInt(), cy))
        assertFalse("not a full-width crosshair", red(cx + (60 * dp).toInt(), cy))
    }
}
