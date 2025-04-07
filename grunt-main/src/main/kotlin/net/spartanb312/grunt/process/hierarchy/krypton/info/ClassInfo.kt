package net.spartanb312.grunt.process.hierarchy.krypton.info

import it.unimi.dsi.fastutil.objects.ObjectArraySet
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import org.objectweb.asm.tree.ClassNode

class ClassInfo(
    val name: String,
    val classNode: ClassNode
) {

    var iterated = false
    val superName get() = classNode.superName
    val interfaces get() = classNode.interfaces
    val parentsNames: MutableSet<String>
        get() = mutableSetOf<String>().apply {
            superName?.let { add(it) }
            interfaces?.let { addAll(it) }
        }

    val isBroken get() = classNode == missingClassNode
    var missingDependencies = false

    var parents = ObjectLinkedOpenHashSet<ClassInfo>()
    var children = ObjectLinkedOpenHashSet<ClassInfo>()

    var fields = ObjectArraySet<FieldInfo>()
    var methods = ObjectArraySet<MethodInfo>()

    companion object {
        val missingClassNode = ClassNode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClassInfo

        if (name != other.name) return false
        return classNode == other.classNode
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + classNode.hashCode()
        return result
    }
}