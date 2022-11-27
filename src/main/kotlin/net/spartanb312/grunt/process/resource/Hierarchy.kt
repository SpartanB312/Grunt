package net.spartanb312.grunt.process.resource

import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode

class Hierarchy(private val resourceCache: ResourceCache) {

    private val hierarchies = mutableMapOf<String, HierarchyInfo>()
    private val hierarchyNodes = mutableMapOf<String, HierarchyNode>()
    val size get() = hierarchyNodes.size

    open inner class HierarchyNode {
        val parents = mutableSetOf<HierarchyNode>()
        val children = mutableSetOf<HierarchyNode>()
    }

    inner class HierarchyInfo(val classNode: ClassNode) : HierarchyNode() {
        var superName: String? = classNode.superName
        val interfaces = classNode.interfaces.toMutableList()
        var missingDependencies = false
    }

    inner class BrokenHierarchyInfo(val name: String) : HierarchyNode()

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
                    val brokenInfo = BrokenHierarchyInfo(superName)
                    newInfo.parents.add(brokenInfo)
                    hierarchyNodes[superName] = brokenInfo
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
                    val brokenInfo = BrokenHierarchyInfo(itf)
                    newInfo.parents.add(brokenInfo)
                    hierarchyNodes[itf] = brokenInfo
                } else {
                    val parentInfo = buildHierarchy(clazz, newInfo)
                    newInfo.parents.add(parentInfo)
                }
            }

            hierarchies[classNode.name] = newInfo
            hierarchyNodes[classNode.name] = newInfo
            return newInfo
        } else {
            if (subClassInfo != null) info.children.add(subClassInfo)
            return info
        }
    }

    private fun fillChildrenInfo() {
        hierarchies.values.forEach {
            fun iterateParents(info: HierarchyNode) {
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
            fun iterateParents(info: HierarchyNode) {
                for (parent in info.parents.toList()) {
                    it.parents.add(parent)
                    if (parent is HierarchyInfo && parent.missingDependencies)
                        it.missingDependencies = true
                    iterateParents(parent)
                }
            }
            iterateParents(it)
        }
    }

    fun isSubType(child: String, father: String): Boolean {
        val childInfo = hierarchyNodes[child] ?: return false
        val fatherInfo = hierarchyNodes[father] ?: return false
        return if (childInfo.parents.contains(fatherInfo)) true
        else fatherInfo.children.contains(childInfo)
    }

}