package net.spartanb312.grunteon.obfuscator.process.transformers.other

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import net.spartanb312.grunteon.obfuscator.process.StableLevel
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.globalScopeValue
import net.spartanb312.grunteon.obfuscator.process.parForEachClassesFiltered
import net.spartanb312.grunteon.obfuscator.process.post
import net.spartanb312.grunteon.obfuscator.process.pre
import net.spartanb312.grunteon.obfuscator.process.reducibleScopeValue
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping.NameMapping
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.ReflectionMetadataEntry
import net.spartanb312.grunteon.obfuscator.util.ReflectionMetadataKind
import net.spartanb312.grunteon.obfuscator.util.appendReflectionMetadata
import net.spartanb312.grunteon.obfuscator.util.appendStringBlacklist
import net.spartanb312.grunteon.obfuscator.util.reflectionMetadata
import net.spartanb312.grunteon.obfuscator.util.stringBlacklist
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.AnalyzerException
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.SourceInterpreter
import org.objectweb.asm.tree.analysis.SourceValue
import java.util.IdentityHashMap

/**
 * Marks reflection-related string literals before encryption, then remaps them
 * after rename mappings are applied.
 *
 * Design:
 * - `StringBlacklist` is the fast path for string encryption: keep these LDC
 *   values plaintext.
 * - `ReflectionMetadata` carries owner/kind data collected before later
 *   transformers reshape the method.
 * - MappingApplier calls [remapReflectionStrings] after normal ASM remapping,
 *   so preserved literals can be rewritten to renamed classes/members.
 */
@Transformer.Stability(StableLevel.Moderate)
@Transformer.Description(
    "process.other.reflection_support.desc",
    "Preserve and remap plaintext reflection strings"
)
class ReflectionSupport : Transformer<ReflectionSupport.Config>(
    "ReflectionSupport",
    Category.Other,
) {
    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Detect Class.forName/loadClass string literals")
        @SettingName("Class literals")
        val classLiterals: Boolean = true,
        @SettingDesc("Detect Class getMethod/getField string literals")
        @SettingName("Member literals")
        val memberLiterals: Boolean = true,
        @SettingDesc("Detect MethodHandles.Lookup string literals")
        @SettingName("Method handles")
        val methodHandleLiterals: Boolean = true,
        @SettingDesc("Preserve unresolved member names that appear in the input class pool")
        @SettingName("Unknown owner fallback")
        val unknownOwnerFallback: Boolean = true,
    ) : TransformerConfig()

    init {
        before(Category.Encryption, "ReflectionSupport should run before encryption category")
        before(Category.Controlflow, "ReflectionSupport should run before controlflow category")
        before(Category.AntiDebug, "ReflectionSupport should run before anti debug category")
        before(Category.Authentication, "ReflectionSupport should run before authentication category")
        before(Category.Exploit, "ReflectionSupport should run before exploit category")
        before(Category.Miscellaneous, "ReflectionSupport should run before miscellaneous category")
        before(Category.Redirect, "ReflectionSupport should run before redirect category")
        before(Category.Renaming, "ReflectionSupport should run before renaming category")
        before(Category.Optimization, "ReflectionSupport should run before optimization category")
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val reflectionIndex = globalScopeValue {
            ReflectionIndex.build(instance.workRes.inputClassCollection)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        pre {
            Logger.info(" > ReflectionSupport: Scanning reflection strings...")
        }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val index = reflectionIndex.global
            val counter = counter.local
            classNode.methods.forEach { methodNode ->
                val result = collectReflectionMetadata(classNode, methodNode, index, config)
                if (!result.strings.isEmpty()) {
                    methodNode.appendStringBlacklist(result.strings)
                    methodNode.appendReflectionMetadata(result.metadata)
                    counter.add(result.strings.size)
                }
            }
        }
        post {
            Logger.info(" - ReflectionSupport:")
            Logger.info("    Preserved ${counter.global.get()} reflection strings")
        }
    }

    companion object {
        private const val LOOK_BACK_LIMIT = 96

        /**
         * Runs after ClassRemapper has produced the renamed class copy.
         *
         * Metadata is preferred because it was captured before control-flow,
         * redirect, and encryption transformers had a chance to obscure owner
         * discovery. Live dataflow and linear fallback are still tried for older
         * annotations or incomplete metadata.
         */
        fun remapReflectionStrings(classNode: ClassNode, mapping: NameMapping): Int {
            var counter = 0
            classNode.methods.forEach { methodNode ->
                val blacklist = methodNode.stringBlacklist()
                val metadataMappings = buildMetadataMappings(methodNode.reflectionMetadata(), mapping)
                if (!metadataMappings.isEmpty()) {
                    counter += remapMetadataLiterals(classNode, methodNode, blacklist, metadataMappings)
                }
                if (blacklist.isEmpty()) return@forEach
                val analysis = analyzeMethod(classNode.name, methodNode)
                methodNode.instructions.forEach { instruction ->
                    if (instruction !is MethodInsnNode) return@forEach

                    val frame = analysis?.frameOf(instruction)
                    if (frame != null) {
                        if (instruction.isClassNameProducer()) {
                            val classSource = frame.argument(instruction, 0) ?: return@forEach
                            stringSources(classSource).forEach { classString ->
                                if (!blacklist.contains(classString.value)) return@forEach
                                val mapped = mapping.mapClassLiteral(classString.value) ?: return@forEach
                                if (mapped != classString.value) {
                                    classString.node.cst = mapped
                                    logRemappedString(
                                        classNode,
                                        methodNode,
                                        classString.node,
                                        "dataflow class literal",
                                        classString.value,
                                        mapped
                                    )
                                    counter++
                                }
                            }
                            return@forEach
                        }

                        val kind = instruction.memberKind(memberLiterals = true, methodHandleLiterals = true)
                            ?: return@forEach
                        val nameSource = frame.memberNameArgument(instruction) ?: return@forEach
                        val ownerSource = frame.memberOwnerArgument(instruction)
                        val owners = ownerSource?.let { ownerSources(it, analysis, null) } ?: ObjectOpenHashSet()
                        stringSources(nameSource).forEach { nameString ->
                            if (!blacklist.contains(nameString.value)) return@forEach
                            val mapped = mapUniqueMemberName(mapping, kind, owners, nameString.value) ?: return@forEach
                            if (mapped != nameString.value) {
                                nameString.node.cst = mapped
                                logRemappedString(
                                    classNode,
                                    methodNode,
                                    nameString.node,
                                    "dataflow ${kind.logName}",
                                    nameString.value,
                                    mapped
                                )
                                counter++
                            }
                        }
                        return@forEach
                    }

                    if (instruction.isClassNameProducer()) {
                        val classString = findPreviousString(instruction) ?: return@forEach
                        if (!blacklist.contains(classString.value)) return@forEach
                        val mapped = mapping.mapClassLiteral(classString.value) ?: return@forEach
                        if (mapped != classString.value) {
                            classString.node.cst = mapped
                            logRemappedString(
                                classNode,
                                methodNode,
                                classString.node,
                                "fallback class literal",
                                classString.value,
                                mapped
                            )
                            counter++
                        }
                        return@forEach
                    }

                    val kind = instruction.memberKind(memberLiterals = true, methodHandleLiterals = true)
                        ?: return@forEach
                    val nameString = findPreviousString(instruction) ?: return@forEach
                    if (!blacklist.contains(nameString.value)) return@forEach
                    val owner = findClassOwnerBefore(nameString.node, null) ?: return@forEach
                    val oldOwner = mapping.getOriginalClassName(owner)
                    val mapped = when (kind) {
                        MemberKind.Method -> mapping.mapUniqueMethodName(oldOwner, nameString.value)
                        MemberKind.Field -> mapping.mapUniqueFieldName(oldOwner, nameString.value)
                    } ?: return@forEach
                    if (mapped != nameString.value) {
                        nameString.node.cst = mapped
                        logRemappedString(
                            classNode,
                            methodNode,
                            nameString.node,
                            "fallback ${kind.logName}",
                            nameString.value,
                            mapped
                        )
                        counter++
                    }
                }
            }
            return counter
        }

        private fun collectReflectionMetadata(
            classNode: ClassNode,
            methodNode: MethodNode,
            index: ReflectionIndex,
            config: Config
        ): ScanResult {
            val result = ScanResult()
            val analysis = analyzeMethod(classNode.name, methodNode)
            methodNode.instructions.forEach { instruction ->
                if (instruction !is MethodInsnNode) return@forEach

                val frame = analysis?.frameOf(instruction)
                if (frame != null) {
                    if (config.classLiterals && instruction.isClassNameProducer()) {
                        val classSource = frame.argument(instruction, 0) ?: return@forEach
                        stringSources(classSource).forEach { classString ->
                            if (index.hasClassLiteral(classString.value)) result.addClass(classString.value)
                        }
                        return@forEach
                    }

                    val kind = instruction.memberKind(config.memberLiterals, config.methodHandleLiterals)
                        ?: return@forEach
                    val nameSource = frame.memberNameArgument(instruction) ?: return@forEach
                    val ownerSource = frame.memberOwnerArgument(instruction)
                    val owners = ownerSource?.let { ownerSources(it, analysis, index) } ?: ObjectOpenHashSet()
                    stringSources(nameSource).forEach { nameString ->
                        val matchedOwners = matchingOwners(index, owners, kind, nameString.value)
                        if (!matchedOwners.isEmpty()) {
                            result.addMember(kind, nameString.value, matchedOwners)
                        } else {
                            val matched = when (kind) {
                                MemberKind.Method -> config.unknownOwnerFallback && index.hasAnyMethod(nameString.value)
                                MemberKind.Field -> config.unknownOwnerFallback && index.hasAnyField(nameString.value)
                            }
                            if (matched) result.addMember(kind, nameString.value, emptyList())
                        }
                    }
                    return@forEach
                }

                if (config.classLiterals && instruction.isClassNameProducer()) {
                    val classString = findPreviousString(instruction) ?: return@forEach
                    if (index.hasClassLiteral(classString.value)) result.addClass(classString.value)
                    return@forEach
                }

                val kind = instruction.memberKind(config.memberLiterals, config.methodHandleLiterals)
                    ?: return@forEach
                val nameString = findPreviousString(instruction) ?: return@forEach
                val owner = findClassOwnerBefore(nameString.node, index)
                val matched = when (kind) {
                    MemberKind.Method -> owner?.let { index.hasMethod(it, nameString.value) }
                        ?: (config.unknownOwnerFallback && index.hasAnyMethod(nameString.value))

                    MemberKind.Field -> owner?.let { index.hasField(it, nameString.value) }
                        ?: (config.unknownOwnerFallback && index.hasAnyField(nameString.value))
                }
                if (matched) {
                    result.addMember(kind, nameString.value, if (owner != null) listOf(owner) else emptyList())
                }
            }
            return result
        }

        private fun buildMetadataMappings(
            entries: List<ReflectionMetadataEntry>,
            mapping: NameMapping
        ): Object2ObjectOpenHashMap<String, String> {
            val mappings = Object2ObjectOpenHashMap<String, String>()
            val ambiguous = ObjectOpenHashSet<String>()
            entries.forEach { entry ->
                if (ambiguous.contains(entry.value)) return@forEach
                val mapped = when (entry.kind) {
                    ReflectionMetadataKind.ClassLiteral -> mapping.mapClassLiteral(entry.value)
                    ReflectionMetadataKind.Method -> {
                        val owners = ObjectOpenHashSet<String>(entry.owners)
                        mapUniqueMemberName(mapping, MemberKind.Method, owners, entry.value)
                    }

                    ReflectionMetadataKind.Field -> {
                        val owners = ObjectOpenHashSet<String>(entry.owners)
                        mapUniqueMemberName(mapping, MemberKind.Field, owners, entry.value)
                    }
                } ?: return@forEach
                val previous = mappings.putIfAbsent(entry.value, mapped)
                if (previous != null && previous != mapped) {
                    mappings.remove(entry.value)
                    ambiguous.add(entry.value)
                }
            }
            return mappings
        }

        private fun remapMetadataLiterals(
            classNode: ClassNode,
            methodNode: MethodNode,
            blacklist: ObjectOpenHashSet<String>,
            mappings: Object2ObjectOpenHashMap<String, String>
        ): Int {
            var counter = 0
            methodNode.instructions.forEach { instruction ->
                if (instruction !is LdcInsnNode) return@forEach
                val original = instruction.cst as? String ?: return@forEach
                if (!blacklist.isEmpty() && !blacklist.contains(original)) return@forEach
                val mapped = mappings[original] ?: return@forEach
                if (mapped != original) {
                    instruction.cst = mapped
                    logRemappedString(classNode, methodNode, instruction, "metadata", original, mapped)
                    counter++
                }
            }
            return counter
        }

        private fun logRemappedString(
            classNode: ClassNode,
            methodNode: MethodNode,
            instruction: AbstractInsnNode,
            source: String,
            original: String,
            mapped: String
        ) {
            val index = methodNode.instructions.indexOf(instruction)
            Logger.debug(
                "    ReflectionSupport remapped $source at " +
                    "${classNode.name}.${methodNode.name}${methodNode.desc}#$index: " +
                    "\"${original.logLiteral()}\" -> \"${mapped.logLiteral()}\""
            )
        }

        private fun String.logLiteral(): String {
            return replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }

        private fun analyzeMethod(owner: String, methodNode: MethodNode): MethodAnalysis? {
            return try {
                val instructions = methodNode.instructions.toArray()
                val indexes = IdentityHashMap<AbstractInsnNode, Int>(instructions.size)
                instructions.forEachIndexed { index, instruction ->
                    indexes[instruction] = index
                }
                MethodAnalysis(
                    Analyzer(TracingSourceInterpreter()).analyze(owner, methodNode),
                    indexes
                )
            } catch (_: AnalyzerException) {
                null
            } catch (_: Throwable) {
                null
            }
        }

        private fun stringSources(source: SourceValue): List<StringInsn> {
            val strings = mutableListOf<StringInsn>()
            source.insns.forEach { instruction ->
                if (instruction is LdcInsnNode && instruction.cst is String) {
                    strings.add(StringInsn(instruction, instruction.cst as String))
                }
            }
            return strings
        }

        private fun ownerSources(
            source: SourceValue,
            analysis: MethodAnalysis,
            index: ReflectionIndex?
        ): ObjectOpenHashSet<String> {
            val owners = ObjectOpenHashSet<String>()
            source.insns.forEach { instruction ->
                if (instruction is LdcInsnNode) {
                    val type = instruction.cst as? Type
                    val internalName = type?.objectInternalName()
                    if (internalName != null && (index == null || index.hasClass(internalName))) {
                        owners.add(internalName)
                    }
                } else if (instruction is MethodInsnNode && instruction.isClassNameProducer()) {
                    val frame = analysis.frameOf(instruction) ?: return@forEach
                    val classSource = frame.argument(instruction, 0) ?: return@forEach
                    stringSources(classSource).forEach { classString ->
                        val internalName = normalizeClassLiteral(classString.value) ?: return@forEach
                        if (index == null || index.hasClass(internalName)) owners.add(internalName)
                    }
                }
            }
            return owners
        }

        /**
         * Reflection calls usually provide only a member name, not a descriptor.
         * Remap only when every candidate owner resolves this literal to exactly
         * one renamed member name.
         */
        private fun mapUniqueMemberName(
            mapping: NameMapping,
            kind: MemberKind,
            owners: ObjectOpenHashSet<String>,
            name: String
        ): String? {
            val mappedNames = ObjectOpenHashSet<String>()
            owners.forEach { owner ->
                val oldOwner = mapping.getOriginalClassName(owner)
                val mapped = when (kind) {
                    MemberKind.Method -> mapping.mapUniqueMethodName(oldOwner, name)
                    MemberKind.Field -> mapping.mapUniqueFieldName(oldOwner, name)
                }
                if (mapped != null) mappedNames.add(mapped)
            }
            return if (mappedNames.size == 1) mappedNames.first() else null
        }

        private fun matchingOwners(
            index: ReflectionIndex,
            owners: ObjectOpenHashSet<String>,
            kind: MemberKind,
            name: String
        ): List<String> {
            if (owners.isEmpty()) return emptyList()
            val result = mutableListOf<String>()
            owners.forEach { owner ->
                val matched = when (kind) {
                    MemberKind.Method -> index.hasMethod(owner, name)
                    MemberKind.Field -> index.hasField(owner, name)
                }
                if (matched) result.add(owner)
            }
            return result
        }

        private fun findPreviousString(start: AbstractInsnNode): StringInsn? {
            var node = start.previous
            var remaining = LOOK_BACK_LIMIT
            while (node != null && remaining-- > 0) {
                if (node is LdcInsnNode && node.cst is String) {
                    return StringInsn(node, node.cst as String)
                }
                node = node.previous
            }
            return null
        }

        private fun findClassOwnerBefore(start: AbstractInsnNode, index: ReflectionIndex?): String? {
            var node = start.previous
            var remaining = LOOK_BACK_LIMIT
            while (node != null && remaining-- > 0) {
                if (node is LdcInsnNode) {
                    val type = node.cst as? Type
                    val internalName = type?.objectInternalName()
                    if (internalName != null) return internalName
                } else if (node is MethodInsnNode && node.isClassNameProducer()) {
                    val classString = findPreviousString(node) ?: return null
                    val internalName = normalizeClassLiteral(classString.value) ?: return null
                    if (index == null || index.hasClass(internalName)) return internalName
                }
                node = node.previous
            }
            return null
        }

        private fun MethodInsnNode.isClassNameProducer(): Boolean {
            return (owner == "java/lang/Class" && name == "forName" &&
                    desc.startsWith("(Ljava/lang/String;") && desc.endsWith(")Ljava/lang/Class;")) ||
                    (name == "loadClass" &&
                            desc.startsWith("(Ljava/lang/String;") && desc.endsWith(")Ljava/lang/Class;"))
        }

        private fun MethodInsnNode.memberKind(
            memberLiterals: Boolean,
            methodHandleLiterals: Boolean
        ): MemberKind? {
            if (memberLiterals && owner == "java/lang/Class") {
                if ((name == "getMethod" || name == "getDeclaredMethod") &&
                    desc == "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"
                ) return MemberKind.Method
                if ((name == "getField" || name == "getDeclaredField") &&
                    desc == "(Ljava/lang/String;)Ljava/lang/reflect/Field;"
                ) return MemberKind.Field
            }
            if (methodHandleLiterals && owner == "java/lang/invoke/MethodHandles\$Lookup") {
                return when (name) {
                    "findVirtual", "findStatic", "findSpecial", "bind" -> MemberKind.Method
                    "findGetter", "findSetter", "findStaticGetter", "findStaticSetter" -> MemberKind.Field
                    else -> null
                }
            }
            return null
        }

        private fun Frame<SourceValue>.argument(instruction: MethodInsnNode, argumentIndex: Int): SourceValue? {
            val arguments = Type.getArgumentTypes(instruction.desc)
            if (argumentIndex !in arguments.indices) return null
            val start = stackSize - arguments.size
            val index = start + argumentIndex
            if (index !in 0..<stackSize) return null
            return getStack(index)
        }

        private fun Frame<SourceValue>.receiver(instruction: MethodInsnNode): SourceValue? {
            if (instruction.opcode == Opcodes.INVOKESTATIC) return null
            val arguments = Type.getArgumentTypes(instruction.desc)
            val index = stackSize - arguments.size - 1
            if (index !in 0..<stackSize) return null
            return getStack(index)
        }

        private fun Frame<SourceValue>.memberNameArgument(instruction: MethodInsnNode): SourceValue? {
            return if (instruction.owner == "java/lang/invoke/MethodHandles\$Lookup" && instruction.name == "bind") {
                argument(instruction, 1)
            } else {
                argument(instruction, if (instruction.owner == "java/lang/Class") 0 else 1)
            }
        }

        private fun Frame<SourceValue>.memberOwnerArgument(instruction: MethodInsnNode): SourceValue? {
            return if (instruction.owner == "java/lang/Class") {
                receiver(instruction)
            } else if (instruction.owner == "java/lang/invoke/MethodHandles\$Lookup" && instruction.name != "bind") {
                argument(instruction, 0)
            } else {
                null
            }
        }

        private fun Type.objectInternalName(): String? {
            return when (sort) {
                Type.OBJECT -> internalName
                Type.ARRAY -> elementType.takeIf { it.sort == Type.OBJECT }?.internalName
                else -> null
            }
        }

        private fun normalizeClassLiteral(value: String): String? {
            if (value.isEmpty()) return null
            if (value[0] == '[') {
                val normalized = value.replace('.', '/')
                val dimensions = normalized.indexOfFirst { it != '[' }
                if (dimensions <= 0 || normalized.getOrNull(dimensions) != 'L') return null
                val end = normalized.indexOf(';', dimensions + 1)
                if (end != normalized.lastIndex) return null
                return normalized.substring(dimensions + 1, end)
            }
            if (value.length > 2 && value[0] == 'L' && value.last() == ';') {
                return value.substring(1, value.lastIndex).replace('.', '/')
            }
            return value.replace('.', '/')
        }

        private class ScanResult {
            val strings = ObjectOpenHashSet<String>()
            val metadata = mutableListOf<ReflectionMetadataEntry>()
            private val encodedMetadata = ObjectOpenHashSet<String>()

            fun addClass(value: String) {
                if (value.isEmpty()) return
                strings.add(value)
                addMetadata(ReflectionMetadataEntry(ReflectionMetadataKind.ClassLiteral, emptyList(), value))
            }

            fun addMember(kind: MemberKind, value: String, owners: Iterable<String>) {
                if (value.isEmpty()) return
                strings.add(value)
                val metadataKind = when (kind) {
                    MemberKind.Method -> ReflectionMetadataKind.Method
                    MemberKind.Field -> ReflectionMetadataKind.Field
                }
                addMetadata(ReflectionMetadataEntry(metadataKind, owners.toList().sorted(), value))
            }

            private fun addMetadata(entry: ReflectionMetadataEntry) {
                if (encodedMetadata.add(entry.encode())) metadata.add(entry)
            }
        }

        private data class StringInsn(
            val node: LdcInsnNode,
            val value: String
        )

        private class MethodAnalysis(
            private val frames: Array<Frame<SourceValue>?>,
            private val indexes: IdentityHashMap<AbstractInsnNode, Int>
        ) {
            fun frameOf(instruction: AbstractInsnNode): Frame<SourceValue>? {
                val index = indexes[instruction] ?: return null
                return frames.getOrNull(index)
            }
        }

        /**
         * ASM SourceInterpreter normally treats ALOAD as the newest source. For
         * reflection tracking we need the original producer, e.g. the LDC before
         * ASTORE, so local variables survive simple control-flow merges.
         */
        private class TracingSourceInterpreter : SourceInterpreter(Opcodes.ASM9) {
            override fun copyOperation(insn: AbstractInsnNode, value: SourceValue): SourceValue {
                return value
            }
        }

        private enum class MemberKind {
            Method,
            Field;

            val logName: String
                get() = when (this) {
                    Method -> "method literal"
                    Field -> "field literal"
                }
        }

        private class ReflectionIndex(
            private val classes: ObjectOpenHashSet<String>,
            private val methodsByOwner: Object2ObjectOpenHashMap<String, ObjectOpenHashSet<String>>,
            private val fieldsByOwner: Object2ObjectOpenHashMap<String, ObjectOpenHashSet<String>>,
            private val methods: ObjectOpenHashSet<String>,
            private val fields: ObjectOpenHashSet<String>
        ) {
            fun hasClass(internalName: String): Boolean = classes.contains(internalName)

            fun hasClassLiteral(value: String): Boolean {
                val internalName = normalizeClassLiteral(value) ?: return false
                return hasClass(internalName)
            }

            fun hasMethod(owner: String, name: String): Boolean {
                return methodsByOwner[owner]?.contains(name) == true
            }

            fun hasField(owner: String, name: String): Boolean {
                return fieldsByOwner[owner]?.contains(name) == true
            }

            fun hasAnyMethod(name: String): Boolean = methods.contains(name)

            fun hasAnyField(name: String): Boolean = fields.contains(name)

            companion object {
                fun build(classNodes: Collection<ClassNode>): ReflectionIndex {
                    val classes = ObjectOpenHashSet<String>(classNodes.size)
                    val methodsByOwner = Object2ObjectOpenHashMap<String, ObjectOpenHashSet<String>>(classNodes.size)
                    val fieldsByOwner = Object2ObjectOpenHashMap<String, ObjectOpenHashSet<String>>(classNodes.size)
                    val methods = ObjectOpenHashSet<String>()
                    val fields = ObjectOpenHashSet<String>()
                    classNodes.forEach { classNode ->
                        classes.add(classNode.name)

                        val ownerMethods = ObjectOpenHashSet<String>(classNode.methods.size)
                        classNode.methods.forEach { method ->
                            if (method.name != "<init>" && method.name != "<clinit>") {
                                ownerMethods.add(method.name)
                                methods.add(method.name)
                            }
                        }
                        if (!ownerMethods.isEmpty()) methodsByOwner[classNode.name] = ownerMethods

                        val ownerFields = ObjectOpenHashSet<String>(classNode.fields.size)
                        classNode.fields.forEach { field ->
                            ownerFields.add(field.name)
                            fields.add(field.name)
                        }
                        if (!ownerFields.isEmpty()) fieldsByOwner[classNode.name] = ownerFields
                    }
                    return ReflectionIndex(classes, methodsByOwner, fieldsByOwner, methods, fields)
                }
            }
        }
    }
}
