package net.spartanb312.grunt.utils

import jdk.incubator.vector.VectorOperators.L2I
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.extensions.toInsnNode
import org.objectweb.asm.tree.InsnList
import kotlin.random.Random

private fun Double.asLong(): Long = java.lang.Double.doubleToRawLongBits(this)

private fun Float.asInt(): Int = java.lang.Float.floatToRawIntBits(this)

fun <T : Number> xor(value: T): InsnList {
    return when (value) {
        is Int -> xor(value.toInt())
        is Long -> xor(value.toLong())
        is Float -> xor(value.toFloat())
        is Double -> xor(value.toDouble())
        else -> throw Exception("Not yet implemented")
    }
}

fun xor(value: Float): InsnList {
    val intBits = value.asInt()
    val key = Random.nextInt()
    val encryptedIntBits = intBits xor key
    return insnList {
        INT(encryptedIntBits)
        INT(key)
        IXOR
        INVOKESTATIC("java/lang/Float", "intBitsToFloat", "(I)F")
    }
}

fun xor(value: Double): InsnList {
    val longBits = value.asLong()
    val key = Random.nextLong()
    val encryptedLongBits = longBits xor key
    return insnList {
        LONG(encryptedLongBits)
        LONG(key)
        LXOR
        INVOKESTATIC("java/lang/Double", "longBitsToDouble", "(J)D")
    }
}

fun xor(value: Int): InsnList {
    val random = Random.nextInt(Int.MAX_VALUE)
    val negative = (if (Random.nextBoolean()) random else -random) + value
    val obfuscated = value xor negative
    return insnList {
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

fun xor(value: Long): InsnList {
    val random = Random.nextLong()
    val obfuscated = value xor random
    return insnList {
        +obfuscated.toInsnNode()
        +random.toInsnNode()
        LXOR
    }
}

fun replaceINEG(type: Int = Random.nextInt(2)): InsnList = insnList {
    when (type) {
        0 -> {
            ICONST_M1
            IXOR
            ICONST_1
            IADD
        }

        else -> {
            ICONST_1
            ISUB
            ICONST_M1
            IXOR
        }
    }
}

fun replaceIAND(): InsnList = insnList {
    SWAP
    DUP_X1
    ICONST_M1
    IXOR
    IOR
    SWAP
    ICONST_M1
    IXOR
    ISUB
}

fun replaceIOR(): InsnList = insnList {
    DUP_X1
    ICONST_M1
    IXOR
    IAND
    IADD
}

fun replaceIXOR(type: Int = Random.nextInt(3)): InsnList = insnList {
    when (type) {
        0 -> {
            DUP2
            IOR
            DUP_X2
            POP
            IAND
            ISUB
        }

        1 -> {
            DUP2
            ICONST_M1
            IXOR
            IAND
            DUP_X2
            POP
            SWAP
            ICONST_M1
            IXOR
            IAND
            IOR
        }

        2 -> {
            DUP2
            IOR
            DUP_X2
            POP
            ICONST_M1
            IXOR
            SWAP
            ICONST_M1
            IXOR
            IOR
            IAND
        }

        else -> {
            DUP2
            IOR
            DUP_X2
            POP
            IAND
            ICONST_M1
            IXOR
            IAND
        }
    }
}