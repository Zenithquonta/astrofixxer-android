import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.ui.EventItem
import org.astrofixxer.ui.PlateSolveHost
import org.astrofixxer.ui.PlateSolveModel
import org.astrofixxer.ui.SkyScreen
import org.astrofixxer.ui.SkyState
import org.astrofixxer.ui.solarSystem
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.time.Instant
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.atan2

/** Shared test scene: New Delhi, 28 Sep 2026 21:30 IST, the bundled catalogue. */
object Fixtures {
    init { TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata")) }
    val catalog: Catalog by lazy { File("src/main/assets/sky_catalog.json.gz").inputStream().use { Catalog.load(it) } }
    val start: Long = Instant.parse("2026-09-28T16:00:00Z").toEpochMilli()
    val screens = File(System.getProperty("screens", "build/screens")).apply { mkdirs() }

    /** A state with the first-run setup already done, so the sky screen (not the wizard) is what the test sees. */
    fun state() = SkyState(start, 28.6139, 77.2090).apply { setupDone = true }

    /** Phone orientation that puts [obj] under the + (no alignment), for the default setup: top edge along the telescope. */
    fun pointAt(state: SkyState, obj: SkyObject) {
        val r = state.ray(obj)
        state.device = Pointing.rotationMatrix(-Math.toDegrees(atan2(r[0], r[1])), Math.toDegrees(asin(r[2])), 0.0)
    }

    /** A device rotation that sends the phone axis [axis] to the world direction [w], rolled by [rollDeg] about it. */
    fun deviceFor(axis: DoubleArray, w: DoubleArray, rollDeg: Double = 0.0): DoubleArray {
        val c = Pointing.cross(axis, w)
        val len = kotlin.math.sqrt(Pointing.dot(c, c))
        val minimal = if (len < 1e-12) Pointing.IDENTITY
        else Pointing.axisAngleMatrix(doubleArrayOf(c[0] / len, c[1] / len, c[2] / len), Pointing.angleBetweenDeg(axis, w) * Math.PI / 180)
        return Pointing.matMul(Pointing.axisAngleMatrix(w, Math.toRadians(rollDeg)), minimal)
    }

    /** Points the telescope of whatever setup [state] has at [obj] (the sensors, before any calibration). */
    fun pointTelescopeAt(state: SkyState, obj: SkyObject, rollDeg: Double = 0.0) {
        state.device = deviceFor(state.setup.axis().vector, state.ray(obj), rollDeg)
    }

    /** The whole alignment as a user does it (pick, centre, confirm, dismiss the result), on the state directly. */
    fun align(state: SkyState, star: SkyObject) {
        state.startAlign()
        check(state.pickStar(star)) { "${star.name} was refused as an alignment star" }
        check(state.confirmAlignment()) { "confirm failed for ${star.name}" }
        state.dismissAlignResult()
    }

    val sampleEvents = listOf(
        EventItem(2461318.5, "Full Moon"),
        EventItem(2461330.0, "%s meteor shower peak", listOf("Orionids"), "Up to %s meteors/hour under dark skies", listOf("20")),
        EventItem(2461340.0, "Moon covers %s", listOf("Antares"), "Disappears %s, reappears %s. Times ±5 min.", listOf("19:02", "20:11"), rare = true),
    )
}

/** Phone-sized test: [widthDp] × [heightDp] at 2.625x density, like a Pixel. */
@OptIn(ExperimentalTestApi::class)
fun phoneTest(widthDp: Int = 360, heightDp: Int = 780, block: ComposeUiTest.() -> Unit) {
    val density = 2.625f
    BackButton.handler = null
    runSkikoComposeUiTest(Size(widthDp * density, heightDp * density), Density(density), block = block)
}

/** Stands in for Android's system Back button: holds whatever SkyScreen registered with its backHandler. */
object BackButton {
    var handler: (() -> Unit)? = null
    /** Presses Back; false means the app would have closed. Call after waitForIdle() so the latest handler is registered. */
    fun press(): Boolean = handler?.let { it(); true } ?: false
}

/** SkyScreen wired like MainActivity does it; [host] is the phone's camera side for the plate-solve flow (null: no camera button anywhere). */
@Composable
fun AppScreen(state: SkyState, events: List<EventItem>? = Fixtures.sampleEvents, host: PlateSolveHost? = null, model: PlateSolveModel? = null,
    updater: org.astrofixxer.ui.Updater? = null) {
    val solveModel = model ?: remember { PlateSolveModel() }
    SkyScreen(state, Fixtures.catalog, solarSystem(state), events, onAsk = null,
        backHandler = { enabled, onBack -> SideEffect { BackButton.handler = if (enabled) onBack else null } },
        plateSolve = host, solveModel = solveModel, updater = updater)
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.screenshot(name: String) {
    waitForIdle()
    val img: ImageBitmap = onRoot().captureToImage()
    val png = Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
    File(Fixtures.screens, "$name.png").writeBytes(png)
}

val anyNode = SemanticsMatcher("any node") { true }

fun SemanticsNode.textLayouts(): List<TextLayoutResult> {
    if (!config.contains(SemanticsActions.GetTextLayoutResult)) return emptyList()
    val out = mutableListOf<TextLayoutResult>()
    config[SemanticsActions.GetTextLayoutResult].action?.invoke(out)
    return out
}

/** Presses the ‹ or › button on the settings row labelled [rowLabel], as a user would. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.clickStepper(rowLabel: String, arrow: String = "›") {
    val y = onNode(androidx.compose.ui.test.hasText(rowLabel)).fetchSemanticsNode().boundsInRoot.center.y
    val node = onAllNodes(androidx.compose.ui.test.hasText(arrow) and androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes()
        .minBy { kotlin.math.abs(it.boundsInRoot.center.y - y) }
    onNode(SemanticsMatcher("node ${node.id}") { it.id == node.id }).tap()
}

/**
 * Presses a node with a finger. On the desktop performClick() is a mouse click, and the mouse pointer left hovering
 * swallows the next touch on the sky canvas; phones have no hovering mouse, so tests use touch like a phone.
 */
@OptIn(ExperimentalTestApi::class)
fun androidx.compose.ui.test.SemanticsNodeInteraction.tap() = performTouchInput { click() }

/** Opens a tab by its label, scrolling the tab strip first if the tab is off-screen (as a person would swipe). */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.tab(label: String) {
    val node = onNode(androidx.compose.ui.test.hasText(label) and androidx.compose.ui.test.hasClickAction())
    runCatching { node.performScrollTo() } // fixed tab rows don't scroll
    node.tap()
}
