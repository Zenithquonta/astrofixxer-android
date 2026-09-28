package org.astrofixxer.ui

import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.SkyObject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToInt

/** What AstroGuide should say, and what it did to the sky state. */
class GuideReply(val speech: String, val target: SkyObject? = null)

/**
 * Offline voice assistant v1: understands a few spoken commands in English and Hindi and answers from the
 * bundled catalogue and the events list. ponytail: keyword matching, no LLM; add one when free-form questions matter.
 */
object AstroGuide {
    private val hindiNames = mapOf(
        "सूर्य" to "Sun", "सूरज" to "Sun", "चंद्रमा" to "Moon", "चाँद" to "Moon", "चांद" to "Moon", "बुध" to "Mercury",
        "शुक्र" to "Venus", "मंगल" to "Mars", "बृहस्पति" to "Jupiter", "गुरु" to "Jupiter", "शनि" to "Saturn",
        "अरुण" to "Uranus", "वरुण" to "Neptune", "ध्रुव तारा" to "Polaris", "व्याध" to "Sirius", "लुब्धक" to "Sirius",
    )
    private val hindiDisplay = mapOf("Sun" to "सूर्य", "Moon" to "चंद्रमा", "Mercury" to "बुध", "Venus" to "शुक्र", "Mars" to "मंगल",
        "Jupiter" to "बृहस्पति", "Saturn" to "शनि", "Uranus" to "अरुण", "Neptune" to "वरुण")
    private val findWords = listOf("find", "show me", "show", "where is", "where's", "point to", "go to", "take me to",
        "दिखाओ", "दिखाइए", "कहाँ है", "कहां है", "ढूंढो", "खोजो")
    private val whatWords = listOf("what is", "what's", "tell me about", "क्या है", "के बारे में बताओ")
    private val directions = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
    private val directionsHi = listOf("उत्तर", "उत्तर-पूर्व", "पूर्व", "दक्षिण-पूर्व", "दक्षिण", "दक्षिण-पश्चिम", "पश्चिम", "उत्तर-पश्चिम")

    private fun isHindi(text: String) = text.any { it in 'ऀ'..'ॿ' }

    fun answer(transcript: String, state: SkyState, catalog: Catalog?, moving: List<MovingObject>, events: List<EventItem>?): GuideReply {
        val text = transcript.trim()
        val lower = text.lowercase()
        val hi = isHindi(text)
        fun say(en: String, hiText: String) = if (hi) hiText else en

        when {
            lower.contains("align") || text.contains("संरेख") -> {
                state.startAlign()
                return GuideReply(say("Point the telescope at a bright star and tap it on the screen.", "टेलीस्कोप को किसी चमकीले तारे पर लगाएँ और स्क्रीन पर उसे छुएँ।"))
            }
            lower.contains("night mode") || text.contains("रात") -> {
                state.night = !lower.contains("off") && !text.contains("बंद")
                return GuideReply(say(if (state.night) "Night mode on." else "Night mode off.", if (state.night) "रात मोड चालू।" else "रात मोड बंद।"))
            }
            lower.contains("meteor") || text.contains("उल्का") -> {
                val next = events?.firstOrNull { it.key.contains("meteor") }
                    ?: return GuideReply(say("No meteor shower peaks in the next two months.", "अगले दो महीनों में कोई उल्का वर्षा नहीं है।"))
                return GuideReply(say("${next.title} on ${formatLocal(next.jd)}. ${next.detail}.", "${next.title}: ${formatLocal(next.jd)}"))
            }
            lower.contains("eclipse") || text.contains("ग्रहण") -> {
                val next = events?.firstOrNull { it.key.contains("eclipse") }
                    ?: return GuideReply(say("No eclipse in the next two months.", "अगले दो महीनों में कोई ग्रहण नहीं है।"))
                return GuideReply(say("${next.title} on ${formatLocal(next.jd)}.", "${next.title}: ${formatLocal(next.jd)}"))
            }
            lower.contains("tonight") || lower.contains("what's up") || lower.contains("what can i see") || text.contains("आज रात") -> {
                val up = moving.filter { it.kind != MovingObject.Kind.SUN && state.ray(it.obj)[2] > 0.1 }.map { it.obj.name }
                val en = if (up.isEmpty()) "No planets are up right now." else "Up now: ${up.joinToString(", ")}."
                val nextEvent = events?.firstOrNull()?.let { " Next: ${it.title}, ${formatLocal(it.jd)}." } ?: ""
                return GuideReply(say(en + nextEvent, (if (up.isEmpty()) "अभी कोई ग्रह ऊपर नहीं है।" else "अभी दिख रहे हैं: ${up.joinToString(", ")}।") + nextEvent))
            }
        }

        val asksWhat = whatWords.any { lower.startsWith(it) || text.contains(it) }
        val name = objectName(text) ?: return GuideReply(say(
            "Try: find Jupiter, what is M42, what's up tonight, next meteor shower.",
            "ऐसे पूछें: बृहस्पति दिखाओ, M42 क्या है, आज रात क्या दिखेगा।"))
        val obj = moving.firstOrNull { it.obj.name.equals(name, ignoreCase = true) }?.obj
            ?: state.resolve(name, catalog)
            ?: return GuideReply(say("I couldn't find $name.", "$name नहीं मिला।"))
        state.target = obj
        val ray = state.ray(obj)
        val alt = Math.toDegrees(asin(ray[2])).roundToInt()
        val az = (Math.toDegrees(atan2(ray[0], ray[1])) + 360) % 360
        val dir = ((az + 22.5) / 45).toInt() % 8
        val hiName = hindiDisplay[obj.name] ?: obj.name
        val where = if (alt > 0) say("${obj.name} is $alt degrees up in the ${directions[dir]}.", "$hiName ${directionsHi[dir]} में $alt डिग्री ऊपर है।")
        else say("${obj.name} is below the horizon now.", "$hiName अभी क्षितिज के नीचे है।")
        if (!asksWhat) return GuideReply(where, obj)
        val kind = mapOf("S" to "a star", "Ga" to "a galaxy", "Oc" to "an open star cluster", "Gc" to "a globular star cluster",
            "Ne" to "a nebula", "P" to "a planet", "C" to "a comet", "U" to "one of your objects")[obj.type] ?: "an object"
        val mag = obj.mag?.let { say(", magnitude %.1f".format(it), ", कांतिमान %.1f".format(it)) } ?: ""
        val aka = obj.otherNames.firstOrNull()?.let { say(", also called $it", ", जिसे $it भी कहते हैं") } ?: ""
        return GuideReply(say("${obj.name} is $kind$aka$mag. $where", "${obj.name}$aka$mag। $where"), obj)
    }

    /** Pulls the object name out of a command: "find the Orion Nebula" -> "Orion Nebula", "मंगल दिखाओ" -> "Mars". */
    fun objectName(text: String): String? {
        for ((hiName, en) in hindiNames) if (text.contains(hiName)) return en
        var rest = text.trim().trimEnd('?', '.', '!', '।')
        for (w in (findWords + whatWords).sortedByDescending { it.length }) {
            val i = rest.lowercase().indexOf(w)
            if (i >= 0) rest = (rest.substring(0, i) + " " + rest.substring(i + w.length)).trim()
        }
        rest = rest.removePrefix("the ").removePrefix("The ").removePrefix("planet ").removePrefix("star ").trim()
        return rest.ifEmpty { null }
    }
}
