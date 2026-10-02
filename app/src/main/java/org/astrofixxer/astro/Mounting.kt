package org.astrofixxer.astro

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.sqrt

/** What kind of telescope it is. It only gives a first guess for the eyepiece view; it never decides anything alone. */
enum class TelescopeType { REFRACTOR, REFLECTOR, OTHER }

/** Where the phone sits on the telescope. */
enum class PhonePlacement { TUBE, CAMERA_FORWARD, EYEPIECE }

/** The phone edge that points toward the front (sky end) of the telescope. */
enum class PhoneEdge { TOP, BOTTOM, LEFT, RIGHT }

/**
 * How the eyepiece meets the tube: straight in, or at a right angle (star diagonal, Newtonian). A Newtonian's eyepiece
 * is always at a right angle; see [Mounting.effectiveEyepieceAngle].
 */
enum class EyepieceAngle { STRAIGHT, RIGHT_ANGLE, UNSURE }

/** Whether an image-erecting prism is used. */
enum class Erecting { NO, YES, UNSURE }

/** A direction on the eyepiece view. */
enum class Dir { UP, DOWN, LEFT, RIGHT }

/**
 * How the eyepiece shows the sky compared with an upright, unmirrored map: first flipped left-right when [mirrored],
 * then turned [rotationDeg] (0, 90, 180 or 270) clockwise.
 */
data class ViewOrientation(val rotationDeg: Int, val mirrored: Boolean) {
    companion object { val UPRIGHT = ViewOrientation(0, false) }
}

/** The telescope axis in phone coordinates ([vector]) and whether it is only a guess that must be checked with two stars. */
class PhoneAxis(val vector: DoubleArray, val needsCheck: Boolean)

/** A first guess at the eyepiece view, and whether the user should check it (the check is in the settings). */
data class ViewGuess(val orientation: ViewOrientation, val pleaseCheck: Boolean)

/** One confirmed alignment star: the phone's sensor rotation at that moment and the star's true direction. */
class AlignSample(val device: DoubleArray, val starRay: DoubleArray)

/** One phone axis tried by [axisCheck], with how badly it fits the samples (degrees; lower is better). */
class AxisCandidate(val label: String, val axis: DoubleArray, val altErrorDeg: Double, val separationErrorDeg: Double?, val score: Double)

/** Candidates best first. [separationUsed] is false when there were not two stars at least 10° apart, so the ranking is weaker. */
class AxisCheck(val ranked: List<AxisCandidate>, val separationUsed: Boolean) {
    val best: AxisCandidate? get() = ranked.firstOrNull()
}

/**
 * Where the phone sits on the telescope, and the maths that follows from it. Phone coordinates are Android's device
 * frame: x right, y toward the top edge, z out of the screen. Vectors in the world are [east, north, up].
 */
object Mounting {
    /** The unit axes tried by [axisCheck]. */
    val CANDIDATE_AXES = listOf(
        "+X" to doubleArrayOf(1.0, 0.0, 0.0), "−X" to doubleArrayOf(-1.0, 0.0, 0.0),
        "+Y" to doubleArrayOf(0.0, 1.0, 0.0), "−Y" to doubleArrayOf(0.0, -1.0, 0.0),
        "+Z" to doubleArrayOf(0.0, 0.0, 1.0), "−Z" to doubleArrayOf(0.0, 0.0, -1.0),
    )

    /** Two alignment stars must be at least this far apart for the separation test of [axisCheck] and for two-star alignment. */
    const val MIN_STAR_SEPARATION_DEG = 10.0

    private fun edgeAxis(edge: PhoneEdge) = when (edge) {
        PhoneEdge.TOP -> doubleArrayOf(0.0, 1.0, 0.0)
        PhoneEdge.BOTTOM -> doubleArrayOf(0.0, -1.0, 0.0)
        PhoneEdge.RIGHT -> doubleArrayOf(1.0, 0.0, 0.0)
        PhoneEdge.LEFT -> doubleArrayOf(-1.0, 0.0, 0.0)
    }

    private fun rearCamera() = doubleArrayOf(0.0, 0.0, -1.0)

    /**
     * The eyepiece angle that really applies. A Newtonian reflector has its eyepiece on the side of the tube, at a right
     * angle to where it looks, whatever was chosen or saved; refractors and other telescopes keep the choice.
     */
    fun effectiveEyepieceAngle(type: TelescopeType, eyepieceAngle: EyepieceAngle): EyepieceAngle =
        if (type == TelescopeType.REFLECTOR) EyepieceAngle.RIGHT_ANGLE else eyepieceAngle

    /** [phoneAxis] for a telescope of [type]: a reflector's eyepiece is always a right angle. */
    fun phoneAxis(type: TelescopeType, placement: PhonePlacement, edge: PhoneEdge, eyepieceAngle: EyepieceAngle): PhoneAxis =
        phoneAxis(placement, edge, effectiveEyepieceAngle(type, eyepieceAngle))

    /**
     * The telescope axis in phone coordinates.
     * - On the tube: the chosen edge.
     * - Camera forward: the rear camera, -Z.
     * - On the eyepiece: straight in, the rear camera looks along the axis (-Z); a right-angle eyepiece leaves an edge
     *   pointing along the tube, so the chosen edge; unsure: -Z, flagged so the user checks it with two stars.
     */
    fun phoneAxis(placement: PhonePlacement, edge: PhoneEdge, eyepieceAngle: EyepieceAngle): PhoneAxis = when (placement) {
        PhonePlacement.TUBE -> PhoneAxis(edgeAxis(edge), false)
        PhonePlacement.CAMERA_FORWARD -> PhoneAxis(rearCamera(), false)
        PhonePlacement.EYEPIECE -> when (eyepieceAngle) {
            EyepieceAngle.STRAIGHT -> PhoneAxis(rearCamera(), false)
            EyepieceAngle.RIGHT_ANGLE -> PhoneAxis(edgeAxis(edge), false)
            EyepieceAngle.UNSURE -> PhoneAxis(rearCamera(), true)
        }
    }

    private fun altDeg(v: DoubleArray) = Math.toDegrees(asin(v[2].coerceIn(-1.0, 1.0)))
    private fun angleDeg(a: DoubleArray, b: DoubleArray) = Math.toDegrees(acos(Pointing.dot(a, b).coerceIn(-1.0, 1.0)))

    /**
     * Which phone axis really points along the telescope? Each candidate axis is scored on the samples:
     * (a) altitude error, |altitude of device·axis − altitude of the star|, which comes from gravity and so is not
     * hurt by a wrong compass; (b) with two or more stars at least [MIN_STAR_SEPARATION_DEG] apart, the error of the
     * angle between the two pointings against the true angle, which no compass error can change either.
     * The score is (a) averaged plus (b) averaged; lower is better.
     */
    fun axisCheck(samples: List<AlignSample>): AxisCheck {
        val pairs = buildList {
            for (i in samples.indices) for (j in i + 1 until samples.size) {
                if (angleDeg(samples[i].starRay, samples[j].starRay) >= MIN_STAR_SEPARATION_DEG) add(i to j)
            }
        }
        val ranked = CANDIDATE_AXES.map { (label, axis) ->
            val pointing = samples.map { Pointing.mvec(it.device, axis) }
            val alt = if (samples.isEmpty()) 0.0 else samples.indices.map { abs(altDeg(pointing[it]) - altDeg(samples[it].starRay)) }.average()
            val sep = if (pairs.isEmpty()) null
            else pairs.map { (i, j) -> abs(angleDeg(pointing[i], pointing[j]) - angleDeg(samples[i].starRay, samples[j].starRay)) }.average()
            AxisCandidate(label, axis, alt, sep, alt + (sep ?: 0.0))
        }.sortedBy { it.score }
        return AxisCheck(ranked, pairs.isNotEmpty())
    }

    private fun vec(d: Dir) = when (d) { Dir.UP -> 0 to 1; Dir.DOWN -> 0 to -1; Dir.LEFT -> -1 to 0; Dir.RIGHT -> 1 to 0 }

    /** Where an upright-map direction (x right, y up) ends up in the eyepiece: mirror first, then turn clockwise. */
    private fun through(v: Pair<Int, Int>, o: ViewOrientation): Pair<Int, Int> {
        var (x, y) = if (o.mirrored) -v.first to v.second else v
        repeat(o.rotationDeg / 90) { val nx = y; y = -x; x = nx } // one quarter turn clockwise: right becomes down
        return x to y
    }

    /**
     * The eyepiece view from two nudges. [whenUp] and [whenRight] say which way the star moved in the eyepiece when the
     * telescope was nudged up (toward the zenith) and then to the right. In a correct, upright view nudging up moves
     * stars down, and nudging right moves them left. Returns null when the two answers are along the same axis.
     */
    fun orientationFromNudges(whenUp: Dir, whenRight: Dir): ViewOrientation? {
        for (mirrored in listOf(false, true)) for (rot in listOf(0, 90, 180, 270)) {
            val o = ViewOrientation(rot, mirrored)
            if (through(vec(Dir.DOWN), o) == vec(whenUp) && through(vec(Dir.LEFT), o) == vec(whenRight)) return o
        }
        return null
    }

    /**
     * A first guess at the eyepiece view from the optics. The telescope type alone never decides: what counts is the
     * eyepiece and any prism, and the type only settles the cases that depend on it.
     * - An image-erecting prism: upright.
     * - Unsure about the prism or the eyepiece angle: upright, and the user is asked to check.
     * - Reflector (Newtonian, whose eyepiece is always a right angle): rotated 180°; it varies with where the eyepiece sits, so the user is asked to check.
     * - Right-angle diagonal on a refractor or other telescope: mirrored (upright but left-right reversed).
     * - Straight-through refractor: rotated 180° (upside down and reversed).
     * - Anything else (straight eyepiece on an unknown telescope): upright, please check.
     * The phone [placement] does not change the guess: the view depends on the optics, not on where the phone sits.
     */
    @Suppress("UNUSED_PARAMETER")
    fun initialViewOrientation(type: TelescopeType, placement: PhonePlacement, angle: EyepieceAngle, erecting: Erecting): ViewGuess {
        val upright = ViewOrientation.UPRIGHT
        val eyepieceAngle = effectiveEyepieceAngle(type, angle)
        return when {
            erecting == Erecting.YES -> ViewGuess(upright, false)
            erecting == Erecting.UNSURE || eyepieceAngle == EyepieceAngle.UNSURE -> ViewGuess(upright, true)
            type == TelescopeType.REFLECTOR -> ViewGuess(ViewOrientation(180, false), true)
            eyepieceAngle == EyepieceAngle.RIGHT_ANGLE -> ViewGuess(ViewOrientation(0, true), false)
            type == TelescopeType.REFRACTOR -> ViewGuess(ViewOrientation(180, false), false)
            else -> ViewGuess(upright, true)
        }
    }

    /**
     * Rotation (row-major 3x3) taking measured directions [u1], [u2] onto true directions [r1], [r2]: [r1] is matched
     * exactly, [r2] as well as one rotation allows (exactly when the angles between the pairs agree). This is the
     * TRIAD method. Null when either pair is (nearly) parallel.
     */
    fun triad(u1: DoubleArray, u2: DoubleArray, r1: DoubleArray, r2: DoubleArray): DoubleArray? {
        fun unit(v: DoubleArray): DoubleArray? { val l = sqrt(Pointing.dot(v, v)); return if (l < 1e-9) null else doubleArrayOf(v[0] / l, v[1] / l, v[2] / l) }
        fun frame(a: DoubleArray, b: DoubleArray): Array<DoubleArray>? {
            val t1 = unit(a) ?: return null
            val t2 = unit(Pointing.cross(t1, unit(b) ?: return null)) ?: return null // null when a and b are parallel
            return arrayOf(t1, t2, Pointing.cross(t1, t2))
        }
        val t = frame(u1, u2) ?: return null
        val s = frame(r1, r2) ?: return null
        return DoubleArray(9) { k ->
            val i = k / 3; val j = k % 3
            s[0][i] * t[0][j] + s[1][i] * t[1][j] + s[2][i] * t[2][j]
        }
    }
}
