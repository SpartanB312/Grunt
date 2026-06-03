package net.spartanb312.grunt.ir.transform

import net.spartanb312.grunt.ir.core.*
import net.spartanb312.grunt.ir.jvm.JvmIrExporter
import net.spartanb312.grunt.ir.jvm.JvmIrImporter
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

class IrControlFlowFlattenerTest {
    @Test
    fun repairsCrossBlockSsaUsesBeforeFlattening() {
        val ids = IrIdAllocator()
        val parameter = IrParameter(ids.valueId(), 0, IrI32Type, "p0")
        val symbol = IrFunctionSymbol(ids.symbolId(), "crossBlock", listOf(IrI32Type), IrI32Type)
        val entry = IrBlock(ids.blockId())
        val child = IrBlock(ids.blockId())
        val addResult = IrInstructionResult(ids.valueId(), IrI32Type, "x")
        val mulResult = IrInstructionResult(ids.valueId(), IrI32Type, "y")
        entry.instructions += IrBinaryInstruction(addResult, IrBinaryOp.Add, parameter, IrIntLiteral(1))
        entry.terminator = IrJumpTerminator(IrSuccessor(child))
        child.instructions += IrBinaryInstruction(mulResult, IrBinaryOp.Mul, addResult, IrIntLiteral(2))
        child.terminator = IrReturnTerminator(mulResult)
        val function = IrFunction(symbol, mutableListOf(parameter), mutableListOf(entry, child), entry)

        val result = IrControlFlowFlattener().flatten(function)

        assertTrue(result.changed, result.reason)
        IrVerifier.verify(function).requireValid()
        val dispatcher = function.blocks[1]
        assertIs<IrSwitchTerminator>(dispatcher.terminator)
        assertEquals(1, child.args.size)
        assertEquals(addResult.type, child.args.single().type)
        val childInstruction = assertIs<IrBinaryInstruction>(child.instructions.single())
        assertEquals(child.args.single(), childInstruction.lhs)
    }

    @Test
    fun dispatchEdgesDoNotReadDispatcherOnlyCarriersFromOriginalBlocks() {
        val ids = IrIdAllocator()
        val symbol = IrFunctionSymbol(ids.symbolId(), "fanout", emptyList(), IrI32Type)
        val entry = IrBlock(ids.blockId())
        val left = IrBlock(ids.blockId())
        val right = IrBlock(ids.blockId())
        val leftArg = IrBlockArg(ids.valueId(), 0, IrI32Type, IrBlockArgOrigin.Join, "left")
        val rightArg = IrBlockArg(ids.valueId(), 0, IrI32Type, IrBlockArgOrigin.Join, "right")
        left.args += leftArg
        right.args += rightArg
        entry.terminator = IrBranchTerminator(
            IrBoolLiteral(true),
            IrSuccessor(left, listOf(IrIntLiteral(1))),
            IrSuccessor(right, listOf(IrIntLiteral(2)))
        )
        left.terminator = IrReturnTerminator(leftArg)
        right.terminator = IrReturnTerminator(rightArg)
        val function = IrFunction(symbol, mutableListOf(), mutableListOf(entry, left, right), entry)

        val result = IrControlFlowFlattener().flatten(function)

        assertTrue(result.changed, result.reason)
        IrVerifier.verify(function).requireValid()
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
        val imported = JvmIrImporter().import("example/Test", method)

        val result = IrControlFlowFlattener().flatten(imported.function)

        assertTrue(result.changed, result.reason)
        IrVerifier.verify(imported.function).requireValid()
        assertTrue(imported.function.blocks.any { it.terminator is IrSwitchTerminator })

        val exported = JvmIrExporter(imported.metadata).export(imported.function)
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
        val imported = JvmIrImporter().import("example/Test", method)

        val result = IrControlFlowFlattener().flatten(imported.function)

        assertTrue(result.changed, result.reason)
        val exported = JvmIrExporter(imported.metadata).export(imported.function)
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

    private class ByteArrayClassLoader : ClassLoader(IrControlFlowFlattenerTest::class.java.classLoader) {
        fun define(name: String, bytes: ByteArray): Class<*> = defineClass(name, bytes, 0, bytes.size)
    }
}
