import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
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
import org.astrofixxer.ui.I18n
import org.astrofixxer.ui.Projector
import org.astrofixxer.ui.SkyState
import org.astrofixxer.update.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The screens the README's guide shows, rendered from the real UI and the shared fixtures into build/screens/readme-*.png.
 * Run: ./gradlew test --offline --tests ReadmeShotsTest   then   python3 tools/repo-art/readme_screens.py
 *
 * Every shot is a real Compose screen on the desktop test harness at 360 x 780 dp. Nothing here is a phone or a telescope.
 * The camera flow's shots are in pending-w4/ReadmeCameraShotsTest.kt until that flow is merged.
 */
@OptIn(ExperimentalTestApi::class)
class ReadmeShotsTest {
    private val catalog get() = Fixtures.catalog
    private val vega get() = catalog.find("Vega")!!
    private val m57 get() = catalog.find("M57")!!
    private fun button(label: String) = hasText(label) and hasClickAction()

    @Before fun english() { I18n.language = "en" }

    private fun ComposeUiTest.next() = onNode(button("Next")).tap()

    private fun fresh() = SkyState(Fixtures.start, 28.6139, 77.2090) // setupDone is false: a first launch

    // ---------------------------------------------------------------- 1. the setup wizard

    @Test fun setupWizard() = phoneTest {
        val state = fresh()
        setContent { AppScreen(state) }
        onNode(hasText("What kind of telescope is it?")).assertExists()
        onNode(button("Refractor")).tap()
        screenshot("readme-wizard-type")
        next()
        onNode(button("Equatorial")).tap()
        screenshot("readme-wizard-mount")
        next()
        onNode(hasText("Where is your phone mounted?")).assertExists()
        screenshot("readme-wizard-placement")
        onNode(button("Flat against the telescope tube")).tap()
        next()
        onNode(button("Left edge")).tap()
        next()
        onNode(hasText("All set")).assertExists()
        screenshot("readme-wizard-summary")
        onNode(button("Finish")).tap()
        assertTrue(state.setupDone)
    }

    @Test fun telescopeAndOrientationTab() = phoneTest {
        val state = Fixtures.state()
        setContent { AppScreen(state) }
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        waitForIdle()
        screenshot("readme-telescope-tab")
        onNode(button("Check orientation")).performScrollTo()
        screenshot("readme-telescope-tab-eyepiece")
    }

    // ---------------------------------------------------------------- 2. alignment

    /** Where [obj] is drawn on the screen now, in pixels. */
    private fun ComposeUiTest.px(state: SkyState, obj: SkyObject): Offset {
        val size = onRoot().fetchSemanticsNode().size
        return Projector(state.camera(), size.width.toFloat(), size.height.toFloat(), state.fovDeg).project(state.ray(obj))!!
    }

    private fun ComposeUiTest.centre(): Offset = onRoot().fetchSemanticsNode().size.let { Offset(it.width / 2f, it.height / 2f) }

    private fun ComposeUiTest.dragMap(by: Offset) {
        val from = centre() + Offset(-by.x / 2, -by.y / 2 + 100f)
        onRoot().performTouchInput { swipe(from, from + by, 400) }
        waitForIdle()
    }

    @Test fun alignOnAStar() = phoneTest {
        val state = Fixtures.state()
        // The telescope really is on Vega, but the phone's compass is 8 degrees off, so the map is not where the sky is.
        Fixtures.pointAt(state, vega)
        state.device = Pointing.matMul(Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), Math.toRadians(8.0)), state.device)
        setContent { AppScreen(state) }
        onNode(button("Align")).tap()
        assertEquals(AlignState.PICK_STAR, state.align)
        onNode(hasText("Tap the star you will centre in the telescope")).assertExists()
        screenshot("readme-align-1-pick")
        onRoot().performTouchInput { click(px(state, vega)) }
        assertEquals(AlignState.CENTER_STAR, state.align)
        onNode(hasText("Drag the map to place Vega under the +")).assertExists()
        screenshot("readme-align-2-centre")
        dragMap(centre() - px(state, vega)) // a finger drags the map until Vega is under the +
        val near = px(state, vega)
        assertTrue("Vega should be under the +", Math.hypot((near.x - centre().x).toDouble(), (near.y - centre().y).toDouble()) < 40.0)
        screenshot("readme-align-3-dragged")
        onNode(button("Confirm alignment")).tap()
        assertEquals(AlignState.ALIGNED, state.align)
        onNode(hasText("Aligned on Vega", substring = true)).assertExists()
        screenshot("readme-align-4-result")
    }

    // ---------------------------------------------------------------- 3. next-star guidance

    /** Aligned on Vega, M57 as the target, and the telescope pointing [dDec] degrees north of M57. */
    private fun guidedAt(dDec: Double): SkyState = Fixtures.state().also { s ->
        Fixtures.pointAt(s, vega)
        Fixtures.align(s, vega)
        s.target = m57
        Fixtures.pointAt(s, SkyObject("Near M57", m57.ra, m57.dec + dDec, null, "S"))
    }

    @Test fun guidanceArrowCloseAndOnTarget() = phoneTest {
        val s = guidedAt(12.0)
        setContent { AppScreen(s) }
        onNode(hasText("Move to M57")).assertExists()
        screenshot("readme-guide-1-arrow")
        Fixtures.pointAt(s, SkyObject("Near M57", m57.ra, m57.dec + 2.0, null, "S"))
        waitForIdle()
        onNode(hasText("Close to M57")).assertExists()
        screenshot("readme-guide-2-close")
        Fixtures.pointAt(s, SkyObject("Near M57", m57.ra, m57.dec + 0.2, null, "S"))
        waitForIdle()
        onNode(hasText("On target: M57")).assertExists()
        screenshot("readme-guide-3-on-target")
    }

    // ---------------------------------------------------------------- 5. orientation and the eyepiece view

    @Test fun orientationCheckAndEyepieceView() = phoneTest {
        val state = Fixtures.state().apply { setup = setup.copy(viewRotationDeg = 90, viewMirrored = true) }
        setContent { AppScreen(state) }
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        onNode(button("Check orientation")).performScrollTo().tap()
        onNode(hasText("Two quick checks. Do them once after fitting the phone, or whenever directions seem wrong.")).assertExists()
        screenshot("readme-orient-1-menu")
        onNode(button("Check the eyepiece view")).tap()
        onNode(hasText("1. Nudge the telescope up a little, toward the zenith. Which way did the star move in the eyepiece?")).assertExists()
        screenshot("readme-orient-2-question")
        onNode(button("Down")).tap()
        onNode(button("Left")).tap()
        onNode(hasText("Your eyepiece view is: upright.")).assertExists()
        screenshot("readme-orient-3-result")
    }

    @Test fun rotateAndMirrorTheView() = phoneTest {
        val s = Fixtures.state()
        Fixtures.pointAt(s, vega)
        Fixtures.align(s, vega)
        s.target = m57
        s.matchEyepieceView = true
        s.setup = s.setup.copy(viewRotationDeg = 90, viewMirrored = true)
        setContent { AppScreen(s) }
        onNode(hasText("Move to M57")).assertExists()
        screenshot("readme-view-matched")
        onNode(button("More")).tap()
        onNode(button("Rotate view 90°")).assertExists()
        onNode(button("Mirror view")).assertExists()
        screenshot("readme-view-more")
    }

    // ---------------------------------------------------------------- 6. App updates

    @Test fun appUpdatesPanel() = phoneTest {
        val info = UpdateInfo(15, "0.2.0-preview", "org.astrofixxer.preview", "AstroFixxer.apk", "a".repeat(64), 41_943_040, "abcdef1", true,
            "https://github.com/Zenithquonta/astrofixxer-android/releases/download/latest-build/AstroFixxer.apk", "latest-build")
        val fake = FakeUpdater(org.astrofixxer.ui.UpdateStatus.Available(info))
        setContent { AppScreen(Fixtures.state(), updater = fake) }
        onNode(button(I18n.t("Sky"))).tap()
        tab(I18n.t("More"))
        onNode(hasText(I18n.t("App updates"))).performScrollTo()
        onNode(hasText("Update available: 0.2.0-preview (build 15)")).assertExists()
        onNode(button("Download and install")).performScrollTo()
        screenshot("readme-updates-available")
    }
}
