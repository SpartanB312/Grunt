package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.dictionary.NameGenerator
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.logging.Logger

object LocalVariableRenameTransformer : Transformer("LocalVariableRename") {

    private val dictionary by value("Dictionary", "Alphabet")
    private val thisRef by value("ThisReference", false)

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming local variables...")
        val count = count {
            nonExcluded.forEach { classNode ->
                for (methodNode in classNode.methods) {
                    val dic = NameGenerator.getByName(dictionary)
                    methodNode.localVariables?.forEach {
                        if (thisRef || it.name != "this") {
                            val newName = dic.nextName()
                            it.name = newName
                            add(1)
                        }
                    }
                }
            }
        }.get()
        Logger.info("    Renamed $count local variables")
    }

}