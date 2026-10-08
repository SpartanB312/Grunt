package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkResourcesJarRoundTripTest {
    private val accessorName = "mixin/LivingEntityAccessor"
    private val existingName = "mixin/ExistingAccessor"
    private val mixinConfig = """{"mixins":["LivingEntityAccessor"]}""".toByteArray()

    @Test
    fun noOpPreservesMixinAccessorWithGlobalExclusions() = withArchives { input, output, fileSystems ->
        writeJar(input, accessorName)
        val instance = Grunteon.create(config(input, output))
        fileSystems.add(instance.workRes.inputResourceSet.root.fileSystem)
        assertEquals(setOf(accessorName), instance.workRes.inputClassMap.keys.toSet())
        instance.run()
        assertAccessor(output, accessorName)
    }

    @Test
    fun nextNoOpJobReadsClassesAddedToRebuiltInput() = withArchives { input, output, fileSystems ->
        writeJar(input, existingName)
        val first = Grunteon.create(config(input, output))
        fileSystems.add(first.workRes.inputResourceSet.root.fileSystem)
        first.run()
        assertAccessor(output, existingName)

        replaceJar(input, existingName, accessorName)
        val second = Grunteon.create(config(input, output))
        fileSystems.add(second.workRes.inputResourceSet.root.fileSystem)
        assertEquals(setOf(existingName, accessorName), second.workRes.inputClassMap.keys.toSet())
        second.run()
        assertAccessor(output, existingName)
        assertAccessor(output, accessorName)
    }

    @Test
    fun readDoesNotReuseOrCloseCallerOwnedArchive() = withArchives { input, _, fileSystems ->
        writeJar(input, existingName)
        val callerFileSystem = FileSystems.newFileSystem(URI.create("jar:" + input.toUri()), emptyMap<String, String>())
        fileSystems.add(callerFileSystem)
        replaceJar(input, accessorName)

        val resources = WorkResources.read(input)
        fileSystems.add(resources.inputResourceSet.root.fileSystem)
        assertEquals(setOf(accessorName), resources.inputClassMap.keys.toSet())
        resources.inputResourceSet.root.fileSystem.close()
        assertTrue(callerFileSystem.isOpen)
        assertSame(callerFileSystem, FileSystems.getFileSystem(URI.create("jar:" + input.toUri())))
    }

    @Test
    fun runLeavesCallerProvidedArchivePathOpen() = withArchives { input, _, fileSystems ->
        writeJar(input, accessorName)
        val callerFileSystem = FileSystems.newFileSystem(URI.create("jar:" + input.toUri()), emptyMap<String, String>())
        fileSystems.add(callerFileSystem)
        val instance = Grunteon.create(
            ObfConfig(globalConfig = GlobalConfig(output = null)),
            ObfuscationIO(PathResourceInput(callerFileSystem.getPath("/")))
        )
        instance.run()
        assertTrue(callerFileSystem.isOpen)
    }

    @Test
    fun useClosesOwnedInputAndLibraryArchivesAfterRun() = withArchives { input, library, fileSystems ->
        writeJar(input, accessorName)
        writeJar(library, existingName)
        val instance = Grunteon.create(ObfConfig(
            globalConfig = GlobalConfig(input = input.toString(), output = null, libs = listOf(library.toString()))
        ))
        fileSystems.add(instance.workRes.inputResourceSet.root.fileSystem)
        fileSystems.add(instance.workRes.libraryResourceSets.values.single().root.fileSystem)
        instance.use {
            it.run()
            fileSystems.forEach { fileSystem -> assertTrue(fileSystem.isOpen) }
            assertContentEquals(mixinConfig, assertNotNull(it.workRes.getInputResource("mixins.json")).content)
        }
        fileSystems.forEach { assertFalse(it.isOpen) }
    }

    @Test
    fun useClosesOwnedArchivesWhenOutputFails() = withArchives { input, _, fileSystems ->
        writeJar(input, accessorName)
        val failure = IOException("test output failure")
        val output = object : ResourceOutput {
            override val description = "failing output"
            override val fileName = "output.jar"
            override fun exists() = false
            override fun openOutputStream(): OutputStream = throw failure
        }
        val instance = Grunteon.create(ObfConfig(), ObfuscationIO(PathResourceInput(input), output = output))
        val fileSystem = instance.workRes.inputResourceSet.root.fileSystem
        fileSystems.add(fileSystem)
        assertSame(failure, assertFailsWith<IOException> { instance.use { it.run() } })
        assertFalse(fileSystem.isOpen)
    }

    @Test
    fun workResourcesCloseLeavesCallerProvidedFileSystemOpen() = withArchives { input, _, fileSystems ->
        writeJar(input, accessorName)
        val callerFileSystem = FileSystems.newFileSystem(URI.create("jar:" + input.toUri()), emptyMap<String, String>())
        fileSystems.add(callerFileSystem)
        WorkResources.read(callerFileSystem.getPath("/")).use {
            assertEquals(setOf(accessorName), it.inputClassMap.keys.toSet())
        }
        assertTrue(callerFileSystem.isOpen)
    }

    private fun config(input: Path, output: Path) = ObfConfig(
        globalConfig = GlobalConfig(
            input = input.toString(),
            output = output.toString(),
            exclusions = listOf("**"),
            mixinExclusions = listOf("mixin/**")
        )
    )

    private fun writeJar(path: Path, vararg names: String) {
        ZipOutputStream(path.outputStream()).use { zip ->
            for (name in names) {
                zip.putNextEntry(ZipEntry("$name.class"))
                zip.write(accessorBytes(name))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("mixins.json"))
            zip.write(mixinConfig)
            zip.closeEntry()
        }
    }

    private fun replaceJar(path: Path, vararg names: String) {
        val replacement = createTempFile(path.parent, "rebuilt-", ".jar")
        try {
            writeJar(replacement, *names)
            Files.move(replacement, path, REPLACE_EXISTING)
        } finally {
            replacement.deleteIfExists()
        }
    }

    private fun accessorBytes(name: String): ByteArray = ClassWriter(0).apply {
        visit(
            Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT or Opcodes.ACC_INTERFACE,
            name, null, "java/lang/Object", null
        )
        visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).apply {
            visitArray("targets").apply {
                visit(null, "example.LivingEntity")
                visitEnd()
            }
            visitEnd()
        }
        visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "getHealth", "()F", null, null).apply {
            visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true).apply {
                visit("value", "health")
                visitEnd()
            }
            visitEnd()
        }
        visitEnd()
    }.toByteArray()

    private fun assertAccessor(output: Path, name: String) {
        ZipFile(output.toFile()).use { zip ->
            val entry = assertNotNull(zip.getEntry("$name.class"))
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            assertTrue(bytes.isNotEmpty())
            val node = ClassNode().also { ClassReader(bytes).accept(it, 0) }
            assertEquals(name, node.name)
            assertTrue(node.access and Opcodes.ACC_INTERFACE != 0)
            assertEquals("Lorg/spongepowered/asm/mixin/Mixin;", node.invisibleAnnotations.single().desc)
            val getter = node.methods.single()
            assertEquals("getHealth", getter.name)
            assertEquals("()F", getter.desc)
            assertEquals("Lorg/spongepowered/asm/mixin/gen/Accessor;", getter.visibleAnnotations.single().desc)
            assertContentEquals(mixinConfig, zip.getInputStream(zip.getEntry("mixins.json")).use { it.readBytes() })
        }
    }

    private fun withArchives(block: (Path, Path, MutableList<FileSystem>) -> Unit) {
        val dir = createTempDirectory("grunteon-jar-roundtrip-")
        val input = dir.resolve("input.jar")
        val output = dir.resolve("output.jar")
        val fileSystems = mutableListOf<FileSystem>()
        try {
            block(input, output, fileSystems)
        } finally {
            fileSystems.distinct().forEach { if (it.isOpen) it.close() }
            input.deleteIfExists()
            output.deleteIfExists()
            dir.deleteIfExists()
        }
    }
}