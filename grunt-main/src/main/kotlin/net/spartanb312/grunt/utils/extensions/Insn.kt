package net.spartanb312.grunt.utils.extensions

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

fun MethodInsnNode.match(owner: String, name: String, desc: String): Boolean {
    return this.owner == owner && this.name == name && this.desc == desc
}

fun AbstractInsnNode.getIntValue(): Int? {
    return when (opcode) {
        in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> opcode - 3
        Opcodes.BIPUSH -> (this as IntInsnNode).operand
        Opcodes.SIPUSH -> (this as IntInsnNode).operand
        Opcodes.LDC -> {
            val ldc = this as LdcInsnNode
            if (ldc.cst is Int) ldc.cst as Int else null
        }

        else -> null
    }
}

fun AbstractInsnNode.getLongValue(): Long? {
    return when (opcode) {
        in Opcodes.LCONST_0..Opcodes.LCONST_1 -> (opcode - 9).toLong()
        Opcodes.LDC -> {
            val ldc = this as LdcInsnNode
            if (ldc.cst is Long) ldc.cst as Long else null
        }

        else -> null
    }
}

fun AbstractInsnNode.getFloatValue(): Float? {
    return when (opcode) {
        in Opcodes.FCONST_0..Opcodes.FCONST_2 -> (opcode - 11).toFloat()
        Opcodes.LDC -> {
            val ldc = this as LdcInsnNode
            if (ldc.cst is Float) ldc.cst as Float else null
        }

        else -> null
    }
}

fun AbstractInsnNode.getDoubleValue(): Double? {
    return when (opcode) {
        in Opcodes.DCONST_0..Opcodes.DCONST_1 -> (opcode - 14).toDouble()
        Opcodes.LDC -> {
            val ldc = this as LdcInsnNode
            if (ldc.cst is Double) ldc.cst as Double else null
        }

        else -> null
    }
}

fun Int.toInsnNode(): AbstractInsnNode =
    when (this) {
        in -1..5 -> InsnNode(this + 0x3)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, this)
        in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, this)
        else -> LdcInsnNode(this)
    }

fun Long.toInsnNode(): AbstractInsnNode = if (this in 0..1) {
    InsnNode((this + 9).toInt())
} else {
    LdcInsnNode(this)
}

fun Float.toInsnNode(): AbstractInsnNode {
    return if (this in 0.0..2.0) {
        InsnNode((this + 11).toInt())
    } else {
        LdcInsnNode(this)
    }
}

fun Double.toInsnNode(): AbstractInsnNode {
    return if (this in 0.0..1.0)
        InsnNode((this + 14).toInt())
    else
        LdcInsnNode(this)
}

fun Int.getOpcodeInsn(): Int = when {
    this >= -1 && this <= 5 -> this + 0x3
    this >= Byte.MIN_VALUE && this <= Byte.MAX_VALUE -> Opcodes.BIPUSH
    this >= Short.MIN_VALUE && this <= Short.MAX_VALUE -> Opcodes.SIPUSH
    else -> throw RuntimeException("Expected value over -1 and under Short.MAX_VALUE")
}
