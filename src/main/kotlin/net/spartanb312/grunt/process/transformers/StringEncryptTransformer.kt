package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

object StringEncryptTransformer : Transformer("StringEncrypt") {

    private val times by value("Intensity", 1)
    private val exclusion by value("Exclusions", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting strings")
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
                            if (!methodNode.isAbstract) {
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
                        }
                        if (shouldAdd) classNode.methods.add(createDecryptFast(randomString, random))
                    }
            }
        }.get()
        Logger.info("    Encrypted $count strings")
    }

    private fun createDecryptFast(methodName: String, decryptValue: Int): MethodNode {
        val firstLabel = Label()
        val secondLabel = Label()
        return MethodNode(
            Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC + Opcodes.ACC_BRIDGE, methodName,
            "(Ljava/lang/String;)Ljava/lang/String;",
            null,
            null
        ).apply {
            visitCode()
            visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder")
            visitInsn(Opcodes.DUP)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
            visitVarInsn(Opcodes.ASTORE, 1)
            visitInsn(Opcodes.ICONST_0)
            visitVarInsn(Opcodes.ISTORE, 2)
            visitJumpInsn(Opcodes.GOTO, firstLabel)

            visitLabel(secondLabel)
            visitFrame(Opcodes.F_APPEND, 2, arrayOf("java/lang/StringBuilder", Opcodes.INTEGER), 0, null)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitVarInsn(Opcodes.ILOAD, 2)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false)
            instructions.add(decryptValue.toInsnNode())
            visitInsn(Opcodes.IXOR)
            visitInsn(Opcodes.I2C)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                "(C)Ljava/lang/StringBuilder;",
                false
            )
            visitInsn(Opcodes.POP)
            visitIincInsn(2, 1)

            visitLabel(firstLabel)
            visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            visitVarInsn(Opcodes.ILOAD, 2)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            visitJumpInsn(Opcodes.IF_ICMPLT, secondLabel)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "toString",
                "()Ljava/lang/String;",
                false
            )
            visitInsn(Opcodes.ARETURN)
            visitMaxs(3, 3)
            visitEnd()
        }
    }

    private fun xor(string: String, xor: Int): String {
        val stringBuilder = StringBuilder()
        for (element in string) {
            stringBuilder.append((element.toInt() xor xor).toChar())
        }
        return stringBuilder.toString()
    }

}