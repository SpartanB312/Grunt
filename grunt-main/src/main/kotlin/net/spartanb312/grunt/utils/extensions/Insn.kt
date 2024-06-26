package net.spartanb312.grunt.utils.extensions

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

fun MethodInsnNode.match(owner: String, name: String, desc: String): Boolean {
    return this.owner == owner && this.name == name && this.desc == desc
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
