package net.spartanb312.grunteon.ui

import kotlinx.coroutines.*
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

class ConfigIoTest {
    @Test
    fun writesRemainFifoAfterCancelledAwaitAndFailure() = runBlocking {
        val queue = ConfigIoQueue()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val written = mutableListOf<Int>()
        try {
            val first = queue.submit {
                started.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                written += 1
            }
            started.await()
            val awaiter = launch { first.await() }
            awaiter.cancelAndJoin()
            val failure = queue.submit<Unit> { error("expected failure") }
            val third = queue.submit { written += 3 }
            release.countDown()
            assertFailsWith<IllegalStateException> { failure.await() }
            third.await()
            queue.flush()
            assertEquals(listOf(1, 3), written)
        } finally {
            release.countDown()
            queue.close()
        }
    }

    @Test
    fun atomicReplacementKeepsOriginalOnSerializationFailureAndCleansTemporaryFile() {
        val directory = Files.createTempDirectory("grunteon-ui-atomic-test-")
        try {
            val target = directory.resolve("config.json")
            target.writeText("old config")
            assertFailsWith<IllegalStateException> {
                atomicConfigWrite(target) { temporary ->
                    temporary.writeText("incomplete new config")
                    error("serialization failed")
                }
            }
            assertEquals("old config", target.readText())
            Files.list(directory).use { assertEquals(1L, it.count()) }
            atomicConfigWrite(target) { it.writeText("complete new config") }
            assertEquals("complete new config", target.readText())
            Files.list(directory).use { assertEquals(1L, it.count()) }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
