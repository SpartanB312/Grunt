package net.spartanb312.grunteon.obfuscator.process.nativecode.ir

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeJvmExceptionDispatchPlannerTest {

    @Test
    fun createsOneDispatcherForSharedActiveCatchChain() {
        val method = tryCatchMethod("java/lang/Throwable")
        val ir = NativeJvmIrImporter.import("test/Example", method)

        val plan = NativeJvmExceptionDispatchPlanner.plan(ir)

        assertFalse(plan.isEmpty)
        assertEquals(1, plan.dispatches.size)
        assertEquals("L_CATCH_0", plan.dispatches.single().label)

        val protectedLabels = ir.instructions
            .filter { it.opcode == Opcodes.ILOAD || it.opcode == Opcodes.IDIV }
            .map { plan.labelFor(it) }
            .toSet()
        assertEquals(setOf("L_CATCH_0"), protectedLabels)

        val handler = plan.dispatches.single().catches.single()
        assertEquals("java/lang/Throwable", handler.caughtType)
        assertFalse(handler.isCatchAll)
    }

    @Test
    fun keepsCatchAllAsDirectHandlerEntry() {
        val method = tryCatchMethod(null)
        val ir = NativeJvmIrImporter.import("test/Example", method)

        val plan = NativeJvmExceptionDispatchPlanner.plan(ir)

        val handler = plan.dispatches.single().catches.single()
        assertEquals(null, handler.caughtType)
        assertTrue(handler.isCatchAll)
    }

    @Test
    fun preservesFirstCatchForDuplicateCaughtType() {
        val start = LabelNode()
        val end = LabelNode()
        val firstHandler = LabelNode()
        val secondHandler = LabelNode()
        val method = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "duplicateCatch",
            "(II)I",
            null,
            null
        ).apply {
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IDIV))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(end)
            instructions.add(firstHandler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(secondHandler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(InsnNode(Opcodes.IRETURN))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, firstHandler, "java/lang/Throwable"))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, secondHandler, "java/lang/Throwable"))
            maxStack = 2
            maxLocals = 2
        }
        val ir = NativeJvmIrImporter.import("test/Example", method)

        val plan = NativeJvmExceptionDispatchPlanner.plan(ir)

        val catches = plan.dispatches.single().catches
        assertEquals(1, catches.size)
        assertEquals(ir.tryCatchRegions[0].handlerIndex, catches.single().handlerInstructionIndex)
    }

    private fun tryCatchMethod(caughtType: String?): MethodNode {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "tryCatch",
            "(II)I",
            null,
            null
        ).apply {
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IDIV))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(end)
            instructions.add(handler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, caughtType))
            maxStack = 2
            maxLocals = 2
        }
    }
}
