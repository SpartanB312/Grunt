package net.spartanb312.grunteon.obfuscator.process.transformers.redirect

import kotlinx.serialization.Serializable

import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

@Transformer.Description(
    "process.redirect.invoke_proxy.desc",
    "Redirect method invokes"
)
class InvokeProxy : Transformer<InvokeProxy.Config>(
    "InvokeProxy",
    Category.Redirect,
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "The chance that attempt to replace put/set to getter/setter")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        val chance: Double = 0.3,
        @SettingDesc(enText = "Generate an outer class to store proxies")
        val outer: Boolean = true,
        @SettingDesc(enText = "Specify method exclusions.")
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
            //Logger.info(" > InvokeProxy: Redirecting method calls...")
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val newClasses = globalScopeValue { mutableMapOf<ClassNode, ClassNode>() }// Owner Companion
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            if (classNode.isExcluded(DISABLE_INVOKE_PROXY)) return@parForEachClassesFiltered
            classNode.methods.toList().asSequence()
                .runIf(classNode.isEnum) {
                    filterNot { it.isStatic && it.name in ENUM_METHOD_EXCLUDES }
                }
                .filter { !it.isAbstract && !it.isNative && !it.isInitializer }
                .forEach { method ->
                    if (method.isExcluded(DISABLE_INVOKE_PROXY)) return@forEach
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    if (excluded) return@forEach
                    val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc))
                    method.instructions.toList().forEach { instruction ->
                        if (instruction !is MethodInsnNode) return@forEach
                        if (randomGen.nextFloat() > config.chance) return@forEach
                        val callingOwner = instance.workRes.getClassNode(instruction.owner) ?: return@forEach
                        if (callingOwner.isExcluded(IGNORE_INVOKE_PROXY)) return@forEach
                        val callingMethod = callingOwner.methods?.toList()?.find {
                            it.name == instruction.name && it.desc == instruction.desc
                        } ?: return@forEach
                        if (callingMethod.isExcluded(IGNORE_INVOKE_PROXY)) return@forEach
                        // Generate proxy
                        val extractToOuterClass = config.outer && callingOwner.isPublic && callingMethod.isPublic
                        val newName = "${instruction.name}_redirected_${randomGen.getRandomString(10)}"
                        val newMethod = instruction.genMethod(
                            newName,
                            callingMethod.signature,
                            callingMethod.exceptions.toTypedArray(),
                            extractToOuterClass
                        )
                        if (newMethod != null) {
                            instruction.name = newName
                            if (instruction.opcode == Opcodes.INVOKEVIRTUAL) {
                                instruction.desc = "(L${instruction.owner};${instruction.desc.removePrefix("(")}"
                                instruction.opcode = Opcodes.INVOKESTATIC
                            }
                            newMethod.appendAnnotation(GENERATED_METHOD)
                            if (extractToOuterClass) {
                                val newOwner = synchronized(newClasses) {
                                    newClasses.global.getOrPut(classNode) {
                                        ClassNode().apply {
                                            visit(
                                                classNode.version,
                                                Opcodes.ACC_PUBLIC,
                                                "${classNode.name}\$InvokeProxy",
                                                null,
                                                "java/lang/Object",
                                                null
                                            )
                                        }
                                    }
                                }
                                newOwner.methods.add(newMethod)
                                instruction.owner = newOwner.name
                            } else {
                                classNode.methods.add(newMethod)
                                instruction.owner = classNode.name
                            }
                            counter.add()
                        }
                    }
                }
        }
        seq {
            newClasses.global.forEach { (_, c) ->
                c.appendAnnotation(GENERATED_CLASS)
                instance.workRes.addGeneratedClass(c)
            }
        }
        post {
            Logger.info(" - InvokeProxy:")
            if (config.outer) Logger.info("    Generated ${newClasses.global.size} outer classes")
            Logger.info("    Redirected ${counter.global.get()} method calls")
        }
    }

    private fun MethodInsnNode.genMethod(
        methodName: String,
        signature: String?,
        exceptions: Array<String>?,
        outer: Boolean
    ): MethodNode? {
        return when (opcode) {
            Opcodes.INVOKESTATIC -> method(
                (if (outer) PUBLIC else PRIVATE) + STATIC,
                methodName,
                desc,
                signature,
                exceptions
            ) {
                INSTRUCTIONS {
                    var stack = 0
                    Type.getArgumentTypes(methodNode.desc).forEach {
                        +VarInsnNode(it.getLoadType(), stack)
                        stack += it.size
                    }
                    INVOKESTATIC(owner, name, desc)
                    +InsnNode(methodNode.desc.getReturnType())
                }
            }

            Opcodes.INVOKEVIRTUAL -> method(
                (if (outer) PUBLIC else PRIVATE) + STATIC,
                methodName,
                "(L$owner;${desc.removePrefix("(")}",
                signature,
                exceptions
            ) {
                INSTRUCTIONS {
                    var stack = 0
                    Type.getArgumentTypes(methodNode.desc).forEach {
                        +VarInsnNode(it.getLoadType(), stack)
                        stack += it.size
                    }
                    INVOKEVIRTUAL(owner, name, desc)
                    +InsnNode(methodNode.desc.getReturnType())
                }
            }

            Opcodes.INVOKEINTERFACE -> null
            Opcodes.INVOKESPECIAL -> null
            else -> throw Exception("Unsupported")
        }
    }

    companion object {
        private val ENUM_METHOD_EXCLUDES = setOf(
            "values",
            "valueOf",
            "getEntries"
        )
    }
}
