package net.spartanb312.grunteon.obfuscator.process.hierarchy

import it.unimi.dsi.fastutil.HashCommon
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
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
    context(_: Grunteon)
    fun checkMissing(classNode: ClassNode): Set<String> = checkMissingReferences(classNode.methods)

    context(_: Grunteon)
    fun checkMissing(methodNode: MethodNode): Set<String> = checkMissingReferences(listOf(methodNode))

    context(instance: Grunteon)
    private fun checkMissingReferences(methods: Iterable<MethodNode>): Set<String> {
        val owners = LinkedHashSet<String>()
        for (method in methods) {
            for (insn in method.instructions) {
                val owner = when (insn) {
                    is FieldInsnNode -> insn.owner
                    is MethodInsnNode -> insn.owner
                    else -> continue
                }
                val name = if (!owner.startsWith("[")) owner
                else owner.substringAfterLast("[").removePrefix("L").removeSuffix(";")
                if (name !in primitiveTypes) owners.add(name)
            }
        }
        val missingReference = LinkedHashSet<String>()
        for (name in owners) {
            val info = findClass(name)
            if (if (info == -1) instance.workRes.getClassMetadata(name) == null else broken[info]) {
                missingReference.add(name)
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

        // The previous path-expanded lists sized fastutil's table BEFORE deduplication.
        // Its iteration order is observable by renamers. Simulate that table sparsely when
        // diamonds make it huge, retaining the first-insertion/probing order without paths.
        private fun IntArrayList.legacyDistinctOrder(pathCount: Long): IntArray {
            val expected = minOf(pathCount, 805306368L).toInt() // Largest .75-load table.
            if (expected.toLong() <= maxOf(16L, size.toLong() * 2)) {
                val set = IntOpenHashSet(expected)
                set.addAll(this)
                return set.toIntArray()
            }
            val mask = HashCommon.arraySize(expected, 0.75f) - 1
            val occupied = Int2IntOpenHashMap(size)
            val slots = LongArray(size)
            var count = 0
            var hasZero = false
            for (i in 0..<size) {
                val value = getInt(i)
                if (value == 0) {
                    hasZero = true
                    continue
                }
                var slot = HashCommon.mix(value) and mask
                while (occupied.containsKey(slot)) slot = (slot + 1) and mask
                occupied.put(slot, value)
                slots[count++] = (slot.toLong() shl 32) or value.toLong()
            }
            Arrays.sort(slots, 0, count)
            val result = IntArray(size)
            var out = if (hasZero) 1 else 0
            for (i in count - 1 downTo 0) result[out++] = slots[i].toInt()
            return result
        }

        private fun addPathCounts(a: Long, b: Long): Long = minOf(805306368L, a + b)

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
            val ancestorPathCounts = LongArray(classCount) { parents[it].size.toLong() }
            val topologicalOrder = IntArrayList(classCount)
            val visited = BooleanArray(classCount)

            fun dfs(myIdx: Int) {
                if (visited[myIdx]) return
                visited[myIdx] = true
                val myParents = parents[myIdx]
                val myAncestors = ancestors[myIdx]
                val seenAncestors = IntOpenHashSet(myAncestors)
                for (myParentIdx in 0..<myParents.size) {
                    val parentIdx = myParents[myParentIdx]
                    dfs(parentIdx)
                    children[parentIdx].add(myIdx)
                    ancestorPathCounts[myIdx] = addPathCounts(ancestorPathCounts[myIdx], ancestorPathCounts[parentIdx])
                    for (ancestor in ancestors[parentIdx]) {
                        if (seenAncestors.add(ancestor)) myAncestors.add(ancestor)
                    }
                }
                topologicalOrder.add(myIdx)
            }
            for (i in 0..<classCount) {
                dfs(i)
            }
            val descendants = Array(classCount) { IntArrayList(children[it]) }
            val descendantSets = Array(classCount) { IntOpenHashSet(children[it]) }
            val descendantPathCounts = LongArray(classCount)
            for (t in topologicalOrder.size - 1 downTo 0) {
                val child = topologicalOrder.getInt(t)
                for (parent in parents[child]) {
                    descendantPathCounts[parent] = addPathCounts(descendantPathCounts[parent], 1 + descendantPathCounts[child])
                }
            }

            for (i in 0..<classCount) {
                val myAncestors = ancestors[i]
                for (j in 0..<myAncestors.size) {
                    val ancestorIdx = myAncestors.getInt(j)
                    if (descendantSets[ancestorIdx].add(i)) descendants[ancestorIdx].add(i)
                }
            }

            val broken = BooleanArray(classCount) { classNodes[it] === MISSING_CLASSNODE }
            val missingDependencies = BooleanArray(classCount)

            val finalChildren = arrayOfNulls<IntArray>(classCount) as Array<IntArray>
            val finalAncestors = arrayOfNulls<IntArray>(classCount) as Array<IntArray>
            val finalDescendants = arrayOfNulls<IntArray>(classCount) as Array<IntArray>

            for (i in 0..<classCount) {
                finalChildren[i] = children[i].toIntArray()
                assert(finalChildren[i].size == finalChildren[i].distinctCount())
                finalAncestors[i] = ancestors[i].legacyDistinctOrder(ancestorPathCounts[i])
                finalDescendants[i] = descendants[i].legacyDistinctOrder(
                    addPathCounts(children[i].size.toLong(), descendantPathCounts[i])
                )
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
