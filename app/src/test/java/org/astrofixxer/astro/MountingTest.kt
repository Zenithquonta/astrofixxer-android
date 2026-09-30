package org.astrofixxer.astro

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MountingTest {
    private val rnd = Random(7)

    private fun v(x: Double, y: Double, z: Double) = doubleArrayOf(x, y, z)
    private fun horizon(azDeg: Double, altDeg: Double): DoubleArray {
        val a = Math.toRadians(azDeg); val h = Math.toRadians(altDeg)
        return v(sin(a) * cos(h), cos(a) * cos(h), sin(h))
    }
    private fun unit(x: DoubleArray): DoubleArray { val l = sqrt(Pointing.dot(x, x)); return v(x[0] / l, x[1] / l, x[2] / l) }

    /** A device-to-world rotation that sends the phone axis [axis] to the world direction [w], rolled by [rollDeg] about it. */
    private fun deviceFor(axis: DoubleArray, w: DoubleArray, rollDeg: Double): DoubleArray {
        val c = Pointing.cross(axis, w)
        val len = sqrt(Pointing.dot(c, c))
        val minimal = if (len < 1e-12) Pointing.IDENTITY else Pointing.axisAngleMatrix(v(c[0] / len, c[1] / len, c[2] / len), Pointing.angleBetweenDeg(axis, w) * PI / 180)
        return Pointing.matMul(Pointing.axisAngleMatrix(w, Math.toRadians(rollDeg)), minimal)
    }

    private fun rotZ(deg: Double) = Pointing.axisAngleMatrix(v(0.0, 0.0, 1.0), Math.toRadians(deg))

    // ---- phoneAxis

    @Test fun phoneAxisForEveryPlacementAndEdge() {
        val tube = mapOf(PhoneEdge.TOP to v(0.0, 1.0, 0.0), PhoneEdge.BOTTOM to v(0.0, -1.0, 0.0), PhoneEdge.RIGHT to v(1.0, 0.0, 0.0), PhoneEdge.LEFT to v(-1.0, 0.0, 0.0))
        for ((edge, axis) in tube) {
            for (angle in EyepieceAngle.values()) {
                val a = Mounting.phoneAxis(PhonePlacement.TUBE, edge, angle)
                assertArrayEquals("tube $edge", axis, a.vector, 0.0)
                assertFalse(a.needsCheck)
            }
            for (angle in EyepieceAngle.values()) { // the camera-forward placement ignores edge and eyepiece
                val a = Mounting.phoneAxis(PhonePlacement.CAMERA_FORWARD, edge, angle)
                assertArrayEquals(v(0.0, 0.0, -1.0), a.vector, 0.0)
                assertFalse(a.needsCheck)
            }
            val straight = Mounting.phoneAxis(PhonePlacement.EYEPIECE, edge, EyepieceAngle.STRAIGHT)
            assertArrayEquals(v(0.0, 0.0, -1.0), straight.vector, 0.0)
            assertFalse(straight.needsCheck)
            val right = Mounting.phoneAxis(PhonePlacement.EYEPIECE, edge, EyepieceAngle.RIGHT_ANGLE)
            assertArrayEquals("right-angle eyepiece $edge", axis, right.vector, 0.0)
            assertFalse(right.needsCheck)
            val unsure = Mounting.phoneAxis(PhonePlacement.EYEPIECE, edge, EyepieceAngle.UNSURE)
            assertArrayEquals(v(0.0, 0.0, -1.0), unsure.vector, 0.0)
            assertTrue("unsure eyepiece must be flagged", unsure.needsCheck)
        }
    }

    @Test fun cameraRaysForwardIsTheDeviceTimesTheAxis() {
        repeat(50) {
            val device = Pointing.rotationMatrix(rnd.nextDouble(0.0, 360.0), rnd.nextDouble(-60.0, 60.0), rnd.nextDouble(-60.0, 60.0))
            for ((_, axis) in Mounting.CANDIDATE_AXES) {
                val cam = Pointing.cameraRays(device, null, axis)
                assertArrayEquals(Pointing.mvec(device, axis), cam[2], 1e-12)
                // left and top are perpendicular unit vectors, left stays level
                assertEquals(0.0, cam[1][2], 1e-12)
                assertEquals(0.0, Pointing.dot(cam[0], cam[2]), 1e-9)
                assertEquals(0.0, Pointing.dot(cam[1], cam[2]), 1e-9)
            }
        }
        // The default axis is the top edge, as before.
        val d = Pointing.rotationMatrix(30.0, 40.0, 10.0)
        assertArrayEquals(Pointing.cameraRays(d, null, v(0.0, 1.0, 0.0))[2], Pointing.cameraRays(d)[2], 0.0)
        // Straight up has no left: still finite.
        val up = Pointing.cameraRays(deviceFor(v(0.0, 0.0, -1.0), v(0.0, 0.0, 1.0), 0.0), null, v(0.0, 0.0, -1.0))
        assertTrue(up.all { r -> r.all { it.isFinite() } })
    }

    @Test fun alignMatrixPutsTheStarOnTheAxisForAnyAzimuthDifference() {
        repeat(300) {
            val device = Pointing.rotationMatrix(rnd.nextDouble(0.0, 360.0), rnd.nextDouble(-80.0, 80.0), rnd.nextDouble(-80.0, 80.0))
            val axis = Mounting.CANDIDATE_AXES[rnd.nextInt(6)].second
            val star = horizon(rnd.nextDouble(0.0, 360.0), rnd.nextDouble(5.0, 85.0))
            val cam = Pointing.cameraRays(device, null, axis)
            if (kotlin.math.hypot(cam[2][0], cam[2][1]) < 1e-3) return@repeat
            val a = Pointing.alignMatrix(cam, star)
            assertArrayEquals(star, Pointing.cameraRays(device, a, axis)[2], 1e-9)
        }
    }

    // ---- axisCheck

    private data class Truth(val placement: PhonePlacement, val edge: PhoneEdge, val angle: EyepieceAngle, val label: String)

    @Test fun axisCheckFindsTheTrueAxisEvenWithACompassError() {
        val truths = listOf(
            Truth(PhonePlacement.TUBE, PhoneEdge.TOP, EyepieceAngle.STRAIGHT, "+Y"),
            Truth(PhonePlacement.TUBE, PhoneEdge.BOTTOM, EyepieceAngle.STRAIGHT, "−Y"),
            Truth(PhonePlacement.TUBE, PhoneEdge.RIGHT, EyepieceAngle.STRAIGHT, "+X"),
            Truth(PhonePlacement.TUBE, PhoneEdge.LEFT, EyepieceAngle.STRAIGHT, "−X"),
            Truth(PhonePlacement.CAMERA_FORWARD, PhoneEdge.TOP, EyepieceAngle.STRAIGHT, "−Z"),
            Truth(PhonePlacement.EYEPIECE, PhoneEdge.TOP, EyepieceAngle.STRAIGHT, "−Z"),
            Truth(PhonePlacement.EYEPIECE, PhoneEdge.RIGHT, EyepieceAngle.RIGHT_ANGLE, "+X"),
            Truth(PhonePlacement.EYEPIECE, PhoneEdge.BOTTOM, EyepieceAngle.RIGHT_ANGLE, "−Y"),
        )
        for (t in truths) for (compassError in listOf(0.0, 15.0, -15.0)) repeat(10) { trial ->
            val axis = Mounting.phoneAxis(t.placement, t.edge, t.angle).vector
            val stars = listOf(horizon(40.0 + trial * 20, 30.0 + trial * 3), horizon(110.0 + trial * 20, 55.0 - trial * 2), horizon(230.0 - trial * 10, 40.0))
            val samples = stars.map { star ->
                // The telescope really points at the star, but the phone's compass is [compassError] degrees off.
                AlignSample(Pointing.matMul(rotZ(compassError), deviceFor(axis, star, rnd.nextDouble(0.0, 360.0))), star)
            }
            for (n in 2..3) {
                val check = Mounting.axisCheck(samples.take(n))
                assertTrue(check.separationUsed)
                assertEquals("${t.label} compass $compassError trial $trial n=$n", t.label, check.best!!.label)
                assertEquals(0.0, check.best!!.score, 1e-6)
                assertTrue("runner-up should be clearly worse", check.ranked[1].score > 2.0)
            }
        }
    }

    @Test fun axisCheckFlagsStarsThatAreTooCloseTogether() {
        val axis = v(0.0, 1.0, 0.0)
        val close = listOf(horizon(100.0, 40.0), horizon(104.0, 42.0)) // ~3.6° apart
        val samples = close.map { AlignSample(deviceFor(axis, it, 33.0), it) }
        val check = Mounting.axisCheck(samples)
        assertFalse("under 10° apart: no separation test", check.separationUsed)
        assertNull(check.best!!.separationErrorDeg)
        assertEquals("+Y", check.best!!.label) // gravity alone still finds it here
        val far = listOf(horizon(100.0, 40.0), horizon(140.0, 40.0))
        assertTrue(Mounting.axisCheck(far.map { AlignSample(deviceFor(axis, it, 33.0), it) }).separationUsed)
        // One star: works on altitude only.
        assertFalse(Mounting.axisCheck(samples.take(1)).separationUsed)
        // Nothing: no crash, no separation.
        assertFalse(Mounting.axisCheck(emptyList()).separationUsed)
    }

    @Test fun axisCheckUsesTheSeparationWhenAltitudesAreEqual() {
        // Two stars at the same altitude: the altitude term alone cannot separate axes that happen to share an altitude,
        // so the separation term (and the exact score of the true axis, 0) has to carry the ranking.
        val axis = v(0.0, 1.0, 0.0)
        val stars = listOf(horizon(0.0, 45.0), horizon(90.0, 45.0))
        val samples = stars.map { AlignSample(deviceFor(axis, it, 0.0), it) }
        val check = Mounting.axisCheck(samples)
        assertEquals("+Y", check.best!!.label)
        assertNotNull(check.best!!.separationErrorDeg)
        assertEquals(0.0, check.best!!.separationErrorDeg!!, 1e-9)
    }

    // ---- orientationFromNudges

    @Test fun nudgesGiveEveryRotationAndMirror() {
        val table = listOf(
            Triple(Dir.DOWN, Dir.LEFT, ViewOrientation(0, false)),
            Triple(Dir.LEFT, Dir.UP, ViewOrientation(90, false)),
            Triple(Dir.UP, Dir.RIGHT, ViewOrientation(180, false)),
            Triple(Dir.RIGHT, Dir.DOWN, ViewOrientation(270, false)),
            Triple(Dir.DOWN, Dir.RIGHT, ViewOrientation(0, true)),
            Triple(Dir.LEFT, Dir.DOWN, ViewOrientation(90, true)),
            Triple(Dir.UP, Dir.LEFT, ViewOrientation(180, true)),
            Triple(Dir.RIGHT, Dir.UP, ViewOrientation(270, true)),
        )
        for ((up, right, expected) in table) assertEquals("$up/$right", expected, Mounting.orientationFromNudges(up, right))
        // All eight answers are different, and every consistent pair of answers is covered.
        assertEquals(8, table.map { it.first to it.second }.toSet().size)
    }

    @Test fun inconsistentNudgesGiveNull() {
        for (up in Dir.values()) for (right in Dir.values()) {
            val sameAxis = (up == Dir.UP || up == Dir.DOWN) == (right == Dir.UP || right == Dir.DOWN)
            if (sameAxis) assertNull("$up/$right", Mounting.orientationFromNudges(up, right))
            else assertNotNull("$up/$right", Mounting.orientationFromNudges(up, right))
        }
    }

    // ---- initialViewOrientation

    @Test fun initialViewOrientationTable() {
        fun g(type: TelescopeType, angle: EyepieceAngle, erecting: Erecting, placement: PhonePlacement = PhonePlacement.EYEPIECE) =
            Mounting.initialViewOrientation(type, placement, angle, erecting)
        val upright = ViewOrientation(0, false)
        // An erecting prism wins over everything.
        for (type in TelescopeType.values()) for (angle in EyepieceAngle.values()) assertEquals(ViewGuess(upright, false), g(type, angle, Erecting.YES))
        assertEquals(ViewGuess(ViewOrientation(0, true), false), g(TelescopeType.REFRACTOR, EyepieceAngle.RIGHT_ANGLE, Erecting.NO))
        assertEquals(ViewGuess(ViewOrientation(0, true), false), g(TelescopeType.OTHER, EyepieceAngle.RIGHT_ANGLE, Erecting.NO))
        assertEquals(ViewGuess(ViewOrientation(180, false), false), g(TelescopeType.REFRACTOR, EyepieceAngle.STRAIGHT, Erecting.NO))
        assertEquals(ViewGuess(ViewOrientation(180, false), true), g(TelescopeType.REFLECTOR, EyepieceAngle.RIGHT_ANGLE, Erecting.NO))
        assertEquals(ViewGuess(ViewOrientation(180, false), true), g(TelescopeType.REFLECTOR, EyepieceAngle.STRAIGHT, Erecting.NO))
        // Unsure: upright and please check, whatever the type says.
        for (type in TelescopeType.values()) {
            assertEquals(ViewGuess(upright, true), g(type, EyepieceAngle.UNSURE, Erecting.NO))
            assertEquals(ViewGuess(upright, true), g(type, EyepieceAngle.STRAIGHT, Erecting.UNSURE))
        }
        // The type alone never decides: an unknown telescope with a straight eyepiece is only a guess.
        assertEquals(ViewGuess(upright, true), g(TelescopeType.OTHER, EyepieceAngle.STRAIGHT, Erecting.NO))
        // Where the phone sits does not change the guess.
        for (p in PhonePlacement.values()) assertEquals(g(TelescopeType.REFRACTOR, EyepieceAngle.STRAIGHT, Erecting.NO), g(TelescopeType.REFRACTOR, EyepieceAngle.STRAIGHT, Erecting.NO, p))
    }

    // ---- triad

    private fun assertRotation(m: DoubleArray) {
        val mt = DoubleArray(9) { m[(it % 3) * 3 + it / 3] }
        val id = Pointing.matMul(m, mt)
        for (i in 0 until 9) assertEquals(Pointing.IDENTITY[i], id[i], 1e-12)
    }

    @Test fun triadIsExactForConsistentPairs() {
        repeat(200) {
            val q = Pointing.rotationMatrix(rnd.nextDouble(0.0, 360.0), rnd.nextDouble(-170.0, 170.0), rnd.nextDouble(-85.0, 85.0))
            val u1 = unit(v(rnd.nextDouble(-1.0, 1.0), rnd.nextDouble(-1.0, 1.0), rnd.nextDouble(-1.0, 1.0)))
            val u2 = unit(v(rnd.nextDouble(-1.0, 1.0), rnd.nextDouble(-1.0, 1.0), rnd.nextDouble(-1.0, 1.0)))
            if (Pointing.angleBetweenDeg(u1, u2) < 5 || Pointing.angleBetweenDeg(u1, u2) > 175) return@repeat
            val r1 = Pointing.mvec(q, u1)
            val r2 = Pointing.mvec(q, u2)
            val t = Mounting.triad(u1, u2, r1, r2)!!
            assertRotation(t)
            assertArrayEquals(r1, Pointing.mvec(t, u1), 1e-12)
            assertArrayEquals(r2, Pointing.mvec(t, u2), 1e-12)
            for (i in 0 until 9) assertEquals(q[i], t[i], 1e-9)
        }
    }

    @Test fun triadMatchesTheFirstPairExactlyWhenTheSecondIsOff() {
        val u1 = horizon(10.0, 30.0); val u2 = horizon(60.0, 40.0)
        val r1 = horizon(14.0, 32.0); val r2 = horizon(66.0, 39.0) // the angle between r1 and r2 differs slightly from u1/u2
        val t = Mounting.triad(u1, u2, r1, r2)!!
        assertRotation(t)
        assertArrayEquals(r1, Pointing.mvec(t, u1), 1e-12)
        val off = Pointing.angleBetweenDeg(Pointing.mvec(t, u2), r2)
        assertTrue("second star should be off by only the inconsistency, got $off", off in 0.0..2.0)
    }

    @Test fun triadRefusesParallelPairs() {
        val a = horizon(10.0, 30.0)
        assertNull(Mounting.triad(a, a, a, horizon(50.0, 30.0)))
        assertNull(Mounting.triad(a, horizon(50.0, 30.0), a, a))
    }
}
