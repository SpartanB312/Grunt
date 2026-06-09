package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.insn.ILOAD
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESTATIC
import net.spartanb312.genesis.kotlin.extensions.insn.ISTORE
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.ir.flow.core.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.JunkCallPool
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.JunkCodeGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.JunkCodeOptions
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

data class FlowControlFlowFlattenOptions(
    val includeMethodEntry: Boolean = true,
    val includeExceptionBlocks: Boolean = true,
    val includeUninitializedFrames: Boolean = false,
    val minFlattenedBlocks: Int = 2,
    val maxFlattenedBlocks: Int = 0,
    val maxDispatcherIslands: Int = 0,
    val fakeCasesPerDispatcher: Int = 1,
    val junkCases: Boolean = true,
    val junkCaseChance: Double = 0.35,
    val sharedFakeCaseTerminatorChance: Double = 0.65,
    val junkCodeOptions: JunkCodeOptions = JunkCodeOptions(),
    val minStateOpsPerCase: Int = 2,
    val maxStateOpsPerCase: Int = 5,
    val stateKeyMode: FlowStateKeyMode = FlowStateKeyMode.Mixed,
    val stateKeyProcessorChance: Double = 0.5,
    val shuffleRegionBlocks: Boolean = false,
    val dispatcherTrailingRealBlock: Boolean = false,
    val dispatcherTrailingRealBlockChance: Double = 1.0
)

data class FlowControlFlowFlattenResult(
    val changed: Boolean,
    val flattenedRegions: Int = 0,
    val flattenedBlocks: Int = 0,
    val dispatcherIslands: Int = 0,
    val stateBridges: Int = 0,
    val rewrittenEdges: Int = 0,
    val fakeCases: Int = 0,
    val dispatcherTrailingRealBlocks: Int = 0,
    val reason: String? = null
)

/**
 * The pass first builds the largest safe Flow region it can for the method,
 * then splits that maximal region into verifier-frame dispatcher islands.
 * This keeps coverage high without forcing incompatible stack/local frames
 * through one JVM switch entry frame.
 */
class FlowControlFlowFlattener(
    private val options: FlowControlFlowFlattenOptions = FlowControlFlowFlattenOptions(),
    private val random: UniformRandomProvider = Xoshiro256PPRandom(DefaultSeed),
    private val hierarchy: ClassHierarchy? = null,
    private val junkCallPool: JunkCallPool? = null,
    private val stateKeyProcessor: FlowStateKeyProcessor? = null,
    private val constructorInitOwners: Set<String> = emptySet()
) {
    private var nextStateProgramSite = 0

    fun flatten(method: FlowMethod): FlowControlFlowFlattenResult {
        val plan = plan(method) ?: return FlowControlFlowFlattenResult(
            changed = false,
            reason = skipReason(method)
        )
        val trailingRealBlocks = apply(method, plan)
        return FlowControlFlowFlattenResult(
            changed = true,
            flattenedRegions = 1,
            flattenedBlocks = plan.regionBlocks.size,
            dispatcherIslands = plan.dispatchers.size,
            stateBridges = plan.normalEdgeEntries.size + plan.handlerEntries.size + if (plan.methodEntry != null) 1 else 0,
            rewrittenEdges = plan.normalEdgeEntries.size,
            fakeCases = plan.dispatchers.sumOf { it.fakeCases.size },
            dispatcherTrailingRealBlocks = trailingRealBlocks
        )
    }

    private fun plan(method: FlowMethod): FlattenPlan? {
        val regionBlocks = selectMaximalRegion(method)
        if (regionBlocks.size < options.minFlattenedBlocks.coerceAtLeast(2)) return null

        val stateSlot = method.locals.nextSlot
        val regionSet = regionBlocks.toSet()
        val unsafeConstructorExits = constructorPreInitBlocks(method)
            .filterNot { it.initializesThis() }
            .toSet()
        val dispatchers = regionBlocks
            .groupBy { it.entryFrame }
            .map { (frame, blocks) ->
                val fakeCaseCount = options.fakeCasesPerDispatcher.coerceAtLeast(0)
                val keys = createCaseKeyPool(blocks.size + fakeCaseCount)
                val statePrograms = createStateTransformChain(keys.take(blocks.size), blocks.size)
                val dispatcherKeyByBlock = blocks
                    .zip(keys.take(blocks.size))
                    .associate { (block, key) -> block to key }
                DispatcherPlan(
                    frame = frame.withLocal(stateSlot, FlowFrameValue.Int),
                    blocks = blocks,
                    keyByBlock = dispatcherKeyByBlock,
                    stateProgramByBlock = blocks
                        .zip(statePrograms)
                        .associate { (block, program) -> block to program },
                    fakeCases = createFakeCasePlans(blocks, keys.drop(blocks.size), keys.toSet())
                )
            }
        val keyByBlock = dispatchers
            .flatMap { it.keyByBlock.entries }
            .associate { it.key to it.value }

        val normalEntries = mutableListOf<EdgeEntryPlan>()
        for (edge in method.edges) {
            val target = edge.to
            if (target !in regionSet) continue
            if (!options.includeUninitializedFrames && edge.from in unsafeConstructorExits) return null
            val sourceFrame = runCatching { FlowVerifier.frameAfterJump(edge.from) }.getOrNull() ?: return null
            normalEntries += EdgeEntryPlan(
                edge = edge,
                target = target,
                entryFrame = sourceFrame,
                dispatcher = dispatchers.first { target in it.blocks }
            )
        }

        val methodEntry = method.entry
            ?.takeIf { it in regionSet && options.includeMethodEntry }
            ?.let { entry ->
                DirectEntryPlan(
                    target = entry,
                    entryFrame = entry.entryFrame,
                    dispatcher = dispatchers.first { entry in it.blocks },
                    kind = FlowBlockKind.RegionEntry
                )
            }

        val handlerEntries = method.exceptionRegions
            .asSequence()
            .map { it.handler }
            .distinct()
            .filter { it in regionSet }
            .map { handler ->
                DirectEntryPlan(
                    target = handler,
                    entryFrame = handler.entryFrame,
                    dispatcher = dispatchers.first { handler in it.blocks },
                    kind = FlowBlockKind.RegionEntry
                )
            }
            .toList()

        return FlattenPlan(
            stateSlot = stateSlot,
            regionBlocks = regionBlocks,
            dispatchers = dispatchers,
            keyByBlock = keyByBlock,
            normalEdgeEntries = normalEntries,
            methodEntry = methodEntry,
            handlerEntries = handlerEntries
        )
    }

    private fun selectMaximalRegion(method: FlowMethod): List<FlowBlock> {
        val reachable = reachableBlocks(method)
        val exceptionBlocks = if (options.includeExceptionBlocks) {
            emptySet()
        } else {
            method.exceptionRegions.flatMapTo(mutableSetOf()) { it.protectedBlocks + it.handler }
        }
        val constructorPreInitBlocks = constructorPreInitBlocks(method)

        val eligible = method.layoutOrder()
            .filter { block ->
                block in reachable &&
                    block !in exceptionBlocks &&
                    block !in constructorPreInitBlocks &&
                    block.kind.isFlattenableOriginalKind() &&
                    (options.includeMethodEntry || block != method.entry) &&
                    (options.includeUninitializedFrames || !block.entryFrame.hasUninitialized())
            }

        if (eligible.isEmpty()) return emptyList()

        val limitedByDispatcher = if (options.maxDispatcherIslands > 0) {
            val allowedFrames = eligible
                .groupBy { it.entryFrame }
                .entries
                .sortedByDescending { it.value.size }
                .take(options.maxDispatcherIslands)
                .mapTo(mutableSetOf()) { it.key }
            eligible.filter { it.entryFrame in allowedFrames }
        } else {
            eligible
        }

        return if (options.maxFlattenedBlocks > 0) {
            limitedByDispatcher.take(options.maxFlattenedBlocks)
        } else {
            limitedByDispatcher
        }
    }

    private fun apply(method: FlowMethod, plan: FlattenPlan): Int {
        val ids = MutableFlowIds(method)
        val state = method.locals.allocate(FlowFrameValue.Int)
        require(state.index == plan.stateSlot) {
            "Unexpected Flow local allocation: planned ${plan.stateSlot}, got ${state.index}"
        }

        val dispatcherBlocks = plan.dispatchers.associateWith { dispatcher ->
            createDispatcher(dispatcher, plan.stateSlot, ids)
        }
        val bridgeBlocks = mutableListOf<FlowBlock>()
        val fakeBlocks = mutableListOf<FakeCaseBlock>()
        val fakeTerminators = FakeTerminatorPlanner(method, ids)

        for ((dispatcher, block) in dispatcherBlocks) {
            method.addBlock(block)
            for ((target, key) in dispatcher.keyByBlock) {
                method.addEdge(
                    FlowEdge(
                        id = ids.edge(),
                        from = block,
                        port = FlowPort.Case(key),
                        to = target,
                        semantics = FlowEdgeSemantics.Dispatcher,
                        flags = mutableSetOf(FlowEdgeFlag.Inserted)
                    )
                )
            }
            for (fakeCase in dispatcher.fakeCases) {
                val fakeBlock = createFakeCaseBlock(method, fakeCase, dispatcher, plan.stateSlot, ids, fakeTerminators)
                fakeBlocks += FakeCaseBlock(fakeBlock, fakeCase.fallthroughTarget)
                if (fakeBlock !in method.blocks) {
                    method.addBlock(fakeBlock)
                }
                method.addEdge(
                    FlowEdge(
                        id = ids.edge(),
                        from = block,
                        port = FlowPort.Case(fakeCase.key),
                        to = fakeBlock,
                        semantics = if (fakeBlock.kind == FlowBlockKind.Junk) {
                            FlowEdgeSemantics.Junk
                        } else {
                            FlowEdgeSemantics.Bogus
                        },
                        flags = mutableSetOf(FlowEdgeFlag.Inserted)
                    )
                )
                connectFakeCase(method, fakeBlock, fakeCase, dispatcherBlocks.getValue(dispatcher), ids)
            }
            method.addEdge(
                FlowEdge(
                    id = ids.edge(),
                    from = block,
                    port = FlowPort.Default,
                    to = dispatcher.blocks.first(),
                    semantics = FlowEdgeSemantics.Dispatcher,
                    flags = mutableSetOf(FlowEdgeFlag.Inserted)
                )
            )
        }

        for (entry in plan.normalEdgeEntries) {
            val bridge = createStateBridge(entry.target, entry.entryFrame, plan, entry.dispatcher, ids, FlowBlockKind.StateSet)
            bridgeBlocks += bridge
            method.addBlock(bridge)
            entry.edge.to = bridge
            entry.edge.flags += FlowEdgeFlag.Inserted
            connectBridge(method, bridge, dispatcherBlocks.getValue(entry.dispatcher), ids)
        }

        val handlerBridgeByTarget = mutableMapOf<FlowBlock, FlowBlock>()
        for (entry in plan.handlerEntries) {
            val bridge = createStateBridge(entry.target, entry.entryFrame, plan, entry.dispatcher, ids, entry.kind)
            bridgeBlocks += bridge
            method.addBlock(bridge)
            handlerBridgeByTarget[entry.target] = bridge
            connectBridge(method, bridge, dispatcherBlocks.getValue(entry.dispatcher), ids)
        }
        rewriteExceptionHandlers(method.exceptionRegions, handlerBridgeByTarget)

        plan.methodEntry?.let { entry ->
            val bridge = createStateBridge(entry.target, entry.entryFrame, plan, entry.dispatcher, ids, entry.kind)
            bridgeBlocks += bridge
            method.addBlock(bridge)
            method.entry = bridge
            connectBridge(method, bridge, dispatcherBlocks.getValue(entry.dispatcher), ids)
        }

        return rewriteLayout(
            method,
            plan.methodEntry?.target,
            plan.regionBlocks,
            dispatcherBlocks.entries.toList(),
            bridgeBlocks,
            fakeBlocks
        )
    }

    private fun createCaseKeyPool(size: Int): List<Int> {
        if (size <= 0) return emptyList()
        val start = nextCaseKeyWindowStart(size)
        val keys = MutableList(size) { offset -> start + offset }
        for (index in keys.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val value = keys[index]
            keys[index] = keys[swapIndex]
            keys[swapIndex] = value
        }
        return keys
    }

    private fun nextCaseKeyWindowStart(size: Int): Int {
        val offsetBound = Int.MAX_VALUE.toLong() - Int.MIN_VALUE.toLong() - size.toLong() + 1L
        var start = (Int.MIN_VALUE.toLong() + ((random.nextLong() ushr 1) % offsetBound)).toInt()
        while (start == 1) {
            start = (Int.MIN_VALUE.toLong() + ((random.nextLong() ushr 1) % offsetBound)).toInt()
        }
        return start
    }

    private fun createStateTransformChain(keys: List<Int>, realCaseCount: Int): List<StateProgram> {
        if (keys.isEmpty()) return emptyList()
        val budgets = createStateOperationBudgets(realCaseCount)
        return keys.mapIndexed { index, key ->
            createStateProgram(key, budgets.getOrElse(index) { normalizedMinStateOps() })
        }
    }

    private fun createStateOperationBudgets(realCaseCount: Int): List<Int> {
        if (realCaseCount <= 0) return emptyList()
        val minOps = normalizedMinStateOps()
        val maxOps = normalizedMaxStateOps(minOps)
        val budgets = MutableList(realCaseCount) {
            minOps + random.nextInt(maxOps - minOps + 1)
        }
        if (budgets.sum() <= realCaseCount) {
            budgets[random.nextInt(budgets.size)] += realCaseCount - budgets.sum() + 1
        }
        return budgets
    }

    private fun normalizedMinStateOps(): Int {
        return options.minStateOpsPerCase.coerceAtLeast(1)
    }

    private fun normalizedMaxStateOps(minOps: Int): Int {
        return options.maxStateOpsPerCase.coerceAtLeast(minOps)
    }

    private fun createFakeCasePlans(blocks: List<FlowBlock>, fakeKeys: List<Int>, usedKeys: Set<Int>): List<FakeCasePlan> {
        if (fakeKeys.isEmpty() || blocks.isEmpty()) return emptyList()

        return fakeKeys.map { key ->
            val exit = if (shouldCreateJunkCase()) FakeCaseExit.JunkTerminal
            else when (random.nextInt(3)) {
                0 -> FakeCaseExit.ScrambleState(createBogusStateProgram(usedKeys))
                1 -> FakeCaseExit.ThrowNull
                else -> FakeCaseExit.RealBranch(
                    target = blocks[random.nextInt(blocks.size)],
                    fallthrough = random.nextBoolean()
                )
            }
            FakeCasePlan(key, exit)
        }
    }

    private fun shouldCreateJunkCase(): Boolean {
        return options.junkCases &&
            hierarchy != null &&
            junkCallPool != null &&
            random.nextDouble() < options.junkCaseChance.coerceIn(0.0, 1.0)
    }

    private fun createBogusStateProgram(usedKeys: Set<Int>): StateProgram {
        val minOps = normalizedMinStateOps()
        val maxOps = normalizedMaxStateOps(minOps)
        repeat(32) {
            val target = nextNonZeroInt()
            if (target !in usedKeys) {
                return createStateProgram(target, minOps + random.nextInt(maxOps - minOps + 1))
            }
        }
        var fallbackTarget = usedKeys.fold(0x13579BDF) { acc, key -> acc xor key }.let { if (it == 0) 1 else it }
        while (fallbackTarget in usedKeys || fallbackTarget == 0) {
            fallbackTarget += 0x1F123BB5
        }
        return createStateProgram(fallbackTarget, minOps)
    }

    private fun createStateProgram(target: Int, operationCount: Int): StateProgram {
        val count = operationCount.coerceAtLeast(1)
        repeat(64) {
            val baseKey = nextNonZeroInt()
            if (baseKey == target) return@repeat
            val operations = mutableListOf<StateOp>()
            var value = baseKey
            repeat(count - 1) {
                val op = createRandomStateOp(value)
                operations += op
                value = op.apply(value)
            }
            if (value == target) return@repeat
            operations += createFinalStateOp(value, target)
            val output = operations.fold(baseKey) { current, op -> op.apply(current) }
            if (output == target) {
                return StateProgram(baseKey, operations, target)
            }
        }

        val baseKey = target xor nextNonZeroInt()
        return StateProgram(
            baseKey = baseKey,
            operations = listOf(StateOp.XorConst(baseKey xor target)),
            output = target
        )
    }

    private fun createRandomStateOp(value: Int): StateOp {
        return when (random.nextInt(6)) {
            0 -> StateOp.AddConst(nextNonZeroInt())
            1 -> StateOp.SubConst(nextNonZeroInt())
            2 -> StateOp.XorConst(nextNonZeroInt())
            3 -> StateOp.MulConst(nextOddInt())
            4 -> StateOp.DivConst(nextDivisor(value))
            else -> StateOp.MaxConst(nextNonZeroInt())
        }
    }

    private fun createFinalStateOp(value: Int, target: Int): StateOp {
        return when (random.nextInt(3)) {
            0 -> StateOp.AddConst(target - value)
            1 -> StateOp.SubConst(value - target)
            else -> StateOp.XorConst(value xor target)
        }
    }

    private fun createFakeCaseBlock(
        method: FlowMethod,
        fakeCase: FakeCasePlan,
        dispatcher: DispatcherPlan,
        stateSlot: Int,
        ids: MutableFlowIds,
        fakeTerminators: FakeTerminatorPlanner
    ): FlowBlock {
        if (fakeCase.exit == FakeCaseExit.JunkTerminal) {
            return fakeTerminators.junkTerminator(dispatcher.frame)
        }
        if (fakeCase.exit == FakeCaseExit.ThrowNull) {
            return fakeTerminators.throwNullTerminator(dispatcher.frame)
        }

        val body = FlowBytecodeSlice(mutableListOf(InsnNode(Opcodes.NOP)))
        val jump = when (val exit = fakeCase.exit) {
            is FakeCaseExit.ScrambleState -> {
                appendNonTerminalJunk(body)
                body.instructions += emitStateProgram(exit.program, stateSlot).instructions
                FlowGotoJump(FlowGotoMode.Synthetic)
            }
            is FakeCaseExit.RealBranch -> {
                appendNonTerminalJunk(body)
                FlowGotoJump(if (exit.fallthrough) FlowGotoMode.Fallthrough else FlowGotoMode.ExplicitGoto)
            }
            FakeCaseExit.ThrowNull -> error("Throw null terminal should be emitted as its own Flow block")
            FakeCaseExit.JunkTerminal -> error("Junk terminal should be emitted as its own Flow block")
        }
        return FlowBlock(
            id = ids.block(),
            kind = FlowBlockKind.Bogus,
            body = body,
            jump = jump,
            entryFrame = dispatcher.frame,
            bodyExitFrame = dispatcher.frame
        )
    }

    private inner class FakeTerminatorPlanner(
        private val method: FlowMethod,
        private val ids: MutableFlowIds
    ) {
        private val sharedJunkTerminals = mutableListOf<FlowBlock>()
        private val sharedThrowTerminals = mutableListOf<FlowBlock>()

        fun junkTerminator(frame: FlowFrame): FlowBlock {
            return terminalFor(frame, sharedJunkTerminals) {
                JunkCodeGenerator(
                    callPool = requireNotNull(junkCallPool),
                    hierarchy = requireNotNull(hierarchy),
                    options = options.junkCodeOptions,
                    random = random
                ).createTerminalBlock(ids.block(), method, frame)
            }
        }

        fun throwNullTerminator(frame: FlowFrame): FlowBlock {
            return terminalFor(frame, sharedThrowTerminals) {
                createThrowNullTerminatorBlock(ids.block(), frame)
            }
        }

        private fun terminalFor(
            frame: FlowFrame,
            shared: MutableList<FlowBlock>,
            create: () -> FlowBlock
        ): FlowBlock {
            if (random.nextDouble() < options.sharedFakeCaseTerminatorChance.coerceIn(0.0, 1.0)) {
                shared
                    .filter { FlowFrames.isCompatible(frame, it.entryFrame) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { return it[random.nextInt(it.size)] }
                return create().also {
                    method.addBlock(it)
                    shared += it
                }
            }
            return create().also { method.addBlock(it) }
        }
    }

    private fun createThrowNullTerminatorBlock(id: FlowBlockId, frame: FlowFrame): FlowBlock {
        val body = FlowBytecodeSlice(mutableListOf(InsnNode(Opcodes.NOP)))
        for (value in frame.stack.asReversed()) {
            body.instructions += InsnNode(if (value.categorySize == 2) Opcodes.POP2 else Opcodes.POP)
        }
        return FlowBlock(
            id = id,
            kind = FlowBlockKind.Bogus,
            body = body,
            jump = FlowThrowJump(
                FlowJumpInput.Generated(
                    code = FlowBytecodeSlice(mutableListOf(InsnNode(Opcodes.ACONST_NULL))),
                    produced = listOf(FlowFrameValue.Null)
                )
            ),
            entryFrame = frame,
            bodyExitFrame = frame.copy(stack = emptyList())
        )
    }

    private fun appendNonTerminalJunk(body: FlowBytecodeSlice) {
        if (!options.junkCases || hierarchy == null || junkCallPool == null) return
        JunkCodeGenerator(
            callPool = junkCallPool,
            hierarchy = hierarchy,
            options = options.junkCodeOptions,
            random = random
        ).appendStackNeutralJunk(body, minimumCalls = 1)
    }

    private fun connectFakeCase(
        method: FlowMethod,
        fakeBlock: FlowBlock,
        fakeCase: FakeCasePlan,
        dispatcherBlock: FlowBlock,
        ids: MutableFlowIds
    ) {
        val (target, semantics, flags) = when (val exit = fakeCase.exit) {
            is FakeCaseExit.ScrambleState -> Triple(
                dispatcherBlock,
                FlowEdgeSemantics.Dispatcher,
                mutableSetOf(FlowEdgeFlag.Inserted)
            )
            FakeCaseExit.ThrowNull -> return
            FakeCaseExit.JunkTerminal -> return
            is FakeCaseExit.RealBranch -> Triple(
                exit.target,
                FlowEdgeSemantics.Bogus,
                mutableSetOf(FlowEdgeFlag.Inserted).also {
                    if (exit.fallthrough) it += FlowEdgeFlag.LayoutSensitive
                }
            )
        }
        method.addEdge(
            FlowEdge(
                id = ids.edge(),
                from = fakeBlock,
                port = FlowPort.Next,
                to = target,
                semantics = semantics,
                flags = flags
            )
        )
    }

    private fun emitStateProgram(program: StateProgram, slot: Int): FlowBytecodeSlice {
        if (shouldUseStateProcessor()) {
            return emitProcessorStateProgram(program, slot)
        }
        return emitInlineStateProgram(program, slot)
    }

    private fun shouldUseStateProcessor(): Boolean {
        if (stateKeyProcessor == null) return false
        return when (options.stateKeyMode) {
            FlowStateKeyMode.Inline -> false
            FlowStateKeyMode.Processor -> true
            FlowStateKeyMode.Mixed -> random.nextDouble() < options.stateKeyProcessorChance.coerceIn(0.0, 1.0)
        }
    }

    private fun emitInlineStateProgram(program: StateProgram, slot: Int): FlowBytecodeSlice {
        val instructions = mutableListOf(pushInt(program.baseKey))
        for (op in program.operations) {
            op.emitTo(instructions)
        }
        instructions += VarInsnNode(Opcodes.ISTORE, slot)
        return FlowBytecodeSlice(instructions)
    }

    private fun emitProcessorStateProgram(program: StateProgram, slot: Int): FlowBytecodeSlice {
        val call = requireNotNull(stateKeyProcessor).reserve(
            siteId = nextStateProgramSite++,
            inputKey = program.baseKey,
            targetKey = program.output,
            random = random
        )
        return FlowBytecodeSlice(
            instructions {
                INT(call.inputKey)
                ISTORE(slot)
                ILOAD(slot)
                INT(call.salt)
                INVOKESTATIC(call.owner, call.name, call.desc)
                ISTORE(slot)
            }.toArray().toMutableList()
        )
    }

    private fun pushInt(value: Int) = when (value) {
        -1 -> InsnNode(Opcodes.ICONST_M1)
        0 -> InsnNode(Opcodes.ICONST_0)
        1 -> InsnNode(Opcodes.ICONST_1)
        2 -> InsnNode(Opcodes.ICONST_2)
        3 -> InsnNode(Opcodes.ICONST_3)
        4 -> InsnNode(Opcodes.ICONST_4)
        5 -> InsnNode(Opcodes.ICONST_5)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, value)
        in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, value)
        else -> LdcInsnNode(value)
    }

    private fun nextNonZeroInt(): Int {
        var value = random.nextInt()
        while (value == 0) value = random.nextInt()
        return value
    }

    private fun nextOddInt(): Int {
        return nextNonZeroInt() or 1
    }

    private fun nextDivisor(value: Int): Int {
        var divisor = 2 + random.nextInt(15)
        while (divisor == 0 || value == Int.MIN_VALUE && divisor == -1) {
            divisor = 2 + random.nextInt(15)
        }
        return divisor
    }

    private fun StateOp.emitTo(instructions: MutableList<org.objectweb.asm.tree.AbstractInsnNode>) {
        when (this) {
            is StateOp.AddConst -> {
                instructions += pushInt(value)
                instructions += InsnNode(Opcodes.IADD)
            }
            is StateOp.SubConst -> {
                instructions += pushInt(value)
                instructions += InsnNode(Opcodes.ISUB)
            }
            is StateOp.XorConst -> {
                instructions += pushInt(value)
                instructions += InsnNode(Opcodes.IXOR)
            }
            is StateOp.MulConst -> {
                instructions += pushInt(value)
                instructions += InsnNode(Opcodes.IMUL)
            }
            is StateOp.DivConst -> {
                instructions += pushInt(value)
                instructions += InsnNode(Opcodes.IDIV)
            }
            is StateOp.MaxConst -> {
                instructions += pushInt(value)
                instructions += MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false)
            }
        }
    }

    private fun FlowFrame.withLocal(index: Int, value: FlowFrameValue): FlowFrame {
        val locals = locals.toMutableList()
        while (locals.size <= index) locals += FlowFrameValue.Top
        locals[index] = value
        return copy(locals = locals)
    }

    private fun FlowFrame.withPushed(value: FlowFrameValue): FlowFrame {
        return copy(stack = stack + value)
    }

    private fun FlowFrame.hasUninitialized(): Boolean {
        return (locals.asSequence() + stack.asSequence()).any {
            it == FlowFrameValue.UninitializedThis || it is FlowFrameValue.UninitializedNew
        }
    }

    private fun constructorPreInitBlocks(method: FlowMethod): Set<FlowBlock> {
        if (options.includeUninitializedFrames) return emptySet()
        if (method.name != "<init>") return emptySet()

        val entry = method.entry ?: return emptySet()
        val visited = linkedSetOf<FlowBlock>()
        val queue = ArrayDeque<FlowBlock>()
        queue.addLast(entry)
        while (queue.isNotEmpty()) {
            val block = queue.removeFirst()
            if (!visited.add(block)) continue
            if (block.initializesThis()) continue
            for (successor in method.successors(block)) {
                if (successor !in visited) queue.addLast(successor)
            }
        }
        return visited
    }

    private fun FlowBlock.initializesThis(): Boolean {
        return body.instructions.any {
            it is MethodInsnNode &&
                it.opcode == Opcodes.INVOKESPECIAL &&
                it.name == "<init>" &&
                it.owner in constructorInitOwners
        }
    }

    private fun FlowBlockKind.isFlattenableOriginalKind(): Boolean {
        return this == FlowBlockKind.Original ||
            this == FlowBlockKind.Split ||
            this == FlowBlockKind.Bogus
    }

    private fun FlowBlockKind.isTrailingRealBlockKind(): Boolean {
        return this == FlowBlockKind.Original ||
            this == FlowBlockKind.Split
    }

    private fun FlowMethod.layoutOrder(): List<FlowBlock> {
        return (layout.order.ifEmpty { blocks }).filter { it in blocks }
    }

    private fun reachableBlocks(method: FlowMethod): Set<FlowBlock> {
        val entry = method.entry ?: return emptySet()
        val visited = linkedSetOf<FlowBlock>()
        val queue = ArrayDeque<FlowBlock>()
        queue.addLast(entry)
        while (queue.isNotEmpty()) {
            val block = queue.removeFirst()
            if (!visited.add(block)) continue
            for (successor in method.allSuccessors(block)) {
                if (successor !in visited) queue.addLast(successor)
            }
        }
        return visited
    }

    private fun createDispatcher(
        plan: DispatcherPlan,
        stateSlot: Int,
        ids: MutableFlowIds
    ): FlowBlock {
        val keyPorts = linkedMapOf<Int, FlowPort>()
        plan.keyByBlock.values.associateWithTo(keyPorts) { FlowPort.Case(it) }
        for (fakeCase in plan.fakeCases) {
            keyPorts[fakeCase.key] = FlowPort.Case(fakeCase.key)
        }
        return FlowBlock(
            id = ids.block(),
            kind = FlowBlockKind.Dispatcher,
            body = FlowBytecodeSlice(mutableListOf(VarInsnNode(Opcodes.ILOAD, stateSlot))),
            jump = FlowSwitchJump(
                input = FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Int)),
                keyPorts = keyPorts,
                defaultPort = FlowPort.Default
            ),
            entryFrame = plan.frame,
            bodyExitFrame = plan.frame.withPushed(FlowFrameValue.Int)
        )
    }

    private fun createStateBridge(
        target: FlowBlock,
        entryFrame: FlowFrame,
        plan: FlattenPlan,
        dispatcher: DispatcherPlan,
        ids: MutableFlowIds,
        kind: FlowBlockKind
    ): FlowBlock {
        return FlowBlock(
            id = ids.block(),
            kind = kind,
            body = emitStateProgram(dispatcher.stateProgramByBlock.getValue(target), plan.stateSlot),
            jump = FlowGotoJump(FlowGotoMode.Synthetic),
            entryFrame = entryFrame,
            bodyExitFrame = entryFrame.withLocal(plan.stateSlot, FlowFrameValue.Int)
        ).also {
            require(dispatcher.blocks.contains(target)) {
                "Target ${target.id} is not in dispatcher island"
            }
        }
    }

    private fun connectBridge(
        method: FlowMethod,
        bridge: FlowBlock,
        dispatcher: FlowBlock,
        ids: MutableFlowIds
    ) {
        method.addEdge(
            FlowEdge(
                id = ids.edge(),
                from = bridge,
                port = FlowPort.Next,
                to = dispatcher,
                semantics = FlowEdgeSemantics.Dispatcher,
                flags = mutableSetOf(FlowEdgeFlag.Inserted)
            )
        )
    }

    private fun rewriteExceptionHandlers(
        regions: List<FlowExceptionRegion>,
        bridgeByHandler: Map<FlowBlock, FlowBlock>
    ) {
        for (region in regions) {
            region.handler = bridgeByHandler[region.handler] ?: region.handler
        }
    }

    private fun rewriteLayout(
        method: FlowMethod,
        oldEntry: FlowBlock?,
        regionBlocks: List<FlowBlock>,
        dispatchers: List<Map.Entry<DispatcherPlan, FlowBlock>>,
        bridges: List<FlowBlock>,
        fakeBlocks: List<FakeCaseBlock>
    ): Int {
        val dispatcherBlocks = dispatchers.map { it.value }
        val insertedBlocks = (dispatcherBlocks + bridges + fakeBlocks.map { it.block }).toSet()
        val oldOrder = method.layoutOrder().filter { it !in insertedBlocks }
        val regionSet = regionBlocks.toSet()
        val reorderedRegionBlocks = if (options.shuffleRegionBlocks) {
            shuffleBlocks(oldOrder.filter { it in regionSet })
        } else {
            oldOrder.filter { it in regionSet }
        }
        val fallthroughFakes = fakeBlocks
            .filter { it.fallthroughTarget != null }
            .groupBy { it.fallthroughTarget }
        val placedFakes = mutableSetOf<FlowBlock>()
        val newOrder = mutableListOf<FlowBlock>()

        fun addWithFallthroughFakes(block: FlowBlock) {
            for (fakeBlock in fallthroughFakes[block].orEmpty()) {
                newOrder += fakeBlock.block
                placedFakes += fakeBlock.block
            }
            newOrder += block
        }

        if (oldEntry != null && method.entry != oldEntry) {
            method.entry?.let { newOrder += it }
        }
        if (options.shuffleRegionBlocks) {
            var insertedRegion = false
            for (block in oldOrder) {
                if (block in regionSet) {
                    if (!insertedRegion) {
                        insertedRegion = true
                        reorderedRegionBlocks.forEach(::addWithFallthroughFakes)
                    }
                } else {
                    addWithFallthroughFakes(block)
                }
            }
        } else {
            oldOrder.forEach(::addWithFallthroughFakes)
        }
        val trailingRealBlocks = selectTrailingRealBlocks(
            method = method,
            dispatchers = dispatchers,
            candidates = newOrder.distinct(),
            oldEntry = oldEntry,
            fallthroughTargets = fallthroughFakes.keys.filterNotNull().toSet()
        )
        newOrder.removeAll(trailingRealBlocks.values.toSet())
        for ((_, dispatcherBlock) in dispatchers) {
            newOrder += dispatcherBlock
            trailingRealBlocks[dispatcherBlock]?.let { newOrder += it }
        }
        newOrder += fakeBlocks.map { it.block }.filter { it !in placedFakes }
        newOrder += bridges.filter { it != method.entry }
        method.layout.order.clear()
        method.layout.order += newOrder.distinct()
        return trailingRealBlocks.size
    }

    private fun selectTrailingRealBlocks(
        method: FlowMethod,
        dispatchers: List<Map.Entry<DispatcherPlan, FlowBlock>>,
        candidates: List<FlowBlock>,
        oldEntry: FlowBlock?,
        fallthroughTargets: Set<FlowBlock>
    ): Map<FlowBlock, FlowBlock> {
        if (!options.dispatcherTrailingRealBlock) return emptyMap()

        val chance = options.dispatcherTrailingRealBlockChance.coerceIn(0.0, 1.0)
        if (chance <= 0.0) return emptyMap()

        val exceptionSensitiveBlocks = method.exceptionRegions
            .flatMapTo(mutableSetOf()) { it.protectedBlocks + it.handler }
        val used = mutableSetOf<FlowBlock>()
        val selected = linkedMapOf<FlowBlock, FlowBlock>()

        fun isSuitableCandidate(block: FlowBlock, dispatcher: DispatcherPlan): Boolean {
            return block !in used &&
                block != method.entry &&
                block != oldEntry &&
                block !in dispatcher.blocks &&
                block !in exceptionSensitiveBlocks &&
                block !in fallthroughTargets &&
                block.kind.isTrailingRealBlockKind()
        }

        for ((dispatcherPlan, dispatcherBlock) in dispatchers) {
            if (random.nextDouble() > chance) continue
            val available = candidates.filter { isSuitableCandidate(it, dispatcherPlan) }
            if (available.isEmpty()) continue
            val block = available[random.nextInt(available.size)]
            used += block
            selected[dispatcherBlock] = block
        }

        return selected
    }

    private fun shuffleBlocks(blocks: List<FlowBlock>): List<FlowBlock> {
        if (blocks.size < 2) return blocks
        val result = blocks.toMutableList()
        for (index in result.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val value = result[index]
            result[index] = result[swapIndex]
            result[swapIndex] = value
        }
        if (result == blocks) {
            result += result.removeAt(0)
        }
        return result
    }

    private fun skipReason(method: FlowMethod): String {
        val eligible = selectMaximalRegion(method).size
        return if (eligible == 0) {
            "no Flow blocks are eligible for flattening"
        } else {
            "eligible Flow blocks $eligible < ${options.minFlattenedBlocks.coerceAtLeast(2)}"
        }
    }

    private data class FlattenPlan(
        val stateSlot: Int,
        val regionBlocks: List<FlowBlock>,
        val dispatchers: List<DispatcherPlan>,
        val keyByBlock: Map<FlowBlock, Int>,
        val normalEdgeEntries: List<EdgeEntryPlan>,
        val methodEntry: DirectEntryPlan?,
        val handlerEntries: List<DirectEntryPlan>
    )

    private data class DispatcherPlan(
        val frame: FlowFrame,
        val blocks: List<FlowBlock>,
        val keyByBlock: Map<FlowBlock, Int>,
        val stateProgramByBlock: Map<FlowBlock, StateProgram>,
        val fakeCases: List<FakeCasePlan>
    )

    private data class EdgeEntryPlan(
        val edge: FlowEdge,
        val target: FlowBlock,
        val entryFrame: FlowFrame,
        val dispatcher: DispatcherPlan
    )

    private data class DirectEntryPlan(
        val target: FlowBlock,
        val entryFrame: FlowFrame,
        val dispatcher: DispatcherPlan,
        val kind: FlowBlockKind
    )

    private data class FakeCasePlan(
        val key: Int,
        val exit: FakeCaseExit
    ) {
        val fallthroughTarget: FlowBlock?
            get() = (exit as? FakeCaseExit.RealBranch)?.takeIf { it.fallthrough }?.target
    }

    private sealed interface FakeCaseExit {
        data class ScrambleState(val program: StateProgram) : FakeCaseExit
        data object ThrowNull : FakeCaseExit
        data object JunkTerminal : FakeCaseExit
        data class RealBranch(val target: FlowBlock, val fallthrough: Boolean) : FakeCaseExit
    }

    private data class StateProgram(
        val baseKey: Int,
        val operations: List<StateOp>,
        val output: Int
    )

    private sealed interface StateOp {
        fun apply(value: Int): Int

        data class AddConst(val value: Int) : StateOp {
            override fun apply(value: Int) = value + this.value
        }

        data class SubConst(val value: Int) : StateOp {
            override fun apply(value: Int) = value - this.value
        }

        data class XorConst(val value: Int) : StateOp {
            override fun apply(value: Int) = value xor this.value
        }

        data class MulConst(val value: Int) : StateOp {
            override fun apply(value: Int) = value * this.value
        }

        data class DivConst(val value: Int) : StateOp {
            override fun apply(value: Int) = value / this.value
        }

        data class MaxConst(val value: Int) : StateOp {
            override fun apply(value: Int) = maxOf(value, this.value)
        }
    }

    private data class FakeCaseBlock(
        val block: FlowBlock,
        val fallthroughTarget: FlowBlock?
    )

    private class MutableFlowIds(method: FlowMethod) {
        private var nextBlock = (method.blocks.maxOfOrNull { it.id.value } ?: -1) + 1
        private var nextEdge = (method.edges.maxOfOrNull { it.id.value } ?: -1) + 1

        fun block() = FlowBlockId(nextBlock++)

        fun edge() = FlowEdgeId(nextEdge++)
    }

    private companion object {
        val DefaultSeed = ByteArray(32) { index -> (index + 1).toByte() }
    }
}
