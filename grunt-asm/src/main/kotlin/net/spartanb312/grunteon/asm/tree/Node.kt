package net.spartanb312.grunteon.asm.tree

import org.objectweb.asm.TypePath

interface Node {
    val nodeFactory: NodeFactory
}

@Suppress("FunctionName")
interface NodeFactory {
    fun Annotation(
        desc: String = "",
        values: MutableList<Any?> = ArrayList(0)
    ): MutableAnnotationNode

    fun TypeAnnotationNode(
        desc: String = "",
        values: MutableList<Any?> = ArrayList(0),
        typeRef: Int,
        typePath: TypePath? = null
    ): MutableTypeAnnotationNode
}