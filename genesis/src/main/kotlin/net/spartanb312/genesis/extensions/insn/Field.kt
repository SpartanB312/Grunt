package net.spartanb312.genesis.extensions.insn

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.extensions.BuilderDSL
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode

/**
 * Get/put stuff from/to a field
 * -> A
 */
@BuilderDSL
fun InsnListBuilder.GETSTATIC(owner: String, name: String, desc: String) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.GETSTATIC, owner, name, desc)

@BuilderDSL
fun InsnListBuilder.PUTSTATIC(owner: String, name: String, desc: String) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.PUTSTATIC, owner, name, desc)

@BuilderDSL
fun InsnListBuilder.GETSTATIC(owner: String, fieldNode: FieldNode) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.GETSTATIC, owner, fieldNode.name, fieldNode.desc)

@BuilderDSL
fun InsnListBuilder.PUTSTATIC(owner: String, fieldNode: FieldNode) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.PUTSTATIC, owner, fieldNode.name, fieldNode.desc)

@BuilderDSL
fun InsnListBuilder.GETSTATIC(owner: ClassNode, fieldNode: FieldNode) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.GETSTATIC, owner.name, fieldNode.name, fieldNode.desc)

@BuilderDSL
fun InsnListBuilder.PUTSTATIC(owner: ClassNode, fieldNode: FieldNode) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.PUTSTATIC, owner.name, fieldNode.name, fieldNode.desc)

/**
 * Get/put stuff from/to a field in an object
 * A -> A
 */
@BuilderDSL
fun InsnListBuilder.GETFIELD(owner: String, name: String, desc: String) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.GETFIELD, owner, name, desc)

@BuilderDSL
fun InsnListBuilder.PUTFIELD(owner: String, name: String, desc: String) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.PUTFIELD, owner, name, desc)

@BuilderDSL
fun InsnListBuilder.GETFIELD(owner: String, fieldNode: FieldNode) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.GETFIELD, owner, fieldNode.name, fieldNode.desc)

@BuilderDSL
fun InsnListBuilder.PUTFIELD(owner: String, fieldNode: FieldNode) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.PUTFIELD, owner, fieldNode.name, fieldNode.desc)

@BuilderDSL
fun InsnListBuilder.GETFIELD(owner: ClassNode, fieldNode: FieldNode) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.GETFIELD, owner.name, fieldNode.name, fieldNode.desc)

@BuilderDSL
fun InsnListBuilder.PUTFIELD(owner: ClassNode, fieldNode: FieldNode) =
    +FieldInsnNode(org.objectweb.asm.Opcodes.PUTFIELD, owner.name, fieldNode.name, fieldNode.desc)