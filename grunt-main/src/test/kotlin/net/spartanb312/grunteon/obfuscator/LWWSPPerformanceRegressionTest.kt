package net.spartanb312.grunteon.obfuscator

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.spartanb312.grunteon.obfuscator.util.LWWSP
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.test.*

class LWWSPPerformanceRegressionTest {
    private class Scope(val worker: Int) {
        var thread: Thread? = null
        var count = 0
    }

    @Test
    fun everyIndexRunsExactlyOnceAcrossSizesBatchesAndWorkerScopes() = runBlocking {
        for (workers in listOf(1, 2, 4)) {
            val pool = LWWSP(workers)
            try {
                for (batch in listOf(1, 4, 16, 32)) for (size in listOf(0, 1, 3, 31, 33, 127, 1025)) {
                    val counts = AtomicIntegerArray(size)
                    val scopes = withTimeout(10_000) {
                        LWWSP.iterativeTask(size, batch, newScopeByWorkerID = { Scope(it) }) { start, end ->
                            if (thread == null) thread = Thread.currentThread()
                            assertSame(thread, Thread.currentThread())
                            assertEquals("LWWSP-" + worker, Thread.currentThread().name)
                            for (i in start until end) {
                                counts.incrementAndGet(i)
                                count++
                                if (i % 31 == 0) Thread.yield()
                            }
                        }.submit(pool).await().getOrThrow()
                    }
                    assertEquals(size, scopes.sumOf { it.count })
                    for (i in 0 until size) assertEquals(1, counts.get(i), "index=$" + i + " size=$" + size)
                }
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun idleWorkerStealsSmallTailWhileOtherWorkerIsBlocked() = runBlocking {
        val pool = LWWSP(2)
        val blocked = CountDownLatch(1)
        val stolen = CountDownLatch(1)
        val counts = AtomicIntegerArray(16)
        try {
            withTimeout(10_000) {
                LWWSP.iterativeTask(16, 4, newScopeByWorkerID = { Scope(it) }) { start, end ->
                    if (start == 0) {
                        blocked.countDown()
                        assertTrue(stolen.await(5, TimeUnit.SECONDS), "Unclaimed tail was abandoned")
                    } else {
                        assertTrue(blocked.await(5, TimeUnit.SECONDS))
                    }
                    for (i in start until end) {
                        counts.incrementAndGet(i)
                        if (i in 4..7 && stolen.count != 0L) {
                            assertEquals(1, worker, "Tail must be stolen before worker 0 is released")
                            stolen.countDown()
                        }
                    }
                }.submit(pool).await().getOrThrow()
            }
            for (i in 0 until 16) assertEquals(1, counts.get(i))
        } finally {
            stolen.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun actionFailureCompletesAwaitInsteadOfSpinning() = runBlocking {
        val pool = LWWSP(3)
        try {
            val result = withTimeout(5_000) {
                LWWSP.iterativeTask(100, 4) { start, _ ->
                    if (start == 0) error("expected failure")
                }.submit(pool).await()
            }
            assertTrue(result.isFailure)
            assertEquals("expected failure", result.exceptionOrNull()?.cause?.message)
        } finally {
            pool.shutdownNow()
        }
    }
}
