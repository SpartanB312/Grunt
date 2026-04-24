package net.spartanb312.grunteon.index

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunteon.index.info.ClassInfo
import net.spartanb312.grunteon.index.io.saveToFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val input = args[0]
    val name = input.removeSuffix(".jar")
    val output = "$name.gi"
    val classInfo = ConcurrentLinkedQueue<ClassInfo>()
    runBlocking {
        JarFile(File(input)).apply {
            entries().asSequence()
                .filter { !it.isDirectory }
                .forEach {
                    if (it.name.endsWith(".class")) launch(Dispatchers.IO) {
                        kotlin.runCatching {
                            ClassReader(getInputStream(it)).apply {
                                val classNode = ClassNode()
                                accept(classNode, ClassReader.SKIP_CODE)
                                classInfo.add(ClassInfo.parse(classNode))
                            }
                        }
                    }
                }
        }
    }
    classInfo.saveToFile(File(output))
}