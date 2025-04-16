package net.spartanb312.grunt.process.transformers.redirect

import net.spartanb312.genesis.kotlin.*
import net.spartanb312.genesis.kotlin.extensions.FINAL
import net.spartanb312.genesis.kotlin.extensions.PRIVATE
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.extensions.*
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.util.Stack

/**
 * Redirect non-static methods to companion class
 * Unstable feature. If you encounter any bugs, please submit them to me
 * TODO : private constructor invoke special
 */
object MethodExtractorTransformer : Transformer("MethodExtractor", Category.Redirect) {

    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Extracting methods...")
        val count = count {
            nonExcluded.asSequence()
                .filter { it.name.notInList(exclusion) }
                .forEach { classNode ->
                    val accessMethod = mutableMapOf<AccessInfo, MethodNode>()
                    val accessGetField = mutableMapOf<AccessInfo, MethodNode>()
                    val accessSetField = mutableMapOf<AccessInfo, MethodNode>()
                    val companionNode = clazz(PUBLIC, "${classNode.name}\$Methods") {
                        +field(PRIVATE, "accessor", "L${classNode.name};")
                        +method(
                            PUBLIC,
                            "<init>",
                            "(L${classNode.name};)V",
                            null,
                            null,
                        ) {
                            INSTRUCTIONS {
                                ALOAD(0)
                                INVOKESPECIAL("java/lang/Object", "<init>", "()V")
                                ALOAD(0)
                                ALOAD(1)
                                PUTFIELD("${classNode.name}\$Methods", "accessor", "L${classNode.name};")
                                RETURN
                            }
                        }
                    }
                    // Remap lambda access (meme code)
                    classNode.methods.forEach { methodNode ->
                        if (methodNode.name.contains("lambda") && methodNode.isPrivate) {
                            methodNode.setPublic()
                        }
                    }
                    var addClass = false
                    // Process non-static methods
                    classNode.methods.forEach { methodNode ->
                        if (
                            !methodNode.isStatic
                            && !methodNode.isAbstract
                            && !methodNode.isNative
                            && !methodNode.isSynthetic
                            && !methodNode.name.contains("lambda")
                            && !methodNode.isInitializer
                        ) {
                            addClass = true
                            val tryCatches = mutableListOf<TryCatchBlockNode>()

                            val invalidNewBlocks = mutableListOf<Pair<TypeInsnNode, MethodInsnNode>>()
                            val invalidDups = mutableListOf<InsnNode>()
                            val newInsnStack = Stack<NewInfo>()

                            val newInsnList = instructions {
                                val clonedLabels = mutableMapOf<LabelNode, LabelNode>()
                                methodNode.instructions.forEach { if (it is LabelNode) clonedLabels[it] = LabelNode() }
                                methodNode.tryCatchBlocks.forEach {
                                    val cloned = TryCatchBlockNode(
                                        clonedLabels[it.start]!!,
                                        clonedLabels[it.end]!!,
                                        clonedLabels[it.handler]!!,
                                        it.type,
                                    )
                                    cloned.visibleTypeAnnotations = it.visibleTypeAnnotations
                                    cloned.invisibleTypeAnnotations = it.invisibleTypeAnnotations
                                    tryCatches.add(cloned)
                                }

                                var shouldCheck = false
                                methodNode.instructions.forEach {
                                    when (val insnNode = it.clone(clonedLabels)) {
                                        is TypeInsnNode if insnNode.opcode == Opcodes.NEW -> {
                                            newInsnStack.add(NewInfo(insnNode))
                                            shouldCheck = true
                                        }
                                        is InsnNode if shouldCheck -> {
                                            shouldCheck = false
                                            if (insnNode.opcode == Opcodes.DUP) invalidDups.add(insnNode)
                                            else newInsnStack.peek().needsPop = true
                                        }
                                        is FieldInsnNode -> solveFieldInsnNode(
                                            classNode,
                                            insnNode,
                                            accessGetField,
                                            accessSetField
                                        )

                                        is MethodInsnNode -> {
                                            var needPop = false
                                            if (insnNode.opcode == Opcodes.INVOKESPECIAL
                                                && insnNode.name == "<init>") {
                                                val newInsn = newInsnStack.pop()
                                                invalidNewBlocks.add(newInsn.newInsn to insnNode)
                                                needPop = newInsn.needsPop
                                                // if (!needPop) invalidDups.add(newInsn.next as InsnNode)
                                            }
                                            solveMethodInsnNode(
                                                classNode,
                                                insnNode,
                                                accessMethod
                                            )
                                            if (needPop) POP
                                        }

                                        is InvokeDynamicInsnNode -> solveInvokeDynamic(
                                            classNode,
                                            methodNode,
                                            insnNode,
                                            accessMethod
                                        )

                                        else -> +insnNode
                                    }
                                }
                                // Replace method instructions
                                methodNode.instructions = instructions {
                                    ALOAD(0)
                                    var stack = 1
                                    Type.getArgumentTypes(methodNode.desc).forEach {
                                        +VarInsnNode(it.getLoadType(), stack)
                                        stack += it.size
                                    }
                                    INVOKESTATIC(
                                        "${classNode.name}\$Methods", methodNode.name,
                                        "(L${classNode.name};" + methodNode.desc.removePrefix("(")
                                    )
                                    +InsnNode(methodNode.desc.getReturnType())
                                }
                                methodNode.tryCatchBlocks.clear()
                                methodNode.localVariables.clear()
                            }

                            companionNode.methods.add(
                                method(
                                    PUBLIC + STATIC,
                                    methodNode.name,
                                    "(L${classNode.name};" + methodNode.desc.removePrefix("(")
                                ).also {
                                    it.signature = methodNode.signature
                                    it.exceptions = methodNode.exceptions
                                    it.parameters = methodNode.parameters
                                    it.visibleAnnotations = methodNode.visibleAnnotations
                                    it.invisibleAnnotations = methodNode.invisibleAnnotations
                                    it.visibleTypeAnnotations = methodNode.visibleTypeAnnotations
                                    it.invisibleTypeAnnotations = methodNode.invisibleTypeAnnotations
                                    it.attrs = methodNode.attrs
                                    it.annotationDefault = methodNode.annotationDefault
                                    it.visibleAnnotableParameterCount = methodNode.visibleAnnotableParameterCount
                                    it.visibleParameterAnnotations = methodNode.visibleParameterAnnotations
                                    it.invisibleAnnotableParameterCount = methodNode.invisibleAnnotableParameterCount
                                    it.invisibleParameterAnnotations = methodNode.invisibleParameterAnnotations
                                    it.instructions = instructions {
                                        val news = invalidNewBlocks.map { it.first }
                                        val insps = invalidNewBlocks.map { it.second }
                                        newInsnList.forEach {
                                            if (it !in insps && it !in news && it !in invalidDups) +it
                                        }
                                    }
                                    it.tryCatchBlocks = tryCatches
                                    it.maxStack = -1
                                    it.maxLocals = -1
                                }
                            )
                            add()
                        }
                    }

                    if (addClass) {
                        addClass(companionNode)
                        accessGetField.forEach { (_, newNode) -> classNode.methods.add(newNode) }
                        accessSetField.forEach { (_, newNode) -> classNode.methods.add(newNode) }
                        accessMethod.forEach { (_, newNode) -> classNode.methods.add(newNode) }
                    }
                }
        }.get()
        Logger.info("    Extracted $count methods")
    }

    private fun InsnListBuilder.solveInvokeDynamic(
        classNode: ClassNode,
        methodNode: MethodNode,
        indyNode: InvokeDynamicInsnNode,
        accessMethod: MutableMap<AccessInfo, MethodNode>
    ) {
        if (indyNode.opcode != Opcodes.INVOKEDYNAMIC) throw Exception("Fuck you")

        for (mn in classNode.methods) {
            if (mn == methodNode) continue
            val key = "${classNode.name}.${mn.name}${mn.desc}"
            val bsmArgsNew = mutableListOf<Any>()
            for (arg in indyNode.bsmArgs) {
                val argStr = arg.toString()
                if (argStr.contains(key)) {
                    // Need an accessor
                    // potential bug
                    val fakeMethodInsn = MethodInsnNode(
                        when {
                            mn.isPrivate -> Opcodes.INVOKESPECIAL
                            mn.isStatic -> Opcodes.INVOKESTATIC
                            else -> {
                                Logger.error("Can't figure bsm arg invoke type. Using invoke virtual.")
                                Logger.error("Indy bsm: ${indyNode.bsm.owner} ${indyNode.bsm.name} ${indyNode.bsm.desc}")
                                Logger.error("Indy bsm args:")
                                for (bsmArg in indyNode.bsmArgs) {
                                    Logger.error(" - $bsmArg")
                                }
                                Opcodes.INVOKEVIRTUAL
                            }
                        },
                        classNode.name,
                        mn.name,
                        mn.desc
                    )
                    // dummy way to create accessor
                    val accessor = InsnListBuilder().solveMethodInsnNode(classNode, fakeMethodInsn, accessMethod)
                    if (accessor != null) {
                        // println("Accessor ${accessor.name} ${accessor.desc}")
                        val accessorKey = "${classNode.name}.${accessor.name}${accessor.desc}"
                        bsmArgsNew.add(argStr.replace(key, accessorKey))
                    } else bsmArgsNew.add(arg)
                } else bsmArgsNew.add(arg)
            }
        }

        val bsm = indyNode.bsm
        when (indyNode.bsm.tag) {
            Opcodes.H_INVOKESTATIC -> {
                val accessor = accessMethod.getOrPut(AccessInfo(bsm)) {
                    method(
                        PUBLIC + STATIC + FINAL,
                        "accessor-indy-static-${bsm.name}-${getRandomString(5)}",
                        bsm.desc
                    ) {
                        INSTRUCTIONS {
                            var stack = 0
                            Type.getArgumentTypes(bsm.desc).forEach {
                                +VarInsnNode(it.getLoadType(), stack)
                                stack += it.size
                            }
                            INVOKESTATIC(bsm.owner, bsm.name, bsm.desc)
                            +InsnNode(bsm.desc.getReturnType())
                        }
                    }
                }
                indyNode.bsm = Handle(indyNode.bsm.tag, classNode.name, accessor.name, accessor.desc, false)
                +indyNode
            }

            Opcodes.H_INVOKEVIRTUAL -> {
                val accessor = accessMethod.getOrPut(AccessInfo(bsm)) {
                    method(
                        PUBLIC + STATIC + FINAL,
                        "accessor-indy-virtual-${bsm.name}-${getRandomString(5)}",
                        "(L${bsm.owner};${bsm.desc.removePrefix("(")}",
                    ) {
                        INSTRUCTIONS {
                            var stack = 0
                            Type.getArgumentTypes(bsm.desc).forEach {
                                +VarInsnNode(it.getLoadType(), stack)
                                stack += it.size
                            }
                            INVOKEVIRTUAL(bsm.owner, bsm.name, bsm.desc)
                            +InsnNode(bsm.desc.getReturnType())
                        }
                    }
                }
                indyNode.bsm = Handle(indyNode.bsm.tag, classNode.name, accessor.name, accessor.desc, false)
                +indyNode
            }

            else -> throw Exception("Not yet implemented! Please turn off this transformer")
        }
    }

    private fun InsnListBuilder.solveMethodInsnNode(
        classNode: ClassNode,
        insnNode: MethodInsnNode,
        accessMethod: MutableMap<AccessInfo, MethodNode>
    ): MethodNode? {
        var accessor: MethodNode? = null
        when (insnNode.opcode) {
            Opcodes.INVOKESTATIC -> {
                accessor = accessMethod.getOrPut(AccessInfo(insnNode)) {
                    method(
                        PUBLIC + STATIC + FINAL,
                        "accessor-static-${insnNode.name}-${getRandomString(5)}",
                        insnNode.desc
                    ) {
                        INSTRUCTIONS {
                            var stack = 0
                            Type.getArgumentTypes(methodNode.desc).forEach {
                                +VarInsnNode(it.getLoadType(), stack)
                                stack += it.size
                            }
                            INVOKESTATIC(insnNode.owner, insnNode.name, insnNode.desc)
                            +InsnNode(methodNode.desc.getReturnType())
                        }
                    }
                }
                INVOKESTATIC(classNode.name, accessor.name, accessor.desc)
            }

            Opcodes.INVOKEVIRTUAL -> {
                accessor = accessMethod.getOrPut(AccessInfo(insnNode)) {
                    method(
                        PUBLIC + STATIC + FINAL,
                        "accessor-virtual-${insnNode.name}-${getRandomString(5)}",
                        "(L${insnNode.owner};${insnNode.desc.removePrefix("(")}",
                    ) {
                        INSTRUCTIONS {
                            var stack = 0
                            Type.getArgumentTypes(methodNode.desc).forEach {
                                +VarInsnNode(it.getLoadType(), stack)
                                stack += it.size
                            }
                            INVOKEVIRTUAL(insnNode.owner, insnNode.name, insnNode.desc)
                            +InsnNode(methodNode.desc.getReturnType())
                        }
                    }
                }
                INVOKESTATIC(classNode.name, accessor.name, accessor.desc)
            }

            Opcodes.INVOKESPECIAL -> {
                if (insnNode.name == "<init>") {
                    accessor = accessMethod.getOrPut(AccessInfo(insnNode)) {
                        method(
                            PUBLIC + STATIC + FINAL,
                            "accessor-special-new-${insnNode.owner.replace("/", "_")}-${getRandomString(5)}",
                            insnNode.desc.removeSuffix("V") + "L${insnNode.owner};" // hacky
                        ) {
                            INSTRUCTIONS {
                                NEW(insnNode.owner)
                                DUP
                                val parameters = Type.getArgumentTypes(insnNode.desc)
                                var stack = 0
                                parameters.forEach { argType ->
                                    +VarInsnNode(argType.getLoadType(), stack)
                                    stack += argType.size
                                }
                                +insnNode
                                ARETURN
                            }
                        }
                    }
                    INVOKESTATIC(classNode.name, accessor.name, accessor.desc)
                }
                else {
                    accessor = accessMethod.getOrPut(AccessInfo(insnNode)) {
                        method(
                            PUBLIC + FINAL,
                            "accessor-special-${insnNode.name}-${getRandomString(5)}",
                            insnNode.desc//"(L${insnNode.owner};${insnNode.desc.removePrefix("(")}",
                        ) {
                            INSTRUCTIONS {
                                ALOAD(0)
                                var stack = 1
                                Type.getArgumentTypes(methodNode.desc).forEach {
                                    +VarInsnNode(it.getLoadType(), stack)
                                    stack += it.size
                                }
                                INVOKESPECIAL(insnNode.owner, insnNode.name, insnNode.desc)
                                +InsnNode(methodNode.desc.getReturnType())
                            }
                        }
                    }
                    INVOKEVIRTUAL(classNode.name, accessor.name, accessor.desc)
                }
            }

            Opcodes.INVOKEINTERFACE -> {
                accessor = accessMethod.getOrPut(AccessInfo(insnNode)) {
                    method(
                        PUBLIC + STATIC + FINAL,
                        "accessor-interface-${insnNode.name}-${getRandomString(5)}",
                        "(L${insnNode.owner};${insnNode.desc.removePrefix("(")}",
                    ) {
                        INSTRUCTIONS {
                            var stack = 0
                            Type.getArgumentTypes(methodNode.desc).forEach {
                                +VarInsnNode(it.getLoadType(), stack)
                                stack += it.size
                            }
                            INVOKEINTERFACE(insnNode.owner, insnNode.name, insnNode.desc)
                            +InsnNode(methodNode.desc.getReturnType())
                        }
                    }
                }
                INVOKESTATIC(classNode.name, accessor.name, accessor.desc)
            }

            else -> throw Exception("Unknown invoke type ${insnNode.opcode}")
        }
        return accessor
    }

    private fun InsnListBuilder.solveFieldInsnNode(
        classNode: ClassNode,
        insnNode: FieldInsnNode,
        accessGetField: MutableMap<AccessInfo, MethodNode>,
        accessSetField: MutableMap<AccessInfo, MethodNode>
    ) {
        when (insnNode.opcode) {
            // GETSTATIC
            Opcodes.GETSTATIC -> {
                val getter = accessGetField.getOrPut(AccessInfo(insnNode)) {
                    method(
                        PUBLIC + STATIC + FINAL,
                        "getter-static-${insnNode.name}-${getRandomString(5)}",
                        "()${insnNode.desc}"
                    ) {
                        INSTRUCTIONS {
                            GETSTATIC(insnNode.owner, insnNode.name, insnNode.desc)
                            when (insnNode.desc) {
                                "I", "B", "C", "S", "Z" -> IRETURN
                                "J" -> LRETURN
                                "F" -> FRETURN
                                "D" -> DRETURN
                                "V" -> throw Exception("Void is not allowed")
                                else -> {
                                    if (insnNode.desc.startsWith("L") || insnNode.desc.startsWith("[")) ARETURN
                                    else throw Exception("Unknown desc ${insnNode.desc}")
                                }
                            }
                        }
                    }
                }
                INVOKESTATIC(classNode.name, getter.name, getter.desc)
            }

            // VAR
            // PUTFIELD
            Opcodes.PUTSTATIC -> {
                val setter = accessSetField.getOrPut(AccessInfo(insnNode)) {
                    method(
                        PUBLIC + STATIC + FINAL,
                        "setter-static-${insnNode.name}-${getRandomString(5)}",
                        "(${insnNode.desc})V"
                    ) {
                        INSTRUCTIONS {
                            when (insnNode.desc) {
                                "I", "B", "C", "S", "Z" -> ILOAD(0)
                                "J" -> LLOAD(0)
                                "F" -> FLOAD(0)
                                "D" -> DLOAD(0)
                                "V" -> throw Exception("Void is not allowed")
                                else -> {
                                    if (insnNode.desc.startsWith("L") || insnNode.desc.startsWith("[")) ALOAD(0)
                                    else throw Exception("Unknown desc ${insnNode.desc}")
                                }
                            }
                            PUTSTATIC(insnNode.owner, insnNode.name, insnNode.desc)
                            RETURN
                        }
                    }
                }
                INVOKESTATIC(classNode.name, setter.name, setter.desc)
            }

            // THIS
            // GETFIELD
            Opcodes.GETFIELD -> {
                val getter = accessGetField.getOrPut(AccessInfo(insnNode)) {
                    method(
                        PUBLIC + STATIC + FINAL,
                        "getter-${insnNode.name}-${getRandomString(5)}",
                        "(L${insnNode.owner};)${insnNode.desc}"
                    ) {
                        INSTRUCTIONS {
                            ALOAD(0)
                            GETFIELD(insnNode.owner, insnNode.name, insnNode.desc)
                            when (insnNode.desc) {
                                "I", "B", "C", "S", "Z" -> IRETURN
                                "J" -> LRETURN
                                "F" -> FRETURN
                                "D" -> DRETURN
                                "V" -> throw Exception("Void is not allowed")
                                else -> {
                                    if (insnNode.desc.startsWith("L") || insnNode.desc.startsWith("[")) ARETURN
                                    else throw Exception("Unknown desc ${insnNode.desc}")
                                }
                            }
                        }
                    }
                }
                //ALOAD(0)
                //GETFIELD("${classNode.name}\$Methods", "accessor", "L${classNode.name};")
                INVOKESTATIC(classNode.name, getter.name, getter.desc)
            }

            // THIS
            // VAR
            // PUTFIELD
            Opcodes.PUTFIELD -> {
                val setter = accessSetField.getOrPut(AccessInfo(insnNode)) {
                    method(
                        PUBLIC + STATIC + FINAL,
                        "setter-${insnNode.name}-${getRandomString(5)}",
                        "(L${insnNode.owner};${insnNode.desc})V"
                    ) {
                        INSTRUCTIONS {
                            ALOAD(0)
                            when (insnNode.desc) {
                                "I", "B", "C", "S", "Z" -> ILOAD(1)
                                "J" -> LLOAD(1)
                                "F" -> FLOAD(1)
                                "D" -> DLOAD(1)
                                "V" -> throw Exception("Void is not allowed")
                                else -> {
                                    if (insnNode.desc.startsWith("L") || insnNode.desc.startsWith("[")) ALOAD(1)
                                    else throw Exception("Unknown desc ${insnNode.desc}")
                                }
                            }
                            PUTFIELD(insnNode.owner, insnNode.name, insnNode.desc)
                            RETURN
                        }
                    }
                }
                INVOKESTATIC(classNode.name, setter.name, setter.desc)
            }
        }
    }

    class NewInfo(val newInsn: TypeInsnNode, var needsPop: Boolean = false)
    data class AccessInfo(val owner: String, val name: String, val desc: String) {
        constructor(fieldInsnNode: FieldInsnNode) : this(
            fieldInsnNode.owner,
            fieldInsnNode.name,
            fieldInsnNode.desc
        )

        constructor(methodInsnNode: MethodInsnNode) : this(
            methodInsnNode.owner,
            methodInsnNode.name,
            methodInsnNode.desc
        )

        constructor(bsmHandle: Handle) : this(
            bsmHandle.owner,
            bsmHandle.name,
            bsmHandle.desc
        )
    }

    private fun MethodNode.setPublic() {
        if (isPublic) return
        if (isPrivate) {
            access = access and Opcodes.ACC_PRIVATE.inv()
            access = access or Opcodes.ACC_PUBLIC
        } else if (isProtected) {
            access = access and Opcodes.ACC_PROTECTED.inv()
            access = access or Opcodes.ACC_PUBLIC
        } else access = access or Opcodes.ACC_PUBLIC
    }

}
