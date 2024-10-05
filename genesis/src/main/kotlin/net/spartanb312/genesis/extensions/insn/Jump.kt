package net.spartanb312.genesis.extensions.insn

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.extensions.BuilderDSL
import net.spartanb312.genesis.extensions.node
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * Unconditional jump
 * ->
 */
@BuilderDSL
fun InsnListBuilder.GOTO(target: LabelNode) = +JumpInsnNode(Opcodes.GOTO, target)

@BuilderDSL
fun InsnListBuilder.GOTO(target: Label) = +JumpInsnNode(Opcodes.GOTO, target.node)

/**
 * Conditional jump. Consume 1 object
 * A ->
 */
@BuilderDSL
fun InsnListBuilder.IFNULL(target: LabelNode) = +JumpInsnNode(Opcodes.IFNULL, target)

@BuilderDSL
fun InsnListBuilder.IFNULL(target: Label) = +JumpInsnNode(Opcodes.IFNULL, target.node)

@BuilderDSL
fun InsnListBuilder.IFNONNULL(target: LabelNode) = +JumpInsnNode(Opcodes.IFNONNULL, target)

@BuilderDSL
fun InsnListBuilder.IFNONNULL(target: Label) = +JumpInsnNode(Opcodes.IFNONNULL, target.node)

/**
 * Conditional jump. Consume 1 integer
 * I ->
 */
@BuilderDSL
fun InsnListBuilder.IFEQ(target: LabelNode) = +JumpInsnNode(Opcodes.IFEQ, target)

@BuilderDSL
fun InsnListBuilder.IFEQ(target: Label) = +JumpInsnNode(Opcodes.IFEQ, target.node)

@BuilderDSL
fun InsnListBuilder.IFNE(target: LabelNode) = +JumpInsnNode(Opcodes.IFNE, target)

@BuilderDSL
fun InsnListBuilder.IFNE(target: Label) = +JumpInsnNode(Opcodes.IFNE, target.node)

@BuilderDSL
fun InsnListBuilder.IFLT(target: LabelNode) = +JumpInsnNode(Opcodes.IFLT, target)

@BuilderDSL
fun InsnListBuilder.IFLT(target: Label) = +JumpInsnNode(Opcodes.IFLT, target.node)

@BuilderDSL
fun InsnListBuilder.IFGE(target: LabelNode) = +JumpInsnNode(Opcodes.IFGE, target)

@BuilderDSL
fun InsnListBuilder.IFGE(target: Label) = +JumpInsnNode(Opcodes.IFGE, target.node)

@BuilderDSL
fun InsnListBuilder.IFGT(target: LabelNode) = +JumpInsnNode(Opcodes.IFGT, target)

@BuilderDSL
fun InsnListBuilder.IFGT(target: Label) = +JumpInsnNode(Opcodes.IFGT, target.node)

@BuilderDSL
fun InsnListBuilder.IFLE(target: LabelNode) = +JumpInsnNode(Opcodes.IFLE, target)

@BuilderDSL
fun InsnListBuilder.IFLE(target: Label) = +JumpInsnNode(Opcodes.IFLE, target.node)

/**
 * Conditional jump. Consume 2 integer
 * I, I ->
 */
@BuilderDSL
fun InsnListBuilder.IF_ICMPEQ(target: LabelNode) = +JumpInsnNode(Opcodes.IF_ICMPEQ, target)

@BuilderDSL
fun InsnListBuilder.IF_ICMPEQ(target: Label) = +JumpInsnNode(Opcodes.IF_ICMPEQ, target.node)

@BuilderDSL
fun InsnListBuilder.IF_ICMPNE(target: LabelNode) = +JumpInsnNode(Opcodes.IF_ICMPNE, target)

@BuilderDSL
fun InsnListBuilder.IF_ICMPNE(target: Label) = +JumpInsnNode(Opcodes.IF_ICMPNE, target.node)

@BuilderDSL
fun InsnListBuilder.IF_ICMPLT(target: LabelNode) = +JumpInsnNode(Opcodes.IF_ICMPLT, target)

@BuilderDSL
fun InsnListBuilder.IF_ICMPLT(target: Label) = +JumpInsnNode(Opcodes.IF_ICMPLT, target.node)

@BuilderDSL
fun InsnListBuilder.IF_ICMPGE(target: LabelNode) = +JumpInsnNode(Opcodes.IF_ICMPGE, target)

@BuilderDSL
fun InsnListBuilder.IF_ICMPGE(target: Label) = +JumpInsnNode(Opcodes.IF_ICMPGE, target.node)

@BuilderDSL
fun InsnListBuilder.IF_ICMPGT(target: LabelNode) = +JumpInsnNode(Opcodes.IF_ICMPGT, target)

@BuilderDSL
fun InsnListBuilder.IF_ICMPGT(target: Label) = +JumpInsnNode(Opcodes.IF_ICMPGT, target.node)

@BuilderDSL
fun InsnListBuilder.IF_ICMPLE(target: LabelNode) = +JumpInsnNode(Opcodes.IF_ICMPLE, target)

@BuilderDSL
fun InsnListBuilder.IF_ICMPLE(target: Label) = +JumpInsnNode(Opcodes.IF_ICMPLE, target.node)

/**
 * Conditional jump. Consume 2 object
 * I, I ->
 */
@BuilderDSL
fun InsnListBuilder.IF_ACMPEQ(target: LabelNode) = +JumpInsnNode(Opcodes.IF_ACMPEQ, target)

@BuilderDSL
fun InsnListBuilder.IF_ACMPEQ(target: Label) = +JumpInsnNode(Opcodes.IF_ACMPEQ, target.node)

@BuilderDSL
fun InsnListBuilder.IF_ACMPNE(target: LabelNode) = +JumpInsnNode(Opcodes.IF_ACMPNE, target)

@BuilderDSL
fun InsnListBuilder.IF_ACMPNE(target: Label) = +JumpInsnNode(Opcodes.IF_ACMPNE, target.node)

/**
 * Jump to a subroutine at specified label and produce an address to the stack
 * -> (address)
 */
@BuilderDSL
fun InsnListBuilder.JSR(target: LabelNode) = +JumpInsnNode(Opcodes.JSR, target)

@BuilderDSL
fun InsnListBuilder.JSR(target: Label) = +JumpInsnNode(Opcodes.JSR, target.node)

/**
 * Return from a subroutine
 * ->
 */
@BuilderDSL
fun InsnListBuilder.RET(slot: Int) = +VarInsnNode(Opcodes.RET, slot)

/**
 * Return values
 * I/J/F/D/A ->
 */
@BuilderDSL
inline val InsnListBuilder.IRETURN get() = +InsnNode(Opcodes.IRETURN)

@BuilderDSL
inline val InsnListBuilder.LRETURN get() = +InsnNode(Opcodes.LRETURN)

@BuilderDSL
inline val InsnListBuilder.FRETURN get() = +InsnNode(Opcodes.FRETURN)

@BuilderDSL
inline val InsnListBuilder.DRETURN get() = +InsnNode(Opcodes.DRETURN)

@BuilderDSL
inline val InsnListBuilder.ARETURN get() = +InsnNode(Opcodes.ARETURN)

/**
 * Return from a method
 */
@BuilderDSL
inline val InsnListBuilder.RETURN get() = +InsnNode(Opcodes.RETURN)

/**
 * Throw an exception
 * A ->
 */
@BuilderDSL
inline val InsnListBuilder.ATHROW get() = +InsnNode(Opcodes.ATHROW)