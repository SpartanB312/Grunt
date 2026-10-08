package net.spartanb312.grunteon.obfuscator.util.logging

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SimpleLogger private constructor(
    private val name: String,
    savePath: String,
    debug: () -> Boolean,
    private val console: Boolean,
    flushIntervalMillis: Long
) : ILogger, Closeable {
    // Keep the original default-argument JVM constructor for already compiled Kotlin plugins.
    constructor(name: String, savePath: String = "", debug: () -> Boolean = { false }) :
            this(name, savePath, debug, true, 250)

    constructor(
        name: String,
        savePath: String = "",
        console: Boolean,
        flushIntervalMillis: Long = 250,
        debug: () -> Boolean = { false }
    ) : this(name, savePath, debug, console, flushIntervalMillis)

    @Volatile
    private var debug = debug
    private val lock = Any()
    private var closed = false
    private var flushFailure: Exception? = null
    private val writer: BufferedWriter? = if (savePath.isEmpty()) null else {
        val file = File(savePath)
        file.parentFile?.mkdirs()
        BufferedWriter(FileWriter(file), 64 * 1024)
    }
    private val scheduledFlush: ScheduledFuture<*>? = writer?.let {
        val weak = WeakReference(this)
        val future = AtomicReference<ScheduledFuture<*>>()
        val task = scheduler.scheduleWithFixedDelay({
            val logger = weak.get()
            if (logger == null) future.get()?.cancel(false)
            else synchronized(logger.lock) {
                if (!logger.closed) {
                    try {
                        logger.writer?.flush()
                    } catch (error: Exception) {
                        logger.flushFailure = error
                        future.get()?.cancel(false)
                    }
                }
            }
        }, flushIntervalMillis.coerceAtLeast(1), flushIntervalMillis.coerceAtLeast(1), TimeUnit.MILLISECONDS)
        future.set(task)
        task
    }

    override fun trace(msg: String) {
        if (debug()) raw(msg, "TRACE")
    }

    override fun debug(msg: String) {
        if (debug()) raw(msg, "DEBUG")
    }

    override fun info(msg: String) = raw(msg, "INFO")
    override fun warn(msg: String) = raw(msg, "WARN")
    override fun error(msg: String) = raw(msg, "ERROR")
    override fun fatal(msg: String) = raw(msg, "FATAL")

    override fun raw(msg: String, level: String) {
        val line = "[${timestamp.format(LocalDateTime.now())}][${Thread.currentThread().name}/$level][$name] $msg"
        synchronized(lock) {
            if (closed) return
            flushFailure?.let { throw it }
            if (console) println(line)
            writer?.write(line)
            writer?.newLine()
            // Errors should reach disk promptly; ordinary progress messages are batched.
            if (level == "ERROR" || level == "FATAL") writer?.flush()
        }
    }

    fun flush() {
        synchronized(lock) {
            flushFailure?.let { throw it }
            if (!closed) writer?.flush()
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            scheduledFlush?.cancel(false)
            try {
                writer?.close()
            } catch (error: Exception) {
                flushFailure?.takeIf { it !== error }?.let(error::addSuppressed)
                throw error
            }
            flushFailure?.let { throw it }
        }
    }

    fun enableDebug() { debug = { true } }
    fun disableDebug() { debug = { false } }
    fun setDebug(debug: () -> Boolean) { this.debug = debug }

    companion object {
        private val timestamp = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
        private val scheduler = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "grunteon-log-flush").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}