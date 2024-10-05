package net.spartanb312.genesis.extensions.insn

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.extensions.BuilderDSL
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.TypeInsnNode

/**
 * No operation performed
 */
@BuilderDSL
inline val InsnListBuilder.NOP get() = +InsnNode(Opcodes.NOP)

/**
 * Pop the top element of the stack
 * A ->
 */
@BuilderDSL
inline val InsnListBuilder.POP get() = +InsnNode(Opcodes.POP)

/**
 * Pop the top 1 or 2 elements of the stack
 * A, A ->
 * J/D ->
 */
@BuilderDSL
inline val InsnListBuilder.POP2 get() = +InsnNode(Opcodes.POP2)

/**
 * Duplicate the top element of the stack.
 * A -> A, A
 */
@BuilderDSL
inline val InsnListBuilder.DUP get() = +InsnNode(Opcodes.DUP)

/**
 * Duplicate the top element of the stack and insert before the second one
 * Double or long are not allowed
 * A1, A2 -> A2, A1, A2
 */
@BuilderDSL
inline val InsnListBuilder.DUP_X1 get() = +InsnNode(Opcodes.DUP_X1)

/**
 * Duplicate the top element of the stack and insert as shown below
 * The top of the stack should not be a double or long
 * J/D, A2 -> A2, J/D, A2
 * A1, A2, A3 -> A3, A1, A2, A3
 */
@BuilderDSL
inline val InsnListBuilder.DUP_X2 get() = +InsnNode(Opcodes.DUP_X2)

/**
 * Duplicate the top 1 or 2 elements of the stack.
 * J/D -> J/D, J/D
 * A1, A2 -> A1, A2, A1, A2
 */
@BuilderDSL
inline val InsnListBuilder.DUP2 get() = +InsnNode(Opcodes.DUP2)

/**
 * Duplicate the top 1 or 2 elements of the stack and insert as shown below
 * The 2nd or 3rd element should not be a double or long
 * A1, J/D -> J/D, A1, J/D
 * A1, A2, A3 -> A2, A3, A1, A2, A3
 */
@BuilderDSL
inline val InsnListBuilder.DUP2_X1 get() = +InsnNode(Opcodes.DUP2_X1)

/**
 * Duplicate the top 1 or 2 elements of the stack and insert as shown below
 * J1/D1, J2/D2 -> J2/D2, J1/D1, J2/D2
 * A1, A2, J/D -> J/D, A1, A2, J/D
 * J/D, A1, A2 -> A1, A2, J/D, A1, A2
 * A1, A2, A3, A4 -> A3, A4, A1, A2, A3, A4
 */
@BuilderDSL
inline val InsnListBuilder.DUP2_X2 get() = +InsnNode(Opcodes.DUP2_X2)

/**
 * Swap the top 2 elements of the stack
 * A1, A2 -> A2, A1
 */
@BuilderDSL
inline val InsnListBuilder.SWAP get() = +InsnNode(Opcodes.SWAP)

/**
 * Create a new instance of specified type
 * -> A
 */
@BuilderDSL
fun InsnListBuilder.NEW(type: String) = +TypeInsnNode(Opcodes.NEW, type)

/**
 * Cast the top of the stack to specified type
 * A -> A
 */
@BuilderDSL
fun InsnListBuilder.CHECKCAST(type: String) = +TypeInsnNode(Opcodes.CHECKCAST, type)

/**
 * Check if the top of the stack is of the specified type
 * A -> I
 */
@BuilderDSL
fun InsnListBuilder.INSTANCEOF(type: String) = +TypeInsnNode(Opcodes.INSTANCEOF, type)

/**
 * Monitors enter/exit
 * A ->
 */
@BuilderDSL
inline val InsnListBuilder.MONITORENTER get() = +InsnNode(Opcodes.MONITORENTER)

@BuilderDSL
inline val InsnListBuilder.MONITOREXIT get() = +InsnNode(Opcodes.MONITOREXIT)