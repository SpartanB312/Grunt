package net.spartanb312.grunt.process.transformers.minecraft

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.isExcludedIn
import net.spartanb312.grunt.utils.isNotExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import java.util.*

/**
 * Renaming fields for mixin classes
 * Last update on 2024/07/02
 */
object MixinFieldRenameTransformer : Transformer("MixinFieldRename", Category.Minecraft) {

    private val dictionary by setting("Dictionary", "Alphabet")
    private val prefix by setting("Prefix", "")
    private val exclusion by setting("Exclusion", listOf())
    private val excludedName by setting("ExcludedName", listOf("INSTANCE", "Companion"))

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming mixin fields...")
        if (mixinClasses.isEmpty()) {
            Logger.info("    No mixin classes found")
            return
        }

        val dictionary = NameGenerator.getByName(dictionary)
        val mappings = HashMap<String, String>()
        val fields: MutableList<FieldNode> = ArrayList()
        nonExcluded.forEach { fields.addAll(it.fields) }
        fields.shuffle()

        val count = count {
            for (fieldNode in fields) {
                if (fieldNode.name.isExcludedIn(excludedName)) continue
                if (fieldNode.visibleAnnotations?.any { it.desc.isExcludedIn(annotations) } == true) continue
                if (fieldNode.invisibleAnnotations?.any { it.desc.isExcludedIn(annotations) } == true) continue
                val name = prefix + dictionary.nextName()
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

        Logger.info("    Applying mappings for mixin fields...")
        applyRemap("mixin fields", mappings)

        Logger.info("    Renamed $count mixin fields")
    }


    private fun getOwner(field: FieldNode, classNodes: MutableMap<String, ClassNode>): ClassNode? {
        for (clazz in classNodes.values) if (clazz.fields.contains(field)) return clazz
        return null
    }

    private val annotations = mutableListOf(
        "Lorg/spongepowered/asm/mixin/gen/Accessor",
        "Lorg/spongepowered/asm/mixin/gen/Invoker",
        "Lorg/spongepowered/asm/mixin/Shadow",
        "Lorg/spongepowered/asm/mixin/Overwrite"
    )

}