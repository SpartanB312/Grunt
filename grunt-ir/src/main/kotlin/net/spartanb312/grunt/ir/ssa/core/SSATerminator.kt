package net.spartanb312.grunt.ir.ssa.core

/**
 * Last operation in a basic block.
 *
 * A block must end in exactly one terminator. Terminators own normal CFG edges;
 * exceptional edges are modeled separately by [SSAExceptionRegion].
 */
sealed interface SSATerminator {
    val operands: List<SSAValue>
    val successors: List<SSASuccessor>
}

/**
 * Normal CFG edge with values passed to the target block arguments.
 *
 * This is the IR's phi-node encoding: a predecessor passes values on the edge,
 * and the successor receives them through [SSABlock.args].
 */
data class SSASuccessor(
    val block: SSABlock,
    val args: List<SSAValue> = emptyList()
)

/** Unconditional jump. */
data class SSAJumpTerminator(
    val target: SSASuccessor
) : SSATerminator {
    override val operands get() = target.args
    override val successors get() = listOf(target)
}

/** Two-way conditional branch. [condition] must have [SSABoolType]. */
data class SSABranchTerminator(
    val condition: SSAValue,
    val trueTarget: SSASuccessor,
    val falseTarget: SSASuccessor
) : SSATerminator {
    override val operands get() = listOf(condition) + trueTarget.args + falseTarget.args
    override val successors get() = listOf(trueTarget, falseTarget)
}

/** One switch arm. */
data class SSASwitchCase(
    val key: Long,
    val target: SSASuccessor
)

/** Integer switch with an explicit default target. */
data class SSASwitchTerminator(
    val value: SSAValue,
    val cases: List<SSASwitchCase>,
    val defaultTarget: SSASuccessor
) : SSATerminator {
    override val operands get() = listOf(value) + cases.flatMap { it.target.args } + defaultTarget.args
    override val successors get() = cases.map { it.target } + defaultTarget
}

/** Return from the current function. Void functions use a null value. */
data class SSAReturnTerminator(
    val value: SSAValue? = null
) : SSATerminator {
    override val operands get() = listOfNotNull(value)
    override val successors get() = emptyList<SSASuccessor>()
}

/** Throw/raise an exception-like reference value. */
data class SSAThrowTerminator(
    val exception: SSAValue
) : SSATerminator {
    override val operands get() = listOf(exception)
    override val successors get() = emptyList<SSASuccessor>()
}

/** Placeholder terminator for incomplete or statically unreachable blocks. */
data object SSAUnreachableTerminator : SSATerminator {
    override val operands get() = emptyList<SSAValue>()
    override val successors get() = emptyList<SSASuccessor>()
}
