package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.genesis.*
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.clinit
import net.spartanb312.genesis.kotlin.extensions.FINAL
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.annotation.DISABLE_SCRAMBLE
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer.createDecryptMethod
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer.encrypt
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorClassic
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.appendAnnotation
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LdcInsnNode
import kotlin.random.Random

/**
 * Encrypt constant pool values
 * Last update on 2024/09/13
 */
object ConstPoolEncryptTransformer : Transformer("ConstPollEncrypt", Category.Encryption) {

    private val integer by setting("Integer", true)
    private val long by setting("Long", true)
    private val float by setting("Float", true)
    private val double by setting("Double", true)
    private val string by setting("String", true)
    private val heavyEncrypt by setting("HeavyEncrypt", false)
    private val dontScramble by setting("DontScramble", true)
    private val exclusion by setting("Exclusion", listOf())

    val generatedClasses = mutableListOf<Generated>()

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting constant pools...")
        generatedClasses.clear()
        val companions = mutableMapOf<ClassNode, MutableList<ConstRef<*>>>()
        val filtered = nonExcluded.filter { it.name.notInList(exclusion) }
        filtered.forEach {
            companions[
                clazz(
                    PUBLIC + FINAL,
                    "${it.name}\$ConstantPool",
                    "java/lang/Object"
                ).apply {
                    if (dontScramble) appendAnnotation(DISABLE_SCRAMBLE)
                }
            ] = mutableListOf()
        }
        val count = count {
            filtered.forEach { classNode ->
                classNode.methods.forEach { methodNode ->
                    if (!methodNode.isAbstract && !methodNode.isNative) {
                        val insnList = instructions {
                            methodNode.instructions.forEach { insn ->
                                if (insn is LdcInsnNode) {
                                    val owner = companions.keys.random()
                                    val list = companions[owner]!!
                                    val cst = insn.cst
                                    when {
                                        cst is Int && integer -> ConstRef.IntRef(cst).let {
                                            list.add(it)
                                            GETSTATIC(owner.name, it.field.name, it.field.desc)
                                        }

                                        cst is Long && long -> ConstRef.LongRef(cst).let {
                                            list.add(it)
                                            GETSTATIC(owner.name, it.field.name, it.field.desc)
                                        }

                                        cst is Float && float -> ConstRef.FloatRef(cst).let {
                                            list.add(it)
                                            GETSTATIC(owner.name, it.field.name, it.field.desc)
                                        }

                                        cst is Double && double -> ConstRef.DoubleRef(cst).let {
                                            list.add(it)
                                            GETSTATIC(owner.name, it.field.name, it.field.desc)
                                        }

                                        cst is String && string -> ConstRef.StringRef(cst).let {
                                            list.add(it)
                                            GETSTATIC(owner.name, it.field.name, it.field.desc)
                                        }

                                        else -> +insn
                                    }
                                    add()
                                } else +insn
                            }
                        }
                        methodNode.instructions = insnList
                    }
                }
            }
        }.get()
        companions.forEach { (clazz, refList) ->
            if (refList.isNotEmpty()) {
                addTrashClass(clazz)
                generatedClasses.add(Generated(clazz, buildMap {
                    refList.forEach { this[it.field] = it }
                }))
                val clinit = clinit {
                    INSTRUCTIONS {
                        refList.forEach {
                            it.field.value = null
                            clazz.fields.add(it.field)
                            when (it) {
                                is ConstRef.NumberRef -> {
                                    +NumberEncryptorClassic.encrypt(it.value as Number)
                                    PUTSTATIC(clazz.name, it.field.name, it.field.desc)
                                }

                                is ConstRef.StringRef -> {
                                    val key = Random.nextInt(0x8, 0x800)
                                    val methodName = getRandomString(10)
                                    val decryptMethod = createDecryptMethod(methodName, key)
                                    clazz.methods.add(decryptMethod)
                                    LDC(encrypt(it.value, key))
                                    INVOKESTATIC(clazz.name, methodName, "(Ljava/lang/String;)Ljava/lang/String;")
                                    PUTSTATIC(clazz.name, it.field.name, it.field.desc)
                                }
                            }
                        }
                        RETURN
                    }
                }
                if (heavyEncrypt) {
                    NumberEncryptTransformer.transformMethod(clazz, clinit)
                    StringEncryptTransformer.transformMethod(clazz, clinit)
                }
                clazz.methods.add(clinit)
            }
        }
        Logger.info("    Encrypted $count constants in ${companions.size} pools")
    }

    interface ConstRef<T> {
        val field: FieldNode
        val value: T

        interface NumberRef<T : Number> : ConstRef<T>

        class IntRef(override val value: Int) : NumberRef<Int> {
            override val field = field(
                PUBLIC + STATIC,
                "const_${getRandomString(15)}",
                "I",
                null,
                value
            )
        }

        class LongRef(override val value: Long) : NumberRef<Long> {
            override val field = field(
                PUBLIC + STATIC,
                "const_${getRandomString(15)}",
                "J",
                null,
                value
            )
        }

        class FloatRef(override val value: Float) : NumberRef<Float> {
            override val field = field(
                PUBLIC + STATIC,
                "const_${getRandomString(15)}",
                "F",
                null,
                value
            )
        }

        class DoubleRef(override val value: Double) : NumberRef<Double> {
            override val field = field(
                PUBLIC + STATIC,
                "const_${getRandomString(15)}",
                "D",
                null,
                value
            )
        }

        class StringRef(override val value: String) : ConstRef<String> {
            override val field = field(
                PUBLIC + STATIC,
                "const_${getRandomString(15)}",
                "Ljava/lang/String;",
                null,
                value
            )
        }
    }

    class Generated(val classNode: ClassNode, val mappings: Map<FieldNode, ConstRef<*>>)

}