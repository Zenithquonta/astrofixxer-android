package org.astrofixxer.astro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Near-Earth SGP4 (Vallado et al. 2006 "Revisiting Spacetrack Report #3", WGS-72), for satellites with periods under
 * 225 minutes such as the ISS. ponytail: deep-space SDP4 is not ported; [isDeepSpace] satellites are skipped.
 * Positions are TEME, km; velocities km/s.
 */
class Sgp4(val name: String, line1: String, line2: String) {
    val catalogNumber = line1.substring(2, 7).trim()
    /** Epoch as a Julian date (UTC). */
    val epochJd: Double

    private val bstar: Double
    private val inclo: Double
    private val nodeo: Double
    private val ecco: Double
    private val argpo: Double
    private val mo: Double
    private val noKozai: Double

    // Initialised constants.
    private val noUnkozai: Double
    private val isimp: Boolean
    private val con41: Double; private val x1mth2: Double; private val x7thm1: Double
    private val cc1: Double; private val cc4: Double; private val cc5: Double
    private val d2: Double; private val d3: Double; private val d4: Double
    private val delmo: Double; private val eta: Double; private val sinmao: Double
    private val argpdot: Double; private val mdot: Double; private val nodedot: Double; private val nodecf: Double
    private val omgcof: Double; private val xmcof: Double; private val xlcof: Double; private val aycof: Double
    private val t2cof: Double; private val t3cof: Double; private val t4cof: Double; private val t5cof: Double
    val isDeepSpace: Boolean

    init {
        val year2 = line1.substring(18, 20).toInt()
        val year = if (year2 < 57) 2000 + year2 else 1900 + year2
        val dayOfYear = line1.substring(20, 32).trim().toDouble()
        epochJd = JulianDate.fromGregorian(year, 1, 1, 0, 0, 0.0) + dayOfYear - 1
        bstar = expField(line1.substring(53, 61))
        inclo = line2.substring(8, 16).trim().toDouble() * D2R
        nodeo = line2.substring(17, 25).trim().toDouble() * D2R
        ecco = ("0." + line2.substring(26, 33).trim()).toDouble()
        argpo = line2.substring(34, 42).trim().toDouble() * D2R
        mo = line2.substring(43, 51).trim().toDouble() * D2R
        noKozai = line2.substring(52, 63).trim().toDouble() / XPDOTP

        val x2o3 = 2.0 / 3.0
        val eccsq = ecco * ecco
        val omeosq = 1 - eccsq
        val rteosq = sqrt(omeosq)
        val cosio = cos(inclo)
        val cosio2 = cosio * cosio
        val ak = (XKE / noKozai).pow(x2o3)
        val d1 = 0.75 * J2 * (3 * cosio2 - 1) / (rteosq * omeosq)
        var del = d1 / (ak * ak)
        val adel = ak * (1 - del * del - del * (1.0 / 3.0 + 134 * del * del / 81))
        del = d1 / (adel * adel)
        noUnkozai = noKozai / (1 + del)
        val ao = (XKE / noUnkozai).pow(x2o3)
        val sinio = sin(inclo)
        val po = ao * omeosq
        val con42 = 1 - 5 * cosio2
        con41 = -con42 - cosio2 - cosio2
        val posq = po * po
        val rp = ao * (1 - ecco)
        isDeepSpace = 2 * PI / noUnkozai >= 225

        isimp = rp < 220 / RADIUS + 1
        var sfour = 78 / RADIUS + 1
        var qzms24 = ((120 - 78) / RADIUS).pow(4)
        val perige = (rp - 1) * RADIUS
        if (perige < 156) {
            sfour = perige - 78
            if (perige < 98) sfour = 20.0
            qzms24 = ((120 - sfour) / RADIUS).pow(4)
            sfour = sfour / RADIUS + 1
        }
        val pinvsq = 1 / posq
        val tsi = 1 / (ao - sfour)
        eta = ao * ecco * tsi
        val etasq = eta * eta
        val eeta = ecco * eta
        val psisq = abs(1 - etasq)
        val coef = qzms24 * tsi.pow(4)
        val coef1 = coef / psisq.pow(3.5)
        val cc2 = coef1 * noUnkozai * (ao * (1 + 1.5 * etasq + eeta * (4 + etasq)) +
            0.375 * J2 * tsi / psisq * con41 * (8 + 3 * etasq * (8 + etasq)))
        cc1 = bstar * cc2
        val cc3 = if (ecco > 1.0e-4) -2 * coef * tsi * J3OJ2 * noUnkozai * sinio / ecco else 0.0
        x1mth2 = 1 - cosio2
        cc4 = 2 * noUnkozai * coef1 * ao * omeosq * (eta * (2 + 0.5 * etasq) + ecco * (0.5 + 2 * etasq) -
            J2 * tsi / (ao * psisq) * (-3 * con41 * (1 - 2 * eeta + etasq * (1.5 - 0.5 * eeta)) +
                0.75 * x1mth2 * (2 * etasq - eeta * (1 + etasq)) * cos(2 * argpo)))
        cc5 = 2 * coef1 * ao * omeosq * (1 + 2.75 * (etasq + eeta) + eeta * etasq)
        val cosio4 = cosio2 * cosio2
        val temp1 = 1.5 * J2 * pinvsq * noUnkozai
        val temp2 = 0.5 * temp1 * J2 * pinvsq
        val temp3 = -0.46875 * J4 * pinvsq * pinvsq * noUnkozai
        mdot = noUnkozai + 0.5 * temp1 * rteosq * con41 + 0.0625 * temp2 * rteosq * (13 - 78 * cosio2 + 137 * cosio4)
        argpdot = -0.5 * temp1 * con42 + 0.0625 * temp2 * (7 - 114 * cosio2 + 395 * cosio4) + temp3 * (3 - 36 * cosio2 + 49 * cosio4)
        val xhdot1 = -temp1 * cosio
        nodedot = xhdot1 + (0.5 * temp2 * (4 - 19 * cosio2) + 2 * temp3 * (3 - 7 * cosio2)) * cosio
        omgcof = bstar * cc3 * cos(argpo)
        xmcof = if (ecco > 1.0e-4) -x2o3 * coef * bstar / eeta else 0.0
        nodecf = 3.5 * omeosq * xhdot1 * cc1
        t2cof = 1.5 * cc1
        xlcof = -0.25 * J3OJ2 * sinio * (3 + 5 * cosio) / (if (abs(cosio + 1) > 1.5e-12) 1 + cosio else 1.5e-12)
        aycof = -0.5 * J3OJ2 * sinio
        delmo = (1 + eta * cos(mo)).pow(3)
        sinmao = sin(mo)
        x7thm1 = 7 * cosio2 - 1
        if (!isimp) {
            val cc1sq = cc1 * cc1
            d2 = 4 * ao * tsi * cc1sq
            val temp = d2 * tsi * cc1 / 3
            d3 = (17 * ao + sfour) * temp
            d4 = 0.5 * temp * ao * tsi * (221 * ao + 31 * sfour) * cc1
            t3cof = d2 + 2 * cc1sq
            t4cof = 0.25 * (3 * d3 + cc1 * (12 * d2 + 10 * cc1sq))
            t5cof = 0.2 * (3 * d4 + 12 * cc1 * d3 + 6 * d2 * d2 + 15 * cc1sq * (2 * d2 + cc1sq))
        } else {
            d2 = 0.0; d3 = 0.0; d4 = 0.0; t3cof = 0.0; t4cof = 0.0; t5cof = 0.0
        }
    }

    /** TEME position (km) and velocity (km/s) [tsince] minutes after epoch. */
    fun propagate(tsince: Double): Pair<DoubleArray, DoubleArray> {
        val x2o3 = 2.0 / 3.0
        val t = tsince
        val xmdf = mo + mdot * t
        val argpdf = argpo + argpdot * t
        val nodedf = nodeo + nodedot * t
        var argpm = argpdf
        var mm = xmdf
        val t2 = t * t
        var nodem = nodedf + nodecf * t2
        var tempa = 1 - cc1 * t
        var tempe = bstar * cc4 * t
        var templ = t2cof * t2
        if (!isimp) {
            val delomg = omgcof * t
            val delm = xmcof * ((1 + eta * cos(xmdf)).pow(3) - delmo)
            val temp = delomg + delm
            mm = xmdf + temp
            argpm = argpdf - temp
            val t3 = t2 * t
            val t4 = t3 * t
            tempa = tempa - d2 * t2 - d3 * t3 - d4 * t4
            tempe += bstar * cc5 * (sin(mm) - sinmao)
            templ += t3cof * t3 + t4 * (t4cof + t * t5cof)
        }
        var nm = noUnkozai
        var em = ecco
        val am = (XKE / nm).pow(x2o3) * tempa * tempa
        nm = XKE / am.pow(1.5)
        em -= tempe
        if (em < 1.0e-6) em = 1.0e-6
        mm += noUnkozai * templ
        var xlm = mm + argpm + nodem
        nodem %= TWO_PI
        argpm %= TWO_PI
        xlm %= TWO_PI
        mm = (xlm - argpm - nodem) % TWO_PI

        val sinip = sin(inclo)
        val cosip = cos(inclo)
        val axnl = em * cos(argpm)
        var temp = 1 / (am * (1 - em * em))
        val aynl = em * sin(argpm) + temp * aycof
        val xl = mm + argpm + nodem + temp * xlcof * axnl
        val u = (xl - nodem) % TWO_PI
        var eo1 = u
        var tem5 = 9999.9
        var ktr = 1
        var sineo1 = 0.0
        var coseo1 = 0.0
        while (abs(tem5) >= 1.0e-12 && ktr <= 10) {
            sineo1 = sin(eo1)
            coseo1 = cos(eo1)
            tem5 = 1 - coseo1 * axnl - sineo1 * aynl
            tem5 = (u - aynl * coseo1 + axnl * sineo1 - eo1) / tem5
            if (abs(tem5) >= 0.95) tem5 = if (tem5 > 0) 0.95 else -0.95
            eo1 += tem5
            ktr++
        }
        val ecose = axnl * coseo1 + aynl * sineo1
        val esine = axnl * sineo1 - aynl * coseo1
        val el2 = axnl * axnl + aynl * aynl
        val pl = am * (1 - el2)
        val rl = am * (1 - ecose)
        val rdotl = sqrt(am) * esine / rl
        val rvdotl = sqrt(pl) / rl
        val betal = sqrt(1 - el2)
        temp = esine / (1 + betal)
        val sinu = am / rl * (sineo1 - aynl - axnl * temp)
        val cosu = am / rl * (coseo1 - axnl + aynl * temp)
        var su = atan2(sinu, cosu)
        val sin2u = (cosu + cosu) * sinu
        val cos2u = 1 - 2 * sinu * sinu
        temp = 1 / pl
        val temp1 = 0.5 * J2 * temp
        val temp2 = temp1 * temp
        val mrt = rl * (1 - 1.5 * temp2 * betal * con41) + 0.5 * temp1 * x1mth2 * cos2u
        su -= 0.25 * temp2 * x7thm1 * sin2u
        val xnode = nodem + 1.5 * temp2 * cosip * sin2u
        val xinc = inclo + 1.5 * temp2 * cosip * sinip * cos2u
        val mvt = rdotl - nm * temp1 * x1mth2 * sin2u / XKE
        val rvdot = rvdotl + nm * temp1 * (x1mth2 * cos2u + 1.5 * con41) / XKE
        val sinsu = sin(su); val cossu = cos(su)
        val snod = sin(xnode); val cnod = cos(xnode)
        val sini = sin(xinc); val cosi = cos(xinc)
        val xmx = -snod * cosi
        val xmy = cnod * cosi
        val ux = xmx * sinsu + cnod * cossu
        val uy = xmy * sinsu + snod * cossu
        val uz = sini * sinsu
        val vx = xmx * cossu - cnod * sinsu
        val vy = xmy * cossu - snod * sinsu
        val vz = sini * cossu
        val vk = RADIUS * XKE / 60
        return Pair(
            doubleArrayOf(mrt * ux * RADIUS, mrt * uy * RADIUS, mrt * uz * RADIUS),
            doubleArrayOf((mvt * ux + rvdot * vx) * vk, (mvt * uy + rvdot * vy) * vk, (mvt * uz + rvdot * vz) * vk),
        )
    }

    /** Position (TEME, km) at a Julian date (UTC). */
    fun positionAt(jdUtc: Double) = propagate((jdUtc - epochJd) * 1440).first

    companion object {
        const val RADIUS = 6378.135 // WGS-72, km
        private const val MU = 398600.8
        private val XKE = 60.0 / sqrt(RADIUS * RADIUS * RADIUS / MU)
        private const val J2 = 0.001082616
        private const val J3 = -0.00000253881
        private const val J4 = -0.00000165597
        private const val J3OJ2 = J3 / J2
        private const val D2R = PI / 180
        private const val TWO_PI = 2 * PI
        private const val XPDOTP = 1440.0 / (2 * PI)

        /** " 28098-4" -> 0.28098e-4 (TLE implied-decimal exponent field). */
        private fun expField(f: String): Double {
            val s = f.trim()
            if (s.isEmpty()) return 0.0
            val sign = if (s.startsWith("-")) -1.0 else 1.0
            val body = s.trimStart('-', '+')
            val expSign = body.lastIndexOfAny(charArrayOf('-', '+'))
            if (expSign <= 0) return sign * ("0.$body").toDouble()
            return sign * ("0." + body.substring(0, expSign)).toDouble() * 10.0.pow(body.substring(expSign).toInt())
        }

        /** Greenwich mean sidereal time (IAU-82), radians, for a Julian date (UT1 ≈ UTC). */
        fun gmst(jd: Double): Double {
            val tut1 = (jd - 2451545.0) / 36525.0
            var temp = -6.2e-6 * tut1 * tut1 * tut1 + 0.093104 * tut1 * tut1 + (876600.0 * 3600 + 8640184.812866) * tut1 + 67310.54841
            temp = (temp * D2R / 240.0) % TWO_PI
            return if (temp < 0) temp + TWO_PI else temp
        }

        /** Parses three-line TLE text (name, line 1, line 2), skipping malformed entries and deep-space orbits. */
        fun parse(text: String): List<Sgp4> {
            val lines = text.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
            val out = mutableListOf<Sgp4>()
            var i = 0
            while (i + 2 < lines.size) {
                val n = lines[i]
                val l1 = lines[i + 1]
                val l2 = lines[i + 2]
                if (l1.startsWith("1 ") && l2.startsWith("2 ")) {
                    runCatching { Sgp4(n.trim(), l1, l2) }.getOrNull()?.takeIf { !it.isDeepSpace }?.let { out += it }
                    i += 3
                } else i += 1
            }
            return out
        }
    }
}
