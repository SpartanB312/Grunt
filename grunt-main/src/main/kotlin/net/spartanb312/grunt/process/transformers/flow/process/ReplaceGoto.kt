package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.LABEL
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.process.transformers.encrypt.number.replaceIAND
import net.spartanb312.grunt.process.transformers.encrypt.number.replaceIOR
import net.spartanb312.grunt.process.transformers.encrypt.number.replaceIXOR
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import org.objectweb.asm.Label
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

object ReplaceGoto {

    fun generate(
        targetLabel: LabelNode,
        classNode: ClassNode,
        methodNode: MethodNode,
        returnType: Type,
        reverse: Boolean
    ): InsnList {
        return when (Random.nextInt(6)) {
            0 -> instructions {
                val action = Action.entries.random()
                val val1 = Random.nextInt(Int.MAX_VALUE / 2)
                val val2 = Random.nextInt(Int.MAX_VALUE / 2)
                val val3 = action.convert.invoke(val1, val2)
                if (ControlflowTransformer.antiSimulation) +AntiSimulation.actions.random()
                    .invoke(val1, val2, val3, classNode, action.insnList)
                else {
                    val usage =
                        if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                        else LocalVarUsages.Default
                    +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                }
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

            1 -> instructions {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) >= val3)
                if (ControlflowTransformer.antiSimulation) +AntiSimulation.actions.random()
                    .invoke(val1, val2, val3, classNode, action.insnList)
                else {
                    val usage =
                        if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                        else LocalVarUsages.Default
                    +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                }
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

            2 -> instructions {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) < val3)
                if (ControlflowTransformer.antiSimulation) +AntiSimulation.actions.random()
                    .invoke(val1, val2, val3, classNode, action.insnList)
                else {
                    val usage =
                        if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                        else LocalVarUsages.Default
                    +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                }
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

            3 -> instructions {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) <= val3)
                if (ControlflowTransformer.antiSimulation) +AntiSimulation.actions.random()
                    .invoke(val1, val2, val3, classNode, action.insnList)
                else {
                    val usage =
                        if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                        else LocalVarUsages.Default
                    +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                }
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

            4 -> instructions {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) > val3)
                if (ControlflowTransformer.antiSimulation) +AntiSimulation.actions.random()
                    .invoke(val1, val2, val3, classNode, action.insnList)
                else {
                    val usage =
                        if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                        else LocalVarUsages.Default
                    +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                }
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

            else -> instructions {
                val action = Action.entries.random()
                var val1: Int
                var val2: Int
                var val3: Int
                do {
                    val1 = Random.nextInt(Int.MAX_VALUE / 2)
                    val2 = Random.nextInt(Int.MAX_VALUE / 2)
                    val3 = Random.nextInt(Int.MAX_VALUE / 2)
                } while (action.convert(val1, val2) == val3)
                if (ControlflowTransformer.antiSimulation) +AntiSimulation.actions.random()
                    .invoke(val1, val2, val3, classNode, action.insnList)
                else {
                    val usage =
                        if (ControlflowTransformer.useLocalVar) LocalVarUsages.entries.random()
                        else LocalVarUsages.Default
                    +usage.localVarUsage(val1, val2, val3, methodNode.maxLocals, action.insnList)
                }
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

    enum class Action(val convert: (Int, Int) -> Int, val insnListProvider: () -> InsnList) {
        AND({ a, b -> a and b }, { replaceIAND() }),
        OR({ a, b -> a or b }, { replaceIOR() }),
        XOR({ a, b -> a xor b }, { replaceIXOR() });

        val insnList get() = insnListProvider.invoke()
    }

    enum class LocalVarUsages(
        private val insnListProvider: (Int, Int, Int, Int, InsnList) -> InsnList
    ) {
        Default({ val1, val2, val3, _, insnList ->
            instructions {
                INT(val1)
                INT(val2)
                +insnList
                INT(val3)
            }
        }),
        Type1({ val1, val2, val3, maxLocals, insnList ->
            instructions {
                INT(val2)
                ISTORE(maxLocals)
                INT(val1)
                ILOAD(maxLocals)
                +insnList
                INT(val3)
            }
        }),
        Type2({ val1, val2, val3, maxLocals, insnList ->
            instructions {
                INT(val3)
                ISTORE(maxLocals)
                INT(val1)
                INT(val2)
                +insnList
                ILOAD(maxLocals)
            }
        }),
        Type3({ val1, val2, val3, maxLocals, insnList ->
            instructions {
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