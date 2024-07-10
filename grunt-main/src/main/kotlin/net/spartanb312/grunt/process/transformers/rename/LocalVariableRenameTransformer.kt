package net.spartanb312.grunt.process.transformers.rename

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList

/**
 * Rename local variables
 * Last update on 2024/06/26
 */
object LocalVariableRenameTransformer : Transformer("LocalVariableRename", Category.Renaming) {

    private val dictionary by setting("Dictionary", "Alphabet")
    private val thisRef by setting("ThisReference", false)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming local variables...")
        val count = count {
            nonExcluded.asSequence()
                .filter { it.name.notInList(exclusion) }
                .forEach { classNode ->
                for (methodNode in classNode.methods) {
                    val dic = NameGenerator.getByName(dictionary)
                    methodNode.localVariables?.forEach {
                        if (thisRef || it.name != "this") {
                            val newName = dic.nextName()
                            it.name = newName
                            add()
                        }
                    }
                }
            }
        }.get()
        Logger.info("    Renamed $count local variables")
    }

}