package net.spartanb312.grunteon.asm.tree

import org.objectweb.asm.Attribute
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

    fun FieldNode(
        access: Int = 0,
        name: String = "",
        desc: String = "",
        signature: String? = null,
        value: Any? = null,
        visibleAnnotations: MutableList<MutableAnnotationNode> = ArrayList(0),
        invisibleAnnotations: MutableList<MutableAnnotationNode> = ArrayList(0),
        visibleTypeAnnotations: MutableList<MutableTypeAnnotationNode> = ArrayList(0),
        invisibleTypeAnnotations: MutableList<MutableTypeAnnotationNode> = ArrayList(0),
        attrs: MutableList<Attribute> = ArrayList(0)
    ): MutableFieldNode

    fun ParameterNode(
        name: String,
        access: Int
    ): MutableParameterNode

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

        override fun FieldNode(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            value: Any?,
            visibleAnnotations: MutableList<MutableAnnotationNode>,
            invisibleAnnotations: MutableList<MutableAnnotationNode>,
            visibleTypeAnnotations: MutableList<MutableTypeAnnotationNode>,
            invisibleTypeAnnotations: MutableList<MutableTypeAnnotationNode>,
            attrs: MutableList<Attribute>
        ): MutableFieldNode = object : MutableFieldNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var access: Int = access
            override var name: String = name
            override var desc: String = desc
            override var signature: String? = signature
            override var value: Any? = value
            override var visibleAnnotations = visibleAnnotations
            override var invisibleAnnotations = invisibleAnnotations
            override var visibleTypeAnnotations = visibleTypeAnnotations
            override var invisibleTypeAnnotations = invisibleTypeAnnotations
            override var attrs: MutableList<Attribute> = attrs
        }

        override fun ParameterNode(
            name: String,
            access: Int
        ): MutableParameterNode = object : MutableParameterNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var name: String = name
            override var access: Int = access
        }
    }
}