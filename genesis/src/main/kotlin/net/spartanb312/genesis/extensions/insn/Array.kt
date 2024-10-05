package net.spartanb312.genesis.extensions.insn

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.extensions.BuilderDSL
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TypeInsnNode

/**
 * Get @BuilderDSL value from array
 * A, I -> I/J/F/D/A
 */
@BuilderDSL
inline val InsnListBuilder.IALOAD get() = +InsnNode(Opcodes.IALOAD)

@BuilderDSL
inline val InsnListBuilder.SALOAD get() = +InsnNode(Opcodes.SALOAD)

@BuilderDSL
inline val InsnListBuilder.BALOAD get() = +InsnNode(Opcodes.BALOAD)

@BuilderDSL
inline val InsnListBuilder.CALOAD get() = +InsnNode(Opcodes.CALOAD)

@BuilderDSL
inline val InsnListBuilder.LALOAD get() = +InsnNode(Opcodes.LALOAD)

@BuilderDSL
inline val InsnListBuilder.FALOAD get() = +InsnNode(Opcodes.FALOAD)

@BuilderDSL
inline val InsnListBuilder.DALOAD get() = +InsnNode(Opcodes.DALOAD)

@BuilderDSL
inline val InsnListBuilder.AALOAD get() = +InsnNode(Opcodes.AALOAD)

/**
 * Store @BuilderDSL value to array
 * A, I -> I/J/F/D/A
 */
@BuilderDSL
inline val InsnListBuilder.IASTORE get() = +InsnNode(Opcodes.IASTORE)

@BuilderDSL
inline val InsnListBuilder.SASTORE get() = +InsnNode(Opcodes.SASTORE)

@BuilderDSL
inline val InsnListBuilder.BASTORE get() = +InsnNode(Opcodes.BASTORE)

@BuilderDSL
inline val InsnListBuilder.CASTORE get() = +InsnNode(Opcodes.CASTORE)

@BuilderDSL
inline val InsnListBuilder.LASTORE get() = +InsnNode(Opcodes.LASTORE)

@BuilderDSL
inline val InsnListBuilder.FASTORE get() = +InsnNode(Opcodes.FASTORE)

@BuilderDSL
inline val InsnListBuilder.DASTORE get() = +InsnNode(Opcodes.DASTORE)

@BuilderDSL
inline val InsnListBuilder.AASTORE get() = +InsnNode(Opcodes.AASTORE)

/**
 * Get array length
 * A -> I
 */
@BuilderDSL
inline val InsnListBuilder.ARRAYLENGTH get() = +InsnNode(Opcodes.ARRAYLENGTH)

/**
 * Create a new primitive array
 * I -> A
 */
@BuilderDSL
fun InsnListBuilder.NEWARRAY(type: Int) {
    require(type in Opcodes.T_CHAR..Opcodes.T_LONG) {
        "NEWARRAY's type should be one of primitive types, but received $type"
    }
    +IntInsnNode(Opcodes.NEWARRAY, type)
}

/**
 * Create a new object array
 * I -> A
 */
@BuilderDSL
fun InsnListBuilder.ANEWARRAY(desc: String) = +TypeInsnNode(Opcodes.ANEWARRAY, desc)

/**
 * Create a multi dimensional new object array
 * I -> A
 */
@BuilderDSL
fun InsnListBuilder.ANEWARRAY(desc: String, dimensions: Int) = +MultiANewArrayInsnNode(desc, dimensions)