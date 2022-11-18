package net.spartanb312.grunt.obfuscate

import net.spartanb312.grunt.obfuscate.transformers.*

object Transformers {
    val transformers = mutableListOf(
        AntiDebugTransformer,
        KotlinOptimizeTransformer,
        StringEncryptTransformer,
        NumberEncryptTransformer,
        FieldRenameTransformer,
        ScrambleTransformer,
        ClassRenameTransformer,
        WatermarkTransformer
    )
}