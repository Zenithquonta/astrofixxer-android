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

fun visibilityTonight(
    obj: SkyObject, millis: Long, latDeg: Double, lonDeg: Double, zone: ZoneId = ZoneId.systemDefault(),
    /** False skips the Moon's altitude (NaN), which is the costly part when only the object matters. */
    withMoon: Boolean = true,
): Visibility {
    val start = nightStart(millis, zone)
    val lat = latDeg * PI / 180
    val lon = lonDeg * PI / 180
    // Planets, the Sun and the Moon move during the night, so they are recomputed at each sample; the rest are fixed.
    val body = if (obj.type == "P") ApparentPosition.bodies.indexOf(obj.name).takeIf { it >= 0 } else null
    fun altOf(b: Int, t: Long) = ApparentPosition.reduce(b, JulianDate.fromEpochMillis(t), lat, lon).alt * 180 / PI
    val samples = (0..144).map { i ->
        val t = start + i * STEP_MS
        val alt = if (body != null) altOf(body, t) else Math.toDegrees(asin(Pointing.rayFromPos(obj.ra, obj.dec, t, latDeg, lonDeg)[2]))
        AltitudeSample(t, alt, altOf(ApparentPosition.SUN, t), if (withMoon) altOf(ApparentPosition.MOON, t) else Double.NaN)
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

/** Next time within a day that a fixed-position object rises above the horizon, or null (already up, or it doesn't rise). */
fun nextRise(obj: SkyObject, millis: Long, latDeg: Double, lonDeg: Double): Long? {
    fun alt(t: Long) = Pointing.rayFromPos(obj.ra, obj.dec, t, latDeg, lonDeg)[2]
    if (alt(millis) > 0) return null
    var prev = millis
    for (i in 1..144) {
        val t = millis + i * STEP_MS
        if (alt(t) > 0) {
            val a = alt(prev); val b = alt(t)
            return prev + ((-a / (b - a)) * (t - prev)).toLong()
        }
        prev = t
    }
    return null
}

/** The "Tonight" card: Sun and Moon times, how much of the Moon is lit, and which bright planets are up in the dark. */
class TonightSummary(
    val sunset: Long?, val darkStart: Long?, val darkEnd: Long?, val sunrise: Long?,
    val moonrise: Long?, val moonset: Long?, val moonLitPercent: Int,
    /** Planet name, time it is highest while the sky is dark (Sun below −12°), and that altitude. */
    val planets: List<Triple<String, Long, Double>>,
)

fun tonightSummary(millis: Long, latDeg: Double, lonDeg: Double, zone: ZoneId = ZoneId.systemDefault()): TonightSummary {
    // The Moon as the object: its altitude is the object's, so the separate Moon track isn't needed.
    val moon = visibilityTonight(SkyObject("Moon", 0.0, 0.0, null, "P"), millis, latDeg, lonDeg, zone, withMoon = false)
    val pairs = moon.samples.zipWithNext()
    fun sunCross(down: Boolean) = pairs.firstOrNull { (a, b) -> if (down) a.sunAlt > -0.833 && b.sunAlt <= -0.833 else a.sunAlt <= -0.833 && b.sunAlt > -0.833 }
        ?.let { (a, b) -> a.millis + (((-0.833 - a.sunAlt) / (b.sunAlt - a.sunAlt)) * (b.millis - a.millis)).toLong() }
    val planets = listOf("Mercury", "Venus", "Mars", "Jupiter", "Saturn").mapNotNull { name ->
        val v = visibilityTonight(SkyObject(name, 0.0, 0.0, null, "P"), millis, latDeg, lonDeg, zone, withMoon = false)
        v.samples.filter { it.sunAlt < -12 && it.alt > 10 }.maxByOrNull { it.alt }?.let { Triple(name, it.millis, it.alt) }
    }
    val midnight = moon.samples[72].millis
    val lit = (org.astrofixxer.astro.Events.moonIllumination(JulianDate.fromEpochMillis(midnight)) * 100).toInt()
    return TonightSummary(sunCross(true), moon.darkStart, moon.darkEnd, sunCross(false), moon.rise, moon.set, lit, planets)
}
