package net.spartanb312.grunteon.index

import net.spartanb312.grunteon.index.info.ClassInfo
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexArchiveTest {
    @Test
    fun boundedWorkersCloseEveryStreamIncludingFailedClasses() = withDirectory { directory ->
        val archive = File(directory, "many.jar")
        val count = 2048
        JarOutputStream(archive.outputStream()).use { jar ->
            repeat(count) { index ->
                jar.putNextEntry(JarEntry("Class$index.class"))
                jar.write(byteArrayOf((index shr 8).toByte(), index.toByte()))
                jar.closeEntry()
            }
            jar.putNextEntry(JarEntry("not-a-class.txt"))
            jar.write(42)
            jar.closeEntry()
        }
        val workers = 3
        val started = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val ready = CountDownLatch(workers)
        val release = CountDownLatch(1)
        val streams = ConcurrentLinkedQueue<InputStream>()
        val previousStream = ThreadLocal<InputStream>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<Collection<ClassInfo>> {
                readJar(archive, workers) { input ->
                    previousStream.get()?.let { previous -> assertFailsWith<IOException> { previous.read() } }
                    previousStream.set(input)
                    streams += input
                    started.incrementAndGet()
                    val current = active.incrementAndGet()
                    peak.updateAndGet { maxOf(it, current) }
                    ready.countDown()
                    try {
                        check(release.await(10, TimeUnit.SECONDS))
                        val value = (input.read() shl 8) or input.read()
                        if (value % 17 == 0) throw IOException("Malformed fixture class")
                        ClassInfo(1, "Class$value", null, null, null)
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                assertEquals(workers, started.get())
                assertFalse(future.isDone)
            } finally {
                release.countDown()
            }
            val classes = future.get(30, TimeUnit.SECONDS)
            assertEquals(count - (count - 1) / 17 - 1, classes.size)
            assertEquals(count, started.get())
            assertEquals(workers, peak.get())
            assertEquals(0, active.get())
            streams.forEach { assertFailsWith<IOException> { it.read() } }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
        assertNoOpenFiles(directory)
    }

    @Test
    fun parsesActualClassMetadataAndReleasesJarAfterRepeatedRuns(): Unit = withDirectory { directory ->
        val archive = File(directory, "valid.jar")
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "sample/Library", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PUBLIC, "field", "I", null, null).visitEnd()
        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "method", "()V", null, null).visitEnd()
        writer.visitEnd()
        JarOutputStream(archive.outputStream()).use { jar ->
            jar.putNextEntry(JarEntry("sample/"))
            jar.closeEntry()
            jar.putNextEntry(JarEntry("sample/Library.class"))
            jar.write(writer.toByteArray())
            jar.closeEntry()
            jar.putNextEntry(JarEntry("bad.class"))
            jar.write(byteArrayOf(1, 2, 3))
            jar.closeEntry()
        }
        repeat(40) {
            val info = readJar(archive, 2).single()
            assertEquals("sample/Library", info.name)
            assertEquals(listOf("field"), info.fields.map { it.name })
            assertEquals(listOf("method"), info.methods.map { it.name })
            assertNoOpenFiles(directory)
        }
        assertFailsWith<IllegalArgumentException> { readJar(archive, 0) }
    }
}
