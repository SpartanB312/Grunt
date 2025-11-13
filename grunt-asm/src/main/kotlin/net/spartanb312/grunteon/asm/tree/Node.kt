package net.spartanb312.grunteon.asm.tree

import net.spartanb312.grunteon.asm.tree.insn.IBaseInsnNode
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

    fun InsnList(): MutableInsnList
    fun InsnList(list: InsnList): MutableInsnList

    fun MethodNode(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: MutableList<String>,
        parameters: MutableList<ParameterNode>,
        visibleAnnotations: MutableList<AnnotationNode> = ArrayList(0),
        invisibleAnnotations: MutableList<AnnotationNode> = ArrayList(0),
        visibleTypeAnnotations: MutableList<TypeAnnotationNode> = ArrayList(0),
        invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = ArrayList(0),
        attrs: MutableList<Attribute> = ArrayList(0),
        annotationDefault: Any? = null,
        visibleAnnotableParameterCount: Int = -1,
        visibleParameterAnnotations: MutableList<MutableList<AnnotationNode>> = ArrayList(0),
        invisibleAnnotableParameterCount: Int = -1,
        invisibleParameterAnnotations: MutableList<MutableList<AnnotationNode>> = ArrayList(0),
        instructions: MutableInsnList = InsnList(),
        tryCatchBlocks: MutableList<MutableTryCatchBlockNode> = ArrayList(0),
        maxStack: Int = -1,
        maxLocals: Int = -1,
        localVariables: MutableList<MutableLocalVariableNode> = ArrayList(0),
        visibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode> = ArrayList(0),
        invisibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode> = ArrayList(0)
    ): MutableMethodNode

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
        mainClass: String? = null,
        packages: MutableList<String> = ArrayList(0),
        requires: MutableList<ModuleRequireNode> = ArrayList(0),
        exports: MutableList<ModuleExportNode> = ArrayList(0),
        opens: MutableList<ModuleOpenNode> = ArrayList(0),
        uses: MutableList<String> = ArrayList(0),
        provides: MutableList<ModuleProvideNode> = ArrayList(0)
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

    fun InnerClassNode(
        name: String,
        outerName: String?,
        innerName: String?,
        access: Int
    ): MutableInnerClassNode

    fun RecordComponentNode(
        name: String,
        desc: String,
        signature: String?,
        visibleAnnotations: MutableList<AnnotationNode> = ArrayList(0),
        invisibleAnnotations: MutableList<AnnotationNode> = ArrayList(0),
        visibleTypeAnnotations: MutableList<TypeAnnotationNode> = ArrayList(0),
        invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = ArrayList(0),
        attrs: MutableList<Attribute> = ArrayList(0)
    ): MutableRecordComponentNode

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

        @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
        private class DefaultMutableInsnList(
            private val list: MutableList<IBaseInsnNode> = ArrayList()
        ) : MutableInsnList, List<IBaseInsnNode> by list {
            override fun addTypeAnnotation(
                index: Int,
                typeAnnotationNode: TypeAnnotationNode,
                isVisible: Boolean
            ) {
                TODO("Not yet implemented")
            }

        }

        override fun MethodNode(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            exceptions: MutableList<String>,
            parameters: MutableList<ParameterNode>,
            visibleAnnotations: MutableList<AnnotationNode>,
            invisibleAnnotations: MutableList<AnnotationNode>,
            visibleTypeAnnotations: MutableList<TypeAnnotationNode>,
            invisibleTypeAnnotations: MutableList<TypeAnnotationNode>,
            attrs: MutableList<Attribute>,
            annotationDefault: Any?,
            visibleAnnotableParameterCount: Int,
            visibleParameterAnnotations: MutableList<MutableList<AnnotationNode>>,
            invisibleAnnotableParameterCount: Int,
            invisibleParameterAnnotations: MutableList<MutableList<AnnotationNode>>,
            instructions: MutableInsnList,
            tryCatchBlocks: MutableList<MutableTryCatchBlockNode>,
            maxStack: Int,
            maxLocals: Int,
            localVariables: MutableList<MutableLocalVariableNode>,
            visibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode>,
            invisibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode>
        ): MutableMethodNode = object : MutableMethodNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var access: Int = access
            override var name: String = name
            override var desc: String = desc
            override var signature: String? = signature
            override val exceptions: MutableList<String> = exceptions
            override val parameters: MutableList<ParameterNode> = parameters
            override val visibleAnnotations: MutableList<AnnotationNode> = visibleAnnotations
            override val invisibleAnnotations: MutableList<AnnotationNode> = invisibleAnnotations
            override val visibleTypeAnnotations: MutableList<TypeAnnotationNode> = visibleTypeAnnotations
            override val invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = invisibleTypeAnnotations
            override val attrs: MutableList<Attribute> = attrs
            override var annotationDefault: Any? = annotationDefault
            override var visibleAnnotableParameterCount: Int = visibleAnnotableParameterCount
            override val visibleParameterAnnotations: MutableList<MutableList<AnnotationNode>> =
                visibleParameterAnnotations
            override var invisibleAnnotableParameterCount: Int = invisibleAnnotableParameterCount
            override val invisibleParameterAnnotations: MutableList<MutableList<AnnotationNode>> =
                invisibleParameterAnnotations
            override val instructions: MutableInsnList = instructions
            override val tryCatchBlocks: MutableList<MutableTryCatchBlockNode> = tryCatchBlocks
            override var maxStack: Int = maxStack
            override var maxLocals: Int = maxLocals
            override val localVariables: MutableList<MutableLocalVariableNode> = localVariables
            override val visibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode> =
                visibleLocalVariableAnnotations
            override val invisibleLocalVariableAnnotations: MutableList<LocalVariableAnnotationNode> =
                invisibleLocalVariableAnnotations
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

        override fun InnerClassNode(
            name: String,
            outerName: String?,
            innerName: String?,
            access: Int
        ): MutableInnerClassNode = object : MutableInnerClassNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var name: String = name
            override var outerName: String? = outerName
            override var innerName: String? = innerName
            override var access: Int = access
        }

        override fun RecordComponentNode(
            name: String,
            desc: String,
            signature: String?,
            visibleAnnotations: MutableList<AnnotationNode>,
            invisibleAnnotations: MutableList<AnnotationNode>,
            visibleTypeAnnotations: MutableList<TypeAnnotationNode>,
            invisibleTypeAnnotations: MutableList<TypeAnnotationNode>,
            attrs: MutableList<Attribute>
        ): MutableRecordComponentNode = object : MutableRecordComponentNode {
            override val nodeFactory: NodeFactory
                get() = this@Default
            override var name: String = name
            override var descriptor: String = desc
            override var signature: String? = signature
            override var visibleAnnotations: MutableList<AnnotationNode> = visibleAnnotations
            override var invisibleAnnotations: MutableList<AnnotationNode> = invisibleAnnotations
            override var visibleTypeAnnotations: MutableList<TypeAnnotationNode> = visibleTypeAnnotations
            override var invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = invisibleTypeAnnotations
            override var attrs: MutableList<Attribute> = attrs
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

fun NodeFactory.InnerClassNode(
    src: InnerClassNode,
    name: String = src.name,
    outerName: String? = src.outerName,
    innerName: String? = src.innerName,
    access: Int = src.access
) = InnerClassNode(
    name,
    outerName,
    innerName,
    access
)

fun NodeFactory.RecordComponentNode(
    src: RecordComponentNode,
    name: String = src.name,
    desc: String = src.descriptor,
    signature: String? = src.signature,
    visibleAnnotations: MutableList<AnnotationNode> = src.visibleAnnotations.toMutableList(),
    invisibleAnnotations: MutableList<AnnotationNode> = src.invisibleAnnotations.toMutableList(),
    visibleTypeAnnotations: MutableList<TypeAnnotationNode> = src.visibleTypeAnnotations.toMutableList(),
    invisibleTypeAnnotations: MutableList<TypeAnnotationNode> = src.invisibleTypeAnnotations.toMutableList(),
    attrs: MutableList<Attribute> = src.attrs.toMutableList()
) = RecordComponentNode(
    name,
    desc,
    signature,
    visibleAnnotations,
    invisibleAnnotations,
    visibleTypeAnnotations,
    invisibleTypeAnnotations,
    attrs
)