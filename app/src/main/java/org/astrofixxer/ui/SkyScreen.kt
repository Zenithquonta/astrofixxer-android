package org.astrofixxer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astrofixxer.astro.Catalog
import kotlin.math.abs
import kotlin.math.min

private val DayColors = darkColorScheme(
    primary = Color(0xFF00BFFF), onPrimary = Color.Black, surface = Color(0xE60A1018), onSurface = Color(0xFFDCE6F2),
    background = Color.Black, onBackground = Color(0xFFDCE6F2), secondary = Color(0xFFFF4FD8), error = Color(0xFFFFB347),
    outline = Color(0xFF3F6E8F),
)
private val NightColors = darkColorScheme(
    primary = Color(0xFFB00C0C), onPrimary = Color.Black, surface = Color(0xF0000000), onSurface = Color(0xFFB00C0C),
    background = Color.Black, onBackground = Color(0xFFB00C0C), secondary = Color(0xFF8A0A0A), error = Color(0xFFD01010),
    outline = Color(0xFF5A0707), outlineVariant = Color(0xFF3A0505), surfaceVariant = Color.Black, onSurfaceVariant = Color(0xFFB00C0C),
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
) {
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    val colors: ColorScheme = if (state.night) NightColors else DayColors
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            SkyCanvas(state, catalog, moving, Modifier.fillMaxSize(), art)
            InfoOverlay(state, Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = 260.dp))
            AlignChip(state, Modifier.align(Alignment.TopEnd).padding(12.dp))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.target != null && state.align == AlignState.ALIGNED) GuidancePanel(state)
                if (state.listIndex >= 0) WatchNavigator(state, catalog)
                if (state.guideListening || state.guideAnswer != null) GuideBubble(state)
                if (catalog == null) Text("Loading sky catalogue…", color = colors.onSurface, modifier = Modifier.padding(start = 8.dp))
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

@Composable
private fun InfoOverlay(state: SkyState, modifier: Modifier) {
    val t = state.target ?: return
    val c = MaterialTheme.colorScheme.onSurface
    val ray = state.ray(t)
    val altDeg = Math.toDegrees(kotlin.math.asin(ray[2]))
    val azDeg = (Math.toDegrees(kotlin.math.atan2(ray[0], ray[1])) + 360) % 360
    Column(modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(t.name, color = c, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (t.otherNames.isNotEmpty()) Text(t.otherNames.take(3).joinToString(" · "), color = c, fontSize = 12.sp)
        val details = listOfNotNull(TYPE_NAMES[t.type] ?: t.type, t.mag?.let { "mag %.1f".format(it) },
            t.sizeArcmin.takeIf { it > 0 }?.let { "%.0f′".format(it) })
        Text(details.joinToString(" · "), color = c, fontSize = 12.sp)
        Text("RA ${hms(t.ra)}  Dec ${dms(t.dec)}", color = c, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text("Alt ${dms(altDeg)}  Az ${dms(azDeg).trimStart('+')}", color = c, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        if (altDeg < 0) Text("Below the horizon", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun AlignChip(state: SkyState, modifier: Modifier) {
    val (label, color) = when (state.align) {
        AlignState.NOT_ALIGNED -> "Not aligned" to MaterialTheme.colorScheme.error
        AlignState.PICK_STAR -> "Tap the star the telescope points at" to MaterialTheme.colorScheme.primary
        AlignState.ALIGNED -> {
            val min = ((state.timeMillis - (state.alignedAtMillis ?: state.timeMillis)) / 60000).coerceAtLeast(0)
            (if (min >= 10) "Aligned $min min ago · re-align soon" else "Aligned ✓") to MaterialTheme.colorScheme.primary
        }
    }
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Text(label, color = color, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
    }
}

@Composable
private fun GuidancePanel(state: SkyState) {
    val (dAlt, dAz, sep) = state.guidance() ?: return
    val onTarget = sep < min(1.0, state.fovDeg * 0.05)
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = c.surface) {
        Column(Modifier.padding(16.dp)) {
            Text(if (onTarget) "On target: ${state.target?.name}" else "Move to ${state.target?.name}",
                color = c.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Readout(if (dAlt >= 0) "↑" else "↓", dAlt, "altitude")
                Readout(if (dAz >= 0) "→" else "←", dAz, "azimuth")
            }
            Text("%.1f° to go · re-align if the target drifts".format(sep), color = c.onSurface, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Readout(arrow: String, value: Double, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$arrow ${dm(abs(value))}", color = MaterialTheme.colorScheme.onSurface, fontSize = 30.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}

@Composable
private fun Toolbar(state: SkyState, onAsk: (() -> Unit)?, onSearch: () -> Unit, onEvents: () -> Unit, onSky: () -> Unit) {
    val big = Modifier.heightIn(min = 56.dp)
    val pad = PaddingValues(horizontal = 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { state.startAlign() }, modifier = big.weight(1.4f)) { Text("Align", fontSize = 18.sp) }
            if (onAsk != null) OutlinedButton(onClick = onAsk, modifier = big.weight(0.9f), contentPadding = pad) { Label("Ask") }
            OutlinedButton(onClick = { state.fovDeg = SkyState.FOV_STEPS.lastOrNull { it < state.fovDeg - 1e-6 } ?: state.fovDeg }, modifier = big.weight(0.7f)) { Text("+", fontSize = 20.sp) }
            Box(big.weight(0.9f), contentAlignment = Alignment.Center) {
                Text("%.0f°".format(state.fovDeg), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(onClick = { state.fovDeg = SkyState.FOV_STEPS.firstOrNull { it > state.fovDeg + 1e-6 } ?: state.fovDeg }, modifier = big.weight(0.7f)) { Text("−", fontSize = 20.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onSearch, modifier = big.weight(1f), contentPadding = pad) { Label("Find") }
            OutlinedButton(onClick = onEvents, modifier = big.weight(1f), contentPadding = pad) { Label("Events") }
            OutlinedButton(onClick = onSky, modifier = big.weight(0.8f), contentPadding = pad) { Label("Sky") }
            OutlinedButton(onClick = { state.night = !state.night }, modifier = big.weight(1f), contentPadding = pad) { Label(if (state.night) "Day" else "Night") }
            OutlinedButton(onClick = {
                state.mode = if (state.mode == PointingMode.COMPASS) PointingMode.MANUAL else PointingMode.COMPASS
            }, modifier = big.weight(1.1f), contentPadding = pad) { Label(if (state.mode == PointingMode.COMPASS) "Compass" else "Manual") }
            if (!state.live) Button(onClick = { state.live = true }, modifier = big.weight(0.8f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), contentPadding = pad) { Label("Now") }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, fontSize = 15.sp, maxLines = 1, softWrap = false)

@Composable
private fun SearchSheet(state: SkyState, catalog: Catalog?, moving: List<MovingObject>, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, catalog) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else (moving.map { it.obj } + state.userObjects).filter { it.name.startsWith(q, ignoreCase = true) } + (catalog?.search(q).orEmpty())
    }
    SheetFrame("Find", onClose) {
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("M42, Orion Nebula, NGC 7000, Sirius, Jupiter…") })
        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(results) { o ->
                val up = state.ray(o)[2] > 0
                Column(Modifier.fillMaxWidth().clickable { state.target = o; onClose() }.padding(vertical = 10.dp)) {
                    Text(o.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
                    val sub = listOfNotNull(TYPE_NAMES[o.type], o.mag?.let { "mag %.1f".format(it) }, if (up) "up now" else "below horizon",
                        o.otherNames.firstOrNull())
                    Text(sub.joinToString(" · "), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EventsSheet(state: SkyState, events: List<EventItem>?, onClose: () -> Unit) {
    SheetFrame("Events", onClose) {
        if (events == null) {
            Text("Working out what's coming up…", color = MaterialTheme.colorScheme.onSurface)
            return@SheetFrame
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(events) { e ->
                Column(Modifier.fillMaxWidth().clickable {
                    state.live = false
                    state.timeMillis = jdToMillis(e.jd)
                    onClose()
                }.padding(vertical = 10.dp)) {
                    Text((if (e.rare) "★ " else "") + e.title, color = if (e.rare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp, fontWeight = if (e.rare) FontWeight.Bold else FontWeight.Normal)
                    Text(formatLocal(e.jd) + if (e.detail.isNotEmpty()) " · ${e.detail}" else "",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SkyOptionsSheet(state: SkyState, onClose: () -> Unit, onLists: () -> Unit, onHelp: () -> Unit, onTutorial: () -> Unit) {
    SheetFrame("Sky & viewing", onClose) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onLists, modifier = Modifier.weight(1.4f).heightIn(min = 48.dp)) { Label("My objects & lists") }
                    OutlinedButton(onClick = onHelp, modifier = Modifier.weight(0.8f).heightIn(min = 48.dp)) { Label("Help") }
                    OutlinedButton(onClick = onTutorial, modifier = Modifier.weight(0.9f).heightIn(min = 48.dp)) { Label("Tutorial") }
                }
            }
            item { Toggle("Constellation lines and names", state.showConstellations) { state.showConstellations = it } }
            item { Toggle("Deep-sky objects", state.showDeepSky) { state.showDeepSky = it } }
            item { Toggle("Milky Way", state.showMilkyWay) { state.showMilkyWay = it } }
            item { Toggle("Constellation artwork", state.showArt) { state.showArt = it } }
            item { Toggle("Atmosphere (daylight and twilight)", state.showAtmosphere) { state.showAtmosphere = it } }
            item { Toggle("Alt/Az grid", state.showGrid) { state.showGrid = it } }
            item {
                Stepper("Light pollution (Bortle)", state.bortle.toString(),
                    onMinus = { state.bortle = (state.bortle - 1).coerceAtLeast(1) }, onPlus = { state.bortle = (state.bortle + 1).coerceAtMost(9) })
            }
            item {
                val all = Landscape.values()
                Stepper("Landscape", state.landscape.label,
                    onMinus = { state.landscape = all[(state.landscape.ordinal + all.size - 1) % all.size] },
                    onPlus = { state.landscape = all[(state.landscape.ordinal + 1) % all.size] })
            }
            item {
                val toggle = { state.skyCulture = if (state.skyCulture == "indian") "modern" else "indian" }
                Stepper("Sky culture", if (state.skyCulture == "indian") "Indian (Vedic)" else "Western", onMinus = toggle, onPlus = toggle)
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
                Text(if (state.guideListening) "Listening…" else "“${state.guideHeard}”",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
                state.guideAnswer?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp) }
            }
            OutlinedButton(onClick = { state.guideAnswer = null; state.guideListening = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("×") }
        }
    }
}

@Composable
private fun WatchNavigator(state: SkyState, catalog: Catalog?) {
    val list = state.watchLists.getOrNull(state.listIndex) ?: return
    val item = list.items.getOrNull(state.itemIndex)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { state.stepWatch(-1, catalog) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹") }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val found = item != null && state.resolve(item.name, catalog) != null
                Text((if (found) "" else "? ") + (item?.name ?: "") + if (item?.comment.isNullOrEmpty()) "" else " · ${item?.comment}",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 1, softWrap = false)
                Text("${list.name} · ${state.itemIndex + 1}/${list.items.size}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            OutlinedButton(onClick = { state.stepWatch(1, catalog) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("›") }
        }
    }
}

@Composable
private fun ListsSheet(state: SkyState, catalog: Catalog?, onClose: () -> Unit) {
    var objectsText by remember { mutableStateOf(state.userObjectsText) }
    var listText by remember { mutableStateOf(state.watchListText) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    SheetFrame("My objects & watch lists", onClose) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text("My objects: one per line, name, RA, Dec", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                OutlinedTextField(objectsText, { objectsText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    placeholder = { Text("Comet C/2026 X, 05:35:17, -05:23:28") })
                Text("Watch lists: \"Name:\" starts a list; items separated by spaces or commas; (comment) after an item",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(listText, { listText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    placeholder = { Text("Tonight: M31 M33 \"Double Cluster\" (low in NE)") })
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        state.userObjectsText = objectsText
                        state.watchListText = listText
                        errors = state.applyUserText(catalog)
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save") }
                    OutlinedButton(onClick = { objectsText = state.userObjectsText; listText = state.watchListText }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Discard") }
                }
                for (e in errors) Text(e, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            item {
                val names = listOf("None") + state.watchLists.map { it.name }
                Stepper("Active watch list", names[state.listIndex + 1],
                    onMinus = { state.selectList(if (state.listIndex < 0) state.watchLists.size - 1 else state.listIndex - 1, catalog) },
                    onPlus = { state.selectList(if (state.listIndex + 1 >= state.watchLists.size) -1 else state.listIndex + 1, catalog) })
            }
        }
    }
}

private val HELP = listOf(
    "Setting up" to "Attach the phone flat on the telescope tube with its top edge pointing where the telescope points. Allow location so the sky matches your place and time.",
    "Aligning" to "Point the telescope at a bright star or planet near your target, tap Align, then tap that star on the screen. Re-align for each new target; phone sensors drift over a few minutes.",
    "Finding a target" to "Tap an object on the sky or use Find. Follow the arrows in the guidance panel until both numbers are close to zero.",
    "Compass and Manual" to "Compass uses the phone's compass. If the alignment star isn't on screen, switch to Manual and drag the sky sideways until it is, then Align.",
    "Zoom" to "Pinch or use + and −. Fainter stars and deep-sky objects appear as you zoom in.",
    "Events" to "Moon phases, eclipses, meteor showers, conjunctions, transits, occultations and bright comets for the next 60 days, all worked out on the phone. Tap one to show the sky at that time; Now returns to the present.",
    "Night mode" to "Turns everything red to protect your dark adaptation. Also lower the screen brightness.",
    "My objects and watch lists" to "Add your own objects by RA/Dec, and lists of targets to step through with ‹ and › during a session.",
    "Offline" to "Everything works without internet. The star, deep-sky and constellation data come from Stellarium and ship inside the app.",
)

@Composable
private fun HelpSheet(onClose: () -> Unit) {
    SheetFrame("Help", onClose) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(HELP) { (title, body) ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(body, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
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
            Text("Quick start ${page + 1}/${ONBOARDING.size}", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 14.sp)
            Text(ONBOARDING[page].first, color = MaterialTheme.colorScheme.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp))
            Text(ONBOARDING[page].second, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
            Row(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label("Skip") }
                OutlinedButton(onClick = { page-- }, enabled = page > 0, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label("Back") }
                Button(onClick = { if (page < ONBOARDING.size - 1) page++ else onDone() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Label(if (page < ONBOARDING.size - 1) "Next" else "Start")
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹") }
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = onPlus, modifier = Modifier.heightIn(min = 48.dp)) { Text("›") }
    }
}

@Composable
private fun LocationEditor(state: SkyState) {
    var lat by remember { mutableStateOf("%.4f".format(java.util.Locale.ROOT, state.lat)) }
    var lon by remember { mutableStateOf("%.4f".format(java.util.Locale.ROOT, state.lon)) }
    val latOk = lat.toDoubleOrNull()?.let { it in -90.0..90.0 } == true
    val lonOk = lon.toDoubleOrNull()?.let { it in -180.0..180.0 } == true
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Location (overrides GPS)", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, isError = !latOk, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }, isError = !lonOk, singleLine = true, modifier = Modifier.weight(1f))
        }
        Button(onClick = { state.lat = lat.toDouble(); state.lon = lon.toDouble() }, enabled = latOk && lonOk,
            modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) { Text("Use this location") }
        if (!latOk || !lonOk) Text("Latitude −90 to 90, longitude −180 to 180 (east positive).", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun SheetFrame(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize().padding(top = 48.dp), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
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
