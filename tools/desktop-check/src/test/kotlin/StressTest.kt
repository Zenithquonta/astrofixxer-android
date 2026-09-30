import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.astrofixxer.astro.CoordinateParser
import org.astrofixxer.astro.JulianDate
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.UserLists
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.AstroGuide
import org.astrofixxer.ui.Erecting
import org.astrofixxer.ui.EyepieceAngle
import org.astrofixxer.ui.Landscape
import org.astrofixxer.ui.MountType
import org.astrofixxer.ui.PhoneEdge
import org.astrofixxer.ui.PhonePlacement
import org.astrofixxer.ui.TelescopeSetup
import org.astrofixxer.ui.TelescopeType
import org.astrofixxer.ui.PointingMode
import org.astrofixxer.ui.SkyScreen
import org.astrofixxer.ui.SkyState
import org.astrofixxer.ui.solarSystem
import org.astrofixxer.ui.upcomingEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Random and extreme inputs: nothing may crash, and the fast paths must agree with brute force. */
class StressTest {
    private val catalog get() = Fixtures.catalog
    private val rnd = Random(2026)

    private fun junk(alphabet: String, maxLen: Int = 24) = String(CharArray(rnd.nextInt(maxLen + 1)) { alphabet[rnd.nextInt(alphabet.length)] })

    @Test fun coordinateParserNeverThrows() {
        val alphabet = "0123456789:hHmMsSdD°'′″\" .+-−\t,e"
        repeat(200_000) {
            val s = junk(alphabet)
            val ra = CoordinateParser.parseRA(s)
            val dec = CoordinateParser.parseDEC(s)
            if (ra != null) assertTrue("RA $ra from '$s'", ra in 0.0..360.0)
            if (dec != null) assertTrue("Dec $dec from '$s'", dec in -90.0..90.0)
        }
        // Regressions: an over-long number used to crash; 90:30 used to be accepted.
        assertNull(CoordinateParser.parseRA("99999999999:00"))
        assertNull(CoordinateParser.parseDEC("+99999999999:00"))
        assertNull(CoordinateParser.parseDEC("90:30"))
        assertEquals(90.0, CoordinateParser.parseDEC("90:00")!!, 0.0)
    }

    @Test fun userListsNeverThrow() {
        val alphabet = "abcM0123456789 ,:()\"\n#−-+."
        repeat(20_000) {
            val text = junk(alphabet, 80)
            UserLists.parseObjects(text) { false }
            UserLists.parseWatchLists(text)
        }
    }

    @Test fun searchNeverThrowsAndNearMatchesBruteForce() {
        repeat(5_000) { catalog.search(junk("MNGCIa b1234567890 -+'αβ", 12)) }
        val bright = catalog.objects.filter { (it.mag ?: 99.0) <= 6.0 }
        repeat(300) {
            val ra = rnd.nextDouble(0.0, 360.0)
            val dec = if (it < 30) listOf(89.9, -89.9, 88.0)[it % 3] else rnd.nextDouble(-90.0, 90.0)
            val radius = rnd.nextDouble(0.5, 80.0)
            val fast = catalog.near(ra, dec, radius, 6.0).filter { (it.mag ?: 99.0) <= 6.0 }.toSet()
            val slow = bright.filter { sep(ra, dec, it.ra, it.dec) <= radius - 1e-9 }.toSet()
            assertTrue("near($ra, $dec, $radius) missed ${(slow - fast).take(3).map { it.name }}", fast.containsAll(slow))
        }
    }

    @Test fun astroGuideNeverThrows() {
        val state = Fixtures.state()
        val moving = solarSystem(state)
        val phrases = listOf("find", "where is", "what is", "align", "night mode", "tonight", "meteor", "eclipse", "M", "NGC", "messier",
            "मंगल", "कहाँ है", "दिखाओ", "आज रात", "रात मोड", "?", "", "   ", "क्या है")
        repeat(3_000) {
            val q = List(rnd.nextInt(4)) { phrases[rnd.nextInt(phrases.size)] }.joinToString(" ") + " " + junk("abM01 ", 6)
            AstroGuide.answer(q, state, catalog, moving, Fixtures.sampleEvents)
            AstroGuide.answer(q, state, null, emptyList(), null) // before the catalogue has loaded
        }
    }

    @Test fun astroGuideRegressions() {
        val state = Fixtures.state()
        val moving = solarSystem(state)
        // "What's up tonight" in Hindi contains "रात" (night) and used to switch night mode instead.
        val reply = AstroGuide.answer("आज रात क्या दिखेगा", state, catalog, moving, Fixtures.sampleEvents)
        assertFalse(state.night)
        assertTrue(reply.speech, reply.speech.startsWith("अभी"))
        AstroGuide.answer("रात मोड चालू", state, catalog, moving, null)
        assertTrue(state.night)
        // Speech recognisers write catalogue prefixes out in words.
        assertEquals("M42", AstroGuide.answer("find Messier 42", state, catalog, moving, null).target?.name)
        assertEquals("NGC7000", AstroGuide.answer("show me N G C 7000", state, catalog, moving, null).target?.name)
    }

    @Test fun skyRendersAnywhereAnytimeAnyOrientation() {
        val cases = 160
        for (i in 0 until cases) {
            val lat = when (i % 8) { 0 -> 90.0; 1 -> -90.0; 2 -> 0.0; else -> rnd.nextDouble(-90.0, 90.0) }
            val millis = (rnd.nextDouble(-2.2e12, 4.1e12)).toLong() // 1900 .. 2100
            val state = SkyState(millis, lat, rnd.nextDouble(-180.0, 180.0)).apply {
                setupDone = true
                fovDeg = listOf(0.5, 1.0, 15.0, 60.0, 120.0)[i % 5]
                device = randomRotation()
                night = rnd.nextBoolean()
                landscape = Landscape.values()[i % Landscape.values().size]
                bortle = 1 + i % 9
                showGrid = i % 3 == 0
                showAtmosphere = i % 4 != 0
                mode = if (i % 2 == 0) PointingMode.FREE else PointingMode.COMPASS
                setup = TelescopeSetup(
                    TelescopeType.values()[i % 3], MountType.values()[(i / 3) % 3], PhonePlacement.values()[i % 3], PhoneEdge.values()[i % 4],
                    EyepieceAngle.values()[i % 3], Erecting.values()[i % 3], listOf(0, 90, 180, 270)[i % 4], i % 2 == 1,
                )
                matchEyepieceView = i % 3 != 0
                target = catalog.objects[rnd.nextInt(catalog.objects.size)]
                if (i % 3 == 1) { // aligned on a random star that is up (others are refused, as in the app)
                    val star = catalog.objects.filter { it.type == "S" }.let { stars -> (0 until 50).map { stars[rnd.nextInt(stars.size)] }.firstOrNull { canAlignOn(it) } }
                    if (star != null) { startAlign(); pickStar(star); confirmAlignment() }
                }
                if (i % 5 == 2) { // mid-alignment with a dragged map
                    startAlign()
                    dragAdjust(rnd.nextFloat() * 400 - 200, rnd.nextFloat() * 400 - 200, 360f, 780f)
                    if (i % 10 == 2) catalog.objects.firstOrNull { it.type == "S" && canAlignOn(it) }?.let { beginCentering(it) }
                }
                live = i % 6 != 0
            }
            val size = if (i % 2 == 0) (360 to 780) else (780 to 360) // portrait and landscape
            val scene = ImageComposeScene(size.first * 2, size.second * 2, Density(2f)) {
                SkyScreen(state, catalog, solarSystem(state), Fixtures.sampleEvents)
            }
            try {
                scene.render(0)
                scene.render(16_000_000)
            } catch (e: Throwable) {
                throw AssertionError("render crashed for lat=$lat time=$millis fov=${state.fovDeg}", e)
            } finally {
                scene.close()
            }
            val g = state.guidance()!!
            assertTrue("guidance NaN at case $i", g.toList().none { it.isNaN() })
            state.moveHint()!!.let { h -> assertTrue("move hint NaN at case $i", listOf(h.vertical, h.horizontal, h.separationDeg, h.arrowDeg).none { it.isNaN() }) }
        }
    }

    @Test fun eventsWorkAtThePolesAndEquator() {
        val jd = JulianDate.fromEpochMillis(Fixtures.start)
        val stars = catalog.objects.filter { it.type == "S" && (it.mag ?: 99.0) <= 2.0 }.map { Triple(it.name, it.ra, it.dec) }
        for (lat in listOf(89.9, -89.9, 0.0, 66.6)) {
            val events = upcomingEvents(jd, 30, emptyList(), emptyList(), stars, lat, 0.0)
            assertTrue("no events at lat $lat", events.isNotEmpty())
            assertTrue(events.all { it.jd.isFinite() && it.title.isNotBlank() })
        }
    }

    private fun sep(ra1: Double, de1: Double, ra2: Double, de2: Double): Double {
        val d = PI / 180
        return acos((sin(de1 * d) * sin(de2 * d) + cos(de1 * d) * cos(de2 * d) * cos((ra1 - ra2) * d)).coerceIn(-1.0, 1.0)) / d
    }

    private fun randomRotation(): DoubleArray =
        Pointing.rotationMatrix(rnd.nextDouble(0.0, 360.0), rnd.nextDouble(-180.0, 180.0), rnd.nextDouble(-90.0, 90.0))
}
