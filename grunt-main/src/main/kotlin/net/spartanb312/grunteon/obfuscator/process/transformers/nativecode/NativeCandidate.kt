package net.spartanb312.grunteon.obfuscator.process.transformers.nativecode

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.GENERATED_METHOD
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.NATIVE_EXCLUDED
import net.spartanb312.grunteon.obfuscator.util.NATIVE_INCLUDED
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

@Transformer.CreditMultiplier(0.2)
@Transformer.Stability(StableLevel.Developing)
@Transformer.Description(
    "process.native.native_candidate.desc",
    "Mark user selected methods as native codegen candidates"
)
class NativeCandidate : Transformer<NativeCandidate.Config>(
    "NativeCandidate",
    Category.Native,
) {

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Rules used to mark methods as native candidates")
        @SettingName("Rules")
        val rules: List<Rule> = emptyList(),
        @SettingDesc("Annotation added to matching methods")
        @SettingName("Annotation")
        val annotation: String = NATIVE_INCLUDED,
        @SettingDesc("Log every method marked by this transformer")
        @SettingName("Log marked methods")
        val logMarkedMethods: Boolean = true,
        @SettingDesc("Maximum marked methods printed in summary. 0 prints all")
        @IntRangeVal(min = 0, max = 100000)
        @SettingName("Log limit")
        val logLimit: Int = 256
    ) : TransformerConfig()

    @Serializable
    @SettingDesc("Set detection rule for scanner")
    @SettingName("Detection rule")
    data class Rule(
        @SettingDesc("Human readable rule name shown in the summary")
        @SettingName("Name")
        val name: String = "user",
        @SettingDesc("Method include rules. Supports package/**, owner/Class, owner/Class.method, owner/Class.method(I)V")
        @SettingName("Method include")
        val methodInclude: List<String> = emptyList(),
        @SettingDesc("Method exclude rules. Exclude wins over include")
        @SettingName("Method exclude")
        val methodExclude: List<String> = emptyList(),
        @SettingDesc("Descriptor include rules. Empty allows all descriptors. Supports * wildcard")
        @SettingName("Descriptor include")
        val descriptorInclude: List<String> = emptyList(),
        @SettingDesc("Descriptor exclude rules. Supports * wildcard")
        @SettingName("Descriptor exclude")
        val descriptorExclude: List<String> = emptyList(),
        @SettingDesc("Required class or method annotations. Empty disables this requirement")
        @SettingName("Required annotations")
        val requiredAnnotationList: List<String> = emptyList(),
        @SettingDesc("Class or method annotations that force a skip")
        @SettingName("Excluded annotations")
        val excludedAnnotationList: List<String> = listOf(NATIVE_EXCLUDED),
        @SettingDesc("Allow generated methods")
        @SettingName("Include generated")
        val includeGenerated: Boolean = true,
        @SettingDesc("Allow constructors")
        @SettingName("Include constructors")
        val includeConstructors: Boolean = false,
        @SettingDesc("Allow class initializers")
        @SettingName("Include class initializers")
        val includeClassInitializers: Boolean = false,
        @SettingDesc("Allow methods declared in interface classes")
        @SettingName("Include interface methods")
        val includeInterfaceMethods: Boolean = false
    ) {
        internal fun isActive(): Boolean {
            return methodInclude.isNotEmpty() ||
                descriptorInclude.isNotEmpty() ||
                descriptorExclude.isNotEmpty() ||
                requiredAnnotationList.isNotEmpty()
        }
    }

    init {
        after(Category.Encryption, "Native candidate marker should run after encryption category")
        after(Category.Controlflow, "Native candidate marker should run after controlflow category")
        after(Category.AntiDebug, "Native candidate marker should run after anti debug category")
        after(Category.Authentication, "Native candidate marker should run after authentication category")
        after(Category.Exploit, "Native candidate marker should run after exploit category")
        after(Category.Miscellaneous, "Native candidate marker should run after miscellaneous category")
        after(Category.Optimization, "Native candidate marker should run after optimization category")
        after(Category.Redirect, "Native candidate marker should run after redirect category")
        after(Category.Renaming, "Native candidate marker should run after renaming category")
        after(Category.Other, "Native candidate marker should run after other category")
        after(NativePreProcessor::class.java, "Native candidate marker should run after native preprocessor")
        before(Category.PostProcess, "Native candidate marker should run before post process category")
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        seq {
            val classPredicate = instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
                .withMapping(instance.nameMapping.revMappings)
            val result = markCandidates(
                instance.workRes.inputClassCollection.filter { classPredicate.test(it) },
                config,
                instance.nameMapping.revMappings
            )
            credit.add(result.marked.size * 50L)

            Logger.info(" - NativeCandidate:")
            Logger.info("    Matched ${result.marked.size} methods")
            Logger.info("    Newly marked ${result.newlyMarkedCount} methods as native candidates")
            if (result.alreadyMarkedCount != 0) {
                Logger.info("    Already marked ${result.alreadyMarkedCount} methods")
            }
            if (result.inactiveRuleNames.isNotEmpty()) {
                Logger.info("    Ignored ${result.inactiveRuleNames.size} inactive rules")
            }
            result.marked.groupingBy { it.ruleName }.eachCount().toSortedMap().forEach { (rule, count) ->
                Logger.info("    Rule $rule: $count methods")
            }
            if (config.logMarkedMethods && result.marked.isNotEmpty()) {
                val limit = if (config.logLimit <= 0) result.marked.size else config.logLimit
                result.marked.take(limit).forEach {
                    Logger.info("      ${it.displayName} (${it.status}, rule=${it.ruleName})")
                }
                val remaining = result.marked.size - limit
                if (remaining > 0) {
                    Logger.info("      ... $remaining more")
                }
            }
        }
    }

    internal fun markCandidates(
        classes: Iterable<ClassNode>,
        config: Config,
        classNameMapping: Map<String, String> = emptyMap()
    ): MarkResult {
        val targetAnnotation = normalizeAnnotation(config.annotation)
        if (targetAnnotation.isBlank()) {
            return MarkResult(emptyList(), config.rules.map { it.name })
        }

        val activeRules = config.rules.filter { it.isActive() }
        val inactiveRuleNames = config.rules.filterNot { it.isActive() }.map { it.name }
        if (activeRules.isEmpty()) {
            return MarkResult(emptyList(), inactiveRuleNames)
        }

        val compiledRules = activeRules.map { CompiledRule(it) }
        val marked = mutableListOf<MarkedMethod>()
        for (classNode in classes) {
            for (method in classNode.methods.orEmpty()) {
                val rule = compiledRules.firstOrNull { it.matches(classNode, method, classNameMapping) } ?: continue
                val status = if (method.hasAnnotation(targetAnnotation)) {
                    MarkStatus.AlreadyMarked
                } else {
                    method.appendAnnotation(targetAnnotation)
                    MarkStatus.NewlyMarked
                }
                marked += MarkedMethod(
                    owner = classNode.name,
                    methodName = method.name,
                    descriptor = method.desc,
                    ruleName = rule.name,
                    status = status
                )
            }
        }
        return MarkResult(marked, inactiveRuleNames)
    }

    private class CompiledRule(private val rule: Rule) {
        val name: String = rule.name
        private val includePredicates = buildMethodNamePredicates(rule.methodInclude)
        private val excludePredicates = buildMethodNamePredicates(rule.methodExclude)
        private val requiredAnnotations = rule.requiredAnnotationList.map { normalizeAnnotation(it) }.filter { it.isNotBlank() }
        private val excludedAnnotations = rule.excludedAnnotationList.map { normalizeAnnotation(it) }.filter { it.isNotBlank() }

        fun matches(classNode: ClassNode, method: MethodNode, classNameMapping: Map<String, String>): Boolean {
            if (method.isNative || method.isAbstract) return false
            if (!rule.includeInterfaceMethods && classNode.isInterface) return false
            if (!rule.includeConstructors && method.name == "<init>") return false
            if (!rule.includeClassInitializers && method.name == "<clinit>") return false
            if (!rule.includeGenerated && method.hasAnnotation(GENERATED_METHOD)) return false
            if (classNode.hasAnyAnnotation(excludedAnnotations) || method.hasAnyAnnotation(excludedAnnotations)) return false
            if (requiredAnnotations.isNotEmpty() &&
                !classNode.hasAnyAnnotation(requiredAnnotations) &&
                !method.hasAnyAnnotation(requiredAnnotations)
            ) {
                return false
            }

            val fullDescs = methodFullDescs(classNode, method, classNameMapping)
            if (includePredicates.isNotEmpty() && fullDescs.none { includePredicates.matchedAnyBy(it) }) return false
            if (excludePredicates.isNotEmpty() && fullDescs.any { excludePredicates.matchedAnyBy(it) }) return false
            val descriptors = methodDescriptors(method, classNameMapping)
            if (rule.descriptorInclude.isNotEmpty() &&
                !rule.descriptorInclude.any { pattern -> descriptors.any { wildcardMatches(pattern, it) } }
            ) {
                return false
            }
            if (rule.descriptorExclude.any { pattern -> descriptors.any { wildcardMatches(pattern, it) } }) return false
            return true
        }
    }

    internal data class MarkResult(
        val marked: List<MarkedMethod>,
        val inactiveRuleNames: List<String>
    ) {
        val newlyMarkedCount: Int get() = marked.count { it.status == MarkStatus.NewlyMarked }
        val alreadyMarkedCount: Int get() = marked.count { it.status == MarkStatus.AlreadyMarked }
    }

    internal data class MarkedMethod(
        val owner: String,
        val methodName: String,
        val descriptor: String,
        val ruleName: String,
        val status: MarkStatus
    ) {
        val displayName: String get() = "$owner.$methodName$descriptor"
    }

    internal enum class MarkStatus {
        NewlyMarked,
        AlreadyMarked
    }
}

private fun ClassNode.hasAnyAnnotation(annotations: List<String>): Boolean {
    return annotations.any { hasAnnotation(it) }
}

private fun MethodNode.hasAnyAnnotation(annotations: List<String>): Boolean {
    return annotations.any { hasAnnotation(it) }
}

private fun methodFullDescs(
    classNode: ClassNode,
    method: MethodNode,
    classNameMapping: Map<String, String>
): Set<String> {
    val fullDescs = linkedSetOf<String>()
    val owners = linkedSetOf(classNode.name)
    val originalOwner = classNameMapping[classNode.name]
    if (!originalOwner.isNullOrBlank() && originalOwner != classNode.name) {
        owners += originalOwner
    }
    val descriptors = methodDescriptors(method, classNameMapping)
    for (owner in owners) {
        for (descriptor in descriptors) {
            fullDescs += "$owner.${method.name}$descriptor"
        }
    }
    return fullDescs
}

private fun methodDescriptors(method: MethodNode, classNameMapping: Map<String, String>): Set<String> {
    return linkedSetOf(method.desc, method.desc.toOriginalDescriptor(classNameMapping))
}

private fun String.toOriginalDescriptor(classNameMapping: Map<String, String>): String {
    if (classNameMapping.isEmpty() || 'L' !in this) return this
    return object : Remapper(Opcodes.ASM9) {
        override fun map(internalName: String): String {
            return classNameMapping[internalName] ?: internalName
        }
    }.mapMethodDesc(this)
}

private fun normalizeAnnotation(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("L") && trimmed.endsWith(";")) {
        return trimmed.replace('.', '/')
    }
    val internalName = trimmed.removePrefix("L").removeSuffix(";").replace('.', '/')
    return "L$internalName;"
}

private fun wildcardMatches(pattern: String, value: String): Boolean {
    val trimmed = pattern.trim()
    if (trimmed == "*" || trimmed == "**") return true
    val regex = buildString {
        append('^')
        for (char in trimmed) {
            when (char) {
                '*' -> append(".*")
                '?' -> append('.')
                '\\', '.', '^', '$', '+', '{', '}', '[', ']', '(', ')', '|' -> {
                    append('\\')
                    append(char)
                }
                else -> append(char)
            }
        }
        append('$')
    }.toRegex()
    return regex.matches(value)
}
