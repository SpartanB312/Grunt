package net.spartanb312.grunteon.obfuscator.process.resource

import kotlinx.coroutines.CompletableDeferred
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Atomic weighted admission: waiters never hold partial reservations. */
internal class InFlightByteBudget(private val capacity: Long) {
    init { require(capacity > 0) }
    private val lock = Any()
    private var available = capacity
    private var changed = CompletableDeferred<Unit>()

    suspend fun acquire(bytes: Long): Closeable {
        val weight = bytes.coerceIn(1, capacity)
        while (true) {
            val wait = synchronized(lock) {
                if (available >= weight) {
                    available -= weight
                    null
                } else changed
            }
            if (wait == null) {
                val released = AtomicBoolean()
                return Closeable {
                    if (released.compareAndSet(false, true)) {
                        val notification = synchronized(lock) {
                            available += weight
                            changed.also { changed = CompletableDeferred() }
                        }
                        notification.complete(Unit)
                    }
                }
            }
            wait.await()
        }
    }
}