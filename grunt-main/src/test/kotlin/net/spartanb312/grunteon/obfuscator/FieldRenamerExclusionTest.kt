package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.testcase.fieldrename.exclusions.FieldExclusions
import org.objectweb.asm.ClassWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FieldRenamerExclusionTest {
    private val fixture = FieldExclusions::class.java.name.replace('.', '/')
    private val parent = "$fixture\$AParent"
    private val child = "$fixture\$BChild"
    private val grandChild = "$fixture\$CGrandChild"

    @Test
    fun globalClassExclusionProtectsHidingDeclarations() {
        val instance = transform(global = GlobalConfig(exclusions = listOf(child)))
        assertChildUnchanged(instance)
        assertInheritedReferences(instance)
        execute(instance, "global")
    }

    @Test
    fun localClassExclusionProtectsHidingDeclarations() {
        val instance = transform(config = FieldRenamer.Config(
            classFilter = ClassFilterConfig(excludeStrategy = listOf(child))
        ))
        assertChildUnchanged(instance)
        assertInheritedReferences(instance)
        execute(instance, "local")
    }

    @Test
    fun classPrefixExclusionsAndEmptyIncludesKeepDeclarations() {
        for (global in listOf(true, false)) {
            val instance = if (global) {
                transform(global = GlobalConfig(exclusions = listOf("$fixture**")))
            } else {
                transform(config = FieldRenamer.Config(classFilter = ClassFilterConfig(includeStrategy = emptyList())))
            }
            assertChildUnchanged(instance)
            assertEquals("inherited", instance.nameMapping.mapFieldName(parent, "inherited", "J"))
            execute(instance, "all-$global")
        }
    }

    @Test
    fun ownerFieldExclusionsKeepExactAndPrefixMatches() {
        val config = readConfig("""
            "fieldExclusions": ["$child.shadow", "$child.shared", "$child.hidden", "$child.a", "$child.keep**"]
        """)
        val instance = transform(config)
        assertChildUnchanged(instance)
        assertInheritedReferences(instance)
        execute(instance, "fields")
    }

    @Test
    fun exactFieldExclusionDoesNotBecomeImplicitPrefixOrRegex() {
        val instance = transform(readConfig("""
            "fieldExclusions": ["$child.keep", "$parent.inherited.*"]
        """))
        assertEquals("keep", instance.nameMapping.mapFieldName(child, "keep", "I"))
        assertNotEquals("keepMore", instance.nameMapping.mapFieldName(child, "keepMore", "I"))
        assertNotEquals("inherited", instance.nameMapping.mapFieldName(parent, "inherited", "J"))
        execute(instance, "exact")
    }

    @Test
    fun excludedBareNamesAreStillExact() {
        val instance = transform(FieldRenamer.Config(excludedNames = listOf("keep")))
        assertEquals("keep", instance.nameMapping.mapFieldName(child, "keep", "I"))
        assertNotEquals("keepMore", instance.nameMapping.mapFieldName(child, "keepMore", "I"))
        execute(instance, "bare")
    }

    @Test
    fun excludedParentNamesCannotBeShadowedByGeneratedChildNames() {
        val instance = transform(global = GlobalConfig(exclusions = listOf("$fixture\$EReservedParent")))
        assertNotEquals("a", instance.nameMapping.mapFieldName("$fixture\$FReservedChild", "other", "J"))
        execute(instance, "reserved-parent")
    }

    @Test
    fun fieldExclusionsRoundTripAndDefaultToEmpty() {
        val path = Files.createTempFile("field-exclusions", ".json")
        try {
            val config = readConfig(""""fieldExclusions": ["$child.keep", "$child.shadow**"]""")
            ObfConfig.write(ObfConfig(transformers = listOf(TransformerEntry(config = config))), path)
            assertContains(path.readText(), "\"fieldExclusions\"")
            assertContains(path.readText(), "$child.keep")
            assertEquals(config, ObfConfig.read(path).transformers.single().config)
            ObfConfig.write(ObfConfig(transformers = listOf(TransformerEntry(config = FieldRenamer.Config()))), path)
            assertContains(path.readText(), "\"fieldExclusions\": []")
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun readConfig(properties: String): FieldRenamer.Config {
        val path = Files.createTempFile("field-exclusion-input", ".json")
        return try {
            path.writeText("""
                {"transformers": [{"config": {
                    "type": "net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer.Config",
                    $properties
                }}]}
            """.trimIndent())
            ObfConfig.read(path).transformers.single().config as FieldRenamer.Config
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun transform(
        config: FieldRenamer.Config = FieldRenamer.Config(),
        global: GlobalConfig = GlobalConfig()
    ): Grunteon {
        val instance = readTestClasses(FieldExclusions::class.java, ObfConfig(
            globalConfig = global.copy(output = null, dumpMappings = false),
            transformers = listOf(TransformerEntry(config = config))
        ))
        retainFixture(instance)
        instance.run()
        return instance
    }

    private fun retainFixture(instance: Grunteon) {
        instance.workRes.inputClassMap.keys.removeAll { !it.startsWith(fixture) }
    }

    private fun assertChildUnchanged(instance: Grunteon) {
        val fields = instance.workRes.inputClassMap.getValue(child).fields.map { it.name }.toSet()
        assertEquals(setOf("shadow", "shared", "hidden", "a", "keep", "keepMore"), fields)
        for ((name, desc) in listOf("shadow" to "I", "shared" to "I", "hidden" to "D")) {
            assertEquals(name, instance.nameMapping.mapFieldName(child, name, desc))
        }
    }

    private fun assertInheritedReferences(instance: Grunteon) {
        for (name in listOf("inherited", "inheritedStatic")) {
            val mapped = instance.nameMapping.mapFieldName(parent, name, "J")
            assertNotEquals(name, mapped)
            assertNotEquals("a", mapped, "A retained child declaration must not hide the renamed parent field")
            assertEquals(mapped, instance.nameMapping.mapFieldName(child, name, "J"))
            assertEquals(mapped, instance.nameMapping.mapFieldName(grandChild, name, "J"))
        }
        assertNotEquals("allowed", instance.nameMapping.mapFieldName(parent, "allowed", "Z"))
        assertNotEquals("hidden", instance.nameMapping.mapFieldName(parent, "hidden", "D"))
    }

    private fun execute(instance: Grunteon, scenario: String) {
        val directory = Path.of("build/tmp/field-renamer-exclusions", scenario)
        for (node in instance.workRes.inputClassCollection) {
            val writer = ClassWriter(0)
            node.accept(writer)
            val output = directory.resolve(node.name + ".class")
            Files.createDirectories(output.parent)
            Files.write(output, writer.toByteArray())
        }
        val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        val process = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", executable).toString(),
            "-Xverify:all", "-cp", directory.toAbsolutePath().toString(), fixture.replace('/', '.')
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
    }
}
