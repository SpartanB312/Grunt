package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode

object KotlinOptimizeTransformer : Transformer("KotlinOptimizer") {

    private val removeMetadata by value("RemoveMetadata", true)
    private val removeIntrinsics by value("RemoveIntrinsics", true)
    private val intrinsicsRemoval by value(
        "IntrinsicsRemoval", listOf(
            "checkExpressionValueIsNotNull",
            "checkNotNullExpressionValue",
            "checkReturnedValueIsNotNull",
            "checkFieldIsNotNull",
            "checkParameterIsNotNull",
            "checkNotNullParameter"
        )
    )
    private val intrinsicsExclusion by value("IntrinsicsExclusions", listOf())
    private val metadataExclusion by value("MetadataExclusions", listOf())

    override fun ResourceCache.transform() {
        if (removeIntrinsics || removeIntrinsics) Logger.info(" - Optimizing kotlin classes")
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
                                        add(1)
                                    } else replace.add(insnNode)
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

        // Remove Metadata
        if (removeMetadata) {
            val metadataRemovalCount = count {
                nonExcluded.asSequence()
                    .filter { it.visibleAnnotations != null && metadataExclusion.none { n -> it.name.startsWith(n) } }
                    .forEach { classNode ->
                        classNode.visibleAnnotations.toList().forEach { annotationNode ->
                            if (
                                annotationNode.desc.startsWith("Lkotlin/Metadata") ||
                                annotationNode.desc.startsWith("Lkotlin/coroutines/jvm/internal/DebugMetadata")
                            ) {
                                classNode.visibleAnnotations.remove(annotationNode)
                                add(1)
                            }
                        }
                    }
            }.get()
            Logger.info("    Removed $metadataRemovalCount kotlin metadata annotations")
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

}