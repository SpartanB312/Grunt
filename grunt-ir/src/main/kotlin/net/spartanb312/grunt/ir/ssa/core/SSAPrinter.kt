package net.spartanb312.grunt.ir.ssa.core

/**
 * Human-readable debug printer for core IR.
 *
 * This output is intentionally friendly rather than strictly parseable. Use the
 * text exporter package when a stable `.ir` interchange format is needed.
 */
class SSAPrinter {
    fun print(function: SSAFunction): String {
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

    private fun printInstruction(instruction: SSAInstruction): String {
        val prefix = instruction.result?.let { "${printValue(it)} = " } ?: ""
        return prefix + when (instruction) {
            is SSAUnaryInstruction -> "${instruction.op.name.lowercase()} ${printValue(instruction.value)}"
            is SSABinaryInstruction -> {
                "${instruction.op.name.lowercase()} ${printValue(instruction.lhs)}, ${printValue(instruction.rhs)}"
            }
            is SSACompareInstruction -> {
                "cmp.${instruction.predicate.name.lowercase()} ${printValue(instruction.lhs)}, ${printValue(instruction.rhs)}"
            }
            is SSAConvertInstruction -> {
                "convert.${instruction.kind.name.lowercase()} ${printValue(instruction.value)} to ${instruction.targetType.displayName}"
            }
            is SSALoadFieldInstruction -> {
                val receiver = instruction.receiver?.let { "${printValue(it)}." } ?: ""
                "load.field $receiver${instruction.field.displayName}"
            }
            is SSAStoreFieldInstruction -> {
                val receiver = instruction.receiver?.let { "${printValue(it)}." } ?: ""
                "store.field $receiver${instruction.field.displayName}, ${printValue(instruction.value)}"
            }
            is SSAArrayLoadInstruction -> {
                "array.load ${printValue(instruction.array)}[${printValue(instruction.index)}]"
            }
            is SSAArrayStoreInstruction -> {
                "array.store ${printValue(instruction.array)}[${printValue(instruction.index)}], ${printValue(instruction.value)}"
            }
            is SSACallInstruction -> {
                "call.${instruction.dispatch.name.lowercase()} ${instruction.target.displayName}(${instruction.args.joinToString(", ") { printValue(it) }})"
            }
            is SSAResolveDynamicValueInstruction -> {
                "resolve.dynamic ${printDynamicSite(instruction.site)}"
            }
            is SSADynamicCallInstruction -> {
                "call.dynamic ${printDynamicSite(instruction.site)}(${instruction.args.joinToString(", ") { printValue(it) }})"
            }
            is SSAAllocateInstruction -> {
                "alloc ${instruction.allocation.type.displayName}(${instruction.args.joinToString(", ") { printValue(it) }})"
            }
            is SSAIntrinsicInstruction -> {
                "intrinsic ${instruction.intrinsic.displayName}(${instruction.args.joinToString(", ") { printValue(it) }})"
            }
            is SSABarrierInstruction -> {
                "barrier${instruction.reason?.let { " \"$it\"" } ?: ""}"
            }
        }
    }

    private fun printTerminator(terminator: SSATerminator): String {
        return when (terminator) {
            is SSAJumpTerminator -> "jump ${printSuccessor(terminator.target)}"
            is SSABranchTerminator -> {
                "branch ${printValue(terminator.condition)}, ${printSuccessor(terminator.trueTarget)}, ${printSuccessor(terminator.falseTarget)}"
            }
            is SSASwitchTerminator -> {
                val cases = terminator.cases.joinToString(", ") {
                    "${it.key} -> ${printSuccessor(it.target)}"
                }
                "switch ${printValue(terminator.value)} [$cases], default -> ${printSuccessor(terminator.defaultTarget)}"
            }
            is SSAReturnTerminator -> {
                terminator.value?.let { "return ${printValue(it)}" } ?: "return"
            }
            is SSAThrowTerminator -> "throw ${printValue(terminator.exception)}"
            SSAUnreachableTerminator -> "unreachable"
        }
    }

    private fun printSuccessor(successor: SSASuccessor): String {
        return buildString {
            append(successor.block.id)
            append("(")
            append(successor.args.joinToString(", ") { printValue(it) })
            append(")")
        }
    }

    private fun printValue(value: SSAValue): String {
        return when (value) {
            is SSAStructure -> value.debugName ?: value.id.toString()
            is SSABoolLiteral -> value.value.toString()
            is SSAIntLiteral -> value.value.toString()
            is SSAFloatLiteral -> value.value.toString()
            SSANullLiteral -> "null"
            is SSAOpaqueLiteral -> value.text
        }
    }

    private fun printDynamicSite(site: SSADynamicSite): String {
        return site.debugName ?: site.externalRef?.toString() ?: site.id.toString()
    }
}
