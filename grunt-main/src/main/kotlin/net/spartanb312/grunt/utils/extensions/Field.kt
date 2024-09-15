@file:Suppress("NOTHING_TO_INLINE")

package net.spartanb312.grunt.utils.extensions

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import java.lang.reflect.Modifier

inline fun FieldNode.setStringVal(value: String): String = value.also {
    if (this.value is String) this.value = it
}

inline fun FieldNode.setPublic() {
    if (isPublic) return
    if (isPrivate) {
        access = access and Opcodes.ACC_PRIVATE.inv()
        access = access or Opcodes.ACC_PUBLIC
    } else if (isProtected) {
        access = access and Opcodes.ACC_PROTECTED.inv()
        access = access or Opcodes.ACC_PUBLIC
    } else access = access or Opcodes.ACC_PUBLIC
}

inline val FieldNode.isStatic get() = Modifier.isStatic(access)

inline val FieldNode.isPrivate get() = Modifier.isPrivate(access)

inline val FieldNode.isSynchronized get() = Modifier.isSynchronized(access)

inline val FieldNode.isFinal get() = Modifier.isFinal(access)

inline val FieldNode.isAbstract get() = Modifier.isAbstract(access)

inline val FieldNode.isProtected get() = Modifier.isProtected(access)

inline val FieldNode.isPublic get() = Modifier.isPublic(access)

inline val FieldNode.hasAnnotations: Boolean
    get() {
        return visibleAnnotations != null && visibleAnnotations.isNotEmpty()
                || invisibleAnnotations != null && invisibleAnnotations.isNotEmpty()
    }

fun FieldNode.appendAnnotation(annotation: String): FieldNode {
    visitAnnotation(annotation, false)
    return this
}

fun FieldNode.removeAnnotation(annotation: String) {
    invisibleAnnotations?.toList()?.forEach {
        if (it.desc == annotation) invisibleAnnotations.remove(it)
    }
    visibleAnnotations?.toList()?.forEach {
        if (it.desc == annotation) visibleAnnotations.remove(it)
    }
}

fun FieldNode.hasAnnotation(desc: String): Boolean = findAnnotation(desc) != null

fun FieldNode.findAnnotation(desc: String): AnnotationNode? {
    return visibleAnnotations?.find { it.desc == desc } ?: invisibleAnnotations?.find { it.desc == desc }
}