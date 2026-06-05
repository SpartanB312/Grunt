package net.spartanb312.grunt.ir.flow.core

/**
 * Single-entry region view used by mutation passes such as CFF.
 *
 * A region does not own blocks or edges. It groups existing graph objects so a
 * pass can rewrite a local subgraph while keeping explicit entry/exit edges.
 */
data class FlowRegion(
    val id: FlowRegionId,
    val method: FlowMethod,
    val entry: FlowBlock,
    val blocks: Set<FlowBlock>,
    val entryEdges: List<FlowEdge>,
    val internalEdges: List<FlowEdge>,
    val exitEdges: List<FlowEdge>,
    val exceptionRegions: List<FlowExceptionRegion>
) {
    operator fun contains(block: FlowBlock): Boolean = block in blocks
}

data class FlowRegionPlanOptions(
    val minBlocks: Int = 2,
    val maxBlocks: Int = 8,
    val includeMethodEntry: Boolean = false,
    val allowExceptionBlocks: Boolean = false,
    val preferSmallRegions: Boolean = true
) {
    init {
        require(minBlocks > 0) { "minBlocks must be positive" }
        require(maxBlocks >= minBlocks) { "maxBlocks must be >= minBlocks" }
    }
}

internal object FlowRegionPlanner {
    fun plan(method: FlowMethod, options: FlowRegionPlanOptions): List<FlowRegion> {
        val excluded = if (options.allowExceptionBlocks) {
            emptySet()
        } else {
            method.exceptionRegions.flatMapTo(mutableSetOf()) { region ->
                region.protectedBlocks + region.handler
            }
        }
        val assigned = linkedSetOf<FlowBlock>()
        val regions = mutableListOf<FlowRegion>()

        for (seed in method.layout.order.ifEmpty { method.blocks }) {
            if (seed in assigned || seed in excluded) continue
            if (!options.includeMethodEntry && seed == method.entry) continue

            val blocks = growRegion(seed, method, options, excluded, assigned)
            if (blocks.size < options.minBlocks) continue

            val region = createRegion(
                id = FlowRegionId(regions.size),
                method = method,
                entry = seed,
                blocks = blocks
            )
            regions += region
            assigned += blocks
        }

        return regions
    }

    private fun growRegion(
        seed: FlowBlock,
        method: FlowMethod,
        options: FlowRegionPlanOptions,
        excluded: Set<FlowBlock>,
        assigned: Set<FlowBlock>
    ): LinkedHashSet<FlowBlock> {
        val region = linkedSetOf(seed)
        var changed: Boolean

        do {
            changed = false
            if (options.preferSmallRegions && region.size >= options.minBlocks) {
                return region
            }

            val candidates = region
                .flatMap { method.successors(it) }
                .filter { it !in region && it !in excluded && it !in assigned }
                .distinct()

            for (candidate in candidates) {
                if (region.size >= options.maxBlocks) return region
                val allPredsInside = method.incomingEdges(candidate).all { it.from in region || it.from == candidate }
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
        id: FlowRegionId,
        method: FlowMethod,
        entry: FlowBlock,
        blocks: Set<FlowBlock>
    ): FlowRegion {
        val internalEdges = method.edges.filter { it.from in blocks && it.to in blocks }
        val exitEdges = method.edges.filter { it.from in blocks && it.to !in blocks }
        val entryEdges = method.edges.filter { it.from !in blocks && it.to == entry }
        val exceptionRegions = method.exceptionRegions.filter { region ->
            region.handler in blocks || region.protectedBlocks.any { it in blocks }
        }

        return FlowRegion(
            id = id,
            method = method,
            entry = entry,
            blocks = blocks,
            entryEdges = entryEdges,
            internalEdges = internalEdges,
            exitEdges = exitEdges,
            exceptionRegions = exceptionRegions
        )
    }
}
