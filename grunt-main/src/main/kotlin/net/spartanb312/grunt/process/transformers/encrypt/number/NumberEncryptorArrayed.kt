package net.spartanb312.grunt.process.transformers.encrypt.number

import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.extensions.toInsnNode
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList

object NumberEncryptorArrayed : NumberEncryptor {

    fun <T : Number> encrypt(value: T, owner: ClassNode, field: FieldNode, list: MutableList<Value<T>>): InsnList {
        return insnList {
            GETSTATIC(owner.name, field.name, field.desc)
            +list.size.toInsnNode()
            AALOAD
            when (value) {
                is Int -> {
                    CHECKCAST("java/lang/Integer")
                    INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I")
                }

                is Long -> {
                    CHECKCAST("java/lang/Long")
                    INVOKEVIRTUAL("java/lang/Long", "longValue", "()J")
                }

                is Float -> {
                    CHECKCAST("java/lang/Float")
                    INVOKEVIRTUAL("java/lang/Float", "floatValue", "()F")
                }

                is Double -> {
                    CHECKCAST("java/lang/Double")
                    INVOKEVIRTUAL("java/lang/Double", "doubleValue", "()D")
                }

                else -> throw IllegalArgumentException("Unsupported value type")
            }
            list.add(Value(value))
        }
    }

    fun <T : Number> decryptMethod(owner: ClassNode, field: FieldNode, values: List<Value<T>>): InsnList = insnList {
        // Create array
        +values.size.toInsnNode()
        ANEWARRAY("java/lang/Object")
        PUTSTATIC(owner.name, field.name, field.desc) // desc should be [Ljava/lang/Object;
        // Decrypt values
        values.forEachIndexed { index, value ->
            GETSTATIC(owner.name, field.name, field.desc)
            +index.toInsnNode()
            +value.decrypt
            // Wrap value
            when (value.value) {
                is Int -> INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                is Long -> INVOKESTATIC("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
                is Float -> INVOKESTATIC("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;")
                is Double -> INVOKESTATIC("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;")
                else -> throw IllegalArgumentException("Unsupported value type")
            }
            AASTORE
        }
    }

    fun ClassNode.getOrCreateField(): FieldNode {
        return fields.find { it.name == "numbers_array" }
            ?: FieldNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "numbers_array",
                "[Ljava/lang/Object;",
                null,
                null
            ).also { fields.add(it) }
    }

    class Value<T : Number>(val value: T) {
        val decrypt: InsnList get() = NumberEncryptorClassic.encrypt(value)
    }

}