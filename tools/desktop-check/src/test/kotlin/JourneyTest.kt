import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.I18n
import org.astrofixxer.ui.PointingMode
import org.astrofixxer.ui.Projector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a first-time user does with the app, step by step, on a 360 dp phone. Every step asserts what the user
 * should see and saves a screenshot to build/screens/journey-*.png.
 */
@OptIn(ExperimentalTestApi::class)
class JourneyTest {
    private val catalog get() = Fixtures.catalog
    private fun button(label: String) = hasText(label) and hasClickAction()

    @Test fun firstLaunchTutorial() = phoneTest {
        val state = Fixtures.state().apply { showOnboarding = true }
        setContent { AppScreen(state) }
        onNode(hasText("Quick start 1/4")).assertExists()
        screenshot("journey-01-tutorial")
        repeat(3) { onNode(button("Next")).tap() }
        onNode(hasText("Quick start 4/4")).assertExists()
        onNode(button("Start")).tap()
        assertFalse(state.showOnboarding)
        onNode(hasText("Quick start", substring = true)).assertDoesNotExist()
        screenshot("journey-02-sky")
    }

    @Test fun findTargetBeforeAligningExplainsWhatToDo() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Find")).tap()
        onNode(hasText("Visible now")).assertExists() // empty box still offers something to pick
        screenshot("journey-03-find-empty")
        onNode(hasSetTextAction()).performTextInput("ring neb")
        onNode(hasText("M57") and hasClickAction()).assertExists()
        screenshot("journey-04-find-results")
        onNode(hasSetTextAction()).performImeAction() // Enter picks the best match
        assertEquals("M57", state.target?.name)
        onNode(hasText("To get directions to M57", substring = true)).assertExists()
        screenshot("journey-05-target-not-aligned")
    }

    @Test fun alignOnVegaThenGuideToM57() = phoneTest {
        val state = Fixtures.state()
        state.target = catalog.find("M57")
        val vega = catalog.find("Vega")!!
        Fixtures.pointAt(state, vega) // telescope (and phone) on Vega
        setContent { AppScreen(state) }
        onNode(button("Align")).tap()
        onNode(hasText("Tap the star the telescope points at")).assertExists()
        screenshot("journey-06-pick-star")
        onRoot().performTouchInput { click(center) } // Vega is under the crosshair
        assertEquals(AlignState.ALIGNED, state.align)
        assertEquals("Vega", state.alignStar?.name)
        onNode(hasText("Move to M57")).assertExists()
        onNode(hasText("Ring Nebula")).assertExists() // the card shows the friendly name, not catalogue numbers
        val (_, _, sep) = state.guidance()!!
        assertTrue("M57 is 6-7° from Vega, got $sep", sep in 5.0..8.0)
        screenshot("journey-07-guidance")
        Fixtures.pointAt(state, catalog.find("M57")!!) // user moves the telescope along the arrows
        onNode(hasText("On target: M57")).assertExists()
        screenshot("journey-08-on-target")
    }

    @Test fun tappingAlignByMistakeKeepsTheAlignment() = phoneTest {
        val state = Fixtures.state()
        Fixtures.pointAt(state, catalog.find("Vega")!!)
        setContent { AppScreen(state) }
        onNode(button("Align")).tap()
        onRoot().performTouchInput { click(center) }
        val alignment = state.alignMatrix
        assertNotNull(alignment)
        onNode(button("Align")).tap()
        onNode(button("Cancel")).tap()
        assertEquals(AlignState.ALIGNED, state.align)
        assertSame(alignment, state.alignMatrix)
        onNode(button("Align")).tap()
        waitForIdle()
        assertTrue(BackButton.press()) // Back also cancels picking
        waitForIdle()
        assertEquals(AlignState.ALIGNED, state.align)
        assertSame(alignment, state.alignMatrix)
    }

    @Test fun backButtonClosesSheetsBeforeLeavingTheApp() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        waitForIdle()
        assertFalse("nothing open: Back should leave the app", BackButton.press())
        for (sheet in listOf("Find", "Events", "Sky")) {
            onNode(button(sheet)).tap()
            onNode(button("Close")).assertExists()
            assertTrue(BackButton.press())
            waitForIdle()
            onNode(button("Close")).assertDoesNotExist()
        }
    }

    @Test fun timeTravelIsVisibleAndReversible() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(hasText("21:30") and hasClickAction()).tap() // the clock chip
        onNode(button("+1 d")).tap()
        assertFalse(state.live)
        assertEquals(Fixtures.start + 86_400_000L, state.timeMillis)
        onNode(hasText("Tue 29 Sep 2026, 21:30")).assertExists() // the chip says which time is shown
        screenshot("journey-09-time-travel")
        onNode(button("−1 h")).tap()
        assertEquals(Fixtures.start + 82_800_000L, state.timeMillis)
        onNode(button("Now")).tap()
        assertTrue(state.live)
    }

    @Test fun eventsJumpToTheirTime() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Events")).tap()
        onNode(hasText("★ Moon covers Antares")).assertExists()
        screenshot("journey-10-events")
        onNode(hasText("Orionids meteor shower peak", substring = true)).tap()
        assertFalse(state.live)
        onNode(button("Now")).assertExists() // time bar shows while away from the present
        screenshot("journey-11-event-sky")
    }

    @Test fun nightModeAndHindi() = phoneTest {
        val state = Fixtures.state().apply { target = catalog.find("M57") }
        Fixtures.pointAt(state, catalog.find("Vega")!!)
        setContent { AppScreen(state) }
        onNode(button("Night")).tap()
        assertTrue(state.night)
        onNode(button("Day")).assertExists()
        screenshot("journey-12-night")
        try {
            onNode(button("Sky")).tap()
            onNode(hasScrollAction()).performScrollToNode(hasText("English"))
            clickStepper("Language")
            assertEquals("hi", I18n.language)
            onNode(hasText("हिन्दी")).assertExists()
            screenshot("journey-13-hindi-settings")
            assertTrue(BackButton.press())
            waitForIdle()
            onNode(button("खोजें")).assertExists() // "Find"
            screenshot("journey-14-hindi-night")
        } finally {
            I18n.language = "en"
        }
    }

    @Test fun manualModeDragMovesTheSkyWithTheFinger() = phoneTest {
        val state = Fixtures.state().apply { mode = PointingMode.MANUAL }
        val vega = catalog.find("Vega")!!
        Fixtures.pointAt(state, vega)
        setContent { AppScreen(state) }
        onRoot().performTouchInput { swipe(center, center + Offset(150f, 0f), 400) }
        waitForIdle()
        val size = onRoot().fetchSemanticsNode().size
        val p = Projector(state.camera(), size.width.toFloat(), size.height.toFloat(), state.fovDeg).project(state.ray(vega))!!
        val moved = p.x - size.width / 2f
        assertTrue("Vega should follow the finger ~150 px right (less the touch slop), moved $moved px", moved in 115f..165f)
    }

    @Test fun myObjectsShowErrorsAndWatchListSteps() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Sky")).tap()
        onNode(button("My objects & lists")).tap()
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextInput("My nova, 19:30:00, +35:00:00\nBroken, 99999999999:00, 0")
        fields[1].performTextInput("Tonight's targets: M57 \"Double Cluster\" (low in NE) Albireo")
        onNode(button("Save")).tap()
        onNode(hasText("Line 2: invalid RA", substring = true)).assertExists() // no crash, a clear message
        assertEquals(listOf("My nova"), state.userObjects.map { it.name })
        screenshot("journey-15-lists")
        onNode(hasScrollAction()).performScrollToNode(hasText("Active watch list"))
        clickStepper("Active watch list")
        assertEquals("M57", state.target?.name)
        onNode(button("Close")).tap()
        onNode(hasText("Tonight's targets · 1/3")).assertExists()
        onNode(button("›")).tap()
        onNode(hasText("Tonight's targets · 2/3")).assertExists()
        screenshot("journey-16-watch-list")
    }

    @Test fun typedLocationIsKept() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Sky")).tap()
        onNode(hasScrollAction()).performScrollToNode(hasText("Use this location"))
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("19.0760")
        fields[1].performTextReplacement("72.8777")
        onNode(button("Use this location")).tap()
        assertTrue(state.manualLocation)
        assertEquals(19.076, state.lat, 1e-9)
        fields[0].performTextReplacement("95")
        onNode(hasText("Latitude −90 to 90", substring = true)).assertExists()
        onNode(button("Use this location")).assertIsNotEnabled()
    }
}
