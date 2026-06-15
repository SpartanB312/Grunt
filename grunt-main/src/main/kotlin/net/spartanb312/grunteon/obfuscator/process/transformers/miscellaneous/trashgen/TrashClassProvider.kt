package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.trashgen

import org.objectweb.asm.tree.ClassNode

interface TrashClassProvider {
    val id: String

    fun generate(context: TrashClassProviderContext): List<ClassNode>
}
