package net.spartanb312.grunt

import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.event.events.FinalizeEvent
import net.spartanb312.grunt.event.events.GuiEvent
import net.spartanb312.grunt.event.events.ProcessEvent
import net.spartanb312.grunt.event.events.TransformerEvent
import net.spartanb312.grunt.gui.GuiFrame
import net.spartanb312.grunt.plugin.PluginManager
import net.spartanb312.grunt.plugin.PluginManager.hasPlugins
import net.spartanb312.grunt.process.Transformers
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.PostProcessTransformer
import net.spartanb312.grunt.utils.logging.Logger
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Gruntpocalypse
 * A java bytecode obfuscator
 */
const val VERSION = "2.5.0"
const val SUBTITLE = "build 250415"
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
    println(" GitHub: $GITHUB")
    println("==========================================================")

    Logger.info("Initializing Grunt Obfuscator...")

    // Plugins
    PluginManager.loadPlugins()
    PluginManager.initPlugins()

    var guiMode = false
    val config = args.firstOrNull { it.endsWith(".json") } ?: run {
        guiMode = true
        "config.json"
    } // If config is provided, use console mode

    Logger.info("Using config $config")

    GuiEvent.BeforeInit.post()
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
            // Сохраняем сломанный конфиг перед созданием нового
            try {
                val brokenConfig = File(config).readText()
                File("config-breaked.json").writeText(brokenConfig)
                Logger.info("Broken config saved to config-breaked.json")
            } catch (e: Exception) {
                Logger.error("Failed to save broken config: ${e.message}")
            }
            Configs.saveConfig(config)
        }
        GuiFrame.loadConfig(config)
        GuiFrame.setTitle("Gruntpocalypse${if (hasPlugins) "*" else ""} v$VERSION | $SUBTITLE")
        GuiEvent.AfterInit.post()
        GuiFrame.view()
    } else {
        try {
            Configs.resetConfig()
            Configs.loadConfig(config)
            Configs.saveConfig(config) // Clean up the config
        } catch (ignore: Exception) {
            Logger.info("Failed to read config $config! But we generated a new one.")
            // Сохраняем сломанный конфиг перед созданием нового
            try {
                val brokenConfig = File(config).readText()
                File("config-breaked.json").writeText(brokenConfig)
                Logger.info("Broken config saved to config-breaked.json")
            } catch (e: Exception) {
                Logger.error("Failed to save broken config: ${e.message}")
            }
            Configs.saveConfig(config)
            Logger.info("Type (Y/N) if you want to continue")
            if (readlnOrNull()?.lowercase() == "n") return
        }
        runProcess()
    }
}

fun runProcess() {
    // Process
    ProcessEvent.Before.post()
    val time = measureTimeMillis {
        ResourceCache(Configs.Settings.input, Configs.Settings.libraries).apply {
            readJar()
            val timeUsage = mutableMapOf<String, Long>()
            val obfTime = measureTimeMillis {
                Logger.info("Processing...")
                Transformers.sortedBy { it.order }.forEach {
                    if (it.enabled) {
                        val preEvent = TransformerEvent.Before(it, this)
                        preEvent.post()
                        if (!preEvent.cancelled) {
                            val actualTransformer = preEvent.transformer
                            val startTime = System.currentTimeMillis()
                            with(actualTransformer) { transform() }
                            timeUsage[actualTransformer.name] = System.currentTimeMillis() - startTime
                            val postEvent = TransformerEvent.After(actualTransformer, this)
                            postEvent.post()
                        }
                    }
                }
                with(PostProcessTransformer) {
                    FinalizeEvent.Before(this@apply).post()
                    finalize()
                    FinalizeEvent.After(this@apply).post()
                }
            }
            Logger.info("Took $obfTime ms to process!")
            if (Configs.Settings.timeUsage) timeUsage.forEach { (name, duration) ->
                Logger.info("   $name $duration ms")
            }
            Logger.info("Dumping to ${Configs.Settings.output}")
        }.dumpJar(Configs.Settings.output)
    }
    ProcessEvent.After.post()
    Logger.info("Finished in $time ms!")
}