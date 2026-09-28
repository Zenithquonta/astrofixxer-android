package org.astrofixxer.ui

import org.astrofixxer.astro.ApparentPosition
import org.astrofixxer.astro.Events
import org.astrofixxer.astro.MinorBody
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EventItem(val jd: Double, val title: String, val detail: String, val rare: Boolean = false)

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

fun jdToMillis(jd: Double) = ((jd - 2440587.5) * 86400000.0).toLong()

fun formatLocal(jd: Double, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm").withZone(zone).format(Instant.ofEpochMilli(jdToMillis(jd)))

/**
 * Offline "what's coming up": moon phases, eclipses, equinoxes/solstices, meteor showers, planet conjunctions,
 * bright comets. ponytail: recomputed on demand (a few hundred ms), not cached.
 */
fun upcomingEvents(nowJd: Double, days: Int, showers: List<MeteorShower>, bodies: List<MinorBody>): List<EventItem> {
    val out = mutableListOf<EventItem>()
    val end = nowJd + days

    val phaseNames = mapOf(0.0 to "New Moon", 90.0 to "First Quarter", 180.0 to "Full Moon", 270.0 to "Last Quarter")
    for ((phase, name) in phaseNames) {
        var t = Events.nextPhase(nowJd, phase)
        while (t < end) {
            out += EventItem(t, name, "")
            if (phase == 180.0) Events.lunarEclipse(t)?.let {
                out += EventItem(it.jdMax, "Lunar eclipse (${it.type.name.lowercase()})",
                    "Umbral magnitude %.2f. Visible wherever the Moon is up.".format(it.umbralMagnitude), rare = true)
            }
            if (phase == 0.0) Events.solarEclipse(t)?.let {
                out += EventItem(it.jdMax, "Solar eclipse (${it.type.name.lowercase()} somewhere on Earth)",
                    "Check the path before travelling; never look at the Sun without a proper filter.", rare = true)
            }
            t = Events.nextPhase(t + 20, phase)
        }
    }

    for ((lon, name) in listOf(0.0 to "March equinox", 90.0 to "June solstice", 180.0 to "September equinox", 270.0 to "December solstice")) {
        val t = Events.nextSolarLongitude(nowJd, lon, ofDate = true)
        if (t < end) out += EventItem(t, name, "")
    }

    for (s in showers) {
        val peakJd = s.peakMillis / 86400000.0 + 2440587.5
        if (peakJd in nowJd..end) out += EventItem(peakJd, "${s.name} meteor shower peak", "Up to ${s.zhr} meteors/hour under dark skies")
    }

    val planets = listOf(ApparentPosition.MERCURY, ApparentPosition.VENUS, ApparentPosition.MARS, ApparentPosition.JUPITER, ApparentPosition.SATURN)
    for (i in planets.indices) for (j in i + 1 until planets.size) {
        for (c in Events.conjunctions(planets[i], planets[j], nowJd, days, 3.0)) {
            out += EventItem(c.jd, "${ApparentPosition.bodies[c.bodyA]}–${ApparentPosition.bodies[c.bodyB]} conjunction",
                "%.1f° apart".format(c.separationDeg), rare = c.separationDeg < 0.5)
        }
    }
    for (p in planets) for (c in Events.conjunctions(ApparentPosition.MOON, p, nowJd, days, 2.0)) {
        out += EventItem(c.jd, "Moon near ${ApparentPosition.bodies[p]}", "%.1f° apart (seen from Earth's centre)".format(c.separationDeg))
    }

    for (b in bodies.filter { it.isComet }) {
        val place = b.geocentric(nowJd)
        val mag = place.mag ?: continue
        if (mag < 11) out += EventItem(nowJd, "Comet ${b.name}", "Magnitude %.1f now, %.2f AU from Earth (orbit elements from JD %.0f)".format(mag, place.delta, b.epoch))
    }

    return out.sortedBy { it.jd }
}
