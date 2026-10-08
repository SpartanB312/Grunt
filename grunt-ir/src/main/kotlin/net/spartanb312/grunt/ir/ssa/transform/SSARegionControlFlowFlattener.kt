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
        val analysis = SSARegionAnalysis(function)
        val insertions = mutableMapOf<SSABlock, List<SSABlock>>()
        var flattened = 0
        var skipped = 0
        for (region in regions) {
            val result = flattenRegion(function, region, ids, analysis, insertions)
            if (result) flattened++ else skipped++
        }

        if (flattened != 0) {
            // Commit layout once; repeated ArrayList insertions otherwise shift the whole suffix.
            val layout = buildList {
                for (block in function.blocks) {
                    addAll(insertions[block].orEmpty())
                    add(block)
                }
            }
            function.blocks.clear()
            function.blocks.addAll(layout)
        }
        return SSARegionControlFlowFlattenResult(
            changed = flattened != 0,
            flattenedRegions = flattened,
            skippedRegions = skipped,
            reason = if (flattened == 0) "All regions were skipped" else null
        )
    }

    private fun flattenRegion(
        function: SSAFunction,
        region: SSARegion,
        ids: FreshIds,
        analysis: SSARegionAnalysis,
        insertions: MutableMap<SSABlock, List<SSABlock>>
    ): Boolean {
        val regionBlocks = region.blocks
        val originalBlocks = regionBlocks.sortedBy(analysis::orderOf)
        val originalEntry = function.entry
        if (originalBlocks.size < options.minRegionBlocks) return false
        if (!canFlattenRegion(function, region, analysis)) return false
        if (originalEntry in regionBlocks && originalEntry.args.any { initialEntryArg(function, it) == null }) {
            return false
        }

        val parameterIds = analysis.parameterIds
        val originalArgCounts = originalBlocks.associateWith { it.args.size }
        // Live-ins can only increase the dispatcher width. Reject this lower bound before analysis.
        if (options.maxDispatcherArgs > 0 &&
            1L + originalBlocks.sumOf { it.args.size.toLong() } > options.maxDispatcherArgs
        ) return false
        val localDefs = originalBlocks.associateWith(analysis::localDefinitions)
        val regionDefinitions = collectDefinitions(originalBlocks)
        val liveOutValuesByBlock = analysis.liveOutValues(regionBlocks, regionDefinitions)

        if (function.entry in liveOutValuesByBlock.keys) {
            return false
        }
        if (liveOutValuesByBlock.keys.any { it in analysis.exceptionHandlers }) {
            return false
        }

        val liveIns = collectLiveIns(originalBlocks, parameterIds, localDefs, liveOutValuesByBlock)
        val projectedDispatcherArgs = 1 + originalBlocks.sumOf { block ->
            originalArgCounts.getValue(block) + liveIns.getValue(block).size
        }
        if (options.maxDispatcherArgs > 0 && projectedDispatcherArgs > options.maxDispatcherArgs) return false

        val changedBlocks = originalBlocks.toCollection(linkedSetOf())
        changedBlocks += liveOutValuesByBlock.keys
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

        val outsideUsers = linkedSetOf<SSABlock>()
        outsideUsers += liveOutArgsByBlock.keys
        for (block in liveOutArgsByBlock.keys) outsideUsers += analysis.predecessors(block)
        for (block in outsideUsers.sortedBy(analysis::orderOf)) {
            if (block in regionBlocks) continue
            changedBlocks += block
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

        redirectExternalEntryEdges(region, analysis, changedBlocks, ::dispatchTo)
        if (originalEntry in regionBlocks) {
            val newEntry = SSABlock(ids.blockId())
            newEntry.terminator = SSAJumpTerminator(
                SSASuccessor(
                    dispatcher,
                    listOf(SSAIntLiteral(caseIds.getValue(originalEntry), SSAI32Type)) + carriers.map(::initialCarrierValue)
                )
            )
            insertions[region.entry] = listOf(newEntry, dispatcher, invalidState)
            analysis.insertedBefore(region.entry, listOf(newEntry, dispatcher, invalidState))
            changedBlocks += newEntry
            function.entry = newEntry
        } else {
            insertions[region.entry] = listOf(dispatcher, invalidState)
            analysis.insertedBefore(region.entry, listOf(dispatcher, invalidState))
        }
        changedBlocks += dispatcher
        changedBlocks += invalidState
        changedBlocks.forEach(analysis::refresh)
        return true
    }

    private fun canFlattenRegion(
        function: SSAFunction,
        region: SSARegion,
        analysis: SSARegionAnalysis
    ): Boolean {
        if (region.entry !in region.blocks) return false
        if (function.entry in region.blocks && region.entry != function.entry) return false
        if (options.skipExceptionRegions && region.blocks.any { it in analysis.exceptionTouched }) return false
        if (region.blocks.any { it in analysis.exceptionHandlers }) return false
        return region.blocks.all { block ->
            block == region.entry || analysis.predecessors(block).all { it in region.blocks }
        }
    }

    private fun redirectExternalEntryEdges(
        region: SSARegion,
        analysis: SSARegionAnalysis,
        changedBlocks: MutableSet<SSABlock>,
        dispatchTo: (SSABlock, SSASuccessor) -> SSASuccessor
    ) {
        for (block in analysis.predecessors(region.entry).sortedBy(analysis::orderOf)) {
            if (block in region.blocks) continue
            changedBlocks += block
            block.terminator = rewriteTerminatorSuccessors(block.terminator) { successor ->
                if (successor.block == region.entry) dispatchTo(block, successor) else successor
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
