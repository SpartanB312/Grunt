package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.transformers.antidebug.RuntimeMaterial
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import net.spartanb312.grunteon.obfuscator.util.DRAFT_RUNTIME_MATERIAL
import net.spartanb312.grunteon.obfuscator.util.DRAFT_RUNTIME_MATERIAL_FIELD
import net.spartanb312.grunteon.obfuscator.util.DRAFT_RUNTIME_MATERIAL_GUARD
import net.spartanb312.grunteon.obfuscator.util.extensions.findAnnotation
import net.spartanb312.grunteon.testcase.methodinline.Basic
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuntimeMaterialTransformerTest {

    @Test
    fun injectsMaterialAndProducesVerifiableClasses() {
        val instance = readTestClasses(
            Basic::class.java,
            ObfConfig(
                output = null,
                transformerConfigs = listOf(
                    RuntimeMaterial.Config(
                        classChance = 1.0,
                        clinit = true,
                        constructors = true
                    )
                )
            )
        )
        context(instance.workRes, instance) {
            instance.execute()
        }

        val classNode = instance.workRes.inputClassMap[CLASS_NAME]
            ?: error("Missing test class $CLASS_NAME")

        assertNotNull(classNode.findAnnotation(DRAFT_RUNTIME_MATERIAL))
        assertEquals(4, classNode.fields.count { it.findAnnotation(DRAFT_RUNTIME_MATERIAL_FIELD) != null })
        assertTrue(classNode.methods.any { it.findAnnotation(DRAFT_RUNTIME_MATERIAL_GUARD) != null })
        assertTrue(classNode.methods.any { it.name == "<clinit>" })
        assertTrue(classNode.methods.first { it.name == "<init>" }.instructions.size() > 0)

        val tempDir = Files.createTempDirectory("grunteon-runtime-material-test")
        writeClasses(instance.workRes.inputClassCollection, tempDir)
        runTestClass(tempDir)
    }

    private fun FieldNode.findAnnotation(desc: String) =
        visibleAnnotations?.firstOrNull { it.desc == desc }
            ?: invisibleAnnotations?.firstOrNull { it.desc == desc }

    private fun MethodNode.findAnnotation(desc: String) =
        visibleAnnotations?.firstOrNull { it.desc == desc }
            ?: invisibleAnnotations?.firstOrNull { it.desc == desc }

    private fun writeClasses(classNodes: Collection<ClassNode>, outputDir: Path) {
        for (classNode in classNodes) {
            val bytes = ClassWriter(COMPUTE_FRAMES).apply {
                classNode.accept(ClearClassNode(Opcodes.ASM9, this))
            }.toByteArray()
            val outputFile = outputDir.resolve(classNode.name + ".class")
            Files.createDirectories(outputFile.parent)
            outputFile.outputStream().use {
                it.write(bytes)
            }
        }
    }

    private fun runTestClass(classPath: Path) {
        val javaHome = Path.of(System.getProperty("java.home"))
        val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")
        val javaExe = javaHome.resolve("bin").resolve(if (isWindows) "java.exe" else "java").toString()
        val process = ProcessBuilder(javaExe, "-cp", classPath.absolutePathString(), CLASS_NAME.replace('/', '.'))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Test class failed with exit code $exitCode:\n$output")
    }

    private companion object {
        const val CLASS_NAME = "net/spartanb312/grunteon/testcase/methodinline/Basic"
    }
}
