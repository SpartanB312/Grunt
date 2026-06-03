package net.spartanb312.grunt.ir.jvm

import net.spartanb312.grunt.ir.core.IrResolveDynamicValueInstruction
import net.spartanb312.grunt.ir.core.IrVerifier
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmIrImporterTest {
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

        val result = JvmIrImporter().import("example/Test", method)
        val verification = IrVerifier.verify(result.function)

        assertTrue(verification.isValid, verification.issues.joinToString("\n"))
        assertTrue(result.function.blocks.size >= 4)
    }

    @Test
    fun importsConstantDynamicAsDynamicValueSite() {
        val bootstrap = Handle(
            Opcodes.H_INVOKESTATIC,
            "example/Test",
            "bootstrap",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;)I",
            false
        )
        val dynamic = ConstantDynamic("answer", "I", bootstrap)
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "dynamicValue", "()I", null, null).apply {
            instructions = InsnList().apply {
                add(LdcInsnNode(dynamic))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 0
            maxStack = 1
        }

        val result = JvmIrImporter().import("example/Test", method)
        val verification = IrVerifier.verify(result.function)
        val dynamicValues = result.function.blocks
            .flatMap { it.instructions }
            .filterIsInstance<IrResolveDynamicValueInstruction>()

        assertTrue(verification.isValid, verification.issues.joinToString("\n"))
        assertEquals(1, result.metadata.dynamicValues.size)
        assertEquals(1, dynamicValues.size)
    }
}
