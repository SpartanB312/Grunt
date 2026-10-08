/* SPDX-License-Identifier: PolyForm-Strict-1.0.0 */
package net.spartanb312.grunt.yapyap.transformers.encrypt.number

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.security.MessageDigest
import kotlin.test.*

class SpeckRegressionTest {
    private val transformer = NumberSPECKEncrypt()

    @Test
    fun oldJsonKeepsOccurrenceSpecificProtectionAndNewModeRoundTrips() {
        val defaults = Json.decodeFromString<NumberSPECKEncrypt.Config>("{}")
        assertFalse(defaults.reuseHelpers)
        assertTrue(defaults.integer && defaults.long && defaults.float && defaults.double)
        assertEquals(16384, defaults.maxInstructions)
        val enabled = defaults.copy(reuseHelpers = true)
        assertEquals(enabled, Json.decodeFromString<NumberSPECKEncrypt.Config>(Json.encodeToString(enabled)))
    }

    @Test
    fun reuseKeysDistinguishTypeSignedZeroAndNanPayloads() {
        val values = listOf<Any>(0, 0L, 0f, -0f, 0.0, -0.0,
            Float.fromBits(0x7fc00123), Float.fromBits(0x7fc00456),
            Double.fromBits(0x7ff8000000000123L), Double.fromBits(0x7ff8000000000456L))
        val config = NumberSPECKEncrypt.Config(reuseHelpers = true)
        val keys = values.map { transformer.literalKey(config, LdcInsnNode(it)) }
        assertEquals(values.size, keys.toSet().size)
        assertFalse(keys.contains(null))
    }

    @Test
    fun encryptedHelpersExecuteWithExactRawBitsAndStableSeed() {
        val values = listOf<Any>(Int.MIN_VALUE, Int.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE,
            0f, -0f, Float.POSITIVE_INFINITY, Float.fromBits(0x7fc00123), Float.fromBits(0x7fc00456),
            0.0, -0.0, Double.NEGATIVE_INFINITY,
            Double.fromBits(0x7ff8000000000123L), Double.fromBits(0x7ff8000000000456L))
        for (reuse in listOf(false, true)) {
            fun generate(): List<ClassNode> {
                val owner = owner()
                (values + values).forEachIndexed { index, value ->
                    val desc = when (value) { is Int -> "I"; is Long -> "J"; is Float -> "F"; else -> "D" }
                    val opcode = when (value) { is Int -> IRETURN; is Long -> LRETURN; is Float -> FRETURN; else -> DRETURN }
                    owner.methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, "v$index", "()$desc", null, null).apply {
                        instructions.add(LdcInsnNode(value))
                        instructions.add(InsnNode(opcode))
                    })
                }
                val (helpers, count) = transform(owner, reuse)
                assertEquals(values.size * 2, count)
                assertEquals(values.size * if (reuse) 1 else 2, helpers.sumOf { it.methods.size })
                helpers.forEach { assertEquals(owner.version, it.version) }
                return helpers + owner
            }
            val first = generate().associate { it.name to encode(it) }
            val second = generate().associate { it.name to encode(it) }
            assertEquals(first.keys, second.keys)
            first.forEach { (name, bytes) -> assertContentEquals(bytes, second.getValue(name)) }
            val loader = BytecodeLoader(first)
            val target = loader.loadClass("test.SpeckOwner")
            (values + values).forEachIndexed { index, expected ->
                val actual = target.getMethod("v$index").invoke(null)
                when (expected) {
                    is Float -> assertEquals(expected.toRawBits(), (actual as Float).toRawBits())
                    is Double -> assertEquals(expected.toRawBits(), (actual as Double).toRawBits())
                    else -> assertEquals(expected, actual)
                }
            }
        }
    }

    @Test
    fun thousandAndTenThousandLiteralsRespectHelperAndClassfileBudgets() {
        for (count in listOf(1000, 10000)) {
            for (repeated in listOf(false, true)) {
                val owner = owner()
                repeat(count / 100) { batch ->
                    owner.methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, "batch$batch", "()V", null, null).apply {
                        repeat(100) { index ->
                            instructions.add(LdcInsnNode(if (repeated) 123456789L else (batch * 100L + index)))
                            instructions.add(InsnNode(POP2))
                        }
                        instructions.add(InsnNode(RETURN))
                    })
                }
                // Distinct literals exercise splitting without weakening occurrence-specific protection.
                // Repeated literals exercise the explicitly selected compact mode.
                val (helpers, protected) = transform(owner, repeated)
                assertEquals(count, protected)
                assertEquals(if (repeated) 1 else count, helpers.sumOf { it.methods.size })
                if (!repeated) assertTrue(helpers.size > 1)
                val classes = (helpers + owner).associate { node ->
                    assertEquals(owner.version, node.version)
                    assertTrue(node.methods.size <= SpeckHelperClasses.MAX_HELPERS)
                    val bytes = encode(node)
                    if (node !== owner) assertTrue(bytes.size <= SpeckHelperClasses.MAX_CLASS_BYTES)
                    assertTrue(ClassReader(bytes).itemCount <= 65535)
                    node.name to bytes
                }
                val loaded = BytecodeLoader(classes).loadClass("test.SpeckOwner")
                loaded.getMethod("batch0").invoke(null)
                loaded.getMethod("batch${count / 100 - 1}").invoke(null)
            }
        }
    }

    @Test
    fun defaultDoesNotDeduplicateRepeatedLiterals() {
        val owner = owner()
        owner.methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, "values", "()V", null, null).apply {
            repeat(1000) { instructions.add(LdcInsnNode(123456789L)); instructions.add(InsnNode(POP2)) }
            instructions.add(InsnNode(RETURN))
        })
        val (helpers, count) = transform(owner, false)
        assertEquals(1000, count)
        assertEquals(1000, helpers.sumOf { it.methods.size })
    }

    @Test
    fun oversizedCallerFailsExplicitlyInsteadOfDumpFallbackOrPlaintext() {
        val owner = owner()
        owner.methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, "nearLimit", "()V", null, null).apply {
            repeat(65532) { instructions.add(InsnNode(NOP)) }
            instructions.add(InsnNode(ICONST_1))
            instructions.add(InsnNode(POP))
            instructions.add(InsnNode(RETURN))
        })
        SpeckHelperClasses.validateClass(owner) // Exactly 65535 code bytes before the transform.
        val failure = assertFailsWith<IllegalStateException> { transform(owner, false, 100000) }
        assertTrue(failure.message!!.contains("JVM limits"))
    }

    @Test
    fun oversizedMemberCountIsRejectedBeforeU2Truncation() {
        val owner = owner()
        repeat(65536) { owner.fields.add(FieldNode(ACC_PUBLIC, "f$it", "I", null, null)) }
        assertFailsWith<IllegalArgumentException> { SpeckHelperClasses.validateClass(owner) }
    }

    private fun owner() = ClassNode().apply { visit(V1_8, ACC_PUBLIC, "test/SpeckOwner", null, "java/lang/Object", null) }

    private fun transform(owner: ClassNode, reuse: Boolean, maxInstructions: Int = 16384) =
        transformer.transformClass(
            NumberSPECKEncrypt.Config(reuseHelpers = reuse, dynamicStrength = false, maxInstructions = maxInstructions),
            owner, emptyList(), FastObjectArrayList()
        ) { MessageDigest.getInstance("SHA-256").digest(it.joinToString("|").toByteArray()) }

    private fun encode(node: ClassNode) = ClassWriter(ClassWriter.COMPUTE_FRAMES).also(node::accept).toByteArray()

    private class BytecodeLoader(private val definitions: Map<String, ByteArray>) : ClassLoader() {
        override fun findClass(name: String): Class<*> {
            val bytes = definitions[name.replace('.', '/')] ?: return super.findClass(name)
            return defineClass(name, bytes, 0, bytes.size)
        }
    }
}
