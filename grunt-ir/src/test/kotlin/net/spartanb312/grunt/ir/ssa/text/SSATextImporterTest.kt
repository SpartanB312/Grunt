package net.spartanb312.grunt.ir.ssa.text

import net.spartanb312.grunt.ir.ssa.core.SSAArrayLoadInstruction
import net.spartanb312.grunt.ir.ssa.core.SSAI8Type
import net.spartanb312.grunt.ir.ssa.core.SSAVerifier
import net.spartanb312.grunt.ir.ssa.export.SSAStrictPrinter
import net.spartanb312.grunt.ir.ssa.export.SSATextExporter
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SSATextImporterTest {
    @Test
    fun readsExporterOutputBackToIrFunction() {
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
        val function = JvmSSAImporter().import("example/Test", method).function
        val output = SSATextExporter().export(function, Files.createTempDirectory("grunt-ir-importer").resolve("sign"))

        val parsed = SSATextImporter().read(output)
        val verification = SSAVerifier.verify(parsed)

        assertTrue(verification.isValid, verification.issues.joinToString("\n"))
        assertEquals(function.symbol.name, parsed.symbol.name)
        assertEquals(function.blocks.size, parsed.blocks.size)
        assertTrue(SSAStrictPrinter().print(parsed).startsWith("(grunt-ir (version 1))"))
    }

    @Test
    fun preservesArrayElementTypeHint() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "loadByte", "([BI)I", null, null).apply {
            instructions = InsnList().apply {
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(VarInsnNode(Opcodes.ILOAD, 1))
                add(InsnNode(Opcodes.BALOAD))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 2
            maxStack = 2
        }
        val function = JvmSSAImporter().import("example/Test", method).function
        val output = SSATextExporter().export(function, Files.createTempDirectory("grunt-ir-importer").resolve("loadByte"))

        val text = Files.readString(output)
        val parsed = SSATextImporter().read(output)
        val arrayLoad = parsed.blocks
            .flatMap { it.instructions }
            .filterIsInstance<SSAArrayLoadInstruction>()
            .single()

        assertTrue(text.contains("(element \"i8\")"))
        assertEquals(SSAI8Type, arrayLoad.elementType)
    }
}
