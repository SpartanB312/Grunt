package net.spartanb312.grunt.process.transformers.flow

import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.encrypt.ArithmeticEncryptTransformer
import net.spartanb312.grunt.utils.*
import net.spartanb312.grunt.utils.builder.*
import net.spartanb312.grunt.utils.extensions.isInitializer
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.random.Random

/**
 * Replace direct jump to implicit jump
 * Last update on 24/07/07
 */
object ImplicitJumpTransformer : Transformer("ImplicitJump", Category.Controlflow) {

    private val replaceGoto by setting("ReplaceGoto", true)
    private val replaceIf by setting("ReplaceIf", true)
    private val junkCode by setting("JunkCode", true)
    private val exclusion by setting("Exclusion", listOf())

    private val staticUtilList = mutableSetOf<TrashCallMethod>()

    override fun ResourceCache.transform() {
        Logger.info(" - Replacing jumps to implicit operations")
        staticUtilList.addAll(generateUtilList(allClasses))
        val count = count {
            nonExcluded.asSequence()
                .filter { !it.name.isExcludedIn(exclusion) }
                .forEach { classNode ->
                    classNode.methods.forEach { methodNode ->
                        add(processMethodNode(methodNode))
                    }
                }
        }
        Logger.info("    Replaced ${count.get()} jumps")
    }

    fun processMethodNode(methodNode: MethodNode): Int {
        var count = 0
        if (replaceGoto) {
            val newInsn = insnList {
                val returnType = Type.getReturnType(methodNode.desc)
                methodNode.instructions.forEach { insnNode ->
                    if (insnNode is JumpInsnNode && insnNode.opcode == Opcodes.GOTO) {
                        +replaceGoto(insnNode.label, methodNode, returnType)
                        count++
                    } else +insnNode
                }
            }
            methodNode.instructions = newInsn
        }
        if (replaceIf) {
            val newInsn = insnList {
                val returnType = Type.getReturnType(methodNode.desc)
                methodNode.instructions.forEach { insnNode ->
                    if (insnNode is JumpInsnNode && (insnNode.opcode == Opcodes.IF_ICMPEQ
                                || insnNode.opcode == Opcodes.IF_ICMPLT
                                || insnNode.opcode == Opcodes.IF_ICMPGE
                                || insnNode.opcode == Opcodes.IF_ICMPGT
                                || insnNode.opcode == Opcodes.IF_ICMPLE
                                || insnNode.opcode == Opcodes.IF_ICMPNE)
                    ) {
                        +replaceIf(insnNode, insnNode.label, methodNode, returnType)
                        count++
                    } else +insnNode
                }
            }
            methodNode.instructions = newInsn
        }
        return count
    }

    private fun replaceIf(
        insnNode: JumpInsnNode,
        targetLabel: LabelNode,
        methodNode: MethodNode,
        returnType: Type
    ): InsnList {
        return insnList {
            val delegateLabel = Label()
            val elseLabel = Label()
            +JumpInsnNode(insnNode.opcode, getLabelNode(delegateLabel))
            GOTO(elseLabel)
            LABEL(delegateLabel)
            +replaceGoto(targetLabel, methodNode, returnType)
            LABEL(elseLabel)
        }
    }

    private fun replaceGoto(
        targetLabel: LabelNode,
        methodNode: MethodNode,
        returnType: Type
    ): InsnList {
        return when (Random.nextInt(7)) {
            0 -> insnList {
                val val1 = Random.nextInt(Int.MAX_VALUE / 2)
                val val2 = Random.nextInt(Int.MAX_VALUE / 2)
                INT(val1)
                INT(val2)
                +ArithmeticEncryptTransformer.generateIXOR()
                INT(val1 xor val2)
                IF_ICMPEQ(targetLabel)
                +generateTrashCode(methodNode, returnType)
            }

            1 -> insnList {
                val val1 = Random.nextInt(Int.MAX_VALUE / 2)
                val val2 = Random.nextInt(Int.MAX_VALUE / 2)
                INT(val1)
                INT(val2)
                +ArithmeticEncryptTransformer.generateIOR()
                INT(val1 or val2)
                IF_ICMPEQ(targetLabel)
                +generateTrashCode(methodNode, returnType)
            }

            2 -> insnList {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) >= val3)
                INT(val1)
                INT(val2)
                +action.insnList
                INT(val3)
                IF_ICMPLT(targetLabel)
                +generateTrashCode(methodNode, returnType)

            }

            3 -> insnList {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) < val3)
                INT(val1)
                INT(val2)
                +action.insnList
                INT(val3)
                IF_ICMPGE(targetLabel)
                +generateTrashCode(methodNode, returnType)

            }

            4 -> insnList {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) <= val3)
                INT(val1)
                INT(val2)
                +action.insnList
                INT(val3)
                IF_ICMPGT(targetLabel)
                +generateTrashCode(methodNode, returnType)

            }

            5 -> insnList {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) > val3)
                INT(val1)
                INT(val2)
                +action.insnList
                INT(val3)
                IF_ICMPLE(targetLabel)
                +generateTrashCode(methodNode, returnType)

            }

            else -> insnList {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) == val3)
                INT(val1)
                INT(val2)
                +action.insnList
                INT(val3)
                IF_ICMPNE(targetLabel)
                +generateTrashCode(methodNode, returnType)
            }
        }
    }

    private fun findRandomGivenReturnTypeMethod(sort: Int): TrashCallMethod? {
        return staticUtilList.filter { it.returnType.sort == sort }.randomOrNull()
    }

    private fun generateTrashCode(methodNode: MethodNode, returnType: Type): InsnList {
        return if (methodNode.isInitializer) insnList {
            ACONST_NULL
            ATHROW
        } else insnList {
            when (returnType.sort) {
                Type.INT -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.INT)
                    if (junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else INT(Random.nextInt())
                    IRETURN
                }

                Type.FLOAT -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.FLOAT)
                    if (junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else FLOAT(Random.nextFloat())
                    FRETURN
                }

                Type.BOOLEAN -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.BOOLEAN)
                    if (junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else INT(if (Random.nextBoolean()) 1 else 0)
                    IRETURN
                }

                Type.CHAR -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.CHAR)
                    if (junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else INT(Random.nextInt(32767))
                    IRETURN
                }

                Type.SHORT -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.SHORT)
                    if (junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else INT(Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
                    IRETURN
                }

                Type.LONG -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.LONG)
                    if (junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else LDC(Random.nextLong())
                    LRETURN
                }

                Type.DOUBLE -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.DOUBLE)
                    if (junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    } else LDC(Random.nextDouble())
                    DRETURN
                }

                Type.VOID -> {
                    val trashCallMethod = findRandomGivenReturnTypeMethod(Type.VOID)
                    if (junkCode && trashCallMethod != null) {
                        +generateTrashCall(trashCallMethod)
                    }
                    RETURN
                }

                Type.OBJECT -> {
                    if (Random.nextBoolean()) {
                        ACONST_NULL
                        ARETURN
                    } else {
                        ACONST_NULL
                        ATHROW
                    }
                }

                else -> {
                    ACONST_NULL
                    ATHROW
                }
            }
        }
    }

    private enum class Action(val convert: (Int, Int) -> Int, val insnListProvider: () -> InsnList) {
        AND({ a, b -> a and b }, { ArithmeticEncryptTransformer.generateIAND() }),
        OR({ a, b -> a or b }, { ArithmeticEncryptTransformer.generateIOR() }),
        XOR({ a, b -> a xor b }, { ArithmeticEncryptTransformer.generateIXOR() });

        val insnList get() = insnListProvider.invoke()
    }

}