/* SPDX-License-Identifier: PolyForm-Strict-1.0.0 */
package net.spartanb312.grunt.yapyap.transformers.encrypt

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class OncePerPassTest {
    @Test
    fun emptyPassDoesNotInitializeAndConcurrentPoolsInitializeOnce() {
        val calls = AtomicInteger()
        val value = Any()
        val setup = OncePerPass { calls.incrementAndGet(); value }
        assertEquals(0, calls.get())
        val executor = Executors.newFixedThreadPool(4)
        try {
            executor.invokeAll(List(32) { Callable { setup.get() } }).forEach { assertSame(value, it.get()) }
        } finally { executor.shutdownNow() }
        assertEquals(1, calls.get())
        assertSame(value, setup.get())
    }

    @Test
    fun setupFailureIsPublishedOnceAndSeparatePassCanInitialize() {
        val calls = AtomicInteger()
        val failure = IllegalStateException("curve setup failure")
        val setup = OncePerPass<Any> { calls.incrementAndGet(); throw failure }
        repeat(3) { assertSame(failure, assertFailsWith<IllegalStateException> { setup.get() }) }
        assertEquals(1, calls.get())
        val nextPass = OncePerPass { calls.incrementAndGet() }
        assertEquals(2, nextPass.get())
    }
}
