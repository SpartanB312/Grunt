package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.resource.ClassDumper
import net.spartanb312.grunteon.obfuscator.process.transformers.PostProcess
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string.StringArrayedEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ReflectionSupport
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import net.spartanb312.grunteon.obfuscator.util.REFLECTION_METADATA
import net.spartanb312.grunteon.obfuscator.util.STRING_BLACKLIST
import net.spartanb312.grunteon.obfuscator.util.dot
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import net.spartanb312.grunteon.testcase.Asserts
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReflectionSupportTest {
    @Test
    fun PlainReflectionLiteralsSurviveStringEncryptionAndRenaming() {
        val originalMains = listOf(
            "net/spartanb312/grunteon/testcase/methodrename/reflection/ReflectionLiteralTarget",
            "net/spartanb312/grunteon/testcase/methodrename/reflection/ReflectionControlFlowTarget"
        )
        val reflectionFilter = ClassFilterConfig(
            excludeStrategy = emptyList(),
            includeStrategy = listOf("net/spartanb312/grunteon/testcase/methodrename/reflection/**")
        )
        val instance = readTestClasses(
            Asserts::class.java,
            ObfConfig(
                globalConfig = GlobalConfig(
                    output = null,
                    dumpMappings = false
                ),
                transformers = listOf(
                    TransformerEntry(config = ReflectionSupport.Config(classFilter = reflectionFilter)),
                    TransformerEntry(
                        config = StringArrayedEncrypt.Config(
                            classFilter = reflectionFilter,
                            carray = false,
                            invokeDynamics = true,
                            exclusion = emptyList()
                        )
                    ),
                    TransformerEntry(
                        config = ClassRenamer.Config(
                            classFilter = reflectionFilter,
                            parent = "net/spartanb312/obf/reflection/"
                        )
                    ),
                    TransformerEntry(config = FieldRenamer.Config(classFilter = reflectionFilter)),
                    TransformerEntry(config = MethodRenamer.Config(classFilter = reflectionFilter)),
                    TransformerEntry(config = PostProcess.Config())
                )
            )
        )
        instance.run()
        instance.workRes.inputClassCollection.forEach { classNode ->
            classNode.methods.forEach { methodNode ->
                assertFalse(methodNode.hasAnnotation(STRING_BLACKLIST))
                assertFalse(methodNode.hasAnnotation(REFLECTION_METADATA))
            }
        }

        val tempDir = Path("build/tmp/grunteon-net.spartanb312.grunteon.obfuscator.ReflectionSupportTest")
        dumpClasses(instance, tempDir)

        originalMains.forEach { originalMain ->
            val mappedMain = instance.nameMapping.getMapping(originalMain)?.dot ?: originalMain.dot
            runTestClass(tempDir, mappedMain)
        }
    }

    private fun dumpClasses(instance: Grunteon, tempDir: Path) {
        val hierarchy = ClassHierarchy.build(instance.workRes.allClassCollection, instance.workRes::getClassNode)
        for (classNode in instance.workRes.inputClassCollection) {
            val bytes = ClassDumper(instance, hierarchy).apply {
                classNode.accept(ClearClassNode(Opcodes.ASM9, this))
            }.toByteArray()
            val outputFile = tempDir.resolve(classNode.name + ".class")
            Files.createDirectories(outputFile.parent)
            outputFile.outputStream().use {
                it.write(bytes)
            }
        }
    }

    private fun runTestClass(tempDir: Path, className: String) {
        val javaHome = Path.of(System.getProperty("java.home"))
        val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")
        val javaExe = javaHome.resolve("bin").resolve(if (isWindows) "java.exe" else "java").toString()
        val process = ProcessBuilder(javaExe, "-cp", tempDir.absolutePathString(), className)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Test class $className failed with exit code $exitCode:\n$output")
    }
}
