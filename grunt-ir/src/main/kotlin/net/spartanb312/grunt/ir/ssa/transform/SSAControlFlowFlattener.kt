package net.spartanb312.grunt.ir.ssa.transform

import net.spartanb312.grunt.ir.ssa.core.*

data class SSAControlFlowFlattenOptions(
    val skipExceptionRegions: Boolean = true
)

data class SSAControlFlowFlattenResult(
    val changed: Boolean,
    val reason: String? = null
)

/**
 * Rewrites normal CFG edges through a central dispatcher block.
 *
 * The pass preserves SSA by turning cross-block uses into synthetic block
 * arguments before flattening. This keeps values explicit after the dispatcher
 * weakens the original dominance relation between blocks.
 */
class SSAControlFlowFlattener(
    private val options: SSAControlFlowFlattenOptions = SSAControlFlowFlattenOptions()
) {
    fun flatten(function: SSAFunction): SSAControlFlowFlattenResult {
        if (function.blocks.size <= 1) {
            return SSAControlFlowFlattenResult(false, "Function has one block")
        }
        if (options.skipExceptionRegions && function.exceptionRegions.isNotEmpty()) {
            return SSAControlFlowFlattenResult(false, "Function has exception regions")
        }
        if (function.entry.args.any { initialEntryArg(function, it) == null }) {
            return SSAControlFlowFlattenResult(false, "Entry block has non-parameter arguments")
        }

        val originalBlocks = function.blocks.toList()
        val originalEntry = function.entry
        val ids = FreshIds(function)
        val parameterIds = function.parameters.mapTo(mutableSetOf()) { it.id }
        val originalArgCounts = originalBlocks.associateWith { it.args.size }
        val localDefs = originalBlocks.associateWith { block ->
            buildSet {
                block.args.forEach { add(it.id) }
                block.instructions.forEach { instruction ->
                    instruction.result?.let { add(it.id) }
                }
            }
        }

        val liveIns = collectLiveIns(originalBlocks, parameterIds, localDefs)
        val liveInSourceByArgId = mutableMapOf<SSAValueId, SSAStructure>()
        val liveInArgsByBlock = mutableMapOf<SSABlock, Map<SSAValueId, SSABlockArg>>()

        for (block in originalBlocks) {
            val map = linkedMapOf<SSAValueId, SSABlockArg>()
            for (value in liveIns.getValue(block)) {
                val arg = SSABlockArg(
                    ids.valueId(),
                    block.args.size,
                    value.type,
                    SSABlockArgOrigin.Synthetic,
                    "flat.live.${value.id.value}"
                )
                block.args += arg
                map[value.id] = arg
                liveInSourceByArgId[arg.id] = value
            }
            liveInArgsByBlock[block] = map
        }

        for (block in originalBlocks) {
            val replacements = liveInArgsByBlock.getValue(block)
            for (index in block.instructions.indices) {
                block.instructions[index] = rewriteInstruction(block.instructions[index], replacements)
            }
        }

        val newEntry = SSABlock(ids.blockId())
        val dispatcher = SSABlock(ids.blockId())
        val invalidState = SSABlock(ids.blockId(), terminator = SSAUnreachableTerminator)
        val stateArg = SSABlockArg(ids.valueId(), 0, SSAI32Type, SSABlockArgOrigin.Synthetic, "flat.state")
        dispatcher.args += stateArg

        val carriers = buildList {
            for (block in originalBlocks) {
                for (arg in block.args) {
                    val dispatchArg = SSABlockArg(
                        ids.valueId(),
                        dispatcher.args.size,
                        arg.type,
                        SSABlockArgOrigin.Synthetic,
                        "flat.arg.${block.id.value}.${arg.index}"
                    )
                    dispatcher.args += dispatchArg
                    add(Carrier(block, arg, dispatchArg))
                }
            }
        }

        val caseIds = originalBlocks.mapIndexed { index, block -> block to index.toLong() }.toMap()

        fun initialCarrierValue(carrier: Carrier): SSAValue {
            return if (
                carrier.targetBlock == originalEntry &&
                carrier.targetArg.index < originalArgCounts.getValue(originalEntry)
            ) {
                initialEntryArg(function, carrier.targetArg) ?: defaultValue(carrier.targetArg.type)
            } else {
                defaultValue(carrier.targetArg.type)
            }
        }

        fun replacement(block: SSABlock, value: SSAValue): SSAValue {
            return if (value is SSAStructure) {
                liveInArgsByBlock.getValue(block)[value.id] ?: value
            } else {
                value
            }
        }

        fun dispatchTo(currentBlock: SSABlock, successor: SSASuccessor): SSASuccessor {
            val target = successor.block
            val targetCase = caseIds[target] ?: error("Cannot flatten edge to non-function block ${target.id}")
            val oldArgCount = originalArgCounts.getValue(target)
            val rewrittenOldArgs = successor.args.map { replacement(currentBlock, it) }
            val targetValues = linkedMapOf<SSAValueId, SSAValue>()

            for (arg in target.args) {
                val value = if (arg.index < oldArgCount) {
                    rewrittenOldArgs.getOrNull(arg.index)
                        ?: error("Successor ${target.id} is missing arg ${arg.index}")
                } else {
                    val source = liveInSourceByArgId[arg.id]
                        ?: error("Missing live-in source for synthetic arg ${arg.id}")
                    replacement(currentBlock, source)
                }
                targetValues[arg.id] = value
            }

            val args = buildList {
                add(SSAIntLiteral(targetCase, SSAI32Type))
                for (carrier in carriers) {
                    add(targetValues[carrier.targetArg.id] ?: defaultValue(carrier.targetArg.type))
                }
            }
            return SSASuccessor(dispatcher, args)
        }

        newEntry.terminator = SSAJumpTerminator(
            SSASuccessor(
                dispatcher,
                listOf(SSAIntLiteral(caseIds.getValue(originalEntry), SSAI32Type)) + carriers.map(::initialCarrierValue)
            )
        )

        dispatcher.terminator = SSASwitchTerminator(
            stateArg,
            originalBlocks.map { block ->
                SSASwitchCase(
                    caseIds.getValue(block),
                    SSASuccessor(block, block.args.map { arg ->
                        carriers.first { it.targetArg == arg }.dispatchArg
                    })
                )
            },
            SSASuccessor(invalidState)
        )

        for (block in originalBlocks) {
            block.terminator = flattenTerminator(block, block.terminator, ::replacement, ::dispatchTo)
        }

        function.blocks.clear()
        function.blocks += newEntry
        function.blocks += dispatcher
        function.blocks += originalBlocks
        function.blocks += invalidState
        function.entry = newEntry

        return SSAControlFlowFlattenResult(true)
    }

    private fun collectLiveIns(
        blocks: List<SSABlock>,
        parameterIds: Set<SSAValueId>,
        localDefs: Map<SSABlock, Set<SSAValueId>>
    ): Map<SSABlock, List<SSAStructure>> {
        return blocks.associateWith { block ->
            val result = linkedMapOf<SSAValueId, SSAStructure>()
            fun add(value: SSAValue) {
                if (value !is SSAStructure) return
                if (value.id in parameterIds) return
                if (value.id in localDefs.getValue(block)) return
                result.putIfAbsent(value.id, value)
            }
            block.instructions.forEach { instruction -> instruction.operands.forEach(::add) }
            block.terminator.operands.forEach(::add)
            result.values.toList()
        }
    }

    private fun flattenTerminator(
        block: SSABlock,
        terminator: SSATerminator,
        replacement: (SSABlock, SSAValue) -> SSAValue,
        dispatchTo: (SSABlock, SSASuccessor) -> SSASuccessor
    ): SSATerminator {
        return when (terminator) {
            is SSAJumpTerminator -> SSAJumpTerminator(dispatchTo(block, terminator.target))
            is SSABranchTerminator -> SSABranchTerminator(
                replacement(block, terminator.condition),
                dispatchTo(block, terminator.trueTarget),
                dispatchTo(block, terminator.falseTarget)
            )
            is SSASwitchTerminator -> SSASwitchTerminator(
                replacement(block, terminator.value),
                terminator.cases.map { SSASwitchCase(it.key, dispatchTo(block, it.target)) },
                dispatchTo(block, terminator.defaultTarget)
            )
            is SSAReturnTerminator -> SSAReturnTerminator(terminator.value?.let { replacement(block, it) })
            is SSAThrowTerminator -> SSAThrowTerminator(replacement(block, terminator.exception))
            SSAUnreachableTerminator -> SSAUnreachableTerminator
        }
    }

    private fun rewriteInstruction(
        instruction: SSAInstruction,
        replacements: Map<SSAValueId, SSAValue>
    ): SSAInstruction {
        fun r(value: SSAValue): SSAValue = if (value is SSAStructure) replacements[value.id] ?: value else value
        fun rn(value: SSAValue?): SSAValue? = value?.let(::r)
        fun rs(values: List<SSAValue>): List<SSAValue> = values.map(::r)

        return when (instruction) {
            is SSAUnaryInstruction -> instruction.copy(value = r(instruction.value))
            is SSABinaryInstruction -> instruction.copy(lhs = r(instruction.lhs), rhs = r(instruction.rhs))
            is SSACompareInstruction -> instruction.copy(lhs = r(instruction.lhs), rhs = r(instruction.rhs))
            is SSAConvertInstruction -> instruction.copy(value = r(instruction.value))
            is SSALoadFieldInstruction -> instruction.copy(receiver = rn(instruction.receiver))
            is SSAStoreFieldInstruction -> instruction.copy(receiver = rn(instruction.receiver), value = r(instruction.value))
            is SSAArrayLoadInstruction -> instruction.copy(array = r(instruction.array), index = r(instruction.index))
            is SSAArrayStoreInstruction -> instruction.copy(
                array = r(instruction.array),
                index = r(instruction.index),
                value = r(instruction.value)
            )
            is SSACallInstruction -> instruction.copy(args = rs(instruction.args))
            is SSAResolveDynamicValueInstruction -> instruction
            is SSADynamicCallInstruction -> instruction.copy(args = rs(instruction.args))
            is SSAAllocateInstruction -> instruction.copy(args = rs(instruction.args))
            is SSAIntrinsicInstruction -> instruction.copy(args = rs(instruction.args))
            is SSABarrierInstruction -> instruction
        }
    }

    private fun initialEntryArg(function: SSAFunction, arg: SSABlockArg): SSAValue? {
        val origin = arg.origin
        return if (origin is SSABlockArgOrigin.Parameter) {
            function.parameters.getOrNull(origin.index)?.takeIf { SSATypes.isAssignable(it.type, arg.type) }
        } else {
            null
        }
    }

    private fun defaultValue(type: SSAType): SSAValue {
        return when (type) {
            SSABoolType -> SSABoolLiteral(false)
            is SSAIntegerType -> SSAIntLiteral(0, type)
            is SSAFloatType -> SSAFloatLiteral(0.0, type)
            SSANullType -> SSANullLiteral
            is SSARefType,
            is SSAArrayType -> SSANullLiteral
            SSAUnknownType -> SSAOpaqueLiteral("flat.default", SSAUnknownType)
            is SSAOpaqueType -> SSAOpaqueLiteral("flat.default", type)
            SSAVoidType -> error("Void value cannot be used as a dispatcher carrier")
        }
    }

    private data class Carrier(
        val targetBlock: SSABlock,
        val targetArg: SSABlockArg,
        val dispatchArg: SSABlockArg
    )

    private class FreshIds(function: SSAFunction) {
        private var nextBlockId = (function.blocks.maxOfOrNull { it.id.value } ?: -1) + 1
        private var nextValueId = maxOf(
            function.parameters.maxOfOrNull { it.id.value } ?: -1,
            function.blocks.maxOfOrNull { block ->
                maxOf(
                    block.args.maxOfOrNull { it.id.value } ?: -1,
                    block.instructions.maxOfOrNull { it.result?.id?.value ?: -1 } ?: -1
                )
            } ?: -1
        ) + 1

        fun blockId() = SSABlockId(nextBlockId++)

        fun valueId() = SSAValueId(nextValueId++)
    }
}
