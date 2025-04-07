package net.spartanb312.grunt.process.hierarchy.krypton

import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.hierarchy.krypton.info.MethodInfo
import net.spartanb312.grunt.utils.extensions.isInitializer
import net.spartanb312.grunt.utils.extensions.isPrivate
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

/**
 * Full Hierarchy
 * For heavy overall renaming
 */
class HeavyHierarchy(resourceCache: ResourceCache) : Hierarchy(resourceCache) {

    override val size get() = classInfos.size + classInfos.values.sumOf { it.methods.size + it.fields.size }

    fun buildFieldFast() = super.buildField()
    fun buildMethodFast() = super.buildMethod()

    override fun buildField() {
        super.buildField()
        fillFieldHierarchyInfo()
        findFieldSource()
    }

    // WARNING: VERY SLOW!!!
    override fun buildMethod() {
        super.buildMethod()
        fillVirtualMethod()
        fillMethodHierarchyInfo()
        findMethodSource()
        findMethodCompetitor()
        findMethodRelated()
    }

    private fun fillVirtualMethod() {
        classInfos.values.forEach { classInfo ->
            classInfo.methods.forEach { methodInfo ->
                if (methodInfo.isSourceMethod && !methodInfo.owner.missingDependencies) {
                    methodInfo.owner.children.forEach { childClass ->
                        if (childClass.methods.none { it.code == methodInfo.code }) { // it.desc == methodInfo.desc && it.name == methodInfo.name
                            // add virtual
                            val virtualNode = MethodNode(
                                Opcodes.ACC_PUBLIC,
                                methodInfo.name,
                                methodInfo.desc,
                                methodInfo.methodNode.signature,
                                null,
                            )
                            val virtualMethodInfo = MethodInfo(childClass, virtualNode, methodCoder, true)
                            childClass.methods.add(virtualMethodInfo)
                            methodInfo.children.add(virtualMethodInfo)
                            virtualMethodInfo.parents.add(methodInfo)
                        }
                    }
                }
            }
        }
    }

    private fun fillFieldHierarchyInfo() {
        classInfos.values.forEach { classInfo ->
            classInfo.fields.forEach { fieldInfo ->
                val fieldNode = fieldInfo.fieldNode
                if (!fieldNode.isPrivate) {
                    // Up check
                    classInfo.children.forEach { child ->
                        child.fields.forEach { childField ->
                            val childFieldNode = childField.fieldNode
                            // Only public field may inherit from this field
                            if (!childFieldNode.isPrivate/* && !childFieldNode.isProtected*/) {
                                val fieldName = childFieldNode.name
                                val desc = childFieldNode.desc
                                if (fieldNode.desc == desc && fieldNode.name == fieldName) {
                                    fieldInfo.children.add(childField)
                                }
                            }
                        }
                    }
                    // Down check
                    classInfo.parents.forEach { parent ->
                        parent.fields.forEach { parentField ->
                            val parentFieldNode = parentField.fieldNode
                            if (!parentFieldNode.isPrivate) {
                                val fieldName = parentFieldNode.name
                                val desc = parentFieldNode.desc
                                if (fieldNode.desc == desc && fieldNode.name == fieldName) {
                                    fieldInfo.parents.add(parentField)
                                }
                            }
                        }
                    }
                }
                fieldInfo.filled = true
            }
        }
    }

    private fun fillMethodHierarchyInfo() {
        for (classInfo in classInfos.values.toTypedArray()) {
            for (methodInfo in classInfo.methods) {
                val methodNode = methodInfo.methodNode
                if (!methodNode.isPrivate && !methodNode.isInitializer) {
                    // Up check
                    if (!methodInfo.virtual) { // Virtual method can't be a parent
                        for (child in classInfo.children) {
                            for (childMethod in child.methods) {
                                if (!childMethod.virtual) {
                                    val childMethodNode = childMethod.methodNode
                                    // Only public field may inherit from this method
                                    if (!childMethodNode.isPrivate/* && !childMethodNode.isProtected*/) {
                                        val methodName = childMethodNode.name
                                        val desc = childMethodNode.desc
                                        if (methodNode.desc == desc && methodNode.name == methodName) {
                                            methodInfo.children.add(childMethod)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Down check
//                    if (!methodNode.isProtected) {
                    for (parent in classInfo.parents) {
                        for (parentMethod in parent.methods) {
                            if (!parentMethod.virtual) { // Virtual method can't be a parent
                                val parentMethodNode = parentMethod.methodNode
                                if (!parentMethodNode.isPrivate) {
                                    val methodName = parentMethodNode.name
                                    val desc = parentMethodNode.desc
                                    if (methodNode.desc == desc && methodNode.name == methodName) {
                                        methodInfo.parents.add(parentMethod)
                                    }
                                }
                            }
                        }
                    }
//                    }
                }
                methodInfo.filled = true
            }
        }
    }

    private fun findFieldSource() {
        classInfos.values.forEach { classInfo ->
            classInfo.fields.forEach { fieldInfo ->
                if (!fieldInfo.isSourceField) {
                    val candidate = fieldInfo.parents.filter { it.isSourceField }
                    candidate.forEach { t1 ->
                        var flag = true
                        candidate.forEach { t2 ->
                            if (t2 != t1) {
                                if (!isSubType(t2.owner, t1.owner)) flag = false
                            }
                        }
                        if (flag) fieldInfo.source = t1
                    }
                } else fieldInfo.source = fieldInfo
            }
        }
    }

    private fun findMethodSource() {
        classInfos.values.forEach { classInfo ->
            classInfo.methods.forEach { methodInfo ->
                if (!methodInfo.isSourceMethod) {
                    val candidate = methodInfo.parents.filter { it.isSourceMethod }
                    candidate.forEach { t1 ->
                        var flag1 = true
                        var flag2 = true
                        candidate.forEach { t2 ->
                            if (t2 != t1) {
                                if (getCommonSuperClass(t1.owner, t2.owner) != null) flag1 = false
                                if (!isSubType(t2.owner, t1.owner)) flag2 = false
                            }
                        }
                        if (flag1 || flag2) methodInfo.sources.add(t1)
                    }
                    if (methodInfo.sources.isEmpty()) {
                        Logger.error("Unknown error encountered. Malformed class files affected ${classInfo.name}")
                        methodInfo.sources.add(methodInfo)
                    }
                } else methodInfo.sources.add(methodInfo)
            }
        }
    }

    private fun findMethodCompetitor() {
        classInfos.values.forEach { classInfo ->
            classInfo.methods.forEach { methodInfo ->
                if (methodInfo.isSourceMethod && methodInfo.children.isNotEmpty()) {
                    methodInfo.children.forEach { child ->
                        if (child.multiSource) {
                            child.sources.forEach { otherSource ->
                                val competitorCandidate = mutableListOf<MethodInfo>()
                                if (otherSource != methodInfo
                                    && getCommonSuperClass(otherSource.owner, methodInfo.owner) == null
                                ) competitorCandidate.add(otherSource)
                                competitorCandidate.forEach { t1 ->
                                    var flag1 = true
                                    var flag2 = true
                                    competitorCandidate.forEach { t2 ->
                                        if (t2 != t1) {
                                            if (getCommonSuperClass(t1.owner, t2.owner) != null) flag1 = false
                                            if (!isSubType(t2.owner, t1.owner)) flag2 = false
                                        }
                                    }
                                    if (flag1 || flag2) {
                                        methodInfo.competitors.add(t1)
                                        t1.competitors.add(methodInfo)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findMethodRelated() {
        classInfos.values.forEach { classInfo ->
            classInfo.methods.forEach { methodInfo ->
                if (methodInfo.competitors.isNotEmpty()) {
                    val visited = mutableSetOf<MethodInfo>()
                    fun visit(methodInfo: MethodInfo) {
                        if (!visited.contains(methodInfo)) {
                            visited.add(methodInfo)
                            // Visit all competitors
                            methodInfo.competitors.forEach {
                                visit(it)
                            }
                        }
                    }
                    visit(methodInfo)
                    methodInfo.relatedMethods.addAll(visited)
                }
            }
        }
    }

}