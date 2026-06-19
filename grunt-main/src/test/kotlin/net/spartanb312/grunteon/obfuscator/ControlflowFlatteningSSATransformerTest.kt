package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.exp.ControlflowFlatteningSSA
import net.spartanb312.grunteon.testcase.Asserts
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ControlflowFlatteningSSATransformerTest {
    @Test
    fun dumpsClassAfterControlflowFlatteningTransformer() {
        val output = createTempFile("grunteon-controlflow-flattening", ".jar")
        try {
            val instance = readTestClasses(
                Asserts::class.java,
                ObfConfig(
                    globalConfig = GlobalConfig(
                        output = output.pathString,
                        dumpMappings = false
                    ),
                    transformers = listOf(
                        TransformerEntry(
                            config = ControlflowFlatteningSSA.Config(
                                classFilter = ClassFilterConfig(
                                    includeStrategy = listOf("net/spartanb312/grunteon/testcase/Asserts"),
                                    excludeStrategy = emptyList()
                                )
                            )
                        )
                    )
                )
            )

            context(instance.workRes, instance) {
                instance.run()
            }

            ZipFile(output.toFile()).use { zip ->
                val entry = zip.getEntry("net/spartanb312/grunteon/testcase/Asserts.class")
                assertNotNull(entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                ClassReader(bytes)
            }
        } finally {
            output.deleteIfExists()
        }
    }

    @Test
    fun skipsConstructorsAfterControlflowFlatteningTransformer() {
        val output = createTempFile("grunteon-controlflow-flattening", ".jar")
        try {
            val instance = readTestClasses(
                Asserts::class.java,
                ObfConfig(
                    globalConfig = GlobalConfig(
                        output = output.pathString,
                        dumpMappings = false
                    ),
                    transformers = listOf(
                        TransformerEntry(
                            config = ControlflowFlatteningSSA.Config(
                                classFilter = ClassFilterConfig(
                                    includeStrategy = listOf("net/spartanb312/grunteon/testcase/Asserts"),
                                    excludeStrategy = emptyList()
                                )
                            )
                        )
                    )
                )
            )

            context(instance.workRes, instance) {
                instance.run()
            }

            ZipFile(output.toFile()).use { zip ->
                val entry = zip.getEntry("net/spartanb312/grunteon/testcase/Asserts.class")
                assertNotNull(entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val classNode = ClassNode()
                ClassReader(bytes).accept(classNode, 0)
                val constructor = classNode.methods.single { it.name == "<init>" }
                val hasFlatteningSwitch = constructor.instructions.toArray().any {
                    it.opcode == Opcodes.LOOKUPSWITCH || it.opcode == Opcodes.TABLESWITCH
                }

                assertFalse(hasFlatteningSwitch)
            }
        } finally {
            output.deleteIfExists()
        }
    }

    @Test
    fun skipsSyntheticMethodsAfterControlflowFlatteningTransformer() {
        val input = createTempFile("grunteon-controlflow-flattening-input", ".jar")
        val output = createTempFile("grunteon-controlflow-flattening-output", ".jar")
        try {
            JarOutputStream(input.outputStream()).use { jar ->
                jar.putNextEntry(JarEntry("example/SyntheticCase.class"))
                jar.write(syntheticBranchClass())
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
                        TransformerEntry(config = ControlflowFlatteningSSA.Config())
                    )
                )
            )

            context(instance.workRes, instance) {
                instance.run()
            }

            ZipFile(output.toFile()).use { zip ->
                val entry = zip.getEntry("example/SyntheticCase.class")
                assertNotNull(entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val classNode = ClassNode()
                ClassReader(bytes).accept(classNode, 0)
                val syntheticMethod = classNode.methods.single { it.name == "choose" }
                val hasFlatteningSwitch = syntheticMethod.instructions.toArray().any {
                    it.opcode == Opcodes.LOOKUPSWITCH || it.opcode == Opcodes.TABLESWITCH
                }

                assertFalse(hasFlatteningSwitch)
            }
        } finally {
            input.deleteIfExists()
            output.deleteIfExists()
        }
    }

    @Test
    fun skipsDefaultMethodsAfterControlflowFlatteningTransformer() {
        val input = createTempFile("grunteon-controlflow-flattening-default-input", ".jar")
        val output = createTempFile("grunteon-controlflow-flattening-default-output", ".jar")
        try {
            JarOutputStream(input.outputStream()).use { jar ->
                jar.putNextEntry(JarEntry("example/DefaultCase.class"))
                jar.write(defaultHelperBranchClass())
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
                        TransformerEntry(config = ControlflowFlatteningSSA.Config())
                    )
                )
            )

            context(instance.workRes, instance) {
                instance.run()
            }

            ZipFile(output.toFile()).use { zip ->
                val entry = zip.getEntry("example/DefaultCase.class")
                assertNotNull(entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val classNode = ClassNode()
                ClassReader(bytes).accept(classNode, 0)
                val defaultMethod = classNode.methods.single { it.name == "choose\$default" }
                val hasFlatteningSwitch = defaultMethod.instructions.toArray().any {
                    it.opcode == Opcodes.LOOKUPSWITCH || it.opcode == Opcodes.TABLESWITCH
                }

                assertFalse(hasFlatteningSwitch)
            }
        } finally {
            input.deleteIfExists()
            output.deleteIfExists()
        }
    }

    @Test
    fun skipsUninitializedObjectFlowAfterControlflowFlatteningTransformer() {
        val input = createTempFile("grunteon-controlflow-flattening-uninit-input", ".jar")
        val output = createTempFile("grunteon-controlflow-flattening-uninit-output", ".jar")
        try {
            JarOutputStream(input.outputStream()).use { jar ->
                jar.putNextEntry(JarEntry("example/UninitializedFlowCase.class"))
                jar.write(uninitializedObjectFlowClass())
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
                        TransformerEntry(config = ControlflowFlatteningSSA.Config())
                    )
                )
            )

            context(instance.workRes, instance) {
                instance.run()
            }

            ZipFile(output.toFile()).use { zip ->
                val entry = zip.getEntry("example/UninitializedFlowCase.class")
                assertNotNull(entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val classNode = ClassNode()
                ClassReader(bytes).accept(classNode, 0)
                val method = classNode.methods.single { it.name == "make" }
                val hasFlatteningSwitch = method.instructions.toArray().any {
                    it.opcode == Opcodes.LOOKUPSWITCH || it.opcode == Opcodes.TABLESWITCH
                }

                assertFalse(hasFlatteningSwitch)
            }
        } finally {
            input.deleteIfExists()
            output.deleteIfExists()
        }
    }

    @Test
    fun skipsMethodsOverInstructionBudgetAfterControlflowFlatteningTransformer() {
        val input = createTempFile("grunteon-controlflow-flattening-budget-input", ".jar")
        val output = createTempFile("grunteon-controlflow-flattening-budget-output", ".jar")
        try {
            JarOutputStream(input.outputStream()).use { jar ->
                jar.putNextEntry(JarEntry("example/BudgetCase.class"))
                jar.write(budgetBranchClass())
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
                            config = ControlflowFlatteningSSA.Config(
                                maxInstructions = 5,
                                maxBasicBlocks = 0,
                                maxLocals = 0,
                                maxEstimatedDispatcherArgs = 0,
                                logBudgetSkips = false
                            )
                        )
                    )
                )
            )

            context(instance.workRes, instance) {
                instance.run()
            }

            ZipFile(output.toFile()).use { zip ->
                val entry = zip.getEntry("example/BudgetCase.class")
                assertNotNull(entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val classNode = ClassNode()
                ClassReader(bytes).accept(classNode, 0)
                val method = classNode.methods.single { it.name == "choose" }
                val hasFlatteningSwitch = method.instructions.toArray().any {
                    it.opcode == Opcodes.LOOKUPSWITCH || it.opcode == Opcodes.TABLESWITCH
                }

                assertFalse(hasFlatteningSwitch)
            }
        } finally {
            input.deleteIfExists()
            output.deleteIfExists()
        }
    }

    private fun syntheticBranchClass(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/SyntheticCase", null, "java/lang/Object", null)

        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
            "choose",
            "(I)I",
            null,
            null
        ).apply {
            val falseLabel = Label()
            visitCode()
            visitVarInsn(Opcodes.ILOAD, 0)
            visitJumpInsn(Opcodes.IFEQ, falseLabel)
            visitInsn(Opcodes.ICONST_1)
            visitInsn(Opcodes.IRETURN)
            visitLabel(falseLabel)
            visitInsn(Opcodes.ICONST_0)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun defaultHelperBranchClass(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/DefaultCase", null, "java/lang/Object", null)

        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "choose\$default",
            "(I)I",
            null,
            null
        ).apply {
            val falseLabel = Label()
            visitCode()
            visitVarInsn(Opcodes.ILOAD, 0)
            visitJumpInsn(Opcodes.IFEQ, falseLabel)
            visitInsn(Opcodes.ICONST_1)
            visitInsn(Opcodes.IRETURN)
            visitLabel(falseLabel)
            visitInsn(Opcodes.ICONST_0)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun budgetBranchClass(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/BudgetCase", null, "java/lang/Object", null)

        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "choose",
            "(I)I",
            null,
            null
        ).apply {
            val falseLabel = Label()
            visitCode()
            visitVarInsn(Opcodes.ILOAD, 0)
            visitJumpInsn(Opcodes.IFEQ, falseLabel)
            visitVarInsn(Opcodes.ILOAD, 0)
            visitInsn(Opcodes.ICONST_1)
            visitInsn(Opcodes.IADD)
            visitInsn(Opcodes.IRETURN)
            visitLabel(falseLabel)
            visitInsn(Opcodes.ICONST_0)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun uninitializedObjectFlowClass(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/UninitializedFlowCase", null, "java/lang/Object", null)

        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "make",
            "(Z)Ljava/lang/StringBuilder;",
            null,
            null
        ).apply {
            val falseLabel = Label()
            val joinLabel = Label()
            visitCode()
            visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder")
            visitInsn(Opcodes.DUP)
            visitVarInsn(Opcodes.ILOAD, 0)
            visitJumpInsn(Opcodes.IFEQ, falseLabel)
            visitLdcInsn("yes")
            visitJumpInsn(Opcodes.GOTO, joinLabel)
            visitLabel(falseLabel)
            visitLdcInsn("no")
            visitLabel(joinLabel)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false)
            visitInsn(Opcodes.ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }
}
