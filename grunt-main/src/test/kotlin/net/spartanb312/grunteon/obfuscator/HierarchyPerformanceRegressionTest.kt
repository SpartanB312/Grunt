package net.spartanb312.grunteon.obfuscator

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy.FieldHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy.MethodHierarchy
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.syntheticOverlapEdges
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random
import kotlin.test.*

class HierarchyPerformanceRegressionTest {
    @Test
    fun layeredDiamondsKeepLegacyArrayAndSetIterationOrder() {
        for (layers in listOf(1, 4, 8, 12, 16)) {
            assertLegacyOrder(ClassHierarchy.build(diamonds(layers)))
        }
        val random = Random(7621)
        repeat(40) {
            val nodes = mutableListOf(node("java/lang/Object"))
            repeat(35) { index ->
                val parents = nodes.drop(1).shuffled(random).take(random.nextInt(4)).map { it.name }
                nodes.add(node("I" + index.toString().padStart(3, '0'), parents))
            }
            assertLegacyOrder(ClassHierarchy.build(nodes.shuffled(random)))
        }
    }

    @Test
    fun deepDiamondsOnlyRetainUniqueClosure() {
        // The old path-expanded implementation cannot build this small valid interface graph.
        val hierarchy = ClassHierarchy.build(diamonds(64))
        assertEquals(129, hierarchy.classCount)
        assertEquals(128, hierarchy.descendants[hierarchy.findClass("java/lang/Object")].size)
        assertEquals(127, hierarchy.ancestors[hierarchy.findClass("L063A")].size)
        for (i in 0 until hierarchy.classCount) {
            assertEquals(hierarchy.ancestors[i].size, hierarchy.ancestors[i].toSet().size)
            assertEquals(hierarchy.descendants[i].size, hierarchy.descendants[i].toSet().size)
            for (ancestor in hierarchy.ancestors[i]) assertTrue(hierarchy.isSubType(i, ancestor))
        }
    }

    @Test
    fun appendedMissingParentsMarkEveryAffectedClass() {
        val root = node("A", listOf("missing/Interface"))
        val child = node("B", listOf("A"))
        val hierarchy = ClassHierarchy.build(listOf(root, child, node("java/lang/Object")))
        val missing = hierarchy.findClass("missing/Interface")
        assertTrue(hierarchy.broken[missing])
        assertTrue(hierarchy.missingDependencies[hierarchy.findClass("A")])
        assertTrue(hierarchy.missingDependencies[hierarchy.findClass("B")])
        assertFalse(hierarchy.missingDependencies[hierarchy.findClass("java/lang/Object")])
        assertContentEquals(intArrayOf(), hierarchy.parents[missing])
        assertLegacyOrder(hierarchy)
    }

    @Test
    fun componentArraysAreSharedAndPrivateStaticDispatchIsSeparate() {
        val interfaces = (0 until 256).map { index ->
            node("I" + index.toString().padStart(3, '0')).apply {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "run", "()I", null, null))
            }
        }
        val impl = node("Impl", interfaces.map { it.name }).apply {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()I", null, null))
            methods.add(MethodNode(Opcodes.ACC_PRIVATE, "hidden", "()I", null, null))
            methods.add(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "staticCall", "()I", null, null))
        }
        val child = node("Sub").apply {
            superName = "Impl"
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "hidden", "()I", null, null))
            methods.add(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "staticCall", "()I", null, null))
        }
        val hierarchy = MethodHierarchy.build(ClassHierarchy.build(interfaces + impl + child + node("java/lang/Object")))
        val components = interfaces.map {
            val method = hierarchy.findMethod(it.name, "run", "()I").index
            hierarchy.sourceMethodConnectedComponents[hierarchy.sourceMethodIndexLookUp.get(method)].array
        }
        assertEquals(256, components.first().size)
        components.forEach { assertSame(components.first(), it) }
        assertEquals(256, hierarchy.methodToSource[hierarchy.findMethod("Impl", "run", "()I").index].size)
        for (owner in listOf("Impl", "Sub")) for (name in listOf("hidden", "staticCall")) {
            val method = hierarchy.findMethod(owner, name, "()I").index
            assertTrue(hierarchy.isSourceMethod[method])
            assertEquals(1, hierarchy.sourceMethodConnectedComponents[hierarchy.sourceMethodIndexLookUp.get(method)].size)
        }
    }

    @Test
    fun primitiveFieldMembershipPreservesHidingPrivateAndStaticFlags() {
        val root = node("Root").apply {
            fields.add(FieldNode(Opcodes.ACC_PUBLIC, "value", "I", null, null))
            fields.add(FieldNode(Opcodes.ACC_PRIVATE, "privateValue", "I", null, null))
            fields.add(FieldNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "staticValue", "I", null, null))
        }
        val children = (0 until 160).map { index ->
            node("Child$" + index).apply {
                superName = root.name
                fields.add(FieldNode(Opcodes.ACC_PUBLIC, "value", "I", null, null))
                fields.add(FieldNode(Opcodes.ACC_PUBLIC, "privateValue", "I", null, null))
                fields.add(FieldNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "staticValue", "I", null, null))
                fields.add(FieldNode(Opcodes.ACC_PUBLIC, "value", "J", null, null))
            }
        }
        val hierarchy = FieldHierarchy.build(ClassHierarchy.build(children + root + node("java/lang/Object")))
        for (child in children) {
            fun source(name: String, desc: String = "I") =
                hierarchy.isSourceField[hierarchy.findField(child.name, name, desc).index]
            assertFalse(source("value"))
            assertTrue(source("privateValue"))
            assertTrue(source("staticValue"))
            assertTrue(source("value", "J"))
        }
    }

    @Test
    fun sparseSyntheticEdgesKeepAllPairsUnionRootsAndComponentOrder() {
        val random = Random(973)
        repeat(100) {
            val nodes = mutableListOf(node("java/lang/Object"))
            repeat(35) { index ->
                nodes.add(node("I" + index.toString().padStart(3, '0'),
                    nodes.drop(1).shuffled(random).take(random.nextInt(5)).map { it.name }))
            }
            val hierarchy = ClassHierarchy.build(nodes)
            val owners = (0 until hierarchy.classCount).shuffled(random).take(25).toIntArray()
            val oldEdges = Array(owners.size) { IntArrayList() }
            for (i in owners.indices) for (j in i + 1 until owners.size) {
                val a = owners[i]
                val b = owners[j]
                if (b in hierarchy.descendantsSet[a] || a in hierarchy.descendantsSet[b] ||
                    hierarchy.descendants[a].any { it in hierarchy.descendantsSet[b] }) oldEdges[i].add(j)
            }
            assertContentEquals(roots(oldEdges), roots(syntheticOverlapEdges(hierarchy, owners)))
        }
        val hierarchy = ClassHierarchy.build(diamonds(32))
        val owners = (0 until hierarchy.classCount).toList().toIntArray()
        val edges = syntheticOverlapEdges(hierarchy, owners)
        assertTrue(edges.sumOf { it?.size ?: 0 } <= hierarchy.ancestors.sumOf { it.size })
    }

    private fun roots(edges: Array<out IntArrayList?>): IntArray {
        val roots = IntArray(edges.size) { it }
        fun find(value: Int): Int {
            var current = value
            while (roots[current] != current) current = roots[current]
            return current
        }
        for (i in edges.indices) for (j in edges[i] ?: continue) roots[find(j)] = find(i)
        return IntArray(edges.size) { find(it) }
    }

    private fun assertLegacyOrder(hierarchy: ClassHierarchy) {
        val ancestors = Array(hierarchy.classCount) { IntArrayList(hierarchy.parents[it]) }
        val children = Array(hierarchy.classCount) { IntArrayList() }
        val visited = BooleanArray(hierarchy.classCount)
        fun dfs(index: Int) {
            if (visited[index]) return
            visited[index] = true
            for (parent in hierarchy.parents[index]) {
                dfs(parent)
                children[parent].add(index)
                ancestors[index].addAll(ancestors[parent])
            }
        }
        for (i in visited.indices) dfs(i)
        val descendants = Array(hierarchy.classCount) { IntArrayList(children[it]) }
        for (i in ancestors.indices) for (ancestor in ancestors[i]) descendants[ancestor].add(i)
        for (i in ancestors.indices) {
            val oldAncestors = IntOpenHashSet(ancestors[i]).toIntArray()
            val oldDescendants = IntOpenHashSet(descendants[i]).toIntArray()
            assertContentEquals(oldAncestors, hierarchy.ancestors[i], "Ancestor order for " + hierarchy.classNames[i])
            assertContentEquals(oldDescendants, hierarchy.descendants[i], "Descendant order for " + hierarchy.classNames[i])
            assertContentEquals(IntOpenHashSet(oldAncestors).toIntArray(), hierarchy.ancestorsSet[i].toIntArray())
            assertContentEquals(IntOpenHashSet(oldDescendants).toIntArray(), hierarchy.descendantsSet[i].toIntArray())
        }
    }

    private fun diamonds(layers: Int): List<ClassNode> {
        val nodes = mutableListOf(node("java/lang/Object"))
        var previous = emptyList<String>()
        repeat(layers) { level ->
            val names = listOf("L" + level.toString().padStart(3, '0') + "A", "L" + level.toString().padStart(3, '0') + "B")
            names.forEach { nodes.add(node(it, previous)) }
            previous = names
        }
        return nodes
    }

    private fun node(name: String, interfaces: List<String> = emptyList()) = ClassNode().apply {
        this.name = name
        this.superName = if (name == "java/lang/Object") null else "java/lang/Object"
        this.interfaces.addAll(interfaces)
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
    }
}
