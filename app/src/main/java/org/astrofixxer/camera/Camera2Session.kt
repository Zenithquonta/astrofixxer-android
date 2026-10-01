package org.astrofixxer.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.astrofixxer.astro.GrayImage
import org.astrofixxer.astro.Luminance
import org.astrofixxer.ui.CameraException
import org.astrofixxer.ui.CameraHandle
import org.astrofixxer.ui.CameraInfo
import org.astrofixxer.ui.CameraProblem
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One open back camera on Camera2: a live preview in [view] and stills as YUV_420_888, of which the Y plane is the picture.
 * Nothing is written to storage. Opens when [view]'s surface is available and closes with [close] (idempotent), which the
 * host calls when the live view leaves the screen or the app pauses. [onReady] and [onProblem] are called on the main thread.
 * Camera callbacks run on their own thread.
 *
 * ponytail: a still with automatic exposure relies on the running preview's exposure; no separate metering sequence.
 * Not run on a device in this repository's checks: real Camera2 behaviour is for the field test.
 */
internal class Camera2Session(
    private val context: Context,
    private val plan: CameraPlan,
    private val view: TextureView,
    private val onReady: (CameraHandle) -> Unit,
    private val onProblem: (CameraProblem) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("astrofixxer-camera").also { it.start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()
    private var closed = false
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    @Volatile private var pending: ((GrayImage?) -> Unit)? = null
    private val oneCapture = Mutex()

    /** Starts as soon as the view has a surface (now, or when it arrives). */
    fun attach() {
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = open(surface)
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { close(); return true }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
        if (view.isAvailable) view.surfaceTexture?.let { open(it) }
    }

    private fun problem(p: CameraProblem) {
        if (isClosed()) return
        main.post { if (!isClosed()) onProblem(p) }
    }

    private fun isClosed() = synchronized(lock) { closed }

    @SuppressLint("MissingPermission") // the flow only shows the live view once the permission is granted
    private fun open(texture: SurfaceTexture) {
        if (isClosed()) return
        try {
            texture.setDefaultBufferSize(plan.previewSize.width, plan.previewSize.height)
            previewSurface = Surface(texture)
            reader = ImageReader.newInstance(plan.stillSize.width, plan.stillSize.height, ImageFormat.YUV_420_888, 2).also { r ->
                r.setOnImageAvailableListener({ ir ->
                    val img = try { ir.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
                    val done = pending
                    try {
                        if (done == null) return@setOnImageAvailableListener // not asked for: dropped
                        pending = null
                        val y = img.planes[0]
                        done(Luminance.fromYPlane(y.buffer, img.width, img.height, y.rowStride, y.pixelStride, plan.quarterTurns))
                    } catch (e: Exception) {
                        pending = null
                        done?.invoke(null)
                    } finally {
                        img.close()
                    }
                }, handler)
            }
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.openCamera(plan.cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (isClosed()) { camera.close(); return }
                    device = camera
                    startSession(camera)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); problem(CameraProblem.DISCONNECTED) }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    problem(if (error == ERROR_CAMERA_IN_USE) CameraProblem.IN_USE else CameraProblem.FAILED)
                }
            }, handler)
        } catch (e: CameraAccessException) {
            problem(CameraProblem.FAILED)
        } catch (e: SecurityException) {
            problem(CameraProblem.FAILED)
        } catch (e: Exception) {
            problem(CameraProblem.FAILED)
        }
    }

    @Suppress("DEPRECATION") // the SessionConfiguration form needs API 28; minSdk is 26
    private fun startSession(camera: CameraDevice) {
        val surfaces = listOfNotNull(previewSurface, reader?.surface)
        try {
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (isClosed()) { s.close(); return }
                    session = s
                    try {
                        val preview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface!!)
                            focusAtInfinity(this)
                        }
                        s.setRepeatingRequest(preview.build(), null, handler)
                        main.post { if (!isClosed()) onReady(handle) }
                    } catch (e: Exception) {
                        problem(CameraProblem.FAILED)
                    }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { problem(CameraProblem.FAILED) }
            }, handler)
        } catch (e: Exception) {
            problem(CameraProblem.FAILED)
        }
    }

    /** Stars are far away: focus at infinity when the lens can be focused (the eyepiece view is focused with the telescope). */
    private fun focusAtInfinity(b: CaptureRequest.Builder) {
        if (!plan.canFocus) return
        b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        b.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
    }

    private val handle = object : CameraHandle {
        override val info = CameraInfo(plan.hFovDeg, plan.manualExposuresSec)

        override suspend fun capture(exposureSec: Double?): GrayImage {
            if (!oneCapture.tryLock()) throw CameraException(CameraProblem.FAILED)
            try {
                return withTimeout(((exposureSec ?: 1.0) * 1000).toLong() + 15_000L) { takeStill(exposureSec) }
            } catch (e: TimeoutCancellationException) {
                throw CameraException(CameraProblem.FAILED)
            } finally {
                pending = null
                oneCapture.unlock()
            }
        }
    }

    private suspend fun takeStill(exposureSec: Double?): GrayImage = suspendCancellableCoroutine { cont ->
        val cam = device
        val s = session
        val r = reader
        if (isClosed() || cam == null || s == null || r == null) { cont.resumeWithException(CameraException(CameraProblem.DISCONNECTED)); return@suspendCancellableCoroutine }
        try {
            while (true) r.acquireLatestImage()?.close() ?: break // an old picture must not be taken for this one
            val b = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            b.addTarget(r.surface)
            focusAtInfinity(b)
            b.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            val expRange = plan.exposureRangeNs
            val isoRange = plan.isoRange
            if (exposureSec != null && expRange != null && isoRange != null) {
                // Manual: automatic exposure off, the chosen time within what the sensor can do, ISO about 1600 within its range.
                val ns = (exposureSec * 1e9).toLong().coerceIn(expRange.lower, expRange.upper)
                b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ns)
                b.set(CaptureRequest.SENSOR_SENSITIVITY, CameraPlan.ISO.coerceIn(isoRange.lower, isoRange.upper))
                b.set(CaptureRequest.SENSOR_FRAME_DURATION, if (plan.maxFrameDurationNs > 0) ns.coerceAtMost(plan.maxFrameDurationNs) else ns)
            }
            pending = { gray ->
                if (!cont.isCompleted) { if (gray != null) cont.resume(gray) else cont.resumeWithException(CameraException(CameraProblem.FAILED)) }
            }
            s.capture(b.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    val done = pending
                    pending = null
                    done?.invoke(null)
                }
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {}
            }, handler)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!cont.isCompleted) cont.resumeWithException(CameraException(CameraProblem.FAILED))
        }
        cont.invokeOnCancellation { pending = null }
    }

    /** Stops the preview and releases the camera. Safe to call more than once and from any thread. */
    fun close() {
        synchronized(lock) { if (closed) return; closed = true }
        view.surfaceTextureListener = null
        pending?.invoke(null)
        pending = null
        try { session?.close() } catch (e: Exception) {}
        try { device?.close() } catch (e: Exception) {}
        try { reader?.close() } catch (e: Exception) {}
        try { previewSurface?.release() } catch (e: Exception) {}
        session = null; device = null; reader = null; previewSurface = null
        thread.quitSafely()
    }

    private companion object {
        const val ERROR_CAMERA_IN_USE = CameraDevice.StateCallback.ERROR_CAMERA_IN_USE
    }
}
