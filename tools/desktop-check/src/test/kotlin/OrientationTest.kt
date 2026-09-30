import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import org.astrofixxer.astro.Pointing
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.PhoneEdge
import org.astrofixxer.ui.PhonePlacement
import org.astrofixxer.ui.Projector
import org.astrofixxer.ui.SkyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Check orientation in the UI, and the eyepiece view drawn on the map. */
@OptIn(ExperimentalTestApi::class)
class OrientationTest {
    private val catalog get() = Fixtures.catalog
    private fun button(label: String) = hasText(label) and hasClickAction()

    private fun androidx.compose.ui.test.ComposeUiTest.openCheck() {
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        onNode(button("Check orientation")).performScrollTo().tap()
        onNode(hasText("Two quick checks. Do them once after fitting the phone, or whenever directions seem wrong.")).assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.answer(up: String, right: String) {
        onNode(button("Check the eyepiece view")).tap()
        onNode(hasText("1. Nudge the telescope up a little, toward the zenith. Which way did the star move in the eyepiece?")).assertExists()
        onNode(button(up)).tap()
        onNode(hasText("2. Now nudge the telescope to the right. Which way did the star move in the eyepiece?")).assertExists()
        onNode(button(right)).tap()
    }

    @Test fun eyepieceViewCheckFindsAnUprightView() = phoneTest {
        val state = Fixtures.state().apply { setup = setup.copy(viewRotationDeg = 90, viewMirrored = true) }
        setContent { AppScreen(state) }
        openCheck()
        screenshot("orientation-menu")
        onNode(button("Check the eyepiece view")).tap()
        screenshot("orientation-view-q1")
        onNode(button("Back")).tap()
        answer("Down", "Left") // a correct view: up moves stars down, right moves them left
        onNode(hasText("Your eyepiece view is: upright.")).assertExists()
        screenshot("orientation-view-result")
        onNode(button("Use this view")).tap()
        assertEquals(0, state.setup.viewRotationDeg)
        assertFalse(state.setup.viewMirrored)
    }

    @Test fun eyepieceViewCheckFindsRotationsAndMirrors() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        openCheck()
        answer("Up", "Right") // upside down
        onNode(hasText("Your eyepiece view is: rotated 180°.")).assertExists()
        onNode(button("Use this view")).tap()
        assertEquals(180, state.setup.viewRotationDeg)
        assertFalse(state.setup.viewMirrored)
        answer("Down", "Right") // reversed left-right, as in a star diagonal
        onNode(hasText("Your eyepiece view is: mirrored.")).assertExists()
        onNode(button("Use this view")).tap()
        assertEquals(0, state.setup.viewRotationDeg)
        assertTrue(state.setup.viewMirrored)
        answer("Right", "Up")
        onNode(hasText("Your eyepiece view is: mirrored + rotated 270°.")).assertExists()
        onNode(button("Use this view")).tap()
        assertEquals(270, state.setup.viewRotationDeg)
    }

    @Test fun inconsistentNudgeAnswersAreRefused() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        openCheck()
        answer("Up", "Down") // both along the same line
        onNode(hasText("Those two answers cannot both be right", substring = true)).assertExists()
        assertEquals(0, state.setup.viewRotationDeg)
        screenshot("orientation-view-inconsistent")
        onNode(button("Try again")).tap()
        onNode(hasText("1. Nudge the telescope up a little", substring = true)).assertExists()
    }

    @Test fun phoneCheckAcceptsTheRightSetting() = phoneTest {
        val state = Fixtures.state()
        Fixtures.pointAt(state, catalog.find("Vega")!!)
        Fixtures.align(state, catalog.find("Vega")!!)
        Fixtures.pointAt(state, catalog.find("Altair")!!)
        Fixtures.align(state, catalog.find("Altair")!!)
        setContent { AppScreen(state) }
        openCheck()
        onNode(button("Check the phone position")).tap()
        onNode(hasText("Stars used: 2")).assertExists()
        onNode(hasText("Your setting fits: the top edge points along the telescope (error 0.0°).")).assertExists()
        screenshot("orientation-phone-fits")
    }

    @Test fun phoneCheckSpotsAWrongEdgeAndOffersTheRightOne() = phoneTest {
        val state = Fixtures.state()
        state.updateSetup(state.setup.copy(edge = PhoneEdge.LEFT)) // the phone really has its left edge forward
        for (name in listOf("Vega", "Altair", "Deneb")) {
            Fixtures.pointTelescopeAt(state, catalog.find(name)!!, rollDeg = 20.0)
            Fixtures.align(state, catalog.find(name)!!)
        }
        state.setup = state.setup.copy(edge = PhoneEdge.TOP) // ... but the setting says top
        setContent { AppScreen(state) }
        openCheck()
        onNode(button("Check the phone position")).tap()
        onNode(hasText("Your stars fit the left edge better than the top edge", substring = true)).assertExists()
        screenshot("orientation-phone-wrong")
        onNode(button("Use the left edge")).tap()
        assertEquals(PhoneEdge.LEFT, state.setup.edge)
        assertEquals(PhonePlacement.TUBE, state.setup.placement)
        // The axis changed, so the old alignment is cleared with the usual explanation.
        assertEquals(AlignState.NOT_ALIGNED, state.align)
        onNode(hasText("The phone is mounted differently now", substring = true)).assertExists()
    }

    @Test fun phoneCheckWithoutStarsOrWithOneStarSaysWhatIsMissing() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        openCheck()
        onNode(button("Check the phone position")).tap()
        onNode(hasText("No stars yet. Align on a star first.")).assertExists()
        onNode(button("Align now")).tap()
        assertEquals(AlignState.PICK_STAR, state.align)
        onNode(button("Close")).assertDoesNotExist() // the sheet closed so the sky is free to tap
        state.cancelAlign()
        Fixtures.pointAt(state, catalog.find("Vega")!!)
        Fixtures.align(state, catalog.find("Vega")!!)
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        onNode(button("Check orientation")).performScrollTo().tap()
        onNode(button("Check the phone position")).tap()
        onNode(hasText("You need two stars at least 10° apart", substring = true)).assertExists()
    }

    @Test fun unsureEyepieceIsFlaggedInTheCheck() = phoneTest {
        val state = Fixtures.state().apply { setup = setup.copy(placement = PhonePlacement.EYEPIECE) } // angle: not sure
        setContent { AppScreen(state) }
        openCheck()
        onNode(button("Check the phone position")).tap()
        onNode(hasText("You said you are not sure how the eyepiece fits, so the phone axis is a guess.")).assertExists()
    }

    @Test fun matchEyepieceViewTurnsTheMapButNotThePlus() = phoneTest {
        val state = Fixtures.state()
        val vega = catalog.find("Vega")!!
        Fixtures.pointAt(state, vega)
        state.device = Pointing.matMul(Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), Math.toRadians(6.0)), state.device)
        setContent { AppScreen(state) }
        val size = onRoot().fetchSemanticsNode().size
        val w = size.width.toFloat(); val h = size.height.toFloat()
        val cam = state.camera()
        val p0 = Projector(cam, w, h, state.fovDeg).project(state.ray(vega))!!
        val dx = p0.x - w / 2; val dy = p0.y - h / 2
        for (mirrored in listOf(false, true)) for (rot in listOf(0, 90, 180, 270)) {
            val p = Projector(cam, w, h, state.fovDeg, rot, mirrored).project(state.ray(vega))!!
            var x = if (mirrored) -dx else dx
            var y = dy
            repeat(rot / 90) { val nx = -y; y = x; x = nx } // a quarter turn clockwise on a screen with y down
            assertEquals("rot $rot mirrored $mirrored x", w / 2 + x, p.x, 0.6f)
            assertEquals("rot $rot mirrored $mirrored y", h / 2 + y, p.y, 0.6f)
        }
        // The centre stays the centre, and the plain projector is untouched.
        val c = Projector(cam, w, h, state.fovDeg, 90, true).project(state.telescopeCamera()[2])!!
        assertEquals(w / 2, c.x, 0.6f); assertEquals(h / 2, c.y, 0.6f)
        // In the app: the switch turns the map (Vega moves), the + does not move.
        state.setup = state.setup.copy(viewRotationDeg = 90, viewMirrored = true)
        screenshot("view-plain")
        state.matchEyepieceView = true
        state.night = false
        screenshot("view-matched-rot90-mirrored")
        state.setup = state.setup.copy(viewRotationDeg = 180, viewMirrored = false)
        screenshot("view-matched-rot180")
        // Off-screen pointer directions turn with the view too.
        val b = Pointing.bearing(state.ray(vega), cam)
        assertEquals(Math.atan2(-b[1], b[0]).toFloat(), Projector(cam, w, h, state.fovDeg).edgeAngle(b), 5e-3f)
        assertEquals(Math.atan2(b[1], -b[0]).toFloat(), Projector(cam, w, h, state.fovDeg, 180, false).edgeAngle(b), 5e-3f)
    }

    @Test fun eyepieceViewSettingsTurnTheSwitchOn() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        onNode(hasText("Match eyepiece view")).performScrollTo().tap()
        assertTrue(state.matchEyepieceView)
        onNode(button("Rotate view 90°")).performScrollTo().tap()
        onNode(hasText("Eyepiece view: rotated 90°")).assertExists()
        onNode(button("Mirror view")).performScrollTo().tap()
        onNode(hasText("Eyepiece view: mirrored + rotated 90°")).assertExists()
        assertNotNull(state.settings()["viewRotation"])
        assertEquals("90", state.settings()["viewRotation"])
        screenshot("orientation-settings-tab")
    }
}
