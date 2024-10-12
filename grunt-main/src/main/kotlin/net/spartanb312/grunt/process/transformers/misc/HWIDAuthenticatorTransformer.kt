package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.genesis.clazz
import net.spartanb312.genesis.clinit
import net.spartanb312.genesis.extensions.*
import net.spartanb312.genesis.extensions.insn.*
import net.spartanb312.genesis.field
import net.spartanb312.genesis.method
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.NumberEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import kotlin.math.min

/**
 * Add Online/Offline HWID verification
 * Providing lightweight protection for your project
 * Last update on 2024/09/20
 */
object HWIDAuthenticatorTransformer : Transformer("HWIDAuthentication", Category.Miscellaneous) {

    private val onlineMode by setting("OnlineMode", true)
    private val offlineHWID by setting("OfflineHWID", listOf("Put HWID here (For offline mode only)"))
    private val onlineURL by setting("OnlineURL", "https://pastebin.com/XXXXX")
    private val encryptKey by setting("EncryptKey", "1186118611861186")
    private val pools by setting("CachePools", 5)
    private val showHWIDWhenFailed by setting("ShowHWIDWhenFailed", true)
    private val encryptConst by setting("EncryptConst", true)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Adding HWID verifications...")
        val pools = min(nonExcluded.size, pools).coerceAtLeast(1)
        val count = count {
            val centers = mutableListOf<Pair<ClassNode, FieldNode>>()
            val nonExcluded = nonExcluded.filter { it.name.notInList(exclusion) }
            val frameUtil = createFrameUtil(nonExcluded.random().name + "\$${getRandomString(5)}")
            if (showHWIDWhenFailed) addTrashClass(frameUtil)
            repeat(pools) {
                clazz(
                    PUBLIC + SUPER,
                    nonExcluded.random().name + "\$${getRandomString(3)}",
                    "java/lang/Object",
                    null,
                    null,
                    Java8
                ) {
                    val constField = +field(
                        PUBLIC + STATIC,
                        getRandomString(15),
                        "Ljava/util/List;",
                        "Ljava/util/List<Ljava/lang/String;>;",
                        null
                    )
                    val clinit = +clinit {
                        INSTRUCTIONS {
                            val label0 = Label()
                            LABEL(label0)
                            NEW("java/util/ArrayList")
                            DUP
                            INVOKESPECIAL("java/util/ArrayList", "<init>", "()V")
                            PUTSTATIC(classNode, constField)
                            if (!onlineMode) {
                                offlineHWID.forEach {
                                    GETSTATIC(classNode, constField)
                                    LDC(it)
                                    INVOKEINTERFACE("java/util/List", "add", "(Ljava/lang/Object;)Z")
                                    POP
                                    RETURN
                                }
                            } else {
                                val label1 = Label()
                                val label2 = Label()
                                val label4 = Label()
                                val label5 = Label()
                                val label6 = Label()
                                val label7 = Label()
                                val label8 = Label()
                                val label9 = Label()
                                val label10 = Label()
                                TRYCATCH(label0, label1, label2, "java/lang/Exception")
                                LABEL(label4)
                                NEW("java/net/URL")
                                DUP
                                LDC(onlineURL)
                                INVOKESPECIAL("java/net/URL", "<init>", "(Ljava/lang/String;)V")
                                ASTORE(0)
                                LABEL(label5)
                                NEW("java/io/BufferedReader")
                                DUP
                                NEW("java/io/InputStreamReader")
                                DUP
                                ALOAD(0)
                                INVOKEVIRTUAL("java/net/URL", "openStream", "()Ljava/io/InputStream;")
                                INVOKESPECIAL("java/io/InputStreamReader", "<init>", "(Ljava/io/InputStream;)V")
                                INVOKESPECIAL("java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V")
                                ASTORE(1)
                                LABEL(label6)
                                FRAME(Opcodes.F_APPEND, 2, arrayOf("java/net/URL", "java/io/BufferedReader"), 0, null)
                                ALOAD(1)
                                INVOKEVIRTUAL("java/io/BufferedReader", "readLine", "()Ljava/lang/String;")
                                DUP
                                ASTORE(2)
                                LABEL(label7)
                                IFNULL(label1)
                                LABEL(label8)
                                GETSTATIC(classNode, constField)
                                ALOAD(2)
                                INVOKEINTERFACE("java/util/List", "add", "(Ljava/lang/Object;)Z")
                                POP
                                GOTO(label6)
                                LABEL(label1)
                                FRAME(Opcodes.F_CHOP, 2, null, 0, null)
                                GOTO(label9)
                                LABEL(label2)
                                FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/Exception"))
                                ASTORE(0)
                                LABEL(label10)
                                LABEL(label9)
                                FRAME(Opcodes.F_SAME, 0, null, 0, null)
                                RETURN
                                LOCALVAR("url", "Ljava/net/URL;", null, label5, label1, 0)
                                LOCALVAR("in", "Ljava/io/BufferedReader;", null, label6, label1, 1)
                                LOCALVAR("inputLine", "Ljava/lang/String;", null, label7, label1, 2)
                                LOCALVAR("ignored", "Ljava/lang/Exception;", null, label10, label9, 0)
                            }
                        }
                        MAXS(5, 3)
                    }
                    if (encryptConst) {
                        StringEncryptTransformer.transformMethod(classNode, clinit)
                        NumberEncryptTransformer.transformMethod(classNode, clinit)
                    }
                    centers.add(classNode to constField)
                }
            }
            nonExcluded.asSequence()
                .filter { !it.isInterface }
                .forEach { classNode ->
                    val parent = centers.random()
                    val verifyMethodName = getRandomString(10)

                    val verifyMethod = createVerifyMethod(
                        verifyMethodName,
                        encryptKey.processKey(),
                        parent.first.name,
                        parent.second.name,
                        frameUtil.name
                    )

                    if (encryptConst) {
                        StringEncryptTransformer.transformMethod(classNode, verifyMethod)
                        NumberEncryptTransformer.transformMethod(classNode, verifyMethod)
                    }

                    var hasClinit = false
                    classNode.methods.forEach {
                        if (it.name == "<clinit>") {
                            hasClinit = true
                            it.instructions.insert(
                                MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    classNode.name,
                                    verifyMethodName,
                                    "()V",
                                    false
                                )
                            )
                        }
                    }
                    if (hasClinit) classNode.methods.add(verifyMethod)
                    else {
                        verifyMethod.name = "<clinit>"
                        verifyMethod.access = Opcodes.ACC_STATIC
                        classNode.methods.add(verifyMethod)
                    }
                    if (classNode.version < Opcodes.V1_7) classNode.version = Opcodes.V1_7
                    add()
                }
            centers.forEach { addTrashClass(it.first) }
        }.get()
        Logger.info("    Added $count HWID verifications in $pools cache pools")
    }

    private fun createVerifyMethod(
        name: String,
        key: String,
        fieldOwner: String,
        fieldName: String,
        frameUtil: String
    ): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            name,
            "()V",
            null,
            null
        ).apply {
            val label0 = Label()
            val label1 = Label()
            val label2 = Label()
            visitTryCatchBlock(label0, label1, label2, "java/lang/Exception")
            val label3 = Label()
            val label4 = Label()
            val label5 = Label()
            visitTryCatchBlock(label3, label4, label5, "java/lang/Exception")
            visitLabel(label0)
            visitLdcInsn(Type.getType("Lsun/misc/Unsafe;"))
            visitVarInsn(Opcodes.ASTORE, 1)
            val label6 = Label()
            visitLabel(label6)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitLdcInsn("theUnsafe")
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Class",
                "getDeclaredField",
                "(Ljava/lang/String;)Ljava/lang/reflect/Field;",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 2)
            val label7 = Label()
            visitLabel(label7)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitInsn(Opcodes.ICONST_1)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false)
            val label8 = Label()
            visitLabel(label8)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitInsn(Opcodes.ACONST_NULL)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/reflect/Field",
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                false
            )
            visitTypeInsn(Opcodes.CHECKCAST, "sun/misc/Unsafe")
            visitVarInsn(Opcodes.ASTORE, 0)
            visitLabel(label1)
            visitJumpInsn(Opcodes.GOTO, label3)
            visitLabel(label2)
            visitFrame(Opcodes.F_SAME1, 0, null, 1, arrayOf<Any>("java/lang/Exception"))
            visitVarInsn(Opcodes.ASTORE, 1)
            val label9 = Label()
            visitLabel(label9)
            visitTypeInsn(Opcodes.NEW, "java/lang/Error")
            visitInsn(Opcodes.DUP)
            visitLdcInsn("Can't reach Unsafe")
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Error", "<init>", "(Ljava/lang/String;)V", false)
            visitInsn(Opcodes.ATHROW)
            visitLabel(label3)
            visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>("sun/misc/Unsafe"), 0, null)
            visitLdcInsn("PROCESS_IDENTIFIER")
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getenv",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitLdcInsn("PROCESSOR_LEVEL")
            val label10 = Label()
            visitLabel(label10)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getenv",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitLdcInsn("PROCESSOR_REVISION")
            val label11 = Label()
            visitLabel(label11)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getenv",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitLdcInsn("PROCESSOR_ARCHITECTURE")
            val label12 = Label()
            visitLabel(label12)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getenv",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitLdcInsn("PROCESSOR_ARCHITEW6432")
            val label13 = Label()
            visitLabel(label13)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getenv",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitLdcInsn("NUMBER_OF_PROCESSORS")
            val label14 = Label()
            visitLabel(label14)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getenv",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitLdcInsn("COMPUTERNAME")
            val label15 = Label()
            visitLabel(label15)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getenv",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitInvokeDynamicInsn(
                "makeConcatWithConstants",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/StringConcatFactory",
                    "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                    false
                ),
                "\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
            )
            visitVarInsn(Opcodes.ASTORE, 2)
            val label16 = Label()
            visitLabel(label16)
            visitLdcInsn("AES")
            visitVarInsn(Opcodes.ASTORE, 3)
            val label17 = Label()
            visitLabel(label17)
            visitLdcInsn(key)
            visitVarInsn(Opcodes.ASTORE, 4)
            val label18 = Label()
            visitLabel(label18)
            visitVarInsn(Opcodes.ALOAD, 3)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "javax/crypto/Cipher",
                "getInstance",
                "(Ljava/lang/String;)Ljavax/crypto/Cipher;",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 5)
            val label19 = Label()
            visitLabel(label19)
            visitTypeInsn(Opcodes.NEW, "javax/crypto/spec/SecretKeySpec")
            visitInsn(Opcodes.DUP)
            visitVarInsn(Opcodes.ALOAD, 4)
            visitFieldInsn(
                Opcodes.GETSTATIC,
                "java/nio/charset/StandardCharsets",
                "UTF_8",
                "Ljava/nio/charset/Charset;"
            )
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "getBytes",
                "(Ljava/nio/charset/Charset;)[B",
                false
            )
            visitVarInsn(Opcodes.ALOAD, 3)
            visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "javax/crypto/spec/SecretKeySpec",
                "<init>",
                "([BLjava/lang/String;)V",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 6)
            val label20 = Label()
            visitLabel(label20)
            visitVarInsn(Opcodes.ALOAD, 5)
            visitInsn(Opcodes.ICONST_1)
            visitVarInsn(Opcodes.ALOAD, 6)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "javax/crypto/Cipher",
                "init",
                "(ILjava/security/Key;)V",
                false
            )
            val label21 = Label()
            visitLabel(label21)
            visitVarInsn(Opcodes.ALOAD, 5)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitFieldInsn(
                Opcodes.GETSTATIC,
                "java/nio/charset/StandardCharsets",
                "UTF_8",
                "Ljava/nio/charset/Charset;"
            )
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "getBytes",
                "(Ljava/nio/charset/Charset;)[B",
                false
            )
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/crypto/Cipher", "doFinal", "([B)[B", false)
            visitVarInsn(Opcodes.ASTORE, 7)
            val label22 = Label()
            visitLabel(label22)
            visitTypeInsn(Opcodes.NEW, "java/lang/String")
            visitInsn(Opcodes.DUP)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/util/Base64",
                "getEncoder",
                "()Ljava/util/Base64\$Encoder;",
                false
            )
            visitVarInsn(Opcodes.ALOAD, 7)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Base64\$Encoder", "encode", "([B)[B", false)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([B)V", false)
            visitLdcInsn("/")
            visitLdcInsn("s")
            val label23 = Label()
            visitLabel(label23)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "replace",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
                false
            )
            visitLdcInsn("=")
            visitLdcInsn("e")
            val label24 = Label()
            visitLabel(label24)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "replace",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
                false
            )
            visitLdcInsn("+")
            visitLdcInsn("p")
            val label25 = Label()
            visitLabel(label25)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "replace",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 1)
            visitLabel(label4)
            val label26 = Label()
            visitJumpInsn(Opcodes.GOTO, label26)
            visitLabel(label5)
            visitFrame(Opcodes.F_SAME1, 0, null, 1, arrayOf<Any>("java/lang/Exception"))
            visitVarInsn(Opcodes.ASTORE, 2)
            val label27 = Label()
            visitLabel(label27)
            visitLdcInsn("Unknown HWID")
            visitVarInsn(Opcodes.ASTORE, 1)
            val label28 = Label()
            visitLabel(label28)

            if (showHWIDWhenFailed) {
                visitTypeInsn(Opcodes.NEW, frameUtil)
                visitInsn(Opcodes.DUP)
                visitVarInsn(Opcodes.ALOAD, 1)
                visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    frameUtil,
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false
                )
                visitInsn(Opcodes.ICONST_0)
                visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    frameUtil,
                    "setVisible",
                    "(Z)V",
                    false
                )
            }

            visitVarInsn(Opcodes.ALOAD, 0)
            visitInsn(Opcodes.LCONST_0)
            visitInsn(Opcodes.LCONST_0)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "sun/misc/Unsafe", "putAddress", "(JJ)V", false)
            visitLabel(label26)

            visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>("java/lang/String"), 0, null)
            visitFieldInsn(
                Opcodes.GETSTATIC,
                fieldOwner,
                fieldName,
                "Ljava/util/List;"
            )
            visitVarInsn(Opcodes.ALOAD, 1)
            visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "contains", "(Ljava/lang/Object;)Z", true)
            val label29 = Label()
            visitJumpInsn(Opcodes.IFEQ, label29)
            visitInsn(Opcodes.ICONST_1)
            val label30 = Label()
            visitJumpInsn(Opcodes.GOTO, label30)
            visitLabel(label29)
            visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            visitInsn(Opcodes.ICONST_0)
            visitLabel(label30)
            visitFrame(Opcodes.F_SAME1, 0, null, 1, arrayOf<Any>(Opcodes.INTEGER))
            visitVarInsn(Opcodes.ISTORE, 2)
            val label31 = Label()
            visitLabel(label31)
            visitVarInsn(Opcodes.ILOAD, 2)
            visitInsn(Opcodes.ICONST_1)
            visitInsn(Opcodes.IADD)
            visitInsn(Opcodes.ICONST_2)
            val label32 = Label()
            visitJumpInsn(Opcodes.IF_ICMPGE, label32)
            val label33 = Label()
            visitLabel(label33)

            if (showHWIDWhenFailed) {
                visitTypeInsn(Opcodes.NEW, frameUtil)
                visitInsn(Opcodes.DUP)
                visitVarInsn(Opcodes.ALOAD, 1)
                visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    frameUtil,
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false
                )
                visitInsn(Opcodes.ICONST_0)
                visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    frameUtil,
                    "setVisible",
                    "(Z)V",
                    false
                )
            }

            visitVarInsn(Opcodes.ALOAD, 0)
            visitInsn(Opcodes.LCONST_0)
            visitInsn(Opcodes.LCONST_0)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "sun/misc/Unsafe", "putAddress", "(JJ)V", false)

            visitLabel(label32)
            visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>(Opcodes.INTEGER), 0, null)
            visitInsn(Opcodes.RETURN)
            val label34 = Label()
            visitLabel(label34)
            visitLocalVariable(
                "unsafeClass",
                "Ljava/lang/Class;",
                "Ljava/lang/Class<Lsun/misc/Unsafe;>;",
                label6,
                label1,
                1
            )
            visitLocalVariable("unsafeField", "Ljava/lang/reflect/Field;", null, label7, label1, 2)
            visitLocalVariable("theUnsafe", "Lsun/misc/Unsafe;", null, label1, label2, 0)
            visitLocalVariable("ignored", "Ljava/lang/Exception;", null, label9, label3, 1)
            visitLocalVariable("raw", "Ljava/lang/String;", null, label16, label4, 2)
            visitLocalVariable("aes", "Ljava/lang/String;", null, label17, label4, 3)
            visitLocalVariable("key", "Ljava/lang/String;", null, label18, label4, 4)
            visitLocalVariable("cipher", "Ljavax/crypto/Cipher;", null, label19, label4, 5)
            visitLocalVariable(
                "secretKeySpec",
                "Ljavax/crypto/spec/SecretKeySpec;",
                null,
                label20,
                label4,
                6
            )
            visitLocalVariable("result", "[B", null, label22, label4, 7)
            visitLocalVariable("hardwareID", "Ljava/lang/String;", null, label4, label5, 1)
            visitLocalVariable("ignored", "Ljava/lang/Exception;", null, label27, label26, 2)
            visitLocalVariable("theUnsafe", "Lsun/misc/Unsafe;", null, label3, label34, 0)
            visitLocalVariable("hardwareID", "Ljava/lang/String;", null, label28, label34, 1)
            visitLocalVariable("flag", "I", null, label31, label34, 2)
            visitMaxs(7, 8)
        }
    }

    private fun createFrameUtil(name: String): ClassNode {
        val classNode = ClassNode()
        classNode.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
            name,
            null,
            "javax/swing/JFrame",
            null
        )
        val init = MethodNode(
            Opcodes.ACC_PUBLIC,
            "<init>",
            "(Ljava/lang/String;)V",
            null,
            null
        ).apply {
            val label0 = Label()
            visitLabel(label0)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "javax/swing/JFrame", "<init>", "()V", false)
            val label1 = Label()
            visitLabel(label1)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitLdcInsn("Authentication failed")
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "setTitle",
                "(Ljava/lang/String;)V",
                false
            )
            val label2 = Label()
            visitLabel(label2)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitInsn(Opcodes.ICONST_2)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "setDefaultCloseOperation",
                "(I)V",
                false
            )
            val label3 = Label()
            visitLabel(label3)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitInsn(Opcodes.ACONST_NULL)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "setLocationRelativeTo",
                "(Ljava/awt/Component;)V",
                false
            )
            val label4 = Label()
            visitLabel(label4)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                name,
                "copyToClipboard",
                "(Ljava/lang/String;)V",
                false
            )
            val label5 = Label()
            visitLabel(label5)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitInvokeDynamicInsn(
                "makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;", Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/StringConcatFactory",
                    "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                    false
                ),
                "You are not allowed to use this application\nHWID: \u0001\n(Copied to clipboard)"
            )
            visitVarInsn(Opcodes.ASTORE, 2)
            val label6 = Label()
            visitLabel(label6)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitLdcInsn("Authentication failed")
            visitInsn(Opcodes.ICONST_M1)
            visitLdcInsn("OptionPane.warningIcon")
            val label7 = Label()
            visitLabel(label7)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "javax/swing/UIManager",
                "getIcon",
                "(Ljava/lang/Object;)Ljavax/swing/Icon;",
                false
            )
            val label8 = Label()
            visitLabel(label8)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "javax/swing/JOptionPane",
                "showMessageDialog",
                "(Ljava/awt/Component;Ljava/lang/Object;Ljava/lang/String;ILjavax/swing/Icon;)V",
                false
            )
            val label9 = Label()
            visitLabel(label9)
            visitInsn(Opcodes.RETURN)
            val label10 = Label()
            visitLabel(label10)
            visitLocalVariable(
                "this",
                "L$name;",
                null,
                label0,
                label10,
                0
            )
            visitLocalVariable("hwid", "Ljava/lang/String;", null, label0, label10, 1)
            visitLocalVariable("message", "Ljava/lang/String;", null, label6, label10, 2)
            visitMaxs(5, 3)
        }
        val copy = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "copyToClipboard",
            "(Ljava/lang/String;)V",
            null,
            null
        ).apply {
            val label0 = Label()
            visitLabel(label0)
            visitTypeInsn(Opcodes.NEW, "java/awt/datatransfer/StringSelection")
            visitInsn(Opcodes.DUP)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/awt/datatransfer/StringSelection",
                "<init>",
                "(Ljava/lang/String;)V",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 1)
            val label1 = Label()
            visitLabel(label1)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/awt/Toolkit",
                "getDefaultToolkit",
                "()Ljava/awt/Toolkit;",
                false
            )
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/awt/Toolkit",
                "getSystemClipboard",
                "()Ljava/awt/datatransfer/Clipboard;",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 2)
            val label2 = Label()
            visitLabel(label2)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/awt/datatransfer/Clipboard",
                "setContents",
                "(Ljava/awt/datatransfer/Transferable;Ljava/awt/datatransfer/ClipboardOwner;)V",
                false
            )
            val label3 = Label()
            visitLabel(label3)
            visitInsn(Opcodes.RETURN)
            val label4 = Label()
            visitLabel(label4)
            visitLocalVariable("s", "Ljava/lang/String;", null, label0, label4, 0)
            visitLocalVariable(
                "selection",
                "Ljava/awt/datatransfer/StringSelection;",
                null,
                label1,
                label4,
                1
            )
            visitLocalVariable("clipboard", "Ljava/awt/datatransfer/Clipboard;", null, label2, label4, 2)
            visitMaxs(3, 3)
        }
        classNode.methods.add(init)
        classNode.methods.add(copy)
        return classNode
    }

    private fun String.processKey(): String = when {
        this.length < 16 -> {
            this + "1186118611861186".substring(this.length)
        }

        this.length > 16 -> {
            this.substring(0, 8) + this.substring(this.length - 8, this.length)
        }

        else -> this
    }

}