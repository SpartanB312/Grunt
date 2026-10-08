package net.spartanb312.grunteon.obfuscator.util.logging

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.*

class SimpleLoggerPerformanceTest {
    @Test
    fun concurrentMessagesKeepWholeLinesAndCloseDrainsFinalTail() = workspace { dir ->
        val path = dir.resolve("concurrent.log")
        val logger = SimpleLogger("concurrent", path.toString(), console = false, flushIntervalMillis = 60_000)
        val executor = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until 6).map { worker ->
                executor.submit {
                    start.await()
                    repeat(250) { index -> logger.info("worker=" + worker + ";item=" + index) }
                }
            }
            start.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            logger.info("last-buffered-tail")
            logger.close()
            logger.close()
            logger.info("ignored-after-close")
            logger.flush()
            val lines = Files.readAllLines(path)
            assertEquals(1501, lines.size)
            assertTrue(lines.last().endsWith("last-buffered-tail"))
            assertFalse(lines.any { "ignored-after-close" in it })
            val messages = lines.dropLast(1).map { it.substringAfter("][concurrent] ") }
            assertEquals(1500, messages.toSet().size)
            for (worker in 0 until 6) {
                assertEquals((0 until 250).map { "worker=" + worker + ";item=" + it },
                    messages.filter { it.startsWith("worker=" + worker + ";") })
            }
            assertTrue(lines.all { it.startsWith("[") && "/INFO][concurrent] " in it })
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            logger.close()
        }
    }

    @Test
    fun explicitFlushAndErrorsPublishOrdinaryBufferedMessages() = workspace { dir ->
        val path = dir.resolve("flush.log")
        SimpleLogger("flush", path.toString(), console = false, flushIntervalMillis = 60_000).use { logger ->
            logger.info("before-explicit-flush")
            logger.flush()
            assertTrue("before-explicit-flush" in Files.readString(path))
            logger.info("before-error")
            logger.error("error-marker")
            val afterError = Files.readString(path)
            assertTrue("before-error" in afterError)
            assertTrue("/ERROR][flush] error-marker" in afterError)
            logger.fatal("fatal-marker")
            assertTrue("/FATAL][flush] fatal-marker" in Files.readString(path))
            logger.info("close-tail")
        }
        assertTrue(Files.readAllLines(path).last().endsWith("close-tail"))
    }

    @Test
    fun scheduledFlushPublishesTailWithoutFurtherLogCalls() = workspace { dir ->
        val path = dir.resolve("periodic.log")
        SimpleLogger("periodic", path.toString(), console = false, flushIntervalMillis = 10).use { logger ->
            dir.fileSystem.newWatchService().use { watch ->
                dir.register(watch, ENTRY_MODIFY)
                logger.info("periodic-tail")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                var published = false
                while (!published && System.nanoTime() < deadline) {
                    val key = watch.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS) ?: break
                    key.pollEvents()
                    key.reset()
                    published = "periodic-tail" in Files.readString(path)
                }
                assertTrue(published, "Scheduled flush must make a quiet logger's tail visible")
            }
        }
    }

    @Test
    fun fileLoggersShareOneDaemonAndCloseRemovesScheduledTasks() = workspace { dir ->
        val scheduler = SimpleLogger::class.java.getDeclaredField("scheduler").apply { isAccessible = true }
            .get(null) as ScheduledThreadPoolExecutor
        val baseline = scheduler.queue.size
        val loggers = (0 until 40).map { index ->
            SimpleLogger("logger-" + index, dir.resolve("logger-" + index + ".log").toString(),
                console = false, flushIntervalMillis = 60_000)
        }
        try {
            assertEquals(baseline + 40, scheduler.queue.size)
            assertEquals(1, scheduler.corePoolSize)
            val flushThreads = Thread.getAllStackTraces().keys.filter { it.name == "grunteon-log-flush" && it.isAlive }
            assertEquals(1, flushThreads.size)
            assertTrue(flushThreads.single().isDaemon)
            loggers.forEach { it.info("tail") }
        } finally {
            loggers.forEach(SimpleLogger::close)
        }
        assertEquals(baseline, scheduler.queue.size, "Cancelled periodic tasks must not retain closed loggers")
        repeat(40) { index -> assertTrue("tail" in Files.readString(dir.resolve("logger-" + index + ".log"))) }
    }

    @Test
    fun consoleCanBeDisabledAndDebugPolicyCanChange() = workspace { dir ->
        val path = dir.resolve("silent.log")
        val original = System.out
        val captured = ByteArrayOutputStream()
        PrintStream(captured).use { output ->
            System.setOut(output)
            try {
                SimpleLogger("silent-regression", path.toString(), console = false).use { logger ->
                    logger.debug("hidden-debug")
                    logger.trace("hidden-trace")
                    logger.info("visible-info")
                    logger.enableDebug()
                    logger.debug("enabled-debug")
                    logger.trace("enabled-trace")
                    logger.disableDebug()
                    logger.debug("hidden-again")
                    var enabled = false
                    logger.setDebug { enabled }
                    logger.trace("hidden-predicate")
                    enabled = true
                    logger.trace("predicate-trace")
                }
            } finally {
                System.setOut(original)
            }
        }
        assertFalse("silent-regression" in captured.toString(Charsets.UTF_8))
        val text = Files.readString(path)
        for (message in listOf("visible-info", "enabled-debug", "enabled-trace", "predicate-trace")) assertTrue(message in text)
        for (message in listOf("hidden-debug", "hidden-trace", "hidden-again", "hidden-predicate")) assertFalse(message in text)
    }

    @Test
    fun legacyPositionalNamedAndTrailingLambdaConstructorsRemainSourceCompatible() {
        // Compile-time coverage of the original API, not reflection on the new constructor layout.
        SimpleLogger("one").use { }
        SimpleLogger("trailing") { true }.use { }
        SimpleLogger("positional", "", { true }).use { }
        SimpleLogger("path-trailing", "") { false }.use { }
        SimpleLogger(name = "named", savePath = "", debug = { true }).use { }
        SimpleLogger(name = "silent", console = false, debug = { false }).use { it.info("no-output") }
    }

    @Test
    fun originalKotlinDefaultArgumentConstructorRemainsBinaryCompatible() {
        val constructor = SimpleLogger::class.java.getConstructor(
            String::class.java,
            String::class.java,
            Class.forName("kotlin.jvm.functions.Function0"),
            Int::class.javaPrimitiveType,
            Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        )
        // A previously compiled SimpleLogger("compat") call passes mask 6 for savePath/debug.
        val logger = constructor.newInstance("compat", null, null, 6, null) as SimpleLogger
        logger.close()
    }

    private fun workspace(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("grunt-logger-regression")
        try { block(dir) } finally {
            Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
