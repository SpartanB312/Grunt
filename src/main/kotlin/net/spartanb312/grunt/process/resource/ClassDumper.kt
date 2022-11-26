package net.spartanb312.grunt.process.resource

import net.spartanb312.grunt.utils.isInterface
import org.objectweb.asm.ClassWriter

class ClassDumper(
    private val resourceCache: ResourceCache,
    private val hierarchy: Hierarchy,
    useComputeMax: Boolean = false
) : ClassWriter(if (useComputeMax) COMPUTE_MAXS else COMPUTE_FRAMES) {

    override fun getCommonSuperClass(type1: String, type2: String): String? {
        val clazz1 = resourceCache.getClassNode(type1) ?: return null
        val clazz2 = resourceCache.getClassNode(type2) ?: return null
        return when {
            type1 == "java/lang/Object" -> type1
            type2 == "java/lang/Object" -> type2
            clazz1.isInterface && clazz2.isInterface -> "java/lang/Object"
            hierarchy.isSubType(type1, type2) -> type2
            hierarchy.isSubType(type2, type1) -> type1
            else -> return null
        }
    }

}