import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.Erecting
import org.astrofixxer.ui.EyepieceAngle
import org.astrofixxer.ui.MountType
import org.astrofixxer.ui.PhoneEdge
import org.astrofixxer.ui.PhonePlacement
import org.astrofixxer.ui.PointingMode
import org.astrofixxer.ui.SkyState
import org.astrofixxer.ui.TelescopeSetup
import org.astrofixxer.ui.TelescopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screens from the Stitch brief that were added in this round, used the way a person would. */
@OptIn(ExperimentalTestApi::class)
class NewScreensTest {
    private val catalog get() = Fixtures.catalog
    private fun button(label: String) = hasText(label) and hasClickAction()

    @Test fun findByPositionAndBrowseLists() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Find")).tap()
        tab("Position")
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextInput("05:35:17")
        fields[1].performTextInput("-05:23:28")
        screenshot("new-find-position")
        onNode(button("Go to this position")).tap()
        assertEquals(83.82, state.target!!.ra, 0.01)
        onNode(button("Find")).tap()
        tab("Lists")
        onNode(hasText("Messier · 110 objects")).assertExists()
        screenshot("new-find-lists")
        tab("Indian constellations") // the chip row scrolls sideways
        onNode(hasText("Indian constellations · 49 objects", substring = true)).assertExists()
    }

    @Test fun infoFromSearchResults() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Find")).tap()
        onNode(hasSetTextAction()).performTextInput("M31")
        onAllNodes(button("Info"))[0].tap()
        onNode(hasText("Andromeda")).assertExists() // constellation row
        waitUntil(15_000) { onAllNodes(hasText("Highest", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        screenshot("new-info-m31")
    }

    @Test fun longPressMenuAlignsAddsAndOpensInfo() = phoneTest {
        val state = Fixtures.state()
        Fixtures.pointAt(state, catalog.find("Vega")!!)
        setContent { AppScreen(state) }
        onRoot().performTouchInput { longClick(center) }
        onNode(button("Align using this star")).assertExists()
        screenshot("new-long-press")
        onNode(button("Align using this star")).tap()
        // The menu starts centring; it never aligns by itself (the old "Align on this" assumed the telescope was centred).
        assertEquals(AlignState.CENTER_STAR, state.align)
        assertEquals("Vega", state.centerStar?.name)
        assertNull(state.alignMatrix)
        state.cancelAlign()
        onRoot().performTouchInput { longClick(center) }
        onNode(button("Add to list")).tap()
        assertTrue(state.watchListText.contains("Vega"))
        onRoot().performTouchInput { longClick(center) }
        onNode(button("Info")).tap()
        onNode(hasText("Lyra")).assertExists()
    }

    @Test fun freeLookPansInBothDirections() = phoneTest {
        val state = Fixtures.state()
        Fixtures.pointAt(state, catalog.find("Vega")!!)
        setContent { AppScreen(state) }
        onNode(button("Compass")).tap()
        onNode(button("Free look")).assertExists()
        assertEquals(PointingMode.FREE, state.mode)
        val alt0 = state.freeAltDeg
        onRoot().performTouchInput { swipe(center, center + Offset(0f, 300f), 400) } // drag the sky down: look higher
        waitForIdle()
        assertTrue("dragging down should raise the view (${state.freeAltDeg} vs $alt0)", state.freeAltDeg > alt0 + 3)
        state.device = org.astrofixxer.astro.Pointing.IDENTITY // sensors move: free look ignores them
        val fwd = state.camera()[2]
        assertTrue(Math.toDegrees(kotlin.math.asin(fwd[2])) > 10)
    }

    @Test fun telescopeSettingsSetTheEyepieceField() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        onNode(hasText("True field: 1.08° · magnification ×48")).assertExists()
        onAllNodes(hasSetTextAction())[1].performTextReplacement("10")
        onNode(hasText("True field: 0.43° · magnification ×120")).assertExists()
        screenshot("new-telescope")
        onNode(button("Equatorial")).performScrollTo().tap() // below the fold on a phone: scroll to it like a person
        assertEquals(MountType.EQUATORIAL, state.setup.mount)
    }

    @Test fun placeAndTime() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Sky")).tap()
        tab("Place & time")
        onAllNodes(hasSetTextAction())[2].performTextInput("Leh")
        onNode(button("Leh") and androidx.compose.ui.test.hasSetTextAction().not()).tap() // the city row, not the search box
        assertEquals(34.15, state.lat, 0.01)
        assertTrue(state.manualLocation)
        screenshot("new-place")
        val fields = onAllNodes(hasSetTextAction())
        fields[3].performTextReplacement("2026-12-14")
        fields[4].performTextReplacement("22:00")
        onNode(button("Show this time")).tap()
        assertFalse(state.live)
        onNode(hasText("Mon 14 Dec 2026, 22:00")).assertExists() // the clock chip, back on the sky
    }

    @Test fun tonightCardAndFilters() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Events")).tap()
        waitUntil(20_000) { onAllNodes(hasText("Sunset", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Fully dark", substring = true)).assertExists()
        onNode(hasText("% lit", substring = true)).assertExists()
        screenshot("new-tonight")
        onNode(hasText("Orionids meteor shower peak", substring = true)).assertIsDisplayed()
        onNode(button("This week")).tap()
        // 18 days away. Compose keeps the dropped row around for reuse (still in the semantics tree, outside the list's
        // clipped area), so the check is that it is no longer shown, not that the node is gone.
        onNode(hasText("Orionids meteor shower peak", substring = true)).assertIsNotDisplayed()
        onNode(hasText("Full Moon")).assertExists()
    }

    @Test fun askWithoutAMicrophoneOffersQuestions() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Ask")).tap()
        onNode(button("Find Saturn")).tap()
        assertEquals("Saturn", state.target?.name)
        onNode(hasText("Saturn is", substring = true)).assertExists()
        screenshot("new-guide-chips")
    }

    @Test fun settingsSurviveARestart() {
        val a = Fixtures.state().apply {
            showEcliptic = true; showBoundaries = true; hiddenDsoTypes = setOf("Ga"); bortle = 7
            telescopeFocalMm = 650.0; eyepieceFocalMm = 9.0; landscape = org.astrofixxer.ui.Landscape.TREES
            setup = TelescopeSetup(TelescopeType.REFLECTOR, MountType.EQUATORIAL, PhonePlacement.EYEPIECE, PhoneEdge.LEFT, EyepieceAngle.RIGHT_ANGLE, Erecting.NO, 90, true)
            setupDone = true; matchEyepieceView = true
        }
        val b = SkyState(0, 0.0, 0.0).apply { applySettings(a.settings()) }
        assertEquals(a.settings(), b.settings())
        assertEquals(a.setup, b.setup)
        assertTrue(b.setupDone && b.matchEyepieceView)
        b.applySettings(mapOf("bortle" to "banana", "telescopeMm" to "-3")) // bad values keep what was there
        assertEquals(7, b.bortle)
        assertEquals(650.0, b.telescopeFocalMm, 0.0)
    }
}
