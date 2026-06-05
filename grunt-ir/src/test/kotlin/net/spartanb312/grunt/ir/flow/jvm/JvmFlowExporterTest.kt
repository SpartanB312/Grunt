package net.spartanb312.grunt.ir.flow.jvm

import net.spartanb312.grunt.ir.flow.core.FlowGotoJump
import net.spartanb312.grunt.ir.flow.core.FlowIfJump
import net.spartanb312.grunt.ir.flow.core.FlowPort
import net.spartanb312.grunt.ir.flow.core.FlowReturnJump
import net.spartanb312.grunt.ir.flow.core.FlowSwitchJump
import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmFlowExporterTest {
    @Test
    fun roundtripsSimpleBranchMethod() {
        val first = JvmFlowImporter().import(Owner, simpleBranchMethod())
        FlowVerifier.verify(first.method).requireValid()

        val exported = JvmFlowExporter(first.metadata).export(first.method)
        assertAnalyzes(exported)

        val second = JvmFlowImporter().import(Owner, exported)
        FlowVerifier.verify(second.method).requireValid()

        assertEquals(first.method.name, exported.name)
        assertEquals(first.method.desc, exported.desc)
        assertEquals(first.method.blocks.size, second.method.blocks.size)
        assertEquals(first.method.edges.size, second.method.edges.size)
        assertIs<FlowIfJump>(second.method.blocks[0].jump)
        assertIs<FlowGotoJump>(second.method.blocks[1].jump)
        assertIs<FlowGotoJump>(second.method.blocks[2].jump)
        assertIs<FlowReturnJump>(second.method.blocks[3].jump)
    }

    @Test
    fun roundtripsLookupSwitchMethod() {
        val first = JvmFlowImporter().import(Owner, lookupSwitchMethod())
        FlowVerifier.verify(first.method).requireValid()

        val exported = JvmFlowExporter(first.metadata).export(first.method)
        assertAnalyzes(exported)

        val second = JvmFlowImporter().import(Owner, exported)
        FlowVerifier.verify(second.method).requireValid()
        val switch = assertIs<FlowSwitchJump>(second.method.blocks.first().jump)

        assertEquals(setOf(1, 2), switch.keyPorts.keys)
        assertNotNull(second.method.edgeFrom(second.method.blocks.first(), FlowPort.Case(1)))
        assertNotNull(second.method.edgeFrom(second.method.blocks.first(), FlowPort.Case(2)))
        assertNotNull(second.method.edgeFrom(second.method.blocks.first(), FlowPort.Default))
    }

    @Test
    fun roundtripsTryCatchRegions() {
        val first = JvmFlowImporter().import(Owner, tryCatchMethod())
        FlowVerifier.verify(first.method).requireValid()

        val exported = JvmFlowExporter(first.metadata).export(first.method)
        assertAnalyzes(exported)

        val second = JvmFlowImporter().import(Owner, exported)
        FlowVerifier.verify(second.method).requireValid()

        assertEquals(1, exported.tryCatchBlocks.size)
        assertEquals(1, second.method.exceptionRegions.size)
        assertEquals("java/lang/Throwable", second.method.exceptionRegions.single().catchType?.internalName)
        assertTrue(second.method.exceptionRegions.single().protectedBlocks.isNotEmpty())
    }

    @Test
    fun exportsReorderedIfWithExplicitFallthroughGoto() {
        val first = JvmFlowImporter().import(Owner, simpleBranchMethod())
        val flow = first.method
        flow.layout.order.clear()
        flow.layout.order += listOf(flow.blocks[0], flow.blocks[2], flow.blocks[1], flow.blocks[3])
        FlowVerifier.verify(flow).requireValid()

        val exported = JvmFlowExporter(first.metadata).export(flow)
        assertAnalyzes(exported)

        val second = JvmFlowImporter().import(Owner, exported)
        FlowVerifier.verify(second.method).requireValid()

        assertTrue(second.method.blocks.any { it.jump is FlowIfJump })
        assertTrue(second.method.blocks.any { it.jump is FlowGotoJump })
    }

    private fun assertAnalyzes(method: MethodNode) {
        Analyzer(BasicInterpreter()).analyze(Owner, method)
    }

    private fun simpleBranchMethod(): MethodNode {
        val elseLabel = LabelNode()
        val endLabel = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "absOrNeg", "(I)I", null, null).apply {
            instructions = InsnList().apply {
                add(VarInsnNode(Opcodes.ILOAD, 0))
                add(JumpInsnNode(Opcodes.IFLT, elseLabel))
                add(VarInsnNode(Opcodes.ILOAD, 0))
                add(JumpInsnNode(Opcodes.GOTO, endLabel))
                add(elseLabel)
                add(VarInsnNode(Opcodes.ILOAD, 0))
                add(InsnNode(Opcodes.INEG))
                add(endLabel)
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 1
            maxStack = 1
        }
    }

    private fun lookupSwitchMethod(): MethodNode {
        val one = LabelNode()
        val two = LabelNode()
        val default = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "route", "(I)I", null, null).apply {
            instructions = InsnList().apply {
                add(VarInsnNode(Opcodes.ILOAD, 0))
                add(LookupSwitchInsnNode(default, intArrayOf(1, 2), arrayOf(one, two)))
                add(one)
                add(InsnNode(Opcodes.ICONST_1))
                add(InsnNode(Opcodes.IRETURN))
                add(two)
                add(InsnNode(Opcodes.ICONST_2))
                add(InsnNode(Opcodes.IRETURN))
                add(default)
                add(InsnNode(Opcodes.ICONST_M1))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 1
            maxStack = 1
        }
    }

    private fun tryCatchMethod(): MethodNode {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "guard", "()V", null, null).apply {
            instructions = InsnList().apply {
                add(start)
                add(InsnNode(Opcodes.NOP))
                add(end)
                add(InsnNode(Opcodes.RETURN))
                add(handler)
                add(InsnNode(Opcodes.ATHROW))
            }
            tryCatchBlocks = mutableListOf(TryCatchBlockNode(start, end, handler, "java/lang/Throwable"))
            maxLocals = 0
            maxStack = 1
        }
    }

    private companion object {
        const val Owner = "example/Test"
    }
}
