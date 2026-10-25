package app.cloven

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cloven.data.db.ClovenDatabase
import app.cloven.data.db.ReadingSessionEntity
import app.cloven.data.prefs.PreferencesRepository
import app.cloven.data.prefs.ReaderBackground
import app.cloven.data.prefs.ReaderPreferences
import app.cloven.data.repo.ActivityRepository
import app.cloven.domain.ClovenClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ActivityRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: ClovenDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File

    private val fixedClock = object : ClovenClock {
        override fun wallMillis() = Instant.parse("2026-03-10T12:00:00Z").toEpochMilli()
        override fun monotonicMillis() = 0L
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    @Before fun setUp() {
        db = ClovenDatabase.inMemory(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File(context.cacheDir, "test-${System.nanoTime()}.preferences_pb")
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
        prefsFile.delete()
    }

    private fun prefs() = PreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })

    private suspend fun session(id: String, date: String, minutes: Int) =
        db.sessionDao().upsert(ReadingSessionEntity(id, null, 0, 0, minutes * 60_000L, date, "UTC"))

    @Test fun streakSurvivesReopeningFromPersistedSessions() = runBlocking {
        session("1", "2026-03-08", 10)
        session("2", "2026-03-09", 6)
        session("3", "2026-03-10", 3)
        session("4", "2026-03-10", 3) // two short sessions add up to a qualifying day

        // A fresh repository instance reads the same persisted data (as after a restart).
        val repo = ActivityRepository(db.sessionDao(), prefs(), fixedClock)
        assertEquals(3, repo.currentStreak())
        val snapshot = repo.observeSnapshot().first()
        assertEquals(3, snapshot.stats.currentStreak)
        assertEquals(6 * 60_000L, snapshot.stats.todayMs)
        assertEquals(20, snapshot.dailyGoalMinutes)
        assertEquals(LocalDate.parse("2026-03-10"), snapshot.week.last().date)
        assertEquals(3, repo.observeCurrentStreak().first())
    }

    @Test fun dailyGoalAndReaderPreferencesPersist() = runBlocking {
        val first = prefs()
        first.setDailyGoal(45)
        first.setReaderPreferences(ReaderPreferences(fontSizeSp = 99, background = ReaderBackground.SEPIA))
        val goal = first.appSettings.first().dailyGoalMinutes
        val reader = first.readerPreferences.first()
        assertEquals(45, goal)
        assertEquals(ReaderPreferences.FONT_SIZE_RANGE.last, reader.fontSizeSp) // clamped
        assertEquals(ReaderBackground.SEPIA, reader.background)
        first.resetReaderPreferences()
        assertEquals(ReaderPreferences(), first.readerPreferences.first())
    }
}
