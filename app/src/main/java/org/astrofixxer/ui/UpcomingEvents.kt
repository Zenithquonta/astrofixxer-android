package org.astrofixxer.ui

import org.astrofixxer.astro.ApparentPosition
import org.astrofixxer.astro.Events
import org.astrofixxer.astro.MinorBody
import org.astrofixxer.astro.Satellites
import org.astrofixxer.astro.Sgp4
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One upcoming event. Title and detail are English templates plus arguments, translated when displayed,
 * so switching language doesn't need the (slow) events calculation again.
 */
class EventItem(
    val jd: Double, val key: String, val args: List<String> = emptyList(),
    val detailKey: String = "", val detailArgs: List<String> = emptyList(), val rare: Boolean = false,
) {
    val title: String get() = t(key).format(*args.map { t(it) }.toTypedArray())
    val detail: String get() = if (detailKey.isEmpty()) "" else t(detailKey).format(*detailArgs.map { t(it) }.toTypedArray())
}

class MeteorShower(val name: String, val peakMillis: Long, val startMillis: Long, val endMillis: Long, val zhr: String, val radiantRa: Double, val radiantDec: Double)

fun parseMeteorShowers(json: String): List<MeteorShower> {
    val arr = JSONObject(json).getJSONArray("showers")
    fun millis(s: String) = Instant.parse(s.replace("Z", ":00Z")).toEpochMilli()
    return List(arr.length()) {
        val o = arr.getJSONObject(it)
        val zhr = if (o.isNull("zhr")) o.optString("zhr_variable", "?") else o.getInt("zhr").toString()
        MeteorShower(o.getString("name"), millis(o.getString("peak_utc")), millis(o.getString("start_utc")), millis(o.getString("end_utc")),
            zhr, o.getDouble("radiant_ra"), o.getDouble("radiant_dec"))
    }
}

fun compass(azDeg: Double) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((azDeg + 22.5) % 360) / 45).toInt()]

fun jdToMillis(jd: Double) = ((jd - 2440587.5) * 86400000.0).toLong()

fun formatLocal(jd: Double, zone: ZoneId = ZoneId.systemDefault()): String = formatMillis(jdToMillis(jd), zone)

fun formatMillis(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm").withZone(zone).format(Instant.ofEpochMilli(millis))

fun formatClock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(millis))

private fun f1(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)
private fun f0(v: Double) = "%.0f".format(java.util.Locale.ROOT, v)

/**
 * Offline "what's coming up": moon phases, eclipses, equinoxes/solstices, meteor showers, planet conjunctions,
 * transits, supermoons, planet gatherings, bright comets, lunar occultations of [brightStars] and satellite passes seen
 * from ([latDeg], [lonDeg]). ponytail: computed once after loading, not cached across launches.
 */
fun upcomingEvents(
    nowJd: Double, days: Int, showers: List<MeteorShower>, bodies: List<MinorBody>,
    brightStars: List<Triple<String, Double, Double>> = emptyList(), latDeg: Double = 0.0, lonDeg: Double = 0.0,
    satellites: List<Sgp4> = emptyList(),
): List<EventItem> {
    val out = mutableListOf<EventItem>()
    val end = nowJd + days
    val name = { body: Int -> ApparentPosition.bodies[body] }

    val phaseNames = mapOf(0.0 to "New Moon", 90.0 to "First Quarter", 180.0 to "Full Moon", 270.0 to "Last Quarter")
    for ((phase, phaseName) in phaseNames) {
        var t = Events.nextPhase(nowJd, phase)
        while (t < end) {
            out += EventItem(t, phaseName)
            if (phase == 180.0) Events.lunarEclipse(t)?.let {
                out += EventItem(it.jdMax, "Lunar eclipse (%s)", listOf(it.type.name.lowercase()),
                    "Umbral magnitude %s. Visible wherever the Moon is up.", listOf("%.2f".format(java.util.Locale.ROOT, it.umbralMagnitude)), rare = true)
            }
            if (phase == 0.0) Events.solarEclipse(t)?.let {
                out += EventItem(it.jdMax, "Solar eclipse (%s somewhere on Earth)", listOf(it.type.name.lowercase()),
                    "Check the path before travelling; never look at the Sun without a proper filter.", rare = true)
            }
            t = Events.nextPhase(t + 20, phase)
        }
    }

    for ((lon, seasonName) in listOf(0.0 to "March equinox", 90.0 to "June solstice", 180.0 to "September equinox", 270.0 to "December solstice")) {
        val t = Events.nextSolarLongitude(nowJd, lon, ofDate = true)
        if (t < end) out += EventItem(t, seasonName)
    }

    for (s in showers) {
        val peakJd = s.peakMillis / 86400000.0 + 2440587.5
        if (peakJd in nowJd..end) out += EventItem(peakJd, "%s meteor shower peak", listOf(s.name),
            "Up to %s meteors/hour under dark skies", listOf(s.zhr))
    }

    val planets = listOf(ApparentPosition.MERCURY, ApparentPosition.VENUS, ApparentPosition.MARS, ApparentPosition.JUPITER, ApparentPosition.SATURN)
    for (i in planets.indices) for (j in i + 1 until planets.size) {
        for (c in Events.conjunctions(planets[i], planets[j], nowJd, days, 3.0)) {
            out += EventItem(c.jd, "%s–%s conjunction", listOf(name(c.bodyA), name(c.bodyB)),
                "%s° apart", listOf(f1(c.separationDeg)), rare = c.separationDeg < 0.5)
        }
    }
    for (p in planets) for (c in Events.conjunctions(ApparentPosition.MOON, p, nowJd, days, 2.0)) {
        out += EventItem(c.jd, "Moon near %s", listOf(name(p)), "%s° apart (seen from Earth's centre)", listOf(f1(c.separationDeg)))
    }

    for (b in bodies.filter { it.isComet }) {
        val place = b.geocentric(nowJd)
        val mag = place.mag ?: continue
        if (mag < 11) out += EventItem(nowJd, "Comet %s", listOf(b.name),
            "Magnitude %s now, %s AU from Earth (orbit elements from JD %s)", listOf(f1(mag), "%.2f".format(java.util.Locale.ROOT, place.delta), f0(b.epoch)))
    }

    for (body in listOf(ApparentPosition.MERCURY, ApparentPosition.VENUS)) for (t in Events.planetTransits(body, nowJd, days)) {
        out += EventItem(t.jd, "Transit of %s across the Sun", listOf(name(body)),
            "Only with a solar filter or projection. Never look at the Sun directly.", rare = true)
    }
    for ((t, km) in Events.supermoons(nowJd, days)) {
        out += EventItem(t, "Supermoon", detailKey = "Full Moon at %s km", detailArgs = listOf("%,.0f".format(java.util.Locale.ROOT, km)), rare = true)
    }
    for ((t, span) in Events.planetGatherings(nowJd, days)) {
        out += EventItem(t, "Planet gathering", detailKey = "Four or more bright planets within %s°", detailArgs = listOf(f0(span)), rare = true)
    }
    for (o in Events.lunarOccultations(brightStars, nowJd, days, latDeg, lonDeg)) {
        if (o.moonAltDeg <= 0) continue
        out += EventItem(o.disappearJd, "Moon covers %s", listOf(o.name),
            if (o.sunAltDeg > -6) "Disappears %s, reappears %s (in daylight or twilight). Times ±5 min." else "Disappears %s, reappears %s. Times ±5 min.",
            listOf(formatLocal(o.disappearJd).substringAfter(", "), formatLocal(o.reappearJd).substringAfter(", ")), rare = true)
    }

    for (sat in satellites) {
        val ageDays = nowJd - sat.epochJd
        if (ageDays > 30) continue // too old to be useful; refreshed when the phone is next online
        val age = f0(ageDays)
        for (p in Satellites.passes(sat, nowJd, 3.0, latDeg, lonDeg).filter { it.visible }) {
            out += EventItem(p.startJd, "%s visible pass", listOf(sat.name),
                "Up to %s° high, from %s to %s · orbit data %s days old", listOf(f0(p.maxAltDeg), compass(p.startAzDeg), compass(p.endAzDeg), age))
        }
        if (ageDays <= 2) for (tr in Satellites.transits(sat, nowJd, 3.0, latDeg, lonDeg)) {
            out += EventItem(tr.jd, "%s crosses the %s", listOf(sat.name, tr.body),
                if (tr.body == "Sun") "Lasts under a second, on a narrow strip of ground near you; use a proper solar filter · orbit data %s days old"
                else "Lasts under a second, on a narrow strip of ground near you · orbit data %s days old", listOf(age), rare = true)
        }
    }

    return out.sortedBy { it.jd }
}
