package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorClassic
import net.spartanb312.grunt.utils.Counter
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Encrypt floating point numbers
 * Last update on 2024/09/13
 */
object FloatingPointEncryptTransformer : Transformer("FloatingPointEncrypt", Category.Encryption), MethodProcessor {

    private val maxInsnSize by setting("MaxInsnSize", 16384)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting floating point numbers...")
        val count = count {
            nonExcluded.asSequence()
                .filter { c -> exclusion.none { c.name.startsWith(it) } }
                .forEach { classNode ->
                    classNode.methods.asSequence()
                        .filter { !it.isAbstract && !it.isNative }
                        .forEach { methodNode: MethodNode ->
                            encryptFloatingPoint(methodNode)
                        }
                }
        }.get()
        Logger.info("    Encrypted $count floating point numbers")
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        Counter().encryptFloatingPoint(method)
    }

    private fun Counter.encryptFloatingPoint(methodNode: MethodNode) {
        methodNode.instructions.toList().forEach {
            fun encryptFloat(cst: Float) {
                methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(cst))
                methodNode.instructions.remove(it)
                add()
            }

            fun encryptDouble(cst: Double) {
                methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(cst))
                methodNode.instructions.remove(it)
                add()
            }
            if (methodNode.instructions.size() + 3 < maxInsnSize) {
                when {
                    it is LdcInsnNode -> when (val cst = it.cst) {
                        is Float -> encryptFloat(cst)
                        is Double -> encryptDouble(cst)
                    }

                    it.opcode == Opcodes.FCONST_0 -> encryptFloat(0.0f)
                    it.opcode == Opcodes.FCONST_1 -> encryptFloat(1.0f)
                    it.opcode == Opcodes.FCONST_2 -> encryptFloat(2.0f)
                    it.opcode == Opcodes.DCONST_0 -> encryptDouble(0.0)
                    it.opcode == Opcodes.DCONST_1 -> encryptDouble(1.0)
                }
            }
        }
    }

}