package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.SUPER
import net.spartanb312.genesis.kotlin.extensions.insn.ALOAD
import net.spartanb312.genesis.kotlin.extensions.insn.DUP
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESPECIAL
import net.spartanb312.genesis.kotlin.extensions.insn.RETURN
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.genesis.kotlin.modify
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode

/**
 * Generates trash classes
 * Last update on 2024/10/12
 */
object TrashClassTransformer : Transformer("TrashClass", Category.Miscellaneous) {

    private val packages by setting("Package", "net/spartanb312/obf/")
    private val prefix by setting("Prefix", "Trash")
    private val count by setting("Count", 0)

    override fun ResourceCache.transform() {
        Logger.info(" - Generating trash classes...")
        val trashClasses = mutableListOf<ClassNode>()
        repeat(count) {
            val name = packages + prefix + getRandomString(5)
            val generated = generateClass(name, (0..2).random())
            trashClasses.add(generated)
        }
        trashClasses.forEach { addTrashClass(it) }
        Logger.info("    Generated ${trashClasses.size} trash classes")
    }

    private fun generateClass(name: String, type: Int): ClassNode = when (type) {
        0 -> clazz(
            PUBLIC + SUPER,
            name,
            "java/util/concurrent/ConcurrentHashMap"
        )

        1 -> clazz(
            PUBLIC + SUPER,
            name,
            "java/util/concurrent/ConcurrentLinkedDeque"
        )

        else -> clazz(
            PUBLIC + SUPER,
            name,
            "java/util/concurrent/ConcurrentSkipListMap"
        )
    }.modify {
        +field(
            PUBLIC + STATIC,
            "c",
            "I",
            null,
            8964
        )
        +method(
            PUBLIC,
            "<init>",
            "()V"
        ) {
            INSTRUCTIONS {
                ALOAD(0)
                DUP
                INVOKESPECIAL("java/util/concurrent/ConcurrentHashMap", "<init>", "()V")
                RETURN
            }
            MAXS(1, 1)
        }
    }

}