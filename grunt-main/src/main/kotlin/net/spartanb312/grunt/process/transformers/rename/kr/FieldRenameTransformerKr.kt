package net.spartanb312.grunt.process.transformers.rename.kr

import net.spartanb312.grunt.config.Configs.isExcluded
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.hierarchy.krypton.Hierarchy
import net.spartanb312.grunt.process.hierarchy.krypton.info.ClassInfo
import net.spartanb312.grunt.process.hierarchy.krypton.info.FieldInfo
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.rename.FieldRenameTransformer
import net.spartanb312.grunt.process.transformers.rename.ReflectionSupportTransformer
import net.spartanb312.grunt.utils.extensions.isPrivate
import net.spartanb312.grunt.utils.extensions.isProtected
import net.spartanb312.grunt.utils.inList
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.nextBadKeyword
import net.spartanb312.grunt.utils.notInList

/**
 * Renaming fields
 * Last update on 2025/04/08
 * Feature from Krypton obfuscator
 */
object FieldRenameTransformerKr : Transformer("FieldRenameKr", Category.Renaming) {

    private val dictionary by setting("Dictionary", "Alphabet")
    private val randomKeywordPrefix by setting("RandomKeywordPrefix", false)
    private val prefix by setting("Prefix", "")
    private val reversed by setting("Reversed", false)
    private val shuffled by setting("Shuffled", false)
    private val exclusion by setting(
        "Exclusion", listOf(
            "net/spartanb312/Example1",
            "net/spartanb312/Example2.field"
        )
    )
    private val excludedName by setting("ExcludedName", listOf("INSTANCE", "Companion"))

    private val malPrefix = (if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix
    private val suffix get() = if (reversed) "\u200E" else ""

    private val FieldInfo.reflectionExcluded
        get() = ReflectionSupportTransformer.enabled && name.inList(ReflectionSupportTransformer.fieldBlacklist)

    override fun ResourceCache.transform() {
        if (FieldRenameTransformer.enabled) {
            Logger.error(" Grunt field renamer enabled, skip krypton field renamer")
            return
        }
        Logger.info(" - Renaming fields...")

        // Build hierarchy
        Logger.info("    Building field hierarchies...")
        val hierarchy = Hierarchy(this)
        hierarchy.buildClass()
        hierarchy.buildField()

        // Generate names
        Logger.info("    Generating mappings for fields...")
        val mappings = HashMap<String, String>()
        val classes = if (shuffled) nonExcluded.shuffled() else nonExcluded
        val existedNameMap = mutableMapOf<ClassInfo, MutableSet<String>>() // class, name list
        for (classNode in classes) {
            if (classNode.name.inList(exclusion)) continue
            val classInfo = hierarchy.getClassInfo(classNode.name)
            if (classInfo.missingDependencies) continue
            val nameGenerator = NameGenerator.getByName(dictionary)
            classInfo.fields.forEach { fieldInfo ->
                if (
                    fieldInfo.name.notInList(excludedName)
                    && fieldInfo.isSourceField
                    && !fieldInfo.reflectionExcluded
                    && fieldInfo.full.notInList(exclusion)
                ) {
                    val checkChildren = fieldInfo.children.none {
                        it.owner.missingDependencies || it.full.inList(exclusion) || it.owner.classNode.isExcluded
                    }
                    if (checkChildren) {
                        // Avoid shadow names
                        val checkList = mutableSetOf<ClassInfo>()
                        checkList.add(classInfo)
                        checkList.addAll(classInfo.children)
                        var newName: String
                        loop@ while (true) {
                            newName = malPrefix + nameGenerator.nextName() + suffix
                            var keepThisName = true
                            check@ for (check in checkList) {
                                val nameSet = existedNameMap.getOrPut(check) { mutableSetOf() }
                                if (nameSet.contains(newName)) {
                                    keepThisName = false
                                    break@check
                                }
                            }
                            if (keepThisName) break
                        }
                        checkList.forEach { check ->
                            val nameSet = existedNameMap.getOrPut(check) { mutableSetOf() }
                            nameSet.add(newName)
                        }
                        val upApply = !fieldInfo.fieldNode.isPrivate || fieldInfo.fieldNode.isProtected
                        // Apply to children
                        if (upApply) {
                            val affected = mutableSetOf(classInfo)
                            affected.addAll(classInfo.children)
                            affected.forEach { apply ->
                                val key = "${apply.classNode.name}.${fieldInfo.name}"
                                mappings[key] = newName
                            }
                        } else mappings[fieldInfo.full] = newName
                    }
                }
            }
        }

        // Apply mappings
        Logger.info("    Applying mappings for fields...")
        applyRemap("fields", mappings)

        Logger.info("    Renamed ${mappings.size} fields")
    }

}