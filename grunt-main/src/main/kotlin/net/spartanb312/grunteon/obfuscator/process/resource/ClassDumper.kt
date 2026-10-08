package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import org.objectweb.asm.ClassWriter

class ClassDumper(
    private val instance: Grunteon,
    private val hierarchy: ClassHierarchy,
    useComputeMax: Boolean = false
) : ClassWriter(if (useComputeMax) COMPUTE_MAXS else COMPUTE_FRAMES) {

    override fun getCommonSuperClass(type1: String, type2: String): String {
        return when {
            type1 == "java/lang/Object" -> type1
            type2 == "java/lang/Object" -> type2
            hierarchy.isSubType(type1, type2) -> type2
            hierarchy.isSubType(type2, type1) -> type1

            else -> context(hierarchy) {
                val clazz1 = instance.workRes.getClassMetadata(type1)
                val clazz2 = instance.workRes.getClassMetadata(type2)
                if (clazz1?.isInterface == true || clazz2?.isInterface == true) return "java/lang/Object"
                else {
                    // search lca
                    var info1 = hierarchy.findClass(type1)
                    val info2 = hierarchy.findClass(type2)
                    if (info1 != -1 && info2 != -1) {
                        do {
                            info1 = ClassHierarchy.Entry(info1).superClass
                        } while (!hierarchy.isSubType(info2, info1))
                        return hierarchy.classNames[info1]
                    }
                }
                // fallback
                try {
                    super.getCommonSuperClass(type1, type2)
                } catch (_: Exception) {
                    try {
                        Class.forName(type1)
                    } catch (_: Exception) {
                        if (clazz1 == null) {
                            Logger.error("Missing dependency $type1")
                            throw Exception("Can't find common super class due to missing $type1")
                        }
                    }
                    try {
                        Class.forName(type2)
                    } catch (_: Exception) {
                        if (clazz2 == null) {
                            Logger.error("Missing dependency $type2")
                            throw Exception("Can't find common super class due to missing $type2")
                        }
                    }
                    "java/lang/Object"
                }
            }
        }
    }

}