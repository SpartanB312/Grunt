package net.spartanb312.grunt.process.hierarchy

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

object ReferenceSearch {

    fun checkMissing(classNode: ClassNode, hierarchy: Hierarchy): List<Hierarchy.ClassInfo> {
        val missingReference = mutableListOf<Hierarchy.ClassInfo>()
        for (method in classNode.methods) {
            missingReference.addAll(checkMissing(method, hierarchy))
        }
        return missingReference
    }

    fun checkMissing(methodNode: MethodNode, hierarchy: Hierarchy): List<Hierarchy.ClassInfo> {
        val missingReference = mutableListOf<Hierarchy.ClassInfo>()
        methodNode.instructions.forEach { insn ->
            if (insn is FieldInsnNode) {
                val name = if (!insn.owner.startsWith("[")) insn.owner
                else insn.owner.substringAfterLast("[").removePrefix("L").removeSuffix(";")
                val info = hierarchy.getClassInfo(name)
                if (info.isBroken) missingReference.add(info)

            }
            if (insn is MethodInsnNode) {
                val name = if (!insn.owner.startsWith("[")) insn.owner
                else insn.owner.substringAfterLast("[").removePrefix("L").removeSuffix(";")
                val info = hierarchy.getClassInfo(name)
                if (info.isBroken) missingReference.add(info)
            }
        }
        return missingReference
    }

}