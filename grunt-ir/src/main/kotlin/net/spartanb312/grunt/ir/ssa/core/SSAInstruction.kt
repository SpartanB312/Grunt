package net.spartanb312.grunt.ir.ssa.core

/**
 * Non-terminating operation inside a basic block.
 *
 * Instructions are in SSA form: an instruction may define at most one
 * [result], and every dependency must appear in [operands]. Control transfer is
 * represented separately by [SSATerminator].
 */
sealed interface SSAInstruction {
    val result: SSAInstructionResult?
    val operands: List<SSAValue>
    val effect: SSAEffect
}

/** Single-operand arithmetic/logical operation. */
enum class SSAUnaryOp {
    Neg,
    LogicalNot,
    BitNot
}

/** Two-operand arithmetic/bit operation. */
enum class SSABinaryOp {
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
enum class SSAComparePredicate {
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
enum class SSAConvertKind {
    Numeric,
    Bitcast,
    ReferenceCast
}

/** Allocation request abstracted away from a concrete runtime allocator. */
sealed interface SSAAllocation {
    val type: SSAType

    /** Allocate an object/reference value. Constructor calls are separate calls. */
    data class Object(override val type: SSARefType) : SSAAllocation

    /** Allocate an array value. Dimensions/counts are carried by instruction args. */
    data class Array(override val type: SSAArrayType) : SSAAllocation

    /** Frontend/backend-specific allocation form not yet modeled directly. */
    data class Opaque(override val type: SSAType, val name: String) : SSAAllocation
}

/** Apply a unary op and produce one SSA result. */
data class SSAUnaryInstruction(
    override val result: SSAInstructionResult,
    val op: SSAUnaryOp,
    val value: SSAValue,
    override val effect: SSAEffect = SSAEffect.Pure
) : SSAInstruction {
    override val operands get() = listOf(value)
}

/** Apply a binary op and produce one SSA result. */
data class SSABinaryInstruction(
    override val result: SSAInstructionResult,
    val op: SSABinaryOp,
    val lhs: SSAValue,
    val rhs: SSAValue,
    override val effect: SSAEffect = defaultBinaryEffect(op)
) : SSAInstruction {
    override val operands get() = listOf(lhs, rhs)
}

/** Compare two values and produce [SSABoolType]. */
data class SSACompareInstruction(
    override val result: SSAInstructionResult,
    val predicate: SSAComparePredicate,
    val lhs: SSAValue,
    val rhs: SSAValue,
    override val effect: SSAEffect = SSAEffect.Pure
) : SSAInstruction {
    override val operands get() = listOf(lhs, rhs)
}

/** Convert one value to [targetType]. */
data class SSAConvertInstruction(
    override val result: SSAInstructionResult,
    val kind: SSAConvertKind,
    val value: SSAValue,
    val targetType: SSAType,
    override val effect: SSAEffect = SSAEffect.Pure
) : SSAInstruction {
    override val operands get() = listOf(value)
}

/** Read a static or instance field. Instance fields carry a receiver operand. */
data class SSALoadFieldInstruction(
    override val result: SSAInstructionResult,
    val field: SSAFieldRef,
    val receiver: SSAValue?,
    override val effect: SSAEffect = SSAEffect.ReadMemory
) : SSAInstruction {
    override val operands get() = listOfNotNull(receiver)
}

/** Write a static or instance field. Instance fields carry a receiver operand. */
data class SSAStoreFieldInstruction(
    val field: SSAFieldRef,
    val receiver: SSAValue?,
    val value: SSAValue,
    override val effect: SSAEffect = SSAEffect.WriteMemory
) : SSAInstruction {
    override val result: SSAInstructionResult? = null
    override val operands get() = listOfNotNull(receiver) + value
}

/** Read an array element. */
data class SSAArrayLoadInstruction(
    override val result: SSAInstructionResult,
    val array: SSAValue,
    val index: SSAValue,
    override val effect: SSAEffect = SSAEffect.ReadMemory.copy(mayThrow = true),
    val elementType: SSAType? = null
) : SSAInstruction {
    override val operands get() = listOf(array, index)
}

/** Write an array element. */
data class SSAArrayStoreInstruction(
    val array: SSAValue,
    val index: SSAValue,
    val value: SSAValue,
    override val effect: SSAEffect = SSAEffect.WriteMemory.copy(mayThrow = true),
    val elementType: SSAType? = null
) : SSAInstruction {
    override val result: SSAInstructionResult? = null
    override val operands get() = listOf(array, index, value)
}

/**
 * Call a function-like target.
 *
 * Receiver values are represented as ordinary leading arguments when required
 * by the frontend/importer.
 */
data class SSACallInstruction(
    override val result: SSAInstructionResult?,
    val target: SSACallableRef,
    val args: List<SSAValue>,
    val dispatch: SSACallDispatch,
    override val effect: SSAEffect = if (target is SSAIntrinsicRef) target.intrinsicEffect else SSAEffect.ExternalCall
) : SSAInstruction {
    override val operands get() = args
}

/** Resolve a late-bound value site into a concrete SSA value. */
data class SSAResolveDynamicValueInstruction(
    override val result: SSAInstructionResult,
    val site: SSADynamicValueSite,
    override val effect: SSAEffect = SSAEffect.DynamicResolve
) : SSAInstruction {
    override val operands get() = emptyList<SSAValue>()
}

/** Invoke a late-bound dynamic call site. */
data class SSADynamicCallInstruction(
    override val result: SSAInstructionResult?,
    val site: SSADynamicCallSite,
    val args: List<SSAValue>,
    override val effect: SSAEffect = SSAEffect.DynamicCall
) : SSAInstruction {
    override val operands get() = args
}

/**
 * Allocate a value.
 *
 * Allocation arguments are shape/count/runtime inputs, not constructor
 * arguments. Constructors or initializers should be modeled as calls.
 */
data class SSAAllocateInstruction(
    override val result: SSAInstructionResult,
    val allocation: SSAAllocation,
    val args: List<SSAValue> = emptyList(),
    override val effect: SSAEffect = SSAEffect(
        mayThrow = true,
        readsExternalState = true,
        canDuplicate = false,
        canMove = false
    )
) : SSAInstruction {
    override val operands get() = args
}

/** Invoke an intrinsic operation that has no ordinary callable target. */
data class SSAIntrinsicInstruction(
    override val result: SSAInstructionResult?,
    val intrinsic: SSAIntrinsicRef,
    val args: List<SSAValue>,
    override val effect: SSAEffect = intrinsic.intrinsicEffect
) : SSAInstruction {
    override val operands get() = args
}

/** Explicit transformation barrier with no value result. */
data class SSABarrierInstruction(
    val reason: String? = null,
    override val effect: SSAEffect = SSAEffect.Barrier
) : SSAInstruction {
    override val result: SSAInstructionResult? = null
    override val operands get() = emptyList<SSAValue>()
}

/** Most arithmetic is pure, except operations that can trap. */
private fun defaultBinaryEffect(op: SSABinaryOp): SSAEffect {
    return when (op) {
        SSABinaryOp.Div,
        SSABinaryOp.Rem -> SSAEffect.Pure.copy(mayThrow = true, canMove = false)
        else -> SSAEffect.Pure
    }
}
