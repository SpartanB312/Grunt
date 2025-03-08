package net.spartanb312.grunt.process.transformers.encrypt.number

import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESTATIC
import net.spartanb312.genesis.kotlin.extensions.insn.L2I
import net.spartanb312.genesis.kotlin.extensions.insn.LAND
import net.spartanb312.genesis.kotlin.extensions.insn.LXOR
import net.spartanb312.genesis.kotlin.extensions.toInsnNode
import net.spartanb312.genesis.kotlin.instructions
import org.objectweb.asm.tree.InsnList
import kotlin.random.Random

object NumberEncryptorClassic : NumberEncryptor {

    fun <T : Number> encrypt(value: T): InsnList {
        return when (value) {
            is Int -> encrypt(value.toInt())
            is Long -> encrypt(value.toLong())
            is Float -> encrypt(value.toFloat())
            is Double -> encrypt(value.toDouble())
            else -> throw Exception("Not yet implemented")
        }
    }

    fun encrypt(value: Float): InsnList {
        return instructions {
            +encrypt(value.asInt())
            INVOKESTATIC("java/lang/Float", "intBitsToFloat", "(I)F")
        }
    }

    fun encrypt(value: Double): InsnList {
        return instructions {
            +encrypt(value.asLong())
            INVOKESTATIC("java/lang/Double", "longBitsToDouble", "(J)D")
        }
    }

    fun encrypt(value: Int): InsnList {
        return instructions {
            +encrypt(value.toLong())
            L2I
        }
    }

    fun encrypt(value: Long): InsnList {
        return instructions {
            val key = Random.nextLong()
            val obfuscated = value xor key
            val mask = Random.nextLong()
            val temp0 = Random.nextLong() and mask or obfuscated
            val temp1 = Random.nextLong() and mask.inv() or obfuscated
            +temp0.toInsnNode()
            +temp1.toInsnNode()
            LAND
            +key.toInsnNode()
            LXOR
        }
    }

}