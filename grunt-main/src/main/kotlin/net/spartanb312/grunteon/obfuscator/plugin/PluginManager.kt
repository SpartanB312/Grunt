package net.spartanb312.grunteon.obfuscator.plugin

import net.spartanb312.everett.bootstrap.ExternalClassLoader
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistry
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistryEntry
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

data class LoadedPlugin(
    val metadata: PluginMetadata,
    val instance: GruntPlugin,
    val classLoader: ClassLoader,
)

object PluginManager {
    private val loadedPlugins = linkedMapOf<String, LoadedPlugin>()

    val plugins: List<LoadedPlugin>
        get() = loadedPlugins.values.toList()

    fun loadPlugins(pluginDirectory: Path = Path("plugins")): List<LoadedPlugin> {
        if (!pluginDirectory.exists()) {
            pluginDirectory.createDirectories()
            return emptyList()
        }
        if (!pluginDirectory.isDirectory()) return emptyList()

        val discovered = pluginDirectory.listDirectoryEntries()
            .filter { it.extension.equals("jar", ignoreCase = true) }
            .sortedBy { it.fileName.toString() }

        discovered.forEach { file ->
            runCatching {
                loadPlugin(file)
            }.onFailure { error ->
                Logger.error("Failed to load plugin ${file.fileName}: ${error.message}")
                Logger.debug(error.stackTraceToString())
            }
        }
        return plugins
    }

    fun loadPlugin(plugin: GruntPlugin): LoadedPlugin {
        val pluginClass = plugin.javaClass
        val metadata = PluginMetadata(
            id = pluginClass.name,
            name = pluginClass.simpleName,
            version = "dev",
            apiVersion = "1",
            entryClass = pluginClass.name,
            file = Path(""),
        )
        return enablePlugin(metadata, plugin, pluginClass.classLoader)
    }

    private fun loadPlugin(file: Path) {
        val manifest = JarFile(file.toFile()).use { jar -> jar.manifest ?: return }
        val metadata = PluginMetadata.fromManifest(file, manifest) ?: return
        if (loadedPlugins.containsKey(metadata.id)) {
            Logger.warn("Plugin ${metadata.id} is already loaded, skipping ${file.fileName}")
            return
        }

        val loader = ExternalClassLoader(metadata.id, PluginManager::class.java.classLoader)
        loader.loadJar(file.toFile())

        val clazz = loader.loadClass(metadata.entryClass)
        require(GruntPlugin::class.java.isAssignableFrom(clazz)) {
            "Entry class ${metadata.entryClass} does not implement ${GruntPlugin::class.qualifiedName}"
        }

        val plugin = createPluginInstance(clazz)
        enablePlugin(metadata, plugin, loader)
    }

    private fun enablePlugin(
        metadata: PluginMetadata,
        plugin: GruntPlugin,
        classLoader: ClassLoader,
    ): LoadedPlugin {
        loadedPlugins[metadata.id]?.let { loaded ->
            Logger.warn("Plugin ${metadata.id} is already loaded")
            return loaded
        }

        val context = DefaultPluginContext(metadata, classLoader)
        plugin.onLoad(context)
        plugin.onEnable(context)

        return LoadedPlugin(metadata, plugin, classLoader).also { loaded ->
            loadedPlugins[metadata.id] = loaded
            Logger.info("Loaded plugin ${metadata.name} ${metadata.version} (${metadata.id})")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createPluginInstance(clazz: Class<*>): GruntPlugin {
        val instanceField = clazz.declaredFields.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.name == "INSTANCE"
        }
        if (instanceField != null) {
            instanceField.isAccessible = true
            return instanceField.get(null) as GruntPlugin
        }
        val constructor = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance() as GruntPlugin
    }

    private class DefaultPluginContext(
        override val metadata: PluginMetadata,
        override val classLoader: ClassLoader,
    ) : PluginContext {
        override fun registerTransformer(entry: TransformerRegistryEntry) {
            TransformerRegistry.register(entry)
            Logger.info("Registered transformer config ${entry.configClass.qualifiedName} from ${metadata.id}")
        }
    }
}
