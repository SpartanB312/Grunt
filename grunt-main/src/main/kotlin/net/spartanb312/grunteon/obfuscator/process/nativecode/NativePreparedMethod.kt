package net.spartanb312.grunteon.obfuscator.process.nativecode

/** A validated, immutable method emission. Scoped to the validate -> generate transaction. */
internal class NativePreparedMethod(
    private val source: String,
    private val slots: NativeReferenceSlots = NativeReferenceSlots(),
    private val stats: NativeJvmIntrinsicStats = NativeJvmIntrinsicStats(),
    private val commitKind: NativeMethodCommitKind = NativeMethodCommitKind.Direct,
    private val primitiveIntrinsics: Boolean? = null
) {
    fun matches(kind: NativeMethodCommitKind, intrinsics: Boolean): Boolean =
        normalize(kind) == normalize(commitKind) && (primitiveIntrinsics == null || primitiveIntrinsics == intrinsics)

    fun render(name: String, targetSlots: NativeReferenceSlots, targetStats: NativeJvmIntrinsicStats): String {
        val nameStart = source.indexOf(" JNICALL ") + " JNICALL ".length
        val nameEnd = source.indexOf('(', nameStart)
        val bodyStart = source.indexOf(") {", nameEnd) + 3
        check(nameStart >= " JNICALL ".length && nameEnd > nameStart && bodyStart > nameEnd)
        val declarations = slots.declarationsInto(targetSlots)
        targetStats.addAll(stats)
        return buildString(source.length + declarations.length + name.length) {
            append(source, 0, nameStart)
            append(name)
            append(source, nameEnd, bodyStart)
            appendLine()
            append(declarations)
            append(source, bodyStart + 1, source.length)
        }
    }

    private fun normalize(kind: NativeMethodCommitKind): NativeMethodCommitKind =
        if (kind == NativeMethodCommitKind.ClassInitializerProxy) NativeMethodCommitKind.Direct else kind
}