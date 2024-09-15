package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer.createDecryptMethod
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer.encrypt
import net.spartanb312.grunt.process.transformers.redirect.FieldScrambleTransformer
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isNative
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.notInList
import net.spartanb312.grunt.utils.xor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
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

    override fun ResourceCache.transform() {
        val companions = mutableMapOf<ClassNode, MutableList<ConstRef<*>>>()
        val filtered = nonExcluded.filter { it.name.notInList(exclusion) }
        filtered.forEach {
            companions[
                ClassNode().apply {
                    visit(
                        it.version,
                        Opcodes.ACC_PUBLIC,
                        "${it.name}\$ConstantPool",
                        null,
                        "java/lang/Object",
                        null
                    )
                    if (dontScramble) FieldScrambleTransformer.blackList.add(this)
                }
            ] = mutableListOf()
        }
        filtered.forEach { classNode ->
            classNode.methods.forEach { methodNode ->
                if (!methodNode.isAbstract && !methodNode.isNative) {
                    val insnList = insnList {
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
                            } else +insn
                        }
                    }
                    methodNode.instructions = insnList
                }
            }
        }
        companions.forEach { (clazz, refList) ->
            if (refList.isNotEmpty()) {
                addTrashClass(clazz)
                val clinit = method(
                    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                    "<clinit>",
                    "()V",
                    null,
                    null
                ) {
                    InsnList {
                        refList.forEach {
                            it.field.value = null
                            clazz.fields.add(it.field)
                            when (it) {
                                is ConstRef.NumberRef -> {
                                    +xor(it.value as Number)
                                    PUTSTATIC(clazz.name, it.field.name, it.field.desc)
                                }

                                is ConstRef.StringRef -> {
                                    val key = Random.nextInt(0x8, 0x800)
                                    val methodName = getRandomString(10)
                                    val decryptMethod = createDecryptMethod(methodName, key)
                                    clazz.methods.add(decryptMethod)
                                    LDC(encrypt(it.value, key))
                                    +MethodInsnNode(
                                        Opcodes.INVOKESTATIC, clazz.name,
                                        methodName, "(Ljava/lang/String;)Ljava/lang/String;",
                                        false
                                    )
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
    }

    interface ConstRef<T> {
        val field: FieldNode
        val value: T

        interface NumberRef<T : Number> : ConstRef<T>

        class IntRef(override val value: Int) : NumberRef<Int> {
            override val field = FieldNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "const_${getRandomString(15)}",
                "I",
                null,
                value
            )
        }

        class LongRef(override val value: Long) : NumberRef<Long> {
            override val field = FieldNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "const_${getRandomString(15)}",
                "J",
                null,
                value
            )
        }

        class FloatRef(override val value: Float) : NumberRef<Float> {
            override val field = FieldNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "const_${getRandomString(15)}",
                "F",
                null,
                value
            )
        }

        class DoubleRef(override val value: Double) : NumberRef<Double> {
            override val field = FieldNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "const_${getRandomString(15)}",
                "D",
                null,
                value
            )
        }

        class StringRef(override val value: String) : ConstRef<String> {
            override val field = FieldNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "const_${getRandomString(15)}",
                "Ljava/lang/String;",
                null,
                value
            )
        }
    }

}