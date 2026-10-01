package org.astrofixxer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.astrofixxer.astro.ApparentPosition
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.JulianDate
import org.astrofixxer.astro.UserLists
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.astro.AlignSample
import org.astrofixxer.astro.Mounting
import org.astrofixxer.astro.PhotoAlignment
import org.astrofixxer.astro.SolveResult
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/** NOT_ALIGNED -> PICK_STAR (tap a star) -> CENTER_STAR (centre it in the eyepiece, drag the map under the +) -> ALIGNED. */
enum class AlignState { NOT_ALIGNED, PICK_STAR, CENTER_STAR, ALIGNED }
enum class Landscape(val label: String) { NONE("None"), HILLS("Hills"), TREES("Trees"), CITY("City"), OBSERVATORY("Dome") }
/** Compass: the phone's sensors point the map. Free look: dragging points it, like a planetarium. */
enum class PointingMode { COMPASS, FREE }

/** See [SkyState.moveHint]. */
class MoveHint(val equatorial: Boolean, val vertical: Double, val horizontal: Double, val separationDeg: Double, val arrowDeg: Double)

/** A one-line message for the alignment panels: an English template for [t] and its optional argument. */
class AlignNote(val text: String, val arg: String? = null) {
    fun render(): String = if (arg == null) t(text) else t(text).format(arg)
}

/**
 * What a confirmed alignment found. [check] is a "Check with another star" result; [refined] means two-star alignment was
 * applied. [fromPhoto] is an alignment from a solved camera photo: there is no [star] then, and [correctionDeg] is how far
 * off the app's pointing was when the photo was taken.
 */
class AlignResult(
    val star: SkyObject?, val correctionDeg: Double, val large: Boolean, val check: Boolean = false, val refined: Boolean = false,
    val note: AlignNote? = null, val fromPhoto: Boolean = false,
)

/** Everything the sky screen shows, independent of Android: time, place, phone orientation, alignment, target. */
class SkyState(nowMillis: Long, lat: Double, lon: Double) {
    var timeMillis by mutableLongStateOf(nowMillis)
    /** False while showing another time (time travel); the host stops advancing the clock. */
    var live by mutableStateOf(true)
    var lat by mutableDoubleStateOf(lat)
    var lon by mutableDoubleStateOf(lon)
    /** True once the user typed a location; GPS fixes no longer replace it. */
    var manualLocation by mutableStateOf(false)
    var fovDeg by mutableDoubleStateOf(60.0)

    /** Device-to-world (east, north, up) rotation from the phone's sensors, row-major 3x3. */
    var device by mutableStateOf(Pointing.IDENTITY)
    var mode by mutableStateOf(PointingMode.COMPASS)
    /** False on phones without a compass: they start with an arbitrary azimuth that dragging during alignment fixes. */
    var hasCompass by mutableStateOf(true)

    /** How the telescope and phone are set up; see [TelescopeSetup]. Use [updateSetup] to change mounting fields. */
    var setup by mutableStateOf(TelescopeSetup())
    /** False until the user finished the first-run wizard or chose "Set up later". */
    var setupDone by mutableStateOf(false)
    /** Draw the sky map rotated and mirrored like the eyepiece view (the + and all guidance text stay as they are). */
    var matchEyepieceView by mutableStateOf(false)
    /** True after an alignment was cleared because the mounting changed; the screen explains it and offers to align again. */
    var mountingChangedNotice by mutableStateOf(false)

    var align by mutableStateOf(AlignState.NOT_ALIGNED)
    /** The calibration: rotates the phone's uncorrected pointing onto the true sky. Catalogue coordinates never change. */
    var alignMatrix by mutableStateOf<DoubleArray?>(null)
    var alignedAtMillis by mutableStateOf<Long?>(null)
    var alignStar by mutableStateOf<SkyObject?>(null)
    /** Name of the alignment star when only the name survived a restart (the host resolves it once the catalogue loads). */
    var alignStarName by mutableStateOf<String?>(null)
    /** CENTER_STAR: the star the user is centring in the eyepiece. */
    var centerStar by mutableStateOf<SkyObject?>(null)
    /** Pending drag adjustment of the map during PICK_STAR/CENTER_STAR (degrees). It never changes the calibration by itself. */
    var adjustAzDeg by mutableDoubleStateOf(0.0)
    var adjustAltDeg by mutableDoubleStateOf(0.0)
    /** True while the alignment in progress is "Check with another star" rather than a first alignment. */
    var checkingSecondStar by mutableStateOf(false)
    /** The guidance panel's "More" is open; the target card at the top hides meanwhile so the two never overlap. */
    var guidanceExpanded by mutableStateOf(false)
    var alignNote by mutableStateOf<AlignNote?>(null)
    var alignResult by mutableStateOf<AlignResult?>(null)
    /** Confirmed alignment stars with the sensor rotation at that moment, for the phone-axis check. Not saved between launches. */
    var alignSamples by mutableStateOf<List<AlignSample>>(emptyList())
    /**
     * Camera beside the tube (camera forward): where the telescope is on the camera's picture, as fractions of its width and
     * height (y down); null until calibrated with a photo (see [calibrateCameraOffset]). It is separate from the alignment
     * calibration and from the eyepiece view rotation and mirror, and only means something for the placement it was made for.
     */
    var cameraOffset by mutableStateOf<Pair<Double, Double>?>(null)
    /** The star the current calibration was made on; the other star of a two-star refinement. Lost on restart. */
    private var calibrationSample: AlignSample? = null
    var target by mutableStateOf<SkyObject?>(null)

    var night by mutableStateOf(false)
    var showConstellations by mutableStateOf(true)
    var showDeepSky by mutableStateOf(true)
    var showGrid by mutableStateOf(false)
    var showEquatorialGrid by mutableStateOf(false)
    var showMeridian by mutableStateOf(false)
    var showEcliptic by mutableStateOf(false)
    var showBoundaries by mutableStateOf(false)
    var showStarColours by mutableStateOf(true)
    var showCardinals by mutableStateOf(true)
    /** Deep-sky types switched off in Sky & Viewing ("Ga", "Oc", "Gc", "Ne"). */
    var hiddenDsoTypes by mutableStateOf<Set<String>>(emptySet())
    /** Free look: where the view points (degrees), driven by dragging instead of the sensors. */
    var freeAzDeg by mutableDoubleStateOf(180.0)
    var freeAltDeg by mutableDoubleStateOf(45.0)

    /** Telescope and eyepiece, which set the eyepiece circle and when the target counts as "on target". */
    var telescopeFocalMm by mutableDoubleStateOf(1200.0)
    var eyepieceFocalMm by mutableDoubleStateOf(25.0)
    var eyepieceAfovDeg by mutableDoubleStateOf(52.0)
    var haptics by mutableStateOf(true)

    /** True field of view of the eyepiece: apparent field × eyepiece focal length / telescope focal length. */
    val eyepieceFovDeg: Double get() = if (telescopeFocalMm > 0) eyepieceAfovDeg * eyepieceFocalMm / telescopeFocalMm else 1.0
    var showAtmosphere by mutableStateOf(true)
    var showMilkyWay by mutableStateOf(true)
    var showArt by mutableStateOf(false)
    var landscape by mutableStateOf(Landscape.HILLS)
    /** Bortle dark-sky scale 1 (pristine) .. 9 (inner city); dims faint stars like Stellarium's light pollution. */
    var bortle by mutableStateOf(4)
    var skyCulture by mutableStateOf("modern")

    /** Raw text the user typed; the host persists these. */
    var userObjectsText by mutableStateOf("")
    var watchListText by mutableStateOf("")
    var userObjects by mutableStateOf<List<SkyObject>>(emptyList())
    var watchLists by mutableStateOf<List<UserLists.WatchList>>(emptyList())
    var listIndex by mutableStateOf(-1)
    var itemIndex by mutableStateOf(0)
    var showOnboarding by mutableStateOf(false)
    /** AstroGuide: last thing heard and the spoken answer (null when the bubble is closed). */
    var guideHeard by mutableStateOf("")
    var guideAnswer by mutableStateOf<String?>(null)
    var guideListening by mutableStateOf(false)

    /** Re-parses the user's objects and watch lists; returns the errors to show. */
    fun applyUserText(catalog: Catalog?): List<String> {
        val parsed = UserLists.parseObjects(userObjectsText) { catalog?.find(it) != null }
        userObjects = parsed.objects
        watchLists = UserLists.parseWatchLists(watchListText)
        if (listIndex >= watchLists.size) listIndex = -1
        return parsed.errors
    }

    fun resolve(name: String, catalog: Catalog?): SkyObject? {
        val key = Catalog.normalizeName(name)
        return userObjects.firstOrNull { Catalog.normalizeName(it.name) == key } ?: catalog?.find(name) ?: catalog?.search(name)?.firstOrNull()
    }

    /** Selects watch list [index] (-1 = none) and targets its first item. */
    fun selectList(index: Int, catalog: Catalog?) {
        listIndex = if (index in watchLists.indices) index else -1
        itemIndex = 0
        if (listIndex >= 0) target = watchLists[listIndex].items.firstOrNull()?.let { resolve(it.name, catalog) }
    }

    fun stepWatch(delta: Int, catalog: Catalog?) {
        val items = watchLists.getOrNull(listIndex)?.items ?: return
        if (items.isEmpty()) return
        itemIndex = (itemIndex + delta).mod(items.size)
        target = resolve(items[itemIndex].name, catalog)
    }

    /** True while the user is picking or centring an alignment star; only then does dragging the map move it. */
    val aligning: Boolean get() = align == AlignState.PICK_STAR || align == AlignState.CENTER_STAR

    private val freeView: Boolean get() = mode == PointingMode.FREE && !aligning

    private fun axis(): DoubleArray = setup.axis().vector

    /** The telescope's own pointing: sensors and calibration, no dragging, no free look. Guidance comes only from this. */
    fun telescopeCamera(): Array<DoubleArray> = Pointing.cameraRays(device, alignMatrix, axis())

    /** The calibration with the pending drag adjustment on top: an azimuth turn about the vertical and an altitude turn about the camera's left. */
    private fun adjustedAlign(): DoubleArray? {
        if (adjustAzDeg == 0.0 && adjustAltDeg == 0.0) return alignMatrix
        val base = telescopeCamera()
        val alt = Pointing.axisAngleMatrix(base[1], -Math.toRadians(adjustAltDeg))
        val az = Pointing.axisAngleMatrix(doubleArrayOf(0.0, 0.0, 1.0), -Math.toRadians(adjustAzDeg))
        return Pointing.matMul(az, Pointing.matMul(alt, alignMatrix ?: Pointing.IDENTITY))
    }

    /** What the screen shows: the telescope's pointing (with the pending adjustment while aligning), or the free-look view. */
    fun camera(): Array<DoubleArray> =
        if (freeView) Pointing.cameraRays(Pointing.rotationMatrix(-freeAzDeg, freeAltDeg, 0.0))
        else Pointing.cameraRays(device, adjustedAlign(), axis())

    fun ray(o: SkyObject) = Pointing.rayFromPos(o.ra, o.dec, timeMillis, lat, lon)

    private fun altitudeDeg(o: SkyObject) = Math.toDegrees(asin(ray(o)[2].coerceIn(-1.0, 1.0)))

    // ---------------------------------------------------------------- alignment

    /** Why [o] cannot be an alignment star (an English template for [t]), or null when it can: stars and planets above the horizon. */
    fun alignBlocker(o: SkyObject): AlignNote? = when {
        o.name == "Sun" -> AlignNote("Never point a telescope at the Sun. Pick a star or a planet.")
        o.name == "Moon" -> AlignNote("The Moon is too big to centre precisely. Pick a star or a planet.")
        o.type != "S" && o.type != "P" -> AlignNote("%s is not a star or planet. Pick a bright star or a planet.", o.name)
        altitudeDeg(o) <= 0.0 -> AlignNote("%s is below the horizon. Pick a star that is up.", o.name)
        else -> null
    }

    fun canAlignOn(o: SkyObject) = alignBlocker(o) == null

    /** True when [o] is up but so low that centring it is harder (below [LOW_STAR_DEG]). */
    fun isLowForAlignment(o: SkyObject) = altitudeDeg(o) < LOW_STAR_DEG

    /** Waits for the user to tap the star they will centre in the telescope. The old alignment stays until a new one is confirmed. */
    fun startAlign() {
        if (mode == PointingMode.FREE) mode = PointingMode.COMPASS // aligning needs the map to follow the phone
        resetAdjustment()
        centerStar = null
        alignResult = null
        alignNote = null
        align = AlignState.PICK_STAR
    }

    /** "Check with another star": the same flow for a second star; on confirm the alignment is checked and refined. */
    fun startCheckWithAnotherStar() {
        startAlign()
        checkingSecondStar = true
    }

    /** A tap on [o] while picking. Returns true when it was accepted (now centring it); otherwise a reason is shown and picking goes on. */
    fun pickStar(o: SkyObject): Boolean {
        val why = alignBlocker(o) ?: if (checkingSecondStar && o.name == alignStar?.name) AlignNote("%s is the star you aligned on. Pick a different one.", o.name) else null
        if (why != null) { alignNote = why; return false }
        beginCentering(o)
        return true
    }

    /**
     * Starts centring [o] (from picking, or straight from "Align using this star"). Nothing is calibrated yet: the user
     * centres the star in the eyepiece, drags the map to put it under the +, and confirms.
     */
    fun beginCentering(o: SkyObject): Boolean {
        val why = alignBlocker(o)
        if (why != null) { alignNote = why; return false }
        if (mode == PointingMode.FREE) mode = PointingMode.COMPASS
        val keepDrag = align == AlignState.PICK_STAR // a drag made while looking for the star carries over
        if (!keepDrag) resetAdjustment()
        centerStar = o
        alignResult = null
        alignNote = if (isLowForAlignment(o)) AlignNote("Low stars are harder to centre") else null
        align = AlignState.CENTER_STAR
        return true
    }

    /** Drops the pending drag adjustment. The calibration is not touched. */
    fun resetAdjustment() {
        adjustAzDeg = 0.0
        adjustAltDeg = 0.0
    }

    /** Leaves picking or centring without changing the calibration. */
    fun cancelAlign() {
        resetAdjustment()
        centerStar = null
        alignNote = null
        checkingSecondStar = false
        align = if (alignMatrix != null) AlignState.ALIGNED else AlignState.NOT_ALIGNED
    }

    /** Forgets the calibration and everything that depends on it. */
    fun clearAlignment() {
        alignMatrix = null; alignStar = null; alignStarName = null; alignedAtMillis = null
        alignSamples = emptyList(); calibrationSample = null
        centerStar = null; alignNote = null; alignResult = null; checkingSecondStar = false
        resetAdjustment()
        align = AlignState.NOT_ALIGNED
    }

    /** Result card "Retry": centre the same star again. */
    fun retryAlignment() {
        val r = alignResult ?: return
        val star = r.star ?: return // an alignment from a photo is repeated by solving another photo
        val again = r.check
        startAlign()
        checkingSecondStar = again
        beginCentering(star)
    }

    /** Result card "Done". */
    fun dismissAlignResult() { alignResult = null }

    /**
     * Drag-to-align: while picking or centring, a drag of [dxPx],[dyPx] on a [widthPx]×[heightPx] sky moves the map with
     * the finger (a pending azimuth/altitude adjustment on top of the calibration, in both directions). Anywhere else
     * this does nothing, so browsing can never change the calibration.
     */
    fun dragAdjust(dxPx: Float, dyPx: Float, widthPx: Float, heightPx: Float) {
        if (!aligning || widthPx <= 0f || heightPx <= 0f) return
        val fovH = if (widthPx < heightPx) fovDeg * widthPx / heightPx else fovDeg
        val fovV = if (widthPx < heightPx) fovDeg else fovDeg * heightPx / widthPx
        val baseAlt = Math.toDegrees(asin(telescopeCamera()[2][2].coerceIn(-1.0, 1.0)))
        val nowAlt = baseAlt + adjustAltDeg
        val cosAlt = cos(Math.toRadians(nowAlt)).coerceAtLeast(0.2) // near the zenith azimuth barely moves anything
        adjustAzDeg = ((adjustAzDeg - dxPx / widthPx * fovH / cosAlt + 180) % 360 + 360) % 360 - 180
        adjustAltDeg = (adjustAltDeg + dyPx / heightPx * fovV).coerceIn(-89.0 - baseAlt, 89.0 - baseAlt)
    }

    /**
     * Confirm: the telescope is on [centerStar]. Computes the calibration exactly from the uncorrected camera and the
     * star, stores it and goes to ALIGNED with a result card. Fails (and changes nothing) if the star has dropped below
     * the horizon or the result does not put the star on the telescope axis. With [checkingSecondStar] it instead reports how
     * far off the calibrated app was, and refines the calibration with both stars.
     */
    fun confirmAlignment(): Boolean {
        val star = centerStar ?: return false
        if (ray(star)[2] <= 0.0) {
            alignNote = AlignNote("%s has dropped below the horizon, so nothing was changed. Cancel and pick another star.", star.name)
            return false
        }
        val starRay = ray(star)
        val uncorrected = Pointing.cameraRays(device, null, axis())
        val before = telescopeCamera()[2] // where the calibrated app thought the telescope pointed
        val offBy = Pointing.angleBetweenDeg(before, starRay)
        val altError = Math.toDegrees(kotlin.math.abs(asin(before[2].coerceIn(-1.0, 1.0)) - asin(starRay[2].coerceIn(-1.0, 1.0))))
        val sample = AlignSample(device.copyOf(), starRay)
        val check = checkingSecondStar && alignMatrix != null

        var matrix: DoubleArray? = null
        var refined = false
        var note: AlignNote? = null
        if (check) {
            val first = calibrationSample
            if (first == null) note = AlignNote("Not refined: align on a star again first to refine with two stars.")
            else if (Pointing.angleBetweenDeg(first.starRay, starRay) < Mounting.MIN_STAR_SEPARATION_DEG)
                note = AlignNote("Not refined: the two stars are less than 10° apart. Use a star farther away to refine.")
            else {
                // The newest star is matched exactly: it is the freshest, and closest to where the user is heading.
                matrix = Mounting.triad(Pointing.mvec(sample.device, axis()), Pointing.mvec(first.device, axis()), starRay, first.starRay)
                refined = matrix != null
                if (!refined) note = AlignNote("Not refined: the two directions could not be combined.")
            }
        } else {
            matrix = Pointing.alignMatrix(uncorrected, starRay)
        }
        if (matrix != null) {
            val landed = Pointing.cameraRays(device, matrix, axis())[2]
            if (!matrix.all { it.isFinite() } || Pointing.angleBetweenDeg(landed, starRay) > 0.01) {
                alignNote = AlignNote("The alignment could not be computed. Nothing was changed. Try again.")
                return false
            }
            alignMatrix = matrix
            calibrationSample = sample
            alignStar = star
            alignStarName = star.name
            alignedAtMillis = timeMillis
        }
        // The same star confirmed again (Retry) replaces its earlier sample.
        alignSamples = (alignSamples.filter { Pointing.angleBetweenDeg(it.starRay, starRay) > 1.0 } + sample).takeLast(MAX_SAMPLES)
        alignResult = AlignResult(star, offBy, large = !check && offBy > LARGE_CORRECTION_DEG && (hasCompass || altError > LARGE_CORRECTION_DEG),
            check = check, refined = refined, note = note)
        resetAdjustment()
        centerStar = null
        alignNote = null
        checkingSecondStar = false
        align = AlignState.ALIGNED
        return true
    }

    // ---------------------------------------------------------------- camera plate solving

    /** Where the telescope points, J2000 (ra, dec) degrees, for a phone with rotation [device] at [timeMillis]: the sensors and the calibration. */
    fun pointingRaDec(device: DoubleArray, timeMillis: Long): Pair<Double, Double> =
        Pointing.rayToRaDec(Pointing.cameraRays(device, alignMatrix, axis())[2], timeMillis, lat, lon)

    /**
     * Why "Apply to alignment" cannot be used for [shot] (an English template for [t]), or null when it can. A photo only
     * aligns the telescope when it came from the live camera (a gallery picture has no record of where the telescope was
     * pointing), the phone stayed still while it was taken, the app knows which way the phone points along the telescope,
     * and, with the camera beside the tube, the camera offset is calibrated.
     */
    fun photoApplyBlocker(shot: PhotoShot): AlignNote? = when {
        setup.placement == PhonePlacement.TUBE -> AlignNote("The phone's camera faces the tube here, so a photo cannot show where the telescope points.")
        !shot.fromCamera -> AlignNote("A gallery photo does not record where the telescope pointed when it was taken. Use Take photo to align.")
        shot.movedDeg > MAX_PHOTO_MOVE_DEG -> AlignNote("The phone moved while the photo was taken, so it cannot be used. Keep it still and take another photo.")
        setup.placement == PhonePlacement.EYEPIECE && setup.axis().needsCheck ->
            AlignNote("The app does not know which way the phone points along the telescope. Say whether the eyepiece goes straight in or at a right angle first.")
        setup.placement == PhonePlacement.CAMERA_FORWARD && cameraOffset == null ->
            AlignNote("The camera and telescope don't point exactly the same way. Calibrate the camera offset first.")
        else -> null
    }

    /**
     * Where the telescope pointed when [shot] was taken, as solved: the picture centre at the eyepiece, or the calibrated
     * pixel with the camera beside the tube. Null when it cannot be told (on the tube, or before the offset is known).
     */
    fun telescopeOnPhoto(solved: SolveResult.Solved): Pair<Double, Double>? = PhotoAlignment.telescopeRaDec(solved, setup.placement, cameraOffset)

    /**
     * Aligns the telescope from a solved photo: the calibration is worked out exactly as for a star that is centred, but for
     * the solved position, with the phone rotation and time stored in [shot] at the moment it was taken (never the current
     * ones), and it must put the telescope axis within 0.01° of that position. Returns null when it was applied, otherwise
     * why not (see [photoApplyBlocker], and a position below the horizon); nothing is changed then. A result card
     * "Aligned from a photo" follows, and the map goes back to following the phone.
     */
    fun applyPhotoAlignment(solved: SolveResult.Solved, shot: PhotoShot): AlignNote? {
        photoApplyBlocker(shot)?.let { return it }
        val at = telescopeOnPhoto(solved) ?: return AlignNote("The alignment could not be computed. Nothing was changed. Try again.")
        val ray = Pointing.rayFromPos(at.first, at.second, shot.timeMillis, lat, lon)
        if (ray[2] <= 0.0) return AlignNote("The photo points below the horizon for your location and time, so nothing was changed. Check the location and the time.")
        val cal = PhotoAlignment.calibrate(shot.device, axis(), at.first, at.second, shot.timeMillis, lat, lon)
            ?: return AlignNote("The alignment could not be computed. Nothing was changed. Try again.")
        val offBy = Pointing.angleBetweenDeg(Pointing.cameraRays(shot.device, alignMatrix, axis())[2], ray) // the app's pointing before, at that moment
        alignMatrix = cal.matrix
        calibrationSample = cal.sample
        alignStar = null; alignStarName = null
        alignedAtMillis = shot.timeMillis
        alignSamples = (alignSamples.filter { Pointing.angleBetweenDeg(it.starRay, cal.ray) > 1.0 } + cal.sample).takeLast(MAX_SAMPLES)
        alignResult = AlignResult(null, offBy, large = false, fromPhoto = true)
        resetAdjustment()
        centerStar = null
        alignNote = null
        checkingSecondStar = false
        mode = PointingMode.COMPASS
        align = AlignState.ALIGNED
        return null
    }

    /**
     * Learns the camera offset (camera beside the tube): [star] was centred in the eyepiece while [shot] was taken with the
     * live camera, and [solved] says where it is on the picture. Returns null when saved, otherwise why not; nothing is
     * changed then. Only for the camera-forward placement, and only from the live camera (a gallery picture has another frame).
     */
    fun calibrateCameraOffset(solved: SolveResult.Solved, shot: PhotoShot, star: SkyObject): AlignNote? {
        if (setup.placement != PhonePlacement.CAMERA_FORWARD) return AlignNote("The camera offset only applies when the camera faces along the telescope.")
        if (!shot.fromCamera) return AlignNote("The camera offset needs a photo taken with the camera here, not one from the gallery.")
        val offset = PhotoAlignment.offsetOf(solved, star.ra, star.dec)
            ?: return AlignNote("%s is not on the photo, so the offset could not be found. Nothing was changed.", star.name)
        cameraOffset = offset
        return null
    }

    /** Forgets the camera offset. The alignment is not touched. */
    fun resetCameraOffset() { cameraOffset = null }

    /** Free look pointing at J2000 ([raDeg], [decDeg]) as the sky is now, so the map shows it. Leaves an alignment in progress first. */
    fun showOnMap(raDeg: Double, decDeg: Double) {
        if (aligning) cancelAlign()
        val r = Pointing.rayFromPos(raDeg, decDeg, timeMillis, lat, lon)
        freeAltDeg = Math.toDegrees(asin(r[2].coerceIn(-1.0, 1.0))).coerceIn(-89.0, 89.0)
        freeAzDeg = (Math.toDegrees(atan2(r[0], r[1])) + 360) % 360
        mode = PointingMode.FREE
    }

    /**
     * Changes the setup. If the phone's placement, edge or eyepiece angle changed while aligned, the old alignment no
     * longer fits: it is cleared and [mountingChangedNotice] is raised. Anything else (type, mount, view) keeps it silently.
     */
    fun updateSetup(new: TelescopeSetup) {
        val old = setup
        setup = new
        if (!new.mountingDiffers(old)) return
        cameraOffset = null // the camera-to-telescope offset belongs to the old arrangement
        val had = alignMatrix != null
        if (had || aligning) {
            clearAlignment()
            if (had) mountingChangedNotice = true
        }
    }

    /** After a restart only the star's name was saved; finds the star again for drawing the ring around it. */
    fun restoreAlignStar(catalog: Catalog?) {
        val name = alignStarName ?: return
        if (alignStar == null) alignStar = resolve(name, catalog)
    }

    /** Switches pointing mode: Compass <-> Free look. Free look starts where the view points now, so nothing jumps. */
    fun nextMode() {
        if (aligning) return
        mode = when (mode) {
            PointingMode.COMPASS -> {
                val fwd = camera()[2]
                freeAltDeg = Math.toDegrees(asin(fwd[2].coerceIn(-1.0, 1.0)))
                freeAzDeg = (Math.toDegrees(atan2(fwd[0], fwd[1])) + 360) % 360
                PointingMode.FREE
            }
            PointingMode.FREE -> PointingMode.COMPASS
        }
    }

    /** Free look: dragging moves the sky with the finger in both directions. */
    fun panFree(dxPx: Float, dyPx: Float, widthPx: Float, heightPx: Float) {
        if (mode != PointingMode.FREE || aligning) return
        val fovH = if (widthPx < heightPx) fovDeg * widthPx / heightPx else fovDeg
        val fovV = if (widthPx < heightPx) fovDeg else fovDeg * heightPx / widthPx
        val cosAlt = kotlin.math.cos(Math.toRadians(freeAltDeg)).coerceAtLeast(0.2)
        freeAzDeg = (freeAzDeg - dxPx / widthPx * fovH / cosAlt + 360) % 360
        freeAltDeg = (freeAltDeg + dyPx / heightPx * fovV).coerceIn(-89.0, 89.0)
    }

    /** Adds an object to the first watch list (or a new "Tonight" list) by editing the list text. */
    fun addToWatchList(name: String, catalog: Catalog?) {
        val item = if (name.any { it.isWhitespace() }) "\"$name\"" else name
        watchListText = if (watchListText.isBlank()) "Tonight: $item" else watchListText.trimEnd() + " " + item
        applyUserText(catalog)
    }

    /** Back to defaults: every display setting, the telescope, the location override, lists and objects. */
    fun resetAll(catalog: Catalog?) {
        showConstellations = true; showDeepSky = true; showGrid = false; showAtmosphere = true; showMilkyWay = true; showArt = false
        showEquatorialGrid = false; showMeridian = false; showEcliptic = false; showBoundaries = false; showStarColours = true
        showCardinals = true; hiddenDsoTypes = emptySet(); landscape = Landscape.HILLS; bortle = 4; skyCulture = "modern"; night = false
        telescopeFocalMm = 1200.0; eyepieceFocalMm = 25.0; eyepieceAfovDeg = 52.0; haptics = true
        setup = TelescopeSetup(); matchEyepieceView = false; clearAlignment(); cameraOffset = null
        mode = PointingMode.COMPASS; manualLocation = false; live = true
        userObjectsText = ""; watchListText = ""; applyUserText(catalog); listIndex = -1
    }

    /** Display and telescope settings as strings, for the host to save between launches. */
    fun settings(): Map<String, String> = mapOf(
        "constellations" to "$showConstellations", "deepSky" to "$showDeepSky", "altAzGrid" to "$showGrid", "atmosphere" to "$showAtmosphere",
        "milkyWay" to "$showMilkyWay", "art" to "$showArt", "eqGrid" to "$showEquatorialGrid", "meridian" to "$showMeridian",
        "ecliptic" to "$showEcliptic", "boundaries" to "$showBoundaries", "starColours" to "$showStarColours", "cardinals" to "$showCardinals",
        "hiddenDso" to hiddenDsoTypes.sorted().joinToString(","), "landscape" to landscape.name, "bortle" to "$bortle", "culture" to skyCulture,
        "telescopeMm" to "$telescopeFocalMm", "eyepieceMm" to "$eyepieceFocalMm", "eyepieceAfov" to "$eyepieceAfovDeg",
        "haptics" to "$haptics",
        "setupDone" to "$setupDone", "telescopeType" to setup.type.name, "mountType" to setup.mount.name, "placement" to setup.placement.name,
        "phoneEdge" to setup.edge.name, "eyepieceAngle" to setup.eyepieceAngle.name, "erecting" to setup.erecting.name,
        "viewRotation" to "${setup.viewRotationDeg}", "viewMirrored" to "${setup.viewMirrored}", "matchEyepiece" to "$matchEyepieceView",
        "alignMatrix" to (alignMatrix?.joinToString(",") ?: ""), "alignStar" to (alignStar?.name ?: alignStarName ?: ""),
        "alignedAt" to (alignedAtMillis?.toString() ?: ""),
        "cameraOffset" to (cameraOffset?.let { "%.5f,%.5f".format(java.util.Locale.ROOT, it.first, it.second) } ?: ""),
    )

    /** Restores [settings] output; unknown or malformed values keep their defaults. */
    fun applySettings(s: Map<String, String>) {
        fun b(k: String, set: (Boolean) -> Unit) = s[k]?.toBooleanStrictOrNull()?.let(set)
        fun d(k: String, set: (Double) -> Unit) = s[k]?.toDoubleOrNull()?.takeIf { it > 0 }?.let(set)
        b("constellations") { showConstellations = it }; b("deepSky") { showDeepSky = it }; b("altAzGrid") { showGrid = it }
        b("atmosphere") { showAtmosphere = it }; b("milkyWay") { showMilkyWay = it }; b("art") { showArt = it }
        b("eqGrid") { showEquatorialGrid = it }; b("meridian") { showMeridian = it }; b("ecliptic") { showEcliptic = it }
        b("boundaries") { showBoundaries = it }; b("starColours") { showStarColours = it }; b("cardinals") { showCardinals = it }
        s["hiddenDso"]?.let { v -> hiddenDsoTypes = v.split(',').filter { it.isNotBlank() }.toSet() }
        s["landscape"]?.let { v -> Landscape.values().firstOrNull { it.name == v }?.let { landscape = it } }
        s["bortle"]?.toIntOrNull()?.takeIf { it in 1..9 }?.let { bortle = it }
        s["culture"]?.takeIf { it == "modern" || it == "indian" }?.let { skyCulture = it }
        d("telescopeMm") { telescopeFocalMm = it }; d("eyepieceMm") { eyepieceFocalMm = it }; d("eyepieceAfov") { eyepieceAfovDeg = it }
        b("haptics") { haptics = it }
        // A saved language that isn't available (Hindi while it is switched off) loads as English.
        s["language"]?.let { v -> I18n.language = if (I18n.languages.any { it.first == v }) v else "en" }
        b("setupDone") { setupDone = it }; b("matchEyepiece") { matchEyepieceView = it }
        var t = setup
        // The old "equatorial" key (before mount types existed) only counts when no mount type was saved.
        if (s["mountType"] == null) s["equatorial"]?.toBooleanStrictOrNull()?.let { t = t.copy(mount = if (it) MountType.EQUATORIAL else MountType.ALT_AZ) }
        fun <E : Enum<E>> e(k: String, all: Array<E>, set: (E) -> Unit) { s[k]?.let { v -> all.firstOrNull { it.name == v }?.let(set) } }
        e("telescopeType", TelescopeType.values()) { t = t.copy(type = it) }; e("mountType", MountType.values()) { t = t.copy(mount = it) }
        e("placement", PhonePlacement.values()) { t = t.copy(placement = it) }; e("phoneEdge", PhoneEdge.values()) { t = t.copy(edge = it) }
        e("eyepieceAngle", EyepieceAngle.values()) { t = t.copy(eyepieceAngle = it) }; e("erecting", Erecting.values()) { t = t.copy(erecting = it) }
        s["viewRotation"]?.toIntOrNull()?.takeIf { it in listOf(0, 90, 180, 270) }?.let { t = t.copy(viewRotationDeg = it) }
        s["viewMirrored"]?.toBooleanStrictOrNull()?.let { t = t.copy(viewMirrored = it) }
        setup = t
        restoreAlignment(s["alignMatrix"], s["alignStar"], s["alignedAt"])
        // Two fractions of the picture, both inside it; anything else is ignored.
        s["cameraOffset"]?.split(',')?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 2 && it.all { v -> v in 0.0..1.0 } }
            ?.let { cameraOffset = it[0] to it[1] }
    }

    /** Brings back a saved calibration so the chip can say how old it is; a missing or damaged one is ignored. */
    private fun restoreAlignment(matrix: String?, star: String?, at: String?) {
        val m = matrix?.split(',')?.mapNotNull { it.toDoubleOrNull()?.takeIf(Double::isFinite) }?.takeIf { it.size == 9 }?.toDoubleArray() ?: return
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])
        if (kotlin.math.abs(det - 1.0) > 1e-3) return // not a rotation
        alignMatrix = m
        alignStarName = star?.takeIf { it.isNotBlank() }
        alignedAtMillis = at?.toLongOrNull()
        if (!aligning) align = AlignState.ALIGNED
    }

    /** Time travel: shows the sky [deltaMillis] from the time shown now, and stops following the clock. */
    fun shiftTime(deltaMillis: Long) {
        live = false
        timeMillis += deltaMillis
    }

    /** Sets a typed location, which from then on wins over GPS. */
    fun setManualLocation(latDeg: Double, lonDeg: Double) {
        lat = latDeg
        lon = lonDeg
        manualLocation = true
    }

    /**
     * ΔAlt/ΔAz (degrees) from where the telescope points to the target, and their angular separation. Uses only the
     * telescope's pointing, never the eyepiece view rotation or mirroring.
     */
    fun guidance(): Triple<Double, Double, Double>? {
        val t = target ?: return null
        val fwd = telescopeCamera()[2]
        val r = ray(t)
        val (dAlt, dAz) = Pointing.deltaAltAz(fwd, r)
        val sep = acos(Pointing.dot(fwd, r).coerceIn(-1.0, 1.0)) * 180 / PI
        return Triple(dAlt, dAz, sep)
    }

    /** Equatorial-mount guidance: ΔRA (east positive) and ΔDec (north positive), degrees, from where the telescope points. */
    fun guidanceEquatorial(): Pair<Double, Double>? {
        val t = target ?: return null
        val (ra, dec) = Pointing.rayToRaDec(telescopeCamera()[2], timeMillis, lat, lon)
        val dRa = ((t.ra - ra) + 540) % 360 - 180
        return Pair(dRa, t.dec - dec) // raw RA difference: that is what the RA axis turns through
    }

    /**
     * How to move the telescope to reach the target, from the telescope's pointing only. Alt-az: [vertical] is up (+) or
     * down (−) and [horizontal] right (+, increasing azimuth) or left (−), both as distances on the sky. Equatorial:
     * [vertical] is Dec (north +) and [horizontal] is RA (east +) in degrees, as the axes turn. [arrowDeg] is the arrow's
     * direction on screen, 0 = up, clockwise positive.
     */
    fun moveHint(): MoveHint? {
        val (dAlt, dAz, sep) = guidance() ?: return null
        val fwdAlt = Math.toDegrees(asin(telescopeCamera()[2][2].coerceIn(-1.0, 1.0)))
        val tgtAlt = fwdAlt + dAlt
        if (setup.mount == MountType.EQUATORIAL) {
            val (dRa, dDec) = guidanceEquatorial() ?: return null
            val decMid = (target!!.dec + (target!!.dec - dDec)) / 2
            val onSkyRa = dRa * cos(Math.toRadians(decMid))
            // Facing south (northern hemisphere) east is on the left; facing north (southern) it is on the right and north is down.
            val north = lat >= 0
            val dx = if (north) -onSkyRa else onSkyRa
            val dy = if (north) dDec else -dDec
            return MoveHint(true, dDec, dRa, sep, Math.toDegrees(atan2(dx, dy)))
        }
        val right = dAz * cos(Math.toRadians((fwdAlt + tgtAlt) / 2))
        return MoveHint(false, dAlt, right, sep, Math.toDegrees(atan2(right, dAlt)))
    }

    /** Current apparent RA/Dec (J2000, degrees) for a Sun/Moon/planet body index. */
    fun bodyPosition(body: Int): Pair<Double, Double> {
        val r = ApparentPosition.reduce(body, JulianDate.fromEpochMillis(timeMillis), lat * PI / 180, lon * PI / 180)
        return Pair(r.raJ2000 * 180 / PI, r.decJ2000 * 180 / PI)
    }

    companion object {
        /** Below this altitude an alignment star is hard to centre (thick air, low mounts): a warning is shown. */
        const val LOW_STAR_DEG = 10.0
        /** A correction bigger than this makes the result card ask whether the star really was centred. */
        const val LARGE_CORRECTION_DEG = 20.0
        private const val MAX_SAMPLES = 4
        /** A photo is not used for alignment if the telescope axis turned more than this while it was taken. */
        const val MAX_PHOTO_MOVE_DEG = 0.3
        val FOV_STEPS = listOf(1.0, 3.0, 7.0, 15.0, 30.0, 60.0, 90.0, 120.0)
    }
}
