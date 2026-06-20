package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping.MappingSource
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.collection.shuffled
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import net.spartanb312.grunteon.obfuscator.util.filters.filter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

@Transformer.Stability(StableLevel.Stable)
@Transformer.Description(
    "process.rename.mixin_field_renamer.desc",
    "Renaming fields in mixin classes"
)
class MixinFieldRenamer : Transformer<MixinFieldRenamer.Config>(
    "MixinFieldRenamer",
    Category.Renaming,
), MappingSource {

    init {
        after(Category.Encryption, "Mixin renamer should run after encryption category")
        after(Category.AntiDebug, "Mixin renamer should run after anti debug category")
        after(Category.Authentication, "Mixin renamer should run after authentication category")
        after(Category.Exploit, "Mixin renamer should run after exploit category")
        after(Category.Miscellaneous, "Mixin renamer should run after miscellaneous category")
        after(Category.Optimization, "Mixin renamer should run after optimization category")
        after(Category.Redirect, "Mixin renamer should run after redirect category")
        after(ControlflowJump::class.java, "MixinFieldRenamer should run after ControlflowJump")
        after(MixinClassRenamer::class.java, "MixinFieldRenamer should run after MixinClassRenamer")
        after(FieldRenamer::class.java, "MixinFieldRenamer should run after FieldRenamer")
    }

    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules inside mixin range")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Dictionary for mixin field renamer")
        @SettingName("Dictionary")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingDesc("Prefix for target mixin field names")
        @SettingName("Prefix")
        val prefix: String = "",
        @SettingDesc("Shuffled mappings for mixin fields")
        @SettingName("Shuffled")
        val shuffled: Boolean = true,
        @SettingDesc("Field owner/name exclusions")
        @SettingName("Exclusion")
        val exclusion: List<String> = listOf(
            "net/spartanb312/Example1",
            "net/spartanb312/Example2.field"
        ),
        @SettingDesc("Field names to keep")
        @SettingName("Excluded names")
        val excludedNames: List<String> = listOf("INSTANCE", "Companion"),
        @SettingDesc("Mixin annotations that bind target members")
        @SettingName("Excluded annotations")
        val excludedAnnotations: List<String> = listOf(
            "Lorg/spongepowered/asm/mixin/Shadow;",
            "Lorg/spongepowered/asm/mixin/Unique;",
            "Lorg/spongepowered/asm/mixin/Final;",
            "Lorg/spongepowered/asm/mixin/Mutable;",
            "Lorg/spongepowered/asm/mixin/Dynamic;"
        )
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" > MixinFieldRenamer: Generating mixin field mappings...")
        }
        buildFull(config)
    }

    context(instance: Grunteon, _: PipelineBuilder)
    private fun buildFull(config: Config) {
        val classHierarchy = globalScopeValue {
            Logger.info("    Building mixin class hierarchies...")
            ClassHierarchy.build(
                instance.workRes.inputClassCollection,
                instance.workRes::getClassNode
            )
        }

        seq {
            val strategy = instance.globalExclusion
                .and(instance.mixinInclusion)
                .and(config.classFilter.toClassPredicate())

            val mixinClasses = instance.workRes.inputClassCollection
                .filter(strategy)
                .sortedBy { it.name }

            if (mixinClasses.isEmpty()) {
                Logger.info("    No mixin classes found")
                return@seq
            }

            val randomGen = Xoshiro256PPRandom(getSeed("MixinFieldRenamer"))
            val fields = mixinClasses.flatMap { owner ->
                owner.fields.map { field -> owner to field }
            }.let {
                if (config.shuffled) it.shuffled(randomGen) else it
            }

            val dictionary = NameGenerator.getDictionary(config.dictionary)
            val nameGenerator = NameGenerator(dictionary)
            val hierarchy = classHierarchy.global
            var counter = 0

            context(hierarchy) {
                fields.forEach { (owner, field) ->
                    if (field.name in config.excludedNames) return@forEach
                    if (field.hasAnyAnnotation(config.excludedAnnotations)) return@forEach

                    val newName = config.prefix + nameGenerator.nextName()
                    val affectedOwners = collectAffectedOwners(hierarchy, owner)
                    affectedOwners.forEach { ownerName ->
                        if (!config.isExcluded(ownerName, field)) {
                            instance.nameMapping.putFieldMapping(ownerName, field.name, field.desc, newName)
                            counter++
                        }
                    }
                }
            }

            Logger.info("    Generated mapping for $counter mixin fields")
        }
    }

    context(_: ClassHierarchy)
    private fun collectAffectedOwners(hierarchy: ClassHierarchy, owner: ClassNode): Set<String> {
        val affected = linkedSetOf(owner.name)
        val ownerIndex = hierarchy.findClass(owner.name)
        if (ownerIndex != -1) {
            ClassHierarchy.Entry(ownerIndex).descendants.forEach { descendant ->
                affected.add(descendant.name)
            }
        }
        return affected
    }

    private fun FieldNode.hasAnyAnnotation(annotations: List<String>): Boolean {
        return annotations.any { hasAnnotation(it) }
    }

    private fun Config.isExcluded(owner: String, field: FieldNode): Boolean {
        val member = "$owner.${field.name}"
        return exclusion.any {
            val rule = it.removeSuffix("**")
            owner.startsWith(rule) || member.startsWith(rule)
        }
    }
}
