package net.spartanb312.grunt.process.resource

import com.google.gson.JsonObject
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.Configs.saveToFile
import net.spartanb312.grunt.utils.corruptCRC32
import net.spartanb312.grunt.utils.isExcluded
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.shouldRemove
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ResourceCache(private val input: String, private val libs: List<String>) {

    val classes = mutableMapOf<String, ClassNode>()
    val libraries = mutableMapOf<String, ClassNode>()
    val resources = mutableMapOf<String, ByteArray>()

    val manifest = mutableMapOf<String, String>()
    val pluginYml = mutableMapOf<String, String>()
    val bungeeYml = mutableMapOf<String, String>()

    val nonExcluded get() = classes.filter { !it.key.isExcluded }.values

    private val mappings = JsonObject()

    fun applyRemap(type: String, remap: Map<String, String>, remapClasses: Boolean = false) {
        if (Configs.Settings.generateRemap) {
            val obj = JsonObject()
            remap.forEach { (prev, new) ->
                obj.addProperty(prev, new)
            }
            mappings.add(type, obj)
        }
        val remapper = SimpleRemapper(remap)
        for ((name, node) in classes.toMutableMap()) {
            val copy = ClassNode()
            val adapter = ClassRemapper(copy, remapper)
            node.accept(adapter)
            classes[name] = copy
        }
        if (remapClasses) classes.toMap().forEach { (name, node) ->
            remap[name]?.let { newName ->
                classes.remove(name)
                classes[newName] = node
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

        Logger.info("Writing Classes...")
        for (classNode in classes.values) {
            if (classNode.name == "module-info" || classNode.name.shouldRemove) continue
            val byteArray = try {
                ClassDumper(this@ResourceCache, hierarchy, true).apply {
                    classNode.accept(this)
                }.toByteArray()
            } catch (ignore: Exception) {
                Logger.error("Failed to dump class ${classNode.name}. Force use COMPUTE_MAXS")
                ClassDumper(this@ResourceCache, hierarchy, false).apply {
                    classNode.accept(this)
                }.toByteArray()
            }
            putNextEntry(ZipEntry(classNode.name + ".class"))
            write(byteArray)
            closeEntry()
        }

        Logger.info("Writing Resources...")
        for ((name, bytes) in resources) {
            if (name.shouldRemove) continue
            putNextEntry(ZipEntry(name))
            write(bytes)
            closeEntry()
        }
        //Manifest plugin.yml bungee.yml
        mapOf(
            manifest to "META-INF/MANIFEST.MF",
            pluginYml to "META-INF/plugin.yml",
            bungeeYml to "META-INF/bungee.yml",
        ).forEach { (file, name) ->
            if (file.isNotEmpty()) {
                putNextEntry(ZipEntry(name))
                write(file.entries.joinToString("\n", postfix = "\n") { "${it.key}: ${it.value}" }.toByteArray())
                closeEntry()
            }
        }
        close()

        if (Configs.Settings.generateRemap) {
            Logger.info("Writing mappings...")
            mappings.saveToFile(File(Configs.Settings.RemapOutput))
        }
        Logger.info("Done!")
    }

    private fun readInput() {
        Logger.info("Reading $input")
        val map = mapOf(
            "META-INF/MANIFEST.MF" to manifest,
            "META-INF/plugin.yml" to pluginYml,
            "META-INF/bungee.yml" to bungeeYml
        )
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
                    } else {
                        val file = map[it.name]
                        if (file != null) {
                            getInputStream(it).readBytes().decodeToString().split("\n").forEach { m ->
                                if (m.isNotBlank()) {
                                    runCatching {
                                        val (k, v) = m.split(": ")
                                        file[k] = v.replace("\r", "")
                                    }
                                }
                            }
                        } else resources[it.name] = getInputStream(it).readBytes()
                    }
                }
        }
    }

    private fun readLibs() {
        Logger.info("Reading Libraries...")
        libs.map { File(it) }.forEach { jar ->
            Logger.info("  - ${jar.name}")
            JarFile(jar).apply {
                entries().asSequence()
                    .filter { !it.isDirectory }
                    .forEach forLoop@{
                        if (it.name.endsWith(".class")) {
                            kotlin.runCatching {
                                ClassReader(getInputStream(it)).apply {
                                    val classNode = ClassNode()
                                    accept(classNode, ClassReader.EXPAND_FRAMES)
                                    libraries[classNode.name] = classNode
                                }
                            }
                        }
                    }
            }
        }
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