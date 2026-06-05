package net.spartanb312.grunt.ir.flow.transform

import net.spartanb312.grunt.ir.flow.core.FlowBlockKind
import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowExporter
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowImporter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowControlFlowFlattenerTest {
    @Test
    fun flattensSimpleLinearRegion() {
        val imported = JvmFlowImporter().import(Owner, linearMethod())
        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                minFlattenedBlocks = 2
            )
        ).flatten(imported.method)

        assertTrue(result.changed, result.reason ?: "not changed")
        assertEquals(1, result.flattenedRegions)
        assertEquals(3, result.flattenedBlocks)
        assertTrue(imported.method.blocks.any { it.kind == FlowBlockKind.Dispatcher })
        assertTrue(imported.method.blocks.any { it.kind == FlowBlockKind.StateSet })
        FlowVerifier.verify(imported.method).requireValid()

        val exported = JvmFlowExporter(imported.metadata).export(imported.method)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
        assertTrue(exported.instructions.toArray().any { it.opcode == Opcodes.LOOKUPSWITCH || it.opcode == Opcodes.TABLESWITCH })
    }

    @Test
    fun flattensThroughNonEmptyStackFrames() {
        val imported = JvmFlowImporter().import(Owner, nonEmptyStackMethod())
        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                minFlattenedBlocks = 2
            )
        ).flatten(imported.method)

        assertTrue(result.changed, result.reason ?: "not changed")
        assertEquals(1, result.flattenedRegions)
        assertEquals(2, result.flattenedBlocks)
        assertTrue(result.dispatcherIslands >= 2)
        FlowVerifier.verify(imported.method).requireValid()

        val exported = JvmFlowExporter(imported.metadata).export(imported.method)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
    }

    private fun linearMethod(): MethodNode {
        val first = LabelNode()
        val second = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null).apply {
            instructions = InsnList().apply {
                add(JumpInsnNode(Opcodes.GOTO, first))
                add(first)
                add(InsnNode(Opcodes.NOP))
                add(JumpInsnNode(Opcodes.GOTO, second))
                add(second)
                add(InsnNode(Opcodes.NOP))
                add(InsnNode(Opcodes.RETURN))
            }
            maxLocals = 0
            maxStack = 0
        }
    }

    private fun nonEmptyStackMethod(): MethodNode {
        val target = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "stacky", "()V", null, null).apply {
            instructions = InsnList().apply {
                add(InsnNode(Opcodes.ICONST_1))
                add(JumpInsnNode(Opcodes.GOTO, target))
                add(target)
                add(InsnNode(Opcodes.POP))
                add(InsnNode(Opcodes.RETURN))
            }
            maxLocals = 0
            maxStack = 1
        }
    }

    private companion object {
        const val Owner = "example/Test"
    }
}
