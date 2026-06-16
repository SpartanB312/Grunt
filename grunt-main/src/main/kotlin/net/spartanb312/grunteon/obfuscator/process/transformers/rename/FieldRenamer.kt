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
        val excludedNames: List<String> = listOf("INSTANCE", "Companion")
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
            val strategy = config.classFilter.buildFilterStrategy()
            val nonExcluded = instance.workRes.inputClassCollection
                .filter {
                    strategy.testClass(it)
                }
                .sortedBy { it.name }
                .toList()

            val existedNameMap = Int2ObjectOpenHashMap<MutableSet<String>>()
            //val nameGenerator = NameGenerator(NameGenerator.getDictionary(config.dictionary))
            val dictionary = NameGenerator.getDictionary(config.dictionary)
            var counter = 0
            context(classHierarchy, fieldHierarchy) {
                Logger.info("    Generating field mappings...")
                val nameGenerators = mutableMapOf<ClassHierarchy.Entry, NameGenerator>()
                nonExcluded.forEach { classNode ->
                    val classIndex = classHierarchy.findClass(classNode.name)
                    if (classIndex == -1) throw Exception("Class ${classNode.name} was not found in field hierarchy")
                    val classEntry = ClassHierarchy.Entry(classIndex)
                    if (!classEntry.hasMissingDependency) {
                        val dic = nameGenerators.getOrPut(classEntry) {
                            NameGenerator(dictionary)
                        }
                        for (fieldIndex in classEntry.fields.array) {
                            val fieldEntry = FieldHierarchy.Entry(fieldIndex)
                            // Source check
                            if (!fieldEntry.isSourceField) continue
                            if (fieldEntry.name in config.excludedNames) continue
                            // Check descendants
                            var checkPass = true
                            descendantsCheck@ for (descendant in classEntry.descendants.array) {
                                if (ClassHierarchy.Entry(descendant).hasMissingDependency) {
                                    checkPass = false
                                    break@descendantsCheck
                                }
                            }
                            if (!checkPass) continue

                            // Avoid shadow names
                            val checkSet = IntLinkedOpenHashSet()
                            checkSet.add(classEntry.index)
                            // Disable up check for static and private fields TODO: check this
                            if ((!fieldEntry.node.isStatic && !fieldEntry.node.isPrivate) || !config.aggressiveShadowNames) {
                                //println("Disable up check for ${classEntry.name}.${fieldEntry.name}${fieldEntry.desc}")
                                classEntry.descendants.forEach {
                                    checkSet.add(it.index)
                                }
                            }
                            val checkList = ClassHierarchy.EntryArray(checkSet.toIntArray())
                            var newName: String
                            loop@ while (true) {
                                newName = config.malPrefix + dic.nextName(
                                    config.heavyOverloads,
                                    fieldEntry.descCode
                                ) + config.suffix
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
                            // Disable up apply for private and static
                            val upApply = (!fieldEntry.node.isPrivate && !fieldEntry.node.isStatic)
                                || fieldEntry.node.isProtected
                            // Apply to children
                            if (upApply) {
                                val affected = mutableSetOf(classEntry.index)
                                affected.addAll(classEntry.descendants.array.toList()) // fixme: optimize this
                                affected.forEach { apply ->
                                    instance.nameMapping.putFieldMapping(
                                        ClassHierarchy.Entry(apply).name,
                                        fieldEntry.node.name,
                                        fieldEntry.desc,
                                        newName
                                    )
                                    counter++
                                }
                            } else {
                                instance.nameMapping.putFieldMapping(
                                    fieldEntry.owner.name,
                                    fieldEntry.name,
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
