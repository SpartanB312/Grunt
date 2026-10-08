package net.spartanb312.grunt.ir.test

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

internal fun loadTestClass(owner: String, method: MethodNode): Class<*> {
    val node = ClassNode().apply {
        version = Opcodes.V17
        access = Opcodes.ACC_PUBLIC
        name = owner
        superName = "java/lang/Object"
        methods.add(method)
    }
    val writer = object : ClassWriter(COMPUTE_FRAMES or COMPUTE_MAXS) {
        override fun getCommonSuperClass(type1: String, type2: String): String = "java/lang/Object"
    }
    node.accept(writer)
    val bytes = writer.toByteArray()
    return object : ClassLoader(MethodNode::class.java.classLoader) {
        fun load(): Class<*> = defineClass(owner.replace('/', '.'), bytes, 0, bytes.size)
    }.load()
}