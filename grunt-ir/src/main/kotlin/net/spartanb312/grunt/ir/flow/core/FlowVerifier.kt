package net.spartanb312.grunt.ir.flow.core

data class FlowVerificationIssue(
    val message: String,
    val block: FlowBlock? = null,
    val edge: FlowEdge? = null
) {
    override fun toString(): String {
        val prefix = when {
            edge != null -> edge.toString()
            block != null -> block.id.toString()
            else -> null
        }
        return if (prefix != null) "$prefix: $message" else message
    }
}

data class FlowVerificationResult(
    val issues: List<FlowVerificationIssue>
) {
    val isValid get() = issues.isEmpty()

    fun requireValid() {
        if (!isValid) {
            error(issues.joinToString(separator = "\n") { it.toString() })
        }
    }
}

/**
 * Structural verifier for Flow IR.
 *
 * This verifier intentionally checks graph and frame contracts rather than
 * normal JVM instruction semantics. Ordinary bytecode lives in opaque slices;
 * control-flow mutation passes rely on these invariants before exporting.
 */
object FlowVerifier {
    fun verify(method: FlowMethod): FlowVerificationResult {
        val issues = mutableListOf<FlowVerificationIssue>()
        val blockSet = method.blocks.toSet()

        verifyMethodShape(method, blockSet, issues)
        verifyBlocks(method, blockSet, issues)
        verifyEdges(method, blockSet, issues)
        verifyExceptions(method, blockSet, issues)

        return FlowVerificationResult(issues)
    }

    fun frameAfterJump(block: FlowBlock): FlowFrame {
        return when (val input = block.jump.input) {
            FlowJumpInput.None,
            is FlowJumpInput.Generated,
            is FlowJumpInput.CapturedLocal -> block.bodyExitFrame
            is FlowJumpInput.StackConsumed -> FlowFrames.dropStackSuffix(block.bodyExitFrame, input.produced.size)
        }
    }

    private fun verifyMethodShape(
        method: FlowMethod,
        blockSet: Set<FlowBlock>,
        issues: MutableList<FlowVerificationIssue>
    ) {
        val entry = method.entry
        if (entry == null) {
            issues += FlowVerificationIssue("Method has no entry block")
        } else if (entry !in blockSet) {
            issues += FlowVerificationIssue("Entry block ${entry.id} is not part of the method")
        }

        val blockIds = mutableSetOf<FlowBlockId>()
        for (block in method.blocks) {
            if (!blockIds.add(block.id)) {
                issues += FlowVerificationIssue("Duplicate block id ${block.id}", block)
            }
        }

        val layoutBlocks = method.layout.order.toSet()
        for (block in method.layout.order) {
            if (block !in blockSet) {
                issues += FlowVerificationIssue("Layout contains block ${block.id} that is not part of the method", block)
            }
        }
        for (block in method.blocks) {
            if (block !in layoutBlocks) {
                issues += FlowVerificationIssue("Block ${block.id} is missing from layout", block)
            }
        }
    }

    private fun verifyBlocks(
        method: FlowMethod,
        blockSet: Set<FlowBlock>,
        issues: MutableList<FlowVerificationIssue>
    ) {
        for (block in method.blocks) {
            verifyJumpInput(block, issues)

            val outgoing = method.outgoingEdges(block)
            val ports = block.jump.ports
            for (port in ports) {
                val count = outgoing.count { it.port == port }
                when {
                    count == 0 -> issues += FlowVerificationIssue("Jump port ${port.displayName} has no edge", block)
                    count > 1 -> issues += FlowVerificationIssue("Jump port ${port.displayName} has $count edges", block)
                }
            }
            for (edge in outgoing) {
                if (edge.port !in ports) {
                    issues += FlowVerificationIssue(
                        "Edge uses port ${edge.port.displayName}, but block jump does not expose it",
                        block,
                        edge
                    )
                }
            }
        }

        if (blockSet.size != method.blocks.size) {
            issues += FlowVerificationIssue("Method contains duplicate block object references")
        }
    }

    private fun verifyJumpInput(
        block: FlowBlock,
        issues: MutableList<FlowVerificationIssue>
    ) {
        val input = block.jump.input
        when (block.jump) {
            is FlowGotoJump -> {
                if (input != FlowJumpInput.None) {
                    issues += FlowVerificationIssue("Goto jump must not consume input", block)
                }
            }
            is FlowIfJump -> {
                if (input.produced.isEmpty()) {
                    issues += FlowVerificationIssue("If jump must consume or generate predicate operands", block)
                }
            }
            is FlowSwitchJump -> {
                if (input.produced.size != 1 || input.produced.single() != FlowFrameValue.Int) {
                    issues += FlowVerificationIssue("Switch jump must consume or generate one int selector", block)
                }
            }
            is FlowReturnJump,
            is FlowThrowJump,
            FlowUnreachableJump -> Unit
        }

        when (input) {
            FlowJumpInput.None -> Unit
            is FlowJumpInput.StackConsumed -> {
                if (!FlowFrames.hasStackSuffix(block.bodyExitFrame, input.produced)) {
                    issues += FlowVerificationIssue(
                        "Jump consumes stack ${input.produced.describeValues()}, but body exits with ${block.bodyExitFrame.stack.describeValues()}",
                        block
                    )
                }
            }
            is FlowJumpInput.Generated -> {
                if (input.produced.isEmpty()) {
                    issues += FlowVerificationIssue("Generated jump input must produce at least one value", block)
                }
            }
            is FlowJumpInput.CapturedLocal -> {
                if (input.locals.size != input.produced.size) {
                    issues += FlowVerificationIssue("Captured local input has mismatched local/value arity", block)
                }
                for ((index, local) in input.locals.withIndex()) {
                    val produced = input.produced.getOrNull(index) ?: continue
                    val actual = block.bodyExitFrame.locals.getOrNull(local.index)
                    if (actual == null) {
                        issues += FlowVerificationIssue("Captured local ${local.index} is outside the body exit frame", block)
                    } else if (!FlowFrames.isAssignable(actual, produced)) {
                        issues += FlowVerificationIssue(
                            "Captured local ${local.index} has ${actual.displayName}, expected ${produced.displayName}",
                            block
                        )
                    }
                }
            }
        }
    }

    private fun verifyEdges(
        method: FlowMethod,
        blockSet: Set<FlowBlock>,
        issues: MutableList<FlowVerificationIssue>
    ) {
        val edgeIds = mutableSetOf<FlowEdgeId>()
        for (edge in method.edges) {
            if (!edgeIds.add(edge.id)) {
                issues += FlowVerificationIssue("Duplicate edge id ${edge.id}", edge = edge)
            }
            if (edge.from !in blockSet) {
                issues += FlowVerificationIssue("Edge source ${edge.from.id} is not part of the method", edge = edge)
            }
            if (edge.to !in blockSet) {
                issues += FlowVerificationIssue("Edge target ${edge.to.id} is not part of the method", edge = edge)
            }
            if (edge.from in blockSet && edge.to in blockSet) {
                verifyEdgeFrame(edge, issues)
            }
        }
    }

    private fun verifyEdgeFrame(
        edge: FlowEdge,
        issues: MutableList<FlowVerificationIssue>
    ) {
        val sourceFrame = runCatching { frameAfterJump(edge.from) }.getOrElse { error ->
            issues += FlowVerificationIssue("Cannot compute source frame after jump: ${error.message}", edge.from, edge)
            return
        }
        if (!FlowFrames.isCompatible(sourceFrame, edge.to.entryFrame)) {
            issues += FlowVerificationIssue(
                "Edge frame ${sourceFrame} is not compatible with target entry ${edge.to.entryFrame}",
                edge.from,
                edge
            )
        }
    }

    private fun verifyExceptions(
        method: FlowMethod,
        blockSet: Set<FlowBlock>,
        issues: MutableList<FlowVerificationIssue>
    ) {
        for (region in method.exceptionRegions) {
            if (region.handler !in blockSet) {
                issues += FlowVerificationIssue("Exception handler ${region.handler.id} is not part of the method")
            }
            for (block in region.protectedBlocks) {
                if (block !in blockSet) {
                    issues += FlowVerificationIssue("Protected block ${block.id} is not part of the method", block)
                }
            }
            if (region.protectedBlocks.isEmpty()) {
                issues += FlowVerificationIssue("Exception region has no protected blocks")
            }
        }
    }

    private fun List<FlowFrameValue>.describeValues(): String {
        return joinToString(prefix = "[", postfix = "]") { it.displayName }
    }
}
