package net.spartanb312.grunteon.obfuscator.process.transformers.antidebug

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

/*
 * Design notes
 * ------------
 * RuntimeMaterial is intentionally a material perturbation pass, not a centralized
 * runtime detector. The generated program should not contain an obvious
 * RuntimeMaterial.check() style choke point.
 *
 * Runtime model:
 *   raw material space R:
 *     per-class static fields shareA, shareB, canonicalKey, poison
 *
 *   canonical operator T:
 *     clean perturbations move shareA/shareB inside the same raw equivalence
 *     class:
 *
 *       shareA += delta
 *       shareB -= delta
 *
 *     ReferenceObfuscate consumes canonicalKey + sticky poison as KDF input.
 *     That lane is intentionally separate from the mutable share pair so BSM
 *     linkage never observes a transient clean update between the two writes.
 *     Suspicious perturbations set sticky poison and disturb canonicalKey, so
 *     T(raw') != T(raw).
 *
 * Execution model:
 *   <clinit> is an opportunistic class-level seed and sampling point.
 *   <init> is an instance-path sampling point and must be inserted after
 *   the first this/super constructor call. Neither path may depend on class
 *   initialization order, nor on every class being executed.
 *
 * Integration contract:
 *   Draft annotations describe material id, field roles, canonical operator,
 *   and guard kind for future ReferenceObfuscate consumption. They are internal
 *   metadata only and are registered in INTERNAL so PostProcess removes them.
 */
@Transformer.Stability(StableLevel.Experimental)
@Transformer.Description(
    "process.antidebug.runtime_material.desc",
    "Inject distributed runtime material perturbation"
)
class RuntimeMaterial : Transformer<RuntimeMaterial.Config>(
    "RuntimeMaterial",
    Category.AntiDebug,
) {

    init {
        // potential bug
        after(Category.Redirect, "RuntimeMaterial should run after redirect category")
    }

    @Serializable
    @SettingDesc("Detect tokens settings")
    @SettingName("Detection tokens")
    data class DetectTokens(
        @SettingDesc("Detect JDWP tokens")
        @SettingName("jdwp")
        val jdwp: Boolean = true,
        @SettingDesc("Detect legacy -Xdebug tokens")
        @SettingName("-Xdebug")
        val xdebug: Boolean = true,
        @SettingDesc("Detect legacy -Xrunjdwp tokens")
        @SettingName("-Xrunjdwp")
        val xrunjdwp: Boolean = true,
        @SettingDesc("Detect dt_socket debug transport tokens")
        @SettingName("dt_socket")
        val dtSocket: Boolean = true,
        @SettingDesc("Detect dt_shmem debug transport tokens")
        @SettingName("dt_shmem")
        val dtShmem: Boolean = true,
        @SettingDesc("Detect generic -agentlib tokens. Disabled by default to avoid profiler/APM false positives")
        @SettingName("-agentlib")
        val genericAgentlib: Boolean = false,
        @SettingDesc("Detect generic -agentpath tokens. Disabled by default to avoid profiler/APM false positives")
        @SettingName("-agentpath")
        val genericAgentpath: Boolean = false,
        @SettingDesc("Detect generic -javaagent tokens. Disabled by default to avoid instrumentation false positives")
        @SettingName("-javaagent")
        val javaAgent: Boolean = false,
        @SettingDesc("Detect JMX remote tokens. Disabled by default because it is not always debugger evidence")
        @SettingName("jmxremote")
        val jmxRemote: Boolean = false,
        @SettingDesc("Additional lowercase tokens to detect in selected sources")
        @SettingName("Extra detection tokens")
        val extra: List<String> = emptyList(),
    )

    @Serializable
    @SettingDesc("Detect sources settings")
    @SettingName("Detect sources")
    data class DetectSources(
        @SettingDesc("Read JVM RuntimeMXBean input arguments as detection source")
        @SettingName("JVM arguments")
        val inputArguments: Boolean = true,
        @SettingDesc("Read JAVA_TOOL_OPTIONS as detection source")
        @SettingName("JAVA_TOOL_OPTIONS")
        val javaToolOptionsEnv: Boolean = true,
        @SettingDesc("Read JDK_JAVA_OPTIONS as detection source")
        @SettingName("JDK_JAVA_OPTIONS")
        val jdkJavaOptionsEnv: Boolean = true,
        @SettingDesc("Read _JAVA_OPTIONS as detection source")
        @SettingName("_JAVA_OPTIONS")
        val javaOptionsEnv: Boolean = true,
        @SettingDesc("Read com.sun.management.jmxremote property as detection source")
        @SettingName("JMX property")
        val jmxRemoteProperty: Boolean = false,
    )

    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Chance that an included class receives runtime material")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Class chance")
        val classChance: Double = 1.0,
        @SettingDesc("Inject a class-level perturbation into <clinit>")
        @SettingName("Static initializer")
        val clinit: Boolean = true,
        @SettingDesc("Inject instance-path perturbations into constructors")
        @SettingName("Constructors")
        val constructors: Boolean = true,
        @SettingDesc("Maximum number of constructors patched per class")
        @IntRangeVal(min = 0, max = 64)
        @SettingName("Constructor limit")
        val constructorLimit: Int = 8,
        val detectSources: DetectSources = DetectSources(),
        val detectTokens: DetectTokens = DetectTokens(),
        @SettingDesc("Emit draft metadata for ReferenceObfuscate integration")
        @SettingName("Draft material metadata")
        val draftMaterialMetadata: Boolean = true
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val classCounter = reducibleScopeValue { MergeableCounter() }
        val clinitCounter = reducibleScopeValue { MergeableCounter() }
        val initCounter = reducibleScopeValue { MergeableCounter() }

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (!isEligible(classNode)) return@parForEachClassesFiltered
            if (classNode.hasAnnotation(DRAFT_RUNTIME_MATERIAL)) return@parForEachClassesFiltered

            val random = Xoshiro256PPRandom(getSeed("RuntimeMaterial", classNode.name))
            if (random.nextDouble() > config.classChance) return@parForEachClassesFiltered

            // Per-class material is deliberately local to the owner class. The
            // later BSM integration should read these fields from the caller
            // class instead of routing through a shared runtime holder.
            val plan = MaterialPlan.create(classNode, random)
            installMaterialFields(classNode, plan, config)
            val guardMethod = createGuardMethod(plan.guardMethodName, plan.badMask, config)
                .appendAnnotation(GENERATED_METHOD)
            if (config.draftMaterialMetadata) {
                appendInvisibleAnnotation(
                    guardMethod,
                    annotation(
                        DRAFT_RUNTIME_MATERIAL_GUARD,
                        "schema" to SCHEMA,
                        "id" to plan.materialId,
                        "kind" to GUARD_KIND_INPUT_ARGS
                    )
                )
            }
            classNode.methods.add(guardMethod)

            if (config.clinit) {
                installClinitPerturbation(classNode, plan)
                clinitCounter.local.add()
            }

            if (config.constructors && config.constructorLimit > 0) {
                val patched = installConstructorPerturbations(classNode, plan, config.constructorLimit)
                repeat(patched) { initCounter.local.add() }
            }

            classCounter.local.add()
        }

        post {
            Logger.info(" - RuntimeMaterial:")
            Logger.info("    Prepared ${classCounter.global.get()} classes")
            Logger.info("    Patched ${clinitCounter.global.get()} <clinit> blocks")
            Logger.info("    Patched ${initCounter.global.get()} constructors")
        }
    }

    private fun isEligible(classNode: ClassNode): Boolean {
        if (classNode.name == "module-info") return false
        if (classNode.version < Opcodes.V1_5) return false
        if (classNode.isInterface || classNode.isAnnotation) return false
        if (classNode.hasAnnotation(GENERATED_CLASS)) return false
        return true
    }

    private fun installMaterialFields(classNode: ClassNode, plan: MaterialPlan, config: Config) {
        classNode.fields.add(
            materialField(
                plan.shareAField,
                "J",
                plan.materialId,
                FIELD_ROLE_SHARE_A,
                plan.layoutId,
                config.draftMaterialMetadata
            )
        )
        classNode.fields.add(
            materialField(
                plan.shareBField,
                "J",
                plan.materialId,
                FIELD_ROLE_SHARE_B,
                plan.layoutId,
                config.draftMaterialMetadata
            )
        )
        classNode.fields.add(
            materialField(
                plan.keyField,
                "J",
                plan.materialId,
                FIELD_ROLE_CANONICAL_KEY,
                plan.layoutId,
                config.draftMaterialMetadata
            )
        )
        classNode.fields.add(
            materialField(
                plan.poisonField,
                "I",
                plan.materialId,
                FIELD_ROLE_POISON,
                plan.layoutId,
                config.draftMaterialMetadata
            )
        )

        if (config.draftMaterialMetadata) {
            appendInvisibleAnnotation(
                classNode,
                annotation(
                    DRAFT_RUNTIME_MATERIAL,
                    "schema" to SCHEMA,
                    "id" to plan.materialId,
                    "layout" to plan.layoutId,
                    "canonical" to CANONICAL_SHARE_SUM_STICKY_POISON,
                    "seed" to plan.canonicalSeed,
                    "role" to classifyRole(classNode),
                    "guard" to GUARD_KIND_INPUT_ARGS,
                    "flags" to MATERIAL_FLAG_REFERENCE_OBF_READY
                )
            )
        }
    }

    private fun materialField(
        name: String,
        desc: String,
        materialId: String,
        role: Int,
        layoutId: Int,
        emitDraftMetadata: Boolean
    ): FieldNode {
        return field(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
            name,
            desc,
            null,
            null
        ).appendAnnotation(GENERATED_FIELD).also {
            if (emitDraftMetadata) {
                appendInvisibleAnnotation(
                    it,
                    annotation(
                        DRAFT_RUNTIME_MATERIAL_FIELD,
                        "schema" to SCHEMA,
                        "id" to materialId,
                        "role" to role,
                        "layout" to layoutId
                    )
                )
            }
        }
    }

    private fun installClinitPerturbation(classNode: ClassNode, plan: MaterialPlan) {
        var clinit = classNode.methods.firstOrNull { it.name == "<clinit>" && it.desc == "()V" }
        if (clinit == null) {
            clinit = MethodNode(
                Opcodes.ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null
            )
            clinit.instructions.add(InsnNode(Opcodes.RETURN))
            classNode.methods.add(clinit)
        }

        // <clinit> initializes the raw material and then performs one clean or
        // suspicious perturbation. It must not pull in other application classes.
        clinit.instructions.insert(
            clinitMaterialInit(classNode.name, plan).apply {
                add(perturbation(classNode.name, plan, plan.clinitDelta, plan.clinitBadDelta))
            }
        )
    }

    private fun installConstructorPerturbations(
        classNode: ClassNode,
        plan: MaterialPlan,
        limit: Int
    ): Int {
        var patched = 0
        classNode.methods
            .filter { it.name == "<init>" && it.desc.endsWith("V") }
            .forEach { method ->
                if (patched >= limit) return@forEach
                val insertionPoint = firstConstructorCall(method) ?: return@forEach
                // Constructor guards are inserted after the first this/super
                // constructor call so the verifier no longer sees uninitialized
                // this at the injected field access site.
                method.instructions.insert(
                    insertionPoint,
                    perturbation(classNode.name, plan, plan.initDelta, plan.initBadDelta)
                )
                patched++
            }
        return patched
    }

    private fun firstConstructorCall(methodNode: MethodNode): MethodInsnNode? {
        var insn = methodNode.instructions.first
        while (insn != null) {
            if (insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESPECIAL && insn.name == "<init>") {
                return insn
            }
            insn = insn.next
        }
        return null
    }

    private fun clinitMaterialInit(owner: String, plan: MaterialPlan): InsnList = InsnList().apply {
        add(instructions {
            LONG(plan.shareAInitial)
            PUTSTATIC(owner, plan.shareAField, "J")
            LONG(plan.shareBInitial)
            PUTSTATIC(owner, plan.shareBField, "J")
            LONG(plan.canonicalSeed)
            PUTSTATIC(owner, plan.keyField, "J")
            ICONST_0
            PUTSTATIC(owner, plan.poisonField, "I")
        })
    }

    private fun perturbation(
        owner: String,
        plan: MaterialPlan,
        cleanDelta: Long,
        badDelta: Long
    ): InsnList {
        val clean = LabelNode()
        val end = LabelNode()
        // Clean path: raw state changes, canonical material stays stable.
        // Suspicious path: sticky poison is written and shareA is disturbed.
        return instructions {
            GETSTATIC(owner, plan.shareAField, "J")
            LONG(cleanDelta)
            LADD
            PUTSTATIC(owner, plan.shareAField, "J")

            GETSTATIC(owner, plan.shareBField, "J")
            LONG(cleanDelta)
            LSUB
            PUTSTATIC(owner, plan.shareBField, "J")

            INVOKESTATIC(owner, plan.guardMethodName, "()I", false)
            DUP
            IFEQ(clean)
            GETSTATIC(owner, plan.poisonField, "I")
            SWAP
            INT(plan.badMask)
            IOR
            IOR
            PUTSTATIC(owner, plan.poisonField, "I")

            GETSTATIC(owner, plan.shareAField, "J")
            LONG(badDelta)
            LXOR
            PUTSTATIC(owner, plan.shareAField, "J")

            GETSTATIC(owner, plan.keyField, "J")
            LONG(badDelta)
            LXOR
            PUTSTATIC(owner, plan.keyField, "J")
            GOTO(end)

            LABEL(clean)
            POP
            LABEL(end)
        }
    }

    private fun createGuardMethod(name: String, baseMask: Int, config: Config): MethodNode {
        val start = Label()
        val end = Label()
        val handler = Label()

        // This guard samples only user-enabled startup/debugger evidence. It
        // returns a bit mask, not a boolean. The caller decides how that mask
        // perturbs material, keeping detection and response loosely coupled.
        return method(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
            name,
            "()I",
            null,
            null
        ) {
            INSTRUCTIONS {
                TRYCATCH(start, end, handler, "java/lang/Throwable")
                LABEL(start)
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ASTORE(0)

                if (config.detectSources.inputArguments) +appendRuntimeInputArguments()
                if (config.detectSources.javaToolOptionsEnv) +appendEnv("JAVA_TOOL_OPTIONS")
                if (config.detectSources.jdkJavaOptionsEnv) +appendEnv("JDK_JAVA_OPTIONS")
                if (config.detectSources.javaOptionsEnv) +appendEnv("_JAVA_OPTIONS")
                if (config.detectSources.jmxRemoteProperty) +appendPropertyNameWhenSet("com.sun.management.jmxremote")

                ALOAD(0)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKEVIRTUAL("java/lang/String", "toLowerCase", "()Ljava/lang/String;", false)
                ASTORE(1)

                ICONST_0
                ISTORE(2)

                detectionTokens(config, baseMask).forEach { token ->
                    +appendContains(token.value, token.mask)
                }

                ILOAD(2)
                LABEL(end)
                IRETURN

                LABEL(handler)
                POP
                ICONST_0
                IRETURN
            }
            MAXS(3, 3)
        }
    }

    private fun appendRuntimeInputArguments(): InsnList = instructions {
        ALOAD(0)
        INVOKESTATIC(
            "java/lang/management/ManagementFactory",
            "getRuntimeMXBean",
            "()Ljava/lang/management/RuntimeMXBean;",
            false
        )
        INVOKEINTERFACE(
            "java/lang/management/RuntimeMXBean",
            "getInputArguments",
            "()Ljava/util/List;",
            true
        )
        INVOKESTATIC("java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false)
        INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
        POP
    }

    private fun appendEnv(name: String): InsnList = instructions {
        ALOAD(0)
        LDC(name)
        INVOKESTATIC("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;", false)
        INVOKESTATIC("java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false)
        INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
        POP
    }

    private fun appendPropertyNameWhenSet(name: String): InsnList {
        val skip = LabelNode()
        return instructions {
            LDC(name)
            INVOKESTATIC("java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false)
            IFNULL(skip)
            ALOAD(0)
            LDC(name)
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            POP
            LABEL(skip)
        }
    }

    private fun detectionTokens(config: Config, baseMask: Int): List<DetectionToken> {
        val tokens = mutableListOf<DetectionToken>()
        fun add(enabled: Boolean, value: String, bit: Int) {
            if (enabled) tokens += DetectionToken(value, baseMask or bit)
        }
        add(config.detectTokens.jdwp, "jdwp", 0x0001)
        add(config.detectTokens.xdebug, "-xdebug", 0x0002)
        add(config.detectTokens.xrunjdwp, "-xrunjdwp", 0x0004)
        add(config.detectTokens.dtSocket, "transport=dt_socket", 0x0008)
        add(config.detectTokens.dtShmem, "transport=dt_shmem", 0x0010)
        add(config.detectTokens.genericAgentlib, "-agentlib", 0x0020)
        add(config.detectTokens.genericAgentpath, "-agentpath", 0x0040)
        add(config.detectTokens.javaAgent, "-javaagent", 0x0080)
        add(config.detectTokens.jmxRemote, "jmxremote", 0x0100)
        config.detectTokens.extra
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEachIndexed { index, token ->
                val bit = 0x0200 shl (index and 0x7)
                tokens += DetectionToken(token, baseMask or bit)
            }
        return tokens
    }

    private fun appendContains(token: String, mask: Int): InsnList {
        val next = LabelNode()
        return instructions {
            ALOAD(1)
            LDC(token)
            INVOKEVIRTUAL("java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false)
            IFEQ(next)
            ILOAD(2)
            INT(mask)
            IOR
            ISTORE(2)
            LABEL(next)
        }
    }

    private fun classifyRole(classNode: ClassNode): Int {
        val lower = classNode.name.lowercase()
        return when {
            classNode.methods.any { it.name == "main" && it.desc == "([Ljava/lang/String;)V" } -> CLASS_ROLE_ANCHOR
            "license" in lower || "auth" in lower || "decrypt" in lower || "config" in lower -> CLASS_ROLE_ANCHOR
            else -> CLASS_ROLE_OPPORTUNISTIC
        }
    }

    private fun annotation(desc: String, vararg values: Pair<String, Any>): AnnotationNode {
        val node = AnnotationNode(desc)
        values.forEach { (name, value) -> node.visit(name, value) }
        return node
    }

    private fun appendInvisibleAnnotation(classNode: ClassNode, annotation: AnnotationNode) {
        classNode.invisibleAnnotations = classNode.invisibleAnnotations ?: mutableListOf()
        classNode.invisibleAnnotations.add(annotation)
    }

    private fun appendInvisibleAnnotation(fieldNode: FieldNode, annotation: AnnotationNode) {
        fieldNode.invisibleAnnotations = fieldNode.invisibleAnnotations ?: mutableListOf()
        fieldNode.invisibleAnnotations.add(annotation)
    }

    private fun appendInvisibleAnnotation(methodNode: MethodNode, annotation: AnnotationNode) {
        methodNode.invisibleAnnotations = methodNode.invisibleAnnotations ?: mutableListOf()
        methodNode.invisibleAnnotations.add(annotation)
    }

    private data class DetectionToken(
        val value: String,
        val mask: Int
    )

    private data class MaterialPlan(
        val materialId: String,
        val layoutId: Int,
        val shareAField: String,
        val shareBField: String,
        val keyField: String,
        val poisonField: String,
        val guardMethodName: String,
        val shareAInitial: Long,
        val shareBInitial: Long,
        val canonicalSeed: Long,
        val clinitDelta: Long,
        val initDelta: Long,
        val clinitBadDelta: Long,
        val initBadDelta: Long,
        val badMask: Int
    ) {
        companion object {
            fun create(classNode: ClassNode, random: UniformRandomProvider): MaterialPlan {
                val materialId = java.lang.Long.toUnsignedString(random.nextLong(), 36)
                val canonical = random.nextLong()
                val shareA = random.nextLong()
                val shareB = canonical - shareA
                return MaterialPlan(
                    materialId = materialId,
                    layoutId = CANONICAL_SHARE_SUM_STICKY_POISON,
                    shareAField = uniqueName(classNode, random, "a"),
                    shareBField = uniqueName(classNode, random, "b"),
                    keyField = uniqueName(classNode, random, "k"),
                    poisonField = uniqueName(classNode, random, "p"),
                    guardMethodName = uniqueName(classNode, random, "g"),
                    shareAInitial = shareA,
                    shareBInitial = shareB,
                    canonicalSeed = canonical,
                    clinitDelta = nonZero(random.nextLong()),
                    initDelta = nonZero(random.nextLong()),
                    clinitBadDelta = nonZero(random.nextLong()),
                    initBadDelta = nonZero(random.nextLong()),
                    badMask = random.nextInt() and 0x7FFF0000
                )
            }

            private fun uniqueName(
                classNode: ClassNode,
                random: UniformRandomProvider,
                prefix: String
            ): String {
                val existingFields = classNode.fields.mapTo(mutableSetOf()) { it.name }
                val existingMethods = classNode.methods.mapTo(mutableSetOf()) { it.name }
                while (true) {
                    val name = "\$g${prefix}${java.lang.Long.toUnsignedString(random.nextLong(), 36)}"
                    if (name !in existingFields && name !in existingMethods) return name
                }
            }

            private fun nonZero(value: Long): Long = if (value == 0L) -0x5a5a5a5a5a5a5a5bL else value
        }
    }

    private companion object {
        const val SCHEMA = 1

        const val CLASS_ROLE_ANCHOR = 1
        const val CLASS_ROLE_OPPORTUNISTIC = 2

        const val FIELD_ROLE_SHARE_A = 1
        const val FIELD_ROLE_SHARE_B = 2
        const val FIELD_ROLE_POISON = 3
        const val FIELD_ROLE_CANONICAL_KEY = 4

        const val CANONICAL_SHARE_SUM_STICKY_POISON = 1
        const val GUARD_KIND_INPUT_ARGS = 1
        const val MATERIAL_FLAG_REFERENCE_OBF_READY = 1
    }
}
