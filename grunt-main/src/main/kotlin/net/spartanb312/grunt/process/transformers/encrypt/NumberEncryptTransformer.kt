package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.grunt.annotation.DISABLE_SCRAMBLE
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorArrayed
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorArrayed.getOrCreateField
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorClassic
import net.spartanb312.grunt.utils.Counter
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.appendAnnotation
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
    private val float by setting("FloatingPoint", true)
    private val arrayed by setting("Arrayed", false)
    private val maxInsnSize by setting("MaxInsnSize", 16384)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting numbers...")
        val count = count {
            repeat(times) { t ->
                if (times > 1) Logger.info("    Encrypting integers ${t + 1} of $times times")
                nonExcluded.asSequence()
                    .filter { c -> exclusion.none { c.name.startsWith(it) } }
                    .forEach { classNode ->
                        val list = mutableListOf<NumberEncryptorArrayed.Value>()
                        val field = if (arrayed && !classNode.isInterface) classNode.getOrCreateField() else null
                        field?.appendAnnotation(DISABLE_SCRAMBLE)
                        classNode.methods.asSequence()
                            .filter { !it.isAbstract && !it.isNative }
                            .forEach { methodNode: MethodNode ->
                                encryptNumber(classNode, methodNode, field, list)
                                if (float) encryptFloatingPoint(classNode, methodNode, field, list)
                            }
                        if (arrayed && list.isNotEmpty() && field != null) {
                            val insert = NumberEncryptorArrayed.decryptMethod(classNode, field, list)
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
        Logger.info("    Encrypted $count numbers")
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        Counter().encryptNumber(owner, method, null, null)
        Counter().encryptFloatingPoint(owner, method, null, null)
    }

    private fun Counter.encryptNumber(
        owner: ClassNode,
        methodNode: MethodNode,
        fieldNode: FieldNode?,
        numList: MutableList<NumberEncryptorArrayed.Value>?
    ) {
        methodNode.instructions
            .filter { it.opcode != Opcodes.NEWARRAY }
            .forEach {
                if (methodNode.instructions.size() < maxInsnSize) {
                    if (it.opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5) {
                        if (arrayed && numList != null && fieldNode != null) {
                            methodNode.instructions.insertBefore(
                                it,
                                NumberEncryptorArrayed.encrypt(
                                    it.opcode - 0x3,
                                    owner,
                                    fieldNode,
                                    numList
                                )
                            )
                        } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(it.opcode - 0x3))
                        methodNode.instructions.remove(it)
                        add()
                    } else if (it is IntInsnNode) {
                        if (arrayed && numList != null && fieldNode != null) {
                            methodNode.instructions.insertBefore(
                                it,
                                NumberEncryptorArrayed.encrypt(
                                    it.operand,
                                    owner,
                                    fieldNode,
                                    numList
                                )
                            )
                        } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(it.operand))
                        methodNode.instructions.remove(it)
                        add()
                    } else if (it is LdcInsnNode && it.cst is Int) {
                        val value = it.cst as Int
                        if (value < -(Short.MAX_VALUE * 8) + Int.MAX_VALUE) {
                            if (arrayed && numList != null && fieldNode != null) {
                                methodNode.instructions.insertBefore(
                                    it,
                                    NumberEncryptorArrayed.encrypt(
                                        value,
                                        owner,
                                        fieldNode,
                                        numList
                                    )
                                )
                            } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(value))
                            methodNode.instructions.remove(it)
                            add()
                        }
                    } else if (it.opcode in Opcodes.LCONST_0..Opcodes.LCONST_1) {
                        val value = (it.opcode - 0x9).toLong()
                        if (arrayed && numList != null && fieldNode != null) {
                            methodNode.instructions.insertBefore(
                                it,
                                NumberEncryptorArrayed.encrypt(
                                    value,
                                    owner,
                                    fieldNode,
                                    numList
                                )
                            )
                        } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(value))
                        methodNode.instructions.remove(it)
                        add()
                    } else if (it is LdcInsnNode && it.cst is Long) {
                        if (arrayed && numList != null && fieldNode != null) {
                            methodNode.instructions.insertBefore(
                                it,
                                NumberEncryptorArrayed.encrypt(
                                    it.cst as Long,
                                    owner,
                                    fieldNode,
                                    numList
                                )
                            )
                        } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(it.cst as Long))
                        methodNode.instructions.remove(it)
                        add()
                    }
                }
            }
    }

    private fun Counter.encryptFloatingPoint(
        owner: ClassNode,
        methodNode: MethodNode,
        fieldNode: FieldNode?,
        numList: MutableList<NumberEncryptorArrayed.Value>?
    ) {
        methodNode.instructions.toList().forEach {
            fun encryptFloat(cst: Float) {
                if (arrayed && numList != null) {
                    methodNode.instructions.insertBefore(
                        it,
                        NumberEncryptorArrayed.encrypt(
                            cst,
                            owner,
                            fieldNode!!,
                            numList
                        )
                    )
                } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(cst))
                methodNode.instructions.remove(it)
                add()
            }

            fun encryptDouble(cst: Double) {
                if (arrayed && numList != null) {
                    methodNode.instructions.insertBefore(
                        it,
                        NumberEncryptorArrayed.encrypt(
                            cst,
                            owner,
                            fieldNode!!,
                            numList
                        )
                    )
                } else methodNode.instructions.insertBefore(it, NumberEncryptorClassic.encrypt(cst))
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