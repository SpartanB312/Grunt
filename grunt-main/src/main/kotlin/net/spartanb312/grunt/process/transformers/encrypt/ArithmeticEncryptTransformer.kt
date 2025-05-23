package net.spartanb312.grunt.process.transformers.encrypt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.number.replaceIAND
import net.spartanb312.grunt.process.transformers.encrypt.number.replaceINEG
import net.spartanb312.grunt.process.transformers.encrypt.number.replaceIOR
import net.spartanb312.grunt.process.transformers.encrypt.number.replaceIXOR
import net.spartanb312.grunt.utils.Counter
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

/**
 * Replace logic operations with substitutions
 * Last update on 24/10/23
 */
object ArithmeticEncryptTransformer : Transformer("ArithmeticEncrypt", Category.Encryption), MethodProcessor {

    private val times by setting("Intensity", 1)
    private val rate by setting("Percentage", 50)
    private val maxInsnSize by setting("MaxInsnSize", 16384)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting arithmetic instructions...")
        val count = count {
            repeat(times) { t ->
                if (times > 1) Logger.info("    Encrypting integers ${t + 1} of $times times")
                runBlocking {
                    nonExcluded.asSequence()
                        .filter { c -> exclusion.none { c.name.startsWith(it) } }
                        .forEach { classNode ->
                            fun job() {
                                classNode.methods.asSequence()
                                    .filter { !it.isAbstract && !it.isNative }
                                    .forEach { methodNode: MethodNode ->
                                        encryptArithmetic(methodNode)
                                    }
                            }
                            if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                        }
                }
            }
        }.get()
        Logger.info("    Encrypted $count arithmetic instructions")
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        Counter().encryptArithmetic(method)
    }

    private fun Counter.encryptArithmetic(methodNode: MethodNode): Boolean {
        var modified = false
        val insnList = instructions {
            var skipInsn = 0
            for ((index, insn) in methodNode.instructions.withIndex()) {
                if (skipInsn > 0) {
                    skipInsn--
                    continue
                }
                // Avoid "method too large"
                val currentSize = insnList.size() + methodNode.instructions.size() - index
                if (currentSize >= maxInsnSize
                    // Obfuscation rate/percentage
                    || Random.nextInt(0, 100) > rate) {
                    +insn
                    continue
                }
                if (index < methodNode.instructions.size() - 2) {
                    val next = methodNode.instructions[index + 1]
                    val nextNext = methodNode.instructions[index + 2]
                    when {
                        insn.opcode == Opcodes.ICONST_M1 && next.opcode == Opcodes.IXOR && nextNext.opcode == Opcodes.IAND -> {
                            if (Random.nextBoolean()) {
                                DUP_X1
                                IOR
                                SWAP
                                ISUB
                            } else {
                                SWAP
                                DUP_X1
                                IAND
                                ISUB
                            }
                            skipInsn += 2
                            modified = true
                        }

                        insn.opcode == Opcodes.ISUB && next.opcode == Opcodes.ICONST_M1 && nextNext.opcode == Opcodes.IXOR -> {
                            if (Random.nextBoolean()) {
                                SWAP
                                ISUB
                                ICONST_1
                                ISUB
                            } else {
                                SWAP
                                ICONST_M1
                                IXOR
                                IADD
                            }
                            skipInsn += 2
                            modified = true
                        }

                        insn.opcode == Opcodes.ICONST_M1 && next.opcode == Opcodes.IXOR -> {
                            INEG
                            ICONST_M1
                            IADD
                            skipInsn += 1
                            modified = true
                        }

                        insn.opcode == Opcodes.INEG -> {
                            +replaceINEG()
                            modified = true
                        }

                        insn.opcode == Opcodes.IXOR -> {
                            +replaceIXOR()
                            modified = true
                        }

                        insn.opcode == Opcodes.IOR -> {
                            +replaceIOR()
                            modified = true
                        }

                        insn.opcode == Opcodes.IAND -> {
                            +replaceIAND()
                            modified = true
                        }

                        else -> {
                            add(-1)
                            +insn
                        }
                    }
                    add(1)
                } else +insn
            }
        }
        methodNode.instructions = insnList
        return modified
    }

}