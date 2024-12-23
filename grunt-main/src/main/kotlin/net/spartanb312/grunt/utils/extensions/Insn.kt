package net.spartanb312.grunt.utils.extensions

import net.spartanb312.genesis.kotlin.InsnListBuilder
import net.spartanb312.genesis.kotlin.extensions.BuilderDSL
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

inline val Int.isReturn inline get() = (this in Opcodes.IRETURN..Opcodes.RETURN)

inline val AbstractInsnNode.isIntInsn: Boolean
    get() = (opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5
            || opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH
            || (this is LdcInsnNode && this.cst is Int))

inline val AbstractInsnNode.isLongInsn: Boolean
    get() = opcode in Opcodes.LCONST_0..Opcodes.LCONST_1 || (this is LdcInsnNode && this.cst is Long)

inline val AbstractInsnNode.isFloatInsn: Boolean
    get() = opcode in Opcodes.FCONST_0..Opcodes.FCONST_2 || this is LdcInsnNode && this.cst is Float

inline val AbstractInsnNode.isDoubleInsn: Boolean
    get() = opcode in Opcodes.DCONST_0..Opcodes.DCONST_1 || this is LdcInsnNode && this.cst is Double

inline val AbstractInsnNode.isNotInstruction: Boolean get() = this is LineNumberNode || this is FrameNode || this is LabelNode || this.opcode == Opcodes.NOP

inline val AbstractInsnNode.isNumberInsn get() = isIntInsn || isLongInsn || isFloatInsn || isDoubleInsn

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

val AbstractInsnNode?.isDummy: Boolean get() = this?.opcode == 1919810

@BuilderDSL
val InsnListBuilder.DUMMY get() = +InsnNode(1919810)