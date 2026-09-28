package org.astrofixxer.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserListsTest {
    @Test fun parsesUserObjectsWithErrorsPerLine() {
        val text = """
            # my targets
            Comet X, 05:35:17, -05:23:28
            Nova, 18h30m, +12°30′
            M42, 05:35:17, -05:23:28
            Broken, 25:00:00, 10
            Short line
            Comet X, 1.0, 2.0
        """.trimIndent()
        val r = UserLists.parseObjects(text) { it.uppercase() == "M42" }
        assertEquals(listOf("Comet X", "Nova"), r.objects.map { it.name })
        assertEquals(83.822, r.objects[0].ra, 0.01)
        assertEquals(12.5, r.objects[1].dec, 1e-9)
        assertEquals(4, r.errors.size)
        assertTrue(r.errors[0].contains("already in the catalogue"))
        assertTrue(r.errors[1].contains("invalid RA"))
        assertTrue(r.errors[2].contains("needs name, RA and Dec"))
        assertTrue(r.errors[3].contains("duplicate"))
    }

    @Test fun parsesWatchListsLikeTheWebApp() {
        val lists = UserLists.parseWatchLists("M31, M33 \"Double Cluster\" (low in the NE)\nAutumn: NGC7000 M15 (bring the 25mm)")
        assertEquals(listOf("default", "Autumn"), lists.map { it.name })
        assertEquals(listOf("M31", "M33", "Double Cluster"), lists[0].items.map { it.name })
        assertEquals("low in the NE", lists[0].items[2].comment)
        assertEquals(listOf("NGC7000", "M15"), lists[1].items.map { it.name })
        assertEquals("bring the 25mm", lists[1].items[1].comment)
        assertTrue(UserLists.parseWatchLists("   ").isEmpty())
    }

    @Test fun listNamesWithSpacesAtLineStart() {
        val lists = UserLists.parseWatchLists("Autumn galaxies: M31 M33 NGC891 (edge-on, faint)\n  Winter  sky : M42")
        assertEquals(listOf("Autumn galaxies", "Winter  sky"), lists.map { it.name })
        assertEquals(listOf("M31", "M33", "NGC891"), lists[0].items.map { it.name })
        assertEquals("edge-on, faint", lists[0].items[2].comment)
    }
}
