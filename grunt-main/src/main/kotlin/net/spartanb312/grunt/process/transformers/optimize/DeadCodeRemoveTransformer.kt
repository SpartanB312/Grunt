package net.spartanb312.grunt.process.transformers.optimize

import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode

/**
 * Remove useless dead codes
 */
object DeadCodeRemoveTransformer : Transformer("DeadCodeRemove", Category.Optimization) {

    override fun ResourceCache.transform() {
        Logger.info("    Removing dead codes...")
        val count = count {
            nonExcluded.forEach { classNode ->
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
        }.get()
        Logger.info("    Removed $count dead codes")
    }

}