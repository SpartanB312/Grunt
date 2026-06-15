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

val DISABLER = mutableSetOf(
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

val IGNORE = mutableSetOf(
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

val PHANTOM_CLASS = "Lnet/spartanb312/grunteon/annotation/internal/PhantomClass;"
val PHANTOM_METHOD = "Lnet/spartanb312/grunteon/annotation/internal/PhantomMethod;"
val PHANTOM_FIELD = "Lnet/spartanb312/grunteon/annotation/internal/PhantomField;"

val DRAFT_RUNTIME_MATERIAL = "Lnet/spartanb312/grunteon/annotation/internal/RuntimeMaterial;"
val DRAFT_RUNTIME_MATERIAL_FIELD = "Lnet/spartanb312/grunteon/annotation/internal/RuntimeMaterialField;"
val DRAFT_RUNTIME_MATERIAL_GUARD = "Lnet/spartanb312/grunteon/annotation/internal/RuntimeMaterialGuard;"
val STRING_BLACKLIST = "Lnet/spartanb312/grunteon/annotation/internal/StringBlacklist;"
val REFLECTION_METADATA = "Lnet/spartanb312/grunteon/annotation/internal/ReflectionMetadata;"
val ANTI_LLM = "Lnet/spartanb312/grunteon/annotation/internal/AntiLLM;"
val ANTI_LLM_JUNK_CALL = "Lnet/spartanb312/grunteon/annotation/internal/AntiLLMJunkCall;"

val INTERNAL = mutableSetOf(
    GENERATED_CLASS,
    GENERATED_METHOD,
    GENERATED_FIELD,
    PHANTOM_CLASS,
    PHANTOM_METHOD,
    PHANTOM_FIELD,
    DRAFT_RUNTIME_MATERIAL,
    DRAFT_RUNTIME_MATERIAL_FIELD,
    DRAFT_RUNTIME_MATERIAL_GUARD,
    STRING_BLACKLIST,
    REFLECTION_METADATA,
    ANTI_LLM,
    ANTI_LLM_JUNK_CALL
)


/**
 * Native codegen
 */
val NATIVE_JVM_BRIDGE = "Lnet/spartanb312/grunteon/annotation/native/NativeJVMBridge;"
val NATIVE_EXCLUDED = "Lnet/spartanb312/grunteon/annotation/native/NativeExcluded;"
val NATIVE_INCLUDED = "Lnet/spartanb312/grunteon/annotation/native/NativeIncluded;"