package net.spartanb312.grunt.utils.builder

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode

fun invokeStatic(owner: String, name: String, desc: String, isInterface: Boolean = false) =
    MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, isInterface)

fun invokeVirtual(owner: String, name: String, desc: String, isInterface: Boolean = false) =
    MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc, isInterface)

fun invokeSpecial(owner: String, name: String, desc: String, isInterface: Boolean = false) =
    MethodInsnNode(Opcodes.INVOKESPECIAL, owner, name, desc, isInterface)

fun invokeInterface(owner: String, name: String, desc: String, isInterface: Boolean = false) =
    MethodInsnNode(Opcodes.INVOKEINTERFACE, owner, name, desc, isInterface)

fun invokeDynamic(
    name: String,
    descriptor: String,
    bootstrapMethodHandle: Handle,
    vararg bootstrapMethodArguments: Any
) = InvokeDynamicInsnNode(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments)