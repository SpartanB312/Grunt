package net.spartanb312.grunt.ir.core

/**
 * Last operation in a basic block.
 *
 * A block must end in exactly one terminator. Terminators own normal CFG edges;
 * exceptional edges are modeled separately by [IrExceptionRegion].
 */
sealed interface IrTerminator {
    val operands: List<IrValue>
    val successors: List<IrSuccessor>
}

/**
 * Normal CFG edge with values passed to the target block arguments.
 *
 * This is the IR's phi-node encoding: a predecessor passes values on the edge,
 * and the successor receives them through [IrBlock.args].
 */
data class IrSuccessor(
    val block: IrBlock,
    val args: List<IrValue> = emptyList()
)

/** Unconditional jump. */
data class IrJumpTerminator(
    val target: IrSuccessor
) : IrTerminator {
    override val operands get() = target.args
    override val successors get() = listOf(target)
}

/** Two-way conditional branch. [condition] must have [IrBoolType]. */
data class IrBranchTerminator(
    val condition: IrValue,
    val trueTarget: IrSuccessor,
    val falseTarget: IrSuccessor
) : IrTerminator {
    override val operands get() = listOf(condition) + trueTarget.args + falseTarget.args
    override val successors get() = listOf(trueTarget, falseTarget)
}

/** One switch arm. */
data class IrSwitchCase(
    val key: Long,
    val target: IrSuccessor
)

/** Integer switch with an explicit default target. */
data class IrSwitchTerminator(
    val value: IrValue,
    val cases: List<IrSwitchCase>,
    val defaultTarget: IrSuccessor
) : IrTerminator {
    override val operands get() = listOf(value) + cases.flatMap { it.target.args } + defaultTarget.args
    override val successors get() = cases.map { it.target } + defaultTarget
}

/** Return from the current function. Void functions use a null value. */
data class IrReturnTerminator(
    val value: IrValue? = null
) : IrTerminator {
    override val operands get() = listOfNotNull(value)
    override val successors get() = emptyList<IrSuccessor>()
}

/** Throw/raise an exception-like reference value. */
data class IrThrowTerminator(
    val exception: IrValue
) : IrTerminator {
    override val operands get() = listOf(exception)
    override val successors get() = emptyList<IrSuccessor>()
}

/** Placeholder terminator for incomplete or statically unreachable blocks. */
data object IrUnreachableTerminator : IrTerminator {
    override val operands get() = emptyList<IrValue>()
    override val successors get() = emptyList<IrSuccessor>()
}
