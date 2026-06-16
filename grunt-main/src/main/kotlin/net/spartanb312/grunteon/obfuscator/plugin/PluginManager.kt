package net.spartanb312.grunteon.obfuscator.plugin

import net.spartanb312.everett.bootstrap.ExternalClassLoader
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistry
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistryEntry
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.*

data class LoadedPlugin(
    val metadata: PluginMetadata,
    val instance: GruntPlugin,
    val classLoader: ClassLoader,
)

class PluginLoadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

private data class PluginCandidate(
    val metadata: PluginMetadata,
)

object PluginManager {
    private const val SUPPORTED_API_VERSION = "1"

    private val loadedPlugins = linkedMapOf<String, LoadedPlugin>()
    private var closed = false

    val plugins: List<LoadedPlugin>
        field = mutableListOf<LoadedPlugin>()

    fun loadPlugins(pluginDirectory: Path = Path("plugins")): List<LoadedPlugin> {
        ensureOpen()

        val candidates = discoverPlugins(pluginDirectory)
        resolveLoadOrder(candidates).forEach { candidate ->
            loadPlugin(candidate)
        }

        freeze()
        return plugins
    }

    fun loadPlugin(plugin: GruntPlugin): LoadedPlugin {
        ensureOpen()

        val metadata = PluginMetadata.fromPlugin(plugin).validated()
        return enablePlugin(metadata, plugin, plugin.javaClass.classLoader)
    }

    fun freeze() {
        if (!closed) {
            TransformerRegistry.freeze()
            closed = true
            plugins.addAll(loadedPlugins.values)
            Logger.info("Plugin registry frozen with ${loadedPlugins.size} loaded plugin(s)")
        }
    }

    private fun ensureOpen() {
        if (closed) {
            throw PluginLoadException("Plugin registry is already frozen; runtime plugin loading is not supported")
        }
    }

    private fun discoverPlugins(pluginDirectory: Path): List<PluginCandidate> {
        if (!pluginDirectory.exists()) {
            pluginDirectory.createDirectories()
            return emptyList()
        }
        if (!pluginDirectory.isDirectory()) {
            throw PluginLoadException("Plugin path $pluginDirectory is not a directory")
        }

        val candidates = pluginDirectory.listDirectoryEntries()
            .filter { it.extension.equals("jar", ignoreCase = true) }
            .sortedBy { it.fileName.toString() }
            .map { file -> PluginCandidate(readPluginMetadata(file).validated()) }

        validateDiscoveredPlugins(candidates)
        return candidates
    }

    private fun readPluginMetadata(file: Path): PluginMetadata {
        val manifest = try {
            JarFile(file.toFile()).use { jar -> jar.manifest }
        } catch (error: Throwable) {
            throw PluginLoadException("Failed to read plugin jar ${file.fileName}", error)
        } ?: throw PluginLoadException("Plugin jar ${file.fileName} does not contain META-INF/MANIFEST.MF")

        return PluginMetadata.fromManifest(file, manifest)
            ?: throw PluginLoadException(
                "Plugin jar ${file.fileName} does not declare Grunt-Plugin-Main or Entry-Class"
            )
    }

    private fun PluginMetadata.validated(): PluginMetadata {
        fun requireNotBlank(value: String, field: String) {
            if (value.isBlank()) {
                throw PluginLoadException("Plugin ${file.fileName} has blank $field")
            }
        }

        requireNotBlank(id, "id")
        requireNotBlank(name, "name")
        requireNotBlank(version, "version")
        requireNotBlank(apiVersion, "api version")
        requireNotBlank(entryClass, "entry class")
        if (apiVersion != SUPPORTED_API_VERSION) {
            throw PluginLoadException(
                "Plugin $id targets Grunt plugin API $apiVersion, but this runtime supports $SUPPORTED_API_VERSION"
            )
        }
        return this
    }

    private fun validateDiscoveredPlugins(candidates: List<PluginCandidate>) {
        val duplicatedId = candidates
            .groupBy { it.metadata.id }
            .filterValues { it.size > 1 }
            .entries
            .firstOrNull()
        if (duplicatedId != null) {
            val files = duplicatedId.value.joinToString { it.metadata.file.fileName.toString() }
            throw PluginLoadException("Duplicate plugin id ${duplicatedId.key} found in $files")
        }

        candidates.firstOrNull { loadedPlugins.containsKey(it.metadata.id) }?.let { candidate ->
            throw PluginLoadException("Plugin ${candidate.metadata.id} is already loaded")
        }

        val availablePluginIds = candidates.mapTo(mutableSetOf()) { it.metadata.id }
        availablePluginIds += loadedPlugins.keys

        candidates.forEach { candidate ->
            val metadata = candidate.metadata
            metadata.depends.firstOrNull { it !in availablePluginIds }?.let { missing ->
                throw PluginLoadException("Plugin ${metadata.id} requires missing plugin $missing")
            }
            metadata.conflicts.firstOrNull { it in availablePluginIds }?.let { conflict ->
                throw PluginLoadException("Plugin ${metadata.id} conflicts with plugin $conflict")
            }
        }
    }

    private fun resolveLoadOrder(candidates: List<PluginCandidate>): List<PluginCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val candidateById = candidates.associateBy { it.metadata.id }
        val orderIndex = candidates.withIndex().associate { it.value.metadata.id to it.index }
        val edges = candidates.associate { it.metadata.id to linkedSetOf<String>() }.toMutableMap()
        val inDegree = candidates.associate { it.metadata.id to 0 }.toMutableMap()

        fun addEdge(from: String, to: String) {
            if (from == to) {
                throw PluginLoadException("Plugin $from declares a self dependency")
            }
            val targets = edges.getValue(from)
            if (targets.add(to)) {
                inDegree[to] = inDegree.getValue(to) + 1
            }
        }

        candidates.forEach { candidate ->
            val metadata = candidate.metadata
            (metadata.depends + metadata.softDepends).forEach { dependency ->
                if (dependency in candidateById) addEdge(dependency, metadata.id)
            }
            metadata.loadBefore.forEach { target ->
                if (target in loadedPlugins) {
                    throw PluginLoadException(
                        "Plugin ${metadata.id} must load before $target, but $target is already loaded"
                    )
                }
                if (target in candidateById) addEdge(metadata.id, target)
            }
        }

        val queue = PriorityQueue<String> { left, right ->
            orderIndex.getValue(left).compareTo(orderIndex.getValue(right))
        }
        inDegree.filterValues { it == 0 }.keys.forEach(queue::add)

        val ordered = mutableListOf<PluginCandidate>()
        while (queue.isNotEmpty()) {
            val id = queue.remove()
            ordered += candidateById.getValue(id)
            edges.getValue(id).forEach { target ->
                val nextDegree = inDegree.getValue(target) - 1
                inDegree[target] = nextDegree
                if (nextDegree == 0) queue.add(target)
            }
        }

        if (ordered.size != candidates.size) {
            val cycle = inDegree.filterValues { it > 0 }.keys.joinToString()
            throw PluginLoadException("Plugin dependency cycle detected: $cycle")
        }

        return ordered
    }

    private fun loadPlugin(candidate: PluginCandidate): LoadedPlugin {
        val metadata = candidate.metadata
        val loader = ExternalClassLoader(metadata.id, PluginManager::class.java.classLoader)
        try {
            loader.loadJar(metadata.file.toFile())

            val clazz = loader.loadClass(metadata.entryClass)
            if (!GruntPlugin::class.java.isAssignableFrom(clazz)) {
                throw PluginLoadException(
                    "Entry class ${metadata.entryClass} does not implement ${GruntPlugin::class.qualifiedName}"
                )
            }

            val plugin = createPluginInstance(clazz)
            if (plugin.pluginID != metadata.id) {
                throw PluginLoadException(
                    "Plugin ${metadata.file.fileName} declares id ${metadata.id}, but entry reports ${plugin.pluginID}"
                )
            }
            return enablePlugin(metadata, plugin, loader)
        } catch (error: PluginLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PluginLoadException("Failed to load plugin ${metadata.id} from ${metadata.file.fileName}", error)
        }
    }

    private fun enablePlugin(
        metadata: PluginMetadata,
        plugin: GruntPlugin,
        classLoader: ClassLoader,
    ): LoadedPlugin {
        loadedPlugins[metadata.id]?.let {
            throw PluginLoadException("Plugin ${metadata.id} is already loaded")
        }

        val context = DefaultPluginContext(metadata, classLoader)
        try {
            plugin.onLoad(context)
            TransformerRegistry.registerAll(context.transformerEntries)
        } catch (error: PluginLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PluginLoadException("Failed to enable plugin ${metadata.id}", error)
        }

        context.transformerEntries.forEach { entry ->
            Logger.info("Registered transformer config ${entry.configClass.qualifiedName} from ${metadata.id}")
        }

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
        val transformerEntries = mutableListOf<TransformerRegistryEntry>()

        override fun registerTransformer(entry: TransformerRegistryEntry) {
            transformerEntries += entry.copy(owner = metadata.id)
        }
    }
}
