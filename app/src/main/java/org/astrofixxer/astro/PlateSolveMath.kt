package org.astrofixxer.astro

import kotlin.math.atan
import kotlin.math.tan

/** How much the app knows about where the telescope points, which sets how much sky the solver has to search. */
enum class CentreHint(val radiusDeg: Double) {
    /** The app is aligned: the sensors point within a few degrees. */
    ALIGNED(10.0),
    /** Not aligned, but the phone has a compass: it can be tens of degrees off near a metal tube. */
    COMPASS(30.0),
    /** Nothing to go on: the whole sky. */
    NONE(180.0),
}

/** What to run the plate solver with, or why it must not be run. */
sealed class SolvePlan {
    /**
     * Solve with [hints]. [fieldWidthDeg] is the expected width of the picture on the sky; [deepStars] says the deep star
     * list is needed (narrow fields), otherwise the bright catalogue is enough. [assumedFov] means the phone camera's
     * field of view is not known (a gallery photo, or a camera that did not report it), so the scale is a wide guess.
     */
    class Run(
        val hints: SolveHints, val fieldWidthDeg: Double, val deepStars: Boolean, val assumedFov: Boolean, val centre: CentreHint,
    ) : SolvePlan()

    /** No solve is attempted. */
    class Refused(val why: Why) : SolvePlan()

    enum class Why {
        /** The phone lies on the tube: its camera faces the tube and cannot see the sky. */
        TUBE,
        /** A field narrower than [PlateSolveHints.NARROW_FIELD_DEG] cannot be found blind, and nothing says where the telescope points. */
        NO_CENTRE_HINT,
    }
}

/** The solver settings for a photo, worked out from the telescope setup and what the camera reports. Pure maths. */
object PlateSolveHints {
    /** The horizontal field of view assumed when it is not known (gallery photos). */
    const val DEFAULT_CAMERA_FOV_DEG = 65.0
    /** Narrower fields than this cannot be solved without a rough position. */
    const val NARROW_FIELD_DEG = 3.0
    /** Fields narrower than this need the deep star list; wider ones solve with the bright catalogue. */
    const val DEEP_STARS_BELOW_DEG = 10.0

    /** Eyepiece afocal magnification: telescope focal length / eyepiece focal length. */
    fun magnification(telescopeFocalMm: Double, eyepieceFocalMm: Double) = telescopeFocalMm / eyepieceFocalMm

    /**
     * The width of the picture on the sky, in degrees, as best the setup can tell. At the eyepiece (afocal) the phone
     * camera sees the eyepiece's exit beam, so the picture spans the phone camera's field / magnification; with the camera
     * beside the tube it spans the camera's own field. A null [cameraHFovDeg] means unknown: [DEFAULT_CAMERA_FOV_DEG].
     */
    fun nominalWidthDeg(placement: PhonePlacement, telescopeFocalMm: Double, eyepieceFocalMm: Double, cameraHFovDeg: Double?): Double {
        val fov = cameraHFovDeg ?: DEFAULT_CAMERA_FOV_DEG
        return when (placement) {
            PhonePlacement.EYEPIECE -> fov / magnification(telescopeFocalMm, eyepieceFocalMm)
            else -> fov
        }
    }

    /** Pixel scale (degrees per pixel at the centre) of a picture [imageWidthPx] wide that spans [widthDeg], for the solver's TAN model. */
    fun scaleDegPerPx(widthDeg: Double, imageWidthPx: Int): Double {
        val w = widthDeg.coerceIn(1e-6, 170.0)
        return Math.toDegrees(atan(2 * tan(Math.toRadians(w) / 2) / imageWidthPx))
    }

    /**
     * The plan for a picture [imageWidthPx] wide.
     * - Scale range: at the eyepiece the width is fov / magnification, ±35 %; camera forward: fov ±25 %; when the field of
     *   view is unknown ([cameraHFovDeg] null) [DEFAULT_CAMERA_FOV_DEG] with a wide range (×0.5 to ×1.8).
     * - Centre: [centre] is where the sensors say the telescope points (J2000 RA/Dec). Aligned: search 10° around it; with
     *   a compass: 30°; otherwise (or with no [centre]) the whole sky.
     * - A field narrower than [NARROW_FIELD_DEG] with no usable centre is refused: the solver would only guess.
     * - Deep stars for fields narrower than [DEEP_STARS_BELOW_DEG], the bright catalogue for wider ones.
     * On the tube nothing is solved.
     */
    fun plan(
        placement: PhonePlacement, telescopeFocalMm: Double, eyepieceFocalMm: Double, cameraHFovDeg: Double?, imageWidthPx: Int,
        centre: Pair<Double, Double>?, aligned: Boolean, hasCompass: Boolean,
    ): SolvePlan {
        if (placement == PhonePlacement.TUBE) return SolvePlan.Refused(SolvePlan.Why.TUBE)
        require(imageWidthPx > 0 && telescopeFocalMm > 0 && eyepieceFocalMm > 0) { "invalid picture or setup" }
        val assumed = cameraHFovDeg == null || cameraHFovDeg <= 0.0
        val nominal = nominalWidthDeg(placement, telescopeFocalMm, eyepieceFocalMm, cameraHFovDeg?.takeIf { it > 0 })
        val (lo, hi) = when {
            assumed -> 0.5 to 1.8
            placement == PhonePlacement.EYEPIECE -> 0.65 to 1.35
            else -> 0.75 to 1.25
        }
        val hint = when {
            centre == null -> CentreHint.NONE
            aligned -> CentreHint.ALIGNED
            hasCompass -> CentreHint.COMPASS
            else -> CentreHint.NONE
        }
        if (nominal < NARROW_FIELD_DEG && hint == CentreHint.NONE) return SolvePlan.Refused(SolvePlan.Why.NO_CENTRE_HINT)
        val sMin = scaleDegPerPx(nominal * lo, imageWidthPx)
        val sMax = scaleDegPerPx(nominal * hi, imageWidthPx)
        val hints = if (hint == CentreHint.NONE || centre == null) SolveHints(sMin, sMax)
        else SolveHints(sMin, sMax, centre.first, centre.second, hint.radiusDeg)
        return SolvePlan.Run(hints, nominal, nominal < DEEP_STARS_BELOW_DEG, assumed, hint)
    }

    /** The horizontal field of view of a lens: [focalMm] focal length over a sensor side [sensorMm] long. */
    fun fovDeg(focalMm: Double, sensorMm: Double): Double = Math.toDegrees(2 * atan(sensorMm / (2 * focalMm)))
}

/** Where the telescope points on a solved picture, and the alignment that follows from it. Pure maths. */
object PhotoAlignment {
    /** After the calibration, the telescope axis must land this close to the solved direction (degrees), as in an alignment on a star. */
    const val EXACT_DEG = 0.01

    /**
     * J2000 (ra, dec) the telescope points at on [solved]. At the eyepiece it is the picture centre; with the camera
     * beside the tube it is the calibrated pixel ([offset], fractions of the picture width and height, y down). Null on
     * the tube, or before the camera offset is known.
     */
    fun telescopeRaDec(solved: SolveResult.Solved, placement: PhonePlacement, offset: Pair<Double, Double>?): Pair<Double, Double>? = when (placement) {
        PhonePlacement.EYEPIECE -> solved.raDeg to solved.decDeg
        PhonePlacement.CAMERA_FORWARD ->
            if (offset == null || solved.imageWidth <= 0 || solved.imageHeight <= 0) null
            else solved.pixelToRaDec(offset.first * solved.imageWidth, offset.second * solved.imageHeight)
        PhonePlacement.TUBE -> null
    }

    /**
     * The camera offset from a star that was centred in the eyepiece while [solved] was taken: where that star is on the
     * picture, as fractions of its width and height. Null when the star is not on the picture (nothing is learned then).
     */
    fun offsetOf(solved: SolveResult.Solved, starRaDeg: Double, starDecDeg: Double): Pair<Double, Double>? {
        if (solved.imageWidth <= 0 || solved.imageHeight <= 0) return null
        val p = solved.raDecToPixel(starRaDeg, starDecDeg) ?: return null
        val fx = p.first / solved.imageWidth
        val fy = p.second / solved.imageHeight
        return if (fx in 0.0..1.0 && fy in 0.0..1.0) fx to fy else null
    }

    /** The calibration for a photo; [matrix] is what the app stores as the calibration, [sample] one more true (device, direction) pair. */
    class Calibration(val matrix: DoubleArray, val sample: AlignSample, val ray: DoubleArray)

    /**
     * The alignment that makes the telescope axis of a phone with rotation [device] (phone axis [axis]) point at J2000
     * ([raDeg], [decDeg]) as seen from ([latDeg], [lonDeg]) at [timeMillis]: the same calculation as aligning on a star
     * that is centred. Null when the direction is below the horizon (nothing in a night photo is), or when the result does
     * not land within [EXACT_DEG] of it.
     */
    fun calibrate(device: DoubleArray, axis: DoubleArray, raDeg: Double, decDeg: Double, timeMillis: Long, latDeg: Double, lonDeg: Double): Calibration? {
        val ray = Pointing.rayFromPos(raDeg, decDeg, timeMillis, latDeg, lonDeg)
        if (!ray.all { it.isFinite() } || ray[2] <= 0.0) return null
        val matrix = Pointing.alignMatrix(Pointing.cameraRays(device, null, axis), ray)
        if (!matrix.all { it.isFinite() }) return null
        val landed = Pointing.cameraRays(device, matrix, axis)[2]
        if (Pointing.angleBetweenDeg(landed, ray) > EXACT_DEG) return null
        return Calibration(matrix, AlignSample(device.copyOf(), ray), ray)
    }
}
