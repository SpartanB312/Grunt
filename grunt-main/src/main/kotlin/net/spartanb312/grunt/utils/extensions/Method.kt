@file:Suppress("NOTHING_TO_INLINE")

package net.spartanb312.grunt.utils.extensions

import net.spartanb312.grunt.process.resource.ResourceCache
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.lang.reflect.Modifier

inline fun ClassNode.methodNode(
    access: Int = Opcodes.ASM9,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<String>?,
    block: MethodNode.() -> Unit,
): MethodNode {
    return MethodNode(access, name, descriptor, signature, exceptions).apply {
        visitCode()
        block()
        visitEnd()
    }
}

val starts = listOf("Z", "B", "C", "S", "I", "L", "F", "D")

inline fun getParameterSizeFromDesc(descriptor: String): Int {
    val list = descriptor.substringAfter("(").substringBefore(")").split(";")
    var count = 0
    for (s in list) {
        var current = s
        whileLoop@ while (true) {
            if (current == "") break@whileLoop
            current = current.removePrefix("[")
            val index = starts.indexOf(current[0].toString())
            if (index != -1) {
                count++
                current = current.removePrefix(starts[index])
            } else break@whileLoop
        }
    }
    return count
}

inline fun MethodNode.setPublic() {
    if (isPublic) return
    if (isPrivate) {
        access = access and Opcodes.ACC_PRIVATE.inv()
        access = access or Opcodes.ACC_PUBLIC
    } else if (isProtected) {
        access = access and Opcodes.ACC_PROTECTED.inv()
        access = access or Opcodes.ACC_PUBLIC
    } else access = access or Opcodes.ACC_PUBLIC
}

inline val MethodNode.isPublic get() = Modifier.isPublic(access)

inline val MethodNode.isPrivate get() = Modifier.isPrivate(access)

inline val MethodNode.isProtected get() = Modifier.isProtected(access)

inline val MethodNode.isStatic get() = Modifier.isStatic(access)

inline val MethodNode.isNative get() = Modifier.isNative(access)

inline val MethodNode.isAbstract get() = Modifier.isAbstract(access)

inline val MethodNode.isMainMethod get() = (name == "main") && (desc == "([Ljava/lang/String;)V")

inline val MethodNode.isInitializer get() = (name == "<init>") || (name == "<clinit>")

inline val Int.isReturn inline get() = (this in Opcodes.IRETURN..Opcodes.RETURN)

inline fun MethodNode.addTo(methods: MutableList<MethodNode>): MethodNode = this.apply { methods.add(this) }

inline fun MethodNode.visit(): MethodNode = this.apply { this.visitCode() }

inline fun MethodNode.end(): MethodNode = this.apply { this.visitEnd() }

inline fun MethodNode.RETURN(): MethodNode = this.apply { this.visitInsn(Opcodes.RETURN) }

inline val AbstractInsnNode.isIntInsn: Boolean
    get() {
        val opcode = this.opcode
        return (opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5
                || opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH || (this is LdcInsnNode
                && this.cst is Int))
    }

inline val AbstractInsnNode.isLongInsn: Boolean
    get() {
        val opcode = this.opcode
        return opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1 || (this is LdcInsnNode
                && this.cst is Long)
    }

inline val AbstractInsnNode.isFloatInsn: Boolean
    get() {
        val opcode = this.opcode
        return opcode in Opcodes.FCONST_0..Opcodes.FCONST_2 || this is LdcInsnNode && this.cst is Float
    }

inline val AbstractInsnNode.isDoubleInsn: Boolean
    get() {
        val opcode = this.opcode
        return opcode in Opcodes.DCONST_0..Opcodes.DCONST_1 || this is LdcInsnNode && this.cst is Double
    }

inline val AbstractInsnNode.isNotInstruction: Boolean get() = this is LineNumberNode || this is FrameNode || this is LabelNode || this.opcode == Opcodes.NOP

inline val AbstractInsnNode.isNumberInsn get() = isIntInsn or isLongInsn or isFloatInsn or isDoubleInsn

fun MethodInsnNode.getCallingMethodNode(resourceCache: ResourceCache): MethodNode? {
    val ownerNode = resourceCache.getClassNode(owner) ?: return null
    return ownerNode.methods.find { it.name == name && it.desc == desc }
}

fun MethodInsnNode.getCallingMethodNodeAndOwner(resourceCache: ResourceCache): Pair<ClassNode, MethodNode>? {
    val ownerNode = resourceCache.getClassNode(owner) ?: return null
    val methodNode = ownerNode.methods.find { it.name == name && it.desc == desc } ?: return null
    return ownerNode to methodNode
}

val MethodNode.hasAnnotations: Boolean
    get() {
        return !(visibleAnnotations.isNullOrEmpty() && invisibleAnnotations.isNullOrEmpty())
    }