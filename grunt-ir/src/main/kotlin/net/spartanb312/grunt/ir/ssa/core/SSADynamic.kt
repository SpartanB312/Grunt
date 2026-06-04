package net.spartanb312.grunt.ir.ssa.core

/**
 * Late-bound site that needs frontend/backend metadata to resolve.
 *
 * The core keeps dynamic sites abstract so JVM ConstantDynamic/InvokeDynamic,
 * native lazy binding, or other platform mechanisms can share one IR shape.
 */
sealed interface SSADynamicSite {
    val id: SSADynamicSiteId
    val externalRef: SSAExternalRef?
    val debugName: String?
}

/**
 * Dynamic value site.
 *
 * JVM input maps ConstantDynamic here, but the core only sees a typed value
 * resolution point plus an optional external metadata reference.
 */
data class SSADynamicValueSite(
    override val id: SSADynamicSiteId,
    val type: SSAType,
    override val externalRef: SSAExternalRef? = null,
    override val debugName: String? = null
) : SSADynamicSite

/**
 * Dynamic call site.
 *
 * JVM input maps InvokeDynamic here. The bootstrap/handle/details stay in
 * external metadata so native and JVM exporters can choose their own lowering.
 */
data class SSADynamicCallSite(
    override val id: SSADynamicSiteId,
    val parameterTypes: List<SSAType>,
    val returnType: SSAType,
    override val externalRef: SSAExternalRef? = null,
    override val debugName: String? = null
) : SSADynamicSite
