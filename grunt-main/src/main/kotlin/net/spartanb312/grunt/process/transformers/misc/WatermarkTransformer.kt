package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.builder.ARETURN
import net.spartanb312.grunt.utils.builder.LDC
import net.spartanb312.grunt.utils.builder.method
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.hasAnnotations
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.isExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

/**
 * Generates watermarks in your class
 * Last update on 2024/07/02
 */
object WatermarkTransformer : Transformer("Watermark", Category.Miscellaneous) {

    private val markers by setting(
        "Messages",
        listOf(
            "PROTECTED BY GRUNT KLASS MASTER",
            "PROTECTED BY SPARTAN EVERETT",
            "YOU MUST CHEATED, YOU ARE CHEATER",
            "WHAT CAN I SAY, MAN? MAMBA OUT!",
            "麻的，纪苟，气死我啦",
            "逸一时,误一世,逸久逸久罢已龄",
            "敢这么和我对枪,你护盾是批发的?",
            "有框你不打?你先开的!",
            "nianianiania",
        )
    )

    private val fieldMark by setting("FieldMark", true)
    private val methodMark by setting("MethodMark", true)
    private val annotationMark by setting("AnnotationMark", false)
    private val annotations by setting("Annotations", listOf("ProtectedByGrunt", "JvavMetadata"))
    private val versions by setting("Versions", listOf("114514", "1919810", "69420"))
    private val interfaceMark by setting("InterfaceMark", false)
    private val fatherOfJava by setting("FatherOfJava", "jvav/lang/YuShengJun")
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Adding watermarks...")
        val count = count {
            nonExcluded.asSequence()
                .filter { !it.isInterface && !it.name.isExcludedIn(exclusion) }
                .forEach { classNode ->
                    if (interfaceMark) {
                        classNode.interfaces = classNode.interfaces ?: mutableListOf()
                        classNode.interfaces.add(fatherOfJava)
                        add(1)
                    }
                    if (fieldMark) {
                        classNode.fields = classNode.fields ?: arrayListOf()
                        val marker = markers.random()
                        when ((0..2).random()) {
                            0 -> classNode.fields.add(
                                FieldNode(
                                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                                    "343 industrials sucks",
                                    "Ljava/lang/String;",
                                    null,
                                    marker
                                )
                            )

                            1 -> classNode.fields.add(
                                FieldNode(
                                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                                    "_$marker _",
                                    "I",
                                    null,
                                    114514
                                )
                            )

                            2 -> classNode.fields.add(
                                FieldNode(
                                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                                    "NobleSix is invincible",
                                    "Ljava/lang/String;",
                                    null,
                                    marker
                                )
                            )
                        }
                        add(1)
                    }
                    if (methodMark) {
                        classNode.methods = classNode.methods ?: arrayListOf()
                        val marker = markers.random()
                        when ((0..2).random()) {
                            0 -> classNode.methods.add(
                                method(
                                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                                    "逸一时,误一世",
                                    "()Ljava/lang/String;",
                                    null,
                                    null
                                ) {
                                    InsnList {
                                        LDC(marker)
                                        ARETURN
                                    }
                                }
                            )

                            1 -> classNode.methods.add(
                                method(
                                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                                    "你是一个一个方法名啊",
                                    "()Ljava/lang/String;",
                                    null,
                                    null
                                ) {
                                    InsnList {
                                        LDC(marker)
                                        ARETURN
                                    }
                                }
                            )

                            2 -> classNode.methods.add(
                                method(
                                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                                    "Spartan 1186 is invincible",
                                    "()Ljava/lang/String;",
                                    null,
                                    null
                                ) {
                                    InsnList {
                                        LDC(marker)
                                        ARETURN
                                    }
                                }
                            )
                        }
                        add(1)
                    }
                    if (annotationMark && !classNode.hasAnnotations) {
                        val annotation = AnnotationNode("Lnet/spartanb312/grunt/${annotations.randomOrNull() ?: "ProtectedByGrunt"};")
                        annotation.visit("version", versions.random())
                        annotation.visit("mapping", "jvav/lang/ZhangHaoYangException")
                        annotation.visit("d1", markers.random())
                        annotation.visit("d2", markers.random())
                        classNode.visibleAnnotations = classNode.visibleAnnotations ?: mutableListOf()
                        classNode.visibleAnnotations.add(annotation)
                        add(1)
                    }
                }
        }.get()
        if (interfaceMark) {
            val fatherNode = ClassNode()
            fatherNode.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC + Opcodes.ACC_ABSTRACT + Opcodes.ACC_INTERFACE,
                fatherOfJava,
                null,
                "java/lang/Object",
                null
            )
            addTrashClass(fatherNode)
        }
        Logger.info("    Added $count watermarks")
    }

}