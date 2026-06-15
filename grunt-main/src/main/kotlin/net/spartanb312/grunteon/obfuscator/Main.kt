package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Grunteon
 * A JVM bytecode obfuscator
 * 3rd generation of Grunt
 */
const val VERSION = "3.0.0"
const val SUBTITLE = "build 260615"
const val GITHUB = "https://github.com/SpartanB312/Grunt"

// Local run
fun main(args: Array<String>) {
    if ("--silent" !in args) {
        Logger = SimpleLogger(
            "Grunteon",
            "logs/${SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())}.txt"
        )
    }
    println(
        """
             ________  __________   ____ ___   _______    ___________
            /  _____/  \______   \ |    |   \  \      \   \__    ___/
           /   \  ___   |       _/ |    |   /  /   |   \    |    |   
           \    \_\  \  |    |   \ |    |  /  /    |    \   |    |   
            \______  /  |____|_  / |______/   \____|__  /   |____|   
        """.trimIndent()
    )
    println("==========================================================")
    println(" Grunteon $VERSION [${SUBTITLE}]")
    println(" GitHub: $GITHUB")
    println("==========================================================")

    Logger.info("Initializing obfuscator...")
    PluginManager.loadPlugins()

    val config = ObfConfig.read(Path(configPath(args)))
    val instance = Grunteon.create(config)

    measureTime {
        instance.execute()
    }.toDouble(DurationUnit.MILLISECONDS).also { time ->
        println("Execution time: ${"%.2f".format(time)} ms")
    }
}

private fun configPath(args: Array<String>): String {
    val configIndex = args.indexOf("--config")
    if (configIndex >= 0 && configIndex + 1 < args.size) {
        return args[configIndex + 1]
    }

    return args.firstOrNull { it.startsWith("--config=") }
        ?.substringAfter("=")
        ?: "config.json"
}
