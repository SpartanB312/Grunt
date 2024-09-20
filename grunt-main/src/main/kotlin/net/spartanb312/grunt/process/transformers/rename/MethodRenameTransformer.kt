package net.spartanb312.grunt.process.transformers.rename

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.hierarchy.Hierarchy
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.*
import net.spartanb312.grunt.utils.inList
import net.spartanb312.grunt.utils.notInList
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.nextBadKeyword
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodNode
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * Renaming methods with FunctionalInterface InvokeDynamic MultiSource check.
 * Last update on 2024/07/05
 */
object MethodRenameTransformer : Transformer("MethodRename", Category.Renaming) {

    private val enums by setting("Enums", true)
    private val interfaces by setting("Interfaces", false) // Make sure you've loaded all the dependencies
    private val dictionary by setting("Dictionary", "Alphabet")
    private val heavyOverloads by setting("HeavyOverloads", false)
    private val randomKeywordPrefix by setting("RandomKeywordPrefix", false)
    private val prefix by setting("Prefix", "")
    private val reversed by setting("Reversed", false)
    private val exclusion by setting("Exclusion", listOf())
    private val excludedName by setting("ExcludedName", listOf())

    private val suffix get() = if (reversed) "\u200E" else ""

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming methods...")

        Logger.info("    Building hierarchy graph...")
        val hierarchy = Hierarchy(this)
        val buildTime = measureTimeMillis {
            hierarchy.build()
        }
        val indyBlacklist = buildIndyBlacklist()
        Logger.info("    Took $buildTime ms to build ${hierarchy.size} hierarchies")

        Logger.info("    Generating mappings for methods...")
        val dictionary = NameGenerator.getByName(dictionary)
        val mappings = ConcurrentHashMap<String, String>()
        val count = count {
            // Generate names and apply to children
            nonExcluded.asSequence()
                .filter {
                    it.name.notInList(exclusion)
                            && it.name.notInList(indyBlacklist)
                            && !it.checkFunctionalInterface()
                            && !it.isAnnotation
                            && (enums || !it.isEnum)
                            && (interfaces || !it.isInterface)
                }
                .forEach { classNode ->
                    val info = hierarchy.getClassInfo(classNode)
                    if (!info.missingDependencies) {
                        val isEnum = classNode.isEnum
                        for (methodNode in classNode.methods) {
                            if (methodNode.isInitializer) continue
                            if (methodNode.isMainMethod) continue
                            if (isEnum && methodNode.name == "values") continue
                            if (methodNode.name.inList(excludedName)) continue
                            if (methodNode.isNative) continue
                            if (hierarchy.isSourceMethod(classNode, methodNode)) {
                                val readyToApply = mutableMapOf<String, String>()
                                var shouldApply = true
                                val newName = (if (randomKeywordPrefix) "$nextBadKeyword " else "") +
                                        prefix + dictionary.nextName(heavyOverloads, methodNode.desc) + suffix
                                readyToApply[combine(classNode.name, methodNode.name, methodNode.desc)] = newName
                                // Check children
                                info.children.forEach { child ->
                                    if (!child.isBroken) {
                                        // Check its origin
                                        loop@ for (parent in child.parents) {
                                            if (parent.missingDependencies) {
                                                shouldApply = false
                                                continue@loop
                                            }
                                            if (!parent.isBroken) {
                                                val flag1 = !hierarchy.isSubType(parent.classNode.name, classNode.name)
                                                val flag2 = !hierarchy.isSubType(classNode.name, parent.classNode.name)
                                                if (parent.classNode.name != classNode.name && (flag1 && flag2)) {
                                                    parent.classNode.methods.forEach { check ->
                                                        if (check.name == methodNode.name && check.desc == methodNode.desc) {
                                                            // Skip multi origin methods
                                                            shouldApply = false
                                                        }
                                                    }
                                                }
                                            } else shouldApply = false
                                        }

                                        val childKey = combine(child.classNode.name, methodNode.name, methodNode.desc)
                                        readyToApply[childKey] = newName
                                    } else shouldApply = false
                                }
                                if (classNode.isInterface) {
                                    readyToApply[".${methodNode.name}${methodNode.desc}"] = newName
                                }
                                // Apply mappings
                                if (shouldApply) readyToApply.forEach { (key, value) -> mappings[key] = value }
                                add()
                            } else continue
                        }
                    }
                }
        }.get()

        Logger.info("    Applying mappings for methods...")
        // Remap
        applyRemap("methods", mappings)
        Logger.info(
            "    Renamed $count methods" +
                    if (heavyOverloads) " with ${dictionary.overloadsCount} overloads in ${dictionary.actualNameCount} names" else ""
        )
    }

    private fun combine(owner: String, name: String, desc: String) = "$owner.$name$desc"

    private fun Hierarchy.isSourceMethod(owner: ClassNode, methodNode: MethodNode): Boolean {
        val ownerInfo = getClassInfo(owner.name)
        if (ownerInfo.missingDependencies) return false
        return ownerInfo.parents.none { p ->
            if (!p.missingDependencies) {
                p.classNode.methods.any { it.name == methodNode.name && it.desc == methodNode.desc }
            } else true//Missing dependencies
        }
    }

    // Return true when implements FunctionalInterface
    private fun ClassNode.checkFunctionalInterface(): Boolean {
        mutableListOf<AnnotationNode>().apply {
            visibleAnnotations?.let { addAll(it) }
            visibleTypeAnnotations?.let { addAll(it) }
            invisibleAnnotations?.let { addAll(it) }
            invisibleTypeAnnotations?.let { addAll(it) }
        }.forEach {
            if (it.desc.startsWith("Ljava/lang/FunctionalInterface;")) {
                return true
            }
        }
        return false
    }

    private fun ResourceCache.buildIndyBlacklist(): List<String> {
        return buildSet {
            allClasses.forEach { classNode ->
                classNode.methods.forEach { methodNode ->
                    methodNode.instructions.forEach { insnNode ->
                        if (insnNode is InvokeDynamicInsnNode) {
                            add(insnNode.desc.substringAfterLast(")L").removeSuffix(";"))
                        }
                    }
                }
            }
        }.toList()
    }

}