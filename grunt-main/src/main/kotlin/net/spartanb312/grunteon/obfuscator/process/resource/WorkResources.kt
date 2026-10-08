package net.spartanb312.grunteon.obfuscator.process.resource

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.spartanb312.grunteon.index.info.ClassInfo
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.PHANTOM_CLASS
import net.spartanb312.grunteon.obfuscator.util.PHANTOM_FIELD
import net.spartanb312.grunteon.obfuscator.util.PHANTOM_METHOD
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.io.Closeable
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

class WorkResources private constructor(
    val inputResourceSet: ResourceSet.Single,
    val libraryResourceSets: Map<String, ResourceSet.Single>,
    val allResourceSets: ResourceSet,
    val generatedResources: MutableMap<String, ByteArray>,
    /** Library metadata. Use getClassNode when method bodies are required. */
    val libraryClassMap: ConcurrentHashMap<String, ClassNode>,
    val inputClassMap: MutableMap<String, ClassNode>,
    private val librarySources: ConcurrentHashMap<String, LibrarySource>,
    private val ownedFileSystems: List<FileSystem>,
    private val stringPools: ConcurrentHashMap<String, List<String>> = ConcurrentHashMap()
) : Closeable {
    val inputClassCollection: Collection<ClassNode> get() = inputClassMap.values
    val librariesClassCollection: Collection<ClassNode> get() = libraryClassMap.values

    // Keep the independent mutable snapshot contract of this public property.
    inline val allClassCollection
        get() = ObjectArrayList<ClassNode>(inputClassCollection.size + librariesClassCollection.size).apply {
            addAll(inputClassCollection)
            addAll(librariesClassCollection)
        }

    private val missingClasses = ConcurrentHashMap.newKeySet<String>()
    private val hierarchySnapshots = arrayOfNulls<HierarchySnapshot>(2)
    @Volatile
    private var closed = false

    fun addGeneratedClass(classNode: ClassNode) {
        inputClassMap[classNode.name] = classNode
        missingClasses.remove(classNode.name)
    }

    fun addGeneratedResource(name: String, content: ByteArray) {
        generatedResources[name] = content
    }

    fun putStringPool(name: String, values: Iterable<String>) {
        stringPools[name] = values.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().toList()
    }

    fun getStringPool(name: String): List<String> = stringPools[name] ?: emptyList()

    fun getInputResource(name: String): ResourceSet.ResourceEntry? = inputResourceSet[name].firstOrNull()

    /** Clears only failed classpath lookups, for callers which extend their class loader after loading resources. */
    fun invalidateMissingClasses() {
        missingClasses.clear()
    }

    /** Resolves signatures and hierarchy without inflating library method bodies. */
    fun getClassMetadata(name: String): ClassNode? {
        inputClassMap[name]?.let { return it }
        libraryClassMap[name]?.let { return it }
        check(!closed) { "Work resources are closed" }
        if (name in missingClasses) return null
        @Suppress("UNCHECKED_CAST", "JavaCollectionWithNullableTypeArgument")
        return (libraryClassMap as ConcurrentHashMap<String, ClassNode?>).computeIfAbsent(name) {
            if (it in missingClasses) return@computeIfAbsent null
            try {
                val reader = { ClassReader(it) }
                val node = readNode(reader(), false)
                librarySources[it] = LibrarySource(node, reader)
                node
            } catch (_: Exception) {
                missingClasses.add(it)
                null
            }
        }
    }

    /** Preserves the full-bytecode lookup API; indexed libraries without a byte source remain phantom nodes. */
    fun getClassNode(name: String): ClassNode? {
        check(!closed) { "Work resources are closed" }
        val node = getClassMetadata(name) ?: return null
        if (inputClassMap[name] === node) return node
        return libraryClassMap.compute(name) { _, current ->
            check(!closed) { "Work resources are closed" }
            val source = librarySources[name]
            // Choose the current node and its source under the same per-key lock. Another worker
            // may already have hydrated the old header returned by getClassMetadata above.
            if (source == null || current !== source.header) current else {
                val full = readNode(source.reader(), true)
                librarySources.remove(name, source)
                full
            }
        }
    }

    /** A cache valid only while class identity, names and hierarchy edges stay unchanged. */
    @Synchronized
    fun classHierarchy(includeLibraries: Boolean = true): ClassHierarchy {
        check(!closed) { "Work resources are closed" }
        val index = if (includeLibraries) 1 else 0
        val nodes = if (includeLibraries) allClassCollection else inputClassCollection
        hierarchySnapshots[index]?.takeIf { it.matches(nodes, libraryClassMap) }?.let { return it.hierarchy }
        val hierarchy = ClassHierarchy.build(nodes, ::getClassMetadata)
        // Ancestor lookup may have populated the library map during the build.
        val completeScope = if (includeLibraries) allClassCollection else inputClassCollection
        hierarchySnapshots[index] = HierarchySnapshot(completeScope.map(::ClassShape), libraryClassMap.toMap(), hierarchy)
        return hierarchy
    }

    fun addIndexedLibrary(classInfo: ClassInfo) {
        val node = ClassNode()
        node.access = classInfo.access
        node.name = classInfo.name
        node.superName = classInfo.superName
        node.interfaces = classInfo.interfaces
        node.methods = classInfo.methods.map {
            MethodNode(it.access, it.name, it.desc, it.signature, null).appendAnnotation(PHANTOM_METHOD)
        }
        node.fields = classInfo.fields.map {
            FieldNode(it.access, it.name, it.desc, it.signature, null).appendAnnotation(PHANTOM_FIELD)
        }
        node.appendAnnotation(PHANTOM_CLASS)
        librarySources.remove(classInfo.name)
        libraryClassMap[classInfo.name] = node
        missingClasses.remove(classInfo.name)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        hierarchySnapshots.fill(null)
        librarySources.clear()
        missingClasses.clear()
        inputResourceSet.cache.clear()
        libraryResourceSets.values.forEach { it.cache.clear() }
        var failure: Exception? = null
        for (fileSystem in ownedFileSystems.asReversed()) {
            try {
                fileSystem.close()
            } catch (error: Exception) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private class LibrarySource(val header: ClassNode, val reader: () -> ClassReader)

    private class ClassShape(val node: ClassNode) {
        private val name = node.name
        private val superName = node.superName
        private val interfaces = node.interfaces?.toList()
        fun matches() = node.name == name && node.superName == superName && node.interfaces == interfaces
    }

    private class HierarchySnapshot(
        shapes: List<ClassShape>,
        private val libraries: Map<String, ClassNode>,
        val hierarchy: ClassHierarchy
    ) {
        private val size = shapes.size
        private val byIdentity = IdentityHashMap<ClassNode, ClassShape>().apply {
            shapes.forEach { put(it.node, it) }
            // Looked-up ancestors may not belong to an input-only snapshot.
            hierarchy.classNodes.forEach { putIfAbsent(it, ClassShape(it)) }
        }
        private val inputs = java.util.Collections.newSetFromMap(IdentityHashMap<ClassNode, Boolean>()).apply {
            shapes.forEach { add(it.node) }
        }
        fun matches(nodes: Collection<ClassNode>, currentLibraries: Map<String, ClassNode>): Boolean {
            if (nodes.size != size || inputs.size != size || currentLibraries != libraries) return false
            if (nodes.any { it !in inputs }) return false
            return byIdentity.values.all { it.matches() }
        }
    }

    companion object {
        const val ANTI_LLM_STRING_POOL = "anti-llm"
        private const val HEADER_FLAGS = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES

        private fun readNode(reader: ClassReader, full: Boolean): ClassNode {
            val node = ClassNode()
            reader.accept(node, if (full) ClassReader.EXPAND_FRAMES else HEADER_FLAGS)
            if (!full) {
                node.appendAnnotation(PHANTOM_CLASS)
                node.methods.forEach { it.appendAnnotation(PHANTOM_METHOD) }
                node.fields.forEach { it.appendAnnotation(PHANTOM_FIELD) }
            }
            return node
        }

        private fun resolvePath(path: Path, owned: MutableList<FileSystem>): Path {
            if (path.isRegularFile() && path.extension.lowercase() in setOf("jar", "zip")) {
                // The Path overload creates an independently owned FS; never close a shared URI-registered FS.
                val fileSystem = FileSystems.newFileSystem(path, emptyMap<String, String>())
                owned += fileSystem
                return fileSystem.getPath("/")
            }
            return path
        }

        fun read(input: Path, libs: List<Path> = emptyList()): WorkResources = read(
            PathResourceInput(input), libs.map { PathResourceInput(it) }.flatMap { it.expandJarInputs() }
        )

        fun read(input: ResourceInput, libs: List<ResourceInput> = emptyList()): WorkResources {
            val inputPath = input.resolvePath()
            require(inputPath.exists()) { "Input file does not exist: ${input.description}" }
            Logger.info("Reading...")
            Logger.info("Input: ${input.description}")
            val owned = mutableListOf<FileSystem>()
            try {
                val inputSet = ResourceSet.Single(resolvePath(inputPath, owned))
                val librarySets = libs.associate { library ->
                    Logger.debug(" - ${library.description}")
                    library.description to ResourceSet.Single(resolvePath(library.resolvePath(), owned))
                }
                val sets = listOf(inputSet) + librarySets.values
                val inputClasses = Object2ObjectOpenHashMap<String, ClassNode>()
                val libraryClasses = ConcurrentHashMap<String, ClassNode>()
                val sources = ConcurrentHashMap<String, LibrarySource>()
                val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
                runBlocking {
                    val tasks = Channel<Pair<Path, Boolean>>(workers)
                    val results = Channel<Triple<ClassNode, Path, Boolean>>(workers)
                    launch(Dispatchers.IO) {
                        try {
                            for (set in sets) {
                                for (path in set.files()) {
                                    if (path.extension != "class") continue
                                    if (set.root.fileSystem.provider().scheme == "jar" &&
                                        set.entryName(path).startsWith("META-INF/")) continue
                                    tasks.send(path to (set === inputSet))
                                }
                            }
                        } finally {
                            tasks.close()
                        }
                    }
                    launch {
                        coroutineScope {
                            repeat(workers) {
                                launch(Dispatchers.Default) {
                                    for ((path, isInput) in tasks) {
                                        val node = try {
                                            val bytes = withContext(Dispatchers.IO) { path.readBytes() }
                                            readNode(ClassReader(bytes), isInput)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            Logger.warn("Unable to read class $path: ${error.message}")
                                            continue
                                        }
                                        results.send(Triple(node, path, isInput))
                                    }
                                }
                            }
                        }
                        results.close()
                    }
                    for ((node, path, isInput) in results) {
                        if (isInput) inputClasses[node.name] = node
                        else {
                            libraryClasses[node.name] = node
                            sources[node.name] = LibrarySource(node) { ClassReader(path.readBytes()) }
                        }
                    }
                }
                Logger.info("Read ${inputClasses.size} classes from input and ${libraryClasses.size} classes from libraries")
                return WorkResources(
                    inputSet, librarySets, ResourceSet.Composite(sets), Object2ObjectOpenHashMap(),
                    libraryClasses, inputClasses, sources, owned
                )
            } catch (error: Throwable) {
                owned.asReversed().forEach { fs ->
                    runCatching { fs.close() }.exceptionOrNull()?.let(error::addSuppressed)
                }
                throw error
            }
        }
    }
}