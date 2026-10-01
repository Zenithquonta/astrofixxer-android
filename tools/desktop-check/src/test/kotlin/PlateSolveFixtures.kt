import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import org.astrofixxer.astro.CatalogStars
import org.astrofixxer.astro.GrayImage
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.RenderSpec
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.astro.SolveHints
import org.astrofixxer.astro.SolveResult
import org.astrofixxer.astro.SolverStars
import org.astrofixxer.astro.StarSource
import org.astrofixxer.astro.SyntheticSky
import org.astrofixxer.astro.Truth
import org.astrofixxer.ui.CameraException
import org.astrofixxer.ui.CameraHandle
import org.astrofixxer.ui.CameraInfo
import org.astrofixxer.ui.CameraPermission
import org.astrofixxer.ui.CameraProblem
import org.astrofixxer.ui.GalleryResult
import org.astrofixxer.ui.PlateSolveHost
import org.astrofixxer.ui.PlateSolveModel
import org.astrofixxer.ui.PhonePlacement
import org.astrofixxer.ui.SkyState
import java.io.File
import java.util.Random
import kotlin.math.cos

/**
 * A phone with no camera hardware: hands the flow pictures the test queued up (synthetic star fields), and records what
 * the flow asked of it. The live view is a plain box with the picture's aspect ratio, tagged "camera-frame", so tests can
 * measure where the flow draws the + on it.
 */
class FakeHost : PlateSolveHost {
    override var hasCamera: Boolean = true
    override var permission: CameraPermission by mutableStateOf(CameraPermission.GRANTED)
    /** What Android answers when the flow asks for the permission. */
    var answer: CameraPermission = CameraPermission.GRANTED
    var permissionRequests = 0
    var settingsOpened = 0
    var liveViewsOpened = 0
    var liveViewsClosed = 0
    var hFovDeg: Double? = 65.0
    var exposures: List<Double> = listOf(1.0, 2.0, 4.0)
    /** Set to make the live view report this problem instead of opening. */
    var problem: CameraProblem? = null
    var captureFails: CameraProblem? = null
    val takenWith = mutableListOf<Double?>()
    /** Pictures the camera will deliver, in order. */
    val camera = ArrayDeque<GrayImage>()
    /** What the photo picker answers. */
    var gallery: GalleryResult = GalleryResult.Cancelled
    var galleryAsked = 0
    var deep: SolverStars? = null
    var deepAsked = 0

    override fun requestPermission() { permissionRequests++; permission = answer }
    override fun openAppSettings() { settingsOpened++ }
    override fun pickFromGallery(onResult: (GalleryResult) -> Unit) { galleryAsked++; onResult(gallery) }
    override suspend fun deepStars(): SolverStars? { deepAsked++; return deep }

    private val handle = object : CameraHandle {
        override val info get() = CameraInfo(hFovDeg, exposures)
        override suspend fun capture(exposureSec: Double?): GrayImage {
            takenWith += exposureSec
            captureFails?.let { throw CameraException(it) }
            return camera.removeFirst()
        }
    }

    @Composable
    override fun LiveView(modifier: Modifier, overlay: @Composable () -> Unit, onReady: (CameraHandle) -> Unit, onProblem: (CameraProblem) -> Unit) {
        LaunchedEffect(Unit) { liveViewsOpened++; problem?.let(onProblem) ?: onReady(handle) }
        DisposableEffect(Unit) { onDispose { liveViewsClosed++ } }
        // No pointer input on the view, like the real one.
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(4f / 3f).testTag("camera-frame").background(Color(0xFF101820))) { overlay() }
        }
    }
}

/** Synthetic pictures for the flow tests, built from the app's own bright catalogue. */
object Shots {
    val bright: StarSource by lazy { CatalogStars(Fixtures.catalog) }
    val deepStars: SolverStars? by lazy {
        File("src/main/assets/solver_stars.bin").takeIf { it.exists() }?.inputStream()?.use { SolverStars.load(it) }
    }

    const val W = 1200
    const val H = 900

    /** TAN scale of a picture [W] px wide that spans [widthDeg]. */
    fun scale(widthDeg: Double) = org.astrofixxer.astro.PlateSolveHints.scaleDegPerPx(widthDeg, W)

    fun truth(ra: Double, dec: Double, widthDeg: Double, roll: Double = 30.0, mirrored: Boolean = false) =
        Truth(ra, dec, roll, scale(widthDeg), mirrored, W, H)

    fun render(t: Truth, seed: Long, limitMag: Double = 5.0, source: StarSource = bright) =
        SyntheticSky.render(t, RenderSpec(limitMag = limitMag, psfSigma = 1.5), source, seed)

    /** The sky position at picture pixel ([x], [y]) of [t] (Newton iteration on the independent forward projection). */
    fun skyAt(t: Truth, x: Double, y: Double): Pair<Double, Double> {
        var ra = t.raDeg; var dec = t.decDeg
        repeat(12) {
            val p = t.toPixel(ra, dec)!!
            val e = 1e-4
            val pr = t.toPixel(ra + e / cos(Math.toRadians(dec)), dec)!!
            val pd = t.toPixel(ra, dec + e)!!
            val j00 = (pr[0] - p[0]) / e; val j10 = (pr[1] - p[1]) / e
            val j01 = (pd[0] - p[0]) / e; val j11 = (pd[1] - p[1]) / e
            val det = j00 * j11 - j01 * j10
            val ex = x - p[0]; val ey = y - p[1]
            ra += ((ex * j11 - ey * j01) / det) / cos(Math.toRadians(dec)); dec += (j00 * ey - j10 * ex) / det
        }
        return ra.mod(360.0) to dec
    }

    /** Pure sensor noise: no star at all. */
    fun noise(seed: Long): GrayImage {
        val rnd = Random(seed)
        return GrayImage(W, H, FloatArray(W * H) { (0.2 + 0.03 * rnd.nextGaussian()).coerceIn(0.0, 1.0).toFloat() })
    }

    /** Sky, noise and hot pixels only, made by the same renderer as the star fields. */
    fun blankSky(seed: Long) = SyntheticSky.render(truth(280.0, 30.0, 60.0), RenderSpec(limitMag = 5.0, drawStars = false), bright, seed)
}

/** Everything the flow may not change when it does not solve, or the person does not apply it. */
fun SkyState.fingerprint(): List<Any?> = listOf(
    align, alignMatrix?.toList(), alignedAtMillis, alignStar?.name, alignStarName, alignResult, alignSamples.size, mode, freeAzDeg, freeAltDeg,
    adjustAzDeg, adjustAltDeg, cameraOffset, settings(), centerStar?.name, checkingSecondStar,
)

/** A solver that must not be called; counts calls. */
class CountingSolver(private val answer: (Int) -> SolveResult? = { null }) : (GrayImage, SolveHints, StarSource) -> SolveResult {
    var calls = 0
    override fun invoke(img: GrayImage, hints: SolveHints, stars: StarSource): SolveResult {
        calls++
        return answer(calls) ?: SolveResult.Failed(SolveResult.Reason.NO_MATCH, 0)
    }
}

/** A model whose solver is [solver]. */
fun modelWith(solver: (GrayImage, SolveHints, StarSource) -> SolveResult) = PlateSolveModel(solver)

/** A state with the phone on the eyepiece (straight in), aimed at [star] by the sensors but off by [compassErrorDeg] in azimuth. */
fun eyepieceState(star: SkyObject, compassErrorDeg: Double = 20.0, telescopeMm: Double = 125.0, eyepieceMm: Double = 25.0): SkyState =
    Fixtures.state().also { s ->
        s.setup = s.setup.copy(placement = PhonePlacement.EYEPIECE, eyepieceAngle = org.astrofixxer.ui.EyepieceAngle.STRAIGHT)
        s.telescopeFocalMm = telescopeMm; s.eyepieceFocalMm = eyepieceMm
        Fixtures.pointTelescopeAt(s, star)
        s.device = Pointing.matMul(Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), Math.toRadians(compassErrorDeg)), s.device)
    }
