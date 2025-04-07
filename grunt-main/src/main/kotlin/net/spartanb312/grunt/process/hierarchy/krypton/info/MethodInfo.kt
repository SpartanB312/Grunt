package net.spartanb312.grunt.process.hierarchy.krypton.info

import net.spartanb312.grunt.utils.extensions.isPrivate
import org.objectweb.asm.tree.MethodNode

class MethodInfo(
    val owner: ClassInfo,
    val methodNode: MethodNode,
    coder: NameCoder,
    val virtual: Boolean = false,
) {

    val name get() = methodNode.name
    val desc get() = methodNode.desc
    val full get() = "${owner.name}.$name$desc"
    val code = coder.getCode("$name$desc")

    var filled = false

    val sources = mutableSetOf<MethodInfo>()
    val competitors = mutableSetOf<MethodInfo>() // No common super class, have same child with same method
    val relatedMethods =
        mutableSetOf<MethodInfo>() // related methods contains all competitors. They should always have the same method name
    val multiSource get() = sources.size > 1
    val isSourceMethod get() = methodNode.isPrivate || parents.isEmpty()
    val isStandaloneMethod get() = methodNode.isPrivate || (parents.isEmpty() && children.isEmpty())

    val parents = hashSetOf<MethodInfo>()
    val children = hashSetOf<MethodInfo>()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MethodInfo

        if (owner != other.owner) return false
        return methodNode == other.methodNode
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + methodNode.hashCode()
        return result
    }
}
