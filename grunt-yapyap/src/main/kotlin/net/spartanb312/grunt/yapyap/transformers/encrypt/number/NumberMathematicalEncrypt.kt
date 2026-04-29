package net.spartanb312.grunt.yapyap.transformers.encrypt.number

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig

/**
 * TODO: Elliptic curve cryptography
 * TODO: SPECK 32/64
 */
class NumberMathematicalEncrypt : Transformer<NumberMathematicalEncrypt.Config>(
    name = enText("process.encrypt.number.number_mathematical_encrypt", "NumberMathematicalEncrypt"),
    category = Category.Encryption,
    description = enText(
        "process.encrypt.number.number_mathematical_encrypt.desc",
        "Encrypt numbers using mathematical transforms"
    )
) {

    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
    }

}
