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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.astrofixxer.astro.Pointing
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.Erecting
import org.astrofixxer.ui.EyepieceAngle
import org.astrofixxer.ui.MountType
import org.astrofixxer.ui.PhonePlacement
import org.astrofixxer.ui.TelescopeType
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

    /** Opens Sky & viewing, Telescope & orientation, Check orientation. */
    private fun ComposeUiTest.openCheck() {
        onNode(button(I18n.t("Sky"))).tap()
        tab(I18n.t("Telescope & orientation"))
        onNode(button(I18n.t("Check orientation"))).performScrollTo().tap()
    }

    private fun ComposeUiTest.next(times: Int) = repeat(times) { onNode(button(I18n.t("Next"))).tap() }

    /** Aligned on Vega with the compass [errorDeg] off, result card showing. */
    private fun confirmed(s: SkyState, errorDeg: Double) {
        Fixtures.pointAt(s, catalog.find("Vega")!!)
        s.device = Pointing.matMul(Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), Math.toRadians(errorDeg)), s.device)
        s.startAlign(); s.pickStar(catalog.find("Vega")!!); s.confirmAlignment()
    }

    /** Aligned on Vega (card dismissed) with M57 as the target, the telescope a few degrees away from it. */
    private fun guided(s: SkyState) {
        Fixtures.pointAt(s, catalog.find("Vega")!!); Fixtures.align(s, catalog.find("Vega")!!)
        s.target = catalog.find("M57")
    }

    /** A bright star 2-8° up, found by moving the clock; null if none within a day. */
    private fun lowStar(s: SkyState): org.astrofixxer.astro.SkyObject? {
        var t = Fixtures.start
        while (t < Fixtures.start + 86_400_000L) {
            s.timeMillis = t
            catalog.objects.firstOrNull { it.type == "S" && (it.mag ?: 9.0) < 3.5 && Math.toDegrees(kotlin.math.asin(s.ray(it)[2])) in 2.0..8.0 }?.let { return it }
            t += 1_800_000L
        }
        return null
    }

    private val screens = listOf(
        Screen("sky", false, {}),
        Screen("sky-target-not-aligned", false, { it.target = catalog.find("M57") }),
        Screen("sky-picking-star", false, { it.target = catalog.find("M57"); it.startAlign() }),
        Screen("sky-picking-star-refused", false, { it.startAlign(); it.pickStar(catalog.find("M57")!!) }),
        Screen("sky-picking-star-no-compass", false, { it.hasCompass = false; it.startAlign() }),
        Screen("sky-centering-star", false, { Fixtures.pointAt(it, catalog.find("Vega")!!); it.startAlign(); it.pickStar(catalog.find("Vega")!!) }),
        Screen("sky-centering-star-dragged", false, { s ->
            Fixtures.pointAt(s, catalog.find("Vega")!!); s.startAlign(); s.pickStar(catalog.find("Vega")!!)
            s.dragAdjust(-90f, 60f, 360f, 780f)
        }),
        Screen("sky-centering-low-star", false, { s -> lowStar(s)?.let { s.startAlign(); s.pickStar(it) } }),
        Screen("sky-align-result", false, { s -> confirmed(s, 4.0) }),
        Screen("sky-align-result-large", false, { s -> confirmed(s, 40.0) }),
        Screen("sky-align-check-result", false, { s ->
            confirmed(s, 0.0); s.dismissAlignResult(); s.startCheckWithAnotherStar()
            val altair = catalog.find("Altair")!!
            Fixtures.pointAt(s, altair); s.pickStar(altair); s.confirmAlignment()
        }),
        Screen("sky-guidance-collapsed", false, { s -> guided(s) }),
        Screen("sky-guidance-expanded", false, { s -> guided(s) }, { onNode(button(I18n.t("More"))).tap() }),
        Screen("sky-guidance-close", false, { s -> guided(s); s.telescopeFocalMm = 300.0 }), // M57 is within three fields
        Screen("sky-guidance-on-target", false, { s -> guided(s); Fixtures.pointAt(s, catalog.find("M57")!!) }),
        Screen("sky-guidance-equatorial", false, { s -> guided(s); s.setup = s.setup.copy(mount = MountType.EQUATORIAL) }),
        Screen("sky-guidance-eyepiece-view", false, { s -> guided(s); s.matchEyepieceView = true; s.setup = s.setup.copy(viewRotationDeg = 90, viewMirrored = true) }),
        Screen("mounting-changed-dialog", true, { s -> confirmed(s, 0.0); s.updateSetup(s.setup.copy(placement = PhonePlacement.CAMERA_FORWARD)) }),
        // The first-run wizard, every step and both eyepiece branches.
        Screen("wizard-1-type", true, { it.setupDone = false }),
        Screen("wizard-2-mount", true, { it.setupDone = false }, { next(1) }),
        Screen("wizard-3-placement", true, { it.setupDone = false }, { next(2) }),
        Screen("wizard-4-edge", true, { it.setupDone = false }, { next(3) }),
        Screen("wizard-5-summary-tube", true, { it.setupDone = false }, { next(4) }),
        Screen("wizard-4-camera-summary", true, { it.setupDone = false; it.setup = it.setup.copy(placement = PhonePlacement.CAMERA_FORWARD) }, { next(3) }),
        Screen("wizard-4-angle", true, { it.setupDone = false; it.setup = it.setup.copy(placement = PhonePlacement.EYEPIECE) }, { next(3) }),
        Screen("wizard-4-angle-edge", true, { it.setupDone = false; it.setup = it.setup.copy(placement = PhonePlacement.EYEPIECE, eyepieceAngle = EyepieceAngle.RIGHT_ANGLE) }, { next(3) }),
        Screen("wizard-5-prism", true, { it.setupDone = false; it.setup = it.setup.copy(placement = PhonePlacement.EYEPIECE) }, { next(4) }),
        Screen("wizard-6-summary-eyepiece", true, { it.setupDone = false; it.setup = it.setup.copy(placement = PhonePlacement.EYEPIECE, eyepieceAngle = EyepieceAngle.RIGHT_ANGLE, erecting = Erecting.NO, type = TelescopeType.REFRACTOR) }, { next(5) }),
        // Telescope & orientation, and the check.
        Screen("sky-tab-telescope-eyepiece", true, { it.setup = it.setup.copy(placement = PhonePlacement.EYEPIECE, eyepieceAngle = EyepieceAngle.RIGHT_ANGLE) },
            { onNode(button(I18n.t("Sky"))).tap(); tab(I18n.t("Telescope & orientation")) }),
        Screen("orientation-menu", true, {}, { openCheck() }),
        Screen("orientation-view-question", true, {}, { openCheck(); onNode(button(I18n.t("Check the eyepiece view"))).tap() }),
        Screen("orientation-view-result", true, {}, {
            openCheck(); onNode(button(I18n.t("Check the eyepiece view"))).tap()
            onNode(button(I18n.t("Up"))).tap(); onNode(button(I18n.t("Right"))).tap()
        }),
        Screen("orientation-phone-no-stars", true, {}, { openCheck(); onNode(button(I18n.t("Check the phone position"))).tap() }),
        Screen("orientation-phone-result", true, { s ->
            for (n in listOf("Vega", "Altair")) { Fixtures.pointAt(s, catalog.find(n)!!); Fixtures.align(s, catalog.find(n)!!) }
        }, { openCheck(); onNode(button(I18n.t("Check the phone position"))).tap() }),
        Screen("sky-guiding-busy", false, { s ->
            Fixtures.pointAt(s, catalog.find("Vega")!!)
            Fixtures.align(s, catalog.find("Vega")!!)
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
        Screen("lists", true, {}, { onNode(button(I18n.t("Sky"))).tap(); tab(I18n.t("More")); onNode(button(I18n.t("My objects & lists"))).tap() }),
        Screen("help", true, {}, { onNode(button(I18n.t("Sky"))).tap(); tab(I18n.t("More")); onNode(button(I18n.t("Help"))).tap() }),
        // The new tabs of Sky & viewing, Find and the other new screens.
        *listOf("Deep-sky", "Markings", "Culture", "Landscape", "Telescope & orientation", "Place & time", "More").map { name ->
            Screen("sky-tab-$name", true, {}, { onNode(button(I18n.t("Sky"))).tap(); tab(I18n.t(name)) })
        }.toTypedArray(),
        Screen("find-position", true, {}, { onNode(button(I18n.t("Find"))).tap(); tab(I18n.t("Position")) }),
        Screen("find-lists", true, {}, { onNode(button(I18n.t("Find"))).tap(); tab(I18n.t("Lists")) }),
        Screen("object-info", true, { it.target = catalog.find("M57") }, {
            onNode(hasText("Ring Nebula") and hasClickAction()).tap()
            waitUntil(15_000) { onAllNodes(hasText(I18n.t("Tonight"))).fetchSemanticsNodes().isNotEmpty() }
        }),
        Screen("guide-suggestions", false, {}, { onNode(button(I18n.t("Ask"))).tap() }),
        Screen("sky-all-markings", false, { s ->
            s.showEquatorialGrid = true; s.showMeridian = true; s.showEcliptic = true; s.showBoundaries = true
            Fixtures.pointAt(s, catalog.find("Vega")!!); Fixtures.align(s, catalog.find("Vega")!!); s.target = catalog.find("M57")
        }),
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
