package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.util.logging.ILogger
import java.text.SimpleDateFormat
import java.util.Date

enum class UiLogLevel(val label: String) {
    Info("INFO"),
    Debug("DEBUG"),
}

class UiLogger(
    private val name: String,
    private val minimumLevel: UiLogLevel,
    private val onLine: (String) -> Unit,
) : ILogger {
    override fun trace(msg: String) = raw(msg, "TRACE")
    override fun debug(msg: String) = raw(msg, "DEBUG")
    override fun info(msg: String) = raw(msg, "INFO")
    override fun warn(msg: String) = raw(msg, "WARN")
    override fun error(msg: String) = raw(msg, "ERROR")
    override fun fatal(msg: String) = raw(msg, "FATAL")

    override fun raw(msg: String, level: String) {
        if (!shouldEmit(level)) return
        onLine("[${SimpleDateFormat("MM-dd HH:mm:ss").format(Date())}][${Thread.currentThread().name}/$level][$name] $msg")
    }

    private fun shouldEmit(level: String): Boolean {
        return when (level) {
            "TRACE" -> false
            "DEBUG" -> minimumLevel == UiLogLevel.Debug
            else -> true
        }
    }
}
