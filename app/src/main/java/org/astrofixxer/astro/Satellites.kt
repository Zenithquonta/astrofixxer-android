package org.astrofixxer.astro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Satellite passes and Sun/Moon transits for an observer, from SGP4 orbits. Times are JD (UTC). */
object Satellites {
    private const val D2R = PI / 180
    private const val AU_KM = 149597870.7
    private const val EARTH_RADIUS_KM = 6378.137
    private const val MIN_ALT_DEG = 10.0

    /** Observer position in Earth-fixed coordinates (km), WGS-84 ellipsoid at sea level. */
    fun observerEcef(latDeg: Double, lonDeg: Double): DoubleArray {
        val f = 1 / 298.257223563
        val e2 = f * (2 - f)
        val lat = latDeg * D2R
        val lon = lonDeg * D2R
        val n = EARTH_RADIUS_KM / sqrt(1 - e2 * sin(lat) * sin(lat))
        return doubleArrayOf(n * cos(lat) * cos(lon), n * cos(lat) * sin(lon), n * (1 - e2) * sin(lat))
    }

    /** TEME (km) to Earth-fixed by rotating through Greenwich sidereal time; polar motion ignored. */
    fun temeToEcef(r: DoubleArray, jd: Double): DoubleArray {
        val g = Sgp4.gmst(jd)
        return doubleArrayOf(cos(g) * r[0] + sin(g) * r[1], -sin(g) * r[0] + cos(g) * r[1], r[2])
    }

    /** Altitude and azimuth (degrees, azimuth from north through east) of an Earth-fixed point seen from the observer. */
    fun altAz(ecef: DoubleArray, latDeg: Double, lonDeg: Double): Pair<Double, Double> {
        val o = observerEcef(latDeg, lonDeg)
        val rx = ecef[0] - o[0]; val ry = ecef[1] - o[1]; val rz = ecef[2] - o[2]
        val lat = latDeg * D2R; val lon = lonDeg * D2R
        val south = sin(lat) * cos(lon) * rx + sin(lat) * sin(lon) * ry - cos(lat) * rz
        val east = -sin(lon) * rx + cos(lon) * ry
        val up = cos(lat) * cos(lon) * rx + cos(lat) * sin(lon) * ry + sin(lat) * rz
        val range = sqrt(rx * rx + ry * ry + rz * rz)
        return Pair(asin(up / range) / D2R, (atan2(east, -south) / D2R + 360) % 360)
    }

    /** True when the satellite (TEME km) is in sunlight, using a cylindrical Earth shadow. */
    fun sunlit(sat: DoubleArray, jd: Double): Boolean {
        val s = Events.geocentric(ApparentPosition.SUN, jd)
        val n = sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2])
        val u = doubleArrayOf(s[0] / n, s[1] / n, s[2] / n)
        val along = sat[0] * u[0] + sat[1] * u[1] + sat[2] * u[2]
        if (along > 0) return true
        val perp = sqrt((sat[0] * sat[0] + sat[1] * sat[1] + sat[2] * sat[2]) - along * along)
        return perp > EARTH_RADIUS_KM
    }

    class Pass(val satellite: String, val startJd: Double, val maxJd: Double, val endJd: Double,
               val maxAltDeg: Double, val startAzDeg: Double, val endAzDeg: Double, val visible: Boolean)

    /** Passes above 10° within [days] of [fromJd], sampled every 20 s. */
    fun passes(sat: Sgp4, fromJd: Double, days: Double, latDeg: Double, lonDeg: Double): List<Pass> {
        val out = mutableListOf<Pass>()
        val step = 20.0 / 86400
        var jd = fromJd
        var inPass = false
        var start = 0.0; var startAz = 0.0; var maxAlt = -90.0; var maxJd = 0.0; var visible = false
        var lastAz = 0.0
        while (jd < fromJd + days) {
            val teme = sat.positionAt(jd)
            val (alt, az) = altAz(temeToEcef(teme, jd), latDeg, lonDeg)
            if (alt >= MIN_ALT_DEG) {
                if (!inPass) { inPass = true; start = jd; startAz = az; maxAlt = alt; maxJd = jd; visible = false }
                if (alt > maxAlt) { maxAlt = alt; maxJd = jd }
                if (!visible && sunlit(teme, jd)) {
                    val sunAlt = ApparentPosition.reduce(ApparentPosition.SUN, jd, latDeg * D2R, lonDeg * D2R).alt / D2R
                    visible = sunAlt < -6
                }
                lastAz = az
            } else if (inPass) {
                out += Pass(sat.name, start, maxJd, jd, maxAlt, startAz, lastAz, visible)
                inPass = false
            }
            jd += step
        }
        return out
    }

    class Transit(val satellite: String, val body: String, val jd: Double, val separationDeg: Double, val bodyRadiusDeg: Double)

    /**
     * Satellite crossing the Sun or Moon disc as seen from the observer. The ground track of a transit is only a few km
     * wide, so this needs orbit data under about 2 days old and an accurate location.
     */
    fun transits(sat: Sgp4, fromJd: Double, days: Double, latDeg: Double, lonDeg: Double): List<Transit> {
        val out = mutableListOf<Transit>()
        val lat = latDeg * D2R
        val lon = lonDeg * D2R
        fun dir(altDeg: Double, azDeg: Double): DoubleArray {
            val a = altDeg * D2R; val z = azDeg * D2R
            return doubleArrayOf(cos(a) * sin(z), cos(a) * cos(z), sin(a))
        }
        fun satDir(jd: Double): DoubleArray {
            val (alt, az) = altAz(temeToEcef(sat.positionAt(jd), jd), latDeg, lonDeg)
            return dir(alt, az)
        }
        for ((body, name) in listOf(ApparentPosition.SUN to "Sun", ApparentPosition.MOON to "Moon")) {
            fun bodyDir(jd: Double): DoubleArray {
                val r = ApparentPosition.reduce(body, jd, lat, lon)
                return dir(r.alt / D2R, r.az / D2R)
            }
            fun sep(jd: Double) = acos((satDir(jd).zip(bodyDir(jd)) { a, b -> a * b }.sum()).coerceIn(-1.0, 1.0)) / D2R
            for (p in passes(sat, fromJd, days, latDeg, lonDeg)) {
                if (bodyDir(p.maxJd)[2] <= 0) continue
                val step = 10.0 / 86400
                var jd = p.startJd
                while (jd < p.endJd) {
                    if (sep(jd) < 3.0) {
                        val best = Events.minimise(::sep, jd - step, jd + step, 1e-7)
                        val s = sep(best)
                        val distKm = norm(Events.geocentric(body, best)) * AU_KM
                        val radius = asin((if (body == ApparentPosition.SUN) 696000.0 else 1737.4) / distKm) / D2R
                        if (s < radius && out.none { abs(it.jd - best) < 1.0 / 1440 && it.body == name }) {
                            out += Transit(sat.name, name, best, s, radius)
                        }
                    }
                    jd += step
                }
            }
        }
        return out
    }

    private fun norm(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
}
