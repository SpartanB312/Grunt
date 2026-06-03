package net.spartanb312.grunt.ir.core

/**
 * Late-bound site that needs frontend/backend metadata to resolve.
 *
 * The core keeps dynamic sites abstract so JVM ConstantDynamic/InvokeDynamic,
 * native lazy binding, or other platform mechanisms can share one IR shape.
 */
sealed interface IrDynamicSite {
    val id: IrDynamicSiteId
    val externalRef: IrExternalRef?
    val debugName: String?
}

/**
 * Dynamic value site.
 *
 * JVM input maps ConstantDynamic here, but the core only sees a typed value
 * resolution point plus an optional external metadata reference.
 */
data class IrDynamicValueSite(
    override val id: IrDynamicSiteId,
    val type: IrType,
    override val externalRef: IrExternalRef? = null,
    override val debugName: String? = null
) : IrDynamicSite

/**
 * Dynamic call site.
 *
 * JVM input maps InvokeDynamic here. The bootstrap/handle/details stay in
 * external metadata so native and JVM exporters can choose their own lowering.
 */
data class IrDynamicCallSite(
    override val id: IrDynamicSiteId,
    val parameterTypes: List<IrType>,
    val returnType: IrType,
    override val externalRef: IrExternalRef? = null,
    override val debugName: String? = null
) : IrDynamicSite
