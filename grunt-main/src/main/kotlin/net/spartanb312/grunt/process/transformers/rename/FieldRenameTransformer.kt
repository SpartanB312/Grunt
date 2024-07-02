package net.spartanb312.grunt.process.transformers.rename

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.isExcludedIn
import net.spartanb312.grunt.utils.isNotExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.nextBadKeyword
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import java.util.*

/**
 * Renaming fields
 * Last update on 2024/07/02
 */
object FieldRenameTransformer : Transformer("FieldRename", Category.Renaming) {

    private val dictionary by setting("Dictionary", "Alphabet")
    private val randomKeywordPrefix by setting("RandomKeywordPrefix", false)
    private val prefix by setting("Prefix", "")
    private val exclusion by setting("Exclusion", listOf())
    private val excludedName by setting("ExcludedName", listOf("INSTANCE", "Companion"))

    private val malPrefix = (if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming fields...")

        val dictionary = NameGenerator.getByName(dictionary)
        val mappings = HashMap<String, String>()
        val fields: MutableList<FieldNode> = ArrayList()
        nonExcluded.forEach { fields.addAll(it.fields) }
        fields.shuffle()

        val count = count {
            for (fieldNode in fields) {
                if (fieldNode.name.isExcludedIn(excludedName)) continue
                val name = malPrefix + dictionary.nextName()
                val stack: Stack<ClassNode> = Stack()
                stack.add(getOwner(fieldNode, classes))
                while (stack.size > 0) {
                    val classNode = stack.pop()
                    val key = classNode.name + "." + fieldNode.name
                    if (key.isNotExcludedIn(exclusion)) {
                        mappings[key] = name
                    }
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

    private fun getOwner(field: FieldNode, classNodes: MutableMap<String, ClassNode>): ClassNode? {
        for (clazz in classNodes.values) if (clazz.fields.contains(field)) return clazz
        return null
    }

}