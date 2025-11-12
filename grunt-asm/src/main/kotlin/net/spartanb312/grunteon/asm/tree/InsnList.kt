@file:Suppress("FunctionName", "SpellCheckingInspection")

package net.spartanb312.grunteon.asm.tree

import net.spartanb312.grunteon.asm.MethodVisitor
import net.spartanb312.grunteon.asm.tree.insn.*

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

    fun F_APPEND(
        local: List<Any>
    )

    fun F_FULL(
        local: List<Any>,
        stack: List<Any>
    )

    fun F_CHOP(
        local: List<Any>
    )

    fun F_SAME()

    fun F_SAME1(
        stack: List<Any>
    )

    fun IINC(
        variable: Int,
        increment: Int
    )

    fun INVOKEVIRTUAL(
        owner: String,
        name: String,
        desc: String
    )

    fun INVOKESPECIAL(
        owner: String,
        name: String,
        desc: String
    )

    fun INVOKESTATIC(
        owner: String,
        name: String,
        desc: String
    )

    fun INVOKEINTERFACE(
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

fun InsnListBuilder.F_APPEND(
    src: FAppendNode,
    local: List<Any> = src.local
) = F_APPEND(
    local
)

fun InsnListBuilder.F_FULL(
    src: FFullNode,
    local: List<Any> = src.local,
    stack: List<Any> = src.stack
) = F_FULL(
    local,
    stack
)

fun InsnListBuilder.F_CHOP(
    src: FAppendNode,
    local: List<Any> = src.local
) = F_CHOP(
    local
)

fun InsnListBuilder.F_SAME1(
    src: FSame1Node,
    stack: List<Any> = src.stack
) = F_SAME1(
    stack
)

fun InsnListBuilder.IINC(
    src: IincInsnNode,
    variable: Int = src.variable,
    increment: Int = src.increment
) = IINC(
    variable,
    increment
)

fun InsnListBuilder.INVOKEVIRTUAL(
    src: InvokeVirtualInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKEVIRTUAL(
    owner,
    name,
    desc
)

fun InsnListBuilder.INVOKESPECIAL(
    src: InvokeSpecialInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKESPECIAL(
    owner,
    name,
    desc
)

fun InsnListBuilder.INVOKESTATIC(
    src: InvokeStaticInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKESTATIC(
    owner,
    name,
    desc
)

fun InsnListBuilder.INVOKEINTERFACE(
    src: InvokeInterfaceInsnNode,
    owner: String = src.owner,
    name: String = src.name,
    desc: String = src.desc
) = INVOKEINTERFACE(
    owner,
    name,
    desc
)