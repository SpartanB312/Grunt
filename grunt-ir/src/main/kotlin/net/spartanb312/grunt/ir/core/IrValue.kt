package net.spartanb312.grunt.ir.core

/** Any value that can be used as an instruction operand or terminator operand. */
sealed interface IrValue {
    val type: IrType
}

/**
 * Value defined by SSA structure.
 *
 * Parameters, block arguments, and instruction results all have stable IDs.
 * Literals do not, because they are embedded constants rather than definitions.
 */
sealed interface IrSsaValue : IrValue {
    val id: IrValueId
    var debugName: String?
}

/** Function-level parameter in source order, including any frontend receiver. */
data class IrParameter(
    override val id: IrValueId,
    val index: Int,
    override val type: IrType,
    override var debugName: String? = null
) : IrSsaValue

/**
 * Explains why a block argument exists.
 *
 * The origin is not required for core SSA correctness, but it gives importers
 * and exporters enough context to preserve foreign execution state such as JVM
 * locals and operand stack slots.
 */
sealed interface IrBlockArgOrigin {
    /** Ordinary phi-like join argument. */
    data object Join : IrBlockArgOrigin

    /** Argument introduced by the IR builder rather than an input frontend. */
    data object Synthetic : IrBlockArgOrigin

    /** Exception object entering an exception handler block. */
    data object ExceptionObject : IrBlockArgOrigin

    /** Argument that mirrors a function parameter. */
    data class Parameter(val index: Int) : IrBlockArgOrigin

    /** Frontend-owned state, for example a JVM local or stack slot. */
    data class FrontendState(val name: String, val index: Int) : IrBlockArgOrigin
}

/**
 * SSA value supplied to a block by its predecessors.
 *
 * Successor edges must pass exactly one value for each block argument. This is
 * the IR's phi representation and also the place where stack-machine state is
 * normalized into SSA form.
 */
data class IrBlockArg(
    override val id: IrValueId,
    val index: Int,
    override val type: IrType,
    val origin: IrBlockArgOrigin = IrBlockArgOrigin.Join,
    override var debugName: String? = null
) : IrSsaValue

/** SSA value produced by an instruction. */
data class IrInstructionResult(
    override val id: IrValueId,
    override val type: IrType,
    override var debugName: String? = null
) : IrSsaValue

/** Embedded constant value. */
sealed interface IrLiteral : IrValue

data class IrBoolLiteral(val value: Boolean) : IrLiteral {
    override val type = IrBoolType
}

data class IrIntLiteral(
    val value: Long,
    override val type: IrIntegerType = IrI32Type
) : IrLiteral

data class IrFloatLiteral(
    val value: Double,
    override val type: IrFloatType = IrF64Type
) : IrLiteral

data object IrNullLiteral : IrLiteral {
    override val type = IrNullType
}

/**
 * Literal payload that the core IR does not interpret.
 *
 * Frontends may use this for constants such as class literals or method handles
 * until a dedicated core representation exists.
 */
data class IrOpaqueLiteral(
    val text: String,
    override val type: IrType
) : IrLiteral
