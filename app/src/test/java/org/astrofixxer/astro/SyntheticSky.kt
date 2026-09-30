package org.astrofixxer.astro

import java.util.Random
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Test-only synthetic sky pictures. The pose is what the solver has to recover: TAN projection about (raDeg, decDeg) at
 * the image centre (width/2, height/2), [rollDeg] = position angle of image "up" (north through east), [mirrored] =
 * east on the right when north is up. The projection below is written out independently of the solver's classes; its
 * convention is pinned against astropy by [PlateSolverTest.conventionMatchesAstropyWcs].
 */
class Truth(
    val raDeg: Double, val decDeg: Double, val rollDeg: Double, val scaleDegPerPx: Double, val mirrored: Boolean,
    val width: Int, val height: Int,
    /** Radial lens distortion: image radius is scaled by 1 + distortion * (r / half diagonal)^2 (0 = ideal TAN). */
    val distortion: Double = 0.0,
) {
    /** Image position of a sky position, or null if it is behind the tangent plane. */
    fun toPixel(ra: Double, dec: Double): DoubleArray? {
        val a = Math.toRadians(ra - raDeg)
        val d = Math.toRadians(dec)
        val d0 = Math.toRadians(decDeg)
        val cosC = sin(d0) * sin(d) + cos(d0) * cos(d) * cos(a)
        if (cosC <= 0.01) return null
        val xi = cos(d) * sin(a) / cosC
        val eta = (cos(d0) * sin(d) - sin(d0) * cos(d) * cos(a)) / cosC
        val t = tan(Math.toRadians(scaleDegPerPx))
        val th = Math.toRadians(rollDeg)
        // up = direction of image -y on the sky; right = direction of image +x
        val upE = sin(th); val upN = cos(th)
        val rE = if (mirrored) cos(th) else -cos(th)
        val rN = if (mirrored) -sin(th) else sin(th)
        var px = (xi * rE + eta * rN) / t
        var py = -(xi * upE + eta * upN) / t
        if (distortion != 0.0) {
            val rMax = 0.5 * hypot(width.toDouble(), height.toDouble())
            val f = 1 + distortion * (px * px + py * py) / (rMax * rMax)
            px *= f; py *= f
        }
        return doubleArrayOf(width / 2.0 + px, height / 2.0 + py)
    }

    /** Half the picture diagonal on the sky, degrees. */
    fun halfDiagonalDeg() = Math.toDegrees(atan(0.5 * hypot(width.toDouble(), height.toDouble()) * tan(Math.toRadians(scaleDegPerPx))))
}

class RenderSpec(
    /** Stars fainter than this are not drawn. */
    val limitMag: Double,
    val psfSigma: Double = 1.5,
    /** Peak signal-to-noise (against the sky noise) of a star at the limiting magnitude. */
    val peakSnrAtLimit: Double = 6.0,
    val sky: Double = 0.06,
    /** Peak-to-peak relative sky gradient across the frame. */
    val gradient: Double = 0.3,
    /** Electrons per unit of pixel value: shot noise variance is value / gain. */
    val gain: Double = 4000.0,
    val readNoise: Double = 0.004,
    val hotPixels: Int = 6,
    val missingFraction: Double = 0.10,
    val fakeStars: Int = 4,
    /** Everything farther than this from the image centre is exactly 0 (an eyepiece field stop). */
    val circleRadiusPx: Double? = null,
    /** Stars are streaks this long (pixels), as in a long exposure on a fixed mount. */
    val trailPx: Double = 0.0,
    /** False draws no stars and no fake stars at all: sky, noise and hot pixels only. */
    val drawStars: Boolean = true,
)

object SyntheticSky {
    /** Shot noise is drawn as a Gaussian with variance = signal / gain (signal is at least 100 electrons everywhere here). */
    fun render(truth: Truth, spec: RenderSpec, source: StarSource, seed: Long): GrayImage {
        val rnd = Random(seed)
        val w = truth.width
        val h = truth.height
        val signal = FloatArray(w * h)
        val sigma = spec.psfSigma
        val noise0 = sqrt(spec.sky / spec.gain + spec.readNoise * spec.readNoise)
        val fluxLimit = spec.peakSnrAtLimit * noise0 * 2 * PI * sigma * sigma
        val rho = truth.halfDiagonalDeg() * 1.05 + 0.1
        val sky = if (spec.drawStars) source.cone(truth.raDeg, truth.decDeg, min(rho, 180.0), spec.limitMag) else emptyList()
        val drawn = ArrayList<Triple<Double, Double, Double>>() // x, y, flux
        for (s in sky) {
            if (rnd.nextDouble() < spec.missingFraction) continue
            val p = truth.toPixel(s.raDeg, s.decDeg) ?: continue
            if (p[0] < -10 || p[1] < -10 || p[0] > w + 10 || p[1] > h + 10) continue
            drawn.add(Triple(p[0], p[1], fluxLimit * 10.0.pow(-0.4 * (s.mag - spec.limitMag))))
        }
        if (spec.drawStars) repeat(spec.fakeStars) {
            val mag = spec.limitMag - rnd.nextDouble() * 2.5
            drawn.add(Triple(rnd.nextDouble() * w, rnd.nextDouble() * h, fluxLimit * 10.0.pow(-0.4 * (mag - spec.limitMag))))
        }
        val trailAngle = rnd.nextDouble() * PI
        for ((x, y, flux) in drawn) {
            if (spec.trailPx <= 0) addGaussian(signal, w, h, x, y, flux, sigma)
            else {
                val steps = ceil(spec.trailPx * 2).toInt()
                for (k in 0 until steps) {
                    val f = (k + 0.5) / steps - 0.5
                    addGaussian(signal, w, h, x + f * spec.trailPx * cos(trailAngle), y + f * spec.trailPx * sin(trailAngle), flux / steps, sigma)
                }
            }
        }
        val out = FloatArray(w * h)
        val gAngle = rnd.nextDouble() * 2 * PI
        val hot = HashMap<Int, Float>()
        repeat(spec.hotPixels) { hot[rnd.nextInt(w * h)] = (0.3 + 0.7 * rnd.nextDouble()).toFloat() }
        val r2 = spec.circleRadiusPx?.let { it * it }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (r2 != null && (x + 0.5 - w / 2.0).pow(2) + (y + 0.5 - h / 2.0).pow(2) > r2) { out[i] = 0f; continue }
            val g = ((x + 0.5) / w - 0.5) * cos(gAngle) + ((y + 0.5) / h - 0.5) * sin(gAngle)
            val v = spec.sky * (1 + spec.gradient * g) + signal[i] + (hot[i] ?: 0f)
            val noisy = v + rnd.nextGaussian() * sqrt(max(v, 0.0) / spec.gain + spec.readNoise * spec.readNoise)
            out[i] = if (noisy <= 0.0) 1e-4f else min(1.0, noisy).toFloat() // never exactly 0 inside the picture
        }
        return GrayImage(w, h, out)
    }

    private fun addGaussian(img: FloatArray, w: Int, h: Int, x: Double, y: Double, flux: Double, sigma: Double) {
        val r = ceil(6 * sigma).toInt()
        val x0 = max(0, floor(x - r).toInt()); val x1 = min(w - 1, ceil(x + r).toInt())
        val y0 = max(0, floor(y - r).toInt()); val y1 = min(h - 1, ceil(y + r).toInt())
        val norm = flux / (2 * PI * sigma * sigma)
        for (j in y0..y1) for (i in x0..x1) {
            val dx = i + 0.5 - x; val dy = j + 0.5 - y
            img[j * w + i] += (norm * exp(-(dx * dx + dy * dy) / (2 * sigma * sigma))).toFloat()
        }
    }

    /** Angular distance in degrees. */
    fun separationDeg(ra1: Double, dec1: Double, ra2: Double, dec2: Double): Double {
        val a = Math.toRadians(dec1); val b = Math.toRadians(dec2); val dr = Math.toRadians(ra1 - ra2)
        val s = atan2(hypot(cos(b) * sin(dr), cos(a) * sin(b) - sin(a) * cos(b) * cos(dr)), sin(a) * sin(b) + cos(a) * cos(b) * cos(dr))
        return Math.toDegrees(s)
    }
}
