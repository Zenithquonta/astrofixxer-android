package org.astrofixxer.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import org.astrofixxer.astro.Pointing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

private const val D2R = PI / 180

/** Naked-eye limiting magnitude for Bortle classes 1..9 (approximate, from the Bortle scale description). */
private val BORTLE_LIMIT = doubleArrayOf(7.6, 7.1, 6.6, 6.1, 5.6, 5.1, 4.6, 4.1, 3.6)

/** How many magnitudes of faint objects light pollution removes compared with a pristine sky. */
fun lightPollutionLoss(bortle: Int) = BORTLE_LIMIT[0] - BORTLE_LIMIT[(bortle - 1).coerceIn(0, 8)]

/** Stars and deep-sky objects fade out as the Sun rises; returns magnitudes lost for a Sun altitude. */
fun twilightLoss(sunAltDeg: Double): Double = when {
    sunAltDeg <= -18 -> 0.0
    sunAltDeg >= 0 -> 8.0
    else -> 8.0 * ((sunAltDeg + 18) / 18).let { it * it }
}

/** Sky background from night through astronomical, nautical and civil twilight to day. */
fun skyColor(sunAltDeg: Double, night: Color): Color {
    val twilight = Color(0xFF1B2A4A)
    val dusk = Color(0xFF4A5E8C)
    val day = Color(0xFF4F86C6)
    return when {
        sunAltDeg <= -18 -> night
        sunAltDeg <= -12 -> lerp(night, twilight, ((sunAltDeg + 18) / 6).toFloat())
        sunAltDeg <= -6 -> lerp(twilight, dusk, ((sunAltDeg + 12) / 6).toFloat())
        sunAltDeg <= 0 -> lerp(dusk, day, ((sunAltDeg + 6) / 6).toFloat())
        else -> day
    }
}

/** J2000 equatorial unit vector for galactic longitude/latitude (degrees); matrix from the Hipparcos definition. */
fun galacticToEquatorial(lDeg: Double, bDeg: Double): Pair<Double, Double> {
    val l = lDeg * D2R
    val b = bDeg * D2R
    val g = doubleArrayOf(cos(b) * cos(l), cos(b) * sin(l), sin(b))
    // Transpose of the equatorial-to-galactic rotation.
    val x = -0.0548755604 * g[0] + 0.4941094279 * g[1] - 0.8676661490 * g[2]
    val y = -0.8734370902 * g[0] - 0.4448296300 * g[1] - 0.1980763734 * g[2]
    val z = -0.4838350155 * g[0] + 0.7469822445 * g[1] + 0.4559837762 * g[2]
    val ra = (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    return Pair(ra, Math.toDegrees(asin(z.coerceIn(-1.0, 1.0))))
}

/** Soft Milky Way band along the galactic plane, brightest toward the galactic centre (l = 0). */
fun DrawScope.drawMilkyWay(state: SkyState, proj: Projector, tint: Color, strength: Float) {
    if (strength <= 0f) return
    for (l in 0 until 360) {
        val (ra, dec) = galacticToEquatorial(l.toDouble(), 0.0)
        val p = proj.project(Pointing.rayFromPos(ra, dec, state.timeMillis, state.lat, state.lon)) ?: continue
        val (ra2, dec2) = galacticToEquatorial(l.toDouble(), 8.0)
        val q = proj.project(Pointing.rayFromPos(ra2, dec2, state.timeMillis, state.lat, state.lon)) ?: continue
        val halfWidth = hypot(q.x - p.x, q.y - p.y)
        val fromCentre = if (l > 180) 360 - l else l
        val glow = (0.35 + 0.65 * exp(-fromCentre / 60.0)).toFloat()
        drawCircle(tint.copy(alpha = 0.012f * glow * strength), halfWidth * 1.6f, p)
        drawCircle(tint.copy(alpha = 0.02f * glow * strength), halfWidth * 0.8f, p)
    }
}

/**
 * Affine map (2x3, row-major a,b,c / d,e,f) taking three source points to three target points,
 * or null when the source points are collinear.
 */
fun affineFrom3(src: List<Offset>, dst: List<Offset>): FloatArray? {
    val (p0, p1, p2) = src
    val det = (p1.x - p0.x) * (p2.y - p0.y) - (p2.x - p0.x) * (p1.y - p0.y)
    if (abs(det) < 1e-6f) return null
    fun solve(v0: Float, v1: Float, v2: Float): Triple<Float, Float, Float> {
        val a = ((v1 - v0) * (p2.y - p0.y) - (v2 - v0) * (p1.y - p0.y)) / det
        val b = ((p1.x - p0.x) * (v2 - v0) - (p2.x - p0.x) * (v1 - v0)) / det
        return Triple(a, b, v0 - a * p0.x - b * p0.y)
    }
    val (a, b, c) = solve(dst[0].x, dst[1].x, dst[2].x)
    val (d, e, f) = solve(dst[0].y, dst[1].y, dst[2].y)
    return floatArrayOf(a, b, c, d, e, f)
}

/**
 * Stellarium constellation illustrations, placed by their three anchor stars and blended so the black
 * background disappears. [images] maps "culture/file" to the decoded picture (may be downscaled).
 */
fun DrawScope.drawConstellationArt(
    state: SkyState, catalog: org.astrofixxer.astro.Catalog, proj: Projector, images: Map<String, ImageBitmap>, tint: Color,
) {
    for (c in catalog.constellations[state.skyCulture].orEmpty()) {
        val art = c.art ?: continue
        val img = images["${state.skyCulture}/${art.file}"] ?: continue
        val screen = art.anchors.map { (_, _, hip) ->
            val star = catalog.starsByHip[hip] ?: return@map null
            proj.project(state.ray(star))
        }
        if (screen.any { it == null }) continue
        val dst = screen.filterNotNull()
        if (dst.all { it.x < -size.width || it.x > 2 * size.width || it.y < -size.height || it.y > 2 * size.height }) continue
        val sx = img.width.toFloat() / art.width
        val sy = img.height.toFloat() / art.height
        val src = art.anchors.map { (x, y, _) -> Offset(x.toFloat() * sx, y.toFloat() * sy) }
        val m = affineFrom3(src, dst) ?: continue
        val values = floatArrayOf(
            m[0], m[3], 0f, 0f,
            m[1], m[4], 0f, 0f,
            0f, 0f, 1f, 0f,
            m[2], m[5], 0f, 1f,
        )
        withTransform({ transform(Matrix(values)) }) {
            drawImage(img, alpha = 0.35f, colorFilter = ColorFilter.tint(tint, BlendMode.Modulate), blendMode = BlendMode.Screen)
        }
    }
}

/** Horizon silhouette altitude (degrees) at an azimuth for each landscape. */
fun landscapeAltitude(landscape: Landscape, azDeg: Double): Double {
    val a = azDeg * D2R
    return when (landscape) {
        Landscape.NONE -> 0.0
        Landscape.HILLS -> 2.0 + 1.6 * sin(3 * a + 0.4) + 1.1 * sin(7 * a + 1.3) + 0.5 * sin(17 * a)
        Landscape.TREES -> 1.5 + 0.6 * sin(5 * a) + 3.2 * abs(sin(23 * a)) * (0.6 + 0.4 * sin(3 * a + 2))
        Landscape.CITY -> 1.0 + listOf(0.0, 4.0, 2.0, 6.5, 1.0, 3.0, 8.0, 2.5, 0.5, 5.0)[((azDeg / 7).toInt().mod(10))] * (0.5 + 0.5 * cos(a * 2))
        Landscape.OBSERVATORY -> if (abs(((azDeg + 180) % 360) - 180) < 12) 1.0 else 32.0 // slit facing north
    }
}

/** Ground below the landscape silhouette. The camera frame keeps its left axis level, so "down" is the screen bottom. */
fun DrawScope.drawLandscape(state: SkyState, proj: Projector, ground: Color, outline: Color) {
    if (state.landscape == Landscape.NONE) return
    val pts = mutableListOf<Offset>()
    for (az in 0..360 step 1) {
        val alt = landscapeAltitude(state.landscape, az.toDouble()) * D2R
        val a = az * D2R
        proj.project(doubleArrayOf(sin(a) * cos(alt), cos(a) * cos(alt), sin(alt)))?.let { pts += it }
    }
    if (pts.size < 2) {
        // Looking straight down: all ground.
        if (state.camera()[2][2] < 0) drawRect(ground)
        return
    }
    val sorted = pts.sortedBy { it.x }
    val path = Path().apply {
        moveTo(sorted.first().x, size.height + 10)
        for (p in sorted) lineTo(p.x, p.y)
        lineTo(sorted.last().x, size.height + 10)
        close()
    }
    drawPath(path, ground)
    for (i in 0 until sorted.size - 1) drawLine(outline, sorted[i], sorted[i + 1], strokeWidth = 1.5f)
}
