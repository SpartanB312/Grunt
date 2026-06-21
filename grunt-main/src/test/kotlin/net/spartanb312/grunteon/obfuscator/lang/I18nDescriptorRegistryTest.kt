package net.spartanb312.grunteon.obfuscator.lang

import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class I18nDescriptorRegistryTest {

    @Test
    fun normalizesSegmentsToLowerCamel() {
        assertEquals("nativeCandidate", I18nDescriptorRegistry.normalizeSegment("NativeCandidate"))
        assertEquals("compilerExecutable", I18nDescriptorRegistry.normalizeSegment("compilerExecutable"))
        assertEquals("uiLogLevel", I18nDescriptorRegistry.normalizeSegment("uiLogLevel"))
        assertEquals("xdebug", I18nDescriptorRegistry.normalizeSegment("-Xdebug"))
    }

    @Test
    fun generatesNestedListDataClassItemKeys() {
        val builder = I18nDescriptorRegistry.Builder()
        I18nDescriptorRegistry.collectConfig(builder, "sample.config", SampleConfig::class)

        val catalog = builder.build()
        assertEquals("Rules", catalog["sample.config.rules.name"])
        assertEquals("Rules used by the sample", catalog["sample.config.rules.desc"])
        assertEquals("Detection rule", catalog["sample.config.rules.item.name"])
        assertEquals("Set detection rule for scanner", catalog["sample.config.rules.item.desc"])
        assertEquals("Name", catalog["sample.config.rules.item.name.name"])
        assertEquals("Human readable rule name", catalog["sample.config.rules.item.name.desc"])
    }

    @Test
    fun duplicateDescriptorKeysWithConflictingFallbacksFail() {
        val builder = I18nDescriptorRegistry.Builder()

        assertFailsWith<IllegalArgumentException> {
            I18nDescriptorRegistry.collectConfig(builder, "sample.config", DuplicateKeyConfig::class)
        }
    }

    private data class SampleConfig(
        @SettingDesc("Rules used by the sample")
        @SettingName("Rules")
        val rules: List<SampleRule> = emptyList(),
    )

    @SettingDesc("Set detection rule for scanner")
    @SettingName("Detection rule")
    private data class SampleRule(
        @SettingDesc("Human readable rule name")
        @SettingName("Name")
        val name: String = "user",
    )

    private data class DuplicateKeyConfig(
        @SettingName("First")
        val fooBar: String = "",
        @SettingName("Second")
        val foo_bar: String = "",
    )
}
