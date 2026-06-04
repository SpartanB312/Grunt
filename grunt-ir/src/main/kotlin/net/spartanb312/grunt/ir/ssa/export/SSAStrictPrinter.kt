package net.spartanb312.grunt.ir.ssa.export

import net.spartanb312.grunt.ir.ssa.core.*

class SSAStrictPrinter {
    fun print(function: SSAFunction): String {
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

    private fun instruction(index: Int, instruction: SSAInstruction): String {
        val result = instruction.result?.let { "(result ${it.id}) (type ${type(it.type)})${name(it.debugName)} " } ?: ""
        val body = when (instruction) {
            is SSAUnaryInstruction -> {
                "$result(unary ${instruction.op.id}) (value ${value(instruction.value)})"
            }
            is SSABinaryInstruction -> {
                "$result(binary ${instruction.op.id}) (lhs ${value(instruction.lhs)}) (rhs ${value(instruction.rhs)})"
            }
            is SSACompareInstruction -> {
                "$result(predicate ${instruction.predicate.id}) (lhs ${value(instruction.lhs)}) (rhs ${value(instruction.rhs)})"
            }
            is SSAConvertInstruction -> {
                "$result(kind ${instruction.kind.id}) (target ${type(instruction.targetType)}) (value ${value(instruction.value)})"
            }
            is SSALoadFieldInstruction -> {
                "$result(field ${field(instruction.field)}) (receiver ${nullableValue(instruction.receiver)})"
            }
            is SSAStoreFieldInstruction -> {
                "(field ${field(instruction.field)}) (receiver ${nullableValue(instruction.receiver)}) (value ${value(instruction.value)})"
            }
            is SSAArrayLoadInstruction -> {
                "$result(array ${value(instruction.array)}) (array-index ${value(instruction.index)})" +
                    element(instruction.elementType)
            }
            is SSAArrayStoreInstruction -> {
                "(array ${value(instruction.array)}) (array-index ${value(instruction.index)}) " +
                    "(value ${value(instruction.value)})${element(instruction.elementType)}"
            }
            is SSACallInstruction -> {
                "$result(target ${callable(instruction.target)}) (dispatch ${instruction.dispatch.id}) (args ${values(instruction.args)})"
            }
            is SSAResolveDynamicValueInstruction -> {
                "$result(site ${dynamicValueSite(instruction.site)})"
            }
            is SSADynamicCallInstruction -> {
                "$result(site ${dynamicCallSite(instruction.site)}) (args ${values(instruction.args)})"
            }
            is SSAAllocateInstruction -> {
                "$result(allocation ${allocation(instruction.allocation)}) (args ${values(instruction.args)})"
            }
            is SSAIntrinsicInstruction -> {
                "$result(intrinsic ${callable(instruction.intrinsic)}) (args ${values(instruction.args)})"
            }
            is SSABarrierInstruction -> {
                "(reason ${instruction.reason?.let(::q) ?: "null"})"
            }
        }

        return "(inst (index $index) (op ${instruction.strictOp}) $body (effect ${effect(instruction.effect)}))"
    }

    private fun element(type: SSAType?): String {
        return type?.let { " (element ${type(it)})" } ?: ""
    }

    private fun terminator(terminator: SSATerminator): String {
        return when (terminator) {
            is SSAJumpTerminator -> "(term (op jump) (target ${successor(terminator.target)}))"
            is SSABranchTerminator -> {
                "(term (op branch) (condition ${value(terminator.condition)}) " +
                    "(true ${successor(terminator.trueTarget)}) (false ${successor(terminator.falseTarget)}))"
            }
            is SSASwitchTerminator -> {
                val cases = terminator.cases.joinToString(" ") {
                    "(case (key ${it.key}) (target ${successor(it.target)}))"
                }
                "(term (op switch) (value ${value(terminator.value)}) (cases $cases) " +
                    "(default ${successor(terminator.defaultTarget)}))"
            }
            is SSAReturnTerminator -> {
                if (terminator.value == null) "(term (op return))"
                else "(term (op return) (value ${value(terminator.value)}))"
            }
            is SSAThrowTerminator -> "(term (op throw) (exception ${value(terminator.exception)}))"
            SSAUnreachableTerminator -> "(term (op unreachable))"
        }
    }

    private fun successor(successor: SSASuccessor): String {
        return "(succ (block ${successor.block.id}) (args ${values(successor.args)}))"
    }

    private fun value(value: SSAValue): String {
        return when (value) {
            is SSAStructure -> "(ssa ${value.id})"
            is SSABoolLiteral -> "(bool ${value.value})"
            is SSAIntLiteral -> "(int (type ${type(value.type)}) (value ${value.value}))"
            is SSAFloatLiteral -> "(float (type ${type(value.type)}) (value ${value.value}))"
            SSANullLiteral -> "(null)"
            is SSAOpaqueLiteral -> "(opaque (type ${type(value.type)}) (text ${q(value.text)}))"
        }
    }

    private fun nullableValue(value: SSAValue?): String {
        return value?.let(::value) ?: "null"
    }

    private fun values(values: List<SSAValue>): String {
        return values.joinToString(" ", prefix = "(", postfix = ")") { value(it) }
    }

    private fun field(field: SSAFieldRef): String {
        return when (field) {
            is SSASymbolFieldRef -> {
                "(symbol-field (id ${field.symbol.id}) (name ${q(field.symbol.name)}) " +
                    "(type ${type(field.type)}) (static ${field.isStatic}))"
            }
            is SSAExternalFieldRef -> {
                "(external-field (ref ${external(field.ref)}) (type ${type(field.type)}) (static ${field.isStatic}))"
            }
        }
    }

    private fun callable(callable: SSACallableRef): String {
        return when (callable) {
            is SSAFunctionRef -> {
                "(function (id ${callable.symbol.id}) (name ${q(callable.symbol.name)}) " +
                    "(params ${types(callable.parameterTypes)}) (return ${type(callable.returnType)}))"
            }
            is SSAExternalFunctionRef -> {
                "(external-function (ref ${external(callable.ref)}) " +
                    "(params ${types(callable.parameterTypes)}) (return ${type(callable.returnType)}))"
            }
            is SSAIntrinsicRef -> {
                "(intrinsic (name ${q(callable.name)}) " +
                    "(params ${types(callable.parameterTypes)}) (return ${type(callable.returnType)}))"
            }
        }
    }

    private fun dynamicValueSite(site: SSADynamicValueSite): String {
        return "(dynamic-value (id ${site.id}) (type ${type(site.type)}) " +
            "(external ${nullableExternal(site.externalRef)}) (name ${site.debugName?.let(::q) ?: "null"}))"
    }

    private fun dynamicCallSite(site: SSADynamicCallSite): String {
        return "(dynamic-call (id ${site.id}) (params ${types(site.parameterTypes)}) (return ${type(site.returnType)}) " +
            "(external ${nullableExternal(site.externalRef)}) (name ${site.debugName?.let(::q) ?: "null"}))"
    }

    private fun allocation(allocation: SSAAllocation): String {
        return when (allocation) {
            is SSAAllocation.Object -> "(object (type ${type(allocation.type)}))"
            is SSAAllocation.Array -> "(array (type ${type(allocation.type)}))"
            is SSAAllocation.Opaque -> "(opaque (type ${type(allocation.type)}) (name ${q(allocation.name)}))"
        }
    }

    private fun external(ref: SSAExternalRef): String {
        return "(id ${ref.id}) (kind ${ref.kind.id}) (name ${ref.debugName?.let(::q) ?: "null"})"
    }

    private fun nullableExternal(ref: SSAExternalRef?): String {
        return ref?.let { "(${external(it)})" } ?: "null"
    }

    private fun effect(effect: SSAEffect): String {
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

    private fun origin(origin: SSABlockArgOrigin): String {
        return when (origin) {
            SSABlockArgOrigin.Join -> "(join)"
            SSABlockArgOrigin.Synthetic -> "(synthetic)"
            SSABlockArgOrigin.ExceptionObject -> "(exception)"
            is SSABlockArgOrigin.Parameter -> "(parameter (index ${origin.index}))"
            is SSABlockArgOrigin.FrontendState -> "(frontend (name ${q(origin.name)}) (index ${origin.index}))"
        }
    }

    private fun type(type: SSAType) = q(type.displayName)

    private fun types(types: List<SSAType>): String {
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

    private val SSAInstruction.strictOp: String
        get() = when (this) {
            is SSAUnaryInstruction -> "unary"
            is SSABinaryInstruction -> "binary"
            is SSACompareInstruction -> "compare"
            is SSAConvertInstruction -> "convert"
            is SSALoadFieldInstruction -> "load-field"
            is SSAStoreFieldInstruction -> "store-field"
            is SSAArrayLoadInstruction -> "array-load"
            is SSAArrayStoreInstruction -> "array-store"
            is SSACallInstruction -> "call"
            is SSAResolveDynamicValueInstruction -> "resolve-dynamic-value"
            is SSADynamicCallInstruction -> "dynamic-call"
            is SSAAllocateInstruction -> "allocate"
            is SSAIntrinsicInstruction -> "intrinsic"
            is SSABarrierInstruction -> "barrier"
        }
}
