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
import org.astrofixxer.astro.GrayImage
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.SolveHints
import org.astrofixxer.astro.SolveResult
import org.astrofixxer.astro.StarSource
import org.astrofixxer.ui.CameraPermission
import org.astrofixxer.ui.CameraProblem
import org.astrofixxer.ui.EyepieceAngle as EyeAngle
import org.astrofixxer.ui.PhotoShot
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks every screen, in every available language (English only while Hindi is switched off), day and night, on 360 dp and 411 dp phones:
 * - every tappable control is at least 48 × 48 dp (Android accessibility guideline),
 * - no text is clipped or runs off the screen,
 * - no two pieces of text overlap on the sky screen,
 * - text colours meet contrast targets (WCAG AA 4.5:1 by day; night mode is deliberately dim red, target 3:1).
 * Findings are written to build/screens/audit.txt; the test fails if there are any.
 */
@OptIn(ExperimentalTestApi::class)
class AuditTest {
    private class Screen(
        val name: String, val overlayOpen: Boolean, val setup: (SkyState) -> Unit, val open: ComposeUiTest.() -> Unit = {},
        /** The fake phone for the plate-solve flow (null: the sky screen has no camera button). */
        val host: (() -> FakeHost)? = null,
        val solver: ((GrayImage, SolveHints, StarSource) -> SolveResult)? = null,
        val updater: org.astrofixxer.ui.UpdateStatus? = null,
    )

    private val catalog get() = Fixtures.catalog
    private fun button(label: String) = hasText(label) and hasClickAction()

    /** Opens Sky & viewing, Telescope & orientation, Check orientation. */
    private fun ComposeUiTest.openCheck() {
        onNode(button(I18n.t("Sky"))).tap()
        tab(I18n.t("Telescope & orientation"))
        onNode(button(I18n.t("Check orientation"))).performScrollTo().tap()
    }

    private fun ComposeUiTest.next(times: Int) = repeat(times) { onNode(button(I18n.t("Next"))).tap(); waitForIdle() }

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
        Screen("wizard-4-angle-newtonian", true, { it.setupDone = false; it.setup = it.setup.copy(type = TelescopeType.REFLECTOR, placement = PhonePlacement.EYEPIECE) }, { next(3) }),
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
        *solveScreens(),
        // Sky & viewing → More → App updates (preview and debug builds), every state.
        *updaterStates.map { (name, status) ->
            Screen("updater-$name", true, {}, { onNode(button(I18n.t("Sky"))).tap(); tab(I18n.t("More")); onNode(hasText(I18n.t("App updates"))).performScrollTo() }, updater = status)
        }.toTypedArray(),
    )

    // ---------------------------------------------------------------- the camera plate-solve flow, every step

    private val blank = GrayImage(Shots.W, Shots.H, FloatArray(Shots.W * Shots.H))
    private fun vegaObj() = catalog.find("Vega")!!
    private fun eye(s: SkyState, unsure: Boolean = false) {
        s.setup = s.setup.copy(placement = PhonePlacement.EYEPIECE, eyepieceAngle = if (unsure) EyeAngle.UNSURE else EyeAngle.STRAIGHT)
        s.telescopeFocalMm = 125.0; s.eyepieceFocalMm = 25.0
        Fixtures.pointTelescopeAt(s, vegaObj())
    }
    private fun forward(s: SkyState, calibrated: Boolean) {
        s.setup = s.setup.copy(placement = PhonePlacement.CAMERA_FORWARD)
        if (calibrated) s.cameraOffset = 0.62 to 0.37
        Fixtures.pointTelescopeAt(s, vegaObj())
    }
    private fun solvedAt(ra: Double, dec: Double, mirrored: Boolean = false) =
        SolveResult.Solved(ra, dec, 20.0, Shots.scale(13.0), mirrored, 31, 1.2, emptyList(), Shots.W, Shots.H)
    private fun fails(reason: SolveResult.Reason) = { _: GrayImage, _: SolveHints, _: StarSource -> SolveResult.Failed(reason, 12) as SolveResult }
    private fun host(configure: FakeHost.() -> Unit = {}): () -> FakeHost = { FakeHost().apply { camera += blank; camera += blank; configure() } }

    private fun ComposeUiTest.openSolve() {
        onNode(button(I18n.t("Sky"))).tap()
        tab(I18n.t("Telescope & orientation"))
        onNode(button(I18n.t("Solve with camera"))).performScrollTo().tap()
    }
    private fun ComposeUiTest.toTips() { openSolve(); next(1) }
    private fun ComposeUiTest.toLive() { openSolve(); next(2); waitForIdle() }
    private fun ComposeUiTest.toResult() {
        toLive()
        onNode(button(I18n.t("Take photo"))).tap()
        waitUntil(60_000) { onAllNodes(hasText(I18n.t("Solving…"))).fetchSemanticsNodes().isEmpty() }
        waitForIdle()
    }
    private fun ComposeUiTest.toPickStar() { openSolve(); onNode(button(I18n.t("Calibrate camera offset"))).tap() }

    private fun solveScreens(): Array<Screen> {
        val ra = vegaObj().ra; val dec = vegaObj().dec
        return arrayOf(
            Screen("sky-tab-telescope-camera", true, { eye(it) }, { onNode(button(I18n.t("Sky"))).tap(); tab(I18n.t("Telescope & orientation")) }, host()),
            Screen("sky-picking-star-photo", false, { it.startAlign() }, {}, host()),
            Screen("sky-guidance-expanded-camera", false, { s -> eye(s); Fixtures.align(s, vegaObj()); s.target = catalog.find("M57") }, { onNode(button(I18n.t("More"))).tap() }, host()),
            Screen("solve-tube", true, {}, { openSolve() }, host()),
            Screen("solve-arrangement-eyepiece", true, { eye(it) }, { openSolve() }, host()),
            Screen("solve-arrangement-eyepiece-asks", true, { eye(it, unsure = true); it.telescopeFocalMm = 1200.0; it.eyepieceFocalMm = 25.0 }, { openSolve() }, host()),
            Screen("solve-arrangement-eyepiece-right-angle", true, { eye(it, unsure = true); it.telescopeFocalMm = 1200.0; it.eyepieceFocalMm = 25.0 },
                { openSolve(); onNode(button(I18n.t("Right angle"))).tap() }, host()),
            Screen("solve-arrangement-forward", true, { forward(it, false) }, { openSolve() }, host()),
            Screen("solve-arrangement-forward-calibrated", true, { forward(it, true) }, { openSolve() }, host()),
            Screen("solve-pick-star", true, { forward(it, false) }, { toPickStar() }, host()),
            Screen("solve-tips", true, { eye(it) }, { toTips() }, host()),
            Screen("solve-tips-calibrating", true, { forward(it, false) }, { toPickStar(); onNode(hasText("Vega", substring = true) and hasClickAction()).tap() }, host()),
            Screen("solve-live-eyepiece", true, { eye(it) }, { toLive() }, host()),
            Screen("solve-live-forward-calibrated", true, { forward(it, true) }, { toLive() }, host()),
            Screen("solve-live-forward-uncalibrated", true, { forward(it, false) }, { toLive() }, host()),
            Screen("solve-live-calibrating", true, { forward(it, false) }, {
                toPickStar(); onNode(hasText("Vega", substring = true) and hasClickAction()).tap(); next(1); waitForIdle()
            }, host()),
            Screen("solve-live-no-exposure-choice", true, { eye(it) }, { toLive() }, host { exposures = emptyList(); hFovDeg = null }),
            Screen("solve-live-denied", true, { forward(it, false) }, { toLive() }, host { permission = CameraPermission.DENIED }),
            Screen("solve-live-blocked", true, { eye(it) }, { toLive() }, host { permission = CameraPermission.BLOCKED }),
            Screen("solve-live-asking", true, { eye(it) }, { toLive() }, host { permission = CameraPermission.UNKNOWN; answer = CameraPermission.UNKNOWN }),
            Screen("solve-live-no-camera", true, { eye(it) }, { toLive() }, host { hasCamera = false }),
            Screen("solve-live-camera-in-use", true, { eye(it) }, { toLive() }, host { problem = CameraProblem.IN_USE }),
            Screen("solve-solving", true, { eye(it) }, {
                toLive(); onNode(button(I18n.t("Take photo"))).tap()
                waitUntil(60_000) { onAllNodes(hasText(I18n.t("Solving…"))).fetchSemanticsNodes().isNotEmpty() }
            }, host(), solver = { _, _, stars -> while (true) { stars.cone(0.0, 0.0, 1.0, 1.0); Thread.sleep(10) }; @Suppress("UNREACHABLE_CODE") SolveResult.Failed(SolveResult.Reason.NO_MATCH, 0) }),
            Screen("solve-result-eyepiece", true, { eye(it) }, { toResult() }, host(), solver = { _, _, _ -> solvedAt(ra, dec) }),
            Screen("solve-result-mirrored", true, { eye(it) }, { toResult() }, host(), solver = { _, _, _ -> solvedAt(ra, dec, mirrored = true) }),
            Screen("solve-result-forward-uncalibrated", true, { forward(it, false) }, { toResult() }, host(), solver = { _, _, _ -> solvedAt(ra, dec) }),
            Screen("solve-result-forward-calibrated", true, { forward(it, true) }, { toResult() }, host(), solver = { _, _, _ -> solvedAt(ra, dec) }),
            Screen("solve-result-gallery", true, { forward(it, true); }, {
                openSolve(); next(2); waitForIdle(); onNode(button(I18n.t("Use a photo from the gallery"))).tap()
                waitUntil(60_000) { onAllNodes(hasText(I18n.t("Solving…"))).fetchSemanticsNodes().isEmpty() }; waitForIdle()
            }, host { gallery = org.astrofixxer.ui.GalleryResult.Picked(blank) }, solver = { _, _, _ -> solvedAt(ra, dec) }),
            Screen("solve-result-calibrating", true, { forward(it, false) }, {
                toPickStar(); onNode(hasText("Vega", substring = true) and hasClickAction()).tap(); next(1); waitForIdle()
                onNode(button(I18n.t("Take photo"))).tap()
                waitUntil(60_000) { onAllNodes(hasText(I18n.t("Solving…"))).fetchSemanticsNodes().isEmpty() }; waitForIdle()
            }, host(), solver = { _, _, _ -> solvedAt(ra, dec) }),
            Screen("solve-result-calibrating-star-off-photo", true, { forward(it, false) }, {
                toPickStar(); onNode(hasText("Vega", substring = true) and hasClickAction()).tap(); next(1); waitForIdle()
                onNode(button(I18n.t("Take photo"))).tap()
                waitUntil(60_000) { onAllNodes(hasText(I18n.t("Solving…"))).fetchSemanticsNodes().isEmpty() }; waitForIdle()
            }, host(), solver = { _, _, _ -> solvedAt((ra + 120) % 360, -dec) }),
            *SolveResult.Reason.values().map { r ->
                Screen("solve-failed-$r", true, { eye(it) }, { toResult() }, host(), solver = fails(r))
            }.toTypedArray(),
            Screen("solve-failed-no-hint", true, { eye(it); it.telescopeFocalMm = 1200.0; it.eyepieceFocalMm = 25.0; it.hasCompass = false }, { toResult() }, host(), solver = fails(SolveResult.Reason.NO_MATCH)),
            Screen("solve-failed-solver-broke", true, { eye(it) }, { toResult() }, host(), solver = { _, _, _ -> throw IllegalStateException("boom") }),
            // The result card on the sky screen after Apply.
            Screen("sky-aligned-from-photo", false, { s ->
                eye(s)
                val shot = PhotoShot(blank, true, 65.0, s.device.copyOf(), s.timeMillis)
                s.applyPhotoAlignment(solvedAt(ra, dec), shot)
            }, {}, host()),
        )
    }

    /**
     * Runs one screen variant and fails, naming it, when it throws or takes longer than [limitMs]. Every wait in the
     * navigation steps (waitForIdle, waitUntil, tap) happens inside, so none can hang the whole run without saying where:
     * after the limit the test thread is interrupted with its stack printed, and if it still does not return the JVM stops.
     */
    private fun <T> withinTime(where: String, limitMs: Long = 600_000, body: () -> T): T {
        val test = Thread.currentThread()
        val finished = AtomicBoolean(false)
        val dog = Thread {
            try { Thread.sleep(limitMs) } catch (e: InterruptedException) { return@Thread }
            if (finished.get()) return@Thread
            System.err.println("AUDIT HANG: $where did not finish in ${limitMs / 1000} s. Test thread:\n" + test.stackTrace.joinToString("\n") { "    at $it" })
            test.interrupt()
            try { Thread.sleep(30_000) } catch (e: InterruptedException) { return@Thread }
            if (!finished.get()) {
                System.err.println("AUDIT HANG: $where ignored the interrupt; stopping the test JVM.")
                System.err.flush()
                Runtime.getRuntime().halt(1)
            }
        }.apply { isDaemon = true; start() }
        try {
            return body()
        } catch (e: Throwable) {
            throw AssertionError("Audit screen $where failed: ${e.message ?: e}", e)
        } finally {
            finished.set(true); dog.interrupt(); Thread.interrupted()
        }
    }

    @Test fun everyScreenPassesTheAudit() {
        val findings = mutableListOf<String>()
        var checked = 0
        for (width in listOf(360, 411)) for (lang in I18n.languages.map { it.first }) for (night in listOf(false, true)) for (screen in screens) {
            I18n.language = lang
            val where = "${screen.name} [$width dp, $lang, ${if (night) "night" else "day"}]"
            try {
                withinTime(where) {
                    phoneTest(widthDp = width) {
                        val state = Fixtures.state().apply { this.night = night; screen.setup(this) }
                        setContent { AppScreen(state, host = screen.host?.invoke(), model = screen.solver?.let { modelWith(it) }, updater = screen.updater?.let { FakeUpdater(it) }) }
                        screen.open(this)
                        waitForIdle()
                        findings += audit(this, where, width, checkOverlap = !screen.overlayOpen)
                        if (width == 360) screenshot("audit-${screen.name}-$lang-${if (night) "night" else "day"}")
                        checked++
                    }
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
