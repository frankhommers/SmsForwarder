package cn.ppps.forwarder.utils

import org.junit.Assert.*
import org.junit.Test

/** Two fields per regular-expression replacement; storage stays pattern===replacement per line. */
class RegexReplaceRowsTest {
    private fun rows(raw: String) = ItemListCodec.split(raw, "\n").map { ItemListCodec.regexPair(it) }
    private fun store(rows: List<Pair<String, String>>) = ItemListCodec.join(rows.map { ItemListCodec.regexLine(it.first, it.second) }, "\n")

    @Test fun roundTrip() {
        val old = "(\\d{3})\\d+(\\d{4})===$1****$2\nkey=value===\n\\s+=== "
        val parsed = rows(old)
        assertEquals(listOf("(\\d{3})\\d+(\\d{4})" to "$1****$2", "key=value" to "", "\\s+" to " "), parsed)
        assertEquals("old value reopened unchanged", old, store(parsed))
        assertEquals("a=b===c", store(listOf("a=b" to "c")))
        assertEquals("empty replacement deletes the match", "x+===", store(listOf("x+" to "")))
        assertEquals("literal backslash-n is kept", "a===line\\nbreak", store(listOf("a" to "line\\nbreak")))
        assertEquals(emptyList<Pair<String, String>>(), rows(""))
    }

    @Test fun validation() {
        assertNull(ItemListCodec.regexProblem("key=value", ""))
        assertNull(ItemListCodec.regexProblem("(\\d{3})\\d+(\\d{4})", "$1****$2"))
        assertEquals(ItemListCodec.RegexProblem.PATTERN_REQUIRED, ItemListCodec.regexProblem(" ", "x"))
        assertEquals(ItemListCodec.RegexProblem.PATTERN_INVALID, ItemListCodec.regexProblem("(unclosed", "x"))
        assertEquals(ItemListCodec.RegexProblem.PATTERN_SEPARATOR, ItemListCodec.regexProblem("a===b", "x"))
        assertEquals(ItemListCodec.RegexProblem.PATTERN_TRAILING_EQUALS, ItemListCodec.regexProblem("a=", "x"))
        assertEquals(ItemListCodec.RegexProblem.PATTERN_LINE_BREAK, ItemListCodec.regexProblem("a\nb", "x"))
        assertEquals(ItemListCodec.RegexProblem.REPLACEMENT_SEPARATOR, ItemListCodec.regexProblem("a", "b===c"))
        assertEquals(ItemListCodec.RegexProblem.REPLACEMENT_LINE_BREAK, ItemListCodec.regexProblem("a", "b\nc"))
    }

    /** What entity/MsgInfo.regexReplace does with a stored line; valid rows must survive it intact. */
    @Test fun storedLinesMatchHowTheyAreApplied() {
        listOf("key=value" to "", "(\\d{3})\\d+(\\d{4})" to "$1****$2", "=x" to "y=").forEach { (p, r) ->
            assertNull(ItemListCodec.regexProblem(p, r))
            val parts = ItemListCodec.regexLine(p, r).split("===".toRegex())
            assertEquals(p, parts[0])
            assertEquals(r, if (parts.size >= 2) parts[1] else "")
        }
    }
}
