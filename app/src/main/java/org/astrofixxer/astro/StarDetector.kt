package org.astrofixxer.astro

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A grey image: row-major [pixels], 0..1, roughly linear in brightness; pixel (i, j) covers x in [i, i+1), y in [j, j+1). */
class GrayImage(val width: Int, val height: Int, val pixels: FloatArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixels must hold width * height values" }
    }
}

/**
 * A star found in an image. [x], [y] are the flux-weighted centroid in image pixels (pixel (i, j) covers
 * [i, i+1) x [j, j+1), so the image centre is (width / 2, height / 2)); [flux] is the background-subtracted sum;
 * [elongation] is the ratio of the major to the minor axis of the second moments (1 = round); [sigma] is the RMS
 * radius in pixels (NaN when unknown).
 */
class DetectedStar(val x: Double, val y: Double, val flux: Double, val elongation: Double, val sigma: Double = Double.NaN)

internal class Detection(
    val stars: List<DetectedStar>,
    /** Fraction of pixels at or above the saturation level. */
    val saturatedFraction: Double,
    /** Median pixel value of the whole image. */
    val medianLevel: Double,
    /** Median read+sky noise of the raw image (pixel units), 0 when nothing could be measured. */
    val noise: Double,
    val blobsRejected: Int,
    val hotPixelsRejected: Int,
)

/**
 * Star detection: coarse-grid median background and MAD noise, 5-sigma threshold on a lightly smoothed image, 8-connected
 * components, flux-weighted centroids and second moments. Pixels that are exactly 0 are treated as "no data" (masked
 * black areas such as the dark surround of an eyepiece field) and never contribute to background or detections.
 */
internal object StarDetector {
    private const val SATURATED = 0.98f
    private const val MAX_STARS = 400
    private const val SIGMA = 5.0f

    fun detect(img: GrayImage): Detection {
        val bin = max(1, ceil(max(img.width, img.height) / 2400.0).toInt())
        val w = img.width / bin
        val h = img.height / bin
        val pix: FloatArray
        if (bin == 1) pix = img.pixels else {
            pix = FloatArray(w * h)
            val inv = 1f / (bin * bin)
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0f
                for (dy in 0 until bin) { val row = (y * bin + dy) * img.width + x * bin; for (dx in 0 until bin) s += img.pixels[row + dx] }
                pix[y * w + x] = s * inv
            }
        }
        val n = w * h
        var sat = 0
        val hist = IntArray(1024)
        for (v in pix) {
            if (v >= SATURATED) sat++
            hist[(v.coerceIn(0f, 1f) * 1023f).toInt()]++
        }
        var acc = 0
        var medBin = 0
        while (medBin < 1023 && acc + hist[medBin] < n / 2) { acc += hist[medBin]; medBin++ }
        val satFrac = sat.toDouble() / n
        val median = medBin / 1023.0
        val empty = Detection(emptyList(), satFrac, median, 0.0, 0, 0)
        if (w < 16 || h < 16) return empty

        // ---- smoothing with a [1 2 1] kernel, normalised over valid pixels
        val valid = BooleanArray(n) { pix[it] > 0f }
        val hv = FloatArray(n)
        val hw = FloatArray(n)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var num = 0f
                var den = 0f
                if (valid[row + x]) { num += 2f * pix[row + x]; den += 2f }
                if (x > 0 && valid[row + x - 1]) { num += pix[row + x - 1]; den += 1f }
                if (x < w - 1 && valid[row + x + 1]) { num += pix[row + x + 1]; den += 1f }
                hv[row + x] = num; hw[row + x] = den
            }
        }
        val sm = FloatArray(n)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var num = 2f * hv[row + x]
                var den = 2f * hw[row + x]
                if (y > 0) { num += hv[row - w + x]; den += hw[row - w + x] }
                if (y < h - 1) { num += hv[row + w + x]; den += hw[row + w + x] }
                sm[row + x] = if (valid[row + x] && den > 0f) num / den else 0f
            }
        }

        // ---- block background and noise
        val bs = (min(w, h) / 25).coerceIn(16, 64)
        val nbx = (w + bs - 1) / bs
        val nby = (h + bs - 1) / bs
        val bgB = DoubleArray(nbx * nby) { Double.NaN }
        val sigSB = DoubleArray(nbx * nby) { Double.NaN }
        val sigRB = DoubleArray(nbx * nby) { Double.NaN }
        val bufS = FloatArray(bs * bs)
        val bufR = FloatArray(bs * bs)
        for (by in 0 until nby) for (bx in 0 until nbx) {
            val x0 = bx * bs; val x1 = min(w, x0 + bs); val y0 = by * bs; val y1 = min(h, y0 + bs)
            var c = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                val i = y * w + x
                if (valid[i]) { bufS[c] = sm[i]; bufR[c] = pix[i]; c++ }
            }
            if (c < 0.3 * (x1 - x0) * (y1 - y0) || c < 16) continue
            java.util.Arrays.sort(bufS, 0, c)
            java.util.Arrays.sort(bufR, 0, c)
            val medS = bufS[c / 2]
            val medR = bufR[c / 2]
            for (k in 0 until c) { bufS[k] = abs(bufS[k] - medS); bufR[k] = abs(bufR[k] - medR) }
            java.util.Arrays.sort(bufS, 0, c)
            java.util.Arrays.sort(bufR, 0, c)
            val k = by * nbx + bx
            bgB[k] = medS.toDouble()
            sigSB[k] = 1.4826 * bufS[c / 2]
            sigRB[k] = 1.4826 * bufR[c / 2]
        }
        val goodS = sigSB.filter { !it.isNaN() }.sorted()
        if (goodS.isEmpty()) return empty
        val goodR = sigRB.filter { !it.isNaN() }.sorted()
        val noiseR = goodR[goodR.size / 2]
        val floorS = max(0.25 * goodS[goodS.size / 2], 3e-4)
        val floorR = max(0.25 * noiseR, 1.1e-3)
        for (k in sigSB.indices) if (!sigSB[k].isNaN()) { sigSB[k] = max(sigSB[k], floorS); sigRB[k] = max(sigRB[k], floorR) }
        fillGaps(bgB, nbx, nby); fillGaps(sigSB, nbx, nby); fillGaps(sigRB, nbx, nby)

        // Bilinear interpolation between block centres.
        val ix0 = IntArray(w); val tx = FloatArray(w)
        for (x in 0 until w) {
            val f = (x + 0.5f) / bs - 0.5f
            val i0 = floor(f).toInt()
            ix0[x] = i0.coerceIn(0, nbx - 1); tx[x] = if (i0 < 0 || i0 >= nbx - 1) 0f else f - i0
        }
        val iy0 = IntArray(h); val ty = FloatArray(h)
        for (y in 0 until h) {
            val f = (y + 0.5f) / bs - 0.5f
            val i0 = floor(f).toInt()
            iy0[y] = i0.coerceIn(0, nby - 1); ty[y] = if (i0 < 0 || i0 >= nby - 1) 0f else f - i0
        }
        fun interp(a: DoubleArray, x: Int, y: Int): Double {
            val i = iy0[y] * nbx + ix0[x]
            val dx = tx[x].toDouble(); val dy = ty[y].toDouble()
            val right = if (dx > 0) 1 else 0
            val down = if (dy > 0) nbx else 0
            val top = a[i] * (1 - dx) + a[i + right] * dx
            val bottom = a[i + down] * (1 - dx) + a[i + down + right] * dx
            return top * (1 - dy) + bottom * dy
        }

        // ---- threshold and connected components
        val cand = BooleanArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (valid[i] && sm[i] - interp(bgB, x, y) > SIGMA * interp(sigSB, x, y)) cand[i] = true
        }
        val maxArea = max(300, (0.0004 * n).toInt())
        val stack = IntArray(n.coerceAtMost(1 shl 20))
        val comp = IntArray(maxArea + 1)
        val stamp = IntArray(n)
        var stampId = 0
        val found = ArrayList<DetectedStar>()
        var blobs = 0
        var hot = 0
        for (start in 0 until n) {
            if (!cand[start]) continue
            var sp = 0
            var area = 0
            var touchesEdge = false
            stack[sp++] = start; cand[start] = false
            while (sp > 0) {
                val p = stack[--sp]
                if (area <= maxArea) comp[area] = p
                area++
                val px = p % w; val py = p / w
                if (px == 0 || py == 0 || px == w - 1 || py == h - 1) touchesEdge = true
                for (dy in -1..1) {
                    val yy = py + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = px + dx
                        if (xx < 0 || xx >= w) continue
                        val q = yy * w + xx
                        if (cand[q]) { cand[q] = false; if (sp < stack.size) stack[sp++] = q }
                    }
                }
            }
            if (area > maxArea) { blobs++; continue }
            if (area < 2 || touchesEdge) continue

            // peak pixel and hot-pixel test on the raw values
            var peak = comp[0]
            var peakVal = -1.0
            for (k in 0 until area) {
                val p = comp[k]
                val r = pix[p] - interp(bgB, p % w, p / w)
                if (r > peakVal) { peakVal = r; peak = p }
            }
            val pxp = peak % w; val pyp = peak / w
            var nb = -1e9
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val xx = pxp + dx; val yy = pyp + dy
                if (xx < 0 || yy < 0 || xx >= w || yy >= h || !valid[yy * w + xx]) continue
                nb = max(nb, (pix[yy * w + xx] - interp(bgB, xx, yy)).toDouble())
            }
            if (nb < max(0.1 * peakVal, 2.5 * interp(sigRB, pxp, pyp))) { hot++; continue }

            // moments over the component plus a one-pixel ring, weights = background-subtracted raw values
            stampId++
            var sw = 0.0; var sx = 0.0; var sy = 0.0
            var sxx = 0.0; var syy = 0.0; var sxy = 0.0
            for (pass in 0..1) {
                for (k in 0 until area) {
                    val p0 = comp[k]
                    val px0 = p0 % w; val py0 = p0 / w
                    for (dy in -1..1) for (dx in -1..1) {
                        val xx = px0 + dx; val yy = py0 + dy
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                        val q = yy * w + xx
                        if (!valid[q] || stamp[q] == stampId * 2 + pass) continue
                        stamp[q] = stampId * 2 + pass
                        val wt = (pix[q] - interp(bgB, xx, yy)).toDouble()
                        if (wt <= 0) continue
                        val fx = xx + 0.5; val fy = yy + 0.5
                        if (pass == 0) { sw += wt; sx += wt * fx; sy += wt * fy }
                        else { val ddx = fx - sx / sw; val ddy = fy - sy / sw; sxx += wt * ddx * ddx; syy += wt * ddy * ddy; sxy += wt * ddx * ddy }
                    }
                }
            }
            if (sw <= 0) continue
            val cx = sx / sw; val cy = sy / sw
            val vxx = sxx / sw + 1.0 / 12; val vyy = syy / sw + 1.0 / 12; val vxy = sxy / sw
            val mean = 0.5 * (vxx + vyy)
            val diff = sqrt(0.25 * (vxx - vyy) * (vxx - vyy) + vxy * vxy)
            val l1 = mean + diff; val l2 = max(mean - diff, 1e-6)
            found.add(DetectedStar(cx * bin, cy * bin, sw * bin * bin, sqrt(l1 / l2), sqrt(mean) * bin))
        }
        found.sortByDescending { it.flux }
        return Detection(if (found.size > MAX_STARS) found.subList(0, MAX_STARS).toList() else found,
            satFrac, median, noiseR, blobs, hot)
    }

    /** Replaces NaN blocks by the mean of their valid neighbours, repeatedly, so every block gets a value. */
    private fun fillGaps(a: DoubleArray, nbx: Int, nby: Int) {
        var guard = nbx + nby
        while (a.any { it.isNaN() } && guard-- > 0) {
            val next = a.copyOf()
            for (y in 0 until nby) for (x in 0 until nbx) {
                if (!a[y * nbx + x].isNaN()) continue
                var s = 0.0; var c = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= nbx || yy >= nby) continue
                    val v = a[yy * nbx + xx]
                    if (!v.isNaN()) { s += v; c++ }
                }
                if (c > 0) next[y * nbx + x] = s / c
            }
            System.arraycopy(next, 0, a, 0, a.size)
        }
    }
}
