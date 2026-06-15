package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow

import kotlinx.serialization.Serializable
import net.spartanb312.grunt.ir.flow.core.*
import net.spartanb312.grunt.ir.flow.jvm.*
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.hierarchy.ClassHierarchyFlowTypeHierarchy
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.*
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.*
import net.spartanb312.grunteon.obfuscator.process.transformers.other.FakeSyntheticBridge
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isMixinClass
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter

@Transformer.Stability(StableLevel.Moderate)
@Transformer.Description(
    "process.controlflow.controlflow_jump.desc",
    "Insert verifier-safe junk branches through Flow IR"
)
class ControlflowJump : Transformer<ControlflowJump.Config>(
    "ControlflowJump",
    Category.Controlflow,
) {
    init {
        before(FakeSyntheticBridge::class.java, "ControlflowJump should run before FakeSyntheticBridge")

    }

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Chance to wrap an eligible Flow edge with an opaque junk branch.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Junk branch chance")
        val chance: Decimal = 0.25.toDecimal(),
        @SettingDesc("Chance to expand an eligible IF jump into opaque true gates with fake junk branches.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Mangled IF chance")
        val mangledIfChance: Decimal = 0.25.toDecimal(),
        @SettingDesc("Maximum junk branches inserted into one method")
        @IntRangeVal(min = 1, max = 32)
        @SettingName("Max branches per method")
        val maxBranchesPerMethod: Int = 4,
        @SettingDesc("Maximum IF jumps mangled in one method")
        @IntRangeVal(min = 0, max = 32)
        @SettingName("Max mangled IFs per method")
        val maxMangledIfsPerMethod: Int = 4,
        @SettingDesc("Chance that a mangled IF fake branch loops back to its gate instead of returning through JunkCode.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Mangled fake loop chance")
        val mangledFakeLoopChance: Decimal = 0.35.toDecimal(),
        @SettingDesc("Chance that a fake junk branch emits a junk prelude and jumps to a shared terminal junk exit instead of owning its own terminator.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Shared junk exit chance")
        val sharedJunkExitChance: Decimal = 0.65.toDecimal(),
        @SettingDesc("Chance that a terminal junk exit throws null instead of returning a junk value.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Junk terminal throw chance")
        val junkTerminalThrowChance: Decimal = 0.2.toDecimal(),
        @SettingDesc("Chance to place a junk landing block immediately after a CFF dispatcher switch.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Dispatcher landing junk chance")
        val dispatcherLandingJunkChance: Decimal = 0.0.toDecimal(),
        @SettingDesc("Maximum dispatcher-adjacent junk landing blocks inserted into one method")
        @IntRangeVal(min = 0, max = 32)
        @SettingName("Max dispatcher landing junk blocks")
        val maxDispatcherLandingJunkBlocksPerMethod: Int = 4,
        @SettingDesc("Chance to reroute an eligible real Flow edge through a throw/catch bridge. Range: 0.0..1.0")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Exception bridge chance")
        val exceptionBridgeChance: Double = 0.0,
        @SettingDesc("Maximum throw/catch bridges inserted into one method")
        @IntRangeVal(min = 0, max = 32)
        @SettingName("Max exception bridges")
        val maxExceptionBridgesPerMethod: Int = 4,
        @SettingDesc("Minimum main arithmetic steps in generated opaque predicate processor actions")
        @IntRangeVal(min = 1, max = 8)
        @SettingName("Predicate min main steps")
        val predicateProcessorMinMainSteps: Int = 1,
        @SettingDesc("Maximum main arithmetic steps in generated opaque predicate processor actions")
        @IntRangeVal(min = 1, max = 8)
        @SettingName("Predicate max main steps")
        val predicateProcessorMaxMainSteps: Int = 2,
        @SettingDesc("Minimum extra arithmetic steps in generated opaque predicate processor actions")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Predicate min extra steps")
        val predicateProcessorMinExtraSteps: Int = 0,
        @SettingDesc("Maximum extra arithmetic steps in generated opaque predicate processor actions")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Predicate max extra steps")
        val predicateProcessorMaxExtraSteps: Int = 1,
        @SettingDesc("Minimum steps used to build encoded opaque predicate constants")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Predicate min chain steps")
        val predicateProcessorMinChainSteps: Int = 1,
        @SettingDesc("Maximum steps used to build encoded opaque predicate constants")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Predicate max chain steps")
        val predicateProcessorMaxChainSteps: Int = 2,
        @SettingDesc("Chance that an opaque predicate gate uses ThreadLocalRandom.nextInt(bound) with lightweight processor actions.")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Predicate random bound chance")
        val predicateRandomBoundChance: Decimal = 0.15.toDecimal(),
        @SettingDesc("Minimum main arithmetic steps in random-bound predicate processor actions")
        @IntRangeVal(min = 1, max = 8)
        @SettingName("Random bound predicate min main steps")
        val randomBoundPredicateMinMainSteps: Int = 1,
        @SettingDesc("Maximum main arithmetic steps in random-bound predicate processor actions")
        @IntRangeVal(min = 1, max = 8)
        @SettingName("Random bound predicate max main steps")
        val randomBoundPredicateMaxMainSteps: Int = 1,
        @SettingDesc("Minimum extra arithmetic steps in random-bound predicate processor actions")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Random bound predicate min extra steps")
        val randomBoundPredicateMinExtraSteps: Int = 0,
        @SettingDesc("Maximum extra arithmetic steps in random-bound predicate processor actions")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Random bound predicate max extra steps")
        val randomBoundPredicateMaxExtraSteps: Int = 0,
        @SettingDesc("Minimum constant-chain steps in random-bound predicate processor actions")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Random bound predicate min chain steps")
        val randomBoundPredicateMinChainSteps: Int = 1,
        @SettingDesc("Maximum constant-chain steps in random-bound predicate processor actions")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Random bound predicate max chain steps")
        val randomBoundPredicateMaxChainSteps: Int = 1,
        @SettingDesc("Maximum junk call preludes emitted before a junk terminal return")
        @IntRangeVal(min = 0, max = 8)
        @SettingName("Max prelude calls")
        val maxPreludeCalls: Int = 2,
        @SettingDesc("Use public static methods as junk return values when hierarchy proves assignability")
        @SettingName("Assignable junk returns")
        val assignableJunkReturns: Boolean = true,
        @SettingDesc("Use natural reference return values such as strings, wrappers, and empty arrays")
        @SettingName("Natural reference values")
        val naturalReferenceValues: Boolean = true,
        @SettingDesc("Allow library classes to be scanned for junk call candidates")
        @SettingName("Expanded junk calls")
        val expandedJunkCalls: Boolean = false,
        @SettingDesc("Run ASM BasicInterpreter after exporting Flow IR bytecode")
        @SettingName("Verify bytecode")
        val verifyBytecode: Boolean = true,
        @SettingDesc("Analyzer used before importing methods into Flow IR")
        @SettingName("Flow analyzer")
        val flowAnalyzer: JvmFlowAnalyzerMode = JvmFlowAnalyzerMode.Hierarchy,
        @SettingDesc("Keep going when one method cannot be transformed")
        @SettingName("Ignore failures")
        val ignoreFailures: Boolean = false,
        @SettingDesc("Parallel class processing batch size")
        @SettingName("Worker batch size")
        val workerBatchSize: Int = 16,
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
        pre {
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }

        val hierarchyKey = globalScopeValue {
            ClassHierarchy.build(instance.workRes.allClassCollection, instance.workRes::getClassNode)
        }
        val junkCallPoolKey = globalScopeValue {
            val classes = if (config.expandedJunkCalls) {
                instance.workRes.allClassCollection
            } else {
                instance.workRes.inputClassCollection
            }.filterNot { it.isMixinClass }
            JunkCallPool.build(classes)
        }
        val junkStringProviderKey = globalScopeValue {
            junkStringProvider(instance.workRes.getStringPool(WorkResources.ANTI_LLM_STRING_POOL))
        }
        val predicateProcessorRegistryKey = globalScopeValue {
            OpaquePredicateProcessorRegistry(
                classMarker = Xoshiro256PPRandom(getSeed("ControlflowJump", "PredicateProcessor", "classMarker"))
                    .getRandomString(10),
                classExists = {
                    instance.workRes.inputClassMap.containsKey(it) ||
                            instance.workRes.libraryClassMap.containsKey(it)
                },
                options = OpaquePredicateProcessorOptions(
                    minMainSteps = config.predicateProcessorMinMainSteps,
                    maxMainSteps = config.predicateProcessorMaxMainSteps,
                    minExtraSteps = config.predicateProcessorMinExtraSteps,
                    maxExtraSteps = config.predicateProcessorMaxExtraSteps,
                    minChainSteps = config.predicateProcessorMinChainSteps,
                    maxChainSteps = config.predicateProcessorMaxChainSteps,
                    randomBoundChance = config.predicateRandomBoundChance.toDouble(),
                    randomBoundMinMainSteps = config.randomBoundPredicateMinMainSteps,
                    randomBoundMaxMainSteps = config.randomBoundPredicateMaxMainSteps,
                    randomBoundMinExtraSteps = config.randomBoundPredicateMinExtraSteps,
                    randomBoundMaxExtraSteps = config.randomBoundPredicateMaxExtraSteps,
                    randomBoundMinChainSteps = config.randomBoundPredicateMinChainSteps,
                    randomBoundMaxChainSteps = config.randomBoundPredicateMaxChainSteps
                )
            )
        }
        val methodCounter = reducibleScopeValue { MergeableCounter() }
        val branchCounter = reducibleScopeValue { MergeableCounter() }
        val mangledIfCounter = reducibleScopeValue { MergeableCounter() }
        val dispatcherLandingJunkCounter = reducibleScopeValue { MergeableCounter() }
        val exceptionBridgeCounter = reducibleScopeValue { MergeableCounter() }
        val failureCounter = reducibleScopeValue { MergeableCounter() }

        parForEachClassesFiltered(
            config.classFilter.buildFilterStrategy(),
            config.workerBatchSize.coerceAtLeast(1)
        ) { classNode ->
            if (classNode.isExcluded(DISABLE_CONTROL_FLOW) || classNode.isExcluded(IGNORE_JUNK_CODE)) {
                return@parForEachClassesFiltered
            }

            val hierarchy = hierarchyKey.global
            val flowTypeHierarchy = ClassHierarchyFlowTypeHierarchy(hierarchy)
            val pool = junkCallPoolKey.global
            val transformedMethods = classNode.methods.map { methodNode ->
                if (methodNode.isAbstract || methodNode.isNative || methodNode.name == "<init>") {
                    methodNode
                } else if (methodNode.isExcluded(DISABLE_CONTROL_FLOW) || methodNode.isExcluded(IGNORE_JUNK_CODE)) {
                    methodNode
                } else if (methodExPredicate.matchedAnyBy(methodFullDesc(classNode, methodNode))) {
                    methodNode
                } else {
                    runCatching {
                        val random = Xoshiro256PPRandom(
                            getSeed(classNode.name, methodNode.name, methodNode.desc, "ControlflowJump")
                        )
                        methodNode.insertJunkBranches(
                            owner = classNode,
                            config = config,
                            hierarchy = hierarchy,
                            flowTypeHierarchy = flowTypeHierarchy,
                            pool = pool,
                            stringProvider = junkStringProviderKey.global,
                            predicateProcessor = predicateProcessorRegistryKey.global.methodProcessor(
                                owner = classNode.name,
                                ownerVersion = classNode.version,
                                methodMarker = Xoshiro256PPRandom(
                                    getSeed(
                                        classNode.name,
                                        methodNode.name,
                                        methodNode.desc,
                                        "ControlflowJump",
                                        "PredicateProcessor",
                                        "methodMarker"
                                    )
                                ).getRandomString(8)
                            ),
                            random = random
                        )
                    }.fold(
                        onSuccess = { result ->
                            if (result.changed) {
                                methodCounter.local.add()
                                branchCounter.local.add(result.branches)
                                mangledIfCounter.local.add(result.mangledIfs)
                                dispatcherLandingJunkCounter.local.add(result.dispatcherLandingJunkBlocks)
                                exceptionBridgeCounter.local.add(result.exceptionBridges)
                            }
                            result.method
                        },
                        onFailure = {
                            failureCounter.local.add()
                            if (!config.ignoreFailures) {
                                throw IllegalStateException(
                                    "Failed to insert junk branches in ${classNode.name}.${methodNode.name}${methodNode.desc}",
                                    it
                                )
                            }
                            Logger.warn("ControlflowJump skipped ${classNode.name}.${methodNode.name}${methodNode.desc}: ${it.message}")
                            methodNode
                        }
                    )
                }
            }
            classNode.methods.clear()
            classNode.methods.addAll(transformedMethods)
        }

        post {
            val predicateRegistry = predicateProcessorRegistryKey.global
            predicateRegistry.materialize().forEach {
                instance.workRes.addGeneratedClass(it)
            }
            Logger.info(" - ControlflowJump:")
            Logger.info("    Inserted ${branchCounter.global.get()} junk branches")
            Logger.info("    Mangled ${mangledIfCounter.global.get()} conditional jumps")
            Logger.info("    Placed ${dispatcherLandingJunkCounter.global.get()} dispatcher landing junk blocks")
            Logger.info("    Routed ${exceptionBridgeCounter.global.get()} edges through exception bridges")
            Logger.info("    Generated ${predicateRegistry.classCount} predicate processor classes")
            Logger.info("    Added ${predicateRegistry.actionCount} predicate processor actions")
            Logger.info("    Transformed ${methodCounter.global.get()} methods")
            if (failureCounter.global.get() != 0) {
                Logger.warn("    Failed ${failureCounter.global.get()} methods")
            }
        }
    }

    private fun MethodNode.insertJunkBranches(
        owner: ClassNode,
        config: Config,
        hierarchy: ClassHierarchy,
        flowTypeHierarchy: JvmFlowTypeHierarchy,
        pool: JunkCallPool,
        stringProvider: JunkStringProvider?,
        predicateProcessor: FlowOpaquePredicateProcessor,
        random: UniformRandomProvider
    ): JunkBranchMethod {
        val imported = JvmFlowImporter(
            analyzerMode = config.flowAnalyzer,
            typeHierarchy = flowTypeHierarchy
        ).import(owner.name, this)
        val result = FlowJunkBranchInserter(
            options = JunkBranchOptions(
                chance = config.chance.toDouble().coerceIn(0.0, 1.0),
                mangledIfChance = config.mangledIfChance.toDouble().coerceIn(0.0, 1.0),
                maxBranchesPerMethod = config.maxBranchesPerMethod.coerceAtLeast(1),
                maxMangledIfsPerMethod = config.maxMangledIfsPerMethod.coerceAtLeast(0),
                mangledFakeLoopChance = config.mangledFakeLoopChance.toDouble().coerceIn(0.0, 1.0),
                sharedJunkExitChance = config.sharedJunkExitChance.toDouble().coerceIn(0.0, 1.0),
                dispatcherLandingJunkChance = config.dispatcherLandingJunkChance.toDouble().coerceIn(0.0, 1.0),
                maxDispatcherLandingJunkBlocksPerMethod = config.maxDispatcherLandingJunkBlocksPerMethod.coerceAtLeast(0),
                exceptionBridgeOptions = FlowExceptionBridgeOptions(
                    chance = config.exceptionBridgeChance.coerceIn(0.0, 1.0),
                    maxBridgesPerMethod = config.maxExceptionBridgesPerMethod.coerceAtLeast(0)
                ),
                junkCodeOptions = JunkCodeOptions(
                    maxPreludeCalls = config.maxPreludeCalls.coerceAtLeast(0),
                    useJunkCallPrelude = !pool.isEmpty(),
                    useNaturalReferenceValues = config.naturalReferenceValues,
                    useAssignableJunkReturns = config.assignableJunkReturns,
                    junkReturnChance = 0.35,
                    terminalThrowChance = config.junkTerminalThrowChance.toDouble()
                )
            ),
            callPool = pool,
            hierarchy = hierarchy,
            stringProvider = stringProvider,
            predicateProcessor = predicateProcessor,
            random = random
        ).insert(imported.method)

        if (!result.changed) return JunkBranchMethod(this, changed = false)

        val exported = JvmFlowExporter(
            imported.metadata,
            JvmFlowExportOptions(
                access = access,
                signature = signature,
                exceptions = exceptions?.toList() ?: emptyList()
            ),
            flowTypeHierarchy
        ).export(imported.method)
        copyMethodMetadataTo(exported)

        if (config.verifyBytecode) {
            Analyzer(BasicInterpreter()).analyze(owner.name, exported)
        }

        return JunkBranchMethod(
            exported,
            changed = true,
            branches = result.branches,
            mangledIfs = result.mangledIfs,
            dispatcherLandingJunkBlocks = result.dispatcherLandingJunkBlocks,
            exceptionBridges = result.exceptionBridges
        )
    }

    private fun MethodNode.copyMethodMetadataTo(target: MethodNode) {
        target.parameters = parameters
        target.visibleAnnotations = visibleAnnotations
        target.invisibleAnnotations = invisibleAnnotations
        target.visibleTypeAnnotations = visibleTypeAnnotations
        target.invisibleTypeAnnotations = invisibleTypeAnnotations
        target.visibleParameterAnnotations = visibleParameterAnnotations
        target.invisibleParameterAnnotations = invisibleParameterAnnotations
        target.visibleAnnotableParameterCount = visibleAnnotableParameterCount
        target.invisibleAnnotableParameterCount = invisibleAnnotableParameterCount
        target.annotationDefault = annotationDefault
        target.attrs = attrs
    }

    private data class JunkBranchOptions(
        val chance: Double,
        val mangledIfChance: Double,
        val maxBranchesPerMethod: Int,
        val maxMangledIfsPerMethod: Int,
        val mangledFakeLoopChance: Double,
        val sharedJunkExitChance: Double,
        val dispatcherLandingJunkChance: Double,
        val maxDispatcherLandingJunkBlocksPerMethod: Int,
        val exceptionBridgeOptions: FlowExceptionBridgeOptions,
        val junkCodeOptions: JunkCodeOptions
    )

    private data class JunkBranchResult(
        val changed: Boolean,
        val branches: Int = 0,
        val mangledIfs: Int = 0,
        val dispatcherLandingJunkBlocks: Int = 0,
        val exceptionBridges: Int = 0
    )

    private data class JunkBranchMethod(
        val method: MethodNode,
        val changed: Boolean,
        val branches: Int = 0,
        val mangledIfs: Int = 0,
        val dispatcherLandingJunkBlocks: Int = 0,
        val exceptionBridges: Int = 0
    )

    private class FlowJunkBranchInserter(
        private val options: JunkBranchOptions,
        private val callPool: JunkCallPool,
        private val hierarchy: ClassHierarchy,
        private val stringProvider: JunkStringProvider?,
        private val predicateProcessor: FlowOpaquePredicateProcessor,
        private val random: UniformRandomProvider
    ) {
        private var nextPredicateSite = 0

        fun insert(method: FlowMethod): JunkBranchResult {
            val ids = MutableFlowIds(method)
            val junk = JunkCodeGenerator(callPool, hierarchy, options.junkCodeOptions, random, stringProvider)
            val junkExits = JunkExitPlanner(method, ids, junk)
            val mangledIfs = insertMangledIfs(method, ids, junk, junkExits)
            val dispatcherLandingJunkBlocks = insertDispatcherLandingJunkBlocks(method, ids, junkExits)
            val branches = dispatcherLandingJunkBlocks + insertJunkEdges(
                method,
                ids,
                junkExits,
                maxBranches = options.maxBranchesPerMethod - dispatcherLandingJunkBlocks
            )
            val exceptionBridges = FlowExceptionBridgeInserter(
                options = options.exceptionBridgeOptions,
                random = random
            ).insert(method).bridges

            return JunkBranchResult(
                changed = branches != 0 || mangledIfs != 0 || exceptionBridges != 0,
                branches = branches,
                mangledIfs = mangledIfs,
                dispatcherLandingJunkBlocks = dispatcherLandingJunkBlocks,
                exceptionBridges = exceptionBridges
            )
        }

        private fun insertMangledIfs(
            method: FlowMethod,
            ids: MutableFlowIds,
            junk: JunkCodeGenerator,
            junkExits: JunkExitPlanner
        ): Int {
            if (options.maxMangledIfsPerMethod <= 0 || options.mangledIfChance <= 0.0) return 0
            val candidates = method.blocks
                .filter { it.isEligibleMangledIf(method) }
                .toMutableList()
            shuffle(candidates)

            var inserted = 0
            for (block in candidates) {
                if (inserted >= options.maxMangledIfsPerMethod) break
                if (random.nextDouble() >= options.mangledIfChance) continue
                val jump = block.jump as? FlowIfJump ?: continue
                val branchEdge = method.edgeFrom(block, jump.branchPort) ?: continue
                val fallthroughEdge = method.edgeFrom(block, jump.fallthroughPort) ?: continue
                if (!branchEdge.isEligibleMangledIfEdge(method)) continue
                if (!fallthroughEdge.isEligibleMangledIfEdge(method)) continue
                val sourceFrame = runCatching { FlowVerifier.frameAfterJump(block) }.getOrNull() ?: continue
                if (sourceFrame.hasUninitialized()) continue

                val branchTarget = branchEdge.to
                val fallthroughTarget = fallthroughEdge.to
                val branchGate = createMangledGateBlock(ids.block(), sourceFrame)
                val fallthroughGate = createMangledGateBlock(ids.block(), sourceFrame)

                method.addBlock(branchGate)
                method.addBlock(fallthroughGate)
                branchEdge.to = branchGate
                branchEdge.flags += FlowEdgeFlag.Inserted
                fallthroughEdge.to = fallthroughGate
                fallthroughEdge.flags += FlowEdgeFlag.Inserted

                connectMangledGate(method, ids, branchGate, branchTarget, sourceFrame, junk, junkExits)
                connectMangledGate(method, ids, fallthroughGate, fallthroughTarget, sourceFrame, junk, junkExits)
                inserted++
            }

            return inserted
        }

        private fun insertJunkEdges(
            method: FlowMethod,
            ids: MutableFlowIds,
            junkExits: JunkExitPlanner,
            maxBranches: Int = options.maxBranchesPerMethod
        ): Int {
            if (maxBranches <= 0) return 0
            val candidates = method.edges
                .filter { it.isEligibleJunkEdge(method) }
                .toMutableList()
            shuffle(candidates)

            var inserted = 0
            for (edge in candidates) {
                if (inserted >= maxBranches) break
                if (random.nextDouble() >= options.chance) continue
                val sourceFrame = runCatching { FlowVerifier.frameAfterJump(edge.from) }.getOrNull() ?: continue
                if (sourceFrame.hasUninitialized()) continue

                val originalTarget = edge.to
                val guard = createGuardBlock(ids.block(), sourceFrame)
                method.addBlock(guard)
                val junkTarget = junkExits.createTarget(sourceFrame)

                edge.to = guard
                edge.flags += FlowEdgeFlag.Inserted
                method.addEdge(
                    FlowEdge(
                        id = ids.edge(),
                        from = guard,
                        port = FlowPort.Branch,
                        to = originalTarget,
                        semantics = FlowEdgeSemantics.Real,
                        flags = mutableSetOf(FlowEdgeFlag.Inserted)
                    )
                )
                method.addEdge(
                    FlowEdge(
                        id = ids.edge(),
                        from = guard,
                        port = FlowPort.Fallthrough,
                        to = junkTarget,
                        semantics = FlowEdgeSemantics.Junk,
                        flags = mutableSetOf(FlowEdgeFlag.Inserted)
                    )
                )
                inserted++
            }

            return inserted
        }

        private fun insertDispatcherLandingJunkBlocks(
            method: FlowMethod,
            ids: MutableFlowIds,
            junkExits: JunkExitPlanner
        ): Int {
            if (options.maxDispatcherLandingJunkBlocksPerMethod <= 0) return 0
            if (options.dispatcherLandingJunkChance <= 0.0) return 0

            val dispatchers = method.layoutOrder()
                .filter { it.isDispatcherLandingCandidate(method) }
                .toMutableList()
            shuffle(dispatchers)

            val launcherEdgesByDispatcher = method.edges
                .filter { it.isEligibleDispatcherLandingLauncher(method) }
                .groupBy { it.to }
            val usedLaunchers = mutableSetOf<FlowEdge>()
            val limit = minOf(options.maxDispatcherLandingJunkBlocksPerMethod, options.maxBranchesPerMethod)

            var inserted = 0
            for (dispatcher in dispatchers) {
                if (inserted >= limit) break
                if (random.nextDouble() >= options.dispatcherLandingJunkChance) continue
                val launchers = launcherEdgesByDispatcher[dispatcher]
                    .orEmpty()
                    .filter { it !in usedLaunchers }
                    .toMutableList()
                if (launchers.isEmpty()) continue
                shuffle(launchers)
                val launcher = launchers.first()
                val sourceFrame = runCatching { FlowVerifier.frameAfterJump(launcher.from) }.getOrNull() ?: continue
                if (sourceFrame != dispatcher.entryFrame || sourceFrame.hasUninitialized()) continue

                val guard = createGuardBlock(ids.block(), sourceFrame)
                method.addBlock(guard)
                val landing = junkExits.createTarget(sourceFrame)

                launcher.to = guard
                launcher.flags += FlowEdgeFlag.Inserted
                method.addEdge(
                    FlowEdge(
                        id = ids.edge(),
                        from = guard,
                        port = FlowPort.Branch,
                        to = dispatcher,
                        semantics = FlowEdgeSemantics.OpaqueTrue,
                        flags = mutableSetOf(FlowEdgeFlag.Inserted)
                    )
                )
                method.addEdge(
                    FlowEdge(
                        id = ids.edge(),
                        from = guard,
                        port = FlowPort.Fallthrough,
                        to = landing,
                        semantics = FlowEdgeSemantics.Junk,
                        flags = mutableSetOf(FlowEdgeFlag.Inserted)
                    )
                )
                method.placeBlockAfter(dispatcher, landing)
                usedLaunchers += launcher
                inserted++
            }

            return inserted
        }

        private fun FlowBlock.isEligibleMangledIf(method: FlowMethod): Boolean {
            if (kind == FlowBlockKind.Junk || kind == FlowBlockKind.Trap) return false
            val jump = jump as? FlowIfJump ?: return false
            val branchEdge = method.edgeFrom(this, jump.branchPort) ?: return false
            val fallthroughEdge = method.edgeFrom(this, jump.fallthroughPort) ?: return false
            if (!branchEdge.isEligibleMangledIfEdge(method)) return false
            if (!fallthroughEdge.isEligibleMangledIfEdge(method)) return false
            return runCatching {
                val sourceFrame = FlowVerifier.frameAfterJump(this)
                !sourceFrame.hasUninitialized()
            }.getOrDefault(false)
        }

        private fun FlowEdge.isEligibleMangledIfEdge(method: FlowMethod): Boolean {
            if (semantics != FlowEdgeSemantics.Real) return false
            if (FlowEdgeFlag.DoNotMutate in flags || FlowEdgeFlag.LayoutSensitive in flags) return false
            if (FlowEdgeFlag.Inserted in flags) return false
            if (from.kind == FlowBlockKind.Junk || to.kind == FlowBlockKind.Junk) return false
            if (from !in method.blocks || to !in method.blocks) return false
            return true
        }

        private fun connectMangledGate(
            method: FlowMethod,
            ids: MutableFlowIds,
            gate: FlowBlock,
            realTarget: FlowBlock,
            frame: FlowFrame,
            junk: JunkCodeGenerator,
            junkExits: JunkExitPlanner
        ) {
            val fakeTarget = if (random.nextDouble() < options.mangledFakeLoopChance) {
                createMangledLoopBlock(method, ids, gate, frame, junk)
            } else {
                junkExits.createTarget(frame)
            }
            method.addEdge(
                FlowEdge(
                    id = ids.edge(),
                    from = gate,
                    port = FlowPort.Branch,
                    to = realTarget,
                    semantics = FlowEdgeSemantics.OpaqueTrue,
                    flags = mutableSetOf(FlowEdgeFlag.Inserted)
                )
            )
            method.addEdge(
                FlowEdge(
                    id = ids.edge(),
                    from = gate,
                    port = FlowPort.Fallthrough,
                    to = fakeTarget,
                    semantics = FlowEdgeSemantics.Junk,
                    flags = mutableSetOf(FlowEdgeFlag.Inserted)
                )
            )
        }

        private fun createMangledLoopBlock(
            method: FlowMethod,
            ids: MutableFlowIds,
            gate: FlowBlock,
            frame: FlowFrame,
            junk: JunkCodeGenerator
        ): FlowBlock {
            val body = FlowBytecodeSlice()
            junk.appendStackNeutralJunk(body, minimumCalls = 1)
            val loop = FlowBlock(
                id = ids.block(),
                kind = FlowBlockKind.Bogus,
                body = body,
                jump = FlowGotoJump(FlowGotoMode.ExplicitGoto),
                entryFrame = frame,
                bodyExitFrame = frame
            )
            method.addBlock(loop)
            method.addEdge(
                FlowEdge(
                    id = ids.edge(),
                    from = loop,
                    port = FlowPort.Next,
                    to = gate,
                    semantics = FlowEdgeSemantics.Bogus,
                    flags = mutableSetOf(FlowEdgeFlag.Inserted)
                )
            )
            return loop
        }

        private inner class JunkExitPlanner(
            private val method: FlowMethod,
            private val ids: MutableFlowIds,
            private val junk: JunkCodeGenerator
        ) {
            private val sharedTerminalByFrame = linkedMapOf<FlowFrame, FlowBlock>()

            fun createTarget(frame: FlowFrame): FlowBlock {
                return if (random.nextDouble() < options.sharedJunkExitChance) {
                    createSharedPrelude(frame)
                } else {
                    junk.createTerminalBlock(ids.block(), method, frame).also { method.addBlock(it) }
                }
            }

            private fun createSharedPrelude(frame: FlowFrame): FlowBlock {
                val prelude = FlowBlock(
                    id = ids.block(),
                    kind = FlowBlockKind.Junk,
                    body = FlowBytecodeSlice().also { junk.appendStackNeutralJunk(it, minimumCalls = 1) },
                    jump = FlowGotoJump(FlowGotoMode.ExplicitGoto),
                    entryFrame = frame,
                    bodyExitFrame = frame
                )
                method.addBlock(prelude)

                val terminal = sharedTerminalByFrame.getOrPut(frame) {
                    junk.createTerminalBlock(ids.block(), method, frame).also { method.addBlock(it) }
                }
                method.addEdge(
                    FlowEdge(
                        id = ids.edge(),
                        from = prelude,
                        port = FlowPort.Next,
                        to = terminal,
                        semantics = FlowEdgeSemantics.Junk,
                        flags = mutableSetOf(FlowEdgeFlag.Inserted)
                    )
                )
                return prelude
            }
        }

        private fun FlowEdge.isEligibleJunkEdge(method: FlowMethod): Boolean {
            if (semantics != FlowEdgeSemantics.Real) return false
            if (FlowEdgeFlag.DoNotMutate in flags || FlowEdgeFlag.LayoutSensitive in flags) return false
            if (FlowEdgeFlag.Inserted in flags) return false
            if (port == FlowPort.Fallthrough) return false
            if (from.kind == FlowBlockKind.Junk || to.kind == FlowBlockKind.Junk) return false
            if (from !in method.blocks || to !in method.blocks) return false
            return runCatching {
                val sourceFrame = FlowVerifier.frameAfterJump(from)
                !sourceFrame.hasUninitialized()
            }.getOrDefault(false)
        }

        private fun FlowBlock.isDispatcherLandingCandidate(method: FlowMethod): Boolean {
            if (jump !is FlowSwitchJump) return false
            return !hasUnrelatedTrailingBlockBelowSwitch(method)
        }

        private fun FlowBlock.hasUnrelatedTrailingBlockBelowSwitch(method: FlowMethod): Boolean {
            val next = method.nextLayoutBlock(this) ?: return false
            if (next in switchTargets(method)) return false
            if (next.isSwitchTrampoline(method)) return false
            return next.kind == FlowBlockKind.Original ||
                    next.kind == FlowBlockKind.Split ||
                    next.kind == FlowBlockKind.Junk
        }

        private fun FlowBlock.switchTargets(method: FlowMethod): Set<FlowBlock> {
            val switch = jump as? FlowSwitchJump ?: return emptySet()
            return switch.ports
                .mapNotNullTo(mutableSetOf()) { method.edgeFrom(this, it)?.to }
        }

        private fun FlowBlock.isSwitchTrampoline(method: FlowMethod): Boolean {
            if (jump !is FlowGotoJump) return false
            return method.edgeFrom(this, FlowPort.Next)?.to?.jump is FlowSwitchJump
        }

        private fun FlowEdge.isEligibleDispatcherLandingLauncher(method: FlowMethod): Boolean {
            if (to.jump !is FlowSwitchJump) return false
            if (semantics != FlowEdgeSemantics.Real && semantics != FlowEdgeSemantics.Dispatcher) return false
            if (FlowEdgeFlag.DoNotMutate in flags || FlowEdgeFlag.LayoutSensitive in flags) return false
            if (from == to) return false
            if (from.kind == FlowBlockKind.Junk || from.kind == FlowBlockKind.Trap) return false
            if (from !in method.blocks || to !in method.blocks) return false
            return runCatching {
                val sourceFrame = FlowVerifier.frameAfterJump(from)
                sourceFrame == to.entryFrame && !sourceFrame.hasUninitialized()
            }.getOrDefault(false)
        }

        private fun FlowMethod.layoutOrder(): List<FlowBlock> {
            return (layout.order.ifEmpty { blocks }).filter { it in blocks }
        }

        private fun FlowMethod.nextLayoutBlock(block: FlowBlock): FlowBlock? {
            val order = layoutOrder()
            val index = order.indexOf(block)
            return order.getOrNull(index + 1)
        }

        private fun FlowMethod.placeBlockAfter(anchor: FlowBlock, block: FlowBlock) {
            layout.order.remove(block)
            val anchorIndex = layout.order.indexOf(anchor)
            if (anchorIndex == -1) {
                layout.order += block
            } else {
                layout.order.add(anchorIndex + 1, block)
            }
        }

        private fun createGuardBlock(id: FlowBlockId, frame: FlowFrame): FlowBlock {
            return FlowBlock(
                id = id,
                kind = FlowBlockKind.Bogus,
                body = FlowBytecodeSlice(),
                jump = createOpaqueTrueJump(),
                entryFrame = frame,
                bodyExitFrame = frame
            )
        }

        private fun createMangledGateBlock(
            id: FlowBlockId,
            frame: FlowFrame
        ): FlowBlock {
            return FlowBlock(
                id = id,
                kind = FlowBlockKind.Bogus,
                body = FlowBytecodeSlice(),
                jump = createOpaqueTrueJump(),
                entryFrame = frame,
                bodyExitFrame = frame
            )
        }

        private fun createOpaqueTrueJump(): FlowIfJump {
            val call = predicateProcessor.reserveGate(nextPredicateSite++, random)
            val conditionIsFalse = call.guarantee == FlowPredicateGuarantee.AlwaysFalse
            return FlowIfJump(
                opcode = call.opcode,
                input = call.toJumpInput(),
                branchPort = if (conditionIsFalse) FlowPort.Fallthrough else FlowPort.Branch,
                fallthroughPort = if (conditionIsFalse) FlowPort.Branch else FlowPort.Fallthrough
            )
        }

        private fun FlowFrame.hasUninitialized(): Boolean {
            return (locals.asSequence() + stack.asSequence()).any {
                it == FlowFrameValue.UninitializedThis || it is FlowFrameValue.UninitializedNew
            }
        }

        private fun <T> shuffle(values: MutableList<T>) {
            for (index in values.lastIndex downTo 1) {
                val swapIndex = random.nextInt(index + 1)
                val value = values[index]
                values[index] = values[swapIndex]
                values[swapIndex] = value
            }
        }
    }

    private class MutableFlowIds(method: FlowMethod) {
        private var nextBlock = (method.blocks.maxOfOrNull { it.id.value } ?: -1) + 1
        private var nextEdge = (method.edges.maxOfOrNull { it.id.value } ?: -1) + 1

        fun block() = FlowBlockId(nextBlock++)

        fun edge() = FlowEdgeId(nextEdge++)
    }
}
