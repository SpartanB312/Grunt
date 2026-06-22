package net.spartanb312.grunt.glsl

import net.spartanb312.grunt.glsl.transformers.GlslObfuscator
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GlslGrunteonIntegrationTest {
    @Test
    fun pluginTransformerRewritesShaderResourcesInOutputJar() {
        ensurePluginLoaded()
        val tempDir = createTempDirectory("grunt-glsl-test")
        val input = tempDir.resolve("input.jar")
        val output = tempDir.resolve("output.jar")
        writeZip(
            input.pathString,
            mapOf(
                "shaders/main.vert" to """
                    float helper(float x) {
                        return x;
                    }

                    void main() {
                        float value = helper(1.0);
                        gl_Position = vec4(value);
                    }
                """.trimIndent(),
                "readme.txt" to "untouched"
            )
        )

        val config = ObfConfig(
            globalConfig = GlobalConfig(
                input = input.pathString,
                output = output.pathString,
                dumpMappings = false,
                missingCheck = false
            ),
            transformers = listOf(
                TransformerEntry(
                    name = "GlslObfuscator",
                    enabled = true,
                    config = GlslObfuscator.Config(inlineMaxExpansionRatio = 10.0)
                )
            )
        )

        Grunteon.create(config).run()

        ZipFile(output.toFile()).use { zip ->
            val shader = zip.getInputStream(zip.getEntry("shaders/main.vert"))
                .use { it.readBytes().decodeToString() }
            val readme = zip.getInputStream(zip.getEntry("readme.txt"))
                .use { it.readBytes().decodeToString() }
            assertFalse("helper" in shader)
            assertContains(shader, "void main()")
            assertContains(shader, "gl_Position")
            assertEquals("untouched", readme)
        }
    }

    private fun ensurePluginLoaded() {
        if (pluginLoaded) return
        PluginManager.loadPlugin(GlslPlugin)
        PluginManager.freeze()
        pluginLoaded = true
    }

    private fun writeZip(path: String, entries: Map<String, String>) {
        ZipOutputStream(Files.newOutputStream(java.nio.file.Path.of(path))).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private companion object {
        var pluginLoaded = false
    }
}
