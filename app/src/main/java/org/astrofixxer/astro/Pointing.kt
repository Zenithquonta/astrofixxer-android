package org.astrofixxer.astro

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Telescope pointing math, ported from the web app (rayFromPos, getRotationMatrix, getCameraRays,
 * selectAlign, cameraBearing). Vectors are [east, north, up]; 3x3 matrices are row-major DoubleArray(9).
 */
object Pointing {
    private const val D2R = PI / 180

    /** Unit vector toward a J2000 RA/Dec (degrees) for an observer, using the same simple sidereal time as the web app. */
    fun rayFromPos(raDeg: Double, decDeg: Double, timeMillis: Long, latDeg: Double, lonDeg: Double): DoubleArray {
        val ra = raDeg * D2R
        val de = decDeg * D2R
        val tu = JulianDate.fromEpochMillis(timeMillis) - 2451545.0
        val angle = 2 * PI * (0.7790572732640 + 1.00273781191135448 * tu)
        val h = angle + lonDeg * D2R - ra
        val f = latDeg * D2R
        val az = atan2(sin(h), cos(h) * sin(f) - tan(de) * cos(f))
        val alt = asin(sin(f) * sin(de) + cos(f) * cos(de) * cos(h))
        return doubleArrayOf(-sin(az) * cos(alt), -cos(az) * cos(alt), sin(alt))
    }

    /** Inverse of [rayFromPos]: J2000 RA/Dec in degrees for a [east, north, up] direction. */
    fun rayToRaDec(ray: DoubleArray, timeMillis: Long, latDeg: Double, lonDeg: Double): Pair<Double, Double> {
        val az = atan2(-ray[0], -ray[1]) // measured from south, as in rayFromPos
        val alt = asin(ray[2].coerceIn(-1.0, 1.0))
        val f = latDeg * D2R
        val h = atan2(sin(az), cos(az) * sin(f) + tan(alt) * cos(f))
        val dec = asin((sin(f) * sin(alt) - cos(f) * cos(alt) * cos(az)).coerceIn(-1.0, 1.0))
        val tu = JulianDate.fromEpochMillis(timeMillis) - 2451545.0
        val lst = 2 * PI * (0.7790572732640 + 1.00273781191135448 * tu) + lonDeg * D2R
        val ra = ((lst - h) / D2R).mod(360.0)
        return Pair(ra, dec / D2R)
    }

    /** W3C DeviceOrientation ZXY rotation matrix from alpha/beta/gamma in degrees. */
    fun rotationMatrix(alpha: Double, beta: Double, gamma: Double): DoubleArray {
        val x = beta * D2R
        val y = gamma * D2R
        val z = alpha * D2R
        val cX = cos(x); val cY = cos(y); val cZ = cos(z)
        val sX = sin(x); val sY = sin(y); val sZ = sin(z)
        return doubleArrayOf(
            cZ * cY - sZ * sX * sY, -cX * sZ, cY * sZ * sX + cZ * sY,
            cY * sZ + cZ * sX * sY, cZ * cX, sZ * sY - cZ * cY * sX,
            -cX * sY, sX, cX * cY,
        )
    }

    /** Camera frame [top, left, forward] for a device rotation, optionally corrected by an alignment matrix. */
    fun cameraRays(device: DoubleArray, align: DoubleArray? = null): Array<DoubleArray> {
        val fwd = mvec(device, doubleArrayOf(0.0, 1.0, 0.0))
        val hlen = sqrt(fwd[0] * fwd[0] + fwd[1] * fwd[1])
        val lft = doubleArrayOf(-fwd[1] / hlen, fwd[0] / hlen, 0.0)
        val top = cross(fwd, lft)
        return if (align == null) arrayOf(top, lft, fwd)
        else arrayOf(mvec(align, top), mvec(align, lft), mvec(align, fwd))
    }

    /**
     * Alignment matrix that rotates the uncorrected camera frame so its forward axis points at [star]:
     * an azimuth rotation followed by an altitude rotation around the camera's left axis.
     */
    fun alignMatrix(uncorrected: Array<DoubleArray>, star: DoubleArray): DoubleArray {
        val fw = uncorrected[2]
        val left = uncorrected[1]
        val dAz = asin(cross(norm(doubleArrayOf(star[0], star[1], 0.0)), norm(doubleArrayOf(fw[0], fw[1], 0.0)))[2])
        val dAlt = asin(star[2]) - asin(fw[2])
        val dazMat = doubleArrayOf(cos(dAz), sin(dAz), 0.0, -sin(dAz), cos(dAz), 0.0, 0.0, 0.0, 1.0)
        val (u0, u1, u2) = Triple(left[0], left[1], left[2])
        val w = doubleArrayOf(0.0, -u2, u1, u2, 0.0, -u0, -u1, u0, 0.0)
        var daltMat = matAdd(IDENTITY, 1.0, w, sin(-dAlt))
        daltMat = matAdd(daltMat, 1.0, matMul(w, w), 2 * sin(-dAlt / 2) * sin(-dAlt / 2))
        return matMul(dazMat, daltMat)
    }

    /** Target direction in camera coordinates: x right, y up, z forward (z <= 0 means behind). */
    fun bearing(ray: DoubleArray, camera: Array<DoubleArray>) =
        doubleArrayOf(-dot(camera[1], ray), dot(camera[0], ray), dot(camera[2], ray))

    /** ΔAlt/ΔAz in degrees to move the telescope from its pointing [fwd] to [target]; +ΔAz = clockwise (east). */
    fun deltaAltAz(fwd: DoubleArray, target: DoubleArray): Pair<Double, Double> {
        val dAlt = (asin(target[2].coerceIn(-1.0, 1.0)) - asin(fwd[2].coerceIn(-1.0, 1.0))) / D2R
        var dAz = (atan2(target[0], target[1]) - atan2(fwd[0], fwd[1])) / D2R
        if (dAz > 180) dAz -= 360
        if (dAz < -180) dAz += 360
        return Pair(dAlt, dAz)
    }

    val IDENTITY = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

    fun mvec(m: DoubleArray, v: DoubleArray) = doubleArrayOf(
        m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
        m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
        m[6] * v[0] + m[7] * v[1] + m[8] * v[2],
    )

    fun matMul(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { k ->
        val i = k / 3; val j = k % 3
        a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
    }

    private fun matAdd(a: DoubleArray, alpha: Double, b: DoubleArray, beta: Double) = DoubleArray(9) { a[it] * alpha + b[it] * beta }
    fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun norm(v: DoubleArray): DoubleArray { val l = sqrt(dot(v, v)); return doubleArrayOf(v[0] / l, v[1] / l, v[2] / l) }
}
