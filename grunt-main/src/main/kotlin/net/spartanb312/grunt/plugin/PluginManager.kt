package net.spartanb312.grunt.plugin

import net.spartanb312.grunt.VERSION
import net.spartanb312.grunt.utils.logging.Logger
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL

object PluginManager {

    val gruntVersion = VERSION
    private val plugins = mutableListOf<PluginInfo>()

    // Force adding a plugin
    fun addInternalPlugin(plugin: PluginInitializer) {
        plugins.add(PluginInfo({ plugin }, plugin.javaClass.classLoader))
    }

    fun loadPlugins() {
        Logger.info("Scanning plugins...")
        val dir = File("plugins/")
        if (!dir.exists()) dir.mkdirs()
        readDirectory(dir)
        Logger.info("Found ${plugins.size} plugins")
    }

    fun initPlugins() {
        if (plugins.isNotEmpty()) {
            Logger.info("Initializing plugins...")
            plugins.forEach {
                it.instance.onInit()
            }
        }
    }

    private fun readDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) readDirectory(file)
            else {
                val pluginInfo = readJar(file)
                if (pluginInfo != null) {
                    Logger.info("Loaded plugin: ${file.name}")
                    plugins.add(pluginInfo)
                } else Logger.info("Failed to load: ${file.name}")
            }
        }
    }

    private fun readJar(jar: File): PluginInfo? {
        val classLoader = ExternalClassLoader()
        classLoader.loadJar(jar)
        val manifest = classLoader.findResource("META-INF/MANIFEST.MF")
        val entryPoint = manifest?.findEntry()
        if (entryPoint != null) {
            try {
                val provider: () -> PluginInitializer = {
                    val mainClass = classLoader.loadClass(entryPoint)
                    classLoader.getInstanceField(mainClass) as PluginInitializer
                }
                return PluginInfo(provider, classLoader)
            } catch (exception: Exception) {
                exception.printStackTrace()
                return null
            }
        }
        return null
    }

    private fun URL.findEntry(): String? {
        try {
            val ir = InputStreamReader(this.openStream())
            val br = BufferedReader(ir)
            var line: String
            while ((br.readLine().also { line = it }) != null) {
                if (line.startsWith("Entry-Point: ")) {
                    return line.substring(13)
                }
            }
            return null
        } catch (ignored: IOException) {
            return null
        }
    }

    class PluginInfo(mainProvider: () -> PluginInitializer, val classLoader: ClassLoader) {
        val instance by lazy { mainProvider.invoke() }
    }

}