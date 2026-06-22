package net.spartanb312.grunt.glsl.shader

internal data class GlslIncludeGraph(
    val edges: Map<ResourcePath, List<ResourcePath>>,
    val warnings: List<String> = emptyList()
) {
    val edgeCount: Int = edges.values.sumOf { it.size }
}

internal class GlslIncludeResolver(
    private val includeRoots: List<String>,
    private val failOnMissingIncludes: Boolean = false
) {
    private val includeRegex = Regex("""^\s*#\s*include\s*([<"])([^>"]+)[>"]""")

    fun resolve(files: Map<ResourcePath, String>): GlslIncludeGraph {
        val available = files.keys.mapTo(linkedSetOf()) { normalizeResourcePath(it) }
        val edges = linkedMapOf<ResourcePath, List<ResourcePath>>()
        val warnings = mutableListOf<String>()
        files.forEach { (path, source) ->
            val includes = mutableListOf<ResourcePath>()
            source.lineSequence().forEach { line ->
                val match = includeRegex.find(line) ?: return@forEach
                val quote = match.groupValues[1]
                val target = match.groupValues[2]
                val resolved = resolveOne(normalizeResourcePath(path), target, quote == "\"", available, warnings)
                if (resolved != null) includes += resolved
            }
            edges[normalizeResourcePath(path)] = includes
        }
        checkCycles(edges)
        return GlslIncludeGraph(edges, warnings)
    }

    private fun resolveOne(
        currentPath: ResourcePath,
        target: String,
        quoted: Boolean,
        available: Set<ResourcePath>,
        warnings: MutableList<String>
    ): ResourcePath? {
        val candidates = linkedSetOf<ResourcePath>()
        if (quoted) {
            val currentDir = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
            normalizeSegments(listOf(currentDir, target))?.let { candidates += it }
        }
        includeRoots.forEach { root ->
            normalizeSegments(listOf(root, target))?.let { candidates += it }
        }
        val matches = candidates.filter { it in available }
        return when (matches.size) {
            1 -> matches.single()
            0 -> {
                val message = "Missing GLSL include '$target' from $currentPath"
                if (failOnMissingIncludes) throw GlslObfuscationException(message)
                warnings += message
                null
            }

            else -> throw GlslObfuscationException("Ambiguous GLSL include '$target' from $currentPath: $matches")
        }
    }

    private fun normalizeSegments(parts: List<String>): ResourcePath? {
        val output = ArrayDeque<String>()
        parts.asSequence()
            .flatMap { normalizeResourcePath(it).split('/').asSequence() }
            .filter { it.isNotEmpty() && it != "." }
            .forEach { part ->
                if (part == "..") {
                    if (output.isEmpty()) return null
                    output.removeLast()
                } else {
                    output += part
                }
            }
        return output.joinToString("/")
    }

    private fun checkCycles(edges: Map<ResourcePath, List<ResourcePath>>) {
        val visiting = linkedSetOf<ResourcePath>()
        val visited = mutableSetOf<ResourcePath>()

        fun visit(path: ResourcePath) {
            if (path in visited) return
            if (!visiting.add(path)) {
                throw GlslObfuscationException("GLSL include cycle detected at $path")
            }
            edges[path].orEmpty().forEach(::visit)
            visiting.remove(path)
            visited += path
        }

        edges.keys.forEach(::visit)
    }
}
