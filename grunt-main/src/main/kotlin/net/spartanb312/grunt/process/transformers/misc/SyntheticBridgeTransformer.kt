package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.Counter
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.hasAnnotations
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isAnnotation
import net.spartanb312.grunt.utils.extensions.isInitializer
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

/**
 * Insert synthetic and bridge to hide members
 * Last update on 2024/06/26
 */
object SyntheticBridgeTransformer : Transformer("SyntheticBridge", Category.Miscellaneous) {

    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Inserting synthetic/bridge...")
        val count = count {
            nonExcluded.asSequence()
                .filter { !it.isAnnotation && it.name.notInList(exclusion) }
                .forEach { classNode ->
                    pushSynthetic(classNode)
                    pushBridge(classNode)
                }
        }.get()
        Logger.info("    Inserted $count synthetic/bridge")
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