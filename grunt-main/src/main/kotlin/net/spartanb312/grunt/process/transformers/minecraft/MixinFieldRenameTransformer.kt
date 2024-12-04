package net.spartanb312.grunt.process.transformers.minecraft

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.inList
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import java.util.*

/**
 * Renaming fields for mixin classes
 * Last update on 2024/10/02
 */
object MixinFieldRenameTransformer : Transformer("MixinFieldRename", Category.Minecraft) {

    private val dictionary by setting("Dictionary", "Alphabet")
    private val prefix by setting("Prefix", "")
    private val exclusion by setting(
        "Exclusion", listOf(
            "net/spartanb312/Example1",
            "net/spartanb312/Example2.field"
        )
    )
    private val excludedName by setting("ExcludedName", listOf("INSTANCE", "Companion"))

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming mixin fields...")
        if (mixinClasses.isEmpty()) {
            Logger.info("    No mixin classes found")
            return
        }

        Logger.info("    Generating mappings for mixin fields...")
        val dictionary = NameGenerator.getByName(dictionary)
        val mappings = HashMap<String, String>()
        val fields: MutableList<Pair<FieldNode, ClassNode>> = ArrayList()
        mixinClasses.forEach { fields.addAll(it.fields.map { field -> field to it }) }
        fields.shuffle()

        val count = count {
            for ((fieldNode, owner) in fields) {
                if (fieldNode.name.inList(excludedName)) continue
                if (fieldNode.visibleAnnotations?.any { it.desc.inList(annotations) } == true) continue
                if (fieldNode.invisibleAnnotations?.any { it.desc.inList(annotations) } == true) continue
                val name = prefix + dictionary.nextName()
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

        Logger.info("    Applying mappings for mixin fields...")
        applyRemap("mixin fields", mappings)

        Logger.info("    Renamed $count mixin fields")
    }

    private val annotations = mutableListOf(
        "Lorg/spongepowered/asm/mixin/gen/Accessor",
        "Lorg/spongepowered/asm/mixin/gen/Invoker",
        "Lorg/spongepowered/asm/mixin/Shadow",
        "Lorg/spongepowered/asm/mixin/Overwrite"
    )

}