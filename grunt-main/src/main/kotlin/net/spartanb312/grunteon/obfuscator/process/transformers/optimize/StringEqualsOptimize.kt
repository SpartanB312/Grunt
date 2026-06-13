package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import kotlinx.serialization.Serializable

import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.match
import org.objectweb.asm.tree.MethodInsnNode

@Transformer.Stability(StableLevel.RockSolid)
@Transformer.Description(
    "process.optimize.string_equals_optimize.desc",
    "Redirect string equals() and equalsIgnoreCase()"
)
class StringEqualsOptimize : Transformer<StringEqualsOptimize.Config>(
    "StringEqualsOptimize",
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
        @SettingDesc("Redirect equalsIgnoreCase()")
        @SettingName("Ignore case")
        val ignoreCase: Boolean = true
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > StringEqualsOptimize: Redirecting string equals calls...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            classNode.methods.forEach { methodNode ->
                val counter = counter.local
                for (insnNode in methodNode.instructions.toArray()) {
                    if (insnNode is MethodInsnNode) {
                        if (insnNode.match(
                                "java/lang/String",
                                "equals",
                                "(Ljava/lang/Object;)Z"
                            )
                        ) {
                            val replacement = instructions {
                                INVOKEVIRTUAL("java/lang/Object", "hashCode", "()I")
                                INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                                SWAP
                                INVOKEVIRTUAL("java/lang/String", "hashCode", "()I")
                                INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                                INVOKEVIRTUAL("java/lang/Integer", "equals", "(Ljava/lang/Object;)Z")
                            }
                            methodNode.instructions.insert(insnNode, replacement)
                            methodNode.instructions.remove(insnNode)
                            counter.add()
                        } else if (config.ignoreCase && insnNode.match(
                                "java/lang/String",
                                "equalsIgnoreCase",
                                "(Ljava/lang/String;)Z"
                            )
                        ) {
                            val replacement = instructions {
                                INVOKEVIRTUAL("java/lang/String", "toUpperCase", "()Ljava/lang/String;")
                                INVOKEVIRTUAL("java/lang/Object", "hashCode", "()I")
                                INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                                SWAP
                                INVOKEVIRTUAL("java/lang/String", "toUpperCase", "()Ljava/lang/String;")
                                INVOKEVIRTUAL("java/lang/String", "hashCode", "()I")
                                INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                                INVOKEVIRTUAL("java/lang/Integer", "equals", "(Ljava/lang/Object;)Z")
                            }
                            methodNode.instructions.insert(insnNode, replacement)
                            methodNode.instructions.remove(insnNode)
                            counter.add()
                        }
                    }
                }
            }
        }
        post {
            Logger.info(" - StringEqualsOptimize:")
            Logger.info("    Redirected ${counter.global.get()} string equals calls")
        }
    }
}
