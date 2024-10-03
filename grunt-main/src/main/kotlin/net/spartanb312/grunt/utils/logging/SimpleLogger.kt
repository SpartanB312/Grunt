package net.spartanb312.grunt.utils.logging

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class SimpleLogger(private val name: String, savePath: String = "") : ILogger {

    private val shouldSaveFile = savePath != ""

    private val bufferedWriter = run {
        run {
            if (savePath == "") null
            else {
                val file = File(savePath)
                try {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                    file
                } catch (ignore: Exception) {
                    null
                }
            }
        }?.let {
            return@run BufferedWriter(FileWriter(it))
        }
        return@run null
    }

    override fun debug(msg: String) = raw(msg, "DEBUG")

    override fun info(msg: String) = raw(msg, "INFO")

    override fun warn(msg: String) = raw(msg, "WARN")

    override fun error(msg: String) = raw(msg, "ERROR")

    override fun fatal(msg: String) = raw(msg, "FATAL")

    override fun raw(msg: String, level: String) {
        val str = "[${SimpleDateFormat("MM-dd HH:mm:ss").format(Date())}][${Thread.currentThread().name}/$level][$name] $msg"
        println(str)
        if (shouldSaveFile) bufferedWriter?.apply {
            write(str)
            flush()
            newLine()
        }
    }

}