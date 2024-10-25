package net.spartanb312.grunt.process.transformers.misc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isAnnotation
import net.spartanb312.grunt.utils.extensions.isEnum
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.util.*

/**
 * Append annotation for native obfuscate
 * Last update on 2024/10/25
 */
object NativeCandidateTransformer : Transformer("NativeCandidate", Category.Miscellaneous) {

    val annotation by setting("NativeAnnotation", "Lnet/spartanb312/example/Native;")
    private val enableSearch by setting("SearchCandidate", true)
    private val upCallLimit by setting("UpCallLimit", 0)
    private val exclusion by setting("Exclusion", listOf())

    val appendedMethods: MutableSet<MethodNode> = Collections.synchronizedSet(HashSet()) // from other place

    override fun ResourceCache.transform() {
        Logger.info(" - Adding annotations on native transformable methods...")
        val candidateMethod = mutableSetOf<MethodNode>()
        if (enableSearch) runBlocking {
            nonExcluded.asSequence()
                .filter {
                    !it.isInterface && !it.isAnnotation && !it.isEnum && !it.isAbstract
                            && it.name.notInList(exclusion)
                }.forEach { classNode ->
                    fun job() {
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
                    if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                }
        }
        candidateMethod.forEach { it.visitAnnotation(annotation, false) }
        Logger.info("    Added ${candidateMethod.size + appendedMethods.size} annotations")
    }

}