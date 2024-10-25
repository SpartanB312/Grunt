package net.spartanb312.grunt.process.transformers.rename

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.event.events.TransformerEvent
import net.spartanb312.grunt.event.listener
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.dot
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.splash
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

/**
 * Reflection support for renamers
 * W.I.P
 */
object ReflectionSupportTransformer : Transformer("ReflectionSupport", Category.Renaming) {

    private val printLog by setting("PrintLog", true)
    private val clazz by setting("Class", true)
    private val method by setting("Method", true)
    private val field by setting("Field", true)

    val methodBlacklist = mutableSetOf<String>()
    val fieldBlacklist = mutableSetOf<String>()
    val strBlacklist = mutableSetOf<String>()

    init {
        listener<TransformerEvent.After> {
            if (it.transformer == ClassRenameTransformer) {
                if (clazz) {
                    it.resourceCache.nonExcluded.forEach { classNode ->
                        classNode.methods.forEach { methodNode ->
                            methodNode.instructions.forEach { insnNode ->
                                if (insnNode is MethodInsnNode) {
                                    val pre = insnNode.previous
                                    val name = insnNode.name
                                    if (insnNode.owner == "java/lang/Class" && insnNode.name == "forName") {
                                        if (pre is LdcInsnNode && pre.cst is String) {
                                            val preName = (pre.cst as String).splash
                                            val mapping = it.resourceCache.getMapping(preName).dot
                                            if (preName != mapping) {
                                                pre.cst = mapping
                                                if (printLog) Logger.info("Remapped reflection: $preName -> $mapping")
                                            }
                                        } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                                    }
                                    if (insnNode.name == "findClass" && insnNode.desc == "(Ljava/lang/String;)Ljava/lang/Class;") {
                                        if (pre is LdcInsnNode && pre.cst is String) {
                                            val preName = (pre.cst as String).splash
                                            val mapping = it.resourceCache.getMapping(preName).dot
                                            if (preName != mapping) {
                                                pre.cst = mapping
                                                if (printLog) Logger.info("Remapped reflection: $preName -> $mapping")
                                            }
                                        } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                                    }
                                    if (insnNode.name == "getResource" && insnNode.desc == "(Ljava/lang/String;)Ljava/net/URL;") {
                                        if (pre is LdcInsnNode && pre.cst is String) {
                                            val preName = pre.cst as String
                                            if (preName.endsWith(".class", true)) {
                                                val clazzName = preName.dropLast(".class".length).splash
                                                val mapping = it.resourceCache.getMapping(clazzName)
                                                if (clazzName != mapping) {
                                                    val newName = "$mapping.class"
                                                    pre.cst = newName
                                                    if (printLog) Logger.info("Remapped resource: $preName -> $newName")
                                                }
                                            }
                                        } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                                    }
                                    if (insnNode.name == "getResourceAsStream" && insnNode.desc == "(Ljava/lang/String;)Ljava/io/InputStream;") {
                                        if (pre is LdcInsnNode && pre.cst is String) {
                                            val preName = pre.cst as String
                                            if (preName.endsWith(".class", true)) {
                                                val clazzName = preName.dropLast(".class".length).splash
                                                val mapping = it.resourceCache.getMapping(clazzName)
                                                if (clazzName != mapping) {
                                                    val newName = "$mapping.class"
                                                    pre.cst = newName
                                                    if (printLog) Logger.info("Remapped resource: $preName -> $newName")
                                                }
                                            }
                                        } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun ResourceCache.transform() {
        methodBlacklist.clear()
        fieldBlacklist.clear()
        strBlacklist.clear()
        allClasses.forEach { classNode ->
            classNode.methods.forEach { methodNode ->
                methodNode.instructions.forEach { insnNode ->
                    // Method and field
                    if (method || field) {
                        if (insnNode is MethodInsnNode && insnNode.opcode == Opcodes.INVOKEVIRTUAL && insnNode.owner == "java/lang/Class") {
                            val pre = insnNode.previous
                            val name = insnNode.name
                            if (method && (name == "getMethod" || name == "getDeclaredMethod")) {
                                val pre2 = pre.previous
                                if (pre2 is LdcInsnNode && pre2.cst is String) {
                                    methodBlacklist.add(pre2.cst as String)
                                } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                            }
                            if (field && (name == "getField" || name == "getDeclaredField")) {
                                if (pre is LdcInsnNode && pre.cst is String) {
                                    fieldBlacklist.add(pre.cst as String)
                                } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                            }
                        }
                    }
                    if (clazz) {
                        if (insnNode is MethodInsnNode) {
                            val pre = insnNode.previous
                            val name = insnNode.name
                            if (insnNode.owner == "java/lang/Class" && insnNode.name == "forName") {
                                if (pre is LdcInsnNode && pre.cst is String) {
                                    strBlacklist.add(pre.cst as String)
                                } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                            }
                            if (insnNode.name == "findClass" && insnNode.desc == "(Ljava/lang/String;)Ljava/lang/Class;") {
                                if (pre is LdcInsnNode && pre.cst is String) {
                                    strBlacklist.add(pre.cst as String)
                                } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                            }
                            if (insnNode.name == "getResource" && insnNode.desc == "(Ljava/lang/String;)Ljava/net/URL;") {
                                if (pre is LdcInsnNode && pre.cst is String) {
                                    strBlacklist.add(pre.cst as String)
                                } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                            }
                            if (insnNode.name == "getResourceAsStream" && insnNode.desc == "(Ljava/lang/String;)Ljava/io/InputStream;") {
                                if (pre is LdcInsnNode && pre.cst is String) {
                                    strBlacklist.add(pre.cst as String)
                                } else if (printLog) Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                            }
                        }
                    }
                }
            }
        }
    }

}