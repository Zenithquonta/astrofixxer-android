package org.astrofixxer.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astrofixxer.astro.Catalog
import kotlin.math.abs
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

private enum class Sheet { NONE, SEARCH, EVENTS, SKY, LISTS, HELP, INFO, CHECK }

internal val TYPE_NAMES = mapOf("S" to "Star", "Ga" to "Galaxy", "Oc" to "Open cluster", "Gc" to "Globular cluster", "Ne" to "Nebula", "P" to "Solar system", "C" to "Comet", "U" to "My object", "Con" to "Constellation", "Pos" to "Position")

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
    /** Starts listening for an AstroGuide question; null (no speech recogniser) makes Ask show the question suggestions instead. */
    onAsk: (() -> Unit)? = null,
    /** Hooks the system Back button (Android's BackHandler); the screen stays free of Android imports. */
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    /** Answers a typed or suggested AstroGuide question; the host adds speech. Default: answer on screen only. */
    onAskText: ((String) -> Unit)? = null,
    /** What the camera plate-solve flow needs from the phone (camera, photo picker, permission); null hides every "Solve with camera" button. */
    plateSolve: PlateSolveHost? = null,
    /** The plate-solve flow's state; the host does not need to pass one (tests do, to swap the solver). */
    solveModel: PlateSolveModel = remember { PlateSolveModel() },
    /** The in-app updater, only in preview and debug builds; null (Google Play builds) hides the "App updates" block. */
    updater: Updater? = null,
) {
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var skyTab by remember { mutableStateOf(0) }
    val openSolve: (() -> Unit)? = if (plateSolve == null) null else ({ sheet = Sheet.NONE; solveModel.start() })
    var wizardStep by remember { mutableStateOf(0) }
    var showTime by remember { mutableStateOf(false) }
    var infoObject by remember { mutableStateOf<org.astrofixxer.astro.SkyObject?>(null) }
    var quick by remember { mutableStateOf<Pair<org.astrofixxer.astro.SkyObject, androidx.compose.ui.geometry.Offset>?>(null) }
    val openInfo = { o: org.astrofixxer.astro.SkyObject -> infoObject = o; sheet = Sheet.INFO }
    val askText = onAskText ?: { q: String ->
        state.guideHeard = q
        state.guideAnswer = AstroGuide.answer(q, state, catalog, moving, events).speech
    }
    val colors: ColorScheme = if (state.night) NightColors else DayColors
    // Back closes the innermost thing that is open; with nothing open it leaves the app as usual.
    val guideOpen = state.guideListening || state.guideAnswer != null
    val wizardOpen = !state.setupDone
    backHandler(state.mountingChangedNotice || (wizardOpen && wizardStep > 0) || quick != null || (state.showOnboarding && !wizardOpen) || sheet != Sheet.NONE ||
        solveModel.open || state.aligning || guideOpen || showTime) {
        when {
            state.mountingChangedNotice -> state.mountingChangedNotice = false
            wizardOpen -> wizardStep--
            quick != null -> quick = null
            state.showOnboarding -> state.showOnboarding = false
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            solveModel.open -> solveModel.back()
            state.aligning -> state.cancelAlign()
            guideOpen -> { state.guideAnswer = null; state.guideListening = false }
            else -> showTime = false
        }
    }
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            SkyCanvas(state, catalog, moving, Modifier.fillMaxSize(), art) { o, at -> quick = o to at }
            // One row so the target card and the status chips share the width and never overlap on narrow phones.
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val guidanceOpen = state.guidanceExpanded && state.align == AlignState.ALIGNED && state.alignResult == null && state.target != null
                Box(Modifier.weight(1f)) { if (!guidanceOpen) InfoOverlay(state, Modifier) { infoObject = state.target; sheet = Sheet.INFO } }
                Column(Modifier.widthIn(max = 170.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Stacked above the chips, not beside them, so it takes nothing from their width or from the target card's.
                    SettingsButton { skyTab = TELESCOPE_TAB; sheet = Sheet.SKY }
                    AlignChip(state)
                    ClockChip(state) { showTime = !showTime }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.align) {
                    AlignState.PICK_STAR -> PickStarPanel(state, openSolve)
                    AlignState.CENTER_STAR -> CenterStarPanel(state)
                    AlignState.ALIGNED -> if (state.alignResult != null) AlignResultCard(state, openSolve) else if (state.target != null) GuidancePanel(state, openSolve)
                    AlignState.NOT_ALIGNED -> if (state.target != null) AlignHint(state)
                }
                // While centring a star only its panel is shown, so the + and the star stay clear of everything else.
                if (state.align != AlignState.CENTER_STAR) {
                    if (showTime || !state.live) TimeBar(state)
                    if (state.listIndex >= 0) WatchNavigator(state, catalog)
                    if (state.guideListening || state.guideAnswer != null) GuideBubble(state, askText)
                    if (catalog == null) Text(t("Loading sky catalogue…"), color = colors.onSurface, modifier = Modifier.padding(start = 8.dp))
                    Toolbar(state, onAsk,
                        onSearch = { sheet = Sheet.SEARCH },
                        onEvents = { sheet = Sheet.EVENTS },
                        onSky = { skyTab = 0; sheet = Sheet.SKY })
                }
            }
            when (sheet) {
                Sheet.SEARCH -> SearchSheet(state, catalog, moving, onClose = { sheet = Sheet.NONE }, onInfo = openInfo)
                Sheet.EVENTS -> EventsSheet(state, events) { sheet = Sheet.NONE }
                Sheet.SKY -> SkyOptionsSheet(state, catalog, onClose = { sheet = Sheet.NONE }, onLists = { sheet = Sheet.LISTS },
                    onHelp = { sheet = Sheet.HELP }, onTutorial = { sheet = Sheet.NONE; state.showOnboarding = true },
                    onCheckOrientation = { sheet = Sheet.CHECK }, onSolveWithCamera = openSolve, updater = updater, initialTab = skyTab)
                Sheet.CHECK -> OrientationCheckSheet(state) { sheet = Sheet.NONE }
                Sheet.LISTS -> ListsSheet(state, catalog) { sheet = Sheet.NONE }
                Sheet.HELP -> HelpSheet { sheet = Sheet.NONE }
                Sheet.INFO -> infoObject?.let { ObjectInfoSheet(state, catalog, it) { sheet = Sheet.NONE } }
                Sheet.NONE -> {}
            }
            quick?.let { (o, at) ->
                QuickMenu(o, at, onDismiss = { quick = null }, onTarget = { state.target = o },
                    onAlign = if (state.canAlignOn(o)) ({ state.beginCentering(o) }) else null,
                    onAdd = { state.addToWatchList(o.name, catalog) }, onInfo = { openInfo(o) })
            }
            // The camera plate-solve flow covers everything but the first-run setup.
            if (solveModel.open && plateSolve != null && !wizardOpen) PlateSolveFlow(state, catalog, plateSolve, solveModel, onChangePlacement = {
                solveModel.close(); skyTab = TELESCOPE_TAB; sheet = Sheet.SKY
            })
            // First launch: the setup wizard, then the tutorial.
            if (wizardOpen) SetupWizard(state, wizardStep) { wizardStep = it }
            else if (state.showOnboarding) Onboarding { state.showOnboarding = false }
            if (state.mountingChangedNotice) ConfirmDialog(
                t("The phone is mounted differently now, so the old alignment no longer fits. Align on a star again."),
                confirm = t("Align now"), dismiss = t("Later"),
                onConfirm = { state.mountingChangedNotice = false; sheet = Sheet.NONE; solveModel.close(); state.startAlign() },
                onDismiss = { state.mountingChangedNotice = false })
        }
    }
}

/** Catalogue designations such as "NGC6720", "PK063+13.1" or "Abell 2151", as opposed to names like "Ring Nebula". */
private val CATALOGUE_ID = Regex("^[A-Za-z]{1,6}[ -]?\\d.*")
private val WORD = Regex("[a-z\\p{L}&&[^A-Z]]{2}")

/** "Ring Nebula" or "47 Tuc" yes; "PN G063.1+13.9" or "Sh2-155" no. */
private fun isFriendlyName(n: String) = WORD.containsMatchIn(n) && !CATALOGUE_ID.matches(n)

@Composable
private fun InfoOverlay(state: SkyState, modifier: Modifier, onOpen: () -> Unit) {
    val obj = state.target ?: return
    val c = MaterialTheme.colorScheme
    val ray = state.ray(obj)
    val altDeg = Math.toDegrees(kotlin.math.asin(ray[2]))
    val azDeg = (Math.toDegrees(kotlin.math.atan2(ray[0], ray[1])) + 360) % 360
    // Tap the card for everything about the object (Object Info).
    Column(modifier.background(c.surface.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(10.dp)) {
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

/** The gear at the top right: opens Sky & viewing on the Telescope & orientation tab. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(48.dp).background(c.surface, CircleShape).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(AppIcons.Gear, contentDescription = t("Settings"), tint = c.onSurface, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun AlignChip(state: SkyState) {
    val (label, color) = when (state.align) {
        AlignState.NOT_ALIGNED -> t("Not aligned") to MaterialTheme.colorScheme.error
        AlignState.PICK_STAR -> t("Choose a star") to MaterialTheme.colorScheme.primary
        AlignState.CENTER_STAR -> t("Aligning…") to MaterialTheme.colorScheme.primary
        AlignState.ALIGNED -> alignAgeText(state) to MaterialTheme.colorScheme.primary
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Text(label, color = color, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
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
        Text(t("To get directions to %s, tap Align, tap a bright star near it, then centre that star in the telescope.").format(state.target?.name),
            color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun Toolbar(state: SkyState, onAsk: (() -> Unit)?, onSearch: () -> Unit, onEvents: () -> Unit, onSky: () -> Unit) {
    val big = Modifier.heightIn(min = 56.dp)
    val pad = PaddingValues(horizontal = 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { state.startAlign() }, modifier = big.weight(1.4f)) { Text(t("Align"), fontSize = 18.sp) }
            // Without speech recognition, Ask still opens AstroGuide with its question suggestions.
            OutlinedButton(onClick = onAsk ?: { state.guideHeard = ""; state.guideAnswer = t("Tap a question below.") },
                modifier = big.weight(0.9f), contentPadding = pad) { Label(t("Ask")) }
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
            OutlinedButton(onClick = { state.nextMode() }, enabled = !state.aligning, modifier = big.weight(1.1f), contentPadding = pad) {
                Label(t(when (state.mode) { PointingMode.COMPASS -> "Compass"; PointingMode.FREE -> "Free look" }))
            }
        }
    }
}

/**
 * Button label that is never cut off. It takes the largest size (15 down to 11 sp) that fits on one line; when none does,
 * the largest size that fits on two centred lines with no word broken in the middle; only when even that fails does it
 * clip at 11 sp. Two lines need about 40 dp, so a 48 dp button grows a little when its label wraps (a 56 dp one by about a dp).
 */
@Composable
internal fun Label(text: String, color: Color = Color.Unspecified) = BoxWithConstraints(contentAlignment = Alignment.Center) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current
    val maxPx = constraints.maxWidth
    val sizes = listOf(15, 14, 13, 12, 11)
    val fit = remember(text, maxPx, base) {
        sizes.map { base.copy(fontSize = it.sp) }.firstOrNull { measurer.measure(text, it, maxLines = 1, softWrap = false).size.width <= maxPx }
            ?.let { it to 1 }
            ?: sizes.map { base.copy(fontSize = it.sp, lineHeight = (it + 3).sp) }.firstOrNull {
                val r = measurer.measure(text, it, maxLines = 2, constraints = Constraints(maxWidth = maxPx))
                !r.hasVisualOverflow && (0 until r.lineCount - 1).all { line -> text[r.getLineEnd(line) - 1].isWhitespace() }
            }?.let { it to 2 }
            ?: (base.copy(fontSize = 11.sp) to 1)
    }
    val (style, lines) = fit
    Text(text, style = style, color = color, maxLines = lines, softWrap = lines > 1, textAlign = TextAlign.Center)
}

@Composable
private fun EventsSheet(state: SkyState, events: List<EventItem>?, onClose: () -> Unit) {
    var days by remember { mutableStateOf(Int.MAX_VALUE) }
    // Counted from the time the sky shows, so the filters follow time travel.
    val shown = events?.filter { days == Int.MAX_VALUE || jdToMillis(it.jd) - state.timeMillis <= days * 86_400_000L }
    SheetFrame(t("Events"), onClose) {
        // Filters stay above the list so they don't scroll away with it.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, d) in listOf("This week" to 7, "This month" to 31, "All" to Int.MAX_VALUE)) {
                if (days == d) Button(onClick = { days = d }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Label(t(label)) }
                else OutlinedButton(onClick = { days = d }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Label(t(label)) }
            }
        }
        EventList(state, shown, onClose)
    }
}

@Composable
private fun EventList(state: SkyState, events: List<EventItem>?, onClose: () -> Unit) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item { TonightCard(state) }
        if (events == null) item { Text(t("Working out what's coming up…"), color = MaterialTheme.colorScheme.onSurface) }
        items(events.orEmpty()) { e ->
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

@Composable
private fun GuideBubble(state: SkyState, onAskText: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (state.guideListening || state.guideHeard.isNotEmpty()) Text(if (state.guideListening) t("Listening…") else "“${state.guideHeard}”",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    state.guideAnswer?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp) }
                }
                OutlinedButton(onClick = { state.guideAnswer = null; state.guideListening = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("×")) }
            }
            // Suggestions: one tap asks the question, useful when speaking isn't possible.
            GuideSuggestions(onAskText)
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
    "Based on AstroHopper by Artyom Beilis (GPLv3, source: github.com/artyom-beilis/skyhopper). " +
    "Deep-sky catalogue, names, meteor showers, and comet and asteroid orbits come from Stellarium (GPL-2.0-or-later). " +
    "Sky cultures come from Stellarium: names and data are CC BY-SA 4.0. Modern constellation illustrations are under the Free Art License. " +
    "Indian sky culture illustrations are CC BY-SA 4.0. " +
    "The star list for plate solving comes from Stellarium's catalogues, built on ESA Gaia DR3 (CC BY-SA 3.0 IGO) and Hipparcos (ESA). " +
    "This work has made use of data from the European Space Agency (ESA) mission Gaia, processed by the Gaia Data Processing and Analysis Consortium (DPAC). " +
    "Star positions and colours come from the HYG database v3 (CC BY-SA). " +
    "The planet series VSOP87 and the position reduction (CPReduce) are by Greg Miller (public domain). " +
    "ISS and Tiangong orbits are downloaded from CelesTrak (celestrak.org) when you are online. " +
    "The Kotlin, AndroidX and Jetpack Compose libraries are Apache-2.0. " +
    "Privacy policy, full credits and licences: PRIVACY.md and NOTICE.md at $SOURCE_URL."

private val HELP = listOf(
    "Setting up" to "Attach the phone to the telescope and tell the app how in the setup wizard (or the gear at the top right, then Telescope & orientation): flat on the tube, camera facing along it, or on the eyepiece. Allow location so the sky matches your place and time.",
    "Aligning" to "Tap Align, then tap a bright star or planet near your target. Centre that star in the eyepiece by moving the telescope, drag the map until the star is under the +, and tap Confirm alignment. The app then says how big the correction was. Re-align for each new target; phone sensors drift over a few minutes.",
    "Finding a target" to "Tap an object on the sky or use Find. Follow the arrows in the guidance panel until both numbers are close to zero.",
    "Compass and dragging" to "Compass uses the phone's compass. If the alignment star isn't on screen, or the phone has no compass, drag the map while aligning until the star is under the +. Dragging never changes the alignment at any other time.",
    "Free look" to "The other pointing mode: the sky ignores the phone's sensors and you drag it in any direction, like a planetarium. Tap the Compass / Free look button to switch.",
    "Checking the alignment" to "In the guidance panel tap More, then Check with another star. Centre a second star and confirm: the app says how far off the alignment was and refines it using both stars.",
    "Eyepiece view" to "Eyepieces can show the sky upside down, reversed or turned. In Sky & viewing, Telescope & orientation, tap Check orientation to find out which, then turn on Match eyepiece view to draw the map the same way. Directions never change.",
    "Solve with camera" to "Put the phone on the eyepiece, or with its camera along the telescope, then tap Solve with camera (under More, in Sky & viewing, Telescope & orientation, or Align with a photo). Take a 1 to 4 second photo of the stars: the app finds where the telescope points, on the phone, and aligns to it if you ask. Photos are never saved or sent anywhere.",
    "Object info" to "Tap the target card at the top left, or long-press any object, for its names, constellation, rise and set times, a graph of its altitude tonight and how it looks in your eyepiece.",
    "Telescope settings" to "Tap the gear at the top right (or Sky & viewing, Telescope & orientation): enter the telescope's and eyepiece's focal lengths and the eyepiece's apparent field. The circle around the + is your eyepiece's view, and On target means the target is inside it. Equatorial mounts get directions in RA and Dec.",
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
    var query by remember { mutableStateOf("") }
    SheetFrame(t("Help"), onClose) {
        OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text(t("Search help")) })
        val shown = HELP.filter { (title, body) -> query.isBlank() || t(title).contains(query.trim(), true) || t(body).contains(query.trim(), true) }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(shown) { (title, body) ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(t(title), color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(t(body), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                }
            }
        }
    }
}

private val ONBOARDING = listOf(
    "Attach the phone" to "Fix the phone to the telescope the way you told the setup wizard. The wizard's picture shows how.",
    "Align on a bright star" to "Pick an easy star or planet near what you want to find, for example Sirius for M41. Tap Align, tap that star, then centre it in the telescope.",
    "Line up the map" to "The compass may be off near the metal tube. Drag the map until the star is under the +, then tap Confirm alignment. Dragging only lines up the map; it never moves the telescope.",
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
internal fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is the switch (one 56 dp target, read out as a switch by TalkBack).
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("‹")) }
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = onPlus, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("›")) }
    }
}

@Composable
internal fun SheetFrame(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    // Opaque: the sky screen's own text must not show through a sheet.
    Surface(Modifier.fillMaxSize().padding(top = 48.dp), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 1f)) {
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
