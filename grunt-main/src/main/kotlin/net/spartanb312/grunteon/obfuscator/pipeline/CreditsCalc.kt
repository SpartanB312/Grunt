package net.spartanb312.grunteon.obfuscator.pipeline

import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer
import java.util.concurrent.atomic.AtomicLong

class CreditCounter {
    private val raw = AtomicLong(0L)

    fun add(units: Long = 1L) {
        raw.addAndGet(units)
    }

    fun raw(): Long {
        return raw.get()
    }

    fun reset() {
        raw.set(0L)
    }
}

data class TransformerCreditReport(
    val index: Int,
    val name: String,
    val category: Category,
    val raw: Long,
    val baseMultiplier: Double,
    val credits: Double,
)

data class CreditsSummary(
    val transformers: List<TransformerCreditReport>,
) {
    val totalRaw: Long = transformers.sumOf { it.raw }
    val totalCredits: Double = transformers.sumOf { it.credits }

    companion object {
        val EMPTY = CreditsSummary(emptyList())
    }
}

object CreditsCalc {
    fun summarize(transformers: List<Transformer<*>>): CreditsSummary {
        return CreditsSummary(
            transformers.map { transformer ->
                val raw = transformer.credit.raw()
                TransformerCreditReport(
                    index = transformer.index,
                    name = transformer.name,
                    category = transformer.category,
                    raw = raw,
                    baseMultiplier = transformer.baseMultiplier,
                    credits = raw * transformer.baseMultiplier,
                )
            }
        )
    }
}
