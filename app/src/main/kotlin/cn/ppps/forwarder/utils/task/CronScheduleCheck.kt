package cn.ppps.forwarder.utils.task

import gatewayapps.crondroid.CronExpression
import java.text.ParseException
import java.util.Date

/**
 * Editor-side checks for the seven Quartz cron fields of the Cron condition.
 *
 * Uses the same crondroid parser as CronWorker and TasksEditFragment, so the editor accepts exactly the schedules
 * the task runner accepts, and the preview times are the times the runner computes. Local only: nothing is
 * scheduled, no task runs and no network is used.
 */
object CronScheduleCheck {
    const val FIELDS = 7
    const val DAY = 3
    const val WEEK = 5

    /** Key for a problem of the whole expression that no single field explains. */
    const val WHOLE = -1

    sealed class Problem {
        /** Empty, or contains a space (that would shift every following field). */
        object Blank : Problem()

        /** The parser rejects this field on its own; [detail] is the parser's (English) message. */
        data class Field(val detail: String) : Problem()

        /** Each field is valid alone, but Quartz needs exactly one of day of month and day of week to be '?'. */
        data class DayWeekConflict(val detail: String) : Problem()

        /** Each field is valid alone, but the parser rejects the combination for another reason. */
        data class Whole(val detail: String) : Problem()
    }

    fun expression(fields: List<String>): String = fields.joinToString(" ") { it.trim() }

    /** Problems by field index (0 = second … 6 = year, or [WHOLE]); empty when the whole expression is valid. */
    fun problems(fields: List<String>): Map<Int, Problem> {
        require(fields.size == FIELDS) { "Expected $FIELDS cron fields" }
        val out = linkedMapOf<Int, Problem>()
        fields.forEachIndexed { index, raw ->
            val value = raw.trim()
            if (value.isEmpty() || value.any { it.isWhitespace() }) {
                out[index] = Problem.Blank
                return@forEachIndexed
            }
            // Check the field alone: every other field neutral, and exactly one of day/week set to '?'.
            val probe = MutableList(FIELDS) { "*" }
            probe[WEEK] = "?"
            if (index == DAY && value == "?") probe[WEEK] = "*"
            if (index == WEEK) probe[DAY] = if (value == "?") "*" else "?"
            probe[index] = value
            parseError(probe.joinToString(" "))?.let { out[index] = Problem.Field(it) }
        }
        if (out.isNotEmpty()) return out
        parseError(expression(fields))?.let { detail ->
            if ((fields[DAY].trim() == "?") == (fields[WEEK].trim() == "?")) {
                val problem = Problem.DayWeekConflict(detail)
                out[DAY] = problem
                out[WEEK] = problem
            } else out[WHOLE] = Problem.Whole(detail)
        }
        return out
    }

    /** The next [count] run times after [after], as the task runner computes them; fewer when the schedule ends. */
    fun nextRuns(expression: String, after: Date, count: Int): List<Date> {
        val cron = CronExpression(expression)
        val out = mutableListOf<Date>()
        var time = after
        while (out.size < count) {
            time = cron.getNextValidTimeAfter(time) ?: break
            out.add(time)
        }
        return out
    }

    private fun parseError(expression: String): String? = try {
        CronExpression.validateExpression(expression)
        null
    } catch (e: ParseException) {
        e.message.orEmpty()
    } catch (e: RuntimeException) {
        // crondroid can throw StringIndexOutOfBounds/NumberFormat for some malformed input; treat as invalid.
        e.message ?: e.javaClass.simpleName
    }
}
