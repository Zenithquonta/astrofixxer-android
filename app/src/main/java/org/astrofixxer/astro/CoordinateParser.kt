package org.astrofixxer.astro

/** RA/Dec text parsing, same accepted formats as the web app's parseRA/parseDEC. Results in degrees, null if invalid. */
object CoordinateParser {
    private val raFloat = Regex("""^(\d+\.\d+)$""")
    private val raColon = Regex("""^(\d+):(\d+)(?::(\d+(\.\d+)?))?$""")
    private val raHms = Regex("""^(\d+)[hH \t]\s*(\d+)[mM'′]\s*(?:(\d+(\.\d+)?)(?:s|S|′′|″|''|"))?$""")
    private val raSpace = Regex("""^(\d+)\s+(\d+)(?:\s+(\d+(\.\d+)?))?$""")

    private val decFloat = Regex("""^([+\-−]?\d+\.\d+)$""")
    private val decColon = Regex("""^([+\-−]?)(\d+):(\d+)(?::(\d+(\.\d+)?))?$""")
    private val decDms = Regex("""^([+\-−]?)(\d+)[dD° \t]\s*(\d+)[mM'′]\s*(?:(\d+(\.\d+)?)(?:s|S|″|′′|''))?$""")
    private val decSpace = Regex("""^([+\-−]?)(\d+)\s+(\d+)(?:\s+(\d+(\.\d+)?))?$""")

    fun parseRA(text: String): Double? {
        val s = text.trim()
        raFloat.matchEntire(s)?.let { val deg = it.groupValues[1].toDouble(); return deg.takeIf { d -> d in 0.0..360.0 } }
        val m = raColon.matchEntire(s) ?: raHms.matchEntire(s) ?: raSpace.matchEntire(s) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].ifEmpty { "0" }.toDouble()
        if (h >= 24 || min >= 60 || sec >= 60) return null
        return 15 * (h + (60 * min + sec) / 3600.0)
    }

    fun parseDEC(text: String): Double? {
        val s = text.trim()
        decFloat.matchEntire(s)?.let { val deg = it.groupValues[1].replace('−', '-').toDouble(); return deg.takeIf { d -> d in -90.0..90.0 } }
        val m = decColon.matchEntire(s) ?: decDms.matchEntire(s) ?: decSpace.matchEntire(s) ?: return null
        val sign = if (m.groupValues[1] == "-" || m.groupValues[1] == "−") -1.0 else 1.0
        val d = m.groupValues[2].toInt()
        val min = m.groupValues[3].toInt()
        val sec = m.groupValues[4].ifEmpty { "0" }.toDouble()
        if (d > 90 || min >= 60 || sec >= 60) return null
        return sign * (d + (60 * min + sec) / 3600.0)
    }
}
