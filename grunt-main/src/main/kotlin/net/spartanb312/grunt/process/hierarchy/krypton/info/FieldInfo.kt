package net.spartanb312.grunt.process.hierarchy.krypton.info

import net.spartanb312.grunt.utils.extensions.isPrivate
import org.objectweb.asm.tree.FieldNode

class FieldInfo(
    val owner: ClassInfo,
    val fieldNode: FieldNode
) {

    val name get() = fieldNode.name
    val full get() = "${owner.name}.$name"

    var filled = false
    var source = this

    val isSourceField get() = fieldNode.isPrivate || parents.isEmpty()
    val isStandaloneField get() = fieldNode.isPrivate || (parents.isEmpty() && children.isEmpty())

    val parents = mutableSetOf<FieldInfo>()
    val children = mutableSetOf<FieldInfo>()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FieldInfo

        if (owner != other.owner) return false
        return fieldNode == other.fieldNode
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + fieldNode.hashCode()
        return result
    }
}