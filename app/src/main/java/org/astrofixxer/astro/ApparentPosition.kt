package org.astrofixxer.astro

import org.astrofixxer.astro.vsop87.Vsop87LargeEarth
import org.astrofixxer.astro.vsop87.vsop87a_milli_velocities
import org.astrofixxer.astro.vsop87.vsop87a_xsmall
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Port of CPReduce from the web app (Greg Miller, public domain): VSOP87 position ->
 * light time, aberration, precession, nutation, topocentric correction. All angles in radians.
 */
object ApparentPosition {
    const val SUN = 0
    const val MERCURY = 1
    const val VENUS = 2
    const val EARTH = 3
    const val MARS = 4
    const val JUPITER = 5
    const val SATURN = 6
    const val URANUS = 7
    const val NEPTUNE = 8
    const val MOON = 10

    val bodies = listOf("Sun", "Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus",
        "Neptune", "Earth-Moon Barrycenter", "Moon")

    /** Topocentric position. [az] is 0 = North, 90° = East. */
    class Result(
        val raJ2000: Double, val decJ2000: Double,
        val ra: Double, val dec: Double,
        val az: Double, val alt: Double,
    )

    /** [latitude], [longitude] in radians (East positive), [height] in AU as in the original. */
    fun reduce(body: Int, jdUtc: Double, latitude: Double, longitude: Double, height: Double = 0.0): Result {
        val jdTt = convertUTCtoTT(jdUtc)
        val jdTdb = jdTt
        val jdUt1 = jdUtc // ponytail: UT1-UTC taken as 0 (< 1 s), as in the web app

        val earth = getBodyPV(EARTH, jdTdb)
        val target = getBodyLightAdjusted(earth, body, jdTdb)

        val precession = getPrecessionMatrix(jdTdb)
        val nutation = getNutationMatrix(jdTdb)

        val geocentric = sub(target, earth)

        var observer = convertGeodeticToECEF(latitude, longitude, height)
        observer = vecMatrixMul(observer, zRotation(-greenwichApparentSiderealTime(jdUt1)))
        observer = vecMatrixMul(observer, transpose(nutation))
        observer = vecMatrixMul(observer, transpose(precession))

        val topocentric = sub(geocentric, observer)
        val radecJ2000 = xyzToRaDec(topocentric)

        val aberrated = aberration(topocentric, earth)
        val nutated = vecMatrixMul(vecMatrixMul(aberrated, precession), nutation)
        val radec = xyzToRaDec(nutated)
        val altaz = raDecToAltAz(radec[0], radec[1], latitude, longitude, jdUt1)

        return Result(radecJ2000[0], radecJ2000[1], radec[0], radec[1], altaz[0], altaz[1])
    }

    private fun aberration(u4: DoubleArray, earthPV: DoubleArray): DoubleArray {
        val c = 173.1446326846693 // AU per day
        val dE = doubleArrayOf(earthPV[3], earthPV[4], earthPV[5])
        val t = magnitude(u4) / c
        val b = magnitude(dE) / c
        val cosD = dot(u4, dE) / (magnitude(u4) * magnitude(dE))
        val y = sqrt(1 - b * b)
        val f1 = b * cosD
        val f2 = (1 + f1 / (1 + y)) * t
        return DoubleArray(3) { (y * u4[it] + f2 * dE[it]) / (1 + f1) }
    }

    private fun getPrecessionMatrix(jdTdb: Double): Array<DoubleArray> {
        val t = (jdTdb - 2451545.5) / 36525.0
        val gamma = arcsec(-0.052928 + 10.556378 * t + 0.4932044 * t * t - 0.00031238 * t * t * t - 0.000002788 * t * t * t * t + 0.0000000260 * t * t * t * t * t)
        val phi = arcsec(84381.412819 - 46.811016 * t + 0.0511268 * t * t + 0.00053289 * t * t * t - 0.000000440 * t * t * t * t - 0.0000000176 * t * t * t * t * t)
        val psi = arcsec(-0.041775 + 5038.481484 * t + 1.5584175 * t * t - 0.00018522 * t * t * t - 0.000026452 * t * t * t * t - 0.0000000148 * t * t * t * t * t)
        val eps = arcsec(84381.406 - 46.836769 * t - 0.0001831 * t * t + 0.00200340 * t * t * t - 0.000000576 * t * t * t * t - 0.0000000434 * t * t * t * t * t)
        val a = zRotation(gamma)
        val b = matMul(xRotation(phi), a)
        val c = matMul(zRotation(-psi), b)
        return matMul(xRotation(-eps), c)
    }

    /** Leap seconds (TAI-UTC) since 1972; update when IERS announces a new one. */
    private fun leapSeconds(jd: Double): Double {
        val table = doubleArrayOf(
            2457754.5, 37.0, 2457204.5, 36.0, 2456109.5, 35.0, 2454832.5, 34.0, 2453736.5, 33.0,
            2451179.5, 32.0, 2450630.5, 31.0, 2450083.5, 30.0, 2449534.5, 29.0, 2449169.5, 28.0,
            2448804.5, 27.0, 2448257.5, 26.0, 2447892.5, 25.0, 2447161.5, 24.0, 2446247.5, 23.0,
            2445516.5, 22.0, 2445151.5, 21.0, 2444786.5, 20.0, 2444239.5, 19.0, 2443874.5, 18.0,
            2443509.5, 17.0, 2443144.5, 16.0, 2442778.5, 15.0, 2442413.5, 14.0, 2442048.5, 13.0,
            2441683.5, 12.0, 2441499.5, 11.0, 2441317.5, 10.0,
        )
        for (i in table.indices step 2) if (jd > table[i]) return table[i + 1]
        return 0.0 // ponytail: pre-1972 fractional offsets dropped; the app works with current dates
    }

    fun convertUTCtoTT(jdUtc: Double) = jdUtc + leapSeconds(jdUtc) / 86400.0 + 32.184 / 86400.0

    private fun getBodyLightAdjusted(origin: DoubleArray, body: Int, jd: Double): DoubleArray {
        var jdLight = jd
        var b = DoubleArray(6)
        repeat(3) {
            b = getBodyPV(body, jdLight)
            val r = sqrt((origin[0] - b[0]) * (origin[0] - b[0]) + (origin[1] - b[1]) * (origin[1] - b[1]) + (origin[2] - b[2]) * (origin[2] - b[2]))
            val lightTime = r / (299792458.0 / 149597870691.0 * 86400.0)
            jdLight = jd - lightTime
        }
        return b
    }

    private fun convertGeodeticToECEF(lat: Double, lon: Double, height: Double): DoubleArray {
        val a = 6378136.6 / 149597870691.0
        val f = 1 / 298.25642
        val c = 1 / sqrt(cos(lat) * cos(lat) + (1.0 - f) * (1.0 - f) * (sin(lat) * sin(lat)))
        val s = (1 - f) * (1 - f) * c
        return doubleArrayOf(
            (a * c + height) * cos(lat) * cos(lon),
            (a * c + height) * cos(lat) * sin(lon),
            (a * s + height) * sin(lat),
        )
    }

    /** Returns [ra, dec, r]. */
    fun xyzToRaDec(v: DoubleArray): DoubleArray {
        val r = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        var l = atan2(v[1], v[0])
        if (l < 0) l += 2 * PI
        return doubleArrayOf(l, 0.5 * PI - acos(v[2] / r), r)
    }

    /** Returns [az, alt, localSiderealTime, hourAngle]; az 0 = North. */
    fun raDecToAltAz(ra: Double, dec: Double, lat: Double, lon: Double, jdUt: Double): DoubleArray {
        val lst = (greenwichApparentSiderealTime(jdUt) + lon) % (2 * PI)
        var h = lst - ra
        if (h < 0) h += 2 * PI
        if (h > PI) h -= 2 * PI
        var az = atan2(sin(h), cos(h) * sin(lat) - tan(dec) * cos(lat))
        val alt = asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(h))
        az -= PI
        if (az < 0) az += 2 * PI
        return doubleArrayOf(az, alt, lst, h)
    }

    /** Heliocentric J2000 position [x, y, z] in AU plus velocity [vx, vy, vz] (Earth only) in AU/day. */
    fun getBodyPV(body: Int, jdTdb: Double): DoubleArray {
        val t = (jdTdb - 2451545.0) / 365250.0
        var v = doubleArrayOf(0.0, 0.0, 0.0)
        val b = when (body) {
            MERCURY -> vsop87a_xsmall.getMercury(t)
            VENUS -> vsop87a_xsmall.getVenus(t)
            EARTH -> Vsop87LargeEarth.getEarth(t).also { v = vsop87a_milli_velocities.getEarth(t) }
            MARS -> vsop87a_xsmall.getMars(t)
            JUPITER -> vsop87a_xsmall.getJupiter(t)
            SATURN -> vsop87a_xsmall.getSaturn(t)
            URANUS -> vsop87a_xsmall.getUranus(t)
            NEPTUNE -> vsop87a_xsmall.getNeptune(t)
            MOON -> vsop87a_xsmall.getMoon(Vsop87LargeEarth.getEarth(t), Vsop87LargeEarth.getEmb(t))
            else -> doubleArrayOf(0.0, 0.0, 0.0)
        }
        val p = rotVsopToJ2000(b)
        val w = rotVsopToJ2000(v)
        return doubleArrayOf(p[0], p[1], p[2], w[0], w[1], w[2])
    }

    private fun rotVsopToJ2000(x: DoubleArray) = doubleArrayOf(
        x[0] + x[1] * 0.000000440360 + x[2] * -0.000000190919,
        x[0] * -0.000000479966 + x[1] * 0.917482137087 + x[2] * -0.397776982902,
        x[1] * 0.397776982902 + x[2] * 0.917482137087,
    )

    private fun meanObliquity(t: Double) =
        arcsec(84381.406 - 46.836769 * t - 0.0001831 * t * t + 0.00200340 * t * t * t - 0.000000576 * t * t * t * t - 0.0000000434 * t * t * t * t * t)

    private fun getNutationMatrix(jdTdb: Double): Array<DoubleArray> {
        val t = (jdTdb - 2451545.5) / 36525.0
        val (dpsi, deps) = nutation2000BTruncated(t)
        val eps = meanObliquity(t)
        val a = xRotation(eps)
        val b = matMul(zRotation(-dpsi), a)
        return matMul(xRotation(-(eps + deps)), b)
    }

    /** IAU 2000B nutation truncated to 6 terms (Kaplan 2005). Returns (dpsi, deps) in radians. */
    private fun nutation2000BTruncated(t: Double): Pair<Double, Double> {
        val as2r = 1.0 / 3600.0 * PI / 180.0
        val t2 = t * t
        val t3 = t * t2
        val t4 = t * t3
        val lp = as2r * (1287104.79305 + 129596581.0481 * t - 0.5532 * t2 + 0.000136 * t3 - 0.00001149 * t4)
        val f = as2r * (335779.526232 + 1739527262.8478 * t - 12.7512 * t2 - 0.001037 * t3 + 0.00000417 * t4)
        val d = as2r * (1072260.70369 + 1602961601.2090 * t - 6.3706 * t2 + 0.006593 * t3 - 0.00003169 * t4)
        val om = as2r * (450160.398036 - 6962890.5431 * t + 7.4722 * t2 + 0.007702 * t3 - 0.00005939 * t4)
        var dp = 0.0
        var de = 0.0
        var arg = lp + 2 * (f - d + om)
        dp += (-516821 + 1226 * t) * sin(arg) + -524 * cos(arg)
        de += (224386 + -677 * t) * cos(arg) + -174 * sin(arg)
        dp += (1475877 + -3633 * t) * sin(lp) + 11817 * cos(lp)
        de += (73871 + -184 * t) * cos(lp) + -1924 * sin(lp)
        arg = 2 * om
        dp += (2074554 + 207 * t) * sin(arg) + -698 * cos(arg)
        de += (-897492 + 470 * t) * cos(arg) + -291 * sin(arg)
        arg = 2 * (f + om)
        dp += (-2276413 + -234 * t) * sin(arg) + 2796 * cos(arg)
        de += (978459 + -485 * t) * cos(arg) + 1374 * sin(arg)
        arg = 2 * (f - d + om)
        dp += (-13170906 + -1675 * t) * sin(arg) + -13696 * cos(arg)
        de += (5730336 + -3015 * t) * cos(arg) + -4587 * sin(arg)
        dp += (-172064161 + -174666 * t) * sin(om) + 33386 * cos(om)
        de += (92052331 + 9086 * t) * cos(om) + 15377 * sin(om)
        return Pair(dp * as2r / 10000000, de * as2r / 10000000)
    }

    fun greenwichApparentSiderealTime(jdUt1: Double): Double {
        var gast = (greenwichMeanSiderealTime(jdUt1) + equationOfTheEquinoxes(jdUt1)) % (2 * PI)
        if (gast < 0) gast += 2 * PI
        return gast
    }

    private fun greenwichMeanSiderealTime(jdUt1: Double): Double {
        val t = (jdUt1 - 2451545.0) / 36525.0
        var gmst = (earthRotationAngle(jdUt1) + arcsec(0.014506 + 4612.15739966 * t + 1.39667721 * t * t + -0.00009344 * t * t * t + 0.00001882 * t * t * t * t)) % (2 * PI)
        if (gmst < 0) gmst += 2 * PI
        return gmst
    }

    private fun earthRotationAngle(jdUt1: Double): Double {
        val t = jdUt1 - 2451545.0
        val frac = jdUt1 % 1.0
        var era = (2 * PI * (0.7790572732640 + 0.00273781191135448 * t + frac)) % (2 * PI)
        if (era < 0) era += 2 * PI
        return era
    }

    private fun equationOfTheEquinoxes(jdUt1: Double): Double {
        val t = (jdUt1 - 2451545.0) / 36525.0
        val (dpsi, deps) = nutation2000BTruncated(t)
        return dpsi * cos(meanObliquity(t) + deps)
    }

    // --- 3x3 helpers, same conventions as the web app's Vec class (row vectors times matrix rows) ---

    private fun arcsec(v: Double) = v / 3600.0 * PI / 180.0
    private fun sub(a: DoubleArray, b: DoubleArray) = DoubleArray(minOf(a.size, b.size)) { a[it] - b[it] }
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun magnitude(a: DoubleArray) = sqrt(dot(a, a))

    private fun vecMatrixMul(v: DoubleArray, m: Array<DoubleArray>) =
        DoubleArray(3) { v[0] * m[it][0] + v[1] * m[it][1] + v[2] * m[it][2] }

    private fun transpose(m: Array<DoubleArray>) = Array(3) { i -> DoubleArray(3) { j -> m[j][i] } }

    private fun matMul(a: Array<DoubleArray>, b: Array<DoubleArray>) =
        Array(3) { i -> DoubleArray(3) { j -> a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j] } }

    private fun xRotation(r: Double) = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, cos(r), sin(r)), doubleArrayOf(0.0, -sin(r), cos(r)))

    private fun zRotation(r: Double) = arrayOf(
        doubleArrayOf(cos(r), sin(r), 0.0), doubleArrayOf(-sin(r), cos(r), 0.0), doubleArrayOf(0.0, 0.0, 1.0))
}

object JulianDate {
    private fun int(d: Double): Double = if (d > 0) floor(d) else if (d == floor(d)) d else floor(d) - 1

    fun fromGregorian(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Double): Double {
        var y = year
        var m = month
        val gregorian = !(y < 1582 || (y == 1582 && (m < 10 || (m == 10 && day < 5))))
        if (m < 3) { y -= 1; m += 12 }
        var b = 0.0
        if (gregorian) {
            val a = int(y / 100.0)
            b = 2 - a + int(a / 4.0)
        }
        return int(365.25 * (y + 4716)) + int(30.6001 * (m + 1)) + day + b - 1524.5 +
            hour / 24.0 + min / 1440.0 + sec / 86400.0
    }

    fun fromEpochMillis(ms: Long) = ms / 86400000.0 + 2440587.5
}
