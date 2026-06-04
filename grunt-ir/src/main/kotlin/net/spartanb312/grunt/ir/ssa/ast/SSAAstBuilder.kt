package net.spartanb312.grunt.ir.ssa.ast

import net.spartanb312.grunt.ir.ssa.core.*

/** Lightweight AST-like view over the linear SSA IR. */
sealed interface SSAAstNode

/** Expression handle backed by an existing IR value. */
@JvmInline
value class SSAExpr(val value: SSAValue) : SSAAstNode {
    val type: SSAType get() = value.type
}

/** Statement view backed by an instruction or a terminator. */
sealed interface SSAStmt : SSAAstNode

data class SSAInstructionStmt(val instruction: SSAInstruction) : SSAStmt

data class SSATerminatorStmt(val terminator: SSATerminator) : SSAStmt

fun SSAValue.asExpr(): SSAExpr = SSAExpr(this)

fun SSAInstruction.asStmt(): SSAInstructionStmt = SSAInstructionStmt(this)

fun SSATerminator.asStmt(): SSATerminatorStmt = SSATerminatorStmt(this)

/**
 * Syntactic sugar for emitting AST-like expressions into a linear SSA block.
 *
 * Expressions are not stored as trees. Each expression-producing helper appends
 * one instruction to [block] and returns the produced SSA value as [SSAExpr].
 */
class SSAAstBuilder(
    val block: SSABlock,
    private val ids: SSAIdAllocator
) {
    fun expr(value: SSAValue): SSAExpr = value.asExpr()

    fun bool(value: Boolean): SSAExpr = SSABoolLiteral(value).asExpr()

    fun int(value: Int, type: SSAIntegerType = SSAI32Type): SSAExpr = SSAIntLiteral(value.toLong(), type).asExpr()

    fun int(value: Long, type: SSAIntegerType = SSAI32Type): SSAExpr = SSAIntLiteral(value, type).asExpr()

    fun float(value: Float, type: SSAFloatType = SSAF32Type): SSAExpr = SSAFloatLiteral(value.toDouble(), type).asExpr()

    fun double(value: Double, type: SSAFloatType = SSAF64Type): SSAExpr = SSAFloatLiteral(value, type).asExpr()

    fun nullRef(): SSAExpr = SSANullLiteral.asExpr()

    fun opaque(text: String, type: SSAType): SSAExpr = SSAOpaqueLiteral(text, type).asExpr()

    fun append(instruction: SSAInstruction): SSAInstructionStmt {
        block.append(instruction)
        return instruction.asStmt()
    }

    fun terminate(terminator: SSATerminator): SSATerminatorStmt {
        block.terminator = terminator
        return terminator.asStmt()
    }

    fun result(type: SSAType, debugName: String? = null): SSAInstructionResult {
        return SSAInstructionResult(ids.valueId(), type, debugName)
    }

    fun unary(
        op: SSAUnaryOp,
        value: SSAExpr,
        resultType: SSAType = value.type,
        debugName: String? = null,
        effect: SSAEffect = SSAEffect.Pure
    ): SSAExpr {
        val result = result(resultType, debugName)
        block.append(SSAUnaryInstruction(result, op, value.value, effect))
        return result.asExpr()
    }

    fun neg(value: SSAExpr, debugName: String? = null): SSAExpr = unary(SSAUnaryOp.Neg, value, debugName = debugName)

    fun logicalNot(value: SSAExpr, debugName: String? = null): SSAExpr {
        return unary(SSAUnaryOp.LogicalNot, value, SSABoolType, debugName)
    }

    fun bitNot(value: SSAExpr, debugName: String? = null): SSAExpr = unary(SSAUnaryOp.BitNot, value, debugName = debugName)

    fun binary(
        op: SSABinaryOp,
        lhs: SSAExpr,
        rhs: SSAExpr,
        resultType: SSAType = lhs.type,
        debugName: String? = null
    ): SSAExpr {
        val result = result(resultType, debugName)
        block.append(SSABinaryInstruction(result, op, lhs.value, rhs.value))
        return result.asExpr()
    }

    fun add(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.Add, lhs, rhs, debugName = debugName)

    fun sub(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.Sub, lhs, rhs, debugName = debugName)

    fun mul(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.Mul, lhs, rhs, debugName = debugName)

    fun div(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.Div, lhs, rhs, debugName = debugName)

    fun rem(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.Rem, lhs, rhs, debugName = debugName)

    fun and(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.And, lhs, rhs, debugName = debugName)

    fun or(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.Or, lhs, rhs, debugName = debugName)

    fun xor(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.Xor, lhs, rhs, debugName = debugName)

    fun shl(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.Shl, lhs, rhs, debugName = debugName)

    fun shr(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.Shr, lhs, rhs, debugName = debugName)

    fun ushr(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = binary(SSABinaryOp.UShr, lhs, rhs, debugName = debugName)

    fun compare(
        predicate: SSAComparePredicate,
        lhs: SSAExpr,
        rhs: SSAExpr,
        debugName: String? = null
    ): SSAExpr {
        val result = result(SSABoolType, debugName)
        block.append(SSACompareInstruction(result, predicate, lhs.value, rhs.value))
        return result.asExpr()
    }

    fun eq(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = compare(SSAComparePredicate.Eq, lhs, rhs, debugName)

    fun ne(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = compare(SSAComparePredicate.Ne, lhs, rhs, debugName)

    fun lt(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = compare(SSAComparePredicate.Lt, lhs, rhs, debugName)

    fun le(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = compare(SSAComparePredicate.Le, lhs, rhs, debugName)

    fun gt(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = compare(SSAComparePredicate.Gt, lhs, rhs, debugName)

    fun ge(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr = compare(SSAComparePredicate.Ge, lhs, rhs, debugName)

    fun refEq(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr {
        return compare(SSAComparePredicate.RefEq, lhs, rhs, debugName)
    }

    fun refNe(lhs: SSAExpr, rhs: SSAExpr, debugName: String? = null): SSAExpr {
        return compare(SSAComparePredicate.RefNe, lhs, rhs, debugName)
    }

    fun convert(
        kind: SSAConvertKind,
        value: SSAExpr,
        targetType: SSAType,
        debugName: String? = null
    ): SSAExpr {
        val result = result(targetType, debugName)
        block.append(SSAConvertInstruction(result, kind, value.value, targetType))
        return result.asExpr()
    }

    fun numericCast(value: SSAExpr, targetType: SSAType, debugName: String? = null): SSAExpr {
        return convert(SSAConvertKind.Numeric, value, targetType, debugName)
    }

    fun bitcast(value: SSAExpr, targetType: SSAType, debugName: String? = null): SSAExpr {
        return convert(SSAConvertKind.Bitcast, value, targetType, debugName)
    }

    fun referenceCast(value: SSAExpr, targetType: SSAType, debugName: String? = null): SSAExpr {
        return convert(SSAConvertKind.ReferenceCast, value, targetType, debugName)
    }

    fun loadField(field: SSAFieldRef, receiver: SSAExpr? = null, debugName: String? = null): SSAExpr {
        val result = result(field.type, debugName)
        block.append(SSALoadFieldInstruction(result, field, receiver?.value))
        return result.asExpr()
    }

    fun storeField(field: SSAFieldRef, value: SSAExpr, receiver: SSAExpr? = null): SSAInstructionStmt {
        return append(SSAStoreFieldInstruction(field, receiver?.value, value.value))
    }

    fun arrayLoad(
        array: SSAExpr,
        index: SSAExpr,
        resultType: SSAType = (array.type as? SSAArrayType)?.elementType ?: SSAUnknownType,
        debugName: String? = null
    ): SSAExpr {
        val result = result(resultType, debugName)
        block.append(SSAArrayLoadInstruction(result, array.value, index.value))
        return result.asExpr()
    }

    fun arrayStore(array: SSAExpr, index: SSAExpr, value: SSAExpr): SSAInstructionStmt {
        return append(SSAArrayStoreInstruction(array.value, index.value, value.value))
    }

    fun call(
        target: SSACallableRef,
        args: List<SSAExpr> = emptyList(),
        dispatch: SSACallDispatch = SSACallDispatch.Direct,
        debugName: String? = null
    ): SSAExpr? {
        val result = if (target.returnType == SSAVoidType) null else result(target.returnType, debugName)
        block.append(SSACallInstruction(result, target, args.map { it.value }, dispatch))
        return result?.asExpr()
    }

    fun callStmt(
        target: SSACallableRef,
        args: List<SSAExpr> = emptyList(),
        dispatch: SSACallDispatch = SSACallDispatch.Direct
    ): SSAInstructionStmt {
        require(target.returnType == SSAVoidType) { "Use call() for non-void calls so the SSA result is preserved" }
        val instruction = SSACallInstruction(null, target, args.map { it.value }, dispatch)
        return append(instruction)
    }

    fun intrinsic(
        intrinsic: SSAIntrinsicRef,
        args: List<SSAExpr> = emptyList(),
        debugName: String? = null
    ): SSAExpr? {
        val result = if (intrinsic.returnType == SSAVoidType) null else result(intrinsic.returnType, debugName)
        block.append(SSAIntrinsicInstruction(result, intrinsic, args.map { it.value }))
        return result?.asExpr()
    }

    fun intrinsicStmt(intrinsic: SSAIntrinsicRef, args: List<SSAExpr> = emptyList()): SSAInstructionStmt {
        require(intrinsic.returnType == SSAVoidType) {
            "Use intrinsic() for non-void intrinsics so the SSA result is preserved"
        }
        return append(SSAIntrinsicInstruction(null, intrinsic, args.map { it.value }))
    }

    fun allocate(
        allocation: SSAAllocation,
        args: List<SSAExpr> = emptyList(),
        debugName: String? = null
    ): SSAExpr {
        val result = result(allocation.type, debugName)
        block.append(SSAAllocateInstruction(result, allocation, args.map { it.value }))
        return result.asExpr()
    }

    fun barrier(reason: String? = null): SSAInstructionStmt = append(SSABarrierInstruction(reason))

    fun successor(block: SSABlock, args: List<SSAExpr> = emptyList()): SSASuccessor {
        return SSASuccessor(block, args.map { it.value })
    }

    fun jump(target: SSASuccessor): SSATerminatorStmt = terminate(SSAJumpTerminator(target))

    fun jump(block: SSABlock, args: List<SSAExpr> = emptyList()): SSATerminatorStmt = jump(successor(block, args))

    fun branch(condition: SSAExpr, trueTarget: SSASuccessor, falseTarget: SSASuccessor): SSATerminatorStmt {
        return terminate(SSABranchTerminator(condition.value, trueTarget, falseTarget))
    }

    fun branch(
        condition: SSAExpr,
        trueBlock: SSABlock,
        falseBlock: SSABlock,
        trueArgs: List<SSAExpr> = emptyList(),
        falseArgs: List<SSAExpr> = emptyList()
    ): SSATerminatorStmt {
        return branch(condition, successor(trueBlock, trueArgs), successor(falseBlock, falseArgs))
    }

    fun switch(
        value: SSAExpr,
        cases: List<Pair<Long, SSASuccessor>>,
        defaultTarget: SSASuccessor
    ): SSATerminatorStmt {
        return terminate(SSASwitchTerminator(value.value, cases.map { SSASwitchCase(it.first, it.second) }, defaultTarget))
    }

    fun returnVoid(): SSATerminatorStmt = terminate(SSAReturnTerminator())

    fun returnValue(value: SSAExpr): SSATerminatorStmt = terminate(SSAReturnTerminator(value.value))

    fun throwValue(exception: SSAExpr): SSATerminatorStmt = terminate(SSAThrowTerminator(exception.value))

    fun unreachable(): SSATerminatorStmt = terminate(SSAUnreachableTerminator)
}

fun SSABlock.ast(ids: SSAIdAllocator): SSAAstBuilder = SSAAstBuilder(this, ids)

inline fun <T> SSABlock.ast(ids: SSAIdAllocator, block: SSAAstBuilder.() -> T): T {
    return SSAAstBuilder(this, ids).block()
}
