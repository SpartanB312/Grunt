package net.spartanb312.grunt.ir.ssa.jvm

import net.spartanb312.grunt.ir.ssa.core.*
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame

class JvmSSAImporter(
    private val context: JvmSSAImportContext = JvmSSAImportContext()
) {
    fun import(ownerInternalName: String, method: MethodNode): JvmSSAImportResult {
        val ownerType = context.types.objectType(ownerInternalName, nullable = false)
        val isStatic = method.access and Opcodes.ACC_STATIC != 0
        val parameterTypes = context.types.methodParameterTypes(
            method.desc,
            includeReceiver = if (isStatic) null else ownerType
        )
        val returnType = context.types.methodReturnType(method.desc)
        val symbol = SSAFunctionSymbol(
            context.ids.symbolId(),
            "$ownerInternalName.${method.name}${method.desc}",
            parameterTypes,
            returnType
        )
        val parameters = createParameters(parameterTypes)
        val syntheticEntry = SSABlock(context.ids.blockId())

        if (method.instructions == null || method.instructions.size() == 0) {
            syntheticEntry.terminator = SSAUnreachableTerminator
            return JvmSSAImportResult(
                SSAFunction(symbol, parameters.toMutableList(), mutableListOf(syntheticEntry), syntheticEntry),
                context.metadata
            )
        }

        val instructions = method.instructions.toArray().toList()
        val executableIndices = instructions.indices.filter { instructions[it].opcode >= 0 }
        if (executableIndices.isEmpty()) {
            syntheticEntry.terminator = if (returnType == SSAVoidType) SSAReturnTerminator() else SSAUnreachableTerminator
            return JvmSSAImportResult(
                SSAFunction(symbol, parameters.toMutableList(), mutableListOf(syntheticEntry), syntheticEntry),
                context.metadata
            )
        }

        val frames = analyze(ownerInternalName, method)
        val blockInfos = buildBlocks(method, instructions, executableIndices, frames)
        val firstBlockInfo = blockInfos.first()

        val ssaBlocks = mutableListOf(syntheticEntry)
        ssaBlocks += blockInfos.map { it.block }
        val function = SSAFunction(symbol, parameters.toMutableList(), ssaBlocks, syntheticEntry)
        addExceptionRegions(function, method, blockInfos, instructions)

        val initialState = createInitialState(ownerInternalName, method, parameters)
        syntheticEntry.terminator = SSAJumpTerminator(
            SSASuccessor(firstBlockInfo.block, successorArgs(firstBlockInfo, initialState))
        )

        for (info in blockInfos) {
            importBlock(info, blockInfos, instructions)
        }

        return JvmSSAImportResult(function, context.metadata)
    }

    private fun createParameters(types: List<SSAType>): List<SSAParameter> {
        return types.mapIndexed { index, type ->
            SSAParameter(context.ids.valueId(), index, type, "p$index")
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
            val block = SSABlock(context.ids.blockId())
            val shape = frameShape(frames.getOrNull(start), block)
            BlockInfo(start, end, block, shape)
        }
    }

    private fun frameShape(frame: Frame<BasicValue>?, block: SSABlock): BlockFrameShape {
        val locals = mutableListOf<FrameValueKey.Local>()
        val stack = mutableListOf<FrameValueKey.Stack>()

        if (frame != null) {
            for (index in 0 until frame.locals) {
                val type = context.types.frameType(frame.getLocal(index)) ?: continue
                locals += FrameValueKey.Local(index, type)
            }
            for (index in 0 until frame.stackSize) {
                val type = context.types.frameType(frame.getStack(index)) ?: SSAUnknownType
                stack += FrameValueKey.Stack(index, type)
            }
        }

        val args = locals.mapIndexed { index, key ->
            SSABlockArg(
                context.ids.valueId(),
                index,
                key.type,
                SSABlockArgOrigin.FrontendState("local", key.index),
                "l${key.index}"
            )
        } + stack.mapIndexed { index, key ->
            SSABlockArg(
                context.ids.valueId(),
                locals.size + index,
                key.type,
                SSABlockArgOrigin.FrontendState("stack", key.index),
                "s${key.index}"
            )
        }

        block.args += args
        return BlockFrameShape(locals, stack)
    }

    private fun addExceptionRegions(
        function: SSAFunction,
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
            function.exceptionRegions += SSAExceptionRegion(protectedBlocks, handler, caughtType)
        }
    }

    private fun createInitialState(
        ownerInternalName: String,
        method: MethodNode,
        parameters: List<SSAParameter>
    ): FrameState {
        val locals = MutableList(maxOf(method.maxLocals, parameters.size + 1)) { null as SSAValue? }
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
            if (info.block.terminator !is SSAUnreachableTerminator) {
                terminated = true
                break
            }
        }

        if (!terminated) {
            val next = blockInfos.firstOrNull { it.start >= info.end }
            info.block.terminator = if (next != null) {
                SSAJumpTerminator(SSASuccessor(next.block, successorArgs(next, state)))
            } else {
                SSAUnreachableTerminator
            }
        }
    }

    private fun frameState(info: BlockInfo): FrameState {
        val localsSize = (info.shape.locals.maxOfOrNull { it.index } ?: -1) + 1
        val locals = MutableList(localsSize) { null as SSAValue? }

        for ((argIndex, key) in info.shape.locals.withIndex()) {
            ensureLocalSize(locals, key.index)
            locals[key.index] = info.block.args[argIndex]
        }

        val stack = mutableListOf<SSAValue>()
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
            Opcodes.ACONST_NULL -> state.push(SSANullLiteral)
            Opcodes.ICONST_M1 -> state.push(SSAIntLiteral(-1))
            Opcodes.ICONST_0 -> state.push(SSAIntLiteral(0))
            Opcodes.ICONST_1 -> state.push(SSAIntLiteral(1))
            Opcodes.ICONST_2 -> state.push(SSAIntLiteral(2))
            Opcodes.ICONST_3 -> state.push(SSAIntLiteral(3))
            Opcodes.ICONST_4 -> state.push(SSAIntLiteral(4))
            Opcodes.ICONST_5 -> state.push(SSAIntLiteral(5))
            Opcodes.LCONST_0 -> state.push(SSAIntLiteral(0, SSAI64Type))
            Opcodes.LCONST_1 -> state.push(SSAIntLiteral(1, SSAI64Type))
            Opcodes.FCONST_0 -> state.push(SSAFloatLiteral(0.0, SSAF32Type))
            Opcodes.FCONST_1 -> state.push(SSAFloatLiteral(1.0, SSAF32Type))
            Opcodes.FCONST_2 -> state.push(SSAFloatLiteral(2.0, SSAF32Type))
            Opcodes.DCONST_0 -> state.push(SSAFloatLiteral(0.0, SSAF64Type))
            Opcodes.DCONST_1 -> state.push(SSAFloatLiteral(1.0, SSAF64Type))
            Opcodes.POP -> state.pop()
            Opcodes.POP2 -> state.popWords(2)
            Opcodes.DUP -> state.dup()
            Opcodes.DUP_X1 -> state.dupX1()
            Opcodes.DUP_X2 -> state.dupX2()
            Opcodes.DUP2 -> state.dup2()
            Opcodes.DUP2_X1 -> state.dup2X1()
            Opcodes.DUP2_X2 -> state.dup2X2()
            Opcodes.SWAP -> state.swap()
            Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD -> binary(block, state, SSABinaryOp.Add)
            Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB -> binary(block, state, SSABinaryOp.Sub)
            Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL -> binary(block, state, SSABinaryOp.Mul)
            Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV -> binary(block, state, SSABinaryOp.Div)
            Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM -> binary(block, state, SSABinaryOp.Rem)
            Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG -> unary(block, state, SSAUnaryOp.Neg)
            Opcodes.ISHL, Opcodes.LSHL -> binary(block, state, SSABinaryOp.Shl, resultTypeFromLhs = true)
            Opcodes.ISHR, Opcodes.LSHR -> binary(block, state, SSABinaryOp.Shr, resultTypeFromLhs = true)
            Opcodes.IUSHR, Opcodes.LUSHR -> binary(block, state, SSABinaryOp.UShr, resultTypeFromLhs = true)
            Opcodes.IAND, Opcodes.LAND -> binary(block, state, SSABinaryOp.And)
            Opcodes.IOR, Opcodes.LOR -> binary(block, state, SSABinaryOp.Or)
            Opcodes.IXOR, Opcodes.LXOR -> binary(block, state, SSABinaryOp.Xor)
            Opcodes.I2L -> convert(block, state, SSAI64Type)
            Opcodes.I2F -> convert(block, state, SSAF32Type)
            Opcodes.I2D -> convert(block, state, SSAF64Type)
            Opcodes.L2I -> convert(block, state, SSAI32Type)
            Opcodes.L2F -> convert(block, state, SSAF32Type)
            Opcodes.L2D -> convert(block, state, SSAF64Type)
            Opcodes.F2I -> convert(block, state, SSAI32Type)
            Opcodes.F2L -> convert(block, state, SSAI64Type)
            Opcodes.F2D -> convert(block, state, SSAF64Type)
            Opcodes.D2I -> convert(block, state, SSAI32Type)
            Opcodes.D2L -> convert(block, state, SSAI64Type)
            Opcodes.D2F -> convert(block, state, SSAF32Type)
            Opcodes.I2B -> convert(block, state, SSAI8Type)
            Opcodes.I2C -> convert(block, state, SSACharType)
            Opcodes.I2S -> convert(block, state, SSAI16Type)
            Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> compare3(block, state, insn.opcode)
            Opcodes.IALOAD -> arrayLoad(block, state, SSAI32Type)
            Opcodes.LALOAD -> arrayLoad(block, state, SSAI64Type)
            Opcodes.FALOAD -> arrayLoad(block, state, SSAF32Type)
            Opcodes.DALOAD -> arrayLoad(block, state, SSAF64Type)
            Opcodes.AALOAD -> arrayLoad(block, state, null)
            Opcodes.BALOAD -> arrayLoad(block, state, SSAI32Type, SSAI8Type)
            Opcodes.CALOAD -> arrayLoad(block, state, SSAI32Type, SSACharType)
            Opcodes.SALOAD -> arrayLoad(block, state, SSAI32Type, SSAI16Type)
            Opcodes.IASTORE -> arrayStore(block, state, SSAI32Type)
            Opcodes.LASTORE -> arrayStore(block, state, SSAI64Type)
            Opcodes.FASTORE -> arrayStore(block, state, SSAF32Type)
            Opcodes.DASTORE -> arrayStore(block, state, SSAF64Type)
            Opcodes.AASTORE -> arrayStore(block, state)
            Opcodes.BASTORE -> arrayStore(block, state, SSAI8Type)
            Opcodes.CASTORE -> arrayStore(block, state, SSACharType)
            Opcodes.SASTORE -> arrayStore(block, state, SSAI16Type)
            Opcodes.ARRAYLENGTH -> arrayLength(block, state)
            Opcodes.ATHROW -> block.terminator = SSAThrowTerminator(state.pop())
            Opcodes.MONITORENTER -> monitor(block, state, "monitor.enter")
            Opcodes.MONITOREXIT -> monitor(block, state, "monitor.exit")
            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> {
                block.terminator = SSAReturnTerminator(state.pop())
            }
            Opcodes.RETURN -> block.terminator = SSAReturnTerminator()
            else -> unsupportedSimple(block, state, insn.opcode)
        }
    }

    private fun importIntInsn(insn: IntInsnNode, info: BlockInfo, state: FrameState) {
        when (insn.opcode) {
            Opcodes.BIPUSH, Opcodes.SIPUSH -> state.push(SSAIntLiteral(insn.operand.toLong()))
            Opcodes.NEWARRAY -> {
                val count = state.pop()
                val arrayType = SSAArrayType(newArrayElementType(insn.operand))
                val result = result(arrayType, "newarray")
                info.block.append(SSAAllocateInstruction(result, SSAAllocation.Array(arrayType), listOf(count)))
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
                info.block.append(SSAAllocateInstruction(result, SSAAllocation.Object(type)))
                state.push(result)
            }
            Opcodes.ANEWARRAY -> {
                val count = state.pop()
                val arrayType = SSAArrayType(arrayElementTypeFromAnewArray(insn.desc))
                val result = result(arrayType, "anewarray")
                info.block.append(SSAAllocateInstruction(result, SSAAllocation.Array(arrayType), listOf(count)))
                state.push(result)
            }
            Opcodes.CHECKCAST -> {
                val value = state.pop()
                val target = context.types.typeFromDescriptor(objectOrArrayDescriptor(insn.desc))
                val result = result(target, "checkcast")
                info.block.append(
                    SSAConvertInstruction(
                        result,
                        SSAConvertKind.ReferenceCast,
                        value,
                        target,
                        SSAEffect.Pure.copy(mayThrow = true, canMove = false)
                    )
                )
                state.push(result)
            }
            Opcodes.INSTANCEOF -> {
                val value = state.pop()
                val intrinsic = SSAIntrinsicRef(
                    "type.instanceof:${insn.desc}",
                    listOf(value.type),
                    SSAI32Type,
                    SSAEffect.Pure.copy(mayThrow = true, canMove = false)
                )
                val result = result(SSAI32Type, "instanceof")
                info.block.append(SSAIntrinsicInstruction(result, intrinsic, listOf(value)))
                state.push(result)
            }
        }
    }

    private fun importFieldInsn(insn: FieldInsnNode, info: BlockInfo, state: FrameState) {
        val isStatic = insn.opcode == Opcodes.GETSTATIC || insn.opcode == Opcodes.PUTSTATIC
        val fieldType = context.types.typeFromDescriptor(insn.desc)
        val ref = context.externalRef(
            SSAExternalRefKind.Field,
            "${insn.owner}.${insn.name}:${insn.desc}"
        )
        context.metadata.fields[ref] = JvmFieldMetadata(insn.owner, insn.name, insn.desc, isStatic)
        val field = SSAExternalFieldRef(ref, fieldType, isStatic)

        when (insn.opcode) {
            Opcodes.GETSTATIC -> {
                val result = result(fieldType, insn.name)
                info.block.append(SSALoadFieldInstruction(result, field, null))
                state.push(result)
            }
            Opcodes.PUTSTATIC -> {
                info.block.append(SSAStoreFieldInstruction(field, null, state.pop()))
            }
            Opcodes.GETFIELD -> {
                val receiver = state.pop()
                val result = result(fieldType, insn.name)
                info.block.append(SSALoadFieldInstruction(result, field, receiver))
                state.push(result)
            }
            Opcodes.PUTFIELD -> {
                val value = state.pop()
                val receiver = state.pop()
                info.block.append(SSAStoreFieldInstruction(field, receiver, value))
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
            SSAExternalRefKind.Function,
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
        val target = SSAExternalFunctionRef(ref, expectedArgs, returnType)
        val result = if (returnType == SSAVoidType) null else result(returnType, insn.name)
        info.block.append(SSACallInstruction(result, target, allArgs, callDispatch(insn.opcode)))
        if (result != null) state.push(result)
    }

    private fun importInvokeDynamicInsn(insn: InvokeDynamicInsnNode, info: BlockInfo, state: FrameState) {
        val argTypes = Type.getArgumentTypes(insn.desc).map { context.types.type(it) }
        val args = popArgs(state, argTypes.size)
        val returnType = context.types.methodReturnType(insn.desc)
        val ref = context.externalRef(
            SSAExternalRefKind.DynamicCallSite,
            "indy:${insn.name}${insn.desc}"
        )
        context.metadata.dynamicCalls[ref] = JvmDynamicCallMetadata(
            insn.name,
            insn.desc,
            bootstrap(insn.bsm, insn.bsmArgs.toList())
        )
        val site = SSADynamicCallSite(
            context.ids.dynamicSiteId(),
            argTypes,
            returnType,
            ref,
            insn.name
        )
        val result = if (returnType == SSAVoidType) null else result(returnType, insn.name)
        info.block.append(SSADynamicCallInstruction(result, site, args))
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
            block.terminator = SSAJumpTerminator(SSASuccessor(target.block, successorArgs(target, state)))
            return
        }
        if (insn.opcode == Opcodes.JSR) {
            throw UnsupportedOperationException("RET/JSR bytecode is not supported by the SSA importer")
        }

        val condition = when (insn.opcode) {
            Opcodes.IFEQ -> compareWithZero(block, state, SSAComparePredicate.Eq)
            Opcodes.IFNE -> compareWithZero(block, state, SSAComparePredicate.Ne)
            Opcodes.IFLT -> compareWithZero(block, state, SSAComparePredicate.Lt)
            Opcodes.IFGE -> compareWithZero(block, state, SSAComparePredicate.Ge)
            Opcodes.IFGT -> compareWithZero(block, state, SSAComparePredicate.Gt)
            Opcodes.IFLE -> compareWithZero(block, state, SSAComparePredicate.Le)
            Opcodes.IF_ICMPEQ -> compareStack(block, state, SSAComparePredicate.Eq)
            Opcodes.IF_ICMPNE -> compareStack(block, state, SSAComparePredicate.Ne)
            Opcodes.IF_ICMPLT -> compareStack(block, state, SSAComparePredicate.Lt)
            Opcodes.IF_ICMPGE -> compareStack(block, state, SSAComparePredicate.Ge)
            Opcodes.IF_ICMPGT -> compareStack(block, state, SSAComparePredicate.Gt)
            Opcodes.IF_ICMPLE -> compareStack(block, state, SSAComparePredicate.Le)
            Opcodes.IF_ACMPEQ -> compareStack(block, state, SSAComparePredicate.RefEq)
            Opcodes.IF_ACMPNE -> compareStack(block, state, SSAComparePredicate.RefNe)
            Opcodes.IFNULL -> compareRefNull(block, state, SSAComparePredicate.RefEq)
            Opcodes.IFNONNULL -> compareRefNull(block, state, SSAComparePredicate.RefNe)
            else -> throw UnsupportedOperationException("Unsupported jump opcode ${insn.opcode}")
        }

        val trueTarget = targetBlock(insn.label, blockInfos, instructions)
        val falseTarget = fallthroughBlock(info, blockInfos)
            ?: throw IllegalStateException("Conditional branch has no fallthrough block")
        block.terminator = SSABranchTerminator(
            condition,
            SSASuccessor(trueTarget.block, successorArgs(trueTarget, state)),
            SSASuccessor(falseTarget.block, successorArgs(falseTarget, state))
        )
    }

    private fun importLdcInsn(insn: LdcInsnNode, info: BlockInfo, state: FrameState) {
        when (val cst = insn.cst) {
            is Int -> state.push(SSAIntLiteral(cst.toLong(), SSAI32Type))
            is Long -> state.push(SSAIntLiteral(cst, SSAI64Type))
            is Float -> state.push(SSAFloatLiteral(cst.toDouble(), SSAF32Type))
            is Double -> state.push(SSAFloatLiteral(cst, SSAF64Type))
            is String -> state.push(SSAOpaqueLiteral(cst.quote(), context.types.objectType("java/lang/String")))
            is Type -> state.push(SSAOpaqueLiteral(cst.descriptor, context.types.objectType("java/lang/Class")))
            is Handle -> state.push(SSAOpaqueLiteral(handle(cst).toString(), context.types.objectType("java/lang/invoke/MethodHandle")))
            is ConstantDynamic -> {
                val valueType = context.types.typeFromDescriptor(cst.descriptor)
                val ref = context.externalRef(
                    SSAExternalRefKind.DynamicValueSite,
                    "condy:${cst.name}:${cst.descriptor}"
                )
                context.metadata.dynamicValues[ref] = dynamicValue(cst)
                val site = SSADynamicValueSite(
                    context.ids.dynamicSiteId(),
                    valueType,
                    ref,
                    cst.name
                )
                val result = result(valueType, cst.name)
                info.block.append(SSAResolveDynamicValueInstruction(result, site))
                state.push(result)
            }
            else -> state.push(SSAOpaqueLiteral(cst.toString(), SSAUnknownType))
        }
    }

    private fun importIincInsn(insn: IincInsnNode, info: BlockInfo, state: FrameState) {
        val lhs = state.getLocal(insn.`var`)
        val rhs = SSAIntLiteral(insn.incr.toLong(), SSAI32Type)
        val result = result(SSAI32Type, "iinc")
        info.block.append(SSABinaryInstruction(result, SSABinaryOp.Add, lhs, rhs))
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
            SSASwitchCase(insn.min.toLong() + index, SSASuccessor(target.block, successorArgs(target, state)))
        }
        val default = targetBlock(insn.dflt, blockInfos, instructions)
        info.block.terminator = SSASwitchTerminator(
            value,
            cases,
            SSASuccessor(default.block, successorArgs(default, state))
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
            SSASwitchCase(key.toLong(), SSASuccessor(target.block, successorArgs(target, state)))
        }
        val default = targetBlock(insn.dflt, blockInfos, instructions)
        info.block.terminator = SSASwitchTerminator(
            value,
            cases,
            SSASuccessor(default.block, successorArgs(default, state))
        )
    }

    private fun importMultiANewArrayInsn(insn: MultiANewArrayInsnNode, info: BlockInfo, state: FrameState) {
        val dims = popArgs(state, insn.dims)
        val arrayType = context.types.typeFromDescriptor(insn.desc)
        val result = result(arrayType, "multianewarray")
        info.block.append(SSAAllocateInstruction(result, SSAAllocation.Opaque(arrayType, "multianewarray"), dims))
        state.push(result)
    }

    private fun unary(block: SSABlock, state: FrameState, op: SSAUnaryOp) {
        val value = state.pop()
        val result = result(value.type, op.name.lowercase())
        block.append(SSAUnaryInstruction(result, op, value))
        state.push(result)
    }

    private fun binary(
        block: SSABlock,
        state: FrameState,
        op: SSABinaryOp,
        resultTypeFromLhs: Boolean = false
    ) {
        val rhs = state.pop()
        val lhs = state.pop()
        val type = if (resultTypeFromLhs) lhs.type else commonType(lhs.type, rhs.type)
        val result = result(type, op.name.lowercase())
        block.append(SSABinaryInstruction(result, op, lhs, rhs))
        state.push(result)
    }

    private fun convert(block: SSABlock, state: FrameState, targetType: SSAType) {
        val value = state.pop()
        val result = result(targetType, "convert")
        block.append(SSAConvertInstruction(result, SSAConvertKind.Numeric, value, targetType))
        state.push(result)
    }

    private fun compare3(block: SSABlock, state: FrameState, opcode: Int) {
        val rhs = state.pop()
        val lhs = state.pop()
        val intrinsic = SSAIntrinsicRef(
            "cmp3.$opcode",
            listOf(lhs.type, rhs.type),
            SSAI32Type,
            SSAEffect.Pure
        )
        val result = result(SSAI32Type, "cmp3")
        block.append(SSAIntrinsicInstruction(result, intrinsic, listOf(lhs, rhs)))
        state.push(result)
    }

    private fun arrayLoad(block: SSABlock, state: FrameState, type: SSAType?, elementType: SSAType? = type) {
        val index = state.pop()
        val array = state.pop()
        val result = result(type ?: arrayLoadResultType(array.type), "aload")
        block.append(SSAArrayLoadInstruction(result, array, index, elementType = elementType))
        state.push(result)
    }

    private fun arrayLoadResultType(arrayType: SSAType): SSAType {
        val type = arrayType as? SSAArrayType ?: return SSARefType()
        return if (type.dimensions == 1) {
            type.elementType
        } else {
            SSAArrayType(type.elementType, type.dimensions - 1, type.nullable)
        }
    }

    private fun arrayStore(block: SSABlock, state: FrameState, elementType: SSAType? = null) {
        val value = state.pop()
        val index = state.pop()
        val array = state.pop()
        block.append(SSAArrayStoreInstruction(array, index, value, elementType = elementType))
    }

    private fun arrayLength(block: SSABlock, state: FrameState) {
        val array = state.pop()
        val intrinsic = SSAIntrinsicRef(
            "array.length",
            listOf(array.type),
            SSAI32Type,
            SSAEffect.Pure.copy(mayThrow = true, canMove = false)
        )
        val result = result(SSAI32Type, "length")
        block.append(SSAIntrinsicInstruction(result, intrinsic, listOf(array)))
        state.push(result)
    }

    private fun monitor(block: SSABlock, state: FrameState, name: String) {
        val value = state.pop()
        val intrinsic = SSAIntrinsicRef(name, listOf(value.type), SSAVoidType, SSAEffect.Barrier.copy(mayThrow = true))
        block.append(SSAIntrinsicInstruction(null, intrinsic, listOf(value)))
    }

    private fun unsupportedSimple(block: SSABlock, state: FrameState, opcode: Int) {
        val intrinsic = SSAIntrinsicRef("jvm.opcode.$opcode", emptyList(), SSAVoidType, SSAEffect.Barrier)
        block.append(SSAIntrinsicInstruction(null, intrinsic, emptyList()))
    }

    private fun compareWithZero(block: SSABlock, state: FrameState, predicate: SSAComparePredicate): SSAValue {
        val value = state.pop()
        val result = result(SSABoolType, "if")
        block.append(SSACompareInstruction(result, predicate, value, SSAIntLiteral(0, value.type as? SSAIntegerType ?: SSAI32Type)))
        return result
    }

    private fun compareStack(block: SSABlock, state: FrameState, predicate: SSAComparePredicate): SSAValue {
        val rhs = state.pop()
        val lhs = state.pop()
        val result = result(SSABoolType, "if")
        block.append(SSACompareInstruction(result, predicate, lhs, rhs))
        return result
    }

    private fun compareRefNull(block: SSABlock, state: FrameState, predicate: SSAComparePredicate): SSAValue {
        val value = state.pop()
        val result = result(SSABoolType, "if")
        block.append(SSACompareInstruction(result, predicate, value, SSANullLiteral))
        return result
    }

    private fun successorArgs(target: BlockInfo, state: FrameState): List<SSAValue> {
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

    private fun result(type: SSAType, debugName: String? = null): SSAInstructionResult {
        return SSAInstructionResult(context.ids.valueId(), type, debugName)
    }

    private fun commonType(lhs: SSAType, rhs: SSAType): SSAType {
        return if (SSATypes.isAssignable(lhs, rhs)) rhs else lhs
    }

    private fun popArgs(state: FrameState, count: Int): List<SSAValue> {
        return List(count) { state.pop() }.asReversed()
    }

    private fun callDispatch(opcode: Int): SSACallDispatch {
        return when (opcode) {
            Opcodes.INVOKESTATIC -> SSACallDispatch.Direct
            Opcodes.INVOKEVIRTUAL -> SSACallDispatch.Virtual
            Opcodes.INVOKEINTERFACE -> SSACallDispatch.Interface
            Opcodes.INVOKESPECIAL -> SSACallDispatch.Direct
            else -> SSACallDispatch.External
        }
    }

    private fun newArrayElementType(operand: Int): SSAType {
        return when (operand) {
            Opcodes.T_BOOLEAN -> SSABoolType
            Opcodes.T_CHAR -> SSACharType
            Opcodes.T_FLOAT -> SSAF32Type
            Opcodes.T_DOUBLE -> SSAF64Type
            Opcodes.T_BYTE -> SSAI8Type
            Opcodes.T_SHORT -> SSAI16Type
            Opcodes.T_INT -> SSAI32Type
            Opcodes.T_LONG -> SSAI64Type
            else -> SSAUnknownType
        }
    }

    private fun arrayElementTypeFromAnewArray(desc: String): SSAType {
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
        val block: SSABlock,
        val shape: BlockFrameShape
    )

    private data class BlockFrameShape(
        val locals: List<FrameValueKey.Local>,
        val stack: List<FrameValueKey.Stack>
    )

    private sealed interface FrameValueKey {
        val index: Int
        val type: SSAType

        data class Local(override val index: Int, override val type: SSAType) : FrameValueKey
        data class Stack(override val index: Int, override val type: SSAType) : FrameValueKey
    }

    private data class FrameState(
        val locals: MutableList<SSAValue?>,
        val stack: MutableList<SSAValue>
    ) {
        fun push(value: SSAValue) {
            stack += value
        }

        fun pop(): SSAValue {
            return if (stack.isEmpty()) SSAOpaqueLiteral("stack_underflow", SSAUnknownType)
            else stack.removeAt(stack.lastIndex)
        }

        fun popWords(words: Int): List<SSAValue> {
            val values = mutableListOf<SSAValue>()
            var size = 0
            while (size < words) {
                val value = pop()
                values.add(0, value)
                size += stackSize(value.type)
                if (value is SSAOpaqueLiteral && value.text == "stack_underflow") break
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

        fun getLocal(index: Int): SSAValue {
            return locals.getOrNull(index) ?: SSAOpaqueLiteral("local_$index", SSAUnknownType)
        }

        fun getLocalOrUndef(index: Int, type: SSAType): SSAValue {
            return locals.getOrNull(index) ?: SSAOpaqueLiteral("undef_local_$index", type)
        }

        fun getStackOrUndef(index: Int, type: SSAType): SSAValue {
            return stack.getOrNull(index) ?: SSAOpaqueLiteral("undef_stack_$index", type)
        }

        fun setLocal(index: Int, value: SSAValue) {
            ensureLocalSize(locals, index)
            locals[index] = value
            if (stackSize(value.type) == 2) {
                ensureLocalSize(locals, index + 1)
                locals[index + 1] = null
            }
        }
    }
}

private fun ensureLocalSize(locals: MutableList<SSAValue?>, index: Int) {
    while (locals.size <= index) locals += null
}

private fun stackSize(type: SSAType): Int {
    return when (type) {
        SSAI64Type, SSAF64Type -> 2
        SSAVoidType -> 0
        else -> 1
    }
}
