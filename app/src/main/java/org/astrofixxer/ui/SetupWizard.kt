package org.astrofixxer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The questions of the first-run wizard, in order. Only the ones the answers make necessary are shown. */
internal enum class WizardStep { TYPE, MOUNT, PLACEMENT, EDGE, ANGLE, ERECTING, SUMMARY }

/**
 * Steps for [s]: telescope, mount and phone placement always; then only the follow-ups that placement needs.
 * On the tube: which edge points forward. On the eyepiece: straight or right angle (a right angle also asks for the
 * edge, on the same step), then the prism. Camera forward needs nothing more. At most six steps.
 */
internal fun wizardSteps(s: TelescopeSetup): List<WizardStep> = buildList {
    add(WizardStep.TYPE); add(WizardStep.MOUNT); add(WizardStep.PLACEMENT)
    when (s.placement) {
        PhonePlacement.TUBE -> add(WizardStep.EDGE)
        PhonePlacement.EYEPIECE -> { add(WizardStep.ANGLE); add(WizardStep.ERECTING) }
        PhonePlacement.CAMERA_FORWARD -> {}
    }
    add(WizardStep.SUMMARY)
}

/** Full-screen first-run setup. [step] and [onStep] are held by the caller so the system Back button can go one step back. */
@Composable
internal fun SetupWizard(state: SkyState, step: Int, onStep: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf(state.setup) }
    val steps = wizardSteps(draft)
    val i = step.coerceIn(0, steps.lastIndex)
    val current = steps[i]
    val last = i == steps.lastIndex
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Step %d of %d").format(i + 1, steps.size), color = c.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { state.setupDone = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(t("Set up later"), color = c.primary, fontSize = 15.sp)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WizardContent(current, draft) { draft = it }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onStep(i - 1) }, enabled = i > 0, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t("Back")) }
                Button(onClick = {
                    if (last) { state.updateSetup(draft.withGuessedView()); state.setupDone = true } else onStep(i + 1)
                }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t(if (last) "Finish" else "Next")) }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
}

@Composable
private fun WizardContent(step: WizardStep, s: TelescopeSetup, change: (TelescopeSetup) -> Unit) {
    val art = Modifier.size(72.dp)
    when (step) {
        WizardStep.TYPE -> {
            Heading(t("What kind of telescope is it?"))
            OptionCard(t("Refractor"), t("A lens at the front; the eyepiece is at the back."), s.type == TelescopeType.REFRACTOR,
                { TelescopeTypeDrawing(TelescopeType.REFRACTOR, art) }) { change(s.copy(type = TelescopeType.REFRACTOR)) }
            OptionCard(t("Reflector (Newtonian)"), t("A mirror at the back; the eyepiece is on the side near the front."), s.type == TelescopeType.REFLECTOR,
                { TelescopeTypeDrawing(TelescopeType.REFLECTOR, art) }) { change(s.copy(type = TelescopeType.REFLECTOR)) }
            OptionCard(t("Something else"), t("Compact (Cassegrain) or any other design."), s.type == TelescopeType.OTHER,
                { TelescopeTypeDrawing(TelescopeType.OTHER, art) }) { change(s.copy(type = TelescopeType.OTHER)) }
        }
        WizardStep.MOUNT -> {
            Heading(t("What is the mount?"))
            OptionCard(t("Alt-azimuth"), t("Moves up-down and left-right: a fork, rocker box or Dobsonian base."), s.mount == MountType.ALT_AZ,
                { MountTypeDrawing(MountType.ALT_AZ, art) }) { change(s.copy(mount = MountType.ALT_AZ)) }
            OptionCard(t("Equatorial"), t("A tilted axis with a counterweight; moves in RA and Dec."), s.mount == MountType.EQUATORIAL,
                { MountTypeDrawing(MountType.EQUATORIAL, art) }) { change(s.copy(mount = MountType.EQUATORIAL)) }
            OptionCard(t("Other"), t("A tripod, a hand-held telescope or something else."), s.mount == MountType.OTHER,
                { MountTypeDrawing(MountType.OTHER, art) }) { change(s.copy(mount = MountType.OTHER)) }
        }
        WizardStep.PLACEMENT -> {
            Heading(t("Where is your phone mounted?"))
            OptionCard(t("Flat against the telescope tube"), t("The back of the phone rests on the tube."), s.placement == PhonePlacement.TUBE,
                { PlacementDrawing(PhonePlacement.TUBE, art) }) { change(s.copy(placement = PhonePlacement.TUBE)) }
            OptionCard(t("Camera facing along the telescope"), t("The rear camera points where the telescope points."), s.placement == PhonePlacement.CAMERA_FORWARD,
                { PlacementDrawing(PhonePlacement.CAMERA_FORWARD, art) }) { change(s.copy(placement = PhonePlacement.CAMERA_FORWARD)) }
            OptionCard(t("Attached to the eyepiece"), t("The rear camera looks through the eyepiece."), s.placement == PhonePlacement.EYEPIECE,
                { PlacementDrawing(PhonePlacement.EYEPIECE, art) }) { change(s.copy(placement = PhonePlacement.EYEPIECE)) }
        }
        WizardStep.EDGE -> {
            Heading(t("Which edge of the phone points toward the front of the telescope?"))
            Text(t("Hold the phone upright with the screen facing you."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            EdgePicker(s.edge, drawing = true) { change(s.copy(edge = it)) }
        }
        WizardStep.ANGLE -> {
            Heading(t("Does the eyepiece go straight in, or at a right angle (diagonal or Newtonian)?"))
            OptionCard(t("Straight"), null, s.eyepieceAngle == EyepieceAngle.STRAIGHT, { EyepieceAngleDrawing(EyepieceAngle.STRAIGHT, art) }) {
                change(s.copy(eyepieceAngle = EyepieceAngle.STRAIGHT))
            }
            OptionCard(t("Right angle"), t("A star diagonal, or the side eyepiece of a Newtonian."), s.eyepieceAngle == EyepieceAngle.RIGHT_ANGLE,
                { EyepieceAngleDrawing(EyepieceAngle.RIGHT_ANGLE, art) }) { change(s.copy(eyepieceAngle = EyepieceAngle.RIGHT_ANGLE)) }
            OptionCard(t("Not sure"), t("You can check it later with two stars."), s.eyepieceAngle == EyepieceAngle.UNSURE,
                { UnsureMark(art) }) { change(s.copy(eyepieceAngle = EyepieceAngle.UNSURE)) }
            if (s.eyepieceAngle == EyepieceAngle.RIGHT_ANGLE) {
                Heading(t("Which edge of the phone points toward the front of the telescope?"))
                EdgePicker(s.edge, drawing = true) { change(s.copy(edge = it)) }
            }
        }
        WizardStep.ERECTING -> {
            Heading(t("Do you use an image-erecting prism?"))
            Text(t("It turns the view the right way up. Most eyepieces on a star diagonal do not have one."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            OptionCard(t("Yes"), null, s.erecting == Erecting.YES, { ErectingDrawing(Erecting.YES, art) }) { change(s.copy(erecting = Erecting.YES)) }
            OptionCard(t("No"), null, s.erecting == Erecting.NO, { ErectingDrawing(Erecting.NO, art) }) { change(s.copy(erecting = Erecting.NO)) }
            OptionCard(t("Not sure"), null, s.erecting == Erecting.UNSURE, { UnsureMark(art) }) { change(s.copy(erecting = Erecting.UNSURE)) }
        }
        WizardStep.SUMMARY -> {
            Heading(t("All set"))
            MountingPreview(s, Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 140.dp))
            for (line in describeSetup(s)) Text(line, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            Text(t("Check orientation any time in Settings → Telescope & orientation."), color = MaterialTheme.colorScheme.primary, fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** A big "?" for the "not sure" answers. */
@Composable
private fun UnsureMark(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("?", color = MaterialTheme.colorScheme.primary, fontSize = 40.sp, fontWeight = FontWeight.Bold) }
}

/** What the setup says, in plain words, for the wizard's last step and the settings. */
internal fun describeSetup(s: TelescopeSetup): List<String> {
    val type = t(when (s.type) { TelescopeType.REFRACTOR -> "Refractor"; TelescopeType.REFLECTOR -> "Reflector (Newtonian)"; TelescopeType.OTHER -> "Something else" })
    val mount = t(when (s.mount) { MountType.ALT_AZ -> "Alt-azimuth"; MountType.EQUATORIAL -> "Equatorial"; MountType.OTHER -> "Other" })
    val edge = t(when (s.edge) { PhoneEdge.TOP -> "top edge"; PhoneEdge.BOTTOM -> "bottom edge"; PhoneEdge.LEFT -> "left edge"; PhoneEdge.RIGHT -> "right edge" })
    val phone = when (s.placement) {
        PhonePlacement.TUBE -> t("Phone: flat on the tube, %s to the front").format(edge)
        PhonePlacement.CAMERA_FORWARD -> t("Phone: rear camera points along the telescope")
        PhonePlacement.EYEPIECE -> when (s.eyepieceAngle) {
            EyepieceAngle.STRAIGHT -> t("Phone: on a straight eyepiece, rear camera looks through it")
            EyepieceAngle.RIGHT_ANGLE -> t("Phone: on a right-angle eyepiece, %s to the front").format(edge)
            EyepieceAngle.UNSURE -> t("Phone: on the eyepiece (angle not sure: check it with two stars)")
        }
    }
    val guess = s.viewGuess()
    val view = t("Eyepiece view: %s").format(describeView(guess.orientation.rotationDeg, guess.orientation.mirrored)) +
        if (guess.pleaseCheck) " " + t("(a first guess: please check)") else ""
    return listOf("$type · $mount", phone, view)
}

/**
 * The four edges of the phone as two rows of big buttons, optionally with a drawing of the phone that marks the chosen
 * edge and shows the arrow toward the front of the telescope.
 */
@Composable
internal fun EdgePicker(selected: PhoneEdge, drawing: Boolean, onSelect: (PhoneEdge) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (drawing) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PhoneEdgeDrawing(selected, Modifier.size(150.dp)) }
        for (pair in listOf(listOf(PhoneEdge.TOP, PhoneEdge.BOTTOM), listOf(PhoneEdge.LEFT, PhoneEdge.RIGHT))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (e in pair) SelectChip(t(when (e) { PhoneEdge.TOP -> "Top edge"; PhoneEdge.BOTTOM -> "Bottom edge"; PhoneEdge.LEFT -> "Left edge"; PhoneEdge.RIGHT -> "Right edge" }),
                    selected == e, Modifier.weight(1f)) { onSelect(e) }
            }
        }
    }
}

/**
 * [options] to pick one from, as big buttons that share the width (the chosen one is filled). When a label would not fit on one
 * line in its share of a narrow phone, the options are stacked full-width instead, so nothing is cut off.
 */
@Composable
internal fun <T> Segmented(options: List<Pair<String, T>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val sideBySide = labelsFit(options.map { it.first }, (maxWidth - 8.dp * (options.size - 1)) / options.size, 6.dp, 13)
        if (sideBySide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in options) SelectChip(label, selected == value, Modifier.weight(1f)) { onSelect(value) }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in options) SelectChip(label, selected == value, Modifier.fillMaxWidth()) { onSelect(value) }
        }
    }
}

/** One choice of a group: filled when chosen, at least 56 dp tall, read out as a radio button. */
@Composable
internal fun SelectChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(modifier.heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = onClick), shape = RoundedCornerShape(12.dp),
        color = if (selected) c.primary else c.surface.copy(alpha = 1f), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outline)) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Label(label, if (selected) c.onPrimary else c.onSurface)
        }
    }
}

/** A big option with an illustration, a label and a hint: at least 72 dp tall, outlined when chosen. */
@Composable
internal fun OptionCard(label: String, detail: String?, selected: Boolean, illustration: @Composable () -> Unit, onSelect: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).border(if (selected) 3.dp else 1.dp, if (selected) c.primary else c.outline, RoundedCornerShape(14.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        illustration()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label, color = c.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (detail != null) Text(detail, color = c.onSurfaceVariant, fontSize = 13.sp)
        }
        RadioButton(selected = selected, onClick = null)
    }
}
