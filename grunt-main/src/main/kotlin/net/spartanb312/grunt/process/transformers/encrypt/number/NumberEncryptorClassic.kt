package net.spartanb312.grunt.process.transformers.encrypt.number

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.extensions.toInsnNode
import net.spartanb312.genesis.kotlin.instructions
import org.objectweb.asm.tree.InsnList
import kotlin.random.Random

/**
 * Replace const load instructions with equivalent instruction set
 * It should not be too complicated, otherwise it will significantly affect performance
 */
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

    // TODO more way to encrypt int
    fun encrypt(value: Int): InsnList {
        val random = Random.nextInt(Int.MAX_VALUE)
        val negative = (if (Random.nextBoolean()) random else -random) + value
        val obfuscated = value xor negative
        return instructions {
            if (Random.nextBoolean()) {
                +negative.toInsnNode()
                I2L
                +obfuscated.toInsnNode()
                I2L
                LXOR
                L2I
            } else {
                LDC(negative.toLong())
                L2I
                +obfuscated.toInsnNode()
                IXOR
            }
        }
    }

    // TODO: VERY SLOW via parse string, using 2 int with bit operation instead
    fun encrypt(value: Long): InsnList = instructions {
        val key = Random.nextLong()
        val unsignedString = java.lang.Long.toUnsignedString(key, 32)
        LDC(unsignedString)
        INT(32)
        INVOKESTATIC("java/lang/Long", "parseUnsignedLong", "(Ljava/lang/String;I)J")
        val obfuscated = key xor value
        +obfuscated.toInsnNode()
        LXOR
    }

}