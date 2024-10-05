package net.spartanb312.genesis.extensions.insn

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.extensions.BuilderDSL
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnNode

/**
 * Integer arithmetic (consume 2 produce 1)
 * I, I -> I
 */
@BuilderDSL
inline val InsnListBuilder.IADD get() = +InsnNode(Opcodes.IADD)

@BuilderDSL
inline val InsnListBuilder.ISUB get() = +InsnNode(Opcodes.ISUB)

@BuilderDSL
inline val InsnListBuilder.IMUL get() = +InsnNode(Opcodes.IMUL)

@BuilderDSL
inline val InsnListBuilder.IDIV get() = +InsnNode(Opcodes.IDIV)

@BuilderDSL
inline val InsnListBuilder.IREM get() = +InsnNode(Opcodes.IREM)

/**
 * Integer arithmetic (consume 1 produce 1)
 * I, I -> I
 */
@BuilderDSL
inline val InsnListBuilder.INEG get() = +InsnNode(Opcodes.INEG)

/**
 * Integer bits operation
 * I, I -> I
 */
@BuilderDSL
inline val InsnListBuilder.ISHL get() = +InsnNode(Opcodes.ISHL)

@BuilderDSL
inline val InsnListBuilder.ISHR get() = +InsnNode(Opcodes.ISHR)

@BuilderDSL
inline val InsnListBuilder.IUSHR get() = +InsnNode(Opcodes.IUSHR)

/**
 * Integer bitwise
 * I, I -> I
 */
@BuilderDSL
inline val InsnListBuilder.IAND get() = +InsnNode(Opcodes.IAND)

@BuilderDSL
inline val InsnListBuilder.IOR get() = +InsnNode(Opcodes.IOR)

@BuilderDSL
inline val InsnListBuilder.IXOR get() = +InsnNode(Opcodes.IXOR)

/**
 * Long arithmetic (consume 2 produce 1)
 * J, J -> J
 */
@BuilderDSL
inline val InsnListBuilder.LADD get() = +InsnNode(Opcodes.LADD)

@BuilderDSL
inline val InsnListBuilder.LSUB get() = +InsnNode(Opcodes.LSUB)

@BuilderDSL
inline val InsnListBuilder.LMUL get() = +InsnNode(Opcodes.LMUL)

@BuilderDSL
inline val InsnListBuilder.LDIV get() = +InsnNode(Opcodes.LDIV)

@BuilderDSL
inline val InsnListBuilder.LREM get() = +InsnNode(Opcodes.LREM)

/**
 * Long arithmetic (consume 1 produce 1)
 * J, J -> J
 */
@BuilderDSL
inline val InsnListBuilder.LNEG get() = +InsnNode(Opcodes.LNEG)

/**
 * Long bits operation
 * J, I -> J
 */
@BuilderDSL
inline val InsnListBuilder.LSHL get() = +InsnNode(Opcodes.LSHL)

@BuilderDSL
inline val InsnListBuilder.LSHR get() = +InsnNode(Opcodes.LSHR)

@BuilderDSL
inline val InsnListBuilder.LUSHR get() = +InsnNode(Opcodes.LUSHR)

/**
 * Long bitwise
 * J, J -> J
 */
@BuilderDSL
inline val InsnListBuilder.LAND get() = +InsnNode(Opcodes.LAND)

@BuilderDSL
inline val InsnListBuilder.LOR get() = +InsnNode(Opcodes.LOR)

@BuilderDSL
inline val InsnListBuilder.LXOR get() = +InsnNode(Opcodes.LXOR)

/**
 * Float arithmetic (consume 2 produce 1)
 * F, F -> F
 */
@BuilderDSL
inline val InsnListBuilder.FADD get() = +InsnNode(Opcodes.FADD)

@BuilderDSL
inline val InsnListBuilder.FSUB get() = +InsnNode(Opcodes.FSUB)

@BuilderDSL
inline val InsnListBuilder.FMUL get() = +InsnNode(Opcodes.FMUL)

@BuilderDSL
inline val InsnListBuilder.FDIV get() = +InsnNode(Opcodes.FDIV)

@BuilderDSL
inline val InsnListBuilder.FREM get() = +InsnNode(Opcodes.FREM)

/**
 * Float arithmetic (consume 1 produce 1)
 * F, F -> F
 */
@BuilderDSL
inline val InsnListBuilder.FNEG get() = +InsnNode(Opcodes.FNEG)

/**
 * Double arithmetic (consume 2 produce 1)
 * D, D -> D
 */
@BuilderDSL
inline val InsnListBuilder.DADD get() = +InsnNode(Opcodes.DADD)

@BuilderDSL
inline val InsnListBuilder.DSUB get() = +InsnNode(Opcodes.DSUB)

@BuilderDSL
inline val InsnListBuilder.DMUL get() = +InsnNode(Opcodes.DMUL)

@BuilderDSL
inline val InsnListBuilder.DDIV get() = +InsnNode(Opcodes.DDIV)

@BuilderDSL
inline val InsnListBuilder.DREM get() = +InsnNode(Opcodes.DREM)

/**
 * Double arithmetic (consume 1 produce 1)
 * D, D -> D
 */
@BuilderDSL
inline val InsnListBuilder.DNEG get() = +InsnNode(Opcodes.DNEG)

/**
 * Increment the integer in specified slot
 * ->
 */
@BuilderDSL
fun InsnListBuilder.IINC(slot: Int, amount: Int = 1) {
    +IincInsnNode(slot, amount)
}

/**
 * Compare two longs
 * J, J -> I
 */
@BuilderDSL
inline val InsnListBuilder.LCMP get() = +InsnNode(Opcodes.LCMP)

/**
 * Compare two floats
 * F, F -> I
 */
@BuilderDSL
inline val InsnListBuilder.FCMPL get() = +InsnNode(Opcodes.FCMPL)

@BuilderDSL
inline val InsnListBuilder.FCMPG get() = +InsnNode(Opcodes.FCMPG)

/**
 * Compare two doubles
 * F, F -> I
 */
@BuilderDSL
inline val InsnListBuilder.DCMPL get() = +InsnNode(Opcodes.DCMPL)

@BuilderDSL
inline val InsnListBuilder.DCMPG get() = +InsnNode(Opcodes.DCMPG)

/**
 * Convert integer to short
 * I -> S
 */
@BuilderDSL
inline val InsnListBuilder.I2S get() = +InsnNode(Opcodes.I2S)

/**
 * Convert integer to char
 * I -> C
 */
@BuilderDSL
inline val InsnListBuilder.I2C get() = +InsnNode(Opcodes.I2C)

/**
 * Convert integer to byte
 * I -> B
 */
@BuilderDSL
inline val InsnListBuilder.I2B get() = +InsnNode(Opcodes.I2B)

/**
 * Convert integer to long
 * I -> J
 */
@BuilderDSL
inline val InsnListBuilder.I2L get() = +InsnNode(Opcodes.I2L)

/**
 * Convert integer to float
 * I -> F
 */
@BuilderDSL
inline val InsnListBuilder.I2F get() = +InsnNode(Opcodes.I2F)

/**
 * Convert integer to double
 * I -> D
 */
@BuilderDSL
inline val InsnListBuilder.I2D get() = +InsnNode(Opcodes.I2D)

/**
 * Convert long to integer
 * J -> I
 */
@BuilderDSL
inline val InsnListBuilder.L2I get() = +InsnNode(Opcodes.L2I)

/**
 * Convert long to float
 * J -> F
 */
@BuilderDSL
inline val InsnListBuilder.L2F get() = +InsnNode(Opcodes.L2F)

/**
 * Convert long to double
 * J -> D
 */
@BuilderDSL
inline val InsnListBuilder.L2D get() = +InsnNode(Opcodes.L2D)

/**
 * Convert float to integer
 * F -> I
 */
@BuilderDSL
inline val InsnListBuilder.F2I get() = +InsnNode(Opcodes.F2I)

/**
 * Convert float to long
 * F -> J
 */
@BuilderDSL
inline val InsnListBuilder.F2L get() = +InsnNode(Opcodes.F2L)

/**
 * Convert float to double
 * F -> D
 */
@BuilderDSL
inline val InsnListBuilder.F2D get() = +InsnNode(Opcodes.F2D)

/**
 * Convert double to integer
 * D -> I
 */
@BuilderDSL
inline val InsnListBuilder.D2I get() = +InsnNode(Opcodes.D2I)

/**
 * Convert double to long
 * D -> J
 */
@BuilderDSL
inline val InsnListBuilder.D2L get() = +InsnNode(Opcodes.D2L)

/**
 * Convert double to long
 * D -> F
 */
@BuilderDSL
inline val InsnListBuilder.D2F get() = +InsnNode(Opcodes.D2F)