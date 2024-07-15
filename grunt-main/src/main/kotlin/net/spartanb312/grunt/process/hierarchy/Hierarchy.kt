package net.spartanb312.grunt.process.hierarchy

import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode

class Hierarchy(private val resourceCache: ResourceCache) {

    private val classInfos = mutableMapOf<String, ClassInfo>()
    private val missingDependencies = mutableMapOf<ClassInfo, List<ClassInfo>>() // Missing, Affected

    val size get() = classInfos.size

    class ClassInfo(val name: String, val classNode: ClassNode) {
        val superName: String? get() = classNode.superName
        val interfaces: MutableList<String>? get() = classNode.interfaces
        val parentNames = mutableListOf<String>().apply {
            superName?.let { add(it) }
            interfaces?.let { addAll(it) }
        }
        val parents = mutableSetOf<ClassInfo>()
        val children = mutableSetOf<ClassInfo>()
        var iterated = false
        var missingDependencies = false
        val isBroken = classNode == dummyClassNode

        companion object {
            val dummyClassNode = ClassNode()
        }
    }

    fun build() {
        // Build all class infos
        resourceCache.classes.forEach { getClassInfo(it.key) }

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

        // Missing dependencies
        classInfos.values.forEach {
            if (it.isBroken) missingDependencies[it] = it.children.toList()
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

    fun getClassInfo(classNode: ClassNode): ClassInfo = getClassInfo(classNode.name)

    fun getClassInfo(name: String): ClassInfo {
        return classInfos[name] ?: buildClassInfo(name)
    }

    private fun buildClassInfo(name: String, subClassInfo: ClassInfo? = null): ClassInfo {
        val info = classInfos[name]
        return if (info == null) {
            val classNode = resourceCache.getClassNode(name)
            val newInfo = ClassInfo(name, classNode ?: ClassInfo.dummyClassNode)
            if (subClassInfo != null) newInfo.children.add(subClassInfo)

            // solve parents
            newInfo.parentNames.forEach {
                newInfo.parents.add(buildClassInfo(it, newInfo))
            }
            classInfos[newInfo.name] = newInfo
            newInfo
        } else {
            if (subClassInfo != null) info.children.add(subClassInfo)
            info
        }
    }

    fun printMissing(printAffected: Boolean = true) {
        missingDependencies.forEach { (dependency, requiredBy) ->
            Logger.error("Missing ${dependency.name}")
            if (printAffected) requiredBy.forEach {
                Logger.error("   Required by ${it.name}")
            }
        }
    }

}