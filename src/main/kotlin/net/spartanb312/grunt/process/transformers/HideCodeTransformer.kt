package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

// Invalid for recaf
object HideCodeTransformer : Transformer("HideCode") {

    override fun ResourceCache.transform() {
        val count = count {
            nonExcluded.forEach { classNode ->
                pushSynthetic(classNode)
                pushBridge(classNode)
            }
        }.get()
        Logger.info(" - Inserted $count synthetic/bridge")
    }

    private fun Counter.pushSynthetic(classNode: ClassNode) {
        if (Opcodes.ACC_SYNTHETIC and classNode.access == 0 && !classNode.hasAnnotations) {
            classNode.access = classNode.access or Opcodes.ACC_SYNTHETIC
        }

        classNode.methods.asSequence()
            .filter { Opcodes.ACC_SYNTHETIC and it.access == 0 }
            .forEach {
                it.access = it.access or Opcodes.ACC_SYNTHETIC
                add(1)
            }

        classNode.fields.asSequence()
            .filter { Opcodes.ACC_SYNTHETIC and it.access == 0 && !it.hasAnnotations }
            .forEach { fieldNode: FieldNode ->
                fieldNode.access = fieldNode.access or Opcodes.ACC_SYNTHETIC
                add(1)
            }
    }

    private fun Counter.pushBridge(classNode: ClassNode) {
        classNode.methods.asSequence()
            .filter { !it.isInitializer && !it.isAbstract && it.access and Opcodes.ACC_BRIDGE == 0 }
            .forEach {
                if (Opcodes.ACC_BRIDGE and it.access == 0) {
                    it.access = it.access or Opcodes.ACC_BRIDGE
                    add(1)
                }
            }
    }

}