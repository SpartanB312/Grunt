package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldNode

object WatermarkTransformer : Transformer("Watermark") {

    private val marker by value("Watermark Message", "PROTECTED BY GRUNT KLASS MASTER")

    override fun ResourceCache.transform() {
        Logger.info(" - Adding watermark fields")
        val count = count {
            nonExcluded.asSequence()
                .filter { !it.isInterface }
                .forEach { classNode ->
                classNode.fields = classNode.fields ?: arrayListOf()
                when ((0..2).random()) {
                    0 -> classNode.fields.add(
                        FieldNode(
                            Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC,
                            "_$marker _",
                            "Ljava/lang/String;",
                            null,
                            marker
                        )
                    )

                    1 -> classNode.fields.add(
                        FieldNode(
                            Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC,
                            "_$marker _",
                            "I",
                            null,
                            null
                        )
                    )

                    2 -> classNode.fields.add(
                        FieldNode(
                            Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC,
                            "NobleSix is invincible",
                            "Ljava/lang/String;",
                            null,
                            marker
                        )
                    )
                }
                add(1)
            }
        }.get()
        Logger.info("    Added $count watermark fields")
    }

}