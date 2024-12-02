package net.spartanb312.grunt.process.transformers.rename

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/**
 * Rename local variables
 * Last update on 2024/10/02
 */
object LocalVariableRenameTransformer : Transformer("LocalVariableRename", Category.Renaming), MethodProcessor {

    private val dictionary by setting("Dictionary", "Alphabet")
    private val thisRef by setting("ThisReference", false)
    private val deleteLocalVars by setting("DeleteLocalVars", false)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Transforming local variables...")
        val count = count {
            nonExcluded.asSequence()
                .filter { it.name.notInList(exclusion) }
                .forEach { classNode ->
                    for (methodNode in classNode.methods) {
                        transformMethod(classNode, methodNode)
                        add(methodNode.localVariables?.size ?: 0)
                    }
                }
        }.get()
        Logger.info("    Transformed $count local variables")
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        if (deleteLocalVars) {
            method.localVariables?.clear()
            return
        }
        val dic = NameGenerator.getByName(dictionary)
        method.localVariables?.forEach {
            if (thisRef || it.name != "this") {
                val newName = dic.nextName()
                it.name = newName
            }
        }
    }

}