package net.spartanb312.grunt.utils.extensions

import net.spartanb312.genesis.extensions.*
import net.spartanb312.grunt.process.resource.ResourceCache
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
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

inline val MethodNode.isPublic get() = access.isPublic

inline val MethodNode.isPrivate get() = access.isPrivate

inline val MethodNode.isProtected get() = access.isProtected

inline val MethodNode.isStatic get() = access.isStatic

inline val MethodNode.isNative get() = access.isNative

inline val MethodNode.isAbstract get() = access.isAbstract

inline val MethodNode.isMainMethod get() = name == "main" && desc == "([Ljava/lang/String;)V"

inline val MethodNode.isInitializer get() = name == "<init>" || name == "<clinit>"

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
    get() = !(visibleAnnotations.isNullOrEmpty() && invisibleAnnotations.isNullOrEmpty())

fun MethodNode.appendAnnotation(annotation: String): MethodNode {
    visitAnnotation(annotation, false)
    return this
}

fun MethodNode.removeAnnotation(annotation: String) {
    invisibleAnnotations?.toList()?.forEach {
        if (it.desc == annotation) invisibleAnnotations.remove(it)
    }
    visibleAnnotations?.toList()?.forEach {
        if (it.desc == annotation) visibleAnnotations.remove(it)
    }
}

fun MethodNode.hasAnnotation(desc: String): Boolean = findAnnotation(desc) != null

fun MethodNode.findAnnotation(desc: String): AnnotationNode? {
    return visibleAnnotations?.find { it.desc == desc } ?: invisibleAnnotations?.find { it.desc == desc }
}