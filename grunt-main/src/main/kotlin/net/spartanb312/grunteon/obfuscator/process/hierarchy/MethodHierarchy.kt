package net.spartanb312.grunteon.obfuscator.process.hierarchy

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import org.objectweb.asm.tree.MethodNode
import java.util.function.ToIntFunction

/**
 * Method hierarchy built on top of class hierarchy, data is stored in parallel array for better performance.
 *
 * It uses internal method indices to read data
 */
class MethodHierarchy(
    /**
     * Class hierarchy this method hierarchy is built on
     */
    val classHierarchy: ClassHierarchy,
    /**
     * All method nodes in this method hierarchy, indexed by internal method index
     */
    val methodNodes: Array<MethodNode>,
    /**
     * Owner class index for each method, indexed by internal method index
     */
    val methodOwners: IntArray,
    /**
     * Class node methods, indexed by class index, returns internal method indices of the class's methods
     */
    val classNodeMethods: Array<IntArray>,
    /**
     * Lookup for methods in a class, indexed by class index, then by method code, returns internal method index
     */
    val classNodeMethodCodeMethodLookup: Array<Int2IntOpenHashMap>,
    /**
     * Lookup for source methods of a method, indexed by method index, returns source methods' internal indices
     */
    val methodToSource: Array<EntryArray>,
    /**
     * Whether a method is a source method, indexed by internal method index
     *
     * A source method is a method that is either private or has no parent method (not inherited from any parent class).
     * Each source method is the root of a method tree.
     */
    val isSourceMethod: BooleanArray,
    /**
     * All source method indices, indexed by method index, returns source method index
     */
    val sourceMethodIndexLookUp: Int2IntMap,
    /**
     * All source methods (aka. method tree roots), indexed by source method index, returns internal method index
     */
    val sourceMethods: EntryArray,
    /**
     * Method tree indices for each connected component, indexed by source method index,
     * returns indices of method trees in the connected component. Backing arrays are shared
     * read-only snapshot data; consumers must not modify them.
     */
    val sourceMethodConnectedComponents: Array<EntryArray>,
    /**
     * Each source method's overrides, indexed by source method index, returns internal method indices of the override methods
     */
    val sourceMethodOverrides: Array<EntryArray>,
    val methodCodeLookup: Object2IntOpenHashMap<String>,
    val methodToMethodCode: IntArray,
    val methodCodeToMethods: Array<EntryArray>,
    val descCodeLookup: Object2IntOpenHashMap<String>,
    val descCode: IntArray
) {
    /**
     * Validate entry using .isValid before using the returned entry
     */
    fun findMethod(className: String, methodName: String, methodDesc: String): Entry {
        val classIdx = classHierarchy.findClass(className)
        if (classIdx == -1) return Entry.INVALID
        return findMethod(classIdx, methodName, methodDesc)
    }

    /**
     * Validate entry using .isValid before using the returned entry
     */
    fun findMethod(classIdx: Int, methodName: String, methodDesc: String): Entry {
        val codename = methodName + methodDesc
        val methodCode = methodCodeLookup.getInt(codename)
        if (methodCode == -1) return Entry.INVALID
        val methodIdx = classNodeMethodCodeMethodLookup[classIdx].get(methodCode)
        return Entry(methodIdx)
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
        val isValid: Boolean get() = index != -1

        context(mh: MethodHierarchy, ch: ClassHierarchy)
        inline val full: String get() = "${owner.name}.$name$desc"

        context(mh: MethodHierarchy)
        inline val name: String get() = mh.methodNodes[index].name

        context(mh: MethodHierarchy)
        inline val desc: String get() = mh.methodNodes[index].desc

        context(mh: MethodHierarchy)
        inline val descCode: Int get() = mh.descCode[index]

        context(mh: MethodHierarchy)
        inline val owner: ClassHierarchy.Entry get() = ClassHierarchy.Entry(mh.methodOwners[index])

        context(mh: MethodHierarchy)
        inline val node: MethodNode get() = mh.methodNodes[index]

        context(mh: MethodHierarchy)
        inline val access: Int get() = mh.methodNodes[index].access

        context(mh: MethodHierarchy)
        inline val isSourceMethod: Boolean get() = mh.isSourceMethod[index]

        context(mh: MethodHierarchy)
        inline val connectedComponent: EntryArray
            get() {
                val sourceMethodIdx = mh.sourceMethodIndexLookUp.get(index)
                if (sourceMethodIdx == -1) return EntryArray.EMPTY
                return mh.sourceMethodConnectedComponents[sourceMethodIdx]
            }

        context(mh: MethodHierarchy)
        inline val sourceMethods: EntryArray get() = mh.methodToSource[index]

        /**
         * Get all override methods of this source method, empty if this method is not a source method
         */
        context(mh: MethodHierarchy)
        val overrideMethods: EntryArray
            get() {
                val sourceMethodIdx = mh.sourceMethodIndexLookUp.get(index)
                if (sourceMethodIdx == -1) return EntryArray.EMPTY
                return mh.sourceMethodOverrides[sourceMethodIdx]
            }

        context(mh: MethodHierarchy)
        val methodCode: Int get() = mh.methodToMethodCode[index]

        companion object {
            val INVALID = Entry(-1)
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun build(classHierarchy: ClassHierarchy): MethodHierarchy {
            val realClassCount = classHierarchy.realClassCount

            // Phase 1: Collect method nodes and build per-class lookup tables.
            // Note: classMethodNodeLookup (Object2IntOpenHashMap<MethodNodeKey>) from the old
            // implementation is intentionally omitted – it was never read after being populated.
            val methodNodes = ObjectArrayList<MethodNode>(realClassCount)
            val methodOwner = IntArrayList(realClassCount)
            val classToMethod = arrayOfNulls<IntArrayList>(realClassCount) as Array<IntArrayList>

            for (i in 0..<realClassCount) {
                val classNode = classHierarchy.classNodes[i]
                val methods = classNode.methods ?: emptyList()
                val methodList = IntArrayList(methods.size)
                classToMethod[i] = methodList
                for (j in methods.indices) {
                    val methodNode = methods[j]
                    if (methodNode.isInitializer) continue
                    val index = methodNodes.size
                    methodNodes.add(methodNode)
                    methodOwner.add(i)
                    methodList.add(index)
                }
            }

            val classNodeMethodCodeMethodLookup = Array(realClassCount) {
                Int2IntOpenHashMap().apply { defaultReturnValue(-1) }
            }
            val methodCodeLookup = Object2IntOpenHashMap<String>()
            methodCodeLookup.defaultReturnValue(-1)
            val methodCodeToMethods = ObjectArrayList<IntArrayList>()

            val descCodeLookup = Object2IntOpenHashMap<String>()
            descCodeLookup.defaultReturnValue(-1)

            val methodCount = methodNodes.size
            val methodCode = IntArray(methodCount)
            val methodAccess = IntArray(methodCount)

            val descCode = IntArray(methodCount)

            for (methodIdx in 0..<methodCount) {
                val methodNode = methodNodes[methodIdx]
                val codename = methodNode.name + methodNode.desc
                val mc = methodCodeLookup.computeIfAbsent(codename, ToIntFunction {
                    methodCodeToMethods.add(IntArrayList())
                    methodCodeLookup.size
                })
                methodCode[methodIdx] = mc
                methodAccess[methodIdx] = methodNode.access
                methodCodeToMethods[mc].add(methodIdx)
                classNodeMethodCodeMethodLookup[methodOwner.getInt(methodIdx)][mc] = methodIdx

                val dc = descCodeLookup.computeIfAbsent(methodNode.desc, ToIntFunction {
                    descCodeLookup.size
                })
                descCode[methodIdx] = dc
            }

            // Phase 2: Topological sort of class indices (ancestors before descendants) using
            // Kahn's algorithm, so we can propagate inheritance bottom-up in one forward pass.
            val inDegree = IntArray(realClassCount)
            for (i in 0..<realClassCount) {
                for (parentIdx in classHierarchy.parents[i]) {
                    if (parentIdx < realClassCount) inDegree[i]++
                }
            }
            val topoQueue = IntArrayList(realClassCount)
            var topoHead = 0
            for (i in 0..<realClassCount) {
                if (inDegree[i] == 0) topoQueue.add(i)
            }
            while (topoHead < topoQueue.size) {
                val classIdx = topoQueue.getInt(topoHead++)
                for (childIdx in classHierarchy.children[classIdx]) {
                    if (childIdx < realClassCount && --inDegree[childIdx] == 0) {
                        topoQueue.add(childIdx)
                    }
                }
            }
            // Fallback for any classes in a cycle (shouldn't occur in valid JVM bytecode).
            if (topoQueue.size < realClassCount) {
                for (i in 0..<realClassCount) {
                    if (inDegree[i] > 0) topoQueue.add(i)
                }
            }

            // Phase 3: Union-Find on method indices with path compression + union-by-rank.
            // Replaces the old O(M × D_avg) broadcast + recursive DFS approach.
            val uf = IntArray(methodCount) { it }
            val ufRank = IntArray(methodCount)

            fun find(x: Int): Int {
                var root = x
                while (uf[root] != root) root = uf[root]
                // Path compression
                var cur = x
                while (cur != root) {
                    val next = uf[cur]; uf[cur] = root; cur = next
                }
                return root
            }

            fun union(x: Int, y: Int) {
                val rx = find(x);
                val ry = find(y)
                if (rx == ry) return
                if (ufRank[rx] < ufRank[ry]) uf[rx] = ry
                else if (ufRank[rx] > ufRank[ry]) uf[ry] = rx
                else {
                    uf[ry] = rx; ufRank[rx]++
                }
            }

            val isSourceMethod = BooleanArray(methodCount)
            // Private and static methods are always source methods (they don't participate in virtual dispatch / inheritance).
            for (i in 0..<methodCount) {
                if (methodAccess[i].isPrivate || methodAccess[i].isStatic) isSourceMethod[i] = true
            }

            // effectiveMethod[classIdx][mc] = representative method index for the (class, methodCode)
            // pair, reflecting the most-derived non-private declaration reachable from this class.
            // Propagated in topological order so parents are always ready when a child is processed.
            val effectiveMethod = Array(realClassCount) {
                Int2IntOpenHashMap().apply { defaultReturnValue(-1) }
            }

            for (t in 0..<topoQueue.size) {
                val classIdx = topoQueue.getInt(t)

                // Step A: inherit effective methods from real-class parents.
                for (pi in classHierarchy.parents[classIdx].indices) {
                    val parentIdx = classHierarchy.parents[classIdx][pi]
                    if (parentIdx >= realClassCount) continue
                    val parentEff = effectiveMethod[parentIdx]
                    val iter = parentEff.int2IntEntrySet().fastIterator()
                    while (iter.hasNext()) {
                        val entry = iter.next()
                        val mc = entry.intKey
                        val parentRep = entry.intValue
                        val existing = effectiveMethod[classIdx].get(mc)
                        if (existing == -1) {
                            effectiveMethod[classIdx].put(mc, parentRep)
                        } else {
                            // Two parents both provide mc → union their trees.
                            val rx = find(existing);
                            val ry = find(parentRep)
                            if (rx != ry) union(rx, ry)
                            effectiveMethod[classIdx].put(mc, find(rx))
                        }
                    }
                }

                // Step B: process this class's own non-private methods.
                val myMethods = classToMethod[classIdx]
                val myMethodArray = myMethods.elements()
                for (j in 0..<myMethods.size) {
                    val methodIdx = myMethodArray[j]
                    if (methodAccess[methodIdx].isPrivate || methodAccess[methodIdx].isStatic) continue
                    val mc = methodCode[methodIdx]
                    val existing = effectiveMethod[classIdx].get(mc)
                    // Source if no ancestor already provides this method code.
                    isSourceMethod[methodIdx] = existing == -1
                    if (existing != -1) {
                        val rx = find(existing);
                        val ry = find(methodIdx)
                        if (rx != ry) union(rx, ry)
                    }
                    effectiveMethod[classIdx].put(mc, find(methodIdx))
                }
            }

            // Phase 4: Collect source methods and build connected components via union-find roots.
            val sourceMethodList = IntArrayList()
            val sourceMethodIndexLookUp = Int2IntOpenHashMap(methodCount).apply { defaultReturnValue(-1) }
            for (i in 0..<methodCount) {
                if (isSourceMethod[i]) {
                    sourceMethodIndexLookUp[i] = sourceMethodList.size
                    sourceMethodList.add(i)
                }
            }
            val sourceCount = sourceMethodList.size

            // Group source methods by their union-find root → one group per connected component.
            val rootToCC = Int2IntOpenHashMap().apply { defaultReturnValue(-1) }
            val ccMethodIndices = ObjectArrayList<IntArrayList>()
            for (sourceIdx in 0..<sourceCount) {
                val methodIdx = sourceMethodList.getInt(sourceIdx)
                val root = find(methodIdx)
                val ccIdx = rootToCC.get(root)
                if (ccIdx == -1) {
                    rootToCC[root] = ccMethodIndices.size
                    val newCC = IntArrayList()
                    newCC.add(methodIdx)
                    ccMethodIndices.add(newCC)
                } else {
                    ccMethodIndices[ccIdx].add(methodIdx)
                }
            }

            // Build once per component; these arrays are read-only hierarchy snapshots.
            val connectedComponents = Array(ccMethodIndices.size) { EntryArray(ccMethodIndices[it].toIntArray()) }
            val sourceMethodConnectedComponents = Array(sourceCount) { sourceIdx ->
                val methodIdx = sourceMethodList.getInt(sourceIdx)
                val root = find(methodIdx)
                connectedComponents[rootToCC.get(root)]
            }

            // Phase 5: Build sourceMethodOverrides and methodToSource by iterating each source
            // method's descendants (avoids the old O(M × D) broadcast structure entirely).
            val sourceMethodOverrideBuilders = Array(sourceCount) { IntArrayList() }
            val methodToSourceBuilders = Array(methodCount) { IntArrayList() }

            for (sourceIdx in 0..<sourceCount) {
                val sourceMethodIdx = sourceMethodList.getInt(sourceIdx)
                // Static and private methods cannot be overridden; skip looking for descendants.
                if (methodAccess[sourceMethodIdx].isPrivate || methodAccess[sourceMethodIdx].isStatic) continue
                val ownerIdx = methodOwner.getInt(sourceMethodIdx)
                val mc = methodCode[sourceMethodIdx]
                for (desc in classHierarchy.descendants[ownerIdx]) {
                    if (desc >= realClassCount) continue
                    val overrideMethodIdx = classNodeMethodCodeMethodLookup[desc].get(mc)
                    if (overrideMethodIdx == -1) continue
                    if (methodAccess[overrideMethodIdx].isPrivate || methodAccess[overrideMethodIdx].isStatic) continue
                    sourceMethodOverrideBuilders[sourceIdx].add(overrideMethodIdx)
                    methodToSourceBuilders[overrideMethodIdx].add(sourceMethodIdx)
                }
            }

            return MethodHierarchy(
                classHierarchy,
                methodNodes.toTypedArray(),
                methodOwner.toIntArray(),
                Array(realClassCount) { classToMethod[it].toIntArray() },
                classNodeMethodCodeMethodLookup,
                Array(methodCount) { EntryArray(methodToSourceBuilders[it].toIntArray()) },
                isSourceMethod,
                sourceMethodIndexLookUp,
                EntryArray(sourceMethodList.toIntArray()),
                sourceMethodConnectedComponents,
                Array(sourceCount) { EntryArray(sourceMethodOverrideBuilders[it].toIntArray()) },
                methodCodeLookup,
                methodCode,
                Array(methodCodeToMethods.size) { EntryArray(methodCodeToMethods[it].toIntArray()) },
                descCodeLookup,
                descCode
            )
        }
    }
}