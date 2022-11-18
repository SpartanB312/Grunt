package net.spartanb312.grunt

import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.obfuscate.Transformers
import net.spartanb312.grunt.obfuscate.resource.ResourceCache
import net.spartanb312.grunt.utils.logging.Logger

const val VERSION = "1.4.1"

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
    println(" Grunt Klass Master (Version: $VERSION, Author: B_312)")
    println(" Lightweight obfuscator for jvm programs")
    println("==========================================================")

    Logger.info("Initializing Grunt...")
    val configName = args.getOrNull(0) ?: "config.json"
    Logger.info("Using config $configName")
    try {
        Configs.loadConfig(configName)
        Configs.saveConfig(configName)
    } catch (ignore: Exception) {
        Logger.info("Failed to read config $configName!")
        Logger.info("Type (Y/N) if you want to continue")
        if (readLine()?.lowercase() == "n") return
    }

    ResourceCache(Configs.Settings.input /*Configs.Settings.libraries*/).apply {
        readJar()
        Logger.info("Obfuscating...")
        Transformers.transformers.forEach {
            if (it.enabled) with(it) { transform() }
        }
        Logger.info("Dumping to ${Configs.Settings.output}")
    }.dumpJar(Configs.Settings.output)

}