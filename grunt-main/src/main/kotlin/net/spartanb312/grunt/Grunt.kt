package net.spartanb312.grunt

import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.process.Transformers
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.logging.Logger
import kotlin.system.measureTimeMillis

/**
 * Gruntpocalypse
 * A continuation of Grunt witch is a lightweight java bytecode obfuscator
 */
const val VERSION = "2.0.0.240706"
const val TYPE = "Beta"
const val GITHUB = "https://github.com/SpartanB312/Grunt"

fun main(args: Array<String>) {

    // Splash
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
    println(" Gruntpocalypse $VERSION[$TYPE]")
    println(" Github: $GITHUB")
    println("==========================================================")

    Logger.info("Initializing Grunt Obfuscator...")
    val configName = args.getOrNull(0) ?: "config.json"
    Logger.info("Using config $configName")
    try {
        Configs.loadConfig(configName)
        Configs.saveConfig(configName)
    } catch (ignore: Exception) {
        Logger.info("Failed to read config $configName!But we generated a new one.")
        Configs.saveConfig(configName)
        Logger.info("Type (Y/N) if you want to continue")
        if (readlnOrNull()?.lowercase() == "n") return
    }

    // Process
    val time = measureTimeMillis {
        ResourceCache(Configs.Settings.input, Configs.Settings.libraries).apply {
            readJar()
            val obfTime = measureTimeMillis {
                Logger.info("Processing...")
                Transformers.forEach { if (it.enabled) with(it) { transform() } }
            }
            Logger.info("Took $obfTime ms to process!")
            Logger.info("Dumping to ${Configs.Settings.output}")
        }.dumpJar(Configs.Settings.output)
    }
    Logger.info("Finished in $time ms!")

}
