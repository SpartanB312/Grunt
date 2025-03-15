package net.spartanb312.grunt.process.resource

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.Configs.isExcluded
import net.spartanb312.grunt.config.Configs.isMixinClass
import net.spartanb312.grunt.config.Configs.saveToFile
import net.spartanb312.grunt.config.Configs.shouldRemove
import net.spartanb312.grunt.event.events.WritingClassEvent
import net.spartanb312.grunt.event.events.WritingResourceEvent
import net.spartanb312.grunt.process.hierarchy.Hierarchy
import net.spartanb312.grunt.process.hierarchy.ReferenceSearch
import net.spartanb312.grunt.utils.corruptCRC32
import net.spartanb312.grunt.utils.corruptJarHeader
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ResourceCache(private val input: String, private val libs: List<String>) {

    val classes = mutableMapOf<String, ClassNode>()
    val libraries = mutableMapOf<String, ClassNode>()
    val resources = mutableMapOf<String, ByteArray>()
    val trashClasses = mutableMapOf<String, ClassNode>()

    val allClasses
        get() = mutableListOf<ClassNode>().apply {
            addAll(classes.values)
            addAll(libraries.values)
        }

    val nonExcluded get() = classes.values.filter { !it.isExcluded }
    val mixinClasses get() = classes.values.filter { it.isMixinClass }

    val classMappings = mutableMapOf<String, String>()
    private val mappingObjects = mutableMapOf<String, JsonObject>()

    fun getMapping(name: String): String = classMappings.getOrElse(name) { name }

    fun applyRemap(type: String, mappings: Map<String, String>, remapClassNames: Boolean = false) {
        if (Configs.Settings.generateRemap) {
            val obj = JsonObject()
            mappings.forEach { (prev, new) ->
                obj.addProperty(prev, new)
                classMappings[prev] = new
            }
            mappingObjects[type] = JsonObject().apply { add(type, obj) }
        }
        val remapper = SimpleRemapper(mappings)
        for ((name, node) in classes.toMutableMap()) {
            val copy = ClassNode()
            val adapter = ClassRemapper(copy, remapper)
            node.accept(adapter)
            classes[name] = copy
            trashClasses[name]?.let {
                trashClasses[name] = copy
            }
        }
        if (remapClassNames) {
            classes.toMap().forEach { (name, node) ->
                mappings[name]?.let { newName ->
                    classes.remove(name)
                    classes[newName] = node
                }
            }
            trashClasses.toMap().forEach { (name, node) ->
                mappings[name]?.let { newName ->
                    trashClasses.remove(name)
                    trashClasses[newName] = node
                }
            }
        }
    }

    fun readJar() {
        readInput()
        readLibs()
    }

    fun dumpJar(targetFile: String) {
        val outputFile = File(targetFile)
        if (outputFile.exists()) {
            Logger.warn("Existing output file will be overridden!")
        }
        val outputStream = outputFile.outputStream()

        if (Configs.Settings.corruptJarHeader) {
            Logger.info("Corrupting jar header...")
            corruptJarHeader(outputStream)
        }

        ZipOutputStream(outputStream).apply {
            setLevel(Configs.Settings.compressionLevel)

            if (Configs.Settings.archiveComment.isNotEmpty()) {
                setComment(Configs.Settings.archiveComment)
            }

            if (Configs.Settings.corruptCRC32) {
                Logger.info("Corrupting CRC32...")
                corruptCRC32()
            }

            Logger.info("Building hierarchies...")
            val hierarchy = Hierarchy(this@ResourceCache)
            hierarchy.build(true)

            Logger.info("Writing classes...")
            val mutex = Mutex()
            runBlocking {
                for (classNode in classes.values) {
                    if (classNode.name == "module-info" || classNode.name.shouldRemove) continue
                    suspend fun job() {
                        val missingList = ReferenceSearch.checkMissing(classNode, hierarchy)
                        val missingRef = missingList.isNotEmpty()
                        if (missingRef && Configs.Settings.missingCheck) {
                            Logger.error("Class ${classNode.name} missing reference:")
                            for (missing in missingList) {
                                Logger.error(" - ${missing.name}")
                            }
                        }
                        val classInfo = hierarchy.getClassInfo(classNode)
                        val missingAny = (classInfo.missingDependencies || missingRef) && Configs.Settings.missingCheck
                        val useComputeMax = Configs.Settings.forceUseComputeMax || missingAny || classNode.isExcluded
                        val missing = missingAny && !Configs.Settings.forceUseComputeMax && !classNode.isExcluded

                        val entryName = classNode.name + ".class"
                        val writingClassEvent = WritingClassEvent(entryName, classNode)
                        writingClassEvent.post()
                        if (!writingClassEvent.cancelled) {
                            val byteArray = try {
                                if (missing) Logger.warn("Using COMPUTE_MAXS due to ${classNode.name} missing dependencies or reference.")
                                ClassDumper(this@ResourceCache, hierarchy, useComputeMax).apply {
                                    classNode.accept(CustomClassNode(Opcodes.ASM9, this))
                                }.toByteArray()
                            } catch (ignore: Exception) {
                                Logger.error("Failed to dump class ${classNode.name}. Trying ${if (useComputeMax) "COMPUTE_FRAMES" else "COMPUTE_MAXS"}")
                                try {
                                    ClassDumper(this@ResourceCache, hierarchy, !useComputeMax).apply {
                                        classNode.accept(CustomClassNode(Opcodes.ASM9, this))
                                    }.toByteArray()
                                } catch (exception: Exception) {
                                    exception.printStackTrace()
                                    ByteArray(0)
                                }
                            }
                            val event = WritingResourceEvent(entryName, byteArray)
                            event.post()
                            if (!event.cancelled) mutex.withLock {
                                putNextEntry(ZipEntry(entryName))
                                write(byteArray)
                                closeEntry()
                            }
                        }
                    }
                    if (Configs.Settings.parallel) launch(Dispatchers.IO) { job() } else job()
                }
            }
            hierarchy.buildMissingMap()
            if (Configs.Settings.missingCheck) hierarchy.printMissing()

            Logger.info("Writing resources...")
            for ((name, bytes) in resources) {
                if (name.shouldRemove) continue
                val event = WritingResourceEvent(name, bytes)
                event.post()
                if (!event.cancelled) {
                    putNextEntry(ZipEntry(name))
                    write(bytes)
                    closeEntry()
                }
            }
            close()

            if (Configs.Settings.generateRemap) {
                Logger.info("Writing mappings...")
                if (mappingObjects.isNotEmpty()) {
                    val dir =
                        "mappings/${SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())}" +
                                " ${File(Configs.Settings.input).name}/"
                    mappingObjects.forEach { (name, obj) ->
                        obj.saveToFile(File("$dir$name.json"))
                    }
                }
            }
        }
    }

    private fun readInput() {
        Logger.info("Reading $input")
        JarFile(File(input)).apply {
            entries().asSequence()
                .filter { !it.isDirectory }
                .forEach {
                    if (it.name.endsWith(".class")) {
                        kotlin.runCatching {
                            ClassReader(getInputStream(it)).apply {
                                val classNode = ClassNode()
                                accept(classNode, ClassReader.EXPAND_FRAMES)
                                classes[classNode.name] = classNode
                            }
                        }
                    } else resources[it.name] = getInputStream(it).readBytes()
                }
        }
    }

    private fun readLibs() {
        Logger.info("Reading Libraries...")
        libs.map { File(it) }.forEach { file ->
            if (file.isDirectory) {
                readDirectory(file)
            } else {
                readJar(JarFile(file))
            }
        }
    }

    private fun readDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                readDirectory(file)
            } else {
                readJar(JarFile(file))
            }
        }
    }

    private fun readJar(jar: JarFile) {
        Logger.info("  - ${jar.name}")
        jar.entries().asSequence().filter { !it.isDirectory }.forEach {
            if (it.name.endsWith(".class")) {
                kotlin.runCatching {
                    ClassReader(jar.getInputStream(it)).apply {
                        val classNode = ClassNode()
                        accept(classNode, ClassReader.EXPAND_FRAMES)
                        libraries[classNode.name] = classNode
                    }
                }
            }
        }
    }

    fun addClass(classNode: ClassNode) {
        classes[classNode.name] = classNode
        trashClasses[classNode.name] = classNode
    }

    fun removeClass(classNode: ClassNode) {
        classes.remove(classNode.name)
        trashClasses.remove(classNode.name)
    }

    fun getClassNode(name: String): ClassNode? {
        return classes[name] ?: libraries[name] ?: readInRuntime(name)
    }

    fun readInRuntime(name: String): ClassNode? {
        return try {
            val classNode = ClassNode()
            ClassReader(name).apply {
                accept(classNode, ClassReader.EXPAND_FRAMES)
                libraries[classNode.name] = classNode
            }
            classNode
        } catch (ignore: Exception) {
            null
        }
    }

    //credit https://blog.51cto.com/lsieun/4594350
    class CustomClassNode(api: Int, cv: ClassVisitor) : ClassNode(api) {
        init {
            this.cv = cv
        }
        override fun visitEnd() {
            methods.forEach { mn ->
                transform(name, mn)
            }
            super.visitEnd()
            if (cv != null) {
                accept(cv)
            }
        }
        fun transform(owner: String, mn: MethodNode) {
            mn.maxStack = -1
            val ana = Analyzer(BasicInterpreter())
            runCatching {
                ana.analyze(owner, mn)
                val frames = ana.frames
                val insnNodes = mn.instructions.toArray()
                for (i in frames.indices) {
                    if (frames[i] == null && insnNodes[i] !is LabelNode) {
                        mn.instructions.remove(insnNodes[i])
                    }
                }
            }.onFailure {
                //it.printStackTrace()
            }
        }
    }
}