package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.LABEL
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import kotlin.random.Random

/**
 * Idea taken from https://github.com/willemml/binscure
 *
 * @author jonesdevelopment
 */
object MutateJumps {

    fun generate(
        jump: JumpInsnNode
    ): InsnList {
        return instructions {
            val trueLabel = LabelNode()
            val proxyTrue = LabelNode()
            val switchLabel = LabelNode()
            val falseGoto = LabelNode()

            val random = Random.nextInt(0xF, 0xFFFF)

            +JumpInsnNode(jump.opcode, trueLabel)
            +obfuscateBasic(random - 1)
            GOTO(switchLabel)
            LABEL(trueLabel)
            +obfuscateBasic(random)
            GOTO(switchLabel)
            LABEL(proxyTrue)
            +insertTrap()
            +obfuscateBasic(random - 2)
            LABEL(switchLabel)
            TABLESWITCH(random - 1, random, jump.label, falseGoto, proxyTrue)
            LABEL(falseGoto)
        }
    }

    fun insertTrap(): InsnList {
        return instructions {
            NOP
            // TODO: make something cool
        }
    }

    fun obfuscateBasic(value: Int): InsnList {
        return instructions {
            val key = Random.nextInt(value)
            when (Random.nextInt(4)) {
                0 -> {
                    INT(value - key)
                    INT(key)
                    IADD
                }
                1 -> {
                    INT(value + key)
                    INT(key)
                    ISUB
                }
                2 -> {
                    INT(value xor key)
                    INT(key)
                    IXOR
                }
                3 -> {
                    val a = Random.nextInt() and key or value
                    val b = Random.nextInt() and key.inv() or value
                    INT(a)
                    INT(b)
                    IAND
                }
            }
        }
    }
}