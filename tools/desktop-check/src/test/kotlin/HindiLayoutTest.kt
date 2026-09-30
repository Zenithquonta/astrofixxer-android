import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.astrofixxer.ui.DayColors
import org.astrofixxer.ui.I18n
import org.astrofixxer.ui.Label
import org.astrofixxer.ui.PhonePlacement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real screen regressions for the labels clipped by the Linux Devanagari fonts. */
@OptIn(ExperimentalTestApi::class)
class HindiLayoutTest {
    private fun inHindi(block: () -> Unit) {
        val previous = I18n.language
        I18n.language = "hi"
        try { block() } finally { I18n.language = previous }
    }

    private fun ComposeUiTest.assertLabelFits(english: String, widthDp: Int) {
        val label = I18n.t(english)
        val nodes = onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Missing visible label: $label", nodes.isNotEmpty())
        val visible = nodes.filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        assertTrue("Label is off screen: $label", visible.isNotEmpty())
        for (node in visible) {
            val layouts = node.textLayouts()
            assertTrue("No measured text for $label", layouts.isNotEmpty())
            assertFalse("Text is cut off: $label", layouts.any { it.hasVisualOverflow })
            assertTrue("Text runs off screen: $label", node.boundsInRoot.left >= -1f && node.boundsInRoot.right <= widthDp * 2.625f + 1f)
        }
        val control = onNode(hasText(label) and hasClickAction()).fetchSemanticsNode()
        assertTrue("Control is smaller than 48 dp: $label", control.size.width / 2.625f >= 47.5f && control.size.height / 2.625f >= 47.5f)
    }

    @Test fun resetAdjustmentFitsOnNarrowPhones() = inHindi {
        for (width in listOf(360, 411)) for (night in listOf(false, true)) phoneTest(widthDp = width) {
            val state = Fixtures.state().apply { this.night = night }
            val vega = Fixtures.catalog.find("Vega")!!
            Fixtures.pointAt(state, vega)
            state.startAlign(); state.pickStar(vega)
            setContent { AppScreen(state) }
            screenshot("hindi-reset-$width-${if (night) "night" else "day"}")
            assertLabelFits("Reset adjustment", width)
        }
    }

    @Test fun alignNowFitsInMountingChangedDialog() = inHindi {
        for (width in listOf(360, 411)) for (night in listOf(false, true)) phoneTest(widthDp = width) {
            val state = Fixtures.state().apply { this.night = night }
            val vega = Fixtures.catalog.find("Vega")!!
            Fixtures.pointAt(state, vega); Fixtures.align(state, vega)
            state.updateSetup(state.setup.copy(placement = PhonePlacement.CAMERA_FORWARD))
            setContent { AppScreen(state) }
            screenshot("hindi-align-now-$width-${if (night) "night" else "day"}")
            assertLabelFits("Align now", width)
        }
    }

    @Test fun refractorFitsInTelescopeSettingsAndWizard() = inHindi {
        for (width in listOf(360, 411)) for (night in listOf(false, true)) phoneTest(widthDp = width) {
            val state = Fixtures.state().apply { this.night = night }
            setContent { AppScreen(state) }
            onNode(hasText(I18n.t("Sky")) and hasClickAction()).tap()
            tab(I18n.t("Telescope & orientation"))
            onNode(hasText(I18n.t("Refractor")) and hasClickAction()).performScrollTo()
            screenshot("hindi-refractor-$width-${if (night) "night" else "day"}")
            assertLabelFits("Refractor", width)
        }
        phoneTest {
            val state = Fixtures.state().apply { setupDone = false }
            setContent { AppScreen(state) }
            screenshot("hindi-refractor-wizard")
            assertLabelFits("Refractor", 360)
        }
    }

    @Test fun labelRemeasuresWhenFontScaleChanges() = inHindi {
        phoneTest {
            val fontScale = mutableStateOf(1f)
            val label = I18n.t("Reset adjustment")
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f, fontScale.value)) {
                    MaterialTheme(colorScheme = DayColors) {
                        Box(Modifier.width(160.dp)) { Label(label) }
                    }
                }
            }
            for (scale in listOf(1f, 2f, 1f)) {
                runOnIdle { fontScale.value = scale }
                waitForIdle()
                val layouts = onNode(hasText(label)).fetchSemanticsNode().textLayouts()
                assertTrue("No measured text at font scale $scale", layouts.isNotEmpty())
                assertTrue("Text did not remeasure at font scale $scale", layouts.all { it.layoutInput.density.fontScale == scale })
                assertFalse("Label clipped after changing font scale to $scale", layouts.any { it.hasVisualOverflow })
            }
        }
    }
}
