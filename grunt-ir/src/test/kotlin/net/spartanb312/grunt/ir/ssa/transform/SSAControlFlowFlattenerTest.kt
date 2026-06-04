package net.spartanb312.grunt.ir.ssa.transform

import net.spartanb312.grunt.ir.ssa.core.*
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAExporter
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SSAControlFlowFlattenerTest {
    @Test
    fun repairsCrossBlockSsaUsesBeforeFlattening() {
        val ids = SSAIdAllocator()
        val parameter = SSAParameter(ids.valueId(), 0, SSAI32Type, "p0")
        val symbol = SSAFunctionSymbol(ids.symbolId(), "crossBlock", listOf(SSAI32Type), SSAI32Type)
        val entry = SSABlock(ids.blockId())
        val child = SSABlock(ids.blockId())
        val addResult = SSAInstructionResult(ids.valueId(), SSAI32Type, "x")
        val mulResult = SSAInstructionResult(ids.valueId(), SSAI32Type, "y")
        entry.instructions += SSABinaryInstruction(addResult, SSABinaryOp.Add, parameter, SSAIntLiteral(1))
        entry.terminator = SSAJumpTerminator(SSASuccessor(child))
        child.instructions += SSABinaryInstruction(mulResult, SSABinaryOp.Mul, addResult, SSAIntLiteral(2))
        child.terminator = SSAReturnTerminator(mulResult)
        val function = SSAFunction(symbol, mutableListOf(parameter), mutableListOf(entry, child), entry)

        val result = SSAControlFlowFlattener().flatten(function)

        assertTrue(result.changed, result.reason)
        SSAVerifier.verify(function).requireValid()
        val dispatcher = function.blocks[1]
        assertIs<SSASwitchTerminator>(dispatcher.terminator)
        assertEquals(1, child.args.size)
        assertEquals(addResult.type, child.args.single().type)
        val childInstruction = assertIs<SSABinaryInstruction>(child.instructions.single())
        assertEquals(child.args.single(), childInstruction.lhs)
    }

    @Test
    fun dispatchEdgesDoNotReadDispatcherOnlyCarriersFromOriginalBlocks() {
        val ids = SSAIdAllocator()
        val symbol = SSAFunctionSymbol(ids.symbolId(), "fanout", emptyList(), SSAI32Type)
        val entry = SSABlock(ids.blockId())
        val left = SSABlock(ids.blockId())
        val right = SSABlock(ids.blockId())
        val leftArg = SSABlockArg(ids.valueId(), 0, SSAI32Type, SSABlockArgOrigin.Join, "left")
        val rightArg = SSABlockArg(ids.valueId(), 0, SSAI32Type, SSABlockArgOrigin.Join, "right")
        left.args += leftArg
        right.args += rightArg
        entry.terminator = SSABranchTerminator(
            SSABoolLiteral(true),
            SSASuccessor(left, listOf(SSAIntLiteral(1))),
            SSASuccessor(right, listOf(SSAIntLiteral(2)))
        )
        left.terminator = SSAReturnTerminator(leftArg)
        right.terminator = SSAReturnTerminator(rightArg)
        val function = SSAFunction(symbol, mutableListOf(), mutableListOf(entry, left, right), entry)

        val result = SSAControlFlowFlattener().flatten(function)

        assertTrue(result.changed, result.reason)
        SSAVerifier.verify(function).requireValid()
        val dispatcher = function.blocks[1]
        val dispatcherArgs = dispatcher.args.toSet()
        val backEdges = function.blocks
            .filterNot { it == function.entry || it == dispatcher }
            .flatMap { block -> block.terminator.successors.filter { it.block == dispatcher } }

        assertTrue(backEdges.isNotEmpty())
        assertTrue(backEdges.all { successor -> successor.args.none { it in dispatcherArgs } })
    }

    @Test
    fun flattensImportedJvmBranchAndExportsValidBytecode() {
        val elseLabel = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "sign", "(I)I", null, null).apply {
            instructions = InsnList().apply {
                add(VarInsnNode(Opcodes.ILOAD, 0))
                add(JumpInsnNode(Opcodes.IFLT, elseLabel))
                add(InsnNode(Opcodes.ICONST_1))
                add(InsnNode(Opcodes.IRETURN))
                add(elseLabel)
                add(InsnNode(Opcodes.ICONST_M1))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 1
            maxStack = 1
        }
        val imported = JvmSSAImporter().import("example/Test", method)

        val result = SSAControlFlowFlattener().flatten(imported.function)

        assertTrue(result.changed, result.reason)
        SSAVerifier.verify(imported.function).requireValid()
        assertTrue(imported.function.blocks.any { it.terminator is SSASwitchTerminator })

        val exported = JvmSSAExporter(imported.metadata).export(imported.function)
        Analyzer(BasicInterpreter()).analyze("example/Test", exported)
    }

    @Test
    fun flattenedJvmExportDoesNotReuseOriginalLocalSlotsForDispatcherCarriers() {
        val elseLabel = LabelNode()
        val method = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "pick",
            "(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;",
            null,
            null
        ).apply {
            instructions = InsnList().apply {
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(VarInsnNode(Opcodes.ASTORE, 3))
                add(VarInsnNode(Opcodes.ALOAD, 1))
                add(VarInsnNode(Opcodes.ASTORE, 4))
                add(VarInsnNode(Opcodes.ILOAD, 2))
                add(JumpInsnNode(Opcodes.IFEQ, elseLabel))
                add(VarInsnNode(Opcodes.ALOAD, 4))
                add(InsnNode(Opcodes.ARETURN))
                add(elseLabel)
                add(VarInsnNode(Opcodes.ALOAD, 3))
                add(InsnNode(Opcodes.ARETURN))
            }
            maxLocals = 5
            maxStack = 1
        }
        val imported = JvmSSAImporter().import("example/Test", method)

        val result = SSAControlFlowFlattener().flatten(imported.function)

        assertTrue(result.changed, result.reason)
        val exported = JvmSSAExporter(imported.metadata).export(imported.function)
        val classNode = ClassNode().apply {
            version = Opcodes.V17
            access = Opcodes.ACC_PUBLIC
            name = "example/Test"
            superName = "java/lang/Object"
            methods.add(exported)
        }
        val writer = object : ClassWriter(COMPUTE_FRAMES or COMPUTE_MAXS) {
            override fun getCommonSuperClass(type1: String, type2: String): String = "java/lang/Object"
        }
        classNode.accept(writer)
        val loaded = ByteArrayClassLoader().define("example.Test", writer.toByteArray())
        val reflected = loaded.getDeclaredMethod("pick", Any::class.java, Any::class.java, Int::class.java)
        val first = Any()
        val second = Any()

        assertEquals(first, reflected.invoke(null, first, second, 0))
        assertEquals(second, reflected.invoke(null, first, second, 1))
    }

    private class ByteArrayClassLoader : ClassLoader(SSAControlFlowFlattenerTest::class.java.classLoader) {
        fun define(name: String, bytes: ByteArray): Class<*> = defineClass(name, bytes, 0, bytes.size)
    }
}
