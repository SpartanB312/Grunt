package net.spartanb312.grunt.process.resource

import com.google.gson.JsonObject
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.Configs.isExcluded
import net.spartanb312.grunt.config.Configs.isMixinClass
import net.spartanb312.grunt.config.Configs.saveToFile
import net.spartanb312.grunt.config.Configs.shouldRemove
import net.spartanb312.grunt.process.hierarchy.Hierarchy
import net.spartanb312.grunt.utils.corruptCRC32
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
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

    val nonExcluded get() = classes.filter { !it.key.isExcluded }.values
    val mixinClasses get() = classes.filter { it.key.isMixinClass }.values

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

    fun dumpJar(targetFile: String) = ZipOutputStream(File(targetFile).outputStream()).apply {
        Logger.info("Building hierarchies...")
        val hierarchy = Hierarchy(this@ResourceCache)
        hierarchy.build()
        if (Configs.Settings.corruptOutput) {
            Logger.info("Corrupting output...")
            corruptCRC32()
        }

        Logger.info("Writing classes...")
        for (classNode in classes.values) {
            if (classNode.name == "module-info" || classNode.name.shouldRemove) continue
            val classInfo = hierarchy.getClassInfo(classNode)
            val useComputeMax = Configs.Settings.useComputeMax || classInfo.missingDependencies || classNode.isExcluded
            val missing = classInfo.missingDependencies && !Configs.Settings.useComputeMax && !classNode.isExcluded
            val byteArray = try {
                if (missing) Logger.warn("Using COMPUTE_MAXS due to ${classNode.name} missing dependencies.")
                ClassDumper(this@ResourceCache, hierarchy, useComputeMax).apply {
                    classNode.accept(this)
                }.toByteArray()
            } catch (ignore: Exception) {
                Logger.error("Failed to dump class ${classNode.name}. Trying ${if (useComputeMax) "COMPUTE_FRAMES" else "COMPUTE_MAXS"}")
                try {
                    ClassDumper(this@ResourceCache, hierarchy, !useComputeMax).apply {
                        classNode.accept(this)
                    }.toByteArray()
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    ByteArray(0)
                }
            }
            putNextEntry(ZipEntry(classNode.name + ".class"))
            write(byteArray)
            closeEntry()
        }
        hierarchy.printMissing()
        hierarchy.buildMissingMap()

        Logger.info("Writing resources...")
        for ((name, bytes) in resources) {
            if (name.shouldRemove) continue
            putNextEntry(ZipEntry(name))
            write(bytes)
            closeEntry()
        }
        close()

        if (Configs.Settings.generateRemap) {
            Logger.info("Writing mappings...")
            if (mappingObjects.isNotEmpty()) {
                val dir =
                    "mappings/${SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())} ${Configs.Settings.input}/"
                mappingObjects.forEach { (name, obj) ->
                    obj.saveToFile(File("$dir$name.json"))
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

    fun addTrashClass(classNode: ClassNode) {
        classes[classNode.name] = classNode
        trashClasses[classNode.name] = classNode
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

}