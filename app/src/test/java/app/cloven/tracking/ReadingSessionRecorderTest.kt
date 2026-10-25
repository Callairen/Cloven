package app.cloven.tracking

import app.cloven.data.db.ReadingSessionEntity
import app.cloven.domain.ClovenClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private class FakeClock(start: Instant, private val zoneId: ZoneId = ZoneId.of("UTC")) : ClovenClock {
    var wall = start.toEpochMilli()
    var mono = 1_000_000L
    fun advance(ms: Long) {
        wall += ms
        mono += ms
    }
    override fun wallMillis() = wall
    override fun monotonicMillis() = mono
    override fun zone(): ZoneId = zoneId
}

private class FakeStore : SessionStore {
    val sessions = linkedMapOf<String, ReadingSessionEntity>()
    override suspend fun saveSession(session: ReadingSessionEntity) {
        sessions[session.id] = session
    }
    override suspend fun totalFor(date: LocalDate): Long =
        sessions.values.filter { it.localDate == date.toString() }.sumOf { it.activeMs }
    fun total() = sessions.values.sumOf { it.activeMs }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionRecorderTest {

    private val minute = 60_000L
    private val threshold = 5 * minute

    private fun TestScope.recorder(clock: FakeClock, store: FakeStore, onChange: () -> Unit = {}) =
        ReadingSessionRecorder("book", store, clock, backgroundScope, threshold, onChange, flushIntervalMs = 30_000L)

    /** Reads for [minutes], interacting once per minute. */
    private fun read(recorder: ReadingSessionRecorder, clock: FakeClock, minutes: Int) = repeat(minutes) {
        clock.advance(minute)
        recorder.onInteraction()
    }

    @Test fun `foreground reading is recorded as one session`() = runTest {
        val clock = FakeClock(Instant.parse("2026-03-10T10:00:00Z"))
        val store = FakeStore()
        val r = recorder(clock, store)
        r.onForeground()
        read(r, clock, 3)
        r.onBackground()
        runCurrent()
        assertEquals(1, store.sessions.size)
        assertEquals(3 * minute, store.total())
        assertEquals("2026-03-10", store.sessions.values.single().localDate)
    }

    @Test fun `time in background is not counted`() = runTest {
        val clock = FakeClock(Instant.parse("2026-03-10T10:00:00Z"))
        val store = FakeStore()
        val r = recorder(clock, store)
        r.onForeground(); read(r, clock, 1); r.onBackground()
        clock.advance(30 * minute) // phone locked
        r.onForeground(); read(r, clock, 1); r.onBackground()
        runCurrent()
        assertEquals(2, store.sessions.size)
        assertEquals(2 * minute, store.total())
    }

    @Test fun `idle reader stops counting after timeout`() = runTest {
        val clock = FakeClock(Instant.parse("2026-03-10T10:00:00Z"))
        val store = FakeStore()
        val r = recorder(clock, store)
        r.onForeground()
        clock.advance(40 * minute) // left open, no interaction
        r.onBackground()
        runCurrent()
        assertEquals(5 * minute, store.total())
    }

    @Test fun `open sheets and menus are not reading time`() = runTest {
        val clock = FakeClock(Instant.parse("2026-03-10T10:00:00Z"))
        val store = FakeStore()
        val r = recorder(clock, store)
        r.onForeground(); read(r, clock, 1)
        r.setBlockedByUi(true); clock.advance(4 * minute)
        r.setBlockedByUi(false); read(r, clock, 1)
        r.onBackground()
        runCurrent()
        assertEquals(2 * minute, store.total())
    }

    @Test fun `session crossing midnight is split by local date`() = runTest {
        val clock = FakeClock(Instant.parse("2026-03-09T23:58:00Z"))
        val store = FakeStore()
        val r = recorder(clock, store)
        r.onForeground()
        read(r, clock, 2) // now 00:00 on the 10th
        r.flush()
        read(r, clock, 3)
        r.onBackground()
        runCurrent()
        assertEquals(2 * minute, store.totalFor(LocalDate.parse("2026-03-09")))
        assertEquals(3 * minute, store.totalFor(LocalDate.parse("2026-03-10")))
    }

    @Test fun `periodic flush persists progress and never double counts`() = runTest {
        val clock = FakeClock(Instant.parse("2026-03-10T10:00:00Z"))
        val store = FakeStore()
        val r = recorder(clock, store)
        r.onForeground()
        read(r, clock, 1)
        advanceTimeBy(30_001); runCurrent() // ticker flush; simulates state before process death
        assertEquals(minute, store.total())
        read(r, clock, 1)
        advanceTimeBy(30_001); runCurrent()
        assertEquals(1, store.sessions.size)
        assertEquals(2 * minute, store.total())
        r.onBackground(); runCurrent()
        assertEquals(2 * minute, store.total())
    }

    @Test fun `streak change is signalled when the day first qualifies and on close`() = runTest {
        val clock = FakeClock(Instant.parse("2026-03-10T10:00:00Z"))
        val store = FakeStore()
        var signals = 0
        val r = recorder(clock, store) { signals++ }
        r.onForeground()
        read(r, clock, 4); r.flush(); runCurrent()
        assertEquals(0, signals)
        read(r, clock, 2); r.flush(); runCurrent()
        assertEquals(1, signals) // crossed 5 minutes
        read(r, clock, 1); r.flush(); runCurrent()
        assertEquals(1, signals) // no repeat
        r.onBackground(); runCurrent()
        assertEquals(2, signals) // session closed
    }

    @Test fun `zero length visits leave no rows`() = runTest {
        val clock = FakeClock(Instant.parse("2026-03-10T10:00:00Z"))
        val store = FakeStore()
        val r = recorder(clock, store)
        r.onForeground(); r.onBackground(); runCurrent()
        assertEquals(0, store.sessions.size)
    }
}
