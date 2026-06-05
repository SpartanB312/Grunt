package net.spartanb312.grunt.ir.flow.jvm

import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowGotoJump
import net.spartanb312.grunt.ir.flow.core.FlowIfJump
import net.spartanb312.grunt.ir.flow.core.FlowPort
import net.spartanb312.grunt.ir.flow.core.FlowReturnJump
import net.spartanb312.grunt.ir.flow.core.FlowSwitchJump
import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmFlowImporterTest {
    @Test
    fun importsSimpleBranchMethod() {
        val elseLabel = LabelNode()
        val endLabel = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "absOrNeg", "(I)I", null, null).apply {
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

        val result = JvmFlowImporter().import("example/Test", method)
        val flow = result.method
        val verification = FlowVerifier.verify(flow)

        assertTrue(verification.isValid, verification.issues.joinToString("\n"))
        assertEquals(4, flow.blocks.size)
        assertIs<FlowIfJump>(flow.blocks[0].jump)
        assertIs<FlowGotoJump>(flow.blocks[1].jump)
        assertIs<FlowGotoJump>(flow.blocks[2].jump)
        assertIs<FlowReturnJump>(flow.blocks[3].jump)
        assertEquals(FlowPort.Branch, flow.outgoingEdges(flow.blocks[0])[0].port)
        assertEquals(FlowPort.Fallthrough, flow.outgoingEdges(flow.blocks[0])[1].port)
    }

    @Test
    fun importsLookupSwitchMethod() {
        val one = LabelNode()
        val two = LabelNode()
        val default = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "route", "(I)I", null, null).apply {
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

        val result = JvmFlowImporter().import("example/Test", method)
        val flow = result.method
        val verification = FlowVerifier.verify(flow)
        val switch = assertIs<FlowSwitchJump>(flow.blocks.first().jump)

        assertTrue(verification.isValid, verification.issues.joinToString("\n"))
        assertEquals(setOf(1, 2), switch.keyPorts.keys)
        assertNotNull(flow.edgeFrom(flow.blocks.first(), FlowPort.Case(1)))
        assertNotNull(flow.edgeFrom(flow.blocks.first(), FlowPort.Case(2)))
        assertNotNull(flow.edgeFrom(flow.blocks.first(), FlowPort.Default))
    }

    @Test
    fun importsTryCatchRegions() {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "guard", "()V", null, null).apply {
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

        val result = JvmFlowImporter().import("example/Test", method)
        val flow = result.method
        val verification = FlowVerifier.verify(flow)

        assertTrue(verification.isValid, verification.issues.joinToString("\n"))
        assertEquals(1, flow.exceptionRegions.size)
        assertEquals("java/lang/Throwable", flow.exceptionRegions.single().catchType?.internalName)
        assertTrue(flow.exceptionRegions.single().protectedBlocks.isNotEmpty())
    }

    @Test
    fun preservesConcreteReferenceFramesWhenLocalSlotsAreReused() {
        val useBuilder = LabelNode()
        val useRunnable = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "reuse", "()V", null, null).apply {
            instructions = InsnList().apply {
                add(FieldInsnNode(Opcodes.GETSTATIC, "example/Test", "builder", "Ljava/lang/StringBuilder;"))
                add(VarInsnNode(Opcodes.ASTORE, 0))
                add(JumpInsnNode(Opcodes.GOTO, useBuilder))
                add(useBuilder)
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "length", "()I", false))
                add(InsnNode(Opcodes.POP))
                add(FieldInsnNode(Opcodes.GETSTATIC, "example/Test", "task", "Ljava/lang/Runnable;"))
                add(VarInsnNode(Opcodes.ASTORE, 0))
                add(JumpInsnNode(Opcodes.GOTO, useRunnable))
                add(useRunnable)
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/lang/Runnable", "run", "()V", true))
                add(InsnNode(Opcodes.RETURN))
            }
            maxLocals = 1
            maxStack = 1
        }

        val flow = JvmFlowImporter().import("example/Test", method).method

        assertEquals(3, flow.blocks.size)
        assertEquals(FlowFrameValue.Object("java/lang/StringBuilder"), flow.blocks[1].entryFrame.locals.single())
        assertEquals(FlowFrameValue.Object("java/lang/Runnable"), flow.blocks[2].entryFrame.locals.single())
    }
}
