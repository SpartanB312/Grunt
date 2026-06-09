package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process

import net.spartanb312.genesis.kotlin.InsnListBuilder
import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.insn.IAND
import net.spartanb312.genesis.kotlin.extensions.insn.IMUL
import net.spartanb312.genesis.kotlin.extensions.insn.IOR
import net.spartanb312.genesis.kotlin.extensions.insn.IXOR
import org.apache.commons.rng.UniformRandomProvider

internal data class IntConstChain(
    val start: Int,
    val ops: List<IntConstOp>
) {
    val value: Int = ops.fold(start) { current, op -> op.apply(current) }

    fun emit(builder: InsnListBuilder) = with(builder) {
        INT(start)
        ops.forEach { it.emit(this) }
    }

    companion object {
        fun random(random: UniformRandomProvider, stepsRange: IntRange): IntConstChain {
            val stepCount = random.nextCount(stepsRange)
            return IntConstChain(
                start = nextNonZeroInt(random),
                ops = List(stepCount) { IntConstOp.random(random) }
            )
        }

        fun endingAt(value: Int, stepsRange: IntRange, random: UniformRandomProvider): IntConstChain {
            val prefix = random(random, stepsRange)
            return IntConstChain(
                start = prefix.start,
                ops = prefix.ops + IntConstOp.Xor(prefix.value xor value)
            )
        }
    }
}

internal sealed interface IntConstOp {
    fun apply(value: Int): Int

    fun emit(builder: InsnListBuilder)

    data class Mul(val value: Int) : IntConstOp {
        override fun apply(value: Int): Int = value * this.value

        override fun emit(builder: InsnListBuilder) = with(builder) {
            INT(value)
            IMUL
            Unit
        }
    }

    data class And(val value: Int) : IntConstOp {
        override fun apply(value: Int): Int = value and this.value

        override fun emit(builder: InsnListBuilder) = with(builder) {
            INT(value)
            IAND
            Unit
        }
    }

    data class Or(val value: Int) : IntConstOp {
        override fun apply(value: Int): Int = value or this.value

        override fun emit(builder: InsnListBuilder) = with(builder) {
            INT(value)
            IOR
            Unit
        }
    }

    data class Xor(val value: Int) : IntConstOp {
        override fun apply(value: Int): Int = value xor this.value

        override fun emit(builder: InsnListBuilder) = with(builder) {
            INT(value)
            IXOR
            Unit
        }
    }

    companion object {
        fun random(random: UniformRandomProvider): IntConstOp {
            val value = nextNonZeroInt(random)
            return when (random.nextInt(4)) {
                0 -> Mul(value or 1)
                1 -> And(value)
                2 -> Or(value)
                else -> Xor(value)
            }
        }
    }
}

internal fun normalizedIntRange(min: Int, max: Int, minimum: Int): IntRange {
    val start = min.coerceAtLeast(minimum)
    val end = max.coerceAtLeast(start)
    return start..end
}

internal fun nextNonZeroInt(random: UniformRandomProvider): Int {
    var value = random.nextInt()
    while (value == 0) value = random.nextInt()
    return value
}

internal fun nextOddInt(random: UniformRandomProvider): Int {
    return nextNonZeroInt(random) or 1
}

internal fun UniformRandomProvider.nextCount(range: IntRange): Int {
    return range.first + nextInt(range.last - range.first + 1)
}
