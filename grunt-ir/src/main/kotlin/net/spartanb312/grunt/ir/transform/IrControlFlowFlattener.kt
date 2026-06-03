package net.spartanb312.grunt.ir.transform

import net.spartanb312.grunt.ir.core.*

data class IrControlFlowFlattenOptions(
    val skipExceptionRegions: Boolean = true
)

data class IrControlFlowFlattenResult(
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
class IrControlFlowFlattener(
    private val options: IrControlFlowFlattenOptions = IrControlFlowFlattenOptions()
) {
    fun flatten(function: IrFunction): IrControlFlowFlattenResult {
        if (function.blocks.size <= 1) {
            return IrControlFlowFlattenResult(false, "Function has one block")
        }
        if (options.skipExceptionRegions && function.exceptionRegions.isNotEmpty()) {
            return IrControlFlowFlattenResult(false, "Function has exception regions")
        }
        if (function.entry.args.any { initialEntryArg(function, it) == null }) {
            return IrControlFlowFlattenResult(false, "Entry block has non-parameter arguments")
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
        val liveInSourceByArgId = mutableMapOf<IrValueId, IrSsaValue>()
        val liveInArgsByBlock = mutableMapOf<IrBlock, Map<IrValueId, IrBlockArg>>()

        for (block in originalBlocks) {
            val map = linkedMapOf<IrValueId, IrBlockArg>()
            for (value in liveIns.getValue(block)) {
                val arg = IrBlockArg(
                    ids.valueId(),
                    block.args.size,
                    value.type,
                    IrBlockArgOrigin.Synthetic,
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

        val newEntry = IrBlock(ids.blockId())
        val dispatcher = IrBlock(ids.blockId())
        val invalidState = IrBlock(ids.blockId(), terminator = IrUnreachableTerminator)
        val stateArg = IrBlockArg(ids.valueId(), 0, IrI32Type, IrBlockArgOrigin.Synthetic, "flat.state")
        dispatcher.args += stateArg

        val carriers = buildList {
            for (block in originalBlocks) {
                for (arg in block.args) {
                    val dispatchArg = IrBlockArg(
                        ids.valueId(),
                        dispatcher.args.size,
                        arg.type,
                        IrBlockArgOrigin.Synthetic,
                        "flat.arg.${block.id.value}.${arg.index}"
                    )
                    dispatcher.args += dispatchArg
                    add(Carrier(block, arg, dispatchArg))
                }
            }
        }

        val caseIds = originalBlocks.mapIndexed { index, block -> block to index.toLong() }.toMap()

        fun initialCarrierValue(carrier: Carrier): IrValue {
            return if (
                carrier.targetBlock == originalEntry &&
                carrier.targetArg.index < originalArgCounts.getValue(originalEntry)
            ) {
                initialEntryArg(function, carrier.targetArg) ?: defaultValue(carrier.targetArg.type)
            } else {
                defaultValue(carrier.targetArg.type)
            }
        }

        fun replacement(block: IrBlock, value: IrValue): IrValue {
            return if (value is IrSsaValue) {
                liveInArgsByBlock.getValue(block)[value.id] ?: value
            } else {
                value
            }
        }

        fun dispatchTo(currentBlock: IrBlock, successor: IrSuccessor): IrSuccessor {
            val target = successor.block
            val targetCase = caseIds[target] ?: error("Cannot flatten edge to non-function block ${target.id}")
            val oldArgCount = originalArgCounts.getValue(target)
            val rewrittenOldArgs = successor.args.map { replacement(currentBlock, it) }
            val targetValues = linkedMapOf<IrValueId, IrValue>()

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
                add(IrIntLiteral(targetCase, IrI32Type))
                for (carrier in carriers) {
                    add(targetValues[carrier.targetArg.id] ?: defaultValue(carrier.targetArg.type))
                }
            }
            return IrSuccessor(dispatcher, args)
        }

        newEntry.terminator = IrJumpTerminator(
            IrSuccessor(
                dispatcher,
                listOf(IrIntLiteral(caseIds.getValue(originalEntry), IrI32Type)) + carriers.map(::initialCarrierValue)
            )
        )

        dispatcher.terminator = IrSwitchTerminator(
            stateArg,
            originalBlocks.map { block ->
                IrSwitchCase(
                    caseIds.getValue(block),
                    IrSuccessor(block, block.args.map { arg ->
                        carriers.first { it.targetArg == arg }.dispatchArg
                    })
                )
            },
            IrSuccessor(invalidState)
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

        return IrControlFlowFlattenResult(true)
    }

    private fun collectLiveIns(
        blocks: List<IrBlock>,
        parameterIds: Set<IrValueId>,
        localDefs: Map<IrBlock, Set<IrValueId>>
    ): Map<IrBlock, List<IrSsaValue>> {
        return blocks.associateWith { block ->
            val result = linkedMapOf<IrValueId, IrSsaValue>()
            fun add(value: IrValue) {
                if (value !is IrSsaValue) return
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
        block: IrBlock,
        terminator: IrTerminator,
        replacement: (IrBlock, IrValue) -> IrValue,
        dispatchTo: (IrBlock, IrSuccessor) -> IrSuccessor
    ): IrTerminator {
        return when (terminator) {
            is IrJumpTerminator -> IrJumpTerminator(dispatchTo(block, terminator.target))
            is IrBranchTerminator -> IrBranchTerminator(
                replacement(block, terminator.condition),
                dispatchTo(block, terminator.trueTarget),
                dispatchTo(block, terminator.falseTarget)
            )
            is IrSwitchTerminator -> IrSwitchTerminator(
                replacement(block, terminator.value),
                terminator.cases.map { IrSwitchCase(it.key, dispatchTo(block, it.target)) },
                dispatchTo(block, terminator.defaultTarget)
            )
            is IrReturnTerminator -> IrReturnTerminator(terminator.value?.let { replacement(block, it) })
            is IrThrowTerminator -> IrThrowTerminator(replacement(block, terminator.exception))
            IrUnreachableTerminator -> IrUnreachableTerminator
        }
    }

    private fun rewriteInstruction(
        instruction: IrInstruction,
        replacements: Map<IrValueId, IrValue>
    ): IrInstruction {
        fun r(value: IrValue): IrValue = if (value is IrSsaValue) replacements[value.id] ?: value else value
        fun rn(value: IrValue?): IrValue? = value?.let(::r)
        fun rs(values: List<IrValue>): List<IrValue> = values.map(::r)

        return when (instruction) {
            is IrUnaryInstruction -> instruction.copy(value = r(instruction.value))
            is IrBinaryInstruction -> instruction.copy(lhs = r(instruction.lhs), rhs = r(instruction.rhs))
            is IrCompareInstruction -> instruction.copy(lhs = r(instruction.lhs), rhs = r(instruction.rhs))
            is IrConvertInstruction -> instruction.copy(value = r(instruction.value))
            is IrLoadFieldInstruction -> instruction.copy(receiver = rn(instruction.receiver))
            is IrStoreFieldInstruction -> instruction.copy(receiver = rn(instruction.receiver), value = r(instruction.value))
            is IrArrayLoadInstruction -> instruction.copy(array = r(instruction.array), index = r(instruction.index))
            is IrArrayStoreInstruction -> instruction.copy(
                array = r(instruction.array),
                index = r(instruction.index),
                value = r(instruction.value)
            )
            is IrCallInstruction -> instruction.copy(args = rs(instruction.args))
            is IrResolveDynamicValueInstruction -> instruction
            is IrDynamicCallInstruction -> instruction.copy(args = rs(instruction.args))
            is IrAllocateInstruction -> instruction.copy(args = rs(instruction.args))
            is IrIntrinsicInstruction -> instruction.copy(args = rs(instruction.args))
            is IrBarrierInstruction -> instruction
        }
    }

    private fun initialEntryArg(function: IrFunction, arg: IrBlockArg): IrValue? {
        val origin = arg.origin
        return if (origin is IrBlockArgOrigin.Parameter) {
            function.parameters.getOrNull(origin.index)?.takeIf { IrTypes.isAssignable(it.type, arg.type) }
        } else {
            null
        }
    }

    private fun defaultValue(type: IrType): IrValue {
        return when (type) {
            IrBoolType -> IrBoolLiteral(false)
            is IrIntegerType -> IrIntLiteral(0, type)
            is IrFloatType -> IrFloatLiteral(0.0, type)
            IrNullType -> IrNullLiteral
            is IrRefType,
            is IrArrayType -> IrNullLiteral
            IrUnknownType -> IrOpaqueLiteral("flat.default", IrUnknownType)
            is IrOpaqueType -> IrOpaqueLiteral("flat.default", type)
            IrVoidType -> error("Void value cannot be used as a dispatcher carrier")
        }
    }

    private data class Carrier(
        val targetBlock: IrBlock,
        val targetArg: IrBlockArg,
        val dispatchArg: IrBlockArg
    )

    private class FreshIds(function: IrFunction) {
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

        fun blockId() = IrBlockId(nextBlockId++)

        fun valueId() = IrValueId(nextValueId++)
    }
}
