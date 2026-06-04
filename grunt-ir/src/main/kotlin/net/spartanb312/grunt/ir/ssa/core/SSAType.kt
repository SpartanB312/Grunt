package net.spartanb312.grunt.ir.ssa.core

/**
 * Type carried by IR values.
 *
 * The core type system is deliberately small and frontend-neutral. JVM-specific
 * descriptors, class metadata, and native lowering details should live in
 * importer/exporter metadata rather than in these types.
 */
sealed interface SSAType {
    val displayName: String
}

/** Primitive scalar type. */
sealed interface SSAScalarType : SSAType

/** Integer-like scalar. Bool is modeled as a 1-bit integer for CFG predicates. */
sealed interface SSAIntegerType : SSAScalarType {
    val bits: Int
}

data object SSABoolType : SSAIntegerType {
    override val bits = 1
    override val displayName = "bool"
}

data object SSAI8Type : SSAIntegerType {
    override val bits = 8
    override val displayName = "i8"
}

data object SSAI16Type : SSAIntegerType {
    override val bits = 16
    override val displayName = "i16"
}

data object SSACharType : SSAIntegerType {
    override val bits = 16
    override val displayName = "char"
}

data object SSAI32Type : SSAIntegerType {
    override val bits = 32
    override val displayName = "i32"
}

data object SSAI64Type : SSAIntegerType {
    override val bits = 64
    override val displayName = "i64"
}

/** Floating point scalar. */
sealed interface SSAFloatType : SSAScalarType {
    val bits: Int
}

data object SSAF32Type : SSAFloatType {
    override val bits = 32
    override val displayName = "f32"
}

data object SSAF64Type : SSAFloatType {
    override val bits = 64
    override val displayName = "f64"
}

data object SSAVoidType : SSAType {
    override val displayName = "void"
}

/**
 * Unknown type used when a frontend cannot recover precise information.
 *
 * It is assignable to and from every type so early import can keep going, but
 * optimization passes should avoid relying on it for semantic decisions.
 */
data object SSAUnknownType : SSAType {
    override val displayName = "unknown"
}

/** Null literal type. It can flow into any reference-like target type. */
data object SSANullType : SSAType {
    override val displayName = "null"
}

/**
 * Object/reference type.
 *
 * A missing [symbol] means the object identity is intentionally opaque to the
 * core IR. For example, a JVM importer can use side metadata to recover the
 * exact class while native lowering may choose a different representation.
 */
data class SSARefType(
    val symbol: SSATypeSymbol? = null,
    val nullable: Boolean = true
) : SSAType {
    override val displayName = buildString {
        append("ref<")
        append(symbol?.name ?: "opaque")
        append(">")
        if (nullable) append("?")
    }
}

/** Array type with an element type, dimension count, and nullable marker. */
data class SSAArrayType(
    val elementType: SSAType,
    val dimensions: Int = 1,
    val nullable: Boolean = true
) : SSAType {
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
 * Use this sparingly. Prefer [SSAExternalRef] side metadata when the value is
 * known to the frontend/backend but should not pollute the core type model.
 */
data class SSAOpaqueType(
    override val displayName: String
) : SSAType

/** Small helper predicates used by verifier/importer/exporter code. */
object SSATypes {
    fun isInteger(type: SSAType) = type is SSAIntegerType

    fun isFloat(type: SSAType) = type is SSAFloatType

    fun isReference(type: SSAType) = type is SSARefType || type is SSAArrayType || type == SSANullType

    fun isAssignable(from: SSAType, to: SSAType): Boolean {
        if (from == to) return true
        if (from == SSAUnknownType || to == SSAUnknownType) return true
        if (from == SSANullType && isReference(to)) return true
        return false
    }
}
