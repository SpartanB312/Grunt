package net.spartanb312.grunteon.ui

import kotlinx.serialization.Transient
import net.spartanb312.grunteon.obfuscator.lang.I18n
import net.spartanb312.grunteon.obfuscator.lang.Language
import net.spartanb312.grunteon.obfuscator.process.*
import kotlin.test.*

class UiSchemaCacheTest {
    data class EditorFixture(
        val second: String = "second",
        @HiddenFromAutoParameter val hidden: Int = 1,
        val first: Int = 2,
        @Transient val transient: Int = 3,
    )

    data class BodyFixture(val id: Int) {
        val names = mutableListOf("default")
        var mode = "default"
    }

    @Test
    fun snapshotPreservesNonConstructorPropertiesAndDetachesTheirCollections() {
        val original = BodyFixture(1).apply {
            names += "loaded"
            mode = "loaded"
        }
        val snapshot = configSnapshot(original)
        assertTrue(configContentEquals(original, snapshot))
        original.names.clear()
        assertFalse(configContentEquals(original, snapshot))
        original.mode = "edited"
        assertEquals(listOf("default", "loaded"), snapshot.names)
        assertEquals("loaded", snapshot.mode)
    }

    data class ArrayFixture(val numbers: IntArray, val nested: Array<ByteArray>) {
        val body = mutableListOf("body")
    }

    @Test
    fun structuralComparisonHandlesClonedArraysAndEachBodyField() {
        val original = ArrayFixture(intArrayOf(1), arrayOf(byteArrayOf(2)))
        val snapshot = configSnapshot(original)
        assertNotEquals(original, snapshot) // Normal data-class equals compares the arrays by identity.
        assertTrue(configContentEquals(original, snapshot))
        original.body += "changed"
        assertFalse(configContentEquals(original, snapshot))
        original.body.removeLast()
        assertTrue(configContentEquals(original, snapshot))
        original.nested[0][0] = 3
        assertFalse(configContentEquals(original, snapshot))
        original.nested[0][0] = 2
        original.numbers[0] = 4
        assertFalse(configContentEquals(original, snapshot))
    }

    @Test
    fun structuralComparisonChecksNestedMapContentsAndRetainedIterationOrder() {
        val original = linkedMapOf("first" to intArrayOf(1), "second" to intArrayOf(2))
        val snapshot = configSnapshot(original)
        assertTrue(configContentEquals(original, snapshot))
        original.getValue("first")[0] = 3
        assertFalse(configContentEquals(original, snapshot))
        original.getValue("first")[0] = 1
        val first = original.remove("first")!!
        original["first"] = first
        assertFalse(configContentEquals(original, snapshot))
    }

    @AfterTest
    fun resetLanguage() {
        I18n.setLanguage(Language.English)
        I18n.clearCatalogsForTesting()
    }

    @Test
    fun schemaIsReusedWithoutRetainingEditorValueOrChangingPropertyOrder() {
        val schema = ConfigSchemas.editor(EditorFixture::class)
        assertSame(schema, ConfigSchemas.editor(EditorFixture::class))
        assertEquals(listOf("second", "first"), schema.properties.map { it.name })
        val first = EditorFixture(second = "one", first = 10)
        val second = EditorFixture(second = "two", first = 20)
        fun update(value: EditorFixture) = schema.copy.callBy(mapOf(
            schema.copy.parameters[0] to value, schema.parameters.getValue("first") to 30)) as EditorFixture
        assertEquals(first.copy(first = 30), update(first))
        assertEquals(second.copy(first = 30), update(second))
    }

    @Test
    fun immutableSnapshotDetachesCollectionsAndPreservesDuplicatesDefaultsAndSeed() {
        val libraries = mutableListOf("first.jar", "second.jar")
        val definition = TransformerRegistry.entries.first()
        val entry = TransformerEntry(name = "repeated", config = definition.createConfig())
        val entries = mutableListOf(entry, entry.copy(enabled = false), entry)
        val original = ObfConfig(globalConfig = GlobalConfig(libs = libraries), transformers = entries)
        val snapshot = configSnapshot(original)
        libraries.clear()
        entries.reverse()
        entries.removeLast()
        assertEquals(listOf("first.jar", "second.jar"), snapshot.globalConfig.libs)
        assertEquals(listOf(true, false, true), snapshot.transformers.map { it.enabled })
        assertEquals(3, snapshot.transformers.size)
        assertEquals(GlobalConfig().baseSeed, snapshot.globalConfig.baseSeed)
        assertEquals(GlobalConfig().compressionLevel, snapshot.globalConfig.compressionLevel)
        assertEquals(original.nativePipeline, snapshot.nativePipeline)
    }

    @Test
    fun orderChecksPreserveRepeatedEntriesAndMapWarningsToEnabledStackRows() {
        val registration = TransformerRegistry.entries.first()
        val transformer = registration.createTransformer()
        transformer.orderRules.clear()
        val rule: (List<Transformer<*>>, Int) -> Boolean = { pipeline, index ->
            assertEquals(2, pipeline.size)
            index == 0
        }
        transformer.orderRules += rule to "second occurrence warning"
        val definitions = TransformerCatalog(listOf(registration.copy(createTransformer = { transformer }, owner = "test-plugin")))
            .definitions()
        val entry = TransformerEntry(config = registration.createConfig())
        val nodes = listOf(entry.copy(enabled = false), entry, entry)
        assertEquals(mapOf(2 to listOf("second occurrence warning")), validateOrder(nodes, definitions))
        transformer.orderRules.clear()
        assertTrue(validateOrder(nodes, definitions).isEmpty())
        assertEquals(listOf(false, true, true), nodes.map { it.enabled })
    }

    @Test
    fun definitionsAndIndexAreCachedButLanguageAndCatalogReplacementInvalidateLabels() {
        val entries = TransformerRegistry.entries
        val catalog = TransformerCatalog(entries)
        val english = catalog.definitions()
        assertSame(english, catalog.definitions())
        val target = english.first()
        val key = "${target.descriptorRoot}.name"
        I18n.replaceCatalogForTesting(Language.ChineseCN, mapOf(key to "本地名称"))
        I18n.setLanguage(Language.ChineseCN)
        val translated = catalog.definitions()
        assertNotSame(english, translated)
        val config = target.configFactory()
        assertEquals("本地名称", findDefinition(config, translated)?.label)
        assertSame(target.transformerPrototype, findDefinition(config, translated)?.transformerPrototype)
        assertEquals(english.map { it.configClass }.toSet(), translated.map { it.configClass }.toSet())
        assertSame(translated, catalog.definitions())
        I18n.replaceCatalogForTesting(Language.ChineseCN, mapOf(key to "更新名称"))
        assertEquals("更新名称", findDefinition(config, catalog.definitions())?.label)
        I18n.setLanguage(Language.English)
        assertEquals(target.label, findDefinition(config, catalog.definitions())?.label)
    }
}
