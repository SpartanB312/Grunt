package net.spartanb312.grunteon.obfuscator.process.resource

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import net.spartanb312.grunteon.index.info.ClassInfo
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.PHANTOM_CLASS
import net.spartanb312.grunteon.obfuscator.util.PHANTOM_FIELD
import net.spartanb312.grunteon.obfuscator.util.PHANTOM_METHOD
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.net.URI
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.io.path.*

class WorkResources private constructor(
    val inputResourceSet: ResourceSet.Single,
    val libraryResourceSets: Map<String, ResourceSet.Single>,
    val allResourceSets: ResourceSet,
    val generatedResources: MutableMap<String, ByteArray>,
    /**
     * Input classes in library input set, maps name to class node.
     * Also included in allClasses.
     */
    val libraryClassMap: ConcurrentHashMap<String, ClassNode>,
    /**
     * Input classes in input/output set, maps name to class node.
     * Also included in allClasses.
     */
    val inputClassMap: MutableMap<String, ClassNode>
) {
    val inputClassCollection: Collection<ClassNode> get() = inputClassMap.values
    val librariesClassCollection: Collection<ClassNode> get() = libraryClassMap.values

    // TODO: optimize this
    inline val allClassCollection
        get() = ObjectArrayList<ClassNode>(
            inputClassCollection.size + librariesClassCollection.size
        ).apply {
            addAll(inputClassCollection)
            addAll(librariesClassCollection)
        }

    fun addGeneratedClass(classNode: ClassNode) {
        inputClassMap[classNode.name] = classNode
    }

    fun addGeneratedResource(name: String, content: ByteArray) {
        generatedResources[name] = content
    }

    fun getInputResource(name: String): ResourceSet.ResourceEntry? {
        return inputResourceSet[name].firstOrNull()
    }

    fun getClassNode(name: String): ClassNode? {
        return inputClassMap[name] ?: libraryClassMap.computeIfAbsent(name) {
            try {
                ClassNode().apply {
                    ClassReader(name)
                        .accept(this, ClassReader.EXPAND_FRAMES)
                }
            } catch (_: Exception) {
                DUMMY_CLASSNODE
            }
        }.takeIf { it !== DUMMY_CLASSNODE }
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
        libraryClassMap[classInfo.name] = node
    }

    companion object {
        private val DUMMY_CLASSNODE = ClassNode()

        private fun toZipRootPath(zipPath: Path): Path {
            val jarURI = URI.create("jar:" + zipPath.toUri())
            // TODO: lifecycle of zipFileSystem
            val zipFileSystem = try {
                FileSystems.getFileSystem(jarURI)
            } catch (_: FileSystemNotFoundException) {
                FileSystems.newFileSystem(jarURI, mapOf<String, String>())
            }

            return zipFileSystem.getPath("/")
        }

        private fun resolvePath(path: Path): Path {
            if (path.isRegularFile()) {
                val ext = path.extension.lowercase()
                if (ext == "jar" || ext == "zip") {
                    return toZipRootPath(path)
                }
            }
            return path
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun read(input: Path, libs: List<Path> = emptyList()): WorkResources {
            return read(
                input = PathResourceInput(input),
                libs = libs
                    .map { PathResourceInput(it) }
                    .flatMap { it.expandJarInputs() }
            )
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun read(input: ResourceInput, libs: List<ResourceInput> = emptyList()): WorkResources {
            val inputPath = input.resolvePath()
            val libraryPaths = libs.map { it to it.resolvePath() }
            Logger.info("Reading...")
            Logger.info("Input: ${input.description}")
            require(inputPath.exists()) { "Input file does not exist: ${input.description}" }
            Logger.debug("Libraries:")
            libraryPaths.forEach { (library, _) ->
                Logger.debug(" - ${library.description}")
            }
            val inputResourceSet = ResourceSet.Single(resolvePath(inputPath))
            val libraryResourceSets = libraryPaths.associate { (library, path) ->
                library.description to ResourceSet.Single(resolvePath(path))
            }
            val allResourceSetList = listOf(inputResourceSet) + libraryResourceSets.values
            val allResourceSets = ResourceSet.Composite(allResourceSetList)

            val inputClassMap = Object2ObjectOpenHashMap<String, ClassNode>()
            // TODO: resolves all classes reference during read
            val libraryClassMap = ConcurrentHashMap<String, ClassNode>()
            runBlocking {
                val inputClassNodes = Channel<ClassNode>(Channel.BUFFERED)
                val libraryNodes = Channel<ClassNode>(Channel.BUFFERED)

                launch(Dispatchers.Default) {
                    coroutineScope {
                        allResourceSetList.forEach { resourceSet ->
                            readSourceSetClassNodes(resourceSet, inputResourceSet, inputClassNodes, libraryNodes)
                        }
                    }
                    inputClassNodes.close()
                    libraryNodes.close()
                }

                launch {
                    inputClassNodes.consumeEach {
                        inputClassMap[it.name] = it
                    }
                }
                launch {
                    libraryNodes.consumeEach {
                        libraryClassMap[it.name] = it
                    }
                }
            }
            Logger.info("Read ${inputClassMap.size} classes from input and ${libraryClassMap.size} classes from libraries")

            return WorkResources(
                inputResourceSet = inputResourceSet,
                libraryResourceSets = libraryResourceSets,
                allResourceSets = allResourceSets,
                generatedResources = Object2ObjectOpenHashMap(),
                libraryClassMap = libraryClassMap,
                inputClassMap = inputClassMap
            )
        }

        private fun CoroutineScope.readSourceSetClassNodes(
            resourceSet: ResourceSet.Single,
            inputResourceSet: ResourceSet.Single,
            inputClassNodes: Channel<ClassNode>,
            libraryNodes: Channel<ClassNode>
        ) {
            launch {
                var uriStr = resourceSet.root.toUri().toString()
                if (uriStr.startsWith("jar:")) {
                    uriStr = uriStr.substring(4, uriStr.length - 2)
                    val entries =
                        resourceSet.root.walk()
                            .filter { !it.isDirectory() }
                            .filter { it.extension == "class" }
                            .filter { !it.absolutePathString().startsWith("/META-INF/") }
                            .toList()
                    ZipFile(URI.create(uriStr).toPath().toFile()).use { zip ->
                        coroutineScope {
                            val isInput = resourceSet === inputResourceSet
                            entries.forEach { entry ->
                                launch {
                                    runCatching {
                                        ClassNode().apply {
                                            val entryPath = entry.pathString.removePrefix("/")
                                            val data =
                                                zip.getInputStream(zip.getEntry(entryPath))
                                                    .use { it.readBytes() }
                                            ClassReader(data)
                                                .accept(this, ClassReader.EXPAND_FRAMES)
                                        }
                                    }.onSuccess {
                                        if (isInput) {
                                            inputClassNodes.send(it)
                                        } else {
                                            libraryNodes.send(it)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val entries = withContext(Dispatchers.IO) {
                        resourceSet.root.walk()
                            .filter { it.extension == "class" }
                            .map { resourceSet[it].first() }
                            .toList()
                    }
                    val isInput = resourceSet === inputResourceSet
                    entries.forEach { entry ->
                        launch {
                            runCatching {
                                ClassNode().apply {
                                    ClassReader(entry.content)
                                        .accept(this, ClassReader.EXPAND_FRAMES)
                                }
                            }.onSuccess {
                                if (isInput) {
                                    inputClassNodes.send(it)
                                } else {
                                    libraryNodes.send(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
