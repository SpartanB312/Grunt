package net.spartanb312.grunt.ir.core

/**
 * Human-readable debug printer for core IR.
 *
 * This output is intentionally friendly rather than strictly parseable. Use the
 * text exporter package when a stable `.ir` interchange format is needed.
 */
class IrPrinter {
    fun print(function: IrFunction): String {
        return buildString {
            append("fun ")
            append(function.symbol.name)
            append("(")
            append(function.parameters.joinToString(", ") { "${printValue(it)}: ${it.type.displayName}" })
            append("): ")
            append(function.returnType.displayName)
            append(" {\n")

            for (block in function.blocks) {
                append("  ")
                append(block.id)
                append("(")
                append(block.args.joinToString(", ") { "${printValue(it)}: ${it.type.displayName}" })
                append("):\n")

                for (instruction in block.instructions) {
                    append("    ")
                    append(printInstruction(instruction))
                    append("\n")
                }

                append("    ")
                append(printTerminator(block.terminator))
                append("\n")
            }

            append("}")
        }
    }

    private fun printInstruction(instruction: IrInstruction): String {
        val prefix = instruction.result?.let { "${printValue(it)} = " } ?: ""
        return prefix + when (instruction) {
            is IrUnaryInstruction -> "${instruction.op.name.lowercase()} ${printValue(instruction.value)}"
            is IrBinaryInstruction -> {
                "${instruction.op.name.lowercase()} ${printValue(instruction.lhs)}, ${printValue(instruction.rhs)}"
            }
            is IrCompareInstruction -> {
                "cmp.${instruction.predicate.name.lowercase()} ${printValue(instruction.lhs)}, ${printValue(instruction.rhs)}"
            }
            is IrConvertInstruction -> {
                "convert.${instruction.kind.name.lowercase()} ${printValue(instruction.value)} to ${instruction.targetType.displayName}"
            }
            is IrLoadFieldInstruction -> {
                val receiver = instruction.receiver?.let { "${printValue(it)}." } ?: ""
                "load.field $receiver${instruction.field.displayName}"
            }
            is IrStoreFieldInstruction -> {
                val receiver = instruction.receiver?.let { "${printValue(it)}." } ?: ""
                "store.field $receiver${instruction.field.displayName}, ${printValue(instruction.value)}"
            }
            is IrArrayLoadInstruction -> {
                "array.load ${printValue(instruction.array)}[${printValue(instruction.index)}]"
            }
            is IrArrayStoreInstruction -> {
                "array.store ${printValue(instruction.array)}[${printValue(instruction.index)}], ${printValue(instruction.value)}"
            }
            is IrCallInstruction -> {
                "call.${instruction.dispatch.name.lowercase()} ${instruction.target.displayName}(${instruction.args.joinToString(", ") { printValue(it) }})"
            }
            is IrResolveDynamicValueInstruction -> {
                "resolve.dynamic ${printDynamicSite(instruction.site)}"
            }
            is IrDynamicCallInstruction -> {
                "call.dynamic ${printDynamicSite(instruction.site)}(${instruction.args.joinToString(", ") { printValue(it) }})"
            }
            is IrAllocateInstruction -> {
                "alloc ${instruction.allocation.type.displayName}(${instruction.args.joinToString(", ") { printValue(it) }})"
            }
            is IrIntrinsicInstruction -> {
                "intrinsic ${instruction.intrinsic.displayName}(${instruction.args.joinToString(", ") { printValue(it) }})"
            }
            is IrBarrierInstruction -> {
                "barrier${instruction.reason?.let { " \"$it\"" } ?: ""}"
            }
        }
    }

    private fun printTerminator(terminator: IrTerminator): String {
        return when (terminator) {
            is IrJumpTerminator -> "jump ${printSuccessor(terminator.target)}"
            is IrBranchTerminator -> {
                "branch ${printValue(terminator.condition)}, ${printSuccessor(terminator.trueTarget)}, ${printSuccessor(terminator.falseTarget)}"
            }
            is IrSwitchTerminator -> {
                val cases = terminator.cases.joinToString(", ") {
                    "${it.key} -> ${printSuccessor(it.target)}"
                }
                "switch ${printValue(terminator.value)} [$cases], default -> ${printSuccessor(terminator.defaultTarget)}"
            }
            is IrReturnTerminator -> {
                terminator.value?.let { "return ${printValue(it)}" } ?: "return"
            }
            is IrThrowTerminator -> "throw ${printValue(terminator.exception)}"
            IrUnreachableTerminator -> "unreachable"
        }
    }

    private fun printSuccessor(successor: IrSuccessor): String {
        return buildString {
            append(successor.block.id)
            append("(")
            append(successor.args.joinToString(", ") { printValue(it) })
            append(")")
        }
    }

    private fun printValue(value: IrValue): String {
        return when (value) {
            is IrSsaValue -> value.debugName ?: value.id.toString()
            is IrBoolLiteral -> value.value.toString()
            is IrIntLiteral -> value.value.toString()
            is IrFloatLiteral -> value.value.toString()
            IrNullLiteral -> "null"
            is IrOpaqueLiteral -> value.text
        }
    }

    private fun printDynamicSite(site: IrDynamicSite): String {
        return site.debugName ?: site.externalRef?.toString() ?: site.id.toString()
    }
}
