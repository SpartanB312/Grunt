/* SPDX-License-Identifier: PolyForm-Strict-1.0.0 */
package net.spartanb312.grunt.yapyap.transformers.encrypt.number

import net.spartanb312.grunteon.obfuscator.util.DISABLE_NUMBER_ENCRYPT
import net.spartanb312.grunteon.obfuscator.util.GENERATED_CLASS
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import org.objectweb.asm.ClassTooLargeException
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodTooLargeException
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.CodeSizeEvaluator
import org.objectweb.asm.tree.*

/** Worker-local budgets. Splitting changes neither keys nor the number of protected occurrences. */
internal class SpeckHelperClasses(private val classVersion: Int, private val baseName: String) {
    private val classes = ArrayList<ClassNode>()
    private var codeBytes = 0L
    private var constantPoolSlots = 128L // class header, descriptors and marker annotations

    fun add(method: MethodNode): String {
        val evaluator = CodeSizeEvaluator(null)
        method.accept(evaluator)
        val size = evaluator.maxSize.toLong()
        require(size <= 65535) { "SPECK helper exceeds JVM method limit: ${method.name}" }
        // Conservative CP upper bound for the deliberately small helper instruction vocabulary.
        // Count duplicates too: actual interning can only make the emitted pool smaller.
        val slots = 16L + method.instructions.sumOf { instruction ->
            when (instruction) {
                is LdcInsnNode -> 2L
                is MethodInsnNode -> 6L
                is InsnNode, is IntInsnNode, is VarInsnNode, is LabelNode, is LineNumberNode -> 0L
                else -> error("Unsupported SPECK helper instruction: ${instruction.javaClass.name}")
            }
        }
        require(slots <= MAX_POOL_SLOTS - 128) { "SPECK helper exceeds constant pool budget" }
        if (classes.isEmpty() || classes.last().methods.size >= MAX_HELPERS ||
            codeBytes + size > MAX_CODE_BYTES || constantPoolSlots + slots > MAX_POOL_SLOTS
        ) {
            val name = if (classes.isEmpty()) baseName else "${baseName}_${classes.size}"
            classes.add(ClassNode().apply {
                visit(classVersion, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
                appendAnnotation(GENERATED_CLASS)
                appendAnnotation(DISABLE_NUMBER_ENCRYPT)
            })
            codeBytes = 0
            constantPoolSlots = 128
        }
        classes.last().methods.add(method)
        codeBytes += size
        constantPoolSlots += slots
        return classes.last().name
    }

    fun finish(): List<ClassNode> {
        classes.forEach {
            val bytes = validateClass(it)
            check(bytes.size <= MAX_CLASS_BYTES) { "SPECK helper exceeds classfile byte budget: ${it.name}" }
        }
        return classes
    }

    companion object {
        internal const val MAX_HELPERS = 256
        internal const val MAX_CODE_BYTES = 256 * 1024
        internal const val MAX_POOL_SLOTS = 48000
        internal const val MAX_CLASS_BYTES = 512 * 1024

        internal fun validateClass(node: ClassNode): ByteArray {
            require(node.methods.size <= 65535 && node.fields.size <= 65535 && node.interfaces.size <= 65535) {
                "SPECK would exceed JVM member limits in ${node.name}"
            }
            try {
                // Exact ASM check includes widened branches, UTF8 and constant-pool limits.
                // No frame computation or hierarchy lookup is needed for this size-only check.
                return ClassWriter(0).also(node::accept).toByteArray()
            } catch (failure: RuntimeException) {
                if (failure !is MethodTooLargeException && failure !is ClassTooLargeException &&
                    failure !is IllegalArgumentException
                ) throw failure
                throw IllegalStateException(
                    "SPECK cannot encode ${node.name} within JVM limits; use explicit helper reuse or narrower filters. " +
                        "No selected literal was silently left unencrypted.", failure
                )
            }
        }
    }
}
