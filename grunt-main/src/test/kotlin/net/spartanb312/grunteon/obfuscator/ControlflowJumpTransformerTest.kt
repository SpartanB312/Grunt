package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowFlattening
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ControlflowJumpTransformerTest {
    @Test
    fun generatesProcessorBackedOpaquePredicates() {
        val classes = runControlflowJumpClasses(
            ControlflowJump.Config(
                chance = 1.0,
                mangledIfChance = 0.0,
                maxBranchesPerMethod = 1,
                verifyBytecode = true
            )
        )
        val method = classes.getValue("example/MangledJumpCase").methods.single { it.name == "choose" }
        val processor = classes.values.single { it.name.contains("\$PredicateProcessor\$") }

        assertTrue(processor.methods.any { it.desc == "(III)I" })
        assertTrue(
            method.instructions.toArray().any {
                it is MethodInsnNode &&
                    it.owner == processor.name &&
                    it.desc == "(III)I"
            }
        )
    }

    @Test
    fun generatesRandomBoundOpaquePredicates() {
        val classes = runControlflowJumpClasses(
            ControlflowJump.Config(
                chance = 1.0,
                mangledIfChance = 0.0,
                maxBranchesPerMethod = 1,
                predicateRandomBoundChance = 1.0,
                verifyBytecode = true
            )
        )
        val method = classes.getValue("example/MangledJumpCase").methods.single { it.name == "choose" }
        val processor = classes.values.single { it.name.contains("\$PredicateProcessor\$") }
        val methodCalls = method.instructions.toArray().filterIsInstance<MethodInsnNode>()

        assertTrue(processor.methods.count { it.desc == "(III)I" } >= 2)
        assertTrue(
            methodCalls.any {
                it.opcode == Opcodes.INVOKESTATIC &&
                    it.owner == "java/util/concurrent/ThreadLocalRandom" &&
                    it.name == "current" &&
                    it.desc == "()Ljava/util/concurrent/ThreadLocalRandom;"
            }
        )
        assertTrue(
            methodCalls.any {
                it.opcode == Opcodes.INVOKEVIRTUAL &&
                    it.owner == "java/util/concurrent/ThreadLocalRandom" &&
                    it.name == "nextInt" &&
                    it.desc == "(I)I"
            }
        )
    }

    @Test
    fun manglesIfJumpWithFakeLoopAndNaturalCallPop() {
        val classNode = runControlflowJump(
            ControlflowJump.Config(
                chance = 0.0,
                mangledIfChance = 1.0,
                maxMangledIfsPerMethod = 1,
                mangledFakeLoopChance = 1.0,
                maxPreludeCalls = 1,
                verifyBytecode = true
            )
        )
        val method = classNode.methods.single { it.name == "choose" }

        assertTrue(method.instructions.toArray().count { isIfOpcode(it.opcode) } >= 3)
        assertTrue(method.hasBackwardGoto())
        assertTrue(method.instructions.toArray().any { it is MethodInsnNode && it.opcode == Opcodes.INVOKESTATIC })
    }

    @Test
    fun manglesIfJumpWithTerminalJunkReturns() {
        val classNode = runControlflowJump(
            ControlflowJump.Config(
                chance = 0.0,
                mangledIfChance = 1.0,
                maxMangledIfsPerMethod = 1,
                mangledFakeLoopChance = 0.0,
                sharedJunkExitChance = 0.0,
                junkTerminalThrowChance = 0.0,
                maxPreludeCalls = 0,
                verifyBytecode = true
            )
        )
        val method = classNode.methods.single { it.name == "choose" }

        assertTrue(method.instructions.toArray().count { it.opcode == Opcodes.IRETURN } >= 4)
        assertTrue(method.instructions.toArray().count { isIfOpcode(it.opcode) } >= 3)
    }

    @Test
    fun mangledIfCanShareTerminalJunkExit() {
        val classNode = runControlflowJump(
            ControlflowJump.Config(
                chance = 0.0,
                mangledIfChance = 1.0,
                maxMangledIfsPerMethod = 1,
                mangledFakeLoopChance = 0.0,
                sharedJunkExitChance = 1.0,
                junkTerminalThrowChance = 0.0,
                maxPreludeCalls = 0,
                verifyBytecode = true
            )
        )
        val method = classNode.methods.single { it.name == "choose" }
        val nodes = method.instructions.toArray().toList()

        assertTrue(nodes.hasSharedGotoTerminator())
    }

    @Test
    fun terminalJunkExitCanThrow() {
        val classNode = runControlflowJump(
            ControlflowJump.Config(
                chance = 0.0,
                mangledIfChance = 1.0,
                maxMangledIfsPerMethod = 1,
                mangledFakeLoopChance = 0.0,
                sharedJunkExitChance = 0.0,
                junkTerminalThrowChance = 1.0,
                maxPreludeCalls = 0,
                verifyBytecode = true
            )
        )
        val method = classNode.methods.single { it.name == "choose" }

        assertTrue(method.instructions.toArray().any { it.opcode == Opcodes.ATHROW })
    }

    @Test
    fun placesDispatcherLandingJunkBelowCffSwitch() {
        val classes = runTransformerClasses(
            listOf(
                ControlflowFlattening.Config(
                    fakeCasesPerDispatcher = 0,
                    junkCases = false,
                    verifyBytecode = true
                ),
                ControlflowJump.Config(
                    chance = 0.0,
                    mangledIfChance = 0.0,
                    maxBranchesPerMethod = 1,
                    dispatcherLandingJunkChance = 1.0,
                    maxDispatcherLandingJunkBlocksPerMethod = 1,
                    maxPreludeCalls = 0,
                    verifyBytecode = true
                )
            )
        )
        val method = classes.getValue("example/MangledJumpCase").methods.single { it.name == "choose" }
        val nodes = method.instructions.toArray().toList()
        val switchIndex = nodes.indexOfFirst { it is LookupSwitchInsnNode || it is TableSwitchInsnNode }
        assertTrue(switchIndex >= 0)

        val switchInsn = nodes[switchIndex]
        val landingLabel = nodes.drop(switchIndex + 1).first { it is LabelNode } as LabelNode
        assertTrue(landingLabel !in switchTargetLabels(switchInsn))
        assertTrue(nodes.hasOpaqueGateJumpTo(landingLabel))
    }

    private fun runControlflowJump(config: ControlflowJump.Config): ClassNode {
        return runControlflowJumpClasses(config).getValue("example/MangledJumpCase")
    }

    private fun runControlflowJumpClasses(config: ControlflowJump.Config): Map<String, ClassNode> {
        return runTransformerClasses(listOf(config))
    }

    private fun runTransformerClasses(transformerConfigs: List<TransformerConfig>): Map<String, ClassNode> {
        val input = createTempFile("grunteon-controlflow-jump-input", ".jar")
        val output = createTempFile("grunteon-controlflow-jump-output", ".jar")
        try {
            JarOutputStream(input.outputStream()).use { jar ->
                jar.putNextEntry(JarEntry("example/MangledJumpCase.class"))
                jar.write(branchClass())
                jar.closeEntry()
            }

            val instance = Grunteon.create(
                ObfConfig(
                    input = input.pathString,
                    output = output.pathString,
                    dumpMappings = false,
                    transformerConfigs = transformerConfigs
                )
            )

            context(instance.workRes, instance) {
                instance.execute()
            }

            ZipFile(output.toFile()).use { zip ->
                return zip.entries()
                    .asSequence()
                    .filter { it.name.endsWith(".class") }
                    .associate { entry ->
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val classNode = ClassNode()
                        ClassReader(bytes).accept(classNode, 0)
                        classNode.name to classNode
                    }
                    .also {
                        assertNotNull(it["example/MangledJumpCase"])
                    }
            }
        } finally {
            input.deleteIfExists()
            output.deleteIfExists()
        }
    }

    private fun List<AbstractInsnNode>.hasOpaqueGateJumpTo(label: LabelNode): Boolean {
        return withIndex().any { (index, insn) ->
            insn is JumpInsnNode &&
                insn.opcode == Opcodes.GOTO &&
                insn.label == label &&
                previousExecutableOpcode(index)?.let(::isIfOpcode) == true
        }
    }

    private fun List<AbstractInsnNode>.previousExecutableOpcode(index: Int): Int? {
        for (candidate in index - 1 downTo 0) {
            val opcode = this[candidate].opcode
            if (opcode >= 0) return opcode
        }
        return null
    }

    private fun List<AbstractInsnNode>.hasSharedGotoTerminator(): Boolean {
        val labelIndices = withIndex()
            .mapNotNull { (index, insn) -> (insn as? LabelNode)?.let { it to index } }
            .toMap()
        val sharedGotoTargets = filterIsInstance<JumpInsnNode>()
            .filter { it.opcode == Opcodes.GOTO }
            .groupingBy { it.label }
            .eachCount()
            .filterValues { it >= 2 }
            .keys
        return sharedGotoTargets.any { label ->
            val start = labelIndices[label] ?: return@any false
            for (index in start + 1 until size) {
                val insn = this[index]
                if (insn is LabelNode) return@any false
                if (insn.opcode in Opcodes.IRETURN..Opcodes.RETURN) return@any true
            }
            false
        }
    }

    private fun switchTargetLabels(insn: AbstractInsnNode): Set<LabelNode> {
        return when (insn) {
            is LookupSwitchInsnNode -> (insn.labels + insn.dflt).toSet()
            is TableSwitchInsnNode -> (insn.labels + insn.dflt).toSet()
            else -> emptySet()
        }
    }

    private fun MethodNode.hasBackwardGoto(): Boolean {
        val nodes = this.instructions.toArray().toList()
        val labelIndices = nodes
            .withIndex()
            .mapNotNull { (index, insn) -> (insn as? LabelNode)?.let { it to index } }
            .toMap()
        return nodes.withIndex().any { (index, insn) ->
            insn is JumpInsnNode &&
                insn.opcode == Opcodes.GOTO &&
                (labelIndices[insn.label] ?: Int.MAX_VALUE) < index
        }
    }

    private fun isIfOpcode(opcode: Int): Boolean {
        return opcode in Opcodes.IFEQ..Opcodes.IF_ACMPNE ||
            opcode == Opcodes.IFNULL ||
            opcode == Opcodes.IFNONNULL
    }

    private fun branchClass(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/MangledJumpCase", null, "java/lang/Object", null)

        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "helper", "(I)I", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ILOAD, 0)
            visitInsn(Opcodes.ICONST_1)
            visitInsn(Opcodes.IADD)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "choose", "(I)I", null, null).apply {
            val falseLabel = Label()
            visitCode()
            visitVarInsn(Opcodes.ILOAD, 0)
            visitJumpInsn(Opcodes.IFEQ, falseLabel)
            visitVarInsn(Opcodes.ILOAD, 0)
            visitInsn(Opcodes.ICONST_1)
            visitInsn(Opcodes.IADD)
            visitInsn(Opcodes.IRETURN)
            visitLabel(falseLabel)
            visitVarInsn(Opcodes.ILOAD, 0)
            visitInsn(Opcodes.INEG)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }
}
