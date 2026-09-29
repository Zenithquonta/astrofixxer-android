package org.astrofixxer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import org.astrofixxer.astro.ApparentPosition
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.SkyObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** A planet, the Moon, the Sun or a comet, positioned for the current time. */
class MovingObject(val obj: SkyObject, val kind: Kind) { enum class Kind { SUN, MOON, PLANET, COMET } }

class Palette(
    val sky: Color, val star: Color, val label: Color, val lines: Color, val deepSky: Color,
    val horizon: Color, val cardinal: Color, val target: Color, val alignStar: Color, val crosshair: Color,
)

val DayPalette = Palette(
    sky = Color(0xFF03060C), star = Color(0xFFF2F6FF), label = Color(0xFFA9B8CC), lines = Color(0xFF2E4A6B),
    deepSky = Color(0xFF7FC7FF), horizon = Color(0xFF3B5A3A), cardinal = Color(0xFFE05A4E), target = Color(0xFF00E5FF),
    alignStar = Color(0xFFFF4FD8), crosshair = Color(0xFF00BFFF),
)
val NightPalette = Palette(
    sky = Color.Black, star = Color(0xFFD01010), label = Color(0xFFC01010), lines = Color(0xFF4A0505),
    deepSky = Color(0xFFC81414), horizon = Color(0xFF5A0707), cardinal = Color(0xFFD01010), target = Color(0xFFFF2020),
    alignStar = Color(0xFFC81414), crosshair = Color(0xFFFF2020),
)

/** Maps [east, north, up] directions to screen pixels with the web app's orthographic "camera" projection. */
class Projector(private val cam: Array<DoubleArray>, val width: Float, val height: Float, fovDeg: Double) {
    val fovH: Double
    val fovV: Double
    private val limX: Double
    private val limY: Double

    init {
        val ratio = width / height
        if (ratio < 1) { fovV = fovDeg; fovH = fovDeg * ratio } else { fovH = fovDeg; fovV = fovDeg / ratio }
        limX = sin(fovH / 2 * PI / 180)
        limY = sin(fovV / 2 * PI / 180)
    }

    /** On-screen radius in pixels of a circle [deg] degrees across its radius, at the centre of the view. */
    fun radiusPx(deg: Double): Float = (sin(deg * PI / 180) / (2 * limX) * width).toFloat()

    fun project(ray: DoubleArray): Offset? {
        val b = Pointing.bearing(ray, cam)
        if (b[2] <= 0) return null
        return Offset(((b[0] + limX) / (2 * limX) * width).toFloat(), ((1 - (b[1] + limY) / (2 * limY)) * height).toFloat())
    }
}

/** Faintest magnitudes drawn at a field of view, like Stellarium's zoom-dependent limit. */
/** Deep-sky objects labelled on the map; the rest are drawn as symbols and searchable. */
private val WELL_KNOWN = Regex("^(M|NGC|IC|C)\\d.*")

fun starMagLimit(fovDeg: Double) = when { fovDeg >= 100 -> 4.5; fovDeg >= 50 -> 5.3; fovDeg >= 25 -> 6.0; else -> 6.5 }
fun deepSkyMagLimit(fovDeg: Double) = when { fovDeg >= 100 -> 7.0; fovDeg >= 50 -> 8.5; fovDeg >= 25 -> 10.0; fovDeg >= 10 -> 11.5; fovDeg >= 4 -> 12.5; else -> 14.0 }

@Composable
fun SkyCanvas(
    state: SkyState,
    catalog: Catalog?,
    moving: List<MovingObject>,
    modifier: Modifier = Modifier,
    art: Map<String, ImageBitmap> = emptyMap(),
    /** Long press on an object: the screen shows its quick menu at that point. */
    onLongPress: (SkyObject, Offset) -> Unit = { _, _ -> },
) {
    val text = rememberTextMeasurer()
    val hits = remember { mutableListOf<Pair<Offset, SkyObject>>() }
    Canvas(
        modifier
            .pointerInput(state) {
                fun nearest(at: Offset): SkyObject? {
                    val best = hits.minByOrNull { (p, _) -> hypot(p.x - at.x, p.y - at.y) } ?: return null
                    return best.second.takeIf { hypot(best.first.x - at.x, best.first.y - at.y) <= 48.dp2px(density) }
                }
                detectTapGestures(
                    onLongPress = { at -> nearest(at)?.let { onLongPress(it, at) } },
                ) { tap ->
                    val obj = nearest(tap) ?: return@detectTapGestures
                    if (state.align == AlignState.PICK_STAR) state.alignOn(obj) else state.target = obj
                }
            }
            .pointerInput(state) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) state.fovDeg = (state.fovDeg / zoom).coerceIn(0.5, 120.0)
                    if (state.mode == PointingMode.MANUAL && pan.x != 0f) state.dragSky(pan.x, size.width.toFloat(), size.height.toFloat())
                    if (state.mode == PointingMode.FREE) state.panFree(pan.x, pan.y, size.width.toFloat(), size.height.toFloat())
                }
            },
    ) {
        hits.clear()
        drawSky(state, catalog, moving, text, hits, art)
    }
}

private fun Int.dp2px(density: Float) = this * density

/**
 * drawText throws when the text starts past the right/bottom edge, or so far left/up (zoomed in, objects project
 * tens of thousands of pixels away) that its layout constraints overflow; labels not near the screen are skipped.
 */
internal fun DrawScope.safeText(tm: TextMeasurer, s: String, at: Offset, style: TextStyle) {
    if (at.x > size.width - 8 || at.y > size.height - 8 || at.x < -size.width || at.y < -size.height) return
    drawText(tm, s, at, style, softWrap = false, maxLines = 1)
}

private fun DrawScope.drawSky(
    state: SkyState, catalog: Catalog?, moving: List<MovingObject>, text: TextMeasurer, hits: MutableList<Pair<Offset, SkyObject>>,
    art: Map<String, ImageBitmap>,
) {
    val pal = if (state.night) NightPalette else DayPalette
    val cam = state.camera()
    val proj = Projector(cam, size.width, size.height, state.fovDeg)
    val labelStyle = TextStyle(color = pal.label, fontSize = 11.sp)
    val sunAlt = moving.firstOrNull { it.kind == MovingObject.Kind.SUN }
        ?.let { Math.toDegrees(kotlin.math.asin(state.ray(it.obj)[2])) } ?: -90.0
    val atmosphere = state.showAtmosphere && !state.night
    drawRect(if (atmosphere) skyColor(sunAlt, pal.sky) else pal.sky)
    val magLoss = lightPollutionLoss(state.bortle) + if (state.showAtmosphere) twilightLoss(sunAlt) else 0.0
    val hideBelowHorizon = state.landscape != Landscape.NONE

    fun label(s: String, at: Offset, color: Color = pal.label, fontSp: Int = 11) {
        if (s == state.target?.name) return // the target gets its own highlighted label
        safeText(text, s, at + Offset(6f, -6f - fontSp), TextStyle(color = color, fontSize = fontSp.sp))
    }

    if (state.showMilkyWay) {
        val dark = ((9 - state.bortle) / 8f) * (if (state.showAtmosphere) (1 - twilightLoss(sunAlt) / 8).toFloat() else 1f)
        drawMilkyWay(state, proj, if (state.night) pal.star else Color(0xFFC8D4F0), dark)
    }
    if (state.showGrid) drawAltAzGrid(proj, pal)
    drawSkyMarkings(state, catalog, proj, text)
    if (state.showArt && catalog != null) drawConstellationArt(state, catalog, proj, art, if (state.night) pal.star else Color(0xFF9FB8E0))

    val center = Pointing.rayToRaDec(cam[2], state.timeMillis, state.lat, state.lon)
    val radius = hypot(proj.fovH, proj.fovV) / 2 * 1.1

    if (catalog != null && state.showConstellations) {
        for (c in catalog.constellations[state.skyCulture].orEmpty()) {
            for (poly in c.lines) for (k in 0 until poly.size - 1) {
                val a = catalog.starsByHip[poly[k]] ?: continue
                val b = catalog.starsByHip[poly[k + 1]] ?: continue
                val pa = proj.project(state.ray(a)) ?: continue
                val pb = proj.project(state.ray(b)) ?: continue
                drawLine(pal.lines, pa, pb, strokeWidth = 1.2f)
            }
            proj.project(Pointing.rayFromPos(c.ra, c.dec, state.timeMillis, state.lat, state.lon))?.let {
                safeText(text, c.name.uppercase(), it, labelStyle.copy(color = if (state.night) pal.lines else Color(0xFF5B7FA6), fontSize = 10.sp))
            }
        }
    }

    if (catalog != null) {
        val starLimit = starMagLimit(state.fovDeg) - magLoss
        val dsoLimit = deepSkyMagLimit(state.fovDeg) - magLoss
        for (o in catalog.near(center.first, center.second, radius, max(starLimit, dsoLimit))) {
            val isStar = o.type == "S"
            if (isStar && (o.mag ?: 99.0) > starLimit) continue
            if (!isStar && (!state.showDeepSky || o.type in state.hiddenDsoTypes || (o.mag ?: 99.0) > dsoLimit)) continue
            val ray = state.ray(o)
            if (hideBelowHorizon && ray[2] < 0) continue
            val p = proj.project(ray) ?: continue
            if (p.x < -20 || p.y < -20 || p.x > size.width + 20 || p.y > size.height + 20) continue
            val dim = if (ray[2] < 0) 0.35f else 1f
            if (isStar) {
                val r = (0.9 + max(0.0, starLimit + 0.5 - (o.mag ?: 6.0)) * 0.9).toFloat()
                val c = if (state.night || !state.showStarColours) pal.star else starColour(o.bv)
                drawCircle(c.copy(alpha = 0.25f * dim), r * 2.2f, p)
                drawCircle(c.copy(alpha = dim), r, p)
                if ((o.mag ?: 9.0) < starLimit - 3.0 && !o.name.startsWith("HIP")) label(o.name, p)
            } else {
                drawDeepSky(o, p, pal.deepSky.copy(alpha = dim))
                if ((o.mag ?: 99.0) < dsoLimit - 2.5 && WELL_KNOWN.matches(o.name)) label(o.name, p, pal.deepSky)
            }
            hits += p to o
        }
    }

    for (u in state.userObjects) {
        val ray = state.ray(u)
        if (hideBelowHorizon && ray[2] < 0) continue
        val p = proj.project(ray) ?: continue
        val d = 6f
        val diamond = androidx.compose.ui.graphics.Path().apply {
            moveTo(p.x - d, p.y); lineTo(p.x, p.y + d); lineTo(p.x + d, p.y); lineTo(p.x, p.y - d); close()
        }
        drawPath(diamond, pal.deepSky, style = Stroke(2f))
        label(u.name, p, pal.deepSky)
        hits += p to u
    }

    for (m in moving) {
        val ray = state.ray(m.obj)
        if (hideBelowHorizon && ray[2] < 0) continue
        val p = proj.project(ray) ?: continue
        val (color, r) = when (m.kind) {
            MovingObject.Kind.SUN -> (if (state.night) pal.star else Color(0xFFFFE08A)) to 9f
            MovingObject.Kind.MOON -> (if (state.night) pal.star else Color(0xFFE8E8DC)) to 8f
            MovingObject.Kind.PLANET -> (if (state.night) pal.star else Color(0xFFFFD27F)) to 4f
            MovingObject.Kind.COMET -> pal.deepSky to 3f
        }
        drawCircle(color, r, p)
        if (m.kind == MovingObject.Kind.COMET) drawLine(color, p, p + Offset(14f, -10f), strokeWidth = 2f)
        label(m.obj.name, p, color, 12)
        hits += p to m.obj
    }

    drawLandscape(state, proj, if (state.night) Color(0xFF0D0000) else Color(0xFF07100A), pal.horizon)
    drawHorizon(proj, pal, text, state.showCardinals)

    state.alignStar?.let { s -> if (state.align == AlignState.ALIGNED) proj.project(state.ray(s))?.let { drawCircle(pal.alignStar, 12f, it, style = Stroke(2f)) } }

    val mid = Offset(size.width / 2, size.height / 2)
    state.target?.let { t ->
        val tr = state.ray(t)
        val p = proj.project(tr)?.takeIf { it.x in 0f..size.width && it.y in 0f..size.height }
        if (p != null) {
            drawCircle(pal.target, 16f, p, style = Stroke(2.5f))
            drawLine(pal.target, mid, p, strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            safeText(text, t.name, p + Offset(6f, -20f), TextStyle(color = pal.target, fontSize = 14.sp))
        } else {
            // Off screen (beside or behind the view): arrow at the edge pointing toward the target.
            val b = Pointing.bearing(tr, cam)
            val ang = kotlin.math.atan2(-b[1], b[0]).toFloat()
            val edge = mid + Offset(cos(ang) * size.minDimension * 0.42f, sin(ang) * size.minDimension * 0.42f)
            rotate(Math.toDegrees(ang.toDouble()).toFloat(), edge) {
                drawLine(pal.target, edge - Offset(26f, 0f), edge, strokeWidth = 4f)
                drawLine(pal.target, edge, edge + Offset(-12f, -10f), strokeWidth = 4f)
                drawLine(pal.target, edge, edge + Offset(-12f, 10f), strokeWidth = 4f)
            }
        }
    }

    drawEyepieceCircle(state, proj)
    for (dir in listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))) {
        drawLine(pal.crosshair, mid + dir * 10f, mid + dir * 40f, strokeWidth = 2.5f)
    }
}

private fun DrawScope.drawDeepSky(o: SkyObject, p: Offset, color: Color) {
    val r = 6f
    when (o.type) {
        "Ga" -> rotate(-35f, p) { drawOval(color, p - Offset(r * 1.5f, r / 1.6f), androidx.compose.ui.geometry.Size(r * 3f, r * 1.25f), style = Stroke(1.5f)) }
        "Gc" -> {
            drawCircle(color, r, p, style = Stroke(1.5f))
            drawLine(color, p - Offset(r, 0f), p + Offset(r, 0f), strokeWidth = 1f)
            drawLine(color, p - Offset(0f, r), p + Offset(0f, r), strokeWidth = 1f)
        }
        "Oc" -> drawCircle(color, r, p, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
        else -> drawRect(color, p - Offset(r * 0.8f, r * 0.8f), androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f), style = Stroke(1.5f))
    }
}

internal fun horizonRay(azDeg: Double, altDeg: Double): DoubleArray {
    val a = azDeg * PI / 180
    val h = altDeg * PI / 180
    return doubleArrayOf(sin(a) * cos(h), cos(a) * cos(h), sin(h))
}

private fun DrawScope.drawHorizon(proj: Projector, pal: Palette, text: TextMeasurer, cardinals: Boolean = true) {
    var prev: Offset? = null
    for (az in 0..360 step 2) {
        val p = proj.project(horizonRay(az.toDouble(), 0.0))
        if (p != null && prev != null) drawLine(pal.horizon, prev, p, strokeWidth = 2f)
        prev = p
    }
    if (cardinals) for ((name, az) in listOf("N" to 0, "NE" to 45, "E" to 90, "SE" to 135, "S" to 180, "SW" to 225, "W" to 270, "NW" to 315)) {
        proj.project(horizonRay(az.toDouble(), 0.0))?.let {
            safeText(text, t(name), it + Offset(-6f, 4f), TextStyle(color = pal.cardinal, fontSize = if (name.length == 1) 16.sp else 12.sp))
        }
    }
}

private fun DrawScope.drawAltAzGrid(proj: Projector, pal: Palette) {
    val c = pal.lines.copy(alpha = 0.6f)
    for (alt in listOf(30, 60)) {
        var prev: Offset? = null
        for (az in 0..360 step 3) {
            val p = proj.project(horizonRay(az.toDouble(), alt.toDouble()))
            if (p != null && prev != null) drawLine(c, prev, p, strokeWidth = 0.8f)
            prev = p
        }
    }
    for (az in 0 until 360 step 30) {
        var prev: Offset? = null
        for (alt in 0..90 step 3) {
            val p = proj.project(horizonRay(az.toDouble(), alt.toDouble()))
            if (p != null && prev != null) drawLine(c, prev, p, strokeWidth = 0.8f)
            prev = p
        }
    }
}

/** Sun, Moon and planets as sky objects at the state's time. */
fun solarSystem(state: SkyState): List<MovingObject> = listOf(
    ApparentPosition.SUN to MovingObject.Kind.SUN, ApparentPosition.MOON to MovingObject.Kind.MOON,
    ApparentPosition.MERCURY to MovingObject.Kind.PLANET, ApparentPosition.VENUS to MovingObject.Kind.PLANET,
    ApparentPosition.MARS to MovingObject.Kind.PLANET, ApparentPosition.JUPITER to MovingObject.Kind.PLANET,
    ApparentPosition.SATURN to MovingObject.Kind.PLANET, ApparentPosition.URANUS to MovingObject.Kind.PLANET,
    ApparentPosition.NEPTUNE to MovingObject.Kind.PLANET,
).map { (body, kind) ->
    val (ra, dec) = state.bodyPosition(body)
    MovingObject(SkyObject(ApparentPosition.bodies[body], ra, dec, null, "P"), kind)
}
