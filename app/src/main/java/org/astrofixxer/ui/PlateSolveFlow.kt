package org.astrofixxer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.PhotoAlignment
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.astro.SolveResult
import kotlin.math.asin
import kotlin.math.roundToInt

/*
 * The guided camera plate-solve flow, a full-screen layer over the sky screen: confirm the arrangement, capture tips, the
 * live camera view, solving, and the result. It has no Android imports; everything phone-specific comes through
 * PlateSolveHost. A photo never changes the alignment or the map by itself: only the buttons on a solved result do, and
 * a failed, cancelled or unsolved photo leaves everything as it was.
 */

/** The moment now, for time-stamping a photo: the sky clock while it follows the real time, the real time otherwise. */
private fun clockNow(state: SkyState) = if (state.live) state.timeMillis else System.currentTimeMillis()

@Composable
internal fun PlateSolveFlow(state: SkyState, catalog: Catalog?, host: PlateSolveHost, model: PlateSolveModel, onChangePlacement: () -> Unit) {
    val c = MaterialTheme.colorScheme
    // The layer covers the whole sky screen; a touch that lands on it must never reach the sky map underneath.
    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = c.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Solve with camera"), color = c.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    model.calibrating?.let { Text(t("Calibrating the camera offset with %s").format(it.name), color = c.onSurfaceVariant, fontSize = 13.sp) }
                }
                OutlinedButton(onClick = { model.close() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Close")) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                when (model.step) {
                    SolveStep.ARRANGEMENT -> ArrangementStep(state, model, onChangePlacement)
                    SolveStep.PICK_STAR -> PickStarStep(state, catalog, model)
                    SolveStep.TIPS -> TipsStep(model)
                    SolveStep.LIVE -> LiveStep(state, model, host)
                    SolveStep.SOLVING -> SolvingStep(state, catalog, model, host)
                    SolveStep.RESULT -> ResultStep(state, catalog, model, host)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ small pieces

@Composable
private fun Bar(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = modifier)
}

@Composable
private fun Heading(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Warning(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
}

@Composable
private fun Small(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
}

/** Steps with plain content scroll, so nothing is ever cut off on a small phone or with big text. */
@Composable
private fun Scrolling(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ------------------------------------------------------------------------------------------------ a. arrangement

@Composable
private fun ArrangementStep(state: SkyState, model: PlateSolveModel, onChangePlacement: () -> Unit) {
    val s = state.setup
    // The eyepiece angle is only asked when the saved setup does not know it.
    // A Newtonian's eyepiece is always a right angle, so it only has the edge question.
    var askAngle by remember { mutableStateOf(s.effectiveEyepieceAngle == EyepieceAngle.UNSURE) }
    val newtonian = s.type == TelescopeType.REFLECTOR
    Column(Modifier.fillMaxSize()) {
        Scrolling(Modifier.weight(1f).fillMaxWidth()) {
            MountingPreview(s, Modifier.fillMaxWidth().height(120.dp))
            Body(describeSetup(s)[1])
            when (s.placement) {
                PhonePlacement.TUBE -> {
                    Heading(t("Solving is not possible with the phone flat on the tube"))
                    Body(t("The phone's camera faces the tube, so it cannot see the sky: a photo would only show the tube."))
                    Body(t("To solve with the camera, put the phone on the eyepiece, or fix it with its camera pointing along the telescope, and change the phone placement."))
                }
                PhonePlacement.EYEPIECE -> {
                    Heading(t("Centre the bright eyepiece circle in the camera view and focus on stars"))
                    if (newtonian) {
                        Body(t("On a Newtonian the eyepiece is on the side, at right angles to the tube."))
                        Body(t("Which edge points toward the front of the telescope?"))
                        EdgePicker(s.edge, drawing = false) { state.updateSetup(s.copy(edge = it)) }
                    } else if (askAngle) {
                        Body(t("Does the eyepiece go straight in, or at a right angle (diagonal or Newtonian)?"))
                        Segmented(listOf(t("Straight") to EyepieceAngle.STRAIGHT, t("Right angle") to EyepieceAngle.RIGHT_ANGLE, t("Not sure") to EyepieceAngle.UNSURE), s.eyepieceAngle) {
                            state.updateSetup(s.copy(eyepieceAngle = it))
                        }
                        if (s.eyepieceAngle == EyepieceAngle.RIGHT_ANGLE) {
                            Body(t("Which edge points toward the front of the telescope?"))
                            EdgePicker(s.edge, drawing = false) { state.updateSetup(s.copy(edge = it)) }
                        }
                        if (s.eyepieceAngle == EyepieceAngle.UNSURE) Small(t("You can solve a photo without knowing, but it cannot be applied to the alignment."))
                    }
                    if (state.telescopeFocalMm == 1200.0 && state.eyepieceFocalMm == 25.0) {
                        // Still the factory values: ask, because the expected size of the star pattern depends on them.
                        Body(t("Check these two numbers: they tell the app how big the star pattern should look."))
                        NumberField(t("Telescope focal length (mm)"), state.telescopeFocalMm, 100.0..10000.0, Modifier.fillMaxWidth()) { state.telescopeFocalMm = it }
                        NumberField(t("Eyepiece (mm)"), state.eyepieceFocalMm, 2.0..60.0, Modifier.fillMaxWidth()) { state.eyepieceFocalMm = it }
                    } else {
                        Body(t("Telescope %.0f mm, eyepiece %.0f mm: magnification ×%.0f").format(state.telescopeFocalMm, state.eyepieceFocalMm, state.telescopeFocalMm / state.eyepieceFocalMm))
                        Small(t("Change these in Sky, Telescope & orientation."))
                    }
                }
                PhonePlacement.CAMERA_FORWARD -> {
                    Heading(t("The camera looks along the telescope, beside the tube"))
                    val offset = state.cameraOffset
                    if (offset != null) {
                        Body(t("Camera offset: calibrated. The telescope is at %d%% across and %d%% down the photo.").format((offset.first * 100).roundToInt(), (offset.second * 100).roundToInt()))
                    } else {
                        Warning(t("Camera offset: not calibrated"))
                        Body(t("The camera and telescope don't point exactly the same way. Until the offset is calibrated, a photo tells where the camera points, not the telescope, so it cannot align the telescope."))
                    }
                    OutlinedButton(onClick = { model.message = null; model.step = SolveStep.PICK_STAR }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Label(t("Calibrate camera offset"))
                    }
                    if (offset != null) OutlinedButton(onClick = { state.resetCameraOffset() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Reset offset")) }
                }
            }
            model.message?.let { Body(it.render()) }
        }
        Bar {
            OutlinedButton(onClick = { model.close() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Cancel")) }
            if (s.placement == PhonePlacement.TUBE) Button(onClick = onChangePlacement, modifier = Modifier.weight(1.4f).heightIn(min = 56.dp)) { Label(t("Change phone placement")) }
            else Button(onClick = { model.message = null; model.step = SolveStep.TIPS }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Next")) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ calibrating: pick the star

@Composable
private fun PickStarStep(state: SkyState, catalog: Catalog?, model: PlateSolveModel) {
    // Bright stars that are up and high enough to centre; brightest first.
    val minute = state.timeMillis / 60000
    val stars = remember(catalog, minute) {
        catalog?.objects?.filter { it.type == "S" && (it.mag ?: 9.0) <= 2.5 && state.canAlignOn(it) && !state.isLowForAlignment(it) }
            ?.sortedBy { it.mag }?.take(8).orEmpty()
    }
    Column(Modifier.fillMaxSize()) {
        Scrolling(Modifier.weight(1f).fillMaxWidth()) {
            Heading(t("Pick the star you will centre in the eyepiece"))
            Body(t("You centre it in the eyepiece, then take a photo. Where the star lands on the photo is where the telescope points."))
            if (catalog == null) Body(t("Loading sky catalogue…"))
            else if (stars.isEmpty()) Warning(t("No bright star is high enough right now. Try again later."))
            for (o in stars) {
                val alt = Math.toDegrees(asin(state.ray(o)[2].coerceIn(-1.0, 1.0)))
                OutlinedButton(onClick = { model.calibrating = o; model.step = SolveStep.TIPS }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(t("%s · magnitude %.1f · %d° up").format(o.name, o.mag ?: 0.0, alt.roundToInt()), fontSize = 16.sp)
                }
            }
        }
        Bar { OutlinedButton(onClick = { model.back() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Back")) } }
    }
}

// ------------------------------------------------------------------------------------------------ b. tips

@Composable
private fun TipsStep(model: PlateSolveModel) {
    Column(Modifier.fillMaxSize()) {
        Scrolling(Modifier.weight(1f).fillMaxWidth()) {
            Heading(t("Before you take the photo"))
            model.calibrating?.let { Body(t("Centre %s in the eyepiece first.").format(it.name)) }
            for (tip in listOf(
                t("Use a dark site: city lights wash out the stars."),
                t("Keep the phone steady, and do not touch the telescope while the photo is taken."),
                t("Focus at infinity or on a bright star."),
                t("Keep the Moon and bright lights out of the view."),
                t("Use a 1 to 4 second exposure if the camera offers one."),
            )) Body("•  $tip")
        }
        Bar {
            OutlinedButton(onClick = { model.back() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Back")) }
            Button(onClick = { model.step = SolveStep.LIVE }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Next")) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ c. live view

/** The small + of the sky map, at a place on the picture given as fractions, with an optional label. */
@Composable
private fun TelescopeMarker(fx: Double, fy: Double, label: String?, night: Boolean) {
    val color = if (night) NightPalette.crosshair else DayPalette.crosshair
    Layout(content = {
        Canvas(Modifier.size(24.dp).semantics { contentDescription = "Telescope marker" }) { drawPlusMarker(Offset(size.width / 2, size.height / 2), color) }
        if (label != null) Text(label, color = color, fontSize = 13.sp, maxLines = 1, softWrap = false)
    }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val side = 24.dp.roundToPx()
        val plus = measurables[0].measure(Constraints.fixed(side, side))
        val text = measurables.getOrNull(1)?.measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            val cx = (fx * constraints.maxWidth).roundToInt()
            val cy = (fy * constraints.maxHeight).roundToInt()
            plus.place(cx - side / 2, cy - side / 2)
            if (text != null) {
                val x = (cx - text.width / 2).coerceIn(0, (constraints.maxWidth - text.width).coerceAtLeast(0))
                val below = cy + side / 2
                val y = if (below + text.height <= constraints.maxHeight) below else (cy - side / 2 - text.height).coerceAtLeast(0)
                text.place(x, y)
            }
        }
    }
}

/** Where the + goes on the live picture: the centre at the eyepiece, the calibrated spot beside the tube, nothing while calibrating or uncalibrated. */
private fun markerPosition(state: SkyState, model: PlateSolveModel): Pair<Double, Double>? = when {
    model.calibrating != null -> null
    state.setup.placement == PhonePlacement.EYEPIECE -> 0.5 to 0.5
    state.setup.placement == PhonePlacement.CAMERA_FORWARD -> state.cameraOffset
    else -> null
}

@Composable
private fun LiveStep(state: SkyState, model: PlateSolveModel, host: PlateSolveHost) {
    val scope = rememberCoroutineScope()
    var handle by remember { mutableStateOf<CameraHandle?>(null) }
    var problem by remember { mutableStateOf<CameraProblem?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val permission = host.permission
    // The camera permission is asked for only now, when the live view opens.
    LaunchedEffect(host) { if (host.hasCamera && host.permission == CameraPermission.UNKNOWN) host.requestPermission() }
    // A manual exposure the camera does not offer (any more) falls back to Auto.
    LaunchedEffect(handle) { val ok = handle?.info?.exposuresSec.orEmpty(); if (model.exposureSec != null && model.exposureSec !in ok) model.exposureSec = null }

    val pick = {
        host.pickFromGallery { r ->
            if (model.open && model.step == SolveStep.LIVE) when (r) {
                is GalleryResult.Picked -> {
                    // Nothing records where the telescope pointed when a gallery picture was taken, and its field of view is unknown.
                    model.shot = PhotoShot(r.image, fromCamera = false, hFovDeg = null, device = state.device.copyOf(), timeMillis = clockNow(state))
                    model.outcome = null; model.message = null
                    model.step = SolveStep.SOLVING
                }
                GalleryResult.Cancelled -> {}
                GalleryResult.Unreadable -> model.message = AlignNote("That picture could not be read. Try another one.")
            }
        }
    }
    val gallery: @Composable () -> Unit = {
        OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Use a photo from the gallery")) }
    }
    val back: @Composable RowScope.() -> Unit = {
        OutlinedButton(onClick = { model.back() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Back")) }
    }

    val live = host.hasCamera && permission == CameraPermission.GRANTED && problem == null
    if (!live) {
        // No live view: say why, and offer the gallery (and the settings, or another try, where they help).
        Column(Modifier.fillMaxSize()) {
            Scrolling(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !host.hasCamera -> { Heading(t("No camera on this phone")); Body(t("This phone has no camera the app can use. You can still solve a photo from the gallery.")) }
                    problem != null -> {
                        Heading(t("The camera is not working"))
                        Body(when (problem) {
                            CameraProblem.IN_USE -> t("Another app is using the camera. Close it, then try again.")
                            CameraProblem.NO_CAMERA -> t("This phone has no camera the app can use. You can still solve a photo from the gallery.")
                            CameraProblem.DISCONNECTED -> t("The camera was disconnected. Try again.")
                            else -> t("The camera failed. Try again, or use a photo from the gallery.")
                        })
                    }
                    permission == CameraPermission.UNKNOWN -> {
                        Heading(t("Camera permission"))
                        Body(t("Allow the camera when Android asks. It is used only to take the picture for solving; nothing is saved or sent anywhere."))
                    }
                    permission == CameraPermission.DENIED -> {
                        Heading(t("The camera permission was refused"))
                        Body(t("The app needs the camera to take the picture for solving. It is used for nothing else, and nothing is saved or sent anywhere. You can allow it, or use a photo from the gallery."))
                    }
                    else -> {
                        Heading(t("The camera permission is blocked"))
                        Body(t("You chose not to be asked again, so Android will not show the question any more. Allow the camera in the phone's settings for this app, or use a photo from the gallery."))
                    }
                }
                model.message?.let { Warning(it.render()) }
            }
            if (host.hasCamera && problem != null) OutlinedButton(onClick = { problem = null; attempt++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Try again")) }
            if (host.hasCamera && problem == null && permission == CameraPermission.DENIED)
                OutlinedButton(onClick = { host.requestPermission() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Ask again")) }
            if (host.hasCamera && problem == null && (permission == CameraPermission.DENIED || permission == CameraPermission.BLOCKED))
                OutlinedButton(onClick = { host.openAppSettings() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Open app settings")) }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { gallery() }
            Bar { back() }
        }
        return
    }

    val position = markerPosition(state, model)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        model.calibrating?.let { Body(t("Centre %s in the eyepiece, then take the photo.").format(it.name)) }
        if (model.calibrating == null && state.setup.placement == PhonePlacement.EYEPIECE) Body(t("Centre the bright eyepiece circle in the camera view and focus on stars"))
        if (position != null) Body(t("The + shows where the telescope points."))
        else if (model.calibrating == null) Small(t("The camera offset is not calibrated, so this photo will say where the camera points, not the telescope."))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(attempt) {
                host.LiveView(Modifier.fillMaxSize(), overlay = {
                    if (position != null) TelescopeMarker(position.first, position.second, if (state.setup.placement == PhonePlacement.CAMERA_FORWARD) t("Telescope") else null, state.night)
                }, onReady = { handle = it; problem = null }, onProblem = { handle = null; problem = it })
            }
        }
        handle?.info?.let { info ->
            Small(info.hFovDeg?.let { t("Camera field of view: %.0f°").format(it) } ?: t("Camera field of view unknown: 65° will be assumed"))
            if (info.exposuresSec.isNotEmpty()) {
                Segmented(listOf<Pair<String, Double?>>(t("Auto") to null) + info.exposuresSec.map { sec -> fmtSeconds(sec) to sec }, model.exposureSec) { model.exposureSec = it }
            }
        }
        model.message?.let { Warning(it.render()) }
        if (capturing) Small(t("Taking the photo… keep the phone still."))
        Bar {
            back()
            Button(onClick = {
                val cam = handle ?: return@Button
                capturing = true
                model.message = null
                scope.launch {
                    val device = state.device.copyOf()
                    val at = clockNow(state)
                    try {
                        val image = cam.capture(model.exposureSec)
                        val axis = state.setup.axis().vector
                        val moved = Pointing.angleBetweenDeg(Pointing.mvec(device, axis), Pointing.mvec(state.device, axis))
                        model.shot = PhotoShot(image, fromCamera = true, hFovDeg = cam.info.hFovDeg, device = device, timeMillis = at, movedDeg = moved)
                        model.outcome = null
                        model.step = SolveStep.SOLVING
                    } catch (e: CameraException) {
                        problem = e.problem
                    } finally {
                        capturing = false
                    }
                }
            }, enabled = handle != null && !capturing, modifier = Modifier.weight(1.6f).heightIn(min = 56.dp)) { Label(t("Take photo")) }
        }
        gallery()
    }
}

private fun fmtSeconds(sec: Double) = if (sec % 1.0 == 0.0) t("%d s").format(sec.toInt()) else t("%s s").format("%.1f".format(java.util.Locale.ROOT, sec))

// ------------------------------------------------------------------------------------------------ d. solving

@Composable
private fun SolvingStep(state: SkyState, catalog: Catalog?, model: PlateSolveModel, host: PlateSolveHost) {
    val shot = model.shot
    // Off the UI thread. Leaving this step (Cancel, Back) cancels the coroutine, which stops the search, and nothing is kept.
    LaunchedEffect(shot) {
        if (shot != null) {
            model.outcome = model.solve(state, catalog, host, shot)
            model.step = SolveStep.RESULT
        }
    }
    Column(Modifier.fillMaxSize()) {
        Scrolling(Modifier.weight(1f).fillMaxWidth()) {
            Heading(t("Solving…"))
            Body(t("Looking for stars in the photo and matching them to the sky. This can take up to 25 seconds. The photo stays on the phone."))
        }
        Bar { OutlinedButton(onClick = { model.back() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Cancel")) } }
    }
}

// ------------------------------------------------------------------------------------------------ e. result

/** What to do next after a failure, for each reason: concrete, and in plain words. */
private fun failureAdvice(o: SolveOutcome): String = when (o) {
    is SolveOutcome.Failed -> when (o.reason) {
        SolveResult.Reason.TOO_FEW_STARS -> t("Too few stars were found (%d). Try a darker, clearer part of the sky, focus on a bright star, and use a longer exposure (2 to 4 s) if the camera offers one. Cloud, haze and city lights hide the faint stars.").format(o.detectedStars)
        SolveResult.Reason.IMAGE_TOO_BRIGHT -> t("The picture is too bright: the sky is washed out or the stars are overexposed. Move away from lights, keep the Moon and streetlights out of the view, wait for a darker sky, and use a shorter exposure (1 s) or Auto.")
        SolveResult.Reason.STARS_TRAILED -> t("The stars are streaks, not dots. The phone or telescope moved, or the exposure was too long for a telescope that does not track. Hold the phone steady, do not touch the telescope, and use a shorter exposure of 1 or 2 s.")
        SolveResult.Reason.NO_MATCH -> t("%d stars were found, but they do not match the sky at the size expected. Check the telescope and eyepiece focal lengths (Sky, Telescope & orientation), that the phone camera sees the whole eyepiece circle, and that the stars are in focus. Then try again.").format(o.detectedStars)
    }
    is SolveOutcome.NoHint -> t("The photo covers only about %.1f° of sky, and the app does not know roughly where the telescope points, so a search could only guess. Align on a bright star first (or use a phone with a compass), then try again.").format(o.fieldWidthDeg)
    is SolveOutcome.Broke -> t("The solver could not run on this picture. Nothing was changed. Try another photo.")
    is SolveOutcome.Solved -> ""
}

@Composable
private fun ResultStep(state: SkyState, catalog: Catalog?, model: PlateSolveModel, host: PlateSolveHost) {
    val outcome = model.outcome
    val shot = model.shot
    Column(Modifier.fillMaxSize()) {
        Scrolling(Modifier.weight(1f).fillMaxWidth()) {
            if (outcome is SolveOutcome.Solved && shot != null) SolvedContent(state, catalog, model, outcome, shot)
            else if (outcome != null) {
                Heading(t("This photo could not be solved"))
                Warning(failureAdvice(outcome))
                Body(t("Nothing was changed: the alignment and the map are as they were."))
                if (outcome.plan?.assumedFov == true) Small(t("The camera's field of view was not known, so 65° was assumed. Use Take photo for a better guess."))
            }
            model.message?.let { Warning(it.render()) }
        }
        if (outcome is SolveOutcome.Solved && shot != null) {
            SolvedButtons(state, model, outcome, shot)
        } else {
            Bar { Button(onClick = { model.back() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Retry")) } }
        }
    }
}

@Composable
private fun SolvedContent(state: SkyState, catalog: Catalog?, model: PlateSolveModel, o: SolveOutcome.Solved, shot: PhotoShot) {
    val r = o.result
    val c = MaterialTheme.colorScheme
    Heading(t("Solved"))
    Text(t("The camera pointed at"), color = c.onSurfaceVariant, fontSize = 14.sp)
    Text("RA ${hms(r.raDeg)}  Dec ${dms(r.decDeg)}", color = c.primary, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    catalog?.constellationAt(r.raDeg, r.decDeg)?.let { Body(t("In the constellation %s").format(catalog.constellationName(it))) }
    Body(t("%d stars matched").format(r.matchedStars))
    Body(t("Scale: %.1f″ per pixel, the photo is %.1f° wide").format(r.scaleDegPerPx * 3600, r.scaleDegPerPx * r.imageWidth))
    if (r.mirrored) Body(t("Mirrored: the photo is a mirror image of the sky."))
    if (o.plan?.assumedFov == true) Small(t("The camera's field of view was not known, so 65° was assumed. Use Take photo for a better guess."))
    val star = model.calibrating
    if (star != null) {
        val off = PhotoAlignment.offsetOf(r, star.ra, star.dec)
        if (off != null) Body(t("%s is at %d%% across and %d%% down the photo.").format(star.name, (off.first * 100).roundToInt(), (off.second * 100).roundToInt()))
        else Warning(t("%s is not on the photo, so the offset cannot be found. Check that it was centred in the eyepiece and take the photo again.").format(star.name))
    } else {
        state.telescopeOnPhoto(r)?.let { (ra, dec) ->
            if (state.setup.placement == PhonePlacement.CAMERA_FORWARD) Body(t("The telescope points at RA %s  Dec %s").format(hms(ra), dms(dec)))
        }
    }
}

@Composable
private fun SolvedButtons(state: SkyState, model: PlateSolveModel, o: SolveOutcome.Solved, shot: PhotoShot) {
    val r = o.result
    val calibrating = model.calibrating
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (calibrating != null) {
            val ok = shot.fromCamera && PhotoAlignment.offsetOf(r, calibrating.ra, calibrating.dec) != null
            if (!shot.fromCamera) Warning(t("The camera offset needs a photo taken with the camera here, not one from the gallery."))
            Button(onClick = {
                val why = state.calibrateCameraOffset(r, shot, calibrating)
                if (why == null) {
                    model.calibrating = null; model.outcome = null; model.shot = null
                    model.message = AlignNote("Camera offset saved. Aim the telescope at the sky and take a photo to align.")
                    model.step = SolveStep.ARRANGEMENT
                } else model.message = why
            }, enabled = ok, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Label(t("Save camera offset")) }
        } else {
            val blocker = state.photoApplyBlocker(shot)
            blocker?.let { Warning(it.render()) }
            Button(onClick = {
                val why = state.applyPhotoAlignment(r, shot)
                if (why == null) model.close() else model.message = why
            }, enabled = blocker == null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("Apply to alignment"), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(t(if (state.setup.placement == PhonePlacement.CAMERA_FORWARD) "The telescope is at the + spot on the photo" else "The telescope is at the centre of the photo"),
                        fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            val target = state.telescopeOnPhoto(r) ?: (r.raDeg to r.decDeg)
            OutlinedButton(onClick = { state.showOnMap(target.first, target.second); model.close() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Show on map")) }
        }
        OutlinedButton(onClick = { model.back() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Retry")) }
    }
}
