package org.astrofixxer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.SkyObject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Everything about one object: names, facts, constellation, tonight's rise/transit/set and altitude graph, eyepiece view. */
@Composable
internal fun ObjectInfoSheet(state: SkyState, catalog: Catalog?, obj: SkyObject, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val night = nightStart(state.timeMillis)
    // A night's worth of positions takes a moment (Sun and Moon every 10 minutes), so it is worked out off the UI thread.
    val vis by produceState<Visibility?>(null, obj, night, state.lat, state.lon) {
        value = withContext(Dispatchers.Default) { visibilityTonight(obj, state.timeMillis, state.lat, state.lon) }
    }
    SheetFrame(obj.name, onClose) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                val names = obj.otherNames.take(12)
                if (names.isNotEmpty()) Text(names.joinToString(" · "), color = c.onSurfaceVariant, fontSize = 14.sp)
                val ray = state.ray(obj)
                val alt = Math.toDegrees(asin(ray[2]))
                val az = (Math.toDegrees(atan2(ray[0], ray[1])) + 360) % 360
                val facts = listOfNotNull(
                    t("Type") to (TYPE_NAMES[obj.type]?.let { t(it) } ?: obj.type),
                    obj.mag?.let { t("Magnitude") to "%.1f".format(it) },
                    obj.sizeArcmin.takeIf { it > 0 }?.let { t("Size") to "%.0f′".format(it) },
                    catalog?.constellationAt(obj.ra, obj.dec)?.let { t("Constellation") to catalog.constellationName(it) },
                    t("RA / Dec") to "${hms(obj.ra)}  ${dms(obj.dec)}",
                    t("Now") to (if (alt > 0) t("%d° up · %s").format(alt.roundToInt(), t(compass(az))) else t("Below the horizon")),
                )
                Column(Modifier.padding(vertical = 8.dp)) {
                    for ((label, value) in facts) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(label, color = c.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(0.4f))
                        Text(value, color = c.onSurface, fontSize = 14.sp, modifier = Modifier.weight(0.6f))
                    }
                }
            }
            item {
                Text(t("Tonight"), color = c.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                val v = vis
                if (v == null) Text(t("Working out tonight's positions…"), color = c.onSurfaceVariant, fontSize = 14.sp)
                else {
                    Text(riseSetSummary(v), color = c.onSurface, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    AltitudeGraph(v, state.timeMillis, state.night, Modifier.fillMaxWidth().height(170.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("— " + obj.name, color = c.primary, fontSize = 12.sp)
                        Text("┄ " + t("Moon"), color = c.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            item {
                Text(t("In the eyepiece"), color = c.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EyepiecePreview(obj, state.eyepieceFovDeg, state.setup.viewRotationDeg, state.setup.viewMirrored, Modifier.size(120.dp))
                    Text(t("Field %.1f° with the %.0f mm eyepiece").format(state.eyepieceFovDeg, state.eyepieceFocalMm) +
                        (obj.sizeArcmin.takeIf { it > 0 }?.let { "\n" + t("Object %.0f′ across").format(it) } ?: ""),
                        color = c.onSurface, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
            item {
                if (state.target?.name != obj.name) Button(onClick = { state.target = obj; onClose() },
                    modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp)) { Text(t("Set as target")) }
            }
            item {
                // Stars and planets above the horizon can be used to align the app with the telescope.
                if (state.canAlignOn(obj)) {
                    OutlinedButton(onClick = { state.beginCentering(obj); onClose() }, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                        Text(t("Align using this star"))
                    }
                    if (state.isLowForAlignment(obj)) Text(t("Low stars are harder to centre"), color = c.error, fontSize = 13.sp)
                }
                Box(Modifier.heightIn(min = 16.dp))
            }
        }
    }
}

private fun clock(millis: Long) = formatClock(millis)

private fun riseSetSummary(v: Visibility): String = when {
    v.alwaysUp -> t("Up all night; highest at %s (%d°)").format(clock(v.transit), v.maxAlt.roundToInt())
    v.neverUp -> t("Doesn't rise tonight")
    else -> listOfNotNull(
        v.rise?.let { t("Rises %s").format(clock(it)) },
        t("Highest %s (%d°)").format(clock(v.transit), v.maxAlt.roundToInt()),
        v.set?.let { t("Sets %s").format(clock(it)) },
    ).joinToString(" · ")
}

/**
 * Altitude from late afternoon to morning (16:00-08:00 local): the object as a solid curve, the Moon dashed, and the sky
 * shaded by the Sun's altitude (day, civil, nautical, astronomical twilight, night). The current time is marked.
 */
@Composable
internal fun AltitudeGraph(v: Visibility, nowMillis: Long, nightMode: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val text = rememberTextMeasurer()
    val shades = if (nightMode) listOf(Color(0xFF200000), Color(0xFF180000), Color(0xFF100000), Color(0xFF080000), Color.Black)
    else listOf(Color(0xFF3F6E9E), Color(0xFF2B4A74), Color(0xFF1C3052), Color(0xFF111D36), Color(0xFF070B18))
    val shown = v.samples.subList(24, 121) // 16:00 to 08:00 from a noon start, every 10 minutes
    Canvas(modifier.semantics { contentDescription = "Altitude tonight" }) {
        val left = 30f
        val bottom = size.height - 20f
        val w = size.width - left
        val h = bottom
        fun x(i: Int) = left + w * i / (shown.size - 1)
        fun y(alt: Double) = (bottom - (alt.coerceIn(0.0, 90.0) / 90.0) * h).toFloat()
        for (i in 0 until shown.size - 1) {
            val sun = shown[i].sunAlt
            val shade = when { sun > 0 -> shades[0]; sun > -6 -> shades[1]; sun > -12 -> shades[2]; sun > -18 -> shades[3]; else -> shades[4] }
            drawRect(shade, Offset(x(i), 0f), Size(x(i + 1) - x(i) + 1f, h))
        }
        for (a in listOf(30, 60)) {
            drawLine(c.outline.copy(alpha = 0.5f), Offset(left, y(a.toDouble())), Offset(size.width, y(a.toDouble())), strokeWidth = 1f)
            safeText(text, "$a°", Offset(2f, y(a.toDouble()) - 8f), TextStyle(color = c.onSurfaceVariant, fontSize = 10.sp))
        }
        drawLine(c.outline, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.5f)
        for (i in shown.indices step 12) { // every two hours
            safeText(text, clock(shown[i].millis).take(2), Offset(x(i) - 6f, bottom + 3f), TextStyle(color = c.onSurfaceVariant, fontSize = 10.sp))
        }
        fun curve(pick: (AltitudeSample) -> Double, color: Color, width: Float, dashed: Boolean) {
            for (i in 0 until shown.size - 1) {
                val a = pick(shown[i]); val b = pick(shown[i + 1])
                if (a <= 0 && b <= 0) continue
                drawLine(color, Offset(x(i), y(a)), Offset(x(i + 1), y(b)), strokeWidth = width,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null)
            }
        }
        curve({ it.moonAlt }, c.onSurfaceVariant, 1.5f, true)
        curve({ it.alt }, c.primary, 3f, false)
        val first = shown.first().millis
        val last = shown.last().millis
        if (nowMillis in first..last) {
            val nx = left + w * (nowMillis - first).toFloat() / (last - first)
            drawLine(c.secondary, Offset(nx, 0f), Offset(nx, bottom), strokeWidth = 2f)
        }
    }
}

/**
 * The eyepiece's circle of sky with the object drawn to scale in it (an ellipse for extended objects, a dot otherwise), turned
 * and mirrored like the eyepiece shows the sky, with a small tick where the zenith is (up on an upright map).
 */
@Composable
internal fun EyepiecePreview(obj: SkyObject, fovDeg: Double, viewRotationDeg: Int, viewMirrored: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Canvas(modifier.semantics { contentDescription = "Eyepiece view" }) {
        val r = size.minDimension / 2
        val mid = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.Black, r, mid)
        drawCircle(c.outline, r, mid, style = Stroke(2f))
        val objR = if (obj.sizeArcmin > 0) (obj.sizeArcmin / 60 / fovDeg * r).toFloat().coerceIn(2f, r * 1.5f) else 3f
        // An ellipse tilted -30° on an upright map: mirroring flips the tilt, then the view is turned clockwise.
        rotate((if (viewMirrored) 30f else -30f) + viewRotationDeg, mid) { drawOval(c.onSurface.copy(alpha = 0.8f), mid - Offset(objR, objR * 0.6f), Size(objR * 2, objR * 1.2f)) }
        // Zenith mark: up on the map, turned with the view.
        rotate(viewRotationDeg.toFloat(), mid) { drawLine(c.primary, mid + Offset(0f, -r), mid + Offset(0f, -r * 0.82f), strokeWidth = 5f) }
    }
}
