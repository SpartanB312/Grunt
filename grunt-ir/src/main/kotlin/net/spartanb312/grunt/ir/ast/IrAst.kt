package net.spartanb312.grunt.ir.ast

import net.spartanb312.grunt.ir.core.*

/** Lightweight AST-like view over the linear SSA IR. */
sealed interface IrAstNode

/** Expression handle backed by an existing IR value. */
@JvmInline
value class IrExpr(val value: IrValue) : IrAstNode {
    val type: IrType get() = value.type
}

/** Statement view backed by an instruction or a terminator. */
sealed interface IrStmt : IrAstNode

data class IrInstructionStmt(val instruction: IrInstruction) : IrStmt

data class IrTerminatorStmt(val terminator: IrTerminator) : IrStmt

fun IrValue.asExpr(): IrExpr = IrExpr(this)

fun IrInstruction.asStmt(): IrInstructionStmt = IrInstructionStmt(this)

fun IrTerminator.asStmt(): IrTerminatorStmt = IrTerminatorStmt(this)

/**
 * Syntactic sugar for emitting AST-like expressions into a linear SSA block.
 *
 * Expressions are not stored as trees. Each expression-producing helper appends
 * one instruction to [block] and returns the produced SSA value as [IrExpr].
 */
class IrAstBuilder(
    val block: IrBlock,
    private val ids: IrIdAllocator
) {
    fun expr(value: IrValue): IrExpr = value.asExpr()

    fun bool(value: Boolean): IrExpr = IrBoolLiteral(value).asExpr()

    fun int(value: Int, type: IrIntegerType = IrI32Type): IrExpr = IrIntLiteral(value.toLong(), type).asExpr()

    fun int(value: Long, type: IrIntegerType = IrI32Type): IrExpr = IrIntLiteral(value, type).asExpr()

    fun float(value: Float, type: IrFloatType = IrF32Type): IrExpr = IrFloatLiteral(value.toDouble(), type).asExpr()

    fun double(value: Double, type: IrFloatType = IrF64Type): IrExpr = IrFloatLiteral(value, type).asExpr()

    fun nullRef(): IrExpr = IrNullLiteral.asExpr()

    fun opaque(text: String, type: IrType): IrExpr = IrOpaqueLiteral(text, type).asExpr()

    fun append(instruction: IrInstruction): IrInstructionStmt {
        block.append(instruction)
        return instruction.asStmt()
    }

    fun terminate(terminator: IrTerminator): IrTerminatorStmt {
        block.terminator = terminator
        return terminator.asStmt()
    }

    fun result(type: IrType, debugName: String? = null): IrInstructionResult {
        return IrInstructionResult(ids.valueId(), type, debugName)
    }

    fun unary(
        op: IrUnaryOp,
        value: IrExpr,
        resultType: IrType = value.type,
        debugName: String? = null,
        effect: IrEffect = IrEffect.Pure
    ): IrExpr {
        val result = result(resultType, debugName)
        block.append(IrUnaryInstruction(result, op, value.value, effect))
        return result.asExpr()
    }

    fun neg(value: IrExpr, debugName: String? = null): IrExpr = unary(IrUnaryOp.Neg, value, debugName = debugName)

    fun logicalNot(value: IrExpr, debugName: String? = null): IrExpr {
        return unary(IrUnaryOp.LogicalNot, value, IrBoolType, debugName)
    }

    fun bitNot(value: IrExpr, debugName: String? = null): IrExpr = unary(IrUnaryOp.BitNot, value, debugName = debugName)

    fun binary(
        op: IrBinaryOp,
        lhs: IrExpr,
        rhs: IrExpr,
        resultType: IrType = lhs.type,
        debugName: String? = null
    ): IrExpr {
        val result = result(resultType, debugName)
        block.append(IrBinaryInstruction(result, op, lhs.value, rhs.value))
        return result.asExpr()
    }

    fun add(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.Add, lhs, rhs, debugName = debugName)

    fun sub(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.Sub, lhs, rhs, debugName = debugName)

    fun mul(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.Mul, lhs, rhs, debugName = debugName)

    fun div(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.Div, lhs, rhs, debugName = debugName)

    fun rem(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.Rem, lhs, rhs, debugName = debugName)

    fun and(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.And, lhs, rhs, debugName = debugName)

    fun or(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.Or, lhs, rhs, debugName = debugName)

    fun xor(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.Xor, lhs, rhs, debugName = debugName)

    fun shl(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.Shl, lhs, rhs, debugName = debugName)

    fun shr(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.Shr, lhs, rhs, debugName = debugName)

    fun ushr(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = binary(IrBinaryOp.UShr, lhs, rhs, debugName = debugName)

    fun compare(
        predicate: IrComparePredicate,
        lhs: IrExpr,
        rhs: IrExpr,
        debugName: String? = null
    ): IrExpr {
        val result = result(IrBoolType, debugName)
        block.append(IrCompareInstruction(result, predicate, lhs.value, rhs.value))
        return result.asExpr()
    }

    fun eq(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = compare(IrComparePredicate.Eq, lhs, rhs, debugName)

    fun ne(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = compare(IrComparePredicate.Ne, lhs, rhs, debugName)

    fun lt(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = compare(IrComparePredicate.Lt, lhs, rhs, debugName)

    fun le(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = compare(IrComparePredicate.Le, lhs, rhs, debugName)

    fun gt(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = compare(IrComparePredicate.Gt, lhs, rhs, debugName)

    fun ge(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr = compare(IrComparePredicate.Ge, lhs, rhs, debugName)

    fun refEq(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr {
        return compare(IrComparePredicate.RefEq, lhs, rhs, debugName)
    }

    fun refNe(lhs: IrExpr, rhs: IrExpr, debugName: String? = null): IrExpr {
        return compare(IrComparePredicate.RefNe, lhs, rhs, debugName)
    }

    fun convert(
        kind: IrConvertKind,
        value: IrExpr,
        targetType: IrType,
        debugName: String? = null
    ): IrExpr {
        val result = result(targetType, debugName)
        block.append(IrConvertInstruction(result, kind, value.value, targetType))
        return result.asExpr()
    }

    fun numericCast(value: IrExpr, targetType: IrType, debugName: String? = null): IrExpr {
        return convert(IrConvertKind.Numeric, value, targetType, debugName)
    }

    fun bitcast(value: IrExpr, targetType: IrType, debugName: String? = null): IrExpr {
        return convert(IrConvertKind.Bitcast, value, targetType, debugName)
    }

    fun referenceCast(value: IrExpr, targetType: IrType, debugName: String? = null): IrExpr {
        return convert(IrConvertKind.ReferenceCast, value, targetType, debugName)
    }

    fun loadField(field: IrFieldRef, receiver: IrExpr? = null, debugName: String? = null): IrExpr {
        val result = result(field.type, debugName)
        block.append(IrLoadFieldInstruction(result, field, receiver?.value))
        return result.asExpr()
    }

    fun storeField(field: IrFieldRef, value: IrExpr, receiver: IrExpr? = null): IrInstructionStmt {
        return append(IrStoreFieldInstruction(field, receiver?.value, value.value))
    }

    fun arrayLoad(
        array: IrExpr,
        index: IrExpr,
        resultType: IrType = (array.type as? IrArrayType)?.elementType ?: IrUnknownType,
        debugName: String? = null
    ): IrExpr {
        val result = result(resultType, debugName)
        block.append(IrArrayLoadInstruction(result, array.value, index.value))
        return result.asExpr()
    }

    fun arrayStore(array: IrExpr, index: IrExpr, value: IrExpr): IrInstructionStmt {
        return append(IrArrayStoreInstruction(array.value, index.value, value.value))
    }

    fun call(
        target: IrCallableRef,
        args: List<IrExpr> = emptyList(),
        dispatch: IrCallDispatch = IrCallDispatch.Direct,
        debugName: String? = null
    ): IrExpr? {
        val result = if (target.returnType == IrVoidType) null else result(target.returnType, debugName)
        block.append(IrCallInstruction(result, target, args.map { it.value }, dispatch))
        return result?.asExpr()
    }

    fun callStmt(
        target: IrCallableRef,
        args: List<IrExpr> = emptyList(),
        dispatch: IrCallDispatch = IrCallDispatch.Direct
    ): IrInstructionStmt {
        require(target.returnType == IrVoidType) { "Use call() for non-void calls so the SSA result is preserved" }
        val instruction = IrCallInstruction(null, target, args.map { it.value }, dispatch)
        return append(instruction)
    }

    fun intrinsic(
        intrinsic: IrIntrinsicRef,
        args: List<IrExpr> = emptyList(),
        debugName: String? = null
    ): IrExpr? {
        val result = if (intrinsic.returnType == IrVoidType) null else result(intrinsic.returnType, debugName)
        block.append(IrIntrinsicInstruction(result, intrinsic, args.map { it.value }))
        return result?.asExpr()
    }

    fun intrinsicStmt(intrinsic: IrIntrinsicRef, args: List<IrExpr> = emptyList()): IrInstructionStmt {
        require(intrinsic.returnType == IrVoidType) {
            "Use intrinsic() for non-void intrinsics so the SSA result is preserved"
        }
        return append(IrIntrinsicInstruction(null, intrinsic, args.map { it.value }))
    }

    fun allocate(
        allocation: IrAllocation,
        args: List<IrExpr> = emptyList(),
        debugName: String? = null
    ): IrExpr {
        val result = result(allocation.type, debugName)
        block.append(IrAllocateInstruction(result, allocation, args.map { it.value }))
        return result.asExpr()
    }

    fun barrier(reason: String? = null): IrInstructionStmt = append(IrBarrierInstruction(reason))

    fun successor(block: IrBlock, args: List<IrExpr> = emptyList()): IrSuccessor {
        return IrSuccessor(block, args.map { it.value })
    }

    fun jump(target: IrSuccessor): IrTerminatorStmt = terminate(IrJumpTerminator(target))

    fun jump(block: IrBlock, args: List<IrExpr> = emptyList()): IrTerminatorStmt = jump(successor(block, args))

    fun branch(condition: IrExpr, trueTarget: IrSuccessor, falseTarget: IrSuccessor): IrTerminatorStmt {
        return terminate(IrBranchTerminator(condition.value, trueTarget, falseTarget))
    }

    fun branch(
        condition: IrExpr,
        trueBlock: IrBlock,
        falseBlock: IrBlock,
        trueArgs: List<IrExpr> = emptyList(),
        falseArgs: List<IrExpr> = emptyList()
    ): IrTerminatorStmt {
        return branch(condition, successor(trueBlock, trueArgs), successor(falseBlock, falseArgs))
    }

    fun switch(
        value: IrExpr,
        cases: List<Pair<Long, IrSuccessor>>,
        defaultTarget: IrSuccessor
    ): IrTerminatorStmt {
        return terminate(IrSwitchTerminator(value.value, cases.map { IrSwitchCase(it.first, it.second) }, defaultTarget))
    }

    fun returnVoid(): IrTerminatorStmt = terminate(IrReturnTerminator())

    fun returnValue(value: IrExpr): IrTerminatorStmt = terminate(IrReturnTerminator(value.value))

    fun throwValue(exception: IrExpr): IrTerminatorStmt = terminate(IrThrowTerminator(exception.value))

    fun unreachable(): IrTerminatorStmt = terminate(IrUnreachableTerminator)
}

fun IrBlock.ast(ids: IrIdAllocator): IrAstBuilder = IrAstBuilder(this, ids)

inline fun <T> IrBlock.ast(ids: IrIdAllocator, block: IrAstBuilder.() -> T): T {
    return IrAstBuilder(this, ids).block()
}
