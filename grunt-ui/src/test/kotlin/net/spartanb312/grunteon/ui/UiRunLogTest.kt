package net.spartanb312.grunteon.ui

import java.io.BufferedWriter
import java.io.StringWriter
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class UiRunLogTest {
    @Test
    fun stickyBottomRespectsUserScrollAndIgnoresAutomaticScrolling() {
        val state = LogTailFollowState()
        assertTrue(state.followsTail)
        state.isAutomaticScroll = true
        state.onScroll(scrolling = true, canScrollForward = true)
        assertTrue(state.followsTail)
        state.isAutomaticScroll = false
        state.onScroll(scrolling = true, canScrollForward = true)
        assertFalse(state.followsTail)
        state.onScroll(scrolling = false, canScrollForward = true)
        assertFalse(state.followsTail)
        state.onScroll(scrolling = true, canScrollForward = false)
        assertTrue(state.followsTail)
        state.onScroll(scrolling = true, canScrollForward = true)
        state.reset()
        assertTrue(state.followsTail)
    }

    @Test
    fun hundredThousandEntriesKeepBoundedTailAndCompleteOrderedSink() {
        val output = StringWriter()
        val log = UiRunLog(Path.of("unused-test-log"), BufferedWriter(output), maxEntries = 200)
        repeat(100_000) { log.append("line-$it") }
        val snapshot = assertNotNull(log.snapshotIfChanged())
        assertEquals(100_000L, snapshot.totalEntries)
        assertEquals(200, snapshot.entries.size)
        assertEquals(99_800L, snapshot.entries.first().sequence)
        assertEquals("line-99999", snapshot.entries.last().text)
        assertNull(log.snapshotIfChanged()) // No UI invalidation without a new batch.
        log.close()
        assertEquals((0 until 100_000).map { "line-$it" }, output.toString().lineSequence().filter { it.isNotEmpty() }.toList())
    }

    @Test
    fun oversizedEntriesOnlyAbbreviateVisibleCopy() {
        val output = StringWriter()
        val log = UiRunLog(Path.of("unused-test-log"), BufferedWriter(output),
            maxEntries = 10, maxCharacters = 20, maxEntryCharacters = 12)
        val original = "a".repeat(100)
        repeat(5) { log.append(original) }
        val snapshot = assertNotNull(log.snapshotIfChanged())
        assertTrue(snapshot.entries.sumOf { it.text.length } <= 20)
        assertTrue(snapshot.entries.all { it.text.endsWith("…") })
        log.close()
        assertEquals((original + "\n").repeat(5), output.toString())
    }

    @Test
    fun concurrentProducersKeepUniqueKeysAndFileOrder() {
        val output = StringWriter()
        val log = UiRunLog(Path.of("unused-test-log"), BufferedWriter(output), maxEntries = 2_000)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val jobs = (0 until 4).map { worker ->
                executor.submit { repeat(500) { log.append("$worker/$it") } }
            }
            jobs.forEach { it.get(5, TimeUnit.SECONDS) }
            val snapshot = assertNotNull(log.snapshotIfChanged())
            log.close()
            assertEquals((0L until 2_000L).toList(), snapshot.entries.map { it.sequence })
            assertEquals(output.toString().lineSequence().filter { it.isNotEmpty() }.toList(), snapshot.entries.map { it.text })
        } finally {
            log.close()
            executor.shutdownNow()
        }
    }
}
