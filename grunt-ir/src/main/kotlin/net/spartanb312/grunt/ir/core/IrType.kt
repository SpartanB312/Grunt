package net.spartanb312.grunt.ir.core

/**
 * Type carried by IR values.
 *
 * The core type system is deliberately small and frontend-neutral. JVM-specific
 * descriptors, class metadata, and native lowering details should live in
 * importer/exporter metadata rather than in these types.
 */
sealed interface IrType {
    val displayName: String
}

/** Primitive scalar type. */
sealed interface IrScalarType : IrType

/** Integer-like scalar. Bool is modeled as a 1-bit integer for CFG predicates. */
sealed interface IrIntegerType : IrScalarType {
    val bits: Int
}

data object IrBoolType : IrIntegerType {
    override val bits = 1
    override val displayName = "bool"
}

data object IrI8Type : IrIntegerType {
    override val bits = 8
    override val displayName = "i8"
}

data object IrI16Type : IrIntegerType {
    override val bits = 16
    override val displayName = "i16"
}

data object IrI32Type : IrIntegerType {
    override val bits = 32
    override val displayName = "i32"
}

data object IrI64Type : IrIntegerType {
    override val bits = 64
    override val displayName = "i64"
}

/** Floating point scalar. */
sealed interface IrFloatType : IrScalarType {
    val bits: Int
}

data object IrF32Type : IrFloatType {
    override val bits = 32
    override val displayName = "f32"
}

data object IrF64Type : IrFloatType {
    override val bits = 64
    override val displayName = "f64"
}

data object IrVoidType : IrType {
    override val displayName = "void"
}

/**
 * Unknown type used when a frontend cannot recover precise information.
 *
 * It is assignable to and from every type so early import can keep going, but
 * optimization passes should avoid relying on it for semantic decisions.
 */
data object IrUnknownType : IrType {
    override val displayName = "unknown"
}

/** Null literal type. It can flow into any reference-like target type. */
data object IrNullType : IrType {
    override val displayName = "null"
}

/**
 * Object/reference type.
 *
 * A missing [symbol] means the object identity is intentionally opaque to the
 * core IR. For example, a JVM importer can use side metadata to recover the
 * exact class while native lowering may choose a different representation.
 */
data class IrRefType(
    val symbol: IrTypeSymbol? = null,
    val nullable: Boolean = true
) : IrType {
    override val displayName = buildString {
        append("ref<")
        append(symbol?.name ?: "opaque")
        append(">")
        if (nullable) append("?")
    }
}

/** Array type with an element type, dimension count, and nullable marker. */
data class IrArrayType(
    val elementType: IrType,
    val dimensions: Int = 1,
    val nullable: Boolean = true
) : IrType {
    init {
        require(dimensions > 0) { "Array dimensions must be positive" }
    }

    override val displayName = buildString {
        append("array")
        append(dimensions)
        append("<")
        append(elementType.displayName)
        append(">")
        if (nullable) append("?")
    }
}

/**
 * Escape hatch for foreign or not-yet-modeled types.
 *
 * Use this sparingly. Prefer [IrExternalRef] side metadata when the value is
 * known to the frontend/backend but should not pollute the core type model.
 */
data class IrOpaqueType(
    override val displayName: String
) : IrType

/** Small helper predicates used by verifier/importer/exporter code. */
object IrTypes {
    fun isInteger(type: IrType) = type is IrIntegerType

    fun isFloat(type: IrType) = type is IrFloatType

    fun isReference(type: IrType) = type is IrRefType || type is IrArrayType || type == IrNullType

    fun isAssignable(from: IrType, to: IrType): Boolean {
        if (from == to) return true
        if (from == IrUnknownType || to == IrUnknownType) return true
        if (from == IrNullType && isReference(to)) return true
        return false
    }
}
