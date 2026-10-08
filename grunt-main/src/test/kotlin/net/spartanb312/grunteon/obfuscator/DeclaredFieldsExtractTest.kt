package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.pipeline.JvmObfuscation
import net.spartanb312.grunteon.obfuscator.pipeline.execute
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.DeclaredFieldsExtract
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DeclaredFieldsExtractTest {
    @Test
    fun preservesInstanceStringConstantsAndConstructorAssignments() {
        for (version in listOf(V1_8, V22)) {
            val input = instanceFields(version, constructor = true, assignments = true)
            assertInstanceValues(verifyAndLoad(input), "first", "second")

            val output = transform(input)
            assertInstanceValues(verifyAndLoad(output), "first", "second")
            assertEquals(listOf("first", "second"), output.fields.map { it.value })
            assertEquals(
                input.methods.single().instructions.map { it.opcode },
                output.methods.single().instructions.map { it.opcode }
            )
        }
    }

    @Test
    fun ignoresInstanceConstantValueWithoutConstructorAssignment() {
        val input = instanceFields(V22, constructor = true, assignments = false)
        assertInstanceValues(verifyAndLoad(input), null, null)
        assertInstanceValues(verifyAndLoad(transform(input)), null, null)
    }

    @Test
    fun doesNotInventAConstructorForInstanceConstantValue() {
        val input = instanceFields(V22, constructor = false, assignments = false)
        assertEquals(0, verifyAndLoad(input).declaredConstructors.size)
        val output = transform(input)
        assertFalse(output.methods.any { it.name == "<init>" })
        assertEquals(0, verifyAndLoad(output).declaredConstructors.size)
    }

    @Test
    fun extractsStaticConstantsBeforeExistingClassInitializerOrCreatesOne() {
        for (withInitializer in listOf(false, true)) {
            val input = staticFields(withInitializer)
            assertStaticValues(verifyAndLoad(input), withInitializer)
            val output = transform(input)
            assertStaticValues(verifyAndLoad(output), withInitializer)
            output.fields.forEach { assertNull(it.value) }
            assertEquals(1, output.methods.count { it.name == "<clinit>" })
        }
    }

    private fun transform(input: ClassNode): ClassNode {
        val directory = createTempDirectory("grunteon-declared-fields-")
        val file = directory.resolve("Fields.class")
        try {
            file.writeBytes(bytes(input))
            val instance = Grunteon.create(
                ObfConfig(
                    globalConfig = GlobalConfig(input = directory.pathString, output = null, dumpMappings = false),
                    transformers = listOf(TransformerEntry(config = DeclaredFieldsExtract.Config()))
                )
            )
            JvmObfuscation().execute(instance)
            return instance.workRes.inputClassMap.getValue(input.name)
        } finally {
            file.deleteIfExists()
            directory.deleteIfExists()
        }
    }

    private fun instanceFields(version: Int, constructor: Boolean, assignments: Boolean): ClassNode {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(version, ACC_PUBLIC or ACC_SUPER, "example/InstanceFields", null, "java/lang/Object", null)
        writer.visitField(ACC_PUBLIC or ACC_FINAL, "a", "Ljava/lang/String;", null, "first").visitEnd()
        writer.visitField(ACC_PUBLIC or ACC_FINAL, "b", "Ljava/lang/String;", null, "second").visitEnd()
        if (constructor) {
            writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null).apply {
                visitCode()
                visitVarInsn(ALOAD, 0)
                visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                if (assignments) {
                    for ((name, value) in listOf("a" to "first", "b" to "second")) {
                        visitVarInsn(ALOAD, 0)
                        visitLdcInsn(value)
                        visitFieldInsn(PUTFIELD, "example/InstanceFields", name, "Ljava/lang/String;")
                    }
                }
                visitInsn(RETURN)
                visitMaxs(0, 0)
                visitEnd()
            }
        }
        writer.visitEnd()
        return ClassNode().apply { ClassReader(writer.toByteArray()).accept(this, 0) }
    }

    private fun staticFields(withInitializer: Boolean): ClassNode {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(V22, ACC_PUBLIC or ACC_SUPER, "example/StaticFields", null, "java/lang/Object", null)
        writer.visitField(ACC_PUBLIC or ACC_STATIC or ACC_FINAL, "text", "Ljava/lang/String;", null, "static").visitEnd()
        writer.visitField(ACC_PUBLIC or ACC_STATIC or ACC_FINAL, "number", "I", null, 42).visitEnd()
        writer.visitField(ACC_PUBLIC or ACC_STATIC or ACC_FINAL, "wide", "J", null, 1234567890123L).visitEnd()
        if (withInitializer) {
            writer.visitField(ACC_PUBLIC or ACC_STATIC, "observed", "Ljava/lang/String;", null, null).visitEnd()
            writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null).apply {
                visitCode()
                visitFieldInsn(GETSTATIC, "example/StaticFields", "text", "Ljava/lang/String;")
                visitFieldInsn(PUTSTATIC, "example/StaticFields", "observed", "Ljava/lang/String;")
                visitInsn(RETURN)
                visitMaxs(0, 0)
                visitEnd()
            }
        }
        writer.visitEnd()
        return ClassNode().apply { ClassReader(writer.toByteArray()).accept(this, 0) }
    }

    private fun bytes(node: ClassNode): ByteArray = ClassWriter(ClassWriter.COMPUTE_MAXS).apply {
        node.accept(this)
    }.toByteArray()

    private fun verifyAndLoad(node: ClassNode): Class<*> {
        val bytes = bytes(node)
        val diagnostics = StringWriter()
        CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(diagnostics))
        assertEquals("", diagnostics.toString(), "ASM verification failed for ${node.name}")
        return object : ClassLoader(javaClass.classLoader) {
            fun define(): Class<*> = defineClass(node.name.replace('/', '.'), bytes, 0, bytes.size)
        }.define()
    }

    private fun assertInstanceValues(type: Class<*>, a: String?, b: String?) {
        val instance = type.getConstructor().newInstance()
        assertEquals(a, type.getField("a").get(instance))
        assertEquals(b, type.getField("b").get(instance))
    }

    private fun assertStaticValues(type: Class<*>, withInitializer: Boolean) {
        assertEquals("static", type.getField("text").get(null))
        assertEquals(42, type.getField("number").get(null))
        assertEquals(1234567890123L, type.getField("wide").get(null))
        if (withInitializer) assertEquals("static", type.getField("observed").get(null))
    }
}