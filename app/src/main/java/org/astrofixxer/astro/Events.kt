package org.astrofixxer.astro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Offline sky events computed from the VSOP87-based positions. Times are JD (UTC) unless noted. */
object Events {
    private const val D2R = PI / 180
    private const val EARTH_RADIUS_AU = 6378.14 / 149597870.7
    private const val SUN_RADIUS_AU = 696000.0 / 149597870.7
    private const val MOON_RADIUS_AU = 1737.4 / 149597870.7
    private val OBLIQUITY_J2000 = 23.4392911 * D2R
    private const val GENERAL_PRECESSION_DEG_PER_CENTURY = 5029.0966 / 3600

    /** Geocentric J2000 equatorial vector (AU) of a body; the Sun is -Earth. */
    fun geocentric(body: Int, jdUtc: Double): DoubleArray {
        val tt = ApparentPosition.convertUTCtoTT(jdUtc)
        val earth = ApparentPosition.getBodyPV(ApparentPosition.EARTH, tt)
        val b = ApparentPosition.getBodyPV(body, tt)
        return doubleArrayOf(b[0] - earth[0], b[1] - earth[1], b[2] - earth[2])
    }

    fun eclipticLongitude(v: DoubleArray): Double {
        val y = v[1] * cos(OBLIQUITY_J2000) + v[2] * sin(OBLIQUITY_J2000)
        return (atan2(y, v[0]) / D2R).mod(360.0)
    }

    fun angle(a: DoubleArray, b: DoubleArray): Double {
        val d = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (norm(a) * norm(b))
        return acos(d.coerceIn(-1.0, 1.0))
    }

    private fun norm(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    /** Moon minus Sun ecliptic longitude, degrees in [0, 360): 0 new, 90 first quarter, 180 full, 270 last quarter. */
    fun moonPhaseAngle(jd: Double) =
        (eclipticLongitude(geocentric(ApparentPosition.MOON, jd)) - eclipticLongitude(geocentric(ApparentPosition.SUN, jd))).mod(360.0)

    fun moonIllumination(jd: Double): Double {
        val elongation = angle(geocentric(ApparentPosition.MOON, jd), geocentric(ApparentPosition.SUN, jd))
        return (1 - cos(elongation)) / 2
    }

    /** First time after [fromJd] when the phase angle equals [phase] degrees. */
    fun nextPhase(fromJd: Double, phase: Double): Double {
        fun f(jd: Double) = (moonPhaseAngle(jd) - phase + 180).mod(360.0) - 180
        var a = fromJd
        var fa = f(a)
        var b = a + 0.5
        while (true) {
            val fb = f(b)
            if (fa < 0 && fb >= 0 && fb - fa < 90) return bisect(::f, a, b)
            a = b; fa = fb; b += 0.5
        }
    }

    /**
     * Next time after [fromJd] when the Sun's ecliptic longitude reaches [longitude].
     * [ofDate] = true measures from the equinox of date (equinoxes, solstices); false uses J2000 (meteor showers).
     */
    fun nextSolarLongitude(fromJd: Double, longitude: Double, ofDate: Boolean): Double {
        fun lon(jd: Double): Double {
            val j2000 = eclipticLongitude(geocentric(ApparentPosition.SUN, jd))
            return if (ofDate) j2000 + GENERAL_PRECESSION_DEG_PER_CENTURY * (jd - 2451545.0) / 36525 else j2000
        }
        fun f(jd: Double) = (lon(jd) - longitude + 180).mod(360.0) - 180
        var a = fromJd
        var fa = f(a)
        var b = a + 1
        while (true) {
            val fb = f(b)
            if (fa < 0 && fb >= 0 && fb - fa < 90) return bisect(::f, a, b)
            a = b; fa = fb; b += 1
        }
    }

    private fun bisect(f: (Double) -> Double, lo: Double, hi: Double): Double {
        var a = lo
        var b = hi
        repeat(60) {
            val m = (a + b) / 2
            if (f(m) < 0) a = m else b = m
        }
        return (a + b) / 2
    }

    /** Minimum of [f] on [a, b] by golden-section search. */
    fun minimise(f: (Double) -> Double, lo: Double, hi: Double, tol: Double = 1e-6): Double {
        val g = (sqrt(5.0) - 1) / 2
        var a = lo
        var b = hi
        var c = b - g * (b - a)
        var d = a + g * (b - a)
        while (b - a > tol) {
            if (f(c) < f(d)) b = d else a = c
            c = b - g * (b - a)
            d = a + g * (b - a)
        }
        return (a + b) / 2
    }

    enum class LunarEclipseType { PENUMBRAL, PARTIAL, TOTAL }
    class LunarEclipse(val jdMax: Double, val type: LunarEclipseType, val umbralMagnitude: Double)

    /** Lunar eclipse at the full moon nearest [fullMoonJd], or null. Danjon's 1.02 shadow enlargement. */
    fun lunarEclipse(fullMoonJd: Double): LunarEclipse? {
        fun separation(jd: Double) = angle(geocentric(ApparentPosition.MOON, jd), geocentric(ApparentPosition.SUN, jd).let { s -> DoubleArray(3) { -s[it] } })
        val jd = minimise(::separation, fullMoonJd - 0.5, fullMoonJd + 0.5)
        val moon = geocentric(ApparentPosition.MOON, jd)
        val sun = geocentric(ApparentPosition.SUN, jd)
        val piMoon = asin(EARTH_RADIUS_AU / norm(moon))
        val piSun = asin(EARTH_RADIUS_AU / norm(sun))
        val sSun = asin(SUN_RADIUS_AU / norm(sun))
        val sMoon = asin(MOON_RADIUS_AU / norm(moon))
        val umbra = 1.02 * (piMoon + piSun - sSun)
        val penumbra = 1.02 * (piMoon + piSun + sSun)
        val d = separation(jd)
        val umbralMagnitude = (umbra + sMoon - d) / (2 * sMoon)
        return when {
            d + sMoon < umbra -> LunarEclipse(jd, LunarEclipseType.TOTAL, umbralMagnitude)
            d - sMoon < umbra -> LunarEclipse(jd, LunarEclipseType.PARTIAL, umbralMagnitude)
            d - sMoon < penumbra -> LunarEclipse(jd, LunarEclipseType.PENUMBRAL, umbralMagnitude)
            else -> null
        }
    }

    enum class SolarEclipseType { PARTIAL, TOTAL, ANNULAR }
    class SolarEclipse(val jdMax: Double, val type: SolarEclipseType)

    /**
     * Solar eclipse somewhere on Earth at the new moon nearest [newMoonJd], or null.
     * ponytail: global geocentric test only; local visibility and contact times come later with topocentric positions.
     */
    fun solarEclipse(newMoonJd: Double): SolarEclipse? {
        fun separation(jd: Double) = angle(geocentric(ApparentPosition.MOON, jd), geocentric(ApparentPosition.SUN, jd))
        val jd = minimise(::separation, newMoonJd - 0.5, newMoonJd + 0.5)
        val moon = geocentric(ApparentPosition.MOON, jd)
        val sun = geocentric(ApparentPosition.SUN, jd)
        val piMoon = asin(EARTH_RADIUS_AU / norm(moon))
        val piSun = asin(EARTH_RADIUS_AU / norm(sun))
        val sSun = asin(SUN_RADIUS_AU / norm(sun))
        val sMoon = asin(MOON_RADIUS_AU / norm(moon))
        val d = separation(jd)
        if (d > piMoon - piSun + sSun + sMoon) return null
        val central = d < piMoon - piSun + abs(sMoon - sSun)
        val type = when {
            !central -> SolarEclipseType.PARTIAL
            sMoon >= sSun -> SolarEclipseType.TOTAL
            else -> SolarEclipseType.ANNULAR
        }
        return SolarEclipse(jd, type)
    }

    /** Rise and set (JD UTC) of a body on the UTC day starting at [dayStartJd]; null when it doesn't cross [horizonDeg]. */
    fun riseSet(body: Int, dayStartJd: Double, latDeg: Double, lonDeg: Double, horizonDeg: Double): Pair<Double?, Double?> {
        fun alt(jd: Double) = ApparentPosition.reduce(body, jd, latDeg * D2R, lonDeg * D2R).alt / D2R - horizonDeg
        var rise: Double? = null
        var set: Double? = null
        val step = 1.0 / 144 // 10 minutes
        var a = dayStartJd
        var fa = alt(a)
        while (a < dayStartJd + 1) {
            val b = a + step
            val fb = alt(b)
            if (fa < 0 && fb >= 0 && rise == null) rise = bisect(::alt, a, b)
            if (fa >= 0 && fb < 0 && set == null) set = bisect({ -alt(it) }, a, b)
            a = b; fa = fb
        }
        return Pair(rise, set)
    }

    class Conjunction(val jd: Double, val bodyA: Int, val bodyB: Int, val separationDeg: Double)

    /** Close approaches (< [maxDeg]) between two bodies over [days] days, sampled every 6 hours and refined. */
    fun conjunctions(bodyA: Int, bodyB: Int, fromJd: Double, days: Int, maxDeg: Double): List<Conjunction> {
        fun sep(jd: Double) = angle(geocentric(bodyA, jd), geocentric(bodyB, jd)) / D2R
        val out = mutableListOf<Conjunction>()
        val step = 0.25
        var prev = sep(fromJd)
        var cur = sep(fromJd + step)
        var t = fromJd + step
        while (t < fromJd + days) {
            val next = sep(t + step)
            if (cur <= prev && cur <= next && cur < maxDeg) {
                val jd = minimise(::sep, t - step, t + step)
                out += Conjunction(jd, bodyA, bodyB, sep(jd))
            }
            prev = cur; cur = next; t += step
        }
        return out
    }
}
