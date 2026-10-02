import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import org.astrofixxer.ui.Erecting
import org.astrofixxer.ui.EyepieceAngle
import org.astrofixxer.ui.MountType
import org.astrofixxer.ui.PhoneEdge
import org.astrofixxer.ui.PhonePlacement
import org.astrofixxer.ui.SkyState
import org.astrofixxer.ui.TelescopeSetup
import org.astrofixxer.ui.TelescopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first-run wizard: which questions each placement asks, what Finish and "Set up later" save, and migration. */
@OptIn(ExperimentalTestApi::class)
class SetupWizardTest {
    private fun button(label: String) = hasText(label) and hasClickAction()
    private fun fresh() = SkyState(Fixtures.start, 28.6139, 77.2090) // setupDone is false: a first launch

    private val edgeQ = "Which edge of the phone points toward the front of the telescope?"
    private val angleQ = "Does the eyepiece go straight in, or at a right angle (diagonal or Newtonian)?"
    private val prismQ = "Do you use an image-erecting prism?"

    private fun androidx.compose.ui.test.ComposeUiTest.next() = onNode(button("Next")).tap()
    private fun androidx.compose.ui.test.ComposeUiTest.toPlacementStep() {
        onNode(hasText("What kind of telescope is it?")).assertExists()
        next()
        onNode(hasText("What is the mount?")).assertExists()
        next()
        onNode(hasText("Where is your phone mounted?")).assertExists()
    }

    @Test fun newUserSeesTheWizardBeforeTheTutorial() = phoneTest {
        val state = fresh().apply { showOnboarding = true }
        setContent { AppScreen(state) }
        onNode(hasText("Step 1 of 5")).assertExists()
        onNode(hasText("Quick start", substring = true)).assertDoesNotExist()
        onNode(button("Back")).assertExists()
        // Three illustrated options, each at least 56 dp tall.
        for (d in listOf("REFRACTOR", "REFLECTOR", "OTHER")) onNode(hasContentDescription(d)).assertExists()
        screenshot("wizard-1-type")
        onNode(button("Set up later")).tap()
        assertTrue(state.setupDone)
        onNode(hasText("Quick start 1/4")).assertExists() // now the tutorial
    }

    @Test fun tubeAsksOnlyForTheEdge() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        onNode(button("Refractor")).tap()
        next()
        onNode(button("Equatorial")).tap()
        next()
        onNode(hasContentDescription("TUBE")).assertExists()
        onNode(hasContentDescription("CAMERA_FORWARD")).assertExists()
        onNode(hasContentDescription("EYEPIECE")).assertExists()
        screenshot("wizard-3-placement")
        onNode(button("Flat against the telescope tube")).tap()
        next()
        onNode(hasText(edgeQ)).assertExists()
        onNode(hasText(angleQ)).assertDoesNotExist()
        onNode(hasText(prismQ)).assertDoesNotExist()
        screenshot("wizard-4-edge")
        onNode(button("Left edge")).tap()
        next()
        onNode(hasText("All set")).assertExists()
        onNode(hasText("Step 5 of 5")).assertExists()
        onNode(hasText("Check orientation any time in Settings → Telescope & orientation.")).assertExists()
        screenshot("wizard-6-summary")
        onNode(button("Finish")).tap()
        assertTrue(state.setupDone)
        assertEquals(TelescopeType.REFRACTOR, state.setup.type)
        assertEquals(MountType.EQUATORIAL, state.setup.mount)
        assertEquals(PhonePlacement.TUBE, state.setup.placement)
        assertEquals(PhoneEdge.LEFT, state.setup.edge)
        onNode(hasText("All set")).assertDoesNotExist()
        assertEquals("-X axis for the left edge", -1.0, state.setup.axis().vector[0], 0.0)
    }

    @Test fun cameraForwardAsksNothingMore() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        toPlacementStep()
        onNode(button("Camera facing along the telescope")).tap()
        next()
        onNode(hasText("All set")).assertExists()
        onNode(hasText("Step 4 of 4")).assertExists()
        onNode(hasText(edgeQ)).assertDoesNotExist()
        onNode(hasText(angleQ)).assertDoesNotExist()
        onNode(button("Finish")).tap()
        assertEquals(PhonePlacement.CAMERA_FORWARD, state.setup.placement)
        assertEquals(-1.0, state.setup.axis().vector[2], 0.0)
    }

    @Test fun eyepieceStraightAsksAngleAndPrism() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        toPlacementStep()
        onNode(button("Attached to the eyepiece")).tap()
        next()
        onNode(hasText(angleQ)).assertExists()
        onNode(hasText(edgeQ)).assertDoesNotExist() // only a right-angle eyepiece asks for the edge
        onNode(button("Straight")).tap()
        onNode(hasText(edgeQ)).assertDoesNotExist()
        screenshot("wizard-4-angle")
        next()
        onNode(hasText(prismQ)).assertExists()
        onNode(button("No")).tap()
        next()
        onNode(hasText("Step 6 of 6")).assertExists()
        onNode(button("Finish")).tap()
        assertEquals(EyepieceAngle.STRAIGHT, state.setup.eyepieceAngle)
        assertEquals(Erecting.NO, state.setup.erecting)
        assertEquals(-1.0, state.setup.axis().vector[2], 0.0)
    }

    @Test fun eyepieceRightAngleAlsoAsksForTheEdge() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        toPlacementStep()
        onNode(button("Attached to the eyepiece")).tap()
        next()
        onNode(button("Right angle")).tap()
        onNode(hasText(edgeQ)).assertExists()
        onNode(button("Bottom edge")).performScrollTo().tap() // below the cards: a person scrolls down to it
        screenshot("wizard-5-angle-edge")
        next()
        onNode(button("Yes")).tap()
        next()
        onNode(button("Finish")).tap()
        assertEquals(EyepieceAngle.RIGHT_ANGLE, state.setup.eyepieceAngle)
        assertEquals(PhoneEdge.BOTTOM, state.setup.edge)
        assertEquals(Erecting.YES, state.setup.erecting)
        assertEquals(-1.0, state.setup.axis().vector[1], 0.0)
        // With a prism the first guess for the eyepiece view is upright.
        assertEquals(0, state.setup.viewRotationDeg)
        assertFalse(state.setup.viewMirrored)
    }

    @Test fun aNewtonianEyepieceIsAlwaysRightAngleSoOnlyTheEdgeIsAsked() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        onNode(button("Reflector (Newtonian)")).tap()
        next(); next()
        onNode(button("Attached to the eyepiece")).tap()
        next()
        // The angle question and its Straight / Not sure answers are not offered: only the edge, with the reason.
        onNode(hasText(angleQ)).assertDoesNotExist()
        onNode(button("Straight")).assertDoesNotExist()
        onNode(button("Right angle")).assertDoesNotExist()
        onNode(button("Not sure")).assertDoesNotExist()
        onNode(hasText("On a Newtonian the eyepiece is on the side, at right angles to the tube.")).assertExists()
        onNode(hasText(edgeQ)).assertExists()
        screenshot("wizard-4-angle-newtonian")
        onNode(button("Bottom edge")).performScrollTo().tap()
        next()
        onNode(hasText(prismQ)).assertExists()
        onNode(button("No")).tap()
        next()
        onNode(hasText("Step 6 of 6")).assertExists() // the step count is the same as for a refractor on the eyepiece
        onNode(hasText("Phone: on a right-angle eyepiece, bottom edge to the front")).assertExists()
        onNode(button("Finish")).tap()
        assertEquals(EyepieceAngle.RIGHT_ANGLE, state.setup.eyepieceAngle)
        assertEquals(PhoneEdge.BOTTOM, state.setup.edge)
        assertEquals(-1.0, state.setup.axis().vector[1], 0.0) // the edge, never the rear camera
        assertEquals(0.0, state.setup.axis().vector[2], 0.0)
    }

    @Test fun aNewtonianChosenAfterAStraightEyepieceStillGetsTheEdgeAxis() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        onNode(button("Refractor")).tap()
        next(); next()
        onNode(button("Attached to the eyepiece")).tap()
        next()
        onNode(button("Straight")).tap()
        onNode(button("Back")).tap(); onNode(button("Back")).tap(); onNode(button("Back")).tap()
        onNode(button("Reflector (Newtonian)")).tap() // changes the answer after a straight eyepiece was picked
        next(); next(); next()
        onNode(hasText(angleQ)).assertDoesNotExist()
        onNode(button("Straight")).assertDoesNotExist()
        onNode(button("Left edge")).performScrollTo().tap()
        next()
        onNode(button("No")).tap()
        next()
        onNode(button("Finish")).tap()
        assertEquals(EyepieceAngle.RIGHT_ANGLE, state.setup.eyepieceAngle)
        assertEquals(-1.0, state.setup.axis().vector[0], 0.0)
    }

    @Test fun finishAppliesTheFirstGuessOfTheEyepieceView() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        onNode(button("Refractor")).tap()
        next(); next()
        onNode(button("Attached to the eyepiece")).tap()
        next()
        onNode(button("Right angle")).tap() // a star diagonal on a refractor: mirrored, upright
        next()
        onNode(button("No")).tap()
        next()
        onNode(hasText("Eyepiece view: mirrored")).assertExists()
        onNode(button("Finish")).tap()
        assertTrue(state.setup.viewMirrored)
        assertEquals(0, state.setup.viewRotationDeg)
    }

    @Test fun backGoesOneStepBack() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        onNode(button("Reflector (Newtonian)")).tap()
        next()
        onNode(hasText("What is the mount?")).assertExists()
        onNode(button("Back")).tap()
        onNode(hasText("What kind of telescope is it?")).assertExists()
        next()
        waitForIdle()
        assertTrue(BackButton.press()) // the system Back button does the same
        waitForIdle()
        onNode(hasText("What kind of telescope is it?")).assertExists()
    }

    @Test fun setUpLaterKeepsTheDefaults() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        onNode(button("Reflector (Newtonian)")).tap() // chosen, but not finished
        onNode(button("Set up later")).tap()
        assertTrue(state.setupDone)
        assertEquals(TelescopeSetup(), state.setup)
        onNode(hasText("Step 1", substring = true)).assertDoesNotExist()
        assertEquals("true", state.settings()["setupDone"])
    }

    @Test fun existingUserGetsTheWizardOnceAndKeepsTheirSettings() = phoneTest {
        // What an installation from before the wizard saved: many s.* keys, the old "equatorial" key, no setupDone.
        val old = mapOf("bortle" to "7", "telescopeMm" to "650.0", "eyepieceMm" to "9.0", "landscape" to "TREES", "equatorial" to "true", "haptics" to "false")
        val state = fresh().apply { applySettings(old) }
        assertFalse(state.setupDone)
        assertEquals(MountType.EQUATORIAL, state.setup.mount) // migrated
        setContent { AppScreen(state) }
        onNode(hasText("Step 1 of 5")).assertExists()
        // The wizard opens on the migrated mount.
        onNode(button("Next")).tap()
        onNode(hasText("What is the mount?")).assertExists()
        onNode(button("Set up later")).tap()
        assertEquals(7, state.bortle)
        assertEquals(650.0, state.telescopeFocalMm, 0.0)
        assertEquals(9.0, state.eyepieceFocalMm, 0.0)
        assertEquals(org.astrofixxer.ui.Landscape.TREES, state.landscape)
        assertFalse(state.haptics)
        assertEquals(MountType.EQUATORIAL, state.setup.mount)
        // Saved and restored, the wizard is gone for good.
        val again = fresh().apply { applySettings(state.settings()) }
        assertTrue(again.setupDone)
        assertEquals(state.setup, again.setup)
        assertEquals(7, again.bortle)
    }

    @Test fun everyFieldRoundTripsAndBadValuesAreIgnored() {
        val a = fresh().apply {
            setup = TelescopeSetup(TelescopeType.REFLECTOR, MountType.OTHER, PhonePlacement.EYEPIECE, PhoneEdge.RIGHT, EyepieceAngle.RIGHT_ANGLE, Erecting.YES, 270, true)
            setupDone = true; matchEyepieceView = true
        }
        val b = fresh().apply { applySettings(a.settings()) }
        assertEquals(a.setup, b.setup)
        assertTrue(b.setupDone && b.matchEyepieceView)
        b.applySettings(mapOf("telescopeType" to "SPACESHIP", "placement" to "", "phoneEdge" to "up", "viewRotation" to "45", "viewMirrored" to "maybe", "setupDone" to "yes"))
        assertEquals(a.setup, b.setup) // nothing malformed was accepted
        assertTrue(b.setupDone)
        // A Newtonian saved by an older build with a straight or unsure eyepiece loads as a right angle; others keep theirs.
        for (old in listOf(EyepieceAngle.STRAIGHT, EyepieceAngle.UNSURE)) {
            val n = fresh().apply { applySettings(mapOf("telescopeType" to "REFLECTOR", "placement" to "EYEPIECE", "phoneEdge" to "LEFT", "eyepieceAngle" to old.name)) }
            assertEquals(EyepieceAngle.RIGHT_ANGLE, n.setup.eyepieceAngle)
            assertEquals(-1.0, n.setup.axis().vector[0], 0.0)
            assertEquals("RIGHT_ANGLE", n.settings()["eyepieceAngle"])
            val r = fresh().apply { applySettings(mapOf("telescopeType" to "REFRACTOR", "placement" to "EYEPIECE", "eyepieceAngle" to old.name)) }
            assertEquals(old, r.setup.eyepieceAngle)
            val o = fresh().apply { applySettings(mapOf("telescopeType" to "OTHER", "eyepieceAngle" to old.name)) }
            assertEquals(old, o.setup.eyepieceAngle)
        }
        // The angle is applied after the type whatever the key order, and a missing angle on a reflector is a right angle too.
        val m = fresh().apply { applySettings(mapOf("telescopeType" to "REFLECTOR", "placement" to "EYEPIECE")) }
        assertEquals(EyepieceAngle.RIGHT_ANGLE, m.setup.eyepieceAngle)
        // The new mount key beats the old one; the old one alone still counts.
        val c = fresh().apply { applySettings(mapOf("equatorial" to "true", "mountType" to "ALT_AZ")) }
        assertEquals(MountType.ALT_AZ, c.setup.mount)
        val d = fresh().apply { applySettings(mapOf("equatorial" to "false")) }
        assertEquals(MountType.ALT_AZ, d.setup.mount)
        // Settings written by an older build have no setup keys at all: defaults stay.
        val e = fresh().apply { applySettings(mapOf("bortle" to "3")) }
        assertEquals(TelescopeSetup(), e.setup)
        assertFalse(e.setupDone)
    }
}
