package net.spartanb312.grunt.ir.flow.jvm

import net.spartanb312.grunt.ir.flow.core.FlowBlock
import net.spartanb312.grunt.ir.flow.core.FlowBlockKind
import net.spartanb312.grunt.ir.flow.core.FlowBytecodeSlice
import net.spartanb312.grunt.ir.flow.core.FlowEdge
import net.spartanb312.grunt.ir.flow.core.FlowEdgeFlag
import net.spartanb312.grunt.ir.flow.core.FlowEdgeSemantics
import net.spartanb312.grunt.ir.flow.core.FlowExceptionRegion
import net.spartanb312.grunt.ir.flow.core.FlowFrame
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowGotoJump
import net.spartanb312.grunt.ir.flow.core.FlowGotoMode
import net.spartanb312.grunt.ir.flow.core.FlowIfJump
import net.spartanb312.grunt.ir.flow.core.FlowJump
import net.spartanb312.grunt.ir.flow.core.FlowJumpInput
import net.spartanb312.grunt.ir.flow.core.FlowLocalPool
import net.spartanb312.grunt.ir.flow.core.FlowMethod
import net.spartanb312.grunt.ir.flow.core.FlowPort
import net.spartanb312.grunt.ir.flow.core.FlowReturnJump
import net.spartanb312.grunt.ir.flow.core.FlowSwitchJump
import net.spartanb312.grunt.ir.flow.core.FlowThrowJump
import net.spartanb312.grunt.ir.flow.core.FlowUnreachableJump
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame

class JvmFlowImporter(
    private val context: JvmFlowImportContext = JvmFlowImportContext()
) {
    fun import(ownerInternalName: String, method: MethodNode): JvmFlowImportResult {
        context.metadata.capture(method)

        if (method.instructions == null || method.instructions.size() == 0) {
            val block = FlowBlock(context.ids.blockId(), jump = FlowUnreachableJump)
            val flow = FlowMethod(
                ownerInternalName = ownerInternalName,
                name = method.name,
                desc = method.desc,
                blocks = mutableListOf(block),
                locals = FlowLocalPool(method.maxLocals)
            )
            flow.entry = block
            return JvmFlowImportResult(flow, context.metadata)
        }

        val instructions = method.instructions.toArray().toList()
        val executableIndices = instructions.indices.filter { instructions[it].opcode >= 0 }
        if (executableIndices.isEmpty()) {
            val block = FlowBlock(context.ids.blockId(), jump = FlowUnreachableJump)
            val flow = FlowMethod(
                ownerInternalName = ownerInternalName,
                name = method.name,
                desc = method.desc,
                blocks = mutableListOf(block),
                locals = FlowLocalPool(method.maxLocals)
            )
            flow.entry = block
            return JvmFlowImportResult(flow, context.metadata)
        }

        val frames = analyze(ownerInternalName, method)
        val blockInfos = buildBlocks(method, instructions, executableIndices, frames)
        val blocks = blockInfos.mapTo(mutableListOf()) { it.block }
        val flow = FlowMethod(
            ownerInternalName = ownerInternalName,
            name = method.name,
            desc = method.desc,
            blocks = blocks,
            locals = FlowLocalPool(method.maxLocals)
        )
        flow.entry = blockInfos.first().block

        for (info in blockInfos) {
            importBlock(info, blockInfos, instructions, frames, flow)
            context.metadata.blockStarts[info.block] = info.start
            context.metadata.blockEnds[info.block] = info.end
            context.metadata.originalInstructions[info.block] = instructions.subList(info.start, info.end)
        }

        addExceptionRegions(flow, method, blockInfos, instructions)

        return JvmFlowImportResult(flow, context.metadata)
    }

    private fun analyze(ownerInternalName: String, method: MethodNode): Array<Frame<BasicValue>?> {
        return try {
            @Suppress("UNCHECKED_CAST")
            Analyzer(BasicInterpreter()).analyze(ownerInternalName, method) as Array<Frame<BasicValue>?>
        } catch (t: Throwable) {
            throw IllegalStateException("Failed to analyze ${ownerInternalName}.${method.name}${method.desc}", t)
        }
    }

    private fun buildBlocks(
        method: MethodNode,
        instructions: List<AbstractInsnNode>,
        executableIndices: List<Int>,
        frames: Array<Frame<BasicValue>?>
    ): List<BlockInfo> {
        val starts = sortedSetOf<Int>()
        starts += executableIndices.first()

        fun addLabel(label: LabelNode?) {
            if (label != null) nextExecutableIndex(instructions, instructions.indexOf(label))?.let { starts += it }
        }

        fun addNext(index: Int) {
            nextExecutableIndex(instructions, index + 1)?.let { starts += it }
        }

        for (index in executableIndices) {
            when (val insn = instructions[index]) {
                is JumpInsnNode -> {
                    addLabel(insn.label)
                    addNext(index)
                }
                is TableSwitchInsnNode -> {
                    addLabel(insn.dflt)
                    insn.labels.forEach(::addLabel)
                    addNext(index)
                }
                is LookupSwitchInsnNode -> {
                    addLabel(insn.dflt)
                    insn.labels.forEach(::addLabel)
                    addNext(index)
                }
                else -> if (isBlockTerminator(insn.opcode)) {
                    addNext(index)
                }
            }
        }

        method.tryCatchBlocks?.forEach { tryCatch ->
            addLabel(tryCatch.start)
            addLabel(tryCatch.end)
            addLabel(tryCatch.handler)
        }

        val orderedStarts = starts.toList()
        return orderedStarts.mapIndexed { index, start ->
            val end = orderedStarts.getOrNull(index + 1) ?: instructions.size
            val block = FlowBlock(
                id = context.ids.blockId(),
                kind = FlowBlockKind.Original,
                entryFrame = JvmFlowFrameMapper.frame(frames.getOrNull(start)),
                bodyExitFrame = JvmFlowFrameMapper.frame(frames.getOrNull(start))
            )
            BlockInfo(start, end, block)
        }
    }

    private fun importBlock(
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>,
        frames: Array<Frame<BasicValue>?>,
        flow: FlowMethod
    ) {
        val terminatorIndex = firstTerminatorIndex(info, instructions)
        val bodyEnd = terminatorIndex ?: info.end
        for (index in info.start until bodyEnd) {
            val insn = instructions[index]
            if (insn.opcode >= 0) {
                info.block.body.append(cloneInstruction(insn))
            }
        }

        if (terminatorIndex != null) {
            info.block.bodyExitFrame = JvmFlowFrameMapper.frame(frames.getOrNull(terminatorIndex))
            importTerminator(instructions[terminatorIndex], info, blockInfos, instructions, flow)
            return
        }

        val next = fallthroughBlock(info, blockInfos)
        if (next != null) {
            info.block.bodyExitFrame = next.block.entryFrame
            info.block.jump = FlowGotoJump(FlowGotoMode.Fallthrough)
            addEdge(flow, info.block, FlowPort.Next, next.block)
        } else {
            info.block.bodyExitFrame = frameBeforeLastExecutable(info, instructions, frames) ?: info.block.entryFrame
            info.block.jump = FlowUnreachableJump
        }
    }

    private fun importTerminator(
        insn: AbstractInsnNode,
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>,
        flow: FlowMethod
    ) {
        when (insn) {
            is JumpInsnNode -> importJumpInsn(insn, info, blockInfos, instructions, flow)
            is TableSwitchInsnNode -> importTableSwitchInsn(insn, info, blockInfos, instructions, flow)
            is LookupSwitchInsnNode -> importLookupSwitchInsn(insn, info, blockInfos, instructions, flow)
            is InsnNode -> importSimpleTerminator(insn, info)
            else -> error("Unsupported terminator ${insn.javaClass.simpleName} opcode ${insn.opcode}")
        }
    }

    private fun importJumpInsn(
        insn: JumpInsnNode,
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>,
        flow: FlowMethod
    ) {
        when (insn.opcode) {
            Opcodes.GOTO -> {
                info.block.jump = FlowGotoJump(FlowGotoMode.ExplicitGoto)
                addEdge(flow, info.block, FlowPort.Next, targetBlock(insn.label, blockInfos, instructions).block)
            }
            Opcodes.JSR -> unsupported(info, "JSR")
            else -> {
                info.block.jump = FlowIfJump(
                    opcode = insn.opcode,
                    input = FlowJumpInput.StackConsumed(jumpInputTypes(insn.opcode))
                )
                addEdge(flow, info.block, FlowPort.Branch, targetBlock(insn.label, blockInfos, instructions).block)
                fallthroughBlock(info, blockInfos)?.let {
                    addEdge(flow, info.block, FlowPort.Fallthrough, it.block)
                }
            }
        }
    }

    private fun importTableSwitchInsn(
        insn: TableSwitchInsnNode,
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>,
        flow: FlowMethod
    ) {
        val keys = (insn.min..insn.max).toList()
        info.block.jump = FlowSwitchJump(
            input = FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Int)),
            keys = keys
        )
        keys.zip(insn.labels).forEach { (key, label) ->
            addEdge(flow, info.block, FlowPort.Case(key), targetBlock(label, blockInfos, instructions).block)
        }
        addEdge(flow, info.block, FlowPort.Default, targetBlock(insn.dflt, blockInfos, instructions).block)
    }

    private fun importLookupSwitchInsn(
        insn: LookupSwitchInsnNode,
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>,
        flow: FlowMethod
    ) {
        val keys = insn.keys.map { it as Int }
        info.block.jump = FlowSwitchJump(
            input = FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Int)),
            keys = keys
        )
        keys.zip(insn.labels).forEach { (key, label) ->
            addEdge(flow, info.block, FlowPort.Case(key), targetBlock(label, blockInfos, instructions).block)
        }
        addEdge(flow, info.block, FlowPort.Default, targetBlock(insn.dflt, blockInfos, instructions).block)
    }

    private fun importSimpleTerminator(
        insn: InsnNode,
        info: BlockInfo
    ) {
        info.block.jump = when (insn.opcode) {
            Opcodes.RETURN -> FlowReturnJump()
            Opcodes.IRETURN -> FlowReturnJump(FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Int)))
            Opcodes.LRETURN -> FlowReturnJump(FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Long)))
            Opcodes.FRETURN -> FlowReturnJump(FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Float)))
            Opcodes.DRETURN -> FlowReturnJump(FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Double)))
            Opcodes.ARETURN -> FlowReturnJump(FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Unknown("ref"))))
            Opcodes.ATHROW -> FlowThrowJump(FlowJumpInput.StackConsumed(listOf(FlowFrameValue.Unknown("throwable"))))
            else -> error("Unsupported simple terminator opcode ${insn.opcode}")
        }
    }

    private fun addExceptionRegions(
        flow: FlowMethod,
        method: MethodNode,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>
    ) {
        method.tryCatchBlocks?.forEachIndexed { index, tryCatch ->
            addExceptionRegion(flow, tryCatch, index, blockInfos, instructions)
        }
    }

    private fun addExceptionRegion(
        flow: FlowMethod,
        tryCatch: TryCatchBlockNode,
        priority: Int,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>
    ) {
        val start = nextExecutableIndex(instructions, instructions.indexOf(tryCatch.start)) ?: return
        val end = nextExecutableIndex(instructions, instructions.indexOf(tryCatch.end)) ?: instructions.size
        val handlerStart = nextExecutableIndex(instructions, instructions.indexOf(tryCatch.handler)) ?: return
        val handler = blockInfos.firstOrNull { it.start == handlerStart }?.block ?: return
        val protectedBlocks = blockInfos
            .filter { it.start >= start && it.start < end }
            .mapTo(mutableSetOf()) { it.block }
        val catchType = tryCatch.type?.let { FlowFrameValue.Object(it) }
        flow.exceptionRegions += FlowExceptionRegion(protectedBlocks, handler, catchType, priority)
    }

    private fun addEdge(
        flow: FlowMethod,
        from: FlowBlock,
        port: FlowPort,
        to: FlowBlock,
        semantics: FlowEdgeSemantics = FlowEdgeSemantics.Real
    ) {
        flow.addEdge(
            FlowEdge(
                id = context.ids.edgeId(),
                from = from,
                port = port,
                to = to,
                semantics = semantics,
                flags = mutableSetOf(FlowEdgeFlag.Original)
            )
        )
    }

    private fun firstTerminatorIndex(info: BlockInfo, instructions: List<AbstractInsnNode>): Int? {
        for (index in info.start until info.end) {
            val insn = instructions[index]
            if (isFlowTerminator(insn)) return index
        }
        return null
    }

    private fun frameBeforeLastExecutable(
        info: BlockInfo,
        instructions: List<AbstractInsnNode>,
        frames: Array<Frame<BasicValue>?>
    ): FlowFrame? {
        for (index in (info.end - 1) downTo info.start) {
            if (instructions[index].opcode >= 0) {
                return JvmFlowFrameMapper.frame(frames.getOrNull(index))
            }
        }
        return null
    }

    private fun targetBlock(
        label: LabelNode,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>
    ): BlockInfo {
        val target = nextExecutableIndex(instructions, instructions.indexOf(label))
            ?: throw IllegalStateException("Label target has no executable instruction")
        return blockInfos.firstOrNull { it.start == target }
            ?: throw IllegalStateException("No Flow block starts at instruction $target")
    }

    private fun fallthroughBlock(info: BlockInfo, blockInfos: List<BlockInfo>): BlockInfo? {
        return blockInfos.firstOrNull { it.start >= info.end }
    }

    private fun jumpInputTypes(opcode: Int): List<FlowFrameValue> {
        return when (opcode) {
            in Opcodes.IFEQ..Opcodes.IFLE -> listOf(FlowFrameValue.Int)
            in Opcodes.IF_ICMPEQ..Opcodes.IF_ICMPLE -> listOf(FlowFrameValue.Int, FlowFrameValue.Int)
            Opcodes.IF_ACMPEQ,
            Opcodes.IF_ACMPNE -> listOf(FlowFrameValue.Unknown("ref"), FlowFrameValue.Unknown("ref"))
            Opcodes.IFNULL,
            Opcodes.IFNONNULL -> listOf(FlowFrameValue.Unknown("ref"))
            else -> error("Unsupported jump opcode $opcode")
        }
    }

    private fun isBlockTerminator(opcode: Int): Boolean {
        return opcode == Opcodes.GOTO ||
            opcode == Opcodes.JSR ||
            opcode == Opcodes.RET ||
            opcode == Opcodes.TABLESWITCH ||
            opcode == Opcodes.LOOKUPSWITCH ||
            opcode == Opcodes.IRETURN ||
            opcode == Opcodes.LRETURN ||
            opcode == Opcodes.FRETURN ||
            opcode == Opcodes.DRETURN ||
            opcode == Opcodes.ARETURN ||
            opcode == Opcodes.RETURN ||
            opcode == Opcodes.ATHROW
    }

    private fun isFlowTerminator(insn: AbstractInsnNode): Boolean {
        return insn is JumpInsnNode ||
            insn is TableSwitchInsnNode ||
            insn is LookupSwitchInsnNode ||
            isBlockTerminator(insn.opcode)
    }

    private fun nextExecutableIndex(instructions: List<AbstractInsnNode>, start: Int): Int? {
        if (start < 0) return null
        for (index in start until instructions.size) {
            if (instructions[index].opcode >= 0) return index
        }
        return null
    }

    private fun cloneInstruction(insn: AbstractInsnNode): AbstractInsnNode {
        return insn.clone(emptyMap<LabelNode, LabelNode>())
    }

    private fun unsupported(info: BlockInfo, name: String): Nothing {
        error("Unsupported JVM flow instruction $name at instruction ${info.start}")
    }

    private data class BlockInfo(
        val start: Int,
        val end: Int,
        val block: FlowBlock
    )
}
