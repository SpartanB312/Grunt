@file:Suppress("NOTHING_TO_INLINE")

package net.spartanb312.grunt.utils.extensions

import org.objectweb.asm.Opcodes
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
