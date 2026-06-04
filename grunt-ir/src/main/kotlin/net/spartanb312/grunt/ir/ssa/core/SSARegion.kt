package net.spartanb312.grunt.ir.ssa.core

/**
 * Normal CFG edge view.
 *
 * The edge does not own the successor object. It identifies which successor
 * slot in [from]'s terminator is being described.
 */
data class SSAEdge(
    val from: SSABlock,
    val successorIndex: Int,
    val successor: SSASuccessor
) {
    val to: SSABlock get() = successor.block
}

/**
 * Single-entry subgraph view generated from an [SSAFunction].
 *
 * A region never owns blocks. It only groups existing function blocks so passes
 * can work on a smaller CFG unit, for example region-local flattening.
 */
data class SSARegion(
    val id: Int,
    val function: SSAFunction,
    val entry: SSABlock,
    val blocks: Set<SSABlock>,
    val entryEdges: List<SSAEdge>,
    val internalEdges: List<SSAEdge>,
    val exitEdges: List<SSAEdge>
) {
    operator fun contains(block: SSABlock): Boolean = block in blocks
}

/**
 * Conservative region planning options.
 *
 * The planner creates connected, single-entry regions. Blocks touched by
 * exception regions are excluded unless explicitly allowed.
 */
data class SSARegionPlanOptions(
    val minBlocks: Int = 2,
    val maxBlocks: Int = 8,
    val includeFunctionEntry: Boolean = false,
    val allowExceptionBlocks: Boolean = false,
    val preferSmallRegions: Boolean = true
) {
    init {
        require(minBlocks > 0) { "minBlocks must be positive" }
        require(maxBlocks >= minBlocks) { "maxBlocks must be >= minBlocks" }
    }
}

internal object SSARegionPlanner {
    fun plan(function: SSAFunction, options: SSARegionPlanOptions): List<SSARegion> {
        val edges = function.normalEdges()
        val preds = edges.groupBy { it.to }
        val excluded = if (options.allowExceptionBlocks) {
            emptySet()
        } else {
            function.exceptionRegions.flatMapTo(mutableSetOf()) { region ->
                region.protectedBlocks + region.handler
            }
        }
        val assigned = linkedSetOf<SSABlock>()
        val regions = mutableListOf<SSARegion>()

        for (seed in function.blocks) {
            if (seed in assigned || seed in excluded) continue
            if (!options.includeFunctionEntry && seed == function.entry) continue

            val blocks = growRegion(seed, options, excluded, assigned, preds)
            if (blocks.size < options.minBlocks) continue

            val region = createRegion(regions.size, function, seed, blocks, edges, preds)
            regions += region
            assigned += blocks
        }

        return regions
    }

    private fun growRegion(
        seed: SSABlock,
        options: SSARegionPlanOptions,
        excluded: Set<SSABlock>,
        assigned: Set<SSABlock>,
        preds: Map<SSABlock, List<SSAEdge>>
    ): LinkedHashSet<SSABlock> {
        val region = linkedSetOf(seed)
        var changed: Boolean

        do {
            changed = false
            if (options.preferSmallRegions && region.size >= options.minBlocks) {
                return region
            }
            val candidates = region
                .flatMap { it.terminator.successors }
                .map { it.block }
                .filter { it !in region && it !in excluded && it !in assigned }
                .distinct()

            for (candidate in candidates) {
                if (region.size >= options.maxBlocks) return region
                val allPredsInside = preds[candidate].orEmpty().all { it.from in region || it.from == candidate }
                if (allPredsInside) {
                    region += candidate
                    changed = true
                    if (options.preferSmallRegions && region.size >= options.minBlocks) {
                        return region
                    }
                }
            }
        } while (changed)

        return region
    }

    private fun createRegion(
        id: Int,
        function: SSAFunction,
        entry: SSABlock,
        blocks: Set<SSABlock>,
        edges: List<SSAEdge>,
        preds: Map<SSABlock, List<SSAEdge>>
    ): SSARegion {
        val internalEdges = edges.filter { it.from in blocks && it.to in blocks }
        val exitEdges = edges.filter { it.from in blocks && it.to !in blocks }
        val entryEdges = preds[entry].orEmpty().filter { it.from !in blocks }

        return SSARegion(
            id = id,
            function = function,
            entry = entry,
            blocks = blocks,
            entryEdges = entryEdges,
            internalEdges = internalEdges,
            exitEdges = exitEdges
        )
    }
}
