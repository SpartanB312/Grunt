package net.spartanb312.grunt.ir.flow.core

/** Stable identifier for a block inside one flow method graph. */
@JvmInline
value class FlowBlockId(val value: Int) {
    override fun toString() = "fb$value"
}

/** Stable identifier for an explicit control-flow edge. */
@JvmInline
value class FlowEdgeId(val value: Int) {
    override fun toString() = "fe$value"
}

/** Stable identifier for a region view over a method graph. */
@JvmInline
value class FlowRegionId(val value: Int) {
    override fun toString() = "fr$value"
}

/** Stable identifier for a synthetic new site used by verifier frame values. */
@JvmInline
value class FlowNewSiteId(val value: Int) {
    override fun toString() = "new$value"
}

/** Monotonic ID source used while importing or mutating one flow graph. */
class FlowIdAllocator {
    private var nextBlockId = 0
    private var nextEdgeId = 0
    private var nextRegionId = 0
    private var nextNewSiteId = 0

    fun blockId() = FlowBlockId(nextBlockId++)

    fun edgeId() = FlowEdgeId(nextEdgeId++)

    fun regionId() = FlowRegionId(nextRegionId++)

    fun newSiteId() = FlowNewSiteId(nextNewSiteId++)
}
