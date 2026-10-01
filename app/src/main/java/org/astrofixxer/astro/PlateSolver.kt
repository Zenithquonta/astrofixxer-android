package org.astrofixxer.astro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * What the caller knows about the picture.
 *
 * [scaleMinDegPerPx]..[scaleMaxDegPerPx] is the pixel scale at the image centre (from the telescope, eyepiece or phone
 * camera settings); the solver only considers scales in this range, so a wrong range ends in [SolveResult.Failed]
 * (in the synthetic tests it never produced a wrong solution). A centre ([raDeg], [decDeg], J2000) plus [searchRadiusDeg] narrows the search to that disc; with no
 * centre (or a radius of 90 degrees or more) the whole sky is searched, which only works for wide fields (roughly 10
 * degrees across or more). Fields narrower than 3 degrees always need a centre hint.
 */
class SolveHints(
    val scaleMinDegPerPx: Double,
    val scaleMaxDegPerPx: Double,
    val raDeg: Double? = null,
    val decDeg: Double? = null,
    val searchRadiusDeg: Double = 180.0,
) {
    init {
        require(scaleMinDegPerPx > 0 && scaleMaxDegPerPx >= scaleMinDegPerPx) { "invalid scale range" }
        require((raDeg == null) == (decDeg == null)) { "give both raDeg and decDeg, or neither" }
        require(searchRadiusDeg >= 0) { "negative search radius" }
    }
}

sealed class SolveResult {
    /**
     * A verified solution. ([raDeg], [decDeg]) is the J2000 position of the image centre (width/2, height/2);
     * [rollDeg] is the position angle of the image "up" direction (towards -y), measured from north through east;
     * [scaleDegPerPx] is the pixel scale at the centre; [mirrored] is true when the image is a mirror image of the sky
     * (north up then puts east on the right). [imageWidth]/[imageHeight] are needed by the pixel conversions.
     * [falseAlarmProbability] is the chance that random stars would have matched at least this well, summed over
     * everything the search tried.
     */
    class Solved(
        val raDeg: Double, val decDeg: Double, val rollDeg: Double, val scaleDegPerPx: Double, val mirrored: Boolean,
        val matchedStars: Int, val rmsArcsec: Double, val stars: List<Pair<DetectedStar, SkyStar>>,
        val imageWidth: Int = 0, val imageHeight: Int = 0, val falseAlarmProbability: Double = Double.NaN,
    ) : SolveResult() {
        private val ra0 = Math.toRadians(raDeg)
        private val dec0 = Math.toRadians(decDeg)
        private val tanScale = tan(Math.toRadians(scaleDegPerPx))
        private val sinRoll = sin(Math.toRadians(rollDeg))
        private val cosRoll = cos(Math.toRadians(rollDeg))
        // Unit vectors of image "right" and "up" in the (east, north) tangent plane.
        private val rightE = if (mirrored) cosRoll else -cosRoll
        private val rightN = if (mirrored) -sinRoll else sinRoll

        /** J2000 (ra, dec) in degrees of image position ([x], [y]). */
        fun pixelToRaDec(x: Double, y: Double): Pair<Double, Double> {
            val px = x - imageWidth / 2.0
            val py = y - imageHeight / 2.0
            val xi = tanScale * (px * rightE - py * sinRoll)
            val eta = tanScale * (px * rightN - py * cosRoll)
            val cosD = cos(dec0)
            val sinD = sin(dec0)
            val ra = ra0 + atan2(xi, cosD - eta * sinD)
            val dec = atan2(sinD + eta * cosD, hypot(xi, cosD - eta * sinD))
            return Pair(Math.toDegrees(ra).mod(360.0), Math.toDegrees(dec))
        }

        /** Image position of J2000 ([ra], [dec]) degrees, or null when that point is more than 90 degrees from the centre. */
        fun raDecToPixel(ra: Double, dec: Double): Pair<Double, Double>? {
            val a = Math.toRadians(ra) - ra0
            val d = Math.toRadians(dec)
            val cosC = sin(dec0) * sin(d) + cos(dec0) * cos(d) * cos(a)
            if (cosC <= 1e-6) return null
            val xi = cos(d) * sin(a) / cosC
            val eta = (cos(dec0) * sin(d) - sin(dec0) * cos(d) * cos(a)) / cosC
            val px = (xi * rightE + eta * rightN) / tanScale
            val py = -(xi * sinRoll + eta * cosRoll) / tanScale
            return Pair(imageWidth / 2.0 + px, imageHeight / 2.0 + py)
        }
    }

    /** No solution; [reason] says why and [detectedStars] how many stars were found in the image. */
    class Failed(val reason: Reason, val detectedStars: Int) : SolveResult()

    enum class Reason {
        /** Fewer than four usable stars in the picture. */
        TOO_FEW_STARS,
        /** Mostly saturated, or the sky background is very bright. */
        IMAGE_TOO_BRIGHT,
        /** Stars are streaks (tracking, shake, long exposure). */
        STARS_TRAILED,
        /** Stars found but no verified match in the catalogue (wrong scale range, wrong area, or too few catalogue stars). */
        NO_MATCH,
    }
}

/**
 * Offline plate solver: finds stars in a picture and matches triangles of them against a star catalogue, using real
 * angular distances and a gnomonic (TAN) projection. Pure Kotlin/JVM.
 *
 * A result is only returned as [SolveResult.Solved] if at least six stars match and the probability that random stars
 * would match this well (binomial, with the catalogue density in the field and the matching tolerance, multiplied by the
 * number of hypotheses tried) is below 1e-6; otherwise the answer is [SolveResult.Failed]. The search gives up after
 * about 25 seconds.
 */
object PlateSolver {
    /** Stars found in [img], brightest first. */
    fun detectStars(img: GrayImage): List<DetectedStar> = StarDetector.detect(img).stars

    fun solve(img: GrayImage, hints: SolveHints, stars: StarSource): SolveResult = Solver(img, hints, stars).run()
}

// ------------------------------------------------------------------------------------------------------------------

private const val D2R = PI / 180
private const val MAX_FALSE_ALARM = 1e-6
private const val MIN_MATCHES = 6
private const val TIME_BUDGET_NS = 25_000_000_000L
private const val TRAIL_ELONGATION = 1.7
private const val MAX_USED_DETECTIONS = 60

private val lnFactorial = DoubleArray(1001).also { for (i in 1..1000) it[i] = it[i - 1] + ln(i.toDouble()) }

/** P(X >= m) for X ~ Binomial(n, p). */
internal fun binomialTail(n: Int, m: Int, p: Double): Double {
    if (m <= 0) return 1.0
    if (m > n) return 0.0
    val pp = p.coerceIn(1e-15, 1 - 1e-12)
    val lp = ln(pp)
    val lq = ln1p(-pp)
    var sum = 0.0
    for (k in m..n) sum += exp(lnFactorial[n] - lnFactorial[k] - lnFactorial[n - k] + k * lp + (n - k) * lq)
    return min(1.0, sum)
}

/** The K brightest stars in a cone, asking the source with a growing magnitude limit so a deep source stays cheap. */
internal fun brightestStars(source: StarSource, ra: Double, dec: Double, radius: Double, k: Int): List<SkyStar> {
    var got: List<SkyStar> = emptyList()
    for (limit in doubleArrayOf(3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.5, 99.0)) {
        got = source.cone(ra, dec, radius, limit)
        if (got.size >= k) break
    }
    return got.sortedBy { it.mag }.let { if (it.size > k) it.subList(0, k) else it }
}

/** Catalogue stars as unit vectors, brightest first. */
private class Stars(list: List<SkyStar>) {
    val stars: List<SkyStar> = list.sortedBy { it.mag }
    val n = stars.size
    val x = DoubleArray(n)
    val y = DoubleArray(n)
    val z = DoubleArray(n)

    init {
        for (i in 0 until n) {
            val ra = stars[i].raDeg * D2R
            val de = stars[i].decDeg * D2R
            x[i] = cos(de) * cos(ra); y[i] = cos(de) * sin(ra); z[i] = sin(de)
        }
    }
}

/**
 * Sky <-> image model: gnomonic projection about the unit vector c0 (east e, north n), then a similarity with parity,
 * pixel offset from the image centre (X, Y) = a * (xi + i eta) + b   (or a * conj(xi + i eta) + b when mirrored).
 */
private class Pose(cx: Double, cy: Double, cz: Double, var ar: Double, var ai: Double, var br: Double, var bi: Double, val mirrored: Boolean) {
    var c0x = 0.0; var c0y = 0.0; var c0z = 0.0
    var ex = 0.0; var ey = 0.0; var ez = 0.0
    var nx = 0.0; var ny = 0.0; var nz = 0.0

    init { setCentre(cx, cy, cz) }

    fun setCentre(x: Double, y: Double, z: Double) {
        val l = sqrt(x * x + y * y + z * z)
        c0x = x / l; c0y = y / l; c0z = z / l
        val h = hypot(c0x, c0y)
        if (h < 1e-9) { ex = 0.0; ey = 1.0; ez = 0.0 } else { ex = -c0y / h; ey = c0x / h; ez = 0.0 }
        nx = c0y * ez - c0z * ey; ny = c0z * ex - c0x * ez; nz = c0x * ey - c0y * ex
    }

    /** Tangent-plane coordinates of a unit vector into out[0], out[1]; false when it is behind the plane. */
    fun tangent(px: Double, py: Double, pz: Double, out: DoubleArray): Boolean {
        val cosC = px * c0x + py * c0y + pz * c0z
        if (cosC < 0.05) return false
        out[0] = (px * ex + py * ey + pz * ez) / cosC
        out[1] = (px * nx + py * ny + pz * nz) / cosC
        return true
    }

    fun pixelX(xi: Double, eta: Double) = if (mirrored) ar * xi + ai * eta + br else ar * xi - ai * eta + br
    fun pixelY(xi: Double, eta: Double) = if (mirrored) ai * xi - ar * eta + bi else ai * xi + ar * eta + bi

    /** Unit vector of the sky point whose image offset is zero (the image centre) in this model. */
    fun centreOfImage(out: DoubleArray) {
        // unmirrored: zeta = -b / a ; mirrored: zeta = conj(-b / a)
        val d = ar * ar + ai * ai
        val zr = -(br * ar + bi * ai) / d
        val zi = -(bi * ar - br * ai) / d
        val xi = zr
        val eta = if (mirrored) -zi else zi
        val x = c0x + xi * ex + eta * nx
        val y = c0y + xi * ey + eta * ny
        val z = c0z + xi * ez + eta * nz
        val l = sqrt(x * x + y * y + z * z)
        out[0] = x / l; out[1] = y / l; out[2] = z / l
    }

    /**
     * Moves the tangent point to the sky position of the image centre. The east/north axes turn relative to each
     * other when the tangent point moves (meridians converge), so the rotation in [ar]/[ai] is corrected; the
     * offset becomes zero.
     */
    fun recentre(scratch: DoubleArray) {
        centreOfImage(scratch)
        val oex = ex; val oey = ey; val oez = ez
        val onx = nx; val ony = ny; val onz = nz
        setCentre(scratch[0], scratch[1], scratch[2])
        // new east / north axes in components of the old ones: zeta_old = (rr + i ri) * zeta_new
        val rr = ex * oex + ey * oey + ez * oez
        val ri = ex * onx + ey * ony + ez * onz
        // unmirrored: z = a zeta_old + b = (a (rr + i ri)) zeta_new ; mirrored: a conj(rr + i ri)
        val sgn = if (mirrored) -1.0 else 1.0
        val nar = ar * rr - sgn * ai * ri
        val nai = ai * rr + sgn * ar * ri
        ar = nar; ai = nai
        br = 0.0; bi = 0.0
    }

    fun copy() = Pose(c0x, c0y, c0z, ar, ai, br, bi, mirrored)
}

private class Refined(
    val pose: Pose, val detIdx: IntArray, val catIdx: IntArray, val matches: Int, val rmsPx: Double, val inImage: Int,
)

private class Solver(val img: GrayImage, val hints: SolveHints, val source: StarSource) {
    val w = img.width
    val h = img.height
    val startTime = System.nanoTime()
    var hypotheses = 0L

    // detections used for solving
    var nDet = 0
    lateinit var dx: DoubleArray
    lateinit var dy: DoubleArray
    lateinit var dTol: DoubleArray
    lateinit var dets: List<DetectedStar>
    var t0 = 2.0
    val kDist = 0.002
    var sigmaPos = 0.5

    fun tol(r: Double) = t0 + kDist * r
    fun timeUp() = System.nanoTime() - startTime > TIME_BUDGET_NS
    fun halfDiagDeg(sDeg: Double) = Math.toDegrees(atan(0.5 * hypot(w.toDouble(), h.toDouble()) * tan(sDeg * D2R)))
    fun fieldSr(sDeg: Double): Double { val t = tan(sDeg * D2R); return w * t * h * t }
    fun capSr(radiusDeg: Double) = 2 * PI * (1 - cos(min(radiusDeg, 180.0) * D2R))

    fun run(): SolveResult {
        val det = StarDetector.detect(img)
        val all = det.stars
        if (det.saturatedFraction > 0.25 || det.medianLevel > 0.6) return SolveResult.Failed(SolveResult.Reason.IMAGE_TOO_BRIGHT, all.size)
        if (all.size >= 4) {
            val elong = all.take(30).map { it.elongation }.sorted()
            if (elong[elong.size / 2] > TRAIL_ELONGATION) return SolveResult.Failed(SolveResult.Reason.STARS_TRAILED, all.size)
        }
        val usable = all.filter { it.elongation <= 2.5 }
        if (usable.size < 4) return SolveResult.Failed(SolveResult.Reason.TOO_FEW_STARS, all.size)
        dets = usable.take(MAX_USED_DETECTIONS)
        nDet = dets.size
        val cxp = w / 2.0
        val cyp = h / 2.0
        dx = DoubleArray(nDet) { dets[it].x - cxp }
        dy = DoubleArray(nDet) { dets[it].y - cyp }
        val sig = dets.take(15).map { it.sigma }.filter { !it.isNaN() }.sorted()
        val sigmaStar = if (sig.isEmpty()) 1.5 else sig[sig.size / 2]
        t0 = (1.0 + 0.6 * sigmaStar).coerceIn(1.5, 6.0)
        sigmaPos = 0.35 + 0.15 * sigmaStar
        dTol = DoubleArray(nDet) { tol(hypot(dx[it], dy[it])) }

        val found = search()
        return found ?: SolveResult.Failed(SolveResult.Reason.NO_MATCH, usable.size)
    }

    // ---------------------------------------------------------------------------------------------------- search

    private class Tile(val x: Double, val y: Double, val z: Double)

    private fun search(): SolveResult? {
        val sLo = hints.scaleMinDegPerPx
        val sHi = hints.scaleMaxDegPerPx
        val sMid = sqrt(sLo * sHi)
        val rho = halfDiagDeg(sHi)
        val blind = hints.raDeg == null || hints.searchRadiusDeg >= 90.0
        if (blind && 2 * rho < 3.0) return null // narrow fields need a centre
        val spacing = 0.5 * rho
        val tiles = ArrayList<Tile>()
        val rTile: Double
        val master: Stars
        val wide = rho > 12.0
        val kMasterPer = 3.0 * nDet
        if (blind) {
            val n = ceil(4 * PI / (0.866 * (spacing * D2R).pow(2))).toInt()
            if (n > 12000) return null
            val golden = PI * (3 - sqrt(5.0))
            for (i in 0 until n) {
                val zz = 1 - 2 * (i + 0.5) / n
                val r = sqrt(1 - zz * zz)
                tiles.add(Tile(r * cos(golden * i), r * sin(golden * i), zz))
            }
            rTile = min(89.0, rho + 0.75 * spacing)
            val k = (kMasterPer * 4 * PI / fieldSr(sMid)).toInt().coerceIn(40, 6000)
            master = Stars(brightestStars(source, 0.0, 0.0, 180.0, k))
        } else {
            val ra0 = hints.raDeg!! * D2R
            val de0 = hints.decDeg!! * D2R
            val r = hints.searchRadiusDeg
            val n = if (r <= 0) 1 else max(1, ceil(PI * r * r / (0.866 * spacing * spacing)).toInt())
            val golden = 137.50776405 * D2R
            for (i in 0 until n) {
                val d = if (n == 1) 0.0 else r * D2R * sqrt((i + 0.5) / n)
                val phi = golden * i
                val dec = asin(sin(de0) * cos(d) + cos(de0) * sin(d) * cos(phi))
                val ra = ra0 + atan2(sin(phi) * sin(d) * cos(de0), cos(d) - sin(de0) * sin(dec))
                tiles.add(Tile(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec)))
            }
            rTile = rho + min(r, 0.75 * spacing)
            val rMaster = min(180.0, r + rTile)
            val k = (kMasterPer * capSr(rMaster) / fieldSr(sMid)).toInt().coerceIn(40, 6000)
            master = Stars(brightestStars(source, hints.raDeg!!, hints.decDeg!!, rMaster, k))
        }
        if (master.n < MIN_MATCHES) return null

        val passes = arrayOf(intArrayOf(10, 30), intArrayOf(14, 50))
        for (pass in passes) {
            val res = searchPass(pass[0], pass[1], tiles, rTile, master, sLo, sHi, wide)
            if (res != null) return res
            if (timeUp()) return null
        }
        return null
    }

    // An image triangle: vertices ordered (opposite shortest side, opposite middle, opposite longest).
    private class ImgTri(
        val v: IntArray, val s: DoubleArray, val u: Double, val vv: Double, val tau: Double, val sign: Int,
        val cLo: Double, val cHi: Double,
    )

    private fun searchPass(
        ki: Int, kt: Int, tiles: List<Tile>, rTile: Double, master: Stars, sLo: Double, sHi: Double, wide: Boolean,
    ): SolveResult? {
        val ratio = sHi / sLo
        val nBins = if (!wide || ratio <= 1.12) 1 else ceil(ln(ratio) / ln(1.12)).toInt()
        val nTop = min(ki, nDet)
        if (nTop < 4) return null
        val minSide = 0.05 * min(w, h)
        val imgTris = ArrayList<ImgTri>()
        var cLoAll = Double.MAX_VALUE
        var cHiAll = 0.0
        for (b in 0 until nBins) {
            val lo = sLo * ratio.pow(b.toDouble() / nBins)
            val hi = sLo * ratio.pow((b + 1.0) / nBins)
            val mid = sqrt(lo * hi)
            val t = tan(mid * D2R)
            // unproject the brightest detections into a camera frame with focal length 1/t
            val ux = DoubleArray(nTop); val uy = DoubleArray(nTop); val uz = DoubleArray(nTop)
            for (i in 0 until nTop) {
                val l = sqrt(1 + t * t * (dx[i] * dx[i] + dy[i] * dy[i]))
                ux[i] = dx[i] * t / l; uy[i] = dy[i] * t / l; uz[i] = 1 / l
            }
            fun angle(i: Int, j: Int): Double {
                val cx = uy[i] * uz[j] - uz[i] * uy[j]; val cy = uz[i] * ux[j] - ux[i] * uz[j]; val cz = ux[i] * uy[j] - uy[i] * ux[j]
                return atan2(sqrt(cx * cx + cy * cy + cz * cz), ux[i] * ux[j] + uy[i] * uy[j] + uz[i] * uz[j])
            }
            for (i in 0 until nTop) for (j in i + 1 until nTop) for (k in j + 1 until nTop) {
                // sides opposite each vertex, in pixels for the size filters and in angle for the matching
                val pij = hypot(dx[i] - dx[j], dy[i] - dy[j]); val pjk = hypot(dx[j] - dx[k], dy[j] - dy[k]); val pik = hypot(dx[i] - dx[k], dy[i] - dy[k])
                val vert = intArrayOf(i, j, k)
                val side = doubleArrayOf(pjk, pik, pij) // opposite i, j, k
                val ord = arrayOf(0, 1, 2).sortedBy { side[it] }
                val sa = side[ord[0]]; val sc = side[ord[2]]
                if (sa < minSide) continue
                if (flatness(side[ord[0]], side[ord[1]], sc) < 0.2) continue
                val ang = doubleArrayOf(angle(j, k), angle(i, k), angle(i, j))
                val a = ang[ord[0]]; val bb = ang[ord[1]]; val c = ang[ord[2]]
                val tau = 2.5 * sigmaPos / sa + 0.004 + (if (wide) 0.006 else 0.0)
                if (tau > 0.06) continue
                val va = vert[ord[0]]; val vb = vert[ord[1]]; val vc = vert[ord[2]]
                val cross = (dx[vb] - dx[va]) * (dy[vc] - dy[va]) - (dy[vb] - dy[va]) * (dx[vc] - dx[va])
                val cLo = c * lo / mid * 0.98
                val cHi = c * hi / mid * 1.02
                cLoAll = min(cLoAll, cLo); cHiAll = max(cHiAll, cHi)
                imgTris.add(ImgTri(intArrayOf(va, vb, vc), doubleArrayOf(a, bb, c), bb / c, a / c, tau, if (cross > 0) 1 else -1, cLo, cHi))
            }
        }
        if (imgTris.isEmpty()) return null

        val nTile = master.n
        val inTile = IntArray(nTile)
        val cosTile = cos(rTile * D2R)
        val cat = CatTris(kt)
        for (tile in tiles) {
            if (timeUp()) return null
            var m = 0
            for (i in 0 until nTile) if (master.x[i] * tile.x + master.y[i] * tile.y + master.z[i] * tile.z >= cosTile) inTile[m++] = i
            if (m < 3) continue
            val res = tileSearch(tile, inTile, m, master, cat, imgTris, cLoAll, cHiAll, kt, sLo, sHi)
            if (res != null) return res
        }
        return null
    }

    private fun flatness(a: Double, b: Double, c: Double): Double {
        val s = 0.5 * (a + b + c)
        val area = sqrt(max(0.0, s * (s - a) * (s - b) * (s - c)))
        return 2 * area / (c * c)
    }

    /** Catalogue triangles of one tile in a shape-bucket grid (u = middle/longest, v = shortest/longest, 64x64 cells). */
    private class CatTris(kt: Int) {
        val cap = kt * (kt - 1) * (kt - 2) / 6
        val va = IntArray(cap); val vb = IntArray(cap); val vc = IntArray(cap)
        val sa = DoubleArray(cap); val sb = DoubleArray(cap); val sc = DoubleArray(cap)
        val sign = ByteArray(cap)
        val cell = IntArray(cap)
        val order = IntArray(cap)
        val start = IntArray(64 * 64 + 1)
        var n = 0
    }

    private fun tileSearch(
        tile: Tile, idx: IntArray, m: Int, master: Stars, cat: CatTris, imgTris: List<ImgTri>,
        cLoAll: Double, cHiAll: Double, kt: Int, sLo: Double, sHi: Double,
    ): SolveResult? {
        val top = min(m, kt)
        // pair separations
        val sep = DoubleArray(top * top)
        for (i in 0 until top) for (j in i + 1 until top) {
            val p = idx[i]; val q = idx[j]
            val cx = master.y[p] * master.z[q] - master.z[p] * master.y[q]
            val cy = master.z[p] * master.x[q] - master.x[p] * master.z[q]
            val cz = master.x[p] * master.y[q] - master.y[p] * master.x[q]
            val d = atan2(sqrt(cx * cx + cy * cy + cz * cz), master.x[p] * master.x[q] + master.y[p] * master.y[q] + master.z[p] * master.z[q])
            sep[i * top + j] = d; sep[j * top + i] = d
        }
        var n = 0
        for (i in 0 until top) for (j in i + 1 until top) for (k in j + 1 until top) {
            val dij = sep[i * top + j]; val djk = sep[j * top + k]; val dik = sep[i * top + k]
            val big = max(dij, max(djk, dik))
            if (big < cLoAll || big > cHiAll) continue
            // opposite-side ordering: side opposite i is djk, opposite j is dik, opposite k is dij
            val vert = intArrayOf(i, j, k)
            val side = doubleArrayOf(djk, dik, dij)
            var o0 = 0; var o1 = 1; var o2 = 2
            if (side[o0] > side[o1]) { val t = o0; o0 = o1; o1 = t }
            if (side[o1] > side[o2]) { val t = o1; o1 = o2; o2 = t }
            if (side[o0] > side[o1]) { val t = o0; o0 = o1; o1 = t }
            val a = side[o0]; val b = side[o1]; val c = side[o2]
            if (flatness(a, b, c) < 0.15) continue
            val pa = idx[vert[o0]]; val pb = idx[vert[o1]]; val pc = idx[vert[o2]]
            val det = master.x[pa] * (master.y[pb] * master.z[pc] - master.z[pb] * master.y[pc]) -
                master.y[pa] * (master.x[pb] * master.z[pc] - master.z[pb] * master.x[pc]) +
                master.z[pa] * (master.x[pb] * master.y[pc] - master.y[pb] * master.x[pc])
            cat.va[n] = vert[o0]; cat.vb[n] = vert[o1]; cat.vc[n] = vert[o2]
            cat.sa[n] = a; cat.sb[n] = b; cat.sc[n] = c
            cat.sign[n] = if (det > 0) 1 else -1
            val cu = min(63, (b / c * 64).toInt()); val cv = min(63, (a / c * 64).toInt())
            cat.cell[n] = cu * 64 + cv
            n++
        }
        cat.n = n
        if (n == 0) return null
        java.util.Arrays.fill(cat.start, 0)
        for (i in 0 until n) cat.start[cat.cell[i] + 1]++
        for (i in 0 until 64 * 64) cat.start[i + 1] += cat.start[i]
        val fill = cat.start.copyOf(64 * 64)
        for (i in 0 until n) cat.order[fill[cat.cell[i]]++] = i

        val perms = arrayOf(intArrayOf(0, 1, 2), intArrayOf(1, 0, 2), intArrayOf(0, 2, 1), intArrayOf(2, 1, 0), intArrayOf(1, 2, 0), intArrayOf(2, 0, 1))
        val permSign = intArrayOf(1, -1, -1, -1, 1, 1)
        val tv = IntArray(3)
        for (tri in imgTris) {
            val u0 = max(0, floor((tri.u - tri.tau) * 64).toInt()); val u1 = min(63, floor((tri.u + tri.tau) * 64).toInt())
            val v0 = max(0, floor((tri.vv - tri.tau) * 64).toInt()); val v1 = min(63, floor((tri.vv + tri.tau) * 64).toInt())
            for (cu in u0..u1) for (cv in v0..v1) {
                val cellId = cu * 64 + cv
                for (q in cat.start[cellId] until cat.start[cellId + 1]) {
                    val ti = cat.order[q]
                    val c = cat.sc[ti]
                    if (c < tri.cLo || c > tri.cHi) continue
                    val cu2 = cat.sb[ti] / c; val cv2 = cat.sa[ti] / c
                    if (abs(cu2 - tri.u) > tri.tau || abs(cv2 - tri.vv) > tri.tau) continue
                    tv[0] = cat.va[ti]; tv[1] = cat.vb[ti]; tv[2] = cat.vc[ti]
                    val cs = doubleArrayOf(cat.sa[ti], cat.sb[ti], cat.sc[ti])
                    for (pi in perms.indices) {
                        val p = perms[pi]
                        // image side k (opposite image vertex k) must equal catalogue side opposite vertex p[k]
                        var ok = true
                        for (kk in 0 until 3) {
                            // side opposite catalogue vertex tv[p[kk]] is cs[p[kk]]
                            if (abs(cs[p[kk]] / c - tri.s[kk] / tri.s[2]) > tri.tau) { ok = false; break }
                        }
                        if (!ok) continue
                        val catSign = cat.sign[ti] * permSign[pi]
                        val mirrored = tri.sign != catSign
                        hypotheses++
                        val res = tryHypothesis(tri, intArrayOf(idx[tv[p[0]]], idx[tv[p[1]]], idx[tv[p[2]]]), mirrored, tile, idx, m, master, sLo, sHi)
                        if (res != null) return res
                    }
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------------------------------- hypotheses

    private val tmp = DoubleArray(3)

    private fun tryHypothesis(
        tri: ImgTri, catVert: IntArray, mirrored: Boolean, tile: Tile, idx: IntArray, m: Int, master: Stars,
        sLo: Double, sHi: Double,
    ): SolveResult? {
        // initial similarity from the three vertex pairs, tangent plane about their centroid
        val cx = master.x[catVert[0]] + master.x[catVert[1]] + master.x[catVert[2]]
        val cy = master.y[catVert[0]] + master.y[catVert[1]] + master.y[catVert[2]]
        val cz = master.z[catVert[0]] + master.z[catVert[1]] + master.z[catVert[2]]
        val pose = Pose(cx, cy, cz, 1.0, 0.0, 0.0, 0.0, mirrored)
        val xi = DoubleArray(3); val eta = DoubleArray(3); val px = DoubleArray(3); val py = DoubleArray(3)
        for (k in 0 until 3) {
            if (!pose.tangent(master.x[catVert[k]], master.y[catVert[k]], master.z[catVert[k]], tmp)) return null
            xi[k] = tmp[0]; eta[k] = tmp[1]
            px[k] = dx[tri.v[k]]; py[k] = dy[tri.v[k]]
        }
        val fit = fitSimilarity(xi, eta, px, py, 3, mirrored) ?: return null
        pose.ar = fit[0]; pose.ai = fit[1]; pose.br = fit[2]; pose.bi = fit[3]
        val scale0 = atan(1 / hypot(pose.ar, pose.ai)) / D2R
        if (scale0 < sLo / 1.1 || scale0 > sHi * 1.1) return null

        // cheap test: do the brighter detections line up with the tile's catalogue stars under this pose?
        val nE = min(16, nDet)
        var hit = 0L
        var count = 0
        val lim = min(m, 120)
        for (j in 0 until lim) {
            val s = idx[j]
            if (!pose.tangent(master.x[s], master.y[s], master.z[s], tmp)) continue
            val X = pose.pixelX(tmp[0], tmp[1]); val Y = pose.pixelY(tmp[0], tmp[1])
            if (abs(X) > w / 2.0 + 20 || abs(Y) > h / 2.0 + 20) continue
            for (i in 0 until nE) {
                val ddx = dx[i] - X; val ddy = dy[i] - Y
                val r = 3 * dTol[i]
                if (ddx * ddx + ddy * ddy < r * r) { if (hit and (1L shl i) == 0L) { hit = hit or (1L shl i); count++ }; break }
            }
        }
        if (count < max(4, min(6, nE - 3))) return null

        // full refinement on the tile's catalogue stars
        val list = IntArray(m) { idx[it] }
        val refined = refine(pose, master, list, m, doubleArrayOf(3.0, 2.0, 1.5, 1.2, 1.0)) ?: return null
        // The tile list is shallow (only the brightest stars made triangles), so this is only a screen: the strict
        // acceptance test runs in polish() on a fresh, deeper list around the solution.
        if (refined.matches < 4) return null
        val pfa0 = significance(refined)
        if (!(pfa0 * hypotheses < 1e-3)) return null
        return polish(refined, sLo, sHi)
    }

    /** Refit with a fresh, deeper catalogue list around the solution, then apply the full acceptance test. */
    private fun polish(seed: Refined, sLo: Double, sHi: Double): SolveResult? {
        val c = DoubleArray(3)
        seed.pose.centreOfImage(c)
        val ra = Math.toDegrees(atan2(c[1], c[0])).mod(360.0)
        val dec = Math.toDegrees(asin(c[2].coerceIn(-1.0, 1.0)))
        val sDeg = atan(1 / hypot(seed.pose.ar, seed.pose.ai)) / D2R
        val rho = halfDiagDeg(sDeg) * 1.1 + 0.02
        val k = (3.0 * nDet * capSr(rho) / fieldSr(sDeg)).toInt().coerceIn(40, 6000)
        val master = Stars(brightestStars(source, ra, dec, rho, k))
        if (master.n < MIN_MATCHES) return null
        val list = IntArray(master.n) { it }
        val pose = seed.pose.copy()
        val refined = refine(pose, master, list, master.n, doubleArrayOf(2.0, 1.5, 1.2, 1.0)) ?: return null
        if (refined.matches < MIN_MATCHES || !plausible(refined, sLo, sHi)) return null
        val pfa = significance(refined)
        val total = pfa * max(1L, hypotheses)
        if (!(total < MAX_FALSE_ALARM)) return null

        // assemble the result
        val p = refined.pose
        p.centreOfImage(c)
        val raC = Math.toDegrees(atan2(c[1], c[0])).mod(360.0)
        val decC = Math.toDegrees(asin(c[2].coerceIn(-1.0, 1.0)))
        val scale = atan(1 / hypot(p.ar, p.ai)) / D2R
        val roll = (if (p.mirrored) -atan2(p.ai, p.ar) else atan2(p.ai, p.ar) - PI) / D2R
        val pairs = (0 until refined.matches).map { Pair(dets[refined.detIdx[it]], master.stars[refined.catIdx[it]]) }
        return SolveResult.Solved(
            raC, decC, roll.mod(360.0), scale, p.mirrored, refined.matches, refined.rmsPx * scale * 3600.0, pairs,
            w, h, total,
        )
    }

    private fun plausible(r: Refined, sLo: Double, sHi: Double): Boolean {
        val s = atan(1 / hypot(r.pose.ar, r.pose.ai)) / D2R
        if (s < sLo * 0.97 || s > sHi * 1.03) return false
        // matched stars must be spread over the picture, not one clump
        var span = 0.0
        for (a in 0 until r.matches) for (b in a + 1 until r.matches) {
            val i = r.detIdx[a]; val j = r.detIdx[b]
            span = max(span, hypot(dx[i] - dx[j], dy[i] - dy[j]))
        }
        return span >= 0.25 * min(w, h)
    }

    /** Probability that random stars would match at least this many detections at the matching tolerance. */
    private fun significance(r: Refined): Double {
        val density = r.inImage.toDouble() / (w.toDouble() * h)
        var t2 = 0.0
        for (i in 0 until nDet) t2 += dTol[i] * dTol[i]
        val tEff2 = 1.5625 * t2 / nDet // (1.25 * rms tolerance)^2
        val p = 1 - exp(-density * PI * tEff2)
        return binomialTail(nDet, r.matches, p)
    }

    /**
     * Iteratively match detections to projected catalogue stars (radius shrinking from factors[0] to 1 times the
     * tolerance), refit the similarity by least squares and recentre the tangent plane on the image centre.
     */
    private fun refine(start: Pose, master: Stars, list: IntArray, m: Int, factors: DoubleArray): Refined? {
        val pose = start.copy()
        val xi = DoubleArray(m); val eta = DoubleArray(m); val cxp = DoubleArray(m); val cyp = DoubleArray(m)
        val ok = BooleanArray(m)
        val matchDet = IntArray(nDet); val matchCat = IntArray(nDet)
        val catUsed = IntArray(m)
        val fx = DoubleArray(nDet); val fy = DoubleArray(nDet); val fxi = DoubleArray(nDet); val feta = DoubleArray(nDet)
        var lastMatches = 0
        var lastRms = 0.0
        var lastIn = 0
        var iter = 0
        val total = factors.size + 6
        while (iter < total) {
            val f = if (iter < factors.size) factors[iter] else 1.0
            var inImage = 0
            for (j in 0 until m) {
                val s = list[j]
                ok[j] = false
                if (!pose.tangent(master.x[s], master.y[s], master.z[s], tmp)) continue
                xi[j] = tmp[0]; eta[j] = tmp[1]
                cxp[j] = pose.pixelX(tmp[0], tmp[1]); cyp[j] = pose.pixelY(tmp[0], tmp[1])
                if (abs(cxp[j]) <= w / 2.0 && abs(cyp[j]) <= h / 2.0) inImage++
                ok[j] = abs(cxp[j]) <= w / 2.0 + 4 * t0 && abs(cyp[j]) <= h / 2.0 + 4 * t0
            }
            java.util.Arrays.fill(catUsed, -1)
            var n = 0
            // nearest catalogue star for each detection, closest claim wins a star
            val bestD = DoubleArray(nDet) { Double.MAX_VALUE }
            val bestJ = IntArray(nDet) { -1 }
            for (i in 0 until nDet) {
                val r = f * dTol[i]
                var bd = r * r
                var bj = -1
                for (j in 0 until m) {
                    if (!ok[j]) continue
                    val ddx = dx[i] - cxp[j]; val ddy = dy[i] - cyp[j]
                    val d = ddx * ddx + ddy * ddy
                    if (d < bd) { bd = d; bj = j }
                }
                bestJ[i] = bj; bestD[i] = bd
            }
            for (i in 0 until nDet) {
                val j = bestJ[i]
                if (j < 0) continue
                val other = catUsed[j]
                if (other >= 0) { if (bestD[i] < bestD[other]) { bestJ[other] = -1; catUsed[j] = i } else bestJ[i] = -1 } else catUsed[j] = i
            }
            var sse = 0.0
            for (i in 0 until nDet) {
                val j = bestJ[i]
                if (j < 0) continue
                fx[n] = dx[i]; fy[n] = dy[i]; fxi[n] = xi[j]; feta[n] = eta[j]
                matchDet[n] = i; matchCat[n] = j
                sse += bestD[i]
                n++
            }
            lastMatches = n; lastIn = inImage
            lastRms = if (n > 0) sqrt(sse / n) else 0.0
            if (n < 3) return null
            val fit = fitSimilarity(fxi, feta, fx, fy, n, pose.mirrored) ?: return null
            pose.ar = fit[0]; pose.ai = fit[1]; pose.br = fit[2]; pose.bi = fit[3]
            val shift = hypot(pose.br, pose.bi)
            // recentre the tangent plane on the image centre; the similarity keeps its scale and rotation
            pose.recentre(tmp)
            iter++
            val s = atan(1 / hypot(pose.ar, pose.ai)) / D2R
            if (s < hints.scaleMinDegPerPx / 1.3 || s > hints.scaleMaxDegPerPx * 1.3) return null
            if (iter >= factors.size && shift < 0.02) break
        }
        // final assignment with the settled pose (tolerance factor 1)
        var inImage = 0
        for (j in 0 until m) {
            val s = list[j]
            ok[j] = false
            if (!pose.tangent(master.x[s], master.y[s], master.z[s], tmp)) continue
            cxp[j] = pose.pixelX(tmp[0], tmp[1]); cyp[j] = pose.pixelY(tmp[0], tmp[1])
            if (abs(cxp[j]) <= w / 2.0 && abs(cyp[j]) <= h / 2.0) inImage++
            ok[j] = abs(cxp[j]) <= w / 2.0 + 4 * t0 && abs(cyp[j]) <= h / 2.0 + 4 * t0
        }
        java.util.Arrays.fill(catUsed, -1)
        val bestD = DoubleArray(nDet) { Double.MAX_VALUE }
        val bestJ = IntArray(nDet) { -1 }
        for (i in 0 until nDet) {
            var bd = dTol[i] * dTol[i]
            var bj = -1
            for (j in 0 until m) {
                if (!ok[j]) continue
                val ddx = dx[i] - cxp[j]; val ddy = dy[i] - cyp[j]
                val d = ddx * ddx + ddy * ddy
                if (d < bd) { bd = d; bj = j }
            }
            bestJ[i] = bj; bestD[i] = bd
        }
        for (i in 0 until nDet) {
            val j = bestJ[i]
            if (j < 0) continue
            val other = catUsed[j]
            if (other >= 0) { if (bestD[i] < bestD[other]) { bestJ[other] = -1; catUsed[j] = i } else bestJ[i] = -1 } else catUsed[j] = i
        }
        var n = 0
        var sse = 0.0
        for (i in 0 until nDet) if (bestJ[i] >= 0) { matchDet[n] = i; matchCat[n] = bestJ[i]; sse += bestD[i]; n++ }
        if (n < 3) return null
        val catIdx = IntArray(n) { list[matchCat[it]] }
        return Refined(pose, matchDet.copyOf(n), catIdx, n, sqrt(sse / n), inImage)
    }

    /** Least-squares similarity (with parity) mapping (xi, eta) to (X, Y); returns (ar, ai, br, bi). */
    private fun fitSimilarity(xi: DoubleArray, eta: DoubleArray, X: DoubleArray, Y: DoubleArray, n: Int, mirrored: Boolean): DoubleArray? {
        var mzr = 0.0; var mzi = 0.0; var mwr = 0.0; var mwi = 0.0
        for (k in 0 until n) {
            mzr += xi[k]; mzi += if (mirrored) -eta[k] else eta[k]
            mwr += X[k]; mwi += Y[k]
        }
        mzr /= n; mzi /= n; mwr /= n; mwi /= n
        var numR = 0.0; var numI = 0.0; var den = 0.0
        for (k in 0 until n) {
            val zr = xi[k] - mzr
            val zi = (if (mirrored) -eta[k] else eta[k]) - mzi
            val wr = X[k] - mwr
            val wi = Y[k] - mwi
            numR += zr * wr + zi * wi // conj(z) * w
            numI += zr * wi - zi * wr
            den += zr * zr + zi * zi
        }
        if (den < 1e-24) return null
        val ar = numR / den
        val ai = numI / den
        val br = mwr - (ar * mzr - ai * mzi)
        val bi = mwi - (ar * mzi + ai * mzr)
        return doubleArrayOf(ar, ai, br, bi)
    }
}
