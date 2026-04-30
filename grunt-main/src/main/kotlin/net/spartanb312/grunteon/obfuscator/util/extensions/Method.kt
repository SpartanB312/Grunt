package net.spartanb312.grunteon.obfuscator.util.extensions

import net.spartanb312.genesis.kotlin.MethodBuilder
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.genesis.kotlin.modify
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

val starts = listOf("Z", "B", "C", "S", "I", "L", "F", "D")

fun getParameterSizeFromDesc(descriptor: String): Int {
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

fun methodFullDesc(owner: ClassNode, method: MethodNode): String = "${owner.name}.${method.name}${method.desc}"

inline val MethodNode.isPublic get() = access.isPublic

inline val MethodNode.isPrivate get() = access.isPrivate

inline val MethodNode.isProtected get() = access.isProtected

inline val MethodNode.isStatic get() = access.isStatic

inline val MethodNode.isNative get() = access.isNative

inline val MethodNode.isAbstract get() = access.isAbstract

inline val MethodNode.isSynthetic get() = access.isSynthetic

inline val MethodNode.isBridge get() = access intersects Opcodes.ACC_BRIDGE

inline val MethodNode.isMainMethod get() = name == "main" && desc == "([Ljava/lang/String;)V"

inline val MethodNode.isInitializer get() = name == "<init>" || name == "<clinit>"

val MethodNode.hasAnnotations: Boolean
    get() = !(visibleAnnotations.isNullOrEmpty() && invisibleAnnotations.isNullOrEmpty())

fun MethodNode.appendAnnotation(annotation: String): MethodNode {
    visitAnnotation(annotation, false)
    return this
}

fun MethodNode.removeAnnotation(annotation: String) {
    invisibleAnnotations?.removeIf {
        it.desc == annotation
    }
    visibleAnnotations?.removeIf {
        it.desc == annotation
    }
}

fun MethodNode.removeAnnotations(annotations: Set<String>) {
    invisibleAnnotations?.removeIf {
        it.desc in annotations
    }
    visibleAnnotations?.removeIf {
        it.desc in annotations
    }
}

fun MethodNode.hasAnnotation(desc: String): Boolean = findAnnotation(desc) != null

fun MethodNode.findAnnotation(desc: String): AnnotationNode? {
    return visibleAnnotations?.find { it.desc == desc } ?: invisibleAnnotations?.find { it.desc == desc }
}

// TODO: migrate to genesis
fun init(block: (MethodBuilder.() -> Unit)? = null): MethodNode = method(
    PUBLIC,
    "<init>",
    "()V", null,
    null,
).apply { if (block != null) modify(block) }