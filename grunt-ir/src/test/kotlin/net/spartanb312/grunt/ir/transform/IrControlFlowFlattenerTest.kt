package net.spartanb312.grunt.ir.transform

import net.spartanb312.grunt.ir.core.*
import net.spartanb312.grunt.ir.jvm.JvmIrExporter
import net.spartanb312.grunt.ir.jvm.JvmIrImporter
import org.objectweb.asm.Opcodes
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
}
