package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.genesis.kotlin.clinit
import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.LONG
import net.spartanb312.genesis.kotlin.extensions.insn.BIPUSH
import net.spartanb312.genesis.kotlin.extensions.insn.LDC
import net.spartanb312.genesis.kotlin.extensions.insn.PUTFIELD
import net.spartanb312.genesis.kotlin.extensions.insn.PUTSTATIC
import net.spartanb312.genesis.kotlin.extensions.insn.SIPUSH
import net.spartanb312.genesis.kotlin.init
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isAnnotation
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import java.lang.reflect.Modifier

/**
 * Fields will be — instead of the field having a constant value —
 * initialized in the '<clinit>' or '<init>' of the respective class.
 * This allows other transformers to transform these final fields.
 *
 * @author jonesdevelopment
 */
object DeclareFieldsTransformer : Transformer("HideDeclaredFields", Category.Miscellaneous) {

    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Hiding declared fields...")
        val count = count {
            nonExcluded.asSequence()
                .filter { !it.isAnnotation && it.name.notInList(exclusion) }
                .forEach { classNode ->
                    var clinit = classNode.methods.firstOrNull { it.name.equals("<clinit>") }
                    var init = classNode.methods.firstOrNull { it.name.equals("<init>") }
                    for (field in classNode.fields) {
                        if (field.value != null) {
                            if (Modifier.isStatic(field.access)) {
                                if (clinit == null) {
                                    clinit = clinit()
                                    clinit.instructions.add(InsnNode(RETURN))
                                    classNode.methods.add(clinit)
                                }
                                clinit.instructions.insert(box(classNode, field, true))
                            } else {
                                if (init == null) {
                                    init = init()
                                    init.instructions.add(InsnNode(RETURN))
                                    classNode.methods.add(init)
                                }
                                init.instructions.insert(box(classNode, field, false))
                            }
                            field.value = null
                            add()
                        }
                    }
                }
        }.get()
        Logger.info("    Hid $count declared fields")
    }

    fun box(owner: ClassNode, field: FieldNode, static: Boolean): InsnList {
        return instructions {
            when (field.desc) {
                "I" -> INT(field.value as Int)
                "J" -> LONG(field.value as Long)
                "B" -> BIPUSH(field.value as Int)
                "S" -> SIPUSH(field.value as Int)
                else -> LDC(field.value)
            }
            if (static) {
                PUTSTATIC(owner.name, field.name, field.desc)
            } else {
                PUTFIELD(owner.name, field.name, field.desc)
            }
        }
    }
}