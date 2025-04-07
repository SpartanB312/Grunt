package net.spartanb312.grunt.process.transformers.rename.kr

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.hierarchy.krypton.HeavyHierarchy
import net.spartanb312.grunt.process.hierarchy.krypton.info.ClassInfo
import net.spartanb312.grunt.process.hierarchy.krypton.info.MethodInfo
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.rename.MethodRenameTransformer
import net.spartanb312.grunt.process.transformers.rename.ReflectionSupportTransformer
import net.spartanb312.grunt.utils.IndyChecker
import net.spartanb312.grunt.utils.extensions.*
import net.spartanb312.grunt.utils.inList
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.nextBadKeyword
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.system.measureTimeMillis

/**
 * Renaming fields
 * Last update on 2025/04/08
 * Feature from Krypton obfuscator
 */
object MethodRenameTransformerKr : Transformer("MethodRenameKr", Category.Renaming) {

    private val mode by setting("Mode", "Fast") // Fast, Full
    private val enums by setting("Enums", true)
    private val interfaces by setting("Interfaces", false) // Make sure you've loaded all the dependencies
    private val dictionary by setting("Dictionary", "Alphabet")
    private val heavyOverloads by setting("HeavyOverloads", false)
    private val randomKeywordPrefix by setting("RandomKeywordPrefix", false)
    private val prefix by setting("Prefix", "")
    private val reversed by setting("Reversed", false)
    private val exclusion by setting(
        "Exclusion", listOf(
            "net/spartanb312/Example1",
            "net/spartanb312/Example2.method",
            "net/spartanb312/Example3.method()V",
        )
    )
    private val excludedName by setting("ExcludedName", listOf("methodName"))

    private val suffix get() = if (reversed) "\u200E" else ""
    private val MethodNode.reflectionExcluded
        get() = ReflectionSupportTransformer.enabled && name.inList(ReflectionSupportTransformer.methodBlacklist)

    override fun ResourceCache.transform() {
        if (MethodRenameTransformer.enabled) {
            Logger.error(" Grunt method renamer enabled, skip krypton method renamer")
            return
        }
        val fast = mode.lowercase() == "fast"
        Logger.info(" - Renaming methods[${if (fast) "FastMode" else "FullMode"}]...")
        if (fast) fastRename() else fullRename()
    }

    private fun ResourceCache.fastRename() {
        Logger.info("    Building method hierarchies...")
        val hierarchy = HeavyHierarchy(this)
        val time = measureTimeMillis {
            hierarchy.buildClass()
            hierarchy.buildMethodFast()
        }
        Logger.info("    Took $time ms to build hierarchy")

        // Generate names for classes
        Logger.info("    Generating mappings for methods...")
        val mappings = mutableMapOf<String, String>()
        val infoMappings = mutableMapOf<MethodInfo, String>()
        val existedNameMap = mutableMapOf<ClassInfo, MutableSet<String>>() // class, name$desc list
        //val affectedMapping = mutableMapOf<MethodInfo, MutableSet<String>>() // source, all affected
        nonExcluded.asSequence()
            .filter {
                it.name.notInList(exclusion)
                        && !it.isAnnotation
                        && (enums || !it.isEnum)
                        && (interfaces || !it.isInterface)
            }.forEach { classNode ->
                val classInfo = hierarchy.getClassInfo(classNode.name)
                val isEnum = classNode.isEnum
                val dic = NameGenerator.getByName(dictionary)
                if (!classInfo.missingDependencies) {
                    val multiSource = mutableSetOf<String>()
                    for (methodInfo in classInfo.methods) {
                        // Info check
                        val methodNode = methodInfo.methodNode
                        if (methodInfo.virtual) continue
                        if (methodNode.isNative) continue
                        if (methodNode.isInitializer) continue
                        if (methodNode.isMainMethod) continue
                        if (isEnum && methodNode.name == "values") continue
                        if (methodNode.name.inList(excludedName)) continue
                        if (methodNode.reflectionExcluded) continue
                        val combined = combine(classNode.name, methodNode.name, methodNode.desc)
                        if (combined.inList(exclusion)) continue
                        if (methodNode.isSourceMethod(hierarchy, classNode)) {
                            val affectedMembers = mutableMapOf<String, String>()
                            val namePrefix = (if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix
                            // Avoid shadow names
                            val checkList = mutableSetOf<ClassInfo>()
                            checkList.add(classInfo)
                            checkList.addAll(classInfo.children)
                            var newName: String
                            loop@ while (true) {
                                newName = namePrefix + dic.nextName(heavyOverloads, methodNode.desc) + suffix
                                var keepThisName = true
                                check@ for (check in checkList) {
                                    val nameSet = existedNameMap.getOrPut(check) { mutableSetOf() }
                                    if (nameSet.contains(newName + methodInfo.desc)) {
                                        keepThisName = false
                                        break@check
                                    }
                                }
                                if (keepThisName) break
                            }
                            checkList.forEach { check ->
                                val nameSet = existedNameMap.getOrPut(check) { mutableSetOf() }
                                nameSet.add(newName + methodInfo.desc)
                            }
                            affectedMembers["${classNode.name}.${methodNode.name}${methodNode.desc}"] = newName
                            infoMappings[methodInfo] = newName
                            // Check children
                            var shouldApply = true
                            classInfo.children.forEach { child ->
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
                                                        multiSource.add("${parent.classNode.name}.${methodNode.name}${methodNode.desc}")
                                                    }
                                                }
                                            }
                                        } else shouldApply = false
                                    }

                                    val childKey = "${child.classNode.name}.${methodNode.name}${methodNode.desc}"
                                    val childMethodInfo = child.methods.find { it.full == childKey }
                                    infoMappings[childMethodInfo ?: MethodInfo(
                                        child, MethodNode(
                                            Opcodes.ACC_PUBLIC,
                                            methodInfo.name,
                                            methodInfo.desc,
                                            methodInfo.methodNode.signature,
                                            null,
                                        ), hierarchy.methodCoder, true
                                    )] = newName
                                    affectedMembers[childKey] = newName
                                } else shouldApply = false
                            }
                            // Apply mappings
                            if (shouldApply) affectedMembers.forEach { (key, value) -> mappings[key] = value }
                        }
                    }
                    if (multiSource.isNotEmpty()) {
                        Logger.info("    Skipped multi source methods: ")
                        multiSource.forEach {
                            Logger.info("     - $it")
                        }
                    }
                }
            }

        // Generate invoke dynamic mapping
        val indyResults = IndyChecker(hierarchy, nonExcluded).check(infoMappings)
        indyResults.forEach { implicitInfo ->
            val key = ".${implicitInfo.indyInsnName}${implicitInfo.indyInsnDesc}"
            Logger.info("    Generated indy mapping for $key")
            mappings[key] = implicitInfo.newName
        }

        Logger.info("    Applying mappings for methods...")
        // Remap
        applyRemap("methods", mappings)
        Logger.info("    Renamed ${mappings.size} methods")
    }

    private fun ResourceCache.fullRename() {
        Logger.info("    Building method hierarchies...")
        val hierarchy = HeavyHierarchy(this)
        hierarchy.buildClass()
        hierarchy.buildMethod()

        // Generate names and apply to children
        Logger.info("    Generating mappings for methods...")
        val mappings = mutableMapOf<String, String>()
        val infoMappings = mutableMapOf<MethodInfo, String>()
        val blackList = mutableSetOf<MethodInfo>()
        val relatedGroups = mutableListOf<MutableSet<MethodInfo>>() // as a family
        nonExcluded.asSequence()
            .filter {
                it.name.notInList(exclusion)
                        && !it.isAnnotation
                        && (enums || !it.isEnum)
                        && (interfaces || !it.isInterface)
            }.forEach { classNode ->
                val classInfo = hierarchy.getClassInfo(classNode.name)
                val isEnum = classNode.isEnum
                if (!classInfo.missingDependencies) {
                    for (methodInfo in classInfo.methods) {
                        // Source check
                        if (!methodInfo.isSourceMethod) continue
                        if (blackList.contains(methodInfo)) continue
                        // Info check
                        val methodNode = methodInfo.methodNode
                        if (methodNode.isNative) continue
                        if (methodNode.isInitializer) continue
                        if (methodNode.isMainMethod) continue
                        if (isEnum && methodInfo.name == "values") continue
                        if (methodNode.name.inList(excludedName)) continue
                        if (methodNode.reflectionExcluded) continue
                        val combined = combine(classNode.name, methodNode.name, methodNode.desc)
                        if (combined.inList(exclusion)) continue
                        // Bind group
                        val group = if (methodInfo.relatedMethods.size > 1) {
                            val relationship = methodInfo.relatedMethods.toMutableSet()
                            relationship.add(methodInfo)
                            Logger.info("    Found multi source method group: ")
                            relationship.forEach {
                                Logger.info("     - " + it.full)
                            }
                            relationship
                        } else mutableSetOf(methodInfo)
                        // apply group
                        blackList.addAll(group)
                        relatedGroups.add(group)
                    }
                }
            }

        // share a same name in a group
        val nameGenerators = mutableMapOf<ClassInfo, NameGenerator>()
        val existedNameMap = mutableMapOf<ClassInfo, MutableSet<String>>() // class, name$desc list
        relatedGroups.forEach { group ->
            val present = group.first()
            val namePrefix = (if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix

            // Avoid shadow names
            val dic = nameGenerators.getOrPut(present.owner) { NameGenerator.getByName(dictionary) }
            val checkList = mutableSetOf<MethodInfo>()
            group.forEach { source ->
                checkList.add(source)
                checkList.addAll(source.children)
            }
            var newName: String
            loop@ while (true) {
                newName = namePrefix + dic.nextName(heavyOverloads, present.desc) + suffix
                var keepThisName = true
                check@ for (check in checkList) {
                    val nameSet = existedNameMap.getOrPut(check.owner) { mutableSetOf() }
                    if (nameSet.contains(newName + present.desc)) {
                        keepThisName = false
                        break@check
                    }
                }
                if (keepThisName) break
            }
            checkList.forEach { check ->
                val nameSet = existedNameMap.getOrPut(check.owner) { mutableSetOf() }
                nameSet.add(newName + present.desc)
            }

            // Apply to all affected
            val affectedSet = mutableSetOf<String>()
            group.forEach { sourceMethod ->
                mappings[sourceMethod.full] = newName
                affectedSet.add(sourceMethod.full)
                infoMappings[sourceMethod] = newName
                sourceMethod.children.forEach {
                    mappings[it.full] = newName
                    affectedSet.add(it.full)
                    if (!it.virtual) infoMappings[it] = newName
                }
            }
        }

        // Generate invoke dynamic mapping
        val indyResults = IndyChecker(hierarchy, nonExcluded).check(infoMappings)
        indyResults.forEach { implicitInfo ->
            val key = ".${implicitInfo.indyInsnName}${implicitInfo.indyInsnDesc}"
            Logger.info("    Generated indy mapping for $key")
            mappings[key] = implicitInfo.newName
        }

        Logger.info("    Applying mappings for methods...")
        // Remap
        applyRemap("methods", mappings)
        Logger.info("    Renamed ${mappings.size} methods")
    }

    private fun MethodNode.isSourceMethod(hierarchy: HeavyHierarchy, owner: ClassNode): Boolean {
        val ownerInfo = hierarchy.getClassInfo(owner.name)
        if (ownerInfo.missingDependencies) return false
        return ownerInfo.parents.none { p ->
            if (!p.missingDependencies) {
                p.classNode.methods.any { it.name == this.name && it.desc == this.desc }
            } else true//Missing dependencies
        }
    }

    private fun combine(owner: String, name: String, desc: String) = "$owner.$name$desc"

}