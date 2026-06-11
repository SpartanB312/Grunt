package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.transformers.antidebug.RuntimeMaterial
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string.StringArrayedEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ReferenceObfuscate
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import net.spartanb312.grunteon.obfuscator.util.DRAFT_RUNTIME_MATERIAL
import net.spartanb312.grunteon.obfuscator.util.extensions.findAnnotation
import net.spartanb312.grunteon.testcase.methodinline.Basic
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReferenceObfuscateRuntimeMaterialIntegrationTest {

    @Test
    fun usesRuntimeMaterialForReferencePayloads() {
        runMaterialReferenceTest(
            RuntimeMaterial.Config(
                classFilter = basicOnlyFilter(),
                classChance = 1.0,
                clinit = true,
                constructors = true
            ),
            ReferenceObfuscate.Config(
                classFilter = basicOnlyFilter(),
                chance = 1.0
            )
        )
    }

    @Test
    fun doesNotRekeyRuntimeMaterialInitializationHelpers() {
        runMaterialReferenceTest(
            RuntimeMaterial.Config(
                classFilter = basicOnlyFilter(),
                classChance = 1.0,
                clinit = true,
                constructors = true
            ),
            NumberBasicEncrypt.Config(
                classFilter = basicOnlyFilter(),
                integerChance = 1.0,
                longChance = 1.0,
                float = false,
                double = false,
                dynamicStrength = false
            ),
            StringArrayedEncrypt.Config(
                classFilter = basicOnlyFilter(),
                carray = false,
                invokeDynamics = false
            ),
            ReferenceObfuscate.Config(
                classFilter = basicOnlyFilter(),
                chance = 1.0
            )
        )
    }

    private fun runMaterialReferenceTest(vararg configs: TransformerConfig) {
        val instance = readTestClasses(
            Basic::class.java,
            ObfConfig(
                output = null,
                transformerConfigs = configs.toList()
            )
        )
        context(instance.workRes, instance) {
            instance.execute()
        }

        val classNode = instance.workRes.inputClassMap[CLASS_NAME]
            ?: error("Missing test class $CLASS_NAME")

        assertNotNull(classNode.findAnnotation(DRAFT_RUNTIME_MATERIAL))
        assertTrue(classNode.methods.any { method ->
            method.name != "<clinit>" &&
                method.name != "<init>" &&
                method.instructions.iterator().asSequence().any { it is InvokeDynamicInsnNode }
        })
        assertTrue(classNode.methods.any { method ->
            (method.name == "<clinit>" || method.name == "<init>") &&
                method.instructions.iterator().asSequence().any { it is InvokeDynamicInsnNode }
        })

        val tempDir = Files.createTempDirectory("grunteon-reference-runtime-material-test")
        writeClasses(instance.workRes.inputClassCollection, tempDir)
        runTestClass(tempDir)
    }

    private fun basicOnlyFilter(): ClassFilterConfig {
        val basicOnly = ClassFilterConfig(
            excludeStrategy = emptyList(),
            includeStrategy = listOf(CLASS_NAME)
        )
        return basicOnly
    }

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
