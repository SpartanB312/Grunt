package net.spartanb312.grunteon.asm.tree

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.asm.tree.insn.*
import org.objectweb.asm.Attribute
import org.objectweb.asm.Handle
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

        override fun InsnList(): MutableInsnList = DefaultMutableInsnList()

        override fun InsnList(list: InsnList): MutableInsnList = DefaultMutableInsnList(ArrayList(list))

        @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
        private class DefaultMutableInsnList(
            private val list: MutableList<IBaseInsnNode> = ArrayList()
        ) : MutableInsnList, List<IBaseInsnNode> by list {
            private val visibleTypeAnnotations = Int2ObjectOpenHashMap<ObjectArrayList<TypeAnnotationNode>>()
            private val invisibleTypeAnnotations = Int2ObjectOpenHashMap<ObjectArrayList<TypeAnnotationNode>>()

            override fun addTypeAnnotation(
                index: Int,
                typeAnnotationNode: TypeAnnotationNode,
                isVisible: Boolean
            ) {
                val annotationMap = if (isVisible) visibleTypeAnnotations else invisibleTypeAnnotations
                annotationMap.computeIfAbsent(index) { ObjectArrayList() }.add(typeAnnotationNode)
            }

            override fun GETSTATIC(owner: String, name: String, desc: String) {
                list.add(
                    DefaultGetStaticInsnNode(
                        list.size,
                        owner,
                        name,
                        desc
                    )
                )
            }

            override fun PUTSTATIC(owner: String, name: String, desc: String) {
                list.add(
                    DefaultPutStaticInsnNode(
                        list.size,
                        owner,
                        name,
                        desc
                    )
                )
            }

            override fun GETFIELD(owner: String, name: String, desc: String) {
                list.add(
                    DefaultGetFieldInsnNode(
                        list.size,
                        owner,
                        name,
                        desc
                    )
                )
            }

            override fun PUTFIELD(owner: String, name: String, desc: String) {
                list.add(
                    DefaultPutFieldInsnNode(
                        list.size,
                        owner,
                        name,
                        desc
                    )
                )
            }

            override fun F_NEW(local: List<Any>, stack: List<Any>) {
                list.add(
                    DefaultFNewNode(
                        list.size,
                        local,
                        stack
                    )
                )
            }

            override fun F_FULL(local: List<Any>, stack: List<Any>) {
                list.add(
                    DefaultFFullNode(
                        list.size,
                        local,
                        stack
                    )
                )
            }

            override fun F_APPEND(local: List<Any>) {
                list.add(
                    DefaultFAppendNode(
                        list.size,
                        local
                    )
                )
            }

            override fun F_CHOP(local: List<Any>) {
                list.add(
                    DefaultFChopNode(
                        list.size,
                        local
                    )
                )
            }

            override fun F_SAME() {
                list.add(
                    DefaultFSameNode(
                        list.size
                    )
                )
            }

            override fun F_SAME1(stack: List<Any>) {
                list.add(
                    DefaultFSame1Node(
                        list.size,
                        stack
                    )
                )
            }

            override fun IINC(variable: Int, increment: Int) {
                list.add(
                    DefaultIincInsnNode(
                        list.size,
                        variable,
                        increment
                    )
                )
            }

            override fun INVOKEVIRTUAL(owner: String, name: String, desc: String) {
                list.add(
                    DefaultInvokeVirtualInsnNode(
                        list.size,
                        owner,
                        name,
                        desc
                    )
                )
            }

            override fun INVOKESPECIAL(owner: String, name: String, desc: String) {
                list.add(
                    DefaultInvokeSpecialInsnNode(
                        list.size,
                        owner,
                        name,
                        desc
                    )
                )
            }

            override fun INVOKESTATIC(owner: String, name: String, desc: String) {
                list.add(
                    DefaultInvokeStaticInsnNode(
                        list.size,
                        owner,
                        name,
                        desc
                    )
                )
            }

            override fun INVOKEINTERFACE(owner: String, name: String, desc: String) {
                list.add(
                    DefaultInvokeInterfaceInsnNode(
                        list.size,
                        owner,
                        name,
                        desc
                    )
                )
            }

            override fun INVOKEDYNAMIC(name: String, desc: String, bsm: Handle, bsmArgs: List<Any>) {
                list.add(
                    DefaultInvokeDynamicInsnNode(
                        list.size,
                        name,
                        desc,
                        bsm,
                        bsmArgs.toMutableList()
                    )
                )
            }

            override fun NOP() {
                list.add(DefaultNopInsnNode(list.size))
            }

            override fun ACONST_NULL() {
                list.add(DefaultAConstNullInsnNode(list.size))
            }

            override fun ICONST_M1() {
                list.add(DefaultIConstM1InsnNode(list.size))
            }

            override fun ICONST_0() {
                list.add(DefaultIConst0InsnNode(list.size))
            }

            override fun ICONST_1() {
                list.add(DefaultIConst1InsnNode(list.size))
            }

            override fun ICONST_2() {
                list.add(DefaultIConst2InsnNode(list.size))
            }

            override fun ICONST_3() {
                list.add(DefaultIConst3InsnNode(list.size))
            }

            override fun ICONST_4() {
                list.add(DefaultIConst4InsnNode(list.size))
            }

            override fun ICONST_5() {
                list.add(DefaultIConst5InsnNode(list.size))
            }

            override fun LCONST_0() {
                list.add(DefaultLConst0InsnNode(list.size))
            }

            override fun LCONST_1() {
                list.add(DefaultLConst1InsnNode(list.size))
            }

            override fun FCONST_0() {
                list.add(DefaultFConst0InsnNode(list.size))
            }

            override fun FCONST_1() {
                list.add(DefaultFConst1InsnNode(list.size))
            }

            override fun FCONST_2() {
                list.add(DefaultFConst2InsnNode(list.size))
            }

            override fun DCONST_0() {
                list.add(DefaultDConst0InsnNode(list.size))
            }

            override fun DCONST_1() {
                list.add(DefaultDConst1InsnNode(list.size))
            }

            override fun IALOAD() {
                list.add(DefaultIALoadInsnNode(list.size))
            }

            override fun LALOAD() {
                list.add(DefaultLALoadInsnNode(list.size))
            }

            override fun FALOAD() {
                list.add(DefaultFALoadInsnNode(list.size))
            }

            override fun DALOAD() {
                list.add(DefaultDALoadInsnNode(list.size))
            }

            override fun AALOAD() {
                list.add(DefaultAALoadInsnNode(list.size))
            }

            override fun BALOAD() {
                list.add(DefaultBALoadInsnNode(list.size))
            }

            override fun CALOAD() {
                list.add(DefaultCALoadInsnNode(list.size))
            }

            override fun SALOAD() {
                list.add(DefaultSALoadInsnNode(list.size))
            }

            override fun IASTORE() {
                list.add(DefaultIAStoreInsnNode(list.size))
            }

            override fun LASTORE() {
                list.add(DefaultLAStoreInsnNode(list.size))
            }

            override fun FASTORE() {
                list.add(DefaultFAStoreInsnNode(list.size))
            }

            override fun DASTORE() {
                list.add(DefaultDAStoreInsnNode(list.size))
            }

            override fun AASTORE() {
                list.add(DefaultAAStoreInsnNode(list.size))
            }

            override fun BASTORE() {
                list.add(DefaultBAStoreInsnNode(list.size))
            }

            override fun CASTORE() {
                list.add(DefaultCAStoreInsnNode(list.size))
            }

            override fun SASTORE() {
                list.add(DefaultSAStoreInsnNode(list.size))
            }

            override fun POP() {
                list.add(DefaultPopInsnNode(list.size))
            }

            override fun POP2() {
                list.add(DefaultPop2InsnNode(list.size))
            }

            override fun DUP() {
                list.add(DefaultDupInsnNode(list.size))
            }

            override fun DUP_X1() {
                list.add(DefaultDupX1InsnNode(list.size))
            }

            override fun DUP_X2() {
                list.add(DefaultDupX2InsnNode(list.size))
            }

            override fun DUP2() {
                list.add(DefaultDup2InsnNode(list.size))
            }

            override fun DUP2_X1() {
                list.add(DefaultDup2X1InsnNode(list.size))
            }

            override fun DUP2_X2() {
                list.add(DefaultDup2X2InsnNode(list.size))
            }

            override fun SWAP() {
                list.add(DefaultSwapInsnNode(list.size))
            }

            override fun IADD() {
                list.add(DefaultIAddInsnNode(list.size))
            }

            override fun LADD() {
                list.add(DefaultLAddInsnNode(list.size))
            }

            override fun FADD() {
                list.add(DefaultFAddInsnNode(list.size))
            }

            override fun DADD() {
                list.add(DefaultDAddInsnNode(list.size))
            }

            override fun ISUB() {
                list.add(DefaultISubInsnNode(list.size))
            }

            override fun LSUB() {
                list.add(DefaultLSubInsnNode(list.size))
            }

            override fun FSUB() {
                list.add(DefaultFSubInsnNode(list.size))
            }

            override fun DSUB() {
                list.add(DefaultDSubInsnNode(list.size))
            }

            override fun IMUL() {
                list.add(DefaultIMulInsnNode(list.size))
            }

            override fun LMUL() {
                list.add(DefaultLMulInsnNode(list.size))
            }

            override fun FMUL() {
                list.add(DefaultFMulInsnNode(list.size))
            }

            override fun DMUL() {
                list.add(DefaultDMulInsnNode(list.size))
            }

            override fun IDIV() {
                list.add(DefaultIDivInsnNode(list.size))
            }

            override fun LDIV() {
                list.add(DefaultLDivInsnNode(list.size))
            }

            override fun FDIV() {
                list.add(DefaultFDivInsnNode(list.size))
            }

            override fun DDIV() {
                list.add(DefaultDDivInsnNode(list.size))
            }

            override fun IREM() {
                list.add(DefaultIRemInsnNode(list.size))
            }

            override fun LREM() {
                list.add(DefaultLRemInsnNode(list.size))
            }

            override fun FREM() {
                list.add(DefaultFRemInsnNode(list.size))
            }

            override fun DREM() {
                list.add(DefaultDRemInsnNode(list.size))
            }

            override fun INEG() {
                list.add(DefaultINegInsnNode(list.size))
            }

            override fun LNEG() {
                list.add(DefaultLNegInsnNode(list.size))
            }

            override fun FNEG() {
                list.add(DefaultFNegInsnNode(list.size))
            }

            override fun DNEG() {
                list.add(DefaultDNegInsnNode(list.size))
            }

            override fun ISHL() {
                list.add(DefaultIShlInsnNode(list.size))
            }

            override fun LSHL() {
                list.add(DefaultLShlInsnNode(list.size))
            }

            override fun ISHR() {
                list.add(DefaultIShrInsnNode(list.size))
            }

            override fun LSHR() {
                list.add(DefaultLShrInsnNode(list.size))
            }

            override fun IUSHR() {
                list.add(DefaultIUshrInsnNode(list.size))
            }

            override fun LUSHR() {
                list.add(DefaultLUshrInsnNode(list.size))
            }

            override fun IAND() {
                list.add(DefaultIAndInsnNode(list.size))
            }

            override fun LAND() {
                list.add(DefaultLAndInsnNode(list.size))
            }

            override fun IOR() {
                list.add(DefaultIOrInsnNode(list.size))
            }

            override fun LOR() {
                list.add(DefaultLOrInsnNode(list.size))
            }

            override fun IXOR() {
                list.add(DefaultIXorInsnNode(list.size))
            }

            override fun LXOR() {
                list.add(DefaultLXorInsnNode(list.size))
            }

            override fun I2L() {
                list.add(DefaultI2LInsnNode(list.size))
            }

            override fun I2F() {
                list.add(DefaultI2FInsnNode(list.size))
            }

            override fun I2D() {
                list.add(DefaultI2DInsnNode(list.size))
            }

            override fun L2I() {
                list.add(DefaultL2IInsnNode(list.size))
            }

            override fun L2F() {
                list.add(DefaultL2FInsnNode(list.size))
            }

            override fun L2D() {
                list.add(DefaultL2DInsnNode(list.size))
            }

            override fun F2I() {
                list.add(DefaultF2IInsnNode(list.size))
            }

            override fun F2L() {
                list.add(DefaultF2LInsnNode(list.size))
            }

            override fun F2D() {
                list.add(DefaultF2DInsnNode(list.size))
            }

            override fun D2I() {
                list.add(DefaultD2IInsnNode(list.size))
            }

            override fun D2L() {
                list.add(DefaultD2LInsnNode(list.size))
            }

            override fun D2F() {
                list.add(DefaultD2FInsnNode(list.size))
            }

            override fun I2B() {
                list.add(DefaultI2BInsnNode(list.size))
            }

            override fun I2C() {
                list.add(DefaultI2CInsnNode(list.size))
            }

            override fun I2S() {
                list.add(DefaultI2SInsnNode(list.size))
            }

            override fun LCMP() {
                list.add(DefaultLCmpInsnNode(list.size))
            }

            override fun FCMPL() {
                list.add(DefaultFCmplInsnNode(list.size))
            }

            override fun FCMPG() {
                list.add(DefaultFCmpgInsnNode(list.size))
            }

            override fun DCMPL() {
                list.add(DefaultDCmplInsnNode(list.size))
            }

            override fun DCMPG() {
                list.add(DefaultDCmpgInsnNode(list.size))
            }

            override fun IRETURN() {
                list.add(DefaultIReturnInsnNode(list.size))
            }

            override fun LRETURN() {
                list.add(DefaultLReturnInsnNode(list.size))
            }

            override fun FRETURN() {
                list.add(DefaultFReturnInsnNode(list.size))
            }

            override fun DRETURN() {
                list.add(DefaultDReturnInsnNode(list.size))
            }

            override fun ARETURN() {
                list.add(DefaultAReturnInsnNode(list.size))
            }

            override fun RETURN() {
                list.add(DefaultReturnInsnNode(list.size))
            }

            override fun ARRAYLENGTH() {
                list.add(DefaultArrayLengthInsnNode(list.size))
            }

            override fun ATHROW() {
                list.add(DefaultAThrowInsnNode(list.size))
            }

            override fun MONITORENTER() {
                list.add(DefaultMonitorEnterInsnNode(list.size))
            }

            override fun MONITOREXIT() {
                list.add(DefaultMonitorExitInsnNode(list.size))
            }

            override fun BIPUSH(byte: Byte) {
                list.add(DefaultBiPushInsnNode(list.size, byte.toInt()))
            }

            override fun SIPUSH(short: Short) {
                list.add(DefaultSiPushInsnNode(list.size, short.toInt()))
            }

            override fun NEWARRAY(arrayType: NewArrayInsnNode.NewArrayType) {
                list.add(DefaultNewArrayInsnNode(list.size, arrayType.value))
            }

            override fun IFEQ(label: LabelNode) {
                list.add(DefaultIfEqInsnNode(list.size, label))
            }

            override fun IFNE(label: LabelNode) {
                list.add(DefaultIfNeInsnNode(list.size, label))
            }

            override fun IFLT(label: LabelNode) {
                list.add(DefaultIfLtInsnNode(list.size, label))
            }

            override fun IFGE(label: LabelNode) {
                list.add(DefaultIfGeInsnNode(list.size, label))
            }

            override fun IFGT(label: LabelNode) {
                list.add(DefaultIfGtInsnNode(list.size, label))
            }

            override fun IFLE(label: LabelNode) {
                list.add(DefaultIfLeInsnNode(list.size, label))
            }

            override fun IF_ICMPEQ(label: LabelNode) {
                list.add(DefaultIfIcmpEqInsnNode(list.size, label))
            }

            override fun IF_ICMPNE(label: LabelNode) {
                list.add(DefaultIfIcmpNeInsnNode(list.size, label))
            }

            override fun IF_ICMPLT(label: LabelNode) {
                list.add(DefaultIfIcmpLtInsnNode(list.size, label))
            }

            override fun IF_ICMPGE(label: LabelNode) {
                list.add(DefaultIfIcmpGeInsnNode(list.size, label))
            }

            override fun IF_ICMPGT(label: LabelNode) {
                list.add(DefaultIfIcmpGtInsnNode(list.size, label))
            }

            override fun IF_ICMPLE(label: LabelNode) {
                list.add(DefaultIfIcmpLeInsnNode(list.size, label))
            }

            override fun IF_ACMPEQ(label: LabelNode) {
                list.add(DefaultIfAcmpEqInsnNode(list.size, label))
            }

            override fun IF_ACMPNE(label: LabelNode) {
                list.add(DefaultIfAcmpNeInsnNode(list.size, label))
            }

            override fun GOTO(label: LabelNode) {
                list.add(DefaultGotoInsnNode(list.size, label))
            }

            override fun IFNULL(label: LabelNode) {
                list.add(DefaultIfNullInsnNode(list.size, label))
            }

            override fun IFNONNULL(label: LabelNode) {
                list.add(DefaultIfNonNullInsnNode(list.size, label))
            }

            override fun createLabelNode(): LabelNode {
                return DefaultLabelNode(-1)
            }

            override fun LABEL(labelNode: LabelNode) {
                labelNode as DefaultLabelNode
                labelNode.index = list.size
                list.add(labelNode)
            }

            override fun LDC(constant: Any) {
                list.add(DefaultLdcInsnNode(list.size, constant))
            }

            override fun LINE(line: Int, label: LabelNode) {
                list.add(DefaultLineNumberNode(list.size, line, label))
            }

            override fun LOOKUPSWITCH(dflt: LabelNode, keys: List<Int>, labels: List<LabelNode>) {
                list.add(
                    DefaultLookupSwitchInsnNode(
                        list.size,
                        dflt,
                        keys.toMutableList(),
                        labels.toMutableList()
                    )
                )
            }

            override fun TABLESWITCH(min: Int, max: Int, dflt: LabelNode, labels: List<LabelNode>) {
                list.add(
                    DefaultTableSwitchInsnNode(
                        list.size,
                        min,
                        max,
                        dflt,
                        labels.toMutableList()
                    )
                )
            }

            override fun MULTIANEWARRAY(type: String, dims: Int) {
                list.add(DefaultMultiANewArrayInsnNode(list.size, type, dims))
            }

            override fun NEW(type: String) {
                list.add(DefaultNewInsnNode(list.size, type))
            }

            override fun ANEWARRAY(type: String) {
                list.add(DefaultANewArrayInsnNode(list.size, type))
            }

            override fun CHECKCAST(type: String) {
                list.add(DefaultCheckCastInsnNode(list.size, type))
            }

            override fun INSTANCEOF(type: String) {
                list.add(DefaultInstanceOfInsnNode(list.size, type))
            }

            override fun ILOAD(variable: Int) {
                list.add(DefaultILoadInsnNode(list.size, variable))
            }

            override fun LLOAD(variable: Int) {
                list.add(DefaultLLoadInsnNode(list.size, variable))
            }

            override fun FLOAD(variable: Int) {
                list.add(DefaultFLoadInsnNode(list.size, variable))
            }

            override fun DLOAD(variable: Int) {
                list.add(DefaultDLoadInsnNode(list.size, variable))
            }

            override fun ALOAD(variable: Int) {
                list.add(DefaultALoadInsnNode(list.size, variable))
            }

            override fun ISTORE(variable: Int) {
                list.add(DefaultIStoreInsnNode(list.size, variable))
            }

            override fun LSTORE(variable: Int) {
                list.add(DefaultLStoreInsnNode(list.size, variable))
            }

            override fun FSTORE(variable: Int) {
                list.add(DefaultFStoreInsnNode(list.size, variable))
            }

            override fun DSTORE(variable: Int) {
                list.add(DefaultDStoreInsnNode(list.size, variable))
            }

            override fun ASTORE(variable: Int) {
                list.add(DefaultAStoreInsnNode(list.size, variable))
            }

            private abstract inner class TypeAnnotatableImpl(var index: Int) : TypeAnnotatable {
                override val visibleTypeAnnotations: List<TypeAnnotationNode>
                    get() = this@DefaultMutableInsnList.visibleTypeAnnotations[index] ?: EMPTY_TYPE_ANNOTATION_LIST
                override val invisibleTypeAnnotations: List<TypeAnnotationNode>
                    get() = this@DefaultMutableInsnList.invisibleTypeAnnotations[index] ?: EMPTY_TYPE_ANNOTATION_LIST
            }

            inner class DefaultLabelNode(index: Int) : TypeAnnotatableImpl(index), LabelNode

            inner class DefaultTableSwitchInsnNode(
                index: Int,
                override var min: Int,
                override var max: Int,
                override var dflt: LabelNode,
                override val labels: MutableList<LabelNode>
            ) : TypeAnnotatableImpl(index), TableSwitchInsnNode

            inner class DefaultBiPushInsnNode(
                index: Int,
                override val operand: Int
            ) : TypeAnnotatableImpl(index), BiPushInsnNode

            inner class DefaultSiPushInsnNode(
                index: Int,
                override val operand: Int
            ) : TypeAnnotatableImpl(index), SiPushInsnNode

            inner class DefaultNewArrayInsnNode(
                index: Int,
                override val operand: Int
            ) : TypeAnnotatableImpl(index), NewArrayInsnNode

            inner class DefaultILoadInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), ILoadInsnNode

            inner class DefaultLLoadInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), LLoadInsnNode

            inner class DefaultFLoadInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), FLoadInsnNode

            inner class DefaultDLoadInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), DLoadInsnNode

            inner class DefaultALoadInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), ALoadInsnNode

            inner class DefaultIStoreInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), IStoreInsnNode

            inner class DefaultLStoreInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), LStoreInsnNode

            inner class DefaultFStoreInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), FStoreInsnNode

            inner class DefaultDStoreInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), DStoreInsnNode

            inner class DefaultAStoreInsnNode(
                index: Int,
                override val variable: Int
            ) : TypeAnnotatableImpl(index), AStoreInsnNode

            inner class DefaultGetStaticInsnNode(
                index: Int,
                override val owner: String,
                override val name: String,
                override val desc: String,
            ) : TypeAnnotatableImpl(index), GetStaticInsnNode

            inner class DefaultPutStaticInsnNode(
                index: Int,
                override val owner: String,
                override val name: String,
                override val desc: String,
            ) : TypeAnnotatableImpl(index), PutStaticInsnNode

            inner class DefaultGetFieldInsnNode(
                index: Int,
                override val owner: String,
                override val name: String,
                override val desc: String,
            ) : TypeAnnotatableImpl(index), GetFieldInsnNode

            inner class DefaultPutFieldInsnNode(
                index: Int,
                override val owner: String,
                override val name: String,
                override val desc: String,
            ) : TypeAnnotatableImpl(index), PutFieldInsnNode

            inner class DefaultIincInsnNode(
                index: Int,
                override val variable: Int,
                override val increment: Int
            ) : TypeAnnotatableImpl(index), IincInsnNode

            inner class DefaultFNewNode(
                index: Int,
                override val local: List<Any>,
                override val stack: List<Any>,
            ) : TypeAnnotatableImpl(index), FNewNode

            inner class DefaultFFullNode(
                index: Int,
                override val local: List<Any>,
                override val stack: List<Any>,
            ) : TypeAnnotatableImpl(index), FFullNode

            inner class DefaultFAppendNode(
                index: Int,
                override val local: List<Any>,
            ) : TypeAnnotatableImpl(index), FAppendNode

            inner class DefaultFChopNode(
                index: Int,
                override val local: List<Any>,
            ) : TypeAnnotatableImpl(index), FChopNode

            inner class DefaultFSameNode(
                index: Int,
            ) : TypeAnnotatableImpl(index), FSameNode

            inner class DefaultFSame1Node(
                index: Int,
                override val stack: List<Any>,
            ) : TypeAnnotatableImpl(index), FSame1Node

            inner class DefaultNopInsnNode(index: Int) : TypeAnnotatableImpl(index), NopInsnNode
            inner class DefaultAConstNullInsnNode(index: Int) : TypeAnnotatableImpl(index), AConstNullInsnNode
            inner class DefaultIConstM1InsnNode(index: Int) : TypeAnnotatableImpl(index), IConstM1InsnNode
            inner class DefaultIConst0InsnNode(index: Int) : TypeAnnotatableImpl(index), IConst0InsnNode
            inner class DefaultIConst1InsnNode(index: Int) : TypeAnnotatableImpl(index), IConst1InsnNode
            inner class DefaultIConst2InsnNode(index: Int) : TypeAnnotatableImpl(index), IConst2InsnNode
            inner class DefaultIConst3InsnNode(index: Int) : TypeAnnotatableImpl(index), IConst3InsnNode
            inner class DefaultIConst4InsnNode(index: Int) : TypeAnnotatableImpl(index), IConst4InsnNode
            inner class DefaultIConst5InsnNode(index: Int) : TypeAnnotatableImpl(index), IConst5InsnNode
            inner class DefaultLConst0InsnNode(index: Int) : TypeAnnotatableImpl(index), LConst0InsnNode
            inner class DefaultLConst1InsnNode(index: Int) : TypeAnnotatableImpl(index), LConst1InsnNode
            inner class DefaultFConst0InsnNode(index: Int) : TypeAnnotatableImpl(index), FConst0InsnNode
            inner class DefaultFConst1InsnNode(index: Int) : TypeAnnotatableImpl(index), FConst1InsnNode
            inner class DefaultFConst2InsnNode(index: Int) : TypeAnnotatableImpl(index), FConst2InsnNode
            inner class DefaultDConst0InsnNode(index: Int) : TypeAnnotatableImpl(index), DConst0InsnNode
            inner class DefaultDConst1InsnNode(index: Int) : TypeAnnotatableImpl(index), DConst1InsnNode
            inner class DefaultIALoadInsnNode(index: Int) : TypeAnnotatableImpl(index), IALoadInsnNode
            inner class DefaultLALoadInsnNode(index: Int) : TypeAnnotatableImpl(index), LALoadInsnNode
            inner class DefaultFALoadInsnNode(index: Int) : TypeAnnotatableImpl(index), FALoadInsnNode
            inner class DefaultDALoadInsnNode(index: Int) : TypeAnnotatableImpl(index), DALoadInsnNode
            inner class DefaultAALoadInsnNode(index: Int) : TypeAnnotatableImpl(index), AALoadInsnNode
            inner class DefaultBALoadInsnNode(index: Int) : TypeAnnotatableImpl(index), BALoadInsnNode
            inner class DefaultCALoadInsnNode(index: Int) : TypeAnnotatableImpl(index), CALoadInsnNode
            inner class DefaultSALoadInsnNode(index: Int) : TypeAnnotatableImpl(index), SALoadInsnNode
            inner class DefaultIAStoreInsnNode(index: Int) : TypeAnnotatableImpl(index), IAStoreInsnNode
            inner class DefaultLAStoreInsnNode(index: Int) : TypeAnnotatableImpl(index), LAStoreInsnNode
            inner class DefaultFAStoreInsnNode(index: Int) : TypeAnnotatableImpl(index), FAStoreInsnNode
            inner class DefaultDAStoreInsnNode(index: Int) : TypeAnnotatableImpl(index), DAStoreInsnNode
            inner class DefaultAAStoreInsnNode(index: Int) : TypeAnnotatableImpl(index), AAStoreInsnNode
            inner class DefaultBAStoreInsnNode(index: Int) : TypeAnnotatableImpl(index), BAStoreInsnNode
            inner class DefaultCAStoreInsnNode(index: Int) : TypeAnnotatableImpl(index), CAStoreInsnNode
            inner class DefaultSAStoreInsnNode(index: Int) : TypeAnnotatableImpl(index), SAStoreInsnNode
            inner class DefaultPopInsnNode(index: Int) : TypeAnnotatableImpl(index), PopInsnNode
            inner class DefaultPop2InsnNode(index: Int) : TypeAnnotatableImpl(index), Pop2InsnNode
            inner class DefaultDupInsnNode(index: Int) : TypeAnnotatableImpl(index), DupInsnNode
            inner class DefaultDupX1InsnNode(index: Int) : TypeAnnotatableImpl(index), DupX1InsnNode
            inner class DefaultDupX2InsnNode(index: Int) : TypeAnnotatableImpl(index), DupX2InsnNode
            inner class DefaultDup2InsnNode(index: Int) : TypeAnnotatableImpl(index), Dup2InsnNode
            inner class DefaultDup2X1InsnNode(index: Int) : TypeAnnotatableImpl(index), Dup2X1InsnNode
            inner class DefaultDup2X2InsnNode(index: Int) : TypeAnnotatableImpl(index), Dup2X2InsnNode
            inner class DefaultSwapInsnNode(index: Int) : TypeAnnotatableImpl(index), SwapInsnNode
            inner class DefaultIAddInsnNode(index: Int) : TypeAnnotatableImpl(index), IAddInsnNode
            inner class DefaultLAddInsnNode(index: Int) : TypeAnnotatableImpl(index), LAddInsnNode
            inner class DefaultFAddInsnNode(index: Int) : TypeAnnotatableImpl(index), FAddInsnNode
            inner class DefaultDAddInsnNode(index: Int) : TypeAnnotatableImpl(index), DAddInsnNode
            inner class DefaultISubInsnNode(index: Int) : TypeAnnotatableImpl(index), ISubInsnNode
            inner class DefaultLSubInsnNode(index: Int) : TypeAnnotatableImpl(index), LSubInsnNode
            inner class DefaultFSubInsnNode(index: Int) : TypeAnnotatableImpl(index), FSubInsnNode
            inner class DefaultDSubInsnNode(index: Int) : TypeAnnotatableImpl(index), DSubInsnNode
            inner class DefaultIMulInsnNode(index: Int) : TypeAnnotatableImpl(index), IMulInsnNode
            inner class DefaultLMulInsnNode(index: Int) : TypeAnnotatableImpl(index), LMulInsnNode
            inner class DefaultFMulInsnNode(index: Int) : TypeAnnotatableImpl(index), FMulInsnNode
            inner class DefaultDMulInsnNode(index: Int) : TypeAnnotatableImpl(index), DMulInsnNode
            inner class DefaultIDivInsnNode(index: Int) : TypeAnnotatableImpl(index), IDivInsnNode
            inner class DefaultLDivInsnNode(index: Int) : TypeAnnotatableImpl(index), LDivInsnNode
            inner class DefaultFDivInsnNode(index: Int) : TypeAnnotatableImpl(index), FDivInsnNode
            inner class DefaultDDivInsnNode(index: Int) : TypeAnnotatableImpl(index), DDivInsnNode
            inner class DefaultIRemInsnNode(index: Int) : TypeAnnotatableImpl(index), IRemInsnNode
            inner class DefaultLRemInsnNode(index: Int) : TypeAnnotatableImpl(index), LRemInsnNode
            inner class DefaultFRemInsnNode(index: Int) : TypeAnnotatableImpl(index), FRemInsnNode
            inner class DefaultDRemInsnNode(index: Int) : TypeAnnotatableImpl(index), DRemInsnNode
            inner class DefaultINegInsnNode(index: Int) : TypeAnnotatableImpl(index), INegInsnNode
            inner class DefaultLNegInsnNode(index: Int) : TypeAnnotatableImpl(index), LNegInsnNode
            inner class DefaultFNegInsnNode(index: Int) : TypeAnnotatableImpl(index), FNegInsnNode
            inner class DefaultDNegInsnNode(index: Int) : TypeAnnotatableImpl(index), DNegInsnNode
            inner class DefaultIShlInsnNode(index: Int) : TypeAnnotatableImpl(index), IShlInsnNode
            inner class DefaultLShlInsnNode(index: Int) : TypeAnnotatableImpl(index), LShlInsnNode
            inner class DefaultIShrInsnNode(index: Int) : TypeAnnotatableImpl(index), IShrInsnNode
            inner class DefaultLShrInsnNode(index: Int) : TypeAnnotatableImpl(index), LShrInsnNode
            inner class DefaultIUshrInsnNode(index: Int) : TypeAnnotatableImpl(index), IUshrInsnNode
            inner class DefaultLUshrInsnNode(index: Int) : TypeAnnotatableImpl(index), LUshrInsnNode
            inner class DefaultIAndInsnNode(index: Int) : TypeAnnotatableImpl(index), IAndInsnNode
            inner class DefaultLAndInsnNode(index: Int) : TypeAnnotatableImpl(index), LAndInsnNode
            inner class DefaultIOrInsnNode(index: Int) : TypeAnnotatableImpl(index), IOrInsnNode
            inner class DefaultLOrInsnNode(index: Int) : TypeAnnotatableImpl(index), LOrInsnNode
            inner class DefaultIXorInsnNode(index: Int) : TypeAnnotatableImpl(index), IXorInsnNode
            inner class DefaultLXorInsnNode(index: Int) : TypeAnnotatableImpl(index), LXorInsnNode
            inner class DefaultI2LInsnNode(index: Int) : TypeAnnotatableImpl(index), I2LInsnNode
            inner class DefaultI2FInsnNode(index: Int) : TypeAnnotatableImpl(index), I2FInsnNode
            inner class DefaultI2DInsnNode(index: Int) : TypeAnnotatableImpl(index), I2DInsnNode
            inner class DefaultL2IInsnNode(index: Int) : TypeAnnotatableImpl(index), L2IInsnNode
            inner class DefaultL2FInsnNode(index: Int) : TypeAnnotatableImpl(index), L2FInsnNode
            inner class DefaultL2DInsnNode(index: Int) : TypeAnnotatableImpl(index), L2DInsnNode
            inner class DefaultF2IInsnNode(index: Int) : TypeAnnotatableImpl(index), F2IInsnNode
            inner class DefaultF2LInsnNode(index: Int) : TypeAnnotatableImpl(index), F2LInsnNode
            inner class DefaultF2DInsnNode(index: Int) : TypeAnnotatableImpl(index), F2DInsnNode
            inner class DefaultD2IInsnNode(index: Int) : TypeAnnotatableImpl(index), D2IInsnNode
            inner class DefaultD2LInsnNode(index: Int) : TypeAnnotatableImpl(index), D2LInsnNode
            inner class DefaultD2FInsnNode(index: Int) : TypeAnnotatableImpl(index), D2FInsnNode
            inner class DefaultI2BInsnNode(index: Int) : TypeAnnotatableImpl(index), I2BInsnNode
            inner class DefaultI2CInsnNode(index: Int) : TypeAnnotatableImpl(index), I2CInsnNode
            inner class DefaultI2SInsnNode(index: Int) : TypeAnnotatableImpl(index), I2SInsnNode
            inner class DefaultLCmpInsnNode(index: Int) : TypeAnnotatableImpl(index), LCmpInsnNode
            inner class DefaultFCmplInsnNode(index: Int) : TypeAnnotatableImpl(index), FCmplInsnNode
            inner class DefaultFCmpgInsnNode(index: Int) : TypeAnnotatableImpl(index), FCmpgInsnNode
            inner class DefaultDCmplInsnNode(index: Int) : TypeAnnotatableImpl(index), DCmplInsnNode
            inner class DefaultDCmpgInsnNode(index: Int) : TypeAnnotatableImpl(index), DCmpgInsnNode
            inner class DefaultIReturnInsnNode(index: Int) : TypeAnnotatableImpl(index), IReturnInsnNode
            inner class DefaultLReturnInsnNode(index: Int) : TypeAnnotatableImpl(index), LReturnInsnNode
            inner class DefaultFReturnInsnNode(index: Int) : TypeAnnotatableImpl(index), FReturnInsnNode
            inner class DefaultDReturnInsnNode(index: Int) : TypeAnnotatableImpl(index), DReturnInsnNode
            inner class DefaultAReturnInsnNode(index: Int) : TypeAnnotatableImpl(index), AReturnInsnNode
            inner class DefaultReturnInsnNode(index: Int) : TypeAnnotatableImpl(index), ReturnInsnNode
            inner class DefaultArrayLengthInsnNode(index: Int) : TypeAnnotatableImpl(index), ArrayLengthInsnNode
            inner class DefaultAThrowInsnNode(index: Int) : TypeAnnotatableImpl(index), AThrowInsnNode
            inner class DefaultMonitorEnterInsnNode(index: Int) : TypeAnnotatableImpl(index), MonitorEnterInsnNode
            inner class DefaultMonitorExitInsnNode(index: Int) : TypeAnnotatableImpl(index), MonitorExitInsnNode

            inner class DefaultIfEqInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfEqInsnNode

            inner class DefaultIfNeInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfNeInsnNode

            inner class DefaultIfLtInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfLtInsnNode

            inner class DefaultIfGeInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfGeInsnNode

            inner class DefaultIfGtInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfGtInsnNode

            inner class DefaultIfLeInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfLeInsnNode

            inner class DefaultIfIcmpEqInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfIcmpEqInsnNode

            inner class DefaultIfIcmpNeInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfIcmpNeInsnNode

            inner class DefaultIfIcmpLtInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfIcmpLtInsnNode

            inner class DefaultIfIcmpGeInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfIcmpGeInsnNode

            inner class DefaultIfIcmpGtInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfIcmpGtInsnNode

            inner class DefaultIfIcmpLeInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfIcmpLeInsnNode

            inner class DefaultIfAcmpEqInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfAcmpEqInsnNode

            inner class DefaultIfAcmpNeInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfAcmpNeInsnNode

            inner class DefaultGotoInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                GotoInsnNode

            inner class DefaultIfNullInsnNode(index: Int, override val label: LabelNode) : TypeAnnotatableImpl(index),
                IfNullInsnNode

            inner class DefaultIfNonNullInsnNode(index: Int, override val label: LabelNode) :
                TypeAnnotatableImpl(index), IfNonNullInsnNode

            inner class DefaultLdcInsnNode(index: Int, override val constant: Any) : TypeAnnotatableImpl(index),
                LdcInsnNode

            inner class DefaultInvokeVirtualInsnNode(
                index: Int,
                override val owner: String,
                override val name: String,
                override val desc: String
            ) : TypeAnnotatableImpl(index), InvokeVirtualInsnNode

            inner class DefaultInvokeSpecialInsnNode(
                index: Int,
                override val owner: String,
                override val name: String,
                override val desc: String
            ) : TypeAnnotatableImpl(index), InvokeSpecialInsnNode

            inner class DefaultInvokeStaticInsnNode(
                index: Int,
                override val owner: String,
                override val name: String,
                override val desc: String
            ) : TypeAnnotatableImpl(index), InvokeStaticInsnNode

            inner class DefaultInvokeInterfaceInsnNode(
                index: Int,
                override val owner: String,
                override val name: String,
                override val desc: String
            ) : TypeAnnotatableImpl(index), InvokeInterfaceInsnNode

            inner class DefaultInvokeDynamicInsnNode(
                index: Int,
                override val name: String,
                override val desc: String,
                override val bsm: Handle,
                override val bsmArgs: List<Any>
            ) : TypeAnnotatableImpl(index), InvokeDynamicInsnNode

            inner class DefaultNewInsnNode(index: Int, override val desc: String) : TypeAnnotatableImpl(index),
                NewInsnNode

            inner class DefaultANewArrayInsnNode(index: Int, override val desc: String) : TypeAnnotatableImpl(index),
                ANewArrayInsnNode

            inner class DefaultCheckCastInsnNode(index: Int, override val desc: String) : TypeAnnotatableImpl(index),
                CheckCastInsnNode

            inner class DefaultInstanceOfInsnNode(index: Int, override val desc: String) : TypeAnnotatableImpl(index),
                InstanceOfInsnNode

            inner class DefaultMultiANewArrayInsnNode(
                index: Int,
                override val desc: String,
                override val dims: Int
            ) : TypeAnnotatableImpl(index), MultiANewArrayInsnNode

            inner class DefaultLookupSwitchInsnNode(
                index: Int,
                override val dflt: LabelNode,
                override val keys: MutableList<Int>,
                override val labels: MutableList<LabelNode>
            ) : TypeAnnotatableImpl(index), LookupSwitchInsnNode

            inner class DefaultLineNumberNode(
                index: Int,
                override val line: Int,
                override val start: LabelNode
            ) : TypeAnnotatableImpl(index), LineNumberNode

            companion object {
                private val EMPTY_TYPE_ANNOTATION_LIST = emptyList<TypeAnnotationNode>()
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

