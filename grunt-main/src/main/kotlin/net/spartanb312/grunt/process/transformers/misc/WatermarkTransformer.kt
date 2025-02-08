package net.spartanb312.grunt.process.transformers.misc

import javassist.ClassPool
import javassist.CtNewMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.genesis.kotlin.annotation
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.ARETURN
import net.spartanb312.genesis.kotlin.extensions.insn.LDC
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.hasAnnotations
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

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
    private val customTrashMethod by setting("CustomTrashMethod", false)
    private val customMethodName by setting("CustomMethodName", "protected by YuShengJun")
    private val customMethodCode by setting("CustomMethodCode", """
        public static String method() {
            return "Protected by YuShengJun";
        }
    """.trimIndent())
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Adding watermarks...")
        val classPool = ClassPool.getDefault()
        val count = count {
            runBlocking {
                nonExcluded.asSequence()
                    .filter { !it.isInterface && it.name.notInList(exclusion) }
                    .forEach { classNode ->
                        fun job() {
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
                                        field(
                                            PRIVATE + STATIC,
                                            names.random(),
                                            "Ljava/lang/String;",
                                            null,
                                            marker
                                        )
                                    )

                                    1 -> classNode.fields.add(
                                        field(
                                            PRIVATE + STATIC,
                                            "_$marker _",
                                            "I",
                                            null,
                                            listOf(114514, 1919810, 69420, 911, 8964).random()
                                        )
                                    )

                                    2 -> classNode.fields.add(
                                        field(
                                            PRIVATE + STATIC,
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
                                            PRIVATE + STATIC,
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
                                            PRIVATE + STATIC,
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
                                            PRIVATE + STATIC,
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

                            if (customTrashMethod && !classNode.isInterface) {
                                val ctClass = classPool.makeClass(classNode.name)
                                val trashMethod = CtNewMethod.make(customMethodCode, ctClass)
                                ctClass.addMethod(trashMethod)
                                val bytes = ctClass.toBytecode()
                                val tempClassNode = ClassNode()
                                ClassReader(bytes).accept(tempClassNode, ClassReader.EXPAND_FRAMES)
                                val newMethod = tempClassNode.methods.find { it.name == trashMethod.name }!!
                                newMethod.name = customMethodName
                                classNode.methods.add(newMethod)
                            }
                        }
                        if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                    }
            }
        }.get()
        if (interfaceMark) addClass(
            clazz(
                PUBLIC + ABSTRACT + INTERFACE,
                fatherOfJava,
                "java/lang/Object",
                null
            )
        )
        Logger.info("    Added $count watermarks")
    }

}