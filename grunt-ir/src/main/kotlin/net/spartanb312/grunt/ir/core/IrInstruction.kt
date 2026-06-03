package net.spartanb312.grunt.ir.core

/**
 * Non-terminating operation inside a basic block.
 *
 * Instructions are in SSA form: an instruction may define at most one
 * [result], and every dependency must appear in [operands]. Control transfer is
 * represented separately by [IrTerminator].
 */
sealed interface IrInstruction {
    val result: IrInstructionResult?
    val operands: List<IrValue>
    val effect: IrEffect
}

/** Single-operand arithmetic/logical operation. */
enum class IrUnaryOp {
    Neg,
    LogicalNot,
    BitNot
}

/** Two-operand arithmetic/bit operation. */
enum class IrBinaryOp {
    Add,
    Sub,
    Mul,
    Div,
    Rem,
    And,
    Or,
    Xor,
    Shl,
    Shr,
    UShr
}

/** Comparison predicate. Ref* predicates make reference intent explicit. */
enum class IrComparePredicate {
    Eq,
    Ne,
    Lt,
    Le,
    Gt,
    Ge,
    RefEq,
    RefNe
}

/** Conversion category. Exporters decide the concrete target instruction. */
enum class IrConvertKind {
    Numeric,
    Bitcast,
    ReferenceCast
}

/** Allocation request abstracted away from a concrete runtime allocator. */
sealed interface IrAllocation {
    val type: IrType

    /** Allocate an object/reference value. Constructor calls are separate calls. */
    data class Object(override val type: IrRefType) : IrAllocation

    /** Allocate an array value. Dimensions/counts are carried by instruction args. */
    data class Array(override val type: IrArrayType) : IrAllocation

    /** Frontend/backend-specific allocation form not yet modeled directly. */
    data class Opaque(override val type: IrType, val name: String) : IrAllocation
}

/** Apply a unary op and produce one SSA result. */
data class IrUnaryInstruction(
    override val result: IrInstructionResult,
    val op: IrUnaryOp,
    val value: IrValue,
    override val effect: IrEffect = IrEffect.Pure
) : IrInstruction {
    override val operands get() = listOf(value)
}

/** Apply a binary op and produce one SSA result. */
data class IrBinaryInstruction(
    override val result: IrInstructionResult,
    val op: IrBinaryOp,
    val lhs: IrValue,
    val rhs: IrValue,
    override val effect: IrEffect = defaultBinaryEffect(op)
) : IrInstruction {
    override val operands get() = listOf(lhs, rhs)
}

/** Compare two values and produce [IrBoolType]. */
data class IrCompareInstruction(
    override val result: IrInstructionResult,
    val predicate: IrComparePredicate,
    val lhs: IrValue,
    val rhs: IrValue,
    override val effect: IrEffect = IrEffect.Pure
) : IrInstruction {
    override val operands get() = listOf(lhs, rhs)
}

/** Convert one value to [targetType]. */
data class IrConvertInstruction(
    override val result: IrInstructionResult,
    val kind: IrConvertKind,
    val value: IrValue,
    val targetType: IrType,
    override val effect: IrEffect = IrEffect.Pure
) : IrInstruction {
    override val operands get() = listOf(value)
}

/** Read a static or instance field. Instance fields carry a receiver operand. */
data class IrLoadFieldInstruction(
    override val result: IrInstructionResult,
    val field: IrFieldRef,
    val receiver: IrValue?,
    override val effect: IrEffect = IrEffect.ReadMemory
) : IrInstruction {
    override val operands get() = listOfNotNull(receiver)
}

/** Write a static or instance field. Instance fields carry a receiver operand. */
data class IrStoreFieldInstruction(
    val field: IrFieldRef,
    val receiver: IrValue?,
    val value: IrValue,
    override val effect: IrEffect = IrEffect.WriteMemory
) : IrInstruction {
    override val result: IrInstructionResult? = null
    override val operands get() = listOfNotNull(receiver) + value
}

/** Read an array element. */
data class IrArrayLoadInstruction(
    override val result: IrInstructionResult,
    val array: IrValue,
    val index: IrValue,
    override val effect: IrEffect = IrEffect.ReadMemory.copy(mayThrow = true),
    val elementType: IrType? = null
) : IrInstruction {
    override val operands get() = listOf(array, index)
}

/** Write an array element. */
data class IrArrayStoreInstruction(
    val array: IrValue,
    val index: IrValue,
    val value: IrValue,
    override val effect: IrEffect = IrEffect.WriteMemory.copy(mayThrow = true),
    val elementType: IrType? = null
) : IrInstruction {
    override val result: IrInstructionResult? = null
    override val operands get() = listOf(array, index, value)
}

/**
 * Call a function-like target.
 *
 * Receiver values are represented as ordinary leading arguments when required
 * by the frontend/importer.
 */
data class IrCallInstruction(
    override val result: IrInstructionResult?,
    val target: IrCallableRef,
    val args: List<IrValue>,
    val dispatch: IrCallDispatch,
    override val effect: IrEffect = if (target is IrIntrinsicRef) target.intrinsicEffect else IrEffect.ExternalCall
) : IrInstruction {
    override val operands get() = args
}

/** Resolve a late-bound value site into a concrete SSA value. */
data class IrResolveDynamicValueInstruction(
    override val result: IrInstructionResult,
    val site: IrDynamicValueSite,
    override val effect: IrEffect = IrEffect.DynamicResolve
) : IrInstruction {
    override val operands get() = emptyList<IrValue>()
}

/** Invoke a late-bound dynamic call site. */
data class IrDynamicCallInstruction(
    override val result: IrInstructionResult?,
    val site: IrDynamicCallSite,
    val args: List<IrValue>,
    override val effect: IrEffect = IrEffect.DynamicCall
) : IrInstruction {
    override val operands get() = args
}

/**
 * Allocate a value.
 *
 * Allocation arguments are shape/count/runtime inputs, not constructor
 * arguments. Constructors or initializers should be modeled as calls.
 */
data class IrAllocateInstruction(
    override val result: IrInstructionResult,
    val allocation: IrAllocation,
    val args: List<IrValue> = emptyList(),
    override val effect: IrEffect = IrEffect(
        mayThrow = true,
        readsExternalState = true,
        canDuplicate = false,
        canMove = false
    )
) : IrInstruction {
    override val operands get() = args
}

/** Invoke an intrinsic operation that has no ordinary callable target. */
data class IrIntrinsicInstruction(
    override val result: IrInstructionResult?,
    val intrinsic: IrIntrinsicRef,
    val args: List<IrValue>,
    override val effect: IrEffect = intrinsic.intrinsicEffect
) : IrInstruction {
    override val operands get() = args
}

/** Explicit transformation barrier with no value result. */
data class IrBarrierInstruction(
    val reason: String? = null,
    override val effect: IrEffect = IrEffect.Barrier
) : IrInstruction {
    override val result: IrInstructionResult? = null
    override val operands get() = emptyList<IrValue>()
}

/** Most arithmetic is pure, except operations that can trap. */
private fun defaultBinaryEffect(op: IrBinaryOp): IrEffect {
    return when (op) {
        IrBinaryOp.Div,
        IrBinaryOp.Rem -> IrEffect.Pure.copy(mayThrow = true, canMove = false)
        else -> IrEffect.Pure
    }
}
