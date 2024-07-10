package net.spartanb312.grunt.process.transformers.flow

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.builder.insnList
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.tree.MethodNode

/**
 * Not finished
 * Coming soon
 */
object TryCatchWrapperTransformer : Transformer("TryCatchWrapper", Category.Controlflow) {

    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Wrapping jumps with try catch")
        val count = count {
            nonExcluded.asSequence()
                .filter { it.name.notInList(exclusion) }
                .forEach { classNode ->
                    classNode.methods.forEach { methodNode ->
                        add(processMethodNode(methodNode))
                    }
                }
        }
        Logger.info("    Replaced ${count.get()} jumps")
    }

    fun processMethodNode(methodNode: MethodNode): Int {
        var count = 0
        val newInsn = insnList {
            methodNode.instructions.forEach { insnNode ->
                +insnNode
            }
        }
        methodNode.instructions = newInsn
        return count
    }

}