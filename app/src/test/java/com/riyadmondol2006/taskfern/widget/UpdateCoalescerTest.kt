package com.riyadmondol2006.taskfern.widget

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCoalescerTest {
    private fun TestScope.coalescer(flushes: MutableList<Set<String>>) = UpdateCoalescer(
        scope = backgroundScope,
        quietMillis = 300,
        maxWaitMillis = 1_500,
        now = { testScheduler.currentTime },
    ) { flushes += it }

    @Test
    fun aBurstBecomesOneUpdateOfTheUnion() = runTest {
        val flushes = mutableListOf<Set<String>>()
        val c = coalescer(flushes)
        c.submit(setOf("today", "agenda"))
        advanceTimeBy(100)
        c.submit(setOf("shopping"))
        advanceTimeBy(100)
        c.submit(setOf("today"))
        advanceTimeBy(299)
        runCurrent()
        assertEquals(emptyList<Set<String>>(), flushes)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(setOf("today", "agenda", "shopping")), flushes)
    }

    @Test
    fun separateBurstsUpdateSeparately() = runTest {
        val flushes = mutableListOf<Set<String>>()
        val c = coalescer(flushes)
        c.submit(setOf("routine"))
        advanceTimeBy(400)
        runCurrent()
        c.submit(setOf("shopping"))
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf(setOf("routine"), setOf("shopping")), flushes)
    }

    @Test
    fun aSteadyStreamStillFlushesWithinTheMaximumWait() = runTest {
        val flushes = mutableListOf<Set<String>>()
        val c = coalescer(flushes)
        repeat(10) {
            c.submit(setOf("today"))
            advanceTimeBy(200)
            runCurrent()
        }
        // 2 s of events every 200 ms: never quiet for 300 ms, but the 1.5 s cap forces an update.
        assertEquals(1, flushes.size)
    }

    @Test
    fun discardedKindsAreNotUpdatedAgain() = runTest {
        val flushes = mutableListOf<Set<String>>()
        val c = coalescer(flushes)
        c.submit(setOf("today", "agenda"))
        c.discard(setOf("today", "agenda"))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(emptyList<Set<String>>(), flushes)

        c.submit(setOf("today", "quick_add"))
        c.discard(setOf("today"))
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf(setOf("quick_add")), flushes)
    }

    @Test
    fun emptySubmissionsAreIgnored() = runTest {
        val flushes = mutableListOf<Set<String>>()
        coalescer(flushes).submit(emptyList())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(emptyList<Set<String>>(), flushes)
    }
}
