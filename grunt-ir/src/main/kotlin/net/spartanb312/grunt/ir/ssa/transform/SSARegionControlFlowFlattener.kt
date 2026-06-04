package net.spartanb312.grunt.ir.ssa.transform

import net.spartanb312.grunt.ir.ssa.core.*

data class SSARegionControlFlowFlattenOptions(
    val skipExceptionRegions: Boolean = false,
    val minRegionBlocks: Int = 2,
    val maxRegionBlocks: Int = 8,
    val maxDispatcherArgs: Int = 64
)

data class SSARegionControlFlowFlattenResult(
    val changed: Boolean,
    val flattenedRegions: Int = 0,
    val skippedRegions: Int = 0,
    val reason: String? = null
)

/**
 * Region-local control-flow flattening.
 *
 * This keeps [SSAFunction] as the method-level CFG, but inserts one dispatcher
 * per selected [SSARegion]. The region planner guarantees single-entry regions,
 * so external edges only need to target the region dispatcher for that entry.
 */
class SSARegionControlFlowFlattener(
    private val options: SSARegionControlFlowFlattenOptions = SSARegionControlFlowFlattenOptions()
) {
    fun flatten(function: SSAFunction): SSARegionControlFlowFlattenResult {
        if (function.blocks.size < options.minRegionBlocks) {
            return SSARegionControlFlowFlattenResult(false, reason = "Function has fewer than ${options.minRegionBlocks} blocks")
        }
        val regions = function.planRegions(
            SSARegionPlanOptions(
                minBlocks = options.minRegionBlocks,
                maxBlocks = options.maxRegionBlocks,
                includeFunctionEntry = true,
                allowExceptionBlocks = !options.skipExceptionRegions
            )
        )
        if (regions.isEmpty()) {
            return SSARegionControlFlowFlattenResult(false, reason = "No flattenable regions")
        }

        val ids = FreshIds(function)
        var flattened = 0
        var skipped = 0
        for (region in regions) {
            val result = flattenRegion(function, region, ids)
            if (result) flattened++ else skipped++
        }

        return SSARegionControlFlowFlattenResult(
            changed = flattened != 0,
            flattenedRegions = flattened,
            skippedRegions = skipped,
            reason = if (flattened == 0) "All regions were skipped" else null
        )
    }

    private fun flattenRegion(function: SSAFunction, region: SSARegion, ids: FreshIds): Boolean {
        val regionBlocks = region.blocks
        val originalBlocks = function.blocks.filter { it in regionBlocks }
        val originalEntry = function.entry
        if (originalBlocks.size < options.minRegionBlocks) return false
        if (!canFlattenRegion(function, region)) return false
        if (originalEntry in regionBlocks && originalEntry.args.any { initialEntryArg(function, it) == null }) {
            return false
        }

        val parameterIds = function.parameters.mapTo(mutableSetOf()) { it.id }
        val originalArgCounts = originalBlocks.associateWith { it.args.size }
        val localDefs = originalBlocks.associateWith(::collectLocalDefIds)
        val allLocalDefs = function.blocks.associateWith(::collectLocalDefIds)
        val regionDefinitions = collectDefinitions(originalBlocks)
        val functionLiveIns = collectLiveIns(function.blocks, parameterIds, allLocalDefs)
        val liveOutValuesByBlock = function.blocks
            .filter { it !in regionBlocks }
            .associateWith { block ->
                functionLiveIns.getValue(block).filter { value -> value.id in regionDefinitions }
            }
            .filterValues { it.isNotEmpty() }

        if (function.entry in liveOutValuesByBlock.keys) {
            return false
        }
        if (liveOutValuesByBlock.keys.any { it in exceptionHandlers(function) }) {
            return false
        }

        val liveIns = collectLiveIns(originalBlocks, parameterIds, localDefs, liveOutValuesByBlock)
        val projectedDispatcherArgs = 1 + originalBlocks.sumOf { block ->
            originalArgCounts.getValue(block) + liveIns.getValue(block).size
        }
        if (options.maxDispatcherArgs > 0 && projectedDispatcherArgs > options.maxDispatcherArgs) return false

        val liveInSourceByArgId = mutableMapOf<SSAValueId, SSAStructure>()
        val liveInArgsByBlock = mutableMapOf<SSABlock, Map<SSAValueId, SSABlockArg>>()
        val liveOutArgsByBlock = mutableMapOf<SSABlock, Map<SSAValueId, SSABlockArg>>()

        for (block in originalBlocks) {
            val map = linkedMapOf<SSAValueId, SSABlockArg>()
            for (value in liveIns.getValue(block)) {
                val arg = SSABlockArg(
                    ids.valueId(),
                    block.args.size,
                    value.type,
                    SSABlockArgOrigin.Synthetic,
                    "region${region.id}.live.${value.id.value}"
                )
                block.args += arg
                map[value.id] = arg
                liveInSourceByArgId[arg.id] = value
            }
            liveInArgsByBlock[block] = map
        }

        for ((block, values) in liveOutValuesByBlock) {
            val map = linkedMapOf<SSAValueId, SSABlockArg>()
            for (value in values) {
                val arg = SSABlockArg(
                    ids.valueId(),
                    block.args.size,
                    value.type,
                    SSABlockArgOrigin.Synthetic,
                    "region${region.id}.out.${value.id.value}"
                )
                block.args += arg
                map[value.id] = arg
            }
            liveOutArgsByBlock[block] = map
        }

        fun replacement(block: SSABlock, value: SSAValue): SSAValue {
            return if (value is SSAStructure) {
                liveInArgsByBlock[block]?.get(value.id)
                    ?: liveOutArgsByBlock[block]?.get(value.id)
                    ?: value
            } else {
                value
            }
        }

        fun appendLiveOutArgs(currentBlock: SSABlock, successor: SSASuccessor): SSASuccessor {
            val liveOutArgs = liveOutArgsByBlock[successor.block] ?: return successor
            if (liveOutArgs.isEmpty()) return successor
            return successor.copy(
                args = successor.args + liveOutArgs.keys.map { id ->
                    replacement(currentBlock, regionDefinitions.getValue(id))
                }
            )
        }

        for (block in originalBlocks) {
            val replacements = liveInArgsByBlock.getValue(block)
            for (index in block.instructions.indices) {
                block.instructions[index] = rewriteInstruction(block.instructions[index], replacements)
            }
        }

        for (block in function.blocks) {
            if (block in regionBlocks) continue
            val replacements = liveOutArgsByBlock[block].orEmpty()
            if (replacements.isNotEmpty()) {
                for (index in block.instructions.indices) {
                    block.instructions[index] = rewriteInstruction(block.instructions[index], replacements)
                }
            }
            block.terminator = rewriteTerminatorValuesAndSuccessors(
                block.terminator,
                rewriteValue = { replacement(block, it) },
                rewriteSuccessor = { appendLiveOutArgs(block, it) }
            )
        }

        val dispatcher = SSABlock(ids.blockId())
        val invalidState = SSABlock(ids.blockId(), terminator = SSAUnreachableTerminator)
        val stateArg = SSABlockArg(ids.valueId(), 0, SSAI32Type, SSABlockArgOrigin.Synthetic, "region${region.id}.state")
        dispatcher.args += stateArg

        val carriers = buildList {
            for (block in originalBlocks) {
                for (arg in block.args) {
                    val dispatchArg = SSABlockArg(
                        ids.valueId(),
                        dispatcher.args.size,
                        arg.type,
                        SSABlockArgOrigin.Synthetic,
                        "region${region.id}.arg.${block.id.value}.${arg.index}"
                    )
                    dispatcher.args += dispatchArg
                    add(Carrier(block, arg, dispatchArg))
                }
            }
        }

        val carrierByTargetArg = carriers.associateBy { it.targetArg }
        val caseIds = originalBlocks.mapIndexed { index, block -> block to index.toLong() }.toMap()

        fun rewriteSuccessorArgs(block: SSABlock, successor: SSASuccessor): SSASuccessor {
            val rewritten = successor.copy(args = successor.args.map { replacement(block, it) })
            return appendLiveOutArgs(block, rewritten)
        }

        fun dispatchTo(currentBlock: SSABlock, successor: SSASuccessor): SSASuccessor {
            val target = successor.block
            val targetCase = caseIds[target] ?: error("Cannot flatten edge to non-region block ${target.id}")
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

        dispatcher.terminator = SSASwitchTerminator(
            stateArg,
            originalBlocks.map { block ->
                SSASwitchCase(
                    caseIds.getValue(block),
                    SSASuccessor(block, block.args.map { arg ->
                        carrierByTargetArg.getValue(arg).dispatchArg
                    })
                )
            },
            SSASuccessor(invalidState)
        )

        for (block in originalBlocks) {
            block.terminator = flattenTerminator(
                block,
                block.terminator,
                regionBlocks,
                ::replacement,
                ::rewriteSuccessorArgs,
                ::dispatchTo
            )
        }

        redirectExternalEntryEdges(function, region, ::dispatchTo)
        if (originalEntry in regionBlocks) {
            val newEntry = SSABlock(ids.blockId())
            newEntry.terminator = SSAJumpTerminator(
                SSASuccessor(
                    dispatcher,
                    listOf(SSAIntLiteral(caseIds.getValue(originalEntry), SSAI32Type)) + carriers.map(::initialCarrierValue)
                )
            )
            insertEntryRegionBlocks(function, region, newEntry, dispatcher, invalidState)
            function.entry = newEntry
        } else {
            insertRegionBlocks(function, region, dispatcher, invalidState)
        }
        return true
    }

    private fun canFlattenRegion(function: SSAFunction, region: SSARegion): Boolean {
        if (region.entry !in region.blocks) return false
        if (function.entry in region.blocks && region.entry != function.entry) return false
        if (options.skipExceptionRegions && region.blocks.any { it in exceptionTouchedBlocks(function) }) return false
        if (region.blocks.any { it in exceptionHandlers(function) }) return false

        return function.normalEdges().none { edge ->
            edge.from !in region.blocks && edge.to in region.blocks && edge.to != region.entry
        }
    }

    private fun exceptionHandlers(function: SSAFunction): Set<SSABlock> {
        return function.exceptionRegions.mapTo(mutableSetOf()) { it.handler }
    }

    private fun exceptionTouchedBlocks(function: SSAFunction): Set<SSABlock> {
        return function.exceptionRegions.flatMapTo(mutableSetOf()) { region ->
            region.protectedBlocks + region.handler
        }
    }

    private fun redirectExternalEntryEdges(
        function: SSAFunction,
        region: SSARegion,
        dispatchTo: (SSABlock, SSASuccessor) -> SSASuccessor
    ) {
        for (block in function.blocks) {
            if (block in region.blocks) continue
            block.terminator = rewriteTerminatorSuccessors(block.terminator) { successor ->
                if (successor.block == region.entry) dispatchTo(block, successor) else successor
            }
        }
    }

    private fun insertRegionBlocks(
        function: SSAFunction,
        region: SSARegion,
        dispatcher: SSABlock,
        invalidState: SSABlock
    ) {
        val entryIndex = function.blocks.indexOf(region.entry).takeIf { it >= 0 } ?: function.blocks.size
        function.blocks.add(entryIndex, dispatcher)
        function.blocks.add(entryIndex + 1, invalidState)
    }

    private fun insertEntryRegionBlocks(
        function: SSAFunction,
        region: SSARegion,
        newEntry: SSABlock,
        dispatcher: SSABlock,
        invalidState: SSABlock
    ) {
        val entryIndex = function.blocks.indexOf(region.entry).takeIf { it >= 0 } ?: 0
        function.blocks.add(entryIndex, newEntry)
        function.blocks.add(entryIndex + 1, dispatcher)
        function.blocks.add(entryIndex + 2, invalidState)
    }

    private fun collectLocalDefIds(block: SSABlock): Set<SSAValueId> {
        return buildSet {
            block.args.forEach { add(it.id) }
            block.instructions.forEach { instruction ->
                instruction.result?.let { add(it.id) }
            }
        }
    }

    private fun collectDefinitions(blocks: List<SSABlock>): Map<SSAValueId, SSAStructure> {
        val result = linkedMapOf<SSAValueId, SSAStructure>()
        for (block in blocks) {
            block.args.forEach { result.putIfAbsent(it.id, it) }
            block.instructions.forEach { instruction ->
                instruction.result?.let { result.putIfAbsent(it.id, it) }
            }
        }
        return result
    }

    private fun collectLiveIns(
        blocks: List<SSABlock>,
        parameterIds: Set<SSAValueId>,
        localDefs: Map<SSABlock, Set<SSAValueId>>,
        externalLiveIns: Map<SSABlock, List<SSAStructure>> = emptyMap()
    ): Map<SSABlock, List<SSAStructure>> {
        val blockSet = blocks.toSet()
        val directUses = blocks.associateWith { block ->
            val result = linkedMapOf<SSAValueId, SSAStructure>()
            fun add(value: SSAValue) {
                if (value !is SSAStructure) return
                if (value.id in parameterIds) return
                if (value.id in localDefs.getValue(block)) return
                result.putIfAbsent(value.id, value)
            }
            block.instructions.forEach { instruction -> instruction.operands.forEach(::add) }
            block.terminator.operands.forEach(::add)
            result
        }
        val liveIns = blocks.associateWith { linkedMapOf<SSAValueId, SSAStructure>() }

        var changed: Boolean
        do {
            changed = false
            for (block in blocks.asReversed()) {
                val next = linkedMapOf<SSAValueId, SSAStructure>()
                fun add(value: SSAStructure) {
                    if (value.id in parameterIds) return
                    if (value.id in localDefs.getValue(block)) return
                    next.putIfAbsent(value.id, value)
                }

                directUses.getValue(block).values.forEach(::add)
                for (successor in block.terminator.successors) {
                    if (successor.block in blockSet) {
                        liveIns.getValue(successor.block).values.forEach(::add)
                    } else {
                        externalLiveIns[successor.block].orEmpty().forEach(::add)
                    }
                }

                val current = liveIns.getValue(block)
                if (current.keys != next.keys) {
                    current.clear()
                    current.putAll(next)
                    changed = true
                }
            }
        } while (changed)

        return liveIns.mapValues { (_, values) -> values.values.toList() }
    }

    private fun rewriteTerminatorValuesAndSuccessors(
        terminator: SSATerminator,
        rewriteValue: (SSAValue) -> SSAValue,
        rewriteSuccessor: (SSASuccessor) -> SSASuccessor
    ): SSATerminator {
        fun next(successor: SSASuccessor): SSASuccessor {
            return rewriteSuccessor(successor.copy(args = successor.args.map(rewriteValue)))
        }

        return when (terminator) {
            is SSAJumpTerminator -> SSAJumpTerminator(next(terminator.target))
            is SSABranchTerminator -> SSABranchTerminator(
                rewriteValue(terminator.condition),
                next(terminator.trueTarget),
                next(terminator.falseTarget)
            )
            is SSASwitchTerminator -> SSASwitchTerminator(
                rewriteValue(terminator.value),
                terminator.cases.map { SSASwitchCase(it.key, next(it.target)) },
                next(terminator.defaultTarget)
            )
            is SSAReturnTerminator -> SSAReturnTerminator(terminator.value?.let(rewriteValue))
            is SSAThrowTerminator -> SSAThrowTerminator(rewriteValue(terminator.exception))
            SSAUnreachableTerminator -> SSAUnreachableTerminator
        }
    }

    private fun flattenTerminator(
        block: SSABlock,
        terminator: SSATerminator,
        regionBlocks: Set<SSABlock>,
        replacement: (SSABlock, SSAValue) -> SSAValue,
        rewriteExternal: (SSABlock, SSASuccessor) -> SSASuccessor,
        dispatchTo: (SSABlock, SSASuccessor) -> SSASuccessor
    ): SSATerminator {
        fun next(successor: SSASuccessor): SSASuccessor {
            return if (successor.block in regionBlocks) dispatchTo(block, successor) else rewriteExternal(block, successor)
        }

        return when (terminator) {
            is SSAJumpTerminator -> SSAJumpTerminator(next(terminator.target))
            is SSABranchTerminator -> SSABranchTerminator(
                replacement(block, terminator.condition),
                next(terminator.trueTarget),
                next(terminator.falseTarget)
            )
            is SSASwitchTerminator -> SSASwitchTerminator(
                replacement(block, terminator.value),
                terminator.cases.map { SSASwitchCase(it.key, next(it.target)) },
                next(terminator.defaultTarget)
            )
            is SSAReturnTerminator -> SSAReturnTerminator(terminator.value?.let { replacement(block, it) })
            is SSAThrowTerminator -> SSAThrowTerminator(replacement(block, terminator.exception))
            SSAUnreachableTerminator -> SSAUnreachableTerminator
        }
    }

    private fun rewriteTerminatorSuccessors(
        terminator: SSATerminator,
        rewrite: (SSASuccessor) -> SSASuccessor
    ): SSATerminator {
        return when (terminator) {
            is SSAJumpTerminator -> SSAJumpTerminator(rewrite(terminator.target))
            is SSABranchTerminator -> terminator.copy(
                trueTarget = rewrite(terminator.trueTarget),
                falseTarget = rewrite(terminator.falseTarget)
            )
            is SSASwitchTerminator -> terminator.copy(
                cases = terminator.cases.map { it.copy(target = rewrite(it.target)) },
                defaultTarget = rewrite(terminator.defaultTarget)
            )
            is SSAReturnTerminator,
            is SSAThrowTerminator,
            SSAUnreachableTerminator -> terminator
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
            SSAUnknownType -> SSAOpaqueLiteral("region.default", SSAUnknownType)
            is SSAOpaqueType -> SSAOpaqueLiteral("region.default", type)
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
