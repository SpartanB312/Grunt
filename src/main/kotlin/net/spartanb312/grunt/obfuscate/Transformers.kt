package net.spartanb312.grunt.obfuscate

import net.spartanb312.grunt.obfuscate.transformers.*

object Transformers : Collection<Transformer> by mutableListOf(
    AntiDebugTransformer,
    KotlinOptimizeTransformer,
    StringEncryptTransformer,
    NumberEncryptTransformer,
    ScrambleTransformer,
    LocalVariableRenameTransformer,
    MethodRenameTransformer,
    FieldRenameTransformer,
    ClassRenameTransformer,
    WatermarkTransformer
)