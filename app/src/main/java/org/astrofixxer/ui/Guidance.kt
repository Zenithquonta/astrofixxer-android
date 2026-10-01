package org.astrofixxer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor

/** Below this many eyepiece fields away the target is "Close". */
internal const val CLOSE_FIELDS = 3.0

private val ARROW_GLYPHS = listOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")

/** The arrow character nearest to [angleDeg] (0 = up, clockwise). */
internal fun arrowGlyph(angleDeg: Double): String = ARROW_GLYPHS[(floor(((angleDeg % 360 + 360) % 360 + 22.5) / 45).toInt()) % 8]

/**
 * The movement in words, from the telescope's pointing only (so eyepiece rotation and mirroring can never change it).
 * Alt-az: one line like "↗ Up 3.2° · Right 5.1°". Equatorial: "RA: east 2.1°" and "Dec: north 1.3°".
 */
internal fun moveWords(h: MoveHint): List<String> {
    if (h.equatorial) return listOf(
        t(if (h.horizontal >= 0) "RA: east %.1f°" else "RA: west %.1f°").format(abs(h.horizontal)),
        t(if (h.vertical >= 0) "Dec: north %.1f°" else "Dec: south %.1f°").format(abs(h.vertical)),
    )
    val parts = listOfNotNull(
        if (abs(h.vertical) >= 0.05) t(if (h.vertical >= 0) "Up %.1f°" else "Down %.1f°").format(abs(h.vertical)) else null,
        if (abs(h.horizontal) >= 0.05) t(if (h.horizontal >= 0) "Right %.1f°" else "Left %.1f°").format(abs(h.horizontal)) else null,
    )
    return listOf((if (parts.isEmpty()) "" else arrowGlyph(h.arrowDeg) + " ") + (parts.joinToString(" · ").ifEmpty { "0.0°" }))
}

/** "Aligned ✓", or how long ago, in the chip and in the guidance details. */
@Composable
internal fun alignAgeText(state: SkyState): String {
    val min = ((state.timeMillis - (state.alignedAtMillis ?: state.timeMillis)) / 60000).coerceAtLeast(0)
    return when {
        min < 10 -> t("Aligned ✓")
        min < 60 -> t("Aligned %d min ago · re-align soon").format(min)
        min < 48 * 60 -> t("Aligned %d h ago · re-align soon").format(min / 60)
        else -> t("Aligned %d d ago · re-align soon").format(min / 1440)
    }
}

/**
 * After alignment, with a target: a big arrow for the telescope movement, the words, the distance and the target name.
 * Close (within [CLOSE_FIELDS] eyepiece fields) turns amber; On target (within half a field) shows the filled bullseye and
 * buzzes. Everything else sits behind "More".
 */
@Composable
internal fun GuidancePanel(state: SkyState, onSolveWithCamera: (() -> Unit)? = null) {
    val hint = state.moveHint() ?: return
    val fov = state.eyepieceFovDeg
    val onTarget = hint.separationDeg < fov / 2
    val close = !onTarget && hint.separationDeg < CLOSE_FIELDS * fov
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(onTarget) { if (onTarget && state.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    val accent = if (close) c.error else c.primary
    val name = state.target?.name ?: ""
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = c.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoveArrow(hint.arrowDeg, onTarget, accent, Modifier.padding(end = 12.dp).size(84.dp))
                Column(Modifier.weight(1f)) {
                    Text(t(if (onTarget) "On target: %s" else if (close) "Close to %s" else "Move to %s").format(name), color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (!onTarget) for (line in moveWords(hint)) Text(line, color = c.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(t("%.1f° to go").format(hint.separationDeg), color = c.onSurface, fontSize = 14.sp)
                }
            }
            OutlinedButton(onClick = { state.guidanceExpanded = !state.guidanceExpanded }, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.End)) { Label(t(if (state.guidanceExpanded) "Less" else "More")) }
            if (state.guidanceExpanded) GuidanceDetails(state, hint, onSolveWithCamera)
        }
    }
}

@Composable
private fun GuidanceDetails(state: SkyState, hint: MoveHint, onSolveWithCamera: (() -> Unit)?) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (hint.equatorial) {
                Readout(if (hint.horizontal >= 0) t("E") else t("W"), hint.horizontal, "ΔRA")
                Readout(if (hint.vertical >= 0) t("N") else t("S"), hint.vertical, "ΔDec")
            } else {
                val (dAlt, dAz, _) = state.guidance() ?: Triple(0.0, 0.0, 0.0)
                Readout(if (dAlt >= 0) "↑" else "↓", dAlt, "ΔAlt")
                Readout(if (dAz >= 0) "→" else "←", dAz, "ΔAz")
            }
        }
        Text((state.alignStar?.name ?: state.alignStarName)?.let { t("On %s · %s").format(it, alignAgeText(state)) } ?: alignAgeText(state),
            color = c.onSurface, fontSize = 14.sp)
        OutlinedButton(onClick = { state.startCheckWithAnotherStar() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Check with another star")) }
        EyepieceViewControls(state, suggest = false)
        if (onSolveWithCamera != null) OutlinedButton(onClick = onSolveWithCamera, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Label(t("Solve with camera")) }
    }
}

/**
 * The direction to move the telescope as a big arrow (0 = up, clockwise), or the filled bullseye when on target.
 * The arrow is in telescope directions (up, right), never turned by the eyepiece view.
 */
@Composable
private fun MoveArrow(angleDeg: Double, onTarget: Boolean, color: Color, modifier: Modifier) {
    val ring = MaterialTheme.colorScheme.outline
    Canvas(modifier.semantics { contentDescription = if (onTarget) "On target" else "Move arrow" }) {
        val r = size.minDimension / 2
        val mid = Offset(size.width / 2, size.height / 2)
        for (k in 1..3) drawCircle(ring, r * k / 3, mid, style = Stroke(1.5f))
        if (onTarget) {
            drawCircle(color, r / 3, mid)
        } else {
            rotate(angleDeg.toFloat(), mid) {
                val shaft = r * 0.55f
                val head = r * 0.42f
                drawLine(color, Offset(mid.x, mid.y + shaft), Offset(mid.x, mid.y - shaft + head * 0.4f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(mid.x, mid.y - shaft - head * 0.2f)
                    lineTo(mid.x - head * 0.75f, mid.y - shaft + head * 0.8f)
                    lineTo(mid.x + head * 0.75f, mid.y - shaft + head * 0.8f)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}

@Composable
private fun Readout(arrow: String, value: Double, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // One line only: if it ever stops fitting, the UI audit reports it as clipped instead of it wrapping silently.
        Text("$arrow ${dm(abs(value))}", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}
