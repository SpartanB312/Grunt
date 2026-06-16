package net.spartanb312.grunteon.obfuscator.process.nativecode.ir

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeJvmIrImporterTest {

    @Test
    fun importsTryCatchRegionAndActiveHandlerState() {
        val method = tryCatchMethod()

        val ir = NativeJvmIrImporter.import("test/Example", method)
        val report = NativeJvmSupportAnalyzer.analyze(ir)

        assertEquals(1, ir.tryCatchRegions.size)
        assertTrue(ir.usesExceptionDispatch)
        assertTrue(NativeJvmFeature.TryCatch in report.features)
        assertTrue(report.isFullJvmLoweringReady)
        assertNotNull(ir.ssaOverlay)

        val protectedInstructions = ir.instructions
            .filter { it.opcode == Opcodes.IDIV || it.opcode == Opcodes.ILOAD }
        assertTrue(protectedInstructions.isNotEmpty())
        assertTrue(protectedInstructions.all { it.activeTryCatchRegionIds == listOf(0) })

        val handlerReturn = ir.instructions.last { it.opcode == Opcodes.IRETURN }
        assertTrue(handlerReturn.activeTryCatchRegionIds.isEmpty())
    }

    @Test
    fun importsMonitorAsSupportedFullJvmFeature() {
        val method = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "monitor",
            "(Ljava/lang/Object;)V",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITORENTER))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITOREXIT))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 1
        }

        val ir = NativeJvmIrImporter.import("test/Example", method)
        val report = NativeJvmSupportAnalyzer.analyze(ir)

        assertTrue(ir.usesMonitor)
        assertTrue(NativeJvmFeature.Monitor in report.features)
        assertTrue(report.isFullJvmLoweringReady)
    }

    @Test
    fun reportsIndyBlockerWhileMethodTypeLdcIsFullJvmFeature() {
        val method = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "needsPreprocess",
            "()V",
            null,
            null
        ).apply {
            instructions.add(
                InvokeDynamicInsnNode(
                    "dyn",
                    "()V",
                    Handle(Opcodes.H_INVOKESTATIC, "test/Bootstrap", "bsm", "()V", false)
                )
            )
            instructions.add(LdcInsnNode(Type.getMethodType("()V")))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 0
        }

        val ir = NativeJvmIrImporter.import("test/Example", method)
        val report = NativeJvmSupportAnalyzer.analyze(ir)

        assertFalse(report.isFullJvmLoweringReady)
        assertTrue(NativeJvmFeature.MethodTypeConstant in report.features)
        assertEquals(
            setOf(
                NativeJvmSupportIssueKind.InvokeDynamicStillPresent
            ),
            report.issues.mapTo(mutableSetOf()) { it.kind }
        )
    }

    @Test
    fun reportsLegacySubroutineAsUnsupported() {
        val target = LabelNode()
        val method = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "legacy",
            "()V",
            null,
            null
        ).apply {
            instructions.add(JumpInsnNode(Opcodes.JSR, target))
            instructions.add(InsnNode(Opcodes.RETURN))
            instructions.add(target)
            instructions.add(VarInsnNode(Opcodes.RET, 0))
            maxStack = 1
            maxLocals = 1
        }

        val ir = NativeJvmIrImporter.import("test/Example", method)
        val report = NativeJvmSupportAnalyzer.analyze(ir)

        assertFalse(report.isFullJvmLoweringReady)
        assertTrue(NativeJvmSupportIssueKind.UnsupportedLegacySubroutine in report.issues.map { it.kind })
    }

    private fun tryCatchMethod(): MethodNode {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "tryCatch",
            "(I)I",
            null,
            null
        ).apply {
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(InsnNode(Opcodes.IDIV))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(end)
            instructions.add(handler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, "java/lang/Throwable"))
            maxStack = 2
            maxLocals = 1
        }
    }
}
