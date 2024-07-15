package net.spartanb312.grunt.process

import net.spartanb312.grunt.process.transformers.PostProcessTransformer
import net.spartanb312.grunt.process.transformers.encrypt.*
import net.spartanb312.grunt.process.transformers.flow.ImplicitJumpTransformer
import net.spartanb312.grunt.process.transformers.minecraft.*
import net.spartanb312.grunt.process.transformers.misc.*
import net.spartanb312.grunt.process.transformers.optimize.*
import net.spartanb312.grunt.process.transformers.redirect.*
import net.spartanb312.grunt.process.transformers.rename.*

object Transformers : Collection<Transformer> by mutableListOf(
    SourceDebugRemoveTransformer,
    ShrinkingTransformer,
    KotlinOptimizeTransformer,
    EnumOptimizeTransformer,
    DeadCodeRemoveTransformer,
    ClonedClassTransformer,
    TrashClassTransformer,
    ImplicitJumpTransformer, // Execute 1
    StringEncryptTransformer,
    NumberEncryptTransformer,
    FloatingPointEncryptTransformer,
    ArithmeticEncryptTransformer,
    ImplicitJumpTransformer, // Execute 2
    StringEqualsRedirectTransformer,
    FieldScrambleTransformer,
    MethodScrambleTransformer,
    NativeCandidateTransformer,
    SyntheticBridgeTransformer,
    LocalVariableRenameTransformer,
    MethodRenameTransformer,
    FieldRenameTransformer,
    ClassRenameTransformer,
    MixinFieldRenameTransformer,
    MixinClassRenameTransformer,
    InvokeDynamicTransformer,
    ShuffleMembersTransformer,
    WatermarkTransformer,
    PostProcessTransformer
)