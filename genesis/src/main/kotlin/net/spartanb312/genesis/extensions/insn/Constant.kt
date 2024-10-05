package net.spartanb312.genesis.extensions.insn

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.extensions.BuilderDSL
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode

/**
 * Push null to the stack
 * -> null
 */
@BuilderDSL
inline val InsnListBuilder.ACONST_NULL get() = +InsnNode(Opcodes.ACONST_NULL)

/**
 * Push integer const [-1, 5] to the stack
 * -> I
 */
@BuilderDSL
inline val InsnListBuilder.ICONST_M1 get() = +InsnNode(Opcodes.ICONST_M1)

@BuilderDSL
inline val InsnListBuilder.ICONST_0 get() = +InsnNode(Opcodes.ICONST_0)

@BuilderDSL
inline val InsnListBuilder.ICONST_1 get() = +InsnNode(Opcodes.ICONST_1)

@BuilderDSL
inline val InsnListBuilder.ICONST_2 get() = +InsnNode(Opcodes.ICONST_2)

@BuilderDSL
inline val InsnListBuilder.ICONST_3 get() = +InsnNode(Opcodes.ICONST_3)

@BuilderDSL
inline val InsnListBuilder.ICONST_4 get() = +InsnNode(Opcodes.ICONST_4)

@BuilderDSL
inline val InsnListBuilder.ICONST_5 get() = +InsnNode(Opcodes.ICONST_5)

/**
 * Push long const [0, 1] to the stack
 * -> J
 */
@BuilderDSL
inline val InsnListBuilder.LCONST_0 get() = +InsnNode(Opcodes.LCONST_0)

@BuilderDSL
inline val InsnListBuilder.LCONST_1 get() = +InsnNode(Opcodes.LCONST_1)

/**
 * Push float const {0f, 1f, 2f} to the stack
 * -> F
 */
@BuilderDSL
inline val InsnListBuilder.FCONST_0 get() = +InsnNode(Opcodes.FCONST_0)

@BuilderDSL
inline val InsnListBuilder.FCONST_1 get() = +InsnNode(Opcodes.FCONST_1)

@BuilderDSL
inline val InsnListBuilder.FCONST_2 get() = +InsnNode(Opcodes.FCONST_2)

/**
 * Push double const {0.0, 1.0} to the stack
 * -> D
 */
@BuilderDSL
inline val InsnListBuilder.DCONST_0 get() = +InsnNode(Opcodes.DCONST_0)

@BuilderDSL
inline val InsnListBuilder.DCONST_1 get() = +InsnNode(Opcodes.DCONST_1)

/**
 * Push 1 byte integer [-128, 127] to the stack
 * -> I
 */
@BuilderDSL
fun InsnListBuilder.BIPUSH(value: Int) {
    require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) {
        "BIPUSH value must be in range [${Byte.MIN_VALUE}..${Byte.MAX_VALUE}]], but received $value"
    }
    +IntInsnNode(Opcodes.BIPUSH, value)
}

/**
 * Push short integer [-32768, 32767] to the stack
 * -> I
 */
@BuilderDSL
fun InsnListBuilder.SIPUSH(value: Int) {
    require(value in Short.MIN_VALUE..Short.MAX_VALUE) {
        "SIPUSH value must be in range [${Short.MIN_VALUE}..${Short.MAX_VALUE}]], but received $value"
    }
    +IntInsnNode(Opcodes.SIPUSH, value)
}

/**
 * Push a constant number to the stack
 * -> I/J/F/D
 */
@BuilderDSL
fun InsnListBuilder.LDC(value: Number) = +LdcInsnNode(value)

/**
 * Push a constant string to the stack
 * -> Ljava/lang/String;
 */
@BuilderDSL
fun InsnListBuilder.LDC(string: String) = +LdcInsnNode(string)

/**
 * Push constant class to the stack
 * -> Ljava/lang/Class;
 */
@BuilderDSL
fun InsnListBuilder.LDC(type: Type) = +LdcInsnNode(type)

@BuilderDSL
fun InsnListBuilder.LDC_TYPE(typeDesc: String, isArray: Boolean = false) {
    +LdcInsnNode(Type.getType(if (isArray) "[" else "" + typeDesc))
}

