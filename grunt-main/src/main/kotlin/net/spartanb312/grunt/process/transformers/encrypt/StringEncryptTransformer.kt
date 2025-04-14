package net.spartanb312.grunt.process.transformers.encrypt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.genesis.kotlin.clinit
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.rename.ReflectionSupportTransformer
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import kotlin.random.Random

/**
 * Encrypt strings
 * Last update on 2024/12/28
 */
object StringEncryptTransformer : Transformer("StringEncrypt", Category.Encryption), MethodProcessor {

    private val arrayed by setting("Arrayed", false)
    private val replaceInvokeDynamics by setting("ReplaceInvokeDynamics", true)
    private val exclusion by setting("Exclusion", listOf())

    private val String.reflectionExcluded
        get() = ReflectionSupportTransformer.enabled && ReflectionSupportTransformer.strBlacklist.contains(this)

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting strings...")
        val count = count {
            runBlocking {
                nonExcluded.asSequence()
                    .filter { c ->
                        c.version > Opcodes.V1_5 && exclusion.none { c.name.startsWith(it) }
                    }.forEach { classNode ->
                        fun job() {
                            transformClass(classNode, null)
                        }
                        if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                    }
            }
        }.get()
        Logger.info("    Encrypted $count strings")
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        transformClass(owner, method)
    }

    private fun transformClass(classNode: ClassNode, onlyObfuscate: MethodNode?) {
        val stringsToEncrypt = mutableMapOf<String, Int>()
        val classKey = Random.nextInt()

        // First, replace all INVOKEDYNAMIC instructions with LDC instructions.
        if (replaceInvokeDynamics) {
            replaceInvokeDynamics(classNode, onlyObfuscate)
        }

        // Then, go over all LDC instructions and collect them.
        classNode.methods.shuffled().forEach { methodNode ->
            if (onlyObfuscate != null && onlyObfuscate != methodNode) return@forEach
            methodNode.instructions.asSequence()
                .filter {
                    it is LdcInsnNode && it.cst is String
                            && (it.cst as String).isNotEmpty()
                            && !(it.cst as String).reflectionExcluded
                }.shuffled()
                .forEach { instruction ->
                    val originalString = (instruction as LdcInsnNode).cst as String
                    // Skip duplicate strings
                    val existingIndex = stringsToEncrypt[originalString]
                    stringsToEncrypt.putIfAbsent(originalString, existingIndex ?: stringsToEncrypt.size)
                }
        }

        if (stringsToEncrypt.isNotEmpty()) {
            val poolField = field(
                (if (classNode.isInterface) PUBLIC + FINAL else PRIVATE) + STATIC,
                getRandomString(16),
                "[Ljava/lang/String;",
                null, null
            )
            val decryptMethod = createDecryptMethod(classNode, getRandomString(16), classKey)
            val encryptedStrings = stringsToEncrypt.keys.map { it }.toTypedArray()
            val arrayInitMethod = method(
                (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
                getRandomString(16),
                "()V"
            ) {
                INSTRUCTIONS {
                    encryptedStrings.forEachIndexed { index, string ->
                        val key = Random.nextInt()
                        val seed = Random.nextLong(100000L)
                        val encrypted = encrypt(string.toCharArray(), seed, key, classKey)
                        GETSTATIC(classNode.name, poolField.name, poolField.desc)
                        INT(index)
                        if (arrayed) {
                            INT(encrypted.length)
                            NEWARRAY(Opcodes.T_CHAR)
                            for (i in 0..(encrypted.length - 1)) {
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
                        INVOKESTATIC(classNode.name, decryptMethod.name, decryptMethod.desc, classNode.isInterface)
                        AASTORE
                    }
                    RETURN
                }
            }

            (classNode.methods.find { it.name == "<clinit>" } ?: clinit().also {
                it.instructions.insert(InsnNode(Opcodes.RETURN))
                classNode.methods.add(it)
            }).instructions.insert(instructions {
                INT(encryptedStrings.size)
                ANEWARRAY("java/lang/String")
                PUTSTATIC(classNode.name, poolField.name, poolField.desc)
                INVOKESTATIC(classNode.name, arrayInitMethod.name, arrayInitMethod.desc, classNode.isInterface)
            })

            classNode.methods.forEach { methodNode ->
                if (onlyObfuscate != null && onlyObfuscate != methodNode) return@forEach
                methodNode.instructions.asSequence()
                    .filter {
                        it is LdcInsnNode && it.cst is String
                                && (it.cst as String).isNotEmpty()
                                && !(it.cst as String).reflectionExcluded
                    }.shuffled()
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

    // https://github.com/yaskylan/GotoObfuscator/blob/master/src/main/java/org/g0to/transformer/features/stringencryption/
    fun replaceInvokeDynamics(classNode: ClassNode, onlyObfuscate: MethodNode?) {
        val invokeDynamicConcatMethods = ArrayList<MethodNode>()

        classNode.methods.forEach { methodNode ->
            if (onlyObfuscate != null && onlyObfuscate != methodNode) return@forEach
            methodNode.instructions.asSequence()
                .filter { it is InvokeDynamicInsnNode && isStringConcatenation(it) }
                .shuffled()
                .forEach { instruction ->
                    invokeDynamicConcatMethods.add(
                        processStringConcatenation(
                            classNode,
                            methodNode,
                            instruction as InvokeDynamicInsnNode,
                            getRandomString(16)
                        )
                    )
                }
        }

        invokeDynamicConcatMethods.forEach {
            classNode.methods.add(it)
        }
    }

    fun isStringConcatenation(instruction: InvokeDynamicInsnNode): Boolean {
        return instruction.name.equals("makeConcatWithConstants")
                && instruction.bsmArgs[0].toString().find { it != '\u0001' } != null
    }

    fun processStringConcatenation(
        classNode: ClassNode, methodNode: MethodNode,
        instruction: InvokeDynamicInsnNode, bootstrapName: String
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

        methodNode.instructions.insert(instruction, instructions {
            INVOKEDYNAMIC(
                instruction.name,
                instruction.desc,
                Handle(
                    Opcodes.H_INVOKESTATIC,
                    classNode.name,
                    bootstrap.name,
                    bootstrap.desc,
                    classNode.isInterface
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