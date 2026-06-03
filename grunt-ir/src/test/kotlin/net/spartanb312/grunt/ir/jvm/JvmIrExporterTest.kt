package net.spartanb312.grunt.ir.jvm

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
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmIrExporterTest {
    @Test
    fun exportsSimpleBranchMethodNode() {
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

        val exported = JvmIrExporter(imported.metadata).export(imported.function)
        Analyzer(BasicInterpreter()).analyze("example/Test", exported)
        val reimported = JvmIrImporter().import("example/Test", exported)
        val verification = IrVerifier.verify(reimported.function)

        assertTrue(verification.isValid, verification.issues.joinToString("\n"))
        assertEquals("sign", exported.name)
        assertEquals("(I)I", exported.desc)
    }

    @Test
    fun exportsConstantDynamicMethodNode() {
        val bootstrap = Handle(
            Opcodes.H_INVOKESTATIC,
            "example/Test",
            "bootstrap",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;)I",
            false
        )
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "dynamicValue", "()I", null, null).apply {
            instructions = InsnList().apply {
                add(LdcInsnNode(ConstantDynamic("answer", "I", bootstrap)))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 0
            maxStack = 1
        }
        val imported = JvmIrImporter().import("example/Test", method)

        val exported = JvmIrExporter(imported.metadata).export(imported.function)
        val hasCondy = exported.instructions.toArray().any {
            it is LdcInsnNode && it.cst is ConstantDynamic
        }

        Analyzer(BasicInterpreter()).analyze("example/Test", exported)
        assertTrue(hasCondy)
    }
}
