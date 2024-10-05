package net.spartanb312.genesis.extensions.insn

import net.spartanb312.genesis.InsnListBuilder
import net.spartanb312.genesis.extensions.BuilderDSL
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Invokedynamic insn node builders
 */
@BuilderDSL
fun InsnListBuilder.INVOKEDYNAMIC(
    name: String,
    desc: String,
    bsmHandle: Handle,
    vararg bsmArgs: Any
) = +InvokeDynamicInsnNode(name, desc, bsmHandle, *bsmArgs)

@BuilderDSL
@JvmName("INVOKEDYNAMIC2")
fun InsnListBuilder.INVOKEDYNAMIC(
    name: String,
    desc: String,
    bsmHandle: Handle,
    bsmArgs: Array<out Any>,
) = +InvokeDynamicInsnNode(name, desc, bsmHandle, bsmArgs)

/**
 * Field handles
 */
@BuilderDSL
fun H_GETFIELD(
    owner: String,
    name: String,
    desc: String,
) = Handle(Opcodes.H_GETFIELD, owner, name, desc, false)

@BuilderDSL
fun H_GETSTATIC(
    owner: String,
    name: String,
    desc: String,
) = Handle(Opcodes.H_GETSTATIC, owner, name, desc, false)

@BuilderDSL
fun H_PUTFIELD(
    owner: String,
    name: String,
    desc: String,
) = Handle(Opcodes.H_PUTFIELD, owner, name, desc, false)

@BuilderDSL
fun H_PUTSTATIC(
    owner: String,
    name: String,
    desc: String,
) = Handle(Opcodes.H_PUTSTATIC, owner, name, desc, false)

@BuilderDSL
fun H_GETFIELD(fieldInsnNode: FieldInsnNode): Handle {
    require(fieldInsnNode.opcode == Opcodes.GETFIELD)
    return Handle(Opcodes.H_GETFIELD, fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc, false)
}

@BuilderDSL
fun H_GETSTATIC(fieldInsnNode: FieldInsnNode): Handle {
    require(fieldInsnNode.opcode == Opcodes.GETSTATIC)
    return Handle(Opcodes.H_GETSTATIC, fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc, false)
}

@BuilderDSL
fun H_PUTFIELD(fieldInsnNode: FieldInsnNode): Handle {
    require(fieldInsnNode.opcode == Opcodes.PUTFIELD)
    return Handle(Opcodes.H_PUTFIELD, fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc, false)
}

@BuilderDSL
fun H_PUTSTATIC(fieldInsnNode: FieldInsnNode): Handle {
    require(fieldInsnNode.opcode == Opcodes.PUTSTATIC)
    return Handle(Opcodes.H_PUTSTATIC, fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc, false)
}

@BuilderDSL
fun H_GETFIELD(
    owner: String,
    fieldNode: FieldNode,
) = Handle(Opcodes.H_GETFIELD, owner, fieldNode.name, fieldNode.desc, false)

@BuilderDSL
fun H_GETSTATIC(
    owner: String,
    fieldNode: FieldNode,
) = Handle(Opcodes.H_GETSTATIC, owner, fieldNode.name, fieldNode.desc, false)

@BuilderDSL
fun H_PUTFIELD(
    owner: String,
    fieldNode: FieldNode,
) = Handle(Opcodes.H_PUTFIELD, owner, fieldNode.name, fieldNode.desc, false)

@BuilderDSL
fun H_PUTSTATIC(
    owner: String,
    fieldNode: FieldNode,
) = Handle(Opcodes.H_PUTSTATIC, owner, fieldNode.name, fieldNode.desc, false)

@BuilderDSL
fun H_GETFIELD(
    owner: ClassNode,
    fieldNode: FieldNode,
) = Handle(Opcodes.H_GETFIELD, owner.name, fieldNode.name, fieldNode.desc, false)

@BuilderDSL
fun H_GETSTATIC(
    owner: ClassNode,
    fieldNode: FieldNode,
) = Handle(Opcodes.H_GETSTATIC, owner.name, fieldNode.name, fieldNode.desc, false)

@BuilderDSL
fun H_PUTFIELD(
    owner: ClassNode,
    fieldNode: FieldNode,
) = Handle(Opcodes.H_PUTFIELD, owner.name, fieldNode.name, fieldNode.desc, false)

@BuilderDSL
fun H_PUTSTATIC(
    owner: ClassNode,
    fieldNode: FieldNode,
) = Handle(Opcodes.H_PUTSTATIC, owner.name, fieldNode.name, fieldNode.desc, false)

/**
 * Method handles
 */
@BuilderDSL
fun H_INVOKEVIRTUAL(
    owner: String,
    name: String,
    desc: String,
) = Handle(Opcodes.H_INVOKEVIRTUAL, owner, name, desc, false)

@BuilderDSL
fun H_INVOKESTATIC(
    owner: String,
    name: String,
    desc: String,
) = Handle(Opcodes.H_INVOKESTATIC, owner, name, desc, false)

@BuilderDSL
fun H_INVOKESPECIAL(
    owner: String,
    name: String,
    desc: String,
) = Handle(Opcodes.H_INVOKESPECIAL, owner, name, desc, false)

@BuilderDSL
fun H_NEWINVOKESPECIAL(
    owner: String,
    name: String,
    desc: String,
) = Handle(Opcodes.H_NEWINVOKESPECIAL, owner, name, desc, false)

@BuilderDSL
fun H_INVOKEINTERFACE(
    owner: String,
    name: String,
    desc: String,
) = Handle(Opcodes.H_INVOKEINTERFACE, owner, name, desc, true)

@BuilderDSL
fun H_INVOKEVIRTUAL(
    methodInsnNode: MethodInsnNode
): Handle {
    require(methodInsnNode.opcode == Opcodes.INVOKEVIRTUAL)
    return Handle(Opcodes.H_INVOKEVIRTUAL, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, false)
}

@BuilderDSL
fun H_INVOKESTATIC(
    methodInsnNode: MethodInsnNode
): Handle {
    require(methodInsnNode.opcode == Opcodes.INVOKESTATIC)
    return Handle(Opcodes.H_INVOKESTATIC, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, false)
}

@BuilderDSL
fun H_INVOKESPECIAL(
    methodInsnNode: MethodInsnNode
): Handle {
    require(methodInsnNode.opcode == Opcodes.INVOKESPECIAL)
    return Handle(Opcodes.H_INVOKESPECIAL, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, false)
}

@BuilderDSL
fun H_NEWINVOKESPECIAL(
    methodInsnNode: MethodInsnNode
): Handle {
    require(methodInsnNode.opcode == Opcodes.INVOKESPECIAL)
    return Handle(Opcodes.H_NEWINVOKESPECIAL, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, false)
}

@BuilderDSL
fun H_INVOKEINTERFACE(methodInsnNode: MethodInsnNode): Handle {
    require(methodInsnNode.opcode == Opcodes.INVOKEINTERFACE)
    return Handle(Opcodes.H_INVOKEINTERFACE, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, true)
}

@BuilderDSL
fun H_INVOKEVIRTUAL(
    owner: String,
    methodNode: MethodNode,
) = Handle(Opcodes.H_INVOKEVIRTUAL, owner, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun H_INVOKESTATIC(
    owner: String,
    methodNode: MethodNode,
) = Handle(Opcodes.H_INVOKESTATIC, owner, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun H_INVOKESPECIAL(
    owner: String,
    methodNode: MethodNode,
) = Handle(Opcodes.H_INVOKESPECIAL, owner, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun H_NEWINVOKESPECIAL(
    owner: String,
    methodNode: MethodNode,
) = Handle(Opcodes.H_NEWINVOKESPECIAL, owner, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun H_INVOKEINTERFACE(
    owner: String,
    methodNode: MethodNode,
) = Handle(Opcodes.H_INVOKEINTERFACE, owner, methodNode.name, methodNode.desc, true)

@BuilderDSL
fun H_INVOKEVIRTUAL(
    owner: ClassNode,
    methodNode: MethodNode,
) = Handle(Opcodes.H_INVOKEVIRTUAL, owner.name, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun H_INVOKESTATIC(
    owner: ClassNode,
    methodNode: MethodNode,
) = Handle(Opcodes.H_INVOKESTATIC, owner.name, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun H_INVOKESPECIAL(
    owner: ClassNode,
    methodNode: MethodNode,
) = Handle(Opcodes.H_INVOKESPECIAL, owner.name, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun H_NEWINVOKESPECIAL(
    owner: ClassNode,
    methodNode: MethodNode,
) = Handle(Opcodes.H_NEWINVOKESPECIAL, owner.name, methodNode.name, methodNode.desc, false)

@BuilderDSL
fun H_INVOKEINTERFACE(
    owner: ClassNode,
    methodNode: MethodNode,
) = Handle(Opcodes.H_INVOKEINTERFACE, owner.name, methodNode.name, methodNode.desc, true)
