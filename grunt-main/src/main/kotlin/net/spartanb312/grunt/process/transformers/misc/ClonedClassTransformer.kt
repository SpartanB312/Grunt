package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.isExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode

/**
 * Clone random classes as trash classes
 * Last update on 2024/06/26
 */
object ClonedClassTransformer : Transformer("ClonedClass", Category.Miscellaneous) {

    private val count by setting("Count", 0)
    private val suffix by setting("Suffix", "-cloned")
    private val removeAnnotations by setting("RemoveAnnotations", true)
    private val exclusion by setting("Exclusion", listOf())

    private val cloneNameMap = mutableMapOf<String, Int>() // Origin, CloneID

    override fun ResourceCache.transform() {
        Logger.info(" - Cloning redundant classes...")
        val workingRange = nonExcluded.filter { !it.name.isExcludedIn(exclusion) }
        repeat(count) {
            val origin = workingRange.random()
            val cloneID = cloneNameMap.getOrPut(origin.name) { 0 }
            cloneNameMap[origin.name] = cloneID + 1

            val newNode = ClassNode()
            origin.accept(newNode)
            newNode.name = origin.name + suffix + "$$cloneID"
            if (removeAnnotations) {
                newNode.visibleAnnotations?.clear()
                newNode.invisibleAnnotations?.clear()
                newNode.visibleTypeAnnotations?.clear()
                newNode.invisibleTypeAnnotations?.clear()
            }
            addTrashClass(newNode)
        }
        Logger.info("    Generated $count cloned classes")
    }

}