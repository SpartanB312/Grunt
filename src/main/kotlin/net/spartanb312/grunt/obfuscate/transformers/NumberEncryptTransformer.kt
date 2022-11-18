package net.spartanb312.grunt.obfuscate.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.obfuscate.Transformer
import net.spartanb312.grunt.obfuscate.resource.ResourceCache
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.util.*
import java.util.concurrent.ThreadLocalRandom

object NumberEncryptTransformer : Transformer("NumberEncrypt") {

    private val times by value("Intensity", 1)
    private val exclusion by value("Exclusions", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting numbers...")
        val count = count {
            repeat(times) { t ->
                if (times > 1) Logger.info("    Encrypting numbers ${t + 1} of $times times")
                nonExcluded.asSequence()
                    .filter { c -> exclusion.none { c.name.startsWith(it) } }
                    .forEach { classNode ->
                        classNode.methods.asSequence()
                            .filter { !it.isAbstract && !it.isNative }
                            .forEach { methodNode: MethodNode ->
                                methodNode.instructions
                                    .filter { insnNode: AbstractInsnNode -> insnNode.opcode != Opcodes.NEWARRAY }
                                    .forEach { insnNode: AbstractInsnNode ->
                                        if (insnNode.opcode in 0x2..0x8) {
                                            methodNode.instructions.insertBefore(insnNode, xor(insnNode.opcode - 0x3))
                                            methodNode.instructions.remove(insnNode)
                                            if (t == 0) add(1)
                                        } else if (insnNode is IntInsnNode) {
                                            methodNode.instructions.insertBefore(insnNode, xor(insnNode.operand))
                                            methodNode.instructions.remove(insnNode)
                                            if (t == 0) add(1)
                                        } else if (insnNode is LdcInsnNode && insnNode.cst is Int) {
                                            val value = insnNode.cst as Int
                                            if (value < -(Short.MAX_VALUE * 8) + Int.MAX_VALUE) {
                                                methodNode.instructions.insertBefore(insnNode, xor(value))
                                                methodNode.instructions.remove(insnNode)
                                                if (t == 0) add(1)
                                            }
                                        }
                                    }
                            }
                    }
            }
        }.get()
        Logger.info("    Encrypted $count numbers")
    }

    private fun xor(value: Int): InsnList {
        val insnList = InsnList()
        val first = Random(ThreadLocalRandom.current().nextInt().toLong()).nextInt(Short.MAX_VALUE.toInt()) + value
        val second = -Random(ThreadLocalRandom.current().nextInt().toLong()).nextInt(Short.MAX_VALUE.toInt()) + value
        val random = Random(ThreadLocalRandom.current().nextInt().toLong()).nextInt(Short.MAX_VALUE.toInt())
        insnList.add((first xor value).toInsnNode())
        insnList.add((second xor value + random).toInsnNode())
        insnList.add(InsnNode(Opcodes.IXOR))
        insnList.add((first xor value + random).toInsnNode())
        insnList.add(InsnNode(Opcodes.IXOR))
        insnList.add((second).toInsnNode())
        insnList.add(InsnNode(Opcodes.IXOR))
        return insnList
    }

}