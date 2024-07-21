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
        return when {
            type1 == "java/lang/Object" -> type1
            type2 == "java/lang/Object" -> type2
            hierarchy.isSubType(type1, type2) -> type2
            hierarchy.isSubType(type2, type1) -> type1
            else -> {
                val clazz1 = resourceCache.getClassNode(type1)
                val clazz2 = resourceCache.getClassNode(type2)
                if (clazz1?.isInterface == true && clazz2?.isInterface == true) return "java/lang/Object"
                try {
                    super.getCommonSuperClass(type1, type2)
                } catch (exception: Exception) {
                    val missing1 = clazz1 == null && try {
                        Class.forName(type1)
                        false
                    } catch (ignore: Exception) {
                        true
                    }
                    if (missing1) {
                        Logger.error("Missing dependency $type1")
                        throw Exception("Can't find common super class due to missing $type1")
                    }
                    "java/lang/Object"
                }
            }
        }
    }

}