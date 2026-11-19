package cn.ppps.forwarder.fragment

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * What the Logs filter dialog shows and applies, independent of views.
 *
 * [toFilter] produces exactly the map LogsFragment always handed to MsgViewModel.setFilter (same keys,
 * value types and list order), so filtering and "delete filtered logs" behave as before. [fromFilter] reads
 * that map back so the dialog reopens with the applied selection, never with abandoned (cancelled) edits.
 */
data class LogsFilterSelection(
    val from: String = "",
    val content: String = "",
    val title: String = "",
    val startTime: String = "",
    val endTime: String = "",
    /** -1 = any SIM, 0 = SIM 1, 1 = SIM 2 (Msg.sim_slot). */
    val simSlot: Int = -1,
    /** Call types 1..6 (Msg.call_type); empty = all. */
    val callTypes: Set<Int> = emptySet(),
    /** Forward statuses 0 = failed, 1 = processing, 2 = success; empty = all. */
    val forwardStatuses: Set<Int> = emptySet(),
) {
    fun toFilter(type: String): MutableMap<String, Any> {
        val filter = mutableMapOf<String, Any>()
        filter["from"] = from.trim()
        filter["content"] = content.trim()
        filter["title"] = title.trim()
        filter["start_time"] = startTime.trim()
        filter["end_time"] = endTime.trim()
        filter["sim_slot"] = if (type == "app") -1 else simSlot
        if (type == "call") filter["call_type"] = CALL_TYPES.filter { it in callTypes }.toMutableList()
        filter["forward_status"] = FORWARD_STATUSES.filter { it in forwardStatuses }.toMutableList()
        return filter
    }

    /** True when both times are set and the end lies before the start (such a filter can never match). */
    fun endBeforeStart(): Boolean {
        val start = parseTime(startTime) ?: return false
        val end = parseTime(endTime) ?: return false
        return end < start
    }

    companion object {
        /** Storage/display format of the time fields; MsgViewModel parses the same pattern. */
        const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
        val CALL_TYPES = listOf(1, 2, 3, 4, 5, 6)
        val FORWARD_STATUSES = listOf(0, 1, 2)

        fun fromFilter(filter: Map<String, Any>): LogsFilterSelection = LogsFilterSelection(
            from = filter["from"]?.toString().orEmpty(),
            content = filter["content"]?.toString().orEmpty(),
            title = filter["title"]?.toString().orEmpty(),
            startTime = filter["start_time"]?.toString().orEmpty(),
            endTime = filter["end_time"]?.toString().orEmpty(),
            simSlot = (filter["sim_slot"] as? Int)?.takeIf { it == 0 || it == 1 } ?: -1,
            callTypes = (filter["call_type"] as? Collection<*>)?.filterIsInstance<Int>()?.toSet().orEmpty(),
            forwardStatuses = (filter["forward_status"] as? Collection<*>)?.filterIsInstance<Int>()?.toSet().orEmpty(),
        )

        fun parseTime(value: String): Long? {
            if (value.isBlank()) return null
            return try {
                SimpleDateFormat(TIME_PATTERN, Locale.US).apply { isLenient = false }.parse(value.trim())?.time
            } catch (_: ParseException) {
                null
            }
        }
    }
}
