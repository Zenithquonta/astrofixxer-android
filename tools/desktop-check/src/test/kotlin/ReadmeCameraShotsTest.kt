import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import org.astrofixxer.astro.GrayImage
import org.astrofixxer.astro.SolverStars
import org.astrofixxer.ui.CameraHandle
import org.astrofixxer.ui.CameraInfo
import org.astrofixxer.ui.CameraPermission
import org.astrofixxer.ui.CameraProblem
import org.astrofixxer.ui.GalleryResult
import org.astrofixxer.ui.PhonePlacement
import org.astrofixxer.ui.PlateSolveHost
import org.astrofixxer.ui.PlateSolveModel
import org.astrofixxer.ui.SkyState
import org.astrofixxer.ui.SolveOutcome
import org.astrofixxer.ui.SolveStep
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The README's camera plate-solve screens. NOT COMPILED YET: it needs the camera flow (PlateSolveHost, PlateSolveModel,
 * PlateSolveFixtures.kt), which is in branch feature/tsap-w4. After that branch is merged, move this file to src/test/kotlin
 * and run: ./gradlew test --offline --tests ReadmeCameraShotsTest   then   python3 tools/repo-art/readme_screens.py
 *
 * It uses the flow's real screens and the real solver. The phone camera is replaced by [StarFieldHost], which hands over a
 * synthetic star field made from the app's own catalogue and also draws that field in the live view, so the + is shown on
 * stars. It is a stand-in: no real camera, permission dialog or sky photo is involved.
 */
@OptIn(ExperimentalTestApi::class)
class ReadmeCameraShotsTest {
    private val catalog get() = Fixtures.catalog
    private val vega get() = catalog.find("Vega")!!
    private fun button(label: String) = hasText(label) and hasClickAction()
    private fun androidx.compose.ui.test.ComposeUiTest.next() = onNode(button("Next")).tap()
    @Before fun english() { org.astrofixxer.ui.I18n.language = "en" }

    /** A phone with no camera hardware whose live view shows the picture it will hand over next. */
    class StarFieldHost : PlateSolveHost {
        override var hasCamera: Boolean = true
        override var permission: CameraPermission by mutableStateOf(CameraPermission.GRANTED)
        var hFovDeg: Double? = 65.0
        val camera = ArrayDeque<GrayImage>()
        override fun requestPermission() {}
        override fun openAppSettings() {}
        override fun pickFromGallery(onResult: (GalleryResult) -> Unit) { onResult(GalleryResult.Cancelled) }
        override suspend fun deepStars(): SolverStars? = Shots.deepStars

        private val handle = object : CameraHandle {
            override val info get() = CameraInfo(hFovDeg, listOf(1.0, 2.0, 4.0))
            override suspend fun capture(exposureSec: Double?): GrayImage = camera.removeFirst()
        }

        /** The picture as a screen would show it: each 3 x 3 block keeps its brightest pixel, so point-like stars stay visible. */
        private fun toBitmap(g: GrayImage): ImageBitmap {
            val k = 3
            val w = g.width / k; val h = g.height / k
            val bytes = ByteArray(w * h * 4)
            for (y in 0 until h) for (x in 0 until w) {
                var m = 0f
                for (dy in 0 until k) for (dx in 0 until k) m = maxOf(m, g.pixels[(y * k + dy) * g.width + x * k + dx])
                val v = (Math.pow(((m - 0.2f) / 0.25f).coerceIn(0f, 1f).toDouble(), 0.6) * 255).toInt().toByte()
                val i = (y * w + x) * 4
                bytes[i] = v; bytes[i + 1] = v; bytes[i + 2] = v; bytes[i + 3] = 255.toByte()
            }
            val bmp = Bitmap()
            bmp.installPixels(ImageInfo.makeN32(w, h, ColorAlphaType.OPAQUE), bytes, w * 4)
            return org.jetbrains.skia.Image.makeFromBitmap(bmp).toComposeImageBitmap()
        }

        @Composable
        override fun LiveView(modifier: Modifier, overlay: @Composable () -> Unit, onReady: (CameraHandle) -> Unit, onProblem: (CameraProblem) -> Unit) {
            LaunchedEffect(Unit) { onReady(handle) }
            DisposableEffect(Unit) { onDispose { } }
            val shown = camera.firstOrNull()
            Box(modifier, contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(4f / 3f).testTag("camera-frame").background(Color(0xFF101820))) {
                    if (shown != null) Image(toBitmap(shown), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    overlay()
                }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.waitForResult(model: PlateSolveModel) = waitUntil(180_000) { model.step == SolveStep.RESULT }

    /** Phone on the eyepiece (straight in), 25 mm eyepiece on a 125 mm telescope: a 65 degree camera sees 13 degrees of sky. */
    @Test fun eyepieceArrangementLiveResultAndAlignedCard() = phoneTest {
        val state = eyepieceState(vega, compassErrorDeg = 20.0)
        val truth = Shots.truth(vega.ra, vega.dec, widthDeg = 13.0, roll = 47.0)
        val host = StarFieldHost().apply { camera += Shots.render(truth, 11, limitMag = 6.5) }
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }
        onNode(button("Align")).tap()
        onNode(button("Align with a photo")).tap()
        onNode(hasText("Centre the bright eyepiece circle in the camera view and focus on stars")).assertExists()
        screenshot("readme-solve-1-arrangement")
        next(); next(); waitForIdle()
        onNode(hasText("Move the telescope until the star reaches the +")).assertExists()
        screenshot("readme-solve-2-live-eyepiece")
        onNode(button("Take photo")).tap()
        waitForResult(model); waitForIdle()
        onNode(hasText("Solved")).assertExists()
        onNode(hasText("Apply to alignment")).assertExists()
        screenshot("readme-solve-3-result")
        onNode(hasText("Apply to alignment")).tap()
        waitForIdle()
        onNode(hasText("Aligned from a photo. Correction ", substring = true)).assertExists()
        screenshot("readme-solve-4-aligned")
    }

    /** Camera beside the tube: the arrangement before the offset is calibrated, then the live view with the + at the calibrated spot. */
    @Test fun cameraBesideTheTube() = phoneTest {
        val state = Fixtures.state().also { it.setup = it.setup.copy(placement = PhonePlacement.CAMERA_FORWARD) }
        val truth = Shots.truth(285.0, 30.0, widthDeg = 60.0, roll = 140.0)
        val host = StarFieldHost().apply { hFovDeg = 60.0; camera += Shots.render(truth, 23) }
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }
        onNode(button("Sky")).tap(); tab("Telescope & orientation")
        onNode(button("Solve with camera")).performScrollTo().tap()
        onNode(hasText("Camera offset: not calibrated")).assertExists()
        screenshot("readme-solve-camera-1-arrangement")
        onNode(button("Cancel")).tap()
        state.cameraOffset = 0.62 to 0.37 // what "Calibrate camera offset" saves; the calibration flow is in PlateSolveFlowTest
        onNode(button("Sky")).tap(); tab("Telescope & orientation")
        onNode(button("Solve with camera")).performScrollTo().tap()
        onNode(hasText("Camera offset: calibrated", substring = true)).assertExists()
        next(); next(); waitForIdle()
        onNode(hasText("Telescope")).assertExists()
        screenshot("readme-solve-camera-2-live")
    }

    /** One failure, produced by the real solver on a picture with no stars. */
    @Test fun aFailureMessage() = phoneTest {
        val state = eyepieceState(vega)
        val host = StarFieldHost().apply { camera += Shots.noise(3) }
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }
        onNode(button("Sky")).tap(); tab("Telescope & orientation")
        onNode(button("Solve with camera")).performScrollTo().tap()
        next(); next(); waitForIdle()
        onNode(button("Take photo")).tap()
        waitForResult(model); waitForIdle()
        onNode(hasText("This photo could not be solved")).assertExists()
        onNode(hasText("Nothing was changed: the alignment and the map are as they were.")).assertExists()
        screenshot("readme-solve-5-failed")
    }
}
