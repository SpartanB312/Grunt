package net.spartanb312.grunt.ir.core

/**
 * Basic block in SSA form.
 *
 * [args] are the block's phi inputs. [instructions] must not contain control
 * transfers; all normal outgoing edges are owned by [terminator].
 */
class IrBlock(
    val id: IrBlockId,
    val args: MutableList<IrBlockArg> = mutableListOf(),
    val instructions: MutableList<IrInstruction> = mutableListOf(),
    var terminator: IrTerminator = IrUnreachableTerminator
) {
    /** Append an instruction and return its result when it has one. */
    fun append(instruction: IrInstruction): IrInstructionResult? {
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
data class IrExceptionRegion(
    val protectedBlocks: MutableSet<IrBlock>,
    val handler: IrBlock,
    val caughtType: IrType? = null
)

/**
 * SSA function body.
 *
 * The function owns all blocks and exception regions. It does not own external
 * metadata; platform-specific data is referenced through external refs and side
 * tables in the importer/exporter layer.
 */
class IrFunction(
    val symbol: IrFunctionSymbol,
    val parameters: MutableList<IrParameter>,
    val blocks: MutableList<IrBlock>,
    var entry: IrBlock,
    val exceptionRegions: MutableList<IrExceptionRegion> = mutableListOf()
) {
    val returnType get() = symbol.returnType

    /** Normal CFG successors from a block terminator. */
    fun normalSuccessors(block: IrBlock): List<IrBlock> {
        return block.terminator.successors.map { it.block }
    }

    /** Exceptional successors from regions protecting [block]. */
    fun exceptionSuccessors(block: IrBlock): List<IrBlock> {
        return exceptionRegions
            .asSequence()
            .filter { block in it.protectedBlocks }
            .map { it.handler }
            .distinct()
            .toList()
    }

    /** Combined normal and exceptional successors. */
    fun allSuccessors(block: IrBlock): List<IrBlock> {
        return (normalSuccessors(block) + exceptionSuccessors(block)).distinct()
    }

    /** Build predecessor lists, optionally including exception edges. */
    fun predecessors(includeExceptionEdges: Boolean = true): Map<IrBlock, List<IrBlock>> {
        val result = blocks.associateWith { mutableListOf<IrBlock>() }
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
}
