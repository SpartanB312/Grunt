package net.spartanb312.grunt.process.transformers.misc

import net.spartanb312.genesis.kotlin.annotation
import net.spartanb312.genesis.kotlin.extensions.insn.ARETURN
import net.spartanb312.genesis.kotlin.extensions.insn.LDC
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.hasAnnotations
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

/**
 * Generates watermarks
 * Last update on 2024/07/11
 */
object WatermarkTransformer : Transformer("Watermark", Category.Miscellaneous) {

    private val names by setting(
        "Names",
        listOf(
            "I AM WATERMARK",
            "CYKA BLYAT",
            "NAME",
        )
    )
    private val markers by setting(
        "Messages",
        listOf(
            "PROTECTED BY GRUNT KLASS MASTER",
            "PROTECTED BY SPARTAN EVERETT",
            "PROTECTED BY SPARTAN 1186",
            "PROTECTED BY NOBLE SIX",
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
                .filter { !it.isInterface && it.name.notInList(exclusion) }
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
                                    names.random(),
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
                                    listOf(114514, 1919810, 69420, 911, 8964).random()
                                )
                            )

                            2 -> classNode.fields.add(
                                FieldNode(
                                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                                    names.random(),
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
                                    names.random(),
                                    "()Ljava/lang/String;"
                                ) {
                                    INSTRUCTIONS {
                                        LDC(marker)
                                        ARETURN
                                    }
                                }
                            )

                            1 -> classNode.methods.add(
                                method(
                                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                                    names.random(),
                                    "()Ljava/lang/String;"
                                ) {
                                    INSTRUCTIONS {
                                        LDC(marker)
                                        ARETURN
                                    }
                                }
                            )

                            2 -> classNode.methods.add(
                                method(
                                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                                    names.random(),
                                    "()Ljava/lang/String;"
                                ) {
                                    INSTRUCTIONS {
                                        LDC(marker)
                                        ARETURN
                                    }
                                }
                            )
                        }
                        add(1)
                    }
                    if (annotationMark && !classNode.hasAnnotations) {
                        val annotation =
                            annotation("Lnet/spartanb312/grunt/${annotations.randomOrNull() ?: "ProtectedByGrunt"};") {
                                this["version"] = versions.random()
                                this["mapping"] = "jvav/lang/ZhangHaoYangException"
                                this["d1"] = markers.random()
                                this["d2"] = markers.random()
                            }
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