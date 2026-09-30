package org.astrofixxer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.CoordinateParser
import org.astrofixxer.astro.SkyObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Find: Object · Position · Lists

@Composable
internal fun SearchSheet(state: SkyState, catalog: Catalog?, moving: List<MovingObject>, onClose: () -> Unit, onInfo: (SkyObject) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    SheetFrame(t("Find"), onClose) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f)) {
            listOf("Object", "Position", "Lists").forEachIndexed { i, label ->
                // Plain one-line text: TabRow measures its tabs in a way Label's BoxWithConstraints can't answer.
                Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.heightIn(min = 48.dp), text = { Text(t(label), maxLines = 1) })
            }
        }
        val pick = { o: SkyObject -> state.target = o; onClose() }
        when (tab) {
            0 -> ObjectSearch(state, catalog, moving, pick, onInfo)
            1 -> PositionSearch(pick)
            else -> ListBrowser(state, catalog, pick, onInfo)
        }
    }
}

@Composable
private fun ObjectSearch(state: SkyState, catalog: Catalog?, moving: List<MovingObject>, pick: (SkyObject) -> Unit, onInfo: (SkyObject) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, catalog, moving) {
        val q = query.trim()
        // Empty box: what is above the horizon right now, so there is always something to pick.
        if (q.isEmpty()) (moving.filter { it.kind != MovingObject.Kind.SUN }.map { it.obj } + state.userObjects).filter { state.ray(it)[2] > 0 }
        else (moving.map { it.obj } + state.userObjects).filter { it.name.contains(q, ignoreCase = true) } +
            constellationObjects(catalog, state.skyCulture).filter { it.name.contains(q, ignoreCase = true) } +
            (catalog?.search(q).orEmpty())
    }
    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        placeholder = { Text(t("M42, Orion Nebula, NGC 7000, Sirius, Jupiter…")) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(pick) }))
    if (query.isBlank() && results.isNotEmpty()) Text(t("Visible now"), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
        modifier = Modifier.padding(top = 8.dp))
    if (query.isNotBlank() && results.isEmpty()) Text(t("Nothing found. Try a catalogue number like M31 or NGC 7000."),
        color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    ResultList(state, results, pick, onInfo)
}

/** Constellations (or nakshatras) as objects at their centres, so they can be searched and targeted. */
private fun constellationObjects(catalog: Catalog?, culture: String): List<SkyObject> =
    catalog?.constellations?.get(culture).orEmpty().map { SkyObject(it.name, it.ra, it.dec, null, "Con", 0.0, it.otherNames) }

@Composable
private fun ResultList(state: SkyState, results: List<SkyObject>, pick: (SkyObject) -> Unit, onInfo: (SkyObject) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        items(results) { o -> ResultRow(state, o, pick, onInfo) }
    }
}

/** One search result: name, type, magnitude and "Up · 43°" or "Rises 21:40"; tap to target, Info for details. */
@Composable
private fun ResultRow(state: SkyState, o: SkyObject, pick: (SkyObject) -> Unit, onInfo: (SkyObject) -> Unit) {
    val c = MaterialTheme.colorScheme
    val alt = Math.toDegrees(kotlin.math.asin(state.ray(o)[2]))
    val where = if (alt > 0) t("Up · %d°").format(alt.roundToInt())
    else nextRise(o, state.timeMillis, state.lat, state.lon)?.let { t("Rises %s").format(formatClock(it)) } ?: t("below horizon")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable { pick(o) }.padding(vertical = 8.dp)) {
            Text(o.name, color = c.onSurface, fontSize = 17.sp)
            val sub = listOfNotNull(TYPE_NAMES[o.type]?.let { t(it) }, o.mag?.let { "mag %.1f".format(it) }, where, o.otherNames.firstOrNull())
            Text(sub.joinToString(" · "), color = c.onSurfaceVariant, fontSize = 12.sp)
        }
        OutlinedButton(onClick = { onInfo(o) }, modifier = Modifier.heightIn(min = 48.dp)) { Label(t("Info")) }
    }
}

@Composable
private fun PositionSearch(pick: (SkyObject) -> Unit) {
    var ra by remember { mutableStateOf("") }
    var dec by remember { mutableStateOf("") }
    val raDeg = CoordinateParser.parseRA(ra)
    val decDeg = CoordinateParser.parseDEC(dec)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Point at any position in the sky (J2000)."), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        OutlinedTextField(ra, { ra = it }, label = { Text(t("RA, e.g. 05:35:17 or 83.82")) }, singleLine = true,
            isError = ra.isNotBlank() && raDeg == null, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dec, { dec = it }, label = { Text(t("Dec, e.g. -05:23:28 or -5.39")) }, singleLine = true,
            isError = dec.isNotBlank() && decDeg == null, modifier = Modifier.fillMaxWidth())
        Button(onClick = { pick(SkyObject("RA ${hms(raDeg!!)} Dec ${dms(decDeg!!)}", raDeg, decDeg, null, "Pos")) },
            enabled = raDeg != null && decDeg != null, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Go to this position")) }
    }
}

@Composable
private fun ListBrowser(state: SkyState, catalog: Catalog?, pick: (SkyObject) -> Unit, onInfo: (SkyObject) -> Unit) {
    val lists = remember(catalog, state.userObjects, state.watchLists) {
        val cat = catalog
        buildList<Pair<String, List<SkyObject>>> {
            if (cat != null) {
                add("Messier" to (1..110).mapNotNull { cat.find("M$it") })
                add("Caldwell" to (1..109).mapNotNull { cat.find("C$it") })
                add("Bright stars" to cat.objects.filter { it.type == "S" && (it.mag ?: 99.0) <= 1.5 }.sortedBy { it.mag })
                add("Indian constellations" to constellationObjects(cat, "indian")) // nakshatras and rashis
            }
            if (state.userObjects.isNotEmpty()) add("My objects" to state.userObjects)
            for (wl in state.watchLists) add(wl.name to wl.items.mapNotNull { state.resolve(it.name, cat) })
        }
    }
    var chosen by remember { mutableStateOf(0) }
    if (lists.isEmpty()) {
        Text(t("Loading sky catalogue…"), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
        return
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        lists.forEachIndexed { i, (name, _) ->
            val selected = i == chosen.coerceAtMost(lists.size - 1)
            if (selected) Button(onClick = { chosen = i }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t(name)) }
            else OutlinedButton(onClick = { chosen = i }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t(name)) }
        }
    }
    val (name, items) = lists[chosen.coerceAtMost(lists.size - 1)]
    Text(t("%s · %d objects").format(t(name), items.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        modifier = Modifier.padding(top = 8.dp))
    ResultList(state, items, pick, onInfo)
}

// ---------------------------------------------------------------- Sky & Viewing (tabbed, like Stellarium's window)

/** Index of "Telescope & orientation" among the tabs of Sky & viewing. */
internal const val TELESCOPE_TAB = 5

@Composable
internal fun SkyOptionsSheet(
    state: SkyState, catalog: Catalog?, onClose: () -> Unit, onLists: () -> Unit, onHelp: () -> Unit, onTutorial: () -> Unit,
    onCheckOrientation: () -> Unit, onSolveWithCamera: (() -> Unit)? = null, initialTab: Int = 0,
) {
    var tab by remember { mutableStateOf(initialTab) }
    val tabs = listOf("Sky", "Deep-sky", "Markings", "Culture", "Landscape", "Telescope & orientation", "Place & time", "More")
    SheetFrame(t("Sky & viewing"), onClose) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f)) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.heightIn(min = 48.dp), text = { Text(t(label), maxLines = 1) })
            }
        }
        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            when (tab) {
                0 -> {
                    item { Toggle(t("Star colours"), state.showStarColours) { state.showStarColours = it } }
                    item { Toggle(t("Milky Way"), state.showMilkyWay) { state.showMilkyWay = it } }
                    item { Toggle(t("Atmosphere (daylight and twilight)"), state.showAtmosphere) { state.showAtmosphere = it } }
                    item {
                        Stepper(t("Light pollution (Bortle)"), state.bortle.toString(),
                            onMinus = { state.bortle = (state.bortle - 1).coerceAtLeast(1) }, onPlus = { state.bortle = (state.bortle + 1).coerceAtMost(9) })
                    }
                }
                1 -> {
                    item { Toggle(t("Deep-sky objects"), state.showDeepSky) { state.showDeepSky = it } }
                    for ((code, name) in listOf("Ga" to "Galaxies", "Oc" to "Open clusters", "Gc" to "Globular clusters", "Ne" to "Nebulae")) {
                        item {
                            Toggle(t(name), code !in state.hiddenDsoTypes) { on ->
                                state.hiddenDsoTypes = if (on) state.hiddenDsoTypes - code else state.hiddenDsoTypes + code
                            }
                        }
                    }
                }
                2 -> {
                    item { Toggle(t("Constellation lines and names"), state.showConstellations) { state.showConstellations = it } }
                    item { Toggle(t("Constellation artwork"), state.showArt) { state.showArt = it } }
                    item { Toggle(t("Constellation boundaries"), state.showBoundaries) { state.showBoundaries = it } }
                    item { Toggle(t("Alt/Az grid"), state.showGrid) { state.showGrid = it } }
                    item { Toggle(t("Equatorial grid"), state.showEquatorialGrid) { state.showEquatorialGrid = it } }
                    item { Toggle(t("Meridian"), state.showMeridian) { state.showMeridian = it } }
                    item { Toggle(t("Ecliptic"), state.showEcliptic) { state.showEcliptic = it } }
                    item { Toggle(t("Cardinal points"), state.showCardinals) { state.showCardinals = it } }
                }
                3 -> {
                    item { Choice(t("Western"), t("The 88 IAU constellations with Stellarium's artwork."), state.skyCulture == "modern") { state.skyCulture = "modern" } }
                    item { Choice(t("Indian (Vedic)"), t("Nakshatras, rashis and Indian star names."), state.skyCulture == "indian") { state.skyCulture = "indian" } }
                }
                4 -> for (l in Landscape.values()) item { Choice(t(l.label), null, state.landscape == l) { state.landscape = l } }
                TELESCOPE_TAB -> item { TelescopeSettings(state, onCheckOrientation, onSolveWithCamera) }
                6 -> {
                    item { LocationEditor(state) }
                    item { CityList(state) }
                    item { DateTimeEditor(state) }
                }
                else -> item { MoreSettings(state, catalog, onLists, onHelp, onTutorial) }
            }
        }
    }
}

/** A radio-button row (sky culture, landscape). */
@Composable
internal fun Choice(label: String, detail: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

/** A number field that only accepts a positive number in [range]; applies it as soon as it is valid. */
@Composable
internal fun NumberField(label: String, value: Double, range: ClosedFloatingPointRange<Double>, modifier: Modifier, onValid: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()) }
    val parsed = text.toDoubleOrNull()?.takeIf { it in range }
    OutlinedTextField(text, { text = it; it.toDoubleOrNull()?.takeIf { v -> v in range }?.let(onValid) }, label = { Text(label) },
        singleLine = true, isError = parsed == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier)
}

/** Offline cities for choosing a location without GPS (India first, then major world cities). */
private val CITIES = listOf(
    "New Delhi" to (28.6139 to 77.2090), "Mumbai" to (19.0760 to 72.8777), "Bengaluru" to (12.9716 to 77.5946),
    "Chennai" to (13.0827 to 80.2707), "Kolkata" to (22.5726 to 88.3639), "Hyderabad" to (17.3850 to 78.4867),
    "Pune" to (18.5204 to 73.8567), "Ahmedabad" to (23.0225 to 72.5714), "Jaipur" to (26.9124 to 75.7873),
    "Lucknow" to (26.8467 to 80.9462), "Bhopal" to (23.2599 to 77.4126), "Chandigarh" to (30.7333 to 76.7794),
    "Guwahati" to (26.1445 to 91.7362), "Bhubaneswar" to (20.2961 to 85.8245), "Thiruvananthapuram" to (8.5241 to 76.9366),
    "Kochi" to (9.9312 to 76.2673), "Patna" to (25.5941 to 85.1376), "Srinagar" to (34.0837 to 74.7973),
    "Leh" to (34.1526 to 77.5771), "Hanle (observatory)" to (32.7794 to 78.9642), "Shillong" to (25.5788 to 91.8933),
    "Panaji" to (15.4909 to 73.8278), "Mount Abu" to (24.5926 to 72.7156), "Nainital" to (29.3919 to 79.4542),
    "Dehradun" to (30.3165 to 78.0322), "Varanasi" to (25.3176 to 82.9739), "Indore" to (22.7196 to 75.8577),
    "Nagpur" to (21.1458 to 79.0882), "Visakhapatnam" to (17.6868 to 83.2185), "Mysuru" to (12.2958 to 76.6394),
    "Kathmandu" to (27.7172 to 85.3240), "Dhaka" to (23.8103 to 90.4125), "Colombo" to (6.9271 to 79.8612),
    "Dubai" to (25.2048 to 55.2708), "Singapore" to (1.3521 to 103.8198), "Tokyo" to (35.6762 to 139.6503),
    "Beijing" to (39.9042 to 116.4074), "Sydney" to (-33.8688 to 151.2093), "Auckland" to (-36.8485 to 174.7633),
    "Nairobi" to (-1.2921 to 36.8219), "Cape Town" to (-33.9249 to 18.4241), "Cairo" to (30.0444 to 31.2357),
    "Istanbul" to (41.0082 to 28.9784), "Moscow" to (55.7558 to 37.6173), "Kyiv" to (50.4501 to 30.5234),
    "Budapest" to (47.4979 to 19.0402), "Berlin" to (52.5200 to 13.4050), "Paris" to (48.8566 to 2.3522),
    "London" to (51.5074 to -0.1278), "Reykjavik" to (64.1466 to -21.9426), "Jerusalem" to (31.7683 to 35.2137),
    "New York" to (40.7128 to -74.0060), "Toronto" to (43.6532 to -79.3832), "Los Angeles" to (34.0522 to -118.2437),
    "Honolulu" to (21.3069 to -157.8583), "Mexico City" to (19.4326 to -99.1332), "São Paulo" to (-23.5505 to -46.6333),
    "Santiago" to (-33.4489 to -70.6693),
)

@Composable
private fun LocationEditor(state: SkyState) {
    var lat by remember(state.lat) { mutableStateOf("%.4f".format(java.util.Locale.ROOT, state.lat)) }
    var lon by remember(state.lon) { mutableStateOf("%.4f".format(java.util.Locale.ROOT, state.lon)) }
    val latOk = lat.toDoubleOrNull()?.let { it in -90.0..90.0 } == true
    val lonOk = lon.toDoubleOrNull()?.let { it in -180.0..180.0 } == true
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(t("Location (overrides GPS)"), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lat, { lat = it }, label = { Text(t("Latitude")) }, isError = !latOk, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(lon, { lon = it }, label = { Text(t("Longitude")) }, isError = !lonOk, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.setManualLocation(lat.toDouble(), lon.toDouble()) }, enabled = latOk && lonOk,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Use this location")) }
            if (state.manualLocation) OutlinedButton(onClick = { state.manualLocation = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Use GPS")) }
        }
        if (!latOk || !lonOk) Text(t("Latitude −90 to 90, longitude −180 to 180 (east positive)."), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun CityList(state: SkyState) {
    var query by remember { mutableStateOf("") }
    val matches = CITIES.filter { query.isBlank() || it.first.contains(query.trim(), ignoreCase = true) }.take(8)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text(t("Or pick a city")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        for ((name, pos) in matches) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { state.setManualLocation(pos.first, pos.second) },
                verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("%.1f°, %.1f°".format(pos.first, pos.second), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DateTimeEditor(state: SkyState) {
    val zone = ZoneId.systemDefault()
    val shown = Instant.ofEpochMilli(state.timeMillis).atZone(zone)
    var date by remember(state.live) { mutableStateOf(shown.toLocalDate().toString()) }
    var time by remember(state.live) { mutableStateOf("%02d:%02d".format(shown.hour, shown.minute)) }
    val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    val parsedTime = runCatching { LocalTime.parse(time.trim()) }.getOrNull()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(t("Date & time"), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text(t("Date (YYYY-MM-DD)")) }, singleLine = true, isError = parsedDate == null,
                modifier = Modifier.weight(1.3f))
            OutlinedTextField(time, { time = it }, label = { Text(t("Time (HH:MM)")) }, singleLine = true, isError = parsedTime == null,
                modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                state.live = false
                state.timeMillis = parsedDate!!.atTime(parsedTime!!).atZone(zone).toInstant().toEpochMilli()
            }, enabled = parsedDate != null && parsedTime != null, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Show this time")) }
            OutlinedButton(onClick = { state.live = true }, enabled = !state.live, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Now")) }
        }
    }
}

@Composable
private fun MoreSettings(state: SkyState, catalog: Catalog?, onLists: () -> Unit, onHelp: () -> Unit, onTutorial: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Own row for the long label: in Hindi it doesn't fit a third of a narrow phone in every font.
        OutlinedButton(onClick = onLists, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("My objects & lists")) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onHelp, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Help")) }
            OutlinedButton(onClick = onTutorial, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Tutorial")) }
        }
        val langs = I18n.languages
        val next = { langs[(langs.indexOfFirst { it.first == I18n.language } + 1) % langs.size].first.also { I18n.language = it } }
        Stepper(t("Language"), langs.first { it.first == I18n.language }.second, onMinus = { next() }, onPlus = { next() })
        Text(t("Data"), color = c.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(if (catalog == null) t("Loading sky catalogue…")
            else t("Stellarium catalogue: %,d objects, %d constellations, %d boundary edges.").format(
                catalog.objects.size, catalog.constellations["modern"].orEmpty().size, catalog.boundaries.size),
            color = c.onSurface, fontSize = 14.sp)
        if (!confirmReset) OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) { Text(t("Reset all")) }
        else Button(onClick = { state.resetAll(catalog); confirmReset = false }, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.error)) { Text(t("Tap again to reset every setting and list")) }
    }
}

// ---------------------------------------------------------------- Tonight card (top of Events)

@Composable
internal fun TonightCard(state: SkyState) {
    val c = MaterialTheme.colorScheme
    val night = nightStart(state.timeMillis)
    val summary by produceState<TonightSummary?>(null, night, state.lat, state.lon) {
        value = withContext(Dispatchers.Default) { tonightSummary(state.timeMillis, state.lat, state.lon) }
    }
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), color = c.surfaceVariant.copy(alpha = 1f)) {
        Column(Modifier.padding(12.dp)) {
            Text(t("Tonight"), color = c.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            val s = summary
            // if/else, not an early return: returning out of a composable lambda upsets older Compose compilers.
            if (s == null) Text(t("Working out tonight's positions…"), color = c.onSurfaceVariant, fontSize = 14.sp)
            else TonightLines(s)
        }
    }
}

@Composable
private fun TonightLines(s: TonightSummary) {
    val c = MaterialTheme.colorScheme
    fun at(m: Long?) = m?.let { formatClock(it) } ?: "—"
    val lines = listOf(
        t("Sunset %s · sunrise %s").format(at(s.sunset), at(s.sunrise)),
        if (s.darkStart != null) t("Fully dark %s–%s").format(at(s.darkStart), at(s.darkEnd)) else t("No fully dark sky tonight"),
        t("Moon %d%% lit · rises %s · sets %s").format(s.moonLitPercent, at(s.moonrise), at(s.moonset)),
        if (s.planets.isEmpty()) t("No bright planets in the dark sky tonight")
        else t("Planets: %s").format(s.planets.joinToString(", ") { (name, time, alt) -> "${t(name)} ${formatClock(time)} (${alt.roundToInt()}°)" }),
    )
    for (line in lines) Text(line, color = c.onSurface, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
}

// ---------------------------------------------------------------- Long-press quick menu

@Composable
/** [onAlign] is null for objects that cannot be an alignment star (deep-sky objects, the Sun and Moon, anything below the horizon). */
internal fun QuickMenu(obj: SkyObject, at: Offset, onDismiss: () -> Unit, onTarget: () -> Unit, onAlign: (() -> Unit)?, onAdd: () -> Unit, onInfo: () -> Unit) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
        val x = with(density) { (at.x.toDp() - 20.dp).coerceAtLeast(8.dp) }
        val y = with(density) { (at.y.toDp() - 20.dp).coerceAtLeast(56.dp) }
        Surface(Modifier.offset { IntOffset(with(density) { x.roundToPx() }, with(density) { y.roundToPx() }) }.width(220.dp),
            shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 1f), shadowElevation = 8.dp) {
            Column(Modifier.padding(8.dp)) {
                Text(obj.name, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                for ((label, action) in listOfNotNull("Set as target" to onTarget, onAlign?.let { "Align using this star" to it }, "Add to list" to onAdd, "Info" to onInfo)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { action(); onDismiss() }.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Text(t(label), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- AstroGuide suggestion chips

internal val GUIDE_SUGGESTIONS = listOf("What's up tonight?", "Find Saturn", "Next meteor shower", "What is M42?", "Next eclipse")

@Composable
internal fun GuideSuggestions(onAskText: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in GUIDE_SUGGESTIONS) OutlinedButton(onClick = { onAskText(t(s)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t(s), maxLines = 1) }
    }
}
