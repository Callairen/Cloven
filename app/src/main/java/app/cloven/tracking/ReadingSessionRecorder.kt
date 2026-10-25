package app.cloven.tracking

import app.cloven.data.db.ReadingSessionEntity
import app.cloven.domain.ActiveTimeAccumulator
import app.cloven.domain.ClovenClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

/** Persistence used by [ReadingSessionRecorder]; implemented by ActivityRepository. */
interface SessionStore {
    suspend fun saveSession(session: ReadingSessionEntity)
    suspend fun totalFor(date: LocalDate): Long
}

/**
 * Records active reading time for one open book.
 *
 * - Time accrues only while the reader is in the foreground ([onForeground]/[onBackground])
 *   and no in-reader sheet or dialog is open ([setBlockedByUi]); idle time beyond
 *   [ActiveTimeAccumulator.DEFAULT_IDLE_TIMEOUT_MS] without interaction is not counted.
 * - The session row is upserted every [flushIntervalMs], so process death loses at most one
 *   interval, and the same row is never counted twice.
 * - When the local date changes mid-session, the current row is closed and a new row starts
 *   on the new date, so each row belongs to exactly one calendar day.
 */
class ReadingSessionRecorder(
    private val bookId: String,
    private val store: SessionStore,
    private val clock: ClovenClock,
    private val scope: CoroutineScope,
    private val qualifyingMs: Long,
    private val onStreakMayHaveChanged: () -> Unit,
    private val flushIntervalMs: Long = 30_000L,
    idleTimeoutMs: Long = ActiveTimeAccumulator.DEFAULT_IDLE_TIMEOUT_MS,
) {
    private class Current(val id: String, val localDate: String, val zoneId: String, val startedAt: Long) {
        var activeMs = 0L
        fun entity(bookId: String, endedAt: Long) =
            ReadingSessionEntity(id, bookId, startedAt, endedAt, activeMs, localDate, zoneId)
    }

    private val lock = Any()
    private val accumulator = ActiveTimeAccumulator(idleTimeoutMs)
    private val writeMutex = Mutex()
    private var current: Current? = null
    private var foreground = false
    private var blockedByUi = false
    private var ticker: Job? = null
    private var qualifiedNotifiedFor: String? = null

    fun onForeground() = synchronized(lock) {
        foreground = true
        reconcile()
    }

    fun onBackground(): Job {
        synchronized(lock) {
            foreground = false
            reconcile()
        }
        return flush(close = true)
    }

    fun setBlockedByUi(blocked: Boolean) = synchronized(lock) {
        blockedByUi = blocked
        reconcile()
    }

    fun onInteraction() = synchronized(lock) {
        accumulator.interaction(clock.monotonicMillis())
    }

    fun close(): Job = onBackground()

    /** Persists accrued time. With [close], the session ends and the next foreground starts a new one. */
    fun flush(close: Boolean = false): Job {
        val toSave = ArrayList<ReadingSessionEntity>(2)
        synchronized(lock) {
            val session = current ?: return Job().apply { complete() }
            val wall = clock.wallMillis()
            session.activeMs += accumulator.drain(clock.monotonicMillis())
            toSave += session.entity(bookId, wall)
            val today = clock.today().toString()
            current = when {
                close -> null
                today != session.localDate -> newSession(wall)
                else -> session
            }
        }
        return scope.launch {
            writeMutex.withLock {
                val saved = toSave.filter { it.activeMs > 0 }
                saved.forEach { store.saveSession(it) }
                if (saved.isEmpty() && !close) return@withLock
                val today = clock.today()
                val qualifiesNow = store.totalFor(today) >= qualifyingMs
                val firstQualification = qualifiesNow && qualifiedNotifiedFor != today.toString()
                if (firstQualification) qualifiedNotifiedFor = today.toString()
                if (firstQualification || (close && saved.isNotEmpty())) onStreakMayHaveChanged()
            }
        }
    }

    /** Must hold [lock]. */
    private fun reconcile() {
        val now = clock.monotonicMillis()
        val shouldRun = foreground && !blockedByUi
        if (shouldRun && !accumulator.isRunning) {
            if (current == null) current = newSession(clock.wallMillis())
            accumulator.start(now)
        } else if (!shouldRun && accumulator.isRunning) {
            accumulator.stop(now)
        }
        if (foreground && ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive) {
                    delay(flushIntervalMs)
                    flush()
                }
            }
        } else if (!foreground) {
            ticker?.cancel()
            ticker = null
        }
    }

    private fun newSession(startedAt: Long) = Current(
        id = UUID.randomUUID().toString(),
        localDate = clock.today().toString(),
        zoneId = clock.zone().id,
        startedAt = startedAt,
    )
}
