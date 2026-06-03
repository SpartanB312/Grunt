package net.spartanb312.grunt.ir.text

import net.spartanb312.grunt.ir.core.IrArrayLoadInstruction
import net.spartanb312.grunt.ir.core.IrI8Type
import net.spartanb312.grunt.ir.core.IrVerifier
import net.spartanb312.grunt.ir.export.IrStrictPrinter
import net.spartanb312.grunt.ir.export.IrTextExporter
import net.spartanb312.grunt.ir.jvm.JvmIrImporter
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

class IrTextImporterTest {
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
        val function = JvmIrImporter().import("example/Test", method).function
        val output = IrTextExporter().export(function, Files.createTempDirectory("grunt-ir-importer").resolve("sign"))

        val parsed = IrTextImporter().read(output)
        val verification = IrVerifier.verify(parsed)

        assertTrue(verification.isValid, verification.issues.joinToString("\n"))
        assertEquals(function.symbol.name, parsed.symbol.name)
        assertEquals(function.blocks.size, parsed.blocks.size)
        assertTrue(IrStrictPrinter().print(parsed).startsWith("(grunt-ir (version 1))"))
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
        val function = JvmIrImporter().import("example/Test", method).function
        val output = IrTextExporter().export(function, Files.createTempDirectory("grunt-ir-importer").resolve("loadByte"))

        val text = Files.readString(output)
        val parsed = IrTextImporter().read(output)
        val arrayLoad = parsed.blocks
            .flatMap { it.instructions }
            .filterIsInstance<IrArrayLoadInstruction>()
            .single()

        assertTrue(text.contains("(element \"i8\")"))
        assertEquals(IrI8Type, arrayLoad.elementType)
    }
}
