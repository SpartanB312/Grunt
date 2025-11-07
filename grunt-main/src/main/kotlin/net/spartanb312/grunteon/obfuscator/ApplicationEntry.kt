package net.spartanb312.grunteon.obfuscator

import net.spartanb312.everett.bootstrap.Main

// for dev mode only
fun main() = Main.main(arrayOf("-DevMode", "net.spartanb312.grunteon.obfuscator.ApplicationEntry"))

object ApplicationEntry {

    init {
        println("Application entry")
    }

}