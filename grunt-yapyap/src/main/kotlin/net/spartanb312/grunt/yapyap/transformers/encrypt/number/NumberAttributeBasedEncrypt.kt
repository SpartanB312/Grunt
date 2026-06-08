package net.spartanb312.grunt.yapyap.transformers.encrypt.number

import it.unisa.dia.gas.jpbc.Pairing
import it.unisa.dia.gas.jpbc.PairingParameters
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory
import it.unisa.dia.gas.plaf.jpbc.pairing.a.TypeACurveGenerator
import it.unisa.dia.gas.plaf.jpbc.pbc.PBCPairingFactory
import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.yapyap.annotation.ABE_EXTERNAL_CLASS
import net.spartanb312.grunt.yapyap.annotation.ABE_NUMBER_POOL_CLASS
import net.spartanb312.grunt.yapyap.annotation.DISABLE_NUMBER_ABE
import net.spartanb312.grunt.yapyap.annotation.DISABLE_STRING_ABE
import net.spartanb312.grunt.yapyap.runtime.NumberAbeRuntime
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import net.spartanb312.grunteon.obfuscator.util.numerical.asInt
import net.spartanb312.grunteon.obfuscator.util.numerical.asLong
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*
import java.util.zip.ZipFile
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.walk

/**
 * Encrypt numbers with a real CP-ABE KEM and an AES-GCM encrypted number pool.
 *
 * The generated pool and runtime classes are marked with ABE_EXTERNAL_CLASS and
 * ABE_NUMBER_POOL_CLASS for identification, but they are embedded into the output jar.
 */
@Transformer.Description(
    "process.encrypt.number.number_attribute_based_encrypt.desc",
    "Encrypt numbers using CP-ABE protected number pools"
)
class NumberAttributeBasedEncrypt : Transformer<NumberAttributeBasedEncrypt.Config>(
    "NumberAttributeBasedEncrypt",
    Category.Encryption,
) {

    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Encrypt integers")
        @SettingName("Integer")
        val integer: Boolean = true,
        @SettingDesc("Encrypt longs")
        @SettingName("Long")
        val long: Boolean = true,
        @SettingDesc("Encrypt floats")
        @SettingName("Float")
        val float: Boolean = true,
        @SettingDesc("Encrypt doubles")
        @SettingName("Double")
        val double: Boolean = true,
        @SettingDesc("Number encrypt rate. Range: 0.0..1.0")
        @DecimalRangeVal(min = 0.0, max = 1.0, step = 0.01)
        @SettingName("Chance")
        val chance: Double = 1.0,
        @SettingDesc("Minimum constants required before generating a CP-ABE pool")
        @SettingName("Min pool size")
        val minPoolSize: Int = 4,
        @SettingDesc("Maximum constants per CP-ABE pool")
        @SettingName("Max pool size")
        val maxPoolSize: Int = 1024,
        @SettingDesc("The upper limit of instruction count for a Method")
        @SettingName("Max instructions")
        val maxInstructions: Int = 16384,
        @SettingDesc("jPBC Type A subgroup order bits")
        @SettingName("R bits")
        val rBits: Int = 256,
        @SettingDesc("jPBC Type A base field bits")
        @SettingName("Q bits")
        val qBits: Int = 1536,
        @SettingDesc("Use jPBC PBC native backend when available")
        @SettingName("Use native")
        val useNative: Boolean = true,
        @SettingDesc("Project/application CP-ABE attribute")
        @SettingName("App ID")
        val appId: String = "grunt-yapyap",
        @SettingDesc("Specify method exclusions.")
        @SettingName("Exclusion")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig()

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }

        val counter = reducibleScopeValue { MergeableCounter() }
        val generatedPools = reducibleScopeValue {
            MergeableObjectList<ClassNode>(FastObjectArrayList())
        }
        val generatedResources = reducibleScopeValue {
            MergeableObjectList<GeneratedResource>(FastObjectArrayList())
        }
        val runtimeNeeded = reducibleScopeValue { MergeableCounter() }

        // Generate curve parameters ONCE for the whole transformer run.
        // TypeACurveGenerator.generate() runs Miller-Rabin primality tests which accounts
        // for a significant fraction of per-pool CPU time. Sharing read-only parameters
        // across all parallel buildPool calls eliminates this repeated cost.
        // Each parallel task still creates its own Pairing instance (JPBC is not thread-safe).
        val sharedParams = NumberAbeRuntime.buildParams(config.rBits, config.qBits)

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy(), 1) { classNode ->
            if (classNode.isExcluded(DISABLE_NUMBER_ENCRYPT)) return@parForEachClassesFiltered
            if (classNode.isExcluded(DISABLE_NUMBER_ABE)) return@parForEachClassesFiltered
            if (classNode.version < Opcodes.V1_5) return@parForEachClassesFiltered

            val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, "number-abe"))
            val pool = collectPool(config, classNode, randomGen)
            if (pool.size < config.minPoolSize) return@parForEachClassesFiltered

            Logger.debug("   NumberABE: Processing ${classNode.name}")

            val companion = createPoolClass(config, classNode, randomGen, pool, generatedResources.local, sharedParams)
            replaceNumberLoads(pool, companion)
            generatedPools.local.add(companion)
            counter.local.add(pool.size)
            runtimeNeeded.local.add()
        }

        seq {
            generatedPools.global.forEach {
                instance.workRes.addGeneratedClass(it)
            }
            generatedResources.global.forEach {
                instance.workRes.addGeneratedResource(it.name, it.content)
            }
            if (runtimeNeeded.global.get() > 0) {
                addRuntimeClasses(instance, config.useNative)
            }
        }

        post {
            Logger.info(" - NumberAttributeBasedEncrypt:")
            Logger.info("    Encrypted ${counter.global.get()} numbers")
            Logger.info("    Generated ${generatedPools.global.size} CP-ABE number pool classes")
        }
    }

    private fun collectPool(
        config: Config,
        classNode: ClassNode,
        randomGen: UniformRandomProvider
    ): NumberPool {
        val pool = NumberPool()
        classNode.methods.toList().asSequence()
            .filter { !it.isAbstract && !it.isNative }
            .forEach { method ->
                if (method.isExcluded(DISABLE_NUMBER_ENCRYPT)) return@forEach
                if (method.isExcluded(DISABLE_NUMBER_ABE)) return@forEach
                if (method.instructions == null || method.instructions.size() >= config.maxInstructions) return@forEach
                if (methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))) return@forEach

                method.instructions.toList().forEach { instruction ->
                    if (pool.size >= config.maxPoolSize) return@forEach
                    if (randomGen.nextFloat() > config.chance) return@forEach
                    if (config.integer) instruction.getIntValue()?.let {
                        pool.addInt(method, instruction, it)
                        return@forEach
                    }
                    if (config.long) instruction.getLongValue()?.let {
                        pool.addLong(method, instruction, it)
                        return@forEach
                    }
                    if (config.float) instruction.getFloatValue()?.let {
                        pool.addFloat(method, instruction, it)
                        return@forEach
                    }
                    if (config.double) instruction.getDoubleValue()?.let {
                        pool.addDouble(method, instruction, it)
                    }
                }
            }
        return pool
    }

    private fun createPoolClass(
        config: Config,
        classNode: ClassNode,
        randomGen: UniformRandomProvider,
        pool: NumberPool,
        generatedResources: MutableList<GeneratedResource>,
        sharedParams: PairingParameters
    ): ClassNode {
        val companionName = "${classNode.name}\$NumberABE_${randomGen.getRandomString(8)}"
        val intField = poolField(randomGen.getRandomString(12), "[I")
        val longField = poolField(randomGen.getRandomString(12), "[J")
        val floatField = poolField(randomGen.getRandomString(12), "[F")
        val doubleField = poolField(randomGen.getRandomString(12), "[D")
        val shapeSalt = randomBytesB64(16)
        val poolId = randomBytesB64(16)
        val buildId = randomBytesB64(16)
        val attributes = arrayOf(
            "app:${config.appId}",
            "build:$buildId",
            "kind:number",
            "pool:$poolId",
            NumberAbeRuntime.shapeAttribute(
                (if (classNode.superName != null) 1 else 0) + (classNode.interfaces?.size ?: 0),
                classNode.access,
                classNode.isAnnotation,
                classNode.isEnum,
                shapeSalt
            )
        )
        val payload = NumberAbeRuntime.buildPool(attributes, pool.toBlob(), config.useNative, sharedParams)
        val payloadResourceName = "META-INF/grunt-abe/${randomGen.getRandomString(16)}.bin"
        generatedResources.add(GeneratedResource(payloadResourceName, payload.toResourcePayload()))

        return ClassNode().apply {
            visit(classNode.version, Opcodes.ACC_PUBLIC, companionName, null, "java/lang/Object", null)
            appendAnnotation(GENERATED_CLASS)
            appendAnnotation(ABE_EXTERNAL_CLASS)
            appendAnnotation(ABE_NUMBER_POOL_CLASS)
            appendAnnotation(DISABLE_STRING_ABE)
            appendAnnotation(DISABLE_NUMBER_ABE)
            fields.add(intField)
            fields.add(longField)
            fields.add(floatField)
            fields.add(doubleField)
            methods.add(
                createClinit(
                    classNode,
                    name,
                    shapeSalt,
                    payloadResourceName,
                    intField,
                    longField,
                    floatField,
                    doubleField
                )
            )
            pool.intField = intField
            pool.longField = longField
            pool.floatField = floatField
            pool.doubleField = doubleField
        }
    }

    private fun poolField(name: String, desc: String): FieldNode =
        field(PUBLIC + STATIC, name, desc, null, null).appendAnnotation(GENERATED_FIELD)

    private fun createClinit(
        owner: ClassNode,
        companionName: String,
        shapeSalt: String,
        payloadResourceName: String,
        intField: FieldNode,
        longField: FieldNode,
        floatField: FieldNode,
        doubleField: FieldNode
    ): MethodNode = method(STATIC, "<clinit>", "()V") {
        INSTRUCTIONS {
            // runtimeShape = NumberAbeRuntime.shapeAttribute(Target.class, shapeSalt)
            LDC(Type.getType("L${owner.name};"))
            LDC(shapeSalt)
            INVOKESTATIC(
                RUNTIME_NAME,
                "shapeAttribute",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/String;"
            )
            ASTORE(0)

            // payload = NumberAbeRuntime.readPayload(NumberAbeRuntime.readResource(Target.class, payloadResourceName))
            LDC(Type.getType("L${owner.name};"))
            LDC(payloadResourceName)
            INVOKESTATIC(RUNTIME_NAME, "readResource", "(Ljava/lang/Class;Ljava/lang/String;)[B")
            INVOKESTATIC(RUNTIME_NAME, "readPayload", "([B)[Ljava/lang/String;")
            ASTORE(8)

            // pairing = NumberAbeRuntime.readPairing(parameters, useNative)
            ALOAD(8)
            ICONST_1
            AALOAD
            ALOAD(8)
            ICONST_0
            AALOAD
            LDC("pbc:true")
            INVOKEVIRTUAL("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
            INVOKESTATIC(
                RUNTIME_NAME,
                "readPairing",
                "(Ljava/lang/String;Z)$PAIRING_DESC"
            )
            ASTORE(1)

            // secretKey = NumberAbeRuntime.readSecretKey(pairing, base64(secretKey))
            ALOAD(1)
            ALOAD(8)
            ICONST_2
            AALOAD
            INVOKESTATIC(RUNTIME_NAME, "decodeBase64", "(Ljava/lang/String;)[B")
            INVOKESTATIC(
                RUNTIME_NAME,
                "readSecretKey",
                "($PAIRING_DESC[B)$SECRET_KEY_DESC"
            )
            ASTORE(2)

            // cipherText = NumberAbeRuntime.readCipherText(pairing, base64(cipherText))
            ALOAD(1)
            ALOAD(8)
            ICONST_3
            AALOAD
            INVOKESTATIC(RUNTIME_NAME, "decodeBase64", "(Ljava/lang/String;)[B")
            INVOKESTATIC(
                RUNTIME_NAME,
                "readCipherText",
                "($PAIRING_DESC[B)$CIPHER_TEXT_DESC"
            )
            ASTORE(3)

            // dataKeyElement = NumberAbeRuntime.decrypt(pairing, secretKey, cipherText, runtimeShape)
            ALOAD(1)
            ALOAD(2)
            ALOAD(3)
            ALOAD(0)
            INVOKESTATIC(
                RUNTIME_NAME,
                "decrypt",
                "($PAIRING_DESC$SECRET_KEY_DESC$CIPHER_TEXT_DESC" +
                        "Ljava/lang/String;)$ELEMENT_DESC"
            )
            ASTORE(4)

            // aesKey = NumberAbeRuntime.dataKey(dataKeyElement)
            ALOAD(4)
            INVOKESTATIC(RUNTIME_NAME, "dataKey", "($ELEMENT_DESC)[B")
            ASTORE(5)

            // plainBlob = NumberAbeRuntime.decryptBlob(aesKey, base64(encryptedBlob))
            ALOAD(5)
            ALOAD(8)
            ICONST_4
            AALOAD
            INVOKESTATIC(RUNTIME_NAME, "decodeBase64", "(Ljava/lang/String;)[B")
            INVOKESTATIC(RUNTIME_NAME, "decryptBlob", "([B[B)[B")
            ASTORE(6)

            // values = NumberAbeRuntime.readNumberBlob(plainBlob)
            ALOAD(6)
            INVOKESTATIC(RUNTIME_NAME, "readNumberBlob", "([B)[Ljava/lang/Object;")
            ASTORE(7)

            ALOAD(7)
            ICONST_0
            AALOAD
            CHECKCAST("[I")
            PUTSTATIC(companionName, intField.name, intField.desc)
            ALOAD(7)
            ICONST_1
            AALOAD
            CHECKCAST("[J")
            PUTSTATIC(companionName, longField.name, longField.desc)
            ALOAD(7)
            ICONST_2
            AALOAD
            CHECKCAST("[F")
            PUTSTATIC(companionName, floatField.name, floatField.desc)
            ALOAD(7)
            ICONST_3
            AALOAD
            CHECKCAST("[D")
            PUTSTATIC(companionName, doubleField.name, doubleField.desc)
            RETURN
        }
    }.appendAnnotation(GENERATED_METHOD)

    private fun replaceNumberLoads(pool: NumberPool, companion: ClassNode) {
        pool.replacements.forEach { replacement ->
            val replacementInsns = when (replacement.kind) {
                NumberKind.INT -> poolLoad(companion.name, pool.intField, replacement.index, Opcodes.IALOAD)
                NumberKind.LONG -> poolLoad(companion.name, pool.longField, replacement.index, Opcodes.LALOAD)
                NumberKind.FLOAT -> poolLoad(companion.name, pool.floatField, replacement.index, Opcodes.FALOAD)
                NumberKind.DOUBLE -> poolLoad(companion.name, pool.doubleField, replacement.index, Opcodes.DALOAD)
            }
            replacement.method.instructions.insertBefore(replacement.instruction, replacementInsns)
            replacement.method.instructions.remove(replacement.instruction)
        }
    }

    private fun poolLoad(owner: String, field: FieldNode, index: Int, arrayLoadOpcode: Int): InsnList = instructions {
        GETSTATIC(owner, field.name, field.desc)
        INT(index)
        +InsnNode(arrayLoadOpcode)
    }

    private fun addRuntimeClasses(instance: Grunteon, useNative: Boolean) {
        addClassesFromCodeSource(instance, NumberAbeRuntime::class.java, RUNTIME_PACKAGE)
        addClassesFromCodeSource(instance, Pairing::class.java, JPBC_PACKAGE)
        addClassesFromCodeSource(instance, PairingFactory::class.java, JPBC_PACKAGE)
        addClassesFromCodeSource(instance, TypeACurveGenerator::class.java, JPBC_PACKAGE)
        if (useNative) {
            addClassesFromCodeSource(instance, PBCPairingFactory::class.java, JPBC_PACKAGE)
            addOptionalClassesFromCodeSource(instance, "com.sun.jna.Native", "com/sun/jna/")
        }
    }

    private fun addOptionalClassesFromCodeSource(instance: Grunteon, anchorName: String, packagePrefix: String) {
        runCatching {
            addClassesFromCodeSource(instance, Class.forName(anchorName), packagePrefix)
        }
    }

    private fun addClassesFromCodeSource(instance: Grunteon, anchor: Class<*>, packagePrefix: String) {
        val location = anchor.protectionDomain.codeSource?.location?.toURI()?.let(Path::of) ?: run {
            addClassResource(instance, anchor.name.replace('.', '/'))
            return
        }
        if (Files.isDirectory(location)) {
            val root = location
            root.walk()
                .filter { !it.isDirectory() && it.extension == "class" }
                .map { root.relativize(it).pathString.replace('\\', '/').removeSuffix(".class") }
                .filter { it.startsWith(packagePrefix) }
                .forEach { addClassResource(instance, it) }
        } else {
            ZipFile(location.toFile()).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") }
                    .map { it.removeSuffix(".class") }
                    .filter { it.startsWith(packagePrefix) }
                    .forEach { addClassResource(instance, it) }
            }
        }
    }

    private fun addClassResource(instance: Grunteon, name: String) {
        if (instance.workRes.inputClassMap.containsKey(name)) return
        val classNode = ClassNode()
        ClassReader(name).accept(classNode, ClassReader.EXPAND_FRAMES)
        classNode.appendAnnotation(GENERATED_CLASS)
        classNode.appendAnnotation(ABE_EXTERNAL_CLASS)
        classNode.appendAnnotation(DISABLE_NUMBER_ABE)
        classNode.appendAnnotation(DISABLE_STRING_ABE)
        instance.workRes.addGeneratedClass(classNode)
    }

    private fun randomBytesB64(size: Int): String {
        val bytes = ByteArray(size)
        SECURE_RANDOM.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private enum class NumberKind {
        INT,
        LONG,
        FLOAT,
        DOUBLE
    }

    private data class Replacement(
        val method: MethodNode,
        val instruction: AbstractInsnNode,
        val kind: NumberKind,
        val index: Int
    )

    private data class GeneratedResource(
        val name: String,
        val content: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GeneratedResource

            if (name != other.name) return false
            if (!content.contentEquals(other.content)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + content.contentHashCode()
            return result
        }
    }

    private class NumberPool {
        val ints = LinkedHashMap<Int, Int>()
        val longs = LinkedHashMap<Long, Int>()
        val floats = LinkedHashMap<Float, Int>()
        val doubles = LinkedHashMap<Double, Int>()
        val replacements = mutableListOf<Replacement>()
        lateinit var intField: FieldNode
        lateinit var longField: FieldNode
        lateinit var floatField: FieldNode
        lateinit var doubleField: FieldNode

        val size get() = replacements.size

        fun addInt(method: MethodNode, instruction: AbstractInsnNode, value: Int) {
            replacements.add(Replacement(method, instruction, NumberKind.INT, ints.getOrPut(value) { ints.size }))
        }

        fun addLong(method: MethodNode, instruction: AbstractInsnNode, value: Long) {
            replacements.add(Replacement(method, instruction, NumberKind.LONG, longs.getOrPut(value) { longs.size }))
        }

        fun addFloat(method: MethodNode, instruction: AbstractInsnNode, value: Float) {
            replacements.add(Replacement(method, instruction, NumberKind.FLOAT, floats.getOrPut(value) { floats.size }))
        }

        fun addDouble(method: MethodNode, instruction: AbstractInsnNode, value: Double) {
            replacements.add(
                Replacement(
                    method,
                    instruction,
                    NumberKind.DOUBLE,
                    doubles.getOrPut(value) { doubles.size })
            )
        }

        fun toBlob(): ByteArray {
            val output = ByteArrayOutputStream()
            val data = DataOutputStream(output)
            data.writeInt(ints.size)
            ints.keys.forEach(data::writeInt)
            data.writeInt(longs.size)
            longs.keys.forEach(data::writeLong)
            data.writeInt(floats.size)
            floats.keys.forEach { data.writeInt(it.asInt()) }
            data.writeInt(doubles.size)
            doubles.keys.forEach { data.writeLong(it.asLong()) }
            return output.toByteArray()
        }
    }

    companion object {
        private val SECURE_RANDOM = SecureRandom()
        private const val RUNTIME_NAME = "net/spartanb312/grunt/yapyap/runtime/NumberAbeRuntime"
        private const val PAIRING_DESC = "Lit/unisa/dia/gas/jpbc/Pairing;"
        private const val ELEMENT_DESC = "Lit/unisa/dia/gas/jpbc/Element;"
        private const val SECRET_KEY_DESC = "L$RUNTIME_NAME\$SecretKey;"
        private const val CIPHER_TEXT_DESC = "L$RUNTIME_NAME\$CipherText;"
        private const val RUNTIME_PACKAGE = "net/spartanb312/grunt/yapyap/runtime/"
        private const val JPBC_PACKAGE = "it/unisa/dia/gas/"
    }
}

private fun Array<String>.toResourcePayload(): ByteArray {
    val output = ByteArrayOutputStream()
    val data = DataOutputStream(output)
    data.writeInt(size)
    forEach {
        val bytes = it.toByteArray(Charsets.UTF_8)
        data.writeInt(bytes.size)
        data.write(bytes)
    }
    return output.toByteArray()
}
