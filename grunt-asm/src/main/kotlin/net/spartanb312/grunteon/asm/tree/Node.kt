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

    object Default : NodeFactory {
        override fun Annotation(
            desc: String,
            values: MutableList<Any?>
        ): MutableAnnotationNode = object : MutableAnnotationNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var desc: String = desc
            override var values: MutableList<Any?> = values
        }


        override fun TypeAnnotationNode(
            desc: String,
            values: MutableList<Any?>,
            typeRef: Int,
            typePath: TypePath?
        ): MutableTypeAnnotationNode = object : MutableTypeAnnotationNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var desc: String = desc
            override var values: MutableList<Any?> = values
            override var typeRef: Int = typeRef
            override var typePath: TypePath? = typePath
        }
    }
}