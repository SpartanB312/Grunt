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

    fun ModuleRequire(
        module: String,
        access: Int,
        version: String?
    ): MutableModuleRequireNode

    fun ModuleExport(
        packaze: String,
        access: Int,
        modules: MutableList<String>
    ): MutableModuleExportNode

    fun ModuleOpen(
        packaze: String,
        access: Int,
        modules: MutableList<String>
    ): MutableModuleOpenNode

    fun ModuleProvide(
        service: String,
        providers: MutableList<String>
    ): MutableModuleProvideNode

    fun Module(
        name: String,
        access: Int,
        version: String?,
        mainClass: String?,
        packages: MutableList<String>,
        requires: MutableList<MutableModuleRequireNode>,
        exports: MutableList<MutableModuleExportNode>,
        opens: MutableList<MutableModuleOpenNode>,
        uses: MutableList<String>,
        provides: MutableList<MutableModuleProvideNode>
    ): MutableModuleNode

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

        override fun ModuleRequire(
            module: String,
            access: Int,
            version: String?
        ): MutableModuleRequireNode = object : MutableModuleRequireNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var module: String = module
            override var access: Int = access
            override var version: String? = version
        }

        override fun ModuleExport(
            packaze: String,
            access: Int,
            modules: MutableList<String>
        ): MutableModuleExportNode = object : MutableModuleExportNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var packaze: String = packaze
            override var access: Int = access
            override var modules: MutableList<String> = modules
        }

        override fun ModuleOpen(
            packaze: String,
            access: Int,
            modules: MutableList<String>
        ): MutableModuleOpenNode = object : MutableModuleOpenNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var packaze: String = packaze
            override var access: Int = access
            override var modules: MutableList<String> = modules
        }

        override fun ModuleProvide(
            service: String,
            providers: MutableList<String>
        ): MutableModuleProvideNode = object : MutableModuleProvideNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var service: String = service
            override var providers: MutableList<String> = providers
        }

        override fun Module(
            name: String,
            access: Int,
            version: String?,
            mainClass: String?,
            packages: MutableList<String>,
            requires: MutableList<MutableModuleRequireNode>,
            exports: MutableList<MutableModuleExportNode>,
            opens: MutableList<MutableModuleOpenNode>,
            uses: MutableList<String>,
            provides: MutableList<MutableModuleProvideNode>
        ): MutableModuleNode = object : MutableModuleNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var name: String = name
            override var access: Int = access
            override var version: String? = version
            override var mainClass: String? = mainClass
            override var packages: MutableList<String> = packages
            override var requires: MutableList<MutableModuleRequireNode> = requires
            override var exports: MutableList<MutableModuleExportNode> = exports
            override var opens: MutableList<MutableModuleOpenNode> = opens
            override var uses: MutableList<String> = uses
            override var provides: MutableList<MutableModuleProvideNode> = provides
        }
    }
}