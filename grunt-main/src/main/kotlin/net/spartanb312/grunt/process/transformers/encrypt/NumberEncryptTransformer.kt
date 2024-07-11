package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.builder.INT
import net.spartanb312.grunt.utils.builder.IXOR
import net.spartanb312.grunt.utils.builder.insnList
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

/**
 * Encrypt integer and long numbers
 * Last update on 2024/06/26
 */
object NumberEncryptTransformer : Transformer("NumberEncrypt", Category.Encryption) {

    private val times by setting("Intensity", 1)
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
                                methodNode.instructions
                                    .filter { it.opcode != Opcodes.NEWARRAY }
                                    .forEach {
                                        if (it.opcode in 0x2..0x8) {
                                            methodNode.instructions.insertBefore(it, xor(it.opcode - 0x3))
                                            methodNode.instructions.remove(it)
                                            if (t == 0) add()
                                        } else if (it is IntInsnNode) {
                                            methodNode.instructions.insertBefore(it, xor(it.operand))
                                            methodNode.instructions.remove(it)
                                            if (t == 0) add()
                                        } else if (it is LdcInsnNode && it.cst is Int) {
                                            val value = it.cst as Int
                                            if (value < -(Short.MAX_VALUE * 8) + Int.MAX_VALUE) {
                                                methodNode.instructions.insertBefore(it, xor(value))
                                                methodNode.instructions.remove(it)
                                                if (t == 0) add()
                                            }
                                        }
                                    }
                            }
                    }
            }
        }.get()
        Logger.info("    Encrypted $count integer numbers")
    }

    private fun xor(value: Int) = insnList {
        val first = Random.nextInt(Short.MAX_VALUE.toInt()) + value
        val second = -Random.nextInt(Short.MAX_VALUE.toInt()) + value
        val random = Random.nextInt(Short.MAX_VALUE.toInt())
        INT(first xor value)
        INT(second xor value + random)
        IXOR
        INT(first xor value + random)
        IXOR
        INT(second)
        IXOR
    }

}