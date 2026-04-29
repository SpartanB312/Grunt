package net.spartanb312.grunteon.obfuscator.util

/**
 * Disabler
 */

// Optimizer
val DISABLE_OPTIMIZER = "Lnet/spartanb312/grunteon/annotation/disable/DisableOptimizer;"

// Encrypt
val DISABLE_NUMBER_ENCRYPT = "Lnet/spartanb312/grunteon/annotation/disable/DisableNumberEncrypt;"
val DISABLE_STRING_ENCRYPT = "Lnet/spartanb312/grunteon/annotation/disable/DisableStringEncrypt;"
val DISABLE_ARITHMETIC_SUBSTITUTE = "Lnet/spartanb312/grunteon/annotation/disable/DisableArithmeticSubstitute;"

// Redirect
val DISABLE_FIELD_PROXY = "Lnet/spartanb312/grunteon/annotation/disable/DisableFieldProxy;"
val DISABLE_INVOKE_PROXY = "Lnet/spartanb312/grunteon/annotation/disable/DisableInvokeProxy;"
val DISABLE_INVOKE_DISPATCHER = "Lnet/spartanb312/grunteon/annotation/disable/DisableInvokeDispatcher;"
val DISABLE_REFERENCE_OBF = "Lnet/spartanb312/grunteon/annotation/disable/DisableReferenceObfuscate;"

// Controlflow
val DISABLE_CONST_FLOW = "Lnet/spartanb312/grunteon/annotation/disable/DisableConstFlow;"
val DISABLE_CONTROL_FLOW = "Lnet/spartanb312/grunteon/annotation/disable/DisableControlFlow;"
val DISABLE_FLATTENING = "Lnet/spartanb312/grunteon/annotation/disable/DisableFlattening;"

val DISABLER = arrayOf(
    DISABLE_OPTIMIZER,
    DISABLE_NUMBER_ENCRYPT,
    DISABLE_STRING_ENCRYPT,
    DISABLE_ARITHMETIC_SUBSTITUTE,
    DISABLE_FIELD_PROXY,
    DISABLE_INVOKE_PROXY,
    DISABLE_INVOKE_DISPATCHER,
    DISABLE_REFERENCE_OBF,
    DISABLE_CONST_FLOW,
    DISABLE_CONTROL_FLOW,
    DISABLE_FLATTENING
)

/**
 * Ignore
 */
// Redirect
val IGNORE_FIELD_PROXY = "Lnet/spartanb312/grunteon/annotation/ignore/ExcludeFromFieldProxy;"
val IGNORE_INVOKE_PROXY = "Lnet/spartanb312/grunteon/annotation/ignore/ExcludeFromInvokeProxy;"
val IGNORE_INVOKE_DISPATCHER = "Lnet/spartanb312/grunteon/annotation/ignore/ExcludeFromInvokeDispatcher;"
val IGNORE_JUNK_CODE = "Lnet/spartanb312/grunteon/annotation/ignore/ExcludeFromJunkCode;"
val IGNORE_PARAMETER_SALT = "Lnet/spartanb312/grunteon/annotation/ignore/ExcludeFromParameterSalt;"
val IGNORE_METADATA_TARGET = "Lnet/spartanb312/grunteon/annotation/ignore/ExcludeFromROMetadataGen;"

val IGNORE = arrayOf(
    IGNORE_FIELD_PROXY,
    IGNORE_INVOKE_PROXY,
    IGNORE_INVOKE_DISPATCHER,
    IGNORE_JUNK_CODE,
    IGNORE_PARAMETER_SALT,
    IGNORE_METADATA_TARGET
)

/**
 * Internal
 */
val GENERATED_CLASS = "Lnet/spartanb312/grunteon/annotation/internal/GeneratedClass;"
val GENERATED_METHOD = "Lnet/spartanb312/grunteon/annotation/internal/GeneratedMethod;"
val GENERATED_FIELD = "Lnet/spartanb312/grunteon/annotation/internal/GeneratedField;"

val ABE_EXTERNAL_CLASS = "Lnet/spartanb312/grunteon/annotation/internal/AbeExternalClass;"
val ABE_NUMBER_POOL_CLASS = "Lnet/spartanb312/grunteon/annotation/internal/AbeNumberPoolClass;"

val PHANTOM_CLASS = "Lnet/spartanb312/grunteon/annotation/internal/PhantomClass;"
val PHANTOM_METHOD = "Lnet/spartanb312/grunteon/annotation/internal/PhantomMethod;"
val PHANTOM_FIELD = "Lnet/spartanb312/grunteon/annotation/internal/PhantomField;"

val INTERNAL = arrayOf(
    GENERATED_CLASS,
    GENERATED_METHOD,
    GENERATED_FIELD,
    ABE_EXTERNAL_CLASS,
    ABE_NUMBER_POOL_CLASS,
    PHANTOM_CLASS,
    PHANTOM_METHOD,
    PHANTOM_FIELD
)
