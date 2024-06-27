package net.spartanb312.grunt.process

import net.spartanb312.grunt.process.transformers.PostProcessTransformer
import net.spartanb312.grunt.process.transformers.encrypt.*
import net.spartanb312.grunt.process.transformers.minecraft.*
import net.spartanb312.grunt.process.transformers.misc.*
import net.spartanb312.grunt.process.transformers.optimize.*
import net.spartanb312.grunt.process.transformers.redirect.*
import net.spartanb312.grunt.process.transformers.rename.*

object Transformers : Collection<Transformer> by mutableListOf(
    SourceDebugRemoveTransformer,
    ShrinkingTransformer,
    KotlinOptimizeTransformer,
    ClonedClassTransformer,
    TrashClassTransformer,
    StringEncryptTransformer,
    NumberEncryptTransformer,
    InitializerRedirectTransformer,
    StringEqualsRedirectTransformer,
    FieldRedirectTransformer,
    NativeCandidateTransformer,
    SyntheticBridgeTransformer,
    LocalVariableRenameTransformer,
    MethodRenameTransformer,
    FieldRenameTransformer,
    ClassRenameTransformer,
    MixinFieldRenameTransformer,
    MixinClassRenameTransformer,
    ShuffleMembersTransformer,
    WatermarkTransformer,
    PostProcessTransformer
)