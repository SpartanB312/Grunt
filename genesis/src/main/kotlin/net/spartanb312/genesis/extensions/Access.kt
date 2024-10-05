package net.spartanb312.genesis.extensions

import org.objectweb.asm.Opcodes


infix fun Int.equals(other: Int) = (this and other) == other
infix fun Int.equals(other: Modifiers) = (this and other.modifier) == other.modifier
infix fun Modifiers.equals(other: Modifiers) = (this.modifier and other.modifier) == other.modifier
infix fun Int.intersects(other: Int) = (this and other) != 0
infix fun Int.intersects(other: Modifiers) = (this and other.modifier) != 0
infix fun Modifiers.intersects(other: Modifiers) = (this.modifier and other.modifier) != 0

inline val Int.isPublic get() = this intersects Opcodes.ACC_PUBLIC
inline val Int.isPrivate get() = this intersects Opcodes.ACC_PRIVATE
inline val Int.isProtected get() = this intersects Opcodes.ACC_PROTECTED
inline val Int.isStatic get() = this intersects Opcodes.ACC_STATIC
inline val Int.isFinal get() = this intersects Opcodes.ACC_FINAL
inline val Int.isSuper get() = this intersects Opcodes.ACC_SUPER
inline val Int.isSynchronized get() = this intersects Opcodes.ACC_SYNCHRONIZED
inline val Int.isOpen get() = this intersects Opcodes.ACC_OPEN
inline val Int.isTransitive get() = this intersects Opcodes.ACC_TRANSITIVE
inline val Int.isNative get() = this intersects Opcodes.ACC_NATIVE
inline val Int.isInterface get() = this intersects Opcodes.ACC_INTERFACE
inline val Int.isAbstract get() = this intersects Opcodes.ACC_ABSTRACT
inline val Int.isStrict get() = this intersects Opcodes.ACC_STRICT
inline val Int.isSynthetic get() = this intersects Opcodes.ACC_SYNTHETIC
inline val Int.isAnnotation get() = this intersects Opcodes.ACC_ANNOTATION
inline val Int.isEnum get() = this intersects Opcodes.ACC_ENUM
inline val Int.isMandated get() = this intersects Opcodes.ACC_MANDATED
inline val Int.isModule get() = this intersects Opcodes.ACC_MODULE
inline val Int.isRecord get() = this intersects Opcodes.ACC_RECORD
inline val Int.isDeprecated get() = this intersects Opcodes.ACC_DEPRECATED