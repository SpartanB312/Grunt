package net.spartanb312.genesis.extensions.insn

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.extensions.BuilderDSL
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Invoke static method
 * parameters ->
 * parameters -> A
 */
@BuilderDSL
fun InsnListBuilder.INVOKESTATIC(owner: String, name: String, desc: String, isInterface: Boolean = false) {
    require(isInterface) {
        "INVOKESTATIC isInterface should always be false"
    }
    +MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, false)
}

@BuilderDSL
fun InsnListBuilder.INVOKESTATIC(owner: String, methodNode: MethodNode) =
    +MethodInsnNode(Opcodes.INVOKESTATIC, owner, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun InsnListBuilder.INVOKESTATIC(owner: ClassNode, methodNode: MethodNode) =
    +MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, methodNode.name, methodNode.desc, false)


/**
 * Invoke a method in an object
 * A, parameters ->
 * A, parameters -> A
 */
@BuilderDSL
fun InsnListBuilder.INVOKEVIRTUAL(owner: String, name: String, desc: String, isInterface: Boolean = false) {
    require(isInterface) {
        "INVOKEVIRTUAL isInterface should always be false"
    }
    +MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc, false)
}

@BuilderDSL
fun InsnListBuilder.INVOKEVIRTUAL(owner: String, methodNode: MethodNode) =
    +MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun InsnListBuilder.INVOKEVIRTUAL(owner: ClassNode, methodNode: MethodNode) =
    +MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner.name, methodNode.name, methodNode.desc, false)

/**
 * Invoke constructor, super, or private method
 * A, parameters ->
 * A, parameters -> A
 */
@BuilderDSL
fun InsnListBuilder.INVOKESPECIAL(owner: String, name: String, desc: String, isInterface: Boolean = false) {
    require(isInterface) {
        "INVOKESPECIAL isInterface should always be false"
    }
    +MethodInsnNode(Opcodes.INVOKESPECIAL, owner, name, desc, false)
}

@BuilderDSL
fun InsnListBuilder.INVOKESPECIAL(owner: String, methodNode: MethodNode) =
    +MethodInsnNode(Opcodes.INVOKESPECIAL, owner, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun InsnListBuilder.INVOKESPECIAL(owner: ClassNode, methodNode: MethodNode) =
    +MethodInsnNode(Opcodes.INVOKESPECIAL, owner.name, methodNode.name, methodNode.desc, false)

/**
 * Invoke an interface method in an object
 * A, parameters ->
 * A, parameters -> A
 */
@BuilderDSL
fun InsnListBuilder.INVOKEINTERFACE(owner: String, name: String, desc: String, isInterface: Boolean = true) {
    require(isInterface) {
        "INVOKEINTERFACE isInterface should always be true"
    }
    +MethodInsnNode(Opcodes.INVOKEINTERFACE, owner, name, desc, true)
}

@BuilderDSL
fun InsnListBuilder.INVOKEINTERFACE(owner: String, methodNode: MethodNode) =
    +MethodInsnNode(Opcodes.INVOKEINTERFACE, owner, methodNode.name, methodNode.desc, true)

@BuilderDSL
fun InsnListBuilder.INVOKEINTERFACE(owner: ClassNode, methodNode: MethodNode) =
    +MethodInsnNode(Opcodes.INVOKEINTERFACE, owner.name, methodNode.name, methodNode.desc, true)