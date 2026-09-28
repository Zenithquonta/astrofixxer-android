import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.DayColors
import org.astrofixxer.ui.DayPalette
import org.astrofixxer.ui.I18n
import org.astrofixxer.ui.NightColors
import org.astrofixxer.ui.NightPalette
import org.astrofixxer.ui.SkyState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks every screen, in English and Hindi, day and night, on 360 dp and 411 dp phones:
 * - every tappable control is at least 48 × 48 dp (Android accessibility guideline),
 * - no text is clipped or runs off the screen,
 * - no two pieces of text overlap on the sky screen,
 * - text colours meet contrast targets (WCAG AA 4.5:1 by day; night mode is deliberately dim red, target 3:1).
 * Findings are written to build/screens/audit.txt; the test fails if there are any.
 */
@OptIn(ExperimentalTestApi::class)
class AuditTest {
    private class Screen(val name: String, val overlayOpen: Boolean, val setup: (SkyState) -> Unit, val open: ComposeUiTest.() -> Unit = {})

    private val catalog get() = Fixtures.catalog
    private fun button(label: String) = hasText(label) and hasClickAction()

    private val screens = listOf(
        Screen("sky", false, {}),
        Screen("sky-target-not-aligned", false, { it.target = catalog.find("M57") }),
        Screen("sky-picking-star", false, { it.target = catalog.find("M57"); it.align = AlignState.PICK_STAR }),
        Screen("sky-guiding-busy", false, { s ->
            Fixtures.pointAt(s, catalog.find("Vega")!!)
            s.alignOn(catalog.find("Vega")!!)
            s.target = catalog.find("M31") // long names: Andromeda Galaxy · …
            s.watchListText = "Autumn galaxies: M31 M33 NGC891 (edge-on, faint)"
            s.applyUserText(catalog)
            s.selectList(0, catalog)
            s.guideHeard = "where is the Andromeda galaxy"
            s.guideAnswer = "M31 is 38 degrees up in the north-east."
        }),
        Screen("sky-time-travel", false, { it.shiftTime(86_400_000L * 40) }),
        Screen("find-empty", true, {}, { onNode(button(I18n.t("Find"))).tap() }),
        Screen("find-results", true, {}, {
            onNode(button(I18n.t("Find"))).tap()
            onNode(hasSetTextAction()).performTextInput("ngc 7")
        }),
        Screen("events", true, {}, { onNode(button(I18n.t("Events"))).tap() }),
        Screen("sky-options", true, {}, { onNode(button(I18n.t("Sky"))).tap() }),
        Screen("lists", true, {}, { onNode(button(I18n.t("Sky"))).tap(); onNode(button(I18n.t("My objects & lists"))).tap() }),
        Screen("help", true, {}, { onNode(button(I18n.t("Sky"))).tap(); onNode(button(I18n.t("Help"))).tap() }),
        Screen("tutorial", true, { it.showOnboarding = true }),
    )

    @Test fun everyScreenPassesTheAudit() {
        val findings = mutableListOf<String>()
        var checked = 0
        for (width in listOf(360, 411)) for (lang in listOf("en", "hi")) for (night in listOf(false, true)) for (screen in screens) {
            I18n.language = lang
            try {
                phoneTest(widthDp = width) {
                    val state = Fixtures.state().apply { this.night = night; screen.setup(this) }
                    setContent { AppScreen(state) }
                    screen.open(this)
                    waitForIdle()
                    val where = "${screen.name} [$width dp, $lang, ${if (night) "night" else "day"}]"
                    findings += audit(this, where, width, checkOverlap = !screen.overlayOpen)
                    if (width == 360) screenshot("audit-${screen.name}-$lang-${if (night) "night" else "day"}")
                    checked++
                }
            } finally {
                I18n.language = "en"
            }
        }
        findings += contrast()
        val report = "Checked $checked screen variants.\n" + if (findings.isEmpty()) "No findings.\n" else findings.joinToString("\n", postfix = "\n")
        File(Fixtures.screens, "audit.txt").writeText(report)
        println(report)
        assertTrue("${findings.size} audit findings:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    private fun audit(t: ComposeUiTest, where: String, widthDp: Int, checkOverlap: Boolean): List<String> {
        val out = mutableListOf<String>()
        val density = 2.625f
        val screenW = widthDp * density
        val nodes = t.onAllNodes(anyNode, useUnmergedTree = true).fetchSemanticsNodes()
        val clickable = t.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        for (n in clickable) {
            if (n.boundsInRoot.width <= 0f) continue // scrolled away
            // Laid-out size, not the visible part: a list row half scrolled out of view is still a full-size target.
            val w = n.size.width / density
            val h = n.size.height / density
            val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: n.config.getOrNull(SemanticsProperties.EditableText)?.text ?: "?"
            if (w < 47.5f || h < 47.5f) out += "$where: control \"$label\" is ${"%.0f".format(w)}×${"%.0f".format(h)} dp (< 48 dp)"
        }
        val texts = mutableListOf<Pair<String, Rect>>()
        for (n in nodes) {
            val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: continue
            val b = n.boundsInRoot
            if (b.width <= 0f) continue
            if (n.textLayouts().any { it.hasVisualOverflow }) out += "$where: text is cut off: \"${text.take(60)}\""
            if (b.right > screenW + 1 || b.left < -1) out += "$where: text runs off the screen: \"${text.take(60)}\""
            texts += text to b
        }
        if (checkOverlap) for (i in texts.indices) for (j in i + 1 until texts.size) {
            val (a, ra) = texts[i]
            val (b, rb) = texts[j]
            val overlap = ra.intersect(rb)
            if (overlap.width > 2 && overlap.height > 2) out += "$where: \"${a.take(30)}\" overlaps \"${b.take(30)}\""
        }
        return out
    }

    private fun ratio(fg: Color, bg: Color): Double {
        val l1 = fg.compositeOver(bg).luminance() + 0.05
        val l2 = bg.luminance() + 0.05
        return maxOf(l1, l2).toDouble() / minOf(l1, l2)
    }

    /** Text/background colour pairs as drawn. Panels are translucent over the sky, so they are composited over it first. */
    private fun contrast(): List<String> {
        val out = mutableListOf<String>()
        for ((mode, scheme, pal, minimum) in listOf(
            Quad("day", DayColors, DayPalette, 4.5),
            Quad("night", NightColors, NightPalette, 3.0),
        )) {
            val panel = scheme.surface.compositeOver(pal.sky)
            val pairs = listOf(
                "panel text" to (scheme.onSurface to panel),
                "panel secondary text" to (scheme.onSurfaceVariant to panel),
                "panel headings / values" to (scheme.primary to panel),
                "warnings" to (scheme.error to panel),
                "Align button label" to (scheme.onPrimary to scheme.primary),
                "Now button / time-travel clock" to (scheme.onPrimary to scheme.secondary),
                "outlined button label on sky" to (scheme.primary to pal.sky),
                "star labels" to (pal.label to pal.sky),
                "compass points" to (pal.cardinal to pal.sky),
                "deep-sky labels" to (pal.deepSky to pal.sky),
                "target label" to (pal.target to pal.sky),
            )
            for ((what, colours) in pairs) {
                val r = ratio(colours.first, colours.second)
                if (r < minimum) out += "contrast [$mode]: $what is ${"%.2f".format(r)}:1 (target $minimum:1)"
            }
        }
        return out
    }

    private data class Quad(val a: String, val b: androidx.compose.material3.ColorScheme, val c: org.astrofixxer.ui.Palette, val d: Double)
}
