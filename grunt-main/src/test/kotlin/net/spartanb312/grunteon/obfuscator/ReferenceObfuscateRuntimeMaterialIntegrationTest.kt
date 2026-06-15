package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
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
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
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

    @Test
    fun reobfuscatesGeneratedBootstrapConstants() {
        val plainInstance = runReferenceObfuscate(reobfBSM = false)
        val reobfInstance = runReferenceObfuscate(reobfBSM = true)
        val plainHelpers = plainInstance.referenceClass().referenceHelpers()
        val reobfHelpers = reobfInstance.referenceClass().referenceHelpers()

        assertTrue(plainHelpers.isNotEmpty())
        assertTrue(reobfHelpers.isNotEmpty())
        assertTrue(plainHelpers.sumOf { it.stringLdcCount() } > 0)
        assertEquals(0, reobfHelpers.sumOf { it.stringLdcCount() })
        assertTrue(reobfHelpers.sumOf { it.numberPushCount() } > plainHelpers.sumOf { it.numberPushCount() })
        assertTrue(reobfHelpers.sumOf { it.xorCount() } > plainHelpers.sumOf { it.xorCount() })
        assertEquals(0, plainHelpers.sumOf { it.exceptionBridgeCount() })
        assertTrue(reobfHelpers.sumOf { it.exceptionBridgeCount() } > 0)
        assertTrue(reobfHelpers.all { it.exceptionBridgeHandlersAreSeparated() })

        val tempDir = Files.createTempDirectory("grunteon-reference-reobf-test")
        writeClasses(reobfInstance.workRes.inputClassCollection, tempDir)
        runTestClass(tempDir)
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

    private fun runReferenceObfuscate(reobfBSM: Boolean): Grunteon {
        val instance = readTestClasses(
            Basic::class.java,
            ObfConfig(
                output = null,
                transformerConfigs = listOf(
                    ReferenceObfuscate.Config(
                        classFilter = basicOnlyFilter(),
                        chance = 1.0,
                        reobfBSM = reobfBSM
                    )
                )
            )
        )
        context(instance.workRes, instance) {
            instance.execute()
        }
        return instance
    }

    private fun Grunteon.referenceClass(): ClassNode {
        return workRes.inputClassMap[CLASS_NAME] ?: error("Missing test class $CLASS_NAME")
    }

    private fun ClassNode.referenceHelpers(): List<MethodNode> {
        return methods.filter { method ->
            method.access and Opcodes.ACC_SYNTHETIC != 0 &&
                method.access and Opcodes.ACC_BRIDGE != 0
        }
    }

    private fun MethodNode.stringLdcCount(): Int {
        return instructions.iterator().asSequence().count { instruction ->
            instruction is LdcInsnNode && instruction.cst is String
        }
    }

    private fun MethodNode.numberPushCount(): Int {
        return instructions.iterator().asSequence().count { it.isNumberPush() }
    }

    private fun MethodNode.xorCount(): Int {
        return instructions.iterator().asSequence().count { instruction ->
            instruction.opcode == Opcodes.IXOR || instruction.opcode == Opcodes.LXOR
        }
    }

    private fun MethodNode.exceptionBridgeCount(): Int {
        return tryCatchBlocks.count { it.type == "java/lang/RuntimeException" }
    }

    private fun MethodNode.exceptionBridgeHandlersAreSeparated(): Boolean {
        val instructionArray = instructions.toArray()
        return tryCatchBlocks
            .filter { it.type == "java/lang/RuntimeException" }
            .all { region ->
                val start = instructionArray.indexOf(region.start)
                val end = instructionArray.indexOf(region.end)
                val handler = instructionArray.indexOf(region.handler)
                if (start < 0 || end < 0 || handler < 0) return@all false
                when {
                    handler > end -> instructionArray.executableBetween(end, handler) > 0
                    handler < start -> {
                        val handlerEnd = instructionArray.firstTerminalFrom(handler) ?: return@all false
                        handlerEnd < start && instructionArray.executableBetween(handlerEnd, start) > 0
                    }

                    else -> false
                }
            }
    }

    private fun Array<AbstractInsnNode>.executableBetween(fromExclusive: Int, toExclusive: Int): Int {
        return ((fromExclusive + 1) until toExclusive).count { this[it].opcode >= 0 }
    }

    private fun Array<AbstractInsnNode>.firstTerminalFrom(index: Int): Int? {
        for (cursor in index + 1 until size) {
            if (this[cursor].opcode.isTerminal()) return cursor
        }
        return null
    }

    private fun Int.isTerminal(): Boolean {
        return this == Opcodes.GOTO ||
            this == Opcodes.ATHROW ||
            this in Opcodes.IRETURN..Opcodes.RETURN
    }

    private fun AbstractInsnNode.isNumberPush(): Boolean {
        return when {
            opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> true
            opcode in Opcodes.LCONST_0..Opcodes.LCONST_1 -> true
            opcode in Opcodes.FCONST_0..Opcodes.FCONST_2 -> true
            opcode in Opcodes.DCONST_0..Opcodes.DCONST_1 -> true
            opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH -> this is IntInsnNode
            this is LdcInsnNode -> cst is Int || cst is Long || cst is Float || cst is Double
            else -> false
        }
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
