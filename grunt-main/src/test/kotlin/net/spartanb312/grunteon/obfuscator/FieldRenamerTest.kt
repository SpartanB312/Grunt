package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import net.spartanb312.grunteon.testcase.Asserts
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldInsnNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@Suppress("TestFunctionName")
class FieldRenamerTest {
    private val baseName =
        "net/spartanb312/grunteon/testcase/fieldrename/inheritedstatic/AbstractStaticFieldReference\$Base"
    private val childName =
        "net/spartanb312/grunteon/testcase/fieldrename/inheritedstatic/AbstractStaticFieldReference\$Child"
    private val mainName =
        "net.spartanb312.grunteon.testcase.fieldrename.inheritedstatic.AbstractStaticFieldReference"

    @Test
    fun InheritedStaticFieldReferenceFromAbstractClass() {
        val instance = readTestClasses(
            Asserts::class.java,
            ObfConfig(
                transformers = listOf(
                    TransformerEntry(
                        config = FieldRenamer.Config()
                    )
                )
            )
        )

        context(instance.workRes, instance) {
            instance.run()
        }

        val base = assertNotNull(instance.workRes.inputClassMap[baseName])
        val child = assertNotNull(instance.workRes.inputClassMap[childName])
        val renamedInheritedField = assertNotNull(base.fields.singleOrNull {
            it.desc == "L$baseName;"
        })
        assertNotEquals("test", renamedInheritedField.name)

        val inheritedFieldAccess = assertNotNull(
            child.methods.single { it.name == "<clinit>" }
                .instructions
                .asSequence()
                .filterIsInstance<FieldInsnNode>()
                .singleOrNull { it.opcode == Opcodes.GETSTATIC && it.desc == "L$baseName;" }
        )
        assertEquals(renamedInheritedField.name, inheritedFieldAccess.name)

        val tempDir = Path("build/tmp/grunteon-net.spartanb312.grunteon.obfuscator.FieldRenamerTest")
        dumpClasses(instance, tempDir)
        runTestClass(tempDir, mainName)
    }

    private fun dumpClasses(instance: Grunteon, tempDir: Path) {
        for (classNode in instance.workRes.inputClassCollection) {
            val bytes = ClassWriter(COMPUTE_FRAMES).apply {
                classNode.accept(ClearClassNode(Opcodes.ASM9, this))
            }.toByteArray()
            val outputFile = tempDir.resolve(classNode.name + ".class")
            Files.createDirectories(outputFile.parent)
            outputFile.outputStream().use {
                it.write(bytes)
            }
        }
    }

    private fun runTestClass(classPath: Path, className: String) {
        val javaHome = Path.of(System.getProperty("java.home"))
        val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")
        val javaExe = javaHome.resolve("bin").resolve(if (isWindows) "java.exe" else "java").toString()
        val process = ProcessBuilder(javaExe, "-cp", classPath.absolutePathString(), className)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Test class $className failed with exit code $exitCode:\n$output")
    }
}
