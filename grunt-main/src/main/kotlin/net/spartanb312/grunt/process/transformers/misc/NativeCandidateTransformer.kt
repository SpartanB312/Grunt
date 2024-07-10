package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isAnnotation
import net.spartanb312.grunt.utils.extensions.isEnum
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.notInList
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Append annotation for native obfuscate
 * Last update on 2024/06/26
 */
object NativeCandidateTransformer : Transformer("NativeCandidate", Category.Miscellaneous) {

    val nativeAnnotation by setting("NativeAnnotation", "Lnet/spartanb312/example/Native;")
    private val upCallLimit by setting("UpCallLimit", 0)
    private val exclusion by setting("Exclusion", listOf())

    val appendedMethods = mutableSetOf<MethodNode>() // from other place

    override fun ResourceCache.transform() {
        Logger.info(" - Adding annotations on native transformable methods...")
        val candidateMethod = mutableSetOf<MethodNode>()
        nonExcluded.asSequence()
            .filter {
                !it.isInterface && !it.isAnnotation && !it.isEnum && !it.isAbstract
                        && it.name.notInList(exclusion)
            }.forEach { classNode ->
                classNode.methods.forEach { methodNode ->
                    if (!appendedMethods.contains(methodNode)) {
                        var count = 0
                        for (insn in methodNode.instructions) {
                            if (count > upCallLimit) break
                            when (insn) {
                                is FieldInsnNode -> count++
                                is MethodInsnNode -> count++
                                is InvokeDynamicInsnNode -> count++
                            }
                        }
                        if (count <= upCallLimit) candidateMethod.add(methodNode)
                    }
                }
            }
        candidateMethod.forEach { it.visitAnnotation(nativeAnnotation, false) }
        Logger.info("    Added ${candidateMethod.size + appendedMethods.size} annotations")
    }

}