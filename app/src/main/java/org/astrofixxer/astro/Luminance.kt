package org.astrofixxer.astro

import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Builds [GrayImage]s (values 0..1) from what the camera and the gallery deliver: the Y plane of a YUV_420_888 picture or
 * rows of ARGB pixels. Pictures are shrunk by area averaging to at most [MAX_LONG_SIDE] pixels on the long side (the
 * solver does not gain from more, and it keeps memory and time small) and turned by whole quarter turns, so the result is
 * upright the way the phone shows it. Pure Kotlin, no Android.
 */
object Luminance {
    const val MAX_LONG_SIDE = 1600

    /**
     * The Y (luma) plane in [y] of a [width] × [height] picture, with the plane's [rowStride] and [pixelStride] in bytes,
     * turned [quarterTurns] × 90° clockwise (0..3; the camera's sensor orientation).
     */
    fun fromYPlane(y: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, quarterTurns: Int, maxLongSide: Int = MAX_LONG_SIDE): GrayImage {
        require(rowStride >= (width - 1) * pixelStride + 1 && pixelStride >= 1) { "the Y plane strides do not fit the picture" }
        val bytes = ByteArray(if (pixelStride == 1) width else 0)
        return fromRows(width, height, quarterTurns, maxLongSide) { row, out ->
            val start = row * rowStride
            if (pixelStride == 1) {
                y.duplicate().apply { position(start) }.get(bytes, 0, width)
                for (x in 0 until width) out[x] = (bytes[x].toInt() and 0xFF) / 255f
            } else {
                for (x in 0 until width) out[x] = (y.get(start + x * pixelStride).toInt() and 0xFF) / 255f
            }
        }
    }

    /** Luma 0..1 of ARGB pixels [argb] (ints as in android.graphics.Color) into [out], one value per pixel. */
    fun argbRow(argb: IntArray, out: FloatArray, count: Int = out.size) {
        for (i in 0 until count) {
            val p = argb[i]
            out[i] = (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f
        }
    }

    /**
     * A [width] × [height] picture whose row `y` is filled by [row] (values 0..1), shrunk to [maxLongSide] and turned
     * [quarterTurns] × 90° clockwise. Rows are read once, top to bottom.
     */
    fun fromRows(width: Int, height: Int, quarterTurns: Int, maxLongSide: Int = MAX_LONG_SIDE, row: (y: Int, out: FloatArray) -> Unit): GrayImage {
        require(width > 0 && height > 0 && maxLongSide > 0) { "empty picture" }
        val shrink = max(1.0, max(width, height).toDouble() / maxLongSide)
        val outW = max(1, floor(width / shrink).toInt())
        val outH = max(1, floor(height / shrink).toInt())
        val rx = width.toDouble() / outW
        val ry = height.toDouble() / outH
        // Horizontal: output column ox averages source columns [ox * rx, (ox + 1) * rx) with fractional edges.
        val firstX = IntArray(outW); val lastX = IntArray(outW)
        for (ox in 0 until outW) {
            firstX[ox] = floor(ox * rx).toInt().coerceIn(0, width - 1)
            lastX[ox] = (ceil((ox + 1) * rx).toInt() - 1).coerceIn(firstX[ox], width - 1)
        }
        val src = FloatArray(width)
        val line = FloatArray(outW)
        val acc = FloatArray(outW * outH)
        for (sy in 0 until height) {
            row(sy, src)
            for (ox in 0 until outW) {
                var sum = 0.0
                for (sx in firstX[ox]..lastX[ox]) {
                    val overlap = min(sx + 1.0, (ox + 1) * rx) - max(sx.toDouble(), ox * rx)
                    if (overlap > 0) sum += src[sx] * overlap
                }
                line[ox] = (sum / rx).toFloat()
            }
            // Vertical: the source row [sy, sy + 1) is shared between the output rows it overlaps.
            val oy0 = floor(sy / ry).toInt().coerceIn(0, outH - 1)
            val oy1 = (ceil((sy + 1) / ry).toInt() - 1).coerceIn(oy0, outH - 1)
            for (oy in oy0..oy1) {
                val overlap = min(sy + 1.0, (oy + 1) * ry) - max(sy.toDouble(), oy * ry)
                if (overlap <= 0) continue
                val wgt = (overlap / ry).toFloat()
                val base = oy * outW
                for (ox in 0 until outW) acc[base + ox] += line[ox] * wgt
            }
        }
        for (i in acc.indices) acc[i] = acc[i].coerceIn(0f, 1f)
        return rotated(GrayImage(outW, outH, acc), quarterTurns)
    }

    /** [img] turned [quarterTurns] × 90° clockwise (any integer; 0 returns [img] itself). */
    fun rotated(img: GrayImage, quarterTurns: Int): GrayImage {
        val q = quarterTurns.mod(4)
        if (q == 0) return img
        val w = img.width
        val h = img.height
        val out = FloatArray(w * h)
        val nw = if (q == 2) w else h
        for (y in 0 until h) for (x in 0 until w) {
            // Source (x, y) lands at (nx, ny) after a clockwise turn.
            val nx: Int; val ny: Int
            when (q) {
                1 -> { nx = h - 1 - y; ny = x }
                2 -> { nx = w - 1 - x; ny = h - 1 - y }
                else -> { nx = y; ny = w - 1 - x }
            }
            out[ny * nw + nx] = img.pixels[y * w + x]
        }
        return if (q == 2) GrayImage(w, h, out) else GrayImage(h, w, out)
    }
}
