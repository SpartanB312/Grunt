package net.spartanb312.grunt.ir.flow.jvm

import net.spartanb312.grunt.ir.flow.core.FlowBlock
import net.spartanb312.grunt.ir.flow.core.FlowBytecodeSlice
import net.spartanb312.grunt.ir.flow.core.FlowExceptionRegion
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowGotoJump
import net.spartanb312.grunt.ir.flow.core.FlowGotoMode
import net.spartanb312.grunt.ir.flow.core.FlowIfJump
import net.spartanb312.grunt.ir.flow.core.FlowJumpInput
import net.spartanb312.grunt.ir.flow.core.FlowMethod
import net.spartanb312.grunt.ir.flow.core.FlowPort
import net.spartanb312.grunt.ir.flow.core.FlowReturnJump
import net.spartanb312.grunt.ir.flow.core.FlowSwitchJump
import net.spartanb312.grunt.ir.flow.core.FlowThrowJump
import net.spartanb312.grunt.ir.flow.core.FlowUnreachableJump
import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import net.spartanb312.grunt.ir.flow.core.categorySize
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.VarInsnNode

data class JvmFlowExportOptions(
    val access: Int? = null,
    val signature: String? = null,
    val exceptions: List<String>? = null,
    val verifyBeforeExport: Boolean = true,
    val minimumMaxStack: Int = 64,
    val maxStackPadding: Int = 16
)

class JvmFlowExporter(
    private val metadata: JvmFlowMetadata = JvmFlowMetadata(),
    private val options: JvmFlowExportOptions = JvmFlowExportOptions(),
    private val typeHierarchy: JvmFlowTypeHierarchy = JvmFlowTypeHierarchy.Empty
) {
    fun export(flow: FlowMethod): MethodNode {
        if (options.verifyBeforeExport) {
            FlowVerifier.verify(flow, JvmFlowFrameAssignability(typeHierarchy)::isAssignable).requireValid()
        }

        val access = options.access ?: metadata.access
        val method = MethodNode(
            access,
            flow.name,
            flow.desc,
            options.signature ?: metadata.signature,
            (options.exceptions ?: metadata.exceptions).toTypedArray()
        )
        val state = ExportState(flow)

        for (block in state.orderedBlocks) {
            method.instructions.add(state.label(block))
            cloneInto(method.instructions, block.body)
            emitJump(method.instructions, flow, block, state)
            method.instructions.add(state.endLabel(block))
        }

        emitExceptionRegions(method, flow, state)
        method.maxLocals = estimateMaxLocals(flow, access)
        method.maxStack = maxOf(options.minimumMaxStack, metadata.maxStack + options.maxStackPadding, estimateMaxStack(flow))
        return method
    }

    private fun emitJump(out: InsnList, flow: FlowMethod, block: FlowBlock, state: ExportState) {
        when (val jump = block.jump) {
            is FlowGotoJump -> emitGoto(out, flow, block, jump, state)
            is FlowIfJump -> emitIf(out, flow, block, jump, state)
            is FlowSwitchJump -> emitSwitch(out, flow, block, jump, state)
            is FlowReturnJump -> {
                emitInput(out, jump.input)
                out.add(InsnNode(returnOpcode(Type.getReturnType(flow.desc))))
            }
            is FlowThrowJump -> {
                emitInput(out, jump.input)
                out.add(InsnNode(Opcodes.ATHROW))
            }
            FlowUnreachableJump -> emitSyntheticUnreachableExit(out, flow.desc)
        }
    }

    private fun emitGoto(
        out: InsnList,
        flow: FlowMethod,
        block: FlowBlock,
        jump: FlowGotoJump,
        state: ExportState
    ) {
        val target = target(flow, block, FlowPort.Next)
        if (jump.mode == FlowGotoMode.Fallthrough && state.nextBlock(block) == target) return
        out.add(JumpInsnNode(Opcodes.GOTO, state.label(target)))
    }

    private fun emitIf(
        out: InsnList,
        flow: FlowMethod,
        block: FlowBlock,
        jump: FlowIfJump,
        state: ExportState
    ) {
        val branch = target(flow, block, jump.branchPort)
        val fallthrough = target(flow, block, jump.fallthroughPort)
        emitInput(out, jump.input)
        out.add(JumpInsnNode(jump.opcode, state.label(branch)))
        if (state.nextBlock(block) != fallthrough) {
            out.add(JumpInsnNode(Opcodes.GOTO, state.label(fallthrough)))
        }
    }

    private fun emitSwitch(
        out: InsnList,
        flow: FlowMethod,
        block: FlowBlock,
        jump: FlowSwitchJump,
        state: ExportState
    ) {
        emitInput(out, jump.input)

        val defaultTarget = target(flow, block, jump.defaultPort)
        val keys = jump.keyPorts.keys.sorted()
        val labels = keys.map { key ->
            state.label(target(flow, block, jump.keyPorts.getValue(key)))
        }

        if (keys.isNotEmpty() && keys.last() - keys.first() + 1 == keys.size) {
            out.add(TableSwitchInsnNode(keys.first(), keys.last(), state.label(defaultTarget), *labels.toTypedArray()))
        } else {
            out.add(LookupSwitchInsnNode(state.label(defaultTarget), keys.toIntArray(), labels.toTypedArray()))
        }
    }

    private fun emitInput(out: InsnList, input: FlowJumpInput) {
        when (input) {
            FlowJumpInput.None,
            is FlowJumpInput.StackConsumed -> Unit
            is FlowJumpInput.Generated -> cloneInto(out, input.code)
            is FlowJumpInput.CapturedLocal -> {
                for ((local, produced) in input.locals.zip(input.produced)) {
                    out.add(VarInsnNode(loadOpcode(produced), local.index))
                }
            }
        }
    }

    private fun emitSyntheticUnreachableExit(out: InsnList, desc: String) {
        when (Type.getReturnType(desc).sort) {
            Type.VOID -> out.add(InsnNode(Opcodes.RETURN))
            Type.LONG -> {
                out.add(InsnNode(Opcodes.LCONST_0))
                out.add(InsnNode(Opcodes.LRETURN))
            }
            Type.FLOAT -> {
                out.add(InsnNode(Opcodes.FCONST_0))
                out.add(InsnNode(Opcodes.FRETURN))
            }
            Type.DOUBLE -> {
                out.add(InsnNode(Opcodes.DCONST_0))
                out.add(InsnNode(Opcodes.DRETURN))
            }
            Type.ARRAY,
            Type.OBJECT -> {
                out.add(InsnNode(Opcodes.ACONST_NULL))
                out.add(InsnNode(Opcodes.ARETURN))
            }
            else -> {
                out.add(InsnNode(Opcodes.ICONST_0))
                out.add(InsnNode(Opcodes.IRETURN))
            }
        }
    }

    private fun emitExceptionRegions(method: MethodNode, flow: FlowMethod, state: ExportState) {
        val orderedRegions = flow.exceptionRegions
            .withIndex()
            .sortedWith(compareBy<IndexedValue<FlowExceptionRegion>> { it.value.priority }.thenBy { it.index })
        for (indexedRegion in orderedRegions) {
            val region = indexedRegion.value
            val protected = region.protectedBlocks
                .filter { state.contains(it) }
                .sortedBy { state.indexOf(it) }
            for (run in contiguousRuns(protected, state)) {
                val first = run.firstOrNull() ?: continue
                val last = run.last()
                method.tryCatchBlocks.add(
                    TryCatchBlockNode(
                        state.label(first),
                        state.endLabel(last),
                        state.label(region.handler),
                        region.catchType?.internalName
                    )
                )
            }
        }
    }

    private fun contiguousRuns(blocks: List<FlowBlock>, state: ExportState): List<List<FlowBlock>> {
        if (blocks.isEmpty()) return emptyList()

        val runs = mutableListOf<MutableList<FlowBlock>>()
        var current = mutableListOf(blocks.first())
        var previousIndex = state.indexOf(blocks.first())
        for (block in blocks.drop(1)) {
            val index = state.indexOf(block)
            if (index == previousIndex + 1) {
                current += block
            } else {
                runs += current
                current = mutableListOf(block)
            }
            previousIndex = index
        }
        runs += current
        return runs
    }

    private fun target(flow: FlowMethod, block: FlowBlock, port: FlowPort): FlowBlock {
        return flow.edgeFrom(block, port)?.to
            ?: error("Block ${block.id} has no edge for port ${port.displayName}")
    }

    private fun estimateMaxLocals(flow: FlowMethod, access: Int): Int {
        var max = maxOf(metadata.maxLocals, flow.locals.nextSlot, argumentLocalSlots(flow.desc, access))

        fun reserve(index: Int, size: Int) {
            max = maxOf(max, index + size)
        }

        fun scan(slice: FlowBytecodeSlice) {
            for (insn in slice.instructions) {
                when (insn) {
                    is VarInsnNode -> reserve(insn.`var`, varInsnSize(insn.opcode))
                    is IincInsnNode -> reserve(insn.`var`, 1)
                }
            }
        }

        for (block in flow.blocks) {
            scan(block.body)
            when (val input = block.jump.input) {
                is FlowJumpInput.Generated -> scan(input.code)
                is FlowJumpInput.CapturedLocal -> input.locals.forEach { reserve(it.index, it.value.categorySize) }
                FlowJumpInput.None,
                is FlowJumpInput.StackConsumed -> Unit
            }
        }

        return max
    }

    private fun estimateMaxStack(flow: FlowMethod): Int {
        var max = if (flow.exceptionRegions.isEmpty()) 0 else 1
        for (block in flow.blocks) {
            max = maxOf(max, block.jump.input.produced.sumOf { it.categorySize })
            if (block.jump === FlowUnreachableJump) {
                max = maxOf(max, Type.getReturnType(flow.desc).size.coerceAtLeast(1))
            }
        }
        return max
    }

    private fun cloneInto(out: InsnList, slice: FlowBytecodeSlice) {
        val labels = collectLabels(slice.instructions)
        for (insn in slice.instructions) {
            out.add(insn.clone(labels))
        }
    }

    private fun collectLabels(instructions: List<AbstractInsnNode>): MutableMap<LabelNode, LabelNode> {
        val labels = linkedMapOf<LabelNode, LabelNode>()

        fun remember(label: LabelNode) {
            labels.getOrPut(label) { LabelNode() }
        }

        fun rememberFrameEntries(entries: List<Any>?) {
            entries?.forEach { entry ->
                if (entry is LabelNode) remember(entry)
            }
        }

        for (insn in instructions) {
            when (insn) {
                is LabelNode -> remember(insn)
                is JumpInsnNode -> remember(insn.label)
                is TableSwitchInsnNode -> {
                    remember(insn.dflt)
                    insn.labels.forEach(::remember)
                }
                is LookupSwitchInsnNode -> {
                    remember(insn.dflt)
                    insn.labels.forEach(::remember)
                }
                is LineNumberNode -> remember(insn.start)
                is FrameNode -> {
                    rememberFrameEntries(insn.local)
                    rememberFrameEntries(insn.stack)
                }
            }
        }

        return labels
    }

    private fun argumentLocalSlots(desc: String, access: Int): Int {
        var slots = if (access and Opcodes.ACC_STATIC == 0) 1 else 0
        Type.getArgumentTypes(desc).forEach { slots += it.size }
        return slots
    }

    private fun varInsnSize(opcode: Int): Int {
        return when (opcode) {
            Opcodes.LLOAD,
            Opcodes.DLOAD,
            Opcodes.LSTORE,
            Opcodes.DSTORE -> 2
            else -> 1
        }
    }

    private fun loadOpcode(value: FlowFrameValue): Int {
        return when (value) {
            FlowFrameValue.Int -> Opcodes.ILOAD
            FlowFrameValue.Float -> Opcodes.FLOAD
            FlowFrameValue.Long -> Opcodes.LLOAD
            FlowFrameValue.Double -> Opcodes.DLOAD
            FlowFrameValue.Null,
            is FlowFrameValue.Object,
            FlowFrameValue.UninitializedThis,
            is FlowFrameValue.UninitializedNew,
            is FlowFrameValue.Unknown -> Opcodes.ALOAD
            FlowFrameValue.Top -> error("Cannot load top from a captured local")
        }
    }

    private fun returnOpcode(type: Type): Int {
        return when (type.sort) {
            Type.VOID -> Opcodes.RETURN
            Type.LONG -> Opcodes.LRETURN
            Type.FLOAT -> Opcodes.FRETURN
            Type.DOUBLE -> Opcodes.DRETURN
            Type.ARRAY,
            Type.OBJECT -> Opcodes.ARETURN
            else -> Opcodes.IRETURN
        }
    }

    private inner class ExportState(flow: FlowMethod) {
        val orderedBlocks = (if (flow.layout.order.isEmpty()) flow.blocks else flow.layout.order).toList()
        private val allBlocks = (orderedBlocks + flow.blocks).distinct()
        private val labels = allBlocks.associateWith { LabelNode() }
        private val endLabels = allBlocks.associateWith { LabelNode() }
        private val orderIndex = orderedBlocks.withIndex().associate { it.value to it.index }

        fun contains(block: FlowBlock): Boolean = block in orderIndex

        fun indexOf(block: FlowBlock): Int = orderIndex[block] ?: error("Block ${block.id} is not in layout")

        fun label(block: FlowBlock): LabelNode = labels[block] ?: error("No label for block ${block.id}")

        fun endLabel(block: FlowBlock): LabelNode = endLabels[block] ?: error("No end label for block ${block.id}")

        fun nextBlock(block: FlowBlock): FlowBlock? {
            val index = orderIndex[block] ?: return null
            return orderedBlocks.getOrNull(index + 1)
        }
    }
}
