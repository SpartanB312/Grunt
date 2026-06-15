package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process

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
import net.spartanb312.grunt.ir.flow.core.FlowThrowJump
import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode

internal data class FlowExceptionBridgeOptions(
    val chance: Double = 0.0,
    val maxBridgesPerMethod: Int = 4
)

internal data class FlowExceptionBridgeResult(
    val changed: Boolean,
    val bridges: Int = 0
)

// Inspired by skidfuscator(MIT)
// https://github.com/skidfuscatordev/skidfuscator-java-obfuscator
internal class FlowExceptionBridgeInserter(
    private val options: FlowExceptionBridgeOptions = FlowExceptionBridgeOptions(),
    private val random: UniformRandomProvider
) {
    fun insert(method: FlowMethod): FlowExceptionBridgeResult {
        val maxBridges = options.maxBridgesPerMethod.coerceAtLeast(0)
        val chance = options.chance.coerceIn(0.0, 1.0)
        if (maxBridges <= 0 || chance <= 0.0) return FlowExceptionBridgeResult(changed = false)

        val ids = MutableFlowIds(method)
        val candidates = method.edges
            .filter { it.isEligibleExceptionBridgeEdge(method) }
            .toMutableList()
        shuffle(candidates)

        var inserted = 0
        for (edge in candidates) {
            if (inserted >= maxBridges) break
            if (random.nextDouble() >= chance) continue
            val sourceFrame = runCatching { FlowVerifier.frameAfterJump(edge.from) }.getOrNull() ?: continue
            if (sourceFrame.hasUninitialized() || !sourceFrame.hasEmptyStack || !edge.to.entryFrame.hasEmptyStack) continue

            val originalTarget = edge.to
            val originalExceptionSensitiveBlocks = method.exceptionSensitiveBlocks()
            val trap = createExceptionTrapBlock(ids.block(), sourceFrame)
            val handler = createExceptionHandlerBlock(ids.block(), sourceFrame)

            method.addBlock(trap)
            method.addBlock(handler)
            relocateHandler(method, trap, handler, originalExceptionSensitiveBlocks)

            edge.to = trap
            edge.flags += FlowEdgeFlag.Inserted
            method.addEdge(
                FlowEdge(
                    id = ids.edge(),
                    from = handler,
                    port = FlowPort.Next,
                    to = originalTarget,
                    semantics = FlowEdgeSemantics.Real,
                    flags = mutableSetOf(FlowEdgeFlag.Inserted)
                )
            )
            method.exceptionRegions += FlowExceptionRegion(
                protectedBlocks = mutableSetOf(trap),
                handler = handler,
                catchType = ExceptionBridgeFrameValue,
                priority = method.exceptionRegions.size
            )
            inserted++
        }

        return FlowExceptionBridgeResult(
            changed = inserted != 0,
            bridges = inserted
        )
    }

    private fun FlowEdge.isEligibleExceptionBridgeEdge(method: FlowMethod): Boolean {
        if (semantics != FlowEdgeSemantics.Real) return false
        if (FlowEdgeFlag.DoNotMutate in flags || FlowEdgeFlag.LayoutSensitive in flags) return false
        if (FlowEdgeFlag.Inserted in flags) return false
        if (port == FlowPort.Fallthrough) return false
        if (from == to) return false
        if (from.kind in ExceptionBridgeExcludedKinds || to.kind in ExceptionBridgeExcludedKinds) return false
        if (from !in method.blocks || to !in method.blocks) return false
        if (from in method.exceptionSensitiveBlocks() || to in method.exceptionSensitiveBlocks()) return false
        return runCatching {
            val sourceFrame = FlowVerifier.frameAfterJump(from)
            sourceFrame.hasEmptyStack &&
                to.entryFrame.hasEmptyStack &&
                !sourceFrame.hasUninitialized()
        }.getOrDefault(false)
    }

    private fun createExceptionTrapBlock(id: FlowBlockId, frame: FlowFrame): FlowBlock {
        return FlowBlock(
            id = id,
            kind = FlowBlockKind.Trap,
            body = FlowBytecodeSlice(),
            jump = FlowThrowJump(
                FlowJumpInput.Generated(
                    code = FlowBytecodeSlice(
                        mutableListOf(
                            TypeInsnNode(Opcodes.NEW, ExceptionBridgeInternalName),
                            InsnNode(Opcodes.DUP),
                            MethodInsnNode(
                                Opcodes.INVOKESPECIAL,
                                ExceptionBridgeInternalName,
                                "<init>",
                                "()V",
                                false
                            )
                        )
                    ),
                    produced = listOf(ExceptionBridgeFrameValue)
                )
            ),
            entryFrame = frame,
            bodyExitFrame = frame
        )
    }

    private fun relocateHandler(
        method: FlowMethod,
        trap: FlowBlock,
        handler: FlowBlock,
        originalExceptionSensitiveBlocks: Set<FlowBlock>
    ) {
        val order = method.layout.order
        if (order.size < 4) return
        order.remove(handler)

        val candidateIndices = (1..order.size).filter { index ->
            val previous = order.getOrNull(index - 1)
            val next = order.getOrNull(index)
            previous != trap &&
                next != trap &&
                previous !in originalExceptionSensitiveBlocks &&
                next !in originalExceptionSensitiveBlocks
        }
        if (candidateIndices.isEmpty()) {
            order += handler
            return
        }

        order.add(candidateIndices[random.nextInt(candidateIndices.size)], handler)
    }

    private fun createExceptionHandlerBlock(id: FlowBlockId, frame: FlowFrame): FlowBlock {
        return FlowBlock(
            id = id,
            kind = FlowBlockKind.Bridge,
            body = FlowBytecodeSlice(mutableListOf(InsnNode(Opcodes.POP))),
            jump = FlowGotoJump(FlowGotoMode.Synthetic),
            entryFrame = frame.copy(stack = listOf(ExceptionBridgeFrameValue)),
            bodyExitFrame = frame
        )
    }

    private fun FlowFrame.hasUninitialized(): Boolean {
        return (locals.asSequence() + stack.asSequence()).any {
            it == FlowFrameValue.UninitializedThis || it is FlowFrameValue.UninitializedNew
        }
    }

    private fun FlowMethod.exceptionSensitiveBlocks(): Set<FlowBlock> {
        return exceptionRegions.flatMapTo(mutableSetOf()) { it.protectedBlocks + it.handler }
    }

    private fun <T> shuffle(values: MutableList<T>) {
        for (index in values.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val value = values[index]
            values[index] = values[swapIndex]
            values[swapIndex] = value
        }
    }

    private class MutableFlowIds(method: FlowMethod) {
        private var nextBlock = (method.blocks.maxOfOrNull { it.id.value } ?: -1) + 1
        private var nextEdge = (method.edges.maxOfOrNull { it.id.value } ?: -1) + 1

        fun block() = FlowBlockId(nextBlock++)

        fun edge() = FlowEdgeId(nextEdge++)
    }

    private companion object {
        const val ExceptionBridgeInternalName = "java/lang/RuntimeException"
        val ExceptionBridgeFrameValue = FlowFrameValue.Object(ExceptionBridgeInternalName)
        val ExceptionBridgeExcludedKinds = setOf(
            FlowBlockKind.Junk,
            FlowBlockKind.Trap,
            FlowBlockKind.Bridge
        )
    }
}
