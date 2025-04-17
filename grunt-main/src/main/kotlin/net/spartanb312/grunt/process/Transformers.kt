package net.spartanb312.grunt.process

import net.spartanb312.grunt.process.transformers.PostProcessTransformer
import net.spartanb312.grunt.process.transformers.encrypt.ArithmeticEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.ConstPoolEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.NumberEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer
import net.spartanb312.grunt.process.transformers.flow.ConstBuilderTransformer
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.process.transformers.minecraft.MixinClassRenameTransformer
import net.spartanb312.grunt.process.transformers.minecraft.MixinFieldRenameTransformer
import net.spartanb312.grunt.process.transformers.misc.*
import net.spartanb312.grunt.process.transformers.optimize.*
import net.spartanb312.grunt.process.transformers.redirect.*
import net.spartanb312.grunt.process.transformers.rename.*
import net.spartanb312.grunt.process.transformers.rename.kr.FieldRenameTransformerKr
import net.spartanb312.grunt.process.transformers.rename.kr.MethodRenameTransformerKr

/**
 * Execution order
 * 0000 Optimization
 * 0100 Miscellaneous 1
 * 0200 Controlflow 1
 * 0300 Encryption 1
 * 0400 Controlflow 2
 * 0500 Encryption 2
 * 0600 Redirect
 * 0700 Miscellaneous 2
 * 0800 Renaming
 * 0900 Minecraft
 * 1000 InvokeDynamic & Miscellaneous 3
 * MAX PostProcess
 */
object Transformers : MutableList<Transformer> by mutableListOf(
    SourceDebugRemoveTransformer order 0,
    KotlinOptimizeTransformer order 2,
    EnumOptimizeTransformer order 3,
    DeadCodeRemoveTransformer order 4,
    MethodExtractorTransformer order 5,
    ClonedClassTransformer order 100,
    TrashClassTransformer order 101,
    HWIDAuthenticatorTransformer order 102,
    DeclareFieldsTransformer order 103,
    ReflectionSupportTransformer order 199,
    //ControlflowTransformer order 200,
    StringEncryptTransformer order 300,
    NumberEncryptTransformer order 301,
    ArithmeticEncryptTransformer order 302,
    ControlflowTransformer order 400,
    ConstBuilderTransformer order 500,
    ConstPoolEncryptTransformer order 501,
    StringEqualsRedirectTransformer order 600,
    FieldScrambleTransformer order 601,
    MethodScrambleTransformer order 602,
    NativeCandidateTransformer order 700,
    SyntheticBridgeTransformer order 701,
    LocalVariableRenameTransformer order 800,
    MethodRenameTransformer order 801,
    MethodRenameTransformerKr order 802,
    FieldRenameTransformer order 811,
    FieldRenameTransformerKr order 812,
    ClassRenameTransformer order 821,
    MixinFieldRenameTransformer order 900,
    MixinClassRenameTransformer order 901,
    InvokeDynamicTransformer order 1000,
    ShuffleMembersTransformer order 1001,
    CrasherTransformer order 1002,
    WatermarkTransformer order 1003,
    ShrinkingTransformer order 1004,
    PostProcessTransformer order Int.MAX_VALUE
) {
    fun register(transformer: Transformer, order: Int) {
        add(transformer order order)
    }
}