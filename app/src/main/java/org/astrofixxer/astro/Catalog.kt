package org.astrofixxer.astro

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One searchable sky object from the bundled Stellarium-derived catalogue. Positions are J2000 degrees. */
class SkyObject(
    val name: String,
    val ra: Double,
    val dec: Double,
    val mag: Double?,
    /** "S" star, "Ga", "Oc", "Gc", "Ne" as in the web app. */
    val type: String,
    val sizeArcmin: Double = 0.0,
    val otherNames: List<String> = emptyList(),
)

class Constellation(
    val id: String, val name: String, val otherNames: List<String>, val ra: Double, val dec: Double, val lines: List<IntArray>,
    val art: ConstellationArt? = null,
)

/** Stellarium illustration placed on the sky by three anchor stars: (x, y) pixel in a [width]×[height] image -> HIP. */
class ConstellationArt(val file: String, val width: Int, val height: Int, val anchors: List<Triple<Double, Double, Int>>)

/**
 * Stars, deep-sky objects and constellations, with a 10°×10° RA/Dec grid for field-of-view queries and a name index.
 * ponytail: grid, not HEALPix; upgrade if profiling on a phone says so.
 */
class Catalog(val objects: List<SkyObject>, val constellations: Map<String, List<Constellation>>, val starsByHip: Map<Int, SkyObject>) {
    private val grid = Array(18 * 36) { mutableListOf<Int>() }
    private val names = HashMap<String, MutableList<Int>>()
    private val sortedNames: List<String>

    init {
        objects.forEachIndexed { i, o ->
            grid[cell(o.ra, o.dec)].add(i)
            for (n in listOf(o.name) + o.otherNames) names.getOrPut(normalizeName(n)) { mutableListOf() }.add(i)
        }
        sortedNames = names.keys.sorted()
    }

    /** Indices of objects within [radiusDeg] of (ra, dec) and brighter than [magLimit] (objects without a magnitude are kept). */
    fun near(ra: Double, dec: Double, radiusDeg: Double, magLimit: Double = 99.0): List<SkyObject> {
        val out = mutableListOf<SkyObject>()
        val dLo = max(-90.0, dec - radiusDeg)
        val dHi = min(90.0, dec + radiusDeg)
        val poleInView = dLo <= -89.0 || dHi >= 89.0
        val raHalf = if (poleInView) 180.0 else radiusDeg / cos(max(abs(dLo), abs(dHi)) * PI / 180)
        var band = floor((dLo + 90) / 10).toInt()
        val bandHi = min(17, floor((dHi + 90) / 10).toInt())
        val cosR = cos(radiusDeg * PI / 180)
        while (band <= bandHi) {
            val cols = if (raHalf >= 180) 0 until 36 else {
                val c0 = floor((ra - raHalf) / 10).toInt()
                val c1 = floor((ra + raHalf) / 10).toInt()
                (c0..c1).map { it.mod(36) }.distinct()
            }
            for (c in cols) for (i in grid[band * 36 + c]) {
                val o = objects[i]
                if ((o.mag ?: -99.0) <= magLimit && cosSeparation(ra, dec, o.ra, o.dec) >= cosR) out += o
            }
            band++
        }
        return out
    }

    /** Exact name match first, then unique-prefix matches, ordered by brightness. */
    fun search(query: String, limit: Int = 20): List<SkyObject> {
        val key = normalizeName(query.trim())
        if (key.isEmpty()) return emptyList()
        val hits = LinkedHashSet<Int>()
        names[key]?.let { hits.addAll(it) }
        var i = sortedNames.binarySearch(key).let { if (it < 0) -it - 1 else it }
        while (i < sortedNames.size && sortedNames[i].startsWith(key) && hits.size < limit * 4) hits.addAll(names.getValue(sortedNames[i++]))
        val exact = names[key].orEmpty().toSet()
        return hits.sortedWith(compareBy<Int>({ it !in exact }, { objects[it].mag ?: 99.0 })).take(limit).map { objects[it] }
    }

    companion object {
        private val zeros = Regex("^(.*)(([A-Z]+)[ 0]+)([^0].*)$")

        /** Same rule as the web app's normalizeName: upper case, drop spaces/leading zeros after letters. */
        fun normalizeName(name: String): String {
            var n = name.uppercase()
            while (true) {
                val m = zeros.matchEntire(n) ?: return n
                n = m.groupValues[1] + m.groupValues[3] + m.groupValues[4]
            }
        }

        private fun cell(ra: Double, dec: Double): Int {
            val band = min(17, floor((dec + 90) / 10).toInt())
            val col = floor(ra.mod(360.0) / 10).toInt().mod(36)
            return band * 36 + col
        }

        private fun cosSeparation(ra1: Double, de1: Double, ra2: Double, de2: Double): Double {
            val d2r = PI / 180
            return kotlin.math.sin(de1 * d2r) * kotlin.math.sin(de2 * d2r) + cos(de1 * d2r) * cos(de2 * d2r) * cos((ra1 - ra2) * d2r)
        }

        private fun JSONArray.strings() = List(length()) { getString(it) }

        /** Loads sky_catalog.json.gz written by tools/stellarium_import/build_sky_data.py. */
        fun load(gz: InputStream, dsoMagLimit: Double = 99.0): Catalog {
            val root = JSONObject(GZIPInputStream(gz).bufferedReader().readText())
            val objects = mutableListOf<SkyObject>()
            val byHip = HashMap<Int, SkyObject>()
            val stars = root.getJSONArray("stars")
            for (k in 0 until stars.length()) {
                val s = stars.getJSONObject(k)
                val names = s.getJSONArray("names").strings()
                val indian = s.getJSONArray("indian_names")
                val indianNames = List(indian.length()) { j ->
                    val x = indian.getJSONObject(j)
                    listOf(x.optString("pronounce"), x.optString("native")).filter { it.isNotEmpty() }.joinToString(" ")
                }
                val hip = if (s.isNull("hip")) null else s.getInt("hip")
                val hipName = hip?.let { listOf("HIP $it") }.orEmpty()
                val o = SkyObject(names.firstOrNull() ?: hipName.firstOrNull() ?: "Star", s.getDouble("ra"), s.getDouble("dec"),
                    s.getDouble("mag"), "S", 0.0, names.drop(1) + indianNames + hipName)
                objects += o
                if (hip != null) byHip[hip] = o
            }
            val dso = root.getJSONArray("dso")
            for (k in 0 until dso.length()) {
                val d = dso.getJSONObject(k)
                val mag = when {
                    !d.isNull("vmag") -> d.getDouble("vmag")
                    !d.isNull("bmag") -> d.getDouble("bmag")
                    else -> null
                }
                if (mag != null && mag > dsoMagLimit) continue
                val ids = d.getJSONArray("designations").strings()
                val common = d.getJSONArray("names").strings()
                if (ids.isEmpty() && common.isEmpty()) continue
                objects += SkyObject(ids.firstOrNull() ?: common.first(), d.getDouble("ra"), d.getDouble("dec"), mag,
                    d.getString("t"), d.getDouble("major"), ids.drop(1) + common)
            }
            val cons = root.getJSONObject("constellations")
            val constellations = cons.keys().asSequence().associateWith { culture ->
                val arr = cons.getJSONArray(culture)
                List(arr.length()) { k ->
                    val c = arr.getJSONObject(k)
                    val lines = c.getJSONArray("lines")
                    val art = c.optJSONObject("art")?.let { a ->
                        val size = a.getJSONArray("size")
                        val anchors = a.getJSONArray("anchors")
                        ConstellationArt(a.getString("file"), size.getInt(0), size.getInt(1), List(anchors.length()) { i ->
                            val p = anchors.getJSONArray(i)
                            Triple(p.getDouble(0), p.getDouble(1), p.getInt(2))
                        })
                    }
                    Constellation(c.getString("id"), c.getString("name"), c.getJSONArray("n2").strings(),
                        c.getDouble("RA"), c.getDouble("DE"),
                        List(lines.length()) { l ->
                            val poly = lines.getJSONArray(l)
                            IntArray(poly.length()) { p -> poly.optInt(p, -1) } // non-integers break the line, as in Stellarium
                        }, art)
                }
            }
            return Catalog(objects, constellations, byHip)
        }
    }
}
