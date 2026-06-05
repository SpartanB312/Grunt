package net.spartanb312.grunt.ir.flow.core

/**
 * JVM verifier frame value.
 *
 * Flow IR keeps verifier-facing frame values instead of high-level semantic
 * types. This is deliberately close to StackMapTable terminology because the
 * exporter should be able to write explicit frames without relying on ASM to
 * infer class hierarchy merges.
 */
sealed interface FlowFrameValue {
    val displayName: String

    data object Top : FlowFrameValue {
        override val displayName = "top"
    }

    data object Int : FlowFrameValue {
        override val displayName = "int"
    }

    data object Float : FlowFrameValue {
        override val displayName = "float"
    }

    data object Long : FlowFrameValue {
        override val displayName = "long"
    }

    data object Double : FlowFrameValue {
        override val displayName = "double"
    }

    data object Null : FlowFrameValue {
        override val displayName = "null"
    }

    data class Object(val internalName: String) : FlowFrameValue {
        override val displayName = "object<$internalName>"
    }

    data object UninitializedThis : FlowFrameValue {
        override val displayName = "uninitialized_this"
    }

    data class UninitializedNew(val site: FlowNewSiteId) : FlowFrameValue {
        override val displayName = "uninitialized<$site>"
    }

    /**
     * Conservative placeholder for importer uncertainty.
     *
     * Keep this rare: it exists so a graph can still be built before a better
     * frame analyzer fills the exact verifier value.
     */
    data class Unknown(val reason: String = "") : FlowFrameValue {
        override val displayName = if (reason.isBlank()) "unknown" else "unknown<$reason>"
    }
}

/** Verifier frame at a block boundary. */
data class FlowFrame(
    val locals: List<FlowFrameValue> = emptyList(),
    val stack: List<FlowFrameValue> = emptyList()
) {
    val hasEmptyStack get() = stack.isEmpty()

    fun withStack(stack: List<FlowFrameValue>) = copy(stack = stack)

    override fun toString(): String {
        val localText = locals.joinToString(prefix = "[", postfix = "]") { it.displayName }
        val stackText = stack.joinToString(prefix = "[", postfix = "]") { it.displayName }
        return "locals=$localText stack=$stackText"
    }

    companion object {
        val Empty = FlowFrame()
    }
}

/** JVM local slot used by generated jump inputs and mutation scratch state. */
data class FlowLocalSlot(
    val index: Int,
    val value: FlowFrameValue,
    val synthetic: Boolean = false
) {
    init {
        require(index >= 0) { "Local slot index must be non-negative" }
    }
}

/** Simple synthetic local allocator for flow passes. */
class FlowLocalPool(
    firstFreeSlot: Int = 0
) {
    var nextSlot: Int = firstFreeSlot
        private set

    fun allocate(value: FlowFrameValue): FlowLocalSlot {
        val slot = FlowLocalSlot(nextSlot, value, synthetic = true)
        nextSlot += value.categorySize
        return slot
    }
}

val FlowFrameValue.categorySize: Int
    get() = when (this) {
        FlowFrameValue.Long,
        FlowFrameValue.Double -> 2
        else -> 1
    }

object FlowFrames {
    fun isCompatible(actual: FlowFrame, expected: FlowFrame): Boolean {
        if (actual.stack.size != expected.stack.size) return false
        if (!actual.stack.zip(expected.stack).all { (from, to) -> isAssignable(from, to) }) return false

        val localCount = maxOf(actual.locals.size, expected.locals.size)
        return (0 until localCount).all { index ->
            val from = actual.locals.getOrElse(index) { FlowFrameValue.Unknown("absentLocal") }
            val to = expected.locals.getOrElse(index) { FlowFrameValue.Unknown("absentLocal") }
            isLocalAssignable(from, to)
        }
    }

    fun isAssignable(actual: FlowFrameValue, expected: FlowFrameValue): Boolean {
        if (actual is FlowFrameValue.Unknown || expected is FlowFrameValue.Unknown) return true
        if (actual == expected) return true
        if (actual == FlowFrameValue.Null && expected is FlowFrameValue.Object) return true
        if (expected is FlowFrameValue.Object && expected.internalName == "java/lang/Object") {
            return actual is FlowFrameValue.Object ||
                actual == FlowFrameValue.Null ||
                actual == FlowFrameValue.UninitializedThis ||
                actual is FlowFrameValue.UninitializedNew
        }
        return false
    }

    private fun isLocalAssignable(actual: FlowFrameValue, expected: FlowFrameValue): Boolean {
        if (expected == FlowFrameValue.Top) return true
        return isAssignable(actual, expected)
    }

    fun hasStackSuffix(frame: FlowFrame, suffix: List<FlowFrameValue>): Boolean {
        if (frame.stack.size < suffix.size) return false
        val actualSuffix = frame.stack.takeLast(suffix.size)
        return actualSuffix.zip(suffix).all { (actual, expected) -> isAssignable(actual, expected) }
    }

    fun dropStackSuffix(frame: FlowFrame, suffixSize: Int): FlowFrame {
        require(frame.stack.size >= suffixSize) { "Cannot drop $suffixSize values from stack of size ${frame.stack.size}" }
        return frame.withStack(frame.stack.dropLast(suffixSize))
    }
}
