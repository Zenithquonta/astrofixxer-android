package org.astrofixxer.ui

import org.astrofixxer.astro.ApparentPosition
import org.astrofixxer.astro.JulianDate
import org.astrofixxer.astro.Pointing
import org.astrofixxer.astro.SkyObject
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.asin

/** Altitudes (degrees) at one moment: the object, the Sun (for twilight) and the Moon (for moonlight). */
class AltitudeSample(val millis: Long, val alt: Double, val sunAlt: Double, val moonAlt: Double)

/**
 * An object's night, noon to noon: altitude samples every 10 minutes, the first rise and set in that window
 * (null if it doesn't happen), when it is highest, and the fully dark window (Sun below −18°).
 */
class Visibility(
    val samples: List<AltitudeSample>,
    val rise: Long?,
    val set: Long?,
    val transit: Long,
    val maxAlt: Double,
    val darkStart: Long?,
    val darkEnd: Long?,
) {
    val alwaysUp get() = samples.all { it.alt > 0 }
    val neverUp get() = samples.all { it.alt <= 0 }
}

private const val STEP_MS = 10 * 60_000L

/** Start of the observing night containing [millis]: local noon today, or yesterday if it's still morning. */
fun nightStart(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val local = Instant.ofEpochMilli(millis).atZone(zone)
    val noon = local.toLocalDate().atTime(12, 0).atZone(zone)
    return (if (local.hour >= 12) noon else noon.minusDays(1)).toInstant().toEpochMilli()
}

fun visibilityTonight(obj: SkyObject, millis: Long, latDeg: Double, lonDeg: Double, zone: ZoneId = ZoneId.systemDefault()): Visibility {
    val start = nightStart(millis, zone)
    val lat = latDeg * PI / 180
    val lon = lonDeg * PI / 180
    // Planets, the Sun and the Moon move during the night, so they are recomputed at each sample; the rest are fixed.
    val body = if (obj.type == "P") ApparentPosition.bodies.indexOf(obj.name).takeIf { it >= 0 } else null
    fun altOf(b: Int, t: Long) = ApparentPosition.reduce(b, JulianDate.fromEpochMillis(t), lat, lon).alt * 180 / PI
    val samples = (0..144).map { i ->
        val t = start + i * STEP_MS
        val alt = if (body != null) altOf(body, t) else Math.toDegrees(asin(Pointing.rayFromPos(obj.ra, obj.dec, t, latDeg, lonDeg)[2]))
        AltitudeSample(t, alt, altOf(ApparentPosition.SUN, t), altOf(ApparentPosition.MOON, t))
    }
    fun crossing(a: AltitudeSample, b: AltitudeSample, level: Double, pick: (AltitudeSample) -> Double): Long {
        val f = (level - pick(a)) / (pick(b) - pick(a))
        return a.millis + (f * (b.millis - a.millis)).toLong()
    }
    val pairs = samples.zipWithNext()
    val rise = pairs.firstOrNull { (a, b) -> a.alt <= 0 && b.alt > 0 }?.let { (a, b) -> crossing(a, b, 0.0) { it.alt } }
    val set = pairs.firstOrNull { (a, b) -> a.alt > 0 && b.alt <= 0 }?.let { (a, b) -> crossing(a, b, 0.0) { it.alt } }
    val top = samples.maxBy { it.alt }
    val darkStart = pairs.firstOrNull { (a, b) -> a.sunAlt > -18 && b.sunAlt <= -18 }?.let { (a, b) -> crossing(a, b, -18.0) { it.sunAlt } }
    val darkEnd = pairs.firstOrNull { (a, b) -> a.sunAlt <= -18 && b.sunAlt > -18 }?.let { (a, b) -> crossing(a, b, -18.0) { it.sunAlt } }
    return Visibility(samples, rise, set, top.millis, top.alt, darkStart, darkEnd)
}
