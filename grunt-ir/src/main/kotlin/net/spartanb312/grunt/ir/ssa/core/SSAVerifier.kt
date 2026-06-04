package net.spartanb312.grunt.ir.ssa.core

/** One structural or typing issue found in an IR function. */
data class SSAVerificationIssue(
    val message: String,
    val block: SSABlock? = null
) {
    override fun toString(): String {
        return if (block != null) "${block.id}: $message" else message
    }
}

/** Result of verifying one function. */
data class SSAVerificationResult(
    val issues: List<SSAVerificationIssue>
) {
    val isValid get() = issues.isEmpty()

    fun requireValid() {
        if (!isValid) {
            error(issues.joinToString(separator = "\n") { it.toString() })
        }
    }
}

/**
 * Lightweight verifier for core SSA invariants.
 *
 * The verifier checks local structure, basic type compatibility, successor
 * arity, and SSA dominance. It is not a semantic verifier for any particular
 * frontend or backend.
 */
object SSAVerifier {
    fun verify(function: SSAFunction): SSAVerificationResult {
        val issues = mutableListOf<SSAVerificationIssue>()
        val blockSet = function.blocks.toSet()
        val blockIds = mutableSetOf<SSABlockId>()

        if (function.entry !in blockSet) {
            issues += SSAVerificationIssue("Entry block ${function.entry.id} is not part of the function")
        }

        for (block in function.blocks) {
            if (!blockIds.add(block.id)) {
                issues += SSAVerificationIssue("Duplicate block id ${block.id}", block)
            }
        }

        val definitions = collectDefinitions(function, issues)
        val dominators = computeDominators(function)

        function.parameters.forEachIndexed { index, parameter ->
            val expected = function.symbol.parameterTypes.getOrNull(index)
            if (expected == null) {
                issues += SSAVerificationIssue("Unexpected parameter ${parameter.id} at index $index")
            } else if (!SSATypes.isAssignable(parameter.type, expected)) {
                issues += SSAVerificationIssue(
                    "Parameter ${parameter.id} has type ${parameter.type.displayName}, expected ${expected.displayName}"
                )
            }
        }

        if (function.parameters.size != function.symbol.parameterTypes.size) {
            issues += SSAVerificationIssue(
                "Function has ${function.parameters.size} parameters, symbol expects ${function.symbol.parameterTypes.size}"
            )
        }

        for (block in function.blocks) {
            verifyBlock(function, block, definitions, dominators, blockSet, issues)
        }

        for (region in function.exceptionRegions) {
            if (region.handler !in blockSet) {
                issues += SSAVerificationIssue("Exception handler ${region.handler.id} is not part of the function")
            }
            for (protectedBlock in region.protectedBlocks) {
                if (protectedBlock !in blockSet) {
                    issues += SSAVerificationIssue("Protected block ${protectedBlock.id} is not part of the function")
                }
            }
            val caughtType = region.caughtType
            if (caughtType != null && caughtType != SSAUnknownType && !SSATypes.isReference(caughtType)) {
                issues += SSAVerificationIssue("Exception caught type must be a reference type, got ${caughtType.displayName}")
            }
        }

        return SSAVerificationResult(issues)
    }

    private fun collectDefinitions(
        function: SSAFunction,
        issues: MutableList<SSAVerificationIssue>
    ): Map<SSAValueId, ValueDef> {
        val definitions = mutableMapOf<SSAValueId, ValueDef>()

        fun add(value: SSAStructure, def: ValueDef, block: SSABlock? = null) {
            val previous = definitions.putIfAbsent(value.id, def)
            if (previous != null) {
                issues += SSAVerificationIssue("Duplicate SSA value id ${value.id}", block)
            }
        }

        for (parameter in function.parameters) {
            add(parameter, ValueDef.Parameter(parameter))
        }

        for (block in function.blocks) {
            block.args.forEachIndexed { index, arg ->
                if (arg.index != index) {
                    issues += SSAVerificationIssue("Block arg ${arg.id} index is ${arg.index}, expected $index", block)
                }
                add(arg, ValueDef.BlockArg(block, arg), block)
            }
            block.instructions.forEachIndexed { index, instruction ->
                val result = instruction.result
                if (result != null) {
                    if (result.type == SSAVoidType) {
                        issues += SSAVerificationIssue("Instruction result ${result.id} cannot have void type", block)
                    }
                    add(result, ValueDef.InstructionResult(block, index, result), block)
                }
            }
        }

        return definitions
    }

    private fun verifyBlock(
        function: SSAFunction,
        block: SSABlock,
        definitions: Map<SSAValueId, ValueDef>,
        dominators: Map<SSABlock, Set<SSABlock>>,
        blockSet: Set<SSABlock>,
        issues: MutableList<SSAVerificationIssue>
    ) {
        block.instructions.forEachIndexed { index, instruction ->
            verifyInstruction(instruction, block, issues)
            for (operand in instruction.operands) {
                verifyUse(operand, block, index, definitions, dominators, issues)
            }
        }

        verifyTerminator(function, block, blockSet, issues)
        for (operand in block.terminator.operands) {
            verifyUse(operand, block, block.instructions.size, definitions, dominators, issues)
        }
    }

    private fun verifyInstruction(
        instruction: SSAInstruction,
        block: SSABlock,
        issues: MutableList<SSAVerificationIssue>
    ) {
        when (instruction) {
            is SSAUnaryInstruction -> {
                if (!SSATypes.isAssignable(instruction.value.type, instruction.result.type)) {
                    issues += SSAVerificationIssue("Unary result type does not match operand type", block)
                }
            }
            is SSABinaryInstruction -> {
                if (!SSATypes.isAssignable(instruction.lhs.type, instruction.rhs.type)) {
                    issues += SSAVerificationIssue("Binary operands have incompatible types", block)
                }
                if (!SSATypes.isAssignable(instruction.lhs.type, instruction.result.type)) {
                    issues += SSAVerificationIssue("Binary result type does not match lhs type", block)
                }
            }
            is SSACompareInstruction -> {
                if (instruction.result.type != SSABoolType) {
                    issues += SSAVerificationIssue("Compare result must be bool", block)
                }
            }
            is SSAConvertInstruction -> {
                if (!SSATypes.isAssignable(instruction.targetType, instruction.result.type)) {
                    issues += SSAVerificationIssue("Convert result type does not match target type", block)
                }
            }
            is SSALoadFieldInstruction -> {
                verifyFieldReceiver(instruction.field, instruction.receiver, block, issues)
                if (!SSATypes.isAssignable(instruction.field.type, instruction.result.type)) {
                    issues += SSAVerificationIssue("Field load result type does not match field type", block)
                }
            }
            is SSAStoreFieldInstruction -> {
                verifyFieldReceiver(instruction.field, instruction.receiver, block, issues)
                if (!SSATypes.isAssignable(instruction.value.type, instruction.field.type)) {
                    issues += SSAVerificationIssue("Stored value type does not match field type", block)
                }
            }
            is SSAArrayLoadInstruction -> {
                if (!SSATypes.isInteger(instruction.index.type)) {
                    issues += SSAVerificationIssue("Array index must be integer", block)
                }
            }
            is SSAArrayStoreInstruction -> {
                if (!SSATypes.isInteger(instruction.index.type)) {
                    issues += SSAVerificationIssue("Array index must be integer", block)
                }
            }
            is SSACallInstruction -> verifyCall(
                instruction.target,
                instruction.args,
                instruction.result,
                block,
                issues
            )
            is SSAResolveDynamicValueInstruction -> {
                if (!SSATypes.isAssignable(instruction.site.type, instruction.result.type)) {
                    issues += SSAVerificationIssue("Dynamic value result type does not match site type", block)
                }
                verifyDynamicBarrier(instruction.effect, "Dynamic value resolve", block, issues)
            }
            is SSADynamicCallInstruction -> {
                verifySignature(
                    instruction.site.parameterTypes,
                    instruction.site.returnType,
                    instruction.args,
                    instruction.result,
                    "Dynamic call",
                    block,
                    issues
                )
                verifyDynamicBarrier(instruction.effect, "Dynamic call", block, issues)
            }
            is SSAAllocateInstruction -> {
                if (!SSATypes.isAssignable(instruction.allocation.type, instruction.result.type)) {
                    issues += SSAVerificationIssue("Allocation result type does not match allocation type", block)
                }
            }
            is SSAIntrinsicInstruction -> verifyCall(
                instruction.intrinsic,
                instruction.args,
                instruction.result,
                block,
                issues
            )
            is SSABarrierInstruction -> {
                if (!instruction.effect.isBarrier) {
                    issues += SSAVerificationIssue("Barrier instruction must carry a barrier effect", block)
                }
            }
        }
    }

    private fun verifyTerminator(
        function: SSAFunction,
        block: SSABlock,
        blockSet: Set<SSABlock>,
        issues: MutableList<SSAVerificationIssue>
    ) {
        for (successor in block.terminator.successors) {
            if (successor.block !in blockSet) {
                issues += SSAVerificationIssue("Successor ${successor.block.id} is not part of the function", block)
            }
            if (successor.args.size != successor.block.args.size) {
                issues += SSAVerificationIssue(
                    "Successor ${successor.block.id} expects ${successor.block.args.size} args, got ${successor.args.size}",
                    block
                )
            }
            for ((index, arg) in successor.args.withIndex()) {
                val expected = successor.block.args.getOrNull(index)?.type ?: continue
                if (!SSATypes.isAssignable(arg.type, expected)) {
                    issues += SSAVerificationIssue(
                        "Successor arg $index for ${successor.block.id} has type ${arg.type.displayName}, expected ${expected.displayName}",
                        block
                    )
                }
            }
        }

        when (val terminator = block.terminator) {
            is SSAJumpTerminator -> Unit
            is SSABranchTerminator -> {
                if (terminator.condition.type != SSABoolType) {
                    issues += SSAVerificationIssue("Branch condition must be bool", block)
                }
            }
            is SSASwitchTerminator -> {
                if (!SSATypes.isInteger(terminator.value.type)) {
                    issues += SSAVerificationIssue("Switch value must be integer", block)
                }
            }
            is SSAReturnTerminator -> {
                if (function.returnType == SSAVoidType) {
                    if (terminator.value != null) {
                        issues += SSAVerificationIssue("Void function cannot return a value", block)
                    }
                } else {
                    val value = terminator.value
                    if (value == null) {
                        issues += SSAVerificationIssue("Non-void function must return a value", block)
                    } else if (!SSATypes.isAssignable(value.type, function.returnType)) {
                        issues += SSAVerificationIssue(
                            "Return value has type ${value.type.displayName}, expected ${function.returnType.displayName}",
                            block
                        )
                    }
                }
            }
            is SSAThrowTerminator -> {
                if (!SSATypes.isReference(terminator.exception.type) && terminator.exception.type != SSAUnknownType) {
                    issues += SSAVerificationIssue("Thrown value must be a reference type", block)
                }
            }
            SSAUnreachableTerminator -> Unit
        }
    }

    private fun verifyUse(
        value: SSAValue,
        useBlock: SSABlock,
        useIndex: Int,
        definitions: Map<SSAValueId, ValueDef>,
        dominators: Map<SSABlock, Set<SSABlock>>,
        issues: MutableList<SSAVerificationIssue>
    ) {
        if (value !is SSAStructure) return

        val def = definitions[value.id]
        if (def == null) {
            issues += SSAVerificationIssue("Use of undefined SSA value ${value.id}", useBlock)
            return
        }

        val valid = when (def) {
            is ValueDef.Parameter -> true
            is ValueDef.BlockArg -> dominates(def.block, useBlock, dominators)
            is ValueDef.InstructionResult -> {
                if (def.block == useBlock) def.index < useIndex
                else dominates(def.block, useBlock, dominators)
            }
        }

        if (!valid) {
            issues += SSAVerificationIssue("Use of ${value.id} is not dominated by its definition", useBlock)
        }
    }

    private fun verifyFieldReceiver(
        field: SSAFieldRef,
        receiver: SSAValue?,
        block: SSABlock,
        issues: MutableList<SSAVerificationIssue>
    ) {
        if (field.isStatic && receiver != null) {
            issues += SSAVerificationIssue("Static field access must not have a receiver", block)
        }
        if (!field.isStatic && receiver == null) {
            issues += SSAVerificationIssue("Instance field access must have a receiver", block)
        }
        if (receiver != null && !SSATypes.isReference(receiver.type) && receiver.type != SSAUnknownType) {
            issues += SSAVerificationIssue("Field receiver must be a reference type", block)
        }
    }

    private fun verifyCall(
        target: SSACallableRef,
        args: List<SSAValue>,
        result: SSAInstructionResult?,
        block: SSABlock,
        issues: MutableList<SSAVerificationIssue>
    ) {
        verifySignature(target.parameterTypes, target.returnType, args, result, "Call ${target.displayName}", block, issues)
    }

    private fun verifySignature(
        parameterTypes: List<SSAType>,
        returnType: SSAType,
        args: List<SSAValue>,
        result: SSAInstructionResult?,
        label: String,
        block: SSABlock,
        issues: MutableList<SSAVerificationIssue>
    ) {
        if (args.size != parameterTypes.size) {
            issues += SSAVerificationIssue("$label expects ${parameterTypes.size} args, got ${args.size}", block)
        }
        for ((index, arg) in args.withIndex()) {
            val expected = parameterTypes.getOrNull(index) ?: continue
            if (!SSATypes.isAssignable(arg.type, expected)) {
                issues += SSAVerificationIssue(
                    "$label arg $index has type ${arg.type.displayName}, expected ${expected.displayName}",
                    block
                )
            }
        }
        if (returnType == SSAVoidType) {
            if (result != null) {
                issues += SSAVerificationIssue("$label returns void but has result ${result.id}", block)
            }
        } else {
            if (result == null) {
                issues += SSAVerificationIssue("$label returns ${returnType.displayName} but has no result", block)
            } else if (!SSATypes.isAssignable(returnType, result.type)) {
                issues += SSAVerificationIssue(
                    "$label result has type ${result.type.displayName}, expected ${returnType.displayName}",
                    block
                )
            }
        }
    }

    private fun verifyDynamicBarrier(
        effect: SSAEffect,
        label: String,
        block: SSABlock,
        issues: MutableList<SSAVerificationIssue>
    ) {
        if (!effect.resolvesExternal || !effect.isBarrier || effect.canDuplicate || effect.canMove) {
            issues += SSAVerificationIssue("$label must be an external resolving barrier", block)
        }
    }

    private fun computeDominators(function: SSAFunction): Map<SSABlock, Set<SSABlock>> {
        val blocks = function.blocks.toSet()
        if (blocks.isEmpty()) return emptyMap()

        val predecessors = function.predecessors(includeExceptionEdges = true)
        val dominators = mutableMapOf<SSABlock, MutableSet<SSABlock>>()

        for (block in blocks) {
            dominators[block] = if (block == function.entry) {
                mutableSetOf(block)
            } else {
                blocks.toMutableSet()
            }
        }

        var changed: Boolean
        do {
            changed = false
            for (block in blocks) {
                if (block == function.entry) continue

                val preds = predecessors[block].orEmpty().filter { it in blocks }
                val newDominators = if (preds.isEmpty()) {
                    mutableSetOf(block)
                } else {
                    preds
                        .map { dominators.getValue(it) }
                        .reduce { acc, set -> acc.intersect(set).toMutableSet() }
                        .toMutableSet()
                        .also { it += block }
                }

                val current = dominators.getValue(block)
                if (current != newDominators) {
                    current.clear()
                    current += newDominators
                    changed = true
                }
            }
        } while (changed)

        return dominators
    }

    private fun dominates(
        definitionBlock: SSABlock,
        useBlock: SSABlock,
        dominators: Map<SSABlock, Set<SSABlock>>
    ): Boolean {
        return definitionBlock in dominators.getOrDefault(useBlock, emptySet())
    }

    private sealed interface ValueDef {
        data class Parameter(val value: SSAParameter) : ValueDef
        data class BlockArg(val block: SSABlock, val value: SSABlockArg) : ValueDef
        data class InstructionResult(val block: SSABlock, val index: Int, val value: SSAInstructionResult) : ValueDef
    }
}
