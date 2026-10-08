package net.spartanb312.grunt.ir.ssa.transform

import net.spartanb312.grunt.ir.ssa.core.*

/**
 * Def/use and normal-CFG indices for one flattening operation. Every rewritten or inserted block
 * must be refreshed before the next region. No analysis state escapes the operation.
 */
internal class SSARegionAnalysis(function: SSAFunction) {
    val parameterIds = function.parameters.mapTo(mutableSetOf()) { it.id }
    val exceptionHandlers = function.exceptionRegions.mapTo(mutableSetOf()) { it.handler }
    val exceptionTouched = function.exceptionRegions.flatMapTo(mutableSetOf()) { it.protectedBlocks + it.handler }

    private data class BlockFacts(
        val definitions: Set<SSAValueId>,
        val uses: Map<SSAValueId, SSAStructure>,
        val successors: Set<SSABlock>
    )

    private val facts = mutableMapOf<SSABlock, BlockFacts>()
    private val users = mutableMapOf<SSAValueId, MutableSet<SSABlock>>()
    private val predecessors = mutableMapOf<SSABlock, MutableSet<SSABlock>>()
    private val order = function.blocks.withIndex().associateTo(mutableMapOf()) { it.value to it.index.toLong() * 4 }

    init {
        function.blocks.forEach(::refresh)
    }

    fun orderOf(block: SSABlock): Long = order.getValue(block)

    fun localDefinitions(block: SSABlock): Set<SSAValueId> = facts.getValue(block).definitions

    fun predecessors(block: SSABlock): Set<SSABlock> = predecessors[block].orEmpty()

    // A planned region inserts at most three blocks before its original entry, exactly once.
    fun insertedBefore(entry: SSABlock, blocks: List<SSABlock>) {
        require(blocks.size <= 3)
        val base = orderOf(entry) - blocks.size
        blocks.forEachIndexed { index, block -> order[block] = base + index }
    }

    fun refresh(block: SSABlock) {
        facts[block]?.let { old ->
            old.uses.keys.forEach { id ->
                users[id]?.let { blocks ->
                    blocks.remove(block)
                    if (blocks.isEmpty()) users.remove(id)
                }
            }
            old.successors.forEach { target -> predecessors[target]?.remove(block) }
        }
        val definitions = buildSet {
            block.args.forEach { add(it.id) }
            block.instructions.forEach { it.result?.let { value -> add(value.id) } }
        }
        val uses = linkedMapOf<SSAValueId, SSAStructure>()
        fun use(value: SSAValue) {
            if (value is SSAStructure && value.id !in parameterIds && value.id !in definitions) {
                uses.putIfAbsent(value.id, value)
            }
        }
        block.instructions.forEach { it.operands.forEach(::use) }
        block.terminator.operands.forEach(::use)
        val successors = block.terminator.successors.mapTo(linkedSetOf()) { it.block }
        facts[block] = BlockFacts(definitions, uses, successors)
        uses.keys.forEach { users.getOrPut(it) { linkedSetOf() }.add(block) }
        successors.forEach { predecessors.getOrPut(it) { linkedSetOf() }.add(block) }
    }

    /**
     * Only values defined by this region can require live-out repair. Propagate their indexed uses
     * backwards until a definition kills the value. This is the same least fixed point as the old
     * whole-function live-in analysis, without analysing unrelated values or blocks.
     */
    fun liveOutValues(
        regionBlocks: Set<SSABlock>,
        definitions: Map<SSAValueId, SSAStructure>
    ): Map<SSABlock, List<SSAStructure>> {
        val result = mutableMapOf<SSABlock, MutableList<SSAStructure>>()
        for ((id, value) in definitions) {
            if (id in parameterIds) continue
            val visited = mutableSetOf<SSABlock>()
            val queue = ArrayDeque<SSABlock>()
            users[id]?.forEach { queue.addLast(it) }
            while (queue.isNotEmpty()) {
                val block = queue.removeFirst()
                val blockFacts = facts[block] ?: continue
                if (id in blockFacts.definitions || !visited.add(block)) continue
                if (block !in regionBlocks) result.getOrPut(block) { mutableListOf() }.add(value)
                predecessors[block]?.forEach { queue.addLast(it) }
            }
        }
        return result.entries.sortedBy { orderOf(it.key) }
            .associateTo(linkedMapOf()) { it.key to it.value }
    }
}