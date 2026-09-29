package org.astrofixxer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * UI translations keyed by the English text, like the web app's i18n dictionaries.
 * [language] is snapshot state, so switching it recomposes the screen.
 */
object I18n {
    var language by mutableStateOf("en")
    val languages = listOf("en" to "English", "hi" to "हिन्दी")

    fun t(en: String): String = if (language == "hi") HI[en] ?: en else en

    private val HI = mapOf(
        // Toolbar and status
        "Align" to "संरेखण", "Ask" to "पूछें", "Find" to "खोजें", "Events" to "घटनाएँ", "Sky" to "आकाश",
        "Night" to "रात", "Day" to "दिन", "Compass" to "कम्पास", "Manual" to "हाथ से", "Now" to "अभी",
        "Not aligned" to "संरेखित नहीं", "Tap the star the telescope points at" to "जिस तारे पर टेलीस्कोप है, उसे छुएँ",
        "Aligned ✓" to "संरेखित ✓", "Aligned %d min ago · re-align soon" to "%d मिनट पहले संरेखित · फिर से संरेखित करें",
        "Move to %s" to "%s की ओर घुमाएँ", "On target: %s" to "लक्ष्य पर: %s", "altitude" to "ऊँचाई", "azimuth" to "दिगंश",
        "%.1f° to go · re-align if the target drifts" to "%.1f° बाकी · लक्ष्य खिसके तो फिर से संरेखित करें",
        "Loading sky catalogue…" to "आकाश सूची लोड हो रही है…", "Below the horizon" to "क्षितिज के नीचे",
        "up now" to "अभी ऊपर", "below horizon" to "क्षितिज के नीचे", "Listening…" to "सुन रहा है…",
        // Object types
        "Star" to "तारा", "Galaxy" to "मंदाकिनी", "Open cluster" to "खुला तारा-गुच्छ", "Globular cluster" to "गोलाकार तारा-गुच्छ",
        "Nebula" to "नीहारिका", "Solar system" to "सौर मंडल", "Comet" to "धूमकेतु", "My object" to "मेरी वस्तु",
        // Sheets
        "Close" to "बंद करें", "Working out what's coming up…" to "आने वाली घटनाओं की गणना हो रही है…",
        "Sky & viewing" to "आकाश और दृश्य", "My objects & lists" to "मेरी वस्तुएँ व सूचियाँ", "Help" to "सहायता",
        "Tutorial" to "परिचय", "Constellation lines and names" to "तारामंडल रेखाएँ और नाम", "Deep-sky objects" to "गहन-आकाशीय पिंड",
        "Milky Way" to "आकाशगंगा", "Constellation artwork" to "तारामंडल चित्र",
        "Atmosphere (daylight and twilight)" to "वायुमंडल (दिन और संध्या)", "Alt/Az grid" to "ऊँचाई/दिगंश जाल",
        "Light pollution (Bortle)" to "प्रकाश प्रदूषण (बोर्टल)", "Landscape" to "भूदृश्य", "Sky culture" to "आकाश संस्कृति",
        "Indian (Vedic)" to "भारतीय (वैदिक)", "Western" to "पश्चिमी", "Language" to "भाषा",
        "None" to "कोई नहीं", "Hills" to "पहाड़ियाँ", "Trees" to "पेड़", "City" to "शहर", "Dome" to "गुंबद",
        "Location (overrides GPS)" to "स्थान (GPS के बजाय)", "Latitude" to "अक्षांश", "Longitude" to "देशांतर",
        "Use this location" to "यह स्थान उपयोग करें",
        "Latitude −90 to 90, longitude −180 to 180 (east positive)." to "अक्षांश −90 से 90, देशांतर −180 से 180 (पूर्व धनात्मक)।",
        "My objects & watch lists" to "मेरी वस्तुएँ और निगरानी सूचियाँ",
        "My objects: one per line, name, RA, Dec" to "मेरी वस्तुएँ: हर पंक्ति में नाम, RA, Dec",
        "Watch lists: \"Name:\" starts a list; items separated by spaces or commas; (comment) after an item" to
            "निगरानी सूचियाँ: \"नाम:\" से सूची शुरू होती है; वस्तुएँ स्पेस या कॉमा से अलग; वस्तु के बाद (टिप्पणी)",
        "Save" to "सहेजें", "Discard" to "रद्द करें", "Active watch list" to "चालू सूची",
        "Skip" to "छोड़ें", "Back" to "पीछे", "Next" to "आगे", "Start" to "शुरू करें", "Quick start %d/%d" to "शुरुआत %d/%d",
        // Onboarding
        "Attach the phone" to "फ़ोन लगाएँ",
        "Fix the phone flat on the telescope tube, with its top edge pointing where the telescope points." to
            "फ़ोन को टेलीस्कोप की नली पर सपाट लगाएँ, ताकि उसका ऊपरी किनारा उसी ओर हो जिधर टेलीस्कोप देखता है।",
        "Align on a bright star" to "चमकीले तारे पर संरेखित करें",
        "Point the telescope at an easy star or planet near what you want to find, for example Sirius for M41. Tap Align, then tap that star on the screen." to
            "जिसे ढूँढना है उसके पास के किसी आसान तारे या ग्रह पर टेलीस्कोप लगाएँ, जैसे M41 के लिए व्याध (Sirius)। संरेखण दबाएँ, फिर स्क्रीन पर उस तारे को छुएँ।",
        "Can't see the star?" to "तारा नहीं दिख रहा?",
        "The compass may be off near the metal tube. Switch to Manual and drag the sky sideways until the star is under the crosshair, then Align." to
            "धातु की नली के पास कम्पास गलत हो सकता है। 'हाथ से' चुनें और आकाश को बगल में खींचें जब तक तारा निशाने के नीचे न आ जाए, फिर संरेखण करें।",
        "Hop to the target" to "लक्ष्य तक पहुँचें",
        "Tap your target and follow the arrows until they reach zero. Re-align for each new target." to
            "अपना लक्ष्य छुएँ और तीरों का पालन करें जब तक संख्याएँ शून्य न हो जाएँ। हर नए लक्ष्य के लिए फिर से संरेखण करें।",
        // Help
        "Setting up" to "तैयारी",
        "Attach the phone flat on the telescope tube with its top edge pointing where the telescope points. Allow location so the sky matches your place and time." to
            "फ़ोन को टेलीस्कोप की नली पर सपाट लगाएँ, ऊपरी किनारा टेलीस्कोप की दिशा में। स्थान की अनुमति दें ताकि आकाश आपके स्थान और समय से मेल खाए।",
        "Aligning" to "संरेखण",
        "Point the telescope at a bright star or planet near your target, tap Align, then tap that star on the screen. Re-align for each new target; phone sensors drift over a few minutes." to
            "लक्ष्य के पास के किसी चमकीले तारे या ग्रह पर टेलीस्कोप लगाएँ, संरेखण दबाएँ, फिर स्क्रीन पर उस तारे को छुएँ। हर नए लक्ष्य के लिए फिर से संरेखण करें; फ़ोन के सेंसर कुछ मिनटों में खिसकते हैं।",
        "Finding a target" to "लक्ष्य ढूँढना",
        "Tap an object on the sky or use Find. Follow the arrows in the guidance panel until both numbers are close to zero." to
            "आकाश में किसी वस्तु को छुएँ या खोजें का उपयोग करें। मार्गदर्शन पैनल के तीरों का पालन करें जब तक दोनों संख्याएँ शून्य के पास न हों।",
        "Compass and Manual" to "कम्पास और हाथ से",
        "Compass uses the phone's compass. If the alignment star isn't on screen, switch to Manual and drag the sky sideways until it is, then Align." to
            "कम्पास फ़ोन के कम्पास का उपयोग करता है। अगर संरेखण वाला तारा स्क्रीन पर नहीं है, तो 'हाथ से' चुनें और आकाश को खींचें जब तक वह दिखे, फिर संरेखण करें।",
        "Zoom" to "ज़ूम",
        "Pinch or use + and −. Fainter stars and deep-sky objects appear as you zoom in." to
            "दो उँगलियों से या + और − से ज़ूम करें। ज़ूम करने पर धुँधले तारे और गहन-आकाशीय पिंड दिखते हैं।",
        "Moon phases, eclipses, meteor showers, conjunctions, transits, occultations and bright comets for the next 60 days, all worked out on the phone. Tap one to show the sky at that time; Now returns to the present." to
            "अगले 60 दिनों की चंद्र कलाएँ, ग्रहण, उल्का वर्षा, युति, पारगमन, प्रच्छादन और चमकीले धूमकेतु, सब फ़ोन पर ही गणना। किसी पर छुएँ तो उस समय का आकाश दिखेगा; 'अभी' वर्तमान पर लौटाता है।",
        "Night mode" to "रात मोड",
        "Turns everything red to protect your dark adaptation. Also lower the screen brightness." to
            "आँखों के अँधेरे के अनुकूलन को बचाने के लिए सब कुछ लाल कर देता है। स्क्रीन की चमक भी कम करें।",
        "My objects and watch lists" to "मेरी वस्तुएँ और निगरानी सूचियाँ",
        "Add your own objects by RA/Dec, and lists of targets to step through with ‹ and › during a session." to
            "RA/Dec से अपनी वस्तुएँ जोड़ें, और लक्ष्यों की सूचियाँ बनाएँ जिन्हें ‹ और › से बारी-बारी देख सकें।",
        "Offline" to "बिना इंटरनेट",
        "Everything works without internet. The star, deep-sky and constellation data come from Stellarium and ship inside the app." to
            "सब कुछ बिना इंटरनेट के चलता है। तारों, गहन-आकाशीय पिंडों और तारामंडलों का डेटा Stellarium से है और ऐप में ही शामिल है।",
        "Licences and source" to "लाइसेंस और स्रोत कोड",
        // Events (templates; arguments such as planet names are translated too)
        "New Moon" to "अमावस्या", "First Quarter" to "शुक्ल पक्ष अष्टमी", "Full Moon" to "पूर्णिमा", "Last Quarter" to "कृष्ण पक्ष अष्टमी",
        "Lunar eclipse (%s)" to "चंद्र ग्रहण (%s)", "Solar eclipse (%s somewhere on Earth)" to "सूर्य ग्रहण (पृथ्वी पर कहीं %s)",
        "total" to "पूर्ण", "partial" to "आंशिक", "penumbral" to "उपछाया", "annular" to "वलयाकार",
        "Umbral magnitude %s. Visible wherever the Moon is up." to "प्रच्छाया परिमाण %s। जहाँ भी चंद्रमा ऊपर है, वहाँ दिखेगा।",
        "Check the path before travelling; never look at the Sun without a proper filter." to "यात्रा से पहले पथ जाँचें; सही फ़िल्टर के बिना सूर्य को कभी न देखें।",
        "March equinox" to "मार्च विषुव", "June solstice" to "जून संक्रांति", "September equinox" to "सितंबर विषुव", "December solstice" to "दिसंबर संक्रांति",
        "%s meteor shower peak" to "%s उल्का वर्षा का चरम", "Up to %s meteors/hour under dark skies" to "अँधेरे आकाश में प्रति घंटे %s तक उल्काएँ",
        "%s–%s conjunction" to "%s–%s युति", "%s° apart" to "%s° की दूरी", "Moon near %s" to "चंद्रमा %s के पास",
        "%s° apart (seen from Earth's centre)" to "%s° की दूरी (पृथ्वी के केंद्र से)",
        "Comet %s" to "धूमकेतु %s", "Magnitude %s now, %s AU from Earth (orbit elements from JD %s)" to "अभी कांतिमान %s, पृथ्वी से %s AU (कक्षा डेटा JD %s का)",
        "Transit of %s across the Sun" to "सूर्य के आगे से %s का पारगमन",
        "Only with a solar filter or projection. Never look at the Sun directly." to "केवल सौर फ़िल्टर या प्रक्षेपण से देखें। सूर्य को सीधे कभी न देखें।",
        "Supermoon" to "सुपरमून", "Full Moon at %s km" to "पूर्णिमा, %s किमी दूर",
        "Planet gathering" to "ग्रहों का जमावड़ा", "Four or more bright planets within %s°" to "चार या अधिक चमकीले ग्रह %s° के भीतर",
        "Moon covers %s" to "चंद्रमा %s को ढकेगा",
        "Disappears %s, reappears %s. Times ±5 min." to "%s पर छिपेगा, %s पर फिर दिखेगा। समय ±5 मिनट।",
        "Disappears %s, reappears %s (in daylight or twilight). Times ±5 min." to "%s पर छिपेगा, %s पर फिर दिखेगा (दिन या संध्या में)। समय ±5 मिनट।",
        "%s visible pass" to "%s दिखाई देगा", "Up to %s° high, from %s to %s · orbit data %s days old" to "%s° तक ऊँचा, %s से %s · कक्षा डेटा %s दिन पुराना",
        "%s crosses the %s" to "%s, %s के सामने से गुज़रेगा",
        "Lasts under a second, on a narrow strip of ground near you; use a proper solar filter · orbit data %s days old" to
            "एक सेकंड से कम, आपके पास की एक पतली पट्टी पर; सही सौर फ़िल्टर लगाएँ · कक्षा डेटा %s दिन पुराना",
        "Lasts under a second, on a narrow strip of ground near you · orbit data %s days old" to
            "एक सेकंड से कम, आपके पास की एक पतली पट्टी पर · कक्षा डेटा %s दिन पुराना",
        "ISS passes" to "ISS के दर्शन",
        "Connect to the internet once to download satellite orbits; everything else works offline." to
            "उपग्रह कक्षाएँ डाउनलोड करने के लिए एक बार इंटरनेट से जुड़ें; बाकी सब बिना इंटरनेट चलता है।",
        "Sun" to "सूर्य", "Moon" to "चंद्रमा", "Mercury" to "बुध", "Venus" to "शुक्र", "Mars" to "मंगल",
        "Jupiter" to "बृहस्पति", "Saturn" to "शनि", "Uranus" to "अरुण", "Neptune" to "वरुण",
        // Alignment, time travel, search
        "Cancel" to "रद्द करें",
        "To get directions to %s, point the telescope at a bright star near it, tap Align, then tap that star." to
            "%s तक दिशा पाने के लिए, टेलीस्कोप को उसके पास किसी चमकीले तारे पर लगाएँ, संरेखण दबाएँ, फिर उस तारे को छुएँ।",
        "−1 d" to "−1 दिन", "−1 h" to "−1 घंटा", "+1 h" to "+1 घंटा", "+1 d" to "+1 दिन",
        "Visible now" to "अभी दिख रहे हैं",
        "Nothing found. Try a catalogue number like M31 or NGC 7000." to "कुछ नहीं मिला। M31 या NGC 7000 जैसा सूची क्रमांक आज़माएँ।",
        "Time travel" to "समय यात्रा",
        "Tap the clock at the top right to step the sky by hours or days. While you are away from the present the clock turns pink; Now returns to the present." to
            "आकाश को घंटे या दिन आगे-पीछे करने के लिए ऊपर दाईं ओर घड़ी छुएँ। वर्तमान से दूर होने पर घड़ी गुलाबी हो जाती है; अभी दबाकर वर्तमान पर लौटें।",
        "%d° up · %s" to "%d° ऊपर · %s", "Not in the catalogue; check the spelling" to "सूची में नहीं है; वर्तनी जाँचें",
        "My list" to "मेरी सूची",
        // Object Info
        "Type" to "प्रकार", "Magnitude" to "कांतिमान", "Size" to "आकार", "Constellation" to "तारामंडल", "RA / Dec" to "RA / Dec",
        "Tonight" to "आज रात", "Working out tonight's positions…" to "आज रात की स्थितियाँ निकाली जा रही हैं…",
        "Up all night; highest at %s (%d°)" to "पूरी रात ऊपर; सबसे ऊँचा %s पर (%d°)", "Doesn't rise tonight" to "आज रात नहीं उगेगा",
        "Rises %s" to "उदय %s", "Highest %s (%d°)" to "सबसे ऊँचा %s (%d°)", "Sets %s" to "अस्त %s",
        "In the eyepiece" to "आईपीस में", "Field %.1f° with the %.0f mm eyepiece" to "%.1f° क्षेत्र, %.0f mm आईपीस के साथ",
        "Object %.0f′ across" to "वस्तु %.0f′ चौड़ी", "Set as target" to "लक्ष्य बनाएँ",
        // New screens (Find tabs, Sky & viewing tabs, telescope, place & time, Tonight, quick menu, AstroGuide chips)
        "Object" to "वस्तु", "Position" to "स्थिति", "Lists" to "सूचियाँ", "Info" to "जानकारी", "Up · %d°" to "ऊपर · %d°",
        "Point at any position in the sky (J2000)." to "आकाश में किसी भी स्थिति पर जाएँ (J2000)।",
        "RA, e.g. 05:35:17 or 83.82" to "RA, जैसे 05:35:17 या 83.82", "Dec, e.g. -05:23:28 or -5.39" to "Dec, जैसे -05:23:28 या -5.39",
        "Go to this position" to "इस स्थिति पर जाएँ", "Messier" to "मेसियर", "Caldwell" to "कॉल्डवेल", "Bright stars" to "चमकीले तारे",
        "Indian constellations" to "भारतीय तारामंडल", "My objects" to "मेरी वस्तुएँ", "%s · %d objects" to "%s · %d वस्तुएँ",
        "Deep-sky" to "गहरा आकाश", "Markings" to "चिह्न", "Culture" to "संस्कृति", "Telescope" to "टेलीस्कोप",
        "Place & time" to "स्थान व समय", "More" to "और", "Star colours" to "तारों के रंग", "Galaxies" to "मंदाकिनियाँ",
        "Open clusters" to "खुले तारा-गुच्छ", "Globular clusters" to "गोलाकार तारा-गुच्छ", "Nebulae" to "नीहारिकाएँ",
        "Constellation boundaries" to "तारामंडल सीमाएँ", "Equatorial grid" to "विषुवतीय ग्रिड", "Meridian" to "याम्योत्तर",
        "Ecliptic" to "क्रांतिवृत्त", "Cardinal points" to "मुख्य दिशाएँ",
        "The 88 IAU constellations with Stellarium's artwork." to "Stellarium के चित्रों सहित 88 IAU तारामंडल।",
        "Nakshatras, rashis and Indian star names." to "नक्षत्र, राशियाँ और भारतीय तारों के नाम।",
        "The eyepiece circle and \"On target\" use these." to "आईपीस वृत्त और \"लक्ष्य पर\" इन्हीं से तय होते हैं।",
        "Telescope focal length (mm)" to "टेलीस्कोप फ़ोकल लंबाई (mm)", "Eyepiece (mm)" to "आईपीस (mm)", "Apparent field (°)" to "आभासी क्षेत्र (°)",
        "True field: %.2f° · magnification ×%.0f" to "वास्तविक क्षेत्र: %.2f° · आवर्धन ×%.0f", "Mount" to "माउंट",
        "Equatorial" to "विषुवतीय", "Alt-Az" to "ऊँचाई-दिगंश", "Vibrate when on target" to "लक्ष्य पर पहुँचने पर कंपन",
        "Use GPS" to "GPS इस्तेमाल करें", "Or pick a city" to "या कोई शहर चुनें", "Date & time" to "तारीख व समय",
        "Date (YYYY-MM-DD)" to "तारीख (YYYY-MM-DD)", "Time (HH:MM)" to "समय (HH:MM)", "Show this time" to "यह समय दिखाएँ",
        "Data" to "डेटा", "Stellarium catalogue: %,d objects, %d constellations, %d boundary edges." to
            "Stellarium सूची: %,d वस्तुएँ, %d तारामंडल, %d सीमा रेखाएँ।",
        "Reset all" to "सब रीसेट करें", "Tap again to reset every setting and list" to "हर सेटिंग और सूची रीसेट करने के लिए फिर से छुएँ",
        "This week" to "इस सप्ताह", "This month" to "इस महीने", "All" to "सभी",
        "Sunset %s · sunrise %s" to "सूर्यास्त %s · सूर्योदय %s", "Fully dark %s–%s" to "पूरा अंधेरा %s–%s",
        "No fully dark sky tonight" to "आज रात पूरा अंधेरा नहीं होगा", "Moon %d%% lit · rises %s · sets %s" to "चंद्रमा %d%% प्रकाशित · उदय %s · अस्त %s",
        "No bright planets in the dark sky tonight" to "आज रात अंधेरे आकाश में कोई चमकीला ग्रह नहीं", "Planets: %s" to "ग्रह: %s",
        "Align on this" to "इस पर संरेखित करें", "Add to list" to "सूची में जोड़ें",
        "Tap a question below." to "नीचे कोई प्रश्न छुएँ।", "What's up tonight?" to "आज रात क्या दिखेगा", "Find Saturn" to "शनि दिखाओ", "Next meteor shower" to "अगली उल्का वर्षा",
        "What is M42?" to "M42 क्या है", "Next eclipse" to "अगला ग्रहण", "Free look" to "मुक्त दृश्य", "Search help" to "सहायता खोजें",
        "Object info" to "वस्तु जानकारी", "Telescope settings" to "टेलीस्कोप सेटिंग",
        "The third pointing mode: the sky ignores the phone's sensors and you drag it in any direction, like a planetarium. Tap the Compass / Manual / Free look button to switch." to
            "तीसरा मोड: आकाश फ़ोन के सेंसर को नहीं मानता और आप उसे किसी भी दिशा में खींच सकते हैं, तारामंडल-भवन की तरह। बदलने के लिए कम्पास / हाथ से / मुक्त दृश्य बटन दबाएँ।",
        "Tap the target card at the top left, or long-press any object, for its names, constellation, rise and set times, a graph of its altitude tonight and how it looks in your eyepiece." to
            "नाम, तारामंडल, उदय-अस्त समय, आज रात की ऊँचाई का ग्राफ़ और आईपीस में दृश्य देखने के लिए ऊपर बाईं ओर लक्ष्य कार्ड छुएँ, या किसी वस्तु को देर तक दबाएँ।",
        "In Sky & viewing, Telescope: enter the telescope's and eyepiece's focal lengths and the eyepiece's apparent field. The circle around the crosshair is your eyepiece's view, and On target means the target is inside it. Equatorial mounts get directions in RA and Dec." to
            "आकाश व दृश्य, टेलीस्कोप में: टेलीस्कोप और आईपीस की फ़ोकल लंबाई और आईपीस का आभासी क्षेत्र भरें। क्रॉसहेयर के चारों ओर का वृत्त आपके आईपीस का दृश्य है, और लक्ष्य पर का अर्थ है कि लक्ष्य उसके भीतर है। विषुवतीय माउंट के लिए दिशाएँ RA और Dec में मिलती हैं।",
        // Cardinal points
        "N" to "उ", "NE" to "उपू", "E" to "पू", "SE" to "दपू", "S" to "द", "SW" to "दप", "W" to "प", "NW" to "उप",
    )
}

fun t(en: String) = I18n.t(en)
