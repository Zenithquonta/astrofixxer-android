package org.astrofixxer.astro

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A catalogue star for plate solving: J2000 position in degrees and V magnitude. */
class SkyStar(val raDeg: Double, val decDeg: Double, val mag: Double)

/** Anything the plate solver can ask for the stars around a point of the sky. */
interface StarSource {
    /** Stars within [radiusDeg] of (raDeg, decDeg) (J2000) that are brighter than [magLimit]; any order. */
    fun cone(raDeg: Double, decDeg: Double, radiusDeg: Double, magLimit: Double): List<SkyStar>
}

/** A fixed list of stars (tests, or a source built from another catalogue); brute-force cone search. */
class ListStars(private val stars: List<SkyStar>) : StarSource {
    private val ux = DoubleArray(stars.size)
    private val uy = DoubleArray(stars.size)
    private val uz = DoubleArray(stars.size)

    init {
        stars.forEachIndexed { i, s ->
            val ra = Math.toRadians(s.raDeg)
            val de = Math.toRadians(s.decDeg)
            ux[i] = cos(de) * cos(ra)
            uy[i] = cos(de) * sin(ra)
            uz[i] = sin(de)
        }
    }

    override fun cone(raDeg: Double, decDeg: Double, radiusDeg: Double, magLimit: Double): List<SkyStar> {
        val ra = Math.toRadians(raDeg)
        val de = Math.toRadians(decDeg)
        val cx = cos(de) * cos(ra)
        val cy = cos(de) * sin(ra)
        val cz = sin(de)
        val cosR = cos(Math.toRadians(min(radiusDeg, 180.0)))
        val out = ArrayList<SkyStar>()
        for (i in stars.indices) {
            if (stars[i].mag <= magLimit && ux[i] * cx + uy[i] * cy + uz[i] * cz >= cosR) out.add(stars[i])
        }
        return out
    }
}

/**
 * The stars of the bright catalogue ([Catalog], HYG-based, complete to about V 6.5) as a [StarSource], so wide fields
 * do not need the deep file. Only objects with type "S" and a magnitude are used.
 */
class CatalogStars(catalog: Catalog) : StarSource {
    private val list = ListStars(catalog.objects.filter { it.type == "S" && it.mag != null }
        .map { SkyStar(it.ra, it.dec, it.mag!!) })

    override fun cone(raDeg: Double, decDeg: Double, radiusDeg: Double, magLimit: Double) =
        list.cone(raDeg, decDeg, radiusDeg, magLimit)
}

/**
 * The deep star list of the plate solver, `assets/solver_stars.bin`, written by
 * `tools/stellarium_import/build_sky_data.py` from Stellarium's Gaia-DR3-based catalogues (`stars/hip_gaia3`, data from
 * ESA Gaia DR3 and Hipparcos; credit them wherever the app credits its data): about 580 000 stars to V 10.5, J2000 positions at epoch 2000.0 (proper motion applied), about 5 bytes per star.
 *
 * File format, all little-endian ("AFSS" = AstroFixxer Solver Stars); keep in step with the Python writer:
 * ```
 * 0   char[4] "AFSS"
 * 4   u16 version (1)
 * 6   u16 bands: declination bands of height 180/bands degrees, band 0 starts at Dec -90
 * 8   u32 star count N
 * 12  f32 magMin (-2.0)          magnitude byte m stands for magMin + m * magStep
 * 16  f32 magStep (0.05)
 * 20  f32 magLimit (10.5)        the list is complete to here (informational)
 * 24  u16 cells[bands]           RA cells in each band; cell width is 360/cells degrees
 * ..  u32 offsets[C + 1]         C = sum(cells); index of the first star of each cell (band-major, RA ascending)
 * ..  N records of 5 bytes, cell after cell, brightest first inside a cell:
 *       u16 x   RA:  ra  = (col  + (x + 0.5) / 65536) * 360 / cells[band]
 *       u16 y   Dec: dec = -90 + (band + (y + 0.5) / 65536) * 180 / bands
 *       u8  m   V magnitude
 * ```
 * A cone query touches only the cells it overlaps and stops early in each cell once the stars are too faint.
 */
class SolverStars private constructor(
    private val data: ByteArray,
    private val bands: Int,
    /** Number of stars in the file. */
    val count: Int,
    private val magMin: Double,
    private val magStep: Double,
    /** The magnitude to which the list is complete. */
    val magLimit: Double,
    private val cells: IntArray,
    private val firstCell: IntArray,
    private val offsets: IntArray,
    private val recordsAt: Int,
) : StarSource {

    private fun u16(at: Int) = (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)

    override fun cone(raDeg: Double, decDeg: Double, radiusDeg: Double, magLimit: Double): List<SkyStar> {
        val r = radiusDeg.coerceIn(0.0, 180.0)
        val out = ArrayList<SkyStar>()
        val bandH = 180.0 / bands
        val decLo = decDeg - r
        val decHi = decDeg + r
        val bandLo = floor((max(decLo, -90.0) + 90.0) / bandH).toInt().coerceIn(0, bands - 1)
        val bandHi = floor((min(decHi, 90.0) + 90.0) / bandH).toInt().coerceIn(0, bands - 1)
        // Largest RA half-width of the cone; the whole circle when a pole is inside it.
        val raHalf = if (decLo <= -90.0 || decHi >= 90.0 || r >= 90.0) 180.0
        else Math.toDegrees(asin(min(1.0, sin(Math.toRadians(r)) / cos(Math.toRadians(decDeg)))))
        val d2r = PI / 180
        val sinD0 = sin(decDeg * d2r)
        val cosD0 = cos(decDeg * d2r)
        val cosR = cos(r * d2r) - 1e-12
        for (band in bandLo..bandHi) {
            val n = cells[band]
            val w = 360.0 / n
            val c0: Int
            val count: Int
            if (raHalf >= 180.0) {
                c0 = 0; count = n
            } else {
                val lo = floor((raDeg - raHalf) / w).toInt()
                val hi = floor((raDeg + raHalf) / w).toInt()
                c0 = lo; count = min(n, hi - lo + 1)
            }
            for (k in 0 until count) {
                val col = (c0 + k).mod(n)
                val cell = firstCell[band] + col
                for (i in offsets[cell] until offsets[cell + 1]) {
                    val at = recordsAt + 5 * i
                    val mag = magMin + (data[at + 4].toInt() and 0xFF) * magStep
                    if (mag > magLimit) break // brightest first inside a cell
                    val ra = (col + (u16(at) + 0.5) / 65536.0) * w
                    val dec = -90.0 + (band + (u16(at + 2) + 0.5) / 65536.0) * bandH
                    val cosSep = sinD0 * sin(dec * d2r) + cosD0 * cos(dec * d2r) * cos((ra - raDeg) * d2r)
                    if (cosSep >= cosR) out.add(SkyStar(ra, dec, mag))
                }
            }
        }
        return out
    }

    companion object {
        /** Reads the whole file; throws [IllegalArgumentException] if it is not a valid `solver_stars.bin`. */
        fun load(input: InputStream): SolverStars {
            val data = input.readBytes()
            require(data.size >= 24 && String(data, 0, 4, Charsets.ISO_8859_1) == "AFSS") { "not a solver star file" }
            val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val version = b.getShort(4).toInt() and 0xFFFF
            require(version == 1) { "unsupported solver star file version $version" }
            val bands = b.getShort(6).toInt() and 0xFFFF
            val count = b.getInt(8)
            require(bands in 1..1000 && count >= 0) { "corrupt solver star header" }
            val magMin = b.getFloat(12).toDouble()
            val magStep = b.getFloat(16).toDouble()
            val magLimit = b.getFloat(20).toDouble()
            require(data.size >= 24 + 2 * bands) { "truncated solver star file" }
            val cells = IntArray(bands) { b.getShort(24 + 2 * it).toInt() and 0xFFFF }
            require(cells.all { it >= 1 }) { "corrupt cell table" }
            val firstCell = IntArray(bands)
            var total = 0
            for (i in 0 until bands) { firstCell[i] = total; total += cells[i] }
            val offAt = 24 + 2 * bands
            val recordsAt = offAt + 4 * (total + 1)
            require(data.size.toLong() == recordsAt + 5L * count) { "solver star file has the wrong size" }
            val offsets = IntArray(total + 1) { b.getInt(offAt + 4 * it) }
            require(offsets[total] == count && (0 until total).all { offsets[it] <= offsets[it + 1] }) { "corrupt offset table" }
            return SolverStars(data, bands, count, magMin, magStep, magLimit, cells, firstCell, offsets, recordsAt)
        }
    }
}
