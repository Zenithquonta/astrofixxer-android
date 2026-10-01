package org.astrofixxer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astrofixxer.astro.Dir
import org.astrofixxer.astro.Mounting
import org.astrofixxer.astro.ViewOrientation

/** What the phone axis is called in plain words. */
private fun axisName(label: String) = t(when (label) {
    "+Y" -> "top edge"; "−Y" -> "bottom edge"; "+X" -> "right edge"; "−X" -> "left edge"; "−Z" -> "rear camera"; else -> "screen side"
})

/** The setup that points the phone along [label], or null for the screen side, which no placement uses. */
private fun setupForAxis(current: TelescopeSetup, label: String): TelescopeSetup? = when (label) {
    "+Y" -> current.copy(placement = PhonePlacement.TUBE, edge = PhoneEdge.TOP)
    "−Y" -> current.copy(placement = PhonePlacement.TUBE, edge = PhoneEdge.BOTTOM)
    "+X" -> current.copy(placement = PhonePlacement.TUBE, edge = PhoneEdge.RIGHT)
    "−X" -> current.copy(placement = PhonePlacement.TUBE, edge = PhoneEdge.LEFT)
    "−Z" -> current.copy(placement = PhonePlacement.CAMERA_FORWARD)
    else -> null
}

private enum class Part { MENU, PHONE, VIEW }

/**
 * The guided check, opened from Telescope & orientation. Two independent checks:
 * the phone position (which way the phone points along the telescope, from the alignment stars) and the eyepiece view
 * (two nudges of the telescope and which way the star moved).
 */
@Composable
internal fun OrientationCheckSheet(state: SkyState, onClose: () -> Unit) {
    var part by remember { mutableStateOf(Part.MENU) }
    SheetFrame(t("Check orientation"), onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (part) {
                Part.MENU -> {
                    Text(t("Two quick checks. Do them once after fitting the phone, or whenever directions seem wrong."), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    Button(onClick = { part = Part.PHONE }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Label(t("Check the phone position")) }
                    Button(onClick = { part = Part.VIEW }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Label(t("Check the eyepiece view")) }
                }
                Part.PHONE -> PhoneCheck(state, onClose) { part = Part.MENU }
                Part.VIEW -> ViewCheck(state) { part = Part.MENU }
            }
        }
    }
}

@Composable
private fun PhoneCheck(state: SkyState, onClose: () -> Unit, back: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val samples = state.alignSamples
    val check = remember(samples) { Mounting.axisCheck(samples) }
    val currentVec = state.setup.axis()
    val current = check.ranked.first { it.axis.contentEquals(currentVec.vector) }
    val best = check.best
    Text(t("Which way does the phone point along the telescope?"), color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text(t("The app tells this from the stars you centre in the telescope. Use two stars at least 10° apart: align on one, then use Check with another star."),
        color = c.onSurface, fontSize = 15.sp)
    if (currentVec.needsCheck) Text(t("You said you are not sure how the eyepiece fits, so the phone axis is a guess."), color = c.error, fontSize = 14.sp)
    if (samples.isEmpty() || best == null) {
        Text(t("No stars yet. Align on a star first."), color = c.error, fontSize = 15.sp)
        Button(onClick = { onClose(); state.startAlign() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Label(t("Align now")) }
    } else {
        Text(t("Stars used: %d").format(samples.size), color = c.onSurfaceVariant, fontSize = 14.sp)
        if (!check.separationUsed) Text(t("You need two stars at least 10° apart for a reliable result. With one star, or two close ones, this is only a rough check."),
            color = c.error, fontSize = 14.sp)
        val fits = current.score <= best.score + 1.0
        if (fits) Text(t("Your setting fits: the %s points along the telescope (error %.1f°).").format(axisName(current.label), current.score), color = c.primary, fontSize = 16.sp)
        else {
            Text(t("Your stars fit the %s better than the %s (error %.1f° against %.1f°).").format(axisName(best.label), axisName(current.label), best.score, current.score),
                color = c.error, fontSize = 16.sp)
            val fix = setupForAxis(state.setup, best.label)
            if (fix != null) Button(onClick = { state.updateSetup(fix); back() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Label(t("Use the %s").format(axisName(best.label)))
            }
        }
        Text(t("Best fits: ") + check.ranked.take(3).joinToString(" · ") { "${axisName(it.label)} %.1f°".format(it.score) }, color = c.onSurfaceVariant, fontSize = 13.sp)
    }
    OutlinedButton(onClick = back, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Back")) }
}

@Composable
private fun ViewCheck(state: SkyState, back: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var up by remember { mutableStateOf<Dir?>(null) }
    var right by remember { mutableStateOf<Dir?>(null) }
    Text(t("Which way does the eyepiece show the sky?"), color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text(t("Find a star in the eyepiece. In a correct, upright view, nudging the telescope up moves the star down, and nudging it right moves the star left."),
        color = c.onSurface, fontSize = 15.sp)
    val u = up
    val r = right
    when {
        u == null -> {
            Text(t("1. Nudge the telescope up a little, toward the zenith. Which way did the star move in the eyepiece?"), color = c.onSurface, fontSize = 16.sp)
            DirButtons { up = it }
        }
        r == null -> {
            Text(t("2. Now nudge the telescope to the right. Which way did the star move in the eyepiece?"), color = c.onSurface, fontSize = 16.sp)
            DirButtons { right = it }
        }
        else -> {
            val result = Mounting.orientationFromNudges(u, r)
            if (result == null) {
                Text(t("Those two answers cannot both be right: up and right must move the star along different lines. Try again."), color = c.error, fontSize = 16.sp)
            } else {
                Text(t("Your eyepiece view is: %s.").format(describeView(result.rotationDeg, result.mirrored)), color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { applyView(state, result); back() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Label(t("Use this view")) }
            }
            OutlinedButton(onClick = { up = null; right = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Try again")) }
        }
    }
    OutlinedButton(onClick = back, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Back")) }
}

private fun applyView(state: SkyState, v: ViewOrientation) {
    state.updateSetup(state.setup.copy(viewRotationDeg = v.rotationDeg, viewMirrored = v.mirrored))
}

@Composable
private fun DirButtons(onPick: (Dir) -> Unit) {
    val labels = mapOf(Dir.UP to "Up", Dir.DOWN to "Down", Dir.LEFT to "Left", Dir.RIGHT to "Right")
    for (pair in listOf(listOf(Dir.UP, Dir.DOWN), listOf(Dir.LEFT, Dir.RIGHT))) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in pair) OutlinedButton(onClick = { onPick(d) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Label(t(labels.getValue(d))) }
        }
    }
}
