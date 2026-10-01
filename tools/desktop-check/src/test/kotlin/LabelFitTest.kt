import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import org.astrofixxer.ui.Label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Button labels must fit their buttons on any sans-serif font, including the wide ones (DejaVu Sans, Verdana) that a
 * phone or a CI runner may use instead of the narrow default. Same "cut off" test as the screen audit: a text layout
 * that reports visual overflow.
 */
@OptIn(ExperimentalTestApi::class)
class LabelFitTest {
    private fun button(label: String) = hasText(label) and hasClickAction()

    private fun ComposeUiTest.clipped(): List<String> =
        onAllNodes(anyNode, useUnmergedTree = true).fetchSemanticsNodes().filter { n -> n.boundsInRoot.width > 0f && n.textLayouts().any { it.hasVisualOverflow } }
            .map { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "?" }

    /** The solve flow's first step with the phone flat on the tube: Cancel and "Change phone placement" share the bottom bar. */
    @Test fun tubePlacementBarFitsAt360dpDayAndNight() {
        for (night in listOf(false, true)) phoneTest(widthDp = 360) {
            val state = Fixtures.state().apply { this.night = night }
            setContent { AppScreen(state, host = FakeHost()) }
            onNode(button("Sky")).tap()
            tab("Telescope & orientation")
            onNode(button("Solve with camera")).performScrollTo().tap()
            waitForIdle()
            onNode(button("Change phone placement")).assertExists()
            assertEquals("night=$night: cut-off texts", emptyList<String>(), clipped())
        }
    }

    /** Every button in the bar is as tall as a finger needs, even when its label wraps. */
    @Test fun tubePlacementButtonsStayAtLeast48dpTall() = phoneTest(widthDp = 360) {
        setContent { AppScreen(Fixtures.state(), host = FakeHost()) }
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        onNode(button("Solve with camera")).performScrollTo().tap()
        waitForIdle()
        for (label in listOf("Cancel", "Change phone placement")) {
            val h = onNode(button(label)).fetchSemanticsNode().size.height / 2.625f
            assertTrue("$label is $h dp tall", h >= 47.5f)
        }
    }

    /** Label alone, in buttons of several widths and both min heights: never cut off, wraps rather than shrinking below need, two-line buttons stay close to their 48/56 dp minimum. */
    @Test fun labelWrapsInsteadOfClippingInNarrowButtons() = phoneTest(widthDp = 360) {
        val texts = listOf("Change phone placement", "Calibrate camera offset", "Use a photo from the gallery")
        setContent {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                for (min in listOf(48, 56)) for (w in listOf(140, 170, 200)) for (t in texts) {
                    androidx.compose.material3.Button(onClick = {}, modifier = androidx.compose.ui.Modifier.width(w.dp).heightIn(min = min.dp).testTag("b-$min-$w-$t")) { Label(t) }
                }
            }
        }
        waitForIdle()
        assertEquals(emptyList<String>(), clipped())
        for (min in listOf(48, 56)) for (w in listOf(140, 170, 200)) for (t in texts) {
            val h = onNode(hasTestTag("b-$min-$w-$t")).fetchSemanticsNode().size.height / 2.625f
            assertTrue("$t at $w dp, min $min: button is $h dp tall (a two-line label may make it a little taller than its minimum, never a lot)", h <= 58f)
        }
        screenshot("label-fit")
    }
}
