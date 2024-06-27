package net.spartanb312.grunt.process.transformers.rename

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.hierarchy.FastHierarchy
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAnnotation
import net.spartanb312.grunt.utils.extensions.isEnum
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.isExcludedIn
import net.spartanb312.grunt.utils.isNotExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.nextBadKeyword
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * Renaming classes
 * Last update on 2024/06/26
 * Improvements:
 * 1.Reflect support
 * 2.Mixin support
 * 3.Renaming for multi-overrides methods
 */
object MethodRenameTransformer : Transformer("MethodRename", Category.Renaming) {

    private val dictionary by value("Dictionary", "Alphabet")
    private val heavyOverloads by value("HeavyOverloads", false)
    private val randomKeywordPrefix by value("RandomKeywordPrefix", false)
    private val prefix by value("Prefix", "")
    private val exclusion by value("Exclusion", listOf())
    private val excludedName by value("ExcludedName", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming methods...")

        Logger.info("    Building hierarchy graph...")
        val hierarchy = FastHierarchy(this, true)
        val buildTime = measureTimeMillis {
            hierarchy.build()
        }
        Logger.info("    Took ${buildTime}ms to build ${hierarchy.size} hierarchies")

        val dic = NameGenerator.getByName(dictionary)
        val mappings = ConcurrentHashMap<String, String>()
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
                            if (methodNode.isNative) continue
                            if (hierarchy.isPrimeMethod(classNode, methodNode)) {
                                val readyToApply = mutableMapOf<String, String>()
                                var shouldApply = true
                                val newName = (if (randomKeywordPrefix) "$nextBadKeyword " else "") +
                                        prefix + dic.nextName(heavyOverloads, methodNode.desc)
                                readyToApply[combine(classNode.name, methodNode.name, methodNode.desc)] = newName
                                // Check children
                                info.children.forEach { c ->
                                    if (c is FastHierarchy.HierarchyInfo) {
                                        // Check its origin
                                        for (parent in c.parents) {
                                            if (parent.missingDependencies) shouldApply = false
                                            if (parent is FastHierarchy.HierarchyInfo) {
                                                val flag1 = !hierarchy.isSubType(parent.classNode.name, classNode.name)
                                                val flag2 = !hierarchy.isSubType(classNode.name, parent.classNode.name)
                                                if (parent.classNode.name != classNode.name && (flag1 && flag2)) {
                                                    parent.classNode.methods.forEach { check ->
                                                        if (check.name == methodNode.name && check.desc == methodNode.desc) {
                                                            // Skip multi origin methods
                                                            shouldApply = false
                                                            //Logger.debug("Multi Origin ${methodNode.name}${methodNode.desc} in ${classNode.name} and ${parent.classNode.name}")
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        val childKey = combine(c.classNode.name, methodNode.name, methodNode.desc)
                                        readyToApply[childKey] = newName
                                    }
                                }
                                // Apply mappings
                                if (shouldApply) readyToApply.forEach { (key, value) -> mappings[key] = value }
                                add()
                            } else continue
                        }
                    }

                }
        }.get()

        Logger.info("    Applying remapping for methods...")
        // Remap
        applyRemap("methods", mappings)
        Logger.info(
            "    Renamed $count methods" +
                    if (heavyOverloads) " with ${dic.overloadsCount} overloads in ${dic.actualNameCount} names" else ""
        )
    }

    private fun combine(owner: String, name: String, desc: String) = "$owner.$name$desc"

    private fun FastHierarchy.isPrimeMethod(owner: ClassNode, method: MethodNode): Boolean {
        val ownerInfo = getHierarchyInfo(owner)
        if (ownerInfo.missingDependencies) return false
        return ownerInfo.parents.none { p ->
            if (p is FastHierarchy.HierarchyInfo) {
                p.classNode.methods.any { it.name == method.name && it.desc == method.desc }
            } else true//Missing dependencies
        }
    }

}