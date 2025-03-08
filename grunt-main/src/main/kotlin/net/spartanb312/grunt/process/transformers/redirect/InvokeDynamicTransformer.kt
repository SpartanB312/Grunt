package net.spartanb312.grunt.process.transformers.redirect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.spartanb312.genesis.kotlin.annotation
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.annotation.DISABLE_INVOKEDYNAMIC
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.Configs.isExcluded
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.process.transformers.flow.process.ArithmeticExpr
import net.spartanb312.grunt.process.transformers.flow.process.JunkCode
import net.spartanb312.grunt.process.transformers.misc.NativeCandidateTransformer
import net.spartanb312.grunt.process.transformers.rename.LocalVariableRenameTransformer
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.extensions.hasAnnotation
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.lang.invoke.CallSite
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.random.Random

/**
 * Replace invokes to invoke dynamic
 * Last update on 24/10/16
 */
object InvokeDynamicTransformer : Transformer("InvokeDynamic", Category.Redirect) {

    private val rate by setting("ReplacePercentage", 10)
    private val heavy by setting("HeavyProtection", false)
    private val metadataClass by setting("MetadataClass", "net/spartanb312/grunt/GruntMetadata")
    private val massiveRandom by setting("MassiveRandomBlank", false)
    private val reobf by setting("Reobfuscate", true)
    private val enhancedFlow by setting("EnhancedFlowReobf", false)
    private val nativeAnnotation by setting("BSMNativeAnnotation", false)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Replacing invokes to InvokeDynamic...")
        if (ControlflowTransformer.enabled) JunkCode.refresh(this@transform)
        // Generate metadata
        val metadata = mutableMapOf<ClassNode, MetaData>()
        if (heavy) {
            nonExcluded.forEach { classNode ->
                val allMethods = mutableSetOf<MethodNode>()
                fun process(classNode: ClassNode) {
                    allMethods.addAll(classNode.methods)
                    val parents = mutableSetOf<String>()
                    if (classNode.superName != null) parents.add(classNode.superName)
                    if (classNode.interfaces?.isNotEmpty() == true) parents.addAll(classNode.interfaces)
                    parents.forEach { getClassNode(it)?.let { p -> process(p) } }
                }
                process(classNode)
                if (allMethods.isNotEmpty()) {
                    val data = MetaData(
                        IntArray(allMethods.size),
                        Array(allMethods.size) { "" },
                        Array(allMethods.size) { "d1" },
                        Array(allMethods.size) { "d2" },
                        IntArray(allMethods.size),
                        IntArray(allMethods.size)
                    )
                    val existedMagic2 = mutableSetOf<Int>()
                    allMethods.forEachIndexed { index, methodNode ->
                        val magic1 = Random.nextInt(0x8, 0x800)
                        // Generate unique magic2
                        var magic2: Int
                        while (true) {
                            magic2 = Random.nextInt(0, 59557)
                            if (!existedMagic2.contains(magic2)) {
                                existedMagic2.add(magic2)
                                break
                            }
                        }
                        val data1 = 59557 * Random.nextInt(1186) + magic2
                        val encryptedData1 = encrypt(data1.toString(), magic2)
                        val data2 = methodNode.name + "<>" + methodNode.desc
                        val encryptedData2 = encrypt(data2, magic1)
                        data.d1[index] = data1
                        data.d2[index] = data2
                        data.ed1[index] = encryptedData1
                        data.ed2[index] = encryptedData2
                        data.m1[index] = magic1
                        data.m2[index] = magic2
                    }
                    metadata[classNode] = data
                    val annotation = annotation("L$metadataClass;") {
                        this["mv"] = 100
                        this["d1"] = data.ed1.toList()
                        this["d2"] = data.ed2.toList()
                    }
                    classNode.visibleAnnotations = classNode.visibleAnnotations ?: mutableListOf()
                    classNode.visibleAnnotations.add(annotation)
                }
            }
            addClass(
                clazz(
                    access = PUBLIC + ABSTRACT + INTERFACE + ANNOTATION,
                    name = metadataClass,
                    superName = "java/lang/Object",
                    interfaces = listOf("java/lang/annotation/Annotation"),
                    signature = null,
                    version = Java8
                ) {
                    +annotation("Ljava/lang/annotation/Retention;") {
                        ENUM("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME")
                    }
                    +method(
                        access = PUBLIC + ABSTRACT,
                        name = "mv",
                        desc = "()I",
                        signature = null,
                        exceptions = null
                    ) {
                        ANNOTATIONDEFAULT {
                            this[null] = 0
                        }
                    }
                    +method(
                        access = PUBLIC + ABSTRACT,
                        name = "d1",
                        desc = "()[Ljava/lang/String;",
                        signature = null,
                        exceptions = null
                    ) {
                        ANNOTATIONDEFAULT {
                            ARRAY(null)
                        }
                    }
                    +method(
                        access = PUBLIC + ABSTRACT,
                        name = "d2",
                        desc = "()[Ljava/lang/String;",
                        signature = null,
                        exceptions = null
                    ) {
                        ANNOTATIONDEFAULT {
                            ARRAY(null)
                        }
                    }
                })
        }
        val count = count {
            val mutex = Mutex()
            val addedMethods = mutableListOf<Pair<ClassNode, List<MethodNode>>>()
            runBlocking {
                classes.filter {
                    val map = getMapping(it.value.name)
                    !it.value.isInterface && it.value.version >= Opcodes.V1_7
                            && !map.isExcluded && map.notInList(exclusion)
                            && !it.value.hasAnnotation(DISABLE_INVOKEDYNAMIC)
                }.values.forEach { classNode ->
                    suspend fun job() {
                        val bsmName1 = if (massiveRandom) massiveBlankString else getRandomString(16)
                        val bsmName2 = bsmName1.substring(1, bsmName1.length - 1)
                        val decryptName = if (massiveRandom) massiveBlankString else getRandomString(16)
                        val decryptKey = Random.nextInt()
                        if (shouldApply(classNode, bsmName1, bsmName2, decryptKey, metadata)) {
                            val decrypt = createDecryptMethod(decryptName, decryptKey)
                            val decrypt2 = if (heavy) createHeavyDecryptMethod(decryptName) else null
                            val bsm = createBootstrap(classNode.name, bsmName1, decryptName)
                            val bsm2 = if (heavy) createHeavyBootstrap(
                                classNode.name,
                                bsmName2,
                                decryptName,
                                decryptKey
                            ) else null
                            if (reobf) mutex.withLock {
                                val methodsAdded = mutableListOf<MethodNode>()
                                methodsAdded.add(decrypt)
                                methodsAdded.add(bsm)
                                if (heavy) {
                                    methodsAdded.add(decrypt2!!)
                                    methodsAdded.add(bsm2!!)
                                }
                                addedMethods.add(classNode to methodsAdded)
                            }
                            classNode.methods.add(decrypt)
                            classNode.methods.add(bsm)
                            if (heavy) {
                                classNode.methods.add(decrypt2!!)
                                classNode.methods.add(bsm2!!)
                            }
                        }
                    }
                    if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                }
            }
            if (reobf) {
                val preState = ControlflowTransformer.arithmeticExpr
                if (!enhancedFlow) ControlflowTransformer.arithmeticExpr = false
                if (ControlflowTransformer.enabled) ArithmeticExpr.refresh(this@transform)
                runBlocking {
                    addedMethods.forEach { (clazz, methods) ->
                        launch(Dispatchers.Default) {
                            if (ControlflowTransformer.enabled) methods.forEach {
                                ControlflowTransformer.transformMethod(clazz, it, true)
                            }
                            if (LocalVariableRenameTransformer.enabled) methods.forEach {
                                LocalVariableRenameTransformer.transformMethod(clazz, it)
                            }
                        }
                    }
                }
                ControlflowTransformer.arithmeticExpr = preState
            }
            if (nativeAnnotation) {
                addedMethods.forEach { (_, methods) ->
                    methods.forEach { method ->
                        NativeCandidateTransformer.appendedMethods.add(method)
                        method.visitAnnotation(NativeCandidateTransformer.annotation, false)
                    }
                }
            }
        }.get()
        Logger.info("    Replaced $count invokes")
    }

    private fun Counter.shouldApply(
        classNode: ClassNode,
        bsm1: String,
        bsm2: String,
        decryptKey: Int,
        metadataMap: Map<ClassNode, MetaData>
    ): Boolean {
        var shouldApply = false
        classNode.methods
            .filter { !it.isAbstract && !it.isNative }
            .forEach { methodNode ->
                if (!methodNode.hasAnnotation(DISABLE_INVOKEDYNAMIC)) {
                    methodNode.instructions.filter {
                        it is MethodInsnNode && it.opcode != Opcodes.INVOKESPECIAL
                    }.forEach { insnNode ->
                        if (insnNode is MethodInsnNode && (0..99).random() < rate) {
                            val metadata = metadataMap.entries.find { (clazz, _) -> clazz.name == insnNode.owner }
                            val metadataKey = insnNode.name + "<>" + insnNode.desc
                            val index = metadata?.value?.d2?.indexOf(metadataKey) ?: -1
                            val invokeDynamicInsnNode = if (heavy && metadata != null && index >= 0) {
                                val magic1 = metadata.value.m1[index]
                                val magic2 = metadata.value.m2[index]
                                InvokeDynamicInsnNode(
                                    bsm2,
                                    if (insnNode.opcode == Opcodes.INVOKESTATIC) insnNode.desc
                                    else insnNode.desc.replace("(", "(Ljava/lang/Object;"),
                                    H_INVOKESTATIC(
                                        classNode.name,
                                        bsm2,
                                        MethodType.methodType(
                                            CallSite::class.java,
                                            MethodHandles.Lookup::class.java,
                                            String::class.java,
                                            MethodType::class.java,
                                            String::class.java,
                                            Integer::class.java,
                                            Integer::class.java,
                                            Integer::class.java
                                        ).toMethodDescriptorString(),
                                    ),
                                    encrypt(insnNode.owner.replace("/", "."), decryptKey),
                                    magic1,
                                    magic2,
                                    if (insnNode.opcode == Opcodes.INVOKESTATIC) 0 else 1
                                )
                            } else InvokeDynamicInsnNode(
                                bsm1,
                                if (insnNode.opcode == Opcodes.INVOKESTATIC) insnNode.desc
                                else insnNode.desc.replace("(", "(Ljava/lang/Object;"),
                                H_INVOKESTATIC(
                                    classNode.name,
                                    bsm1,
                                    MethodType.methodType(
                                        CallSite::class.java,
                                        MethodHandles.Lookup::class.java,
                                        String::class.java,
                                        MethodType::class.java,
                                        String::class.java,
                                        String::class.java,
                                        String::class.java,
                                        Integer::class.java
                                    ).toMethodDescriptorString(),
                                ),
                                encrypt(insnNode.owner.replace("/", "."), decryptKey),
                                encrypt(insnNode.name, decryptKey),
                                encrypt(insnNode.desc, decryptKey),
                                if (insnNode.opcode == Opcodes.INVOKESTATIC) 0 else 1
                            )
                            methodNode.instructions.insertBefore(insnNode, invokeDynamicInsnNode)
                            methodNode.instructions.remove(insnNode)
                            shouldApply = true
                            add()
                        }
                    }
                }
            }
        return shouldApply
    }

    private fun createBootstrap(className: String, methodName: String, decryptName: String) =
        method(
            PUBLIC + STATIC + SYNTHETIC + BRIDGE,
            methodName,
            MethodType.methodType(
                CallSite::class.java,
                MethodHandles.Lookup::class.java,
                String::class.java,
                MethodType::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Integer::class.java
            ).toMethodDescriptorString()
        ) {
            INSTRUCTIONS {
                TRYCATCH(L["labelA"], L["labelB"], L["labelC"], "java/lang/Exception")
                TRYCATCH(L["labelD"], L["labelE"], L["labelC"], "java/lang/Exception")
                ALOAD(3)
                CHECKCAST("java/lang/String")
                ASTORE(7)
                ALOAD(4)
                CHECKCAST("java/lang/String")
                ASTORE(8)
                ALOAD(5)
                CHECKCAST("java/lang/String")
                ASTORE(9)
                ALOAD(6)
                CHECKCAST("java/lang/Integer")
                INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I")
                ISTORE(10)
                ALOAD(9)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                LDC_TYPE("L$className;")
                INVOKEVIRTUAL("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;")
                INVOKESTATIC(
                    "java/lang/invoke/MethodType",
                    "fromMethodDescriptorString",
                    "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;"
                )
                ASTORE(11)
                LABEL(L["labelA"])
                ILOAD(10)
                ICONST_1
                IF_ICMPNE(L["labelD"])
                NEW("java/lang/invoke/ConstantCallSite")
                DUP
                ALOAD(0)
                ALOAD(7)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
                ALOAD(8)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                ALOAD(11)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "findVirtual",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
                )
                ALOAD(2)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandle",
                    "asType",
                    "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
                )
                INVOKESPECIAL("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
                LABEL(L["labelB"])
                ARETURN
                LABEL(L["labelD"])
                FRAME(
                    Opcodes.F_FULL, 12, arrayOf(
                        "java/lang/invoke/MethodHandles\$Lookup",
                        "java/lang/String",
                        "java/lang/invoke/MethodType",
                        "java/lang/Object",
                        "java/lang/Object",
                        "java/lang/Object",
                        "java/lang/Object",
                        "java/lang/String",
                        "java/lang/String",
                        "java/lang/String",
                        Opcodes.INTEGER,
                        "java/lang/invoke/MethodType"
                    ), 0, arrayOf()
                )
                NEW("java/lang/invoke/ConstantCallSite")
                DUP
                ALOAD(0)
                ALOAD(7)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
                ALOAD(8)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
                ALOAD(11)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "findStatic",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
                )
                ALOAD(2)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandle",
                    "asType",
                    "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
                )
                INVOKESPECIAL("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
                LABEL(L["labelE"])
                ARETURN
                LABEL(L["labelC"])
                FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/Exception"))
                ASTORE(12)
                ACONST_NULL
                ARETURN
            }
            MAXS(6, 13)
        }

    private fun createHeavyDecryptMethod(methodName: String): MethodNode = method(
        access = PRIVATE + STATIC + SYNTHETIC + BRIDGE,
        name = methodName,
        desc = "(Ljava/lang/String;I)Ljava/lang/String;"
    ) {
        val label6 = Label()
        val label5 = Label()
        val label4 = Label()
        val label3 = Label()
        val label2 = Label()
        val label1 = Label()
        val label0 = Label()
        INSTRUCTIONS {
            LABEL(label0)
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
            ASTORE(2)
            LABEL(label1)
            ICONST_0
            ISTORE(3)
            LABEL(label2)
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/lang/StringBuilder", Opcodes.INTEGER), 0, null)
            ILOAD(3)
            ALOAD(0)
            INVOKEVIRTUAL("java/lang/String", "length", "()I", false)
            IF_ICMPGE(label3)
            LABEL(label4)
            ALOAD(2)
            ALOAD(0)
            ILOAD(3)
            INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C", false)
            ILOAD(1)
            IXOR
            I2C
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false)
            POP
            LABEL(label5)
            IINC(3, 1)
            GOTO(label2)
            LABEL(label3)
            FRAME(Opcodes.F_SAME, 0, null, 0, null)
            ALOAD(2)
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
            ARETURN
            LABEL(label6)
            LOCALVAR("str", "Ljava/lang/String;", null, label0, label6, 0)
            LOCALVAR("key", "I", null, label0, label6, 1)
            LOCALVAR("stringBuilder", "Ljava/lang/StringBuilder;", null, label1, label6, 2)
            LOCALVAR("n", "I", null, label2, label6, 3)
            MAXS(3, 4)
        }
    }

    private fun createHeavyBootstrap(
        className: String,
        methodName: String,
        decryptName: String,
        generalKey: Int
    ): MethodNode = method(
        access = PRIVATE + STATIC + SYNTHETIC + BRIDGE,
        name = methodName,
        desc = MethodType.methodType(
            CallSite::class.java,
            MethodHandles.Lookup::class.java,
            String::class.java,
            MethodType::class.java,
            String::class.java,
            Integer::class.java,
            Integer::class.java,
            Integer::class.java
        ).toMethodDescriptorString()
    ) {
        val label27 = Label()
        val label26 = Label()
        val label25 = Label()
        val label24 = Label()
        val label23 = Label()
        val label22 = Label()
        val label21 = Label()
        val label20 = Label()
        val label19 = Label()
        val label18 = Label()
        val label17 = Label()
        val label16 = Label()
        val label15 = Label()
        val label14 = Label()
        val label13 = Label()
        val label12 = Label()
        val label11 = Label()
        val label10 = Label()
        val label9 = Label()
        val label8 = Label()
        val label7 = Label()
        val label6 = Label()
        val label5 = Label()
        val label4 = Label()
        val label3 = Label()
        val label2 = Label()
        val label1 = Label()
        val label0 = Label()
        INSTRUCTIONS {
            TRYCATCH(label0, label1, label2, "java/lang/NumberFormatException")
            LABEL(label3)
            ALOAD(3)
            LDC(generalKey)
            INVOKESTATIC(className, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;", false)
            INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false)
            ASTORE(7)
            LABEL(label4)
            ALOAD(7)
            LDC(Type.getType("L$metadataClass;"))
            INVOKEVIRTUAL(
                "java/lang/Class",
                "getAnnotation",
                "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;",
                false
            )
            CHECKCAST(metadataClass)
            ASTORE(8)
            LABEL(label5)
            ALOAD(8)
            INVOKEINTERFACE(metadataClass, "mv", "()I", true)
            BIPUSH(100.toInt())
            IF_ICMPGE(label6)
            NEW("java/lang/Exception")
            DUP
            LDC("Outdated metadata version")
            INVOKESPECIAL("java/lang/Exception", "<init>", "(Ljava/lang/String;)V", false)
            ATHROW
            LABEL(label6)
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/lang/Class", metadataClass), 0, null)
            ALOAD(8)
            INVOKEINTERFACE(metadataClass, "d1", "()[Ljava/lang/String;", true)
            ASTORE(9)
            LABEL(label7)
            ALOAD(8)
            INVOKEINTERFACE(metadataClass, "d2", "()[Ljava/lang/String;", true)
            ASTORE(10)
            LABEL(label8)
            LDC("")
            ASTORE(11)
            LABEL(label9)
            LDC("")
            ASTORE(12)
            LABEL(label10)
            ICONST_0
            ISTORE(13)
            LABEL(label11)
            FRAME(
                Opcodes.F_FULL,
                14,
                arrayOf(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "java/lang/String",
                    "java/lang/invoke/MethodType",
                    "java/lang/String",
                    "java/lang/Integer",
                    "java/lang/Integer",
                    "java/lang/Integer",
                    "java/lang/Class",
                    metadataClass,
                    "[Ljava/lang/String;",
                    "[Ljava/lang/String;",
                    "java/lang/String",
                    "java/lang/String",
                    Opcodes.INTEGER
                ),
                0,
                arrayOf()
            )
            ILOAD(13)
            ALOAD(9)
            ARRAYLENGTH
            IF_ICMPGE(label12)
            LABEL(label0)
            ALOAD(9)
            ILOAD(13)
            AALOAD
            ALOAD(5)
            INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
            INVOKESTATIC(className, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;", false)
            INVOKESTATIC("java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false)
            ISTORE(14)
            LABEL(label1)
            GOTO(label13)
            LABEL(label2)
            FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/NumberFormatException"))
            ASTORE(15)
            LABEL(label14)
            GOTO(label15)
            LABEL(label13)
            FRAME(Opcodes.F_APPEND, 1, arrayOf(Opcodes.INTEGER), 0, null)
            ILOAD(14)
            LDC(59557.toInt())
            IREM
            ALOAD(5)
            INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
            IF_ICMPNE(label15)
            LABEL(label16)
            ALOAD(10)
            ILOAD(13)
            AALOAD
            ALOAD(4)
            INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
            INVOKESTATIC(className, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;", false)
            ASTORE(15)
            LABEL(label17)
            ALOAD(15)
            LDC("<>")
            INVOKEVIRTUAL("java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false)
            ASTORE(16)
            LABEL(label18)
            ALOAD(16)
            ICONST_0
            AALOAD
            ASTORE(11)
            LABEL(label19)
            ALOAD(16)
            ICONST_1
            AALOAD
            ASTORE(12)
            LABEL(label20)
            GOTO(label12)
            LABEL(label15)
            FRAME(Opcodes.F_CHOP, 1, null, 0, null)
            IINC(13, 1)
            GOTO(label11)
            LABEL(label12)
            FRAME(Opcodes.F_CHOP, 1, null, 0, null)
            ALOAD(11)
            INVOKEVIRTUAL("java/lang/String", "isEmpty", "()Z", false)
            IFEQ(label21)
            LABEL(label22)
            NEW("java/lang/Exception")
            DUP
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
            LDC("Can't find method in ")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            ALOAD(3)
            LDC(generalKey)
            INVOKESTATIC(className, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;", false)
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            LDC(" ")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            ALOAD(4)
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false)
            LDC(" ")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            ALOAD(5)
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false)
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
            INVOKESPECIAL("java/lang/Exception", "<init>", "(Ljava/lang/String;)V", false)
            ATHROW
            LABEL(label21)
            FRAME(Opcodes.F_SAME, 0, null, 0, null)
            ALOAD(12)
            ALOAD(7)
            INVOKEVIRTUAL("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false)
            INVOKESTATIC(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;",
                false
            )
            ASTORE(13)
            LABEL(label23)
            ALOAD(6)
            INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
            ICONST_1
            IF_ICMPNE(label24)
            ALOAD(0)
            ALOAD(7)
            ALOAD(11)
            ALOAD(13)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandles\$Lookup",
                "findVirtual",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                false
            )
            ASTORE(14)
            LABEL(label25)
            GOTO(label26)
            LABEL(label24)
            FRAME(Opcodes.F_APPEND, 1, arrayOf("java/lang/invoke/MethodType"), 0, null)
            ALOAD(0)
            ALOAD(7)
            ALOAD(11)
            ALOAD(13)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandles\$Lookup",
                "findStatic",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                false
            )
            ASTORE(14)
            LABEL(label26)
            FRAME(Opcodes.F_APPEND, 1, arrayOf("java/lang/invoke/MethodHandle"), 0, null)
            NEW("java/lang/invoke/ConstantCallSite")
            DUP
            ALOAD(14)
            ALOAD(2)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                false
            )
            INVOKESPECIAL("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false)
            ARETURN
            LABEL(label27)
            LOCALVAR("data1", "I", null, label1, label2, 14)
            LOCALVAR("e", "Ljava/lang/NumberFormatException;", null, label14, label13, 15)
            LOCALVAR("data2", "Ljava/lang/String;", null, label17, label15, 15)
            LOCALVAR("pair", "[Ljava/lang/String;", null, label18, label15, 16)
            LOCALVAR("data1", "I", null, label13, label15, 14)
            LOCALVAR("i", "I", null, label11, label12, 13)
            LOCALVAR("handle", "Ljava/lang/invoke/MethodHandle;", null, label25, label24, 14)
            LOCALVAR("lookup", "Ljava/lang/invoke/MethodHandles\$Lookup;", null, label3, label27, 0)
            LOCALVAR("ignore", "Ljava/lang/String;", null, label3, label27, 1)
            LOCALVAR("methodType", "Ljava/lang/invoke/MethodType;", null, label3, label27, 2)
            LOCALVAR("owner", "Ljava/lang/String;", null, label3, label27, 3)
            LOCALVAR("magic", "Ljava/lang/Integer;", null, label3, label27, 4)
            LOCALVAR("magic2", "Ljava/lang/Integer;", null, label3, label27, 5)
            LOCALVAR("isVirtual", "Ljava/lang/Integer;", null, label3, label27, 6)
            LOCALVAR("ownerClass", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", label4, label27, 7)
            LOCALVAR("meta", "L$metadataClass;", null, label5, label27, 8)
            LOCALVAR("d1", "[Ljava/lang/String;", null, label7, label27, 9)
            LOCALVAR("d2", "[Ljava/lang/String;", null, label8, label27, 10)
            LOCALVAR("name", "Ljava/lang/String;", null, label9, label27, 11)
            LOCALVAR("desc", "Ljava/lang/String;", null, label10, label27, 12)
            LOCALVAR("targetType", "Ljava/lang/invoke/MethodType;", null, label23, label27, 13)
            LOCALVAR("handle", "Ljava/lang/invoke/MethodHandle;", null, label26, label27, 14)
            MAXS(5, 17)
        }
    }

    fun createDecryptMethod(methodName: String, key: Int): MethodNode = method(
        PRIVATE + STATIC + SYNTHETIC + BRIDGE,
        methodName,
        "(Ljava/lang/String;)Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            //A:
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V")
            ASTORE(1)
            ICONST_0
            ISTORE(2)
            GOTO(L["labelC"])

            //B:
            LABEL(L["labelB"])
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/lang/StringBuilder", Opcodes.INTEGER), 0)
            ALOAD(1)
            ALOAD(0)
            ILOAD(2)
            INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C")
            LDC(key)
            IXOR
            I2C
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;")
            POP
            IINC(2, 1)

            //C:
            LABEL(L["labelC"])
            FRAME(Opcodes.F_SAME, 0, null, 0)
            ILOAD(2)
            ALOAD(0)
            INVOKEVIRTUAL("java/lang/String", "length", "()I")
            IF_ICMPLT(L["labelB"])
            ALOAD(1)
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
            ARETURN
        }
        MAXS(3, 3)
    }

    fun encrypt(string: String, xor: Int): String {
        val stringBuilder = StringBuilder()
        for (element in string) {
            stringBuilder.append((element.code xor xor).toChar())
        }
        return stringBuilder.toString()
    }

    class MetaData(
        val d1: IntArray,
        val d2: Array<String>,
        val ed1: Array<String>,
        val ed2: Array<String>,
        val m1: IntArray,
        val m2: IntArray
    )

    @JvmStatic
    fun main(args: Array<String>) {
        println(
            MethodType.methodType(
                CallSite::class.java,
                MethodHandles.Lookup::class.java,
                String::class.java,
                MethodType::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Integer::class.java
            ).toMethodDescriptorString()
        )
    }
}
