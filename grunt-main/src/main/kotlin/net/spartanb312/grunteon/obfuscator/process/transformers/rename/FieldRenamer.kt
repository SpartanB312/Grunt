package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet
import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy.FieldHierarchy
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping.MappingSource
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isPrivate
import net.spartanb312.grunteon.obfuscator.util.extensions.isProtected
import net.spartanb312.grunteon.obfuscator.util.extensions.isStatic
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.filter
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy

/**
 * Last update on 2026/03/31 by FluixCarvin
 * TODO: Reflection remap
 */
@Transformer.CreditMultiplier(1.0)
@Transformer.Stability(StableLevel.Stable)
@Transformer.Description(
    "process.rename.field_renamer.desc",
    "Renaming fields"
)
class FieldRenamer : Transformer<FieldRenamer.Config>(
    "FieldRenamer",
    Category.Renaming,
), MappingSource {

    init {
        after(Category.Encryption, "Renamer should run after encryption category")
        after(Category.AntiDebug, "Renamer should run after anti debug category")
        after(Category.Authentication, "Renamer should run after authentication category")
        after(Category.Exploit, "Renamer should run after exploit category")
        after(Category.Miscellaneous, "Renamer should run after miscellaneous category")
        after(Category.Optimization, "Renamer should run after optimization category")
        after(Category.Redirect, "Renamer should run after redirect category")
        after(ControlflowJump::class.java, "Renamer should run after ControlflowJump")
        after(ClassRenamer::class.java, "MethodRenamer should run after ClassRenamer")
    }

    @Serializable
    data class Config(
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingName("Dictionary")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingName("Prefix")
        val prefix: String = "",
        @SettingName("Reversed")
        val reversed: Boolean = false,
        @SettingName("Shuffled")
        val shuffled: Boolean = true,
        @SettingName("Heavy overloads")
        val heavyOverloads: Boolean = true,
        @SettingName("Aggressive shadow names")
        val aggressiveShadowNames: Boolean = true,
        @SettingName("Excluded names")
        val excludedNames: List<String> = listOf("INSTANCE", "Companion"),
        @SettingName("Field exclusions")
        @SettingDesc("Keep declared fields matching owner.name exactly or a prefix ending in **; not regex")
        val fieldExclusions: List<String> = emptyList()
    ) : TransformerConfig() {

        // getter
        val malPrefix = prefix //(if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix
        val suffix get() = if (reversed) "\u200E" else ""
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" > FieldRenamer: Renaming fields...")
        }
        buildFull(config)
    }

    context(instance: Grunteon, _: PipelineBuilder)
    private fun buildFull(config: Config) {
        val fieldHierarchy = globalScopeValue {
            Logger.info("    Building field hierarchies...")
            val classHierarchy = ClassHierarchy.build(
                instance.workRes.inputClassCollection, // Only include input classes
                instance.workRes::getClassNode
            )
            FieldHierarchy.build(classHierarchy)
        }
        seq {
            val fieldHierarchy = fieldHierarchy.global
            val classHierarchy = fieldHierarchy.classHierarchy
            val strategy = instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())

            val nonExcluded = instance.workRes.inputClassCollection
                .filter(strategy)
                .sortedBy { it.name }
                .toList()

            val existedNameMap = Int2ObjectOpenHashMap<MutableSet<String>>()
            // Record components are a reflection contract shared by fields, accessors and the constructor.
            // Reserve retained fields before renaming inherited fields as well as the record itself.
            classHierarchy.classNodes.forEachIndexed { index, node ->
                node.recordComponents?.forEach { component ->
                    existedNameMap.getOrPut(index) { mutableSetOf() }.add(component.name + component.descriptor)
                }
            }
            //val nameGenerator = NameGenerator(NameGenerator.getDictionary(config.dictionary))
            val dictionary = NameGenerator.getDictionary(config.dictionary)
            var counter = 0
            context(classHierarchy, fieldHierarchy) {
                val fieldExclusions = buildClassNamePredicates(config.fieldExclusions)
                val excluded = BooleanArray(fieldHierarchy.fieldNodes.size) { index ->
                    val field = FieldHierarchy.Entry(index)
                    field.owner.name !in instance.workRes.inputClassMap ||
                            !strategy.testImpl(field.owner.name) || field.name in config.excludedNames ||
                            fieldExclusions.matchedAnyBy("${field.owner.name}.${field.name}")
                }
                val renameSources = BooleanArray(fieldHierarchy.fieldNodes.size) { index ->
                    val field = FieldHierarchy.Entry(index)
                    field.isSourceField && !excluded[index] && !field.owner.hasMissingDependency &&
                            field.owner.descendants.array.all { descendant ->
                                val declared = fieldHierarchy.findField(descendant, field.name, field.desc)
                                !ClassHierarchy.Entry(descendant).hasMissingDependency &&
                                        (field.node.isPrivate || !declared.isValid || !excluded[declared.index])
                            }
                }

                // A source mapping also renames same-signature declarations in descendants.
                // Keep the whole group if it would touch an excluded declaration, but still
                // remap inherited references through excluded classes with no such declaration.
                val renamed = BooleanArray(fieldHierarchy.fieldNodes.size)
                for (index in renameSources.indices) {
                    if (!renameSources[index]) continue
                    val field = FieldHierarchy.Entry(index)
                    renamed[index] = true
                    if (!field.node.isPrivate) {
                        field.owner.descendants.forEach { descendant ->
                            val declared = fieldHierarchy.findField(descendant.index, field.name, field.desc)
                            if (declared.isValid) renamed[declared.index] = true
                        }
                    }
                }
                // Retained fields must not collide with generated declarations, including
                // inherited fields resolved through a descendant's symbolic owner.
                for (index in renamed.indices) {
                    if (renamed[index]) continue
                    val field = FieldHierarchy.Entry(index)
                    val signature = field.name + field.desc
                    existedNameMap.getOrPut(field.owner.index) { mutableSetOf() }.add(signature)
                    if (!field.node.isPrivate || !config.aggressiveShadowNames) {
                        field.owner.descendants.forEach { descendant ->
                            existedNameMap.getOrPut(descendant.index) { mutableSetOf() }.add(signature)
                        }
                    }
                }

                Logger.info("    Generating field mappings...")
                val nameGenerators = mutableMapOf<ClassHierarchy.Entry, NameGenerator>()
                nonExcluded.forEach { classNode ->
                    val recordComponents = classNode.recordComponents.orEmpty()
                    val classIndex = classHierarchy.findClass(classNode.name)
                    if (classIndex == -1) throw Exception("Class ${classNode.name} was not found in field hierarchy")
                    val classEntry = ClassHierarchy.Entry(classIndex)
                    if (!classEntry.hasMissingDependency) {
                        val dic = nameGenerators.getOrPut(classEntry) {
                            NameGenerator(dictionary)
                        }
                        for (fieldIndex in classEntry.fields.array) {
                            val fieldEntry = FieldHierarchy.Entry(fieldIndex)
                            if (!renameSources[fieldIndex]) continue
                            if (recordComponents.any { it.name == fieldEntry.name && it.descriptor == fieldEntry.desc }) continue
                            var checkPass = true
                            descendantsCheck@ for (descendant in classEntry.descendants.array) {
                                if (ClassHierarchy.Entry(descendant).hasMissingDependency) {
                                    checkPass = false
                                    break@descendantsCheck
                                }
                            }
                            if (!checkPass) continue

                            val affected = IntLinkedOpenHashSet()
                            affected.add(classEntry.index)
                            if (!fieldEntry.node.isPrivate) {
                                classEntry.descendants.forEach { descendant ->
                                    affected.add(descendant.index)
                                }
                            }

                            val checkSet = IntLinkedOpenHashSet()
                            checkSet.add(classEntry.index)
                            if (!fieldEntry.node.isPrivate || !config.aggressiveShadowNames) {
                                classEntry.descendants.forEach { checkSet.add(it.index) }
                            }
                            val checkList = ClassHierarchy.EntryArray(checkSet.toIntArray())
                            var newName: String
                            loop@ while (true) {
                                newName = config.malPrefix + dic.nextName(
                                    config.heavyOverloads,
                                    fieldEntry.descCode
                                ) + config.suffix
                                // Reflection looks fields up by name, not by descriptor.
                                if (recordComponents.any { it.name == newName }) continue
                                var keepThisName = true
                                check@ for (check in checkList.array) {
                                    val nameSet = existedNameMap.getOrPut(check) { mutableSetOf() }
                                    if (nameSet.contains(newName + fieldEntry.desc)) {
                                        keepThisName = false
                                        //println(
                                        //    "discard name $newName for ${classEntry.name}.${fieldEntry.name}${fieldEntry.desc} " +
                                        //            "due to ${ClassHierarchy.Entry(check).name}"
                                        //)
                                        break@check
                                    }
                                }
                                if (keepThisName) break
                            }
                            checkList.forEach { check ->
                                val nameSet = existedNameMap.getOrPut(check.index) { mutableSetOf() }
                                nameSet.add(newName + fieldEntry.desc)
                            }
                            affected.forEach { apply ->
                                instance.nameMapping.putFieldMapping(
                                    ClassHierarchy.Entry(apply).name,
                                    fieldEntry.node.name,
                                    fieldEntry.desc,
                                    newName
                                )
                                counter++
                            }
                        }
                    }
                }
                credit.add(counter * 300L)
                Logger.info("    Generated mapping for $counter fields")
            }
        }
    }

}
