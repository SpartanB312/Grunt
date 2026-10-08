/* SPDX-License-Identifier: PolyForm-Strict-1.0.0 */
package net.spartanb312.grunt.yapyap.transformers.encrypt

/** Lazy setup scoped to one pipeline pass, including deterministic publication of failures. */
internal class OncePerPass<T>(initialize: () -> T) {
    private val result by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { runCatching(initialize) }

    fun get(): T = result.getOrThrow()
}
