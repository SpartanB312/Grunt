package net.spartanb312.grunteon.obfuscator.process.hierarchy

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.util.*
import java.util.function.ToIntFunction

/**
 * Class hierarchy info, data is stored in parallel array for better performance.
 *
 * It uses internal class indices to read data
 */
class ClassHierarchy(
    /**
     * All classNode, indexed by internal class index
     */
    val classNodes: Array<ClassNode>,
    /**
     * Class names, indexed by internal class index
     */
    val classNames: Array<String>,
    /**
     * Internal class index looked up by class name, -1 if not found
     */
    val classNameLookUp: Object2IntOpenHashMap<String>,
    /**
     * Direct parents indices, indexed by internal class index
     */
    val parents: Array<IntArray>,
    /**
     * Direct children indices, indexed by internal class index
     */
    val children: Array<IntArray>,
    /**
     * All ancestors indices, indexed by internal class index, including direct parents and indirect parents
     */
    val ancestors: Array<IntArray>,
    /**
     * All descendants indices, indexed by internal class indexm including direct children and indirect children
     */
    val descendants: Array<IntArray>,
    /**
     * All ancestors indices, indexed by internal class index, including direct parents and indirect parents
     */
    val ancestorsSet: Array<IntOpenHashSet>,
    /**
     * All descendants indices, indexed by internal class indexm including direct children and indirect children
     */
    val descendantsSet: Array<IntOpenHashSet>,
    /**
     * Whether the class is broken (missing dependency), indexed by internal class index
     */
    val broken: BooleanArray,
    /**
     * Whether the class itself is broken or any of its ancestors is broken, indexed by internal class index
     */
    val missingDependencies: BooleanArray,
    /**
     * Number of classes in the input, excluding phantom classes added by lookup function
     */
    val realClassCount: Int,
    /**
     * Total number of classes, including phantom classes added by lookup function
     */
    val classCount: Int
) {
    /**
     * Find internal class index by class name, return -1 if not found
     */
    fun findClass(className: String): Int {
        return classNameLookUp.getInt(className)
    }

    /**
     * Validate entry using .isValid before using the returned entry
     */
    fun findClassEntry(className: String): Entry {
        return Entry(classNameLookUp.getInt(className))
    }

    // subtype
    fun isSubType(child: ClassNode, father: ClassNode): Boolean {
        return isSubType(child.name, father.name)
    }

    fun isSubType(child: String, father: String): Boolean {
        if (child == father) return true
        val childInfo = findClass(child)
        val fatherInfo = findClass(father)
        return isSubType(childInfo, fatherInfo)
    }

    fun isSubType(child: Int, father: Int): Boolean {
        if (child == -1 || father == -1) return false
        if (child == father) return true
        //assert(descendantsSet[father].contains(child) == ancestorsSet[child].contains(father))
        return ancestorsSet[child].contains(father)
    }

    // missing dependencies
    context(instance: Grunteon)
    fun printMissing(printAffected: Boolean = true) {
        val inputKeys = instance.workRes.inputClassMap.keys
        for (index in classNodes.indices) {
            if (broken[index]) {
                val dependency = classNames[index]
                Logger.error("Missing ancestor $dependency")
                if (printAffected) {
                    descendants[index].forEach { des ->
                        val name = classNames[des]
                        if (name in inputKeys) Logger.error("   Required by $name")
                        else Logger.warn("    Required by $name")
                    }
                }
            }
        }
    }

    // reference search
    context(instance: Grunteon)
    fun checkMissing(classNode: ClassNode): Set<String> {
        val missingReference = mutableSetOf<String>()
        for (method in classNode.methods) {
            missingReference.addAll(checkMissing(method))
        }
        return missingReference
    }

    context(instance: Grunteon)
    fun checkMissing(methodNode: MethodNode): Set<String> {
        val missingReference = mutableSetOf<String>()
        methodNode.instructions.forEach { insn ->
            if (insn is FieldInsnNode) {
                val name = if (!insn.owner.startsWith("[")) insn.owner
                else insn.owner.substringAfterLast("[").removePrefix("L").removeSuffix(";")
                if (name in primitiveTypes) return@forEach
                val info = findClass(name)
                val node = instance.workRes.getClassNode(name)
                if (info == -1) {
                    //println("Missing $name")
                    if (node == null) missingReference.add(name)
                } else if (broken[info]) {
                    //println("Broken $name")
                    missingReference.add(name)
                }
            }
            if (insn is MethodInsnNode) {
                val name = if (!insn.owner.startsWith("[")) insn.owner
                else insn.owner.substringAfterLast("[").removePrefix("L").removeSuffix(";")
                if (name in primitiveTypes) return@forEach
                val info = findClass(name)
                val node = instance.workRes.getClassNode(name)
                if (info == -1) {
                    //println("Missing $name")
                    if (node == null) missingReference.add(name)
                } else if (broken[info]) {
                    //println("Broken $name")
                    missingReference.add(name)
                }
            }
        }
        return missingReference
    }

    @JvmInline
    value class EntryArray(val array: IntArray) {
        inline val size get() = array.size

        operator fun get(index: Int): Entry {
            return Entry(array[index])
        }

        inline fun forEach(action: (Entry) -> Unit) {
            for (i in array.indices) {
                action(Entry(array[i]))
            }
        }

        inline fun any(predicate: (Entry) -> Boolean): Boolean {
            return array.any { predicate(Entry(it)) }
        }

        companion object {
            val EMPTY = EntryArray(IntArray(0))
        }
    }

    @JvmInline
    value class Entry(val index: Int) {
        val isValid get() = index != -1

        context(ch: ClassHierarchy)
        val classNode get() = ch.classNodes[index]

        context(ch: ClassHierarchy)
        val name get() = ch.classNames[index]

        context(ch: ClassHierarchy)
        val parents
            get() =
                if (index >= ch.realClassCount) EMPTY_INT_ARRAY else ch.parents[index]

        context(ch: ClassHierarchy)
        val superClass
            get() = parents[0]

        context(ch: ClassHierarchy)
        val children
            get() =
                if (index >= ch.realClassCount) EMPTY_INT_ARRAY else ch.children[index]

        context(ch: ClassHierarchy)
        val ancestors
            get() =
                if (index >= ch.realClassCount) EMPTY_INT_ARRAY else ch.ancestors[index]

        context(ch: ClassHierarchy)
        val descendants
            get() =
                if (index >= ch.realClassCount) EntryArray.EMPTY else EntryArray(ch.descendants[index])

        context(ch: ClassHierarchy)
        val isBroken get() = ch.broken[index]

        context(ch: ClassHierarchy)
        val hasMissingDependency get() = ch.missingDependencies[index]

        context(fh: FieldHierarchy)
        val fields: FieldHierarchy.EntryArray
            get() =
                if (index >= fh.classHierarchy.realClassCount) FieldHierarchy.EntryArray.EMPTY
                else FieldHierarchy.EntryArray(fh.classNodeFields[index])

        context(mh: MethodHierarchy)
        val methods: MethodHierarchy.EntryArray
            get() =
                if (index >= mh.classHierarchy.realClassCount) return MethodHierarchy.EntryArray.EMPTY
                else MethodHierarchy.EntryArray(mh.classNodeMethods[index])

        context(mh: MethodHierarchy)
        fun findMethod(name: String, desc: String): MethodHierarchy.Entry {
            val codename = "$name$desc"
            val methodCode = mh.methodCodeLookup.getInt(codename)
            if (methodCode == -1) return MethodHierarchy.Entry.INVALID
            return findMethod(methodCode)
        }

        context(mh: MethodHierarchy)
        fun findMethod(methodCode: Int): MethodHierarchy.Entry {
            return MethodHierarchy.Entry(mh.classNodeMethodCodeMethodLookup[index][methodCode])
        }

        companion object {
            val EMPTY_INT_ARRAY = IntArray(0)
        }
    }


    companion object {
        const val JAVA_OBJECT = "java/lang/Object"
        val MISSING_CLASSNODE = ClassNode()

        private val primitiveTypes = arrayOf("B", "C", "D", "F", "I", "J", "S", "Z", "V")

        private fun IntArray.distinctCount(): Int {
            val set = IntOpenHashSet(this)
            for (i in 0..<size) {
                set.add(get(i))
            }
            return set.size
        }

        private fun IntArrayList.distinctIntArray(): IntArray {
            val set = IntOpenHashSet(this)
            for (i in 0..<size) {
                set.add(getInt(i))
            }
            return set.toIntArray()
        }

        @OptIn(ExperimentalStdlibApi::class)
        @Suppress("UNCHECKED_CAST")
        fun build(inputClassNodes: Collection<ClassNode>, lookup: ((String) -> ClassNode?)? = null): ClassHierarchy {
            val emptyIntArray = IntArray(0)
            val arr = inputClassNodes.toTypedArray()
            Arrays.sort(arr, compareBy { it.name })
            val classNodes = ObjectArrayList.wrap(arr)
            val classNameLookUp = Object2IntOpenHashMap<String>()
            classNameLookUp.defaultReturnValue(-1)
            var realClassCount = classNodes.size
            val classNames = ObjectArrayList<String>(realClassCount)

            for (i in 0..<realClassCount) {
                val myName = classNodes[i].name
                classNameLookUp[myName] = i
                assert(classNames.size == i)
                classNames.add(myName)
            }

            assert(realClassCount == classNodes.size)
            assert(classNames.size == classNodes.size)

            if (lookup != null) {
                fun addRemainingAncestorsRecursively(node: ClassNode) {
                    val superName = node.superName ?: JAVA_OBJECT
                    if (!classNameLookUp.containsKey(superName)) {
                        lookup(superName)?.let {
                            classNodes.add(it)
                            classNameLookUp[superName] = classNodes.size - 1
                            classNames.add(superName)
                            addRemainingAncestorsRecursively(it)
                        }
                    }
                    val interfaces = node.interfaces ?: emptyList()
                    for (j in 0 until interfaces.size) {
                        val interfaceName = interfaces[j]
                        if (!classNameLookUp.containsKey(interfaceName)) {
                            lookup(interfaceName)?.let {
                                classNodes.add(it)
                                classNameLookUp[interfaceName] = classNodes.size - 1
                                classNames.add(interfaceName)
                                addRemainingAncestorsRecursively(it)
                            }
                        }
                    }
                }

                for (i in 0..<classNodes.size) {
                    addRemainingAncestorsRecursively(classNodes[i])
                }

                realClassCount = classNodes.size
                assert(realClassCount >= inputClassNodes.size)
                assert(classNames.size == classNodes.size)
            }

            var classCount = realClassCount
            val phantomClassHandle = ToIntFunction<String> {
                val newIdx = classCount++
                assert(newIdx == classNames.size)
                classNames.add(it)
                classNodes.add(MISSING_CLASSNODE)
                newIdx
            }

            var parents = arrayOfNulls<IntArray>(realClassCount) as Array<IntArray>

            for (i in 0..<realClassCount) {
                val classNode = classNodes[i]
                if (classNode.name == JAVA_OBJECT) {
                    parents[i] = emptyIntArray
                    continue
                }
                val interfaces = classNode.interfaces ?: emptyList()
                val parentCount = 1 + interfaces.size
                val parentArray = IntArray(parentCount)
                parentArray[0] = classNameLookUp.computeIfAbsent(classNode.superName ?: JAVA_OBJECT, phantomClassHandle)
                for (j in 0 until interfaces.size) {
                    parentArray[j + 1] = classNameLookUp.computeIfAbsent(interfaces[j], phantomClassHandle)
                }
                parents[i] = parentArray
            }

            assert(classCount >= realClassCount)
            assert(classNames.size == classCount)

            parents = Array(classCount) { if (it < parents.size) parents[it] else emptyIntArray }

            val children = Array(classCount) { IntArrayList() }
            val ancestors = Array(classCount) { IntArrayList(parents[it]) }
            val visited = BooleanArray(classCount)

            fun dfs(myIdx: Int) {
                if (visited[myIdx]) return
                visited[myIdx] = true
                val myParents = parents[myIdx]
                val myAncestors = ancestors[myIdx]
                for (myParentIdx in 0..<myParents.size) {
                    val parentIdx = myParents[myParentIdx]
                    dfs(parentIdx)
                    children[parentIdx].add(myIdx)
                    myAncestors.addAll(ancestors[parentIdx])
                }
            }
            for (i in 0..<classCount) {
                dfs(i)
            }
            val descendants = Array(classCount) { IntArrayList(children[it]) }

            for (i in 0..<classCount) {
                val myAncestors = ancestors[i]
                for (j in 0..<myAncestors.size) {
                    val ancestorIdx = myAncestors.getInt(j)
                    descendants[ancestorIdx].add(i)
                }
            }

            val broken = BooleanArray(classCount)
            val missingDependencies = BooleanArray(classCount)

            val finalChildren = arrayOfNulls<IntArray>(classCount) as Array<IntArray>
            val finalAncestors = arrayOfNulls<IntArray>(classCount) as Array<IntArray>
            val finalDescendants = arrayOfNulls<IntArray>(classCount) as Array<IntArray>

            for (i in 0..<classCount) {
                finalChildren[i] = children[i].toIntArray()
                assert(finalChildren[i].size == finalChildren[i].distinctCount())
                finalAncestors[i] = ancestors[i].distinctIntArray()
                finalDescendants[i] = descendants[i].distinctIntArray()
                broken[i] = classNodes[i] === MISSING_CLASSNODE
                missingDependencies[i] = broken[i] || ancestors[i].any { broken[it] }
            }

            return ClassHierarchy(
                classNodes.toTypedArray(),
                classNames.toTypedArray(),
                classNameLookUp,
                parents,
                finalChildren,
                finalAncestors,
                finalDescendants,
                Array(classCount) { IntOpenHashSet(finalAncestors[it]) },
                Array(classCount) { IntOpenHashSet(finalDescendants[it]) },
                broken,
                missingDependencies,
                realClassCount,
                classCount
            )
        }
    }
}
