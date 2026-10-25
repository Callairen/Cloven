package app.cloven.domain

import java.time.LocalDate

/** Total active reading time recorded for one local calendar date. */
data class DailyTotal(val date: LocalDate, val activeMs: Long)

data class StreakStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val qualifyingDays: Int,
    val todayMs: Long,
    val todayQualified: Boolean,
) {
    companion object {
        val EMPTY = StreakStats(0, 0, 0, 0L, false)
    }
}

data class DayActivity(val date: LocalDate, val activeMs: Long, val qualified: Boolean)

/**
 * Deterministic streak rules, derived purely from daily totals.
 *
 * - A day qualifies when its total active reading time is at least the threshold.
 * - The current streak counts consecutive qualifying days ending today, or ending yesterday
 *   when today has not qualified yet (so the streak doesn't read 0 every morning).
 * - A qualifying date one day *after* today is accepted as the anchor: it can occur after
 *   travelling west across time zones, where reading was recorded on a "later" local date.
 */
object StreakCalculator {

    const val DEFAULT_QUALIFYING_MS: Long = 5 * 60_000L

    fun compute(totals: Iterable<DailyTotal>, today: LocalDate, thresholdMs: Long): StreakStats {
        val byDate = sumByDate(totals)
        val qualifying = byDate.filterValues { it >= thresholdMs }.keys.toSortedSet()
        if (qualifying.isEmpty()) {
            return StreakStats(0, 0, 0, byDate[today] ?: 0L, false)
        }

        val anchor = when {
            today.plusDays(1) in qualifying -> today.plusDays(1)
            today in qualifying -> today
            today.minusDays(1) in qualifying -> today.minusDays(1)
            else -> null
        }
        var current = 0
        if (anchor != null) {
            var day: LocalDate = anchor
            while (day in qualifying) {
                current++
                day = day.minusDays(1)
            }
        }

        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in qualifying) {
            run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
            longest = maxOf(longest, run)
            previous = date
        }

        return StreakStats(
            currentStreak = current,
            longestStreak = maxOf(longest, current),
            qualifyingDays = qualifying.size,
            todayMs = byDate[today] ?: 0L,
            todayQualified = today in qualifying,
        )
    }

    /** The [days] most recent calendar days ending with [today], oldest first. */
    fun recentDays(totals: Iterable<DailyTotal>, today: LocalDate, thresholdMs: Long, days: Int = 7): List<DayActivity> {
        val byDate = sumByDate(totals)
        return (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val ms = byDate[date] ?: 0L
            DayActivity(date, ms, ms >= thresholdMs)
        }
    }

    private fun sumByDate(totals: Iterable<DailyTotal>): Map<LocalDate, Long> =
        totals.groupingBy { it.date }.fold(0L) { acc, t -> acc + t.activeMs.coerceAtLeast(0L) }
}
