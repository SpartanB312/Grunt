package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunt.ir.ssa.core.SSABinaryInstruction
import net.spartanb312.grunt.ir.ssa.core.SSABinaryOp
import net.spartanb312.grunt.ir.ssa.core.SSABlock
import net.spartanb312.grunt.ir.ssa.core.SSABlockArg
import net.spartanb312.grunt.ir.ssa.core.SSABoolLiteral
import net.spartanb312.grunt.ir.ssa.core.SSABoolType
import net.spartanb312.grunt.ir.ssa.core.SSABranchTerminator
import net.spartanb312.grunt.ir.ssa.core.SSACallInstruction
import net.spartanb312.grunt.ir.ssa.core.SSACharType
import net.spartanb312.grunt.ir.ssa.core.SSACompareInstruction
import net.spartanb312.grunt.ir.ssa.core.SSAComparePredicate
import net.spartanb312.grunt.ir.ssa.core.SSAConvertInstruction
import net.spartanb312.grunt.ir.ssa.core.SSAConvertKind
import net.spartanb312.grunt.ir.ssa.core.SSAExternalFunctionRef
import net.spartanb312.grunt.ir.ssa.core.SSAF32Type
import net.spartanb312.grunt.ir.ssa.core.SSAF64Type
import net.spartanb312.grunt.ir.ssa.core.SSAFloatLiteral
import net.spartanb312.grunt.ir.ssa.core.SSAFloatType
import net.spartanb312.grunt.ir.ssa.core.SSAI16Type
import net.spartanb312.grunt.ir.ssa.core.SSAI32Type
import net.spartanb312.grunt.ir.ssa.core.SSAI64Type
import net.spartanb312.grunt.ir.ssa.core.SSAI8Type
import net.spartanb312.grunt.ir.ssa.core.SSAIntrinsicInstruction
import net.spartanb312.grunt.ir.ssa.core.SSAIntLiteral
import net.spartanb312.grunt.ir.ssa.core.SSAIntegerType
import net.spartanb312.grunt.ir.ssa.core.SSAInstruction
import net.spartanb312.grunt.ir.ssa.core.SSAInstructionResult
import net.spartanb312.grunt.ir.ssa.core.SSAJumpTerminator
import net.spartanb312.grunt.ir.ssa.core.SSAParameter
import net.spartanb312.grunt.ir.ssa.core.SSAReturnTerminator
import net.spartanb312.grunt.ir.ssa.core.SSASuccessor
import net.spartanb312.grunt.ir.ssa.core.SSASwitchTerminator
import net.spartanb312.grunt.ir.ssa.core.SSATerminator
import net.spartanb312.grunt.ir.ssa.core.SSAType
import net.spartanb312.grunt.ir.ssa.core.SSAUnaryInstruction
import net.spartanb312.grunt.ir.ssa.core.SSAUnaryOp
import net.spartanb312.grunt.ir.ssa.core.SSAUnreachableTerminator
import net.spartanb312.grunt.ir.ssa.core.SSAValue
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAMetadata
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmMethodIr
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode
import java.util.Locale

internal object NativeSsaIntMethodTranslator {
    private val IntegerRotateLeftKey = NativeJvmIntrinsicKey(
        Opcodes.INVOKESTATIC,
        "java/lang/Integer",
        "rotateLeft",
        "(II)I"
    )

    fun validate(methodNode: MethodNode, ir: NativeJvmMethodIr) {
        translate(methodNode, ir, "grt_validate", intrinsicStats = null)
    }

    fun translate(
        method: NativeValidatedMethod,
        functionName: String,
        intrinsicStats: NativeJvmIntrinsicStats? = null
    ): String {
        return translate(method.methodNode, method.jvmIr, functionName, intrinsicStats)
    }

    private fun translate(
        methodNode: MethodNode,
        ir: NativeJvmMethodIr,
        functionName: String,
        intrinsicStats: NativeJvmIntrinsicStats?
    ): String {
        val argumentTypes = Type.getArgumentTypes(methodNode.desc)
        val returnType = Type.getReturnType(methodNode.desc)
        if (methodNode.access and Opcodes.ACC_STATIC == 0 ||
            argumentTypes.any { !it.isPrimitiveScalar() } ||
            !returnType.isPrimitiveScalar()
        ) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedDescriptor,
                "SSA primitive lowering accepts static primitive scalar helpers; descriptor=${methodNode.desc}"
            )
        }

        val overlay = ir.ssaOverlay ?: throw UnsupportedNativeInstruction(
            NativeSkipReason.UnsupportedInstruction,
            "SSA overlay is unavailable for primitive int lowering"
        )
        val function = overlay.function
        if (function.exceptionRegions.isNotEmpty()) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.TryCatch,
                "SSA primitive int lowering does not handle exception regions"
            )
        }
        if (function.parameters.size != argumentTypes.size) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedDescriptor,
                "SSA primitive lowering parameter count does not match descriptor"
            )
        }
        function.parameters.zip(argumentTypes).forEach { (parameter, type) ->
            if (!parameter.type.matchesJvmPrimitive(type)) {
                throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedDescriptor,
                    "SSA primitive lowering requires int/long parameter types to match descriptor"
                )
            }
        }
        if (!function.returnType.matchesJvmPrimitive(returnType)) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedDescriptor,
                "SSA primitive lowering requires int/long return type to match descriptor"
            )
        }

        val blocks = reachableBlocks(function.entry)
        validateBlocks(blocks)

        val environment = linkedMapOf<SSAValue, String>()
        function.parameters.forEachIndexed { index, parameter ->
            environment[parameter] = argumentExpression(parameter.type, "arg$index")
        }
        blocks.forEach { block ->
            block.args.forEach { arg ->
                environment[arg] = blockArgVariable(arg)
            }
            block.instructions.forEach { instruction ->
                instruction.result?.let { result ->
                    environment[result] = resultVariable(result)
                }
            }
        }

        return buildString {
            append("static ")
                .append(jniType(returnType))
                .append(" JNICALL ")
                .append(functionName)
                .append("(JNIEnv*, jclass")
            argumentTypes.forEachIndexed { index, type ->
                append(", ")
                    .append(jniType(type))
                    .append(" arg")
                    .append(index)
            }
            appendLine(") {")

            blocks.forEach { block ->
                block.args.forEach { arg ->
                    append("    ")
                        .append(unsignedType(arg.type))
                        .append(' ')
                        .append(blockArgVariable(arg))
                        .append(" = ")
                        .append(zeroLiteral(arg.type))
                        .appendLine(";")
                }
                block.instructions.forEach { instruction ->
                    instruction.result?.let { result ->
                        append("    ")
                            .append(unsignedType(result.type))
                            .append(' ')
                            .append(resultVariable(result))
                            .append(" = ")
                            .append(zeroLiteral(result.type))
                            .appendLine(";")
                    }
                }
            }

            appendLine("    goto ${label(function.entry)};")
            blocks.forEach { block ->
                appendLine("${label(block)}:")
                appendLine("{")
                block.instructions.forEach { instruction ->
                    val result = instruction.result ?: unsupportedInstruction(instruction, "instruction has no SSA result")
                    val value = expression(instruction, environment, overlay.metadata, intrinsicStats)
                    append("    ")
                        .append(resultVariable(result))
                        .append(" = ")
                        .append(value)
                        .appendLine(";")
                }
                emitTerminator(block, block.terminator, environment, overlay.metadata)
                appendLine("}")
            }

            appendLine("}")
        }
    }

    private fun reachableBlocks(entry: SSABlock): List<SSABlock> {
        val visited = linkedSetOf<SSABlock>()
        fun visit(block: SSABlock) {
            if (!visited.add(block)) return
            block.terminator.successors.forEach { visit(it.block) }
        }
        visit(entry)
        return visited.toList()
    }

    private fun validateBlocks(blocks: List<SSABlock>) {
        blocks.forEach { block ->
            block.args.forEach { arg ->
                if (!arg.type.isPrimitiveScalar()) {
                    throw UnsupportedNativeInstruction(
                            NativeSkipReason.UnsupportedDescriptor,
                            "SSA primitive lowering requires primitive scalar block arguments"
                    )
                }
            }
            block.instructions.forEach { instruction ->
                val result = instruction.result
                if (result != null && !result.type.isPrimitiveScalar()) {
                    unsupportedInstruction(instruction, "SSA instruction result is not primitive scalar")
                }
            }
            when (val terminator = block.terminator) {
                is SSAJumpTerminator -> validateSuccessor(terminator.target)
                is SSABranchTerminator -> {
                    validateSuccessor(terminator.trueTarget)
                    validateSuccessor(terminator.falseTarget)
                }
                is SSASwitchTerminator -> {
                    if (!terminator.value.type.isI32Like()) {
                        throw UnsupportedNativeInstruction(
                            NativeSkipReason.UnsupportedDescriptor,
                            "SSA primitive int switch requires an i32-like switch value"
                        )
                    }
                    terminator.cases.forEach { case ->
                        if (case.key < Int.MIN_VALUE.toLong() || case.key > Int.MAX_VALUE.toLong()) {
                            throw UnsupportedNativeInstruction(
                                NativeSkipReason.UnsupportedInstruction,
                                "SSA primitive int switch key is outside JVM int range: ${case.key}"
                            )
                        }
                        validateSuccessor(case.target)
                    }
                    validateSuccessor(terminator.defaultTarget)
                }
                is SSAReturnTerminator -> {
                    val value = terminator.value
                    if (value == null || !value.type.isPrimitiveScalar()) {
                        throw UnsupportedNativeInstruction(
                            NativeSkipReason.UnsupportedDescriptor,
                            "SSA primitive lowering requires primitive scalar return values"
                        )
                    }
                }
                is SSAUnreachableTerminator -> throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedInstruction,
                    "SSA primitive int lowering does not support reachable unreachable terminators"
                )
                else -> throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedInstruction,
                    "SSA primitive int lowering does not support ${terminator::class.simpleName}"
                )
            }
        }
    }

    private fun validateSuccessor(successor: SSASuccessor) {
        if (successor.args.size != successor.block.args.size) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "SSA successor argument count does not match target block arguments"
            )
        }
        if (successor.args.any { !it.type.isPrimitiveScalar() }) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedDescriptor,
                "SSA primitive lowering requires primitive scalar successor arguments"
            )
        }
    }

    private fun StringBuilder.emitTerminator(
        block: SSABlock,
        terminator: SSATerminator,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata
    ) {
        when (terminator) {
            is SSAJumpTerminator -> emitSuccessor(block, edgeIndex = 0, terminator.target, environment, metadata)
            is SSABranchTerminator -> {
                val condition = expression(terminator.condition, environment, metadata)
                append("    if ((")
                    .append(condition)
                    .appendLine(") != 0u) {")
                emitSuccessor(block, edgeIndex = 0, terminator.trueTarget, environment, metadata, indent = "        ")
                appendLine("    } else {")
                emitSuccessor(block, edgeIndex = 1, terminator.falseTarget, environment, metadata, indent = "        ")
                appendLine("    }")
            }
            is SSASwitchTerminator -> {
                val value = expression(terminator.value, environment, metadata)
                append("    switch (")
                    .append(value)
                    .appendLine(") {")
                terminator.cases.forEachIndexed { index, case ->
                    append("        case ")
                        .append(intLiteral(case.key.toInt()))
                        .appendLine(": {")
                    emitSuccessor(block, edgeIndex = index, case.target, environment, metadata, indent = "            ")
                    appendLine("        }")
                }
                appendLine("        default: {")
                emitSuccessor(
                    block,
                    edgeIndex = terminator.cases.size,
                    terminator.defaultTarget,
                    environment,
                    metadata,
                    indent = "            "
                )
                appendLine("        }")
                appendLine("    }")
            }
            is SSAReturnTerminator -> {
                val value = terminator.value ?: throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedDescriptor,
                    "SSA primitive lowering requires a primitive scalar return value"
                )
                append("    return ")
                    .append(returnExpression(value.type, expression(value, environment, metadata)))
                    .appendLine(";")
            }
            else -> throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "unsupported SSA terminator ${terminator::class.simpleName}"
            )
        }
    }

    private fun StringBuilder.emitSuccessor(
        source: SSABlock,
        edgeIndex: Int,
        successor: SSASuccessor,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata,
        indent: String = "    "
    ) {
        successor.args.forEachIndexed { index, value ->
            append(indent)
                .append(unsignedType(value.type))
                .append(" edge_")
                .append(source.id.value)
                .append('_')
                .append(edgeIndex)
                .append('_')
                .append(index)
                .append(" = ")
                .append(expression(value, environment, metadata))
                .appendLine(";")
        }
        successor.block.args.forEachIndexed { index, arg ->
            append(indent)
                .append(blockArgVariable(arg))
                .append(" = edge_")
                .append(source.id.value)
                .append('_')
                .append(edgeIndex)
                .append('_')
                .append(index)
                .appendLine(";")
        }
        append(indent)
            .append("goto ")
            .append(label(successor.block))
            .appendLine(";")
    }

    private fun label(block: SSABlock): String = "L_SSA_${block.id.value}"

    private fun blockArgVariable(arg: SSABlockArg): String = "barg_${arg.id.value}"

    private fun resultVariable(result: SSAInstructionResult): String = "v${result.id.value}"

    private fun argumentExpression(type: SSAType, argument: String): String {
        return when (type) {
            SSAI64Type -> "static_cast<uint64_t>($argument)"
            SSAF32Type,
            SSAF64Type -> argument
            else -> "static_cast<uint32_t>($argument)"
        }
    }

    private fun returnExpression(type: SSAType, expression: String): String {
        return when (type) {
            SSAI64Type -> "grt_i64($expression)"
            SSAF32Type -> "static_cast<jfloat>($expression)"
            SSAF64Type -> "static_cast<jdouble>($expression)"
            else -> "grt_i32($expression)"
        }
    }

    private fun unsignedType(type: SSAType): String {
        return when (type) {
            SSAI64Type -> "uint64_t"
            SSAF32Type -> "jfloat"
            SSAF64Type -> "jdouble"
            else -> "uint32_t"
        }
    }

    private fun zeroLiteral(type: SSAType): String {
        return when (type) {
            SSAI64Type -> "0ULL"
            SSAF32Type -> "0.0f"
            SSAF64Type -> "0.0"
            else -> "0u"
        }
    }

    private fun jniType(type: Type): String {
        return when (type.sort) {
            Type.INT -> "jint"
            Type.LONG -> "jlong"
            Type.FLOAT -> "jfloat"
            Type.DOUBLE -> "jdouble"
            else -> throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedDescriptor,
                "SSA primitive lowering accepts only primitive scalar descriptors"
            )
        }
    }

    private fun Type.isPrimitiveScalar(): Boolean {
        return sort == Type.INT || sort == Type.LONG || sort == Type.FLOAT || sort == Type.DOUBLE
    }

    private fun SSAType.matchesJvmPrimitive(type: Type): Boolean {
        return when (type.sort) {
            Type.INT -> isI32Like()
            Type.LONG -> this == SSAI64Type
            Type.FLOAT -> this == SSAF32Type
            Type.DOUBLE -> this == SSAF64Type
            else -> false
        }
    }

    private fun expression(
        value: SSAValue,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata
    ): String {
        return when (value) {
            is SSAIntLiteral -> integerLiteral(value.value, value.type)
            is SSAFloatLiteral -> floatLiteral(value.value, value.type)
            is SSABoolLiteral -> if (value.value) "1u" else "0u"
            is SSAParameter,
            is SSABlockArg,
            is SSAInstructionResult -> environment[value]
                ?: throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedInstruction,
                    "SSA value $value is not available in primitive int lowering"
                )
            else -> throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "unsupported SSA value ${value::class.simpleName}"
            )
        }
    }

    private fun expression(
        instruction: SSAInstruction,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata,
        intrinsicStats: NativeJvmIntrinsicStats?
    ): String {
        val allowedFloatingArithmetic = instruction is SSABinaryInstruction &&
            instruction.result.type.isFloatScalar() &&
            (instruction.op == SSABinaryOp.Div || instruction.op == SSABinaryOp.Rem)
        if (!allowedFloatingArithmetic && (instruction.effect.mayThrow ||
            instruction.effect.readsMemory ||
            instruction.effect.writesMemory ||
            instruction.effect.readsExternalState ||
            instruction.effect.writesExternalState ||
            instruction.effect.callsExternal ||
            instruction.effect.resolvesExternal ||
            instruction.effect.isBarrier)
        ) {
            val call = instruction as? SSACallInstruction
            if (call == null || !call.isIntegerRotateLeft(metadata)) {
                unsupportedInstruction(instruction, "SSA instruction has effects outside pure primitive lowering")
            }
        }

        return when (instruction) {
            is SSABinaryInstruction -> binaryExpression(instruction, environment, metadata)
            is SSACompareInstruction -> compareExpression(instruction, environment, metadata)
            is SSAUnaryInstruction -> unaryExpression(instruction, environment, metadata)
            is SSAConvertInstruction -> convertExpression(instruction, environment, metadata)
            is SSACallInstruction -> callExpression(instruction, environment, metadata, intrinsicStats)
            is SSAIntrinsicInstruction -> intrinsicExpression(instruction, environment, metadata)
            else -> unsupportedInstruction(instruction, "unsupported SSA instruction ${instruction::class.simpleName}")
        }
    }

    private fun binaryExpression(
        instruction: SSABinaryInstruction,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata
    ): String {
        if (!instruction.result.type.isPrimitiveScalar()) {
            unsupportedInstruction(instruction, "SSA binary result is not a primitive scalar")
        }
        val lhs = expression(instruction.lhs, environment, metadata)
        val rhs = expression(instruction.rhs, environment, metadata)
        if (instruction.result.type.isFloatScalar()) {
            return when (instruction.op) {
                SSABinaryOp.Add -> "(($lhs) + ($rhs))"
                SSABinaryOp.Sub -> "(($lhs) - ($rhs))"
                SSABinaryOp.Mul -> "(($lhs) * ($rhs))"
                SSABinaryOp.Div -> "(($lhs) / ($rhs))"
                SSABinaryOp.Rem -> if (instruction.result.type == SSAF32Type) {
                    "static_cast<jfloat>(std::fmod(($lhs), ($rhs)))"
                } else {
                    "std::fmod(($lhs), ($rhs))"
                }
                else -> unsupportedInstruction(instruction, "floating-point binary op ${instruction.op} is unsupported")
            }
        }
        val longResult = instruction.result.type == SSAI64Type
        val shiftMask = if (longResult) "63u" else "31u"
        return when (instruction.op) {
            SSABinaryOp.Add -> "(($lhs) + ($rhs))"
            SSABinaryOp.Sub -> "(($lhs) - ($rhs))"
            SSABinaryOp.Mul -> "(($lhs) * ($rhs))"
            SSABinaryOp.And -> "(($lhs) & ($rhs))"
            SSABinaryOp.Or -> "(($lhs) | ($rhs))"
            SSABinaryOp.Xor -> "(($lhs) ^ ($rhs))"
            SSABinaryOp.Shl -> "(($lhs) << (($rhs) & $shiftMask))"
            SSABinaryOp.Shr -> if (longResult) {
                "grt_lshr64_bits(($lhs), static_cast<uint32_t>($rhs))"
            } else {
                "grt_ishr32_bits(($lhs), ($rhs))"
            }
            SSABinaryOp.UShr -> "(($lhs) >> (($rhs) & $shiftMask))"
            SSABinaryOp.Div,
            SSABinaryOp.Rem -> unsupportedInstruction(instruction, "trapping integer binary op is not in the SSA primitive subset")
        }
    }

    private fun compareExpression(
        instruction: SSACompareInstruction,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata
    ): String {
        if (instruction.predicate == SSAComparePredicate.RefEq ||
            instruction.predicate == SSAComparePredicate.RefNe
        ) {
            unsupportedInstruction(instruction, "reference compare is not in the SSA primitive int subset")
        }
        if (!instruction.lhs.type.isPrimitiveIntLike() ||
            !instruction.rhs.type.isPrimitiveIntLike() ||
            instruction.lhs.type.isWide() != instruction.rhs.type.isWide()
        ) {
            unsupportedInstruction(instruction, "SSA primitive compare requires matching int/long operands")
        }
        val lhs = expression(instruction.lhs, environment, metadata)
        val rhs = expression(instruction.rhs, environment, metadata)
        val signed = if (instruction.lhs.type.isWide()) "grt_i64" else "grt_i32"
        val predicate = when (instruction.predicate) {
            SSAComparePredicate.Eq -> "(($lhs) == ($rhs))"
            SSAComparePredicate.Ne -> "(($lhs) != ($rhs))"
            SSAComparePredicate.Lt -> "($signed($lhs) < $signed($rhs))"
            SSAComparePredicate.Le -> "($signed($lhs) <= $signed($rhs))"
            SSAComparePredicate.Gt -> "($signed($lhs) > $signed($rhs))"
            SSAComparePredicate.Ge -> "($signed($lhs) >= $signed($rhs))"
            SSAComparePredicate.RefEq,
            SSAComparePredicate.RefNe -> error("handled above")
        }
        return "($predicate ? 1u : 0u)"
    }

    private fun unaryExpression(
        instruction: SSAUnaryInstruction,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata
    ): String {
        if (!instruction.result.type.isPrimitiveScalar()) {
            unsupportedInstruction(instruction, "SSA unary result is not a primitive scalar")
        }
        val value = expression(instruction.value, environment, metadata)
        return when (instruction.op) {
            SSAUnaryOp.Neg -> if (instruction.result.type.isFloatScalar()) {
                "(-($value))"
            } else {
                "(${zeroLiteral(instruction.result.type)} - ($value))"
            }
            SSAUnaryOp.BitNot -> if (instruction.result.type.isFloatScalar()) {
                unsupportedInstruction(instruction, "floating-point bit-not is unsupported")
            } else {
                "(~($value))"
            }
            SSAUnaryOp.LogicalNot -> "(($value) == 0u ? 1u : 0u)"
        }
    }

    private fun convertExpression(
        instruction: SSAConvertInstruction,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata
    ): String {
        if (instruction.kind != SSAConvertKind.Numeric && instruction.kind != SSAConvertKind.Bitcast) {
            unsupportedInstruction(instruction, "SSA primitive int lowering only supports numeric/bitcast conversions")
        }
        val value = expression(instruction.value, environment, metadata)
        return when (instruction.targetType) {
            SSABoolType -> "($value)"
            SSAI32Type -> when (instruction.value.type) {
                SSAI64Type -> "static_cast<uint32_t>($value)"
                SSAF32Type -> "static_cast<uint32_t>(grt_f2i($value))"
                SSAF64Type -> "static_cast<uint32_t>(grt_d2i($value))"
                else -> "($value)"
            }
            SSAI64Type -> when (instruction.value.type) {
                SSAF32Type -> "static_cast<uint64_t>(grt_f2l($value))"
                SSAF64Type -> "static_cast<uint64_t>(grt_d2l($value))"
                else -> if (instruction.value.type.isI32Like()) "static_cast<uint64_t>(grt_i32($value))" else "($value)"
            }
            SSAI8Type -> "(((($value) & 0xffu) ^ 0x80u) - 0x80u)"
            SSAI16Type -> "(((($value) & 0xffffu) ^ 0x8000u) - 0x8000u)"
            SSACharType -> "(($value) & 0xffffu)"
            SSAF32Type -> when (instruction.value.type) {
                SSAF64Type -> "static_cast<jfloat>($value)"
                SSAI64Type -> "static_cast<jfloat>(grt_i64($value))"
                else -> if (instruction.value.type.isI32Like()) "static_cast<jfloat>(grt_i32($value))" else "($value)"
            }
            SSAF64Type -> when (instruction.value.type) {
                SSAF32Type -> "static_cast<jdouble>($value)"
                SSAI64Type -> "static_cast<jdouble>(grt_i64($value))"
                else -> if (instruction.value.type.isI32Like()) "static_cast<jdouble>(grt_i32($value))" else "($value)"
            }
            else -> unsupportedInstruction(instruction, "SSA conversion target is not int/long primitive")
        }
    }

    private fun callExpression(
        instruction: SSACallInstruction,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata,
        intrinsicStats: NativeJvmIntrinsicStats?
    ): String {
        if (!instruction.isIntegerRotateLeft(metadata) || instruction.args.size != 2) {
            unsupportedInstruction(instruction, "unsupported SSA call in primitive int lowering")
        }
        intrinsicStats?.record(IntegerRotateLeftKey)
        val value = expression(instruction.args[0], environment, metadata)
        val distance = expression(instruction.args[1], environment, metadata)
        return "grt_rotl32($value, $distance)"
    }

    private fun intrinsicExpression(
        instruction: SSAIntrinsicInstruction,
        environment: Map<SSAValue, String>,
        metadata: JvmSSAMetadata
    ): String {
        val name = instruction.intrinsic.name
        if (!name.startsWith("cmp3.") || instruction.args.size != 2) {
            unsupportedInstruction(instruction, "unsupported SSA intrinsic $name in primitive lowering")
        }
        val opcode = name.removePrefix("cmp3.").toIntOrNull()
            ?: unsupportedInstruction(instruction, "invalid cmp3 opcode in SSA intrinsic $name")
        val lhs = expression(instruction.args[0], environment, metadata)
        val rhs = expression(instruction.args[1], environment, metadata)
        return when (opcode) {
            Opcodes.LCMP -> "(($lhs) == ($rhs) ? 0u : (grt_i64($lhs) < grt_i64($rhs) ? ${intLiteral(-1)} : 1u))"
            Opcodes.FCMPL -> floatCompare3(lhs, rhs, nanResult = -1)
            Opcodes.FCMPG -> floatCompare3(lhs, rhs, nanResult = 1)
            Opcodes.DCMPL -> doubleCompare3(lhs, rhs, nanResult = -1)
            Opcodes.DCMPG -> doubleCompare3(lhs, rhs, nanResult = 1)
            else -> unsupportedInstruction(instruction, "unsupported cmp3 opcode $opcode")
        }
    }

    private fun SSACallInstruction.isIntegerRotateLeft(metadata: JvmSSAMetadata): Boolean {
        val target = target as? SSAExternalFunctionRef ?: return false
        val method = metadata.methods[target.ref] ?: return false
        return method.opcode == Opcodes.INVOKESTATIC &&
            method.owner == "java/lang/Integer" &&
            method.name == "rotateLeft" &&
            method.desc == "(II)I"
    }

    private fun floatCompare3(lhs: String, rhs: String, nanResult: Int): String {
        return "(std::isnan($lhs) || std::isnan($rhs) ? ${intLiteral(nanResult)} : (($lhs) == ($rhs) ? 0u : (($lhs) < ($rhs) ? ${intLiteral(-1)} : 1u)))"
    }

    private fun doubleCompare3(lhs: String, rhs: String, nanResult: Int): String {
        return "(std::isnan($lhs) || std::isnan($rhs) ? ${intLiteral(nanResult)} : (($lhs) == ($rhs) ? 0u : (($lhs) < ($rhs) ? ${intLiteral(-1)} : 1u)))"
    }

    private fun SSAType.isI32Like(): Boolean = when (this) {
        SSABoolType,
        SSAI8Type,
        SSAI16Type,
        SSACharType,
        SSAI32Type -> true
        else -> false
    }

    private fun SSAType.isPrimitiveIntLike(): Boolean = isI32Like() || this == SSAI64Type

    private fun SSAType.isFloatScalar(): Boolean = this == SSAF32Type || this == SSAF64Type

    private fun SSAType.isPrimitiveScalar(): Boolean = isPrimitiveIntLike() || isFloatScalar()

    private fun SSAType.isWide(): Boolean = this == SSAI64Type

    private fun unsupportedInstruction(instruction: SSAInstruction, message: String): Nothing {
        throw UnsupportedNativeInstruction(
            NativeSkipReason.UnsupportedInstruction,
            message
        )
    }

    private fun integerLiteral(value: Long, type: SSAIntegerType): String {
        return if (type == SSAI64Type) {
            String.format(Locale.US, "0x%016xULL", value)
        } else {
            String.format(Locale.US, "0x%08xU", value.toInt())
        }
    }

    private fun floatLiteral(value: Double, type: SSAFloatType): String {
        val typeName = if (type == SSAF32Type) "jfloat" else "jdouble"
        val limitType = if (type == SSAF32Type) "jfloat" else "jdouble"
        val literal = when {
            value.isNaN() -> "std::numeric_limits<$limitType>::quiet_NaN()"
            value == Double.POSITIVE_INFINITY -> "std::numeric_limits<$limitType>::infinity()"
            value == Double.NEGATIVE_INFINITY -> "-std::numeric_limits<$limitType>::infinity()"
            type == SSAF32Type -> String.format(Locale.US, "%.9g", value) + "f"
            else -> String.format(Locale.US, "%.17g", value)
        }
        return "static_cast<$typeName>($literal)"
    }

    private fun intLiteral(value: Int): String {
        return integerLiteral(value.toLong(), SSAI32Type)
    }
}
