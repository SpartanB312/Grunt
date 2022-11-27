package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.dictionary.NameGenerator
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.Hierarchy
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.system.measureTimeMillis

object MethodRenameTransformer : Transformer("MethodRename") {

    private val dictionary by value("Dictionary", "Alphabet")
    private val randomKeywordPrefix by value("RandomKeywordPrefix", false)
    private val prefix by value("Prefix", "")
    private val exclusion by value("Exclusion", listOf())
    private val excludedName by value("ExcludedName", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming methods...")
        val hierarchy = Hierarchy(this)
        Logger.info("    Building hierarchy graph...")
        val buildTime = measureTimeMillis {
            hierarchy.build()
        }
        Logger.info("    Took ${buildTime}ms to build ${hierarchy.size} hierarchies")
        val dic = NameGenerator.getByName(dictionary)
        val mapping = mutableMapOf<String, String>()

        val count = count {
            // Generate names and apply to children
            nonExcluded.asSequence()
                .filter { !it.isEnum && !it.isAnnotation && it.name.isNotExcludedIn(exclusion) }
                .forEach { classNode ->
                    val info = hierarchy.getHierarchyInfo(classNode)
                    if (!info.missingDependencies) {
                        for (methodNode in classNode.methods) {
                            if (methodNode.name.startsWith("<")) continue
                            if (methodNode.name == "main") continue
                            if (methodNode.name.isExcludedIn(excludedName)) continue
                            if (hierarchy.isPrimeMethod(classNode, methodNode)) {
                                val newName =
                                    (if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix + dic.nextName()
                                mapping[combine(classNode.name, methodNode.name, methodNode.desc)] = newName
                                // Apply to children
                                info.children.forEach { c ->
                                    if (c is Hierarchy.HierarchyInfo)
                                        mapping[combine(c.classNode.name, methodNode.name, methodNode.desc)] = newName
                                }
                                add(1)
                            } else continue
                        }
                    }
                }
        }.get()

        Logger.info("    Applying remapping for methods...")
        // Remap
        applyRemap("methods", mapping)
        Logger.info("    Renamed $count methods")
    }

    private fun combine(owner: String, name: String, desc: String) = "$owner.$name$desc"

    private fun Hierarchy.isPrimeMethod(owner: ClassNode, method: MethodNode): Boolean {
        val ownerInfo = getHierarchyInfo(owner)
        if (ownerInfo.missingDependencies) return false
        return ownerInfo.parents.none { p ->
            if (p is Hierarchy.HierarchyInfo)
                p.classNode.methods.any { it.name == method.name && it.desc == method.desc }
            else false//Missing dependencies
        }
    }

}