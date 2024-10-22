package net.spartanb312.grunt.process.transformers.optimize

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode

/**
 * Remove useless dead codes
 * Last update on 2024/07/02
 */
object DeadCodeRemoveTransformer : Transformer("DeadCodeRemove", Category.Optimization) {

    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Removing dead codes...")
        val count = count {
            runBlocking {
                nonExcluded.asSequence()
                    .filter { it.name.notInList(exclusion) }
                    .forEach { classNode ->
                        fun job() {
                            classNode.methods.toList().asSequence()
                                .filter { !it.isNative && !it.isAbstract }
                                .forEach { methodNode ->
                                    for (it in methodNode.instructions.toList()) {
                                        if (it is InsnNode) {
                                            if (it.opcode == Opcodes.POP) {
                                                val pre = it.previous ?: continue
                                                if (pre.opcode == Opcodes.ILOAD
                                                    || pre.opcode == Opcodes.FLOAD
                                                    || pre.opcode == Opcodes.ALOAD
                                                ) {
                                                    methodNode.instructions.remove(pre)
                                                    methodNode.instructions.remove(it)
                                                    add(2)
                                                }
                                            } else if (it.opcode == Opcodes.POP2) {
                                                val pre = it.previous ?: continue
                                                if (pre.opcode == Opcodes.DLOAD
                                                    || pre.opcode == Opcodes.LLOAD
                                                ) {
                                                    methodNode.instructions.remove(pre)
                                                    methodNode.instructions.remove(it)
                                                    add(2)
                                                } else if (pre.opcode == Opcodes.ILOAD
                                                    || pre.opcode == Opcodes.FLOAD
                                                    || pre.opcode == Opcodes.ALOAD
                                                ) {
                                                    val prePre = pre.previous ?: continue
                                                    if (prePre.opcode == Opcodes.ILOAD
                                                        || prePre.opcode == Opcodes.FLOAD
                                                        || prePre.opcode == Opcodes.ALOAD
                                                    ) {
                                                        methodNode.instructions.remove(prePre)
                                                        methodNode.instructions.remove(pre)
                                                        methodNode.instructions.remove(it)
                                                        add(3)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                        if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                    }
            }
        }.get()
        Logger.info("    Removed $count dead codes")
    }

}