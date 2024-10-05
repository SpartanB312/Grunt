package net.spartanb312.genesis.extensions

import org.objectweb.asm.Opcodes

open class Modifiers(val modifier: Int) {
    infix operator fun plus(other: Modifiers): Modifiers = Modifiers(modifier or other.modifier)
}

@BuilderDSL
object PUBLIC : Modifiers(Opcodes.ACC_PUBLIC)

@BuilderDSL
object PRIVATE : Modifiers(Opcodes.ACC_PRIVATE)

@BuilderDSL
object PROTECTED : Modifiers(Opcodes.ACC_PROTECTED)

@BuilderDSL
object STATIC : Modifiers(Opcodes.ACC_STATIC)

@BuilderDSL
object FINAL : Modifiers(Opcodes.ACC_FINAL)

@BuilderDSL
object SUPER : Modifiers(Opcodes.ACC_SUPER)

@BuilderDSL
object SYNCHRONIZED : Modifiers(Opcodes.ACC_SYNCHRONIZED)

@BuilderDSL
object OPEN : Modifiers(Opcodes.ACC_OPEN)

@BuilderDSL
object TRANSITIVE : Modifiers(Opcodes.ACC_TRANSITIVE)

@BuilderDSL
object VOLATILE : Modifiers(Opcodes.ACC_VOLATILE)

@BuilderDSL
object BRIDGE : Modifiers(Opcodes.ACC_BRIDGE)

@BuilderDSL
object STATIC_PHASE : Modifiers(Opcodes.ACC_STATIC_PHASE)

@BuilderDSL
object VARARGS : Modifiers(Opcodes.ACC_VARARGS)

@BuilderDSL
object TRANSIENT : Modifiers(Opcodes.ACC_TRANSIENT)

@BuilderDSL
object NATIVE : Modifiers(Opcodes.ACC_NATIVE)

@BuilderDSL
object INTERFACE : Modifiers(Opcodes.ACC_INTERFACE)

@BuilderDSL
object ABSTRACT : Modifiers(Opcodes.ACC_ABSTRACT)

@BuilderDSL
object STRICT : Modifiers(Opcodes.ACC_STRICT)

@BuilderDSL
object SYNTHETIC : Modifiers(Opcodes.ACC_SYNTHETIC)

@BuilderDSL
object ANNOTATION : Modifiers(Opcodes.ACC_ANNOTATION)

@BuilderDSL
object ENUM : Modifiers(Opcodes.ACC_ENUM)

@BuilderDSL
object MANDATED : Modifiers(Opcodes.ACC_MANDATED)

@BuilderDSL
object MODULE : Modifiers(Opcodes.ACC_MODULE)

@BuilderDSL
object RECORD : Modifiers(Opcodes.ACC_RECORD)

@BuilderDSL
object DEPRECATED : Modifiers(Opcodes.ACC_DEPRECATED)

@BuilderDSL
val Java1 = Opcodes.V1_1

@BuilderDSL
val Java2 = Opcodes.V1_2

@BuilderDSL
val Java3 = Opcodes.V1_3

@BuilderDSL
val Java4 = Opcodes.V1_4

@BuilderDSL
val Java5 = Opcodes.V1_5

@BuilderDSL
val Java6 = Opcodes.V1_6

@BuilderDSL
val Java7 = Opcodes.V1_7

@BuilderDSL
val Java8 = Opcodes.V1_8

@BuilderDSL

val Java9 = Opcodes.V9

@BuilderDSL
val Java10 = Opcodes.V10

@BuilderDSL
val Java11 = Opcodes.V11

@BuilderDSL
val Java12 = Opcodes.V12

@BuilderDSL
val Java13 = Opcodes.V13

@BuilderDSL
val Java14 = Opcodes.V14

@BuilderDSL
val Java15 = Opcodes.V15

@BuilderDSL
val Java16 = Opcodes.V16

@BuilderDSL
val Java17 = Opcodes.V17

@BuilderDSL
val Java18 = Opcodes.V18

@BuilderDSL
val Java19 = Opcodes.V19

@BuilderDSL
val Java20 = Opcodes.V20

@BuilderDSL
val Java21 = Opcodes.V21

@BuilderDSL
val Java22 = Opcodes.V22

@BuilderDSL
val Java23 = Opcodes.V23
