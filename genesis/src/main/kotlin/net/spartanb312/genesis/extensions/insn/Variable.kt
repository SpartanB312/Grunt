package net.spartanb312.genesis.extensions.insn

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.extensions.BuilderDSL
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.VarInsnNode

/**
 * Load a value from a slot
 * -> I/J/F/D/A
 */
@BuilderDSL
fun InsnListBuilder.ILOAD(slot: Int) = +VarInsnNode(Opcodes.ILOAD, slot)

@BuilderDSL
fun InsnListBuilder.LLOAD(slot: Int) = +VarInsnNode(Opcodes.LLOAD, slot)

@BuilderDSL
fun InsnListBuilder.FLOAD(slot: Int) = +VarInsnNode(Opcodes.FLOAD, slot)

@BuilderDSL
fun InsnListBuilder.DLOAD(slot: Int) = +VarInsnNode(Opcodes.DLOAD, slot)

@BuilderDSL
fun InsnListBuilder.ALOAD(slot: Int) = +VarInsnNode(Opcodes.ALOAD, slot)

/**
 * Store a value to a slot
 * I/J/F/D/A ->
 */
@BuilderDSL
fun InsnListBuilder.ISTORE(slot: Int) = +VarInsnNode(Opcodes.ISTORE, slot)

@BuilderDSL
fun InsnListBuilder.LSTORE(slot: Int) = +VarInsnNode(Opcodes.LSTORE, slot)

@BuilderDSL
fun InsnListBuilder.FSTORE(slot: Int) = +VarInsnNode(Opcodes.FSTORE, slot)

@BuilderDSL
fun InsnListBuilder.DSTORE(slot: Int) = +VarInsnNode(Opcodes.DSTORE, slot)

@BuilderDSL
fun InsnListBuilder.ASTORE(slot: Int) = +VarInsnNode(Opcodes.ASTORE, slot)