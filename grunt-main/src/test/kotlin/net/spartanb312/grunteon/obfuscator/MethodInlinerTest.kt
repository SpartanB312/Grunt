package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.MethodInliner
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import net.spartanb312.grunteon.testcase.methodinline.Basic
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MethodInlinerTest {

    @Test
    fun inlinesSmallSameClassMethods() {
        val instance = readTestClasses(
            Basic::class.java,
            ObfConfig(
                output = null,
                transformerConfigs = listOf(MethodInliner.Config(maxInstructions = 8))
            )
        )
        context(instance.workRes, instance) {
            instance.execute()
        }

        val classNode = instance.workRes.inputClassMap[CLASS_NAME]
            ?: error("Missing test class $CLASS_NAME")
        val runMethod = classNode.findMethod("run", "(I)I")
        assertFalse(runMethod.hasInvoke("add", "(I)I"))
        assertFalse(runMethod.hasInvoke("multiply", "(II)I"))
        assertFalse(runMethod.hasInvoke("finalAdd", "(I)I"))

        val callNoThisMethod = classNode.findMethod("callNoThis", "(L$CLASS_NAME;)I")
        assertFalse(callNoThisMethod.hasInvoke("noThis", "()I"))

        val tempDir = Files.createTempDirectory("grunteon-method-inliner-test")
        writeClasses(instance.workRes.inputClassCollection, tempDir)
        runTestClass(tempDir)
    }

    private fun ClassNode.findMethod(name: String, desc: String): MethodNode {
        return methods.firstOrNull { it.name == name && it.desc == desc }
            ?: error("Missing method $name$desc")
    }

    private fun MethodNode.hasInvoke(name: String, desc: String): Boolean {
        return instructions.toArray().any {
            it is MethodInsnNode && it.owner == CLASS_NAME && it.name == name && it.desc == desc
        }
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
