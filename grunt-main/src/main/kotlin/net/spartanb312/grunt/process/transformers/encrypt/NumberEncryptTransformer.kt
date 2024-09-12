package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.Counter
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.xor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Encrypt integer and long numbers
 * Last update on 2024/09/13
 */
object NumberEncryptTransformer : Transformer("NumberEncrypt", Category.Encryption), MethodProcessor {

    private val times by setting("Intensity", 1)
    private val maxInsnSize by setting("MaxInsnSize", 16384)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting integer numbers...")
        val count = count {
            repeat(times) { t ->
                if (times > 1) Logger.info("    Encrypting integers ${t + 1} of $times times")
                nonExcluded.asSequence()
                    .filter { c -> exclusion.none { c.name.startsWith(it) } }
                    .forEach { classNode ->
                        classNode.methods.asSequence()
                            .filter { !it.isAbstract && !it.isNative }
                            .forEach { methodNode: MethodNode ->
                                encryptNumber(methodNode)
                            }
                    }
            }
        }.get()
        Logger.info("    Encrypted $count integer numbers")
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        Counter().encryptNumber(method)
    }

    private fun Counter.encryptNumber(methodNode: MethodNode) {
        methodNode.instructions
            .filter { it.opcode != Opcodes.NEWARRAY }
            .forEach {
                if (methodNode.instructions.size() < maxInsnSize) {
                    if (it.opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5) {
                        methodNode.instructions.insertBefore(it, xor(it.opcode - 0x3))
                        methodNode.instructions.remove(it)
                        add()
                    } else if (it is IntInsnNode) {
                        methodNode.instructions.insertBefore(it, xor(it.operand))
                        methodNode.instructions.remove(it)
                        add()
                    } else if (it is LdcInsnNode && it.cst is Int) {
                        val value = it.cst as Int
                        if (value < -(Short.MAX_VALUE * 8) + Int.MAX_VALUE) {
                            methodNode.instructions.insertBefore(it, xor(value))
                            methodNode.instructions.remove(it)
                            add()
                        }
                    } else if (it.opcode in Opcodes.LCONST_0..Opcodes.LCONST_1) {
                        methodNode.instructions.insertBefore(it, xor(it.opcode - 0x9))
                        methodNode.instructions.remove(it)
                        add()
                    } else if (it is LdcInsnNode && it.cst is Long) {
                        methodNode.instructions.insertBefore(it, xor(it.cst as Long))
                        methodNode.instructions.remove(it)
                        add()
                    }
                }
            }
    }

}