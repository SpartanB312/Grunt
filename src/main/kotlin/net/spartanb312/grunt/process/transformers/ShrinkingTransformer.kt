package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.isNotExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

object ShrinkingTransformer : Transformer("Shrinking") {

    private val removeInnerClass by value("RemoveInnerClass", true)
    private val removeUnusedLabel by value("RemoveUnusedLabel", true)

    private val exclusions by value("Exclusions", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Shrinking classes...")
        val nonExcluded = nonExcluded.filter { it.name.isNotExcludedIn(exclusions) }
        if (removeInnerClass) {
            val innerClassCount = count {
                nonExcluded.forEach { classNode ->
                    classNode.outerClass = null
                    classNode.outerMethod = null
                    classNode.outerMethodDesc = null
                    add(classNode.innerClasses.size)
                    classNode.innerClasses.clear()
                }
            }.get()
            Logger.info("    Removed $innerClassCount inner classes")
        }
        //if (removeNOP) {
        //    val nopCount = count {
        //        nonExcluded.forEach { classNode ->
        //            classNode.methods.forEach { methodNode ->
        //                methodNode.instructions.toList().asSequence()
        //                    .filter { insnNode -> insnNode.opcode == Opcodes.NOP && insnNode is InsnNode }
        //                    .forEach { insnNode ->
        //                        methodNode.instructions.remove(insnNode)
        //                        add(1)
        //                    }
        //            }
        //        }
        //    }.get()
        //    Logger.info("    Removed $nopCount NOPs")
        //}
        if (removeUnusedLabel) {
            val labelCount = count {
                nonExcluded.forEach { classNode ->
                    classNode.methods.forEach { methodNode ->
                        val labels = mutableListOf<LabelNode>()
                        methodNode.instructions.forEach { if (it is LabelNode) labels.add(it) }
                        methodNode.instructions.forEach { insnNode ->
                            when (insnNode) {
                                is JumpInsnNode -> labels.remove(insnNode.label)
                                is LookupSwitchInsnNode -> {
                                    labels.remove(insnNode.dflt)
                                    labels.removeAll(insnNode.labels)
                                }

                                is TableSwitchInsnNode -> {
                                    labels.remove(insnNode.dflt)
                                    labels.removeAll(insnNode.labels)
                                }

                                is FrameNode -> {
                                    insnNode.local?.forEach { if (it is LabelNode) labels.remove(it) }
                                    insnNode.stack?.forEach { if (it is LabelNode) labels.remove(it) }
                                }
                            }
                        }
                        methodNode.localVariables?.forEach {
                            labels.remove(it.start)
                            labels.remove(it.end)
                        }
                        methodNode.tryCatchBlocks?.forEach {
                            labels.remove(it.start)
                            labels.remove(it.end)
                            labels.remove(it.handler)
                        }
                        labels.forEach { methodNode.instructions.remove(it) }
                        add(labels.size)
                    }
                }
            }.get()
            Logger.info("    Removed $labelCount unused labels")
        }
    }

}