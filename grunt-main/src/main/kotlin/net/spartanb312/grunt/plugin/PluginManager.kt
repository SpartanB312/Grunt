package net.spartanb312.grunt.plugin

import net.spartanb312.grunt.VERSION
import net.spartanb312.grunt.utils.compareVersion
import net.spartanb312.grunt.utils.logging.Logger
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL

object PluginManager {

    val gruntVersion = VERSION
    val plugins = mutableListOf<PluginInfo>()
    val hasPlugins get() = plugins.isNotEmpty()

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
        plugins.forEach {
            Logger.info("${it.instance.name} = [")
            Logger.info("    version = ${it.instance.version},")
            Logger.info("    author = ${it.instance.author},")
            Logger.info("    description = ${it.instance.description}")
            Logger.info("]")
            if (!compareVersion(gruntVersion, it.instance.minVersion)) {
                Logger.warn("Warning: Plugin ${it.instance.name} requires grunt version at least ${it.instance.minVersion}, but current version is $gruntVersion")
            }
        }
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

    @Suppress("DEPRECATION")
    private fun readJar(jar: File): PluginInfo? {
        val classLoader = ExternalClassLoader()
        classLoader.loadJar(jar)
        val manifest = classLoader.findResource("META-INF/MANIFEST.MF")
        val entryPoint = manifest?.findEntry()
        if (entryPoint != null) {
            try {
                val provider: () -> PluginInitializer = {
                    val mainClass = classLoader.loadClass(entryPoint)
                    val instanceField = classLoader.getInstanceField(mainClass)
                    if (instanceField != null) instanceField as PluginInitializer
                    else mainClass.newInstance() as PluginInitializer
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