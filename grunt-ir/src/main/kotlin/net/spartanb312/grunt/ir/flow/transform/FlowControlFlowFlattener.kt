package net.spartanb312.grunt.ir.flow.transform

import net.spartanb312.grunt.ir.flow.core.FlowBlock
import net.spartanb312.grunt.ir.flow.core.FlowBlockId
import net.spartanb312.grunt.ir.flow.core.FlowBlockKind
import net.spartanb312.grunt.ir.flow.core.FlowBytecodeSlice
import net.spartanb312.grunt.ir.flow.core.FlowEdge
import net.spartanb312.grunt.ir.flow.core.FlowEdgeFlag
import net.spartanb312.grunt.ir.flow.core.FlowEdgeId
import net.spartanb312.grunt.ir.flow.core.FlowEdgeSemantics
import net.spartanb312.grunt.ir.flow.core.FlowExceptionRegion
import net.spartanb312.grunt.ir.flow.core.FlowFrame
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowGotoJump
import net.spartanb312.grunt.ir.flow.core.FlowGotoMode
import net.spartanb312.grunt.ir.flow.core.FlowJumpInput
import net.spartanb312.grunt.ir.flow.core.FlowMethod
import net.spartanb312.grunt.ir.flow.core.FlowPort
import net.spartanb312.grunt.ir.flow.core.FlowSwitchJump
import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.VarInsnNode

data class FlowControlFlowFlattenOptions(
    val includeMethodEntry: Boolean = true,
    val includeExceptionBlocks: Boolean = true,
    val includeUninitializedFrames: Boolean = false,
    val minFlattenedBlocks: Int = 2,
    val maxFlattenedBlocks: Int = 0,
    val maxDispatcherIslands: Int = 0
)

data class FlowControlFlowFlattenResult(
    val changed: Boolean,
    val flattenedRegions: Int = 0,
    val flattenedBlocks: Int = 0,
    val dispatcherIslands: Int = 0,
    val stateBridges: Int = 0,
    val rewrittenEdges: Int = 0,
    val reason: String? = null
)

/**
 * FlowIR-native control-flow flattening.
 *
 * The pass first builds the largest safe Flow region it can for the method,
 * then splits that maximal region into verifier-frame dispatcher islands.
 * This keeps coverage high without forcing incompatible stack/local frames
 * through one JVM switch entry frame.
 */
class FlowControlFlowFlattener(
    private val options: FlowControlFlowFlattenOptions = FlowControlFlowFlattenOptions()
) {
    fun flatten(method: FlowMethod): FlowControlFlowFlattenResult {
        val plan = plan(method) ?: return FlowControlFlowFlattenResult(
            changed = false,
            reason = skipReason(method)
        )
        apply(method, plan)
        return FlowControlFlowFlattenResult(
            changed = true,
            flattenedRegions = 1,
            flattenedBlocks = plan.regionBlocks.size,
            dispatcherIslands = plan.dispatchers.size,
            stateBridges = plan.normalEdgeEntries.size + plan.handlerEntries.size + if (plan.methodEntry != null) 1 else 0,
            rewrittenEdges = plan.normalEdgeEntries.size
        )
    }

    private fun plan(method: FlowMethod): FlattenPlan? {
        val regionBlocks = selectMaximalRegion(method)
        if (regionBlocks.size < options.minFlattenedBlocks.coerceAtLeast(2)) return null

        val stateSlot = method.locals.nextSlot
        val keyByBlock = regionBlocks
            .mapIndexed { index, block -> block to (index + 1) }
            .toMap()
        val regionSet = regionBlocks.toSet()
        val dispatchers = regionBlocks
            .groupBy { it.entryFrame }
            .map { (frame, blocks) ->
                DispatcherPlan(
                    frame = frame.withLocal(stateSlot, FlowFrameValue.Int),
                    blocks = blocks,
                    keyByBlock = blocks.associateWith { keyByBlock.getValue(it) }
                )
            }

        val normalEntries = mutableListOf<EdgeEntryPlan>()
        for (edge in method.edges) {
            val target = edge.to
            if (target !in regionSet) continue
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

        val eligible = method.layoutOrder()
            .filter { block ->
                block in reachable &&
                    block !in exceptionBlocks &&
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

    private fun apply(method: FlowMethod, plan: FlattenPlan) {
        val ids = MutableFlowIds(method)
        val state = method.locals.allocate(FlowFrameValue.Int)
        require(state.index == plan.stateSlot) {
            "Unexpected Flow local allocation: planned ${plan.stateSlot}, got ${state.index}"
        }

        val dispatcherBlocks = plan.dispatchers.associateWith { dispatcher ->
            createDispatcher(method, dispatcher, plan.stateSlot, ids)
        }
        val bridgeBlocks = mutableListOf<FlowBlock>()

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

        rewriteLayout(method, plan.methodEntry?.target, dispatcherBlocks.values.toList(), bridgeBlocks)
    }

    private fun setState(key: Int, slot: Int): FlowBytecodeSlice {
        return FlowBytecodeSlice(
            mutableListOf(pushInt(key), VarInsnNode(Opcodes.ISTORE, slot))
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

    private fun FlowBlockKind.isFlattenableOriginalKind(): Boolean {
        return this == FlowBlockKind.Original ||
            this == FlowBlockKind.Split ||
            this == FlowBlockKind.Bogus
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
        method: FlowMethod,
        plan: DispatcherPlan,
        stateSlot: Int,
        ids: MutableFlowIds
    ): FlowBlock {
        return FlowBlock(
            id = ids.block(),
            kind = FlowBlockKind.Dispatcher,
            body = FlowBytecodeSlice(mutableListOf(VarInsnNode(Opcodes.ILOAD, stateSlot))),
            jump = FlowSwitchJump(
                input = FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Int)),
                keyPorts = plan.keyByBlock.values.associateWithTo(linkedMapOf()) { FlowPort.Case(it) },
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
            body = setState(plan.keyByBlock.getValue(target), plan.stateSlot),
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
        dispatchers: List<FlowBlock>,
        bridges: List<FlowBlock>
    ) {
        val oldOrder = method.layoutOrder()
        val newOrder = mutableListOf<FlowBlock>()
        if (oldEntry != null && method.entry != oldEntry) {
            method.entry?.let { newOrder += it }
        }
        newOrder += oldOrder
        newOrder += dispatchers
        newOrder += bridges.filter { it != method.entry }
        method.layout.order.clear()
        method.layout.order += newOrder.distinct()
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
        val keyByBlock: Map<FlowBlock, Int>
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

    private class MutableFlowIds(method: FlowMethod) {
        private var nextBlock = (method.blocks.maxOfOrNull { it.id.value } ?: -1) + 1
        private var nextEdge = (method.edges.maxOfOrNull { it.id.value } ?: -1) + 1

        fun block() = FlowBlockId(nextBlock++)

        fun edge() = FlowEdgeId(nextEdge++)
    }
}
