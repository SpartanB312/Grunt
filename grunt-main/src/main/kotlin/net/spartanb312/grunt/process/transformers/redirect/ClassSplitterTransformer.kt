package net.spartanb312.grunt.process.transformers.redirect

import net.spartanb312.genesis.kotlin.*
import net.spartanb312.genesis.kotlin.extensions.FINAL
import net.spartanb312.genesis.kotlin.extensions.PRIVATE
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.extensions.isInitializer
import net.spartanb312.grunt.utils.extensions.isPrivate
import net.spartanb312.grunt.utils.extensions.isSynthetic
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Redirect private methods to companion class
 * W.I.P
 */
object ClassSplitterTransformer : Transformer("ClassSplitter", Category.Redirect) {

    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Splitting class members...")
        nonExcluded.asSequence()
            .filter { it.name.notInList(exclusion) }
            .forEach { classNode ->
                val pull = mutableListOf<MethodNode>()
                val accessMethod = mutableMapOf<AccessInfo, MethodNode>()
                val accessGetField = mutableMapOf<AccessInfo, MethodNode>()
                val accessSetField = mutableMapOf<AccessInfo, MethodNode>()
                val companionNode = clazz(PUBLIC, "${classNode.name}\$Methods") {
                    +field(PRIVATE, "accessor", "L${classNode.name};")
                    +method(
                        PUBLIC,
                        "<init>",
                        "(L${classNode.name};)V",
                        null,
                        null,
                    ) {
                        INSTRUCTIONS {
                            ALOAD(0)
                            INVOKESPECIAL("java/lang/Object", "<init>", "()V")
                            ALOAD(0)
                            ALOAD(1)
                            PUTFIELD("${classNode.name}\$Methods", "accessor", "L${classNode.name};")
                            RETURN
                        }
                    }
                }
                classNode.methods.forEach { methodNode ->
                    if (methodNode.isPrivate
                        && !methodNode.isSynthetic
                        && !methodNode.name.contains("lambda")
                        && !methodNode.isInitializer
                    ) {
                        /*val newInsnList = instructions {
                            methodNode.instructions.forEach { insnNode ->
                                when (insnNode) {
                                    is FieldInsnNode -> {
                                        /*if (insnNode.opcode == Opcodes.GETSTATIC) {
                                            val accessorMethod = accessGetField.getOrPut(
                                                AccessInfo(
                                                    insnNode.owner,
                                                    insnNode.name,
                                                    insnNode.desc
                                                )
                                            ) {
                                                method(
                                                    PUBLIC + FINAL,
                                                    "getter-${insnNode.name}-${getRandomString(5)}",
                                                    "()${insnNode.desc}"
                                                ) {

                                                }
                                            }
                                            ALOAD(0)
                                            GETFIELD("${classNode.name}\$Methods", "accessor", "L${classNode.name};")
                                            INVOKEVIRTUAL(classNode.name, accessorMethod.name, accessorMethod.desc)
                                        } else if (insnNode.opcode == Opcodes.PUTSTATIC) {
                                            val accessorMethod = accessGetField.getOrPut(
                                                AccessInfo(
                                                    insnNode.owner,
                                                    insnNode.name,
                                                    "(L${classNode.name};)${insnNode.desc}"
                                                )
                                            ) {
                                                method(
                                                    PUBLIC + FINAL,
                                                    "setter-${insnNode.name}-${getRandomString(5)}",
                                                    "(${insnNode.desc})V"
                                                ) {

                                                }
                                            }
                                            ALOAD(0)
                                            GETFIELD("${classNode.name}\$Methods", "accessor", "L${classNode.name};")
                                            SWAP
                                            INVOKEVIRTUAL(classNode.name, accessorMethod.name, accessorMethod.desc)
                                        } else +insnNode*/
                                        +insnNode
                                    }

                                    else -> +insnNode
                                }
                            }
                        }*/
                        companionNode.methods.add(
                            method(
                                PUBLIC,
                                methodNode.name,
                                methodNode.desc
                            )//.also { it.instructions =  }
                        )
                    }
                }

                addClass(companionNode)
            }
        //Logger.info("    Redirected $count methods")
    }

    data class AccessInfo(val owner: String, val name: String, val desc: String)

}
