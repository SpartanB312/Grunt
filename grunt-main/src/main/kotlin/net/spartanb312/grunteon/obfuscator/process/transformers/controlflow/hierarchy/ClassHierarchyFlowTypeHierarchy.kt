package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.hierarchy

import net.spartanb312.grunt.ir.flow.jvm.JavaObjectInternalName
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowTypeHierarchy
import net.spartanb312.grunteon.obfuscator.process.HiddenTransformer
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface

internal class ClassHierarchyFlowTypeHierarchy(
    private val hierarchy: ClassHierarchy
) : JvmFlowTypeHierarchy {
    override fun isSubType(childInternalName: String, parentInternalName: String): Boolean {
        return hierarchy.isSubType(childInternalName, parentInternalName)
    }

    override fun commonSuperClass(type1InternalName: String, type2InternalName: String): String {
        return when {
            type1InternalName == type2InternalName -> type1InternalName
            type1InternalName == JavaObjectInternalName -> type1InternalName
            type2InternalName == JavaObjectInternalName -> type2InternalName
            hierarchy.isSubType(type1InternalName, type2InternalName) -> type2InternalName
            hierarchy.isSubType(type2InternalName, type1InternalName) -> type1InternalName
            isInterface(type1InternalName) || isInterface(type2InternalName) -> JavaObjectInternalName
            else -> commonClassAncestor(type1InternalName, type2InternalName)
        }
    }

    private fun isInterface(internalName: String): Boolean {
        val index = hierarchy.findClass(internalName)
        return index >= 0 && hierarchy.classNodes.getOrNull(index)?.isInterface == true
    }

    private fun commonClassAncestor(type1InternalName: String, type2InternalName: String): String {
        val type2Index = hierarchy.findClass(type2InternalName)
        if (type2Index < 0) return JavaObjectInternalName

        var cursor = hierarchy.findClass(type1InternalName)
        while (cursor >= 0) {
            if (hierarchy.isSubType(type2Index, cursor)) {
                return hierarchy.classNames.getOrElse(cursor) { JavaObjectInternalName }
            }
            cursor = hierarchy.parents.getOrNull(cursor)?.firstOrNull() ?: -1
        }

        return JavaObjectInternalName
    }
}