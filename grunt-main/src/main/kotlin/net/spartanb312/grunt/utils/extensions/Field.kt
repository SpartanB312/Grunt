package net.spartanb312.grunt.utils.extensions

import net.spartanb312.genesis.extensions.*
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode

inline val FieldNode.isStatic get() = access.isStatic

inline val FieldNode.isPrivate get() = access.isPrivate

inline val FieldNode.isSynchronized get() = access.isSynchronized

inline val FieldNode.isFinal get() = access.isFinal

inline val FieldNode.isAbstract get() = access.isAbstract

inline val FieldNode.isProtected get() = access.isProtected

inline val FieldNode.isPublic get() = access.isPublic

inline val FieldNode.hasAnnotations: Boolean
    get() = visibleAnnotations != null && visibleAnnotations.isNotEmpty() || invisibleAnnotations != null && invisibleAnnotations.isNotEmpty()

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