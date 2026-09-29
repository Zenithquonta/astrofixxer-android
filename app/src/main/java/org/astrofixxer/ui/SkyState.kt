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
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

enum class AlignState { NOT_ALIGNED, PICK_STAR, ALIGNED }
enum class Landscape(val label: String) { NONE("None"), HILLS("Hills"), TREES("Trees"), CITY("City"), OBSERVATORY("Dome") }
enum class PointingMode { COMPASS, MANUAL, FREE }

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
    /** Manual mode: user drags the sky to fix the azimuth. */
    var azOffsetDeg by mutableDoubleStateOf(0.0)

    var align by mutableStateOf(AlignState.NOT_ALIGNED)
    var alignMatrix by mutableStateOf<DoubleArray?>(null)
    var alignedAtMillis by mutableStateOf<Long?>(null)
    var alignStar by mutableStateOf<SkyObject?>(null)
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
    /** Equatorial mounts move in RA/Dec, so guidance is given in those instead of altitude/azimuth. */
    var equatorialMount by mutableStateOf(false)
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

    /** Phone orientation with the manual azimuth offset applied (rotation about the up axis); Free look ignores the sensors. */
    fun worldDevice(): DoubleArray {
        if (mode == PointingMode.FREE) return Pointing.rotationMatrix(-freeAzDeg, freeAltDeg, 0.0)
        if (azOffsetDeg == 0.0) return device
        val a = -azOffsetDeg * PI / 180
        val rz = doubleArrayOf(cos(a), -sin(a), 0.0, sin(a), cos(a), 0.0, 0.0, 0.0, 1.0)
        return Pointing.matMul(rz, device)
    }

    fun camera(): Array<DoubleArray> = Pointing.cameraRays(worldDevice(), alignMatrix)

    fun ray(o: SkyObject) = Pointing.rayFromPos(o.ra, o.dec, timeMillis, lat, lon)

    /** Tapped star while picking: the telescope is pointing at it, so rotate the frame onto it. */
    fun alignOn(star: SkyObject) {
        alignMatrix = Pointing.alignMatrix(Pointing.cameraRays(worldDevice()), ray(star))
        alignStar = star
        alignedAtMillis = timeMillis
        align = AlignState.ALIGNED
    }

    /** Waits for the user to tap the star the telescope points at. The old alignment stays until a new one is made. */
    fun startAlign() {
        align = AlignState.PICK_STAR
    }

    /** Leaves star picking without changing anything. */
    fun cancelAlign() {
        align = if (alignMatrix != null) AlignState.ALIGNED else AlignState.NOT_ALIGNED
    }

    /**
     * Manual mode: a horizontal drag of [dxPx] on a [widthPx]×[heightPx] sky moves the sky with the finger.
     * Turning by Δaz moves things sideways by only Δaz·cos(altitude), so high up the turn is larger.
     */
    fun dragSky(dxPx: Float, widthPx: Float, heightPx: Float) {
        val fovH = if (widthPx < heightPx) fovDeg * widthPx / heightPx else fovDeg
        val up = camera()[2][2].coerceIn(-1.0, 1.0)
        val cosAlt = kotlin.math.sqrt(1 - up * up).coerceAtLeast(0.2) // near the zenith azimuth barely moves anything
        azOffsetDeg -= dxPx / widthPx * fovH / cosAlt
    }

    /** Switches pointing mode: Compass -> Manual -> Free look. Free look starts where the view points now, so nothing jumps. */
    fun nextMode() {
        mode = when (mode) {
            PointingMode.COMPASS -> PointingMode.MANUAL
            PointingMode.MANUAL -> {
                val fwd = camera()[2]
                freeAltDeg = Math.toDegrees(kotlin.math.asin(fwd[2].coerceIn(-1.0, 1.0)))
                freeAzDeg = (Math.toDegrees(kotlin.math.atan2(fwd[0], fwd[1])) + 360) % 360
                PointingMode.FREE
            }
            PointingMode.FREE -> PointingMode.COMPASS
        }
    }

    /** Free look: dragging moves the sky with the finger in both directions. */
    fun panFree(dxPx: Float, dyPx: Float, widthPx: Float, heightPx: Float) {
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
        telescopeFocalMm = 1200.0; eyepieceFocalMm = 25.0; eyepieceAfovDeg = 52.0; equatorialMount = false; haptics = true
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
        "equatorial" to "$equatorialMount", "haptics" to "$haptics",
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
        b("equatorial") { equatorialMount = it }; b("haptics") { haptics = it }
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

    /** ΔAlt/ΔAz (degrees) from where the telescope points to the target, and their angular separation. */
    fun guidance(): Triple<Double, Double, Double>? {
        val t = target ?: return null
        val fwd = camera()[2]
        val r = ray(t)
        val (dAlt, dAz) = Pointing.deltaAltAz(fwd, r)
        val sep = acos(Pointing.dot(fwd, r).coerceIn(-1.0, 1.0)) * 180 / PI
        return Triple(dAlt, dAz, sep)
    }

    /** Equatorial-mount guidance: ΔRA (east positive) and ΔDec (north positive), degrees, from where the telescope points. */
    fun guidanceEquatorial(): Pair<Double, Double>? {
        val t = target ?: return null
        val (ra, dec) = Pointing.rayToRaDec(camera()[2], timeMillis, lat, lon)
        val dRa = ((t.ra - ra) + 540) % 360 - 180
        return Pair(dRa, t.dec - dec) // raw RA difference: that is what the RA axis turns through
    }

    /** Current apparent RA/Dec (J2000, degrees) for a Sun/Moon/planet body index. */
    fun bodyPosition(body: Int): Pair<Double, Double> {
        val r = ApparentPosition.reduce(body, JulianDate.fromEpochMillis(timeMillis), lat * PI / 180, lon * PI / 180)
        return Pair(r.raJ2000 * 180 / PI, r.decJ2000 * 180 / PI)
    }

    companion object {
        val FOV_STEPS = listOf(1.0, 3.0, 7.0, 15.0, 30.0, 60.0, 90.0, 120.0)
    }
}
