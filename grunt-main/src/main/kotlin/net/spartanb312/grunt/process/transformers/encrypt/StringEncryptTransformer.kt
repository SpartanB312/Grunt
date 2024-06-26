package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

/**
 * Encrypt strings
 */
object StringEncryptTransformer : Transformer("StringEncrypt", Category.Encryption) {

    private val times by value("Intensity", 1)
    private val exclusion by value("Exclusions", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting strings...")
        val count = count {
            repeat(times) { t ->
                if (times > 1) Logger.info("    Encrypting strings ${t + 1} of $times times")
                nonExcluded.asSequence()
                    .filter { c -> !c.isInterface && c.version > Opcodes.V1_5 && exclusion.none { c.name.startsWith(it) } }
                    .forEach { classNode ->
                        val randomString = getRandomString(10)
                        val random = Random.nextInt(0x8, 0x800)
                        var shouldAdd = false
                        for (methodNode in classNode.methods) {
                            if (methodNode.isAbstract) continue
                            methodNode.instructions.asSequence()
                                .filter { (it is LdcInsnNode && it.cst is String) }
                                .forEach { insnNode ->
                                    methodNode.instructions.insert(
                                        insnNode,
                                        MethodInsnNode(
                                            Opcodes.INVOKESTATIC, classNode.name,
                                            randomString, "(Ljava/lang/String;)Ljava/lang/String;",
                                            false
                                        )
                                    )
                                    (insnNode as LdcInsnNode).cst = xor(insnNode.cst as String, random)
                                    if (t == 0) add()
                                    shouldAdd = true
                                }

                        }
                        if (shouldAdd) classNode.methods.add(createDecryptMethod(randomString, random))
                    }
            }
        }.get()
        Logger.info("    Encrypted $count strings")
    }

    private fun createDecryptMethod(methodName: String, decryptValue: Int): MethodNode = method(
        Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC + Opcodes.ACC_BRIDGE,
        methodName,
        "(Ljava/lang/String;)Ljava/lang/String;",
        null,
        null
    ) {
        InsnList {
            val labelB = Label()
            val labelC = Label()

            //A:
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V")
            ASTORE(1)
            ICONST_0
            ISTORE(2)
            GOTO(labelC)

            //B:
            LABEL(labelB)
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/lang/StringBuilder", Opcodes.INTEGER), 0)
            ALOAD(1)
            ALOAD(0)
            ILOAD(2)
            INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C")
            LDC(decryptValue)
            IXOR
            I2C
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;")
            POP
            IINC(2, 1)

            //C:
            LABEL(labelC)
            FRAME(Opcodes.F_SAME, 0, null, 0)
            ILOAD(2)
            ALOAD(0)
            INVOKEVIRTUAL("java/lang/String", "length", "()I")
            IF_ICMPLT(labelB)
            ALOAD(1)
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
            ARETURN
        }
        Maxs(3, 3)
    }

    private fun xor(string: String, xor: Int): String {
        val stringBuilder = StringBuilder()
        for (element in string) {
            stringBuilder.append((element.code xor xor).toChar())
        }
        return stringBuilder.toString()
    }

}