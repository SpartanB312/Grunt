package net.spartanb312.grunt.glsl.shader

// Inline patches replace non-empty source spans. Only adjacent intervals can overlap a new span.
internal class GlslPatchIndex {
    private val byFile = mutableMapOf<ResourcePath, java.util.TreeMap<Int, TextPatch>>()

    fun overlaps(patch: TextPatch): Boolean {
        val intervals = byFile[patch.file] ?: return false
        if (intervals.floorEntry(patch.start)?.value?.end?.let { it > patch.start } == true) return true
        return intervals.ceilingKey(patch.start)?.let { it < patch.end } == true
    }

    fun tryAdd(patch: TextPatch): Boolean {
        require(patch.start < patch.end)
        if (overlaps(patch)) return false
        byFile.getOrPut(patch.file) { java.util.TreeMap() }[patch.start] = patch
        return true
    }
}

internal fun applyPatches(files: Map<ResourcePath, String>, patches: List<TextPatch>): Map<ResourcePath, String> {
    if (patches.isEmpty()) return files
    val grouped = patches.groupBy { it.file }
    return files.mapValues { (path, source) ->
        val filePatches = grouped[path].orEmpty()
            .sortedWith(compareBy<TextPatch> { it.start }.thenBy { it.end })
        if (filePatches.isEmpty()) return@mapValues source
        var cursor = 0
        buildString(source.length + filePatches.sumOf { it.replacement.length - (it.end - it.start) }) {
            filePatches.forEach { patch ->
                if (patch.start < cursor) {
                    throw GlslObfuscationException("Overlapping GLSL text patches in $path at ${patch.start}")
                }
                if (patch.start < 0 || patch.end > source.length || patch.start > patch.end) {
                    throw GlslObfuscationException("Invalid GLSL text patch in $path: ${patch.start}..${patch.end}")
                }
                append(source, cursor, patch.start)
                append(patch.replacement)
                cursor = patch.end
            }
            append(source, cursor, source.length)
        }
    }
}

internal fun lineIndent(source: String, offset: Int): String {
    var lineStart = offset.coerceAtMost(source.length)
    while (lineStart > 0 && source[lineStart - 1] != '\n' && source[lineStart - 1] != '\r') lineStart--
    var cursor = lineStart
    while (cursor < source.length && (source[cursor] == ' ' || source[cursor] == '\t')) cursor++
    return source.substring(lineStart, cursor)
}
