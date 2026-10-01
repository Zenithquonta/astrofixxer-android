import androidx.compose.ui.test.ExperimentalTestApi
import org.astrofixxer.astro.ApparentPosition
import org.astrofixxer.astro.Pointing
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.eclipticToEquatorial
import org.astrofixxer.ui.starColour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** The sky markings are drawn in the right places, and look right (build/screens/plot-*.png). */
@OptIn(ExperimentalTestApi::class)
class PlottingTest {
    private val catalog get() = Fixtures.catalog

    private fun sep(ra1: Double, de1: Double, ra2: Double, de2: Double): Double {
        val d = PI / 180
        return acos((sin(de1 * d) * sin(de2 * d) + cos(de1 * d) * cos(de2 * d) * cos((ra1 - ra2) * d)).coerceIn(-1.0, 1.0)) / d
    }

    @Test fun theSunIsOnTheEclipticAllYear() {
        val state = Fixtures.state()
        for (day in 0 until 365 step 15) {
            state.timeMillis = Fixtures.start + day * 86_400_000L
            val (ra, dec) = state.bodyPosition(ApparentPosition.SUN)
            val closest = (0 until 3600).minOf { val (r, d) = eclipticToEquatorial(it / 10.0); sep(ra, dec, r, d) }
            assertTrue("Sun is ${"%.3f".format(closest)}° off the drawn ecliptic on day $day", closest < 0.05)
        }
    }

    @Test fun vegaIsInsideLyrasBoundary() {
        // Ray-casting point-in-polygon in RA/Dec: count the boundary edges crossed going north from Vega. Lyra's
        // boundary is a closed loop, so a point inside it crosses an odd number of Lyra edges.
        val vega = catalog.find("Vega")!!
        var crossings = 0
        for (edge in catalog.boundaries.filter { "LYR" in it.constellations }) {
            val e = edge.points
            for (k in 0 until e.size / 2 - 1) {
                val (ra0, de0, ra1, de1) = listOf(e[2 * k], e[2 * k + 1], e[2 * k + 2], e[2 * k + 3])
                if ((ra0 > vega.ra) != (ra1 > vega.ra) && kotlin.math.abs(ra0 - ra1) < 180) {
                    val decAt = de0 + (vega.ra - ra0) / (ra1 - ra0) * (de1 - de0)
                    if (decAt > vega.dec) crossings++
                }
            }
        }
        assertEquals("Vega should be inside Lyra (odd number of crossings)", 1, crossings % 2)
    }

    @Test fun starColoursRunBlueToRed() {
        val blue = starColour(-0.3)
        val red = starColour(1.8)
        assertTrue(blue.blue > blue.red)
        assertTrue(red.red > red.blue)
    }

    @Test fun eyepieceFieldFromFocalLengths() {
        val state = Fixtures.state().apply { telescopeFocalMm = 1200.0; eyepieceFocalMm = 25.0; eyepieceAfovDeg = 52.0 }
        assertEquals(1.083, state.eyepieceFovDeg, 0.001) // 52° × 25 / 1200: a classic 8" Dobsonian with a 25 mm eyepiece
    }

    @Test fun renderAllMarkings() = phoneTest {
        val state = Fixtures.state().apply {
            showEquatorialGrid = true; showMeridian = true; showEcliptic = true; showBoundaries = true
            target = catalog.find("M57"); fovDeg = 90.0
        }
        Fixtures.pointAt(state, catalog.find("Vega")!!)
        Fixtures.align(state, catalog.find("Vega")!!)
        assertEquals(AlignState.ALIGNED, state.align)
        setContent { AppScreen(state) }
        screenshot("plot-markings-day")
        state.night = true
        screenshot("plot-markings-night")
        state.night = false
        state.fovDeg = 3.0
        Fixtures.pointAt(state, catalog.find("M57")!!) // telescope on the target: bullseye filled, eyepiece circle visible
        screenshot("plot-eyepiece-on-target")
        state.setup = state.setup.copy(mount = org.astrofixxer.ui.MountType.EQUATORIAL)
        Fixtures.pointAt(state, catalog.find("Vega")!!)
        screenshot("plot-equatorial-guidance")
        onNode(androidx.compose.ui.test.hasText("RA: ", substring = true)).assertExists()
        onNode(androidx.compose.ui.test.hasText("Dec: ", substring = true)).assertExists()
        assertTrue(Pointing.dot(state.camera()[2], state.ray(catalog.find("Vega")!!)) > 0.999)
    }
}
