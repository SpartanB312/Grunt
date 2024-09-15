package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.massiveBlankString
import net.spartanb312.grunt.utils.massiveString
import net.spartanb312.grunt.utils.notInList

/**
 * Using big brain signature to crash decompiler
 * Idea from MioClient Loader
 */
object CrasherTransformer : Transformer("Crasher", Category.Miscellaneous) {

    private val random by setting("Random", false)
    private val exclusion by setting("Exclusion", listOf())

    private val String?.bigBrainSignature
        get() = if (isNullOrEmpty()) {
            if (random) massiveBlankString else massiveString
        } else this

    override fun ResourceCache.transform() {
        nonExcluded.asSequence()
            .filter { it.name.notInList(exclusion) }
            .forEach { classNode ->
                classNode.methods.forEach { methodNode ->
                    methodNode.signature = methodNode.signature.bigBrainSignature
                }
                classNode.fields.forEach { fieldNode ->
                    fieldNode.signature = fieldNode.signature.bigBrainSignature
                }
                classNode.signature = classNode.signature.bigBrainSignature
            }
    }

}