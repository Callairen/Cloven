package app.cloven.domain

/**
 * Measures active reading time from a monotonic clock.
 *
 * Time accrues only between [start] and [stop], and only up to [idleTimeoutMs] after the
 * most recent [interaction]. A reader left open on a table therefore stops counting after
 * the idle timeout instead of inflating the streak. Not thread-safe: call from one thread.
 */
class ActiveTimeAccumulator(private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS) {

    private var running = false
    private var segmentStart = 0L
    private var lastInteraction = 0L
    private var banked = 0L

    val isRunning: Boolean get() = running

    fun start(now: Long) {
        if (running) return
        running = true
        segmentStart = now
        lastInteraction = now
    }

    fun interaction(now: Long) {
        if (!running) return
        if (now - lastInteraction > idleTimeoutMs) {
            // The reader was idle: bank what counted before going idle, resume counting now.
            banked += activeSpan(now)
            segmentStart = now
        }
        lastInteraction = now
    }

    fun stop(now: Long) {
        if (!running) return
        banked += activeSpan(now)
        running = false
    }

    /** Total active milliseconds since construction or the last [drain]. */
    fun total(now: Long): Long = banked + if (running) activeSpan(now) else 0L

    /** Returns [total] and resets the count to zero, keeping the running state. */
    fun drain(now: Long): Long {
        val value = total(now)
        banked = 0L
        // Counting resumes from 'now'; any remaining idle allowance is kept.
        if (running) segmentStart = now
        return value
    }

    private fun activeSpan(now: Long): Long {
        val end = minOf(now, lastInteraction + idleTimeoutMs)
        return (end - segmentStart).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MS: Long = 5 * 60_000L
    }
}
