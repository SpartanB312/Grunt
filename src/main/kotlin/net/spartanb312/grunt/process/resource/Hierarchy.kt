package net.spartanb312.grunt.process.resource

import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode

class Hierarchy(private val resourceCache: ResourceCache) {

    private val hierarchies = mutableMapOf<String, HierarchyInfo>()
    val size get() = hierarchies.size

    inner class HierarchyInfo(val classNode: ClassNode) {
        var superName: String? = classNode.superName
        val interfaces = classNode.interfaces.toMutableList()

        val parents = mutableSetOf<HierarchyInfo>()
        val children = mutableSetOf<HierarchyInfo>()

        var missingDependencies = false
    }

    fun build() {
        resourceCache.classes.values.forEach { getHierarchyInfo(it) }
        fillParentsInfo()
        fillChildrenInfo()
    }

    fun getHierarchyInfo(classNode: ClassNode): HierarchyInfo {
        return hierarchies[classNode.name] ?: buildHierarchy(classNode)
    }

    private fun buildHierarchy(classNode: ClassNode, subClassInfo: HierarchyInfo? = null): HierarchyInfo {
        val info = hierarchies[classNode.name]
        if (info == null) {
            val newInfo = HierarchyInfo(classNode)

            // SuperName
            val superName = newInfo.superName
            if (superName != null) {
                val clazz = resourceCache.getClassNode(superName)
                if (clazz == null) {
                    Logger.error("Missing dependency $superName")
                    newInfo.missingDependencies = true
                } else {
                    val parentInfo = buildHierarchy(clazz, newInfo)
                    newInfo.parents.add(parentInfo)
                }
            }

            // Interfaces
            for (itf in newInfo.interfaces) {
                val clazz = resourceCache.getClassNode(itf)
                if (clazz == null) {
                    Logger.error("Missing dependency $itf")
                    newInfo.missingDependencies = true
                } else {
                    val parentInfo = buildHierarchy(clazz, newInfo)
                    newInfo.parents.add(parentInfo)
                }
            }

            hierarchies[classNode.name] = newInfo
            return newInfo
        } else {
            if (subClassInfo != null) info.children.add(subClassInfo)
            return info
        }
    }

    private fun fillChildrenInfo() {
        hierarchies.values.forEach {
            fun iterateParents(info: HierarchyInfo) {
                for (parent in info.parents) {
                    parent.children.add(it)
                    iterateParents(parent)
                }
            }
            iterateParents(it)
        }
    }

    private fun fillParentsInfo() {
        hierarchies.values.forEach {
            fun iterateParents(info: HierarchyInfo) {
                for (parent in info.parents.toList()) {
                    it.parents.add(parent)
                    iterateParents(parent)
                }
            }
            iterateParents(it)
        }
    }

    fun isSubType(child: String, father: String): Boolean {
        val childClass = resourceCache.getClassNode(child) ?: return false
        val fatherClass = resourceCache.getClassNode(father) ?: return false
        val childInfo = getHierarchyInfo(childClass)
        val fatherInfo = getHierarchyInfo(fatherClass)
        return if (childInfo.parents.contains(fatherInfo)) true
        else fatherInfo.children.contains(childInfo)
    }

}