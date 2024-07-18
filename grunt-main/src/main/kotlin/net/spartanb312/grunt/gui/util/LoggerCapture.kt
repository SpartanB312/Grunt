package net.spartanb312.grunt.gui.util

import java.io.OutputStream
import java.io.PrintStream

class LoggerCapture(out: OutputStream, val onPrint: (String) -> Unit): PrintStream(out) {
    override fun print(s: String?) {
        onPrint(s ?: "")
        super.print(s)
    }
}