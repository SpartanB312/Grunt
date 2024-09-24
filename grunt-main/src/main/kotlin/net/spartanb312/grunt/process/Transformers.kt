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
 * 010 Miscellaneous 1
 * 020 Controlflow 1
 * 030 Encryption 1
 * 040 Controlflow 2
 * 050 Encryption 2
 * 060 Redirect
 * 070 Miscellaneous 2
 * 080 Renaming
 * 090 Minecraft
 * 100 InvokeDynamic & Miscellaneous 3
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
    HWIDAuthenticatorTransformer order 12,
    //ControlflowTransformer order 20,
    StringEncryptTransformer order 30,
    NumberEncryptTransformer order 31,
    ArithmeticEncryptTransformer order 32,
    ControlflowTransformer order 40,
    ConstPoolEncryptTransformer order 51,
    StringEqualsRedirectTransformer order 60,
    FieldScrambleTransformer order 61,
    MethodScrambleTransformer order 62,
    NativeCandidateTransformer order 70,
    SyntheticBridgeTransformer order 71,
    LocalVariableRenameTransformer order 80,
    MethodRenameTransformer order 81,
    FieldRenameTransformer order 82,
    ClassRenameTransformer order 83,
    MixinFieldRenameTransformer order 90,
    MixinClassRenameTransformer order 91,
    InvokeDynamicTransformer order 100,
    ShuffleMembersTransformer order 101,
    CrasherTransformer order 102,
    WatermarkTransformer order 103,
    PostProcessTransformer order Int.MAX_VALUE
)