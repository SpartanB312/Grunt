package net.spartanb312.grunt.process.transformers.rename

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.isNotExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode

/**
 * Renaming classes
 * Last update on 2024/06/26
 * Improvements:
 * 1.Reflect support
 * 2.Mixin support
 */
object ClassRenameTransformer : Transformer("ClassRename", Category.Renaming) {

    private val dictionary by value("Dictionary", "Alphabet")
    private val parent by value("Parent", "net/spartanb312/obf/")
    private val prefix by value("Prefix", "")
    private val shuffled by value("Shuffled", false)
    private val corruptedName by value("CorruptedName", false)
    private val corruptedNameExclusion by value("CorruptedNameExclusions", listOf())
    private val exclusion by value("Exclusion", listOf())

    private val ClassNode.malNamePrefix
        get() = if (corruptedName) {
            if (corruptedNameExclusion.any { ex -> name.startsWith(ex) }) {
                Logger.debug("    MalName excluded for $name")
                ""
            } else "\u0000"
        } else ""

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming classes...")
        Logger.info("    Generating mappings for classes...")
        val nameGenerator = NameGenerator.getByName(dictionary)
        val mappings = mutableMapOf<String, String>()

        val count = count {
            val classes = if (shuffled) nonExcluded.shuffled() else nonExcluded
            classes.forEach {
                if (it.name.isNotExcludedIn(exclusion)) {
                    mappings[it.name] = parent + prefix + it.malNamePrefix + nameGenerator.nextName()
                    add()
                }
            }
        }.get()

        Logger.info("    Applying remapping for classes...")
        applyRemap("classes", mappings, true)
        Logger.info("    Renamed $count classes")
    }

}