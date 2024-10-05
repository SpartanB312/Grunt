package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.genesis.extensions.insn.ALOAD
import net.spartanb312.genesis.extensions.insn.DUP
import net.spartanb312.genesis.extensions.insn.INVOKESPECIAL
import net.spartanb312.genesis.extensions.insn.RETURN
import net.spartanb312.genesis.method
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

/**
 * Generates trash classes
 * Last update on 2024/06/26
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

    private fun generateClass(name: String, type: Int): ClassNode {
        val classNode = ClassNode()
        when (type) {
            0 -> classNode.visit(
                Opcodes.V1_8,
                Opcodes.ACC_SUPER + Opcodes.ACC_PUBLIC,
                name,
                null,
                "java/util/concurrent/ConcurrentHashMap",
                null
            )

            1 -> classNode.visit(
                Opcodes.V1_8,
                Opcodes.ACC_SUPER + Opcodes.ACC_PUBLIC,
                name,
                null,
                "java/util/concurrent/ConcurrentLinkedDeque",
                null
            )

            2 -> classNode.visit(
                Opcodes.V1_8,
                Opcodes.ACC_SUPER + Opcodes.ACC_PUBLIC,
                name,
                null,
                "java/util/concurrent/ConcurrentSkipListMap",
                null
            )
        }
        val fieldNode = FieldNode(
            Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
            "c",
            "I",
            null,
            8964
        )
        val methodNode = method(
            Opcodes.ACC_PUBLIC,
            "<init>",
            "()V",
            null,
            null
        ) {
            INSTRUCTIONS {
                ALOAD(0)
                DUP
                INVOKESPECIAL("java/util/concurrent/ConcurrentHashMap", "<init>", "()V")
                RETURN
            }
            MAXS(1, 1)
        }
        classNode.methods.add(methodNode)
        classNode.fields.add(fieldNode)
        return classNode
    }

}