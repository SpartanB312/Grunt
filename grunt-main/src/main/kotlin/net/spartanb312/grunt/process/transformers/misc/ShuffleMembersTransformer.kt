package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.forEachThis
import net.spartanb312.grunt.utils.logging.Logger

/**
 * Shuffle members
 */
object ShuffleMembersTransformer : Transformer("ShuffleMembers", Category.Miscellaneous) {

    private val methods by value("Methods", true)
    private val fields by value("Fields", true)
    private val annotations by value("Annotations", true)
    private var exceptions by value("Exceptions", true)

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