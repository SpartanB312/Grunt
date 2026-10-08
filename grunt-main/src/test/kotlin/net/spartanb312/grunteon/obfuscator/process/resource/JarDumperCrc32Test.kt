package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.util.ImplLookupGetter
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.file.corruptCRC32
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JarDumperCrc32Test {

    @Test
    fun disabledCorruptionPreservesValidChecksumsAndContents() = withFixture { fixture ->
        val archive = fixture.dump(corrupt = false)
        assertEquals(fixture.contents.keys, archive.entries.keys)
        for ((name, entry) in archive.entries) {
            assertContentEquals(fixture.contents.getValue(name), entry.content, name)
            assertEquals(crc32(entry.content), entry.crc, name)
            assertMetadata(archive.bytes, name, entry)
        }
        ZipInputStream(ByteArrayInputStream(archive.bytes)).use { zip ->
            val names = mutableSetOf<String>()
            while (true) {
                val entry = zip.nextEntry ?: break
                assertContentEquals(fixture.contents.getValue(entry.name), zip.readBytes(), entry.name)
                names.add(entry.name)
            }
            assertEquals(fixture.contents.keys, names)
        }
    }

    @Test
    fun enabledCorruptionChangesEveryStoredChecksumButNotEntryContents() = withFixture { fixture ->
        val valid = fixture.dump(corrupt = false)
        val corrupt = fixture.dump(corrupt = true)
        assertEquals(valid.entries.keys, corrupt.entries.keys)
        for ((name, entry) in corrupt.entries) {
            val original = valid.entries.getValue(name)
            assertContentEquals(fixture.contents.getValue(name), entry.content, name)
            assertContentEquals(original.content, entry.content, name)
            assertMetadata(corrupt.bytes, name, entry)
            assertNotEquals(crc32(entry.content), entry.crc, name)
            assertContentEquals(original.compressed, entry.compressed, name)
            val error = assertFailsWith<ZipException>(name) {
                // Start at each local header so a failure on one entry cannot hide the others.
                val input = ByteArrayInputStream(
                    corrupt.bytes, entry.localOffset, corrupt.bytes.size - entry.localOffset
                )
                ZipInputStream(input).use { zip ->
                    assertEquals(name, zip.nextEntry.name)
                    zip.readBytes()
                }
            }
            assertTrue(error.message.orEmpty().contains("invalid entry CRC"), error.message)
        }
    }

    @Test
    fun corruptionUsesReproduciblePerEntrySeeds() = withFixture { fixture ->
        val first = fixture.dump(corrupt = true, seed = "crc-seed")
        repeat(3) {
            val again = fixture.dump(corrupt = true, seed = "crc-seed")
            assertEquals(first.entries.mapValues { it.value.crc }, again.entries.mapValues { it.value.crc })
            for ((name, entry) in again.entries) {
                assertMetadata(again.bytes, name, entry)
                assertContentEquals(first.entries.getValue(name).content, entry.content, name)
            }
        }
        val different = fixture.dump(corrupt = true, seed = "another-crc-seed")
        assertNotEquals(first.entries.mapValues { it.value.crc }, different.entries.mapValues { it.value.crc })
    }

    @Test
    fun corruptedChecksumRemainsStableBetweenUpdatesAndResets() {
        ZipOutputStream(ByteArrayOutputStream()).use { zip ->
            zip.corruptCRC32(Xoshiro256PPRandom(ByteArray(32) { (it + 1).toByte() }))
            val checksum = ImplLookupGetter.getLookup()
                .findVarHandle(ZipOutputStream::class.java, "crc", CRC32::class.java).get(zip) as CRC32
            for (content in listOf(ByteArray(0), ByteArray(4096) { it.toByte() }, byteArrayOf(1, 2, 3))) {
                checksum.update(content)
                val stored = checksum.value
                assertTrue(stored in 0L..0xffff_ffffL)
                assertNotEquals(crc32(content), stored)
                repeat(4) { assertEquals(stored, checksum.value) }
                checksum.reset()
            }
        }
    }

    private fun assertMetadata(bytes: ByteArray, name: String, entry: Entry) {
        val local = entry.localOffset
        assertEquals(0x04034b50L, bytes.uint(local), name)
        assertEquals(ZipEntry.DEFLATED, bytes.ushort(local + 8), name)
        assertEquals(entry.flags, bytes.ushort(local + 6), name)
        assertTrue(entry.flags and 8 != 0, "$name must use a data descriptor")
        // With bit 3 set, the local header carries zeros; the descriptor carries the actual metadata.
        assertEquals(0L, bytes.uint(local + 14), name)
        assertEquals(0L, bytes.uint(local + 18), name)
        assertEquals(0L, bytes.uint(local + 22), name)
        val nameLength = bytes.ushort(local + 26)
        assertEquals(name, bytes.copyOfRange(local + 30, local + 30 + nameLength).toString(Charsets.UTF_8))
        val descriptor = local + 30 + nameLength + bytes.ushort(local + 28) + entry.compressed.size
        assertEquals(0x08074b50L, bytes.uint(descriptor), name)
        assertEquals(entry.crc, bytes.uint(descriptor + 4), "$name descriptor/central CRC")
        assertEquals(entry.compressed.size.toLong(), bytes.uint(descriptor + 8), name)
        assertEquals(entry.content.size.toLong(), bytes.uint(descriptor + 12), name)
    }

    private class Fixture(val instance: Grunteon, val output: Path, val contents: Map<String, ByteArray>) {
        fun dump(corrupt: Boolean, seed: String = "crc-seed"): Archive {
            val configured = Grunteon(
                globalConfig = instance.globalConfig.copy(corruptCRC32 = corrupt, baseSeed = seed),
                io = instance.io,
                workRes = instance.workRes,
                transformers = emptyList()
            )
            context(configured) {
                JarDumper.dumpJar(PathResourceOutput(output))
            }
            return readArchive(output)
        }
    }

    private fun withFixture(test: (Fixture) -> Unit) {
        val directory = createTempDirectory("grunteon-crc32")
        val input = directory.resolve("input.jar")
        val output = directory.resolve("output.jar")
        // Root-level entries isolate CRC handling from directory-offset behavior.
        val contents = linkedMapOf(
            "Input.class" to classBytes("Input"),
            "resource.bin" to ByteArray(4096) { it.toByte() },
            "empty.bin" to ByteArray(0),
            "Generated.class" to classBytes("Generated"),
            "generated.bin" to "generated resource".toByteArray()
        )
        var instance: Grunteon? = null
        try {
            ZipOutputStream(input.outputStream()).use { zip ->
                for ((name, content) in contents.entries.take(3)) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            val created = Grunteon.create(
                ObfConfig(globalConfig = GlobalConfig(
                    input = "input.jar",
                    output = "output.jar",
                    dumpMappings = false,
                    removeTimeStamps = true,
                    controllableRandom = true,
                    missingCheck = false
                )),
                ObfuscationIO(PathResourceInput(input), output = PathResourceOutput(output))
            )
            instance = created
            created.workRes.addGeneratedClass(ClassNode().apply {
                ClassReader(contents.getValue("Generated.class")).accept(this, 0)
            })
            created.workRes.addGeneratedResource("generated.bin", contents.getValue("generated.bin"))
            test(Fixture(created, output, contents))
        } finally {
            instance?.workRes?.inputResourceSet?.root?.fileSystem?.close()
            output.deleteIfExists()
            input.deleteIfExists()
            directory.deleteIfExists()
        }
    }

    private fun classBytes(name: String): ByteArray = ClassWriter(0).apply {
        visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "answer", "()I", null, null).apply {
            visitCode()
            visitIntInsn(Opcodes.BIPUSH, 42)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(1, 0)
            visitEnd()
        }
        visitEnd()
    }.toByteArray()

    private data class Archive(val bytes: ByteArray, val entries: Map<String, Entry>)

    private data class Entry(
        val crc: Long,
        val flags: Int,
        val localOffset: Int,
        val compressed: ByteArray,
        val content: ByteArray
    )

    companion object {
        private fun crc32(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

        private fun ByteArray.ushort(offset: Int): Int =
            (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

        private fun ByteArray.uint(offset: Int): Long =
            ushort(offset).toLong() or (ushort(offset + 2).toLong() shl 16)

        private fun readArchive(path: Path): Archive {
            val bytes = path.readBytes()
            // Fixtures have no archive comment or ZIP64 records.
            val end = bytes.size - 22
            assertEquals(0x06054b50L, bytes.uint(end))
            var central = bytes.uint(end + 16).toInt()
            val entries = linkedMapOf<String, Entry>()
            ZipFile(path.toFile()).use { zip ->
                repeat(bytes.ushort(end + 10)) {
                    assertEquals(0x02014b50L, bytes.uint(central))
                    assertEquals(ZipEntry.DEFLATED, bytes.ushort(central + 10))
                    val nameLength = bytes.ushort(central + 28)
                    val name = bytes.copyOfRange(central + 46, central + 46 + nameLength).toString(Charsets.UTF_8)
                    val entry = zip.getEntry(name)
                    val local = bytes.uint(central + 42).toInt()
                    val data = local + 30 + bytes.ushort(local + 26) + bytes.ushort(local + 28)
                    val compressedSize = bytes.uint(central + 20).toInt()
                    val content = zip.getInputStream(entry).use { it.readBytes() }
                    assertEquals(entry.crc, bytes.uint(central + 16), name)
                    assertEquals(content.size.toLong(), bytes.uint(central + 24), name)
                    entries[name] = Entry(
                        entry.crc, bytes.ushort(central + 8), local,
                        bytes.copyOfRange(data, data + compressedSize), content
                    )
                    central += 46 + nameLength + bytes.ushort(central + 30) + bytes.ushort(central + 32)
                }
            }
            assertEquals(end.toLong(), central.toLong())
            return Archive(bytes, entries)
        }
    }
}