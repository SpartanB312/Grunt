package net.spartanb312.grunteon.obfuscator

import com.google.gson.Gson
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import net.spartanb312.grunteon.testcase.recordrename.RecordRoundTrip
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RecordRenamerTest {
    private val mainName = RecordRoundTrip::class.java.name
    private val owner = mainName.replace('.', '/')
    private val mappingName = owner + "\$Int2IntMapping"

    @Test
    fun untransformedGsonRoundTrip() = roundTrip()

    @Test
    fun fieldOnly() = roundTrip(FieldRenamer.Config(), expectFieldRename = true)

    @Test
    fun methodOnly() = roundTrip(MethodRenamer.Config(), expectMethodRename = true)

    @Test
    fun both() = roundTrip(
        FieldRenamer.Config(), MethodRenamer.Config(), expectFieldRename = true, expectMethodRename = true
    )

    @Test
    fun reverseOrder() = roundTrip(
        MethodRenamer.Config(), FieldRenamer.Config(), expectFieldRename = true, expectMethodRename = true
    )

    @Test
    fun fieldClassFilter() = roundTrip(
        FieldRenamer.Config(classFilter = excludedRecords()), MethodRenamer.Config(), expectMethodRename = true
    )

    @Test
    fun methodClassFilter() = roundTrip(
        FieldRenamer.Config(), MethodRenamer.Config(classFilter = excludedRecords()), expectFieldRename = true
    )

    @Test
    fun excludedFieldName() = roundTrip(
        FieldRenamer.Config(excludedNames = listOf("name")), MethodRenamer.Config(),
        expectFieldRename = true, expectMethodRename = true
    )

    @Test
    fun interfacesDisabled() = roundTrip(
        FieldRenamer.Config(), MethodRenamer.Config(interfaces = false),
        expectFieldRename = true, expectMethodRename = true
    )

    @Test
    fun globallyExcludedRecords() = roundTrip(
        FieldRenamer.Config(), MethodRenamer.Config(),
        global = GlobalConfig(exclusions = listOf(owner + "$**"))
    )

    @Test
    fun mixinExcludedRecords() = roundTrip(
        FieldRenamer.Config(), MethodRenamer.Config(),
        global = GlobalConfig(mixinExclusions = listOf(owner + "$**"))
    )

    private fun excludedRecords() = ClassFilterConfig(excludeStrategy = listOf(owner + "$**"))

    private fun roundTrip(
        vararg configs: TransformerConfig,
        expectFieldRename: Boolean = false,
        expectMethodRename: Boolean = false,
        global: GlobalConfig = GlobalConfig()
    ) {
        val tempDir = Files.createTempDirectory("grunteon-record-renamer-")
        try {
            val input = tempDir.resolve("input.jar")
            val fixtureJar = Path.of(RecordRoundTrip::class.java.protectionDomain.codeSource.location.toURI())
            ZipFile(fixtureJar.toFile()).use { source ->
                JarOutputStream(input.outputStream()).use { target ->
                    source.entries().asSequence().filter { it.name.startsWith(owner) && it.name.endsWith(".class") }
                        .forEach { entry ->
                            target.putNextEntry(JarEntry(entry.name))
                            source.getInputStream(entry).use { it.copyTo(target) }
                            target.closeEntry()
                        }
                }
            }
            val gsonJar = Path.of(Gson::class.java.protectionDomain.codeSource.location.toURI())
            val instance = Grunteon.create(
                ObfConfig(
                    globalConfig = global.copy(
                        input = input.toString(), output = null, dumpMappings = false, libs = listOf(gsonJar.toString())
                    ),
                    transformers = configs.map { TransformerEntry(config = it) }
                )
            )
            instance.run()
            val outputDir = tempDir.resolve("classes")
            for (node in instance.workRes.inputClassCollection) {
                val bytes = ClassWriter(0).apply { node.accept(this) }.toByteArray()
                val output = outputDir.resolve(node.name + ".class")
                Files.createDirectories(output.parent)
                Files.write(output, bytes)
            }
            val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            val java = Path.of(System.getProperty("java.home"), "bin", executable)
            val process = ProcessBuilder(
                java.toString(), "-Xverify:all", "-cp", outputDir.toString() + File.pathSeparator + gsonJar, mainName
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
            assertTrue(output.contains("record-round-trip-ok"), output)

            // Records are not excluded wholesale: unrelated members still obey the selected renamers/filters.
            val fieldName = instance.nameMapping.mapFieldName(mappingName, "marker", "Ljava/lang/String;")
            val methodName = instance.nameMapping.mapMethodName(mappingName, "describe", "()Ljava/lang/String;")
            if (expectFieldRename) assertNotEquals("marker", fieldName) else assertEquals("marker", fieldName)
            if (expectMethodRename) assertNotEquals("describe", methodName) else assertEquals("describe", methodName)
            for (node in instance.workRes.inputClassCollection) {
                val components = node.recordComponents ?: continue
                val names = components.joinToString(";") { it.name }
                val constructor = node.methods.single {
                    it.name == "<init>" && it.desc == components.joinToString("", "(", ")V") { c -> c.descriptor }
                }
                assertEquals(components.map { it.name }, constructor.parameters.map { it.name })
                node.methods.flatMap { it.instructions.asSequence().filterIsInstance<InvokeDynamicInsnNode>().toList() }
                    .filter { it.bsm.owner == "java/lang/runtime/ObjectMethods" }
                    .forEach { assertEquals(names, it.bsmArgs[1]) }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
