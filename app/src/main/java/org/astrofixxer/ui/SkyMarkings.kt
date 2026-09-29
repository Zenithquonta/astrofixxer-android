package org.astrofixxer.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.Pointing
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Colours of the sky markings (grids, lines, boundaries, eyepiece) for day and night mode. */
class MarkingPalette(val eqGrid: Color, val ecliptic: Color, val meridian: Color, val boundary: Color, val eyepiece: Color, val label: Color)

val DayMarkings = MarkingPalette(
    eqGrid = Color(0x8C3A6EA5), ecliptic = Color(0xFFC9A227), meridian = Color(0xFF9B7FE0), boundary = Color(0x8C5B7FA6),
    eyepiece = Color(0xFF00BFFF), label = Color(0xFF7FA3CC),
)
val NightMarkings = MarkingPalette(
    eqGrid = Color(0xFF5A0707), ecliptic = Color(0xFF9A0C0C), meridian = Color(0xFF7A0909), boundary = Color(0xFF4A0505),
    eyepiece = Color(0xFFFF2020), label = Color(0xFFC01010),
)

/** B−V colour index to a star colour (blue-white to orange-red), from the usual stellar colour table. */
private val STAR_COLOURS = listOf(
    -0.33 to Color(0xFF9BB0FF), -0.1 to Color(0xFFAABFFF), 0.0 to Color(0xFFCAD7FF), 0.3 to Color(0xFFF8F7FF),
    0.6 to Color(0xFFFFF4EA), 0.8 to Color(0xFFFFEDDE), 1.0 to Color(0xFFFFDAB5), 1.4 to Color(0xFFFFC682), 2.0 to Color(0xFFFF9D5C),
)

fun starColour(bv: Double?): Color {
    if (bv == null) return Color(0xFFF2F6FF)
    val (lo, hi) = STAR_COLOURS.zipWithNext().firstOrNull { (_, b) -> bv <= b.first } ?: return STAR_COLOURS.last().second
    if (bv <= lo.first) return lo.second
    return lerp(lo.second, hi.second, ((bv - lo.first) / (hi.first - lo.first)).toFloat())
}

private const val OBLIQUITY_J2000 = 23.4392911 * PI / 180

/** J2000 RA/Dec (degrees) of the point on the ecliptic at longitude [lonDeg]. */
fun eclipticToEquatorial(lonDeg: Double): Pair<Double, Double> {
    val l = lonDeg * PI / 180
    val ra = (Math.toDegrees(atan2(sin(l) * cos(OBLIQUITY_J2000), cos(l))) + 360) % 360
    return Pair(ra, Math.toDegrees(asin(sin(OBLIQUITY_J2000) * sin(l))))
}

/** Draws a line through projected points, breaking where a point is behind the view or the line would jump across it. */
private fun DrawScope.polyline(points: List<Offset?>, color: Color, width: Float, dashed: Boolean = false) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
    val maxJump = size.maxDimension * 0.5f
    for (i in 0 until points.size - 1) {
        val a = points[i] ?: continue
        val b = points[i + 1] ?: continue
        if (hypot(a.x - b.x, a.y - b.y) > maxJump) continue
        drawLine(color, a, b, strokeWidth = width, pathEffect = effect)
    }
}

/**
 * Stellarium's "Markings": equatorial grid (with hour labels along the equator), meridian, ecliptic and the IAU
 * constellation boundaries, each switched by its own toggle.
 */
fun DrawScope.drawSkyMarkings(state: SkyState, catalog: Catalog?, proj: Projector, text: TextMeasurer) {
    val m = if (state.night) NightMarkings else DayMarkings
    fun radec(ra: Double, dec: Double) = proj.project(Pointing.rayFromPos(ra, dec, state.timeMillis, state.lat, state.lon))

    if (state.showBoundaries && catalog != null) {
        for (edge in catalog.boundaries) {
            val p = edge.points
            polyline(List(p.size / 2) { radec(p[2 * it], p[2 * it + 1]) }, m.boundary, 1f, dashed = true)
        }
    }
    if (state.showEquatorialGrid) {
        for (dec in listOf(-60, -30, 0, 30, 60)) {
            polyline((0..360 step 3).map { radec(it.toDouble(), dec.toDouble()) }, m.eqGrid, if (dec == 0) 1.4f else 0.8f)
        }
        for (ra in 0 until 360 step 30) {
            polyline((-88..88 step 4).map { radec(ra.toDouble(), it.toDouble()) }, m.eqGrid, 0.8f)
            radec(ra.toDouble(), 0.0)?.let { safeText(text, "${ra / 15}h", it + Offset(4f, 2f), TextStyle(color = m.label, fontSize = 10.sp)) }
        }
    }
    if (state.showEcliptic) {
        polyline((0..360 step 2).map { val (ra, dec) = eclipticToEquatorial(it.toDouble()); radec(ra, dec) }, m.ecliptic, 1.3f)
    }
    if (state.showMeridian) {
        // North point, up through the zenith, down to the south point.
        val pts = (0..90 step 2).map { proj.project(horizonRay(0.0, it.toDouble())) } +
            (90 downTo 0 step 2).map { proj.project(horizonRay(180.0, it.toDouble())) }
        polyline(pts, m.meridian, 1.3f)
    }
}

/** The telescope's eyepiece field around the crosshair, sized from the telescope settings. */
fun DrawScope.drawEyepieceCircle(state: SkyState, proj: Projector) {
    val m = if (state.night) NightMarkings else DayMarkings
    val r = proj.radiusPx(state.eyepieceFovDeg / 2)
    if (r < 6f || r > size.maxDimension) return
    drawCircle(m.eyepiece.copy(alpha = 0.8f), r, Offset(size.width / 2, size.height / 2), style = Stroke(1.5f))
}
