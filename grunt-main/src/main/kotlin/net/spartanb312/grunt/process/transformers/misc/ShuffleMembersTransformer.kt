package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.logging.Logger

/**
 * Shuffle members
 * Last update on 2024/06/26
 */
object ShuffleMembersTransformer : Transformer("ShuffleMembers", Category.Miscellaneous) {

    private val methods by setting("Methods", true)
    private val fields by setting("Fields", true)
    private val annotations by setting("Annotations", true)
    private var exceptions by setting("Exceptions", true)

    override fun ResourceCache.transform() {
        Logger.info(" - Shuffling members...")
        val count = count {
            nonExcluded.forEach { classNode ->
                if (methods) classNode.methods?.let {
                    classNode.methods = it.shuffled()
                    add(it.size)
                    it.forEach { method ->
                        if (exceptions) {
                            method.exceptions?.shuffle()
                            add(method.exceptions.size)
                        }
                    }
                }
                if (fields) classNode.fields?.let {
                    classNode.fields = it.shuffled()
                    add(it.size)
                }
                if (annotations) {
                    classNode.visibleAnnotations?.let {
                        classNode.visibleAnnotations = it.shuffled()
                        add(it.size)
                    }
                    classNode.invisibleAnnotations?.let {
                        classNode.invisibleAnnotations = it.shuffled()
                        add(it.size)
                    }
                    classNode.methods?.forEach { methodNode ->
                        methodNode.visibleAnnotations?.let {
                            methodNode.visibleAnnotations = it.shuffled()
                            add(it.size)
                        }
                        methodNode.invisibleAnnotations?.let {
                            methodNode.invisibleAnnotations = it.shuffled()
                            add(it.size)
                        }
                    }
                }
            }
        }.get()
        Logger.info("    Shuffled $count members")
    }

}