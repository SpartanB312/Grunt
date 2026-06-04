package net.spartanb312.grunt.ir.ssa.core

/** Any value that can be used as an instruction operand or terminator operand. */
sealed interface SSAValue {
    val type: SSAType
}

/**
 * Value defined by SSA structure.
 *
 * Parameters, block arguments, and instruction results all have stable IDs.
 * Literals do not, because they are embedded constants rather than definitions.
 */
sealed interface SSAStructure : SSAValue {
    val id: SSAValueId
    var debugName: String?
}

/** Function-level parameter in source order, including any frontend receiver. */
data class SSAParameter(
    override val id: SSAValueId,
    val index: Int,
    override val type: SSAType,
    override var debugName: String? = null
) : SSAStructure

/**
 * Explains why a block argument exists.
 *
 * The origin is not required for core SSA correctness, but it gives importers
 * and exporters enough context to preserve foreign execution state such as JVM
 * locals and operand stack slots.
 */
sealed interface SSABlockArgOrigin {
    /** Ordinary phi-like join argument. */
    data object Join : SSABlockArgOrigin

    /** Argument introduced by the IR builder rather than an input frontend. */
    data object Synthetic : SSABlockArgOrigin

    /** Exception object entering an exception handler block. */
    data object ExceptionObject : SSABlockArgOrigin

    /** Argument that mirrors a function parameter. */
    data class Parameter(val index: Int) : SSABlockArgOrigin

    /** Frontend-owned state, for example a JVM local or stack slot. */
    data class FrontendState(val name: String, val index: Int) : SSABlockArgOrigin
}

/**
 * SSA value supplied to a block by its predecessors.
 *
 * Successor edges must pass exactly one value for each block argument. This is
 * the IR's phi representation and also the place where stack-machine state is
 * normalized into SSA form.
 */
data class SSABlockArg(
    override val id: SSAValueId,
    val index: Int,
    override val type: SSAType,
    val origin: SSABlockArgOrigin = SSABlockArgOrigin.Join,
    override var debugName: String? = null
) : SSAStructure

/** SSA value produced by an instruction. */
data class SSAInstructionResult(
    override val id: SSAValueId,
    override val type: SSAType,
    override var debugName: String? = null
) : SSAStructure

/** Embedded constant value. */
sealed interface SSALiteral : SSAValue

data class SSABoolLiteral(val value: Boolean) : SSALiteral {
    override val type = SSABoolType
}

data class SSAIntLiteral(
    val value: Long,
    override val type: SSAIntegerType = SSAI32Type
) : SSALiteral

data class SSAFloatLiteral(
    val value: Double,
    override val type: SSAFloatType = SSAF64Type
) : SSALiteral

data object SSANullLiteral : SSALiteral {
    override val type = SSANullType
}

/**
 * Literal payload that the core IR does not interpret.
 *
 * Frontends may use this for constants such as class literals or method handles
 * until a dedicated core representation exists.
 */
data class SSAOpaqueLiteral(
    val text: String,
    override val type: SSAType
) : SSALiteral
