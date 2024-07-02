package net.spartanb312.grunt.process.transformers.optimize

import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isEnum
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodInsnNode

/**
 * Optimize enum casts
 * Last update on 2024/07/02
 */
object EnumOptimizeTransformer : Transformer("EnumOptimize", Category.Optimization) {

    override fun ResourceCache.transform() {
        Logger.info(" - Optimizing enums...")
        val count = count {
            nonExcluded.asSequence()
                .filter { it.isEnum }
                .forEach { classNode ->
                    val desc = "[L${classNode.name};"
                    val valuesMethod = classNode.methods.firstOrNull {
                        it.name == "values" && it.desc == "()$desc" && it.instructions.size() >= 4
                    }

                    if (valuesMethod != null) {
                        for (insn in valuesMethod.instructions.toList()) {
                            if (insn is MethodInsnNode) {
                                if (insn.opcode == Opcodes.INVOKEVIRTUAL && insn.name == "clone") {
                                    if (insn.next.opcode == Opcodes.CHECKCAST) {
                                        valuesMethod.instructions.remove(insn.next)
                                    }
                                    valuesMethod.instructions.remove(insn)
                                    add(1)
                                }
                            }
                        }
                    }
                }
        }.get()
        Logger.info("    Optimized $count enum insn casts")
    }

}