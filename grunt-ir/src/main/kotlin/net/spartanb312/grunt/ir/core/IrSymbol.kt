package net.spartanb312.grunt.ir.core

/**
 * Named declaration known by the IR core.
 *
 * Symbols are used when the declaration is part of the IR universe. References
 * owned by a source/target platform should use [IrExternalRef] instead.
 */
sealed interface IrSymbol {
    val id: IrSymbolId
    var name: String
}

/** Named type declaration. */
data class IrTypeSymbol(
    override val id: IrSymbolId,
    override var name: String
) : IrSymbol

/** Named function declaration with a core-level signature. */
data class IrFunctionSymbol(
    override val id: IrSymbolId,
    override var name: String,
    val parameterTypes: List<IrType>,
    val returnType: IrType,
    val externalRef: IrExternalRef? = null
) : IrSymbol

/** Named field declaration with optional owner type. */
data class IrFieldSymbol(
    override val id: IrSymbolId,
    override var name: String,
    val type: IrType,
    val owner: IrTypeSymbol? = null,
    val isStatic: Boolean = false,
    val externalRef: IrExternalRef? = null
) : IrSymbol

/** Coarse kind for opaque references kept by importer/exporter side tables. */
enum class IrExternalRefKind {
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
data class IrExternalRef(
    val id: IrExternalRefId,
    val kind: IrExternalRefKind,
    val debugName: String? = null
) {
    override fun toString() = debugName ?: id.toString()
}

/** Common call target abstraction for local symbols, external functions, and intrinsics. */
sealed interface IrCallableRef {
    val parameterTypes: List<IrType>
    val returnType: IrType
    val displayName: String
}

/** Call target backed by a function symbol in the IR. */
data class IrFunctionRef(
    val symbol: IrFunctionSymbol
) : IrCallableRef {
    override val parameterTypes get() = symbol.parameterTypes
    override val returnType get() = symbol.returnType
    override val displayName get() = symbol.name
}

/** Call target backed by importer/exporter metadata. */
data class IrExternalFunctionRef(
    val ref: IrExternalRef,
    override val parameterTypes: List<IrType>,
    override val returnType: IrType
) : IrCallableRef {
    override val displayName get() = ref.toString()
}

/**
 * Core or runtime intrinsic.
 *
 * Intrinsics should be used for operations that are intentionally abstracted
 * away from a specific frontend instruction but still need special lowering.
 */
data class IrIntrinsicRef(
    val name: String,
    override val parameterTypes: List<IrType>,
    override val returnType: IrType,
    val intrinsicEffect: IrEffect = IrEffect.Pure
) : IrCallableRef {
    override val displayName get() = name
}

/** Dispatch mode requested by a call site. */
enum class IrCallDispatch {
    Direct,
    Virtual,
    Interface,
    External,
    Runtime
}

/** Common field target abstraction for local symbols and external fields. */
sealed interface IrFieldRef {
    val type: IrType
    val isStatic: Boolean
    val displayName: String
}

/** Field reference backed by a field symbol in the IR. */
data class IrSymbolFieldRef(
    val symbol: IrFieldSymbol
) : IrFieldRef {
    override val type get() = symbol.type
    override val isStatic get() = symbol.isStatic
    override val displayName get() = symbol.name
}

/** Field reference backed by importer/exporter metadata. */
data class IrExternalFieldRef(
    val ref: IrExternalRef,
    override val type: IrType,
    override val isStatic: Boolean = false
) : IrFieldRef {
    override val displayName get() = ref.toString()
}
