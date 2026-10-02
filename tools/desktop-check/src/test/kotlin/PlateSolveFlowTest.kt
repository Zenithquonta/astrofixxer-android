import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.astrofixxer.astro.GrayImage
import org.astrofixxer.astro.PhotoAlignment
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.astro.SolveHints
import org.astrofixxer.astro.SolveResult
import org.astrofixxer.astro.StarSource
import org.astrofixxer.astro.SyntheticSky
import org.astrofixxer.astro.Truth
import org.astrofixxer.ui.AlignState
import org.astrofixxer.ui.CameraPermission
import org.astrofixxer.ui.CameraProblem
import org.astrofixxer.ui.EyepieceAngle
import org.astrofixxer.ui.GalleryResult
import org.astrofixxer.ui.PhonePlacement
import org.astrofixxer.ui.PhotoShot
import org.astrofixxer.ui.PlateSolveModel
import org.astrofixxer.ui.PointingMode
import org.astrofixxer.ui.SkyState
import org.astrofixxer.ui.SolveOutcome
import org.astrofixxer.ui.SolveStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * The camera plate-solve flow with a fake phone camera that hands over synthetic star fields. The real solver runs
 * wherever a test needs a real answer; other tests swap in a solver that returns a chosen failure.
 * Nothing here has touched a real camera, a real sky photo or a real permission dialog.
 */
@OptIn(ExperimentalTestApi::class)
class PlateSolveFlowTest {
    private val catalog get() = Fixtures.catalog
    private val vega get() = catalog.find("Vega")!!
    private fun button(label: String) = hasText(label) and hasClickAction()
    private fun star(ra: Double, dec: Double) = SkyObject("Field", ra, dec, null, "S")

    private fun ComposeUiTest.openFromTelescopeTab() {
        onNode(button("Sky")).tap()
        tab("Telescope & orientation")
        onNode(button("Solve with camera")).performScrollTo().tap()
    }

    private fun ComposeUiTest.next() = onNode(button("Next")).tap()

    /** From the arrangement step to the live view. */
    private fun ComposeUiTest.toLive() { next(); waitForIdle(); onNode(hasText("Before you take the photo")).assertExists(); next(); waitForIdle() }

    private fun ComposeUiTest.waitForResult(model: PlateSolveModel) = waitUntil(180_000) { model.step == SolveStep.RESULT }

    private fun ComposeUiTest.takePhotoAndWait(model: PlateSolveModel) { onNode(button("Take photo")).tap(); waitForResult(model); waitForIdle() }

    private fun sep(a: Pair<Double, Double>, b: Pair<Double, Double>) = SyntheticSky.separationDeg(a.first, a.second, b.first, b.second)

    /** Where the phone's rear camera axis must point for the sensors to say the camera looks at [ra], [dec] with the compass [errorDeg] off. */
    private fun aim(s: SkyState, ra: Double, dec: Double, errorDeg: Double = 20.0) {
        Fixtures.pointTelescopeAt(s, star(ra, dec))
        s.device = Pointing.matMul(Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), Math.toRadians(errorDeg)), s.device)
    }

    private fun cameraForwardState(): SkyState = Fixtures.state().also { it.setup = it.setup.copy(placement = PhonePlacement.CAMERA_FORWARD) }

    // ---------------------------------------------------------------- eyepiece: solve, apply

    @Test fun eyepiecePhotoSolvesAndAlignsFromTheAlignmentPanel() = phoneTest {
        val state = eyepieceState(vega, compassErrorDeg = 20.0) // x5: a 65 degree phone camera sees 13 degrees of sky
        val truth = Shots.truth(vega.ra, vega.dec, widthDeg = 13.0, roll = 47.0)
        val host = FakeHost().apply { camera += Shots.render(truth, 11, limitMag = 6.5) }
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }
        assertEquals(0, host.permissionRequests)
        onNode(button("Align")).tap()
        assertEquals(AlignState.PICK_STAR, state.align)
        onNode(button("Align with a photo")).tap()
        assertTrue(model.open)
        // a. the arrangement, from the saved setup: nothing is asked that the setup already says
        onNode(hasText("Centre the bright eyepiece circle in the camera view and focus on stars")).assertExists()
        onNode(hasText("Telescope 125 mm, eyepiece 25 mm: magnification ×5")).assertExists()
        onNode(hasText("Does the eyepiece go straight in, or at a right angle (diagonal or Newtonian)?")).assertDoesNotExist()
        screenshot("solve-eyepiece-arrangement")
        next()
        // b. tips
        onNode(hasText("Before you take the photo")).assertExists()
        assertEquals("the camera is not asked for before the live view opens", 0, host.permissionRequests)
        screenshot("solve-tips")
        next()
        // c. live view
        onNode(hasText("The + shows where the telescope points.")).assertExists()
        assertEquals(1, host.liveViewsOpened)
        onNode(hasText("Camera field of view: 65°")).assertExists()
        screenshot("solve-live-eyepiece")
        val before = state.fingerprint()
        onNode(button("Take photo")).tap()
        // d. solving happens off the UI thread; the screen is there meanwhile
        waitForResult(model); waitForIdle()
        assertEquals("the live camera was closed when the view left the screen", 1, host.liveViewsClosed)
        // e. the result
        val solved = (model.outcome as SolveOutcome.Solved).result
        assertTrue("centre off by ${sep(solved.raDeg to solved.decDeg, truth.raDeg to truth.decDeg)} deg", sep(solved.raDeg to solved.decDeg, truth.raDeg to truth.decDeg) < 0.05)
        onNode(hasText("Solved")).assertExists()
        onNode(hasText("In the constellation Lyra")).assertExists()
        onNode(hasText(" stars matched", substring = true)).assertExists()
        onNode(hasText("Scale: ", substring = true)).assertExists()
        assertFalse(solved.mirrored)
        onNode(hasText("Mirrored: the photo is a mirror image of the sky.")).assertDoesNotExist()
        onNode(hasText("Apply to alignment")).assertIsEnabled()
        onNode(hasText("The telescope is at the centre of the photo")).assertExists()
        onNode(button("Show on map")).assertExists()
        screenshot("solve-result-solved")
        assertEquals("solving alone changes nothing", before, state.fingerprint())
        onNode(hasText("Apply to alignment")).tap()
        waitForIdle()
        assertFalse(model.open)
        assertEquals(AlignState.ALIGNED, state.align)
        assertFalse("the alignment in progress is over", state.aligning)
        val result = state.alignResult!!
        assertTrue(result.fromPhoto)
        assertNull(result.star)
        onNode(hasText("Aligned from a photo. Correction ", substring = true)).assertExists()
        // The calibration points the telescope at the true field centre.
        val trueRay = Pointing.rayFromPos(truth.raDeg, truth.decDeg, state.timeMillis, state.lat, state.lon)
        val off = Pointing.angleBetweenDeg(state.telescopeCamera()[2], trueRay)
        assertTrue("the telescope is $off deg from the true field centre", off < 0.05)
        // 20 degrees of azimuth compass error at Vega's altitude is a correction of roughly 12 to 20 degrees.
        assertTrue("the correction card reports what was corrected: ${result.correctionDeg}", result.correctionDeg > 5.0)
        screenshot("solve-aligned-from-photo")
        onNode(button("Done")).tap()
        assertNull(state.alignResult)
    }

    // ---------------------------------------------------------------- camera beside the tube

    @Test fun cameraForwardNeedsTheOffsetThenAppliesCorrectly() = phoneTest {
        val state = cameraForwardState()
        val w = Shots.W.toDouble(); val h = Shots.H.toDouble()
        val fx = 0.62; val fy = 0.37
        // Picture 1: any sky. The offset is not calibrated, so Apply is disabled with the reason.
        val t1 = Shots.truth(300.0, 35.0, 60.0, roll = 80.0)
        // Picture 2: Vega centred in the eyepiece sits at the telescope's spot (fx, fy) of the camera picture.
        val vegaCentred = Shots.truth(vega.ra, vega.dec, 60.0, roll = 25.0)
        val c2 = Shots.skyAt(vegaCentred, w / 2 - (fx - 0.5) * w, h / 2 - (fy - 0.5) * h)
        val t2 = Shots.truth(c2.first, c2.second, 60.0, roll = 25.0)
        val expected = t2.toPixel(vega.ra, vega.dec)!!.let { it[0] / w to it[1] / h }
        // Picture 3: another part of the sky, mirrored. The telescope is at the same spot of the picture.
        val t3 = Shots.truth(285.0, 20.0, 60.0, roll = 140.0, mirrored = true)
        val host = FakeHost().apply {
            hFovDeg = 60.0
            camera += Shots.render(t1, 21); camera += Shots.render(t2, 22); camera += Shots.render(t3, 23)
        }
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }

        // 1. uncalibrated
        aim(state, t1.raDeg, t1.decDeg)
        openFromTelescopeTab()
        onNode(hasText("Camera offset: not calibrated")).assertExists()
        screenshot("solve-camera-forward-arrangement")
        toLive()
        onNode(hasContentDescription("Telescope marker")).assertDoesNotExist()
        onNode(hasText("The camera offset is not calibrated, so this photo will say where the camera points, not the telescope.")).assertExists()
        takePhotoAndWait(model)
        onNode(hasText("Solved")).assertExists()
        onNode(hasText("The telescope points at RA", substring = true)).assertDoesNotExist()
        onNode(hasText("Apply to alignment")).assertIsNotEnabled()
        onNode(hasText("The camera and telescope don't point exactly the same way. Calibrate the camera offset first.")).assertExists()
        screenshot("solve-result-offset-missing")
        val untouched = state.fingerprint()
        onNode(hasText("Apply to alignment")).tap() // a disabled button does nothing
        assertEquals(untouched, state.fingerprint())
        assertNull(state.alignMatrix)
        onNode(button("Close")).tap()
        assertFalse(model.open)

        // 2. calibrate the offset: pick Vega, take a photo with Vega at the telescope's spot
        openFromTelescopeTab()
        onNode(button("Calibrate camera offset")).tap()
        onNode(hasText("Pick the star you will centre in the eyepiece")).assertExists()
        screenshot("solve-pick-star")
        onNode(hasText("Vega · magnitude", substring = true) and hasClickAction()).tap()
        onNode(hasText("Centre Vega in the eyepiece first.")).assertExists()
        aim(state, t2.raDeg, t2.decDeg)
        next(); waitForIdle()
        onNode(hasText("Centre Vega in the eyepiece, then take the photo.")).assertExists()
        screenshot("solve-live-calibrating")
        takePhotoAndWait(model)
        onNode(hasText("Vega is at ", substring = true)).assertExists()
        onNode(hasText("Apply to alignment")).assertDoesNotExist() // this photo only measures the offset
        onNode(hasText("Save camera offset")).assertIsEnabled()
        assertNull("nothing is saved until the person says so", state.cameraOffset)
        onNode(hasText("Save camera offset")).tap()
        waitForIdle()
        val saved = state.cameraOffset!!
        assertEquals(expected.first, saved.first, 0.004)
        assertEquals(expected.second, saved.second, 0.004)
        assertEquals("the offset is not the alignment", null, state.alignMatrix)
        onNode(hasText("Camera offset saved. Aim the telescope at the sky and take a photo to align.")).assertExists()
        onNode(hasText("Camera offset: calibrated", substring = true)).assertExists()
        onNode(button("Reset offset")).assertExists()

        // 3. a photo of another sky: Apply is enabled and puts the telescope where the offset says
        aim(state, t3.raDeg, t3.decDeg, errorDeg = -25.0)
        toLive()
        onNode(hasText("The + shows where the telescope points.")).assertExists()
        onNode(hasText("Telescope")).assertExists()
        takePhotoAndWait(model)
        onNode(hasText("Apply to alignment")).assertIsEnabled()
        onNode(hasText("The telescope is at the + spot on the photo")).assertExists()
        onNode(hasText("The telescope points at RA", substring = true)).assertExists()
        screenshot("solve-result-camera-forward")
        val solved = (model.outcome as SolveOutcome.Solved).result
        val trueTelescope = Shots.skyAt(t3, saved.first * w, saved.second * h)
        val reported = state.telescopeOnPhoto(solved)!!
        assertTrue("reported ${sep(reported, trueTelescope)} deg from the telescope", sep(reported, trueTelescope) < 0.05)
        assertTrue("and that is not the picture centre", sep(reported, t3.raDeg to t3.decDeg) > 3.0)
        onNode(hasText("Apply to alignment")).tap()
        waitForIdle()
        assertEquals(AlignState.ALIGNED, state.align)
        val trueRay = Pointing.rayFromPos(trueTelescope.first, trueTelescope.second, state.timeMillis, state.lat, state.lon)
        val off = Pointing.angleBetweenDeg(state.telescopeCamera()[2], trueRay)
        assertTrue("the telescope is $off deg from where it really points", off < 0.05)
        // resetting the offset leaves the alignment alone
        val matrix = state.alignMatrix!!.toList()
        state.resetCameraOffset()
        assertNull(state.cameraOffset); assertEquals(matrix, state.alignMatrix!!.toList())
    }

    // ---------------------------------------------------------------- the phone on the tube

    @Test fun tubePlacementExplainsAndSolvesNothing() = phoneTest {
        val state = Fixtures.state() // the default setup: flat on the tube
        val host = FakeHost()
        val solver = CountingSolver()
        val model = modelWith(solver)
        setContent { AppScreen(state, host = host, model = model) }
        val before = state.fingerprint()
        openFromTelescopeTab()
        onNode(hasText("Solving is not possible with the phone flat on the tube")).assertExists()
        onNode(hasText("The phone's camera faces the tube, so it cannot see the sky: a photo would only show the tube.")).assertExists()
        onNode(button("Change phone placement")).assertExists()
        onNode(button("Cancel")).assertExists()
        onNode(button("Next")).assertDoesNotExist()
        onNode(button("Take photo")).assertDoesNotExist()
        screenshot("solve-tube")
        // Back from the arrangement leaves; nothing was asked of the phone
        onNode(button("Change phone placement")).tap()
        assertFalse(model.open)
        onNode(hasText("Where is the phone?")).assertExists() // the Telescope & orientation tab is open
        assertEquals(0, host.permissionRequests); assertEquals(0, host.liveViewsOpened); assertEquals(0, solver.calls)
        assertEquals(before, state.fingerprint())
        // and Cancel closes it
        onNode(button("Close")).tap()
        state.night = false
        model.start()
        waitForIdle()
        onNode(button("Cancel")).tap()
        assertFalse(model.open)
    }

    // ---------------------------------------------------------------- failures

    private fun failureCase(reason: SolveResult.Reason, expected: String) = phoneTest {
        val state = eyepieceState(vega)
        val host = FakeHost().apply { camera += Shots.noise(3) }
        val solver = CountingSolver { SolveResult.Failed(reason, 12) }
        val model = modelWith(solver)
        setContent { AppScreen(state, host = host, model = model) }
        openFromTelescopeTab()
        toLive()
        val before = state.fingerprint()
        takePhotoAndWait(model)
        assertEquals(1, solver.calls)
        onNode(hasText("This photo could not be solved")).assertExists()
        onNode(hasText(expected, substring = true)).assertExists()
        onNode(hasText("Nothing was changed: the alignment and the map are as they were.")).assertExists()
        onNode(hasText("Apply to alignment")).assertDoesNotExist()
        onNode(button("Show on map")).assertDoesNotExist()
        onNode(button("Retry")).assertExists()
        screenshot("solve-failed-$reason")
        assertEquals(before, state.fingerprint())
        // Retry goes back to the live view for another photo
        host.camera += Shots.noise(4)
        onNode(button("Retry")).tap()
        waitForIdle()
        assertEquals(SolveStep.LIVE, model.step)
        assertEquals(before, state.fingerprint())
    }

    @Test fun tooFewStarsAdvice() = failureCase(SolveResult.Reason.TOO_FEW_STARS, "Too few stars were found (12). Try a darker, clearer part of the sky")
    @Test fun tooBrightAdvice() = failureCase(SolveResult.Reason.IMAGE_TOO_BRIGHT, "The picture is too bright: the sky is washed out")
    @Test fun trailedStarsAdvice() = failureCase(SolveResult.Reason.STARS_TRAILED, "The stars are streaks, not dots.")
    @Test fun noMatchAdvice() = failureCase(SolveResult.Reason.NO_MATCH, "12 stars were found, but they do not match the sky at the size expected.")

    @Test fun narrowFieldWithoutAnyCentreIsNotSolved() = phoneTest {
        // x48 gives a 1.4 degree field; no compass and no alignment: the app cannot say where to look.
        val state = eyepieceState(vega, telescopeMm = 1200.0, eyepieceMm = 25.0).apply { hasCompass = false }
        state.telescopeFocalMm = 1200.0; state.eyepieceFocalMm = 25.0
        val host = FakeHost().apply { camera += Shots.noise(5) }
        val solver = CountingSolver()
        val model = modelWith(solver)
        setContent { AppScreen(state, host = host, model = model) }
        openFromTelescopeTab()
        onNode(hasText("Check these two numbers: they tell the app how big the star pattern should look.")).assertExists() // still the factory values
        toLive()
        val before = state.fingerprint()
        takePhotoAndWait(model)
        assertEquals("the solver is not even started", 0, solver.calls)
        onNode(hasText("This photo could not be solved")).assertExists()
        onNode(hasText("The photo covers only about 1.4° of sky, and the app does not know roughly where the telescope points", substring = true)).assertExists()
        onNode(hasText("Apply to alignment")).assertDoesNotExist()
        screenshot("solve-failed-no-hint")
        assertEquals(before, state.fingerprint())
    }

    @Test fun solverThatBreaksChangesNothing() = phoneTest {
        val state = eyepieceState(vega)
        val host = FakeHost().apply { camera += Shots.noise(6) }
        val model = modelWith { _, _, _ -> throw IllegalStateException("boom") }
        setContent { AppScreen(state, host = host, model = model) }
        openFromTelescopeTab(); toLive()
        val before = state.fingerprint()
        takePhotoAndWait(model)
        onNode(hasText("The solver could not run on this picture. Nothing was changed. Try another photo.")).assertExists()
        assertEquals(before, state.fingerprint())
    }

    @Test fun noiseAndBlankPicturesNeverSolveAndChangeNothing() {
        val pictures = listOf<Pair<String, GrayImage>>("noise" to Shots.noise(7), "blank sky" to Shots.blankSky(8))
        for ((name, image) in pictures) phoneTest {
            val state = eyepieceState(vega)
            val host = FakeHost().apply { camera += image }
            val model = PlateSolveModel() // the real solver
            setContent { AppScreen(state, host = host, model = model) }
            openFromTelescopeTab(); toLive()
            val before = state.fingerprint()
            takePhotoAndWait(model)
            assertTrue("$name gave ${model.outcome}", model.outcome is SolveOutcome.Failed)
            onNode(hasText("This photo could not be solved")).assertExists()
            onNode(hasText("Solved")).assertDoesNotExist()
            onNode(hasText("Apply to alignment")).assertDoesNotExist()
            assertEquals("$name changed something", before, state.fingerprint())
        }
    }

    @Test fun aRealStarFieldAtTheWrongScaleIsNotSolvedEither() = phoneTest {
        // The picture is 13 degrees wide but the setup says x2 (32 degrees, allowed 21 to 44): outside every allowed scale.
        val state = eyepieceState(vega, telescopeMm = 50.0, eyepieceMm = 25.0)
        val truth = Shots.truth(vega.ra, vega.dec, widthDeg = 13.0)
        val host = FakeHost().apply { camera += Shots.render(truth, 12, limitMag = 6.5) }
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }
        openFromTelescopeTab(); toLive()
        val before = state.fingerprint()
        takePhotoAndWait(model)
        assertTrue("got ${model.outcome}", model.outcome is SolveOutcome.Failed)
        assertEquals(before, state.fingerprint())
    }

    // ---------------------------------------------------------------- permission, gallery, camera problems

    @Test fun permissionIsAskedOnlyWhenTheLiveViewOpens() = phoneTest {
        val state = eyepieceState(vega)
        val host = FakeHost().apply { permission = CameraPermission.UNKNOWN; answer = CameraPermission.GRANTED }
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }
        openFromTelescopeTab()
        assertEquals(0, host.permissionRequests)
        next(); waitForIdle()
        assertEquals("the tips step does not ask", 0, host.permissionRequests)
        next(); waitForIdle()
        assertEquals(1, host.permissionRequests)
        onNode(button("Take photo")).assertExists() // granted: the live view is up
    }

    @Test fun deniedPermissionOffersTheGalleryAndTheSettings() = phoneTest {
        val state = cameraForwardState()
        val host = FakeHost().apply { permission = CameraPermission.DENIED; hFovDeg = null }
        val t = Shots.truth(300.0, 35.0, 60.0, roll = 10.0)
        host.gallery = GalleryResult.Picked(Shots.render(t, 31))
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }
        aim(state, t.raDeg, t.decDeg)
        openFromTelescopeTab(); toLive()
        onNode(hasText("The camera permission was refused")).assertExists()
        onNode(button("Use a photo from the gallery")).assertExists()
        onNode(button("Open app settings")).assertExists()
        onNode(button("Ask again")).assertExists()
        onNode(button("Take photo")).assertDoesNotExist()
        assertEquals(0, host.permissionRequests)
        screenshot("solve-permission-denied")
        onNode(button("Open app settings")).tap()
        assertEquals(1, host.settingsOpened)
        val before = state.fingerprint()
        onNode(button("Use a photo from the gallery")).tap()
        waitForResult(model); waitForIdle()
        assertEquals(1, host.galleryAsked)
        // a gallery picture is solved (65 degrees assumed) but can never align the telescope
        onNode(hasText("Solved")).assertExists()
        onNode(hasText("The camera's field of view was not known, so 65° was assumed. Use Take photo for a better guess.")).assertExists()
        onNode(hasText("Apply to alignment")).assertIsNotEnabled()
        onNode(hasText("A gallery photo does not record where the telescope pointed when it was taken. Use Take photo to align.")).assertExists()
        screenshot("solve-result-gallery")
        assertEquals(before, state.fingerprint())
        // Show on map still works: it only moves Free look
        onNode(button("Show on map")).tap()
        assertFalse(model.open)
        assertEquals(PointingMode.FREE, state.mode)
        assertEquals(before.filterIndexed { i, _ -> i != 7 && i != 8 && i != 9 }, state.fingerprint().filterIndexed { i, _ -> i != 7 && i != 8 && i != 9 })
    }

    @Test fun blockedPermissionSaysSo() = phoneTest {
        val state = eyepieceState(vega)
        val host = FakeHost().apply { permission = CameraPermission.BLOCKED }
        setContent { AppScreen(state, host = host) }
        openFromTelescopeTab(); toLive()
        onNode(hasText("The camera permission is blocked")).assertExists()
        onNode(hasText("You chose not to be asked again", substring = true)).assertExists()
        onNode(button("Open app settings")).assertExists()
        onNode(button("Use a photo from the gallery")).assertExists()
        onNode(button("Ask again")).assertDoesNotExist()
        assertEquals(0, host.permissionRequests)
        screenshot("solve-permission-blocked")
    }

    @Test fun aPhoneWithoutACameraCanStillUseThePhotoPicker() = phoneTest {
        val state = eyepieceState(vega)
        val host = FakeHost().apply { hasCamera = false }
        setContent { AppScreen(state, host = host) }
        openFromTelescopeTab(); toLive()
        onNode(hasText("No camera on this phone")).assertExists()
        onNode(button("Use a photo from the gallery")).assertExists()
        onNode(button("Take photo")).assertDoesNotExist()
        onNode(button("Open app settings")).assertDoesNotExist()
        assertEquals(0, host.permissionRequests)
        screenshot("solve-no-camera")
    }

    @Test fun cameraProblemsAreShownAndTheGalleryStaysAvailable() = phoneTest {
        val state = eyepieceState(vega)
        val host = FakeHost().apply { problem = CameraProblem.IN_USE }
        setContent { AppScreen(state, host = host) }
        openFromTelescopeTab(); toLive()
        onNode(hasText("Another app is using the camera. Close it, then try again.")).assertExists()
        onNode(button("Try again")).assertExists()
        onNode(button("Use a photo from the gallery")).assertExists()
        host.problem = null
        onNode(button("Try again")).tap(); waitForIdle()
        onNode(button("Take photo")).assertExists()
        // a capture that fails is a problem too, and changes nothing
        host.captureFails = CameraProblem.FAILED
        host.camera += Shots.noise(9)
        val before = state.fingerprint()
        onNode(button("Take photo")).tap(); waitForIdle()
        onNode(hasText("The camera failed. Try again, or use a photo from the gallery.")).assertExists()
        assertEquals(before, state.fingerprint())
    }

    @Test fun anUnreadableGalleryPictureChangesNothing() = phoneTest {
        val state = eyepieceState(vega)
        val host = FakeHost().apply { gallery = GalleryResult.Unreadable }
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }
        openFromTelescopeTab(); toLive()
        val before = state.fingerprint()
        onNode(button("Use a photo from the gallery")).tap(); waitForIdle()
        onNode(hasText("That picture could not be read. Try another one.")).assertExists()
        assertEquals(SolveStep.LIVE, model.step)
        host.gallery = GalleryResult.Cancelled
        onNode(button("Use a photo from the gallery")).tap(); waitForIdle()
        assertEquals(SolveStep.LIVE, model.step)
        assertEquals(before, state.fingerprint())
    }

    @Test fun exposureChoicesAreOnlyWhatTheCameraSupports() = phoneTest {
        val state = eyepieceState(vega)
        val host = FakeHost().apply { exposures = listOf(2.0); camera += Shots.noise(10) }
        val model = modelWith { _, _, _ -> SolveResult.Failed(SolveResult.Reason.TOO_FEW_STARS, 0) }
        setContent { AppScreen(state, host = host, model = model) }
        openFromTelescopeTab(); toLive()
        onNode(hasText("Auto")).assertExists()
        onNode(hasText("2 s")).assertExists()
        onNode(hasText("1 s")).assertDoesNotExist()
        onNode(hasText("4 s")).assertDoesNotExist()
        onNode(hasText("2 s")).tap()
        takePhotoAndWait(model)
        assertEquals(listOf<Double?>(2.0), host.takenWith)
        // a camera with no manual exposure offers no choice at all
        host.exposures = emptyList(); host.camera += Shots.noise(11)
        onNode(button("Retry")).tap(); waitForIdle()
        onNode(hasText("Auto")).assertDoesNotExist()
        onNode(button("Take photo")).tap()
        waitForResult(model)
        assertEquals(listOf<Double?>(2.0, null), host.takenWith)
    }

    // ---------------------------------------------------------------- the camera view is not an input

    @Test fun draggingTheCameraViewChangesNothing() = phoneTest {
        val state = eyepieceState(vega)
        state.mode = PointingMode.FREE
        val host = FakeHost()
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = host, model = model) }
        openFromTelescopeTab(); toLive()
        assertEquals(SolveStep.LIVE, model.step)
        val before = state.fingerprint()
        val device = state.device.toList()
        val fov = state.fovDeg
        onNode(hasTestTag("camera-frame")).performTouchInput { swipe(center, center + Offset(150f, 90f), 300) }
        waitForIdle()
        // and over the rest of the flow's layer, which must not pass touches to the sky map underneath either
        onRoot().performTouchInput { swipe(Offset(100f, 150f), Offset(500f, 900f), 300) }
        waitForIdle()
        assertEquals(before, state.fingerprint())
        assertEquals(device, state.device.toList())
        assertEquals(fov, state.fovDeg, 0.0)
        assertEquals(SolveStep.LIVE, model.step)
        assertNull(model.shot)
        // Even while aligning, where a drag on the sky map does move the calibration map, the camera view ignores it.
        state.mode = PointingMode.COMPASS
        state.startAlign()
        val adjust = state.adjustAzDeg to state.adjustAltDeg
        onNode(hasTestTag("camera-frame")).performTouchInput { swipe(center, center + Offset(-120f, 60f), 300) }
        waitForIdle()
        assertEquals(adjust, state.adjustAzDeg to state.adjustAltDeg)
    }

    // ---------------------------------------------------------------- the + on the live view

    private fun ComposeUiTest.markerFraction(): Pair<Double, Double> {
        val frame = onNode(hasTestTag("camera-frame")).fetchSemanticsNode().boundsInRoot
        val plus = onNode(hasContentDescription("Telescope marker")).fetchSemanticsNode().boundsInRoot
        return ((plus.center.x - frame.left) / frame.width).toDouble() to ((plus.center.y - frame.top) / frame.height).toDouble()
    }

    @Test fun thePlusIsAtTheCentreForTheEyepieceAndAtTheOffsetBesideTheTube() {
        phoneTest {
            val state = eyepieceState(vega)
            setContent { AppScreen(state, host = FakeHost()) }
            openFromTelescopeTab(); toLive()
            val (x, y) = markerFraction()
            assertEquals(0.5, x, 0.01); assertEquals(0.5, y, 0.01)
            onNode(hasText("Telescope")).assertDoesNotExist() // the label is only for the calibrated spot beside the tube
            screenshot("solve-live-eyepiece-plus")
        }
        for (night in listOf(false, true)) phoneTest {
            val state = cameraForwardState().apply { cameraOffset = 0.7 to 0.3; this.night = night }
            setContent { AppScreen(state, host = FakeHost()) }
            openFromTelescopeTab(); toLive()
            val (x, y) = markerFraction()
            assertEquals(0.7, x, 0.01); assertEquals(0.3, y, 0.01)
            onNode(hasText("Telescope")).assertExists()
            screenshot("solve-live-camera-forward-plus" + if (night) "-night" else "")
        }
        // the corner of the picture: the label stays on the picture
        phoneTest {
            val state = cameraForwardState().apply { cameraOffset = 0.97 to 0.97 }
            setContent { AppScreen(state, host = FakeHost()) }
            openFromTelescopeTab(); toLive()
            val frame = onNode(hasTestTag("camera-frame")).fetchSemanticsNode().boundsInRoot
            val label = onNode(hasText("Telescope")).fetchSemanticsNode().boundsInRoot
            assertTrue(label.right <= frame.right + 1 && label.bottom <= frame.bottom + 1 && label.left >= frame.left - 1)
        }
    }

    // ---------------------------------------------------------------- entrances, cancel, back

    @Test fun theFlowOpensFromMoreAndTheTelescopeTabAndBackStepsBack() = phoneTest {
        val state = eyepieceState(vega)
        Fixtures.align(state, vega)
        state.target = catalog.find("M57")
        val model = PlateSolveModel()
        setContent { AppScreen(state, host = FakeHost(), model = model) }
        onNode(button("More")).tap()
        onNode(button("Solve with camera")).tap()
        assertTrue(model.open)
        waitForIdle()
        assertTrue(BackButton.press())
        assertFalse("Back from the first step closes the flow", model.open)
        // The camera slot needs a host
        model.start(); waitForIdle()
        next(); next(); waitForIdle()
        assertEquals(SolveStep.LIVE, model.step)
        BackButton.press(); waitForIdle()
        assertEquals(SolveStep.TIPS, model.step)
        BackButton.press(); waitForIdle()
        assertEquals(SolveStep.ARRANGEMENT, model.step)
        BackButton.press(); waitForIdle()
        assertFalse(model.open)
    }

    @Test fun cancelWhileSolvingStopsTheSearchAndChangesNothing() = phoneTest {
        val state = eyepieceState(vega)
        val host = FakeHost().apply { camera += Shots.noise(13) }
        val stopped = AtomicBoolean(false)
        val started = AtomicBoolean(false)
        val model = modelWith { _, _, stars ->
            started.set(true)
            try {
                while (true) { stars.cone(0.0, 0.0, 1.0, 1.0); Thread.sleep(10) }
                @Suppress("UNREACHABLE_CODE") SolveResult.Failed(SolveResult.Reason.NO_MATCH, 0)
            } finally { stopped.set(true) }
        }
        setContent { AppScreen(state, host = host, model = model) }
        openFromTelescopeTab(); toLive()
        val before = state.fingerprint()
        onNode(button("Take photo")).tap()
        waitUntil(30_000) { model.step == SolveStep.SOLVING && started.get() }
        onNode(hasText("Solving…")).assertExists()
        screenshot("solve-solving")
        onNode(button("Cancel")).tap()
        waitUntil(30_000) { stopped.get() }
        waitForIdle()
        assertEquals(SolveStep.LIVE, model.step)
        assertNull(model.outcome)
        assertEquals(before, state.fingerprint())
    }

    // ---------------------------------------------------------------- SkyState: applying, blockers, persistence

    private fun solvedFrom(t: Truth) = SolveResult.Solved(t.raDeg, t.decDeg, t.rollDeg, t.scaleDegPerPx, t.mirrored, 20, 1.0, emptyList(), t.width, t.height)

    private fun shotAt(s: SkyState, fromCamera: Boolean = true, moved: Double = 0.0) =
        PhotoShot(GrayImage(2, 2, FloatArray(4)), fromCamera, 60.0, s.device.copyOf(), s.timeMillis, moved)

    @Test fun applyIsRefusedForEveryReasonAndChangesNothing() {
        val truth = Shots.truth(vega.ra, vega.dec, 13.0)
        val solved = solvedFrom(truth)
        // gallery
        run {
            val s = eyepieceState(vega); val before = s.fingerprint()
            assertNotNull(s.photoApplyBlocker(shotAt(s, fromCamera = false)))
            assertNotNull(s.applyPhotoAlignment(solved, shotAt(s, fromCamera = false)))
            assertEquals(before, s.fingerprint())
        }
        // the phone moved while the photo was taken
        run {
            val s = eyepieceState(vega); val before = s.fingerprint()
            assertNull(s.photoApplyBlocker(shotAt(s, moved = 0.29)))
            assertEquals("The phone moved while the photo was taken, so it cannot be used. Keep it still and take another photo.",
                s.applyPhotoAlignment(solved, shotAt(s, moved = 0.31))!!.text)
            assertEquals(before, s.fingerprint())
        }
        // the eyepiece angle is not known: the phone axis is a guess
        run {
            val s = eyepieceState(vega); s.setup = s.setup.copy(eyepieceAngle = EyepieceAngle.UNSURE); val before = s.fingerprint()
            assertNotNull(s.applyPhotoAlignment(solved, shotAt(s)))
            assertEquals(before, s.fingerprint())
        }
        // the phone on the tube
        run {
            val s = eyepieceState(vega); s.setup = s.setup.copy(placement = PhonePlacement.TUBE); val before = s.fingerprint()
            assertNotNull(s.applyPhotoAlignment(solved, shotAt(s)))
            assertEquals(before, s.fingerprint())
        }
        // camera beside the tube, no offset yet
        run {
            val s = cameraForwardState(); val before = s.fingerprint()
            assertEquals("The camera and telescope don't point exactly the same way. Calibrate the camera offset first.", s.applyPhotoAlignment(solved, shotAt(s))!!.text)
            assertEquals(before, s.fingerprint())
        }
        // a position below the horizon for this place and time (the time is 12 hours off)
        run {
            val s = eyepieceState(vega)
            val late = PhotoShot(GrayImage(2, 2, FloatArray(4)), true, 60.0, s.device.copyOf(), s.timeMillis + 12 * 3_600_000L)
            val before = s.fingerprint()
            assertTrue(s.applyPhotoAlignment(solved, late)!!.text.startsWith("The photo points below the horizon"))
            assertEquals(before, s.fingerprint())
        }
    }

    @Test fun applyUsesTheDeviceAndTimeOfTheCaptureNotOfNow() {
        val s = eyepieceState(vega, compassErrorDeg = 15.0)
        val truth = Shots.truth(vega.ra, vega.dec, 13.0)
        val shot = shotAt(s)
        // The phone and the clock move on before Apply is pressed.
        s.device = Pointing.matMul(Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), Math.toRadians(70.0)), s.device)
        s.shiftTime(1_800_000L)
        assertNull(s.applyPhotoAlignment(solvedFrom(truth), shot))
        // With the capture-time phone rotation and time, the calibrated telescope is exactly on the solved centre...
        val ray = Pointing.rayFromPos(truth.raDeg, truth.decDeg, shot.timeMillis, s.lat, s.lon)
        val fwd = Pointing.cameraRays(shot.device, s.alignMatrix, s.setup.axis().vector)[2]
        assertTrue(Pointing.angleBetweenDeg(fwd, ray) < 0.01)
        // ... and the alignment is dated to the capture.
        assertEquals(shot.timeMillis, s.alignedAtMillis)
        assertEquals(1, s.alignSamples.size)
    }

    @Test fun cameraOffsetIsSeparateSavedAndClearedWithTheMounting() {
        val s = cameraForwardState()
        assertNull(s.cameraOffset)
        assertEquals("", s.settings()["cameraOffset"])
        val t = Shots.truth(285.0, 30.0, 60.0)
        val solved = solvedFrom(t)
        val target = SkyObject("Near the centre", t.raDeg, t.decDeg, 1.0, "S")
        assertNull(s.calibrateCameraOffset(solved, shotAt(s), target))
        val off = s.cameraOffset!!
        assertEquals(0.5, off.first, 1e-6); assertEquals(0.5, off.second, 1e-6)
        val alignBefore = s.alignMatrix
        assertEquals("the alignment is a different thing", alignBefore, s.alignMatrix)
        assertEquals(setOf("0.50000,0.50000"), setOf(s.settings()["cameraOffset"]))
        // it survives a restart
        val again = cameraForwardState().also { it.applySettings(s.settings()) }
        assertEquals(off.first, again.cameraOffset!!.first, 1e-5)
        // damaged values are ignored
        for (bad in listOf("", "0.5", "1.5,0.5", "a,b", "0.5,0.5,0.5", "-0.1,0.2")) {
            val fresh = cameraForwardState().also { it.applySettings(mapOf("cameraOffset" to bad)) }
            assertNull("\"$bad\"", fresh.cameraOffset)
        }
        // a star that is not on the photo teaches nothing
        val far = SkyObject("Far", (t.raDeg + 90) % 360, -t.decDeg, 1.0, "S")
        val s2 = cameraForwardState().also { it.cameraOffset = 0.3 to 0.4 }
        assertNotNull(s2.calibrateCameraOffset(solved, shotAt(s2), far))
        assertEquals(0.3 to 0.4, s2.cameraOffset)
        // a gallery picture cannot calibrate it
        assertNotNull(s2.calibrateCameraOffset(solved, shotAt(s2, fromCamera = false), target))
        assertEquals(0.3 to 0.4, s2.cameraOffset)
        // only for the camera-forward placement
        val eye = eyepieceState(vega)
        assertNotNull(eye.calibrateCameraOffset(solved, shotAt(eye), target)); assertNull(eye.cameraOffset)
        // moving the phone somewhere else clears the offset; the view rotation does not
        s2.updateSetup(s2.setup.copy(viewRotationDeg = 90, viewMirrored = true))
        assertEquals(0.3 to 0.4, s2.cameraOffset)
        s2.updateSetup(s2.setup.copy(placement = PhonePlacement.EYEPIECE))
        assertNull(s2.cameraOffset)
        // reset
        s.resetCameraOffset(); assertNull(s.cameraOffset)
    }

    @Test fun showOnMapMovesFreeLookOnlyAndLeavesTheAlignmentAlone() {
        val s = eyepieceState(vega)
        Fixtures.align(s, catalog.find("Altair")!!)
        val matrix = s.alignMatrix!!.toList()
        s.showOnMap(vega.ra, vega.dec)
        assertEquals(PointingMode.FREE, s.mode)
        val ray = s.ray(vega)
        val alt = Math.toDegrees(Math.asin(ray[2]))
        assertTrue(abs(s.freeAltDeg - alt) < 1e-6)
        val fwd = s.camera()[2]
        assertTrue("the map now looks at the position", Pointing.angleBetweenDeg(fwd, ray) < 0.01)
        assertEquals(matrix, s.alignMatrix!!.toList())
        assertEquals(AlignState.ALIGNED, s.align)
    }
}
