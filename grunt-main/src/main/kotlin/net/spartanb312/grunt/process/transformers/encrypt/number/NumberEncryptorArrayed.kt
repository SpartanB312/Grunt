package net.spartanb312.grunt.process.transformers.encrypt.number

import net.spartanb312.genesis.kotlin.extensions.PRIVATE
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.extensions.toInsnNode
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.getRandomString
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
                    LALOAD
                    L2I
                    list.add(Value(value.toLong()))
                }

                is Long -> {
                    GETSTATIC(owner.name, field.name, field.desc)
                    +list.size.toInsnNode()
                    LALOAD
                    list.add(Value(value))
                }

                is Float -> {
                    GETSTATIC(owner.name, field.name, field.desc)
                    +list.size.toInsnNode()
                    LALOAD
                    L2I
                    INVOKESTATIC("java/lang/Float", "intBitsToFloat", "(I)F")
                    list.add(Value(value.asInt().toLong()))
                }

                is Double -> {
                    GETSTATIC(owner.name, field.name, field.desc)
                    +list.size.toInsnNode()
                    LALOAD
                    INVOKESTATIC("java/lang/Double", "longBitsToDouble", "(J)D")
                    list.add(Value(value.asLong()))
                }

                else -> throw IllegalArgumentException("Unsupported value type")
            }
        }
    }

    fun decryptMethod(owner: ClassNode, field: FieldNode, values: List<Value>): InsnList = instructions {
        // Create array
        +values.size.toInsnNode()
        NEWARRAY(Opcodes.T_LONG)
        PUTSTATIC(owner.name, field.name, field.desc)
        val arrayInitMethod = method(
            (if (owner.isInterface) PUBLIC else PRIVATE) + STATIC,
            getRandomString(16),
            "()V") {
            INSTRUCTIONS {
                // Decrypt values
                values.forEachIndexed { index, value ->
                    GETSTATIC(owner.name, field.name, field.desc)
                    +index.toInsnNode()
                    +value.decrypt
                    LASTORE
                }
                RETURN
            }
            MAXS(3, 0)
        }.also { owner.methods.add(it) }
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

    class Value(val value: Long) {
        val decrypt: InsnList get() = NumberEncryptorClassic.encrypt(value)
    }

}