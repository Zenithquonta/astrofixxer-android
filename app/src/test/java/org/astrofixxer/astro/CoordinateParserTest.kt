package org.astrofixxer.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {
    private val ra = 15 * (5 + (60 * 35 + 17.3) / 3600.0)
    private val dec = -(5 + (60 * 23 + 28.0) / 3600.0)

    @Test fun raFormats() {
        for (s in listOf("05:35:17.3", "5h35m17.3s", "5h 35m 17.3s", "5 35 17.3", "5h35'17.3\"")) {
            assertEquals(s, ra, CoordinateParser.parseRA(s)!!, 1e-9)
        }
        assertEquals(83.82, CoordinateParser.parseRA("83.82")!!, 1e-12)
        assertEquals(15 * (5 + 35 / 60.0), CoordinateParser.parseRA("05:35")!!, 1e-12)
    }

    @Test fun decFormats() {
        for (s in listOf("-05:23:28", "-5°23′28″", "-5d23m28s", "-5 23 28", "−05:23:28")) {
            assertEquals(s, dec, CoordinateParser.parseDEC(s)!!, 1e-9)
        }
        assertEquals(-5.391, CoordinateParser.parseDEC("-5.391")!!, 1e-12)
        assertEquals(-5.391, CoordinateParser.parseDEC("−5.391")!!, 1e-12) // Unicode minus: NaN in the web app
        assertEquals(41.27, CoordinateParser.parseDEC("+41.27")!!, 1e-12)
    }

    @Test fun rejectsOutOfRange() {
        for (s in listOf("24:00:00", "12:60:00", "12:00:60", "361.0", "abc", "")) assertNull(s, CoordinateParser.parseRA(s))
        for (s in listOf("91:00:00", "10:60:00", "-90.5", "x")) assertNull(s, CoordinateParser.parseDEC(s))
    }
}
