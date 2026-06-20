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
            return
        }

        val (accepted, skipped) = NativeValidator.validate(candidates, config.backend)
        logValidation(candidates.size, accepted.size, skipped, config)
        if (skipped.isNotEmpty() && config.failOnValidationError) {
            throw NativeValidationException(skipped)
        }
        if (accepted.isEmpty()) {
            Logger.warn(" - NativePipeline: all candidates were skipped")
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
        val compileResult = NativeCompiler.compile(sourceBundle, config)
        if (!compileResult.success) {
            val message = "Native compilation failed; Java bytecode was left unchanged.\n${compileResult.output}"
            if (config.failOnCompileError) throw NativeCompileException(message)
            Logger.warn(message)
            return
        }

        val libraries = compileResult.libraries.takeIf { it.isNotEmpty() }
            ?: compileResult.libraryPath?.let {
                listOf(NativeCompiledLibrary(sourceBundle.plan.platform, sourceBundle.plan.resourceName, it))
            }
            ?: throw NativeCompileException("Native compiler reported success without a library path")
        val libraryBytes = libraries.map { library ->
            library to Files.readAllBytes(library.libraryPath)
        }
        libraryBytes.forEach { (library, bytes) ->
            instance.workRes.addGeneratedResource(library.resourceName, bytes)
        }
        NativeCommitter.commit(sourceBundle, config)

        Logger.info(" - NativePipeline:")
        Logger.info("    Nativeized ${accepted.size} methods in ${sourceBundle.plan.classes.size} classes")
        Logger.info("    Wrote ${sourceBundle.sourceFiles.size} native source file(s) under ${sourceBundle.sourcePath.parent}")
        Logger.info("    Native compiler elapsed ${compileResult.compileTimeMillis} ms")
        Logger.info("    Native libraries: ${libraryBytes.size}")
        libraryBytes.forEach { (library, bytes) ->
            Logger.info("      ${library.resourceName}: ${bytes.size} bytes")
        }
        Logger.info(
            "    Reference slots: " +
                "classes=${sourceBundle.plan.referenceSlots.classSlotCount}, " +
                "methods=${sourceBundle.plan.referenceSlots.methodSlotCount}, " +
                "fields=${sourceBundle.plan.referenceSlots.fieldSlotCount}, " +
                "strings=${sourceBundle.plan.referenceSlots.stringSlotCount}"
        )
        Logger.info("    Injected native library resources: ${libraries.joinToString { it.resourceName }}")
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
