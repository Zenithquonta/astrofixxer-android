package org.astrofixxer.astro

import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Comets and asteroids on two-body Kepler orbits, from Stellarium's osculating elements
 * (data/events/minor_bodies.json). ponytail: no planetary perturbations; accuracy drops away from the element epoch.
 */
class MinorBody(
    val name: String,
    val type: String,
    /** Perihelion distance, AU. */
    val q: Double,
    val e: Double,
    /** Inclination, ascending node, argument of perihelion: degrees, J2000 ecliptic. */
    val i: Double, val node: Double, val peri: Double,
    /** Time of perihelion, JD (TDB). */
    val tp: Double,
    val epoch: Double,
    val h: Double?,
    val slope: Double?,
) {
    val isComet get() = type == "comet" || type == "interstellar object"

    /** Heliocentric position, J2000 ecliptic, AU. */
    fun eclipticPosition(jdTdb: Double): DoubleArray {
        val dt = jdTdb - tp
        val xp: Double
        val yp: Double
        when {
            e < 1.0 -> {
                val a = q / (1 - e)
                val m = GAUSS_K / (a * sqrt(a)) * dt
                val ea = solveElliptic(m, e)
                xp = a * (cos(ea) - e)
                yp = a * sqrt(1 - e * e) * sin(ea)
            }
            e > 1.0 -> {
                val a = q / (e - 1)
                val m = GAUSS_K / (a * sqrt(a)) * dt
                val ha = solveHyperbolic(m, e)
                xp = a * (e - cosh(ha))
                yp = a * sqrt(e * e - 1) * sinh(ha)
            }
            else -> { // parabola: Barker's equation s^3 + 3s = w, s = tan(v/2)
                val w = 3 * GAUSS_K / sqrt(2 * q * q * q) * dt
                val y = cbrt(w / 2 + sqrt(w * w / 4 + 1))
                val s = y - 1 / y
                xp = q * (1 - s * s)
                yp = 2 * q * s
            }
        }
        val w = peri * D2R; val o = node * D2R; val inc = i * D2R
        val px = cos(w) * cos(o) - sin(w) * sin(o) * cos(inc)
        val py = cos(w) * sin(o) + sin(w) * cos(o) * cos(inc)
        val pz = sin(w) * sin(inc)
        val qx = -sin(w) * cos(o) - cos(w) * sin(o) * cos(inc)
        val qy = -sin(w) * sin(o) + cos(w) * cos(o) * cos(inc)
        val qz = cos(w) * sin(inc)
        return doubleArrayOf(xp * px + yp * qx, xp * py + yp * qy, xp * pz + yp * qz)
    }

    /** Geocentric astrometric J2000 place with light time: RA/Dec in radians, distances in AU, visual magnitude. */
    fun geocentric(jdUtc: Double): Place {
        val jdTdb = ApparentPosition.convertUTCtoTT(jdUtc)
        val earth = ApparentPosition.getBodyPV(ApparentPosition.EARTH, jdTdb)
        var body = eclipticToEquatorial(eclipticPosition(jdTdb))
        var geo = DoubleArray(3) { body[it] - earth[it] }
        repeat(2) {
            val lightTime = sqrt(geo[0] * geo[0] + geo[1] * geo[1] + geo[2] * geo[2]) / C_AU_PER_DAY
            body = eclipticToEquatorial(eclipticPosition(jdTdb - lightTime))
            geo = DoubleArray(3) { body[it] - earth[it] }
        }
        val radec = ApparentPosition.xyzToRaDec(geo)
        val r = sqrt(body[0] * body[0] + body[1] * body[1] + body[2] * body[2])
        val delta = radec[2]
        val sunEarth = sqrt(earth[0] * earth[0] + earth[1] * earth[1] + earth[2] * earth[2])
        return Place(radec[0], radec[1], r, delta, magnitude(r, delta, sunEarth))
    }

    class Place(val ra: Double, val dec: Double, val r: Double, val delta: Double, val mag: Double?)

    private fun magnitude(r: Double, delta: Double, sunEarth: Double): Double? {
        val hh = h ?: return null
        if (isComet) return hh + 5 * log10(delta) + 2.5 * (slope ?: 4.0) * log10(r)
        // IAU H-G system for asteroids; phase angle from the Sun-body-Earth triangle.
        val g = slope ?: 0.15
        val cosAlpha = ((r * r + delta * delta - sunEarth * sunEarth) / (2 * r * delta)).coerceIn(-1.0, 1.0)
        val t = tan(kotlin.math.acos(cosAlpha) / 2)
        val phi1 = exp(-3.33 * t.pow(0.63))
        val phi2 = exp(-1.87 * t.pow(1.22))
        return hh + 5 * log10(r * delta) - 2.5 * log10((1 - g) * phi1 + g * phi2)
    }

    companion object {
        const val GAUSS_K = 0.01720209895
        private const val D2R = PI / 180
        private const val C_AU_PER_DAY = 173.1446326846693
        private const val OBLIQUITY_J2000 = 23.4392911 * D2R

        fun eclipticToEquatorial(v: DoubleArray) = doubleArrayOf(
            v[0],
            v[1] * cos(OBLIQUITY_J2000) - v[2] * sin(OBLIQUITY_J2000),
            v[1] * sin(OBLIQUITY_J2000) + v[2] * cos(OBLIQUITY_J2000),
        )

        fun solveElliptic(meanAnomaly: Double, e: Double): Double {
            val m = meanAnomaly.mod(2 * PI).let { if (it > PI) it - 2 * PI else it }
            var ea = if (e < 0.8) m else PI * kotlin.math.sign(m).let { if (it == 0.0) 1.0 else it }
            repeat(100) {
                val d = (ea - e * sin(ea) - m) / (1 - e * cos(ea))
                ea -= d
                if (abs(d) < 1e-14) return ea
            }
            return ea
        }

        fun solveHyperbolic(m: Double, e: Double): Double {
            var ha = kotlin.math.asinh(m / e)
            repeat(100) {
                val d = (e * sinh(ha) - ha - m) / (e * cosh(ha) - 1)
                ha -= d
                if (abs(d) < 1e-14) return ha
            }
            return ha
        }

        /** Parses the importer's minor_bodies.json. Asteroids given as (a, M at epoch) get q and time of perihelion. */
        fun parse(json: String): List<MinorBody> {
            val arr = JSONObject(json).getJSONArray("bodies")
            return List(arr.length()) { idx ->
                val o = arr.getJSONObject(idx)
                val e = o.getDouble("e")
                val epoch = o.getDouble("epoch_jd")
                val q: Double
                val tp: Double
                if (o.has("q")) {
                    q = o.getDouble("q")
                    tp = o.getDouble("tp_jd")
                } else {
                    val a = o.getDouble("a")
                    q = a * (1 - e)
                    val n = if (o.has("n")) o.getDouble("n") else GAUSS_K / (a * sqrt(a)) / D2R
                    tp = epoch - o.getDouble("m") / n
                }
                MinorBody(
                    o.getString("name"), o.getString("type"), q, e,
                    o.getDouble("i"), o.getDouble("node"), o.getDouble("peri"), tp, epoch,
                    if (o.has("h")) o.getDouble("h") else null,
                    if (o.has("slope")) o.getDouble("slope") else null,
                )
            }
        }
    }
}
