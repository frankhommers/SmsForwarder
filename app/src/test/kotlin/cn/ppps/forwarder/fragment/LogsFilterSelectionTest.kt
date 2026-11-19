package cn.ppps.forwarder.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filter semantics of the Logs filter dialog: the map handed to MsgViewModel must not change. */
class LogsFilterSelectionTest {

    /** The exact map the previous Search handler built from these inputs (same keys, value types, list order). */
    private fun legacyFilter(type: String, from: String, content: String, title: String, start: String, end: String,
                             simButton: Int, calls: Set<Int>, statuses: Set<Int>): MutableMap<String, Any> {
        val filter = mutableMapOf<String, Any>()
        filter["from"] = from.trim()
        filter["content"] = content.trim()
        filter["title"] = title.trim()
        filter["start_time"] = start.trim()
        filter["end_time"] = end.trim()
        filter["sim_slot"] = if (type == "app") -1 else when (simButton) { 1 -> 0; 2 -> 1; else -> -1 }
        if (type == "call") filter["call_type"] = mutableListOf<Int>().apply { for (t in 1..6) if (t in calls) add(t) }
        filter["forward_status"] = mutableListOf<Int>().apply { for (s in 0..2) if (s in statuses) add(s) }
        return filter
    }

    @Test fun matchesLegacyMapForEveryTypeAndSimChoice() {
        for (type in listOf("sms", "call", "app")) for (sim in 0..2) {
            val calls = setOf(5, 3, 1)
            val statuses = setOf(2, 0)
            val selection = LogsFilterSelection(" 0612 ", " code ", " Bank ", "2026-01-02 03:04:00", "2026-02-03 04:05:59",
                simSlot = when (sim) { 1 -> 0; 2 -> 1; else -> -1 }, callTypes = calls, forwardStatuses = statuses)
            val expected = legacyFilter(type, " 0612 ", " code ", " Bank ", "2026-01-02 03:04:00", "2026-02-03 04:05:59", sim, calls, statuses)
            assertEquals("$type/$sim", expected, selection.toFilter(type))
            assertEquals(expected.keys, selection.toFilter(type).keys)
        }
    }

    @Test fun listsAreSortedMutableIntListsAndCallTypeOnlyForCalls() {
        val selection = LogsFilterSelection(callTypes = setOf(6, 2, 4), forwardStatuses = setOf(1, 0))
        val call = selection.toFilter("call")
        assertEquals(listOf(2, 4, 6), call["call_type"])
        assertEquals(listOf(0, 1), call["forward_status"])
        assertTrue(call["call_type"] is MutableList<*>)
        assertFalse(selection.toFilter("sms").containsKey("call_type"))
        assertFalse(selection.toFilter("app").containsKey("call_type"))
        assertEquals(-1, LogsFilterSelection(simSlot = 1).toFilter("app")["sim_slot"])
        assertEquals(1, LogsFilterSelection(simSlot = 1).toFilter("sms")["sim_slot"])
    }

    @Test fun emptySelectionAppliesNoRestriction() {
        val filter = LogsFilterSelection().toFilter("sms")
        assertEquals(mutableMapOf<String, Any>("from" to "", "content" to "", "title" to "", "start_time" to "", "end_time" to "",
            "sim_slot" to -1, "forward_status" to mutableListOf<Int>()), filter)
    }

    @Test fun reopeningRestoresAppliedSelection() {
        val applied = LogsFilterSelection("a", "b", "c", "2026-01-02 03:04:00", "2026-01-02 05:00:59", 0, setOf(1, 6), setOf(2))
        for (type in listOf("sms", "call", "app")) {
            val restored = LogsFilterSelection.fromFilter(applied.toFilter(type))
            assertEquals(type, applied.toFilter(type), restored.toFilter(type))
        }
        // No applied filter (fresh, after Reset or after switching tab) reopens a blank form.
        assertEquals(LogsFilterSelection(), LogsFilterSelection.fromFilter(emptyMap()))
        assertEquals(-1, LogsFilterSelection.fromFilter(mapOf("sim_slot" to 7)).simSlot)
    }

    @Test fun endBeforeStartDetection() {
        assertTrue(LogsFilterSelection(startTime = "2026-03-01 10:00:00", endTime = "2026-03-01 09:59:59").endBeforeStart())
        assertFalse(LogsFilterSelection(startTime = "2026-03-01 10:00:00", endTime = "2026-03-01 10:00:00").endBeforeStart())
        assertFalse(LogsFilterSelection(startTime = "2026-03-01 10:00:00").endBeforeStart())
        assertFalse(LogsFilterSelection(endTime = "2026-03-01 10:00:00").endBeforeStart())
        assertFalse(LogsFilterSelection(startTime = "garbage", endTime = "2026-03-01 10:00:00").endBeforeStart())
        assertNull(LogsFilterSelection.parseTime("2026-13-45 99:00:00"))
        assertEquals("yyyy-MM-dd HH:mm:ss", LogsFilterSelection.TIME_PATTERN)
    }

    @Test fun actionRowFitDecision() {
        assertTrue(LogsFilterActionBar.fitsInRow(listOf(100, 100, 100), 8, 316))
        assertFalse(LogsFilterActionBar.fitsInRow(listOf(100, 100, 100), 8, 315))
        assertTrue(LogsFilterActionBar.fitsInRow(emptyList(), 8, 0))
    }
}
