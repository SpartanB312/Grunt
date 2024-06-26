package net.spartanb312.grunt.process.transformers.optimize

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.LineNumberNode

/**
 * Remove or replace source debug info in class
 */
object SourceDebugRemoveTransformer : Transformer("SourceDebugRemove", Category.Miscellaneous) {

    private val sourceDebug by value("SourceDebug", true)
    private val lineDebug by value("LineDebug", true)
    private val renameSourceDebug by value("RenameSourceDebug", false)
    private val sourceNames by value(
        "SourceNames", listOf(
            "114514.java",
            "1919810.kt",
            "69420.java",
            "你妈死了.kt"
        )
    )

    override fun ResourceCache.transform() {
        Logger.info(" - Removing/Editing debug information...")
        val count = count {
            nonExcluded.forEach { classNode ->
                if (sourceDebug) {
                    if (renameSourceDebug) {
                        classNode.sourceDebug = sourceNames.random()
                        classNode.sourceFile = sourceNames.random()
                    } else {
                        classNode.sourceDebug = null
                        classNode.sourceFile = null
                    }
                    add()
                }
                if (lineDebug) classNode.methods.forEach { methodNode ->
                    methodNode.instructions.toList().forEach { insn ->
                        if (insn is LineNumberNode) {
                            methodNode.instructions.remove(insn)
                            add()
                        }
                    }
                }
            }
        }.get()
        Logger.info("    Removed/Edited $count debug information")
    }

}