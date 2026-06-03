package net.spartanb312.grunt.ir.core

/** One structural or typing issue found in an IR function. */
data class IrVerificationIssue(
    val message: String,
    val block: IrBlock? = null
) {
    override fun toString(): String {
        return if (block != null) "${block.id}: $message" else message
    }
}

/** Result of verifying one function. */
data class IrVerificationResult(
    val issues: List<IrVerificationIssue>
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
object IrVerifier {
    fun verify(function: IrFunction): IrVerificationResult {
        val issues = mutableListOf<IrVerificationIssue>()
        val blockSet = function.blocks.toSet()
        val blockIds = mutableSetOf<IrBlockId>()

        if (function.entry !in blockSet) {
            issues += IrVerificationIssue("Entry block ${function.entry.id} is not part of the function")
        }

        for (block in function.blocks) {
            if (!blockIds.add(block.id)) {
                issues += IrVerificationIssue("Duplicate block id ${block.id}", block)
            }
        }

        val definitions = collectDefinitions(function, issues)
        val dominators = computeDominators(function)

        function.parameters.forEachIndexed { index, parameter ->
            val expected = function.symbol.parameterTypes.getOrNull(index)
            if (expected == null) {
                issues += IrVerificationIssue("Unexpected parameter ${parameter.id} at index $index")
            } else if (!IrTypes.isAssignable(parameter.type, expected)) {
                issues += IrVerificationIssue(
                    "Parameter ${parameter.id} has type ${parameter.type.displayName}, expected ${expected.displayName}"
                )
            }
        }

        if (function.parameters.size != function.symbol.parameterTypes.size) {
            issues += IrVerificationIssue(
                "Function has ${function.parameters.size} parameters, symbol expects ${function.symbol.parameterTypes.size}"
            )
        }

        for (block in function.blocks) {
            verifyBlock(function, block, definitions, dominators, blockSet, issues)
        }

        for (region in function.exceptionRegions) {
            if (region.handler !in blockSet) {
                issues += IrVerificationIssue("Exception handler ${region.handler.id} is not part of the function")
            }
            for (protectedBlock in region.protectedBlocks) {
                if (protectedBlock !in blockSet) {
                    issues += IrVerificationIssue("Protected block ${protectedBlock.id} is not part of the function")
                }
            }
            val caughtType = region.caughtType
            if (caughtType != null && caughtType != IrUnknownType && !IrTypes.isReference(caughtType)) {
                issues += IrVerificationIssue("Exception caught type must be a reference type, got ${caughtType.displayName}")
            }
        }

        return IrVerificationResult(issues)
    }

    private fun collectDefinitions(
        function: IrFunction,
        issues: MutableList<IrVerificationIssue>
    ): Map<IrValueId, ValueDef> {
        val definitions = mutableMapOf<IrValueId, ValueDef>()

        fun add(value: IrSsaValue, def: ValueDef, block: IrBlock? = null) {
            val previous = definitions.putIfAbsent(value.id, def)
            if (previous != null) {
                issues += IrVerificationIssue("Duplicate SSA value id ${value.id}", block)
            }
        }

        for (parameter in function.parameters) {
            add(parameter, ValueDef.Parameter(parameter))
        }

        for (block in function.blocks) {
            block.args.forEachIndexed { index, arg ->
                if (arg.index != index) {
                    issues += IrVerificationIssue("Block arg ${arg.id} index is ${arg.index}, expected $index", block)
                }
                add(arg, ValueDef.BlockArg(block, arg), block)
            }
            block.instructions.forEachIndexed { index, instruction ->
                val result = instruction.result
                if (result != null) {
                    if (result.type == IrVoidType) {
                        issues += IrVerificationIssue("Instruction result ${result.id} cannot have void type", block)
                    }
                    add(result, ValueDef.InstructionResult(block, index, result), block)
                }
            }
        }

        return definitions
    }

    private fun verifyBlock(
        function: IrFunction,
        block: IrBlock,
        definitions: Map<IrValueId, ValueDef>,
        dominators: Map<IrBlock, Set<IrBlock>>,
        blockSet: Set<IrBlock>,
        issues: MutableList<IrVerificationIssue>
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
        instruction: IrInstruction,
        block: IrBlock,
        issues: MutableList<IrVerificationIssue>
    ) {
        when (instruction) {
            is IrUnaryInstruction -> {
                if (!IrTypes.isAssignable(instruction.value.type, instruction.result.type)) {
                    issues += IrVerificationIssue("Unary result type does not match operand type", block)
                }
            }
            is IrBinaryInstruction -> {
                if (!IrTypes.isAssignable(instruction.lhs.type, instruction.rhs.type)) {
                    issues += IrVerificationIssue("Binary operands have incompatible types", block)
                }
                if (!IrTypes.isAssignable(instruction.lhs.type, instruction.result.type)) {
                    issues += IrVerificationIssue("Binary result type does not match lhs type", block)
                }
            }
            is IrCompareInstruction -> {
                if (instruction.result.type != IrBoolType) {
                    issues += IrVerificationIssue("Compare result must be bool", block)
                }
            }
            is IrConvertInstruction -> {
                if (!IrTypes.isAssignable(instruction.targetType, instruction.result.type)) {
                    issues += IrVerificationIssue("Convert result type does not match target type", block)
                }
            }
            is IrLoadFieldInstruction -> {
                verifyFieldReceiver(instruction.field, instruction.receiver, block, issues)
                if (!IrTypes.isAssignable(instruction.field.type, instruction.result.type)) {
                    issues += IrVerificationIssue("Field load result type does not match field type", block)
                }
            }
            is IrStoreFieldInstruction -> {
                verifyFieldReceiver(instruction.field, instruction.receiver, block, issues)
                if (!IrTypes.isAssignable(instruction.value.type, instruction.field.type)) {
                    issues += IrVerificationIssue("Stored value type does not match field type", block)
                }
            }
            is IrArrayLoadInstruction -> {
                if (!IrTypes.isInteger(instruction.index.type)) {
                    issues += IrVerificationIssue("Array index must be integer", block)
                }
            }
            is IrArrayStoreInstruction -> {
                if (!IrTypes.isInteger(instruction.index.type)) {
                    issues += IrVerificationIssue("Array index must be integer", block)
                }
            }
            is IrCallInstruction -> verifyCall(
                instruction.target,
                instruction.args,
                instruction.result,
                block,
                issues
            )
            is IrResolveDynamicValueInstruction -> {
                if (!IrTypes.isAssignable(instruction.site.type, instruction.result.type)) {
                    issues += IrVerificationIssue("Dynamic value result type does not match site type", block)
                }
                verifyDynamicBarrier(instruction.effect, "Dynamic value resolve", block, issues)
            }
            is IrDynamicCallInstruction -> {
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
            is IrAllocateInstruction -> {
                if (!IrTypes.isAssignable(instruction.allocation.type, instruction.result.type)) {
                    issues += IrVerificationIssue("Allocation result type does not match allocation type", block)
                }
            }
            is IrIntrinsicInstruction -> verifyCall(
                instruction.intrinsic,
                instruction.args,
                instruction.result,
                block,
                issues
            )
            is IrBarrierInstruction -> {
                if (!instruction.effect.isBarrier) {
                    issues += IrVerificationIssue("Barrier instruction must carry a barrier effect", block)
                }
            }
        }
    }

    private fun verifyTerminator(
        function: IrFunction,
        block: IrBlock,
        blockSet: Set<IrBlock>,
        issues: MutableList<IrVerificationIssue>
    ) {
        for (successor in block.terminator.successors) {
            if (successor.block !in blockSet) {
                issues += IrVerificationIssue("Successor ${successor.block.id} is not part of the function", block)
            }
            if (successor.args.size != successor.block.args.size) {
                issues += IrVerificationIssue(
                    "Successor ${successor.block.id} expects ${successor.block.args.size} args, got ${successor.args.size}",
                    block
                )
            }
            for ((index, arg) in successor.args.withIndex()) {
                val expected = successor.block.args.getOrNull(index)?.type ?: continue
                if (!IrTypes.isAssignable(arg.type, expected)) {
                    issues += IrVerificationIssue(
                        "Successor arg $index for ${successor.block.id} has type ${arg.type.displayName}, expected ${expected.displayName}",
                        block
                    )
                }
            }
        }

        when (val terminator = block.terminator) {
            is IrJumpTerminator -> Unit
            is IrBranchTerminator -> {
                if (terminator.condition.type != IrBoolType) {
                    issues += IrVerificationIssue("Branch condition must be bool", block)
                }
            }
            is IrSwitchTerminator -> {
                if (!IrTypes.isInteger(terminator.value.type)) {
                    issues += IrVerificationIssue("Switch value must be integer", block)
                }
            }
            is IrReturnTerminator -> {
                if (function.returnType == IrVoidType) {
                    if (terminator.value != null) {
                        issues += IrVerificationIssue("Void function cannot return a value", block)
                    }
                } else {
                    val value = terminator.value
                    if (value == null) {
                        issues += IrVerificationIssue("Non-void function must return a value", block)
                    } else if (!IrTypes.isAssignable(value.type, function.returnType)) {
                        issues += IrVerificationIssue(
                            "Return value has type ${value.type.displayName}, expected ${function.returnType.displayName}",
                            block
                        )
                    }
                }
            }
            is IrThrowTerminator -> {
                if (!IrTypes.isReference(terminator.exception.type) && terminator.exception.type != IrUnknownType) {
                    issues += IrVerificationIssue("Thrown value must be a reference type", block)
                }
            }
            IrUnreachableTerminator -> Unit
        }
    }

    private fun verifyUse(
        value: IrValue,
        useBlock: IrBlock,
        useIndex: Int,
        definitions: Map<IrValueId, ValueDef>,
        dominators: Map<IrBlock, Set<IrBlock>>,
        issues: MutableList<IrVerificationIssue>
    ) {
        if (value !is IrSsaValue) return

        val def = definitions[value.id]
        if (def == null) {
            issues += IrVerificationIssue("Use of undefined SSA value ${value.id}", useBlock)
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
            issues += IrVerificationIssue("Use of ${value.id} is not dominated by its definition", useBlock)
        }
    }

    private fun verifyFieldReceiver(
        field: IrFieldRef,
        receiver: IrValue?,
        block: IrBlock,
        issues: MutableList<IrVerificationIssue>
    ) {
        if (field.isStatic && receiver != null) {
            issues += IrVerificationIssue("Static field access must not have a receiver", block)
        }
        if (!field.isStatic && receiver == null) {
            issues += IrVerificationIssue("Instance field access must have a receiver", block)
        }
        if (receiver != null && !IrTypes.isReference(receiver.type) && receiver.type != IrUnknownType) {
            issues += IrVerificationIssue("Field receiver must be a reference type", block)
        }
    }

    private fun verifyCall(
        target: IrCallableRef,
        args: List<IrValue>,
        result: IrInstructionResult?,
        block: IrBlock,
        issues: MutableList<IrVerificationIssue>
    ) {
        verifySignature(target.parameterTypes, target.returnType, args, result, "Call ${target.displayName}", block, issues)
    }

    private fun verifySignature(
        parameterTypes: List<IrType>,
        returnType: IrType,
        args: List<IrValue>,
        result: IrInstructionResult?,
        label: String,
        block: IrBlock,
        issues: MutableList<IrVerificationIssue>
    ) {
        if (args.size != parameterTypes.size) {
            issues += IrVerificationIssue("$label expects ${parameterTypes.size} args, got ${args.size}", block)
        }
        for ((index, arg) in args.withIndex()) {
            val expected = parameterTypes.getOrNull(index) ?: continue
            if (!IrTypes.isAssignable(arg.type, expected)) {
                issues += IrVerificationIssue(
                    "$label arg $index has type ${arg.type.displayName}, expected ${expected.displayName}",
                    block
                )
            }
        }
        if (returnType == IrVoidType) {
            if (result != null) {
                issues += IrVerificationIssue("$label returns void but has result ${result.id}", block)
            }
        } else {
            if (result == null) {
                issues += IrVerificationIssue("$label returns ${returnType.displayName} but has no result", block)
            } else if (!IrTypes.isAssignable(returnType, result.type)) {
                issues += IrVerificationIssue(
                    "$label result has type ${result.type.displayName}, expected ${returnType.displayName}",
                    block
                )
            }
        }
    }

    private fun verifyDynamicBarrier(
        effect: IrEffect,
        label: String,
        block: IrBlock,
        issues: MutableList<IrVerificationIssue>
    ) {
        if (!effect.resolvesExternal || !effect.isBarrier || effect.canDuplicate || effect.canMove) {
            issues += IrVerificationIssue("$label must be an external resolving barrier", block)
        }
    }

    private fun computeDominators(function: IrFunction): Map<IrBlock, Set<IrBlock>> {
        val blocks = function.blocks.toSet()
        if (blocks.isEmpty()) return emptyMap()

        val predecessors = function.predecessors(includeExceptionEdges = true)
        val dominators = mutableMapOf<IrBlock, MutableSet<IrBlock>>()

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
        definitionBlock: IrBlock,
        useBlock: IrBlock,
        dominators: Map<IrBlock, Set<IrBlock>>
    ): Boolean {
        return definitionBlock in dominators.getOrDefault(useBlock, emptySet())
    }

    private sealed interface ValueDef {
        data class Parameter(val value: IrParameter) : ValueDef
        data class BlockArg(val block: IrBlock, val value: IrBlockArg) : ValueDef
        data class InstructionResult(val block: IrBlock, val index: Int, val value: IrInstructionResult) : ValueDef
    }
}
