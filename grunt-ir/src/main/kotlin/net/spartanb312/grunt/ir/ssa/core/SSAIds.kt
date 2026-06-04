package net.spartanb312.grunt.ir.ssa.core

/**
 * Stable identifier for a basic block inside one IR construction context.
 *
 * IDs are intentionally small value classes: they are cheap to copy, readable in
 * dumps, and do not imply global identity outside the function/import session
 * that created them.
 */
@JvmInline
value class SSABlockId(val value: Int) {
    override fun toString() = "b$value"
}

/** Stable identifier for an SSA value, including parameters, block args, and instruction results. */
@JvmInline
value class SSAValueId(val value: Int) {
    override fun toString() = "%$value"
}

/** Stable identifier for a named type/function/field symbol owned by the IR. */
@JvmInline
value class SSASymbolId(val value: Int) {
    override fun toString() = "sym$value"
}

/**
 * Identifier for a frontend/backend owned reference.
 *
 * The core IR only keeps this opaque handle. The side table that explains it
 * belongs to the importer/exporter.
 */
@JvmInline
value class SSAExternalRefId(val value: Int) {
    override fun toString() = "ext$value"
}

/** Identifier for a dynamic value/call site. */
@JvmInline
value class SSADynamicSiteId(val value: Int) {
    override fun toString() = "dyn$value"
}

/**
 * Monotonic ID source used while building one IR graph.
 *
 * Keep one allocator per import/build session when deterministic dumps matter.
 */
class SSAIdAllocator {
    private var nextBlockId = 0
    private var nextValueId = 0
    private var nextSymbolId = 0
    private var nextExternalRefId = 0
    private var nextDynamicSiteId = 0

    fun blockId() = SSABlockId(nextBlockId++)

    fun valueId() = SSAValueId(nextValueId++)

    fun symbolId() = SSASymbolId(nextSymbolId++)

    fun externalRefId() = SSAExternalRefId(nextExternalRefId++)

    fun dynamicSiteId() = SSADynamicSiteId(nextDynamicSiteId++)
}
