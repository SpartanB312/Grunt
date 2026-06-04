package net.spartanb312.grunt.ir.ssa.export

import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAImporter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SSATextExporterTest {
    @Test
    fun exportsFunctionToIrFile() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "answer", "()I", null, null).apply {
            instructions = InsnList().apply {
                add(InsnNode(Opcodes.ICONST_1))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 0
            maxStack = 1
        }
        val function = JvmSSAImporter().import("example/Test", method).function
        val tempDir = Files.createTempDirectory("grunt-ir-exporter")

        val output = SSATextExporter().export(function, tempDir.resolve("answer"))
        val text = Files.readString(output)

        assertEquals("answer.ir", output.fileName.toString())
        assertTrue(text.startsWith("(grunt-ir (version 1))"))
        assertTrue(text.contains("(function "))
        assertTrue(text.contains("(term (op return)"))
    }

    @Test
    fun exportsFunctionToDirectoryWithGeneratedIrName() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "one", "()I", null, null).apply {
            instructions = InsnList().apply {
                add(InsnNode(Opcodes.ICONST_1))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 0
            maxStack = 1
        }
        val function = JvmSSAImporter().import("example/Test", method).function
        val tempDir = Files.createTempDirectory("grunt-ir-exporter")

        val output = SSATextExporter().export(function, tempDir)

        assertTrue(output.fileName.toString().endsWith(".ir"))
        assertTrue(Files.exists(output))
    }
}
