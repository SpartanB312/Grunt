package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

/**
 * Generates trash classes
 */
object TrashClassTransformer : Transformer("TrashClass", Category.Miscellaneous) {

    private val packages by value("Package", "net/spartanb312/obf/")
    private val prefix by value("Prefix", "Trash")
    private val count by value("Count", 0)

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
            InsnList {
                ALOAD(0)
                DUP
                INVOKESPECIAL("java/util/concurrent/ConcurrentHashMap", "<init>", "()V")
                RETURN
                Maxs(1, 1)
            }
        }
        classNode.methods.add(methodNode)
        classNode.fields.add(fieldNode)
        return classNode
    }

}