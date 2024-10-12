package net.spartanb312.grunt.process.transformers.redirect

import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.annotation.DISABLE_INVOKEDYNAMIC
import net.spartanb312.grunt.config.Configs.isExcluded
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer.createDecryptMethod
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer.encrypt
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.process.transformers.flow.process.JunkCode
import net.spartanb312.grunt.process.transformers.rename.LocalVariableRenameTransformer
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.extensions.hasAnnotation
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.logging.Logger
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
 * Last update on 24/10/12
 */
object InvokeDynamicTransformer : Transformer("InvokeDynamic", Category.Redirect) {

    private val rate by setting("ReplacePercentage", 10)
    private val massiveRandom by setting("MassiveRandomBlank", false)
    private val reobf by setting("Reobfuscate", true)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Replacing invokes to InvokeDynamic...")
        if (ControlflowTransformer.enabled) JunkCode.refresh(this@transform)
        val count = count {
            classes.filter {
                val map = getMapping(it.value.name)
                !it.value.isInterface && it.value.version >= Opcodes.V1_7
                        && !map.isExcluded && map.notInList(exclusion)
                        && !it.value.hasAnnotation(DISABLE_INVOKEDYNAMIC)
            }.values.forEach { classNode ->
                val bootstrapName = if (massiveRandom) massiveBlankString else massiveString
                val decryptName = if (massiveRandom) massiveBlankString else massiveString
                val decryptKey = Random.nextInt(0x8, 0x800)
                if (shouldApply(classNode, bootstrapName, decryptKey)) {
                    val decrypt = createDecryptMethod(decryptName, decryptKey)
                    val bsm = createBootstrap(classNode.name, bootstrapName, decryptName)
                    if (reobf) {
                        if (ControlflowTransformer.enabled) {
                            val antiSim = ControlflowTransformer.antiSimulation
                            ControlflowTransformer.antiSimulation = false
                            ControlflowTransformer.transformMethod(classNode, decrypt)
                            ControlflowTransformer.transformMethod(classNode, bsm)
                            ControlflowTransformer.antiSimulation = antiSim
                        }
                        if (LocalVariableRenameTransformer.enabled) {
                            LocalVariableRenameTransformer.transformMethod(classNode, decrypt)
                            LocalVariableRenameTransformer.transformMethod(classNode, bsm)
                        }
                    }
                    classNode.methods.add(decrypt)
                    classNode.methods.add(bsm)
                }
            }
        }.get()
        Logger.info("    Replaced $count invokes")
    }

    private fun Counter.shouldApply(classNode: ClassNode, bootstrapName: String, decryptKey: Int): Boolean {
        var shouldApply = false
        classNode.methods
            .filter { !it.isAbstract && !it.isNative }
            .forEach { methodNode ->
                if (!methodNode.hasAnnotation(DISABLE_INVOKEDYNAMIC)) {
                    methodNode.instructions.filter {
                        it is MethodInsnNode && it.opcode != Opcodes.INVOKESPECIAL
                    }.forEach { insnNode ->
                        if (insnNode is MethodInsnNode && (0..99).random() < rate) {
                            val invokeDynamicInsnNode = InvokeDynamicInsnNode(
                                bootstrapName,
                                if (insnNode.opcode == Opcodes.INVOKESTATIC) insnNode.desc
                                else insnNode.desc.replace("(", "(Ljava/lang/Object;"),
                                H_INVOKESTATIC(
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

}