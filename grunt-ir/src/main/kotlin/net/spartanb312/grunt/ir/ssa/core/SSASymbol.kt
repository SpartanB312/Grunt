package net.spartanb312.grunt.ir.ssa.core

/**
 * Named declaration known by the IR core.
 *
 * Symbols are used when the declaration is part of the IR universe. References
 * owned by a source/target platform should use [SSAExternalRef] instead.
 */
sealed interface SSASymbol {
    val id: SSASymbolId
    var name: String
}

/** Named type declaration. */
data class SSATypeSymbol(
    override val id: SSASymbolId,
    override var name: String
) : SSASymbol

/** Named function declaration with a core-level signature. */
data class SSAFunctionSymbol(
    override val id: SSASymbolId,
    override var name: String,
    val parameterTypes: List<SSAType>,
    val returnType: SSAType,
    val externalRef: SSAExternalRef? = null
) : SSASymbol

/** Named field declaration with optional owner type. */
data class SSAFieldSymbol(
    override val id: SSASymbolId,
    override var name: String,
    val type: SSAType,
    val owner: SSATypeSymbol? = null,
    val isStatic: Boolean = false,
    val externalRef: SSAExternalRef? = null
) : SSASymbol

/** Coarse kind for opaque references kept by importer/exporter side tables. */
enum class SSAExternalRefKind {
    Type,
    Function,
    Field,
    DynamicValueSite,
    DynamicCallSite,
    RuntimeIntrinsic,
    Other
}

/**
 * Opaque handle to foreign metadata.
 *
 * The core IR may carry the handle, but it does not interpret descriptors,
 * owner names, bootstrap methods, or other platform details attached to it.
 */
data class SSAExternalRef(
    val id: SSAExternalRefId,
    val kind: SSAExternalRefKind,
    val debugName: String? = null
) {
    override fun toString() = debugName ?: id.toString()
}

/** Common call target abstraction for local symbols, external functions, and intrinsics. */
sealed interface SSACallableRef {
    val parameterTypes: List<SSAType>
    val returnType: SSAType
    val displayName: String
}

/** Call target backed by a function symbol in the IR. */
data class SSAFunctionRef(
    val symbol: SSAFunctionSymbol
) : SSACallableRef {
    override val parameterTypes get() = symbol.parameterTypes
    override val returnType get() = symbol.returnType
    override val displayName get() = symbol.name
}

/** Call target backed by importer/exporter metadata. */
data class SSAExternalFunctionRef(
    val ref: SSAExternalRef,
    override val parameterTypes: List<SSAType>,
    override val returnType: SSAType
) : SSACallableRef {
    override val displayName get() = ref.toString()
}

/**
 * Core or runtime intrinsic.
 *
 * Intrinsics should be used for operations that are intentionally abstracted
 * away from a specific frontend instruction but still need special lowering.
 */
data class SSAIntrinsicRef(
    val name: String,
    override val parameterTypes: List<SSAType>,
    override val returnType: SSAType,
    val intrinsicEffect: SSAEffect = SSAEffect.Pure
) : SSACallableRef {
    override val displayName get() = name
}

/** Dispatch mode requested by a call site. */
enum class SSACallDispatch {
    Direct,
    Virtual,
    Interface,
    External,
    Runtime
}

/** Common field target abstraction for local symbols and external fields. */
sealed interface SSAFieldRef {
    val type: SSAType
    val isStatic: Boolean
    val displayName: String
}

/** Field reference backed by a field symbol in the IR. */
data class SSASymbolFieldRef(
    val symbol: SSAFieldSymbol
) : SSAFieldRef {
    override val type get() = symbol.type
    override val isStatic get() = symbol.isStatic
    override val displayName get() = symbol.name
}

/** Field reference backed by importer/exporter metadata. */
data class SSAExternalFieldRef(
    val ref: SSAExternalRef,
    override val type: SSAType,
    override val isStatic: Boolean = false
) : SSAFieldRef {
    override val displayName get() = ref.toString()
}
