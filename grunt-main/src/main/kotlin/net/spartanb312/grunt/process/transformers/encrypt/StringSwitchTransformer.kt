package net.spartanb312.grunt.process.transformers.encrypt

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.MethodProcessor
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.random.Random

object StringSwitchTransformer : Transformer("StringSwitch", Category.Encryption), MethodProcessor {

    private val times by setting("Intensity", 1)
    private val exclusion by setting("Exclusions", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Encrypting strings...")
        val count = count {
            repeat(times) { t ->
                if (times > 1) Logger.info("    Encrypting strings ${t + 1} of $times times")
                nonExcluded.asSequence()
                    .filter { c ->
                        !c.isInterface
                                && c.version > Opcodes.V1_5
                                && exclusion.none {
                            c.name.startsWith(it)
                        }
                    }.forEach { classNode ->
                        classNode.methods.toList().forEach { methodNode ->
                            if (EncryptionMethod.FlattenedSwitch.transform(classNode, methodNode)) add()
                        }
                    }
            }
        }.get()
        Logger.info("    Encrypted $count strings")
    }

    override fun transformMethod(owner: ClassNode, method: MethodNode) {
        EncryptionMethod.FlattenedSwitch.transform(owner, method)
    }

    enum class EncryptionMethod {
        FlattenedSwitch {
            override fun transform(owner: ClassNode, method: MethodNode): Boolean {
                var transformed = false
                method.instructions.toList().forEach insn@{ insn ->
                    if ((insn is LdcInsnNode && insn.cst is String)) {
                        val (decrypt, newString) =
                            generateDecryptMethod(
                                insn.cst as String,
                                owner,
                            ) ?: return@insn

                        method.instructions.insertBefore(insn, insnList {
                            LDC(newString)
                            INVOKESTATIC(owner.name, decrypt.name, decrypt.desc)
                        })
                        method.instructions.remove(insn)
                        transformed = true
                    }
                }
                return transformed
            }

            private fun generateDecryptMethod(
                rawString: String,
                classNode: ClassNode
            ): Pair<MethodNode, String>? {
                if (rawString.length <= 1) return null

                val keys = buildList { repeat(rawString.length) { add(Random.nextInt()) } }
                val encryptedString = buildString {
                    rawString.forEachIndexed { i, c ->
                        append((c.code xor keys[i]).toChar())
                    }
                }
                require(encryptedString.length == rawString.length)
                val cases = rawString.indices.toList().shuffled()
                val casesMapping = mutableMapOf<Int, InsnList>()
                // here instancing LabelNode directly is soundness because we only use each node for one time.
                val labels = Array(cases.size) { LabelNode() }
                val decryptMethod = method(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    getRandomString(10), "(Ljava/lang/String;)Ljava/lang/String;"
                ) {
                    var currentChar = 0
                    var currentKey: Int
                    val dispatchLabel = LabelNode()
                    InsnList {
                        // 0 -> encrypted string
                        // 1 -> stringbuilder
                        // 2 -> current char's decrypt key(transformed before decrypt in each switch case)
                        // 3 -> program counter
                        NEW("java/lang/StringBuilder")
                        DUP
                        INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V")
                        ASTORE(1)
                        LDC(keys[currentChar])
                        ISTORE(2) // decrypt key
                        LDC(cases[currentChar])
                        ISTORE(3) // pc
                        +dispatchLabel
                        ILOAD(3)
                        +TableSwitchInsnNode(0, rawString.length - 1, labels[cases.last()], *labels)
                    }

                    casesMapping[cases[currentChar]] = insnList {
                        // the first case uses decrypt key directly
                        +labels[cases[currentChar]]
                        ALOAD(1) // builder
                        ALOAD(0) // raw str
                        require(currentChar == 0)
                        LDC(currentChar)
                        INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C")
                        ILOAD(2)
                        IXOR
                        INVOKEVIRTUAL(
                            "java/lang/StringBuilder",
                            "append", "(C)Ljava/lang/StringBuilder;"
                        )
                        POP

                        currentChar++
                        LDC(cases[currentChar])
                        ISTORE(3) // pc
                        GOTO(dispatchLabel)
                    }

                    while (currentChar < rawString.length - 1) {
                        casesMapping[cases[currentChar]] = insnList {
                            +labels[cases[currentChar]]
                            LDC(keys[currentChar])
                            ISTORE(2)
                            currentKey = keys[currentChar]
                            require(currentKey == keys[currentChar])

                            ALOAD(1)
                            ALOAD(0)
                            LDC(currentChar)
                            INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C")
                            ILOAD(2) // key
                            IXOR
                            INVOKEVIRTUAL(
                                "java/lang/StringBuilder",
                                "append", "(C)Ljava/lang/StringBuilder;"
                            )
                            POP
                            currentChar++
                            LDC(cases[currentChar])
                            ISTORE(3) // pc
                            GOTO(dispatchLabel)
                        }
                    }

                    casesMapping.toList().sortedBy { it.first }.forEach { (_, insnList) ->
                        +insnList
                    }

                    InsnList {
                        +labels[cases.last()]
                        LDC(keys.last())
                        ISTORE(2)
                        currentKey = keys.last()
                        require(currentKey == keys.last())
                        ALOAD(1)
                        ALOAD(0)
                        LDC(currentChar)
                        INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C")
                        ILOAD(2)
                        IXOR
                        INVOKEVIRTUAL(
                            "java/lang/StringBuilder",
                            "append", "(C)Ljava/lang/StringBuilder;"
                        )
                        POP
                        ALOAD(1)
                        INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
                        ARETURN
                    }
                }
                classNode.methods.add(decryptMethod)
                return decryptMethod to encryptedString
            }
        };

        abstract fun transform(owner: ClassNode, method: MethodNode): Boolean
    }
}