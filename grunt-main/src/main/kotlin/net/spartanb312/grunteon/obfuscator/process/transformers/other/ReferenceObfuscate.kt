package net.spartanb312.grunteon.obfuscator.process.transformers.other

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.annotation
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.withMapping
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import java.lang.invoke.CallSite
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Transformer.CreditMultiplier(1.5)
@Transformer.Stability(StableLevel.Stable)
@Transformer.Description(
    "process.other.reference_obfuscate.desc",
    "Using invokedynamics to hide reference"
)
class ReferenceObfuscate : Transformer<ReferenceObfuscate.Config>(
    "ReferenceObfuscate",
    Category.Other,
) {

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("The chance that attempt to replace invokes")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Chance")
        val chance: Decimal = 0.3.toDecimal(),
        @SettingDesc("Specify encrypted data container")
        @SettingName("Metadata class")
        val metadataClass: String = "net/spartanb312/grunteon/internal/Metadata",
        @SettingDesc("Reobfuscate bootstrap method")
        @SettingName("Reobfuscate BSM")
        val reobfBSM: Boolean = true,
        @SettingDesc("Mark generated bootstrap/decrypt helper methods as native codegen candidates")
        @SettingName("Generated helper native candidate")
        val generatedHelperNativeCandidate: Boolean = false,
        @SettingDesc("Specify method exclusions.")
        @SettingName("Exclusion")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig()

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            //Logger.info(" > ReferenceObfuscate: Hiding method call references...")
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        seq {
            // Add metadata annotation class
            val metadata = clazz(
                access = PUBLIC + ABSTRACT + INTERFACE + ANNOTATION,
                name = config.metadataClass,
                superName = "java/lang/Object",
                interfaces = listOf("java/lang/annotation/Annotation"),
                signature = null,
                version = Java8
            ) {
                +annotation("Ljava/lang/annotation/Retention;") {
                    ENUM("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME")
                }
                +method(
                    access = PUBLIC + ABSTRACT,
                    name = "mv",
                    desc = "()I",
                    signature = null,
                    exceptions = null
                ) {
                    ANNOTATIONDEFAULT {
                        this[null] = 0
                    }
                }
                +method(
                    access = PUBLIC + ABSTRACT,
                    name = "d1",
                    desc = "()[Ljava/lang/String;",
                    signature = null,
                    exceptions = null
                ) {
                    ANNOTATIONDEFAULT {
                        ARRAY(null)
                    }
                }
                +method(
                    access = PUBLIC + ABSTRACT,
                    name = "d2",
                    desc = "()[Ljava/lang/String;",
                    signature = null,
                    exceptions = null
                ) {
                    ANNOTATIONDEFAULT {
                        ARRAY(null)
                    }
                }
            }.appendAnnotation(GENERATED_CLASS)
            instance.workRes.addGeneratedClass(metadata)
        }

        val metadata = ConcurrentHashMap<ClassNode, MetaData>()
        // Generate metadata for all included classes
        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
                .withMapping(instance.nameMapping.revMappings)
        ) { classNode ->
            if (classNode.hasAnnotation(IGNORE_METADATA_TARGET)) return@parForEachClassesFiltered
            val allMethods = mutableSetOf<MethodNode>()

            // DFS search methods, TODO: Optimize this via Method hierarchy
            fun process(classNode: ClassNode) {
                allMethods.addAll(classNode.methods.filter { !it.hasAnnotation(IGNORE_METADATA_TARGET) })
                val parents = mutableSetOf<String>()
                if (classNode.superName != null) parents.add(classNode.superName)
                if (classNode.interfaces?.isNotEmpty() == true) parents.addAll(classNode.interfaces)
                parents.forEach { instance.workRes.getClassNode(it)?.let { p -> process(p) } }
            }
            process(classNode)
            if (allMethods.isNotEmpty()) {
                val data = MetaData(
                    IntArray(allMethods.size),
                    Array(allMethods.size) { "" },
                    Array(allMethods.size) { "d1" },
                    Array(allMethods.size) { "d2" },
                    IntArray(allMethods.size),
                    IntArray(allMethods.size)
                )
                val existedMagic2 = mutableSetOf<Int>()
                val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, "magic"))
                allMethods.forEachIndexed { index, methodNode ->
                    val magic1 = randomGen.nextInt(0x8, 0x800)
                    // Generate unique magic2
                    var magic2: Int
                    while (true) {
                        magic2 = randomGen.nextInt(0, 59557)
                        if (!existedMagic2.contains(magic2)) {
                            existedMagic2.add(magic2)
                            break
                        }
                    }
                    val data1 = 59557 * randomGen.nextInt(1186) + magic2
                    val encryptedData1 = encrypt(data1.toString(), magic2)
                    val data2 = methodNode.name + "<>" + methodNode.desc
                    val encryptedData2 = encrypt(data2, magic1)
                    data.d1[index] = data1
                    data.d2[index] = data2
                    data.ed1[index] = encryptedData1
                    data.ed2[index] = encryptedData2
                    data.m1[index] = magic1
                    data.m2[index] = magic2
                }
                metadata[classNode] = data
                val annotation = annotation("L${config.metadataClass};") {
                    this["mv"] = 100
                    this["d1"] = data.ed1.toList()
                    this["d2"] = data.ed2.toList()
                }
                classNode.visibleAnnotations = classNode.visibleAnnotations ?: mutableListOf()
                classNode.visibleAnnotations.add(annotation)
            }
        }
        // Force barrier
        barrier()

        val counter = reducibleScopeValue { MergeableCounter() }
        val addedMethods = ConcurrentLinkedQueue<Pair<ClassNode, List<MethodNode>>>()
        // Replace to invoke dynamics
        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
                .withMapping(instance.nameMapping.revMappings)
        ) { classNode ->
            if (classNode.isInterface) return@parForEachClassesFiltered
            if (classNode.version < Opcodes.V1_7) return@parForEachClassesFiltered
            if (classNode.hasAnnotation(DISABLE_REFERENCE_OBF)) return@parForEachClassesFiltered

            val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, "apply"))
            val counter = counter.local
            val bsmName1 = massiveString
            val bsmName2 = bsmName1.substring(1, bsmName1.length - 1)
            val decryptName = bsmName1.substring(2, bsmName1.length - 1)
            val material = findRuntimeMaterial(classNode)
            val materialKeyName = material?.let { bsmName1.substring(1, bsmName1.length - 2) }
            val plainBsmName = material?.let { bsmName1.substring(1, bsmName1.length - 3) }
            val plainDecryptName = material?.let { bsmName1.substring(1, bsmName1.length - 3) }
            val decryptKey = randomGen.nextInt()
            context(config, counter) {
                if (shouldApply(classNode, bsmName1, bsmName2, plainBsmName, decryptKey, metadata, material)) {
                    val decrypt = createDecryptMethod(classNode.name, decryptName, decryptKey, materialKeyName)
                    val decrypt2 = createHeavyDecryptMethod(decryptName)
                    val bsm = createBootstrap(classNode.name, bsmName1, decryptName)
                    val bsm2 = createHeavyBootstrap(
                        classNode.name,
                        bsmName2,
                        decryptName,
                        decryptKey
                    )
                    val materialKey = if (material != null && materialKeyName != null) {
                        createMaterialKeyMethod(classNode.name, materialKeyName, material)
                    } else null
                    val plainDecrypt = if (material != null && plainDecryptName != null) {
                        createDecryptMethod(classNode.name, plainDecryptName, decryptKey, null)
                    } else null
                    val plainBsm = if (material != null && plainBsmName != null && plainDecryptName != null) {
                        createBootstrap(classNode.name, plainBsmName, plainDecryptName)
                    } else null
                    val generatedHelpers = listOfNotNull(
                        materialKey,
                        plainDecrypt,
                        plainBsm,
                        decrypt,
                        bsm,
                        decrypt2,
                        bsm2
                    )
                    generatedHelpers.forEach { it.markGeneratedReferenceHelper(config) }
                    if (config.reobfBSM) {
                        val methodsAdded = mutableListOf<MethodNode>()
                        materialKey?.let { methodsAdded.add(it) }
                        plainDecrypt?.let { methodsAdded.add(it) }
                        plainBsm?.let { methodsAdded.add(it) }
                        methodsAdded.add(decrypt)
                        methodsAdded.add(bsm)
                        methodsAdded.add(decrypt2)
                        methodsAdded.add(bsm2)
                        addedMethods.add(classNode to methodsAdded)
                    }
                    materialKey?.let { classNode.methods.add(it) }
                    plainDecrypt?.let { classNode.methods.add(it) }
                    plainBsm?.let { classNode.methods.add(it) }
                    classNode.methods.add(decrypt)
                    classNode.methods.add(bsm)
                    classNode.methods.add(decrypt2)
                    classNode.methods.add(bsm2)
                }
            }
        }
        // Force sync
        barrier()
        // Reobf
        if (config.reobfBSM) seq {
            runBlocking {
                credit.add(addedMethods.sumOf { it.second.size } * 750L)
                addedMethods.forEach { (classNode, methods) ->
                    methods.forEach { method ->
                        launch(Dispatchers.Default) {
                            reobfuscateBootstrapMethod(classNode, method)
                        }
                    }
                }
            }
        }
        post {
            Logger.info(" - ReferenceObfuscate:")
            credit.add(counter.global.get()* 200L)
            Logger.info("    Replaced ${counter.global.get()} invokes")
        }
    }

    context(instance: Grunteon)
    private fun reobfuscateBootstrapMethod(classNode: ClassNode, methodNode: MethodNode) {
        methodNode.localVariables.clear()
        val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, methodNode.name, methodNode.desc, "reobf"))
        methodNode.instructions.toArray().forEach { instruction ->
            val replacement = when {
                instruction is LdcInsnNode && instruction.cst is String ->
                    randomGen.reobfuscateString(instruction.cst as String)

                instruction.intConstantValue() != null ->
                    randomGen.reobfuscateInt(instruction.intConstantValue()!!)

                instruction.longConstantValue() != null ->
                    randomGen.reobfuscateLong(instruction.longConstantValue()!!)

                else -> null
            }
            if (replacement != null) {
                methodNode.instructions.insertBefore(instruction, replacement)
                methodNode.instructions.remove(instruction)
            }
        }
        methodNode.reobfuscateGotoEdges(classNode.name, randomGen)
    }

    private fun MethodNode.reobfuscateGotoEdges(owner: String, random: UniformRandomProvider): Int {
        if (!instructions.endsWithTerminalInstruction()) return 0
        val frames = runCatching {
            Analyzer(BasicInterpreter()).analyze(owner, this)
        }.getOrNull() ?: return 0
        val instructionArray = instructions.toArray()
        val candidates = instructionArray
            .withIndex()
            .filter { (index, instruction) ->
                instruction is JumpInsnNode &&
                    instruction.opcode == Opcodes.GOTO &&
                    instruction.isEligibleExceptionBridge(index, instructionArray, frames)
            }
            .map { it.value as JumpInsnNode }
            .toMutableList()
        candidates.shuffle(random)
        val candidateSet = candidates.toSet()
        val bridgePlans = candidates.mapNotNull { goto ->
            val gotoIndex = instructionArray.indexOf(goto)
            val anchor = instructionArray.findDistantHandlerAnchor(gotoIndex, candidateSet, frames, random)
                ?: return@mapNotNull null
            goto to anchor
        }

        var inserted = 0
        for ((goto, handlerAnchor) in bridgePlans) {
            if (inserted >= REOBF_MAX_EXCEPTION_BRIDGES_PER_METHOD) break
            val target = goto.label
            val trapStart = LabelNode()
            val trapEnd = LabelNode()
            val handler = LabelNode()

            instructions.insertBefore(goto, InsnList().apply {
                add(trapStart)
                add(TypeInsnNode(Opcodes.NEW, REOBF_EXCEPTION_BRIDGE_INTERNAL_NAME))
                add(InsnNode(Opcodes.DUP))
                add(MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    REOBF_EXCEPTION_BRIDGE_INTERNAL_NAME,
                    "<init>",
                    "()V",
                    false
                ))
                add(InsnNode(Opcodes.ATHROW))
                add(trapEnd)
            })
            instructions.remove(goto)

            instructions.insert(handlerAnchor, InsnList().apply {
                add(handler)
                add(InsnNode(Opcodes.POP))
                add(JumpInsnNode(Opcodes.GOTO, target))
            })
            tryCatchBlocks.add(
                0,
                TryCatchBlockNode(
                    trapStart,
                    trapEnd,
                    handler,
                    REOBF_EXCEPTION_BRIDGE_INTERNAL_NAME
                )
            )
            inserted++
        }
        return inserted
    }

    private fun Array<AbstractInsnNode>.findDistantHandlerAnchor(
        gotoIndex: Int,
        candidateSet: Set<AbstractInsnNode>,
        frames: Array<Frame<BasicValue>?>,
        random: UniformRandomProvider
    ): AbstractInsnNode? {
        if (gotoIndex < 0) return null
        val anchors = withIndex()
            .filter { (index, instruction) ->
                frames.getOrNull(index) != null &&
                instruction !in candidateSet &&
                    instruction.isHandlerAnchor() &&
                    index.distanceTo(gotoIndex) >= REOBF_MIN_EXCEPTION_BRIDGE_DISTANCE
            }
            .map { it.value }
            .toMutableList()
        if (anchors.isEmpty()) return null
        return anchors[random.nextInt(anchors.size)]
    }

    private fun AbstractInsnNode.isHandlerAnchor(): Boolean {
        return opcode == Opcodes.GOTO ||
            opcode == Opcodes.ATHROW ||
            opcode in Opcodes.IRETURN..Opcodes.RETURN
    }

    private fun Int.distanceTo(other: Int): Int {
        val distance = this - other
        return if (distance < 0) -distance else distance
    }

    private fun UniformRandomProvider.reobfuscateString(value: String): InsnList = instructions {
        NEW("java/lang/String")
        DUP
        +reobfuscateInt(value.length)
        NEWARRAY(Opcodes.T_CHAR)
        value.forEachIndexed { index, char ->
            val key = nextInt()
            DUP
            +reobfuscateInt(index)
            +reobfuscateInt(char.code xor key)
            +reobfuscateInt(key)
            IXOR
            I2C
            CASTORE
        }
        INVOKESPECIAL("java/lang/String", "<init>", "([C)V")
    }

    private fun UniformRandomProvider.reobfuscateInt(value: Int): InsnList = instructions {
        val key = nextInt()
        INT(value xor key)
        INT(key)
        IXOR
    }

    private fun UniformRandomProvider.reobfuscateLong(value: Long): InsnList = instructions {
        val key = nextLong()
        LONG(value xor key)
        LONG(key)
        LXOR
    }

    private fun JumpInsnNode.isEligibleExceptionBridge(
        index: Int,
        instructions: Array<AbstractInsnNode>,
        frames: Array<Frame<BasicValue>?>
    ): Boolean {
        val sourceFrame = frames.getOrNull(index) ?: return false
        if (sourceFrame.stackSize != 0) return false
        val targetIndex = instructions.indexOf(label)
        if (targetIndex < 0) return false
        val targetFrame = frames.frameAtOrAfter(targetIndex, instructions) ?: return false
        return targetFrame.stackSize == 0
    }

    private fun Array<Frame<BasicValue>?>.frameAtOrAfter(
        index: Int,
        instructions: Array<AbstractInsnNode>
    ): Frame<BasicValue>? {
        var cursor = index
        while (cursor < size && cursor < instructions.size) {
            this[cursor]?.let { return it }
            cursor++
        }
        return null
    }

    private fun InsnList.endsWithTerminalInstruction(): Boolean {
        var instruction = last
        while (instruction != null) {
            val opcode = instruction.opcode
            if (opcode >= 0) return opcode == Opcodes.GOTO ||
                opcode == Opcodes.ATHROW ||
                opcode in Opcodes.IRETURN..Opcodes.RETURN
            instruction = instruction.previous
        }
        return false
    }

    private fun <T> MutableList<T>.shuffle(random: UniformRandomProvider) {
        for (index in lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val value = this[index]
            this[index] = this[swapIndex]
            this[swapIndex] = value
        }
    }

    private fun AbstractInsnNode.intConstantValue(): Int? {
        return when (opcode) {
            in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> opcode - Opcodes.ICONST_0
            Opcodes.BIPUSH, Opcodes.SIPUSH -> (this as IntInsnNode).operand
            Opcodes.LDC -> {
                val constant = (this as LdcInsnNode).cst
                constant as? Int
            }

            else -> null
        }
    }

    private fun AbstractInsnNode.longConstantValue(): Long? {
        return when (opcode) {
            in Opcodes.LCONST_0..Opcodes.LCONST_1 -> (opcode - Opcodes.LCONST_0).toLong()
            Opcodes.LDC -> {
                val constant = (this as LdcInsnNode).cst
                constant as? Long
            }

            else -> null
        }
    }

    context(instance: Grunteon, counter: MergeableCounter, config: Config)
    private fun shouldApply(
        classNode: ClassNode,
        bsm1: String,
        bsm2: String,
        plainBsm: String?,
        decryptKey: Int,
        metadataMap: Map<ClassNode, MetaData>,
        runtimeMaterial: RuntimeMaterial?
    ): Boolean {
        var shouldApply = false
        classNode.methods
            .filter { shouldProcessMethod(it) }
            .forEach { methodNode ->
                if (!methodNode.hasAnnotation(DISABLE_REFERENCE_OBF)) {
                    val methodMaterial = materialForMethod(methodNode, runtimeMaterial)
                    val simpleDecryptKey = decryptKey xor (methodMaterial?.seedFold ?: 0)
                    val simpleBsm = if (runtimeMaterial != null && methodMaterial == null) {
                        plainBsm ?: bsm1
                    } else bsm1
                    val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, methodNode.name, methodNode.desc))
                    methodNode.instructions.filter {
                        it is MethodInsnNode && it.opcode != Opcodes.INVOKESPECIAL
                    }.forEach { insnNode ->
                        if (insnNode is MethodInsnNode && randomGen.nextFloat() < config.chance.toFloat()) {
                            val metadata = metadataMap.entries.find { (clazz, _) -> clazz.name == insnNode.owner }
                            val metadataKey = insnNode.name + "<>" + insnNode.desc
                            val index = metadata?.value?.d2?.indexOf(metadataKey) ?: -1
                            // Material-aware callers use simple BSM payloads for now.
                            // Normal methods are keyed by the caller-local RuntimeMaterial
                            // quotient. <clinit>/<init> fall back to plain simple BSM:
                            // they still hide references, but do not read material while
                            // the material lane itself is being initialized or perturbed.
                            // Heavy metadata stays unchanged until it gets its own
                            // authenticated material lane.
                            val invokeDynamicInsnNode = if (runtimeMaterial == null && metadata != null && index >= 0) {
                                val magic1 = metadata.value.m1[index]
                                val magic2 = metadata.value.m2[index]
                                InvokeDynamicInsnNode(
                                    bsm2,
                                    if (insnNode.opcode == Opcodes.INVOKESTATIC) insnNode.desc
                                    else insnNode.desc.replace("(", "(Ljava/lang/Object;"),
                                    H_INVOKESTATIC(
                                        classNode.name,
                                        bsm2,
                                        MethodType.methodType(
                                            CallSite::class.java,
                                            MethodHandles.Lookup::class.java,
                                            String::class.java,
                                            MethodType::class.java,
                                            String::class.java,
                                            Integer::class.java,
                                            Integer::class.java,
                                            Integer::class.java
                                        ).toMethodDescriptorString(),
                                    ),
                                    encrypt(insnNode.owner.replace("/", "."), decryptKey),
                                    magic1,
                                    magic2,
                                    if (insnNode.opcode == Opcodes.INVOKESTATIC) 0 else 1
                                )
                            } else InvokeDynamicInsnNode(
                                simpleBsm,
                                if (insnNode.opcode == Opcodes.INVOKESTATIC) insnNode.desc
                                else insnNode.desc.replace("(", "(Ljava/lang/Object;"),
                                H_INVOKESTATIC(
                                    classNode.name,
                                    simpleBsm,
                                    MethodType.methodType(
                                        CallSite::class.java,
                                        MethodHandles.Lookup::class.java,
                                        String::class.java,
                                        MethodType::class.java,
                                        String::class.java,
                                        String::class.java,
                                        String::class.java,
                                        Integer::class.java
                                    ).toMethodDescriptorString(),
                                ),
                                encrypt(insnNode.owner.replace("/", "."), simpleDecryptKey),
                                encrypt(insnNode.name, simpleDecryptKey),
                                encrypt(insnNode.desc, simpleDecryptKey),
                                if (insnNode.opcode == Opcodes.INVOKESTATIC) 0 else 1
                            )
                            methodNode.instructions.insertBefore(insnNode, invokeDynamicInsnNode)
                            methodNode.instructions.remove(insnNode)
                            shouldApply = true
                            counter.add()
                            //println("Applied on ${classNode.name}.${methodNode.name}${methodNode.desc}")
                        }
                    }
                }
            }
        return shouldApply
    }

    private fun MethodNode.markGeneratedReferenceHelper(config: Config): MethodNode {
        appendAnnotation(GENERATED_METHOD)
        if (config.generatedHelperNativeCandidate) appendAnnotation(NATIVE_INCLUDED)
        return this
    }

    private fun shouldProcessMethod(methodNode: MethodNode): Boolean {
        if (methodNode.isAbstract || methodNode.isNative) return false
        if (methodNode.hasAnnotation(GENERATED_METHOD)) return false
        return true
    }

    private fun materialForMethod(methodNode: MethodNode, runtimeMaterial: RuntimeMaterial?): RuntimeMaterial? {
        if (runtimeMaterial == null) return null
        // Design note:
        // <clinit>/<init> are still protected by ReferenceObfuscate, but they
        // deliberately fall back to plain simple BSM. Later passes may expand
        // RuntimeMaterial constants into helper calls in these methods; re-keying
        // those helpers by material would read the material lane while it is
        // being initialized or perturbed.
        if (methodNode.name == "<clinit>" || methodNode.name == "<init>") return null
        return runtimeMaterial
    }

    private fun createBootstrap(className: String, methodName: String, decryptName: String) =
        method(
            PUBLIC + STATIC + SYNTHETIC + BRIDGE,
            methodName,
            MethodType.methodType(
                CallSite::class.java,
                MethodHandles.Lookup::class.java,
                String::class.java,
                MethodType::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Integer::class.java
            ).toMethodDescriptorString()
        ) {
            INSTRUCTIONS {
                TRYCATCH(L["labelA"], L["labelB"], L["labelC"], "java/lang/Exception")
                TRYCATCH(L["labelD"], L["labelE"], L["labelC"], "java/lang/Exception")
                ALOAD(3)
                CHECKCAST("java/lang/String")
                ASTORE(7)
                ALOAD(4)
                CHECKCAST("java/lang/String")
                ASTORE(8)
                ALOAD(5)
                CHECKCAST("java/lang/String")
                ASTORE(9)
                ALOAD(6)
                CHECKCAST("java/lang/Integer")
                INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I")
                ISTORE(10)
                ALOAD(9)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                LDC_TYPE("L$className;")
                INVOKEVIRTUAL("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;")
                INVOKESTATIC(
                    "java/lang/invoke/MethodType",
                    "fromMethodDescriptorString",
                    "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;"
                )
                ASTORE(11)
                LABEL(L["labelA"])
                ILOAD(10)
                ICONST_1
                IF_ICMPNE(L["labelD"])
                NEW("java/lang/invoke/ConstantCallSite")
                DUP
                ALOAD(0)
                ALOAD(7)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
                ALOAD(8)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                ALOAD(11)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "findVirtual",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
                )
                ALOAD(2)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandle",
                    "asType",
                    "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
                )
                INVOKESPECIAL("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
                LABEL(L["labelB"])
                ARETURN
                LABEL(L["labelD"])
                FRAME(
                    Opcodes.F_FULL, 12, arrayOf(
                        "java/lang/invoke/MethodHandles\$Lookup",
                        "java/lang/String",
                        "java/lang/invoke/MethodType",
                        "java/lang/Object",
                        "java/lang/Object",
                        "java/lang/Object",
                        "java/lang/Object",
                        "java/lang/String",
                        "java/lang/String",
                        "java/lang/String",
                        Opcodes.INTEGER,
                        "java/lang/invoke/MethodType"
                    ), 0, arrayOf()
                )
                NEW("java/lang/invoke/ConstantCallSite")
                DUP
                ALOAD(0)
                ALOAD(7)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
                ALOAD(8)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                ALOAD(11)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "findStatic",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
                )
                ALOAD(2)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandle",
                    "asType",
                    "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
                )
                INVOKESPECIAL("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
                LABEL(L["labelE"])
                ARETURN
                LABEL(L["labelC"])
                FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/Exception"))
                ASTORE(12)
                ACONST_NULL
                ARETURN
            }
            MAXS(6, 13)
        }

    private fun createHeavyDecryptMethod(methodName: String): MethodNode = method(
        access = PRIVATE + STATIC + SYNTHETIC + BRIDGE,
        name = methodName,
        desc = "(Ljava/lang/String;I)Ljava/lang/String;"
    ) {
        val label6 = Label()
        val label5 = Label()
        val label4 = Label()
        val label3 = Label()
        val label2 = Label()
        val label1 = Label()
        val label0 = Label()
        INSTRUCTIONS {
            LABEL(label0)
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
            ASTORE(2)
            LABEL(label1)
            ICONST_0
            ISTORE(3)
            LABEL(label2)
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/lang/StringBuilder", Opcodes.INTEGER), 0, null)
            ILOAD(3)
            ALOAD(0)
            INVOKEVIRTUAL("java/lang/String", "length", "()I", false)
            IF_ICMPGE(label3)
            LABEL(label4)
            ALOAD(2)
            ALOAD(0)
            ILOAD(3)
            INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C", false)
            ILOAD(1)
            IXOR
            I2C
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false)
            POP
            LABEL(label5)
            IINC(3, 1)
            GOTO(label2)
            LABEL(label3)
            FRAME(Opcodes.F_SAME, 0, null, 0, null)
            ALOAD(2)
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
            ARETURN
            LABEL(label6)
            LOCALVAR("str", "Ljava/lang/String;", null, label0, label6, 0)
            LOCALVAR("key", "I", null, label0, label6, 1)
            LOCALVAR("stringBuilder", "Ljava/lang/StringBuilder;", null, label1, label6, 2)
            LOCALVAR("n", "I", null, label2, label6, 3)
            MAXS(3, 4)
        }
    }

    context(config: Config)
    private fun createHeavyBootstrap(
        className: String,
        methodName: String,
        decryptName: String,
        generalKey: Int
    ): MethodNode = method(
        access = PRIVATE + STATIC + SYNTHETIC + BRIDGE,
        name = methodName,
        desc = MethodType.methodType(
            CallSite::class.java,
            MethodHandles.Lookup::class.java,
            String::class.java,
            MethodType::class.java,
            String::class.java,
            Integer::class.java,
            Integer::class.java,
            Integer::class.java
        ).toMethodDescriptorString()
    ) {
        val label27 = Label()
        val label26 = Label()
        val label25 = Label()
        val label24 = Label()
        val label23 = Label()
        val label22 = Label()
        val label21 = Label()
        val label20 = Label()
        val label19 = Label()
        val label18 = Label()
        val label17 = Label()
        val label16 = Label()
        val label15 = Label()
        val label14 = Label()
        val label13 = Label()
        val label12 = Label()
        val label11 = Label()
        val label10 = Label()
        val label9 = Label()
        val label8 = Label()
        val label7 = Label()
        val label6 = Label()
        val label5 = Label()
        val label4 = Label()
        val label3 = Label()
        val label2 = Label()
        val label1 = Label()
        val label0 = Label()
        val metadataClass = config.metadataClass
        INSTRUCTIONS {
            TRYCATCH(label0, label1, label2, "java/lang/NumberFormatException")
            LABEL(label3)
            ALOAD(3)
            LDC(generalKey)
            INVOKESTATIC(className, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;", false)
            INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false)
            ASTORE(7)
            LABEL(label4)
            ALOAD(7)
            LDC(Type.getType("L$metadataClass;"))
            INVOKEVIRTUAL(
                "java/lang/Class",
                "getAnnotation",
                "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;",
                false
            )
            CHECKCAST(metadataClass)
            ASTORE(8)
            LABEL(label5)
            ALOAD(8)
            INVOKEINTERFACE(metadataClass, "mv", "()I", true)
            BIPUSH(100.toInt())
            IF_ICMPGE(label6)
            NEW("java/lang/Exception")
            DUP
            LDC("Outdated metadata version")
            INVOKESPECIAL("java/lang/Exception", "<init>", "(Ljava/lang/String;)V", false)
            ATHROW
            LABEL(label6)
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/lang/Class", metadataClass), 0, null)
            ALOAD(8)
            INVOKEINTERFACE(metadataClass, "d1", "()[Ljava/lang/String;", true)
            ASTORE(9)
            LABEL(label7)
            ALOAD(8)
            INVOKEINTERFACE(metadataClass, "d2", "()[Ljava/lang/String;", true)
            ASTORE(10)
            LABEL(label8)
            LDC("")
            ASTORE(11)
            LABEL(label9)
            LDC("")
            ASTORE(12)
            LABEL(label10)
            ICONST_0
            ISTORE(13)
            LABEL(label11)
            FRAME(
                Opcodes.F_FULL,
                14,
                arrayOf(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "java/lang/String",
                    "java/lang/invoke/MethodType",
                    "java/lang/String",
                    "java/lang/Integer",
                    "java/lang/Integer",
                    "java/lang/Integer",
                    "java/lang/Class",
                    metadataClass,
                    "[Ljava/lang/String;",
                    "[Ljava/lang/String;",
                    "java/lang/String",
                    "java/lang/String",
                    Opcodes.INTEGER
                ),
                0,
                arrayOf()
            )
            ILOAD(13)
            ALOAD(9)
            ARRAYLENGTH
            IF_ICMPGE(label12)
            LABEL(label0)
            ALOAD(9)
            ILOAD(13)
            AALOAD
            ALOAD(5)
            INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
            INVOKESTATIC(className, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;", false)
            INVOKESTATIC("java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false)
            ISTORE(14)
            LABEL(label1)
            GOTO(label13)
            LABEL(label2)
            FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/NumberFormatException"))
            ASTORE(15)
            LABEL(label14)
            GOTO(label15)
            LABEL(label13)
            FRAME(Opcodes.F_APPEND, 1, arrayOf(Opcodes.INTEGER), 0, null)
            ILOAD(14)
            LDC(59557.toInt())
            IREM
            ALOAD(5)
            INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
            IF_ICMPNE(label15)
            LABEL(label16)
            ALOAD(10)
            ILOAD(13)
            AALOAD
            ALOAD(4)
            INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
            INVOKESTATIC(className, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;", false)
            ASTORE(15)
            LABEL(label17)
            ALOAD(15)
            LDC("<>")
            INVOKEVIRTUAL("java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false)
            ASTORE(16)
            LABEL(label18)
            ALOAD(16)
            ICONST_0
            AALOAD
            ASTORE(11)
            LABEL(label19)
            ALOAD(16)
            ICONST_1
            AALOAD
            ASTORE(12)
            LABEL(label20)
            GOTO(label12)
            LABEL(label15)
            FRAME(Opcodes.F_CHOP, 1, null, 0, null)
            IINC(13, 1)
            GOTO(label11)
            LABEL(label12)
            FRAME(Opcodes.F_CHOP, 1, null, 0, null)
            ALOAD(11)
            INVOKEVIRTUAL("java/lang/String", "isEmpty", "()Z", false)
            IFEQ(label21)
            LABEL(label22)
            NEW("java/lang/Exception")
            DUP
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
            LDC("Can't find method in ")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            ALOAD(3)
            LDC(generalKey)
            INVOKESTATIC(className, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;", false)
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            LDC(" ")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            ALOAD(4)
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false)
            LDC(" ")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            ALOAD(5)
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false)
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
            INVOKESPECIAL("java/lang/Exception", "<init>", "(Ljava/lang/String;)V", false)
            ATHROW
            LABEL(label21)
            FRAME(Opcodes.F_SAME, 0, null, 0, null)
            ALOAD(12)
            ALOAD(7)
            INVOKEVIRTUAL("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false)
            INVOKESTATIC(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;",
                false
            )
            ASTORE(13)
            LABEL(label23)
            ALOAD(6)
            INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
            ICONST_1
            IF_ICMPNE(label24)
            ALOAD(0)
            ALOAD(7)
            ALOAD(11)
            ALOAD(13)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandles\$Lookup",
                "findVirtual",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                false
            )
            ASTORE(14)
            LABEL(label25)
            GOTO(label26)
            LABEL(label24)
            FRAME(Opcodes.F_APPEND, 1, arrayOf("java/lang/invoke/MethodType"), 0, null)
            ALOAD(0)
            ALOAD(7)
            ALOAD(11)
            ALOAD(13)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandles\$Lookup",
                "findStatic",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                false
            )
            ASTORE(14)
            LABEL(label26)
            FRAME(Opcodes.F_APPEND, 1, arrayOf("java/lang/invoke/MethodHandle"), 0, null)
            NEW("java/lang/invoke/ConstantCallSite")
            DUP
            ALOAD(14)
            ALOAD(2)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                false
            )
            INVOKESPECIAL("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false)
            ARETURN
            LABEL(label27)
            LOCALVAR("data1", "I", null, label1, label2, 14)
            LOCALVAR("e", "Ljava/lang/NumberFormatException;", null, label14, label13, 15)
            LOCALVAR("data2", "Ljava/lang/String;", null, label17, label15, 15)
            LOCALVAR("pair", "[Ljava/lang/String;", null, label18, label15, 16)
            LOCALVAR("data1", "I", null, label13, label15, 14)
            LOCALVAR("i", "I", null, label11, label12, 13)
            LOCALVAR("handle", "Ljava/lang/invoke/MethodHandle;", null, label25, label24, 14)
            LOCALVAR("lookup", "Ljava/lang/invoke/MethodHandles\$Lookup;", null, label3, label27, 0)
            LOCALVAR("ignore", "Ljava/lang/String;", null, label3, label27, 1)
            LOCALVAR("methodType", "Ljava/lang/invoke/MethodType;", null, label3, label27, 2)
            LOCALVAR("owner", "Ljava/lang/String;", null, label3, label27, 3)
            LOCALVAR("magic", "Ljava/lang/Integer;", null, label3, label27, 4)
            LOCALVAR("magic2", "Ljava/lang/Integer;", null, label3, label27, 5)
            LOCALVAR("isVirtual", "Ljava/lang/Integer;", null, label3, label27, 6)
            LOCALVAR("ownerClass", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", label4, label27, 7)
            LOCALVAR("meta", "L$metadataClass;", null, label5, label27, 8)
            LOCALVAR("d1", "[Ljava/lang/String;", null, label7, label27, 9)
            LOCALVAR("d2", "[Ljava/lang/String;", null, label8, label27, 10)
            LOCALVAR("name", "Ljava/lang/String;", null, label9, label27, 11)
            LOCALVAR("desc", "Ljava/lang/String;", null, label10, label27, 12)
            LOCALVAR("targetType", "Ljava/lang/invoke/MethodType;", null, label23, label27, 13)
            LOCALVAR("handle", "Ljava/lang/invoke/MethodHandle;", null, label26, label27, 14)
            MAXS(5, 17)
        }
    }

    fun createDecryptMethod(
        className: String,
        methodName: String,
        key: Int,
        materialKeyName: String?
    ): MethodNode = method(
        PRIVATE + STATIC + SYNTHETIC + BRIDGE,
        methodName,
        "(Ljava/lang/String;)Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            //A:
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V")
            ASTORE(1)
            ICONST_0
            ISTORE(2)
            GOTO(L["labelC"])

            //B:
            LABEL(L["labelB"])
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/lang/StringBuilder", Opcodes.INTEGER), 0)
            ALOAD(1)
            ALOAD(0)
            ILOAD(2)
            INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C")
            LDC(key)
            if (materialKeyName != null) {
                INVOKESTATIC(className, materialKeyName, "()I")
                IXOR
            }
            IXOR
            I2C
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;")
            POP
            IINC(2, 1)

            //C:
            LABEL(L["labelC"])
            FRAME(Opcodes.F_SAME, 0, null, 0)
            ILOAD(2)
            ALOAD(0)
            INVOKEVIRTUAL("java/lang/String", "length", "()I")
            IF_ICMPLT(L["labelB"])
            ALOAD(1)
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
            ARETURN
        }
        MAXS(if (materialKeyName == null) 3 else 4, 3)
    }

    private fun createMaterialKeyMethod(
        className: String,
        methodName: String,
        material: RuntimeMaterial
    ): MethodNode = method(
        PRIVATE + STATIC + SYNTHETIC + BRIDGE,
        methodName,
        "()I"
    ) {
        INSTRUCTIONS {
            // The ReferenceObfuscate side consumes only the canonical quotient:
            // clean RuntimeMaterial perturbations may churn raw share fields, but the
            // KDF lane reads canonicalKey + sticky poison so BSM linkage cannot
            // observe a transient two-field update. Suspicious paths disturb
            // canonicalKey and/or poison, producing a wrong decrypt key.
            GETSTATIC(className, material.keyField, "J")
            DUP2
            INT(32)
            LUSHR
            LXOR
            L2I
            GETSTATIC(className, material.poisonField, "I")
            IXOR
            IRETURN
        }
        MAXS(6, 0)
    }

    fun encrypt(string: String, xor: Int): String {
        val stringBuilder = StringBuilder()
        for (element in string) {
            stringBuilder.append((element.code xor xor).toChar())
        }
        return stringBuilder.toString()
    }

    private fun findRuntimeMaterial(classNode: ClassNode): RuntimeMaterial? {
        val materialAnnotation = classNode.findAnnotation(DRAFT_RUNTIME_MATERIAL) ?: return null
        val materialId = materialAnnotation.value("id") as? String ?: return null
        val seed = materialAnnotation.value("seed") as? Long ?: return null
        classNode.findRuntimeMaterialField(materialId, RUNTIME_MATERIAL_FIELD_ROLE_SHARE_A, "J") ?: return null
        classNode.findRuntimeMaterialField(materialId, RUNTIME_MATERIAL_FIELD_ROLE_SHARE_B, "J") ?: return null
        val key = classNode.findRuntimeMaterialField(materialId, RUNTIME_MATERIAL_FIELD_ROLE_CANONICAL_KEY, "J")
            ?: return null
        val poison =
            classNode.findRuntimeMaterialField(materialId, RUNTIME_MATERIAL_FIELD_ROLE_POISON, "I") ?: return null
        return RuntimeMaterial(
            keyField = key.name,
            poisonField = poison.name,
            seedFold = seed.foldToInt()
        )
    }

    private fun ClassNode.findRuntimeMaterialField(materialId: String, role: Int, desc: String): FieldNode? {
        return fields.firstOrNull { field ->
            if (field.desc != desc) return@firstOrNull false
            val annotation = field.findAnnotation(DRAFT_RUNTIME_MATERIAL_FIELD) ?: return@firstOrNull false
            annotation.value("id") == materialId && annotation.value("role") == role
        }
    }

    private fun AnnotationNode.value(name: String): Any? {
        val values = values ?: return null
        var index = 0
        while (index + 1 < values.size) {
            if (values[index] == name) return values[index + 1]
            index += 2
        }
        return null
    }

    private fun Long.foldToInt(): Int = (this xor (this ushr 32)).toInt()

    private data class RuntimeMaterial(
        val keyField: String,
        val poisonField: String,
        val seedFold: Int
    )

    class MetaData(
        val d1: IntArray,
        val d2: Array<String>,
        val ed1: Array<String>,
        val ed2: Array<String>,
        val m1: IntArray,
        val m2: IntArray
    )

    private companion object {
        const val RUNTIME_MATERIAL_FIELD_ROLE_SHARE_A = 1
        const val RUNTIME_MATERIAL_FIELD_ROLE_SHARE_B = 2
        const val RUNTIME_MATERIAL_FIELD_ROLE_POISON = 3
        const val RUNTIME_MATERIAL_FIELD_ROLE_CANONICAL_KEY = 4
        const val REOBF_EXCEPTION_BRIDGE_INTERNAL_NAME = "java/lang/RuntimeException"
        const val REOBF_MAX_EXCEPTION_BRIDGES_PER_METHOD = 3
        const val REOBF_MIN_EXCEPTION_BRIDGE_DISTANCE = 1
    }

}
