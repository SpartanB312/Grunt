package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import net.spartanb312.grunteon.testcase.Asserts
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("TestFunctionName")
class MethodRenamerTest {
    private lateinit var tempDir: Path

    @BeforeTest
    fun before() {
        if (System.getenv("GRUNTEON_TEST_LOGGING").toBoolean()) {
            Logger = SimpleLogger("Grunteon")
        }
        val instance = readTestClasses(
            Asserts::class.java,
            ObfConfig(
                transformers = listOf(
                    TransformerEntry(
                        config = MethodRenamer.Config()
                    )
                )
            )
        )
        context(instance.workRes, instance) {
            instance.execute()
        }
        tempDir =
            Path("build/tmp/grunteon-net.spartanb312.grunteon.obfuscator.MethodRenamerTest").also { println(it.absolutePathString()) }
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

    private fun runTestClass(className: String) {
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

    @Test
    fun ImplicitOverrideEnd1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.implicitoverride.End1")

    @Test
    fun ImplicitOverrideMid1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.implicitoverride.Mid1")

    @Test
    fun OverlapComplex() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.overlap.Complex")

    @Test
    fun OverlapImplicitOverride2To1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.overlap.ImplicitOverride2To1")

    @Test
    fun OverlapImplicitOverride3To1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.overlap.ImplicitOverride3To1")

    @Test
    fun OverlapImplicitOverride3To1To1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.overlap.ImplicitOverride3To1To1")

    @Test
    fun OverlapInterface2To1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.overlap.Interface2To1")

    @Test
    fun OverlapInterface3To2() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.overlap.Interface3To2")

    @Test
    fun OverlapInterface3To2To1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.overlap.Interface3To2To1")

    @Test
    @Ignore
    fun OverloadShadow1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.OverloadShadow1")

    @Test
    fun FunctionalInterfaceBasic() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.functional.Basic")

    @Test
    fun FunctionalInterfaceCapturing() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.functional.Capturing")

    @Test
    fun FunctionalInterfaceObjectCapturing() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.functional.ObjectCapturing")

    @Test
    fun TypeOverloadTest() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.typeoverload.TypeOverload")

}
