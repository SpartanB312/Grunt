package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.FINAL
import net.spartanb312.genesis.kotlin.extensions.Java8
import net.spartanb312.genesis.kotlin.extensions.PRIVATE
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.SUPER
import net.spartanb312.genesis.kotlin.extensions.SYNTHETIC
import net.spartanb312.genesis.kotlin.extensions.insn.ALOAD
import net.spartanb312.genesis.kotlin.extensions.insn.ARETURN
import net.spartanb312.genesis.kotlin.extensions.insn.IADD
import net.spartanb312.genesis.kotlin.extensions.insn.ILOAD
import net.spartanb312.genesis.kotlin.extensions.insn.IMUL
import net.spartanb312.genesis.kotlin.extensions.insn.IOR
import net.spartanb312.genesis.kotlin.extensions.insn.IRETURN
import net.spartanb312.genesis.kotlin.extensions.insn.ISTORE
import net.spartanb312.genesis.kotlin.extensions.insn.ISUB
import net.spartanb312.genesis.kotlin.extensions.insn.IXOR
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESPECIAL
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESTATIC
import net.spartanb312.genesis.kotlin.extensions.insn.LDC
import net.spartanb312.genesis.kotlin.extensions.insn.RETURN
import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.DecimalRangeVal
import net.spartanb312.grunteon.obfuscator.process.IntRangeVal
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import net.spartanb312.grunteon.obfuscator.process.StableLevel
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.post
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.seq
import net.spartanb312.grunteon.obfuscator.util.GENERATED_CLASS
import net.spartanb312.grunteon.obfuscator.util.GENERATED_FIELD
import net.spartanb312.grunteon.obfuscator.util.GENERATED_METHOD
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

@Transformer.Stability(StableLevel.Moderate)
@Transformer.Description(
    "process.other.trash_class_generator.desc",
    "Generate harmless synthetic trash classes"
)
class TrashClassGenerator : Transformer<TrashClassGenerator.Config>(
    "TrashClassGenerator",
    Category.Miscellaneous,
) {

    init {
        after(Category.Optimization, "TrashClassGenerator should run after optimization category")
        before(Category.Encryption, "TrashClassGenerator should run before encryption category")
        before(Category.Controlflow, "TrashClassGenerator should run before controlflow category")
        before(Category.AntiDebug, "TrashClassGenerator should run before anti debug category")
        before(Category.Redirect, "TrashClassGenerator should run before redirect category")
        before(Category.Renaming, "TrashClassGenerator should run before renaming category")
        before(Category.Other, "TrashClassGenerator should run before other category")
        before(Category.PostProcess, "TrashClassGenerator should run before postprocess category")
    }

    @Serializable
    data class Config(
        @SettingDesc("Number of generated trash classes")
        @IntRangeVal(min = 0, max = 4096)
        @SettingName("Class count")
        val classCount: Int = 32,
        @SettingDesc("Packages used for generated classes. Dot and slash notation are both accepted")
        @SettingName("Packages")
        val packages: List<String> = listOf("net/spartanb312/grunteon/trash"),
        @SettingDesc("Also place trash classes into packages already present in the input")
        @SettingName("Mirror input packages")
        val mirrorInputPackages: Boolean = false,
        @SettingDesc("Generated class name prefix")
        @SettingName("Class prefix")
        val classPrefix: String = "Trash",
        @SettingDesc("Generated field and method name prefix")
        @SettingName("Member prefix")
        val memberPrefix: String = "t",
        @SettingDesc("Dictionary used for generated class and member names")
        @SettingName("Dictionary")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingDesc("Class file version. Use 0 to match the lowest input class version")
        @IntRangeVal(min = 0, max = 65)
        @SettingName("Class version")
        val classVersion: Int = 0,
        @SettingDesc("Minimum static fields generated per class")
        @IntRangeVal(min = 0, max = 64)
        @SettingName("Minimum fields")
        val minFields: Int = 1,
        @SettingDesc("Maximum static fields generated per class")
        @IntRangeVal(min = 0, max = 64)
        @SettingName("Maximum fields")
        val maxFields: Int = 4,
        @SettingDesc("Minimum methods generated per class")
        @IntRangeVal(min = 1, max = 128)
        @SettingName("Minimum methods")
        val minMethods: Int = 2,
        @SettingDesc("Maximum methods generated per class")
        @IntRangeVal(min = 1, max = 128)
        @SettingName("Maximum methods")
        val maxMethods: Int = 6,
        @SettingDesc("Minimum arithmetic operations in generated int methods")
        @IntRangeVal(min = 1, max = 64)
        @SettingName("Minimum method steps")
        val minMethodSteps: Int = 2,
        @SettingDesc("Maximum arithmetic operations in generated int methods")
        @IntRangeVal(min = 1, max = 64)
        @SettingName("Maximum method steps")
        val maxMethodSteps: Int = 8,
        @SettingDesc("Chance that a generated method returns a String instead of int/Object")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("String method chance")
        val stringMethodChance: Double = 0.25,
        @SettingDesc("Attach Grunteon generated annotations for PostProcess cleanup")
        @SettingName("Generated markers")
        val generatedMarkers: Boolean = true,
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val plans = buildPlans(config)

        seq {
            plans.forEach { plan ->
                instance.workRes.addGeneratedClass(plan.toClassNode(config))
            }
        }

        post {
            Logger.info(" - TrashClassGenerator:")
            Logger.info("    Generated ${plans.size} trash classes")
            Logger.info("    Generated ${plans.sumOf { it.fieldCount }} fields")
            Logger.info("    Generated ${plans.sumOf { it.methods.size }} methods")
        }
    }

    context(instance: Grunteon)
    private fun buildPlans(config: Config): List<TrashClassPlan> {
        val count = config.classCount.coerceIn(0, 4096)
        if (count == 0) return emptyList()

        val random = Xoshiro256PPRandom(getSeed(transformerSeed, "trash-classes"))
        val packagePool = packagePool(config)
        val dictionary = dictionary(config.dictionary)
        val classNames = NameGenerator(dictionary)
        val reservedNames = (instance.workRes.inputClassMap.keys + instance.workRes.libraryClassMap.keys).toMutableSet()
        val version = resolveClassVersion(config)
        val classPrefix = sanitizeIdentifier(config.classPrefix, "Trash")

        return buildList(count) {
            repeat(count) { index ->
                val owner = nextClassName(packagePool, classPrefix, classNames, reservedNames, random)
                val memberNames = NameGenerator(dictionary)
                val usedMembers = mutableSetOf("<init>")
                val fieldRange = normalizedRange(config.minFields, config.maxFields, minimum = 0)
                val methodRange = normalizedRange(config.minMethods, config.maxMethods, minimum = 1)
                val stepRange = normalizedRange(config.minMethodSteps, config.maxMethodSteps, minimum = 1)

                add(
                    TrashClassPlan(
                        name = owner,
                        version = version,
                        seed = random.nextInt(),
                        fieldCount = random.nextCount(fieldRange),
                        methods = List(random.nextCount(methodRange)) { methodIndex ->
                            TrashMethodPlan.create(
                                name = nextMemberName(config.memberPrefix, memberNames, usedMembers),
                                classIndex = index,
                                methodIndex = methodIndex,
                                steps = random.nextCount(stepRange),
                                stringChance = config.stringMethodChance,
                                random = random
                            )
                        }
                    )
                )
            }
        }
    }

    context(instance: Grunteon)
    private fun dictionary(type: NameGenerator.DictionaryType): NameGenerator.Dictionary {
        val dictionary = NameGenerator.getDictionary(type)
        return if (dictionary.elements.isNotEmpty()) {
            dictionary
        } else {
            Logger.warn("TrashClassGenerator dictionary ${dictionary.name} is empty. Falling back to Alphabet.")
            NameGenerator.getDictionary(NameGenerator.DictionaryType.Alphabet)
        }
    }

    context(instance: Grunteon)
    private fun packagePool(config: Config): List<String> {
        val configured = config.packages
            .map { sanitizePackage(it, "net/spartanb312/grunteon/trash") }
        val mirrored = if (config.mirrorInputPackages) {
            instance.workRes.inputClassCollection
                .asSequence()
                .mapNotNull { it.name.substringBeforeLast("/", missingDelimiterValue = "").ifEmpty { null } }
                .map { sanitizePackage(it, "net/spartanb312/grunteon/trash") }
                .toList()
        } else {
            emptyList()
        }
        return (configured + mirrored)
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { listOf("net/spartanb312/grunteon/trash") }
    }

    context(instance: Grunteon)
    private fun resolveClassVersion(config: Config): Int {
        if (config.classVersion > 0) return config.classVersion
        return instance.workRes.inputClassCollection
            .asSequence()
            .map { it.version }
            .filter { it > 0 }
            .minOrNull()
            ?.coerceAtLeast(Opcodes.V1_5)
            ?: Java8
    }

    private fun nextClassName(
        packagePool: List<String>,
        classPrefix: String,
        names: NameGenerator,
        reservedNames: MutableSet<String>,
        random: UniformRandomProvider
    ): String {
        while (true) {
            val pkg = packagePool[random.nextInt(packagePool.size)]
            val simpleName = classPrefix + names.nextName()
            val name = "$pkg/$simpleName"
            if (reservedNames.add(name)) return name
        }
    }

    private fun nextMemberName(
        prefix: String,
        names: NameGenerator,
        usedMembers: MutableSet<String>
    ): String {
        val safePrefix = sanitizeIdentifier(prefix, "t")
        while (true) {
            val name = safePrefix + names.nextName()
            if (usedMembers.add(name)) return name
        }
    }

    private data class TrashClassPlan(
        val name: String,
        val version: Int,
        val seed: Int,
        val fieldCount: Int,
        val methods: List<TrashMethodPlan>
    ) {
        fun toClassNode(config: Config): ClassNode {
            val fields = List(fieldCount) { index ->
                createField("${sanitizeIdentifier(config.memberPrefix, "t")}f$index", seed + index, config)
            }
            val node = clazz(
                access = PUBLIC + FINAL + SUPER,
                name = name,
                version = version
            ) {
                fields.forEach { +it }
                +constructor(config)
                methods.forEach { +it.toMethodNode(config) }
            }
            if (config.generatedMarkers) node.appendAnnotation(GENERATED_CLASS)
            return node
        }

        private fun constructor(config: Config): MethodNode {
            val node = method(PRIVATE, "<init>", "()V", null, null) {
                INSTRUCTIONS {
                    ALOAD(0)
                    INVOKESPECIAL("java/lang/Object", "<init>", "()V", false)
                    RETURN
                }
                MAXS(1, 1)
            }
            if (config.generatedMarkers) node.appendAnnotation(GENERATED_METHOD)
            return node
        }

        private fun createField(name: String, value: Int, config: Config): FieldNode {
            val node = when (value and 3) {
                0 -> field(PRIVATE + STATIC + FINAL + SYNTHETIC, name, "I", null, value)
                1 -> field(PRIVATE + STATIC + FINAL + SYNTHETIC, name, "J", null, value.toLong() * 0x9E3779B9L)
                else -> field(
                    PRIVATE + STATIC + FINAL + SYNTHETIC,
                    name,
                    "Ljava/lang/String;",
                    null,
                    "trash:${Integer.toUnsignedString(value, 36)}"
                )
            }
            if (config.generatedMarkers) node.appendAnnotation(GENERATED_FIELD)
            return node
        }
    }

    private sealed interface TrashMethodPlan {
        val name: String

        fun toMethodNode(config: Config): MethodNode

        data class IntMethod(
            override val name: String,
            val seed: Int,
            val steps: List<IntStep>
        ) : TrashMethodPlan {
            override fun toMethodNode(config: Config): MethodNode {
                val node = method(PUBLIC + STATIC + SYNTHETIC, name, "(II)I", null, null) {
                    INSTRUCTIONS {
                        INT(seed)
                        ISTORE(2)
                        ILOAD(2)
                        ILOAD(0)
                        IADD
                        ISTORE(2)
                        ILOAD(2)
                        ILOAD(1)
                        IXOR
                        ISTORE(2)
                        steps.forEach { it.emit(this) }
                        ILOAD(2)
                        IRETURN
                    }
                    MAXS(4, 3)
                }
                if (config.generatedMarkers) node.appendAnnotation(GENERATED_METHOD)
                return node
            }
        }

        data class StringMethod(
            override val name: String,
            val value: String
        ) : TrashMethodPlan {
            override fun toMethodNode(config: Config): MethodNode {
                val node = method(PUBLIC + STATIC + SYNTHETIC, name, "()Ljava/lang/String;", null, null) {
                    INSTRUCTIONS {
                        LDC(value)
                        ARETURN
                    }
                    MAXS(1, 0)
                }
                if (config.generatedMarkers) node.appendAnnotation(GENERATED_METHOD)
                return node
            }
        }

        data class ObjectMethod(
            override val name: String,
            val value: Int
        ) : TrashMethodPlan {
            override fun toMethodNode(config: Config): MethodNode {
                val node = method(PUBLIC + STATIC + SYNTHETIC, name, "()Ljava/lang/Object;", null, null) {
                    INSTRUCTIONS {
                        INT(value)
                        INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
                        ARETURN
                    }
                    MAXS(1, 0)
                }
                if (config.generatedMarkers) node.appendAnnotation(GENERATED_METHOD)
                return node
            }
        }

        companion object {
            fun create(
                name: String,
                classIndex: Int,
                methodIndex: Int,
                steps: Int,
                stringChance: Double,
                random: UniformRandomProvider
            ): TrashMethodPlan {
                val chance = stringChance.coerceIn(0.0, 1.0)
                return when {
                    random.nextDouble() < chance -> StringMethod(
                        name = name,
                        value = "trash:$classIndex:$methodIndex:${random.hex(12)}"
                    )

                    random.nextBoolean() -> ObjectMethod(
                        name = name,
                        value = random.nextInt()
                    )

                    else -> IntMethod(
                        name = name,
                        seed = random.nextInt(),
                        steps = List(steps.coerceAtLeast(1)) { IntStep.random(random) }
                    )
                }
            }
        }
    }

    private data class IntStep(
        val op: Op,
        val value: Int
    ) {
        fun emit(builder: net.spartanb312.genesis.kotlin.InsnListBuilder) = with(builder) {
            ILOAD(2)
            INT(value)
            when (op) {
                Op.Add -> IADD
                Op.Sub -> ISUB
                Op.Mul -> IMUL
                Op.Xor -> IXOR
                Op.Or -> IOR
            }
            ISTORE(2)
        }

        companion object {
            fun random(random: UniformRandomProvider): IntStep {
                val op = Op.entries[random.nextInt(Op.entries.size)]
                val value = if (op == Op.Mul) random.nextInt() or 1 else random.nextInt()
                return IntStep(op, value)
            }
        }
    }

    private enum class Op {
        Add,
        Sub,
        Mul,
        Xor,
        Or
    }

    private companion object {
        fun normalizedRange(min: Int, max: Int, minimum: Int): IntRange {
            val start = min.coerceAtLeast(minimum)
            val end = max.coerceAtLeast(start)
            return start..end
        }

        fun UniformRandomProvider.nextCount(range: IntRange): Int {
            return range.first + nextInt(range.last - range.first + 1)
        }

        fun UniformRandomProvider.hex(length: Int): String {
            val alphabet = "0123456789abcdef"
            return buildString(length) {
                repeat(length) {
                    append(alphabet[nextInt(alphabet.length)])
                }
            }
        }

        fun sanitizePackage(value: String, fallback: String): String {
            val normalized = value
                .replace('.', '/')
                .trim('/')
            val parts = normalized
                .split('/')
                .mapNotNull { part ->
                    val sanitized = sanitizeIdentifier(part, "")
                    sanitized.ifEmpty { null }
                }
            return parts.joinToString("/").ifEmpty { fallback }
        }

        fun sanitizeIdentifier(value: String, fallback: String): String {
            val source = value.trim().ifEmpty { fallback }
            val builder = StringBuilder(source.length)
            source.forEachIndexed { index, char ->
                val valid = if (index == 0) isIdentifierStart(char) else isIdentifierPart(char)
                builder.append(if (valid) char else '_')
            }
            if (builder.isEmpty()) builder.append(fallback)
            if (!isIdentifierStart(builder[0])) builder.insert(0, '_')
            return builder.toString()
        }

        fun isIdentifierStart(char: Char): Boolean {
            return char == '_' || char == '$' || char.isLetter()
        }

        fun isIdentifierPart(char: Char): Boolean {
            return isIdentifierStart(char) || char.isDigit()
        }
    }
}
