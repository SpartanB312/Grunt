package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorArrayed
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorArrayed.getOrCreateField
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorClassic
import net.spartanb312.grunt.utils.Counter
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

/**
 * Encrypt integer and long numbers
 * Last update on 2024/09/23
 */
object NumberEncryptTransformer : Transformer("NumberEncrypt", Category.Encryption), MethodProcessor {

    private val times by setting("Intensity", 1)
    private val arrayed by setting("Arrayed", false)
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
                        val list = mutableListOf<NumberEncryptorArrayed.Value<in Number>>()
                        val field = if (arrayed && !classNode.isInterface) classNode.getOrCreateField() else null
                        classNode.methods.asSequence()
                            .filter { !it.isAbstract && !it.isNative }
                            .forEach { methodNode: MethodNode ->
                                encryptNumber(classNode, methodNode, field, list)
                            }
                        if (arrayed && list.isNotEmpty()) {
                            val insert = NumberEncryptorArrayed.decryptMethod(classNode, field!!, list)
                            val clinit = classNode.methods.find { it.name == "<clinit>" } ?: MethodNode(
                                Opcodes.ACC_STATIC,
                                "<clinit>",
                                "()V",
                                null,
                                null
                            ).also {
                                it.instructions.insert(InsnNode(Opcodes.RETURN))
                                classNode.methods.add(it)
                            }
                            clinit.instructions.insert(insert)
                        }
                    }
            }
        }.get()
        Logger.info("    Encrypted $count integer numbers")
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        Counter().encryptNumber(owner, method, null, null)
    }

    private fun Counter.encryptNumber(
        owner: ClassNode,
        methodNode: MethodNode,
        fieldNode: FieldNode?,
        numList: MutableList<NumberEncryptorArrayed.Value<in Number>>?
    ) {
        methodNode.instructions
            .filter { it.opcode != Opcodes.NEWARRAY }
            .forEach {
                if (methodNode.instructions.size() < maxInsnSize) {
                    if (it.opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5) {
                        if (arrayed && numList != null) {
                            methodNode.instructions.insertBefore(
                                it,
                                NumberEncryptorArrayed.encrypt(
                                    it.opcode - 0x3,
                                    owner,
                                    fieldNode!!,
                                    numList
                                )
                            )
                        } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(it.opcode - 0x3))
                        methodNode.instructions.remove(it)
                        add()
                    } else if (it is IntInsnNode) {
                        if (arrayed && numList != null) {
                            methodNode.instructions.insertBefore(
                                it,
                                NumberEncryptorArrayed.encrypt(
                                    it.operand,
                                    owner,
                                    fieldNode!!,
                                    numList
                                )
                            )
                        } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(it.operand))
                        methodNode.instructions.remove(it)
                        add()
                    } else if (it is LdcInsnNode && it.cst is Int) {
                        val value = it.cst as Int
                        if (value < -(Short.MAX_VALUE * 8) + Int.MAX_VALUE) {
                            if (arrayed && numList != null) {
                                methodNode.instructions.insertBefore(
                                    it,
                                    NumberEncryptorArrayed.encrypt(
                                        value,
                                        owner,
                                        fieldNode!!,
                                        numList
                                    )
                                )
                            } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(value))
                            methodNode.instructions.remove(it)
                            add()
                        }
                    } else if (it.opcode in Opcodes.LCONST_0..Opcodes.LCONST_1) {
                        methodNode.instructions.insertBefore(
                            it,
                            NumberEncryptorClassic.encrypt((it.opcode - 0x9).toLong())
                        )
                        methodNode.instructions.remove(it)
                        add()
                    } else if (it is LdcInsnNode && it.cst is Long) {
                        methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(it.cst as Long))
                        methodNode.instructions.remove(it)
                        add()
                    }
                }
            }
    }

}