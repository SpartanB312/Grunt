package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.util.concurrent.ConcurrentHashMap

@Transformer.CreditMultiplier(1.5)
@Transformer.Stability(StableLevel.Unstable)
@Transformer.Description(
    "process.miscellaneous.parameter_obfuscate.desc",
    "Obfuscate parameters to object type"
)
class ParameterObfuscate : Transformer<ParameterObfuscate.Config>(
    "ParameterObfuscate",
    Category.Miscellaneous,
) {
    @Serializable
    data class Config(
        @SettingDesc("Only obfuscate private methods")
        @SettingName("Only private method")
        val onlyPrivateMethod: Boolean = true, // Not stable for all methods
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
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
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val remapJobs = globalScopeValue { ConcurrentHashMap<String, String>() }

        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
        ) { classNode ->
            val remapJobs = remapJobs.global
            val counter = counter.local
            val callInstances = Object2ObjectOpenHashMap<CallTarget, MutableSet<MethodInsnNode>>()
            // Collect method call instance
            classNode.methods.toList().asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    method.instructions.toList().forEach { instruction ->
                        if (instruction !is MethodInsnNode) return@forEach
                        val callingOwner = instance.workRes.getClassNode(instruction.owner) ?: return@forEach
                        if (callingOwner.name != classNode.name) return@forEach
                        val callingMethod = callingOwner.methods?.toList()?.find {
                            it.name == instruction.name && it.desc == instruction.desc
                        } ?: return@forEach
                        //if (callingMethod.isInitializer) return@forEach
                        if (!callingMethod.isPrivate && (!callingMethod.isStatic || config.onlyPrivateMethod)) return@forEach
                        if (callingMethod.isSynthetic || callingMethod.isBridge) return@forEach
                        val shadowNames = callingOwner.methods.filter {
                            it.name == callingMethod.name
                                && Type.getArgumentTypes(it.desc).size == Type.getArgumentTypes(callingMethod.desc).size
                        }
                        if (shadowNames.size > 1) return@forEach // avoid bridge method shadow
                        callInstances.getOrPut(CallTarget(callingOwner, callingMethod)) {
                            mutableSetOf()
                        }.add(instruction) // add this
                    }
                }
            // Obfuscate parameter
            callInstances.forEach { (callTarget, instances) ->
                val callingOwner = callTarget.owner
                val callingMethod = callTarget.method
                val isStatic = callingMethod.isStatic
                val params = Type.getArgumentTypes(callingMethod.desc)
                val mappedParams =
                    params.map {
                        if (it.descriptor.startsWith("L")) {
                            if (it.descriptor != "Ljava/lang/Object;") counter.add()
                            "Ljava/lang/Object;"
                        } else it.descriptor
                    }
                val indexedParams = Int2ObjectOpenHashMap<Type>()
                var stack = 0
                params.forEach {
                    indexedParams[stack] = it
                    stack += it.size
                }
                val oldDesc = callingMethod.desc
                val newDesc = "(${mappedParams.joinToString("")})${oldDesc.substringAfter(")")}"
                //if (oldDesc != newDesc) println("Desc ${callingMethod.desc} -> $newDesc")
                if (oldDesc != newDesc) {
                    // println("Obfuscated ${classNode.name}.${callingMethod.name}")
                    callingMethod.desc = newDesc
                    instances.forEach { it.desc = newDesc }
                    // Scan job
                    if (!config.onlyPrivateMethod) {
                        remapJobs["${callingOwner.name}.${callingMethod.name}${callingMethod.desc}"] = oldDesc
                    }
                    callingMethod.instructions.forEach { instruction ->
                        if (instruction is VarInsnNode && instruction.opcode == Opcodes.ALOAD) {
                            val index = if (isStatic) instruction.`var`
                            else if (instruction.`var` > 0) instruction.`var` - 1
                            else -1
                            val foundType = indexedParams.getOrElse(index) { null }
                            if (foundType != null) {
                                val cast = TypeInsnNode(
                                    Opcodes.CHECKCAST,
                                    foundType.descriptor.correctCast()
                                )
                                callingMethod.instructions.insert(instruction, cast)
                            }
                        }
                    }
                }
            }
        }
        barrier()
        if (!config.onlyPrivateMethod) {
            parForEachClasses { classNode ->
                classNode.methods.forEach { method ->
                    method.instructions.forEach { instr ->
                        if (instr !is MethodInsnNode) return@forEach
                        val key = "${instr.owner}.${instr.name}${instr.desc}"
                        val remapJob = remapJobs.global[key] ?: return@forEach
                        instr.desc = remapJob
                    }
                }
            }
        }
        post {
            Logger.info(" - ParameterObfuscate:")
            credit.add(counter.global.get() * 500L)
            Logger.info("    Obfuscated ${counter.global.get()} parameters")
        }
    }

    data class CallTarget(
        val owner: ClassNode,
        val method: MethodNode,
    )

    private fun String.correctCast(): String {
        return if (startsWith("L")) removePrefix("L").removeSuffix(";") else this
    }

}
