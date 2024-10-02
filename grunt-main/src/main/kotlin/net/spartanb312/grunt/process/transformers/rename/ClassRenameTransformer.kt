package net.spartanb312.grunt.process.transformers.rename

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.notInList
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode

/**
 * Renaming classes
 * Last update on 2024/06/26
 */
object ClassRenameTransformer : Transformer("ClassRename", Category.Renaming) {

    private val dictionary by setting("Dictionary", "Alphabet")
    private val parent by setting("Parent", "net/spartanb312/obf/")
    private val prefix by setting("Prefix", "")
    private val reversed by setting("Reversed", false)
    private val shuffled by setting("Shuffled", false)
    private val corruptedName by setting("CorruptedName", false)
    private val corruptedNameExclusion by setting("CorruptedNameExclusion", listOf())
    private val exclusion by setting("Exclusion", listOf())

    private val ClassNode.malNamePrefix
        get() = if (corruptedName) {
            if (corruptedNameExclusion.any { ex -> name.startsWith(ex) }) {
                Logger.debug("    MalName excluded for $name")
                ""
            } else "\u0000"
        } else ""

    private val suffix get() = if (reversed) "\u200E" else ""

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming classes...")
        Logger.info("    Generating mappings for classes...")
        val nameGenerator = NameGenerator.getByName(dictionary)
        val mappings = mutableMapOf<String, String>()
        val count = count {
            val classes = if (shuffled) nonExcluded.shuffled() else nonExcluded
            classes.forEach {
                if (it.name.notInList(exclusion)) {
                    mappings[it.name] = parent + prefix + it.malNamePrefix + nameGenerator.nextName() + suffix
                    add()
                }
            }
        }.get()

        Logger.info("    Applying mappings for classes...")
        applyRemap("classes", mappings, true)
        Logger.info("    Renamed $count classes")
    }

}