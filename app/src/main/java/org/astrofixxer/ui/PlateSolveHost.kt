package org.astrofixxer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.astrofixxer.astro.GrayImage
import org.astrofixxer.astro.SolverStars

/*
 * What the camera plate-solve flow needs from the phone. The flow itself (PlateSolveFlow.kt) has no Android imports; the
 * Android app implements this in org.astrofixxer.camera (Camera2, the photo picker, the permission), and the desktop tests
 * implement it with a fake camera that returns synthetic star fields. Pictures never leave the phone: nothing here saves
 * or sends an image.
 */

/** The camera permission as the flow needs to tell it. */
enum class CameraPermission {
    /** Not asked yet. */
    UNKNOWN,
    GRANTED,
    /** Refused, but Android will show the dialog again if asked. */
    DENIED,
    /** Refused for good ("don't ask again"): only the phone's settings can change it. */
    BLOCKED,
}

/** Why the live camera is not working. */
enum class CameraProblem {
    /** The phone has no usable back camera. */
    NO_CAMERA,
    /** Another app is using the camera. */
    IN_USE,
    /** The camera was closed or unplugged, for example when the app left the screen. */
    DISCONNECTED,
    /** Anything else: the camera or a capture failed. */
    FAILED,
}

/** A camera problem, thrown by [CameraHandle.capture]. */
class CameraException(val problem: CameraProblem) : Exception(problem.name)

/**
 * What an open camera reports. [hFovDeg] is the horizontal field of view of the upright pictures it delivers (from the lens
 * focal length and sensor size, with the picture rotation taken into account), or null when the camera did not report it.
 * [exposuresSec] are the manual exposure times the camera can do (the flow offers Auto plus these); empty when only
 * automatic exposure is possible.
 */
class CameraInfo(val hFovDeg: Double?, val exposuresSec: List<Double>)

/** An open camera. */
interface CameraHandle {
    val info: CameraInfo

    /**
     * Takes one still picture, [exposureSec] seconds long, or with automatic exposure when null, and returns its brightness
     * as an upright [GrayImage] (at most 1600 pixels on the long side, values 0 to 1). Throws [CameraException].
     * The picture is not saved anywhere.
     */
    suspend fun capture(exposureSec: Double?): GrayImage
}

/** The answer of the photo picker. */
sealed class GalleryResult {
    /** A picture, already turned upright and converted to brightness. */
    class Picked(val image: GrayImage) : GalleryResult()
    object Cancelled : GalleryResult()
    /** The picture could not be read. */
    object Unreadable : GalleryResult()
}

interface PlateSolveHost {
    /** False when the phone has no camera at all: only gallery pictures can be solved. */
    val hasCamera: Boolean

    /** The camera permission now. A Compose state read: the flow updates when it changes. */
    val permission: CameraPermission

    /** Shows Android's permission dialog. The answer arrives as a change of [permission]. Only called when the live view opens. */
    fun requestPermission()

    /** Opens the app's page in the phone's settings, where the camera permission can be allowed. */
    fun openAppSettings()

    /**
     * The live camera view. The camera opens when this enters the composition and is closed when it leaves (and when the
     * app pauses). The view shows the whole picture, letterboxed inside [modifier]'s box; [overlay] is drawn on top of
     * exactly the picture's rectangle, so positions given as fractions of the picture line up. The view must not react to
     * touches. [onReady] hands over the open camera (again after the app resumes); [onProblem] reports failures.
     */
    @Composable
    fun LiveView(modifier: Modifier, overlay: @Composable () -> Unit, onReady: (CameraHandle) -> Unit, onProblem: (CameraProblem) -> Unit)

    /** Asks the photo picker for a picture; [onResult] is called once, on the main thread. */
    fun pickFromGallery(onResult: (GalleryResult) -> Unit)

    /** The deep star list (`solver_stars.bin`), loaded once off the UI thread and kept; null if it is missing or damaged. */
    suspend fun deepStars(): SolverStars?
}

/**
 * One picture on its way to the solver, with what the phone knew when it was taken. [device] (the phone's rotation from
 * the sensors) and [timeMillis] are stored at the moment the picture was taken, because an alignment from it must use
 * them and not whatever the phone does afterwards. [fromCamera] is false for gallery pictures, whose moment is unknown.
 * [hFovDeg] is the camera's reported field of view (null when unknown or from the gallery). [movedDeg] is how far the
 * telescope axis turned while the picture was taken.
 */
class PhotoShot(
    val image: GrayImage, val fromCamera: Boolean, val hFovDeg: Double?,
    val device: DoubleArray, val timeMillis: Long, val movedDeg: Double = 0.0,
)
