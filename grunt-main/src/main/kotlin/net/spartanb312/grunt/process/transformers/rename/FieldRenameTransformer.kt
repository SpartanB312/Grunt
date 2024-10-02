package net.spartanb312.grunt.process.transformers.rename

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.inList
import net.spartanb312.grunt.utils.notInList
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.nextBadKeyword
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import java.util.*

/**
 * Renaming fields
 * Last update on 2024/10/02
 */
object FieldRenameTransformer : Transformer("FieldRename", Category.Renaming) {

    private val dictionary by setting("Dictionary", "Alphabet")
    private val randomKeywordPrefix by setting("RandomKeywordPrefix", false)
    private val prefix by setting("Prefix", "")
    private val reversed by setting("Reversed", false)
    private val exclusion by setting(
        "Exclusion", listOf(
            "net/spartanb312/Example1",
            "net/spartanb312/Example2.field"
        )
    )
    private val excludedName by setting("ExcludedName", listOf("INSTANCE", "Companion"))

    private val malPrefix = (if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix
    private val suffix get() = if (reversed) "\u200E" else ""

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming fields...")
        Logger.info("    Generating mappings for fields...")

        val dictionary = NameGenerator.getByName(dictionary)
        val mappings = HashMap<String, String>()
        val fields: MutableList<Pair<FieldNode, ClassNode>> = ArrayList()
        nonExcluded.forEach { fields.addAll(it.fields.map { field -> field to it }) }
        fields.shuffle()

        val count = count {
            for ((fieldNode, owner) in fields) {
                if (fieldNode.name.inList(excludedName)) continue
                val name = malPrefix + dictionary.nextName() + suffix
                val stack: Stack<ClassNode> = Stack()
                stack.add(owner)
                while (stack.size > 0) {
                    val classNode = stack.pop()
                    val key = classNode.name + "." + fieldNode.name
                    if (key.notInList(exclusion)) mappings[key] = name
                    classes.values.forEach {
                        if (it.superName == classNode.name || it.interfaces.contains(classNode.name)) stack.add(it)
                    }
                }
                add()
            }
        }.get()

        Logger.info("    Applying mappings for fields...")
        applyRemap("fields", mappings)

        Logger.info("    Renamed $count fields")
    }

}