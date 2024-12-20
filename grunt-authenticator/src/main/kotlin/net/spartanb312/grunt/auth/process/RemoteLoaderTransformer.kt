package net.spartanb312.grunt.auth.process

import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.annotation.DISABLE_SCRAMBLE
import net.spartanb312.grunt.annotation.JUNKCALL_EXCLUDED
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.event.events.FinalizeEvent
import net.spartanb312.grunt.event.events.ProcessEvent
import net.spartanb312.grunt.event.events.TransformerEvent
import net.spartanb312.grunt.event.events.WritingResourceEvent
import net.spartanb312.grunt.event.listener
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.ConstPoolEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.NumberEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorClassic
import net.spartanb312.grunt.process.transformers.redirect.InvokeDynamicTransformer
import net.spartanb312.grunt.process.transformers.rename.ClassRenameTransformer
import net.spartanb312.grunt.utils.extensions.appendAnnotation
import net.spartanb312.grunt.utils.extensions.hasAnnotation
import net.spartanb312.grunt.utils.extensions.removeAnnotation
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * Automatically generate loader and remote jar containing classes with constants
 * Server Mode: Use dedicate server to distribute exclusive jar to each user (W.I.P).
 * URL Mode: Download remote jar from a URL
 */
object RemoteLoaderTransformer : Transformer("RemoteLoader", Category.Miscellaneous) {

    private val heavyEncrypt by setting("HeavyEncrypt", false)
    private val outputJar by setting("OutputJar", "remote.jar")
    private val useServerMode by setting("ServerMode", true) // The server distributes exclusive jar to each user

    // Server Mode
    private val serverAddress by setting("ServerAddress", "(Your remote server address. Server mode only)")
    private val hardwareIDKey by setting("Server-HWID key", "1186118611861186")

    // URL Mode
    private val downloadURL by setting("DownloadURL", "(The remote url of your jar. URL mode only)")

    private val generated get() = ConstPoolEncryptTransformer.generatedClasses

    const val REMOTE_CLASS = "Lnet/spartanb312/grunt/annotation/RemoteClass;"
    const val LOCAL_CLASS = "Lnet/spartanb312/grunt/annotation/LocalClass;"

    init {
        listener<TransformerEvent.After> { event ->
            if (!enabled) return@listener
            if (event.transformer == ClassRenameTransformer) {
                event.resourceCache.classes.values.asSequence()
                    .filter { it.hasAnnotation(LOCAL_CLASS) }
                    .forEach { classNode ->
                        val clinit = classNode.methods.find { it.name == "<clinit>" }!!
                        // Remap ldc insn
                        for (it in clinit.instructions) {
                            if (it is LdcInsnNode) {
                                val next = it.next
                                if (next is MethodInsnNode && next.opcode == Opcodes.INVOKESTATIC && next.desc == "(Ljava/lang/String;)Ljava/lang/Class;") {
                                    val prevName = (it.cst as String).replace(".", "/")
                                    val newName = event.resourceCache.classMappings[prevName]!!
                                    it.cst = newName.replace("/", ".")
                                    //println("Remapped $prevName -> $newName")
                                }
                            }
                        }
                    }
            }
        }
        listener<FinalizeEvent.Before> { event ->
            remoteClasses.clear()
            if (!enabled) return@listener
            // Replace with reflection
            event.resourceCache.classes.values.asSequence()
                .filter { it.hasAnnotation(LOCAL_CLASS) }
                .forEach { classNode ->
                    val clinit = classNode.methods.find { it.name == "<clinit>" }!!
                    for (it in clinit.instructions) {
                        if (it is FieldInsnNode && it.opcode == Opcodes.GETSTATIC) {
                            val next = it.next
                            if (next is FieldInsnNode && next.opcode == Opcodes.PUTSTATIC) {
                                val reflectGet = instructions {
                                    DUP
                                    LDC(it.name)
                                    INVOKEVIRTUAL(
                                        "java/lang/Class",
                                        "getDeclaredField",
                                        "(Ljava/lang/String;)Ljava/lang/reflect/Field;",
                                        false
                                    )
                                    ACONST_NULL
                                    INVOKEVIRTUAL(
                                        "java/lang/reflect/Field",
                                        "get",
                                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                                        false
                                    )
                                    // Wrap primitive
                                    when (it.desc) {
                                        "I" -> {
                                            CHECKCAST("java/lang/Integer")
                                            INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
                                        }

                                        "J" -> {
                                            CHECKCAST("java/lang/Long")
                                            INVOKEVIRTUAL("java/lang/Long", "longValue", "()J", false)
                                        }

                                        "F" -> {
                                            CHECKCAST("java/lang/Float")
                                            INVOKEVIRTUAL("java/lang/Float", "floatValue", "()F", false)
                                        }

                                        "D" -> {
                                            CHECKCAST("java/lang/Double")
                                            INVOKEVIRTUAL("java/lang/Double", "doubleValue", "()D", false)
                                        }

                                        else -> CHECKCAST("java/lang/String")
                                    }
                                }
                                clinit.instructions.insertBefore(it, reflectGet)
                                clinit.instructions.remove(it)
                            }
                        }
                        if (it.opcode == Opcodes.RETURN) clinit.instructions.insertBefore(it, InsnNode(Opcodes.POP))
                    }
                }
            event.resourceCache.classes.values.forEach {
                if (it.hasAnnotation(REMOTE_CLASS)) {
                    remoteClasses.add(it.name)
                }
            }
        }
        listener<FinalizeEvent.After> { event ->
            if (!enabled) return@listener
            event.resourceCache.classes.values.forEach {
                it.removeAnnotation(REMOTE_CLASS)
                it.removeAnnotation(LOCAL_CLASS)
            }
        }
        listener<WritingResourceEvent> { event ->
            if (!enabled) return@listener
            if (remoteClasses.any { event.name.startsWith(it) }) {
                //println("Removed ${event.name}")
                event.cancel()
                remoteBytes[event.name] = event.byteArray
            }
        }
        listener<ProcessEvent.After> {
            val bytesMap = remoteBytes.toMap()
            remoteBytes.clear()
            if (!enabled) return@listener
            // Write remote class
            ZipOutputStream(File(outputJar).outputStream()).apply {
                bytesMap.forEach { (name, bytes) ->
                    putNextEntry(ZipEntry(name))
                    write(bytes)
                    closeEntry()
                }
                close()
            }
        }
    }

    private val remoteClasses = mutableListOf<String>()
    private val remoteBytes = mutableMapOf<String, ByteArray>()

    override fun ResourceCache.transform() {
        val companions = generated.toList()
        generated.clear()
        // Requires ConstPollEncrypt to be enabled
        if (!ConstPoolEncryptTransformer.enabled) {
            Logger.warn("Disabled ConstClassLoader, which requires ConstPoolEncrypt to be enabled!")
            return
        }

        // Assets
        val luckyClass = companions.random().classNode
        val classLoaderName = luckyClass.name.substringBefore("$") + "\$ClassLoader"
        val downloaderName = luckyClass.name.substringBefore("$") + "\$Download"
        val classLoader = createClassLoader(classLoaderName)
        val downloaderJar = createDownloadClass(downloaderName, classLoaderName)

        // Redirect fields
        companions.forEach { generated ->
            generated.classNode.methods.clear()
            generated.classNode.fields.clear()
            generated.classNode.appendAnnotation(LOCAL_CLASS)
            generated.classNode.appendAnnotation(JUNKCALL_EXCLUDED)

            // Generate remote class
            val remoteCompanion = ClassNode().apply {
                visit(
                    generated.classNode.version,
                    Opcodes.ACC_PUBLIC,
                    "${generated.classNode.name}\$Remote",
                    null,
                    "java/lang/Object",
                    null
                )
                appendAnnotation(REMOTE_CLASS)
                appendAnnotation(DISABLE_SCRAMBLE)
                appendAnnotation(JUNKCALL_EXCLUDED)
            }

            // Generate local field initializer
            val remoteFieldPair = mutableListOf<Pair<FieldNode, ConstPoolEncryptTransformer.ConstRef<*>>>()
            val localClinit = method(
                Opcodes.ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null
            ) {
                INSTRUCTIONS {
                    generated.mappings.forEach { (field, ref) ->
                        field.value = null
                        generated.classNode.fields.add(field)
                        val remoteFieldName = getRandomString(15)
                        val remoteField = FieldNode(
                            field.access,
                            remoteFieldName,
                            field.desc,
                            field.signature,
                            ref.value
                        )
                        remoteCompanion.fields.add(remoteField)
                        remoteFieldPair.add(remoteField to ref)
                        GETSTATIC(remoteCompanion.name, remoteField.name, remoteField.desc)
                        PUTSTATIC(generated.classNode.name, field.name, field.desc)
                    }
                    RETURN
                }
            }
            localClinit.instructions.insert(instructions {
                LDC(remoteCompanion.name.replace("/", "."))
                INVOKESTATIC(downloaderName, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;")
            })

            generated.classNode.methods.add(localClinit)

            // Generate remote field initializer
            val remoteClinit = method(
                Opcodes.ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null
            ) {
                INSTRUCTIONS {
                    remoteFieldPair.forEach { (field, ref) ->
                        field.value = null
                        when (ref) {
                            is ConstPoolEncryptTransformer.ConstRef.NumberRef -> {
                                +NumberEncryptorClassic.encrypt(ref.value as Number)
                                PUTSTATIC(remoteCompanion.name, field.name, field.desc)
                            }

                            is ConstPoolEncryptTransformer.ConstRef.StringRef -> {
                                val key = Random.nextInt(0x8, 0x800)
                                val methodName = getRandomString(10)
                                val decryptMethod = InvokeDynamicTransformer.createDecryptMethod(methodName, key)
                                remoteCompanion.methods.add(decryptMethod)
                                LDC(InvokeDynamicTransformer.encrypt(ref.value, key))
                                +MethodInsnNode(
                                    Opcodes.INVOKESTATIC, remoteCompanion.name,
                                    methodName, "(Ljava/lang/String;)Ljava/lang/String;",
                                    false
                                )
                                PUTSTATIC(remoteCompanion.name, field.name, field.desc)
                            }
                        }
                    }
                    RETURN
                }
            }
            // Double encrypt
            if (heavyEncrypt) {
                NumberEncryptTransformer.transformMethod(remoteCompanion, remoteClinit)
                StringEncryptTransformer.transformMethod(remoteCompanion, remoteClinit)
            }
            remoteCompanion.methods.add(remoteClinit)
            addClass(remoteCompanion)
        }

        classLoader.appendAnnotation(JUNKCALL_EXCLUDED)
        downloaderJar.appendAnnotation(JUNKCALL_EXCLUDED)
        addClass(classLoader)
        addClass(downloaderJar)
    }

    private fun createDownloadClass(name: String, loaderName: String): ClassNode {
        val classWriter = ClassNode()
        var fieldVisitor: FieldVisitor
        var methodVisitor: MethodVisitor

        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
            name,
            null,
            "java/lang/Object",
            null
        )

        run {
            fieldVisitor = classWriter.visitField(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL or Opcodes.ACC_STATIC,
                "classLoader",
                "L$loaderName;",
                null,
                null
            )
            fieldVisitor.visitEnd()
        }
        run {
            methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            methodVisitor.visitCode()
            val label0 = Label()
            methodVisitor.visitLabel(label0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            methodVisitor.visitInsn(Opcodes.RETURN)
            val label1 = Label()
            methodVisitor.visitLabel(label1)
            methodVisitor.visitLocalVariable(
                "this",
                "L$name;",
                null,
                label0,
                label1,
                0
            )
            methodVisitor.visitMaxs(1, 1)
            methodVisitor.visitEnd()
        }
        run {
            methodVisitor = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "loadClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                "(Ljava/lang/String;)Ljava/lang/Class<*>;",
                null
            )
            methodVisitor.visitCode()
            val label0 = Label()
            val label1 = Label()
            val label2 = Label()
            methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception")
            methodVisitor.visitLabel(label0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETSTATIC,
                name,
                "classLoader",
                "L$loaderName;"
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                loaderName,
                "loadClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                false
            )
            methodVisitor.visitLabel(label1)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label2)
            methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, arrayOf<Any>("java/lang/Exception"))
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 1)
            val label3 = Label()
            methodVisitor.visitLabel(label3)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/RuntimeException",
                "<init>",
                "(Ljava/lang/Throwable;)V",
                false
            )
            methodVisitor.visitInsn(Opcodes.ATHROW)
            val label4 = Label()
            methodVisitor.visitLabel(label4)
            methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label3, label4, 1)
            methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label4, 0)
            methodVisitor.visitMaxs(3, 2)
            methodVisitor.visitEnd()
        }
        run {
            methodVisitor = classWriter.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
            methodVisitor.visitCode()
            val label0 = Label()
            val label1 = Label()
            val label2 = Label()
            methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception")
            val label3 = Label()
            methodVisitor.visitLabel(label3)
            methodVisitor.visitTypeInsn(Opcodes.NEW, loaderName)
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                loaderName,
                "<init>",
                "()V",
                false
            )
            methodVisitor.visitFieldInsn(
                Opcodes.PUTSTATIC,
                name,
                "classLoader",
                "L$loaderName;"
            )
            methodVisitor.visitLabel(label0)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/net/URL")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitLdcInsn(downloadURL)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/net/URL",
                "<init>",
                "(Ljava/lang/String;)V",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 0)
            val label4 = Label()
            methodVisitor.visitLabel(label4)
            methodVisitor.visitFieldInsn(
                Opcodes.GETSTATIC,
                name,
                "classLoader",
                "L$loaderName;"
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/net/URL",
                "openStream",
                "()Ljava/io/InputStream;",
                false
            )
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                loaderName,
                "loadJar",
                "(Ljava/io/InputStream;)V",
                false
            )
            methodVisitor.visitLabel(label1)
            val label5 = Label()
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label5)
            methodVisitor.visitLabel(label2)
            methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, arrayOf<Any>("java/lang/Exception"))
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 0)
            val label6 = Label()
            methodVisitor.visitLabel(label6)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/RuntimeException",
                "<init>",
                "(Ljava/lang/Throwable;)V",
                false
            )
            methodVisitor.visitInsn(Opcodes.ATHROW)
            methodVisitor.visitLabel(label5)
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            methodVisitor.visitInsn(Opcodes.RETURN)
            methodVisitor.visitLocalVariable("url", "Ljava/net/URL;", null, label4, label1, 0)
            methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label6, label5, 0)
            methodVisitor.visitMaxs(3, 1)
            methodVisitor.visitEnd()
        }
        classWriter.visitEnd()
        return classWriter
    }

    private fun createClassLoader(name: String): ClassNode {
        val classNode = ClassNode()
        // Class
        classNode.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
            name,
            null,
            "java/net/URLClassLoader",
            null
        )
        classNode.visitInnerClass(
            "java/lang/invoke/MethodHandles\$Lookup",
            "java/lang/invoke/MethodHandles",
            "Lookup",
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_STATIC
        )

        // Fields
        var fieldVisitor = classNode.visitField(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL,
            "classesCache",
            "Ljava/util/HashMap;",
            "Ljava/util/HashMap<Ljava/lang/String;[B>;",
            null
        )
        fieldVisitor.visitEnd()
        fieldVisitor = classNode.visitField(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL,
            "resourceCache",
            "Ljava/util/HashMap;",
            "Ljava/util/HashMap<Ljava/lang/String;Ljava/net/URL;>;",
            null
        )
        fieldVisitor.visitEnd()
        fieldVisitor = classNode.visitField(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL,
            "systemPaths",
            "[Ljava/lang/String;",
            null,
            null
        )
        fieldVisitor.visitEnd()
        fieldVisitor = classNode.visitField(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL,
            "dummyPaths",
            "[Ljava/lang/String;",
            null,
            null
        )
        fieldVisitor.visitEnd()
        fieldVisitor = classNode.visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL, "isWindows", "Z", null, null)
        fieldVisitor.visitEnd()

        // Methods
        var methodVisitor: MethodVisitor
        // <init>
        run {
            methodVisitor = classNode.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            methodVisitor.visitCode()
            val label0 = Label()
            methodVisitor.visitLabel(label0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitInsn(Opcodes.ICONST_0)
            methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/net/URL")
            methodVisitor.visitLdcInsn(Type.getType("L$name;"))
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Class",
                "getClassLoader",
                "()Ljava/lang/ClassLoader;",
                false
            )
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/net/URLClassLoader",
                "<init>",
                "([Ljava/net/URL;Ljava/lang/ClassLoader;)V",
                false
            )
            val label1 = Label()
            methodVisitor.visitLabel(label1)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            methodVisitor.visitFieldInsn(
                Opcodes.PUTFIELD,
                name,
                "classesCache",
                "Ljava/util/HashMap;"
            )
            val label2 = Label()
            methodVisitor.visitLabel(label2)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            methodVisitor.visitFieldInsn(
                Opcodes.PUTFIELD,
                name,
                "resourceCache",
                "Ljava/util/HashMap;"
            )
            val label3 = Label()
            methodVisitor.visitLabel(label3)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitLdcInsn("java.library.path")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            methodVisitor.visitLdcInsn(";")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "split",
                "(Ljava/lang/String;)[Ljava/lang/String;",
                false
            )
            methodVisitor.visitFieldInsn(
                Opcodes.PUTFIELD,
                name,
                "systemPaths",
                "[Ljava/lang/String;"
            )
            val label4 = Label()
            methodVisitor.visitLabel(label4)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitInsn(Opcodes.ICONST_2)
            methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitInsn(Opcodes.ICONST_0)
            methodVisitor.visitLdcInsn("C:\\Windows\\System\\")
            methodVisitor.visitInsn(Opcodes.AASTORE)
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitInsn(Opcodes.ICONST_1)
            methodVisitor.visitLdcInsn("C:\\Windows\\System32\\")
            methodVisitor.visitInsn(Opcodes.AASTORE)
            methodVisitor.visitFieldInsn(
                Opcodes.PUTFIELD,
                name,
                "dummyPaths",
                "[Ljava/lang/String;"
            )
            val label5 = Label()
            methodVisitor.visitLabel(label5)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitLdcInsn("os.name")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "toLowerCase",
                "()Ljava/lang/String;",
                false
            )
            methodVisitor.visitLdcInsn("windows")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "contains",
                "(Ljava/lang/CharSequence;)Z",
                false
            )
            methodVisitor.visitFieldInsn(
                Opcodes.PUTFIELD,
                name,
                "isWindows",
                "Z"
            )
            val label6 = Label()
            methodVisitor.visitLabel(label6)
            methodVisitor.visitInsn(Opcodes.RETURN)
            val label7 = Label()
            methodVisitor.visitLabel(label7)
            methodVisitor.visitLocalVariable(
                "this",
                "L$name;",
                null,
                label0,
                label7,
                0
            )
            methodVisitor.visitMaxs(5, 1)
            methodVisitor.visitEnd()
        }
        // findClass
        run {
            methodVisitor = classNode.visitMethod(
                Opcodes.ACC_PROTECTED,
                "findClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                "(Ljava/lang/String;)Ljava/lang/Class<*>;",
                arrayOf<String>("java/lang/ClassNotFoundException")
            )
            methodVisitor.visitCode()
            val label0 = Label()
            val label1 = Label()
            val label2 = Label()
            methodVisitor.visitTryCatchBlock(label0, label1, label2, null)
            val label3 = Label()
            val label4 = Label()
            methodVisitor.visitTryCatchBlock(label3, label4, label2, null)
            val label5 = Label()
            val label6 = Label()
            methodVisitor.visitTryCatchBlock(label5, label6, label2, null)
            val label7 = Label()
            methodVisitor.visitTryCatchBlock(label2, label7, label2, null)
            val label8 = Label()
            methodVisitor.visitLabel(label8)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "getClassLoadingLock",
                "(Ljava/lang/String;)Ljava/lang/Object;",
                false
            )
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 2)
            methodVisitor.visitInsn(Opcodes.MONITORENTER)
            methodVisitor.visitLabel(label0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "findLoadedClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 3)
            val label9 = Label()
            methodVisitor.visitLabel(label9)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitJumpInsn(Opcodes.IFNULL, label3)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitInsn(Opcodes.MONITOREXIT)
            methodVisitor.visitLabel(label1)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label3)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 2, arrayOf<Any>("java/lang/Object", "java/lang/Class"), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                name,
                "classesCache",
                "Ljava/util/HashMap;"
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitInsn(Opcodes.ACONST_NULL)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "getOrDefault",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false
            )
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "[B")
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 4)
            val label10 = Label()
            methodVisitor.visitLabel(label10)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            methodVisitor.visitJumpInsn(Opcodes.IFNULL, label5)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            methodVisitor.visitInsn(Opcodes.ICONST_0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            methodVisitor.visitInsn(Opcodes.ARRAYLENGTH)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "defineClass",
                "(Ljava/lang/String;[BII)Ljava/lang/Class;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitInsn(Opcodes.MONITOREXIT)
            methodVisitor.visitLabel(label4)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label5)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>("[B"), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/net/URLClassLoader",
                "findClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitInsn(Opcodes.MONITOREXIT)
            methodVisitor.visitLabel(label6)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label2)
            methodVisitor.visitFrame(
                Opcodes.F_FULL,
                3,
                arrayOf<Any>(
                    name,
                    "java/lang/String",
                    "java/lang/Object"
                ),
                1,
                arrayOf<Any>("java/lang/Throwable")
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 5)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitInsn(Opcodes.MONITOREXIT)
            methodVisitor.visitLabel(label7)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 5)
            methodVisitor.visitInsn(Opcodes.ATHROW)
            val label11 = Label()
            methodVisitor.visitLabel(label11)
            methodVisitor.visitLocalVariable("loaded", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", label9, label2, 3)
            methodVisitor.visitLocalVariable("bytes", "[B", null, label10, label2, 4)
            methodVisitor.visitLocalVariable(
                "this",
                "L$name;",
                null,
                label8,
                label11,
                0
            )
            methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label8, label11, 1)
            methodVisitor.visitMaxs(5, 6)
            methodVisitor.visitEnd()
        }
        // loadClass
        run {
            methodVisitor = classNode.visitMethod(
                Opcodes.ACC_PROTECTED,
                "loadClass",
                "(Ljava/lang/String;Z)Ljava/lang/Class;",
                "(Ljava/lang/String;Z)Ljava/lang/Class<*>;",
                arrayOf("java/lang/ClassNotFoundException")
            )
            methodVisitor.visitCode()
            val label0 = Label()
            val label1 = Label()
            val label2 = Label()
            methodVisitor.visitTryCatchBlock(label0, label1, label2, null)
            val label3 = Label()
            val label4 = Label()
            methodVisitor.visitTryCatchBlock(label3, label4, label2, null)
            val label5 = Label()
            methodVisitor.visitTryCatchBlock(label2, label5, label2, null)
            val label6 = Label()
            methodVisitor.visitLabel(label6)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "getClassLoadingLock",
                "(Ljava/lang/String;)Ljava/lang/Object;",
                false
            )
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 3)
            methodVisitor.visitInsn(Opcodes.MONITORENTER)
            methodVisitor.visitLabel(label0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "findLoadedClass",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 4)
            val label7 = Label()
            methodVisitor.visitLabel(label7)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, label3)
            val label8 = Label()
            methodVisitor.visitLabel(label8)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                name,
                "classesCache",
                "Ljava/util/HashMap;"
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitInsn(Opcodes.ACONST_NULL)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "getOrDefault",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false
            )
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "[B")
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 5)
            val label9 = Label()
            methodVisitor.visitLabel(label9)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 5)
            methodVisitor.visitJumpInsn(Opcodes.IFNULL, label3)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 5)
            methodVisitor.visitInsn(Opcodes.ICONST_0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 5)
            methodVisitor.visitInsn(Opcodes.ARRAYLENGTH)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "defineClass",
                "(Ljava/lang/String;[BII)Ljava/lang/Class;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitInsn(Opcodes.MONITOREXIT)
            methodVisitor.visitLabel(label1)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label3)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 2, arrayOf<Any>("java/lang/Object", "java/lang/Class"), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            val label10 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, label10)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 2)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/net/URLClassLoader",
                "loadClass",
                "(Ljava/lang/String;Z)Ljava/lang/Class;",
                false
            )
            val label11 = Label()
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label11)
            methodVisitor.visitLabel(label10)
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            methodVisitor.visitLabel(label11)
            methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, arrayOf<Any>("java/lang/Class"))
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitInsn(Opcodes.MONITOREXIT)
            methodVisitor.visitLabel(label4)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label2)
            methodVisitor.visitFrame(
                Opcodes.F_FULL,
                4,
                arrayOf<Any>(
                    name,
                    "java/lang/String",
                    Opcodes.INTEGER,
                    "java/lang/Object"
                ),
                1,
                arrayOf<Any>("java/lang/Throwable")
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 6)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitInsn(Opcodes.MONITOREXIT)
            methodVisitor.visitLabel(label5)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 6)
            methodVisitor.visitInsn(Opcodes.ATHROW)
            val label12 = Label()
            methodVisitor.visitLabel(label12)
            methodVisitor.visitLocalVariable("bytes", "[B", null, label9, label3, 5)
            methodVisitor.visitLocalVariable("c", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", label7, label2, 4)
            methodVisitor.visitLocalVariable(
                "this",
                "L$name;",
                null,
                label6,
                label12,
                0
            )
            methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label6, label12, 1)
            methodVisitor.visitLocalVariable("resolve", "Z", null, label6, label12, 2)
            methodVisitor.visitMaxs(5, 7)
            methodVisitor.visitEnd()
        }
        // findResource
        run {
            methodVisitor = classNode.visitMethod(
                Opcodes.ACC_PUBLIC,
                "findResource",
                "(Ljava/lang/String;)Ljava/net/URL;",
                null,
                null
            )
            methodVisitor.visitCode()
            val label0 = Label()
            methodVisitor.visitLabel(label0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                name,
                "resourceCache",
                "Ljava/util/HashMap;"
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitInsn(Opcodes.ACONST_NULL)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "getOrDefault",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false
            )
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/net/URL")
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 2)
            val label1 = Label()
            methodVisitor.visitLabel(label1)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            val label2 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFNULL, label2)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label2)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>("java/net/URL"), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/net/URLClassLoader",
                "findResource",
                "(Ljava/lang/String;)Ljava/net/URL;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 3)
            val label3 = Label()
            methodVisitor.visitLabel(label3)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            val label4 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFNULL, label4)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label4)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>("java/net/URL"), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "getParent",
                "()Ljava/lang/ClassLoader;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/ClassLoader",
                "getResource",
                "(Ljava/lang/String;)Ljava/net/URL;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 4)
            val label5 = Label()
            methodVisitor.visitLabel(label5)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            val label6 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFNULL, label6)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label6)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>("java/net/URL"), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                name,
                "systemPaths",
                "[Ljava/lang/String;"
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 5)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 5)
            methodVisitor.visitInsn(Opcodes.ARRAYLENGTH)
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 6)
            methodVisitor.visitInsn(Opcodes.ICONST_0)
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 7)
            val label7 = Label()
            methodVisitor.visitLabel(label7)
            methodVisitor.visitFrame(
                Opcodes.F_APPEND,
                3,
                arrayOf<Any>("[Ljava/lang/String;", Opcodes.INTEGER, Opcodes.INTEGER),
                0,
                null
            )
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 7)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 6)
            val label8 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, label8)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 5)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 7)
            methodVisitor.visitInsn(Opcodes.AALOAD)
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 8)
            val label9 = Label()
            methodVisitor.visitLabel(label9)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 8)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                name,
                "findInPath",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/net/URL;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 9)
            val label10 = Label()
            methodVisitor.visitLabel(label10)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 9)
            val label11 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFNULL, label11)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 9)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label11)
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            methodVisitor.visitIincInsn(7, 1)
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label7)
            methodVisitor.visitLabel(label8)
            methodVisitor.visitFrame(Opcodes.F_CHOP, 3, null, 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                name,
                "dummyPaths",
                "[Ljava/lang/String;"
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 5)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 5)
            methodVisitor.visitInsn(Opcodes.ARRAYLENGTH)
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 6)
            methodVisitor.visitInsn(Opcodes.ICONST_0)
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 7)
            val label12 = Label()
            methodVisitor.visitLabel(label12)
            methodVisitor.visitFrame(
                Opcodes.F_APPEND,
                3,
                arrayOf<Any>("[Ljava/lang/String;", Opcodes.INTEGER, Opcodes.INTEGER),
                0,
                null
            )
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 7)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 6)
            val label13 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, label13)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 5)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 7)
            methodVisitor.visitInsn(Opcodes.AALOAD)
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 8)
            val label14 = Label()
            methodVisitor.visitLabel(label14)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 8)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                name,
                "findInPath",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/net/URL;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 9)
            val label15 = Label()
            methodVisitor.visitLabel(label15)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 9)
            val label16 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFNULL, label16)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 9)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label16)
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            methodVisitor.visitIincInsn(7, 1)
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label12)
            methodVisitor.visitLabel(label13)
            methodVisitor.visitFrame(Opcodes.F_CHOP, 3, null, 0, null)
            methodVisitor.visitInsn(Opcodes.ACONST_NULL)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            val label17 = Label()
            methodVisitor.visitLabel(label17)
            methodVisitor.visitLocalVariable("res", "Ljava/net/URL;", null, label10, label11, 9)
            methodVisitor.visitLocalVariable("sysPath", "Ljava/lang/String;", null, label9, label11, 8)
            methodVisitor.visitLocalVariable("res", "Ljava/net/URL;", null, label15, label16, 9)
            methodVisitor.visitLocalVariable("dummy", "Ljava/lang/String;", null, label14, label16, 8)
            methodVisitor.visitLocalVariable(
                "this",
                "L$name;",
                null,
                label0,
                label17,
                0
            )
            methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label17, 1)
            methodVisitor.visitLocalVariable("resInCache", "Ljava/net/URL;", null, label1, label17, 2)
            methodVisitor.visitLocalVariable("resInThis", "Ljava/net/URL;", null, label3, label17, 3)
            methodVisitor.visitLocalVariable("resInParent", "Ljava/net/URL;", null, label5, label17, 4)
            methodVisitor.visitMaxs(3, 10)
            methodVisitor.visitEnd()
        }
        // loadJar
        run {
            methodVisitor = classNode.visitMethod(
                Opcodes.ACC_PUBLIC,
                "loadJar",
                "(Ljava/io/InputStream;)V",
                null,
                arrayOf("java/io/IOException")
            )
            methodVisitor.visitCode()
            val label0 = Label()
            methodVisitor.visitLabel(label0)
            methodVisitor.visitLdcInsn("loader-temp")
            methodVisitor.visitLdcInsn(".jar")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/io/File",
                "createTempFile",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/io/File;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 2)
            val label1 = Label()
            methodVisitor.visitLabel(label1)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "deleteOnExit", "()V", false)
            val label2 = Label()
            methodVisitor.visitLabel(label2)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/io/FileOutputStream")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/io/FileOutputStream",
                "<init>",
                "(Ljava/io/File;)V",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 3)
            val label3 = Label()
            methodVisitor.visitLabel(label3)
            methodVisitor.visitIntInsn(Opcodes.SIPUSH, 1024)
            methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 4)
            val label4 = Label()
            methodVisitor.visitLabel(label4)
            methodVisitor.visitFrame(
                Opcodes.F_APPEND,
                3,
                arrayOf<Any>("java/io/File", "java/io/FileOutputStream", "[B"),
                0,
                null
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "read", "([B)I", false)
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 5)
            val label5 = Label()
            methodVisitor.visitLabel(label5)
            methodVisitor.visitInsn(Opcodes.ICONST_M1)
            val label6 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IF_ICMPEQ, label6)
            val label7 = Label()
            methodVisitor.visitLabel(label7)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            methodVisitor.visitInsn(Opcodes.ICONST_0)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 5)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/FileOutputStream", "write", "([BII)V", false)
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label4)
            methodVisitor.visitLabel(label6)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>(Opcodes.INTEGER), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/FileOutputStream", "close", "()V", false)
            val label8 = Label()
            methodVisitor.visitLabel(label8)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "close", "()V", false)
            val label9 = Label()
            methodVisitor.visitLabel(label9)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                name,
                "loadJar",
                "(Ljava/io/File;)V",
                false
            )
            val label10 = Label()
            methodVisitor.visitLabel(label10)
            methodVisitor.visitInsn(Opcodes.RETURN)
            val label11 = Label()
            methodVisitor.visitLabel(label11)
            methodVisitor.visitLocalVariable(
                "this",
                "L$name;",
                null,
                label0,
                label11,
                0
            )
            methodVisitor.visitLocalVariable("inputStream", "Ljava/io/InputStream;", null, label0, label11, 1)
            methodVisitor.visitLocalVariable("temp", "Ljava/io/File;", null, label1, label11, 2)
            methodVisitor.visitLocalVariable("fos", "Ljava/io/FileOutputStream;", null, label3, label11, 3)
            methodVisitor.visitLocalVariable("buffer", "[B", null, label4, label11, 4)
            methodVisitor.visitLocalVariable("length", "I", null, label5, label11, 5)
            methodVisitor.visitMaxs(4, 6)
            methodVisitor.visitEnd()
        }
        // loadJar
        run {
            methodVisitor = classNode.visitMethod(
                Opcodes.ACC_PUBLIC,
                "loadJar",
                "(Ljava/io/File;)V",
                null,
                arrayOf("java/io/IOException")
            )
            methodVisitor.visitCode()
            val label0 = Label()
            methodVisitor.visitLabel(label0)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/zip/ZipInputStream")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/io/FileInputStream")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/io/FileInputStream",
                "<init>",
                "(Ljava/io/File;)V",
                false
            )
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/util/zip/ZipInputStream",
                "<init>",
                "(Ljava/io/InputStream;)V",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 2)
            val label1 = Label()
            methodVisitor.visitLabel(label1)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>("java/util/zip/ZipInputStream"), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/zip/ZipInputStream",
                "getNextEntry",
                "()Ljava/util/zip/ZipEntry;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 3)
            val label2 = Label()
            methodVisitor.visitLabel(label2)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            val label3 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, label3)
            val label4 = Label()
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label4)
            methodVisitor.visitLabel(label3)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, arrayOf<Any>("java/util/zip/ZipEntry"), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/zip/ZipEntry",
                "getName",
                "()Ljava/lang/String;",
                false
            )
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "toLowerCase",
                "()Ljava/lang/String;",
                false
            )
            methodVisitor.visitLdcInsn(".class")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "endsWith",
                "(Ljava/lang/String;)Z",
                false
            )
            val label5 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, label5)
            val label6 = Label()
            methodVisitor.visitLabel(label6)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                name,
                "classesCache",
                "Ljava/util/HashMap;"
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/zip/ZipEntry",
                "getName",
                "()Ljava/lang/String;",
                false
            )
            methodVisitor.visitLdcInsn("/")
            methodVisitor.visitLdcInsn(".")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "replace",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
                false
            )
            methodVisitor.visitLdcInsn(".class")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                name,
                "removeSuffix",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                name,
                "readBytes",
                "(Ljava/io/InputStream;)[B",
                false
            )
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false
            )
            methodVisitor.visitInsn(Opcodes.POP)
            val label7 = Label()
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label7)
            methodVisitor.visitLabel(label5)
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/net/URL")
            methodVisitor.visitInsn(Opcodes.DUP)
            val label8 = Label()
            methodVisitor.visitLabel(label8)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                name,
                "isWindows",
                "Z"
            )
            val label9 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, label9)
            methodVisitor.visitLdcInsn("/")
            val label10 = Label()
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label10)
            methodVisitor.visitLabel(label9)
            methodVisitor.visitFrame(
                Opcodes.F_FULL,
                4,
                arrayOf<Any>(
                    name,
                    "java/io/File",
                    "java/util/zip/ZipInputStream",
                    "java/util/zip/ZipEntry"
                ),
                2,
                arrayOf<Any>(label5, label5)
            )
            methodVisitor.visitLdcInsn("")
            methodVisitor.visitLabel(label10)
            methodVisitor.visitFrame(
                Opcodes.F_FULL,
                4,
                arrayOf<Any>(
                    name,
                    "java/io/File",
                    "java/util/zip/ZipInputStream",
                    "java/util/zip/ZipEntry"
                ),
                3,
                arrayOf(label5, label5, "java/lang/String")
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            val label11 = Label()
            methodVisitor.visitLabel(label11)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/io/File",
                "getAbsolutePath",
                "()Ljava/lang/String;",
                false
            )
            methodVisitor.visitLdcInsn("\\")
            methodVisitor.visitLdcInsn("/")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "replace",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            val label12 = Label()
            methodVisitor.visitLabel(label12)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/zip/ZipEntry",
                "getName",
                "()Ljava/lang/String;",
                false
            )
            methodVisitor.visitInvokeDynamicInsn(
                "makeConcatWithConstants",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/StringConcatFactory",
                    "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                    false
                ),
                "jar:file:\u0001\u0001!/\u0001"
            )
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/net/URL",
                "<init>",
                "(Ljava/lang/String;)V",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 4)
            val label13 = Label()
            methodVisitor.visitLabel(label13)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                name,
                "resourceCache",
                "Ljava/util/HashMap;"
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/zip/ZipEntry",
                "getName",
                "()Ljava/lang/String;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 4)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false
            )
            methodVisitor.visitInsn(Opcodes.POP)
            methodVisitor.visitLabel(label7)
            methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null)
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label1)
            methodVisitor.visitLabel(label4)
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            methodVisitor.visitInsn(Opcodes.RETURN)
            val label14 = Label()
            methodVisitor.visitLabel(label14)
            methodVisitor.visitLocalVariable("resourceURL", "Ljava/net/URL;", null, label13, label7, 4)
            methodVisitor.visitLocalVariable("entry", "Ljava/util/zip/ZipEntry;", null, label2, label7, 3)
            methodVisitor.visitLocalVariable(
                "this",
                "L$name;",
                null,
                label0,
                label14,
                0
            )
            methodVisitor.visitLocalVariable("file", "Ljava/io/File;", null, label0, label14, 1)
            methodVisitor.visitLocalVariable("zip", "Ljava/util/zip/ZipInputStream;", null, label1, label14, 2)
            methodVisitor.visitMaxs(6, 5)
            methodVisitor.visitEnd()
        }
        // readBytes
        run {
            methodVisitor = classNode.visitMethod(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                "readBytes",
                "(Ljava/io/InputStream;)[B",
                null,
                arrayOf("java/io/IOException")
            )
            methodVisitor.visitCode()
            val label0 = Label()
            methodVisitor.visitLabel(label0)
            methodVisitor.visitIntInsn(Opcodes.SIPUSH, 8192)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "available", "()I", false)
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false)
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 1)
            val label1 = Label()
            methodVisitor.visitLabel(label1)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/io/ByteArrayOutputStream")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/io/ByteArrayOutputStream",
                "<init>",
                "(I)V",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 2)
            val label2 = Label()
            methodVisitor.visitLabel(label2)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                name,
                "copyTo",
                "(Ljava/io/InputStream;Ljava/io/OutputStream;I)V",
                false
            )
            val label3 = Label()
            methodVisitor.visitLabel(label3)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/io/ByteArrayOutputStream",
                "toByteArray",
                "()[B",
                false
            )
            methodVisitor.visitInsn(Opcodes.ARETURN)
            val label4 = Label()
            methodVisitor.visitLabel(label4)
            methodVisitor.visitLocalVariable("input", "Ljava/io/InputStream;", null, label0, label4, 0)
            methodVisitor.visitLocalVariable("size", "I", null, label1, label4, 1)
            methodVisitor.visitLocalVariable("buffer", "Ljava/io/ByteArrayOutputStream;", null, label2, label4, 2)
            methodVisitor.visitMaxs(3, 3)
            methodVisitor.visitEnd()
        }
        // copyTo
        run {
            methodVisitor = classNode.visitMethod(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                "copyTo",
                "(Ljava/io/InputStream;Ljava/io/OutputStream;I)V",
                null,
                arrayOf("java/io/IOException")
            )
            methodVisitor.visitCode()
            val label0 = Label()
            methodVisitor.visitLabel(label0)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 2)
            methodVisitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 3)
            val label1 = Label()
            methodVisitor.visitLabel(label1)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "read", "([B)I", false)
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 4)
            val label2 = Label()
            methodVisitor.visitLabel(label2)
            methodVisitor.visitFrame(Opcodes.F_APPEND, 2, arrayOf<Any>("[B", Opcodes.INTEGER), 0, null)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 4)
            val label3 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFLT, label3)
            val label4 = Label()
            methodVisitor.visitLabel(label4)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitInsn(Opcodes.ICONST_0)
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 4)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/OutputStream", "write", "([BII)V", false)
            val label5 = Label()
            methodVisitor.visitLabel(label5)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "read", "([B)I", false)
            methodVisitor.visitVarInsn(Opcodes.ISTORE, 4)
            methodVisitor.visitJumpInsn(Opcodes.GOTO, label2)
            methodVisitor.visitLabel(label3)
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            methodVisitor.visitInsn(Opcodes.RETURN)
            val label6 = Label()
            methodVisitor.visitLabel(label6)
            methodVisitor.visitLocalVariable("in", "Ljava/io/InputStream;", null, label0, label6, 0)
            methodVisitor.visitLocalVariable("out", "Ljava/io/OutputStream;", null, label0, label6, 1)
            methodVisitor.visitLocalVariable("bufferSize", "I", null, label0, label6, 2)
            methodVisitor.visitLocalVariable("buffer", "[B", null, label1, label6, 3)
            methodVisitor.visitLocalVariable("bytes", "I", null, label2, label6, 4)
            methodVisitor.visitMaxs(4, 5)
            methodVisitor.visitEnd()
        }
        // removeSuffix
        run {
            methodVisitor = classNode.visitMethod(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                "removeSuffix",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                null,
                null
            )
            methodVisitor.visitCode()
            val label0 = Label()
            methodVisitor.visitLabel(label0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "endsWith",
                "(Ljava/lang/String;)Z",
                false
            )
            val label1 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, label1)
            val label2 = Label()
            methodVisitor.visitLabel(label2)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitInsn(Opcodes.ICONST_0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            methodVisitor.visitInsn(Opcodes.ISUB)
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "substring",
                "(II)Ljava/lang/String;",
                false
            )
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label1)
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            val label3 = Label()
            methodVisitor.visitLabel(label3)
            methodVisitor.visitLocalVariable("value", "Ljava/lang/String;", null, label0, label3, 0)
            methodVisitor.visitLocalVariable("suffix", "Ljava/lang/String;", null, label0, label3, 1)
            methodVisitor.visitMaxs(4, 2)
            methodVisitor.visitEnd()
        }
        // findInPath
        run {
            methodVisitor = classNode.visitMethod(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                "findInPath",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/net/URL;",
                null,
                null
            )
            methodVisitor.visitCode()
            val label0 = Label()
            val label1 = Label()
            val label2 = Label()
            methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/net/MalformedURLException")
            val label3 = Label()
            methodVisitor.visitLabel(label3)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
            methodVisitor.visitLdcInsn("/")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                name,
                "removeSuffix",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            methodVisitor.visitLdcInsn("\\")
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                name,
                "removeSuffix",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 2)
            val label4 = Label()
            methodVisitor.visitLabel(label4)
            methodVisitor.visitTypeInsn(Opcodes.NEW, "java/io/File")
            methodVisitor.visitInsn(Opcodes.DUP)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1)
            methodVisitor.visitInvokeDynamicInsn(
                "makeConcatWithConstants", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/StringConcatFactory",
                    "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                    false
                ), "\u0001/\u0001"
            )
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/io/File",
                "<init>",
                "(Ljava/lang/String;)V",
                false
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 3)
            val label5 = Label()
            methodVisitor.visitLabel(label5)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false)
            val label6 = Label()
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, label6)
            methodVisitor.visitLabel(label0)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 3)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "toURI", "()Ljava/net/URI;", false)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/net/URI", "toURL", "()Ljava/net/URL;", false)
            methodVisitor.visitLabel(label1)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label2)
            methodVisitor.visitFrame(
                Opcodes.F_FULL,
                4,
                arrayOf<Any>("java/lang/String", "java/lang/String", "java/lang/String", "java/io/File"),
                1,
                arrayOf<Any>("java/net/MalformedURLException")
            )
            methodVisitor.visitVarInsn(Opcodes.ASTORE, 4)
            val label7 = Label()
            methodVisitor.visitLabel(label7)
            methodVisitor.visitInsn(Opcodes.ACONST_NULL)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitLabel(label6)
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
            methodVisitor.visitInsn(Opcodes.ACONST_NULL)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            val label8 = Label()
            methodVisitor.visitLabel(label8)
            methodVisitor.visitLocalVariable("e", "Ljava/net/MalformedURLException;", null, label7, label6, 4)
            methodVisitor.visitLocalVariable("path", "Ljava/lang/String;", null, label3, label8, 0)
            methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label3, label8, 1)
            methodVisitor.visitLocalVariable("adjustedPath", "Ljava/lang/String;", null, label4, label8, 2)
            methodVisitor.visitLocalVariable("file", "Ljava/io/File;", null, label5, label8, 3)
            methodVisitor.visitMaxs(4, 5)
            methodVisitor.visitEnd()
        }
        classNode.visitEnd()
        return classNode
    }

}