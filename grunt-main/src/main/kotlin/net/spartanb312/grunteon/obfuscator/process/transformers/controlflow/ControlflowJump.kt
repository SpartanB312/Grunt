package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow

import kotlinx.serialization.Serializable
import net.spartanb312.grunt.ir.flow.core.FlowBlock
import net.spartanb312.grunt.ir.flow.core.FlowBlockId
import net.spartanb312.grunt.ir.flow.core.FlowBlockKind
import net.spartanb312.grunt.ir.flow.core.FlowBytecodeSlice
import net.spartanb312.grunt.ir.flow.core.FlowEdge
import net.spartanb312.grunt.ir.flow.core.FlowEdgeFlag
import net.spartanb312.grunt.ir.flow.core.FlowEdgeId
import net.spartanb312.grunt.ir.flow.core.FlowEdgeSemantics
import net.spartanb312.grunt.ir.flow.core.FlowFrame
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowGotoJump
import net.spartanb312.grunt.ir.flow.core.FlowGotoMode
import net.spartanb312.grunt.ir.flow.core.FlowIfJump
import net.spartanb312.grunt.ir.flow.core.FlowJumpInput
import net.spartanb312.grunt.ir.flow.core.FlowMethod
import net.spartanb312.grunt.ir.flow.core.FlowPort
import net.spartanb312.grunt.ir.flow.core.FlowPredicateGuarantee
import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowExportOptions
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowExporter
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowImporter
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.DecimalRangeVal
import net.spartanb312.grunteon.obfuscator.process.IntRangeVal
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.globalScopeValue
import net.spartanb312.grunteon.obfuscator.process.parForEachClassesFiltered
import net.spartanb312.grunteon.obfuscator.process.post
import net.spartanb312.grunteon.obfuscator.process.pre
import net.spartanb312.grunteon.obfuscator.process.reducibleScopeValue
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.JunkCallPool
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.JunkCodeGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.JunkCodeOptions
import net.spartanb312.grunteon.obfuscator.process.transformers.other.FakeSyntheticBridge
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.HiddenTransformer
import net.spartanb312.grunteon.obfuscator.util.DISABLE_CONTROL_FLOW
import net.spartanb312.grunteon.obfuscator.util.IGNORE_JUNK_CODE
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter

@HiddenTransformer
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
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Chance to wrap an eligible Flow edge with an opaque junk branch. Range: 0.0..1.0")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Junk branch chance")
        val chance: Double = 0.25,
        @SettingDesc("Chance to expand an eligible IF jump into opaque true gates with fake junk branches. Range: 0.0..1.0")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Mangled IF chance")
        val mangledIfChance: Double = 0.25,
        @SettingDesc("Maximum junk branches inserted into one method")
        @IntRangeVal(min = 1, max = 32)
        @SettingName("Max branches per method")
        val maxBranchesPerMethod: Int = 4,
        @SettingDesc("Maximum IF jumps mangled in one method")
        @IntRangeVal(min = 0, max = 32)
        @SettingName("Max mangled IFs per method")
        val maxMangledIfsPerMethod: Int = 4,
        @SettingDesc("Chance that a mangled IF fake branch loops back to its gate instead of returning through JunkCode. Range: 0.0..1.0")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Mangled fake loop chance")
        val mangledFakeLoopChance: Double = 0.35,
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
            }
            JunkCallPool.build(classes)
        }
        val methodCounter = reducibleScopeValue { MergeableCounter() }
        val branchCounter = reducibleScopeValue { MergeableCounter() }
        val mangledIfCounter = reducibleScopeValue { MergeableCounter() }
        val failureCounter = reducibleScopeValue { MergeableCounter() }

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy(), config.workerBatchSize.coerceAtLeast(1)) { classNode ->
            if (classNode.isExcluded(DISABLE_CONTROL_FLOW) || classNode.isExcluded(IGNORE_JUNK_CODE)) {
                return@parForEachClassesFiltered
            }

            val hierarchy = hierarchyKey.global
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
                        methodNode.insertJunkBranches(classNode, config, hierarchy, pool, random)
                    }.fold(
                        onSuccess = { result ->
                            if (result.changed) {
                                methodCounter.local.add()
                                branchCounter.local.add(result.branches)
                                mangledIfCounter.local.add(result.mangledIfs)
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
            Logger.info(" - ControlflowJump:")
            Logger.info("    Inserted ${branchCounter.global.get()} junk branches")
            Logger.info("    Mangled ${mangledIfCounter.global.get()} conditional jumps")
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
        pool: JunkCallPool,
        random: UniformRandomProvider
    ): JunkBranchMethod {
        val imported = JvmFlowImporter().import(owner.name, this)
        val result = FlowJunkBranchInserter(
            options = JunkBranchOptions(
                chance = config.chance.coerceIn(0.0, 1.0),
                mangledIfChance = config.mangledIfChance.coerceIn(0.0, 1.0),
                maxBranchesPerMethod = config.maxBranchesPerMethod.coerceAtLeast(1),
                maxMangledIfsPerMethod = config.maxMangledIfsPerMethod.coerceAtLeast(0),
                mangledFakeLoopChance = config.mangledFakeLoopChance.coerceIn(0.0, 1.0),
                junkCodeOptions = JunkCodeOptions(
                    maxPreludeCalls = config.maxPreludeCalls.coerceAtLeast(0),
                    useJunkCallPrelude = !pool.isEmpty(),
                    useNaturalReferenceValues = config.naturalReferenceValues,
                    useAssignableJunkReturns = config.assignableJunkReturns,
                    junkReturnChance = 0.35
                )
            ),
            callPool = pool,
            hierarchy = hierarchy,
            random = random
        ).insert(imported.method)

        if (!result.changed) return JunkBranchMethod(this, changed = false)

        val exported = JvmFlowExporter(
            imported.metadata,
            JvmFlowExportOptions(
                access = access,
                signature = signature,
                exceptions = exceptions?.toList() ?: emptyList()
            )
        ).export(imported.method)
        copyMethodMetadataTo(exported)

        if (config.verifyBytecode) {
            Analyzer(BasicInterpreter()).analyze(owner.name, exported)
        }

        return JunkBranchMethod(
            exported,
            changed = true,
            branches = result.branches,
            mangledIfs = result.mangledIfs
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
        val junkCodeOptions: JunkCodeOptions
    )

    private data class JunkBranchResult(
        val changed: Boolean,
        val branches: Int = 0,
        val mangledIfs: Int = 0
    )

    private data class JunkBranchMethod(
        val method: MethodNode,
        val changed: Boolean,
        val branches: Int = 0,
        val mangledIfs: Int = 0
    )

    private class FlowJunkBranchInserter(
        private val options: JunkBranchOptions,
        private val callPool: JunkCallPool,
        private val hierarchy: ClassHierarchy,
        private val random: UniformRandomProvider
    ) {
        fun insert(method: FlowMethod): JunkBranchResult {
            val ids = MutableFlowIds(method)
            val junk = JunkCodeGenerator(callPool, hierarchy, options.junkCodeOptions, random)
            val mangledIfs = insertMangledIfs(method, ids, junk)
            val branches = insertJunkEdges(method, ids, junk)

            return JunkBranchResult(
                changed = branches != 0 || mangledIfs != 0,
                branches = branches,
                mangledIfs = mangledIfs
            )
        }

        private fun insertMangledIfs(
            method: FlowMethod,
            ids: MutableFlowIds,
            junk: JunkCodeGenerator
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

                connectMangledGate(method, ids, branchGate, branchTarget, sourceFrame, junk)
                connectMangledGate(method, ids, fallthroughGate, fallthroughTarget, sourceFrame, junk)
                inserted++
            }

            return inserted
        }

        private fun insertJunkEdges(
            method: FlowMethod,
            ids: MutableFlowIds,
            junk: JunkCodeGenerator
        ): Int {
            val candidates = method.edges
                .filter { it.isEligibleJunkEdge(method) }
                .toMutableList()
            shuffle(candidates)

            var inserted = 0
            for (edge in candidates) {
                if (inserted >= options.maxBranchesPerMethod) break
                if (random.nextDouble() >= options.chance) continue
                val sourceFrame = runCatching { FlowVerifier.frameAfterJump(edge.from) }.getOrNull() ?: continue
                if (sourceFrame.hasUninitialized()) continue

                val originalTarget = edge.to
                val guard = createGuardBlock(ids.block(), sourceFrame)
                val terminalJunk = junk.createTerminalBlock(ids.block(), method, sourceFrame)

                method.addBlock(guard)
                method.addBlock(terminalJunk)
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
                        to = terminalJunk,
                        semantics = FlowEdgeSemantics.Junk,
                        flags = mutableSetOf(FlowEdgeFlag.Inserted)
                    )
                )
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
            junk: JunkCodeGenerator
        ) {
            val fakeTarget = if (random.nextDouble() < options.mangledFakeLoopChance) {
                createMangledLoopBlock(method, ids, gate, frame, junk)
            } else {
                junk.createTerminalBlock(ids.block(), method, frame).also { method.addBlock(it) }
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

        private fun createGuardBlock(id: FlowBlockId, frame: FlowFrame): FlowBlock {
            return FlowBlock(
                id = id,
                kind = FlowBlockKind.Bogus,
                body = FlowBytecodeSlice(),
                jump = FlowIfJump(
                    opcode = Opcodes.IFNE,
                    input = FlowJumpInput.Generated(
                        code = FlowBytecodeSlice(mutableListOf(InsnNode(Opcodes.ICONST_1))),
                        produced = listOf(FlowFrameValue.Int),
                        guarantee = FlowPredicateGuarantee.AlwaysTrue
                    )
                ),
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
            val instructions = mutableListOf<AbstractInsnNode>()
            val opcode = when (random.nextInt(6)) {
                0 -> {
                    instructions += InsnNode(Opcodes.ICONST_0)
                    Opcodes.IFEQ
                }
                1 -> {
                    instructions += InsnNode(Opcodes.ICONST_1)
                    Opcodes.IFNE
                }
                2 -> {
                    instructions += InsnNode(Opcodes.ICONST_1)
                    instructions += InsnNode(Opcodes.ICONST_1)
                    Opcodes.IF_ICMPEQ
                }
                3 -> {
                    instructions += InsnNode(Opcodes.ICONST_0)
                    instructions += InsnNode(Opcodes.ICONST_1)
                    Opcodes.IF_ICMPLT
                }
                4 -> {
                    instructions += InsnNode(Opcodes.ICONST_1)
                    instructions += InsnNode(Opcodes.ICONST_0)
                    Opcodes.IF_ICMPGT
                }
                else -> {
                    instructions += InsnNode(Opcodes.ICONST_0)
                    instructions += InsnNode(Opcodes.ICONST_0)
                    Opcodes.IF_ICMPGE
                }
            }
            return FlowIfJump(
                opcode = opcode,
                input = FlowJumpInput.Generated(
                    code = FlowBytecodeSlice(instructions),
                    produced = List(instructions.size) { FlowFrameValue.Int },
                    guarantee = FlowPredicateGuarantee.AlwaysTrue
                )
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
