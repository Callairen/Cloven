package app.cloven.domain

import android.os.SystemClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Time source abstraction so streak and session logic can be tested with fixed dates.
 *
 * [wallMillis] is used for timestamps and calendar dates; [monotonicMillis] is used for
 * measuring elapsed reading time because it is unaffected by the user changing the clock.
 */
interface ClovenClock {
    fun wallMillis(): Long
    fun monotonicMillis(): Long
    fun zone(): ZoneId
    fun today(): LocalDate = Instant.ofEpochMilli(wallMillis()).atZone(zone()).toLocalDate()
}

object SystemClovenClock : ClovenClock {
    override fun wallMillis(): Long = System.currentTimeMillis()
    override fun monotonicMillis(): Long = SystemClock.elapsedRealtime()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
