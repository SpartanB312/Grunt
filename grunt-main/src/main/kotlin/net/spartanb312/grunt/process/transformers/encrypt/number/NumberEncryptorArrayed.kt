package net.spartanb312.grunt.process.transformers.encrypt.number

import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.getRandomString
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import kotlin.random.Random

/**
 * It will only decrypt once. We can use more complex encryption methods without worrying about performance overhead
 */
object NumberEncryptorArrayed : NumberEncryptor {

    fun <T : Number> encrypt(value: T, owner: ClassNode, field: FieldNode, list: MutableList<Long>): InsnList {
        return instructions {
            when (value) {
                is Int -> {
                    GETSTATIC(owner.name, field.name, field.desc)
                    val index = list.indexOf(value.toLong())
                    if (index != -1) {
                        INT(index)
                    } else {
                        +list.size.toInsnNode()
                        list.add(value.toLong())
                    }
                    LALOAD
                    L2I
                }

                is Long -> {
                    GETSTATIC(owner.name, field.name, field.desc)
                    val index = list.indexOf(value)
                    if (index != -1) {
                        INT(index)
                    } else {
                        +list.size.toInsnNode()
                        list.add(value)
                    }
                    LALOAD
                }

                is Float -> {
                    GETSTATIC(owner.name, field.name, field.desc)
                    val index = list.indexOf(value.asInt().toLong())
                    if (index != -1) {
                        INT(index)
                    } else {
                        +list.size.toInsnNode()
                        list.add(value.asInt().toLong())
                    }
                    LALOAD
                    L2I
                    INVOKESTATIC("java/lang/Float", "intBitsToFloat", "(I)F")
                }

                is Double -> {
                    GETSTATIC(owner.name, field.name, field.desc)
                    val index = list.indexOf(value.asLong())
                    if (index != -1) {
                        INT(index)
                    } else {
                        +list.size.toInsnNode()
                        list.add(value.asLong())
                    }
                    LALOAD
                    INVOKESTATIC("java/lang/Double", "longBitsToDouble", "(J)D")
                }

                else -> throw IllegalArgumentException("Unsupported value type")
            }
        }
    }

    fun decryptMethod(owner: ClassNode, field: FieldNode, values: List<Long>): InsnList = instructions {
        // Create array
        +values.size.toInsnNode()
        NEWARRAY(Opcodes.T_LONG)
        PUTSTATIC(owner.name, field.name, field.desc)
        val localKey = Random.nextLong()
        val arrayInitMethod = method(
            (if (owner.isInterface) PUBLIC else PRIVATE) + STATIC,
            getRandomString(16),
            "(J)V") {
            INSTRUCTIONS {
                val localKeySlot = 0
                // Decrypt values
                val map = mutableListOf<Pair<Int, Long>>()
                values.forEachIndexed { index, value ->
                    map.add(Pair(index, value))
                }
                map.shuffled().forEach { (index, value) ->
                    GETSTATIC(owner.name, field.name, field.desc)
                    +index.toInsnNode()
                    LLOAD(localKeySlot)
                    +NumberEncryptorClassic.encrypt(value xor localKey)
                    LXOR
                    // TODO: modify local key?
                    LASTORE
                }
                RETURN
            }
        }.also { owner.methods.add(it) }
        +NumberEncryptorClassic.encrypt(localKey)
        INVOKESTATIC(owner.name, arrayInitMethod.name, arrayInitMethod.desc, owner.isInterface)
    }

    fun ClassNode.getOrCreateField(): FieldNode {
        return fields.find { it.name == "numbers_array" }
            ?: FieldNode(
                (if (isInterface) Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL else Opcodes.ACC_PRIVATE) + Opcodes.ACC_STATIC,
                "numbers_array",
                "[J",
                null,
                null
            )
    }

}