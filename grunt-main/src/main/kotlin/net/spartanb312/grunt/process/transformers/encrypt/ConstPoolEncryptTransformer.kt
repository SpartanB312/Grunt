package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.genesis.kotlin.*
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.grunt.annotation.DISABLE_SCRAMBLE
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.number.NumberEncryptorClassic
import net.spartanb312.grunt.process.transformers.misc.NativeCandidateTransformer
import net.spartanb312.grunt.process.transformers.redirect.InvokeDynamicTransformer
import net.spartanb312.grunt.process.transformers.rename.ReflectionSupportTransformer
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.appendAnnotation
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.notInList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LdcInsnNode
import kotlin.random.Random

/**
 * Encrypt constant pool values
 * Last update on 2024/10/28
 */
object ConstPoolEncryptTransformer : Transformer("ConstPollEncrypt", Category.Encryption) {

    private val integer by setting("Integer", true)
    private val long by setting("Long", true)
    private val float by setting("Float", true)
    private val double by setting("Double", true)
    private val string by setting("String", true)
    private val heavyEncrypt by setting("HeavyEncrypt", false)
    private val dontScramble by setting("DontScramble", true)
    private val nativeAnnotation by setting("NativeAnnotation", false)
    private val exclusion by setting("Exclusion", listOf())

    private val String.reflectionExcluded
        get() = ReflectionSupportTransformer.enabled && ReflectionSupportTransformer.strBlacklist.contains(this)

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
                                if (insn.opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5) {
                                    if (integer) {
                                        val owner = companions.keys.random()
                                        val list = companions[owner]!!
                                        val value = insn.opcode - Opcodes.ICONST_0
                                        ConstRef.IntRef(value).let {
                                            list.add(it)
                                            GETSTATIC(owner.name, it.field.name, it.field.desc)
                                        }
                                        add()
                                    }
                                }
                                else if (insn.opcode in Opcodes.LCONST_0..Opcodes.LCONST_1) {
                                    if (long) {
                                        val owner = companions.keys.random()
                                        val list = companions[owner]!!
                                        val value = insn.opcode - Opcodes.LCONST_0
                                        ConstRef.LongRef(value.toLong()).let {
                                            list.add(it)
                                            GETSTATIC(owner.name, it.field.name, it.field.desc)
                                        }
                                        add()
                                    }
                                }
                                else if (insn is LdcInsnNode) {
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

                                        cst is String && string -> {
                                            if (!cst.reflectionExcluded) ConstRef.StringRef(cst).let {
                                                list.add(it)
                                                GETSTATIC(owner.name, it.field.name, it.field.desc)
                                            } else +insn
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
                addClass(clazz)
                generatedClasses.add(Generated(clazz, buildMap {
                    refList.forEach { this[it.field] = it }
                }))
                val insnList = instructions {
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
                                val decryptMethod = InvokeDynamicTransformer.createDecryptMethod(methodName, key)
                                clazz.methods.add(decryptMethod)
                                LDC(InvokeDynamicTransformer.encrypt(it.value, key))
                                INVOKESTATIC(clazz.name, methodName, "(Ljava/lang/String;)Ljava/lang/String;")
                                PUTSTATIC(clazz.name, it.field.name, it.field.desc)
                            }
                        }
                    }
                    RETURN
                }
                val clinit = if (nativeAnnotation) {
                    val decryptMethod = method(
                        PRIVATE + STATIC + SYNTHETIC + BRIDGE,
                        "decrypt",
                        "()V"
                    ) { INSTRUCTIONS { +insnList } }
                    if (heavyEncrypt) {
                        NumberEncryptTransformer.transformMethod(clazz, decryptMethod)
                        StringEncryptTransformer.transformMethod(clazz, decryptMethod)
                    }
                    NativeCandidateTransformer.appendedMethods.add(decryptMethod)
                    decryptMethod.visitAnnotation(NativeCandidateTransformer.annotation, false)
                    clazz.methods.add(decryptMethod)
                    clinit {
                        INSTRUCTIONS {
                            INVOKESTATIC(clazz, decryptMethod)
                            RETURN
                        }
                    }
                } else clinit { INSTRUCTIONS { +insnList } }
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