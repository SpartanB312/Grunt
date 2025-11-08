package net.spartanb312.grunteon.asm.tree.insn

import net.spartanb312.grunteon.asm.MethodVisitor
import net.spartanb312.grunteon.asm.tree.TypeAnnotationNode

interface InsnList : List<BaseInsnNode> {
    fun accept(mv: MethodVisitor) {
        forEach { it.accept(mv) }
    }
}

@Suppress("FunctionName")
interface InsnListBuilder {
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