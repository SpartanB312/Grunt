package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.isAbstract
import net.spartanb312.grunt.utils.isAnnotation
import net.spartanb312.grunt.utils.isEnum
import net.spartanb312.grunt.utils.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

object NativeCandidateTransformer : Transformer("NativeCandidate") {

    val nativeAnnotation by value("NativeAnnotation", "Lnet/spartanb312/example/Native;")
    private val upCallLimit by value("UpCallLimit", 0)

    override fun ResourceCache.transform() {
        Logger.info(" - Adding annotations on native transformable methods...")
        val candidateMethod = mutableListOf<MethodNode>()
        nonExcluded.asSequence()
            .filter { !it.isInterface && !it.isAnnotation && !it.isEnum && !it.isAbstract }
            .forEach { classNode ->
                classNode.methods.forEach { methodNode ->
                    if (!ScrambleTransformer.appendedMethods.contains(methodNode)) {
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
        Logger.info("    Added ${candidateMethod.size + ScrambleTransformer.appendedMethods.size} annotations")
    }

}