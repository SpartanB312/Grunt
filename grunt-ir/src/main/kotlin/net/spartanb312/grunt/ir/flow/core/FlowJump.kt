package net.spartanb312.grunt.ir.flow.core

/** Named output port from a block jump node. */
sealed interface FlowPort {
    val displayName: String

    data object Next : FlowPort {
        override val displayName = "next"
    }

    data object Branch : FlowPort {
        override val displayName = "branch"
    }

    data object Fallthrough : FlowPort {
        override val displayName = "fallthrough"
    }

    data object Default : FlowPort {
        override val displayName = "default"
    }

    data class Case(val key: Int) : FlowPort {
        override val displayName = "case<$key>"
    }

    data class Named(override val displayName: String) : FlowPort
}

/** Value source consumed by a jump instruction. */
sealed interface FlowJumpInput {
    val produced: List<FlowFrameValue>

    data object None : FlowJumpInput {
        override val produced: List<FlowFrameValue> = emptyList()
    }

    /**
     * The original block body leaves operands on the stack and the jump consumes
     * them. Original IF/SWITCH/RETURN/ATHROW imports usually start here.
     */
    data class StackConsumed(
        override val produced: List<FlowFrameValue>
    ) : FlowJumpInput

    /**
     * The jump owns bytecode that creates its operands, for example opaque
     * predicate code inserted before IFEQ.
     */
    data class Generated(
        val code: FlowBytecodeSlice,
        override val produced: List<FlowFrameValue>,
        val guarantee: FlowPredicateGuarantee = FlowPredicateGuarantee.Unknown
    ) : FlowJumpInput

    /** Jump operands are reloaded from locals captured by a prior mutation pass. */
    data class CapturedLocal(
        val locals: List<FlowLocalSlot>,
        override val produced: List<FlowFrameValue>
    ) : FlowJumpInput
}

enum class FlowPredicateGuarantee {
    AlwaysTrue,
    AlwaysFalse,
    Unknown
}

/** Terminal control node of a [FlowBlock]. */
sealed interface FlowJump {
    val input: FlowJumpInput
    val ports: List<FlowPort>
}

enum class FlowGotoMode {
    ExplicitGoto,
    Fallthrough,
    Synthetic
}

data class FlowGotoJump(
    val mode: FlowGotoMode = FlowGotoMode.ExplicitGoto
) : FlowJump {
    override val input: FlowJumpInput = FlowJumpInput.None
    override val ports: List<FlowPort> = listOf(FlowPort.Next)
}

data class FlowIfJump(
    val opcode: Int,
    override val input: FlowJumpInput,
    val branchPort: FlowPort = FlowPort.Branch,
    val fallthroughPort: FlowPort = FlowPort.Fallthrough
) : FlowJump {
    override val ports: List<FlowPort> = listOf(branchPort, fallthroughPort)
}

data class FlowSwitchJump(
    override val input: FlowJumpInput,
    val keyPorts: Map<Int, FlowPort>,
    val defaultPort: FlowPort = FlowPort.Default
) : FlowJump {
    constructor(
        input: FlowJumpInput,
        keys: List<Int>,
        defaultPort: FlowPort = FlowPort.Default
    ) : this(
        input = input,
        keyPorts = keys.associateWithTo(linkedMapOf()) { FlowPort.Case(it) },
        defaultPort = defaultPort
    )

    override val ports: List<FlowPort> = keyPorts.values.toList() + defaultPort
}

data class FlowReturnJump(
    override val input: FlowJumpInput = FlowJumpInput.None
) : FlowJump {
    override val ports: List<FlowPort> = emptyList()
}

data class FlowThrowJump(
    override val input: FlowJumpInput
) : FlowJump {
    override val ports: List<FlowPort> = emptyList()
}

data object FlowUnreachableJump : FlowJump {
    override val input: FlowJumpInput = FlowJumpInput.None
    override val ports: List<FlowPort> = emptyList()
}
