package net.spartanb312.grunt.ir.export

import net.spartanb312.grunt.ir.core.*

class IrStrictPrinter {
    fun print(function: IrFunction): String {
        return buildString {
            appendLine("(grunt-ir (version 1))")
            appendLine(
                "(function (id ${function.symbol.id}) (name ${q(function.symbol.name)}) " +
                    "(return ${type(function.returnType)}) (params ${function.parameters.size}) (blocks ${function.blocks.size}))"
            )

            for (parameter in function.parameters) {
                appendLine(
                    "  (param (id ${parameter.id}) (index ${parameter.index}) " +
                        "(type ${type(parameter.type)})${name(parameter.debugName)})"
                )
            }

            for (block in function.blocks) {
                appendLine("  (block (id ${block.id}) (args ${block.args.size}) (insts ${block.instructions.size}))")

                for (arg in block.args) {
                    appendLine(
                        "    (arg (id ${arg.id}) (index ${arg.index}) (type ${type(arg.type)}) " +
                            "(origin ${origin(arg.origin)})${name(arg.debugName)})"
                    )
                }

                for ((index, instruction) in block.instructions.withIndex()) {
                    appendLine("    ${instruction(index, instruction)}")
                }

                appendLine("    ${terminator(block.terminator)}")
                appendLine("  (end block)")
            }

            append("(end function)")
        }
    }

    private fun instruction(index: Int, instruction: IrInstruction): String {
        val result = instruction.result?.let { "(result ${it.id}) (type ${type(it.type)})${name(it.debugName)} " } ?: ""
        val body = when (instruction) {
            is IrUnaryInstruction -> {
                "$result(unary ${instruction.op.id}) (value ${value(instruction.value)})"
            }
            is IrBinaryInstruction -> {
                "$result(binary ${instruction.op.id}) (lhs ${value(instruction.lhs)}) (rhs ${value(instruction.rhs)})"
            }
            is IrCompareInstruction -> {
                "$result(predicate ${instruction.predicate.id}) (lhs ${value(instruction.lhs)}) (rhs ${value(instruction.rhs)})"
            }
            is IrConvertInstruction -> {
                "$result(kind ${instruction.kind.id}) (target ${type(instruction.targetType)}) (value ${value(instruction.value)})"
            }
            is IrLoadFieldInstruction -> {
                "$result(field ${field(instruction.field)}) (receiver ${nullableValue(instruction.receiver)})"
            }
            is IrStoreFieldInstruction -> {
                "(field ${field(instruction.field)}) (receiver ${nullableValue(instruction.receiver)}) (value ${value(instruction.value)})"
            }
            is IrArrayLoadInstruction -> {
                "$result(array ${value(instruction.array)}) (array-index ${value(instruction.index)})" +
                    element(instruction.elementType)
            }
            is IrArrayStoreInstruction -> {
                "(array ${value(instruction.array)}) (array-index ${value(instruction.index)}) " +
                    "(value ${value(instruction.value)})${element(instruction.elementType)}"
            }
            is IrCallInstruction -> {
                "$result(target ${callable(instruction.target)}) (dispatch ${instruction.dispatch.id}) (args ${values(instruction.args)})"
            }
            is IrResolveDynamicValueInstruction -> {
                "$result(site ${dynamicValueSite(instruction.site)})"
            }
            is IrDynamicCallInstruction -> {
                "$result(site ${dynamicCallSite(instruction.site)}) (args ${values(instruction.args)})"
            }
            is IrAllocateInstruction -> {
                "$result(allocation ${allocation(instruction.allocation)}) (args ${values(instruction.args)})"
            }
            is IrIntrinsicInstruction -> {
                "$result(intrinsic ${callable(instruction.intrinsic)}) (args ${values(instruction.args)})"
            }
            is IrBarrierInstruction -> {
                "(reason ${instruction.reason?.let(::q) ?: "null"})"
            }
        }

        return "(inst (index $index) (op ${instruction.strictOp}) $body (effect ${effect(instruction.effect)}))"
    }

    private fun element(type: IrType?): String {
        return type?.let { " (element ${type(it)})" } ?: ""
    }

    private fun terminator(terminator: IrTerminator): String {
        return when (terminator) {
            is IrJumpTerminator -> "(term (op jump) (target ${successor(terminator.target)}))"
            is IrBranchTerminator -> {
                "(term (op branch) (condition ${value(terminator.condition)}) " +
                    "(true ${successor(terminator.trueTarget)}) (false ${successor(terminator.falseTarget)}))"
            }
            is IrSwitchTerminator -> {
                val cases = terminator.cases.joinToString(" ") {
                    "(case (key ${it.key}) (target ${successor(it.target)}))"
                }
                "(term (op switch) (value ${value(terminator.value)}) (cases $cases) " +
                    "(default ${successor(terminator.defaultTarget)}))"
            }
            is IrReturnTerminator -> {
                if (terminator.value == null) "(term (op return))"
                else "(term (op return) (value ${value(terminator.value)}))"
            }
            is IrThrowTerminator -> "(term (op throw) (exception ${value(terminator.exception)}))"
            IrUnreachableTerminator -> "(term (op unreachable))"
        }
    }

    private fun successor(successor: IrSuccessor): String {
        return "(succ (block ${successor.block.id}) (args ${values(successor.args)}))"
    }

    private fun value(value: IrValue): String {
        return when (value) {
            is IrSsaValue -> "(ssa ${value.id})"
            is IrBoolLiteral -> "(bool ${value.value})"
            is IrIntLiteral -> "(int (type ${type(value.type)}) (value ${value.value}))"
            is IrFloatLiteral -> "(float (type ${type(value.type)}) (value ${value.value}))"
            IrNullLiteral -> "(null)"
            is IrOpaqueLiteral -> "(opaque (type ${type(value.type)}) (text ${q(value.text)}))"
        }
    }

    private fun nullableValue(value: IrValue?): String {
        return value?.let(::value) ?: "null"
    }

    private fun values(values: List<IrValue>): String {
        return values.joinToString(" ", prefix = "(", postfix = ")") { value(it) }
    }

    private fun field(field: IrFieldRef): String {
        return when (field) {
            is IrSymbolFieldRef -> {
                "(symbol-field (id ${field.symbol.id}) (name ${q(field.symbol.name)}) " +
                    "(type ${type(field.type)}) (static ${field.isStatic}))"
            }
            is IrExternalFieldRef -> {
                "(external-field (ref ${external(field.ref)}) (type ${type(field.type)}) (static ${field.isStatic}))"
            }
        }
    }

    private fun callable(callable: IrCallableRef): String {
        return when (callable) {
            is IrFunctionRef -> {
                "(function (id ${callable.symbol.id}) (name ${q(callable.symbol.name)}) " +
                    "(params ${types(callable.parameterTypes)}) (return ${type(callable.returnType)}))"
            }
            is IrExternalFunctionRef -> {
                "(external-function (ref ${external(callable.ref)}) " +
                    "(params ${types(callable.parameterTypes)}) (return ${type(callable.returnType)}))"
            }
            is IrIntrinsicRef -> {
                "(intrinsic (name ${q(callable.name)}) " +
                    "(params ${types(callable.parameterTypes)}) (return ${type(callable.returnType)}))"
            }
        }
    }

    private fun dynamicValueSite(site: IrDynamicValueSite): String {
        return "(dynamic-value (id ${site.id}) (type ${type(site.type)}) " +
            "(external ${nullableExternal(site.externalRef)}) (name ${site.debugName?.let(::q) ?: "null"}))"
    }

    private fun dynamicCallSite(site: IrDynamicCallSite): String {
        return "(dynamic-call (id ${site.id}) (params ${types(site.parameterTypes)}) (return ${type(site.returnType)}) " +
            "(external ${nullableExternal(site.externalRef)}) (name ${site.debugName?.let(::q) ?: "null"}))"
    }

    private fun allocation(allocation: IrAllocation): String {
        return when (allocation) {
            is IrAllocation.Object -> "(object (type ${type(allocation.type)}))"
            is IrAllocation.Array -> "(array (type ${type(allocation.type)}))"
            is IrAllocation.Opaque -> "(opaque (type ${type(allocation.type)}) (name ${q(allocation.name)}))"
        }
    }

    private fun external(ref: IrExternalRef): String {
        return "(id ${ref.id}) (kind ${ref.kind.id}) (name ${ref.debugName?.let(::q) ?: "null"})"
    }

    private fun nullableExternal(ref: IrExternalRef?): String {
        return ref?.let { "(${external(it)})" } ?: "null"
    }

    private fun effect(effect: IrEffect): String {
        return "(mayThrow ${effect.mayThrow}) " +
            "(readsMemory ${effect.readsMemory}) " +
            "(writesMemory ${effect.writesMemory}) " +
            "(readsExternalState ${effect.readsExternalState}) " +
            "(writesExternalState ${effect.writesExternalState}) " +
            "(callsExternal ${effect.callsExternal}) " +
            "(resolvesExternal ${effect.resolvesExternal}) " +
            "(barrier ${effect.isBarrier}) " +
            "(canDuplicate ${effect.canDuplicate}) " +
            "(canMove ${effect.canMove})"
    }

    private fun origin(origin: IrBlockArgOrigin): String {
        return when (origin) {
            IrBlockArgOrigin.Join -> "(join)"
            IrBlockArgOrigin.Synthetic -> "(synthetic)"
            IrBlockArgOrigin.ExceptionObject -> "(exception)"
            is IrBlockArgOrigin.Parameter -> "(parameter (index ${origin.index}))"
            is IrBlockArgOrigin.FrontendState -> "(frontend (name ${q(origin.name)}) (index ${origin.index}))"
        }
    }

    private fun type(type: IrType) = q(type.displayName)

    private fun types(types: List<IrType>): String {
        return types.joinToString(" ", prefix = "(", postfix = ")") { type(it) }
    }

    private fun name(name: String?): String {
        return name?.let { " (name ${q(it)})" } ?: ""
    }

    private fun q(value: String): String {
        return buildString {
            append('"')
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }

    private val Enum<*>.id: String get() = name.lowercase()

    private val IrInstruction.strictOp: String
        get() = when (this) {
            is IrUnaryInstruction -> "unary"
            is IrBinaryInstruction -> "binary"
            is IrCompareInstruction -> "compare"
            is IrConvertInstruction -> "convert"
            is IrLoadFieldInstruction -> "load-field"
            is IrStoreFieldInstruction -> "store-field"
            is IrArrayLoadInstruction -> "array-load"
            is IrArrayStoreInstruction -> "array-store"
            is IrCallInstruction -> "call"
            is IrResolveDynamicValueInstruction -> "resolve-dynamic-value"
            is IrDynamicCallInstruction -> "dynamic-call"
            is IrAllocateInstruction -> "allocate"
            is IrIntrinsicInstruction -> "intrinsic"
            is IrBarrierInstruction -> "barrier"
        }
}
