package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.DISABLE_OPTIMIZER
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import net.spartanb312.grunteon.obfuscator.util.extensions.matchInvoke
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode

@Transformer.Description(
    "process.optimize.kotlin_class_shrink.desc",
    "Remove kotlin metadata and intrinsics check"
)
class KotlinClassShrink : Transformer<KotlinClassShrink.Config>(
    "KotlinClassShrink",
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
        @SettingDesc("Remove kotlin metadata. Warning: It will render KReflect unusable")
        @SettingName("Metadata")
        val metaData: Boolean = true,
        @SettingDesc("Remove kotlin intrinsics like parameter check")
        @SettingName("Intrinsics")
        val intrinsics: Boolean = true,
        @SettingDesc("Specify intrinsics remove target")
        @SettingName("Intrinsics removal")
        val intrinsicsRemoval: List<String> = listOf(
            "checkExpressionValueIsNotNull",
            "checkNotNullExpressionValue",
            "checkReturnedValueIsNotNull",
            "checkFieldIsNotNull",
            "checkParameterIsNotNull",
            "checkNotNullParameter"
        ),
        @SettingDesc("Replace LDC to avoid reference leaking")
        @SettingName("Replace LDC")
        val replaceLDC: Boolean = true
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > KotlinClassShrink: Shrinking kotlin classes...")
        }
        val intrinsics = reducibleScopeValue { MergeableCounter() }
        val metadata = reducibleScopeValue { MergeableCounter() }
        val pendingReplaceCache = localScopeValue { FastObjectArrayList<AbstractInsnNode>() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.isExcluded(DISABLE_OPTIMIZER)) return@parForEachClassesFiltered
            if (config.intrinsics) {
                val intrinsics = intrinsics.local
                classNode.methods.forEach { methodNode ->
                    if (methodNode.isExcluded(DISABLE_OPTIMIZER)) return@forEach
                    val replace = pendingReplaceCache.local
                    replace.clearFast()
                    methodNode.instructions.forEach { insnNode ->
                        if (insnNode.matchInvoke(Opcodes.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics")) {
                            val removeSize = intrinsicsRemoveMethods[insnNode.name + insnNode.desc] ?: 0
                            if (removeSize > 0 && config.intrinsicsRemoval.contains(insnNode.name)) {
                                replace.removeLast()
                                repeat(removeSize) {
                                    replace.add(InsnNode(Opcodes.POP))
                                }
                                intrinsics.add()
                            } else {
                                if (config.replaceLDC && intrinsicsReplaceMethods.contains(insnNode.name + insnNode.desc)) {
                                    val ldc = replace.last()
                                    if (ldc is LdcInsnNode) {
                                        ldc.cst = "REMOVED BY GRUNT"
                                        intrinsics.add()
                                    }
                                }
                                replace.add(insnNode)
                            }
                        } else replace.add(insnNode)
                    }
                    methodNode.instructions.clear()
                    replace.forEach { methodNode.instructions.add(it) }
                }
            }
            if (config.metaData) {
                val metadata = metadata.local
                fun MutableList<AnnotationNode>.removeCheck() {
                    removeIf {
                        if (
                            it.desc.startsWith("Lkotlin/jvm/internal/SourceDebugExtension") ||
                            it.desc.startsWith("Lkotlin/Metadata") ||
                            it.desc.startsWith("Lkotlin/coroutines/jvm/internal/DebugMetadata")
                        ) {
                            metadata.add()
                            true
                        } else {
                            false
                        }
                    }
                }
                classNode.visibleAnnotations?.removeCheck()
                classNode.invisibleAnnotations?.removeCheck()
            }
        }
        post {
            Logger.info(" - KotlinClassShrink:")
            if (config.metaData) Logger.info("    Removed ${metadata.global.get()} kotlin metadata")
            if (config.intrinsics) Logger.info("    Removed ${intrinsics.global.get()} kotlin intrinsics")
        }
    }

    companion object {
        private val intrinsicsRemoveMethods = mutableMapOf(
            "checkExpressionValueIsNotNull(Ljava/lang/Object;Ljava/lang/String;)V" to 1,
            "checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V" to 1,
            "checkReturnedValueIsNotNull(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V" to 2,
            "checkReturnedValueIsNotNull(Ljava/lang/Object;Ljava/lang/String;)V" to 1,
            "checkFieldIsNotNull(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V" to 2,
            "checkFieldIsNotNull(Ljava/lang/Object;Ljava/lang/String;)V" to 1,
            "checkParameterIsNotNull(Ljava/lang/Object;Ljava/lang/String;)V" to 1,
            "checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V" to 1
        )

        private val intrinsicsReplaceMethods = mutableListOf(
            "checkNotNull(Ljava/lang/Object;Ljava/lang/String;)V",
            "throwNpe(Ljava/lang/String;)V",
            "throwJavaNpe(Ljava/lang/String;)V",
            "throwUninitializedProperty(Ljava/lang/String;)V",
            "throwUninitializedPropertyAccessException(Ljava/lang/String;)V",
            "throwAssert(Ljava/lang/String;)V",
            "throwIllegalArgument(Ljava/lang/String;)V",
            "throwIllegalState(Ljava/lang/String;)V",
            "throwUndefinedForReified(Ljava/lang/String;)V",
        )
    }

}
