import org.astrofixxer.ui.I18n
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File

/** Every user-visible string in the UI code has a Hindi entry, so no screen falls back to English by accident. */
class I18nTest {
    private val ui = File("src/main/java/org/astrofixxer/ui")

    /** Strings that are symbols, or example text that is deliberately not translated. */
    private val untranslated = setOf(
        "RA / Dec", "+", "−", "×", "‹", "›", "M42, Orion Nebula, NGC 7000, Sirius, Jupiter…", "Comet C/2026 X, 05:35:17, -05:23:28",
        "Tonight: M31 M33 \"Double Cluster\" (low in NE)", "Stellarium catalogue: %,d objects, %d constellations, %d boundary edges.",
    )

    private fun unescape(s: String) = s.replace("\\\"", "\"").replace("\\n", "\n")

    @Ignore("Hindi interface switched off until a later release")
    @Test fun everyLiteralPassedToTHasAHindiEntry() {
        val literal = Regex("""\bt\("((?:[^"\\]|\\.)*)"\)""")
        val missing = mutableListOf<String>()
        I18n.language = "hi"
        try {
            for (f in ui.listFiles()!!.filter { it.extension == "kt" && it.name != "I18n.kt" }) {
                for (m in literal.findAll(f.readText())) {
                    val key = unescape(m.groupValues[1])
                    if (key in untranslated || key.none { it.isLetter() }) continue
                    if (I18n.t(key) == key) missing += "${f.name}: $key"
                }
            }
            // Help topics and tutorial pages are passed to t() as variables: check the pairs in SkyScreen.kt.
            val pair = Regex("""^\s+"((?:[^"\\]|\\.)*)" to (?:"((?:[^"\\]|\\.)*)"|LICENCES),?$""", RegexOption.MULTILINE)
            for (m in pair.findAll(File(ui, "SkyScreen.kt").readText())) {
                for (k in listOf(m.groupValues[1], m.groupValues[2]).filter { it.isNotEmpty() }) {
                    if (I18n.t(unescape(k)) == unescape(k) && k != "Licences and source") missing += "SkyScreen.kt help/tutorial: $k"
                }
            }
        } finally {
            I18n.language = "en"
        }
        assertTrue("strings with no Hindi entry:\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
