package net.spartanb312.grunteon.index

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunteon.index.info.ClassInfo
import net.spartanb312.grunteon.index.io.saveToFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.jar.JarFile

const val FILE_VERSION = "26.4.1" // year.month.version

fun main(args: Array<String>) {
    val input = args[0]
    val name = input.removeSuffix(".jar")
    val output = "$name.gi"
    readJar(File(input)).saveToFile(File(output))
}

internal fun readJar(
    file: File,
    workerCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8),
    parseClass: (InputStream) -> ClassInfo = { input ->
        val classNode = ClassNode()
        ClassReader(input).accept(classNode, ClassReader.SKIP_CODE)
        ClassInfo.parse(classNode)
    }
): Collection<ClassInfo> {
    require(workerCount > 0)
    val classInfo = ConcurrentLinkedQueue<ClassInfo>()
    JarFile(file).use { jar ->
        // The archive outlives all workers; both queued entries and open entry streams are bounded.
        runBlocking {
            val entries = Channel<JarEntry>(workerCount)
            repeat(workerCount) {
                launch(Dispatchers.IO) {
                    for (entry in entries) {
                        // Keep the existing best-effort contract for malformed/unsupported classes.
                        runCatching {
                            jar.getInputStream(entry).use { classInfo.add(parseClass(it)) }
                        }
                    }
                }
            }
            try {
                val archiveEntries = jar.entries()
                while (archiveEntries.hasMoreElements()) {
                    val entry = archiveEntries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".class")) entries.send(entry)
                }
            } finally {
                entries.close()
            }
        }
    }
    return classInfo
}