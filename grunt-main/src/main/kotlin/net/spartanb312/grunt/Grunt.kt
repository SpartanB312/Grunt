package net.spartanb312.grunt

import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.gui.GuiFrame
import net.spartanb312.grunt.process.Transformers
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.PostProcessTransformer
import net.spartanb312.grunt.process.transformers.PostProcessTransformer.finalize
import net.spartanb312.grunt.utils.logging.Logger
import java.awt.GraphicsEnvironment
import kotlin.system.measureTimeMillis

/**
 * Gruntpocalypse
 * A java bytecode obfuscator
 */
const val VERSION = "2.2.4"
const val SUBTITLE = "build 240924"
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
    println(" Gruntpocalypse $VERSION [$SUBTITLE]")
    println(" Github: $GITHUB")
    println("==========================================================")

    Logger.info("Initializing Grunt Obfuscator...")

    var guiMode = false
    val config = args.firstOrNull { it.endsWith(".json") } ?: run {
        guiMode = true
        "config.json"
    } // If config is provided, use console mode

    Logger.info("Using config $config")

    if (GraphicsEnvironment.isHeadless()) {
        Logger.error("Gui is not allowed in your environment. Switching to console mode.")
        guiMode = false
    }

    if (guiMode) {
        try {
            Configs.loadConfig(config)
            Configs.saveConfig(config) // Clean up the config
        } catch (ignore: Exception) {
            Logger.info("Failed to read config $config! But we generated a new one.")
            Configs.saveConfig(config)
        }
        GuiFrame.loadConfig(config)
        GuiFrame.view()
    } else {
        try {
            Configs.resetConfig()
            Configs.loadConfig(config)
            Configs.saveConfig(config) // Clean up the config
        } catch (ignore: Exception) {
            Logger.info("Failed to read config $config! But we generated a new one.")
            Configs.saveConfig(config)
            Logger.info("Type (Y/N) if you want to continue")
            if (readlnOrNull()?.lowercase() == "n") return
        }
        runProcess()
    }
}

fun runProcess() {
    // Process
    val time = measureTimeMillis {
        ResourceCache(Configs.Settings.input, Configs.Settings.libraries).apply {
            readJar()
            val obfTime = measureTimeMillis {
                Logger.info("Processing...")
                Transformers.sortedBy { it.order }.forEach { if (it.enabled) with(it) { transform() } }
                with(PostProcessTransformer) { finalize() }
            }
            Logger.info("Took $obfTime ms to process!")
            Logger.info("Dumping to ${Configs.Settings.output}")
        }.dumpJar(Configs.Settings.output)
    }
    Logger.info("Finished in $time ms!")
}