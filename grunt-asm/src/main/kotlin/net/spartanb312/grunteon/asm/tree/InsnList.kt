@file:Suppress("FunctionName", "SpellCheckingInspection")

package net.spartanb312.grunteon.asm.tree

import net.spartanb312.grunteon.asm.MethodVisitor
import net.spartanb312.grunteon.asm.tree.insn.GetStaticInsnNode
import net.spartanb312.grunteon.asm.tree.insn.IBaseInsnNode

interface InsnList : List<IBaseInsnNode> {
    fun accept(mv: MethodVisitor) {
        forEach { it.accept(mv) }
    }
}

interface InsnListBuilder {
    fun GETSTATIC(
        owner: String,
        name: String,
        desc: String
    )

    fun PUTSTATIC(
        owner: String,
        name: String,
        desc: String
    )

    fun GETFIELD(
        owner: String,
        name: String,
        desc: String
    )

    fun PUTFIELD(
        owner: String,
        name: String,
        desc: String
    )

    fun build(): InsnList
}

fun InsnListBuilder.GETSTATIC(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = GETSTATIC(
    owner,
    name,
    desc
)

fun InsnListBuilder.PUTSTATIC(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = PUTSTATIC(
    owner,
    name,
    desc
)

fun InsnListBuilder.GETFIELD(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = GETFIELD(
    owner,
    name,
    desc
)

fun InsnListBuilder.PUTFIELD(
    src: GetStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = PUTFIELD(
    owner,
    name,
    desc
)