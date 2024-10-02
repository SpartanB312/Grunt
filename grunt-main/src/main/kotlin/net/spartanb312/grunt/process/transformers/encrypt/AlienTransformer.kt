package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.builder.ICONST_1
import net.spartanb312.grunt.utils.builder.IOR
import net.spartanb312.grunt.utils.builder.POP
import net.spartanb312.grunt.utils.builder.insnList
import org.apache.commons.lang3.StringEscapeUtils
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

object AlienTransformer : Transformer("AlienCrack", Category.Encryption) {
    private fun String.escape(): String = StringEscapeUtils.escapeJava(this)

    override fun ResourceCache.transform() {
        var entryClass: ClassNode? = null
        var entryMethod: MethodNode? = null
        var scrambledMethod: MethodNode? = null

        var temp: MethodInsnNode? = null
        classes.forEach { (name, classNode) ->
            if (classNode.interfaces?.contains("net/fabricmc/api/ModInitializer") == true && entryClass == null) { // EntryClass
                entryClass = classNode
                println("Found fabric entry class: ${classNode.name.escape()}")
                val entryMethod0 = classNode.methods.find { it.name == "onInitialize" }!!
                println(entryMethod0.instructions.toList().size)
                entryMethod0.instructions.forEach { insn ->
                    if (insn.opcode == Opcodes.INVOKESTATIC) {
                        val methodInsn = insn as MethodInsnNode
                        println("Found callee from entry method: ${methodInsn.owner.escape()}.${methodInsn.name.escape()}${methodInsn.desc.escape()}")
                        temp = methodInsn

                    }
                }
            }
            if (temp != null && classNode.name == temp!!.owner) {
                scrambledMethod = classNode.methods.find {
//                            println("${it.name.escape()}")
                    it.name == temp!!.name
                }
            }
        }

        scrambledMethod!!.instructions.forEach { insn ->
            if (insn.opcode == Opcodes.INVOKESTATIC) {
                val methodInsn = insn as MethodInsnNode
                println("Found callee from scramble method: ${methodInsn.owner.escape()}.${methodInsn.name.escape()}${methodInsn.desc.escape()}")
                entryMethod = entryClass?.methods?.find { it.name == methodInsn.name && it.desc == methodInsn.desc }!!
            }
        }

        require(entryClass != null && entryMethod != null) { "Failed to find entry class or entry method" }
        entryMethod!!.instructions.toList().forEach { insn ->
            if (insn.opcode == Opcodes.INVOKEVIRTUAL) {
                val methodInsn = insn as MethodInsnNode
                if (methodInsn.name == "contains" && "ArrayList" in methodInsn.owner) {
                    println("Find check call")
                    entryMethod!!.instructions.insert(methodInsn, insnList {
                        POP
                        ICONST_1
                    })
                }
            }
        }
    }
}