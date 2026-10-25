package app.cloven.data.repo

import app.cloven.data.db.DailyTotalRow
import app.cloven.data.db.ReadingSessionEntity
import app.cloven.data.db.SessionDao
import app.cloven.data.prefs.AppSettings
import app.cloven.data.prefs.PreferencesRepository
import app.cloven.domain.ClovenClock
import app.cloven.domain.DailyTotal
import app.cloven.domain.DayActivity
import app.cloven.domain.StreakCalculator
import app.cloven.domain.StreakStats
import app.cloven.tracking.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

data class ActivitySnapshot(
    val stats: StreakStats,
    val dailyGoalMinutes: Int,
    val week: List<DayActivity>,
) {
    val todayMinutes: Int get() = (stats.todayMs / 60_000L).toInt()
    val goalReached: Boolean get() = stats.todayMs >= dailyGoalMinutes * 60_000L
    val goalFraction: Float get() = (stats.todayMs.toFloat() / (dailyGoalMinutes * 60_000L)).coerceIn(0f, 1f)
}

/**
 * Reading activity derived from session rows. Daily totals are always aggregated from
 * sessions, so there is one source of truth for the app screen and the widget alike.
 */
class ActivityRepository(
    private val sessions: SessionDao,
    private val preferences: PreferencesRepository,
    private val clock: ClovenClock,
    val qualifyingMs: Long = StreakCalculator.DEFAULT_QUALIFYING_MS,
) : SessionStore {

    fun observeSnapshot(): Flow<ActivitySnapshot> =
        combine(sessions.observeDailyTotals(), preferences.appSettings, todayFlow()) { rows, settings, today ->
            snapshot(rows.toDomain(), settings, today)
        }

    fun observeCurrentStreak(): Flow<Int> =
        combine(sessions.observeDailyTotals(), todayFlow()) { rows, today ->
            StreakCalculator.compute(rows.toDomain(), today, qualifyingMs).currentStreak
        }.distinctUntilChanged()

    suspend fun currentStreak(): Int =
        StreakCalculator.compute(sessions.dailyTotals().toDomain(), clock.today(), qualifyingMs).currentStreak

    override suspend fun saveSession(session: ReadingSessionEntity) = sessions.upsert(session)

    suspend fun deleteSession(id: String) = sessions.delete(id)

    override suspend fun totalFor(date: LocalDate): Long = sessions.totalForDate(date.toString())

    private fun snapshot(totals: List<DailyTotal>, settings: AppSettings, today: LocalDate) = ActivitySnapshot(
        stats = StreakCalculator.compute(totals, today, qualifyingMs),
        dailyGoalMinutes = settings.dailyGoalMinutes,
        week = StreakCalculator.recentDays(totals, today, qualifyingMs, days = 7),
    )

    /** Emits today's date now and again shortly after each local midnight. */
    private fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            emit(clock.today())
            val now = ZonedDateTime.now(clock.zone())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone())
            delay(Duration.between(now, nextMidnight).toMillis() + 1_000L)
        }
    }.distinctUntilChanged()

    private fun List<DailyTotalRow>.toDomain(): List<DailyTotal> = mapNotNull { row ->
        runCatching { DailyTotal(LocalDate.parse(row.localDate), row.activeMs) }.getOrNull()
    }
}
