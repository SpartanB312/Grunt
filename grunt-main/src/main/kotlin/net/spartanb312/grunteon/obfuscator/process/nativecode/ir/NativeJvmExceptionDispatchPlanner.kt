package net.spartanb312.grunteon.obfuscator.process.nativecode.ir

/**
 * Builds the exception dispatch shape for full JVM
 * lowering.
 *
 * Each executable instruction already records the try/catch regions active at
 * that bytecode position. The C++ backend can use this plan to append a small
 * exception check after throwable JNI operations:
 *
 *   if (env->ExceptionCheck()) { ... goto L_CATCH_n; }
 *
 * and then emit one dispatcher block per distinct active catch chain.
 */
internal object NativeJvmExceptionDispatchPlanner {

    fun plan(ir: NativeJvmMethodIr): NativeJvmExceptionDispatchPlan {
        if (ir.tryCatchRegions.isEmpty()) {
            return NativeJvmExceptionDispatchPlan(emptyList(), emptyMap())
        }

        val regionsById = ir.tryCatchRegions.associateBy { it.id }
        val dispatchesByCatches = linkedMapOf<List<NativeJvmCatchHandler>, NativeJvmCatchDispatch>()
        val labelsByInstructionIndex = linkedMapOf<Int, String>()

        ir.instructions.forEach { instruction ->
            val catches = instruction.activeTryCatchRegionIds
                .asSequence()
                .mapNotNull { regionsById[it] }
                .filter { it.handlerIndex != null }
                .sortedBy { it.priority }
                .map { region ->
                    NativeJvmCatchHandler(
                        caughtType = region.caughtType,
                        handlerInstructionIndex = region.handlerIndex ?: return@map null
                    )
                }
                .filterNotNull()
                .distinctBy { it.caughtType }
                .toList()

            if (catches.isEmpty()) return@forEach

            val dispatch = dispatchesByCatches.getOrPut(catches) {
                NativeJvmCatchDispatch(
                    label = "L_CATCH_${dispatchesByCatches.size}",
                    catches = catches
                )
            }
            labelsByInstructionIndex[instruction.instructionIndex] = dispatch.label
        }

        return NativeJvmExceptionDispatchPlan(
            dispatches = dispatchesByCatches.values.toList(),
            dispatchLabelByInstructionIndex = labelsByInstructionIndex
        )
    }
}

internal data class NativeJvmExceptionDispatchPlan(
    val dispatches: List<NativeJvmCatchDispatch>,
    val dispatchLabelByInstructionIndex: Map<Int, String>
) {
    val isEmpty: Boolean
        get() = dispatches.isEmpty()

    fun labelFor(instruction: NativeJvmInstruction): String? {
        return dispatchLabelByInstructionIndex[instruction.instructionIndex]
    }
}

internal data class NativeJvmCatchDispatch(
    val label: String,
    val catches: List<NativeJvmCatchHandler>
)

internal data class NativeJvmCatchHandler(
    val caughtType: String?,
    val handlerInstructionIndex: Int
) {
    val isCatchAll: Boolean
        get() = caughtType == null
}
