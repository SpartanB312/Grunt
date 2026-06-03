package net.spartanb312.grunt.ir.jvm

import net.spartanb312.grunt.ir.core.*
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame

class JvmIrImporter(
    private val context: JvmIrImportContext = JvmIrImportContext()
) {
    fun import(ownerInternalName: String, method: MethodNode): JvmIrImportResult {
        val ownerType = context.types.objectType(ownerInternalName, nullable = false)
        val isStatic = method.access and Opcodes.ACC_STATIC != 0
        val parameterTypes = context.types.methodParameterTypes(
            method.desc,
            includeReceiver = if (isStatic) null else ownerType
        )
        val returnType = context.types.methodReturnType(method.desc)
        val symbol = IrFunctionSymbol(
            context.ids.symbolId(),
            "$ownerInternalName.${method.name}${method.desc}",
            parameterTypes,
            returnType
        )
        val parameters = createParameters(parameterTypes)
        val syntheticEntry = IrBlock(context.ids.blockId())

        if (method.instructions == null || method.instructions.size() == 0) {
            syntheticEntry.terminator = IrUnreachableTerminator
            return JvmIrImportResult(
                IrFunction(symbol, parameters.toMutableList(), mutableListOf(syntheticEntry), syntheticEntry),
                context.metadata
            )
        }

        val instructions = method.instructions.toArray().toList()
        val executableIndices = instructions.indices.filter { instructions[it].opcode >= 0 }
        if (executableIndices.isEmpty()) {
            syntheticEntry.terminator = if (returnType == IrVoidType) IrReturnTerminator() else IrUnreachableTerminator
            return JvmIrImportResult(
                IrFunction(symbol, parameters.toMutableList(), mutableListOf(syntheticEntry), syntheticEntry),
                context.metadata
            )
        }

        val frames = analyze(ownerInternalName, method)
        val blockInfos = buildBlocks(method, instructions, executableIndices, frames)
        val firstBlockInfo = blockInfos.first()

        val irBlocks = mutableListOf(syntheticEntry)
        irBlocks += blockInfos.map { it.block }
        val function = IrFunction(symbol, parameters.toMutableList(), irBlocks, syntheticEntry)
        addExceptionRegions(function, method, blockInfos, instructions)

        val initialState = createInitialState(ownerInternalName, method, parameters)
        syntheticEntry.terminator = IrJumpTerminator(
            IrSuccessor(firstBlockInfo.block, successorArgs(firstBlockInfo, initialState))
        )

        for (info in blockInfos) {
            importBlock(info, blockInfos, instructions)
        }

        return JvmIrImportResult(function, context.metadata)
    }

    private fun createParameters(types: List<IrType>): List<IrParameter> {
        return types.mapIndexed { index, type ->
            IrParameter(context.ids.valueId(), index, type, "p$index")
        }
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

        method.tryCatchBlocks?.forEach {
            addLabel(it.start)
            addLabel(it.end)
            addLabel(it.handler)
        }

        val orderedStarts = starts.toList()
        return orderedStarts.mapIndexed { index, start ->
            val end = orderedStarts.getOrNull(index + 1) ?: instructions.size
            val block = IrBlock(context.ids.blockId())
            val shape = frameShape(frames.getOrNull(start), block)
            BlockInfo(start, end, block, shape)
        }
    }

    private fun frameShape(frame: Frame<BasicValue>?, block: IrBlock): BlockFrameShape {
        val locals = mutableListOf<FrameValueKey.Local>()
        val stack = mutableListOf<FrameValueKey.Stack>()

        if (frame != null) {
            for (index in 0 until frame.locals) {
                val type = context.types.frameType(frame.getLocal(index)) ?: continue
                locals += FrameValueKey.Local(index, type)
            }
            for (index in 0 until frame.stackSize) {
                val type = context.types.frameType(frame.getStack(index)) ?: IrUnknownType
                stack += FrameValueKey.Stack(index, type)
            }
        }

        val args = locals.mapIndexed { index, key ->
            IrBlockArg(
                context.ids.valueId(),
                index,
                key.type,
                IrBlockArgOrigin.FrontendState("local", key.index),
                "l${key.index}"
            )
        } + stack.mapIndexed { index, key ->
            IrBlockArg(
                context.ids.valueId(),
                locals.size + index,
                key.type,
                IrBlockArgOrigin.FrontendState("stack", key.index),
                "s${key.index}"
            )
        }

        block.args += args
        return BlockFrameShape(locals, stack)
    }

    private fun addExceptionRegions(
        function: IrFunction,
        method: MethodNode,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>
    ) {
        method.tryCatchBlocks?.forEach { tryCatch ->
            val start = nextExecutableIndex(instructions, instructions.indexOf(tryCatch.start)) ?: return@forEach
            val end = nextExecutableIndex(instructions, instructions.indexOf(tryCatch.end)) ?: instructions.size
            val handlerStart = nextExecutableIndex(instructions, instructions.indexOf(tryCatch.handler)) ?: return@forEach
            val handler = blockInfos.firstOrNull { it.start == handlerStart }?.block ?: return@forEach
            val protectedBlocks = blockInfos
                .filter { it.start >= start && it.start < end }
                .mapTo(mutableSetOf()) { it.block }
            val caughtType = tryCatch.type?.let { context.types.objectType(it) }
            function.exceptionRegions += IrExceptionRegion(protectedBlocks, handler, caughtType)
        }
    }

    private fun createInitialState(
        ownerInternalName: String,
        method: MethodNode,
        parameters: List<IrParameter>
    ): FrameState {
        val locals = MutableList(maxOf(method.maxLocals, parameters.size + 1)) { null as IrValue? }
        var parameterIndex = 0
        var slot = 0

        if (method.access and Opcodes.ACC_STATIC == 0) {
            locals[slot] = parameters[parameterIndex++]
            slot++
        }

        for (argType in Type.getArgumentTypes(method.desc)) {
            val parameter = parameters[parameterIndex++]
            ensureLocalSize(locals, slot)
            locals[slot] = parameter
            if (argType.size == 2) {
                ensureLocalSize(locals, slot + 1)
                locals[slot + 1] = null
            }
            slot += argType.size
        }

        return FrameState(locals, mutableListOf())
    }

    private fun importBlock(
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>
    ) {
        val state = frameState(info)
        var terminated = false

        for (index in info.start until info.end) {
            val insn = instructions[index]
            if (insn.opcode < 0) continue
            importInstruction(insn, info, blockInfos, instructions, state)
            if (info.block.terminator !is IrUnreachableTerminator) {
                terminated = true
                break
            }
        }

        if (!terminated) {
            val next = blockInfos.firstOrNull { it.start >= info.end }
            info.block.terminator = if (next != null) {
                IrJumpTerminator(IrSuccessor(next.block, successorArgs(next, state)))
            } else {
                IrUnreachableTerminator
            }
        }
    }

    private fun frameState(info: BlockInfo): FrameState {
        val localsSize = (info.shape.locals.maxOfOrNull { it.index } ?: -1) + 1
        val locals = MutableList(localsSize) { null as IrValue? }

        for ((argIndex, key) in info.shape.locals.withIndex()) {
            ensureLocalSize(locals, key.index)
            locals[key.index] = info.block.args[argIndex]
        }

        val stack = mutableListOf<IrValue>()
        for ((stackIndex, _) in info.shape.stack.withIndex()) {
            stack += info.block.args[info.shape.locals.size + stackIndex]
        }

        return FrameState(locals, stack)
    }

    private fun importInstruction(
        insn: AbstractInsnNode,
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>,
        state: FrameState
    ) {
        when (insn) {
            is InsnNode -> importSimpleInsn(insn, info, blockInfos, state)
            is IntInsnNode -> importIntInsn(insn, info, state)
            is VarInsnNode -> importVarInsn(insn, state)
            is TypeInsnNode -> importTypeInsn(insn, info, state)
            is FieldInsnNode -> importFieldInsn(insn, info, state)
            is MethodInsnNode -> importMethodInsn(insn, info, state)
            is InvokeDynamicInsnNode -> importInvokeDynamicInsn(insn, info, state)
            is JumpInsnNode -> importJumpInsn(insn, info, blockInfos, instructions, state)
            is LdcInsnNode -> importLdcInsn(insn, info, state)
            is IincInsnNode -> importIincInsn(insn, info, state)
            is TableSwitchInsnNode -> importTableSwitchInsn(insn, info, blockInfos, instructions, state)
            is LookupSwitchInsnNode -> importLookupSwitchInsn(insn, info, blockInfos, instructions, state)
            is MultiANewArrayInsnNode -> importMultiANewArrayInsn(insn, info, state)
        }
    }

    private fun importSimpleInsn(
        insn: InsnNode,
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        state: FrameState
    ) {
        val block = info.block
        when (insn.opcode) {
            Opcodes.NOP -> Unit
            Opcodes.ACONST_NULL -> state.push(IrNullLiteral)
            Opcodes.ICONST_M1 -> state.push(IrIntLiteral(-1))
            Opcodes.ICONST_0 -> state.push(IrIntLiteral(0))
            Opcodes.ICONST_1 -> state.push(IrIntLiteral(1))
            Opcodes.ICONST_2 -> state.push(IrIntLiteral(2))
            Opcodes.ICONST_3 -> state.push(IrIntLiteral(3))
            Opcodes.ICONST_4 -> state.push(IrIntLiteral(4))
            Opcodes.ICONST_5 -> state.push(IrIntLiteral(5))
            Opcodes.LCONST_0 -> state.push(IrIntLiteral(0, IrI64Type))
            Opcodes.LCONST_1 -> state.push(IrIntLiteral(1, IrI64Type))
            Opcodes.FCONST_0 -> state.push(IrFloatLiteral(0.0, IrF32Type))
            Opcodes.FCONST_1 -> state.push(IrFloatLiteral(1.0, IrF32Type))
            Opcodes.FCONST_2 -> state.push(IrFloatLiteral(2.0, IrF32Type))
            Opcodes.DCONST_0 -> state.push(IrFloatLiteral(0.0, IrF64Type))
            Opcodes.DCONST_1 -> state.push(IrFloatLiteral(1.0, IrF64Type))
            Opcodes.POP -> state.pop()
            Opcodes.POP2 -> state.popWords(2)
            Opcodes.DUP -> state.dup()
            Opcodes.DUP_X1 -> state.dupX1()
            Opcodes.DUP_X2 -> state.dupX2()
            Opcodes.DUP2 -> state.dup2()
            Opcodes.DUP2_X1 -> state.dup2X1()
            Opcodes.DUP2_X2 -> state.dup2X2()
            Opcodes.SWAP -> state.swap()
            Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD -> binary(block, state, IrBinaryOp.Add)
            Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB -> binary(block, state, IrBinaryOp.Sub)
            Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL -> binary(block, state, IrBinaryOp.Mul)
            Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV -> binary(block, state, IrBinaryOp.Div)
            Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM -> binary(block, state, IrBinaryOp.Rem)
            Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG -> unary(block, state, IrUnaryOp.Neg)
            Opcodes.ISHL, Opcodes.LSHL -> binary(block, state, IrBinaryOp.Shl, resultTypeFromLhs = true)
            Opcodes.ISHR, Opcodes.LSHR -> binary(block, state, IrBinaryOp.Shr, resultTypeFromLhs = true)
            Opcodes.IUSHR, Opcodes.LUSHR -> binary(block, state, IrBinaryOp.UShr, resultTypeFromLhs = true)
            Opcodes.IAND, Opcodes.LAND -> binary(block, state, IrBinaryOp.And)
            Opcodes.IOR, Opcodes.LOR -> binary(block, state, IrBinaryOp.Or)
            Opcodes.IXOR, Opcodes.LXOR -> binary(block, state, IrBinaryOp.Xor)
            Opcodes.I2L -> convert(block, state, IrI64Type)
            Opcodes.I2F -> convert(block, state, IrF32Type)
            Opcodes.I2D -> convert(block, state, IrF64Type)
            Opcodes.L2I -> convert(block, state, IrI32Type)
            Opcodes.L2F -> convert(block, state, IrF32Type)
            Opcodes.L2D -> convert(block, state, IrF64Type)
            Opcodes.F2I -> convert(block, state, IrI32Type)
            Opcodes.F2L -> convert(block, state, IrI64Type)
            Opcodes.F2D -> convert(block, state, IrF64Type)
            Opcodes.D2I -> convert(block, state, IrI32Type)
            Opcodes.D2L -> convert(block, state, IrI64Type)
            Opcodes.D2F -> convert(block, state, IrF32Type)
            Opcodes.I2B -> convert(block, state, IrI8Type)
            Opcodes.I2C -> convert(block, state, IrI16Type)
            Opcodes.I2S -> convert(block, state, IrI16Type)
            Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> compare3(block, state, insn.opcode)
            Opcodes.IALOAD -> arrayLoad(block, state, IrI32Type)
            Opcodes.LALOAD -> arrayLoad(block, state, IrI64Type)
            Opcodes.FALOAD -> arrayLoad(block, state, IrF32Type)
            Opcodes.DALOAD -> arrayLoad(block, state, IrF64Type)
            Opcodes.AALOAD -> arrayLoad(block, state, IrRefType())
            Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> arrayLoad(block, state, IrI32Type)
            Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE,
            Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> arrayStore(block, state)
            Opcodes.ARRAYLENGTH -> arrayLength(block, state)
            Opcodes.ATHROW -> block.terminator = IrThrowTerminator(state.pop())
            Opcodes.MONITORENTER -> monitor(block, state, "monitor.enter")
            Opcodes.MONITOREXIT -> monitor(block, state, "monitor.exit")
            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> {
                block.terminator = IrReturnTerminator(state.pop())
            }
            Opcodes.RETURN -> block.terminator = IrReturnTerminator()
            else -> unsupportedSimple(block, state, insn.opcode)
        }
    }

    private fun importIntInsn(insn: IntInsnNode, info: BlockInfo, state: FrameState) {
        when (insn.opcode) {
            Opcodes.BIPUSH, Opcodes.SIPUSH -> state.push(IrIntLiteral(insn.operand.toLong()))
            Opcodes.NEWARRAY -> {
                val count = state.pop()
                val arrayType = IrArrayType(newArrayElementType(insn.operand))
                val result = result(arrayType, "newarray")
                info.block.append(IrAllocateInstruction(result, IrAllocation.Array(arrayType), listOf(count)))
                state.push(result)
            }
        }
    }

    private fun importVarInsn(insn: VarInsnNode, state: FrameState) {
        when (insn.opcode) {
            Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> state.push(state.getLocal(insn.`var`))
            Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> state.setLocal(insn.`var`, state.pop())
            Opcodes.RET -> throw UnsupportedOperationException("RET/JSR bytecode is not supported by the SSA importer")
        }
    }

    private fun importTypeInsn(insn: TypeInsnNode, info: BlockInfo, state: FrameState) {
        when (insn.opcode) {
            Opcodes.NEW -> {
                val type = context.types.objectType(insn.desc, nullable = false)
                val result = result(type, "new")
                info.block.append(IrAllocateInstruction(result, IrAllocation.Object(type)))
                state.push(result)
            }
            Opcodes.ANEWARRAY -> {
                val count = state.pop()
                val arrayType = IrArrayType(arrayElementTypeFromAnewArray(insn.desc))
                val result = result(arrayType, "anewarray")
                info.block.append(IrAllocateInstruction(result, IrAllocation.Array(arrayType), listOf(count)))
                state.push(result)
            }
            Opcodes.CHECKCAST -> {
                val value = state.pop()
                val target = context.types.typeFromDescriptor(objectOrArrayDescriptor(insn.desc))
                val result = result(target, "checkcast")
                info.block.append(
                    IrConvertInstruction(
                        result,
                        IrConvertKind.ReferenceCast,
                        value,
                        target,
                        IrEffect.Pure.copy(mayThrow = true, canMove = false)
                    )
                )
                state.push(result)
            }
            Opcodes.INSTANCEOF -> {
                val value = state.pop()
                val intrinsic = IrIntrinsicRef(
                    "type.instanceof:${insn.desc}",
                    listOf(value.type),
                    IrI32Type,
                    IrEffect.Pure.copy(mayThrow = true, canMove = false)
                )
                val result = result(IrI32Type, "instanceof")
                info.block.append(IrIntrinsicInstruction(result, intrinsic, listOf(value)))
                state.push(result)
            }
        }
    }

    private fun importFieldInsn(insn: FieldInsnNode, info: BlockInfo, state: FrameState) {
        val isStatic = insn.opcode == Opcodes.GETSTATIC || insn.opcode == Opcodes.PUTSTATIC
        val fieldType = context.types.typeFromDescriptor(insn.desc)
        val ref = context.externalRef(
            IrExternalRefKind.Field,
            "${insn.owner}.${insn.name}:${insn.desc}"
        )
        context.metadata.fields[ref] = JvmFieldMetadata(insn.owner, insn.name, insn.desc, isStatic)
        val field = IrExternalFieldRef(ref, fieldType, isStatic)

        when (insn.opcode) {
            Opcodes.GETSTATIC -> {
                val result = result(fieldType, insn.name)
                info.block.append(IrLoadFieldInstruction(result, field, null))
                state.push(result)
            }
            Opcodes.PUTSTATIC -> {
                info.block.append(IrStoreFieldInstruction(field, null, state.pop()))
            }
            Opcodes.GETFIELD -> {
                val receiver = state.pop()
                val result = result(fieldType, insn.name)
                info.block.append(IrLoadFieldInstruction(result, field, receiver))
                state.push(result)
            }
            Opcodes.PUTFIELD -> {
                val value = state.pop()
                val receiver = state.pop()
                info.block.append(IrStoreFieldInstruction(field, receiver, value))
            }
        }
    }

    private fun importMethodInsn(insn: MethodInsnNode, info: BlockInfo, state: FrameState) {
        val argTypes = Type.getArgumentTypes(insn.desc).map { context.types.type(it) }
        val args = popArgs(state, argTypes.size)
        val receiver = if (insn.opcode == Opcodes.INVOKESTATIC) null else state.pop()
        val allArgs = listOfNotNull(receiver) + args
        val returnType = context.types.methodReturnType(insn.desc)
        val ref = context.externalRef(
            IrExternalRefKind.Function,
            "${insn.owner}.${insn.name}${insn.desc}"
        )
        context.metadata.methods[ref] = JvmMethodMetadata(
            insn.opcode,
            insn.owner,
            insn.name,
            insn.desc,
            insn.itf
        )
        val expectedArgs = if (receiver == null) {
            argTypes
        } else {
            listOf(context.types.objectType(insn.owner)) + argTypes
        }
        val target = IrExternalFunctionRef(ref, expectedArgs, returnType)
        val result = if (returnType == IrVoidType) null else result(returnType, insn.name)
        info.block.append(IrCallInstruction(result, target, allArgs, callDispatch(insn.opcode)))
        if (result != null) state.push(result)
    }

    private fun importInvokeDynamicInsn(insn: InvokeDynamicInsnNode, info: BlockInfo, state: FrameState) {
        val argTypes = Type.getArgumentTypes(insn.desc).map { context.types.type(it) }
        val args = popArgs(state, argTypes.size)
        val returnType = context.types.methodReturnType(insn.desc)
        val ref = context.externalRef(
            IrExternalRefKind.DynamicCallSite,
            "indy:${insn.name}${insn.desc}"
        )
        context.metadata.dynamicCalls[ref] = JvmDynamicCallMetadata(
            insn.name,
            insn.desc,
            bootstrap(insn.bsm, insn.bsmArgs.toList())
        )
        val site = IrDynamicCallSite(
            context.ids.dynamicSiteId(),
            argTypes,
            returnType,
            ref,
            insn.name
        )
        val result = if (returnType == IrVoidType) null else result(returnType, insn.name)
        info.block.append(IrDynamicCallInstruction(result, site, args))
        if (result != null) state.push(result)
    }

    private fun importJumpInsn(
        insn: JumpInsnNode,
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>,
        state: FrameState
    ) {
        val block = info.block
        if (insn.opcode == Opcodes.GOTO) {
            val target = targetBlock(insn.label, blockInfos, instructions)
            block.terminator = IrJumpTerminator(IrSuccessor(target.block, successorArgs(target, state)))
            return
        }
        if (insn.opcode == Opcodes.JSR) {
            throw UnsupportedOperationException("RET/JSR bytecode is not supported by the SSA importer")
        }

        val condition = when (insn.opcode) {
            Opcodes.IFEQ -> compareWithZero(block, state, IrComparePredicate.Eq)
            Opcodes.IFNE -> compareWithZero(block, state, IrComparePredicate.Ne)
            Opcodes.IFLT -> compareWithZero(block, state, IrComparePredicate.Lt)
            Opcodes.IFGE -> compareWithZero(block, state, IrComparePredicate.Ge)
            Opcodes.IFGT -> compareWithZero(block, state, IrComparePredicate.Gt)
            Opcodes.IFLE -> compareWithZero(block, state, IrComparePredicate.Le)
            Opcodes.IF_ICMPEQ -> compareStack(block, state, IrComparePredicate.Eq)
            Opcodes.IF_ICMPNE -> compareStack(block, state, IrComparePredicate.Ne)
            Opcodes.IF_ICMPLT -> compareStack(block, state, IrComparePredicate.Lt)
            Opcodes.IF_ICMPGE -> compareStack(block, state, IrComparePredicate.Ge)
            Opcodes.IF_ICMPGT -> compareStack(block, state, IrComparePredicate.Gt)
            Opcodes.IF_ICMPLE -> compareStack(block, state, IrComparePredicate.Le)
            Opcodes.IF_ACMPEQ -> compareStack(block, state, IrComparePredicate.RefEq)
            Opcodes.IF_ACMPNE -> compareStack(block, state, IrComparePredicate.RefNe)
            Opcodes.IFNULL -> compareRefNull(block, state, IrComparePredicate.RefEq)
            Opcodes.IFNONNULL -> compareRefNull(block, state, IrComparePredicate.RefNe)
            else -> throw UnsupportedOperationException("Unsupported jump opcode ${insn.opcode}")
        }

        val trueTarget = targetBlock(insn.label, blockInfos, instructions)
        val falseTarget = fallthroughBlock(info, blockInfos)
            ?: throw IllegalStateException("Conditional branch has no fallthrough block")
        block.terminator = IrBranchTerminator(
            condition,
            IrSuccessor(trueTarget.block, successorArgs(trueTarget, state)),
            IrSuccessor(falseTarget.block, successorArgs(falseTarget, state))
        )
    }

    private fun importLdcInsn(insn: LdcInsnNode, info: BlockInfo, state: FrameState) {
        when (val cst = insn.cst) {
            is Int -> state.push(IrIntLiteral(cst.toLong(), IrI32Type))
            is Long -> state.push(IrIntLiteral(cst, IrI64Type))
            is Float -> state.push(IrFloatLiteral(cst.toDouble(), IrF32Type))
            is Double -> state.push(IrFloatLiteral(cst, IrF64Type))
            is String -> state.push(IrOpaqueLiteral(cst.quote(), context.types.objectType("java/lang/String")))
            is Type -> state.push(IrOpaqueLiteral(cst.descriptor, context.types.objectType("java/lang/Class")))
            is Handle -> state.push(IrOpaqueLiteral(handle(cst).toString(), context.types.objectType("java/lang/invoke/MethodHandle")))
            is ConstantDynamic -> {
                val valueType = context.types.typeFromDescriptor(cst.descriptor)
                val ref = context.externalRef(
                    IrExternalRefKind.DynamicValueSite,
                    "condy:${cst.name}:${cst.descriptor}"
                )
                context.metadata.dynamicValues[ref] = dynamicValue(cst)
                val site = IrDynamicValueSite(
                    context.ids.dynamicSiteId(),
                    valueType,
                    ref,
                    cst.name
                )
                val result = result(valueType, cst.name)
                info.block.append(IrResolveDynamicValueInstruction(result, site))
                state.push(result)
            }
            else -> state.push(IrOpaqueLiteral(cst.toString(), IrUnknownType))
        }
    }

    private fun importIincInsn(insn: IincInsnNode, info: BlockInfo, state: FrameState) {
        val lhs = state.getLocal(insn.`var`)
        val rhs = IrIntLiteral(insn.incr.toLong(), IrI32Type)
        val result = result(IrI32Type, "iinc")
        info.block.append(IrBinaryInstruction(result, IrBinaryOp.Add, lhs, rhs))
        state.setLocal(insn.`var`, result)
    }

    private fun importTableSwitchInsn(
        insn: TableSwitchInsnNode,
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>,
        state: FrameState
    ) {
        val value = state.pop()
        val cases = insn.labels.mapIndexed { index, label ->
            val target = targetBlock(label, blockInfos, instructions)
            IrSwitchCase(insn.min.toLong() + index, IrSuccessor(target.block, successorArgs(target, state)))
        }
        val default = targetBlock(insn.dflt, blockInfos, instructions)
        info.block.terminator = IrSwitchTerminator(
            value,
            cases,
            IrSuccessor(default.block, successorArgs(default, state))
        )
    }

    private fun importLookupSwitchInsn(
        insn: LookupSwitchInsnNode,
        info: BlockInfo,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>,
        state: FrameState
    ) {
        val value = state.pop()
        val cases = insn.keys.zip(insn.labels).map { (key, label) ->
            val target = targetBlock(label, blockInfos, instructions)
            IrSwitchCase(key.toLong(), IrSuccessor(target.block, successorArgs(target, state)))
        }
        val default = targetBlock(insn.dflt, blockInfos, instructions)
        info.block.terminator = IrSwitchTerminator(
            value,
            cases,
            IrSuccessor(default.block, successorArgs(default, state))
        )
    }

    private fun importMultiANewArrayInsn(insn: MultiANewArrayInsnNode, info: BlockInfo, state: FrameState) {
        val dims = popArgs(state, insn.dims)
        val arrayType = context.types.typeFromDescriptor(insn.desc)
        val result = result(arrayType, "multianewarray")
        info.block.append(IrAllocateInstruction(result, IrAllocation.Opaque(arrayType, "multianewarray"), dims))
        state.push(result)
    }

    private fun unary(block: IrBlock, state: FrameState, op: IrUnaryOp) {
        val value = state.pop()
        val result = result(value.type, op.name.lowercase())
        block.append(IrUnaryInstruction(result, op, value))
        state.push(result)
    }

    private fun binary(
        block: IrBlock,
        state: FrameState,
        op: IrBinaryOp,
        resultTypeFromLhs: Boolean = false
    ) {
        val rhs = state.pop()
        val lhs = state.pop()
        val type = if (resultTypeFromLhs) lhs.type else commonType(lhs.type, rhs.type)
        val result = result(type, op.name.lowercase())
        block.append(IrBinaryInstruction(result, op, lhs, rhs))
        state.push(result)
    }

    private fun convert(block: IrBlock, state: FrameState, targetType: IrType) {
        val value = state.pop()
        val result = result(targetType, "convert")
        block.append(IrConvertInstruction(result, IrConvertKind.Numeric, value, targetType))
        state.push(result)
    }

    private fun compare3(block: IrBlock, state: FrameState, opcode: Int) {
        val rhs = state.pop()
        val lhs = state.pop()
        val intrinsic = IrIntrinsicRef(
            "cmp3.$opcode",
            listOf(lhs.type, rhs.type),
            IrI32Type,
            IrEffect.Pure
        )
        val result = result(IrI32Type, "cmp3")
        block.append(IrIntrinsicInstruction(result, intrinsic, listOf(lhs, rhs)))
        state.push(result)
    }

    private fun arrayLoad(block: IrBlock, state: FrameState, type: IrType) {
        val index = state.pop()
        val array = state.pop()
        val result = result(type, "aload")
        block.append(IrArrayLoadInstruction(result, array, index))
        state.push(result)
    }

    private fun arrayStore(block: IrBlock, state: FrameState) {
        val value = state.pop()
        val index = state.pop()
        val array = state.pop()
        block.append(IrArrayStoreInstruction(array, index, value))
    }

    private fun arrayLength(block: IrBlock, state: FrameState) {
        val array = state.pop()
        val intrinsic = IrIntrinsicRef(
            "array.length",
            listOf(array.type),
            IrI32Type,
            IrEffect.Pure.copy(mayThrow = true, canMove = false)
        )
        val result = result(IrI32Type, "length")
        block.append(IrIntrinsicInstruction(result, intrinsic, listOf(array)))
        state.push(result)
    }

    private fun monitor(block: IrBlock, state: FrameState, name: String) {
        val value = state.pop()
        val intrinsic = IrIntrinsicRef(name, listOf(value.type), IrVoidType, IrEffect.Barrier.copy(mayThrow = true))
        block.append(IrIntrinsicInstruction(null, intrinsic, listOf(value)))
    }

    private fun unsupportedSimple(block: IrBlock, state: FrameState, opcode: Int) {
        val intrinsic = IrIntrinsicRef("jvm.opcode.$opcode", emptyList(), IrVoidType, IrEffect.Barrier)
        block.append(IrIntrinsicInstruction(null, intrinsic, emptyList()))
    }

    private fun compareWithZero(block: IrBlock, state: FrameState, predicate: IrComparePredicate): IrValue {
        val value = state.pop()
        val result = result(IrBoolType, "if")
        block.append(IrCompareInstruction(result, predicate, value, IrIntLiteral(0, value.type as? IrIntegerType ?: IrI32Type)))
        return result
    }

    private fun compareStack(block: IrBlock, state: FrameState, predicate: IrComparePredicate): IrValue {
        val rhs = state.pop()
        val lhs = state.pop()
        val result = result(IrBoolType, "if")
        block.append(IrCompareInstruction(result, predicate, lhs, rhs))
        return result
    }

    private fun compareRefNull(block: IrBlock, state: FrameState, predicate: IrComparePredicate): IrValue {
        val value = state.pop()
        val result = result(IrBoolType, "if")
        block.append(IrCompareInstruction(result, predicate, value, IrNullLiteral))
        return result
    }

    private fun successorArgs(target: BlockInfo, state: FrameState): List<IrValue> {
        return target.shape.locals.map { state.getLocalOrUndef(it.index, it.type) } +
            target.shape.stack.map { state.getStackOrUndef(it.index, it.type) }
    }

    private fun targetBlock(
        label: LabelNode,
        blockInfos: List<BlockInfo>,
        instructions: List<AbstractInsnNode>
    ): BlockInfo {
        val target = nextExecutableIndex(instructions, instructions.indexOf(label))
            ?: throw IllegalStateException("Label target has no executable instruction")
        return blockInfos.firstOrNull { it.start == target }
            ?: throw IllegalStateException("No IR block starts at instruction $target")
    }

    private fun fallthroughBlock(info: BlockInfo, blockInfos: List<BlockInfo>): BlockInfo? {
        return blockInfos.firstOrNull { it.start >= info.end }
    }

    private fun result(type: IrType, debugName: String? = null): IrInstructionResult {
        return IrInstructionResult(context.ids.valueId(), type, debugName)
    }

    private fun commonType(lhs: IrType, rhs: IrType): IrType {
        return if (IrTypes.isAssignable(lhs, rhs)) rhs else lhs
    }

    private fun popArgs(state: FrameState, count: Int): List<IrValue> {
        return List(count) { state.pop() }.asReversed()
    }

    private fun callDispatch(opcode: Int): IrCallDispatch {
        return when (opcode) {
            Opcodes.INVOKESTATIC -> IrCallDispatch.Direct
            Opcodes.INVOKEVIRTUAL -> IrCallDispatch.Virtual
            Opcodes.INVOKEINTERFACE -> IrCallDispatch.Interface
            Opcodes.INVOKESPECIAL -> IrCallDispatch.Direct
            else -> IrCallDispatch.External
        }
    }

    private fun newArrayElementType(operand: Int): IrType {
        return when (operand) {
            Opcodes.T_BOOLEAN -> IrI32Type
            Opcodes.T_CHAR -> IrI32Type
            Opcodes.T_FLOAT -> IrF32Type
            Opcodes.T_DOUBLE -> IrF64Type
            Opcodes.T_BYTE -> IrI32Type
            Opcodes.T_SHORT -> IrI32Type
            Opcodes.T_INT -> IrI32Type
            Opcodes.T_LONG -> IrI64Type
            else -> IrUnknownType
        }
    }

    private fun arrayElementTypeFromAnewArray(desc: String): IrType {
        return if (desc.startsWith("[")) {
            context.types.typeFromDescriptor(desc)
        } else {
            context.types.objectType(desc)
        }
    }

    private fun objectOrArrayDescriptor(desc: String): String {
        return if (desc.startsWith("[")) desc else "L$desc;"
    }

    private fun bootstrap(handle: Handle, args: List<Any?>): JvmBootstrapMetadata {
        return JvmBootstrapMetadata(handle(handle), args.map { bootstrapArg(it) })
    }

    private fun handle(handle: Handle): JvmHandleMetadata {
        return JvmHandleMetadata(handle.tag, handle.owner, handle.name, handle.desc, handle.isInterface)
    }

    private fun dynamicValue(value: ConstantDynamic): JvmDynamicValueMetadata {
        val args = List(value.bootstrapMethodArgumentCount) {
            bootstrapArg(value.getBootstrapMethodArgument(it))
        }
        return JvmDynamicValueMetadata(
            value.name,
            value.descriptor,
            JvmBootstrapMetadata(handle(value.bootstrapMethod), args)
        )
    }

    private fun bootstrapArg(value: Any?): JvmBootstrapArgMetadata {
        return when (value) {
            is Int -> JvmBootstrapArgMetadata.IntArg(value)
            is Long -> JvmBootstrapArgMetadata.LongArg(value)
            is Float -> JvmBootstrapArgMetadata.FloatArg(value)
            is Double -> JvmBootstrapArgMetadata.DoubleArg(value)
            is String -> JvmBootstrapArgMetadata.StringArg(value)
            is Type -> JvmBootstrapArgMetadata.TypeArg(value.descriptor)
            is Handle -> JvmBootstrapArgMetadata.HandleArg(handle(value))
            is ConstantDynamic -> JvmBootstrapArgMetadata.DynamicValueArg(dynamicValue(value))
            null -> JvmBootstrapArgMetadata.OpaqueArg("null")
            else -> JvmBootstrapArgMetadata.OpaqueArg(value.toString())
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

    private fun nextExecutableIndex(instructions: List<AbstractInsnNode>, start: Int): Int? {
        if (start < 0) return null
        for (index in start until instructions.size) {
            if (instructions[index].opcode >= 0) return index
        }
        return null
    }

    private fun String.quote() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private data class BlockInfo(
        val start: Int,
        val end: Int,
        val block: IrBlock,
        val shape: BlockFrameShape
    )

    private data class BlockFrameShape(
        val locals: List<FrameValueKey.Local>,
        val stack: List<FrameValueKey.Stack>
    )

    private sealed interface FrameValueKey {
        val index: Int
        val type: IrType

        data class Local(override val index: Int, override val type: IrType) : FrameValueKey
        data class Stack(override val index: Int, override val type: IrType) : FrameValueKey
    }

    private data class FrameState(
        val locals: MutableList<IrValue?>,
        val stack: MutableList<IrValue>
    ) {
        fun push(value: IrValue) {
            stack += value
        }

        fun pop(): IrValue {
            return if (stack.isEmpty()) IrOpaqueLiteral("stack_underflow", IrUnknownType)
            else stack.removeAt(stack.lastIndex)
        }

        fun popWords(words: Int): List<IrValue> {
            val values = mutableListOf<IrValue>()
            var size = 0
            while (size < words) {
                val value = pop()
                values.add(0, value)
                size += stackSize(value.type)
                if (value is IrOpaqueLiteral && value.text == "stack_underflow") break
            }
            return values
        }

        fun dup() {
            val value = pop()
            push(value)
            push(value)
        }

        fun dupX1() {
            val value1 = pop()
            val value2 = pop()
            push(value1)
            push(value2)
            push(value1)
        }

        fun dupX2() {
            val value1 = pop()
            val below = popWords(2)
            push(value1)
            below.forEach(::push)
            push(value1)
        }

        fun dup2() {
            val values = popWords(2)
            values.forEach(::push)
            values.forEach(::push)
        }

        fun dup2X1() {
            val values = popWords(2)
            val below = pop()
            values.forEach(::push)
            push(below)
            values.forEach(::push)
        }

        fun dup2X2() {
            val values = popWords(2)
            val below = popWords(2)
            values.forEach(::push)
            below.forEach(::push)
            values.forEach(::push)
        }

        fun swap() {
            val value1 = pop()
            val value2 = pop()
            push(value1)
            push(value2)
        }

        fun getLocal(index: Int): IrValue {
            return locals.getOrNull(index) ?: IrOpaqueLiteral("local_$index", IrUnknownType)
        }

        fun getLocalOrUndef(index: Int, type: IrType): IrValue {
            return locals.getOrNull(index) ?: IrOpaqueLiteral("undef_local_$index", type)
        }

        fun getStackOrUndef(index: Int, type: IrType): IrValue {
            return stack.getOrNull(index) ?: IrOpaqueLiteral("undef_stack_$index", type)
        }

        fun setLocal(index: Int, value: IrValue) {
            ensureLocalSize(locals, index)
            locals[index] = value
            if (stackSize(value.type) == 2) {
                ensureLocalSize(locals, index + 1)
                locals[index + 1] = null
            }
        }
    }
}

private fun ensureLocalSize(locals: MutableList<IrValue?>, index: Int) {
    while (locals.size <= index) locals += null
}

private fun stackSize(type: IrType): Int {
    return when (type) {
        IrI64Type, IrF64Type -> 2
        IrVoidType -> 0
        else -> 1
    }
}
