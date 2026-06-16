package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.nio.file.Files

object NativePipelineRunner {

    context(instance: Grunteon)
    fun run(config: NativePipelineConfig) {
        if (!config.enabled) return

        Logger.info("Running NativePipeline...")
        val candidates = NativeCandidateScanner.scan(config)
        if (candidates.isEmpty()) {
            Logger.info(" - NativePipeline: no annotation-marked candidates")
            if (config.cleanNativeAnnotations) NativeCommitter.cleanNativeAnnotations()
            return
        }

        val (accepted, skipped) = NativeValidator.validate(candidates, config.backend)
        logValidation(candidates.size, accepted.size, skipped, config)
        if (skipped.isNotEmpty() && config.failOnValidationError) {
            throw NativeValidationException(skipped)
        }
        if (accepted.isEmpty()) {
            Logger.warn(" - NativePipeline: all candidates were skipped")
            if (config.cleanNativeAnnotations) NativeCommitter.cleanNativeAnnotations()
            return
        }

        val sourceBundle = NativeCppBackend.generate(
            methods = accepted,
            config = config,
            classExists = {
                instance.workRes.inputClassMap.containsKey(it) ||
                    instance.workRes.libraryClassMap.containsKey(it)
            }
        )
        val compileResult = NativeCompiler.compile(sourceBundle)
        if (!compileResult.success) {
            val message = "Native compilation failed; Java bytecode was left unchanged.\n${compileResult.output}"
            if (config.failOnCompileError) throw NativeCompileException(message)
            Logger.warn(message)
            if (config.cleanNativeAnnotations) NativeCommitter.cleanNativeAnnotations()
            return
        }

        val libraryPath = compileResult.libraryPath
            ?: throw NativeCompileException("Native compiler reported success without a library path")
        val libraryBytes = Files.readAllBytes(libraryPath)
        instance.workRes.addGeneratedResource(sourceBundle.plan.resourceName, libraryBytes)
        NativeCommitter.commit(sourceBundle, config)

        Logger.info(" - NativePipeline:")
        Logger.info("    Nativeized ${accepted.size} methods in ${sourceBundle.plan.classes.size} classes")
        Logger.info("    Wrote native source to ${sourceBundle.sourcePath}")
        Logger.info("    Injected native library resource ${sourceBundle.plan.resourceName}")
    }

    private fun logValidation(
        candidateCount: Int,
        acceptedCount: Int,
        skipped: List<NativeSkip>,
        config: NativePipelineConfig
    ) {
        Logger.info(" - NativePipeline candidate scan:")
        Logger.info("    Found $candidateCount candidates")
        Logger.info("    Accepted $acceptedCount candidates")
        Logger.info("    Skipped ${skipped.size} candidates")
        if (!config.logSkips) return
        skipped.forEach { skip ->
            val detail = skip.detail?.let { ": $it" } ?: ""
            Logger.warn("    Skipped ${skip.displayName}: ${skip.reason}$detail")
        }
    }
}
