package net.spartanb312.grunteon.index

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.spartanb312.grunteon.index.info.ClassInfo
import net.spartanb312.grunteon.index.info.FieldInfo
import net.spartanb312.grunteon.index.info.MethodInfo
import net.spartanb312.grunteon.index.io.readFromFile
import net.spartanb312.grunteon.index.io.saveToFile
import net.spartanb312.grunteon.index.io.toJsonObj
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexIoTest {
    @Test
    fun streamingWriterMatchesLegacyJsonBytesFieldOrderAndUtf8() = withDirectory { directory ->
        val classes = listOf(
            ClassInfo(17, "例/Ü<&'", "historically-not-written", "java/lang/Object", listOf("iface/Å")).apply {
                methods = listOf(MethodInfo(1, "méthode", "()V", "<T:Ljava/lang/Object;>()V"))
                fields = listOf(FieldInfo(2, "поле", "Ljava/lang/String;", null))
            },
            ClassInfo(1, "Empty", null, null, emptyList())
        )
        val legacy = JsonObject().apply {
            addProperty("version", FILE_VERSION)
            add("classes", JsonArray().apply { classes.forEach { add(it.toJsonObj()) } })
        }
        val expected = GsonBuilder().setPrettyPrinting().create().toJson(legacy) + System.lineSeparator()
        val file = File(directory, "nested/index.gi")
        classes.saveToFile(file)
        assertContentEquals(expected.toByteArray(Charsets.UTF_8), file.readBytes())
        assertFalse(classes.first().toJsonObj().has("signature"))
        val restored = readFromFile(file)
        assertEquals(classes.map { it.copy(signature = null, interfaces = it.interfaces?.takeIf { it.isNotEmpty() }) }, restored)
        assertEquals(classes.first().methods, restored.first().methods)
        assertEquals(classes.first().fields, restored.first().fields)
        assertNull(restored.last().interfaces)
    }

    @Test
    fun readerPreservesReorderedUnknownFieldsDefaultsAndPrimitiveCoercion() = withDirectory { directory ->
        val file = File(directory, "index.gi")
        file.writeText("""
            { /* JsonParser previously allowed comments and unquoted names. */
              classes: [{
                fields: [{desc:'I', name:'field', access:'2'}],
                unknown: {nested:[true, null, {x:123}]},
                methods: [{desc:'()V', signature:'()V', access:1, name:'method'}],
                signature:'<T:Ljava/lang/Object;>Ljava/lang/Object;',
                name:'ignored', name:'Класс', access:4.5
              }],
              version: '$FILE_VERSION', unknown: []
            }
        """.trimIndent(), Charsets.UTF_8)
        val clazz = readFromFile(file).single()
        assertEquals(4, clazz.access)
        assertEquals("Класс", clazz.name)
        assertEquals("<T:Ljava/lang/Object;>Ljava/lang/Object;", clazz.signature)
        assertNull(clazz.superName)
        assertNull(clazz.interfaces)
        assertEquals(listOf(MethodInfo(1, "method", "()V", "()V")), clazz.methods)
        assertEquals(listOf(FieldInfo(2, "field", "I", null)), clazz.fields)
    }

    @Test
    fun validatesVersionsRequiredFieldsExplicitNullsAndTrailingContent() = withDirectory { directory ->
        val file = File(directory, "index.gi")
        fun read(text: String) {
            file.writeText(text, Charsets.UTF_8)
            readFromFile(file)
        }
        assertFails { read("{version:'1.0.0', classes:[]}") }
        assertFails { read("{version:'26.4', classes:[]}") }
        assertFails { read("{classes:[]}") }
        assertFails { read("{version:'$FILE_VERSION'}") }
        assertFails { read("{version:'$FILE_VERSION', classes:[{access:1,name:'A',fields:[]}]}") }
        assertFails { read("{version:'$FILE_VERSION', classes:[{access:1,name:'A',signature:null,fields:[],methods:[]}]}") }
        assertFails { read("{version:'$FILE_VERSION', classes:[]} {}") }
        read("{version:'99.0.0', classes:[]}")
        assertEquals(emptyList(), readFromFile(file))
        assertNoOpenFiles(directory)
    }

    @Test
    fun writesLargeLazyCollectionBeforeItHasAllElementsAndReadsItBack() = withDirectory { directory ->
        val file = File(directory, "large.gi")
        val count = 10000
        val classes = object : AbstractCollection<ClassInfo>() {
            override val size = count
            override fun iterator(): Iterator<ClassInfo> = object : Iterator<ClassInfo> {
                var index = 0
                override fun hasNext() = index < count
                override fun next(): ClassInfo {
                    if (index == 256) assertTrue(file.length() > 0, "Writer must stream before materializing all classes")
                    return ClassInfo(1, "scale/Class${index++}", null, "java/lang/Object", null).apply {
                        methods = (0 until 8).map { MethodInfo(1, "method$it", "(Ljava/lang/String;)V", null) }
                        fields = (0 until 8).map { FieldInfo(2, "field$it", "I", null) }
                    }
                }
            }
        }
        classes.saveToFile(file)
        val restored = readFromFile(file)
        assertEquals(count, restored.size)
        restored.forEachIndexed { index, clazz ->
            assertEquals("scale/Class$index", clazz.name)
            assertEquals(8, clazz.methods.size)
            assertEquals(8, clazz.fields.size)
        }
        assertNoOpenFiles(directory)
    }

    @Test
    fun closesFileWhenWriterIterationFails() = withDirectory { directory ->
        val file = File(directory, "failed.gi")
        val classes = object : AbstractCollection<ClassInfo>() {
            override val size = 1
            override fun iterator(): Iterator<ClassInfo> = object : Iterator<ClassInfo> {
                override fun hasNext() = true
                override fun next(): ClassInfo = error("fixture failure")
            }
        }
        assertFails { classes.saveToFile(file) }
        assertNoOpenFiles(directory)
    }
}

internal fun <T> withDirectory(block: (File) -> T): T {
    val directory = Files.createTempDirectory("grunt-index-test-").toFile()
    return try {
        block(directory)
    } finally {
        check(directory.deleteRecursively())
    }
}

internal fun assertNoOpenFiles(directory: File) {
    val descriptors = File("/proc/self/fd")
    if (!descriptors.isDirectory) return // Non-Linux platforms still exercise use/roundtrip paths.
    for (descriptor in descriptors.listFiles().orEmpty()) {
        val target = runCatching { Files.readSymbolicLink(descriptor.toPath()).toString() }.getOrNull() ?: continue
        assertFalse(target.startsWith(directory.absolutePath + File.separator), "Leaked descriptor: $target")
    }
}
