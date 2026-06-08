package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.DISABLE_OPTIMIZER
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*


/**
 * @author StaR4y
 * @since 2026/5/21
 */

@Transformer.Description(
    "process.optimize.method_inliner.desc",
    "Inline small same-class methods"
)
class MethodInliner : Transformer<MethodInliner.Config>(
    "MethodInliner",
    Category.Optimization,
) {

    init {
        before(Category.Encryption, "Optimizer should run before encryption category")
        before(Category.Controlflow, "Optimizer should run before controlflow category")
        before(Category.AntiDebug, "Optimizer should run before anti debug category")
        before(Category.Authentication, "Optimizer should run before authentication category")
        before(Category.Exploit, "Optimizer should run before exploit category")
        before(Category.Miscellaneous, "Optimizer should run before miscellaneous category")
        before(Category.Redirect, "Optimizer should run before redirect category")
        before(Category.Renaming, "Optimizer should run before renaming category")
    }

    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Maximum real instruction count for inline candidates, including return")
        @IntRangeVal(min = 1, max = 128, step = 1)
        @SettingName("Max instructions")
        val maxInstructions: Int = 10,
        @SettingDesc("Maximum inline replacements per caller method")
        @IntRangeVal(min = 1, max = 512, step = 1)
        @SettingName("Max inline per method")
        val maxInlinePerMethod: Int = 64,
        @SettingDesc("Inline static methods")
        @SettingName("Inline static")
        val inlineStatic: Boolean = true,
        @SettingDesc("Inline private instance methods")
        @SettingName("Inline private")
        val inlinePrivate: Boolean = true,
        @SettingDesc("Inline final instance methods")
        @SettingName("Inline final")
        val inlineFinal: Boolean = true,
        @SettingDesc("Preserve null checks for inlined instance calls")
        @SettingName("Preserve null check")
        val preserveNullCheck: Boolean = true,
        @SettingDesc("Specify method exclusions.")
        @SettingName("Exclusion")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val methodExPredicate = buildMethodNamePredicates(config.exclusion)
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.isExcluded(DISABLE_OPTIMIZER)) return@parForEachClassesFiltered

            val targets = classNode.methods.asSequence()
                .filter { !it.isExcluded(DISABLE_OPTIMIZER) }
                .filterNot { methodExPredicate.matchedAnyBy(methodFullDesc(classNode, it)) }
                .mapNotNull { method -> method.toInlineTarget(config, classNode)?.let { method.name + method.desc to it } }
                .toMap()
            if (targets.isEmpty()) return@parForEachClassesFiltered

            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative && !it.isInitializer }
                .forEach { methodNode ->
                    if (methodNode.isExcluded(DISABLE_OPTIMIZER)) return@forEach
                    if (methodExPredicate.matchedAnyBy(methodFullDesc(classNode, methodNode))) return@forEach

                    val localCounter = counter.local
                    var inlined = 0
                    for (instruction in methodNode.instructions.toArray()) {
                        if (inlined >= config.maxInlinePerMethod) break
                        val invoke = instruction as? MethodInsnNode ?: continue
                        if (invoke.owner != classNode.name) continue
                        if (invoke.name == "<init>" || invoke.name == "<clinit>") continue
                        val target = targets[invoke.name + invoke.desc] ?: continue
                        if (target.method == methodNode) continue
                        if (!target.acceptsInvoke(invoke, classNode)) continue

                        val replacement = target.buildReplacement(methodNode, config.preserveNullCheck)
                        methodNode.instructions.insert(invoke, replacement.instructions)
                        methodNode.instructions.remove(invoke)
                        methodNode.maxLocals = maxOf(methodNode.maxLocals, replacement.maxLocals)
                        methodNode.maxStack = maxOf(methodNode.maxStack, replacement.maxStack)
                        localCounter.add()
                        inlined++
                    }
                }
        }
        post {
            Logger.info(" - MethodInliner:")
            Logger.info("    Inlined ${counter.global.get()} method calls")
        }
    }

    private fun MethodNode.toInlineTarget(config: Config, owner: ClassNode): InlineTarget? {
        if (isAbstract || isNative || isInitializer) return null
        if (isSynthetic || isBridge) return null
        if (access and Opcodes.ACC_SYNCHRONIZED != 0) return null
        if (!tryCatchBlocks.isNullOrEmpty()) return null
        if (isStatic && !config.inlineStatic) return null
        if (!isStatic) {
            val privateCandidate = isPrivate && config.inlinePrivate
            val finalCandidate = (isFinalMethod || owner.isFinal) && config.inlineFinal
            if (!privateCandidate && !finalCandidate) return null
        }

        val realInstructions = instructions.toArray().filter { it.opcode >= 0 }
        if (realInstructions.isEmpty() || realInstructions.size > config.maxInstructions) return null
        val returnInstruction = realInstructions.last()
        if (!returnInstruction.opcode.isReturn) return null
        if (realInstructions.count { it.opcode.isReturn } != 1) return null
        if (realInstructions.any { it.isUnsafeInlineInstruction() }) return null

        return InlineTarget(this, realInstructions, returnInstruction)
    }

    private fun AbstractInsnNode.isUnsafeInlineInstruction(): Boolean {
        return when (this) {
            is JumpInsnNode,
            is LookupSwitchInsnNode,
            is TableSwitchInsnNode -> true
            else -> opcode == Opcodes.JSR
                    || opcode == Opcodes.RET
                    || opcode == Opcodes.ATHROW
                    || opcode == Opcodes.MONITORENTER
                    || opcode == Opcodes.MONITOREXIT
        }
    }

    private data class InlineTarget(
        val method: MethodNode,
        val realInstructions: List<AbstractInsnNode>,
        val returnInstruction: AbstractInsnNode,
    ) {
        fun acceptsInvoke(invoke: MethodInsnNode, owner: ClassNode): Boolean {
            return when (invoke.opcode) {
                Opcodes.INVOKESTATIC -> method.isStatic
                Opcodes.INVOKESPECIAL -> method.isPrivate
                Opcodes.INVOKEVIRTUAL -> !method.isStatic &&
                        (method.isPrivate || method.isFinalMethod || owner.isFinal)
                else -> false
            }
        }

        fun buildReplacement(caller: MethodNode, preserveNullCheck: Boolean): InlineReplacement {
            val replacement = InsnList()
            val localMapping = mutableMapOf<Int, Int>()
            val localSlotSizes = computeLocalSlotSizes(method)
            var nextLocal = caller.maxLocals

            fun mapLocal(index: Int): Int {
                return localMapping.getOrPut(index) {
                    val mapped = nextLocal
                    nextLocal += localSlotSizes[index] ?: 1
                    mapped
                }
            }

            val argumentTypes = Type.getArgumentTypes(method.desc)
            var parameterLocal = 0
            val ownerLocal = if (method.isStatic) -1 else mapLocal(parameterLocal++)
            val argumentLocals = IntArray(argumentTypes.size)
            argumentTypes.forEachIndexed { index, type ->
                argumentLocals[index] = mapLocal(parameterLocal)
                parameterLocal += type.size
            }
            var prologueStack = 0
            if (ownerLocal != -1) prologueStack += 1
            for (type in argumentTypes) prologueStack += type.size
            val nullCheckPeak = if (ownerLocal != -1 && preserveNullCheck) 2 else 0
            val prologuePeak = maxOf(prologueStack, nullCheckPeak)

            for (index in argumentTypes.indices.reversed()) {
                replacement.add(VarInsnNode(argumentTypes[index].getOpcode(Opcodes.ISTORE), argumentLocals[index]))
            }
            if (ownerLocal != -1) {
                if (preserveNullCheck) {
                    replacement.add(InsnNode(Opcodes.DUP))
                    replacement.add(
                        MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL,
                            "java/lang/Object",
                            "getClass",
                            "()Ljava/lang/Class;",
                            false
                        )
                    )
                    replacement.add(InsnNode(Opcodes.POP))
                }
                replacement.add(VarInsnNode(Opcodes.ASTORE, ownerLocal))
            }

            for (instruction in realInstructions) {
                if (instruction == returnInstruction) break
                val cloned = instruction.cloneInline { mapLocal(it) } ?: continue
                replacement.add(cloned)
            }
            val bodyPeak = method.maxStack
            return InlineReplacement(replacement, nextLocal, maxOf(prologuePeak, bodyPeak))
        }

        private fun computeLocalSlotSizes(method: MethodNode): Map<Int, Int> {
            val sizes = mutableMapOf<Int, Int>()
            fun bump(index: Int, size: Int) {
                val current = sizes[index] ?: 0
                if (size > current) sizes[index] = size
            }

            val argumentTypes = Type.getArgumentTypes(method.desc)
            var local = 0
            if (!method.isStatic) bump(local++, 1)
            for (type in argumentTypes) {
                bump(local, type.size)
                local += type.size
            }
            for (instruction in method.instructions) {
                when (instruction) {
                    is VarInsnNode -> bump(instruction.`var`, instruction.opcode.localSlotSize())
                    is IincInsnNode -> bump(instruction.`var`, 1)
                }
            }
            return sizes
        }

        private fun AbstractInsnNode.cloneInline(
            mapLocal: (Int) -> Int,
        ): AbstractInsnNode? {
            return when (this) {
                is LabelNode,
                is LineNumberNode,
                is FrameNode -> null
                is VarInsnNode -> VarInsnNode(opcode, mapLocal(`var`))
                is IincInsnNode -> IincInsnNode(mapLocal(`var`), incr)
                else -> clone(emptyMap())
            }
        }

        private fun Int.localSlotSize(): Int {
            return when (this) {
                Opcodes.LLOAD,
                Opcodes.DLOAD,
                Opcodes.LSTORE,
                Opcodes.DSTORE -> 2
                else -> 1
            }
        }
    }

    private data class InlineReplacement(
        val instructions: InsnList,
        val maxLocals: Int,
        val maxStack: Int,
    )
}

private inline val MethodNode.isFinalMethod: Boolean get() = access and Opcodes.ACC_FINAL != 0
