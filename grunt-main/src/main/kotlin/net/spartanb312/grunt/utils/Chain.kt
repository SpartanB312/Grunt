package net.spartanb312.grunt.utils

import org.objectweb.asm.Opcodes
import kotlin.random.Random

class ChainNodeGenerator(initValue: Int) {
    var startValue = initValue
    fun next(stepsRange: IntRange): Pair<Int, ChainNode> {
        val nextNode = ChainNode.nextChainNode(startValue, stepsRange)
        val endValue = nextNode.endValue
        startValue = endValue
        return endValue to nextNode
    }
}

class ChainNode(val startValue: Int, val actions: List<Pair<Actions, Int>>) {
    val endValue = run {
        var current = startValue
        actions.forEach { (action, value) ->
            current = action.action.invoke(current, value)
        }
        current
    }

    enum class Actions(val str: String, val action: (Int, Int) -> Int, val opCode: Int) {
        TimesAssignment("*=", { prev, param -> prev * param }, Opcodes.IMUL), // *=
        AndAssignment("&=", { prev, param -> prev and param }, Opcodes.IAND), // &=
        OrAssignment("|=", { prev, param -> prev or param }, Opcodes.IOR), // |=
        XorAssignment("^=", { prev, param -> prev xor param }, Opcodes.IXOR), // ^=
    }

    companion object {
        fun generateNodeChain(count: Int, stepsRange: IntRange): List<ChainNode> {
            val nodes = mutableListOf<ChainNode>()
            repeat(count) {
                while (true) {
                    val startValue = nodes.lastOrNull()?.endValue ?: Random.nextInt()
                    val new = nextChainNode(startValue, stepsRange)
                    if (nodes.none { it.startValue == new.endValue }) {
                        nodes.add(new)
                        break
                    }
                }
            }
            return nodes
        }

        fun nextChainNode(startValue: Int, stepsRange: IntRange): ChainNode {
            val actions = mutableListOf<Pair<Actions, Int>>()
            repeat(stepsRange.random()) {
                val pair = Actions.entries.random() to Random.nextInt()
                actions.add(pair)
            }
            return ChainNode(startValue, actions)
        }
    }
}

fun main() {
    val nodes = mutableListOf<ChainNode>()
    repeat(10) {
        while (true) {
            val lastValue = nodes.lastOrNull()?.endValue ?: 0
            val new = ChainNode.nextChainNode(lastValue, 1..3)
            if (nodes.none { it.startValue == new.endValue }) {
                nodes.add(new)
                break
            }
        }
    }
    nodes.forEachIndexed { index, chainNode ->
        println("Node $index")
        println("Start ${chainNode.startValue}")
        var current = chainNode.startValue
        chainNode.actions.forEach { (action, value) ->
            val next = action.action.invoke(current, value)
            println("Action $current ${action.str} $value -> $next")
            current = next
        }
        println("End ${chainNode.endValue}")
        println()
    }
}