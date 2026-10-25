package app.cloven.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val threshold = 5 * 60_000L
    private val today = LocalDate.of(2026, 3, 10)
    private fun min(m: Int) = m * 60_000L
    private fun day(offset: Long, minutes: Int) = DailyTotal(today.plusDays(offset), min(minutes))

    @Test fun `no activity gives zero streak`() {
        val stats = StreakCalculator.compute(emptyList(), today, threshold)
        assertEquals(StreakStats(0, 0, 0, 0L, false), stats)
    }

    @Test fun `first qualifying day starts a streak of one`() {
        val stats = StreakCalculator.compute(listOf(day(0, 6)), today, threshold)
        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.longestStreak)
        assertTrue(stats.todayQualified)
    }

    @Test fun `reading under the threshold does not qualify`() {
        val stats = StreakCalculator.compute(listOf(day(0, 4)), today, threshold)
        assertEquals(0, stats.currentStreak)
        assertFalse(stats.todayQualified)
        assertEquals(min(4), stats.todayMs)
    }

    @Test fun `exactly the threshold qualifies`() {
        val stats = StreakCalculator.compute(listOf(DailyTotal(today, threshold)), today, threshold)
        assertEquals(1, stats.currentStreak)
    }

    @Test fun `consecutive days accumulate`() {
        val totals = listOf(day(-3, 10), day(-2, 7), day(-1, 30), day(0, 5))
        assertEquals(4, StreakCalculator.compute(totals, today, threshold).currentStreak)
    }

    @Test fun `streak survives until today ends if yesterday qualified`() {
        val totals = listOf(day(-2, 10), day(-1, 10))
        val stats = StreakCalculator.compute(totals, today, threshold)
        assertEquals(2, stats.currentStreak)
        assertFalse(stats.todayQualified)
    }

    @Test fun `missing a full day resets the current streak but keeps longest`() {
        val totals = listOf(day(-5, 10), day(-4, 10), day(-3, 10), day(-1, 0), day(0, 0))
        val stats = StreakCalculator.compute(totals, today, threshold)
        assertEquals(0, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
    }

    @Test fun `gap in the middle splits runs`() {
        val totals = listOf(day(-6, 10), day(-5, 10), day(-3, 10), day(-2, 10), day(-1, 10), day(0, 10))
        val stats = StreakCalculator.compute(totals, today, threshold)
        assertEquals(4, stats.currentStreak)
        assertEquals(4, stats.longestStreak)
        assertEquals(6, stats.qualifyingDays)
    }

    @Test fun `multiple sessions on one day are summed`() {
        val totals = listOf(DailyTotal(today, min(2)), DailyTotal(today, min(2)), DailyTotal(today, min(2)))
        val stats = StreakCalculator.compute(totals, today, threshold)
        assertTrue(stats.todayQualified)
        assertEquals(min(6), stats.todayMs)
    }

    @Test fun `returning after a long break starts again at one`() {
        val totals = listOf(day(-40, 30), day(-39, 30), day(0, 8))
        val stats = StreakCalculator.compute(totals, today, threshold)
        assertEquals(1, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
    }

    @Test fun `date recorded one day ahead after travelling west still anchors the streak`() {
        val totals = listOf(day(-1, 10), day(0, 10), day(1, 10))
        assertEquals(3, StreakCalculator.compute(totals, today, threshold).currentStreak)
    }

    @Test fun `dates far in the future are not used as anchor`() {
        val totals = listOf(day(5, 10))
        assertEquals(0, StreakCalculator.compute(totals, today, threshold).currentStreak)
    }

    @Test fun `negative durations are ignored`() {
        val totals = listOf(DailyTotal(today, -min(10)), DailyTotal(today, min(3)))
        assertFalse(StreakCalculator.compute(totals, today, threshold).todayQualified)
    }

    @Test fun `recent days lists seven days oldest first`() {
        val totals = listOf(day(-6, 1), day(0, 9))
        val week = StreakCalculator.recentDays(totals, today, threshold)
        assertEquals(7, week.size)
        assertEquals(today.minusDays(6), week.first().date)
        assertEquals(today, week.last().date)
        assertFalse(week.first().qualified)
        assertTrue(week.last().qualified)
    }

    @Test fun `computation is deterministic and order independent`() {
        val totals = listOf(day(-1, 10), day(-3, 10), day(0, 10), day(-2, 10))
        assertEquals(
            StreakCalculator.compute(totals, today, threshold),
            StreakCalculator.compute(totals.reversed(), today, threshold),
        )
    }
}
