package org.astrofixxer.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.astrofixxer.astro.SolverStars
import org.astrofixxer.ui.CameraPermission
import org.astrofixxer.ui.CameraProblem
import org.astrofixxer.ui.CameraHandle
import org.astrofixxer.ui.GalleryResult
import org.astrofixxer.ui.PlateSolveHost

/**
 * The Android side of the camera plate-solve flow: Camera2 for the live view and stills, the system photo picker for gallery
 * pictures, the camera permission (asked for only when the live view opens) and the deep star list. Pictures are only ever
 * processed in memory: nothing is saved to storage and nothing uses the network.
 */
class AndroidPlateSolveHost internal constructor(private val activity: ComponentActivity) : PlateSolveHost {
    // ponytail: portrait only (the manifest fixes it), so the display rotation is read once.
    @Suppress("DEPRECATION")
    private val displayRotationDeg = (activity.windowManager.defaultDisplay.rotation) * 90
    private val plan: CameraPlan? by lazy { CameraPlan.create(activity, displayRotationDeg) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val hasCamera: Boolean by lazy { activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) && plan != null }

    override var permission: CameraPermission by mutableStateOf(current())
        private set

    /** Bumped when the app comes back after a pause, so a live view that was closed opens a fresh camera. */
    private var generation by mutableIntStateOf(0)
    private var activeSession: Camera2Session? = null
    private var permissionLauncher: (() -> Unit)? = null
    private var pickLauncher: (() -> Unit)? = null
    private var galleryCallback: ((GalleryResult) -> Unit)? = null

    private fun current() =
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) CameraPermission.GRANTED else CameraPermission.UNKNOWN

    internal fun bind(askPermission: () -> Unit, pick: () -> Unit) { permissionLauncher = askPermission; pickLauncher = pick }

    override fun requestPermission() { permissionLauncher?.invoke() }

    internal fun onPermissionResult(granted: Boolean) {
        permission = when {
            granted -> CameraPermission.GRANTED
            // Refused: asking again shows the dialog only while Android still offers it; after "don't ask again" only the settings help.
            activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> CameraPermission.DENIED
            else -> CameraPermission.BLOCKED
        }
    }

    override fun openAppSettings() {
        try {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // no settings app: nothing to do
        }
    }

    override fun pickFromGallery(onResult: (GalleryResult) -> Unit) { galleryCallback = onResult; pickLauncher?.invoke() }

    internal fun onPicked(uri: Uri?) {
        val cb = galleryCallback ?: return
        galleryCallback = null
        if (uri == null) { cb(GalleryResult.Cancelled); return }
        scope.launch {
            val image = withContext(Dispatchers.IO) { GalleryDecoder.decode(activity.contentResolver, uri) }
            cb(if (image == null) GalleryResult.Unreadable else GalleryResult.Picked(image))
        }
    }

    private val deepLock = Mutex()
    private var deepLoaded = false
    private var deep: SolverStars? = null

    override suspend fun deepStars(): SolverStars? = deepLock.withLock {
        if (!deepLoaded) {
            deep = withContext(Dispatchers.IO) { runCatching { activity.assets.open("solver_stars.bin").use { SolverStars.load(it) } }.getOrNull() }
            deepLoaded = true
        }
        deep
    }

    @Composable
    override fun LiveView(modifier: Modifier, overlay: @Composable () -> Unit, onReady: (CameraHandle) -> Unit, onProblem: (CameraProblem) -> Unit) {
        val p = plan
        if (p == null) {
            LaunchedEffect(Unit) { onProblem(CameraProblem.NO_CAMERA) }
            return
        }
        key(generation) {
            val holder = remember { arrayOfNulls<Camera2Session>(1) }
            Box(modifier, contentAlignment = Alignment.Center) {
                // The whole picture, letterboxed; the overlay is drawn on exactly this rectangle. The view takes no touches.
                Box(Modifier.aspectRatio(p.uprightAspect)) {
                    AndroidView(factory = { ctx ->
                        TextureView(ctx).also { view ->
                            val s = Camera2Session(activity, p, view, onReady, onProblem)
                            holder[0] = s
                            activeSession = s
                            s.attach()
                        }
                    }, modifier = Modifier.fillMaxSize())
                    overlay()
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    holder[0]?.close()
                    if (activeSession === holder[0]) activeSession = null
                }
            }
        }
    }

    /** The app left the screen: the camera must not stay open. */
    fun pause() { activeSession?.close(); activeSession = null }

    /** The app is back: re-read the permission (it may have been allowed in the settings) and let a live view open a fresh camera. */
    fun resume() {
        if (permission != CameraPermission.GRANTED && current() == CameraPermission.GRANTED) permission = CameraPermission.GRANTED
        generation++
    }

    fun close() { pause(); scope.cancel() }
}

/** The host for [activity], with its permission and photo-picker launchers. Call from composition. */
@Composable
fun rememberPlateSolveHost(activity: ComponentActivity): AndroidPlateSolveHost {
    val host = remember { AndroidPlateSolveHost(activity) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> host.onPermissionResult(granted) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> host.onPicked(uri) }
    host.bind(
        askPermission = { permission.launch(Manifest.permission.CAMERA) },
        pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
    return host
}
