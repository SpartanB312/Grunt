package net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.clinit
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import kotlin.random.Random

@Transformer.Stability(StableLevel.RockSolid)
@Transformer.Description(
    "process.encrypt.string.string_arrayed_encrypt.desc",
    "Encrypt string and replace ldc to array load"
)
class StringArrayedEncrypt : Transformer<StringArrayedEncrypt.Config>(
    "StringBasicEncrypt",
    Category.Encryption,
) {
    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Using char array instead of string LDC")
        @SettingName("Char array")
        val carray: Boolean = true,
        @SettingDesc("Replace invokedynamic string concat")
        @SettingName("Invoke dynamics")
        val invokeDynamics: Boolean = true,
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
        pre {
            //Logger.info(" > StringBasicEncrypt: Encrypting strings...")
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.version < Opcodes.V1_5) return@parForEachClassesFiltered
            if (classNode.isExcluded(DISABLE_STRING_ENCRYPT)) return@parForEachClassesFiltered
            val counter = counter.local
            val stringsToEncrypt = mutableMapOf<String, Int>()
            val classKey = Random.nextInt()
            // First, replace all INVOKEDYNAMIC instructions with LDC instructions.
            val randomGen = Xoshiro256PPRandom(getSeed(classNode.name))
            if (config.invokeDynamics) context(randomGen, counter) {
                replaceInvokeDynamics(classNode)
            }
            // Then, go over all LDC instructions and collect them.
            classNode.methods.shuffled().forEach { method ->
                if (method.isExcluded(DISABLE_STRING_ENCRYPT)) return@forEach
                val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                if (excluded) return@forEach
                method.instructions.asSequence()
                    .filter { it is LdcInsnNode && it.cst is String && (it.cst as String).isNotEmpty() }
                    .shuffled()
                    .forEach { instruction ->
                        val originalString = (instruction as LdcInsnNode).cst as String
                        // Skip duplicate strings
                        val existingIndex = stringsToEncrypt[originalString]
                        stringsToEncrypt.putIfAbsent(originalString, existingIndex ?: stringsToEncrypt.size)
                    }
            }
            if (stringsToEncrypt.isNotEmpty()) {
                counter.add(stringsToEncrypt.size)
                val poolField = field(
                    (if (classNode.isInterface) PUBLIC + FINAL else PRIVATE) + STATIC,
                    randomGen.getRandomString(16),
                    "[Ljava/lang/String;",
                    null, null
                ).appendAnnotation(GENERATED_FIELD)
                val decryptMethod = createDecryptMethod(classNode, randomGen.getRandomString(16), classKey)
                    .appendAnnotation(GENERATED_METHOD)
                val encryptedStrings = stringsToEncrypt.keys.map { it }.toTypedArray()
                val arrayInitMethod = method(
                    (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
                    randomGen.getRandomString(16),
                    "()V"
                ) {
                    INSTRUCTIONS {
                        encryptedStrings.forEachIndexed { index, string ->
                            val key = Random.nextInt()
                            val seed = Random.nextLong(100000L)
                            val encrypted = encrypt(string.toCharArray(), seed, key, classKey)
                            GETSTATIC(classNode.name, poolField.name, poolField.desc)
                            INT(index)
                            if (config.carray) {
                                INT(encrypted.length)
                                NEWARRAY(Opcodes.T_CHAR)
                                for (i in 0..<encrypted.length) {
                                    DUP
                                    INT(i)
                                    INT(encrypted[i].code)
                                    CASTORE
                                }
                            } else {
                                LDC(encrypted)
                                INVOKEVIRTUAL("java/lang/String", "toCharArray", "()[C", false)
                            }
                            LONG(seed)
                            INT(key)
                            INVOKESTATIC(
                                classNode.name,
                                decryptMethod.name,
                                decryptMethod.desc
                            )
                            AASTORE
                        }
                        RETURN
                    }
                }.appendAnnotation(GENERATED_METHOD)
                (classNode.methods.find { it.name == "<clinit>" } ?: clinit().also {
                    it.instructions.insert(InsnNode(Opcodes.RETURN))
                    classNode.methods.add(it)
                }).instructions.insert(instructions {
                    INT(encryptedStrings.size)
                    ANEWARRAY("java/lang/String")
                    PUTSTATIC(classNode.name, poolField.name, poolField.desc)
                    INVOKESTATIC(classNode.name, arrayInitMethod.name, arrayInitMethod.desc)
                })
                classNode.methods.forEach { methodNode ->
                    methodNode.instructions.asSequence()
                        .filter { it is LdcInsnNode && it.cst is String && (it.cst as String).isNotEmpty() }
                        .shuffled()
                        .forEach { instruction ->
                            val originalString = (instruction as LdcInsnNode).cst as String
                            val index = stringsToEncrypt[originalString]!!
                            methodNode.instructions.insert(instruction, instructions {
                                GETSTATIC(classNode.name, poolField.name, poolField.desc)
                                INT(index)
                                AALOAD
                            })
                            methodNode.instructions.remove(instruction)
                        }
                }
                classNode.fields.add(poolField)
                classNode.methods.add(decryptMethod)
                classNode.methods.add(arrayInitMethod)
            }
        }
        post {
            Logger.info(" - StringBasicEncrypt:")
            Logger.info("    Encrypted ${counter.global.get()} strings")
        }
        barrier()
    }

    // https://github.com/yaskylan/GotoObfuscator/blob/master/src/main/java/org/g0to/transformer/features/stringencryption/
    context(randomGen: UniformRandomProvider, counter: MergeableCounter)
    fun replaceInvokeDynamics(classNode: ClassNode) {
        val invokeDynamicConcatMethods = ArrayList<MethodNode>()
        classNode.methods.forEach { methodNode ->
            methodNode.instructions.asSequence()
                .filter { it is InvokeDynamicInsnNode && isStringConcatenation(it) }
                .shuffled()
                .forEach { instruction ->
                    val indy = instruction as InvokeDynamicInsnNode
                    invokeDynamicConcatMethods.add(
                        processStringConcatenation(
                            classNode,
                            methodNode,
                            indy,
                            randomGen.getRandomString(16)
                        )
                    )
                }
        }
        counter.add(invokeDynamicConcatMethods.size)
        invokeDynamicConcatMethods.forEach {
            classNode.methods.add(it)
        }
    }

    fun isStringConcatenation(instruction: InvokeDynamicInsnNode): Boolean {
        return instruction.name.equals("makeConcatWithConstants")
            && instruction.bsmArgs[0].toString().find { it != '\u0001' } != null
    }

    fun processStringConcatenation(
        classNode: ClassNode,
        methodNode: MethodNode,
        instruction: InvokeDynamicInsnNode,
        bootstrapName: String
    ): MethodNode {
        val arg = instruction.bsmArgs[0].toString()
        val argString = StringBuilder()
        val newArg = StringBuilder()
        val constants = ArrayList<String>()

        fun flushArgs() {
            if (argString.isNotEmpty()) {
                constants.add(argString.toString())
                argString.setLength(0)
                newArg.append('\u0002')
            }
        }

        var bsmArgIndex = 1

        for (c in arg) {
            when (c) {
                '\u0001' -> {
                    flushArgs()
                    newArg.append('\u0001')
                }

                '\u0002' -> {
                    flushArgs()
                    constants.add(instruction.bsmArgs[bsmArgIndex++].toString())
                    newArg.append('\u0002')
                }

                else -> {
                    argString.append(c)
                }
            }
        }

        flushArgs()

        if (constants.isEmpty()) {
            throw IllegalStateException()
        }

        val bootstrap = createConcatBootstrap(classNode, bootstrapName, constants)
        bootstrap.appendAnnotation(GENERATED_METHOD)
        methodNode.instructions.insert(instruction, instructions {
            INVOKEDYNAMIC(
                instruction.name,
                instruction.desc,
                Handle(
                    Opcodes.H_INVOKESTATIC,
                    classNode.name,
                    bootstrap.name,
                    bootstrap.desc
                ),
                newArg.toString()
            )
        })
        methodNode.instructions.remove(instruction)
        return bootstrap
    }

    private fun createConcatBootstrap(classNode: ClassNode, methodName: String, constants: ArrayList<String>) = method(
        (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
        methodName,
        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;"
    ) {
        INSTRUCTIONS {
            ALOAD(0)
            ALOAD(1)
            ALOAD(2)
            ALOAD(3)
            INT(constants.size)
            ANEWARRAY("java/lang/Object")
            DUP
            for ((i, cst) in constants.withIndex()) {
                INT(i)
                LDC(cst)
                AASTORE
                if (i != constants.lastIndex) {
                    DUP
                }
            }
            INVOKESTATIC(
                "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"
            )
            ARETURN
        }
        MAXS(8, 4)
    }

    fun createDecryptMethod(classNode: ClassNode, methodName: String, classKey: Int): MethodNode = method(
        (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
        methodName,
        "([CJI)Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            LABEL(L["L0"])
            INT(classKey)
            ILOAD(3)
            IXOR
            ISTORE(4)
            LABEL(L["L1"])
            ICONST_0
            ISTORE(5)
            LABEL(L["L2"])
            FRAME(Opcodes.F_APPEND, 2, arrayOf(Opcodes.INTEGER, Opcodes.INTEGER), 0, null)
            ILOAD(5)
            ALOAD(0)
            ARRAYLENGTH
            IF_ICMPGE(L["L3"])
            LABEL(L["L4"])
            ILOAD(4)
            LLOAD(1)
            L2I
            IXOR
            ILOAD(5)
            ICONST_M1
            IXOR
            IXOR
            ISTORE(4)
            LABEL(L["L5"])
            ILOAD(4)
            ILOAD(3)
            ILOAD(5)
            ALOAD(0)
            ARRAYLENGTH
            IMUL
            ISUB
            IXOR
            ISTORE(4)
            LABEL(L["L6"])
            ILOAD(4)
            INEG
            ILOAD(3)
            IMUL
            ILOAD(5)
            IOR
            ISTORE(4)
            LABEL(L["L7"])
            ALOAD(0)
            ILOAD(5)
            ALOAD(0)
            ILOAD(5)
            CALOAD
            ILOAD(4)
            IXOR
            I2C
            CASTORE
            LABEL(L["L8"])
            ILOAD(5)
            SIPUSH(255)
            IAND
            ISTORE(6)
            LABEL(L["L9"])
            ILOAD(3)
            ILOAD(6)
            ISHL
            ILOAD(3)
            ILOAD(6)
            INEG
            IUSHR
            IOR
            ISTORE(3)
            LABEL(L["L10"])
            LLOAD(1)
            ILOAD(6)
            I2L
            LXOR
            LSTORE(1)
            LABEL(L["L11"])
            IINC(5, 1)
            GOTO(L["L2"])
            LABEL(L["L3"])
            FRAME(Opcodes.F_CHOP, 1, null, 0, null)
            NEW("java/lang/String")
            DUP
            ALOAD(0)
            INVOKESPECIAL("java/lang/String", "<init>", "([C)V")
            ARETURN
            LABEL(L["L12"])
        }
        MAXS(4, 7)
    }

    fun encrypt(cArray: CharArray, seed: Long, key: Int, classKey: Int): String {
        var n = key
        var l = seed
        var n2 = classKey xor n

        for (i in cArray.indices) {
            n2 = n2 xor l.toInt() xor i.inv()
            n2 = n2 xor (n - i * cArray.size)
            n2 = (-n2 * n) or i
            cArray[i] = (cArray[i].code xor n2).toChar()
            val n3 = i and 0xFF
            n = (n shl n3) or (n ushr -n3)
            l = l xor n3.toLong()
        }
        return String(cArray)
    }

}
