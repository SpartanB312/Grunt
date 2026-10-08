package net.spartanb312.grunt.ir.flow.core

/** Per-operation snapshot; public mutable graph collections must not share cached indices. */
internal class FlowGraphIndex(edges: List<FlowEdge>) {
    private val outgoing = mutableMapOf<FlowBlock, MutableList<FlowEdge>>()
    private val incoming = mutableMapOf<FlowBlock, MutableList<FlowEdge>>()
    private val firstByPort = mutableMapOf<FlowBlock, MutableMap<FlowPort, FlowEdge>>()
    private val orderedOutgoing = mutableMapOf<FlowBlock, MutableList<IndexedValue<FlowEdge>>>()

    init {
        edges.forEachIndexed { index, edge ->
            outgoing.getOrPut(edge.from) { mutableListOf() }.add(edge)
            incoming.getOrPut(edge.to) { mutableListOf() }.add(edge)
            firstByPort.getOrPut(edge.from) { mutableMapOf() }.putIfAbsent(edge.port, edge)
            orderedOutgoing.getOrPut(edge.from) { mutableListOf() }.add(IndexedValue(index, edge))
        }
    }

    fun outgoing(block: FlowBlock): List<FlowEdge> = outgoing[block].orEmpty()

    fun incoming(block: FlowBlock): List<FlowEdge> = incoming[block].orEmpty()

    fun edgeFrom(block: FlowBlock, port: FlowPort): FlowEdge? = firstByPort[block]?.get(port)

    fun outgoingInGraphOrder(blocks: Set<FlowBlock>): List<FlowEdge> = blocks
        .flatMap { orderedOutgoing[it].orEmpty() }
        .sortedBy { it.index }
        .map { it.value }
}