package net.spartanb312.grunt.process

import net.spartanb312.grunt.process.transformers.*

object Transformers : Collection<Transformer> by mutableListOf(
    AntiDebugTransformer,
    ShrinkingTransformer,
    KotlinOptimizeTransformer,
    StringEncryptTransformer,
    NumberEncryptTransformer,
    ScrambleTransformer,
    NativeCandidateTransformer,
    LocalVariableRenameTransformer,
    MethodRenameTransformer,
    FieldRenameTransformer,
    ClassRenameTransformer,
    WatermarkTransformer
)