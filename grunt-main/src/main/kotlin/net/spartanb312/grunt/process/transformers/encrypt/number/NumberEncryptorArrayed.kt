package net.spartanb312.grunt.process.transformers.encrypt.number

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.LONG
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.extensions.toInsnNode
import net.spartanb312.genesis.kotlin.instructions
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList

object NumberEncryptorArrayed : NumberEncryptor {

    fun <T : Number> encrypt(value: T, owner: ClassNode, field: FieldNode, list: MutableList<Value>): InsnList {
        return instructions {
            when (value) {
                is Int -> {
                    GETSTATIC(owner.name, field.name, field.desc)
                    +list.size.toInsnNode()
                    IALOAD
                    list.add(Value(value))
                }

                is Long -> {
                    val head = (value shr 32).toInt()
                    val tail = (value and 0x000000FFFFFFFFL).toInt()

                    // Head
                    GETSTATIC(owner.name, field.name, field.desc)
                    +list.size.toInsnNode()
                    IALOAD
                    list.add(Value(head))

                    I2L
                    LONG(0x000000FFFFFFFFL)
                    LAND
                    INT(32)
                    LSHL

                    // Tail
                    GETSTATIC(owner.name, field.name, field.desc)
                    +list.size.toInsnNode()
                    IALOAD
                    list.add(Value(tail))

                    I2L
                    LONG(0x00000000FFFFFFFFL)
                    LAND
                    // Combine
                    LOR
                }

                is Float -> {
                    val bits = value.asInt()
                    GETSTATIC(owner.name, field.name, field.desc)
                    +list.size.toInsnNode()
                    IALOAD
                    list.add(Value(bits))
                    INVOKESTATIC("java/lang/Float", "intBitsToFloat", "(I)F")
                }

                is Double -> {
                    val bits = value.asLong()
                    val head = (bits shr 32).toInt()
                    val tail = (bits and 0x000000FFFFFFFFL).toInt()

                    // Head
                    GETSTATIC(owner.name, field.name, field.desc)
                    +list.size.toInsnNode()
                    IALOAD
                    list.add(Value(head))

                    I2L
                    LONG(0x000000FFFFFFFFL)
                    LAND
                    INT(32)
                    LSHL

                    // Tail
                    GETSTATIC(owner.name, field.name, field.desc)
                    +list.size.toInsnNode()
                    IALOAD
                    list.add(Value(tail))

                    I2L
                    LONG(0x00000000FFFFFFFFL)
                    LAND
                    // Combine
                    LOR
                    INVOKESTATIC("java/lang/Double", "longBitsToDouble", "(J)D")
                }

                else -> throw IllegalArgumentException("Unsupported value type")
            }
        }
    }

    fun decryptMethod(owner: ClassNode, field: FieldNode, values: List<Value>): InsnList = instructions {
        // Create array
        +values.size.toInsnNode()
        NEWARRAY(Opcodes.T_INT)
        PUTSTATIC(owner.name, field.name, field.desc)
        // Decrypt values
        values.forEachIndexed { index, value ->
            GETSTATIC(owner.name, field.name, field.desc)
            +index.toInsnNode()
            +value.decrypt
            IASTORE
        }
    }

    fun ClassNode.getOrCreateField(): FieldNode {
        return fields.find { it.name == "numbers_array" }
            ?: FieldNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "numbers_array",
                "[I",
                null,
                null
            ).also { fields.add(it) }
    }

    class Value(val value: Int) {
        val decrypt: InsnList get() = NumberEncryptorClassic.encrypt(value)
    }

}