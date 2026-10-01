package org.astrofixxer.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.util.Size
import org.astrofixxer.astro.Luminance
import org.astrofixxer.astro.PlateSolveHints
import kotlin.math.abs
import kotlin.math.max

/**
 * What the back camera can do and which of its sizes to use, worked out once from its Camera2 characteristics. The preview
 * and the still have the same aspect ratio (the sensor's), so both show the whole field of view and a position given as
 * fractions of the picture means the same thing on both. Pictures are turned [quarterTurns] × 90° so they are upright the
 * way the phone shows them; the app is fixed to portrait.
 */
internal class CameraPlan(
    val cameraId: String,
    val previewSize: Size,
    val stillSize: Size,
    val quarterTurns: Int,
    /** Horizontal field of view of the upright picture, or null when the camera did not report enough to tell. */
    val hFovDeg: Double?,
    /** Manual exposure times in seconds this camera can do (1, 2 and 4 s where allowed); empty without MANUAL_SENSOR. */
    val manualExposuresSec: List<Double>,
    val exposureRangeNs: Range<Long>?,
    val isoRange: Range<Int>?,
    val maxFrameDurationNs: Long,
    /** The lens can be focused, so infinity can be asked for. */
    val canFocus: Boolean,
) {
    /** Width over height of the upright preview. */
    val uprightAspect: Float get() = if (quarterTurns % 2 == 1) previewSize.height.toFloat() / previewSize.width else previewSize.width.toFloat() / previewSize.height

    companion object {
        private val EXPOSURES_SEC = listOf(1.0, 2.0, 4.0)
        const val ISO = 1600

        /** The plan for the first back camera, or null when there is none. [displayRotationDeg] is the screen's rotation (0 in portrait). */
        fun create(context: Context, displayRotationDeg: Int): CameraPlan? = try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            if (id == null) null else plan(id, manager.getCameraCharacteristics(id), displayRotationDeg)
        } catch (e: Exception) {
            null
        }

        private fun plan(id: String, c: CameraCharacteristics, displayRotationDeg: Int): CameraPlan? {
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
            val yuv = map.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()
            val tex = map.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
            if (yuv.isEmpty() || tex.isEmpty()) return null
            val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val big = yuv.maxByOrNull { it.width.toLong() * it.height }!!
            val ratio = if (active != null) active.width().toDouble() / active.height() else big.width.toDouble() / big.height
            fun matches(s: Size) = abs(s.width.toDouble() / s.height - ratio) < 0.02 * ratio
            val yuvMatch = yuv.filter(::matches)
            val texMatch = tex.filter(::matches)
            val yuvPool = yuvMatch.ifEmpty { yuv }
            val texPool = texMatch.ifEmpty { tex }
            // The smallest still that has a full-size picture to give (the solver uses at most 1600 px), else the largest.
            val still = yuvPool.filter { max(it.width, it.height) >= Luminance.MAX_LONG_SIDE }.minByOrNull { it.width.toLong() * it.height }
                ?: yuvPool.maxByOrNull { it.width.toLong() * it.height }!!
            val preview = texPool.filter { it.width.toLong() * it.height <= 1920L * 1440 }.maxByOrNull { it.width.toLong() * it.height }
                ?: texPool.minByOrNull { it.width.toLong() * it.height }!!

            val sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val turns = (((sensorOrientation - displayRotationDeg) % 360 + 360) % 360) / 90

            // Field of view: focal length and the physical size of the part of the sensor that is used, along the upright picture's width.
            val fov = if (yuvMatch.isEmpty() || texMatch.isEmpty() || active == null) null else {
                val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                val phys = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val pixels = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                if (focal == null || focal <= 0f || phys == null || pixels == null || pixels.width <= 0 || pixels.height <= 0) null else {
                    val widthMm = phys.width.toDouble() * active.width() / pixels.width
                    val heightMm = phys.height.toDouble() * active.height() / pixels.height
                    val acrossMm = if (turns % 2 == 1) heightMm else widthMm // a quarter turn makes the sensor's height the picture's width
                    PlateSolveHints.fovDeg(focal.toDouble(), acrossMm).takeIf { it in 5.0..170.0 }
                }
            }

            val manual = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
            val expRange = if (manual) c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) else null
            val isoRange = if (manual) c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) else null
            val maxFrame = c.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: 0L
            val exposures = if (expRange == null || isoRange == null) emptyList() else EXPOSURES_SEC.filter { sec ->
                val ns = (sec * 1e9).toLong()
                ns >= expRange.lower && ns <= expRange.upper && (maxFrame == 0L || ns <= maxFrame)
            }
            val canFocus = (c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f) > 0f
            return CameraPlan(id, preview, still, turns, fov, exposures, expRange, isoRange, maxFrame, canFocus)
        }
    }
}
