package net.spartanb312.grunt.process.transformers.encrypt.number

import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESTATIC
import net.spartanb312.genesis.kotlin.extensions.insn.L2I
import net.spartanb312.genesis.kotlin.extensions.insn.LAND
import net.spartanb312.genesis.kotlin.extensions.insn.LNEG
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
        val maxRandom = Int.MAX_VALUE.toLong()
        val key = Random.nextLong() and maxRandom
        return instructions {
            val temp0 = Random.nextLong(maxRandom) and key or -value
            val temp1 = Random.nextLong(maxRandom) and key.inv() or -value
            +temp0.toInsnNode()
            +temp1.toInsnNode()
            LAND
            LNEG
        }
    }

}