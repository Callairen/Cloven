package app.cloven.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveTimeAccumulatorTest {

    private val idle = 5 * 60_000L

    @Test fun `counts time between start and stop`() {
        val acc = ActiveTimeAccumulator(idle)
        acc.start(1_000)
        acc.interaction(30_000)
        acc.stop(61_000)
        assertEquals(60_000, acc.total(500_000))
    }

    @Test fun `nothing counts while stopped (backgrounded)`() {
        val acc = ActiveTimeAccumulator(idle)
        acc.start(0)
        acc.stop(10_000)
        acc.interaction(20_000) // ignored while stopped
        acc.start(1_000_000)
        acc.stop(1_005_000)
        assertEquals(15_000, acc.total(2_000_000))
    }

    @Test fun `stops accruing after idle timeout`() {
        val acc = ActiveTimeAccumulator(idle)
        acc.start(0)
        // no interaction for 20 minutes
        assertEquals(idle, acc.total(20 * 60_000L))
    }

    @Test fun `interaction after idle resumes counting without crediting the idle gap`() {
        val acc = ActiveTimeAccumulator(idle)
        acc.start(0)
        acc.interaction(20 * 60_000L) // came back after 20 min
        acc.stop(21 * 60_000L)
        assertEquals(idle + 60_000L, acc.total(30 * 60_000L))
    }

    @Test fun `drain returns accrued time and resets`() {
        val acc = ActiveTimeAccumulator(idle)
        acc.start(0)
        assertEquals(30_000, acc.drain(30_000))
        acc.interaction(40_000)
        assertEquals(30_000, acc.drain(60_000))
        acc.stop(90_000)
        assertEquals(30_000, acc.drain(100_000))
        assertEquals(0, acc.total(200_000))
    }

    @Test fun `double start and double stop are harmless`() {
        val acc = ActiveTimeAccumulator(idle)
        acc.start(0)
        acc.start(5_000)
        acc.stop(10_000)
        acc.stop(20_000)
        assertEquals(10_000, acc.total(30_000))
    }

    @Test fun `clock going backwards never yields negative time`() {
        val acc = ActiveTimeAccumulator(idle)
        acc.start(10_000)
        acc.stop(5_000)
        assertEquals(0, acc.total(5_000))
    }
}
