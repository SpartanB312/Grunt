package net.spartanb312.grunt.process

import net.spartanb312.grunt.process.transformers.PostProcessTransformer
import net.spartanb312.grunt.process.transformers.encrypt.*
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.process.transformers.minecraft.*
import net.spartanb312.grunt.process.transformers.misc.*
import net.spartanb312.grunt.process.transformers.optimize.*
import net.spartanb312.grunt.process.transformers.redirect.*
import net.spartanb312.grunt.process.transformers.rename.*

/**
 * Execution order
 * 000 Optimization
 * 010 Trash classes
 * 020 Controlflow 1
 * 030 Encryption
 * 040 Controlflow 2
 * 050 Redirect
 * 060 Miscellaneous 1
 * 070 Renaming
 * 080 Minecraft
 * 090 InvokeDynamic
 * 100 Miscellaneous 2
 * MAX PostProcess
 */
object Transformers : Collection<Transformer> by mutableListOf(
    SourceDebugRemoveTransformer order 0,
    ShrinkingTransformer order 1,
    KotlinOptimizeTransformer order 2,
    EnumOptimizeTransformer order 3,
    DeadCodeRemoveTransformer order 4,
    ClonedClassTransformer order 10,
    TrashClassTransformer order 11,
    ControlflowTransformer order 20,
    StringEncryptTransformer order 30,
    NumberEncryptTransformer order 31,
    FloatingPointEncryptTransformer order 32,
    ArithmeticEncryptTransformer order 33,
    ConstPoolEncryptTransformer order 34,
    //ImplicitJumpTransformer order 40,
    StringEqualsRedirectTransformer order 50,
    FieldScrambleTransformer order 51,
    MethodScrambleTransformer order 52,
    NativeCandidateTransformer order 60,
    SyntheticBridgeTransformer order 61,
    LocalVariableRenameTransformer order 70,
    MethodRenameTransformer order 71,
    FieldRenameTransformer order 72,
    ClassRenameTransformer order 73,
    MixinFieldRenameTransformer order 80,
    MixinClassRenameTransformer order 81,
    InvokeDynamicTransformer order 90,
    ShuffleMembersTransformer order 100,
    CrasherTransformer order 101,
    WatermarkTransformer order 102,
    PostProcessTransformer order Int.MAX_VALUE
)