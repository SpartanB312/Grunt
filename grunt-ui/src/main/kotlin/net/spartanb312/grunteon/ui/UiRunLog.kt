package net.spartanb312.grunteon.ui

import java.io.BufferedWriter
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque

/** Sequence keys remain unique when the visible tail evicts old entries. */
data class UiLogEntry(val sequence: Long, val text: String)

internal data class UiLogSnapshot(val entries: List<UiLogEntry>, val totalEntries: Long)

internal class LogTailFollowState {
    var followsTail = true
        private set
    var isAutomaticScroll = false

    fun onScroll(scrolling: Boolean, canScrollForward: Boolean, automatic: Boolean = isAutomaticScroll) {
        if (scrolling && !automatic) followsTail = !canScrollForward
    }

    fun reset() { followsTail = true }
}

/** Producers stream the complete log to disk; UI polling only copies a bounded tail.
 * Disk and tail locks are separate: a slow filesystem never blocks an EDT snapshot.
 */
internal class UiRunLog(
    val path: Path,
    private val writer: BufferedWriter,
    private val maxEntries: Int = 2_000,
    private val maxCharacters: Int = 2 * 1024 * 1024,
    private val maxEntryCharacters: Int = 16_384,
) : Closeable {
    init {
        require(maxEntries > 0 && maxCharacters > 0 && maxEntryCharacters > 0)
    }
    private val sinkLock = Any()
    private val tailLock = Any()
    private val tail = ArrayDeque<UiLogEntry>()
    private var characters = 0
    private var sequence = 0L
    private var publishedSequence = -1L
    private var lastFlush = System.nanoTime()
    private var closed = false

    fun append(line: String) = synchronized(sinkLock) {
        check(!closed) { "Log sink is closed: $path" }
        writer.append(line).append('\n')
        val now = System.nanoTime()
        if (now - lastFlush >= 75_000_000L) {
            writer.flush()
            lastFlush = now
        }
        synchronized(tailLock) {
            val limit = minOf(maxEntryCharacters, maxCharacters)
            val preview = if (line.length > limit) line.take((limit - 1).coerceAtLeast(0)) + "…" else line
            val entry = UiLogEntry(sequence++, preview)
            tail.addLast(entry)
            characters += preview.length
            while (tail.size > maxEntries || characters > maxCharacters) {
                characters -= tail.removeFirst().text.length
            }
        }
    }

    fun snapshotIfChanged(): UiLogSnapshot? = synchronized(tailLock) {
        if (publishedSequence == sequence) null else {
            publishedSequence = sequence
            UiLogSnapshot(tail.toList(), sequence)
        }
    }

    fun flush() = synchronized(sinkLock) {
        if (!closed) writer.flush()
    }

    override fun close() = synchronized(sinkLock) {
        if (!closed) {
            closed = true
            writer.close()
        }
    }

    companion object {
        fun open(directory: Path): UiRunLog {
            Files.createDirectories(directory)
            val path = Files.createTempFile(directory, "obfuscation-", ".log").toAbsolutePath().normalize()
            return UiRunLog(path, Files.newBufferedWriter(path))
        }
    }
}
