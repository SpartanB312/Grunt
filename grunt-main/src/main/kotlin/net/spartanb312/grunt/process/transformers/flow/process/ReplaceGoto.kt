package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.utils.builder.*
import org.objectweb.asm.Label
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

object ReplaceGoto {

    fun generate(
        targetLabel: LabelNode,
        methodNode: MethodNode,
        returnType: Type,
        reverse: Boolean
    ): InsnList {
        return when (Random.nextInt(6)) {
            0 -> insnList {
                val action = Action.entries.random()
                val val1 = Random.nextInt(Int.MAX_VALUE / 2)
                val val2 = Random.nextInt(Int.MAX_VALUE / 2)
                val val3 = action.convert.invoke(val1, val2)
                val usage =
                    if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                    else LocalVarUsages.Default
                +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                if (reverse) {
                    val junkLabel = Label()
                    IF_ICMPNE(junkLabel)
                    GOTO(targetLabel)
                    LABEL(junkLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                } else {
                    IF_ICMPEQ(targetLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                }
            }

            1 -> insnList {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) >= val3)
                val usage =
                    if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                    else LocalVarUsages.Default
                +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                if (reverse) {
                    val junkLabel = Label()
                    IF_ICMPGE(junkLabel)
                    GOTO(targetLabel)
                    LABEL(junkLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                } else {
                    IF_ICMPLT(targetLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                }
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
                } while (action.convert(val1, val2) < val3)
                val usage =
                    if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                    else LocalVarUsages.Default
                +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                if (reverse) {
                    val junkLabel = Label()
                    IF_ICMPLT(junkLabel)
                    GOTO(targetLabel)
                    LABEL(junkLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                } else {
                    IF_ICMPGE(targetLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                }
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
                } while (action.convert(val1, val2) <= val3)
                val usage =
                    if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                    else LocalVarUsages.Default
                +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                if (reverse) {
                    val junkLabel = Label()
                    IF_ICMPLE(junkLabel)
                    GOTO(targetLabel)
                    LABEL(junkLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                } else {
                    IF_ICMPGT(targetLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                }
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
                } while (action.convert(val1, val2) > val3)
                val usage =
                    if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                    else LocalVarUsages.Default
                +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                if (reverse) {
                    val junkLabel = Label()
                    IF_ICMPGT(junkLabel)
                    GOTO(targetLabel)
                    LABEL(junkLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                } else {
                    IF_ICMPLE(targetLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                }
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
                val usage = if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                else LocalVarUsages.Default
                +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                if (reverse) {
                    val junkLabel = Label()
                    IF_ICMPEQ(junkLabel)
                    GOTO(targetLabel)
                    LABEL(junkLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                } else {
                    IF_ICMPNE(targetLabel)
                    +JunkCode.generate(methodNode, returnType, Random.nextInt(ControlflowTransformer.maxJunkCode))
                }
            }
        }
    }

    private enum class Action(val convert: (Int, Int) -> Int, val insnListProvider: () -> InsnList) {
        AND({ a, b -> a and b }, { net.spartanb312.grunt.utils.replaceIAND() }),
        OR({ a, b -> a or b }, { net.spartanb312.grunt.utils.replaceIOR() }),
        XOR({ a, b -> a xor b }, { net.spartanb312.grunt.utils.replaceIXOR() });

        val insnList get() = insnListProvider.invoke()
    }

    private enum class LocalVarUsages(
        private val insnListProvider: (Int, Int, Int, Int, InsnList) -> InsnList
    ) {
        Default({ val1, val2, val3, _, insnList ->
            insnList {
                INT(val1)
                INT(val2)
                +insnList
                INT(val3)
            }
        }),
        Type1({ val1, val2, val3, maxLocals, insnList ->
            insnList {
                INT(val2)
                ISTORE(maxLocals)
                INT(val1)
                ILOAD(maxLocals)
                +insnList
                INT(val3)
            }
        }),
        Type2({ val1, val2, val3, maxLocals, insnList ->
            insnList {
                INT(val3)
                ISTORE(maxLocals)
                INT(val1)
                INT(val2)
                +insnList
                ILOAD(maxLocals)
            }
        }),
        Type3({ val1, val2, val3, maxLocals, insnList ->
            insnList {
                INT(val1)
                INT(val3)
                ISTORE(maxLocals)
                INT(val2)
                +insnList
                ILOAD(maxLocals)
            }
        });

        fun localVarUsage(val1: Int, val2: Int, val3: Int, maxLocals: Int, insnList: InsnList): InsnList {
            return insnListProvider.invoke(val1, val2, val3, maxLocals, insnList)
        }
    }

}