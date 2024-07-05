package net.spartanb312.grunt.process.transformers.redirect

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer.createDecryptMethod
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer.encrypt
import net.spartanb312.grunt.utils.Counter
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.massiveBlankString
import net.spartanb312.grunt.utils.massiveString
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.lang.invoke.CallSite
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.random.Random

/**
 * Replace invokes to invoke dynamic
 * Last update on 24/07/02
 */
object InvokeDynamicTransformer : Transformer("InvokeDynamic", Category.Redirect) {

    private val rate by setting("ReplacePercentage", 10)
    private val massiveRandom by setting("MassiveRandomBlank", true)

    override fun ResourceCache.transform() {
        Logger.info(" - Replacing invokes to InvokeDynamic...")
        val count = count {
            nonExcluded.asSequence()
                .filter { !it.isInterface && it.version >= Opcodes.V1_7 }
                .forEach { classNode ->
                    val bootstrapName = if (massiveRandom) massiveBlankString else massiveString
                    val decryptName = if (massiveRandom) massiveBlankString else massiveString
                    val decryptValue = Random.nextInt(0x8, 0x800)
                    if (shouldApply(classNode, bootstrapName, decryptValue)) {
                        classNode.methods.add(createDecryptMethod(decryptName, decryptValue))
                        classNode.methods.add(createBootstrap(classNode.name, bootstrapName, decryptName))
                    }
                }
        }.get()
        Logger.info("    Replaced $count invokes")
    }

    private fun Counter.shouldApply(classNode: ClassNode, bootstrapName: String, decryptValue: Int): Boolean {
        var shouldApply = false
        classNode.methods
            .filter { !it.isAbstract }
            .forEach { methodNode ->
                methodNode.instructions.filter { it is MethodInsnNode && it.opcode != Opcodes.INVOKESPECIAL }
                    .forEach { insnNode ->
                        if (insnNode is MethodInsnNode && (0..99).random() < rate) {
                            val handle = Handle(
                                Opcodes.H_INVOKESTATIC,
                                classNode.name,
                                bootstrapName,
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
                                false
                            )
                            val invokeDynamicInsnNode = InvokeDynamicInsnNode(
                                bootstrapName,
                                if (insnNode.opcode == Opcodes.INVOKESTATIC) insnNode.desc
                                else insnNode.desc.replace("(", "(Ljava/lang/Object;"),
                                handle,
                                encrypt(insnNode.owner.replace("/", "."), decryptValue),
                                encrypt(insnNode.name, decryptValue),
                                encrypt(insnNode.desc, decryptValue),
                                if (insnNode.opcode == Opcodes.INVOKESTATIC) 0 else 1
                            )
                            methodNode.instructions.insertBefore(insnNode, invokeDynamicInsnNode)
                            methodNode.instructions.remove(insnNode)
                            shouldApply = true
                            add()
                        }
                    }
            }
        return shouldApply
    }

    private fun createBootstrap(className: String, methodName: String, decryptName: String) =
        method(
            Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC + Opcodes.ACC_BRIDGE,
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
            ).toMethodDescriptorString(),
            null,
            null
        ) {
            val labelA = Label()
            val labelB = Label()
            val labelC = Label()
            val labelD = Label()
            val labelE = Label()
            TRYCATCH(labelA, labelB, labelC, "java/lang/Exception")
            TRYCATCH(labelD, labelE, labelC, "java/lang/Exception")
            InsnList {
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
                INVOKEVIRTUAL("java/lang/Integer", "intValue", "()I", false)
                ISTORE(10)
                ALOAD(9)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;", false)
                LDC_TYPE(className)
                INVOKEVIRTUAL("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false)
                INVOKESTATIC(
                    "java/lang/invoke/MethodType",
                    "fromMethodDescriptorString",
                    "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;",
                    false
                )
                ASTORE(11)
                LABEL(labelA)
                ILOAD(10)
                ICONST_1
                IF_ICMPNE(labelD)
                NEW("java/lang/invoke/ConstantCallSite")
                DUP
                ALOAD(0)
                ALOAD(7)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;", false)
                INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false)
                ALOAD(8)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;", false)
                ALOAD(11)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "findVirtual",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                    false
                )
                ALOAD(2)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandle",
                    "asType",
                    "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                    false
                )
                INVOKESPECIAL(
                    "java/lang/invoke/ConstantCallSite",
                    "<init>",
                    "(Ljava/lang/invoke/MethodHandle;)V",
                    false
                )
                LABEL(labelB)
                ARETURN
                LABEL(labelD)
                FRAME(
                    Opcodes.F_FULL, 12, arrayOf(
                        "java/lang/invoke/MethodHandles\$Lookup", "java/lang/String",
                        "java/lang/invoke/MethodType", "java/lang/Object", "java/lang/Object", "java/lang/Object",
                        "java/lang/Object", "java/lang/String", "java/lang/String", "java/lang/String", Opcodes.INTEGER,
                        "java/lang/invoke/MethodType"
                    ), 0, arrayOf()
                )
                NEW("java/lang/invoke/ConstantCallSite")
                DUP
                ALOAD(0)
                ALOAD(7)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;", false)
                INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false)
                ALOAD(8)
                INVOKESTATIC(className, decryptName, "(Ljava/lang/String;)Ljava/lang/String;", false)
                ALOAD(11)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "findStatic",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                    false
                )
                ALOAD(2)
                INVOKEVIRTUAL(
                    "java/lang/invoke/MethodHandle",
                    "asType",
                    "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                    false
                )
                INVOKESPECIAL(
                    "java/lang/invoke/ConstantCallSite",
                    "<init>",
                    "(Ljava/lang/invoke/MethodHandle;)V",
                    false
                )
                LABEL(labelE)
                ARETURN
                LABEL(labelC)
                FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/Exception"))
                ASTORE(12)
                ACONST_NULL
                ARETURN
            }
            Maxs(6, 13)
        }

}