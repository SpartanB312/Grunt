@file:Suppress("NOTHING_TO_INLINE")

package net.spartanb312.grunt.utils.extensions

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.lang.reflect.Modifier

inline val String.canMethodBeRenamed get() = this != "main" && this != "<init>" && this != "<clinit>"

inline val ClassNode.isPublic get() = Modifier.isPublic(access)

inline val ClassNode.isPrivate get() = Modifier.isPrivate(access)

inline val ClassNode.isProtected get() = Modifier.isProtected(access)

inline val ClassNode.isInterface get() = Modifier.isInterface(access)

inline val ClassNode.isAbstract get() = Modifier.isAbstract(access)

inline val ClassNode.isFinal get() = Modifier.isFinal(access)

inline val ClassNode.isAnnotation get() = access and Opcodes.ACC_ANNOTATION != 0

inline val ClassNode.isEnum get() = access and Opcodes.ACC_ENUM != 0

inline fun ClassNode.setPublic() {
    if (isPublic) return
    if (isPrivate) {
        access = access and Opcodes.ACC_PRIVATE.inv()
        access = access or Opcodes.ACC_PUBLIC
    } else if (isProtected) {
        access = access and Opcodes.ACC_PROTECTED.inv()
        access = access or Opcodes.ACC_PUBLIC
    } else access = access or Opcodes.ACC_PUBLIC
}

inline fun ClassNode.getOrCreateClinit(): MethodNode =
    methods.firstOrNull { it.name.equals("<clinit>") } ?: methodNode(
        Opcodes.ACC_STATIC,
        "<clinit>",
        "()V",
        null,
        null
    ) {
        RETURN()
    }.addTo(methods)

val ClassNode.hasAnnotations: Boolean
    get() {
        return visibleAnnotations != null && visibleAnnotations.isNotEmpty()
                || invisibleAnnotations != null && invisibleAnnotations.isNotEmpty()
    }
