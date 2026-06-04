package net.spartanb312.grunt.ir.jvm

import net.spartanb312.grunt.ir.core.*
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

data class JvmIrExportOptions(
    val access: Int? = null,
    val signature: String? = null,
    val exceptions: List<String> = emptyList(),
    val computeMaxsPadding: Int = 16
)

class JvmIrExporter(
    private val metadata: JvmIrMetadata = JvmIrMetadata(),
    private val options: JvmIrExportOptions = JvmIrExportOptions()
) {
    fun export(function: IrFunction): MethodNode {
        val descriptor = methodDescriptor(function)
        val method = MethodNode(
            options.access ?: inferredAccess(function, descriptor),
            methodName(function),
            descriptor,
            options.signature,
            options.exceptions.toTypedArray()
        )
        val state = ExportState(function)

        for (block in function.blocks) {
            method.instructions.add(state.label(block))
            emitBlockPrologue(method.instructions, block, state)
            for (instruction in block.instructions) {
                emitInstruction(method.instructions, instruction, state)
            }
            emitTerminator(method.instructions, block.terminator, state)
            method.instructions.add(state.endLabel(block))
        }

        emitExceptionRegions(method, function, state)
        method.maxLocals = state.nextLocal + options.computeMaxsPadding
        method.maxStack = maxOf(64, estimateMaxStack(function))
        return method
    }

    private fun estimateMaxStack(function: IrFunction): Int {
        var maxStack = if (function.exceptionRegions.isEmpty()) 0 else 1
        for (block in function.blocks) {
            if (stateStartsWithException(block, function)) maxStack = maxOf(maxStack, 1)
            for (instruction in block.instructions) {
                maxStack = maxOf(maxStack, estimateMaxStack(instruction))
            }
            maxStack = maxOf(maxStack, estimateMaxStack(block.terminator))
        }
        return maxStack
    }

    private fun stateStartsWithException(block: IrBlock, function: IrFunction): Boolean {
        return function.exceptionRegions.any { it.handler == block }
    }

    private fun estimateMaxStack(instruction: IrInstruction): Int {
        return when (instruction) {
            is IrUnaryInstruction -> when {
                instruction.op == IrUnaryOp.BitNot && instruction.value.type == IrI64Type ->
                    stackSize(instruction.value.type) + stackSize(IrI64Type)
                else -> maxOf(stackSize(instruction.value.type), stackSize(instruction.result.type))
            }
            is IrBinaryInstruction -> maxOf(
                stackSize(instruction.lhs.type) + stackSize(instruction.rhs.type),
                stackSize(instruction.result.type)
            )
            is IrCompareInstruction -> maxOf(
                stackSize(instruction.lhs.type) + stackSize(instruction.rhs.type),
                stackSize(instruction.result.type)
            )
            is IrConvertInstruction -> maxOf(
                stackSize(instruction.value.type),
                stackSize(instruction.targetType),
                stackSize(instruction.result.type)
            )
            is IrLoadFieldInstruction -> {
                val field = fieldMetadata(instruction.field)
                val fieldStack = Type.getType(field.desc).size
                if (field.isStatic) fieldStack else maxOf(stackSizeOrZero(instruction.receiver?.type), fieldStack)
            }
            is IrStoreFieldInstruction -> stackSizeOrZero(instruction.receiver?.type) + stackSize(instruction.value.type)
            is IrArrayLoadInstruction -> maxOf(
                stackSize(instruction.array.type) + stackSize(instruction.index.type),
                stackSize(instruction.result.type)
            )
            is IrArrayStoreInstruction -> stackSize(instruction.array.type) + stackSize(instruction.index.type) + stackSize(
                instruction.value.type
            )
            is IrCallInstruction -> maxOf(sumStackSize(instruction.args), stackSizeOrZero(instruction.result?.type))
            is IrResolveDynamicValueInstruction -> stackSize(instruction.result.type)
            is IrDynamicCallInstruction -> maxOf(
                sumStackSize(instruction.args),
                stackSizeOrZero(instruction.result?.type)
            )
            is IrAllocateInstruction -> maxOf(sumStackSize(instruction.args), stackSize(instruction.result.type))
            is IrIntrinsicInstruction -> estimateMaxStack(instruction)
            is IrBarrierInstruction -> 0
        }
    }

    private fun estimateMaxStack(instruction: IrIntrinsicInstruction): Int {
        val argsStack = sumStackSize(instruction.args)
        val resultStack = stackSizeOrZero(instruction.result?.type)
        return when {
            instruction.intrinsic.name.startsWith("jvm.opcode.") -> maxOf(argsStack, resultStack, 4)
            else -> maxOf(argsStack, resultStack)
        }
    }

    private fun estimateMaxStack(terminator: IrTerminator): Int {
        return when (terminator) {
            is IrJumpTerminator -> estimatePassArgsMaxStack(terminator.target)
            is IrBranchTerminator -> maxOf(
                stackSize(terminator.condition.type),
                estimatePassArgsMaxStack(terminator.trueTarget),
                estimatePassArgsMaxStack(terminator.falseTarget)
            )
            is IrSwitchTerminator -> maxOf(
                stackSize(terminator.value.type),
                estimatePassArgsMaxStack(terminator.defaultTarget),
                terminator.cases.maxOfOrNull { estimatePassArgsMaxStack(it.target) } ?: 0
            )
            is IrReturnTerminator -> stackSizeOrZero(terminator.value?.type)
            is IrThrowTerminator -> stackSize(terminator.exception.type)
            IrUnreachableTerminator -> 1
        }
    }

    private fun estimatePassArgsMaxStack(successor: IrSuccessor): Int {
        return successor.args.maxOfOrNull { stackSize(it.type) } ?: 0
    }

    private fun sumStackSize(values: Iterable<IrValue>): Int {
        return values.sumOf { stackSize(it.type) }
    }

    private fun stackSizeOrZero(type: IrType?): Int {
        return if (type == null) 0 else stackSize(type)
    }

    private fun emitBlockPrologue(out: InsnList, block: IrBlock, state: ExportState) {
        if (!state.isExceptionHandler(block)) return

        val exceptionArg = block.args
            .filter { it.origin == IrBlockArgOrigin.ExceptionObject || it.frontendStateName == "stack" }
            .minByOrNull { it.frontendStateIndex ?: 0 }
            ?: return
        out.add(VarInsnNode(storeOpcode(exceptionArg.type), state.slot(exceptionArg)))
    }

    private fun emitInstruction(out: InsnList, instruction: IrInstruction, state: ExportState) {
        when (instruction) {
            is IrUnaryInstruction -> {
                load(out, instruction.value, state)
                when (instruction.op) {
                    IrUnaryOp.Neg -> out.add(InsnNode(negOpcode(instruction.result.type)))
                    IrUnaryOp.BitNot -> emitBitNot(out, instruction.result.type)
                    IrUnaryOp.LogicalNot -> emitLogicalNot(out)
                }
                storeResult(out, instruction.result, state)
            }
            is IrBinaryInstruction -> {
                load(out, instruction.lhs, state)
                load(out, instruction.rhs, state)
                out.add(InsnNode(binaryOpcode(instruction.op, instruction.result.type)))
                storeResult(out, instruction.result, state)
            }
            is IrCompareInstruction -> emitCompare(out, instruction, state)
            is IrConvertInstruction -> {
                load(out, instruction.value, state)
                emitConversion(out, instruction)
                storeResult(out, instruction.result, state)
            }
            is IrLoadFieldInstruction -> {
                val field = fieldMetadata(instruction.field)
                if (!field.isStatic) {
                    load(out, instruction.receiver ?: error("Instance field load requires receiver"), state)
                }
                out.add(FieldInsnNode(if (field.isStatic) Opcodes.GETSTATIC else Opcodes.GETFIELD, field.owner, field.name, field.desc))
                storeResult(out, instruction.result, state)
            }
            is IrStoreFieldInstruction -> {
                val field = fieldMetadata(instruction.field)
                if (!field.isStatic) {
                    load(out, instruction.receiver ?: error("Instance field store requires receiver"), state)
                }
                load(out, instruction.value, state)
                out.add(FieldInsnNode(if (field.isStatic) Opcodes.PUTSTATIC else Opcodes.PUTFIELD, field.owner, field.name, field.desc))
            }
            is IrArrayLoadInstruction -> {
                load(out, instruction.array, state)
                load(out, instruction.index, state)
                out.add(InsnNode(arrayLoadOpcode(instruction.elementType ?: arrayElementType(instruction.array.type), instruction.result.type)))
                storeResult(out, instruction.result, state)
            }
            is IrArrayStoreInstruction -> {
                load(out, instruction.array, state)
                load(out, instruction.index, state)
                load(out, instruction.value, state)
                out.add(InsnNode(arrayStoreOpcode(instruction.elementType ?: arrayElementType(instruction.array.type), instruction.value.type)))
            }
            is IrCallInstruction -> emitCall(out, instruction, state)
            is IrResolveDynamicValueInstruction -> {
                val dynamic = dynamicValueMetadata(instruction.site)
                out.add(LdcInsnNode(constantDynamic(dynamic)))
                storeResult(out, instruction.result, state)
            }
            is IrDynamicCallInstruction -> {
                val dynamic = dynamicCallMetadata(instruction.site)
                instruction.args.forEach { load(out, it, state) }
                out.add(
                    InvokeDynamicInsnNode(
                        dynamic.name,
                        dynamic.desc,
                        handle(dynamic.bootstrap.handle),
                        *dynamic.bootstrap.args.map { bootstrapArg(it) }.toTypedArray()
                    )
                )
                instruction.result?.let { storeResult(out, it, state) }
            }
            is IrAllocateInstruction -> emitAllocation(out, instruction, state)
            is IrIntrinsicInstruction -> emitIntrinsic(out, instruction, state)
            is IrBarrierInstruction -> Unit
        }
    }

    private fun emitTerminator(out: InsnList, terminator: IrTerminator, state: ExportState) {
        when (terminator) {
            is IrJumpTerminator -> {
                passArgs(out, terminator.target, state)
                out.add(JumpInsnNode(Opcodes.GOTO, state.label(terminator.target.block)))
            }
            is IrBranchTerminator -> {
                val trueLabel = LabelNode()
                load(out, terminator.condition, state)
                out.add(JumpInsnNode(Opcodes.IFNE, trueLabel))
                passArgs(out, terminator.falseTarget, state)
                out.add(JumpInsnNode(Opcodes.GOTO, state.label(terminator.falseTarget.block)))
                out.add(trueLabel)
                passArgs(out, terminator.trueTarget, state)
                out.add(JumpInsnNode(Opcodes.GOTO, state.label(terminator.trueTarget.block)))
            }
            is IrSwitchTerminator -> {
                val defaultStub = LabelNode()
                val stubs = terminator.cases.map { LabelNode() }
                load(out, terminator.value, state)
                out.add(
                    LookupSwitchInsnNode(
                        defaultStub,
                        terminator.cases.map { it.key.toInt() }.toIntArray(),
                        stubs.toTypedArray()
                    )
                )
                for ((case, stub) in terminator.cases.zip(stubs)) {
                    out.add(stub)
                    passArgs(out, case.target, state)
                    out.add(JumpInsnNode(Opcodes.GOTO, state.label(case.target.block)))
                }
                out.add(defaultStub)
                passArgs(out, terminator.defaultTarget, state)
                out.add(JumpInsnNode(Opcodes.GOTO, state.label(terminator.defaultTarget.block)))
            }
            is IrReturnTerminator -> {
                val value = terminator.value
                if (value == null) {
                    out.add(InsnNode(Opcodes.RETURN))
                } else {
                    load(out, value, state)
                    out.add(InsnNode(returnOpcode(value.type)))
                }
            }
            is IrThrowTerminator -> {
                load(out, terminator.exception, state)
                out.add(InsnNode(Opcodes.ATHROW))
            }
            IrUnreachableTerminator -> {
                out.add(InsnNode(Opcodes.ACONST_NULL))
                out.add(InsnNode(Opcodes.ATHROW))
            }
        }
    }

    private fun passArgs(out: InsnList, successor: IrSuccessor, state: ExportState) {
        if (successor.args.isEmpty()) return

        val tempSlots = successor.args.map { state.allocateTemp(it.type) }
        for ((arg, slot) in successor.args.zip(tempSlots)) {
            load(out, arg, state)
            out.add(VarInsnNode(storeOpcode(arg.type), slot))
        }
        for (index in successor.args.indices) {
            val arg = successor.args[index]
            val blockArg = successor.block.args[index]
            val tempSlot = tempSlots[index]
            out.add(VarInsnNode(loadOpcode(arg.type), tempSlot))
            out.add(VarInsnNode(storeOpcode(blockArg.type), state.slot(blockArg)))
        }
    }

    private fun emitCall(out: InsnList, instruction: IrCallInstruction, state: ExportState) {
        val method = methodMetadata(instruction.target)
        instruction.args.forEach { load(out, it, state) }
        out.add(MethodInsnNode(method.opcode, method.owner, method.name, method.desc, method.isInterface))
        instruction.result?.let { storeResult(out, it, state) }
    }

    private fun emitAllocation(out: InsnList, instruction: IrAllocateInstruction, state: ExportState) {
        when (val allocation = instruction.allocation) {
            is IrAllocation.Object -> {
                val internalName = refInternalName(allocation.type)
                out.add(TypeInsnNode(Opcodes.NEW, internalName))
                storeResult(out, instruction.result, state)
            }
            is IrAllocation.Array -> {
                instruction.args.forEach { load(out, it, state) }
                emitNewArray(out, allocation.type)
                storeResult(out, instruction.result, state)
            }
            is IrAllocation.Opaque -> {
                if (allocation.name == "multianewarray" && allocation.type is IrArrayType) {
                    instruction.args.forEach { load(out, it, state) }
                    out.add(MultiANewArrayInsnNode(descriptor(allocation.type), instruction.args.size))
                    storeResult(out, instruction.result, state)
                } else {
                    error("Unsupported opaque allocation ${allocation.name}")
                }
            }
        }
    }

    private fun emitIntrinsic(out: InsnList, instruction: IrIntrinsicInstruction, state: ExportState) {
        when {
            instruction.intrinsic.name == "array.length" -> {
                load(out, instruction.args.single(), state)
                out.add(InsnNode(Opcodes.ARRAYLENGTH))
                instruction.result?.let { storeResult(out, it, state) }
            }
            instruction.intrinsic.name.startsWith("type.instanceof:") -> {
                load(out, instruction.args.single(), state)
                out.add(TypeInsnNode(Opcodes.INSTANCEOF, instruction.intrinsic.name.removePrefix("type.instanceof:")))
                instruction.result?.let { storeResult(out, it, state) }
            }
            instruction.intrinsic.name == "monitor.enter" -> {
                load(out, instruction.args.single(), state)
                out.add(InsnNode(Opcodes.MONITORENTER))
            }
            instruction.intrinsic.name == "monitor.exit" -> {
                load(out, instruction.args.single(), state)
                out.add(InsnNode(Opcodes.MONITOREXIT))
            }
            instruction.intrinsic.name.startsWith("cmp3.") -> {
                instruction.args.forEach { load(out, it, state) }
                out.add(InsnNode(instruction.intrinsic.name.removePrefix("cmp3.").toInt()))
                instruction.result?.let { storeResult(out, it, state) }
            }
            instruction.intrinsic.name.startsWith("jvm.opcode.") -> {
                out.add(InsnNode(instruction.intrinsic.name.removePrefix("jvm.opcode.").toInt()))
            }
            else -> error("Unsupported intrinsic ${instruction.intrinsic.name}")
        }
    }

    private fun emitCompare(out: InsnList, instruction: IrCompareInstruction, state: ExportState) {
        val trueLabel = LabelNode()
        val endLabel = LabelNode()
        load(out, instruction.lhs, state)
        load(out, instruction.rhs, state)
        out.add(JumpInsnNode(compareOpcode(instruction.predicate, instruction.lhs.type), trueLabel))
        out.add(InsnNode(Opcodes.ICONST_0))
        out.add(JumpInsnNode(Opcodes.GOTO, endLabel))
        out.add(trueLabel)
        out.add(InsnNode(Opcodes.ICONST_1))
        out.add(endLabel)
        storeResult(out, instruction.result, state)
    }

    private fun emitBitNot(out: InsnList, type: IrType) {
        if (type == IrI64Type) {
            out.add(LdcInsnNode(-1L))
            out.add(InsnNode(Opcodes.LXOR))
        } else {
            out.add(InsnNode(Opcodes.ICONST_M1))
            out.add(InsnNode(Opcodes.IXOR))
        }
    }

    private fun emitLogicalNot(out: InsnList) {
        val trueLabel = LabelNode()
        val endLabel = LabelNode()
        out.add(JumpInsnNode(Opcodes.IFEQ, trueLabel))
        out.add(InsnNode(Opcodes.ICONST_0))
        out.add(JumpInsnNode(Opcodes.GOTO, endLabel))
        out.add(trueLabel)
        out.add(InsnNode(Opcodes.ICONST_1))
        out.add(endLabel)
    }

    private fun load(out: InsnList, value: IrValue, state: ExportState) {
        when (value) {
            is IrSsaValue -> out.add(VarInsnNode(loadOpcode(value.type), state.slot(value)))
            is IrBoolLiteral -> out.add(InsnNode(if (value.value) Opcodes.ICONST_1 else Opcodes.ICONST_0))
            is IrIntLiteral -> emitInt(out, value.value, value.type)
            is IrFloatLiteral -> emitFloat(out, value.value, value.type)
            IrNullLiteral -> out.add(InsnNode(Opcodes.ACONST_NULL))
            is IrOpaqueLiteral -> emitOpaque(out, value)
        }
    }

    private fun storeResult(out: InsnList, result: IrInstructionResult, state: ExportState) {
        out.add(VarInsnNode(storeOpcode(result.type), state.slot(result)))
    }

    private fun emitInt(out: InsnList, value: Long, type: IrIntegerType) {
        if (type == IrI64Type) {
            when (value) {
                0L -> out.add(InsnNode(Opcodes.LCONST_0))
                1L -> out.add(InsnNode(Opcodes.LCONST_1))
                else -> out.add(LdcInsnNode(value))
            }
        } else {
            when (value) {
                -1L -> out.add(InsnNode(Opcodes.ICONST_M1))
                0L -> out.add(InsnNode(Opcodes.ICONST_0))
                1L -> out.add(InsnNode(Opcodes.ICONST_1))
                2L -> out.add(InsnNode(Opcodes.ICONST_2))
                3L -> out.add(InsnNode(Opcodes.ICONST_3))
                4L -> out.add(InsnNode(Opcodes.ICONST_4))
                5L -> out.add(InsnNode(Opcodes.ICONST_5))
                in Byte.MIN_VALUE..Byte.MAX_VALUE -> out.add(IntInsnNode(Opcodes.BIPUSH, value.toInt()))
                in Short.MIN_VALUE..Short.MAX_VALUE -> out.add(IntInsnNode(Opcodes.SIPUSH, value.toInt()))
                else -> out.add(LdcInsnNode(value.toInt()))
            }
        }
    }

    private fun emitFloat(out: InsnList, value: Double, type: IrFloatType) {
        if (type == IrF32Type) {
            when (value.toFloat()) {
                0.0f -> out.add(InsnNode(Opcodes.FCONST_0))
                1.0f -> out.add(InsnNode(Opcodes.FCONST_1))
                2.0f -> out.add(InsnNode(Opcodes.FCONST_2))
                else -> out.add(LdcInsnNode(value.toFloat()))
            }
        } else {
            when (value) {
                0.0 -> out.add(InsnNode(Opcodes.DCONST_0))
                1.0 -> out.add(InsnNode(Opcodes.DCONST_1))
                else -> out.add(LdcInsnNode(value))
            }
        }
    }

    private fun emitOpaque(out: InsnList, value: IrOpaqueLiteral) {
        when {
            value.type is IrRefType && value.type.symbol?.name == "java/lang/String" -> {
                out.add(LdcInsnNode(unquote(value.text)))
            }
            value.type is IrRefType && value.type.symbol?.name == "java/lang/Class" -> {
                out.add(LdcInsnNode(Type.getType(value.text)))
            }
            else -> error("Cannot export opaque literal ${value.text} of type ${value.type.displayName}")
        }
    }

    private fun emitNewArray(out: InsnList, type: IrArrayType) {
        if (type.dimensions != 1) {
            out.add(MultiANewArrayInsnNode(descriptor(type), 1))
            return
        }
        when (type.elementType) {
            IrBoolType -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN))
            IrI8Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE))
            IrI16Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_SHORT))
            IrCharType -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_CHAR))
            IrI32Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT))
            IrI64Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_LONG))
            IrF32Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_FLOAT))
            IrF64Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_DOUBLE))
            is IrRefType -> out.add(TypeInsnNode(Opcodes.ANEWARRAY, refInternalName(type.elementType)))
            is IrArrayType -> out.add(TypeInsnNode(Opcodes.ANEWARRAY, descriptor(type.elementType)))
            else -> error("Unsupported array element type ${type.elementType.displayName}")
        }
    }

    private fun emitExceptionRegions(method: MethodNode, function: IrFunction, state: ExportState) {
        for (region in function.exceptionRegions) {
            val protected = region.protectedBlocks.sortedBy { function.blocks.indexOf(it) }
            val first = protected.firstOrNull() ?: continue
            val last = protected.last()
            method.tryCatchBlocks.add(
                TryCatchBlockNode(
                    state.label(first),
                    state.endLabel(last),
                    state.label(region.handler),
                    (region.caughtType as? IrRefType)?.symbol?.name
                )
            )
        }
    }

    private fun fieldMetadata(field: IrFieldRef): JvmFieldMetadata {
        return when (field) {
            is IrExternalFieldRef -> metadata.fields[field.ref] ?: parseFieldRef(field.ref.debugName, field.isStatic)
            is IrSymbolFieldRef -> {
                val owner = field.symbol.owner?.name ?: error("Symbol field ${field.symbol.name} has no JVM owner")
                JvmFieldMetadata(owner, field.symbol.name, descriptor(field.type), field.isStatic)
            }
        }
    }

    private fun methodMetadata(callable: IrCallableRef): JvmMethodMetadata {
        return when (callable) {
            is IrExternalFunctionRef -> metadata.methods[callable.ref] ?: parseMethodRef(callable.ref.debugName, callable)
            is IrFunctionRef -> parseMethodRef(callable.symbol.name, callable)
            is IrIntrinsicRef -> error("Intrinsic ${callable.name} is not a JVM method call")
        }
    }

    private fun dynamicValueMetadata(site: IrDynamicValueSite): JvmDynamicValueMetadata {
        val ref = site.externalRef ?: error("Dynamic value ${site.id} has no JVM external ref")
        return metadata.dynamicValues[ref] ?: error("Missing JVM metadata for dynamic value $ref")
    }

    private fun dynamicCallMetadata(site: IrDynamicCallSite): JvmDynamicCallMetadata {
        val ref = site.externalRef ?: error("Dynamic call ${site.id} has no JVM external ref")
        return metadata.dynamicCalls[ref] ?: error("Missing JVM metadata for dynamic call $ref")
    }

    private fun parseFieldRef(debugName: String?, isStatic: Boolean): JvmFieldMetadata {
        val text = debugName ?: error("Missing JVM field metadata")
        val colon = text.lastIndexOf(':')
        val dot = text.lastIndexOf('.', colon)
        return JvmFieldMetadata(text.substring(0, dot), text.substring(dot + 1, colon), text.substring(colon + 1), isStatic)
    }

    private fun parseMethodRef(debugName: String?, callable: IrCallableRef): JvmMethodMetadata {
        val text = debugName ?: error("Missing JVM method metadata")
        val descStart = text.indexOf('(')
        val dot = text.lastIndexOf('.', descStart)
        val owner = text.substring(0, dot)
        val name = text.substring(dot + 1, descStart)
        val desc = text.substring(descStart)
        val opcode = when {
            callable is IrExternalFunctionRef && callable.parameterTypes.size == Type.getArgumentTypes(desc).size -> Opcodes.INVOKESTATIC
            callable is IrFunctionRef && callable.parameterTypes.size == Type.getArgumentTypes(desc).size -> Opcodes.INVOKESTATIC
            else -> Opcodes.INVOKEVIRTUAL
        }
        return JvmMethodMetadata(opcode, owner, name, desc, opcode == Opcodes.INVOKEINTERFACE)
    }

    private fun methodDescriptor(function: IrFunction): String {
        val name = function.symbol.name
        val descStart = name.indexOf('(')
        return if (descStart >= 0) name.substring(descStart) else Type.getMethodDescriptor(
            Type.getType(descriptor(function.returnType)),
            *function.symbol.parameterTypes.map { Type.getType(descriptor(it)) }.toTypedArray()
        )
    }

    private fun methodName(function: IrFunction): String {
        val name = function.symbol.name
        val descStart = name.indexOf('(')
        if (descStart < 0) return name.substringAfterLast('.')
        val dot = name.lastIndexOf('.', descStart)
        return if (dot >= 0) name.substring(dot + 1, descStart) else name.substring(0, descStart)
    }

    private fun inferredAccess(function: IrFunction, descriptor: String): Int {
        val jvmArgCount = Type.getArgumentTypes(descriptor).size
        return Opcodes.ACC_PUBLIC or if (function.parameters.size == jvmArgCount) Opcodes.ACC_STATIC else 0
    }

    private fun handle(metadata: JvmHandleMetadata): Handle {
        return Handle(metadata.tag, metadata.owner, metadata.name, metadata.desc, metadata.isInterface)
    }

    private fun constantDynamic(metadata: JvmDynamicValueMetadata): ConstantDynamic {
        return ConstantDynamic(
            metadata.name,
            metadata.desc,
            handle(metadata.bootstrap.handle),
            *metadata.bootstrap.args.map { bootstrapArg(it) }.toTypedArray()
        )
    }

    private fun bootstrapArg(arg: JvmBootstrapArgMetadata): Any {
        return when (arg) {
            is JvmBootstrapArgMetadata.IntArg -> arg.value
            is JvmBootstrapArgMetadata.LongArg -> arg.value
            is JvmBootstrapArgMetadata.FloatArg -> arg.value
            is JvmBootstrapArgMetadata.DoubleArg -> arg.value
            is JvmBootstrapArgMetadata.StringArg -> arg.value
            is JvmBootstrapArgMetadata.TypeArg -> Type.getType(arg.descriptor)
            is JvmBootstrapArgMetadata.HandleArg -> handle(arg.handle)
            is JvmBootstrapArgMetadata.DynamicValueArg -> constantDynamic(arg.value)
            is JvmBootstrapArgMetadata.OpaqueArg -> arg.value
        }
    }

    private fun descriptor(type: IrType): String {
        return when (type) {
            IrBoolType -> "Z"
            IrI8Type -> "B"
            IrI16Type -> "S"
            IrCharType -> "C"
            IrI32Type -> "I"
            IrI64Type -> "J"
            IrF32Type -> "F"
            IrF64Type -> "D"
            IrVoidType -> "V"
            IrNullType -> "Ljava/lang/Object;"
            IrUnknownType -> "Ljava/lang/Object;"
            is IrRefType -> "L${refInternalName(type)};"
            is IrArrayType -> "[".repeat(type.dimensions) + descriptor(type.elementType)
            is IrOpaqueType -> type.displayName
        }
    }

    private fun refInternalName(type: IrRefType): String {
        return type.symbol?.name ?: "java/lang/Object"
    }

    private fun compareOpcode(predicate: IrComparePredicate, lhsType: IrType): Int {
        val ref = IrTypes.isReference(lhsType)
        return when (predicate) {
            IrComparePredicate.Eq -> if (ref) Opcodes.IF_ACMPEQ else Opcodes.IF_ICMPEQ
            IrComparePredicate.Ne -> if (ref) Opcodes.IF_ACMPNE else Opcodes.IF_ICMPNE
            IrComparePredicate.Lt -> Opcodes.IF_ICMPLT
            IrComparePredicate.Le -> Opcodes.IF_ICMPLE
            IrComparePredicate.Gt -> Opcodes.IF_ICMPGT
            IrComparePredicate.Ge -> Opcodes.IF_ICMPGE
            IrComparePredicate.RefEq -> Opcodes.IF_ACMPEQ
            IrComparePredicate.RefNe -> Opcodes.IF_ACMPNE
        }
    }

    private fun binaryOpcode(op: IrBinaryOp, type: IrType): Int {
        val prefix = opcodePrefix(type)
        return when (op) {
            IrBinaryOp.Add -> intArrayOf(Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD)[prefix]
            IrBinaryOp.Sub -> intArrayOf(Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB)[prefix]
            IrBinaryOp.Mul -> intArrayOf(Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL)[prefix]
            IrBinaryOp.Div -> intArrayOf(Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV)[prefix]
            IrBinaryOp.Rem -> intArrayOf(Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM)[prefix]
            IrBinaryOp.And -> if (type == IrI64Type) Opcodes.LAND else Opcodes.IAND
            IrBinaryOp.Or -> if (type == IrI64Type) Opcodes.LOR else Opcodes.IOR
            IrBinaryOp.Xor -> if (type == IrI64Type) Opcodes.LXOR else Opcodes.IXOR
            IrBinaryOp.Shl -> if (type == IrI64Type) Opcodes.LSHL else Opcodes.ISHL
            IrBinaryOp.Shr -> if (type == IrI64Type) Opcodes.LSHR else Opcodes.ISHR
            IrBinaryOp.UShr -> if (type == IrI64Type) Opcodes.LUSHR else Opcodes.IUSHR
        }
    }

    private fun negOpcode(type: IrType): Int {
        return when (type) {
            IrI64Type -> Opcodes.LNEG
            IrF32Type -> Opcodes.FNEG
            IrF64Type -> Opcodes.DNEG
            else -> Opcodes.INEG
        }
    }

    private fun conversionOpcode(from: IrType, to: IrType): Int? {
        if (from == to) return null
        return when (from) {
            IrI32Type, IrI16Type, IrI8Type, IrBoolType, IrCharType -> when (to) {
                IrI64Type -> Opcodes.I2L
                IrF32Type -> Opcodes.I2F
                IrF64Type -> Opcodes.I2D
                IrI8Type -> Opcodes.I2B
                IrI16Type -> Opcodes.I2S
                IrCharType -> Opcodes.I2C
                else -> null
            }
            IrI64Type -> when (to) {
                IrI32Type, IrI16Type, IrI8Type, IrBoolType, IrCharType -> Opcodes.L2I
                IrF32Type -> Opcodes.L2F
                IrF64Type -> Opcodes.L2D
                else -> null
            }
            IrF32Type -> when (to) {
                IrI32Type, IrI16Type, IrI8Type, IrBoolType, IrCharType -> Opcodes.F2I
                IrI64Type -> Opcodes.F2L
                IrF64Type -> Opcodes.F2D
                else -> null
            }
            IrF64Type -> when (to) {
                IrI32Type, IrI16Type, IrI8Type, IrBoolType, IrCharType -> Opcodes.D2I
                IrI64Type -> Opcodes.D2L
                IrF32Type -> Opcodes.D2F
                else -> null
            }
            else -> null
        }
    }

    private fun emitConversion(out: InsnList, instruction: IrConvertInstruction) {
        when (instruction.kind) {
            IrConvertKind.Numeric,
            IrConvertKind.Bitcast -> {
                conversionOpcode(instruction.value.type, instruction.targetType)?.let { out.add(InsnNode(it)) }
            }
            IrConvertKind.ReferenceCast -> {
                out.add(TypeInsnNode(Opcodes.CHECKCAST, checkcastOperand(instruction.targetType)))
            }
        }
    }

    private fun checkcastOperand(type: IrType): String {
        return when (type) {
            is IrRefType -> refInternalName(type)
            is IrArrayType -> descriptor(type)
            IrNullType,
            IrUnknownType -> "java/lang/Object"
            else -> error("CHECKCAST target must be a reference type, got ${type.displayName}")
        }
    }

    private fun loadOpcode(type: IrType): Int {
        return when (type) {
            IrI64Type -> Opcodes.LLOAD
            IrF32Type -> Opcodes.FLOAD
            IrF64Type -> Opcodes.DLOAD
            is IrRefType, is IrArrayType, IrNullType, IrUnknownType -> Opcodes.ALOAD
            else -> Opcodes.ILOAD
        }
    }

    private fun storeOpcode(type: IrType): Int {
        return when (type) {
            IrI64Type -> Opcodes.LSTORE
            IrF32Type -> Opcodes.FSTORE
            IrF64Type -> Opcodes.DSTORE
            is IrRefType, is IrArrayType, IrNullType, IrUnknownType -> Opcodes.ASTORE
            else -> Opcodes.ISTORE
        }
    }

    private fun returnOpcode(type: IrType): Int {
        return when (type) {
            IrVoidType -> Opcodes.RETURN
            IrI64Type -> Opcodes.LRETURN
            IrF32Type -> Opcodes.FRETURN
            IrF64Type -> Opcodes.DRETURN
            is IrRefType, is IrArrayType, IrNullType, IrUnknownType -> Opcodes.ARETURN
            else -> Opcodes.IRETURN
        }
    }

    private fun arrayLoadOpcode(elementType: IrType?, resultType: IrType): Int {
        return when (elementType ?: resultType) {
            IrBoolType,
            IrI8Type -> Opcodes.BALOAD
            IrCharType -> Opcodes.CALOAD
            IrI16Type -> Opcodes.SALOAD
            IrI64Type -> Opcodes.LALOAD
            IrF32Type -> Opcodes.FALOAD
            IrF64Type -> Opcodes.DALOAD
            is IrRefType, is IrArrayType, IrNullType, IrUnknownType -> Opcodes.AALOAD
            else -> Opcodes.IALOAD
        }
    }

    private fun arrayStoreOpcode(elementType: IrType?, valueType: IrType): Int {
        return when (elementType ?: valueType) {
            IrBoolType,
            IrI8Type -> Opcodes.BASTORE
            IrCharType -> Opcodes.CASTORE
            IrI16Type -> Opcodes.SASTORE
            IrI64Type -> Opcodes.LASTORE
            IrF32Type -> Opcodes.FASTORE
            IrF64Type -> Opcodes.DASTORE
            is IrRefType, is IrArrayType, IrNullType, IrUnknownType -> Opcodes.AASTORE
            else -> Opcodes.IASTORE
        }
    }

    private fun arrayElementType(type: IrType): IrType? {
        val arrayType = type as? IrArrayType ?: return null
        return if (arrayType.dimensions == 1) {
            arrayType.elementType
        } else {
            IrArrayType(arrayType.elementType, arrayType.dimensions - 1, arrayType.nullable)
        }
    }

    private fun opcodePrefix(type: IrType): Int {
        return when (type) {
            IrI64Type -> 1
            IrF32Type -> 2
            IrF64Type -> 3
            else -> 0
        }
    }

    private fun stackSize(type: IrType): Int {
        return when (type) {
            IrI64Type, IrF64Type -> 2
            IrVoidType -> 0
            else -> 1
        }
    }

    private fun unquote(text: String): String {
        if (text.length < 2 || text.first() != '"' || text.last() != '"') return text
        val body = text.substring(1, text.length - 1)
        return buildString {
            var index = 0
            while (index < body.length) {
                val char = body[index++]
                if (char == '\\' && index < body.length) {
                    append(
                        when (val escaped = body[index++]) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            '"' -> '"'
                            '\\' -> '\\'
                            else -> escaped
                        }
                    )
                } else {
                    append(char)
                }
            }
        }
    }

    private inner class ExportState(function: IrFunction) {
        private val labels = function.blocks.associateWith { LabelNode() }
        private val endLabels = function.blocks.associateWith { LabelNode() }
        private val exceptionHandlers = function.exceptionRegions.mapTo(mutableSetOf()) { it.handler }
        private val slots = linkedMapOf<IrSsaValue, Int>()
        var nextLocal = 0
            private set

        init {
            for (parameter in function.parameters) {
                slots[parameter] = allocate(parameter.type)
            }
            for (block in function.blocks) {
                for (arg in block.args) {
                    val localIndex = arg.frontendStateIndex?.takeIf { arg.frontendStateName == "local" }
                    if (localIndex != null) {
                        slots[arg] = localIndex
                        reserve(localIndex, arg.type)
                    }
                }
            }
            for (block in function.blocks) {
                for (arg in block.args) {
                    if (arg !in slots) slots[arg] = allocate(arg.type)
                }
            }
            for (block in function.blocks) {
                for (instruction in block.instructions) {
                    instruction.result?.let { slots[it] = allocate(it.type) }
                }
            }
        }

        fun label(block: IrBlock): LabelNode = labels.getValue(block)

        fun endLabel(block: IrBlock): LabelNode = endLabels.getValue(block)

        fun isExceptionHandler(block: IrBlock): Boolean = block in exceptionHandlers

        fun slot(value: IrSsaValue): Int = slots[value] ?: error("No local slot for ${value.id}")

        fun allocateTemp(type: IrType): Int = allocate(type)

        private fun reserve(slot: Int, type: IrType) {
            nextLocal = maxOf(nextLocal, slot + stackSize(type).coerceAtLeast(1))
        }

        private fun allocate(type: IrType): Int {
            val slot = nextLocal
            nextLocal += stackSize(type).coerceAtLeast(1)
            return slot
        }
    }
}

private val IrBlockArg.frontendStateName: String?
    get() = (origin as? IrBlockArgOrigin.FrontendState)?.name

private val IrBlockArg.frontendStateIndex: Int?
    get() = (origin as? IrBlockArgOrigin.FrontendState)?.index
