package org.astrofixxer.astro

/** The web app's user objects ("name, RA, Dec" per line) and watch lists, with the same input formats. */
object UserLists {
    class ParsedObjects(val objects: List<SkyObject>, val errors: List<String>)

    /** [exists] tells whether a name is already in the catalogue (user objects must not shadow it). */
    fun parseObjects(text: String, exists: (String) -> Boolean): ParsedObjects {
        val objects = mutableListOf<SkyObject>()
        val errors = mutableListOf<String>()
        val seen = HashSet<String>()
        text.lines().forEachIndexed { i, raw ->
            if (raw.isBlank() || raw.trimStart().startsWith("#")) return@forEachIndexed
            val cols = raw.split(',').map { it.trim() }
            val line = i + 1
            if (cols.size < 3) { errors += "Line $line: needs name, RA and Dec separated by commas"; return@forEachIndexed }
            val name = cols[0]
            if (name.isEmpty()) { errors += "Line $line: empty object name"; return@forEachIndexed }
            val key = Catalog.normalizeName(name)
            if (exists(name)) { errors += "Line $line: $name is already in the catalogue"; return@forEachIndexed }
            if (!seen.add(key)) { errors += "Line $line: duplicate object $name"; return@forEachIndexed }
            val ra = CoordinateParser.parseRA(cols[1])
            if (ra == null) { errors += "Line $line: invalid RA '${cols[1]}' (use 05:35:17, 5h35m17s or degrees)"; return@forEachIndexed }
            val dec = CoordinateParser.parseDEC(cols[2])
            if (dec == null) { errors += "Line $line: invalid Dec '${cols[2]}' (use -05:23:28, -5°23′28″ or degrees)"; return@forEachIndexed }
            objects += SkyObject(name, ra, dec, null, "U")
        }
        return ParsedObjects(objects, errors)
    }

    class Item(val name: String, var comment: String = "")
    class WatchList(val name: String, val items: MutableList<Item>)

    private val token = Regex("\"[^\"]*\"|[:()]|[^\\s:,\"()]+")

    /** "Tonight: M31 M33 "Double Cluster" (low in NE), Autumn: NGC7000" -> named lists of items. */
    fun parseWatchLists(text: String): List<WatchList> {
        val items = token.findAll(text).map { it.value }.toList()
        val lists = LinkedHashMap<String, WatchList>()
        var current = "default"
        var i = 0
        while (i < items.size) {
            val item = items[i].replace("\"", "")
            if (i + 1 < items.size && items[i + 1] == ":") {
                current = item
                i += 2
                continue
            }
            if (item == "(") {
                val start = ++i
                while (i < items.size && items[i] != ")") i++
                if (i > start) lists[current]?.items?.lastOrNull()?.comment = items.subList(start, i).joinToString(" ")
                i++
                continue
            }
            if (item != ")" && item != ":") lists.getOrPut(current) { WatchList(current, mutableListOf()) }.items += Item(item)
            i++
        }
        return lists.values.toList()
    }
}
