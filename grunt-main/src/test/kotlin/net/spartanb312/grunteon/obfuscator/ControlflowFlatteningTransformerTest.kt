package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowFlattening
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.FlowStateKeyMode
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ControlflowFlatteningTransformerTest {
    @Test
    fun flattensLinearMethodThroughFlowIr() {
        val input = createTempFile("grunteon-flow-cff-input", ".jar")
        val output = createTempFile("grunteon-flow-cff-output", ".jar")
        try {
            JarOutputStream(input.outputStream()).use { jar ->
                jar.putNextEntry(JarEntry("example/FlowFlattenCase.class"))
                jar.write(linearBranchClass())
                jar.closeEntry()
            }

            val instance = Grunteon.create(
                ObfConfig(
                    globalConfig = GlobalConfig(
                        input = input.pathString,
                        output = output.pathString,
                        dumpMappings = false
                    ),
                    transformers = listOf(
                        TransformerEntry(
                            config = ControlflowFlattening.Config(
                                verifyBytecode = true,
                                stateKeyMode = FlowStateKeyMode.Processor,
                                logSkips = false
                            )
                        )
                    )
                )
            )

            context(instance.workRes, instance) {
                instance.run()
            }

            ZipFile(output.toFile()).use { zip ->
                val entry = zip.getEntry("example/FlowFlattenCase.class")
                assertNotNull(entry)
                val classNode = ClassNode()
                ClassReader(zip.getInputStream(entry).use { it.readBytes() }).accept(classNode, 0)
                val method = classNode.methods.single { it.name == "run" }
                assertTrue(method.instructions.toArray().any {
                    it.opcode == Opcodes.LOOKUPSWITCH || it.opcode == Opcodes.TABLESWITCH
                })
                val processorEntry = zip.entries().asSequence().firstOrNull {
                    it.name.startsWith("example/FlowFlattenCase\$KeyProcessor\$") &&
                        it.name.endsWith(".class")
                }
                assertNotNull(processorEntry)
                val processorNode = ClassNode()
                ClassReader(zip.getInputStream(processorEntry).use { it.readBytes() }).accept(processorNode, 0)
                assertTrue(processorNode.version > 0)
            }
        } finally {
            input.deleteIfExists()
            output.deleteIfExists()
        }
    }

    private fun linearBranchClass(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/FlowFlattenCase", null, "java/lang/Object", null)

        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null).apply {
            val first = Label()
            val second = Label()
            visitCode()
            visitJumpInsn(Opcodes.GOTO, first)
            visitLabel(first)
            visitInsn(Opcodes.NOP)
            visitJumpInsn(Opcodes.GOTO, second)
            visitLabel(second)
            visitInsn(Opcodes.NOP)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }
}
