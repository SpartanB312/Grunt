package net.spartanb312.grunt.ir.jvm

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import java.util.IdentityHashMap

/** Immutable, operation-local indices: importing never mutates the instruction list. */
internal class JvmInstructionIndex private constructor(
    private val nodes: List<AbstractInsnNode>
) : List<AbstractInsnNode> by nodes {
    constructor(instructions: InsnList) : this(instructions.toArray().asList())

    private val positions = IdentityHashMap<AbstractInsnNode, Int>(nodes.size)
    private val nextExecutable = IntArray(nodes.size + 1) { -1 }

    init {
        for (index in nodes.indices.reversed()) {
            positions[nodes[index]] = index
            nextExecutable[index] = if (nodes[index].opcode >= 0) index else nextExecutable[index + 1]
        }
    }

    override fun indexOf(element: AbstractInsnNode): Int = positions[element] ?: -1

    fun nextExecutableIndex(start: Int): Int? = nextExecutable.getOrNull(start)?.takeIf { it >= 0 }
}

/** Blocks are in ascending instruction-start order, with non-overlapping intervals. */
internal class JvmBlockIndex<T>(
    private val blocks: List<T>,
    startOf: (T) -> Int
) : List<T> by blocks {
    private val starts = IntArray(blocks.size) { startOf(blocks[it]) }
    private val byStart = blocks.associateBy(startOf)

    fun atStart(start: Int): T? = byStart[start]

    fun between(start: Int, end: Int): List<T> =
        if (end <= start) emptyList() else blocks.subList(lowerBound(start), lowerBound(end))

    private fun lowerBound(value: Int): Int {
        var low = 0
        var high = starts.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (starts[middle] < value) low = middle + 1 else high = middle
        }
        return low
    }
}