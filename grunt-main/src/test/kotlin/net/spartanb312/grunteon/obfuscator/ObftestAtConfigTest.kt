package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistry
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObftestAtConfigTest {
    @Test
    fun atNativeAcceptanceConfigsAreReadableAndOrdered() {
        val configDir = repoRoot().resolve(Path.of("obftest", "AT", "configs-grunteon"))
        val configs = configDir.listDirectoryEntries("*.json").sortedBy { it.name }
        assertEquals(7, configs.size)

        configs.forEach { path ->
            val config = ObfConfig.read(path)
            assertEquals("obftest/AT/engine/boar-main-origin.jar", config.globalConfig.input)
            assertEquals("obftest/AT/engine/boar-main.jar", config.globalConfig.output)
            assertEquals(listOf("obftest/AT/libs", "obftest/AT/boar-launch.jar"), config.globalConfig.libs)
            assertTrue(config.nativePipeline.enabled, "${path.name} should enable native pipeline")
            assertTrue(config.nativePipeline.failOnCompileError, "${path.name} should fail on native compile errors")
            assertTransformerOrder(config, path.name)
        }
    }

    private fun repoRoot(): Path {
        var cursor = Path.of("").toAbsolutePath().normalize()
        while (cursor.parent != null) {
            if (cursor.resolve("settings.gradle.kts").toFile().isFile) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root")
    }

    private fun assertTransformerOrder(config: ObfConfig, label: String) {
        val transformers = config.transformers
            .filter { it.enabled }
            .map { entry ->
                val registryEntry = TransformerRegistry.find(entry.config)
                    ?: error("Unregistered transformer config in $label: ${entry.config::class.qualifiedName}")
                registryEntry.createTransformer()
            }

        val errors = buildList {
            transformers.forEachIndexed { index, transformer ->
                transformer.orderRules.forEach { (rule, message) ->
                    if (!rule(transformers, index)) add("${transformer.engName}: $message")
                }
            }
        }
        assertTrue(errors.isEmpty(), "Transformer order rules violated in $label:\n${errors.joinToString("\n")}")
    }
}
