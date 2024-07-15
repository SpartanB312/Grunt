package net.spartanb312.grunt.process.resource

import net.spartanb312.grunt.process.hierarchy.Hierarchy
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.ClassWriter

class ClassDumper(
    private val resourceCache: ResourceCache,
    private val hierarchy: Hierarchy,
    useComputeMax: Boolean = false
) : ClassWriter(if (useComputeMax) COMPUTE_MAXS else COMPUTE_FRAMES) {

    override fun getCommonSuperClass(type1: String, type2: String): String {
        val clazz1 = resourceCache.getClassNode(type1) ?: return "java/lang/Object"
        val clazz2 = resourceCache.getClassNode(type2) ?: return "java/lang/Object"
        return when {
            type1 == "java/lang/Object" -> type1
            type2 == "java/lang/Object" -> type2
            hierarchy.isSubType(type1, type2) -> type2
            hierarchy.isSubType(type2, type1) -> type1
            clazz1.isInterface && clazz2.isInterface -> "java/lang/Object"
            else -> {
                if (!type1.startsWith("java")
                    && !type1.startsWith("sun")
                    && !type1.startsWith("jdk")
                ) Logger.fatal("Failed to find common super class for $type1")
                return super.getCommonSuperClass(type1, type2)
            }
        }
    }

}