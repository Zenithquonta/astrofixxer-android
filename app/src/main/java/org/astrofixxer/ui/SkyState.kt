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
enum class PointingMode { COMPASS, MANUAL }

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

    /** Phone orientation with the manual azimuth offset applied (rotation about the up axis). */
    fun worldDevice(): DoubleArray {
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

    /** Current apparent RA/Dec (J2000, degrees) for a Sun/Moon/planet body index. */
    fun bodyPosition(body: Int): Pair<Double, Double> {
        val r = ApparentPosition.reduce(body, JulianDate.fromEpochMillis(timeMillis), lat * PI / 180, lon * PI / 180)
        return Pair(r.raJ2000 * 180 / PI, r.decJ2000 * 180 / PI)
    }

    companion object {
        val FOV_STEPS = listOf(1.0, 3.0, 7.0, 15.0, 30.0, 60.0, 90.0, 120.0)
    }
}
