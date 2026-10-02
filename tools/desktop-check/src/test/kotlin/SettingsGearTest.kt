import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.SkyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gear at the top right of the sky screen: opens Sky & viewing on Telescope & orientation, and never crowds the other top-bar items. */
@OptIn(ExperimentalTestApi::class)
class SettingsGearTest {
    private val catalog get() = Fixtures.catalog
    private val phoneDensity = 2.625f
    private fun button(label: String) = hasText(label) and hasClickAction()
    private fun ComposeUiTest.gear() = onNode(hasContentDescription("Settings"))
    private fun ComposeUiTest.sheetOpen() = onAllNodes(hasText("Sky & viewing")).fetchSemanticsNodes().isNotEmpty()

    @Test fun gearIsAButtonOfAtLeast48dp() = phoneTest {
        setContent { AppScreen(Fixtures.state()) }
        gear().assertHasClickAction()
        val n = gear().fetchSemanticsNode()
        assertTrue("gear is ${n.size.width / phoneDensity} x ${n.size.height / phoneDensity} dp", n.size.width / phoneDensity >= 47.5f && n.size.height / phoneDensity >= 47.5f)
        screenshot("gear-sky-day")
    }

    @Test fun gearOpensTelescopeAndOrientation() = phoneTest {
        setContent { AppScreen(Fixtures.state()) }
        assertTrue(!sheetOpen())
        gear().tap()
        waitForIdle()
        assertTrue(sheetOpen())
        onNode(hasText("Telescope & orientation") and isSelected()).assertExists()
        onNode(hasText("Sky") and isSelected()).assertDoesNotExist()
        onNode(hasText("Check orientation") and hasClickAction()).assertExists() // that tab's content
        // Back closes the sheet, and the gear works again.
        waitForIdle()
        assertTrue(BackButton.press())
        waitForIdle()
        assertTrue(!sheetOpen())
        gear().tap()
        waitForIdle()
        assertTrue(sheetOpen())
    }

    @Test fun bottomSkyButtonStillOpensTheSkyTab() = phoneTest {
        setContent { AppScreen(Fixtures.state()) }
        onNode(button("Sky")).tap()
        waitForIdle()
        assertTrue(sheetOpen())
        onNode(hasText("Sky") and isSelected()).assertExists()
        onNode(hasText("Telescope & orientation") and isSelected()).assertDoesNotExist()
        onNode(hasText("Star colours")).assertExists()
    }

    @Test fun gearIsNotReachableWhileTheSetupWizardCoversTheScreen() = phoneTest {
        val state = Fixtures.state().apply { setupDone = false }
        setContent { AppScreen(state) }
        val b = gear().fetchSemanticsNode().boundsInRoot
        onRoot().performTouchInput { click(b.center) }
        waitForIdle()
        // (The touch lands on the wizard's own "Set up later" button, which is drawn in the same corner.)
        assertTrue("the wizard let a touch through to the gear", !sheetOpen())
    }

    /** The gear is on screen, and no text or other control sits on top of it. */
    private fun ComposeUiTest.assertGearClear(where: String, widthDp: Int) {
        waitForIdle()
        val g = gear().fetchSemanticsNode()
        val gb = g.boundsInRoot
        assertTrue("$where: gear off screen $gb", gb.left >= 0f && gb.right <= widthDp * phoneDensity + 1 && gb.top >= 0f)
        for (n in onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()) {
            if (n.id == g.id) continue
            val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: continue
            val b = n.boundsInRoot
            if (b.width <= 0f) continue
            val o = gb.intersect(b)
            assertTrue("$where: \"${text.take(30)}\" overlaps the gear (${o.width} x ${o.height} px)", o.width <= 2 || o.height <= 2)
        }
        // The clickable chips must not overlap it either (their padding has no text of its own).
        for (n in onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
            if (n.id == g.id) continue
            val o = gb.intersect(n.boundsInRoot)
            assertTrue("$where: a control at ${n.boundsInRoot} overlaps the gear", o.width <= 2 || o.height <= 2)
        }
    }

    /** The worst cases for the top bar. */
    private val worst: List<Pair<String, (SkyState) -> Unit>> = listOf(
        "sky" to {},
        "long target name" to { s -> s.target = catalog.find("M31") },
        "time travel with a full date" to { s -> s.shiftTime(86_400_000L * 40) },
        "aligned long ago, time travel" to { s ->
            Fixtures.pointAt(s, catalog.find("Vega")!!); Fixtures.align(s, catalog.find("Vega")!!); s.target = catalog.find("M31"); s.shiftTime(86_400_000L * 40)
        },
        "centring a star" to { s -> Fixtures.pointAt(s, catalog.find("Vega")!!); s.startAlign(); s.pickStar(catalog.find("Vega")!!) },
        "guidance open" to { s ->
            Fixtures.pointAt(s, catalog.find("Vega")!!); Fixtures.align(s, catalog.find("Vega")!!); s.target = catalog.find("M31"); s.guidanceExpanded = true
        },
    )

    @Test fun gearNeverOverlapsAnythingOnA360dpPhone() {
        for (night in listOf(false, true)) for ((name, setup) in worst) phoneTest(widthDp = 360) {
            val state = Fixtures.state().apply { this.night = night; setup(this) }
            setContent { AppScreen(state) }
            if (name == "centring a star") assertEquals(AlignState.CENTER_STAR, state.align)
            assertGearClear("$name ${if (night) "night" else "day"}", 360)
            if (name == "time travel with a full date") screenshot("gear-time-travel-${if (night) "night" else "day"}")
            if (night && name == "sky") screenshot("gear-sky-night")
        }
    }
}
