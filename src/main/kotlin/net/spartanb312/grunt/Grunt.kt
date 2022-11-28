package net.spartanb312.grunt

import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.process.Transformers
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.logging.Logger

const val VERSION = "1.5.5"
const val TYPE = "Beta"
const val AUTHOR = "B_312"
const val GITHUB = "https://github.com/SpartanB312/Grunt"

fun main(args: Array<String>) {

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
    println(" Grunt Klass Master (Version: $VERSION[$TYPE], Author: $AUTHOR)")
    println(" Github: $GITHUB")
    println("==========================================================")

    Logger.info("Initializing Grunt...")
    val configName = args.getOrNull(0) ?: "config.json"
    Logger.info("Using config $configName")
    try {
        Configs.loadConfig(configName)
        Configs.saveConfig(configName)
    } catch (ignore: Exception) {
        Logger.info("Failed to read config $configName!But we generated a new one.")
        Logger.info("Type (Y/N) if you want to continue")
        if (readLine()?.lowercase() == "n") return
    }

    ResourceCache(Configs.Settings.input, Configs.Settings.libraries).apply {
        readJar()
        Logger.info("Processing...")
        Transformers.forEach {
            if (it.enabled) with(it) { transform() }
        }
        Logger.info("Dumping to ${Configs.Settings.output}")
    }.dumpJar(Configs.Settings.output)

}