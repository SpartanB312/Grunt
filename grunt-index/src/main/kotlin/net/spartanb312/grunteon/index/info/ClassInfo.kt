package net.spartanb312.grunteon.index.info

import org.objectweb.asm.tree.ClassNode

data class ClassInfo(
    val access: Int,
    val name: String,
    val superName: String?,
    val interfaces: List<String>?
) {
    var methods = listOf<MethodInfo>()
    var fields = listOf<FieldInfo>()

    companion object {
        @JvmStatic
        fun parse(classNode: ClassNode): ClassInfo {
            return ClassInfo(
                classNode.access,
                classNode.name,
                classNode.superName,
                classNode.interfaces
            ).apply {
                methods = classNode.methods.map { MethodInfo(it.access, it.name, it.desc) }
                fields = classNode.fields.map { FieldInfo(it.access, it.name, it.desc) }
            }
        }
    }

}