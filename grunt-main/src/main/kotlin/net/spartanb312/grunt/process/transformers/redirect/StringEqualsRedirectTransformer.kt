package net.spartanb312.grunt.process.transformers.redirect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESTATIC
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKEVIRTUAL
import net.spartanb312.genesis.kotlin.extensions.insn.SWAP
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.match
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.tree.MethodInsnNode

/**
 * Replace string.equals()
 * Last update on 2024/06/28
 */
object StringEqualsRedirectTransformer : Transformer("RedirectStringEquals", Category.Redirect) {

    private val ignoreCase by setting("IgnoreCase", true)
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Redirecting string equals calls...")
        val count = count {
            runBlocking {
                nonExcluded.asSequence()
                    .filter { it.name.notInList(exclusion) }
                    .forEach { classNode ->
                        fun job() {
                            classNode.methods.forEach { methodNode ->
                                for (insnNode in methodNode.instructions.toArray()) {
                                    if (insnNode is MethodInsnNode) {
                                        if (insnNode.match(
                                                "java/lang/String",
                                                "equals",
                                                "(Ljava/lang/Object;)Z"
                                            )
                                        ) {
                                            val replacement = instructions {
                                                INVOKEVIRTUAL("java/lang/Object", "hashCode", "()I")
                                                INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                                                SWAP
                                                INVOKEVIRTUAL("java/lang/String", "hashCode", "()I")
                                                INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                                                INVOKEVIRTUAL("java/lang/Integer", "equals", "(Ljava/lang/Object;)Z")
                                            }
                                            methodNode.instructions.insert(insnNode, replacement)
                                            methodNode.instructions.remove(insnNode)
                                            add(1)
                                        } else if (insnNode.match(
                                                "java/lang/String",
                                                "equalsIgnoreCase",
                                                "(Ljava/lang/String;)Z"
                                            ) && ignoreCase
                                        ) {
                                            val replacement = instructions {
                                                INVOKEVIRTUAL("java/lang/String", "toUpperCase", "()Ljava/lang/String;")
                                                INVOKEVIRTUAL("java/lang/Object", "hashCode", "()I")
                                                INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                                                SWAP
                                                INVOKEVIRTUAL("java/lang/String", "toUpperCase", "()Ljava/lang/String;")
                                                INVOKEVIRTUAL("java/lang/String", "hashCode", "()I")
                                                INVOKESTATIC("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                                                INVOKEVIRTUAL("java/lang/Integer", "equals", "(Ljava/lang/Object;)Z")
                                            }
                                            methodNode.instructions.insert(insnNode, replacement)
                                            methodNode.instructions.remove(insnNode)
                                            add(1)
                                        }
                                    }
                                }
                            }
                        }
                        if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                    }
            }
        }.get()
        Logger.info("    Redirected $count string equals calls")
    }

}