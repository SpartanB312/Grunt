package net.spartanb312.grunteon.asm.tree

import net.spartanb312.grunteon.asm.tree.insn.LabelNode
import org.objectweb.asm.Attribute
import org.objectweb.asm.TypePath

interface Node {
    val nodeFactory: NodeFactory
}

@Suppress("FunctionName")
interface NodeFactory {
    fun Annotation(
        desc: String = "",
        values: MutableList<Any> = ArrayList(0)
    ): MutableAnnotationNode

    fun TypeAnnotationNode(
        desc: String,
        values: MutableList<Any> = ArrayList(0),
        typeRef: Int,
        typePath: TypePath? = null
    ): MutableTypeAnnotationNode

    fun FieldNode(
        access: Int = 0,
        name: String = "",
        desc: String = "",
        signature: String? = null,
        value: Any? = null,
        visibleAnnotations: MutableList<AnnotationNode> = ArrayList(0),
        invisibleAnnotations: MutableList<AnnotationNode> = ArrayList(0),
        visibleTypeAnnotations: MutableList<TypeAnnotationNode> = ArrayList(0),
        invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = ArrayList(0),
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
        requires: MutableList<ModuleRequireNode>,
        exports: MutableList<ModuleExportNode>,
        opens: MutableList<ModuleOpenNode>,
        uses: MutableList<String>,
        provides: MutableList<ModuleProvideNode>
    ): MutableModuleNode

    fun TryCatchBlockNode(
        start: LabelNode,
        end: LabelNode,
        handler: LabelNode,
        type: String?,
        visibleTypeAnnotations: MutableList<TypeAnnotationNode> = ArrayList(0),
        invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = ArrayList(0)
    ): MutableTryCatchBlockNode

    fun LocalVariableNode(
        name: String,
        desc: String,
        signature: String?,
        start: LabelNode,
        end: LabelNode,
        index: Int
    ): MutableLocalVariableNode

    fun LocalVariableAnnotationNode(
        typeRef: Int,
        typePath: TypePath?,
        start: MutableList<LabelNode>,
        end: MutableList<LabelNode>,
        index: MutableList<Int>,
        desc: String,
        visible: Boolean
    ): MutableLocalVariableAnnotationNode

    object Default : NodeFactory {
        override fun Annotation(
            desc: String,
            values: MutableList<Any>
        ): MutableAnnotationNode = object : MutableAnnotationNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var desc: String = desc
            override var values: MutableList<Any> = values
        }

        override fun TypeAnnotationNode(
            desc: String,
            values: MutableList<Any>,
            typeRef: Int,
            typePath: TypePath?
        ): MutableTypeAnnotationNode = object : MutableTypeAnnotationNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var desc: String = desc
            override var values: MutableList<Any> = values
            override var typeRef: Int = typeRef
            override var typePath: TypePath? = typePath
        }

        override fun FieldNode(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            value: Any?,
            visibleAnnotations: MutableList<AnnotationNode>,
            invisibleAnnotations: MutableList<AnnotationNode>,
            visibleTypeAnnotations: MutableList<TypeAnnotationNode>,
            invisibleTypeAnnotations: MutableList<TypeAnnotationNode>,
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
            requires: MutableList<ModuleRequireNode>,
            exports: MutableList<ModuleExportNode>,
            opens: MutableList<ModuleOpenNode>,
            uses: MutableList<String>,
            provides: MutableList<ModuleProvideNode>
        ): MutableModuleNode = object : MutableModuleNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var name: String = name
            override var access: Int = access
            override var version: String? = version
            override var mainClass: String? = mainClass
            override var packages: MutableList<String> = packages
            override var requires: MutableList<ModuleRequireNode> = requires
            override var exports: MutableList<ModuleExportNode> = exports
            override var opens: MutableList<ModuleOpenNode> = opens
            override var uses: MutableList<String> = uses
            override var provides: MutableList<ModuleProvideNode> = provides
        }

        override fun TryCatchBlockNode(
            start: LabelNode,
            end: LabelNode,
            handler: LabelNode,
            type: String?,
            visibleTypeAnnotations: MutableList<TypeAnnotationNode>,
            invisibleTypeAnnotations: MutableList<TypeAnnotationNode>
        ): MutableTryCatchBlockNode = object : MutableTryCatchBlockNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var start: LabelNode = start
            override var end: LabelNode = end
            override var handler: LabelNode = handler
            override var type: String? = type
            override var visibleTypeAnnotations: MutableList<TypeAnnotationNode> = visibleTypeAnnotations
            override var invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = invisibleTypeAnnotations
        }

        override fun LocalVariableNode(
            name: String,
            desc: String,
            signature: String?,
            start: LabelNode,
            end: LabelNode,
            index: Int
        ): MutableLocalVariableNode = object : MutableLocalVariableNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var name: String = name
            override var desc: String = desc
            override var signature: String? = signature
            override var start: LabelNode = start
            override var end: LabelNode = end
            override var index: Int = index
        }

        override fun LocalVariableAnnotationNode(
            typeRef: Int,
            typePath: TypePath?,
            start: MutableList<LabelNode>,
            end: MutableList<LabelNode>,
            index: MutableList<Int>,
            desc: String,
            visible: Boolean
        ): MutableLocalVariableAnnotationNode = object : MutableLocalVariableAnnotationNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override val start: MutableList<LabelNode> = start
            override val end: MutableList<LabelNode> = end
            override val index: MutableList<Int> = index
            override var typeRef: Int = typeRef
            override var typePath: TypePath? = typePath
            override var desc: String = desc
            override val values: MutableList<Any> = ArrayList(0)
        }
    }
}

fun NodeFactory.Annotation(
    src: AnnotationNode,
    desc: String = src.desc,
    values: MutableList<Any> = src.values.toMutableList()
) = Annotation(
    desc,
    values
)

fun NodeFactory.TypeAnnotationNode(
    src: TypeAnnotationNode,
    desc: String = src.desc,
    values: MutableList<Any> = src.values.toMutableList(),
    typeRef: Int = src.typeRef,
    typePath: TypePath? = src.typePath
) = TypeAnnotationNode(
    desc,
    values,
    typeRef,
    typePath
)

fun NodeFactory.FieldNode(
    src: FieldNode,
    access: Int = src.access,
    name: String = src.name,
    desc: String = src.desc,
    signature: String? = src.signature,
    value: Any? = src.value,
    visibleAnnotations: MutableList<AnnotationNode> = src.visibleAnnotations.toMutableList(),
    invisibleAnnotations: MutableList<AnnotationNode> = src.invisibleAnnotations.toMutableList(),
    visibleTypeAnnotations: MutableList<TypeAnnotationNode> = src.visibleTypeAnnotations.toMutableList(),
    invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = src.invisibleTypeAnnotations.toMutableList(),
    attrs: MutableList<Attribute> = src.attrs.toMutableList()
) = FieldNode(
    access,
    name,
    desc,
    signature,
    value,
    visibleAnnotations,
    invisibleAnnotations,
    visibleTypeAnnotations,
    invisibleTypeAnnotations,
    attrs
)

fun NodeFactory.ParameterNode(
    src: ParameterNode,
    name: String = src.name,
    access: Int = src.access
) = ParameterNode(
    name,
    access
)

fun NodeFactory.ModuleRequire(
    src: ModuleRequireNode,
    module: String = src.module,
    access: Int = src.access,
    version: String? = src.version
) = ModuleRequire(
    module,
    access,
    version
)

fun NodeFactory.ModuleExport(
    src: ModuleExportNode,
    packaze: String = src.packaze,
    access: Int = src.access,
    modules: MutableList<String> = src.modules.toMutableList()
) = ModuleExport(
    packaze,
    access,
    modules
)

fun NodeFactory.ModuleOpen(
    src: ModuleOpenNode,
    packaze: String = src.packaze,
    access: Int = src.access,
    modules: MutableList<String> = src.modules.toMutableList()
) = ModuleOpen(
    packaze,
    access,
    modules
)

fun NodeFactory.ModuleProvide(
    src: ModuleProvideNode,
    service: String = src.service,
    providers: MutableList<String> = src.providers.toMutableList()
) = ModuleProvide(
    service,
    providers
)

fun NodeFactory.Module(
    src: ModuleNode,
    name: String = src.name,
    access: Int = src.access,
    version: String? = src.version,
    mainClass: String? = src.mainClass,
    packages: MutableList<String> = src.packages.toMutableList(),
    requires: MutableList<ModuleRequireNode> = src.requires.toMutableList(),
    exports: MutableList<ModuleExportNode> = src.exports.toMutableList(),
    opens: MutableList<ModuleOpenNode> = src.opens.toMutableList(),
    uses: MutableList<String> = src.uses.toMutableList(),
    provides: MutableList<ModuleProvideNode> = src.provides.toMutableList()
) = Module(
    name,
    access,
    version,
    mainClass,
    packages,
    requires,
    exports,
    opens,
    uses,
    provides
)

fun NodeFactory.TryCatchBlockNode(
    src: TryCatchBlockNode,
    start: LabelNode = src.start,
    end: LabelNode = src.end,
    handler: LabelNode = src.handler!!,
    type: String? = src.type,
    visibleTypeAnnotations: MutableList<TypeAnnotationNode> = src.visibleTypeAnnotations.toMutableList(),
    invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = src.invisibleTypeAnnotations.toMutableList()
) = TryCatchBlockNode(
    start,
    end,
    handler,
    type,
    visibleTypeAnnotations,
    invisibleTypeAnnotations
)

fun NodeFactory.LocalVariableNode(
    src: LocalVariableNode,
    name: String = src.name,
    desc: String = src.desc,
    signature: String? = src.signature,
    start: LabelNode = src.start,
    end: LabelNode = src.end,
    index: Int = src.index
) = LocalVariableNode(
    name,
    desc,
    signature,
    start,
    end,
    index
)

fun NodeFactory.LocalVariableAnnotationNode(
    src: LocalVariableAnnotationNode,
    typeRef: Int = src.typeRef,
    typePath: TypePath? = src.typePath,
    start: MutableList<LabelNode> = src.start.toMutableList(),
    end: MutableList<LabelNode> = src.end.toMutableList(),
    index: MutableList<Int> = src.index.toMutableList(),
    desc: String = src.desc,
    visible: Boolean
) = LocalVariableAnnotationNode(
    typeRef,
    typePath,
    start,
    end,
    index,
    desc,
    visible
)