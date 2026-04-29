package net.spartanb312.grunt.yapyap.transformers.encrypt.number

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*

/**
 * TODO: Elliptic curve cryptography
 * TODO: SPECK 32/64
 */
@Transformer.Description(
    "process.encrypt.number.number_mathematical_encrypt.desc",
    "Encrypt numbers using mathematical transforms"
)
class NumberMathematicalEncrypt : Transformer<NumberMathematicalEncrypt.Config>(
    "NumberMathematicalEncrypt",
    Category.Encryption,
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
