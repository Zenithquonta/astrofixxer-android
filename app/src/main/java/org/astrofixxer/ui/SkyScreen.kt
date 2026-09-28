package org.astrofixxer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astrofixxer.astro.Catalog
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal val DayColors = darkColorScheme(
    primary = Color(0xFF00BFFF), onPrimary = Color.Black, surface = Color(0xE60A1018), onSurface = Color(0xFFDCE6F2),
    onSurfaceVariant = Color(0xFFA3AEBC),
    background = Color.Black, onBackground = Color(0xFFDCE6F2), secondary = Color(0xFFFF4FD8), error = Color(0xFFFFB347),
    outline = Color(0xFF3F6E8F),
)
// Night mode: pure red only (keeps dark adaptation), just bright enough for 3:1 contrast on black.
private val NightRed = Color(0xFFC81414)
internal val NightColors = darkColorScheme(
    primary = NightRed, onPrimary = Color.Black, surface = Color(0xF0000000), onSurface = NightRed,
    background = Color.Black, onBackground = NightRed, secondary = NightRed, error = Color(0xFFE01818),
    outline = Color(0xFF5A0707), outlineVariant = Color(0xFF3A0505), surfaceVariant = Color.Black, onSurfaceVariant = NightRed,
)

private enum class Sheet { NONE, SEARCH, EVENTS, SKY, LISTS, HELP }

private val TYPE_NAMES = mapOf("S" to "Star", "Ga" to "Galaxy", "Oc" to "Open cluster", "Gc" to "Globular cluster", "Ne" to "Nebula", "P" to "Solar system", "C" to "Comet", "U" to "My object")

/**
 * The main screen: Stellarium-style sky, info overlay, alignment chip, guidance panel and toolbar.
 * [events] is computed by the host in the background; null while computing.
 */
@Composable
fun SkyScreen(
    state: SkyState,
    catalog: Catalog?,
    moving: List<MovingObject>,
    events: List<EventItem>?,
    art: Map<String, ImageBitmap> = emptyMap(),
    /** Starts listening for an AstroGuide question; null hides the Ask button (no speech recogniser). */
    onAsk: (() -> Unit)? = null,
    /** Hooks the system Back button (Android's BackHandler); the screen stays free of Android imports. */
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var showTime by remember { mutableStateOf(false) }
    val colors: ColorScheme = if (state.night) NightColors else DayColors
    // Back closes the innermost thing that is open; with nothing open it leaves the app as usual.
    val guideOpen = state.guideListening || state.guideAnswer != null
    backHandler(state.showOnboarding || sheet != Sheet.NONE || state.align == AlignState.PICK_STAR || guideOpen || showTime) {
        when {
            state.showOnboarding -> state.showOnboarding = false
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            state.align == AlignState.PICK_STAR -> state.cancelAlign()
            guideOpen -> { state.guideAnswer = null; state.guideListening = false }
            else -> showTime = false
        }
    }
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            SkyCanvas(state, catalog, moving, Modifier.fillMaxSize(), art)
            // One row so the target card and the status chips share the width and never overlap on narrow phones.
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { InfoOverlay(state, Modifier) }
                Column(Modifier.widthIn(max = 170.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlignChip(state)
                    ClockChip(state) { showTime = !showTime }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.target != null && state.align == AlignState.ALIGNED) GuidancePanel(state)
                if (state.target != null && state.align == AlignState.NOT_ALIGNED) AlignHint(state)
                if (showTime || !state.live) TimeBar(state)
                if (state.listIndex >= 0) WatchNavigator(state, catalog)
                if (state.guideListening || state.guideAnswer != null) GuideBubble(state)
                if (catalog == null) Text(t("Loading sky catalogue…"), color = colors.onSurface, modifier = Modifier.padding(start = 8.dp))
                Toolbar(state, onAsk,
                    onSearch = { sheet = Sheet.SEARCH },
                    onEvents = { sheet = Sheet.EVENTS },
                    onSky = { sheet = Sheet.SKY })
            }
            when (sheet) {
                Sheet.SEARCH -> SearchSheet(state, catalog, moving) { sheet = Sheet.NONE }
                Sheet.EVENTS -> EventsSheet(state, events) { sheet = Sheet.NONE }
                Sheet.SKY -> SkyOptionsSheet(state, onClose = { sheet = Sheet.NONE }, onLists = { sheet = Sheet.LISTS },
                    onHelp = { sheet = Sheet.HELP }, onTutorial = { sheet = Sheet.NONE; state.showOnboarding = true })
                Sheet.LISTS -> ListsSheet(state, catalog) { sheet = Sheet.NONE }
                Sheet.HELP -> HelpSheet { sheet = Sheet.NONE }
                Sheet.NONE -> {}
            }
            if (state.showOnboarding) Onboarding { state.showOnboarding = false }
        }
    }
}

/** Catalogue designations such as "NGC6720", "PK063+13.1" or "Abell 2151", as opposed to names like "Ring Nebula". */
private val CATALOGUE_ID = Regex("^[A-Za-z]{1,6}[ -]?\\d.*")
private val WORD = Regex("[a-z\\p{L}&&[^A-Z]]{2}")

/** "Ring Nebula" or "47 Tuc" yes; "PN G063.1+13.9" or "Sh2-155" no. */
private fun isFriendlyName(n: String) = WORD.containsMatchIn(n) && !CATALOGUE_ID.matches(n)

@Composable
private fun InfoOverlay(state: SkyState, modifier: Modifier) {
    val obj = state.target ?: return
    val c = MaterialTheme.colorScheme
    val ray = state.ray(obj)
    val altDeg = Math.toDegrees(kotlin.math.asin(ray[2]))
    val azDeg = (Math.toDegrees(kotlin.math.atan2(ray[0], ray[1])) + 360) % 360
    Column(modifier.background(c.surface.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(obj.name, color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        // The friendly name if there is one ("Ring Nebula"); catalogue numbers are noise here.
        obj.otherNames.firstOrNull(::isFriendlyName)?.let { Text(it, color = c.onSurface, fontSize = 14.sp) }
        val details = listOfNotNull(TYPE_NAMES[obj.type]?.let { t(it) } ?: obj.type, obj.mag?.let { "mag %.1f".format(it) },
            obj.sizeArcmin.takeIf { it > 0 }?.let { "%.0f′".format(it) })
        Text(details.joinToString(" · "), color = c.onSurfaceVariant, fontSize = 13.sp)
        if (altDeg >= 0) Text(t("%d° up · %s").format(altDeg.roundToInt(), t(compass(azDeg))), color = c.primary, fontSize = 14.sp)
        else Text(t("Below the horizon"), color = c.error, fontSize = 14.sp)
        Text("RA ${hms(obj.ra)} · Dec ${dms(obj.dec)}", color = c.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun AlignChip(state: SkyState) {
    val (label, color) = when (state.align) {
        AlignState.NOT_ALIGNED -> t("Not aligned") to MaterialTheme.colorScheme.error
        AlignState.PICK_STAR -> t("Tap the star the telescope points at") to MaterialTheme.colorScheme.primary
        AlignState.ALIGNED -> {
            val min = ((state.timeMillis - (state.alignedAtMillis ?: state.timeMillis)) / 60000).coerceAtLeast(0)
            (if (min >= 10) t("Aligned %d min ago · re-align soon").format(min) else t("Aligned ✓")) to MaterialTheme.colorScheme.primary
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(horizontalAlignment = Alignment.End) {
            Text(label, color = color, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
            if (state.align == AlignState.PICK_STAR) OutlinedButton(onClick = { state.cancelAlign() },
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp).heightIn(min = 48.dp)) { Label(t("Cancel")) }
        }
    }
}

/** Shows the time the sky is drawn for; tapping it opens the time-travel bar. Highlighted when not live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockChip(state: SkyState, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = if (state.live) c.surface else c.secondary,
        modifier = Modifier.heightIn(min = 48.dp)) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(if (state.live) formatClock(state.timeMillis) else formatMillis(state.timeMillis),
                color = if (state.live) c.onSurface else c.onPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 14.dp))
        }
    }
}

@Composable
private fun TimeBar(state: SkyState) {
    val pad = PaddingValues(horizontal = 2.dp)
    val big = Modifier.heightIn(min = 48.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((label, ms) in listOf("−1 d" to -86_400_000L, "−1 h" to -3_600_000L, "+1 h" to 3_600_000L, "+1 d" to 86_400_000L)) {
            OutlinedButton(onClick = { state.shiftTime(ms) }, modifier = big.weight(1f), contentPadding = pad) { Label(t(label)) }
        }
        Button(onClick = { state.live = true }, enabled = !state.live, modifier = big.weight(1f), contentPadding = pad,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Label(t("Now")) }
    }
}

@Composable
private fun AlignHint(state: SkyState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Text(t("To get directions to %s, point the telescope at a bright star near it, tap Align, then tap that star.").format(state.target?.name),
            color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun GuidancePanel(state: SkyState) {
    val (dAlt, dAz, sep) = state.guidance() ?: return
    val onTarget = sep < min(1.0, state.fovDeg * 0.05)
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = c.surface) {
        Column(Modifier.padding(16.dp)) {
            Text((if (onTarget) t("On target: %s") else t("Move to %s")).format(state.target?.name),
                color = c.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Readout(if (dAlt >= 0) "↑" else "↓", dAlt, "altitude")
                Readout(if (dAz >= 0) "→" else "←", dAz, "azimuth")
            }
            Text(t("%.1f° to go · re-align if the target drifts").format(sep), color = c.onSurface, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Readout(arrow: String, value: Double, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$arrow ${dm(abs(value))}", color = MaterialTheme.colorScheme.onSurface, fontSize = 30.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(t(label), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}

@Composable
private fun Toolbar(state: SkyState, onAsk: (() -> Unit)?, onSearch: () -> Unit, onEvents: () -> Unit, onSky: () -> Unit) {
    val big = Modifier.heightIn(min = 56.dp)
    val pad = PaddingValues(horizontal = 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { state.startAlign() }, modifier = big.weight(1.4f)) { Text(t("Align"), fontSize = 18.sp) }
            if (onAsk != null) OutlinedButton(onClick = onAsk, modifier = big.weight(0.9f), contentPadding = pad) { Label(t("Ask")) }
            OutlinedButton(onClick = { state.fovDeg = SkyState.FOV_STEPS.lastOrNull { it < state.fovDeg - 1e-6 } ?: state.fovDeg }, modifier = big.weight(0.7f)) { Text(t("+"), fontSize = 20.sp) }
            Box(big.weight(0.9f), contentAlignment = Alignment.Center) {
                Text("%.0f°".format(state.fovDeg), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(onClick = { state.fovDeg = SkyState.FOV_STEPS.firstOrNull { it > state.fovDeg + 1e-6 } ?: state.fovDeg }, modifier = big.weight(0.7f)) { Text(t("−"), fontSize = 20.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onSearch, modifier = big.weight(1f), contentPadding = pad) { Label(t("Find")) }
            OutlinedButton(onClick = onEvents, modifier = big.weight(1f), contentPadding = pad) { Label(t("Events")) }
            OutlinedButton(onClick = onSky, modifier = big.weight(0.8f), contentPadding = pad) { Label(t("Sky")) }
            OutlinedButton(onClick = { state.night = !state.night }, modifier = big.weight(1f), contentPadding = pad) { Label(t(if (state.night) "Day" else "Night")) }
            OutlinedButton(onClick = {
                state.mode = if (state.mode == PointingMode.COMPASS) PointingMode.MANUAL else PointingMode.COMPASS
            }, modifier = big.weight(1.1f), contentPadding = pad) { Label(t(if (state.mode == PointingMode.COMPASS) "Compass" else "Manual")) }
        }
    }
}

/** One-line button label that steps its font down (15 to 11 sp) until it fits, so no label is ever cut off. */
@Composable
private fun Label(text: String) = BoxWithConstraints(contentAlignment = Alignment.Center) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current
    val maxPx = constraints.maxWidth
    val style = remember(text, maxPx, base) {
        listOf(15, 14, 13, 12, 11).map { base.copy(fontSize = it.sp) }
            .firstOrNull { measurer.measure(text, it, maxLines = 1, softWrap = false).size.width <= maxPx } ?: base.copy(fontSize = 11.sp)
    }
    Text(text, style = style, maxLines = 1, softWrap = false)
}

@Composable
private fun SearchSheet(state: SkyState, catalog: Catalog?, moving: List<MovingObject>, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, catalog, moving) {
        val q = query.trim()
        // Empty box: what is above the horizon right now, so there is always something to pick.
        if (q.isEmpty()) (moving.filter { it.kind != MovingObject.Kind.SUN }.map { it.obj } + state.userObjects).filter { state.ray(it)[2] > 0 }
        else (moving.map { it.obj } + state.userObjects).filter { it.name.contains(q, ignoreCase = true) } + (catalog?.search(q).orEmpty())
    }
    val pick = { o: org.astrofixxer.astro.SkyObject -> state.target = o; onClose() }
    SheetFrame(t("Find"), onClose) {
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(t("M42, Orion Nebula, NGC 7000, Sirius, Jupiter…")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(pick) }))
        if (query.isBlank() && results.isNotEmpty()) Text(t("Visible now"), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp))
        if (query.isNotBlank() && results.isEmpty()) Text(t("Nothing found. Try a catalogue number like M31 or NGC 7000."),
            color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(results) { o ->
                val up = state.ray(o)[2] > 0
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { pick(o) }.padding(vertical = 10.dp)) {
                    Text(o.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
                    val sub = listOfNotNull(TYPE_NAMES[o.type]?.let { t(it) }, o.mag?.let { "mag %.1f".format(it) }, t(if (up) "up now" else "below horizon"),
                        o.otherNames.firstOrNull())
                    Text(sub.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EventsSheet(state: SkyState, events: List<EventItem>?, onClose: () -> Unit) {
    SheetFrame(t("Events"), onClose) {
        if (events == null) {
            Text(t("Working out what's coming up…"), color = MaterialTheme.colorScheme.onSurface)
            return@SheetFrame
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(events) { e ->
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                    state.live = false
                    state.timeMillis = jdToMillis(e.jd)
                    onClose()
                }.padding(vertical = 10.dp)) {
                    Text((if (e.rare) "★ " else "") + e.title, color = if (e.rare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp, fontWeight = if (e.rare) FontWeight.Bold else FontWeight.Normal)
                    Text(formatLocal(e.jd) + if (e.detail.isNotEmpty()) " · ${e.detail}" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SkyOptionsSheet(state: SkyState, onClose: () -> Unit, onLists: () -> Unit, onHelp: () -> Unit, onTutorial: () -> Unit) {
    SheetFrame(t("Sky & viewing"), onClose) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                val pad = PaddingValues(horizontal = 8.dp)
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onLists, modifier = Modifier.weight(1.5f).heightIn(min = 48.dp), contentPadding = pad) { Label(t("My objects & lists")) }
                    OutlinedButton(onClick = onHelp, modifier = Modifier.weight(0.8f).heightIn(min = 48.dp), contentPadding = pad) { Label(t("Help")) }
                    OutlinedButton(onClick = onTutorial, modifier = Modifier.weight(0.9f).heightIn(min = 48.dp), contentPadding = pad) { Label(t("Tutorial")) }
                }
            }
            item { Toggle(t("Constellation lines and names"), state.showConstellations) { state.showConstellations = it } }
            item { Toggle(t("Deep-sky objects"), state.showDeepSky) { state.showDeepSky = it } }
            item { Toggle(t("Milky Way"), state.showMilkyWay) { state.showMilkyWay = it } }
            item { Toggle(t("Constellation artwork"), state.showArt) { state.showArt = it } }
            item { Toggle(t("Atmosphere (daylight and twilight)"), state.showAtmosphere) { state.showAtmosphere = it } }
            item { Toggle(t("Alt/Az grid"), state.showGrid) { state.showGrid = it } }
            item {
                Stepper(t("Light pollution (Bortle)"), state.bortle.toString(),
                    onMinus = { state.bortle = (state.bortle - 1).coerceAtLeast(1) }, onPlus = { state.bortle = (state.bortle + 1).coerceAtMost(9) })
            }
            item {
                val all = Landscape.values()
                Stepper(t("Landscape"), t(state.landscape.label),
                    onMinus = { state.landscape = all[(state.landscape.ordinal + all.size - 1) % all.size] },
                    onPlus = { state.landscape = all[(state.landscape.ordinal + 1) % all.size] })
            }
            item {
                val toggle = { state.skyCulture = if (state.skyCulture == "indian") "modern" else "indian" }
                Stepper(t("Sky culture"), t(if (state.skyCulture == "indian") "Indian (Vedic)" else "Western"), onMinus = toggle, onPlus = toggle)
            }
            item {
                val langs = I18n.languages
                val next = { langs[(langs.indexOfFirst { it.first == I18n.language } + 1) % langs.size].first.also { I18n.language = it } }
                Stepper(t("Language"), langs.first { it.first == I18n.language }.second, onMinus = { next() }, onPlus = { next() })
            }
            item { LocationEditor(state) }
        }
    }
}

@Composable
private fun GuideBubble(state: SkyState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (state.guideListening || state.guideHeard.isNotEmpty()) Text(if (state.guideListening) t("Listening…") else "“${state.guideHeard}”",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                state.guideAnswer?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp) }
            }
            OutlinedButton(onClick = { state.guideAnswer = null; state.guideListening = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("×")) }
        }
    }
}

@Composable
private fun WatchNavigator(state: SkyState, catalog: Catalog?) {
    val list = state.watchLists.getOrNull(state.listIndex) ?: return
    val item = list.items.getOrNull(state.itemIndex)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { state.stepWatch(-1, catalog) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("‹")) }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val found = item != null && state.resolve(item.name, catalog) != null
                Text((item?.name ?: "") + if (item?.comment.isNullOrEmpty()) "" else " · ${item?.comment}",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!found) Text(t("Not in the catalogue; check the spelling"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Text("${listName(list.name)} · ${state.itemIndex + 1}/${list.items.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { state.stepWatch(1, catalog) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("›")) }
        }
    }
}

/** Items typed before any "Name:" go into a list the parser calls "default". */
private fun listName(name: String) = if (name == "default") t("My list") else name

@Composable
private fun ListsSheet(state: SkyState, catalog: Catalog?, onClose: () -> Unit) {
    var objectsText by remember { mutableStateOf(state.userObjectsText) }
    var listText by remember { mutableStateOf(state.watchListText) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    SheetFrame(t("My objects & watch lists"), onClose) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(t("My objects: one per line, name, RA, Dec"), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                OutlinedTextField(objectsText, { objectsText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    placeholder = { Text(t("Comet C/2026 X, 05:35:17, -05:23:28")) })
                Text(t("Watch lists: \"Name:\" starts a list; items separated by spaces or commas; (comment) after an item"),
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(listText, { listText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    placeholder = { Text(t("Tonight: M31 M33 \"Double Cluster\" (low in NE)")) })
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        state.userObjectsText = objectsText
                        state.watchListText = listText
                        errors = state.applyUserText(catalog)
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Save")) }
                    OutlinedButton(onClick = { objectsText = state.userObjectsText; listText = state.watchListText }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Discard")) }
                }
                for (e in errors) Text(e, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            item {
                val names = listOf(t("None")) + state.watchLists.map { listName(it.name) }
                Stepper(t("Active watch list"), names[state.listIndex + 1],
                    onMinus = { state.selectList(if (state.listIndex < 0) state.watchLists.size - 1 else state.listIndex - 1, catalog) },
                    onPlus = { state.selectList(if (state.listIndex + 1 >= state.watchLists.size) -1 else state.listIndex + 1, catalog) })
            }
        }
    }
}

/** Must be publicly reachable before the app is published: the GPL requires offering the source with the binary. */
const val SOURCE_URL = "https://github.com/Zenithquonta/astrofixxer-android"

private const val LICENCES = "AstroFixxer is free software under the GNU GPL v3. Source code: $SOURCE_URL. " +
    "Based on AstroHopper by Artyom Beilis (GPLv3). Deep-sky catalogue, names, meteor showers and comet orbits from Stellarium " +
    "(GPL-2.0-or-later). Sky cultures from Stellarium (CC BY-SA 4.0); constellation artwork under the Free Art License. " +
    "Star positions from the HYG database (CC BY-SA). Planet theory VSOP87 and position reduction by Greg Miller (public domain)."

private val HELP = listOf(
    "Setting up" to "Attach the phone flat on the telescope tube with its top edge pointing where the telescope points. Allow location so the sky matches your place and time.",
    "Aligning" to "Point the telescope at a bright star or planet near your target, tap Align, then tap that star on the screen. Re-align for each new target; phone sensors drift over a few minutes.",
    "Finding a target" to "Tap an object on the sky or use Find. Follow the arrows in the guidance panel until both numbers are close to zero.",
    "Compass and Manual" to "Compass uses the phone's compass. If the alignment star isn't on screen, switch to Manual and drag the sky sideways until it is, then Align.",
    "Zoom" to "Pinch or use + and −. Fainter stars and deep-sky objects appear as you zoom in.",
    "Events" to "Moon phases, eclipses, meteor showers, conjunctions, transits, occultations and bright comets for the next 60 days, all worked out on the phone. Tap one to show the sky at that time; Now returns to the present.",
    "Time travel" to "Tap the clock at the top right to step the sky by hours or days. While you are away from the present the clock turns pink; Now returns to the present.",
    "Night mode" to "Turns everything red to protect your dark adaptation. Also lower the screen brightness.",
    "My objects and watch lists" to "Add your own objects by RA/Dec, and lists of targets to step through with ‹ and › during a session.",
    "Offline" to "Everything works without internet. The star, deep-sky and constellation data come from Stellarium and ship inside the app.",
    "Licences and source" to LICENCES,
)

@Composable
private fun HelpSheet(onClose: () -> Unit) {
    SheetFrame(t("Help"), onClose) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(HELP) { (title, body) ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(t(title), color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(t(body), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                }
            }
        }
    }
}

private val ONBOARDING = listOf(
    "Attach the phone" to "Fix the phone flat on the telescope tube, with its top edge pointing where the telescope points.",
    "Align on a bright star" to "Point the telescope at an easy star or planet near what you want to find, for example Sirius for M41. Tap Align, then tap that star on the screen.",
    "Can't see the star?" to "The compass may be off near the metal tube. Switch to Manual and drag the sky sideways until the star is under the crosshair, then Align.",
    "Hop to the target" to "Tap your target and follow the arrows until they reach zero. Re-align for each new target.",
)

@Composable
private fun Onboarding(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(t("Quick start %d/%d").format(page + 1, ONBOARDING.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(t(ONBOARDING[page].first), color = MaterialTheme.colorScheme.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp))
            Text(t(ONBOARDING[page].second), color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
            Row(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Skip")) }
                OutlinedButton(onClick = { page-- }, enabled = page > 0, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Back")) }
                Button(onClick = { if (page < ONBOARDING.size - 1) page++ else onDone() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Label(t(if (page < ONBOARDING.size - 1) "Next" else "Start"))
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is the switch (one 56 dp target, read out as a switch by TalkBack).
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("‹")) }
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = onPlus, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("›")) }
    }
}

@Composable
private fun LocationEditor(state: SkyState) {
    var lat by remember { mutableStateOf("%.4f".format(java.util.Locale.ROOT, state.lat)) }
    var lon by remember { mutableStateOf("%.4f".format(java.util.Locale.ROOT, state.lon)) }
    val latOk = lat.toDoubleOrNull()?.let { it in -90.0..90.0 } == true
    val lonOk = lon.toDoubleOrNull()?.let { it in -180.0..180.0 } == true
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(t("Location (overrides GPS)"), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lat, { lat = it }, label = { Text(t("Latitude")) }, isError = !latOk, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(lon, { lon = it }, label = { Text(t("Longitude")) }, isError = !lonOk, singleLine = true, modifier = Modifier.weight(1f))
        }
        Button(onClick = { state.setManualLocation(lat.toDouble(), lon.toDouble()) }, enabled = latOk && lonOk,
            modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) { Text(t("Use this location")) }
        if (!latOk || !lonOk) Text(t("Latitude −90 to 90, longitude −180 to 180 (east positive)."), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun SheetFrame(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize().padding(top = 48.dp), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Close")) }
            }
            content()
        }
    }
}

fun hms(deg: Double): String {
    val total = kotlin.math.round(deg.mod(360.0) / 15 * 3600).toLong() % 86400
    return "%02dh%02dm%02ds".format(total / 3600, total / 60 % 60, total % 60)
}

fun dms(deg: Double): String {
    val total = kotlin.math.round(abs(deg) * 3600).toLong()
    return "%s%d°%02d′%02d″".format(if (deg < 0) "−" else "+", total / 3600, total / 60 % 60, total % 60)
}

fun dm(deg: Double): String {
    val total = kotlin.math.round(deg * 60).toLong()
    return "%d°%02d′".format(total / 60, total % 60)
}
