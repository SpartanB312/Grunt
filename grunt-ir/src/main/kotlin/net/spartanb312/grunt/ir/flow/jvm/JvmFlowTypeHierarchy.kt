package net.spartanb312.grunt.ir.flow.jvm

interface JvmFlowTypeHierarchy {
    fun isSubType(childInternalName: String, parentInternalName: String): Boolean = childInternalName == parentInternalName

    fun commonSuperClass(type1InternalName: String, type2InternalName: String): String {
        return when {
            type1InternalName == type2InternalName -> type1InternalName
            isSubType(type1InternalName, type2InternalName) -> type2InternalName
            isSubType(type2InternalName, type1InternalName) -> type1InternalName
            else -> JavaObjectInternalName
        }
    }

    companion object {
        val Empty: JvmFlowTypeHierarchy = object : JvmFlowTypeHierarchy {}
    }
}

const val JavaObjectInternalName = "java/lang/Object"
const val JavaCloneableInternalName = "java/lang/Cloneable"
const val JavaSerializableInternalName = "java/io/Serializable"
const val JvmFlowNullInternalName = "null"
