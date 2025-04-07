package net.spartanb312.grunt.utils.extensions

import net.spartanb312.genesis.kotlin.MethodBuilder
import net.spartanb312.genesis.kotlin.clinit
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.grunt.process.hierarchy.krypton.info.ClassInfo
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.lang.reflect.Modifier

inline val ClassNode.isPublic get() = access.isPublic

inline val ClassNode.isPrivate get() = access.isPrivate

inline val ClassNode.isProtected get() = access.isProtected

inline val ClassNode.isInterface get() = access.isInterface

inline val ClassNode.isAbstract get() = access.isAbstract

inline val ClassNode.isFinal get() = access.isFinal

inline val ClassNode.isAnnotation get() = access.isAnnotation

inline val ClassNode.isEnum get() = access.isEnum

inline val ClassInfo.isPublic get() = Modifier.isPublic(classNode.access)

inline val ClassInfo.isPrivate get() = Modifier.isPrivate(classNode.access)

inline val ClassInfo.isProtected get() = Modifier.isProtected(classNode.access)

inline val ClassInfo.isInterface get() = Modifier.isInterface(classNode.access)

inline val ClassInfo.isAbstract get() = Modifier.isAbstract(classNode.access)

inline val ClassInfo.isFinal get() = Modifier.isFinal(classNode.access)

inline val ClassInfo.isAnnotation get() = classNode.access and Opcodes.ACC_ANNOTATION != 0

inline val ClassInfo.isEnum get() = classNode.access and Opcodes.ACC_ENUM != 0

fun ClassNode.getOrCreateClinit(builder: (MethodBuilder.() -> Unit)? = null): MethodNode =
    methods.firstOrNull { it.name.equals("<clinit>") } ?: clinit(builder)

val ClassNode.hasAnnotations: Boolean
    get() = (visibleAnnotations != null && visibleAnnotations.isNotEmpty()) || (invisibleAnnotations != null && invisibleAnnotations.isNotEmpty())

fun ClassNode.appendAnnotation(annotation: String): ClassNode {
    visitAnnotation(annotation, false)
    return this
}

fun ClassNode.removeAnnotation(annotation: String) {
    invisibleAnnotations?.toList()?.forEach {
        if (it.desc == annotation) invisibleAnnotations.remove(it)
    }
    visibleAnnotations?.toList()?.forEach {
        if (it.desc == annotation) visibleAnnotations.remove(it)
    }
}

fun ClassNode.hasAnnotation(desc: String): Boolean = findAnnotation(desc) != null

fun ClassNode.findAnnotation(desc: String): AnnotationNode? {
    return visibleAnnotations?.find { it.desc == desc } ?: invisibleAnnotations?.find { it.desc == desc }
}