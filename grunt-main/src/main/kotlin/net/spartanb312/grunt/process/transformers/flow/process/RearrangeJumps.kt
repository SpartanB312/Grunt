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
object RearrangeJumps {

    fun generate(
        jump: JumpInsnNode
    ): InsnList {
        return instructions {
            val trueLabel = LabelNode()
            val proxyTrue = LabelNode()
            val switchLabel = LabelNode()
            val falseGoto = LabelNode()

            val random = Random.nextInt() and 0xFFFF

            +JumpInsnNode(jump.opcode, trueLabel)
            +obfuscate(random - 1)
            GOTO(switchLabel)
            LABEL(trueLabel)
            +obfuscate(random)
            GOTO(switchLabel)
            LABEL(proxyTrue)
            NOP
            +obfuscate(random - 2)
            LABEL(switchLabel)
            TABLESWITCH(random - 1, random, jump.label, falseGoto, proxyTrue)
            LABEL(falseGoto)
        }
    }

    fun obfuscate(v: Int): InsnList {
        return instructions {
            val add = Random.nextBoolean()
            val key = Random.nextInt(v)
            if (add) {
                INT(v - key)
                INT(key)
                IADD
            } else {
                INT(v + key)
                INT(key)
                ISUB
            }
        }
    }
}