package net.spartanb312.grunt.obfuscate.resource

import net.spartanb312.grunt.utils.isExcluded
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.shouldRemove
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ResourceCache(private val input: String/*, private val libs: List<String>*/) {

    val classes = mutableMapOf<String, ClassNode>()

    //val libraries = mutableMapOf<String, ClassNode>()
    val resources = mutableMapOf<String, ByteArray>()

    val manifest = mutableMapOf<String, String>()
    val pluginInfo = mutableMapOf<String, String>()

    val nonExcluded get() = classes.filter { !it.key.isExcluded }.values

    fun applyRemap(remap: Map<String, String>, remapClasses: Boolean = false) {
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
        //readLibs()
    }

    fun dumpJar(targetFile: String) = ZipOutputStream(File(targetFile).outputStream()).apply {
        Logger.info("Writing Classes...")
        for (classNode in classes.values) {
            if (classNode.name == "module-info" || classNode.name.shouldRemove) continue
            val byteArray = ClassWriter(COMPUTE_MAXS).apply { classNode.accept(this) }.toByteArray()
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
        putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
        write(manifest.entries.joinToString("\n", postfix = "\n") { "${it.key}: ${it.value}" }.toByteArray())
        closeEntry()
        close()
        Logger.info("Done!")
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
                    } else if (it.name == "META-INF/MANIFEST.MF") {
                        getInputStream(it).readBytes().decodeToString().split("\n").forEach { m ->
                            if (m.isNotBlank()) {
                                runCatching {
                                    val (k, v) = m.split(": ")
                                    this@ResourceCache.manifest[k] = v.replace("\r", "")
                                }
                            }
                        }
                    } else resources[it.name] = getInputStream(it).readBytes()
                }
        }
    }

    //private fun readLibs() {
    //    Logger.info("Reading Libraries...")
    //    libs.map { File(it) }.forEach { jar ->
    //        Logger.info("  - ${jar.name}")
    //        JarFile(jar).apply {
    //            entries().asSequence()
    //                .filter { !it.isDirectory }
    //                .forEach forLoop@{
    //                    if (it.name.endsWith(".class")) {
    //                        kotlin.runCatching {
    //                            ClassReader(getInputStream(it)).apply {
    //                                val classNode = ClassNode()
    //                                accept(classNode, ClassReader.EXPAND_FRAMES)
    //                                libraries[classNode.name] = classNode
    //                            }
    //                        }
    //                    }
    //                }
    //        }
    //    }
    //}

}