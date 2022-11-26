package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
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
                                    .filter { it.opcode != Opcodes.NEWARRAY }
                                    .forEach {
                                        if (it.opcode in 0x2..0x8) {
                                            methodNode.instructions.insertBefore(it, xor(it.opcode - 0x3))
                                            methodNode.instructions.remove(it)
                                            if (t == 0) add(1)
                                        } else if (it is IntInsnNode) {
                                            methodNode.instructions.insertBefore(it, xor(it.operand))
                                            methodNode.instructions.remove(it)
                                            if (t == 0) add(1)
                                        } else if (it is LdcInsnNode && it.cst is Int) {
                                            val value = it.cst as Int
                                            if (value < -(Short.MAX_VALUE * 8) + Int.MAX_VALUE) {
                                                methodNode.instructions.insertBefore(it, xor(value))
                                                methodNode.instructions.remove(it)
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

    private fun xor(value: Int) = InsnList().apply {
        val first = Random(ThreadLocalRandom.current().nextInt().toLong()).nextInt(Short.MAX_VALUE.toInt()) + value
        val second = -Random(ThreadLocalRandom.current().nextInt().toLong()).nextInt(Short.MAX_VALUE.toInt()) + value
        val random = Random(ThreadLocalRandom.current().nextInt().toLong()).nextInt(Short.MAX_VALUE.toInt())
        add((first xor value).toInsnNode())
        add((second xor value + random).toInsnNode())
        add(InsnNode(Opcodes.IXOR))
        add((first xor value + random).toInsnNode())
        add(InsnNode(Opcodes.IXOR))
        add((second).toInsnNode())
        add(InsnNode(Opcodes.IXOR))
    }

}