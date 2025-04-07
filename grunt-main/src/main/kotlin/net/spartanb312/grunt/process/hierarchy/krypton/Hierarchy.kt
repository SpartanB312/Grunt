package net.spartanb312.grunt.process.hierarchy.krypton

import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.hierarchy.krypton.info.ClassInfo
import net.spartanb312.grunt.process.hierarchy.krypton.info.FieldInfo
import net.spartanb312.grunt.process.hierarchy.krypton.info.MethodInfo
import net.spartanb312.grunt.process.hierarchy.krypton.info.NameCoder
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode

/**
 * Fast Hierarchy
 * containing most used infos
 */
open class Hierarchy(val resourceCache: ResourceCache) {

    val classInfos = mutableMapOf<String, ClassInfo>()
    private val missingDependencies = mutableMapOf<ClassInfo, List<ClassInfo>>() // Missing, Affected
    open val size get() = classInfos.size
    val methodCoder = NameCoder()

    fun clear() {
        classInfos.clear()
        missingDependencies.clear()
        methodCoder.clear()
    }

    fun buildAll() {
        buildClass()
        buildField()
        buildMethod()
    }

    open fun buildClass() {
        // Build all class infos
        resourceCache.allClasses.forEach { getClassInfo(it) }
        fillClassHierarchyInfo()

        // Missing dependencies
        classInfos.values.forEach {
            if (it.isBroken) missingDependencies[it] = it.children.toList()
        }
    }

    open fun buildField() {
        // Build all field infos
        classInfos.values.forEach { classInfo ->
            classInfo.classNode.fields.forEach { fieldNode ->
                val fieldInfo = FieldInfo(classInfo, fieldNode)
                classInfo.fields.add(fieldInfo)
            }
        }

    }

    open fun buildMethod() {
        // Build all method infos
        classInfos.values.forEach { classInfo ->
            classInfo.classNode.methods.forEach { methodNode ->
                val methodInfo = MethodInfo(classInfo, methodNode, methodCoder)
                classInfo.methods.add(methodInfo)
            }
        }
    }

    fun getClassInfo(classNode: ClassNode): ClassInfo = getClassInfo(classNode.name)

    fun getClassInfo(className: String): ClassInfo {
        return classInfos[className] ?: buildClassInfo(className, null)
    }

    private fun fillClassHierarchyInfo() {
        // Iterate parents
        classInfos.values.forEach { classInfo ->
            fun iterateParents(current: ClassInfo): Set<ClassInfo> {
                if (!current.iterated) {
                    current.iterated = true
                    val parents = mutableSetOf<ClassInfo>()
                    for (parent in current.parents) {
                        parents.addAll(iterateParents(parent))
                    }
                    current.parents.addAll(parents)
                }
                return current.parents
            }
            iterateParents(classInfo)
        }

        // Iterate children
        classInfos.values.forEach { classInfo ->
            for (parent in classInfo.parents) {
                parent.children.add(classInfo)
            }
        }

        // Update missing dependencies states
        classInfos.values.forEach { classInfo ->
            classInfo.missingDependencies = classInfo.parents.any { it.isBroken } || classInfo.isBroken
        }
    }

    private fun buildClassInfo(className: String, subClassInfo: ClassInfo?): ClassInfo {
        val existInfo = classInfos[className]
        if (existInfo != null) {
            // Process exist class info
            if (subClassInfo != null) existInfo.children.add(subClassInfo)
            return existInfo
        } else {
            // Create a new class info
            val classNode = resourceCache.getClassNode(className) ?: ClassInfo.missingClassNode
            val newInfo = ClassInfo(className, classNode)
            if (subClassInfo != null) newInfo.children.add(subClassInfo)

            // Solve parents
            newInfo.parentsNames.forEach {
                newInfo.parents.add(buildClassInfo(it, newInfo))
            }
            classInfos[newInfo.name] = newInfo
            return newInfo
        }
    }

    fun isSubType(child: ClassInfo, father: ClassInfo): Boolean {
        return isSubType(child.name, father.name)
    }

    fun isSubType(child: String, father: String): Boolean {
        if (child == father) return true
        val childInfo = classInfos[child] ?: return false
        val fatherInfo = classInfos[father] ?: return false
        return if (childInfo.parents.contains(fatherInfo)) true
        else fatherInfo.children.contains(childInfo)
    }

    fun getCommonSuperClass(type1: ClassInfo, type2: ClassInfo): ClassInfo? {
        return getCommonSuperClass(type1.name, type2.name)
    }

    fun getCommonSuperClass(type1: String, type2: String): ClassInfo? {
        val info1 = classInfos[type1] ?: return null
        val info2 = classInfos[type2] ?: return null
        return when {
            type1 == "java/lang/Object" -> info1
            type2 == "java/lang/Object" -> info2
            isSubType(type1, type2) -> info2
            isSubType(type2, type1) -> info1
            info1.isInterface && info2.isInterface -> null
            else -> return null
        }
    }

    fun printMissing(printAffected: Boolean = true) {
        missingDependencies.forEach { (dependency, requiredBy) ->
            Logger.error("Missing ${dependency.name}")
            if (printAffected) requiredBy.forEach {
                if (it.name in resourceCache.classes.keys) Logger.error("   Required by ${it.name}")
                else Logger.warn("    Required by ${it.name}")
            }
        }
    }

}