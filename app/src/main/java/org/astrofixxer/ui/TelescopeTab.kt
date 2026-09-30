package org.astrofixxer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sky & viewing, "Telescope & orientation": what the telescope and phone are, where the phone sits, the eyepiece and its
 * view, and the check. Mounting changes (where the phone sits) break an alignment and say so; everything else does not.
 */
@Composable
internal fun TelescopeSettings(state: SkyState, onCheckOrientation: () -> Unit, onSolveWithCamera: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val s = state.setup
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(t("Telescope"))
        Segmented(listOf(t("Refractor") to TelescopeType.REFRACTOR, t("Reflector") to TelescopeType.REFLECTOR, t("Other") to TelescopeType.OTHER), s.type) {
            state.updateSetup(s.copy(type = it))
        }
        SectionTitle(t("Mount"))
        Segmented(listOf(t("Alt-Az") to MountType.ALT_AZ, t("Equatorial") to MountType.EQUATORIAL, t("Other") to MountType.OTHER), s.mount) {
            state.updateSetup(s.copy(mount = it))
        }

        SectionTitle(t("Where is the phone?"))
        Choice(t("Flat against the tube"), null, s.placement == PhonePlacement.TUBE) { state.updateSetup(s.copy(placement = PhonePlacement.TUBE)) }
        Choice(t("Camera facing along the telescope"), null, s.placement == PhonePlacement.CAMERA_FORWARD) { state.updateSetup(s.copy(placement = PhonePlacement.CAMERA_FORWARD)) }
        Choice(t("Attached to the eyepiece"), null, s.placement == PhonePlacement.EYEPIECE) { state.updateSetup(s.copy(placement = PhonePlacement.EYEPIECE)) }
        when (s.placement) {
            PhonePlacement.TUBE -> {
                Text(t("Which edge points toward the front of the telescope?"), color = c.onSurface, fontSize = 15.sp)
                EdgePicker(s.edge, drawing = false) { state.updateSetup(s.copy(edge = it)) }
            }
            PhonePlacement.EYEPIECE -> {
                Text(t("Does the eyepiece go straight in, or at a right angle (diagonal or Newtonian)?"), color = c.onSurface, fontSize = 15.sp)
                Segmented(listOf(t("Straight") to EyepieceAngle.STRAIGHT, t("Right angle") to EyepieceAngle.RIGHT_ANGLE, t("Not sure") to EyepieceAngle.UNSURE), s.eyepieceAngle) {
                    state.updateSetup(s.copy(eyepieceAngle = it))
                }
                if (s.eyepieceAngle == EyepieceAngle.RIGHT_ANGLE) {
                    Text(t("Which edge points toward the front of the telescope?"), color = c.onSurface, fontSize = 15.sp)
                    EdgePicker(s.edge, drawing = false) { state.updateSetup(s.copy(edge = it)) }
                }
                Text(t("Do you use an image-erecting prism?"), color = c.onSurface, fontSize = 15.sp)
                Segmented(listOf(t("Yes") to Erecting.YES, t("No") to Erecting.NO, t("Not sure") to Erecting.UNSURE), s.erecting) {
                    state.updateSetup(s.copy(erecting = it))
                }
            }
            PhonePlacement.CAMERA_FORWARD -> {}
        }
        MountingPreview(s, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 120.dp).padding(top = 4.dp))
        Text(t("Changing where the phone sits clears the alignment. The telescope type, mount and view never do."), color = c.onSurfaceVariant, fontSize = 13.sp)

        SectionTitle(t("Eyepiece"))
        Text(t("The eyepiece circle and \"On target\" use these."), color = c.onSurface, fontSize = 15.sp)
        NumberField(t("Telescope focal length (mm)"), state.telescopeFocalMm, 100.0..10000.0, Modifier.fillMaxWidth()) { state.telescopeFocalMm = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(t("Eyepiece (mm)"), state.eyepieceFocalMm, 2.0..60.0, Modifier.weight(1f)) { state.eyepieceFocalMm = it }
            NumberField(t("Apparent field (°)"), state.eyepieceAfovDeg, 20.0..120.0, Modifier.weight(1f)) { state.eyepieceAfovDeg = it }
        }
        Text(t("True field: %.2f° · magnification ×%.0f").format(state.eyepieceFovDeg, state.telescopeFocalMm / state.eyepieceFocalMm),
            color = c.primary, fontSize = 15.sp)
        Toggle(t("Vibrate when on target"), state.haptics) { state.haptics = it }

        SectionTitle(t("Eyepiece view"))
        EyepieceViewControls(state)
        Button(onClick = onCheckOrientation, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Label(t("Check orientation")) }
        Text(t("Checks which way the phone points along the telescope, and how the eyepiece shows the sky."), color = c.onSurfaceVariant, fontSize = 13.sp)
        if (onSolveWithCamera != null) {
            SectionTitle(t("Camera"))
            Button(onClick = onSolveWithCamera, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Label(t("Solve with camera")) }
            Text(t("Takes a photo of the stars and finds where the telescope points. The photo stays on the phone."), color = c.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
}

/**
 * "Match eyepiece view" (the sky map drawn like the eyepiece shows it; the + and all guidance stay as they are), the
 * current view, "Rotate view 90°" and "Mirror view", and a suggestion from the optics when it differs.
 */
@Composable
internal fun EyepieceViewControls(state: SkyState, suggest: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val s = state.setup
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Toggle(t("Match eyepiece view"), state.matchEyepieceView) { state.matchEyepieceView = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.updateSetup(s.copy(viewRotationDeg = (s.viewRotationDeg + 90) % 360)) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Rotate view 90°")) }
            if (s.viewMirrored) Button(onClick = { state.updateSetup(s.copy(viewMirrored = false)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Mirror view")) }
            else OutlinedButton(onClick = { state.updateSetup(s.copy(viewMirrored = true)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Mirror view")) }
        }
        Text(t("Eyepiece view: %s").format(describeView(s.viewRotationDeg, s.viewMirrored)), color = c.onSurface, fontSize = 14.sp)
        val guess = s.viewGuess()
        if (suggest && (guess.orientation.rotationDeg != s.viewRotationDeg || guess.orientation.mirrored != s.viewMirrored)) {
            OutlinedButton(onClick = { state.updateSetup(s.withGuessedView()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Label(t("Use suggested view: %s").format(describeView(guess.orientation.rotationDeg, guess.orientation.mirrored)))
            }
        }
    }
}
