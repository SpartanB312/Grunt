package net.spartanb312.grunt.process

import net.spartanb312.grunt.process.transformers.PostProcessTransformer
import net.spartanb312.grunt.process.transformers.encrypt.ArithmeticEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.ConstPoolEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.NumberEncryptTransformer
import net.spartanb312.grunt.process.transformers.encrypt.StringEncryptTransformer
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.process.transformers.minecraft.MixinClassRenameTransformer
import net.spartanb312.grunt.process.transformers.minecraft.MixinFieldRenameTransformer
import net.spartanb312.grunt.process.transformers.misc.*
import net.spartanb312.grunt.process.transformers.optimize.*
import net.spartanb312.grunt.process.transformers.redirect.FieldScrambleTransformer
import net.spartanb312.grunt.process.transformers.redirect.InvokeDynamicTransformer
import net.spartanb312.grunt.process.transformers.redirect.MethodScrambleTransformer
import net.spartanb312.grunt.process.transformers.redirect.StringEqualsRedirectTransformer
import net.spartanb312.grunt.process.transformers.rename.ClassRenameTransformer
import net.spartanb312.grunt.process.transformers.rename.FieldRenameTransformer
import net.spartanb312.grunt.process.transformers.rename.LocalVariableRenameTransformer
import net.spartanb312.grunt.process.transformers.rename.MethodRenameTransformer

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
    ShrinkingTransformer order 1,
    KotlinOptimizeTransformer order 2,
    EnumOptimizeTransformer order 3,
    DeadCodeRemoveTransformer order 4,
    ClonedClassTransformer order 100,
    TrashClassTransformer order 101,
    HWIDAuthenticatorTransformer order 102,
    //ControlflowTransformer order 200,
    StringEncryptTransformer order 300,
    NumberEncryptTransformer order 301,
    ArithmeticEncryptTransformer order 302,
    ControlflowTransformer order 400,
    ConstPoolEncryptTransformer order 501,
    StringEqualsRedirectTransformer order 600,
    FieldScrambleTransformer order 601,
    MethodScrambleTransformer order 602,
    NativeCandidateTransformer order 700,
    SyntheticBridgeTransformer order 701,
    LocalVariableRenameTransformer order 800,
    MethodRenameTransformer order 801,
    FieldRenameTransformer order 802,
    ClassRenameTransformer order 803,
    MixinFieldRenameTransformer order 900,
    MixinClassRenameTransformer order 901,
    InvokeDynamicTransformer order 1000,
    ShuffleMembersTransformer order 1001,
    CrasherTransformer order 1002,
    WatermarkTransformer order 1003,
    PostProcessTransformer order Int.MAX_VALUE
) {
    fun register(transformer: Transformer, order: Int) {
        add(transformer order order)
    }
}