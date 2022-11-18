package net.spartanb312.grunt.utils.logging

interface ILogger {

    fun debug(msg: String) {
    }

    fun info(msg: String) {
    }

    fun warn(msg: String) {
    }

    fun error(msg: String) {
    }

    fun fatal(msg: String) {
    }

    fun raw(msg: String, level: String) {
    }

}