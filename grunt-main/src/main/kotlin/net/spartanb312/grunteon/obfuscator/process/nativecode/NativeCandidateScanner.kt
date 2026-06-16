package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.Grunteon

internal object NativeCandidateScanner {

    context(instance: Grunteon)
    fun scan(config: NativePipelineConfig): List<NativeCandidate> {
        val includedAnnotations = normalizedAnnotationSet(config.includedAnnotationList)
        val excludedAnnotations = normalizedAnnotationSet(config.excludedAnnotationList)
        if (includedAnnotations.isEmpty()) return emptyList()

        val filter = config.classFilter.buildFilterStrategy()
        val candidates = mutableListOf<NativeCandidate>()
        instance.workRes.inputClassCollection
            .sortedBy { it.name }
            .forEach { classNode ->
                if (!filter.testClass(classNode)) return@forEach

                val classAnnotations = classNode.annotationDescs()
                val classExcluded = classAnnotations.any { it in excludedAnnotations }
                if (classExcluded) return@forEach

                val classIncluded = classAnnotations.any { it in includedAnnotations }
                classNode.methods
                    .sortedWith(compareBy({ it.name }, { it.desc }))
                    .forEach { methodNode ->
                        val methodAnnotations = methodNode.annotationDescs()
                        val methodExcluded = methodAnnotations.any { it in excludedAnnotations }
                        if (methodExcluded) return@forEach

                        val methodIncluded = methodAnnotations.any { it in includedAnnotations }
                        if (classIncluded || methodIncluded) {
                            candidates += NativeCandidate(
                                classNode = classNode,
                                methodNode = methodNode,
                                source = if (methodIncluded) {
                                    NativeCandidateSource.MethodAnnotation
                                } else {
                                    NativeCandidateSource.ClassAnnotation
                                }
                            )
                        }
                    }
            }
        return candidates
    }
}
