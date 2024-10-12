package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.genesis.kotlin.*
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
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
                    "java/lang/Object"
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
                            LABEL(L["label0"])
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
                                }
                                RETURN
                            } else {
                                TRYCATCH(L["label0"], L["label1"], L["label2"], "java/lang/Exception")
                                LABEL(L["label4"])
                                NEW("java/net/URL")
                                DUP
                                LDC(onlineURL)
                                INVOKESPECIAL("java/net/URL", "<init>", "(Ljava/lang/String;)V")
                                ASTORE(0)
                                LABEL(L["label5"])
                                NEW("java/io/BufferedReader")
                                DUP
                                NEW("java/io/InputStreamReader")
                                DUP
                                ALOAD(0)
                                INVOKEVIRTUAL("java/net/URL", "openStream", "()Ljava/io/InputStream;")
                                INVOKESPECIAL("java/io/InputStreamReader", "<init>", "(Ljava/io/InputStream;)V")
                                INVOKESPECIAL("java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V")
                                ASTORE(1)
                                LABEL(L["label6"])
                                FRAME(Opcodes.F_APPEND, 2, arrayOf("java/net/URL", "java/io/BufferedReader"), 0, null)
                                ALOAD(1)
                                INVOKEVIRTUAL("java/io/BufferedReader", "readLine", "()Ljava/lang/String;")
                                DUP
                                ASTORE(2)
                                LABEL(L["label7"])
                                IFNULL(L["label1"])
                                LABEL(L["label8"])
                                GETSTATIC(classNode, constField)
                                ALOAD(2)
                                INVOKEINTERFACE("java/util/List", "add", "(Ljava/lang/Object;)Z")
                                POP
                                GOTO(L["label6"])
                                LABEL(L["label1"])
                                FRAME(Opcodes.F_CHOP, 2, null, 0, null)
                                GOTO(L["label9"])
                                LABEL(L["label2"])
                                FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/Exception"))
                                ASTORE(0)
                                LABEL(L["label10"])
                                LABEL(L["label9"])
                                FRAME(Opcodes.F_SAME, 0, null, 0, null)
                                RETURN
                                LOCALVAR("url", "Ljava/net/URL;", null, L["label5"], L["label1"], 0)
                                LOCALVAR("in", "Ljava/io/BufferedReader;", null, L["label6"], L["label1"], 1)
                                LOCALVAR("inputLine", "Ljava/lang/String;", null, L["label7"], L["label1"], 2)
                                LOCALVAR("ignored", "Ljava/lang/Exception;", null, L["label10"], L["label9"], 0)
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
    ): MethodNode = method(
        PUBLIC + STATIC,
        name,
        "()V"
    ) {
        INSTRUCTIONS {
            TRYCATCH(L["label0"], L["label1"], L["label2"], "java/lang/Exception")
            TRYCATCH(L["label3"], L["label4"], L["label5"], "java/lang/Exception")
            LABEL(L["label0"])
            LDC(Type.getType("Lsun/misc/Unsafe;"))
            ASTORE(1)
            LABEL(L["label6"])
            ALOAD(1)
            LDC("theUnsafe")
            INVOKEVIRTUAL("java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;")
            ASTORE(2)
            LABEL(L["label7"])
            ALOAD(2)
            ICONST_1
            INVOKEVIRTUAL("java/lang/reflect/Field", "setAccessible", "(Z)V")
            LABEL(L["label8"])
            ALOAD(2)
            ACONST_NULL
            INVOKEVIRTUAL("java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
            CHECKCAST("sun/misc/Unsafe")
            ASTORE(0)
            LABEL(L["label1"])
            GOTO(L["label3"])
            LABEL(L["label2"])
            FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/Exception"))
            ASTORE(1)
            LABEL(L["label9"])
            NEW("java/lang/Error")
            DUP
            LDC("Can't reach Unsafe")
            INVOKESPECIAL("java/lang/Error", "<init>", "(Ljava/lang/String;)V")
            ATHROW
            LABEL(L["label3"])
            FRAME(Opcodes.F_APPEND, 1, arrayOf("sun/misc/Unsafe"), 0, null)
            LDC("PROCESS_IDENTIFIER")
            INVOKESTATIC("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")
            LDC("PROCESSOR_LEVEL")
            LABEL(L["label10"])
            INVOKESTATIC("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")
            LDC("PROCESSOR_REVISION")
            LABEL(L["label11"])
            INVOKESTATIC("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")
            LDC("PROCESSOR_ARCHITECTURE")
            LABEL(L["label12"])
            INVOKESTATIC("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")
            LDC("PROCESSOR_ARCHITEW6432")
            LABEL(L["label13"])
            INVOKESTATIC("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")
            LDC("NUMBER_OF_PROCESSORS")
            LABEL(L["label14"])
            INVOKESTATIC("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")
            LDC("COMPUTERNAME")
            LABEL(L["label15"])
            INVOKESTATIC("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKEDYNAMIC(
                "makeConcatWithConstants",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                H_INVOKESTATIC(
                    "java/lang/invoke/StringConcatFactory",
                    "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"
                ),
                "\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
            )
            ASTORE(2)
            LABEL(L["label16"])
            LDC("AES")
            ASTORE(3)
            LABEL(L["label17"])
            LDC(key)
            ASTORE(4)
            LABEL(L["label18"])
            ALOAD(3)
            INVOKESTATIC("javax/crypto/Cipher", "getInstance", "(Ljava/lang/String;)Ljavax/crypto/Cipher;")
            ASTORE(5)
            LABEL(L["label19"])
            NEW("javax/crypto/spec/SecretKeySpec")
            DUP
            ALOAD(4)
            GETSTATIC("java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;")
            INVOKEVIRTUAL("java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B")
            ALOAD(3)
            INVOKESPECIAL("javax/crypto/spec/SecretKeySpec", "<init>", "([BLjava/lang/String;)V")
            ASTORE(6)
            LABEL(L["label20"])
            ALOAD(5)
            ICONST_1
            ALOAD(6)
            INVOKEVIRTUAL("javax/crypto/Cipher", "init", "(ILjava/security/Key;)V")
            LABEL(L["label21"])
            ALOAD(5)
            ALOAD(2)
            GETSTATIC("java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;")
            INVOKEVIRTUAL("java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B")
            INVOKEVIRTUAL("javax/crypto/Cipher", "doFinal", "([B)[B")
            ASTORE(7)
            LABEL(L["label22"])
            NEW("java/lang/String")
            DUP
            INVOKESTATIC("java/util/Base64", "getEncoder", "()Ljava/util/Base64\$Encoder;")
            ALOAD(7)
            INVOKEVIRTUAL("java/util/Base64\$Encoder", "encode", "([B)[B")
            INVOKESPECIAL("java/lang/String", "<init>", "([B)V")
            LDC("/")
            LDC("s")
            LABEL(L["label23"])
            INVOKEVIRTUAL(
                "java/lang/String",
                "replace",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"
            )
            LDC("=")
            LDC("e")
            LABEL(L["label24"])
            INVOKEVIRTUAL(
                "java/lang/String",
                "replace",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"
            )
            LDC("+")
            LDC("p")
            LABEL(L["label25"])
            INVOKEVIRTUAL(
                "java/lang/String",
                "replace",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"
            )
            ASTORE(1)
            LABEL(L["label4"])
            GOTO(L["label26"])
            LABEL(L["label5"])
            FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/Exception"))
            ASTORE(2)
            LABEL(L["label27"])
            LDC("Unknown HWID")
            ASTORE(1)
            LABEL(L["label28"])
            if (showHWIDWhenFailed) {
                NEW(frameUtil)
                DUP
                ALOAD(1)
                INVOKESPECIAL(frameUtil, "<init>", "(Ljava/lang/String;)V")
                ICONST_0
                INVOKEVIRTUAL(frameUtil, "setVisible", "(Z)V")
            }
            ALOAD(0)
            LCONST_0
            LCONST_0
            INVOKEVIRTUAL("sun/misc/Unsafe", "putAddress", "(JJ)V")
            LABEL(L["label26"])
            FRAME(Opcodes.F_APPEND, 1, arrayOf("java/lang/String"), 0, null)
            GETSTATIC(fieldOwner, fieldName, "Ljava/util/List;")
            ALOAD(1)
            INVOKEINTERFACE("java/util/List", "contains", "(Ljava/lang/Object;)Z")
            IFEQ(L["label29"])
            ICONST_1
            GOTO(L["label30"])
            LABEL(L["label29"])
            FRAME(Opcodes.F_SAME, 0, null, 0, null)
            ICONST_0
            LABEL(L["label30"])
            FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf(Opcodes.INTEGER))
            ISTORE(2)
            LABEL(L["label31"])
            ILOAD(2)
            ICONST_1
            IADD
            ICONST_2
            IF_ICMPGE(L["label32"])
            LABEL(L["label33"])
            if (showHWIDWhenFailed) {
                NEW(frameUtil)
                DUP
                ALOAD(1)
                INVOKESPECIAL(frameUtil, "<init>", "(Ljava/lang/String;)V")
                ICONST_0
                INVOKEVIRTUAL(frameUtil, "setVisible", "(Z)V")
            }
            ALOAD(0)
            LCONST_0
            LCONST_0
            INVOKEVIRTUAL("sun/misc/Unsafe", "putAddress", "(JJ)V")
            LABEL(L["label32"])
            FRAME(Opcodes.F_APPEND, 1, arrayOf(Opcodes.INTEGER), 0, null)
            RETURN
            LABEL(L["label34"])
            LOCALVAR(
                "unsafeClass",
                "Ljava/lang/Class;",
                "Ljava/lang/Class<Lsun/misc/Unsafe;>;",
                L["label6"],
                L["label1"],
                1
            )
            LOCALVAR("unsafeField", "Ljava/lang/reflect/Field;", null, L["label7"], L["label1"], 2)
            LOCALVAR("theUnsafe", "Lsun/misc/Unsafe;", null, L["label1"], L["label2"], 0)
            LOCALVAR("ignored", "Ljava/lang/Exception;", null, L["label9"], L["label3"], 1)
            LOCALVAR("raw", "Ljava/lang/String;", null, L["label16"], L["label4"], 2)
            LOCALVAR("aes", "Ljava/lang/String;", null, L["label17"], L["label4"], 3)
            LOCALVAR("key", "Ljava/lang/String;", null, L["label18"], L["label4"], 4)
            LOCALVAR("cipher", "Ljavax/crypto/Cipher;", null, L["label19"], L["label4"], 5)
            LOCALVAR("secretKeySpec", "Ljavax/crypto/spec/SecretKeySpec;", null, L["label20"], L["label4"], 6)
            LOCALVAR("result", "[B", null, L["label22"], L["label4"], 7)
            LOCALVAR("hardwareID", "Ljava/lang/String;", null, L["label4"], L["label5"], 1)
            LOCALVAR("ignored", "Ljava/lang/Exception;", null, L["label27"], L["label26"], 2)
            LOCALVAR("theUnsafe", "Lsun/misc/Unsafe;", null, L["label3"], L["label34"], 0)
            LOCALVAR("hardwareID", "Ljava/lang/String;", null, L["label28"], L["label34"], 1)
            LOCALVAR("flag", "I", null, L["label31"], L["label34"], 2)
        }
        MAXS(7, 8)
    }

    private fun createFrameUtil(name: String): ClassNode = clazz(
        PUBLIC + SUPER,
        name,
        "javax/swing/JFrame",
        null,
        null
    ) {
        +method(
            PUBLIC,
            "<init>",
            "(Ljava/lang/String;)V"
        ) {
            INSTRUCTIONS {
                LABEL(L["label0"])
                ALOAD(0)
                INVOKESPECIAL("javax/swing/JFrame", "<init>", "()V")
                LABEL(L["label1"])
                ALOAD(0)
                LDC("Authentication failed")
                INVOKEVIRTUAL(name, "setTitle", "(Ljava/lang/String;)V")
                LABEL(L["label2"])
                ALOAD(0)
                ICONST_2
                INVOKEVIRTUAL(name, "setDefaultCloseOperation", "(I)V")
                LABEL(L["label3"])
                ALOAD(0)
                ACONST_NULL
                INVOKEVIRTUAL(name, "setLocationRelativeTo", "(Ljava/awt/Component;)V")
                LABEL(L["label4"])
                ALOAD(1)
                INVOKESTATIC(name, "copyToClipboard", "(Ljava/lang/String;)V")
                LABEL(L["label5"])
                ALOAD(1)
                INVOKEDYNAMIC(
                    "makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;",
                    H_INVOKESTATIC(
                        "java/lang/invoke/StringConcatFactory",
                        "makeConcatWithConstants",
                        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"
                    ),
                    "You are not allowed to use this application\nHWID: \u0001\n(Copied to clipboard)"
                )
                ASTORE(2)
                LABEL(L["label6"])
                ALOAD(0)
                ALOAD(2)
                LDC("Authentication failed")
                ICONST_M1
                LDC("OptionPane.warningIcon")
                LABEL(L["label7"])
                INVOKESTATIC("javax/swing/UIManager", "getIcon", "(Ljava/lang/Object;)Ljavax/swing/Icon;")
                LABEL(L["label8"])
                INVOKESTATIC(
                    "javax/swing/JOptionPane",
                    "showMessageDialog",
                    "(Ljava/awt/Component;Ljava/lang/Object;Ljava/lang/String;ILjavax/swing/Icon;)V"
                )
                LABEL(L["label9"])
                RETURN
                LABEL(L["label10"])
                LOCALVAR("this", "L$name;", null, L["label0"], L["label10"], 0)
                LOCALVAR("hwid", "Ljava/lang/String;", null, L["label0"], L["label10"], 1)
                LOCALVAR("message", "Ljava/lang/String;", null, L["label6"], L["label10"], 2)
            }
            MAXS(5, 3)
        }
        +method(
            PUBLIC + STATIC,
            "copyToClipboard",
            "(Ljava/lang/String;)V"
        ) {
            INSTRUCTIONS {
                LABEL(L["label0"])
                NEW("java/awt/datatransfer/StringSelection")
                DUP
                ALOAD(0)
                INVOKESPECIAL("java/awt/datatransfer/StringSelection", "<init>", "(Ljava/lang/String;)V")
                ASTORE(1)
                LABEL(L["label1"])
                INVOKESTATIC("java/awt/Toolkit", "getDefaultToolkit", "()Ljava/awt/Toolkit;")
                INVOKEVIRTUAL("java/awt/Toolkit", "getSystemClipboard", "()Ljava/awt/datatransfer/Clipboard;")
                ASTORE(2)
                LABEL(L["label2"])
                ALOAD(2)
                ALOAD(1)
                ALOAD(1)
                INVOKEVIRTUAL(
                    "java/awt/datatransfer/Clipboard",
                    "setContents",
                    "(Ljava/awt/datatransfer/Transferable;Ljava/awt/datatransfer/ClipboardOwner;)V"
                )
                LABEL(L["label3"])
                RETURN
                LABEL(L["label4"])
                LOCALVAR("s", "Ljava/lang/String;", null, L["label0"], L["label4"], 0)
                LOCALVAR(
                    "selection",
                    "Ljava/awt/datatransfer/StringSelection;",
                    null,
                    L["label1"],
                    L["label4"],
                    1
                )
                LOCALVAR("clipboard", "Ljava/awt/datatransfer/Clipboard;", null, L["label2"], L["label4"], 2)
            }
            MAXS(3, 3)
        }
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