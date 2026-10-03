package org.astrofixxer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** PICK_STAR: asks for the object that will be centred in the telescope. Tapping it starts centring; it does not align anything. */
@Composable
internal fun PickStarPanel(state: SkyState, onAlignWithPhoto: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Panel {
        Text(t(if (state.checkingSecondStar) "Tap a second object, at least 10° from the first, to check the alignment" else "Tap the object you will centre in the telescope"),
            color = c.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(t("Choose a bright star, a planet, the Moon or any object you can centre, well above the horizon."), color = c.onSurface, fontSize = 14.sp)
        if (!state.hasCompass) Text(t("This phone has no compass. Drag the map until the sky matches what you see."), color = c.onSurfaceVariant, fontSize = 13.sp)
        state.alignNote?.let { Text(it.render(), color = c.error, fontSize = 15.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.cancelAlign() }, modifier = Modifier.heightIn(min = 48.dp)) { Label(t("Cancel")) }
            // A photo of the stars can align the telescope without picking a star (not for the second star of a check).
            if (onAlignWithPhoto != null && !state.checkingSecondStar) OutlinedButton(onClick = onAlignWithPhoto, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Align with a photo")) }
        }
    }
}

/**
 * CENTER_STAR: two clearly different steps. Moving the telescope is physical; dragging the map only lines the map up so
 * the star is under the +, and never moves the telescope.
 */
@Composable
internal fun CenterStarPanel(state: SkyState) {
    val c = MaterialTheme.colorScheme
    val star = state.centerStar ?: return
    Panel {
        Text(star.name, color = c.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Step("1", t("Centre %s in the eyepiece by moving the telescope.").format(star.name))
        Step("2", t("Drag the map to place %s under the +").format(star.name))
        Text(t("Step 1 moves the telescope. Step 2 only lines up the map on screen."), color = c.onSurfaceVariant, fontSize = 13.sp)
        state.alignNote?.let { Text(it.render(), color = c.error, fontSize = 15.sp) }
        Button(onClick = { state.confirmAlignment() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Label(t("Confirm alignment")) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.resetAdjustment() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Reset adjustment")) }
            OutlinedButton(onClick = { state.cancelAlign() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Cancel")) }
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, color = c.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = c.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

/** Shown after Confirm: what was computed, a warning for a large correction, and Retry or Done. */
@Composable
internal fun AlignResultCard(state: SkyState, onSolveWithCamera: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val r = state.alignResult ?: return
    Panel {
        if (r.fromPhoto) {
            Text(t("Aligned from a photo. Correction %.1f°.").format(r.correctionDeg), color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(t("The telescope now points where the photo says. Check with a star if you like."), color = c.onSurface, fontSize = 15.sp)
        } else if (r.check) {
            Text(t("Off by %.1f° at %s").format(r.correctionDeg, r.star?.name ?: ""), color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (r.refined) Text(t("The alignment now uses both stars."), color = c.onSurface, fontSize = 15.sp)
            r.note?.let { Text(it.render(), color = c.error, fontSize = 14.sp) }
        } else {
            Text(t("Aligned on %s. Correction %.1f°.").format(r.star?.name ?: "", r.correctionDeg), color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (r.large) Text(t("That's a large correction. Is %s really centred in the eyepiece?").format(r.star?.name ?: ""), color = c.error, fontSize = 15.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.fromPhoto) { if (onSolveWithCamera != null) OutlinedButton(onClick = onSolveWithCamera, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Solve again")) } }
            else OutlinedButton(onClick = { state.retryAlignment() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Retry")) }
            Button(onClick = { state.dismissAlignResult() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(t("Done")) }
        }
    }
}

/** A simple modal message with two buttons, drawn inside the screen (so it follows night mode and the audit like everything else). */
@Composable
internal fun ConfirmDialog(message: String, confirm: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(24.dp).clickable(enabled = false) {}, shape = RoundedCornerShape(16.dp), color = c.surface.copy(alpha = 1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.outline)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message, color = c.onSurface, fontSize = 17.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(dismiss) }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Label(confirm) }
                }
            }
        }
    }
}
