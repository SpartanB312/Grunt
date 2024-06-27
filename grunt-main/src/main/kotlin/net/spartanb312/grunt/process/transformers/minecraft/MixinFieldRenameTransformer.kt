package net.spartanb312.grunt.process.transformers.minecraft

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.hierarchy.FastHierarchy
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.rename.FieldRenameTransformer.isPrimeField
import net.spartanb312.grunt.utils.extensions.isAnnotation
import net.spartanb312.grunt.utils.isExcludedIn
import net.spartanb312.grunt.utils.isNotExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import kotlin.system.measureTimeMillis

/**
 * Renaming fields for mixin classes
 */
object MixinFieldRenameTransformer : Transformer("MixinFieldRename", Category.Minecraft) {

    private val dictionary by value("Dictionary", "Alphabet")
    private val prefix by value("Prefix", "")
    private val exclusion by value("Exclusion", listOf())
    private val excludedName by value("ExcludedName", listOf("INSTANCE"))

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming mixin fields...")

        Logger.info("    Building hierarchy graph...")
        val hierarchy = FastHierarchy(this, mixinClasses)
        val buildTime = measureTimeMillis {
            hierarchy.build()
        }
        Logger.info("    Took ${buildTime}ms to build ${hierarchy.size} hierarchies")

        val mappings = HashMap<String, String>()
        mixinClasses.asSequence()
            .filter { !it.isAnnotation && it.name.isNotExcludedIn(exclusion) }
            .forEach { classNode ->
                val dictionary = NameGenerator.getByName(dictionary)
                val info = hierarchy.getHierarchyInfo(classNode)
                if (!info.missingDependencies) {
                    for (fieldNode in classNode.fields) {
                        if (fieldNode.name.isExcludedIn(excludedName)) continue
                        if (fieldNode.visibleAnnotations?.any { it.desc.isExcludedIn(annotations) } == true) continue
                        if (fieldNode.invisibleAnnotations?.any { it.desc.isExcludedIn(annotations) } == true) continue
                        if (hierarchy.isPrimeField(classNode, fieldNode)) {
                            val key = classNode.name + "." + fieldNode.name
                            val newName = prefix + dictionary.nextName()
                            mappings[key] = newName
                            // Apply for children
                            info.children.forEach { c ->
                                if (c is FastHierarchy.HierarchyInfo) {
                                    val childKey = c.classNode.name + "." + fieldNode.name
                                    mappings[childKey] = newName
                                }
                            }
                        } else continue
                    }
                }
            }

        Logger.info("    Applying remapping for mixin fields...")
        applyRemap("mixin fields", mappings)

        Logger.info("    Renamed ${mappings.size} mixin fields")
    }

    private val annotations = mutableListOf(
        "Lorg/spongepowered/asm/mixin/gen/Accessor",
        "Lorg/spongepowered/asm/mixin/gen/Invoker",
        "Lorg/spongepowered/asm/mixin/Shadow",
        "Lorg/spongepowered/asm/mixin/Overwrite"
    )

}