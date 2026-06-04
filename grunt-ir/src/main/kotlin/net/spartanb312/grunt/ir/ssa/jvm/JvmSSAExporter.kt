package net.spartanb312.grunt.ir.ssa.jvm

import net.spartanb312.grunt.ir.ssa.core.*
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

data class JvmSSAExportOptions(
    val access: Int? = null,
    val signature: String? = null,
    val exceptions: List<String> = emptyList(),
    val computeMaxsPadding: Int = 16
)

class JvmSSAExporter(
    private val metadata: JvmSSAMetadata = JvmSSAMetadata(),
    private val options: JvmSSAExportOptions = JvmSSAExportOptions()
) {
    fun export(function: SSAFunction): MethodNode {
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

    private fun estimateMaxStack(function: SSAFunction): Int {
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

    private fun stateStartsWithException(block: SSABlock, function: SSAFunction): Boolean {
        return function.exceptionRegions.any { it.handler == block }
    }

    private fun estimateMaxStack(instruction: SSAInstruction): Int {
        return when (instruction) {
            is SSAUnaryInstruction -> when {
                instruction.op == SSAUnaryOp.BitNot && instruction.value.type == SSAI64Type ->
                    stackSize(instruction.value.type) + stackSize(SSAI64Type)
                else -> maxOf(stackSize(instruction.value.type), stackSize(instruction.result.type))
            }
            is SSABinaryInstruction -> maxOf(
                stackSize(instruction.lhs.type) + stackSize(instruction.rhs.type),
                stackSize(instruction.result.type)
            )
            is SSACompareInstruction -> maxOf(
                stackSize(instruction.lhs.type) + stackSize(instruction.rhs.type),
                stackSize(instruction.result.type)
            )
            is SSAConvertInstruction -> maxOf(
                stackSize(instruction.value.type),
                stackSize(instruction.targetType),
                stackSize(instruction.result.type)
            )
            is SSALoadFieldInstruction -> {
                val field = fieldMetadata(instruction.field)
                val fieldStack = Type.getType(field.desc).size
                if (field.isStatic) fieldStack else maxOf(stackSizeOrZero(instruction.receiver?.type), fieldStack)
            }
            is SSAStoreFieldInstruction -> stackSizeOrZero(instruction.receiver?.type) + stackSize(instruction.value.type)
            is SSAArrayLoadInstruction -> maxOf(
                stackSize(instruction.array.type) + stackSize(instruction.index.type),
                stackSize(instruction.result.type)
            )
            is SSAArrayStoreInstruction -> stackSize(instruction.array.type) + stackSize(instruction.index.type) + stackSize(
                instruction.value.type
            )
            is SSACallInstruction -> maxOf(sumStackSize(instruction.args), stackSizeOrZero(instruction.result?.type))
            is SSAResolveDynamicValueInstruction -> stackSize(instruction.result.type)
            is SSADynamicCallInstruction -> maxOf(
                sumStackSize(instruction.args),
                stackSizeOrZero(instruction.result?.type)
            )
            is SSAAllocateInstruction -> maxOf(sumStackSize(instruction.args), stackSize(instruction.result.type))
            is SSAIntrinsicInstruction -> estimateMaxStack(instruction)
            is SSABarrierInstruction -> 0
        }
    }

    private fun estimateMaxStack(instruction: SSAIntrinsicInstruction): Int {
        val argsStack = sumStackSize(instruction.args)
        val resultStack = stackSizeOrZero(instruction.result?.type)
        return when {
            instruction.intrinsic.name.startsWith("jvm.opcode.") -> maxOf(argsStack, resultStack, 4)
            else -> maxOf(argsStack, resultStack)
        }
    }

    private fun estimateMaxStack(terminator: SSATerminator): Int {
        return when (terminator) {
            is SSAJumpTerminator -> estimatePassArgsMaxStack(terminator.target)
            is SSABranchTerminator -> maxOf(
                stackSize(terminator.condition.type),
                estimatePassArgsMaxStack(terminator.trueTarget),
                estimatePassArgsMaxStack(terminator.falseTarget)
            )
            is SSASwitchTerminator -> maxOf(
                stackSize(terminator.value.type),
                estimatePassArgsMaxStack(terminator.defaultTarget),
                terminator.cases.maxOfOrNull { estimatePassArgsMaxStack(it.target) } ?: 0
            )
            is SSAReturnTerminator -> stackSizeOrZero(terminator.value?.type)
            is SSAThrowTerminator -> stackSize(terminator.exception.type)
            SSAUnreachableTerminator -> 1
        }
    }

    private fun estimatePassArgsMaxStack(successor: SSASuccessor): Int {
        return successor.args.maxOfOrNull { stackSize(it.type) } ?: 0
    }

    private fun sumStackSize(values: Iterable<SSAValue>): Int {
        return values.sumOf { stackSize(it.type) }
    }

    private fun stackSizeOrZero(type: SSAType?): Int {
        return if (type == null) 0 else stackSize(type)
    }

    private fun emitBlockPrologue(out: InsnList, block: SSABlock, state: ExportState) {
        if (!state.isExceptionHandler(block)) return

        val exceptionArg = block.args
            .filter { it.origin == SSABlockArgOrigin.ExceptionObject || it.frontendStateName == "stack" }
            .minByOrNull { it.frontendStateIndex ?: 0 }
            ?: return
        out.add(VarInsnNode(storeOpcode(exceptionArg.type), state.slot(exceptionArg)))
    }

    private fun emitInstruction(out: InsnList, instruction: SSAInstruction, state: ExportState) {
        when (instruction) {
            is SSAUnaryInstruction -> {
                load(out, instruction.value, state)
                when (instruction.op) {
                    SSAUnaryOp.Neg -> out.add(InsnNode(negOpcode(instruction.result.type)))
                    SSAUnaryOp.BitNot -> emitBitNot(out, instruction.result.type)
                    SSAUnaryOp.LogicalNot -> emitLogicalNot(out)
                }
                storeResult(out, instruction.result, state)
            }
            is SSABinaryInstruction -> {
                load(out, instruction.lhs, state)
                load(out, instruction.rhs, state)
                out.add(InsnNode(binaryOpcode(instruction.op, instruction.result.type)))
                storeResult(out, instruction.result, state)
            }
            is SSACompareInstruction -> emitCompare(out, instruction, state)
            is SSAConvertInstruction -> {
                load(out, instruction.value, state)
                emitConversion(out, instruction)
                storeResult(out, instruction.result, state)
            }
            is SSALoadFieldInstruction -> {
                val field = fieldMetadata(instruction.field)
                if (!field.isStatic) {
                    load(out, instruction.receiver ?: error("Instance field load requires receiver"), state)
                }
                out.add(FieldInsnNode(if (field.isStatic) Opcodes.GETSTATIC else Opcodes.GETFIELD, field.owner, field.name, field.desc))
                storeResult(out, instruction.result, state)
            }
            is SSAStoreFieldInstruction -> {
                val field = fieldMetadata(instruction.field)
                if (!field.isStatic) {
                    load(out, instruction.receiver ?: error("Instance field store requires receiver"), state)
                }
                load(out, instruction.value, state)
                out.add(FieldInsnNode(if (field.isStatic) Opcodes.PUTSTATIC else Opcodes.PUTFIELD, field.owner, field.name, field.desc))
            }
            is SSAArrayLoadInstruction -> {
                load(out, instruction.array, state)
                load(out, instruction.index, state)
                out.add(InsnNode(arrayLoadOpcode(instruction.elementType ?: arrayElementType(instruction.array.type), instruction.result.type)))
                storeResult(out, instruction.result, state)
            }
            is SSAArrayStoreInstruction -> {
                load(out, instruction.array, state)
                load(out, instruction.index, state)
                load(out, instruction.value, state)
                out.add(InsnNode(arrayStoreOpcode(instruction.elementType ?: arrayElementType(instruction.array.type), instruction.value.type)))
            }
            is SSACallInstruction -> emitCall(out, instruction, state)
            is SSAResolveDynamicValueInstruction -> {
                val dynamic = dynamicValueMetadata(instruction.site)
                out.add(LdcInsnNode(constantDynamic(dynamic)))
                storeResult(out, instruction.result, state)
            }
            is SSADynamicCallInstruction -> {
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
            is SSAAllocateInstruction -> emitAllocation(out, instruction, state)
            is SSAIntrinsicInstruction -> emitIntrinsic(out, instruction, state)
            is SSABarrierInstruction -> Unit
        }
    }

    private fun emitTerminator(out: InsnList, terminator: SSATerminator, state: ExportState) {
        when (terminator) {
            is SSAJumpTerminator -> {
                passArgs(out, terminator.target, state)
                out.add(JumpInsnNode(Opcodes.GOTO, state.label(terminator.target.block)))
            }
            is SSABranchTerminator -> {
                val trueLabel = LabelNode()
                load(out, terminator.condition, state)
                out.add(JumpInsnNode(Opcodes.IFNE, trueLabel))
                passArgs(out, terminator.falseTarget, state)
                out.add(JumpInsnNode(Opcodes.GOTO, state.label(terminator.falseTarget.block)))
                out.add(trueLabel)
                passArgs(out, terminator.trueTarget, state)
                out.add(JumpInsnNode(Opcodes.GOTO, state.label(terminator.trueTarget.block)))
            }
            is SSASwitchTerminator -> {
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
            is SSAReturnTerminator -> {
                val value = terminator.value
                if (value == null) {
                    out.add(InsnNode(Opcodes.RETURN))
                } else {
                    load(out, value, state)
                    out.add(InsnNode(returnOpcode(value.type)))
                }
            }
            is SSAThrowTerminator -> {
                load(out, terminator.exception, state)
                out.add(InsnNode(Opcodes.ATHROW))
            }
            SSAUnreachableTerminator -> {
                out.add(InsnNode(Opcodes.ACONST_NULL))
                out.add(InsnNode(Opcodes.ATHROW))
            }
        }
    }

    private fun passArgs(out: InsnList, successor: SSASuccessor, state: ExportState) {
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

    private fun emitCall(out: InsnList, instruction: SSACallInstruction, state: ExportState) {
        val method = methodMetadata(instruction.target)
        instruction.args.forEach { load(out, it, state) }
        out.add(MethodInsnNode(method.opcode, method.owner, method.name, method.desc, method.isInterface))
        instruction.result?.let { storeResult(out, it, state) }
    }

    private fun emitAllocation(out: InsnList, instruction: SSAAllocateInstruction, state: ExportState) {
        when (val allocation = instruction.allocation) {
            is SSAAllocation.Object -> {
                val internalName = refInternalName(allocation.type)
                out.add(TypeInsnNode(Opcodes.NEW, internalName))
                storeResult(out, instruction.result, state)
            }
            is SSAAllocation.Array -> {
                instruction.args.forEach { load(out, it, state) }
                emitNewArray(out, allocation.type)
                storeResult(out, instruction.result, state)
            }
            is SSAAllocation.Opaque -> {
                if (allocation.name == "multianewarray" && allocation.type is SSAArrayType) {
                    instruction.args.forEach { load(out, it, state) }
                    out.add(MultiANewArrayInsnNode(descriptor(allocation.type), instruction.args.size))
                    storeResult(out, instruction.result, state)
                } else {
                    error("Unsupported opaque allocation ${allocation.name}")
                }
            }
        }
    }

    private fun emitIntrinsic(out: InsnList, instruction: SSAIntrinsicInstruction, state: ExportState) {
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

    private fun emitCompare(out: InsnList, instruction: SSACompareInstruction, state: ExportState) {
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

    private fun emitBitNot(out: InsnList, type: SSAType) {
        if (type == SSAI64Type) {
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

    private fun load(out: InsnList, value: SSAValue, state: ExportState) {
        when (value) {
            is SSAStructure -> out.add(VarInsnNode(loadOpcode(value.type), state.slot(value)))
            is SSABoolLiteral -> out.add(InsnNode(if (value.value) Opcodes.ICONST_1 else Opcodes.ICONST_0))
            is SSAIntLiteral -> emitInt(out, value.value, value.type)
            is SSAFloatLiteral -> emitFloat(out, value.value, value.type)
            SSANullLiteral -> out.add(InsnNode(Opcodes.ACONST_NULL))
            is SSAOpaqueLiteral -> emitOpaque(out, value)
        }
    }

    private fun storeResult(out: InsnList, result: SSAInstructionResult, state: ExportState) {
        out.add(VarInsnNode(storeOpcode(result.type), state.slot(result)))
    }

    private fun emitInt(out: InsnList, value: Long, type: SSAIntegerType) {
        if (type == SSAI64Type) {
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

    private fun emitFloat(out: InsnList, value: Double, type: SSAFloatType) {
        if (type == SSAF32Type) {
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

    private fun emitOpaque(out: InsnList, value: SSAOpaqueLiteral) {
        when {
            value.type is SSARefType && value.type.symbol?.name == "java/lang/String" -> {
                out.add(LdcInsnNode(unquote(value.text)))
            }
            value.type is SSARefType && value.type.symbol?.name == "java/lang/Class" -> {
                out.add(LdcInsnNode(Type.getType(value.text)))
            }
            else -> error("Cannot export opaque literal ${value.text} of type ${value.type.displayName}")
        }
    }

    private fun emitNewArray(out: InsnList, type: SSAArrayType) {
        if (type.dimensions != 1) {
            out.add(MultiANewArrayInsnNode(descriptor(type), 1))
            return
        }
        when (type.elementType) {
            SSABoolType -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN))
            SSAI8Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE))
            SSAI16Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_SHORT))
            SSACharType -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_CHAR))
            SSAI32Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT))
            SSAI64Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_LONG))
            SSAF32Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_FLOAT))
            SSAF64Type -> out.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_DOUBLE))
            is SSARefType -> out.add(TypeInsnNode(Opcodes.ANEWARRAY, refInternalName(type.elementType)))
            is SSAArrayType -> out.add(TypeInsnNode(Opcodes.ANEWARRAY, descriptor(type.elementType)))
            else -> error("Unsupported array element type ${type.elementType.displayName}")
        }
    }

    private fun emitExceptionRegions(method: MethodNode, function: SSAFunction, state: ExportState) {
        for (region in function.exceptionRegions) {
            val protected = region.protectedBlocks.sortedBy { function.blocks.indexOf(it) }
            val first = protected.firstOrNull() ?: continue
            val last = protected.last()
            method.tryCatchBlocks.add(
                TryCatchBlockNode(
                    state.label(first),
                    state.endLabel(last),
                    state.label(region.handler),
                    (region.caughtType as? SSARefType)?.symbol?.name
                )
            )
        }
    }

    private fun fieldMetadata(field: SSAFieldRef): JvmFieldMetadata {
        return when (field) {
            is SSAExternalFieldRef -> metadata.fields[field.ref] ?: parseFieldRef(field.ref.debugName, field.isStatic)
            is SSASymbolFieldRef -> {
                val owner = field.symbol.owner?.name ?: error("Symbol field ${field.symbol.name} has no JVM owner")
                JvmFieldMetadata(owner, field.symbol.name, descriptor(field.type), field.isStatic)
            }
        }
    }

    private fun methodMetadata(callable: SSACallableRef): JvmMethodMetadata {
        return when (callable) {
            is SSAExternalFunctionRef -> metadata.methods[callable.ref] ?: parseMethodRef(callable.ref.debugName, callable)
            is SSAFunctionRef -> parseMethodRef(callable.symbol.name, callable)
            is SSAIntrinsicRef -> error("Intrinsic ${callable.name} is not a JVM method call")
        }
    }

    private fun dynamicValueMetadata(site: SSADynamicValueSite): JvmDynamicValueMetadata {
        val ref = site.externalRef ?: error("Dynamic value ${site.id} has no JVM external ref")
        return metadata.dynamicValues[ref] ?: error("Missing JVM metadata for dynamic value $ref")
    }

    private fun dynamicCallMetadata(site: SSADynamicCallSite): JvmDynamicCallMetadata {
        val ref = site.externalRef ?: error("Dynamic call ${site.id} has no JVM external ref")
        return metadata.dynamicCalls[ref] ?: error("Missing JVM metadata for dynamic call $ref")
    }

    private fun parseFieldRef(debugName: String?, isStatic: Boolean): JvmFieldMetadata {
        val text = debugName ?: error("Missing JVM field metadata")
        val colon = text.lastIndexOf(':')
        val dot = text.lastIndexOf('.', colon)
        return JvmFieldMetadata(text.substring(0, dot), text.substring(dot + 1, colon), text.substring(colon + 1), isStatic)
    }

    private fun parseMethodRef(debugName: String?, callable: SSACallableRef): JvmMethodMetadata {
        val text = debugName ?: error("Missing JVM method metadata")
        val descStart = text.indexOf('(')
        val dot = text.lastIndexOf('.', descStart)
        val owner = text.substring(0, dot)
        val name = text.substring(dot + 1, descStart)
        val desc = text.substring(descStart)
        val opcode = when {
            callable is SSAExternalFunctionRef && callable.parameterTypes.size == Type.getArgumentTypes(desc).size -> Opcodes.INVOKESTATIC
            callable is SSAFunctionRef && callable.parameterTypes.size == Type.getArgumentTypes(desc).size -> Opcodes.INVOKESTATIC
            else -> Opcodes.INVOKEVIRTUAL
        }
        return JvmMethodMetadata(opcode, owner, name, desc, opcode == Opcodes.INVOKEINTERFACE)
    }

    private fun methodDescriptor(function: SSAFunction): String {
        val name = function.symbol.name
        val descStart = name.indexOf('(')
        return if (descStart >= 0) name.substring(descStart) else Type.getMethodDescriptor(
            Type.getType(descriptor(function.returnType)),
            *function.symbol.parameterTypes.map { Type.getType(descriptor(it)) }.toTypedArray()
        )
    }

    private fun methodName(function: SSAFunction): String {
        val name = function.symbol.name
        val descStart = name.indexOf('(')
        if (descStart < 0) return name.substringAfterLast('.')
        val dot = name.lastIndexOf('.', descStart)
        return if (dot >= 0) name.substring(dot + 1, descStart) else name.substring(0, descStart)
    }

    private fun inferredAccess(function: SSAFunction, descriptor: String): Int {
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

    private fun descriptor(type: SSAType): String {
        return when (type) {
            SSABoolType -> "Z"
            SSAI8Type -> "B"
            SSAI16Type -> "S"
            SSACharType -> "C"
            SSAI32Type -> "I"
            SSAI64Type -> "J"
            SSAF32Type -> "F"
            SSAF64Type -> "D"
            SSAVoidType -> "V"
            SSANullType -> "Ljava/lang/Object;"
            SSAUnknownType -> "Ljava/lang/Object;"
            is SSARefType -> "L${refInternalName(type)};"
            is SSAArrayType -> "[".repeat(type.dimensions) + descriptor(type.elementType)
            is SSAOpaqueType -> type.displayName
        }
    }

    private fun refInternalName(type: SSARefType): String {
        return type.symbol?.name ?: "java/lang/Object"
    }

    private fun compareOpcode(predicate: SSAComparePredicate, lhsType: SSAType): Int {
        val ref = SSATypes.isReference(lhsType)
        return when (predicate) {
            SSAComparePredicate.Eq -> if (ref) Opcodes.IF_ACMPEQ else Opcodes.IF_ICMPEQ
            SSAComparePredicate.Ne -> if (ref) Opcodes.IF_ACMPNE else Opcodes.IF_ICMPNE
            SSAComparePredicate.Lt -> Opcodes.IF_ICMPLT
            SSAComparePredicate.Le -> Opcodes.IF_ICMPLE
            SSAComparePredicate.Gt -> Opcodes.IF_ICMPGT
            SSAComparePredicate.Ge -> Opcodes.IF_ICMPGE
            SSAComparePredicate.RefEq -> Opcodes.IF_ACMPEQ
            SSAComparePredicate.RefNe -> Opcodes.IF_ACMPNE
        }
    }

    private fun binaryOpcode(op: SSABinaryOp, type: SSAType): Int {
        val prefix = opcodePrefix(type)
        return when (op) {
            SSABinaryOp.Add -> intArrayOf(Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD)[prefix]
            SSABinaryOp.Sub -> intArrayOf(Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB)[prefix]
            SSABinaryOp.Mul -> intArrayOf(Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL)[prefix]
            SSABinaryOp.Div -> intArrayOf(Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV)[prefix]
            SSABinaryOp.Rem -> intArrayOf(Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM)[prefix]
            SSABinaryOp.And -> if (type == SSAI64Type) Opcodes.LAND else Opcodes.IAND
            SSABinaryOp.Or -> if (type == SSAI64Type) Opcodes.LOR else Opcodes.IOR
            SSABinaryOp.Xor -> if (type == SSAI64Type) Opcodes.LXOR else Opcodes.IXOR
            SSABinaryOp.Shl -> if (type == SSAI64Type) Opcodes.LSHL else Opcodes.ISHL
            SSABinaryOp.Shr -> if (type == SSAI64Type) Opcodes.LSHR else Opcodes.ISHR
            SSABinaryOp.UShr -> if (type == SSAI64Type) Opcodes.LUSHR else Opcodes.IUSHR
        }
    }

    private fun negOpcode(type: SSAType): Int {
        return when (type) {
            SSAI64Type -> Opcodes.LNEG
            SSAF32Type -> Opcodes.FNEG
            SSAF64Type -> Opcodes.DNEG
            else -> Opcodes.INEG
        }
    }

    private fun conversionOpcode(from: SSAType, to: SSAType): Int? {
        if (from == to) return null
        return when (from) {
            SSAI32Type, SSAI16Type, SSAI8Type, SSABoolType, SSACharType -> when (to) {
                SSAI64Type -> Opcodes.I2L
                SSAF32Type -> Opcodes.I2F
                SSAF64Type -> Opcodes.I2D
                SSAI8Type -> Opcodes.I2B
                SSAI16Type -> Opcodes.I2S
                SSACharType -> Opcodes.I2C
                else -> null
            }
            SSAI64Type -> when (to) {
                SSAI32Type, SSAI16Type, SSAI8Type, SSABoolType, SSACharType -> Opcodes.L2I
                SSAF32Type -> Opcodes.L2F
                SSAF64Type -> Opcodes.L2D
                else -> null
            }
            SSAF32Type -> when (to) {
                SSAI32Type, SSAI16Type, SSAI8Type, SSABoolType, SSACharType -> Opcodes.F2I
                SSAI64Type -> Opcodes.F2L
                SSAF64Type -> Opcodes.F2D
                else -> null
            }
            SSAF64Type -> when (to) {
                SSAI32Type, SSAI16Type, SSAI8Type, SSABoolType, SSACharType -> Opcodes.D2I
                SSAI64Type -> Opcodes.D2L
                SSAF32Type -> Opcodes.D2F
                else -> null
            }
            else -> null
        }
    }

    private fun emitConversion(out: InsnList, instruction: SSAConvertInstruction) {
        when (instruction.kind) {
            SSAConvertKind.Numeric,
            SSAConvertKind.Bitcast -> {
                conversionOpcode(instruction.value.type, instruction.targetType)?.let { out.add(InsnNode(it)) }
            }
            SSAConvertKind.ReferenceCast -> {
                out.add(TypeInsnNode(Opcodes.CHECKCAST, checkcastOperand(instruction.targetType)))
            }
        }
    }

    private fun checkcastOperand(type: SSAType): String {
        return when (type) {
            is SSARefType -> refInternalName(type)
            is SSAArrayType -> descriptor(type)
            SSANullType,
            SSAUnknownType -> "java/lang/Object"
            else -> error("CHECKCAST target must be a reference type, got ${type.displayName}")
        }
    }

    private fun loadOpcode(type: SSAType): Int {
        return when (type) {
            SSAI64Type -> Opcodes.LLOAD
            SSAF32Type -> Opcodes.FLOAD
            SSAF64Type -> Opcodes.DLOAD
            is SSARefType, is SSAArrayType, SSANullType, SSAUnknownType -> Opcodes.ALOAD
            else -> Opcodes.ILOAD
        }
    }

    private fun storeOpcode(type: SSAType): Int {
        return when (type) {
            SSAI64Type -> Opcodes.LSTORE
            SSAF32Type -> Opcodes.FSTORE
            SSAF64Type -> Opcodes.DSTORE
            is SSARefType, is SSAArrayType, SSANullType, SSAUnknownType -> Opcodes.ASTORE
            else -> Opcodes.ISTORE
        }
    }

    private fun returnOpcode(type: SSAType): Int {
        return when (type) {
            SSAVoidType -> Opcodes.RETURN
            SSAI64Type -> Opcodes.LRETURN
            SSAF32Type -> Opcodes.FRETURN
            SSAF64Type -> Opcodes.DRETURN
            is SSARefType, is SSAArrayType, SSANullType, SSAUnknownType -> Opcodes.ARETURN
            else -> Opcodes.IRETURN
        }
    }

    private fun arrayLoadOpcode(elementType: SSAType?, resultType: SSAType): Int {
        return when (elementType ?: resultType) {
            SSABoolType,
            SSAI8Type -> Opcodes.BALOAD
            SSACharType -> Opcodes.CALOAD
            SSAI16Type -> Opcodes.SALOAD
            SSAI64Type -> Opcodes.LALOAD
            SSAF32Type -> Opcodes.FALOAD
            SSAF64Type -> Opcodes.DALOAD
            is SSARefType, is SSAArrayType, SSANullType, SSAUnknownType -> Opcodes.AALOAD
            else -> Opcodes.IALOAD
        }
    }

    private fun arrayStoreOpcode(elementType: SSAType?, valueType: SSAType): Int {
        return when (elementType ?: valueType) {
            SSABoolType,
            SSAI8Type -> Opcodes.BASTORE
            SSACharType -> Opcodes.CASTORE
            SSAI16Type -> Opcodes.SASTORE
            SSAI64Type -> Opcodes.LASTORE
            SSAF32Type -> Opcodes.FASTORE
            SSAF64Type -> Opcodes.DASTORE
            is SSARefType, is SSAArrayType, SSANullType, SSAUnknownType -> Opcodes.AASTORE
            else -> Opcodes.IASTORE
        }
    }

    private fun arrayElementType(type: SSAType): SSAType? {
        val arrayType = type as? SSAArrayType ?: return null
        return if (arrayType.dimensions == 1) {
            arrayType.elementType
        } else {
            SSAArrayType(arrayType.elementType, arrayType.dimensions - 1, arrayType.nullable)
        }
    }

    private fun opcodePrefix(type: SSAType): Int {
        return when (type) {
            SSAI64Type -> 1
            SSAF32Type -> 2
            SSAF64Type -> 3
            else -> 0
        }
    }

    private fun stackSize(type: SSAType): Int {
        return when (type) {
            SSAI64Type, SSAF64Type -> 2
            SSAVoidType -> 0
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

    private inner class ExportState(function: SSAFunction) {
        private val labels = function.blocks.associateWith { LabelNode() }
        private val endLabels = function.blocks.associateWith { LabelNode() }
        private val exceptionHandlers = function.exceptionRegions.mapTo(mutableSetOf()) { it.handler }
        private val slots = linkedMapOf<SSAStructure, Int>()
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

        fun label(block: SSABlock): LabelNode = labels.getValue(block)

        fun endLabel(block: SSABlock): LabelNode = endLabels.getValue(block)

        fun isExceptionHandler(block: SSABlock): Boolean = block in exceptionHandlers

        fun slot(value: SSAStructure): Int = slots[value] ?: error("No local slot for ${value.id}")

        fun allocateTemp(type: SSAType): Int = allocate(type)

        private fun reserve(slot: Int, type: SSAType) {
            nextLocal = maxOf(nextLocal, slot + stackSize(type).coerceAtLeast(1))
        }

        private fun allocate(type: SSAType): Int {
            val slot = nextLocal
            nextLocal += stackSize(type).coerceAtLeast(1)
            return slot
        }
    }
}

private val SSABlockArg.frontendStateName: String?
    get() = (origin as? SSABlockArgOrigin.FrontendState)?.name

private val SSABlockArg.frontendStateIndex: Int?
    get() = (origin as? SSABlockArgOrigin.FrontendState)?.index
