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
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame

class JvmFlowImporter(
    private val context: JvmFlowImportContext = JvmFlowImportContext(),
    private val analyzerMode: JvmFlowAnalyzerMode = JvmFlowAnalyzerMode.Basic,
    private val typeHierarchy: JvmFlowTypeHierarchy = JvmFlowTypeHierarchy.Empty
) {
    fun import(ownerInternalName: String, method: MethodNode): JvmFlowImportResult {
        context.metadata.capture(method)
        val localSlots = analysisMaxLocals(method)
        context.metadata.maxLocals = maxOf(context.metadata.maxLocals, localSlots)
        context.metadata.maxStack = maxOf(context.metadata.maxStack, analysisMaxStack(method))

        if (method.instructions == null || method.instructions.size() == 0) {
            val block = FlowBlock(context.ids.blockId(), jump = FlowUnreachableJump)
            val flow = FlowMethod(
                ownerInternalName = ownerInternalName,
                name = method.name,
                desc = method.desc,
                blocks = mutableListOf(block),
                locals = FlowLocalPool(localSlots)
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
                locals = FlowLocalPool(localSlots)
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
            locals = FlowLocalPool(localSlots)
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
        val oldMaxLocals = method.maxLocals
        val oldMaxStack = method.maxStack
        return try {
            method.maxLocals = analysisMaxLocals(method)
            method.maxStack = analysisMaxStack(method)
            val interpreter = when (analyzerMode) {
                JvmFlowAnalyzerMode.Basic -> BasicFlowInterpreter()
                JvmFlowAnalyzerMode.Hierarchy -> HierarchyFlowInterpreter(typeHierarchy)
            }
            @Suppress("UNCHECKED_CAST")
            Analyzer<BasicValue>(interpreter).analyze(ownerInternalName, method) as Array<Frame<BasicValue>?>
        } catch (t: Throwable) {
            throw IllegalStateException("Failed to analyze ${ownerInternalName}.${method.name}${method.desc}", t)
        } finally {
            method.maxLocals = oldMaxLocals
            method.maxStack = oldMaxStack
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

    private fun analysisMaxLocals(method: MethodNode): Int {
        var max = argumentLocalSlots(method.access, method.desc)
        method.instructions?.toArray()?.forEach { insn ->
            when (insn) {
                is VarInsnNode -> max = maxOf(max, insn.`var` + localSizeForOpcode(insn.opcode))
                is IincInsnNode -> max = maxOf(max, insn.`var` + 1)
            }
        }
        return maxOf(method.maxLocals, max)
    }

    private fun analysisMaxStack(method: MethodNode): Int {
        val instructionCount = method.instructions?.size() ?: 0
        return maxOf(method.maxStack, instructionCount + 16, 64)
    }

    private fun argumentLocalSlots(access: Int, desc: String): Int {
        var slots = if (access and Opcodes.ACC_STATIC == 0) 1 else 0
        Type.getArgumentTypes(desc).forEach { slots += it.size }
        return slots
    }

    private fun localSizeForOpcode(opcode: Int): Int {
        return when (opcode) {
            Opcodes.LLOAD,
            Opcodes.DLOAD,
            Opcodes.LSTORE,
            Opcodes.DSTORE -> 2
            else -> 1
        }
    }

    private fun unsupported(info: BlockInfo, name: String): Nothing {
        error("Unsupported JVM flow instruction $name at instruction ${info.start}")
    }

    private data class BlockInfo(
        val start: Int,
        val end: Int,
        val block: FlowBlock
    )

    private open class BasicFlowInterpreter : BasicInterpreter(Opcodes.ASM9) {
        override fun newValue(type: Type?): BasicValue? {
            if (type == null) return BasicValue.UNINITIALIZED_VALUE
            return when (type.sort) {
                Type.VOID -> null
                Type.BOOLEAN,
                Type.CHAR,
                Type.BYTE,
                Type.SHORT,
                Type.INT -> BasicValue.INT_VALUE
                Type.FLOAT -> BasicValue.FLOAT_VALUE
                Type.LONG -> BasicValue.LONG_VALUE
                Type.DOUBLE -> BasicValue.DOUBLE_VALUE
                Type.ARRAY,
                Type.OBJECT -> BasicValue(type)
                else -> BasicValue(type)
            }
        }

        override fun binaryOperation(insn: AbstractInsnNode, value1: BasicValue, value2: BasicValue): BasicValue? {
            if (insn.opcode == Opcodes.AALOAD) {
                val arrayType = value1.type
                if (arrayType?.sort == Type.ARRAY) {
                    return newValue(Type.getType(arrayType.descriptor.substring(1)))
                }
            }
            return super.binaryOperation(insn, value1, value2)
        }

        override fun merge(value1: BasicValue, value2: BasicValue): BasicValue {
            if (value1 == value2) return value1
            if (value1 == BasicValue.UNINITIALIZED_VALUE || value2 == BasicValue.UNINITIALIZED_VALUE) {
                return BasicValue.UNINITIALIZED_VALUE
            }
            if (value1.isReference && value2.isReference) {
                return BasicValue.REFERENCE_VALUE
            }
            return BasicValue.UNINITIALIZED_VALUE
        }
    }

    private class HierarchyFlowInterpreter(
        private val hierarchy: JvmFlowTypeHierarchy
    ) : BasicFlowInterpreter() {
        override fun newOperation(insn: AbstractInsnNode): BasicValue? {
            return when (insn.opcode) {
                Opcodes.ACONST_NULL -> NullValue
                Opcodes.LDC -> ldcValue((insn as LdcInsnNode).cst)
                Opcodes.GETSTATIC -> newValue(Type.getType((insn as FieldInsnNode).desc))
                Opcodes.NEW -> newValue(Type.getObjectType((insn as TypeInsnNode).desc))
                else -> super.newOperation(insn)
            }
        }

        override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
            return when (insn.opcode) {
                Opcodes.CHECKCAST -> newValue(typeInsnType((insn as TypeInsnNode).desc))
                Opcodes.INSTANCEOF -> BasicValue.INT_VALUE
                Opcodes.GETFIELD -> newValue(Type.getType((insn as FieldInsnNode).desc))
                Opcodes.NEWARRAY -> newValue(newPrimitiveArrayType((insn as IntInsnNode).operand))
                Opcodes.ANEWARRAY -> newValue(Type.getType("[${typeInsnType((insn as TypeInsnNode).desc).descriptor}"))
                Opcodes.ARRAYLENGTH -> BasicValue.INT_VALUE
                else -> super.unaryOperation(insn, value)
            }
        }

        override fun binaryOperation(insn: AbstractInsnNode, value1: BasicValue, value2: BasicValue): BasicValue? {
            return when (insn.opcode) {
                Opcodes.IALOAD,
                Opcodes.BALOAD,
                Opcodes.CALOAD,
                Opcodes.SALOAD -> BasicValue.INT_VALUE
                Opcodes.LALOAD -> BasicValue.LONG_VALUE
                Opcodes.FALOAD -> BasicValue.FLOAT_VALUE
                Opcodes.DALOAD -> BasicValue.DOUBLE_VALUE
                Opcodes.AALOAD -> aaloadValue(value1)
                else -> super.binaryOperation(insn, value1, value2)
            }
        }

        override fun naryOperation(insn: AbstractInsnNode, values: MutableList<out BasicValue>): BasicValue? {
            return when (insn.opcode) {
                Opcodes.MULTIANEWARRAY -> newValue(Type.getType((insn as MultiANewArrayInsnNode).desc))
                Opcodes.INVOKEDYNAMIC -> newValue(Type.getReturnType((insn as InvokeDynamicInsnNode).desc))
                Opcodes.INVOKEVIRTUAL,
                Opcodes.INVOKESPECIAL,
                Opcodes.INVOKESTATIC,
                Opcodes.INVOKEINTERFACE -> newValue(Type.getReturnType((insn as MethodInsnNode).desc))
                else -> super.naryOperation(insn, values)
            }
        }

        override fun merge(value1: BasicValue, value2: BasicValue): BasicValue {
            if (value1 == value2) return value1
            if (value1 == BasicValue.UNINITIALIZED_VALUE || value2 == BasicValue.UNINITIALIZED_VALUE) {
                return BasicValue.UNINITIALIZED_VALUE
            }
            if (value1.isNullReference() && value2.isReferenceValue()) return value2
            if (value2.isNullReference() && value1.isReferenceValue()) return value1
            if (value1.isReferenceValue() && value2.isReferenceValue()) {
                val type1 = value1.type ?: return BasicValue.REFERENCE_VALUE
                val type2 = value2.type ?: return BasicValue.REFERENCE_VALUE
                return BasicValue(commonReferenceType(type1, type2))
            }
            return BasicValue.UNINITIALIZED_VALUE
        }

        private fun ldcValue(cst: Any?): BasicValue? {
            return when (cst) {
                is Int -> BasicValue.INT_VALUE
                is Float -> BasicValue.FLOAT_VALUE
                is Long -> BasicValue.LONG_VALUE
                is Double -> BasicValue.DOUBLE_VALUE
                is String -> newValue(Type.getObjectType("java/lang/String"))
                is Type -> when (cst.sort) {
                    Type.METHOD -> newValue(Type.getObjectType("java/lang/invoke/MethodType"))
                    else -> newValue(Type.getObjectType("java/lang/Class"))
                }
                is org.objectweb.asm.Handle -> newValue(Type.getObjectType("java/lang/invoke/MethodHandle"))
                is org.objectweb.asm.ConstantDynamic -> newValue(Type.getType(cst.descriptor))
                else -> newValue(Type.getObjectType(JavaObjectInternalName))
            }
        }

        private fun aaloadValue(arrayValue: BasicValue): BasicValue {
            val arrayType = arrayValue.type ?: return BasicValue.REFERENCE_VALUE
            if (arrayType.sort != Type.ARRAY) return BasicValue.REFERENCE_VALUE
            return newValue(Type.getType(arrayType.descriptor.substring(1))) ?: BasicValue.UNINITIALIZED_VALUE
        }

        private fun commonReferenceType(type1: Type, type2: Type): Type {
            if (type1 == type2) return type1
            return when {
                type1.sort == Type.ARRAY && type2.sort == Type.ARRAY -> commonArrayType(type1, type2)
                type1.sort == Type.ARRAY && type2.sort == Type.OBJECT -> commonArrayAndObject(type2)
                type1.sort == Type.OBJECT && type2.sort == Type.ARRAY -> commonArrayAndObject(type1)
                type1.sort == Type.OBJECT && type2.sort == Type.OBJECT -> {
                    Type.getObjectType(hierarchy.commonSuperClass(type1.internalName, type2.internalName))
                }
                else -> Type.getObjectType(JavaObjectInternalName)
            }
        }

        private fun commonArrayType(type1: Type, type2: Type): Type {
            val element1 = type1.oneDimensionElementType()
            val element2 = type2.oneDimensionElementType()
            if (!element1.isReferenceType() || !element2.isReferenceType()) {
                return if (type1 == type2) type1 else Type.getObjectType(JavaObjectInternalName)
            }

            val commonElement = commonReferenceType(element1, element2)
            return Type.getType("[${commonElement.descriptor}")
        }

        private fun commonArrayAndObject(objectType: Type): Type {
            return when (objectType.internalName) {
                JavaObjectInternalName,
                JavaCloneableInternalName,
                JavaSerializableInternalName -> objectType
                else -> Type.getObjectType(JavaObjectInternalName)
            }
        }

        private fun BasicValue.isNullReference(): Boolean {
            return type?.sort == Type.OBJECT && type?.internalName == JvmFlowNullInternalName
        }

        private fun BasicValue.isReferenceValue(): Boolean {
            return isNullReference() || isReference
        }

        private fun Type.isReferenceType(): Boolean {
            return sort == Type.OBJECT || sort == Type.ARRAY
        }

        private fun Type.oneDimensionElementType(): Type {
            return Type.getType(descriptor.substring(1))
        }

        companion object {
            private val NullValue = BasicValue(Type.getObjectType(JvmFlowNullInternalName))

            private fun typeInsnType(desc: String): Type {
                return if (desc.startsWith("[")) Type.getType(desc) else Type.getObjectType(desc)
            }

            private fun newPrimitiveArrayType(operand: Int): Type {
                val descriptor = when (operand) {
                    Opcodes.T_BOOLEAN -> "[Z"
                    Opcodes.T_CHAR -> "[C"
                    Opcodes.T_FLOAT -> "[F"
                    Opcodes.T_DOUBLE -> "[D"
                    Opcodes.T_BYTE -> "[B"
                    Opcodes.T_SHORT -> "[S"
                    Opcodes.T_INT -> "[I"
                    Opcodes.T_LONG -> "[J"
                    else -> "[Ljava/lang/Object;"
                }
                return Type.getType(descriptor)
            }
        }
    }
}
