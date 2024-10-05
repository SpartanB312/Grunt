package net.spartanb312.genesis.extensions

import net.spartanb312.genesis.InsnListBuilder
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode

fun Int.toInsnNode(): AbstractInsnNode = when (this) {
    in -1..5 -> InsnNode(this + 0x3)
    in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, this)
    in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, this)
    else -> LdcInsnNode(this)
}

fun Long.toInsnNode(): AbstractInsnNode = when (this) {
    0L -> InsnNode(Opcodes.LCONST_0)
    1L -> InsnNode(Opcodes.LCONST_1)
    else -> LdcInsnNode(this)
}

fun Float.toInsnNode(): AbstractInsnNode = when (this) {
    0F -> InsnNode(Opcodes.FCONST_0)
    1F -> InsnNode(Opcodes.FCONST_1)
    2F -> InsnNode(Opcodes.FCONST_2)
    else -> LdcInsnNode(this)
}

fun Double.toInsnNode(): AbstractInsnNode = when (this) {
    0.0 -> InsnNode(Opcodes.DCONST_0)
    1.0 -> InsnNode(Opcodes.DCONST_1)
    else -> LdcInsnNode(this)
}

@BuilderDSL
fun InsnListBuilder.INT(value: Int) = +value.toInsnNode()

@BuilderDSL
fun InsnListBuilder.LONG(value: Long) = +value.toInsnNode()

@BuilderDSL
fun InsnListBuilder.FLOAT(value: Float) = +value.toInsnNode()

@BuilderDSL
fun InsnListBuilder.DOUBLE(value: Double) = +value.toInsnNode()