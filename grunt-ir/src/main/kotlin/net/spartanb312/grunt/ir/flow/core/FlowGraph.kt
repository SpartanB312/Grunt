package net.spartanb312.grunt.ir.flow.core

class FlowBlock(
    val id: FlowBlockId,
    var kind: FlowBlockKind = FlowBlockKind.Original,
    val body: FlowBytecodeSlice = FlowBytecodeSlice(),
    var jump: FlowJump = FlowUnreachableJump,
    var entryFrame: FlowFrame = FlowFrame.Empty,
    var bodyExitFrame: FlowFrame = entryFrame
) {
    override fun toString() = id.toString()
}

enum class FlowBlockKind {
    Original,
    Split,
    Dispatcher,
    FlattenCase,
    StateSet,
    RegionEntry,
    RegionExit,
    Bogus,
    Junk,
    Trap,
    Bridge,
    Synthetic
}

data class FlowEdge(
    val id: FlowEdgeId,
    val from: FlowBlock,
    val port: FlowPort,
    var to: FlowBlock,
    var semantics: FlowEdgeSemantics = FlowEdgeSemantics.Real,
    val flags: MutableSet<FlowEdgeFlag> = mutableSetOf()
) {
    override fun toString() = "${from.id}.${port.displayName} -> ${to.id}"
}

enum class FlowEdgeSemantics {
    Real,
    Bogus,
    OpaqueTrue,
    OpaqueFalse,
    Junk,
    Dispatcher,
    Synthetic
}

enum class FlowEdgeFlag {
    Original,
    Inserted,
    RegionEntry,
    RegionExit,
    LayoutSensitive,
    DoNotMutate
}

data class FlowExceptionRegion(
    val protectedBlocks: MutableSet<FlowBlock>,
    var handler: FlowBlock,
    val catchType: FlowFrameValue.Object? = null,
    val priority: Int = 0
)

data class FlowLayout(
    val order: MutableList<FlowBlock> = mutableListOf()
)

/**
 * Control-flow mutation graph for one JVM method.
 *
 * Blocks own bytecode bodies and jump nodes. Edges are first-class graph
 * objects that connect a jump port to a target block.
 */
class FlowMethod(
    val ownerInternalName: String,
    val name: String,
    val desc: String,
    val blocks: MutableList<FlowBlock> = mutableListOf(),
    val edges: MutableList<FlowEdge> = mutableListOf(),
    val exceptionRegions: MutableList<FlowExceptionRegion> = mutableListOf(),
    val layout: FlowLayout = FlowLayout(blocks.toMutableList()),
    val locals: FlowLocalPool = FlowLocalPool()
) {
    var entry: FlowBlock? = blocks.firstOrNull()

    fun outgoingEdges(block: FlowBlock): List<FlowEdge> {
        return edges.filter { it.from == block }
    }

    fun incomingEdges(block: FlowBlock): List<FlowEdge> {
        return edges.filter { it.to == block }
    }

    fun edgeFrom(block: FlowBlock, port: FlowPort): FlowEdge? {
        return edges.firstOrNull { it.from == block && it.port == port }
    }

    fun successors(block: FlowBlock): List<FlowBlock> {
        return outgoingEdges(block).map { it.to }.distinct()
    }

    fun predecessors(block: FlowBlock): List<FlowBlock> {
        return incomingEdges(block).map { it.from }.distinct()
    }

    fun exceptionSuccessors(block: FlowBlock): List<FlowBlock> {
        return exceptionRegions
            .asSequence()
            .filter { block in it.protectedBlocks }
            .map { it.handler }
            .distinct()
            .toList()
    }

    fun allSuccessors(block: FlowBlock): List<FlowBlock> {
        return (successors(block) + exceptionSuccessors(block)).distinct()
    }

    fun addBlock(block: FlowBlock): FlowBlock {
        blocks += block
        layout.order += block
        if (entry == null) entry = block
        return block
    }

    fun addEdge(edge: FlowEdge): FlowEdge {
        edges += edge
        return edge
    }

    fun planRegions(options: FlowRegionPlanOptions = FlowRegionPlanOptions()): List<FlowRegion> {
        return FlowRegionPlanner.plan(this, options)
    }
}
