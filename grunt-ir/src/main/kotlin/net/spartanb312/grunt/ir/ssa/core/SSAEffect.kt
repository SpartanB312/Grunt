package net.spartanb312.grunt.ir.ssa.core

/**
 * Conservative side-effect summary for an instruction.
 *
 * Passes should use this before moving, duplicating, or deleting instructions.
 * The flags are intentionally broad; frontend/backend-specific facts can refine
 * them later, but the core defaults should prefer correctness over cleverness.
 */
data class SSAEffect(
    /** Instruction may throw or otherwise transfer control exceptionally. */
    val mayThrow: Boolean = false,
    /** Instruction reads memory visible to other instructions. */
    val readsMemory: Boolean = false,
    /** Instruction writes memory visible to other instructions. */
    val writesMemory: Boolean = false,
    /** Instruction observes external runtime state outside normal memory. */
    val readsExternalState: Boolean = false,
    /** Instruction mutates external runtime state outside normal memory. */
    val writesExternalState: Boolean = false,
    /** Instruction may call code outside the current IR function. */
    val callsExternal: Boolean = false,
    /** Instruction resolves a late-bound external entity. */
    val resolvesExternal: Boolean = false,
    /** Hard ordering boundary for transformations. */
    val isBarrier: Boolean = false,
    /** Whether duplicating this instruction is semantics-preserving. */
    val canDuplicate: Boolean = true,
    /** Whether moving this instruction across unrelated code is semantics-preserving. */
    val canMove: Boolean = true
) {
    /** Combine effects for a group of operations. */
    fun merge(other: SSAEffect) = SSAEffect(
        mayThrow = mayThrow || other.mayThrow,
        readsMemory = readsMemory || other.readsMemory,
        writesMemory = writesMemory || other.writesMemory,
        readsExternalState = readsExternalState || other.readsExternalState,
        writesExternalState = writesExternalState || other.writesExternalState,
        callsExternal = callsExternal || other.callsExternal,
        resolvesExternal = resolvesExternal || other.resolvesExternal,
        isBarrier = isBarrier || other.isBarrier,
        canDuplicate = canDuplicate && other.canDuplicate,
        canMove = canMove && other.canMove
    )

    companion object {
        /** Pure, total operation with no externally visible effect. */
        val Pure = SSAEffect()

        /** Memory read with conservative movement restrictions. */
        val ReadMemory = SSAEffect(
            readsMemory = true,
            canDuplicate = false,
            canMove = false
        )

        /** Memory write. */
        val WriteMemory = SSAEffect(
            writesMemory = true,
            canDuplicate = false,
            canMove = false
        )

        /** Ordinary call into code not modeled by this IR function. */
        val ExternalCall = SSAEffect(
            mayThrow = true,
            readsExternalState = true,
            writesExternalState = true,
            callsExternal = true,
            canDuplicate = false,
            canMove = false
        )

        /** Late-bound value resolution, for example a dynamic constant. */
        val DynamicResolve = SSAEffect(
            mayThrow = true,
            readsExternalState = true,
            callsExternal = true,
            resolvesExternal = true,
            isBarrier = true,
            canDuplicate = false,
            canMove = false
        )

        /** Late-bound call site invocation. */
        val DynamicCall = SSAEffect(
            mayThrow = true,
            readsExternalState = true,
            writesExternalState = true,
            callsExternal = true,
            resolvesExternal = true,
            isBarrier = true,
            canDuplicate = false,
            canMove = false
        )

        /** Explicit ordering boundary. */
        val Barrier = SSAEffect(
            isBarrier = true,
            canDuplicate = false,
            canMove = false
        )
    }
}
