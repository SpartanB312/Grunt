package net.spartanb312.grunt.process.transformers.encrypt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.rename.ReflectionSupportTransformer
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

/**
 * Encrypt strings
 * Last update on 2024/12/20
 */
object StringEncryptTransformer : Transformer("StringEncrypt", Category.Encryption), MethodProcessor {

    private val arrayed by setting("Arrayed", false)
    private val exclusion by setting("Exclusion", listOf())

    private val String.reflectionExcluded
        get() = ReflectionSupportTransformer.enabled && ReflectionSupportTransformer.strBlacklist.contains(this)

    private const val DECRYPT_METHOD_DESC = "([CJI)Ljava/lang/String;"

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting strings...")
        val count = count {
            runBlocking {
                nonExcluded.asSequence()
                    .filter { c -> c.version > Opcodes.V1_5 && exclusion.none { c.name.startsWith(it) }
                    }.forEach { classNode ->
                        fun job() {
                            var shouldAdd = false
                            val decryptMethodName = getRandomString(10)
                            val classKey = Random.nextInt()
                            for (methodNode in classNode.methods) {
                                if (methodNode.isAbstract) continue
                                if (replaceLdcInstructions(classNode, methodNode, decryptMethodName, classKey)) {
                                    shouldAdd = true
                                }
                            }
                            if (shouldAdd) {
                                classNode.methods.add(createDecryptMethod(classNode, decryptMethodName, classKey))
                            }
                        }
                        if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                    }
            }
        }.get()
        Logger.info("    Encrypted $count strings")
    }

    override fun transformMethod(classNode: ClassNode, methodNode: MethodNode) {
        val decryptMethodName = getRandomString(10)
        val classKey = Random.nextInt()
        if (replaceLdcInstructions(classNode, methodNode, decryptMethodName, classKey)) {
            classNode.methods.add(createDecryptMethod(classNode, decryptMethodName, classKey))
        }
    }

    fun replaceLdcInstructions(classNode: ClassNode, methodNode: MethodNode, decrypt: String, classKey: Int): Boolean {
        var foundLdc = false
        methodNode.instructions.asSequence()
            .filter { (it is LdcInsnNode && it.cst is String) }
            .forEach { insnNode ->
                val string = (insnNode as LdcInsnNode).cst as String
                methodNode.instructions.insert(
                    insnNode,
                    instructions {
                        val key = (Random.nextInt() and 0xFF) + 1
                        val seed = Random.nextLong(100000L)
                        val encrypted = encrypt(string.toCharArray(), seed, key, classKey)
                        if (arrayed) {
                            INT(string.length)
                            NEWARRAY(Opcodes.T_CHAR)
                            for (i in 0..(string.length - 1)) {
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
                        INVOKESTATIC(classNode.name, decrypt, DECRYPT_METHOD_DESC, false)
                    }
                )
                methodNode.instructions.remove(insnNode)
                foundLdc = true
            }
        return foundLdc
    }

    fun createDecryptMethod(classNode: ClassNode, methodName: String, classKey: Int): MethodNode = method(
        (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
        methodName,
        DECRYPT_METHOD_DESC
    ) {
        INSTRUCTIONS {
            LABEL(L["label0"])
            NEW("java/util/Random")
            DUP
            LLOAD(1)
            INVOKESPECIAL("java/util/Random", "<init>", "(J)V")
            ASTORE(4)
            LABEL(L["label1"])
            ALOAD(4)
            INVOKEVIRTUAL("java/util/Random", "nextInt", "()I")
            ILOAD(3)
            INEG
            IXOR
            ISTORE(5)
            LABEL(L["label2"])
            ICONST_0
            ISTORE(6)
            LABEL(L["label3"])
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/util/Random", Opcodes.INTEGER), 0, null)
            ILOAD(6)
            ALOAD(0)
            ARRAYLENGTH
            IF_ICMPGE(L["label4"])
            LABEL(L["label5"])
            ALOAD(0)
            ILOAD(6)
            ALOAD(0)
            ILOAD(6)
            CALOAD
            ILOAD(5)
            SIPUSH(255)
            IAND
            IXOR
            I2C
            CASTORE
            LABEL(L["label6"])
            ALOAD(0)
            ILOAD(6)
            ALOAD(0)
            ILOAD(6)
            CALOAD
            ILOAD(3)
            IXOR
            I2C
            CASTORE
            LABEL(L["label7"])
            ILOAD(5)
            ALOAD(4)
            INVOKEVIRTUAL("java/util/Random", "nextInt", "()I")
            ICONST_M1
            IXOR
            IADD
            ISTORE(5)
            LABEL(L["label8"])
            ILOAD(5)
            INT(classKey)
            IXOR
            ISTORE(5)
            LABEL(L["label9"])
            IINC(6, 1)
            GOTO(L["label3"])
            LABEL(L["label4"])
            FRAME(Opcodes.F_CHOP, 1, null, 0, null)
            NEW("java/lang/String")
            DUP
            ALOAD(0)
            INVOKESPECIAL("java/lang/String", "<init>", "([C)V")
            ARETURN
            LABEL(L["label10"])
        }
        MAXS(5, 7)
    }

    fun encrypt(chars: CharArray, seed: Long, key: Int, classKey: Int): String {
        val random = java.util.Random(seed)
        var n = random.nextInt() xor -key
        for (i in 0..(chars.size - 1)) {
            chars[i] = (chars[i].code xor (n and 0xFF)).toChar()
            chars[i] = (chars[i].code xor key).toChar()
            n += random.nextInt().inv()
            n = n xor classKey
        }
        return String(chars)
    }
}