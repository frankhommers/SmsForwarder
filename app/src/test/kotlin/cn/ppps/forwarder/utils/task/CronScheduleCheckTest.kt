package cn.ppps.forwarder.utils.task

import gatewayapps.crondroid.CronExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Inline Cron editor checks use the task runner's crondroid parser; preview times equal its own next times. */
class CronScheduleCheckTest {
    private fun fields(expr: String) = expr.split(" ")

    @Test fun validExpressionsHaveNoProblems() {
        for (expr in listOf("* * * * * ? *", "0 0 12 ? * 2-6 *", "0/15 * * L * ? *", "0 30 8 ? * 2#1 *", "0 0 0 15W * ? 2030", "05,07 * * * * ? *")) {
            assertEquals(expr, emptyMap<Int, CronScheduleCheck.Problem>(), CronScheduleCheck.problems(fields(expr)))
        }
    }

    @Test fun invalidFieldIsReportedOnThatFieldOnly() {
        val p = CronScheduleCheck.problems(listOf("abc", "*", "*", "*", "*", "?", "*"))
        assertEquals(setOf(0), p.keys)
        assertTrue(p[0] is CronScheduleCheck.Problem.Field)
        assertEquals(setOf(2), CronScheduleCheck.problems(listOf("*", "*", "25", "*", "*", "?", "*")).keys)
        assertEquals(setOf(4), CronScheduleCheck.problems(listOf("*", "*", "*", "*", "13", "?", "*")).keys)
        assertEquals(setOf(5), CronScheduleCheck.problems(listOf("*", "*", "*", "?", "*", "9", "*")).keys)
    }

    @Test fun blankOrSpacedFieldIsReported() {
        assertEquals(CronScheduleCheck.Problem.Blank, CronScheduleCheck.problems(listOf("", "*", "*", "*", "*", "?", "*"))[0])
        assertEquals(CronScheduleCheck.Problem.Blank, CronScheduleCheck.problems(listOf("*", "1 2", "*", "*", "*", "?", "*"))[1])
    }

    @Test fun dayAndWeekClashMarksBothFields() {
        val p = CronScheduleCheck.problems(fields("* * * * * * *"))
        assertEquals(setOf(CronScheduleCheck.DAY, CronScheduleCheck.WEEK), p.keys)
        assertTrue(p.values.all { it is CronScheduleCheck.Problem.DayWeekConflict })
        assertEquals(setOf(CronScheduleCheck.DAY, CronScheduleCheck.WEEK), CronScheduleCheck.problems(fields("* * * ? * ? *")).keys)
    }

    @Test fun editorAgreesWithRunnerParser() {
        val samples = listOf("* * * * * ? *", "abc * * * * ? *", "* * * * * * *", "0 0 25 * * ? *", "0 0 0 32 * ? *", "0 0 0 ? * 8 *", "0 0 0 ? * MON-FRI *", "0 0 0 L-3 * ? *", "1-70 * * * * ? *")
        for (expr in samples) {
            val runnerAccepts = try { CronExpression.validateExpression(expr); true } catch (_: Exception) { false }
            assertEquals(expr, runnerAccepts, CronScheduleCheck.problems(fields(expr)).isEmpty())
        }
    }

    @Test fun nextRunsAreTheRunnersNextTimes() {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getDefault() }
        val start = format.parse("2026-11-16 10:00:00")!!
        val runs = CronScheduleCheck.nextRuns("0 30 8 ? * 2-6 *", start, 10)
        assertEquals(10, runs.size)
        assertEquals("2026-11-17 08:30:00", format.format(runs[0]))
        assertEquals("weekend skipped", "2026-11-23 08:30:00", format.format(runs[4]))
        val parser = CronExpression("0 30 8 ? * 2-6 *")
        var previous = start
        for (run in runs) {
            assertEquals(parser.getNextValidTimeAfter(previous), run)
            previous = run
        }
    }

    @Test fun pastOnlyScheduleHasNoRuns() {
        val start = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-11-16")!!
        assertEquals(emptyList<java.util.Date>(), CronScheduleCheck.nextRuns("0 0 0 1 1 ? 2021", start, 10))
    }
}
