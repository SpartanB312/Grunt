@file:Suppress("FunctionName")

package net.spartanb312.grunteon.asm.tree.insn

import net.spartanb312.grunteon.asm.MethodVisitor
import net.spartanb312.grunteon.asm.tree.TypeAnnotationNode

interface InsnList : List<BaseInsnNode> {
    fun accept(mv: MethodVisitor) {
        forEach { it.accept(mv) }
    }
}

interface InsnListBuilder {
    fun FieldInsnNode(
        opcode: Int,
        owner: String,
        name: String,
        desc: String,
        visibleTypeAnnotations: List<TypeAnnotationNode> = emptyList(),
        invisibleTypeAnnotations: List<TypeAnnotationNode> = emptyList()
    )

    fun FrameNode(
        type: Int,
        numLocal: Int,
        local: List<Any>?,
        numStack: Int,
        stack: List<Any>?
    )

    fun MethodInsnNode(
        opcode: Int,
        owner: String,
        name: String,
        desc: String,
        visibleTypeAnnotations: List<TypeAnnotationNode> = emptyList(),
        invisibleTypeAnnotations: List<TypeAnnotationNode> = emptyList()
    )

    fun build(): InsnList
}

fun InsnListBuilder.FieldInsnNode(
    fieldInsnNode: FieldInsnNode,
    opcode: Int = fieldInsnNode.opcode,
    owner: String = fieldInsnNode.owner,
    name: String = fieldInsnNode.name,
    desc: String = fieldInsnNode.desc,
    visibleTypeAnnotations: List<TypeAnnotationNode> = fieldInsnNode.visibleTypeAnnotations,
    invisibleTypeAnnotations: List<TypeAnnotationNode> = fieldInsnNode.invisibleTypeAnnotations
) = FieldInsnNode(
    opcode,
    owner,
    name,
    desc,
    visibleTypeAnnotations,
    invisibleTypeAnnotations
)

fun InsnListBuilder.FrameNode(
    frameNode: FrameNode,
    type: Int = frameNode.frameType,
    numLocal: Int = frameNode.local?.size ?: -1,
    local: List<Any>? = frameNode.local,
    numStack: Int = frameNode.stack?.size ?: -1,
    stack: List<Any>? = frameNode.stack
) = FrameNode(
    type,
    numLocal,
    local,
    numStack,
    stack
)

fun InsnListBuilder.MethodInsnNode(
    methodInsnNode: MethodInsnNode,
    opcode: Int = methodInsnNode.opcode,
    owner: String = methodInsnNode.owner,
    name: String = methodInsnNode.name,
    desc: String = methodInsnNode.desc,
    visibleTypeAnnotations: List<TypeAnnotationNode> = methodInsnNode.visibleTypeAnnotations,
    invisibleTypeAnnotations: List<TypeAnnotationNode> = methodInsnNode.invisibleTypeAnnotations
) = MethodInsnNode(
    opcode,
    owner,
    name,
    desc,
    visibleTypeAnnotations,
    invisibleTypeAnnotations
)