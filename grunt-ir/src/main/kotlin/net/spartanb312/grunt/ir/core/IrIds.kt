package net.spartanb312.grunt.ir.core

/**
 * Stable identifier for a basic block inside one IR construction context.
 *
 * IDs are intentionally small value classes: they are cheap to copy, readable in
 * dumps, and do not imply global identity outside the function/import session
 * that created them.
 */
@JvmInline
value class IrBlockId(val value: Int) {
    override fun toString() = "b$value"
}

/** Stable identifier for an SSA value, including parameters, block args, and instruction results. */
@JvmInline
value class IrValueId(val value: Int) {
    override fun toString() = "%$value"
}

/** Stable identifier for a named type/function/field symbol owned by the IR. */
@JvmInline
value class IrSymbolId(val value: Int) {
    override fun toString() = "sym$value"
}

/**
 * Identifier for a frontend/backend owned reference.
 *
 * The core IR only keeps this opaque handle. The side table that explains it
 * belongs to the importer/exporter.
 */
@JvmInline
value class IrExternalRefId(val value: Int) {
    override fun toString() = "ext$value"
}

/** Identifier for a dynamic value/call site. */
@JvmInline
value class IrDynamicSiteId(val value: Int) {
    override fun toString() = "dyn$value"
}

/**
 * Monotonic ID source used while building one IR graph.
 *
 * Keep one allocator per import/build session when deterministic dumps matter.
 */
class IrIdAllocator {
    private var nextBlockId = 0
    private var nextValueId = 0
    private var nextSymbolId = 0
    private var nextExternalRefId = 0
    private var nextDynamicSiteId = 0

    fun blockId() = IrBlockId(nextBlockId++)

    fun valueId() = IrValueId(nextValueId++)

    fun symbolId() = IrSymbolId(nextSymbolId++)

    fun externalRefId() = IrExternalRefId(nextExternalRefId++)

    fun dynamicSiteId() = IrDynamicSiteId(nextDynamicSiteId++)
}
