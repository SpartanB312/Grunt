package net.spartanb312.grunt.ir.ssa.core

/**
 * Basic block in SSA form.
 *
 * [args] are the block's phi inputs. [instructions] must not contain control
 * transfers; all normal outgoing edges are owned by [terminator].
 */
class SSABlock(
    val id: SSABlockId,
    val args: MutableList<SSABlockArg> = mutableListOf(),
    val instructions: MutableList<SSAInstruction> = mutableListOf(),
    var terminator: SSATerminator = SSAUnreachableTerminator
) {
    /** Append an instruction and return its result when it has one. */
    fun append(instruction: SSAInstruction): SSAInstructionResult? {
        instructions += instruction
        return instruction.result
    }

    override fun toString() = id.toString()
}

/**
 * Exception handler region.
 *
 * [protectedBlocks] may branch normally as usual, but operations inside them
 * also have an exceptional edge to [handler]. A null [caughtType] means catch
 * all / finally-style handling.
 */
data class SSAExceptionRegion(
    val protectedBlocks: MutableSet<SSABlock>,
    val handler: SSABlock,
    val caughtType: SSAType? = null
)

/**
 * SSA function body.
 *
 * The function owns all blocks and exception regions. It does not own external
 * metadata; platform-specific data is referenced through external refs and side
 * tables in the importer/exporter layer.
 */
class SSAFunction(
    val symbol: SSAFunctionSymbol,
    val parameters: MutableList<SSAParameter>,
    val blocks: MutableList<SSABlock>,
    var entry: SSABlock,
    val exceptionRegions: MutableList<SSAExceptionRegion> = mutableListOf()
) {
    val returnType get() = symbol.returnType

    /** Normal CFG successors from a block terminator. */
    fun normalSuccessors(block: SSABlock): List<SSABlock> {
        return block.terminator.successors.map { it.block }
    }

    /** Exceptional successors from regions protecting [block]. */
    fun exceptionSuccessors(block: SSABlock): List<SSABlock> {
        return exceptionRegions
            .asSequence()
            .filter { block in it.protectedBlocks }
            .map { it.handler }
            .distinct()
            .toList()
    }

    /** Combined normal and exceptional successors. */
    fun allSuccessors(block: SSABlock): List<SSABlock> {
        return (normalSuccessors(block) + exceptionSuccessors(block)).distinct()
    }

    /** Enumerate normal CFG edges in function block order. */
    fun normalEdges(): List<SSAEdge> {
        return blocks.flatMap { block ->
            block.terminator.successors.mapIndexed { index, successor ->
                SSAEdge(block, index, successor)
            }
        }
    }

    /** Build predecessor lists, optionally including exception edges. */
    fun predecessors(includeExceptionEdges: Boolean = true): Map<SSABlock, List<SSABlock>> {
        val result = blocks.associateWith { mutableListOf<SSABlock>() }
        for (block in blocks) {
            for (successor in block.terminator.successors) {
                result[successor.block]?.add(block)
            }
        }
        if (includeExceptionEdges) {
            for (region in exceptionRegions) {
                for (protectedBlock in region.protectedBlocks) {
                    result[region.handler]?.add(protectedBlock)
                }
            }
        }
        return result
    }

    /** Generate conservative single-entry regions from this function CFG. */
    fun planRegions(options: SSARegionPlanOptions = SSARegionPlanOptions()): List<SSARegion> {
        return SSARegionPlanner.plan(this, options)
    }
}
