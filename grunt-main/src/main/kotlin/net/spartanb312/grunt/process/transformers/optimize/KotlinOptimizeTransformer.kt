package net.spartanb312.grunt.process.transformers.optimize

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

/**
 * Remove kotlin redundant calls
 * Last update on 24/07/02
 */
object KotlinOptimizeTransformer : Transformer("KotlinOptimizer", Category.Optimization) {

    private val removeAnnotation by setting("Annotations", true)
    private val removeIntrinsics by setting("Intrinsics", true)
    private val intrinsicsRemoval by setting(
        "IntrinsicsRemoval", listOf(
            "checkExpressionValueIsNotNull",
            "checkNotNullExpressionValue",
            "checkReturnedValueIsNotNull",
            "checkFieldIsNotNull",
            "checkParameterIsNotNull",
            "checkNotNullParameter"
        )
    )
    private val replaceLdc by setting("ReplaceLdc", true)
    private val intrinsicsExclusion by setting("IntrinsicsExclusion", listOf())
    private val metadataExclusion by setting("MetadataExclusion", listOf())

    override fun ResourceCache.transform() {
        if (removeIntrinsics || removeIntrinsics) Logger.info(" - Optimizing kotlin classes...")
        // Remove Intrinsics check
        if (removeIntrinsics) {
            val intrinsicsCount = count {
                nonExcluded.asSequence()
                    .filter { intrinsicsExclusion.none { n -> it.name.startsWith(n) } }
                    .forEach { classNode ->
                        classNode.methods.forEach { methodNode ->
                            val replace = mutableListOf<AbstractInsnNode>()
                            methodNode.instructions.forEach { insnNode ->
                                if (
                                    insnNode is MethodInsnNode
                                    && insnNode.opcode == Opcodes.INVOKESTATIC
                                    && insnNode.owner == "kotlin/jvm/internal/Intrinsics"
                                ) {
                                    val removeSize = intrinsicsRemoveMethods[insnNode.name + insnNode.desc] ?: 0
                                    if (removeSize > 0 && intrinsicsRemoval.contains(insnNode.name)) {
                                        replace.removeLast()
                                        repeat(removeSize) {
                                            replace.add(InsnNode(Opcodes.POP))
                                        }
                                        add()
                                    } else {
                                        if (replaceLdc && intrinsicsReplaceMethods.contains(insnNode.name + insnNode.desc)) {
                                            val ldc = replace.last()
                                            if (ldc is LdcInsnNode) {
                                                ldc.cst = "REMOVED BY GRUNT"
                                                add(1)
                                            }
                                        }
                                        replace.add(insnNode)
                                    }
                                } else {
                                    replace.add(insnNode)
                                }
                            }
                            methodNode.instructions.clear()
                            replace.forEach { methodNode.instructions.add(it) }
                        }
                    }
            }.get()
            Logger.info("    Removed $intrinsicsCount Intrinsics checks")
        }

        // Remove annotations
        if (removeAnnotation) {
            val annotationCount = count {
                nonExcluded.asSequence()
                    .filter { it.visibleAnnotations != null && metadataExclusion.none { n -> it.name.startsWith(n) } }
                    .forEach { classNode ->
                        fun MutableList<AnnotationNode>.removeCheck() {
                            toList().forEach {
                                if (
                                    it.desc.startsWith("Lkotlin/jvm/internal/SourceDebugExtension") ||
                                    it.desc.startsWith("Lkotlin/Metadata") ||
                                    it.desc.startsWith("Lkotlin/coroutines/jvm/internal/DebugMetadata")
                                ) {
                                    remove(it)
                                    add()
                                }
                            }
                        }
                        classNode.visibleAnnotations?.removeCheck()
                        classNode.invisibleAnnotations?.removeCheck()
                    }
            }.get()
            Logger.info("    Removed $annotationCount kotlin annotations")
        }
    }

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