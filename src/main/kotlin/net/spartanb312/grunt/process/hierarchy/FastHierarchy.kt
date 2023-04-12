package net.spartanb312.grunt.process.hierarchy

import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode

/**
 * Fast class hierarchy graph
 * Prebuild all class infos
 * Faster in most situations
 */
class FastHierarchy(private val resourceCache: ResourceCache) : Hierarchy {

    // All hierarchy nodes
    private val hierarchyNodes = mutableMapOf<String, HierarchyNode>()

    // Subset of hierarchy nodes, only includes hierarchyNode with classNode
    private val hierarchies = mutableMapOf<String, HierarchyInfo>()
    override val size get() = hierarchyNodes.size

    open inner class HierarchyNode {
        val parents = mutableSetOf<HierarchyNode>()
        val children = mutableSetOf<HierarchyNode>()
        var iterated = false
        open var missingDependencies = false
    }

    inner class HierarchyInfo(val classNode: ClassNode) : HierarchyNode() {
        val superName: String? get() = classNode.superName
        val interfaces: MutableList<String> get() = classNode.interfaces
    }

    inner class BrokenHierarchyInfo(val name: String) : HierarchyNode() {
        override var missingDependencies = true
    }

    override fun build() {
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

            fun solveParentNode(className: String): HierarchyNode {
                val clazz = resourceCache.getClassNode(className)
                return if (clazz == null) {
                    Logger.error("Missing dependency $className")
                    newInfo.missingDependencies = true
                    val brokenInfo = BrokenHierarchyInfo(className)
                    hierarchyNodes[className] = brokenInfo
                    brokenInfo
                } else buildHierarchy(clazz, newInfo)
            }

            // SuperName
            newInfo.superName?.let { superName ->
                newInfo.parents.add(solveParentNode(superName))
            }

            // Interfaces
            for (itf in newInfo.interfaces) {
                newInfo.parents.add(solveParentNode(itf))
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
            for (parent in it.parents) {
                parent.children.add(it)
            }
        }
    }

    private fun fillParentsInfo() {
        hierarchies.values.forEach {
            fun iterateParents(current: HierarchyNode): Set<HierarchyNode> {
                if (!current.iterated) {
                    current.iterated = true
                    val parents = mutableSetOf<HierarchyNode>()
                    for (p in current.parents) {
                        if (p is HierarchyInfo && p.missingDependencies)
                            current.missingDependencies = true
                        parents.addAll(iterateParents(p))
                    }
                    current.parents.addAll(parents)
                }
                return current.parents
            }
            iterateParents(it)
        }
    }

    override fun isSubType(child: String, father: String): Boolean {
        val childInfo = hierarchyNodes[child] ?: return false
        val fatherInfo = hierarchyNodes[father] ?: return false
        return if (childInfo.parents.contains(fatherInfo)) true
        else fatherInfo.children.contains(childInfo)
    }

}