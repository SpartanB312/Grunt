package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmFeature
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmIrImporter
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmMethodIr
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmSupportAnalyzer
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmSupportIssueKind
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmSupportReport
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.isStatic

internal object NativeValidator {

    fun validate(
        candidates: List<NativeCandidate>,
        backend: NativeBackend
    ): Pair<List<NativeValidatedMethod>, List<NativeSkip>> {
        if (backend != NativeBackend.Cpp) {
            return emptyList<NativeValidatedMethod>() to candidates.map {
                NativeSkip(it, NativeSkipReason.UnsupportedBackend, "backend $backend is reserved but not implemented yet")
            }
        }

        val accepted = mutableListOf<NativeValidatedMethod>()
        val skipped = mutableListOf<NativeSkip>()
        candidates.forEach { candidate ->
            val result = validateOne(candidate)
            if (result.skip == null) {
                accepted += NativeValidatedMethod(
                    candidate = candidate,
                    jvmIr = result.jvmIr,
                    fullJvmSupport = result.fullJvmSupport,
                    lowering = result.lowering ?: NativeLoweringKind.PrimitiveInt
                )
            } else {
                skipped += result.skip
            }
        }
        return accepted to skipped
    }

    private fun validateOne(candidate: NativeCandidate): ValidationResult {
        val classNode = candidate.classNode
        val methodNode = candidate.methodNode
        val jvmIr = NativeJvmIrImporter.import(classNode.name, methodNode)
        val fullJvmSupport = NativeJvmSupportAnalyzer.analyze(jvmIr)

        fun skip(reason: NativeSkipReason, detail: String? = null): NativeSkip {
            return NativeSkip(
                candidate = candidate,
                reason = reason,
                detail = detail,
                jvmIr = jvmIr,
                fullJvmSupport = fullJvmSupport
            )
        }

        fun skipped(reason: NativeSkipReason, detail: String? = null): ValidationResult {
            return ValidationResult(jvmIr, fullJvmSupport, null, skip(reason, detail))
        }

        fun accepted(lowering: NativeLoweringKind): ValidationResult {
            return ValidationResult(jvmIr, fullJvmSupport, lowering, null)
        }

        if (methodNode.isAbstract) return skipped(NativeSkipReason.AbstractMethod, withFullJvmSummary("method is abstract", fullJvmSupport))
        if (methodNode.isNative) return skipped(NativeSkipReason.NativeMethod, withFullJvmSummary("method is already native", fullJvmSupport))
        if (methodNode.name == "<init>") return skipped(NativeSkipReason.Constructor, withFullJvmSummary("constructors require a proxy lowering path", fullJvmSupport))
        if (methodNode.instructions == null || methodNode.instructions.size() == 0) {
            return skipped(NativeSkipReason.EmptyInstructions, withFullJvmSummary("method has no instruction list", fullJvmSupport))
        }

        fullJvmPreprocessorBlocker(fullJvmSupport)?.let { (reason, detail) ->
            return skipped(reason, detail)
        }

        if (methodNode.name == "<clinit>") {
            if (methodNode.desc != "()V") {
                return skipped(NativeSkipReason.UnsupportedDescriptor, withFullJvmSummary("invalid <clinit> descriptor ${methodNode.desc}", fullJvmSupport))
            }
            return tryFullJvm(
                candidate,
                jvmIr,
                fullJvmSupport,
                ::accepted,
                ::skipped,
                if (classNode.isInterface) {
                    NativeMethodCommitKind.InterfaceClassInitializerProxy
                } else {
                    NativeMethodCommitKind.Direct
                }
            )
        }
        if (classNode.isInterface) {
            return tryFullJvm(
                candidate,
                jvmIr,
                fullJvmSupport,
                ::accepted,
                ::skipped,
                NativeMethodCommitKind.InterfaceProxy
            )
        }
        if (!methodNode.isStatic) {
            return tryFullJvm(candidate, jvmIr, fullJvmSupport, ::accepted, ::skipped)
        }
        if (NativeJvmFeature.TryCatch in fullJvmSupport.features) {
            return tryFullJvm(candidate, jvmIr, fullJvmSupport, ::accepted, ::skipped)
        }
        if (NativeJvmFeature.Monitor in fullJvmSupport.features) {
            return tryFullJvm(candidate, jvmIr, fullJvmSupport, ::accepted, ::skipped)
        }

        return try {
            NativeIntMethodTranslator.validate(methodNode)
            accepted(NativeLoweringKind.PrimitiveInt)
        } catch (exception: UnsupportedNativeInstruction) {
            try {
                NativeJvmCppMethodTranslator.validate(methodNode, jvmIr, fullJvmSupport)
                accepted(NativeLoweringKind.FullJvm)
            } catch (_: UnsupportedNativeInstruction) {
                skipped(exception.reason, withFullJvmSummary(exception.message, fullJvmSupport))
            }
        }
    }

    private fun tryFullJvm(
        candidate: NativeCandidate,
        jvmIr: NativeJvmMethodIr,
        fullJvmSupport: NativeJvmSupportReport,
        accepted: (NativeLoweringKind) -> ValidationResult,
        skipped: (NativeSkipReason, String?) -> ValidationResult,
        commitKind: NativeMethodCommitKind = NativeMethodCommitKind.Direct
    ): ValidationResult {
        return try {
            NativeJvmCppMethodTranslator.validate(candidate.methodNode, jvmIr, fullJvmSupport, commitKind)
            accepted(NativeLoweringKind.FullJvm)
        } catch (exception: UnsupportedNativeInstruction) {
            skipped(exception.reason, withFullJvmSummary(exception.message, fullJvmSupport))
        }
    }

    private fun fullJvmPreprocessorBlocker(
        report: NativeJvmSupportReport
    ): Pair<NativeSkipReason, String>? {
        val issue = report.issues.firstOrNull { issue ->
            issue.kind == NativeJvmSupportIssueKind.InvokeDynamicStillPresent ||
                issue.kind == NativeJvmSupportIssueKind.ConstantDynamicStillPresent ||
                issue.kind == NativeJvmSupportIssueKind.InvalidTryCatchBoundary ||
                issue.kind == NativeJvmSupportIssueKind.UnsupportedLegacySubroutine ||
                issue.kind == NativeJvmSupportIssueKind.EmptyMethod
        } ?: return null

        val reason = when (issue.kind) {
            NativeJvmSupportIssueKind.EmptyMethod -> NativeSkipReason.EmptyInstructions
            NativeJvmSupportIssueKind.InvokeDynamicStillPresent -> NativeSkipReason.InvokeDynamic
            NativeJvmSupportIssueKind.ConstantDynamicStillPresent -> NativeSkipReason.ConstantDynamic
            NativeJvmSupportIssueKind.InvalidTryCatchBoundary,
            NativeJvmSupportIssueKind.UnsupportedLegacySubroutine -> NativeSkipReason.UnsupportedInstruction
        }
        return reason to withFullJvmSummary(issue.detail, report)
    }

    private fun withFullJvmSummary(detail: String?, report: NativeJvmSupportReport): String {
        val prefix = detail?.takeIf { it.isNotBlank() } ?: "unsupported by current backend"
        return "$prefix; ${fullJvmSummary(report)}"
    }

    private fun fullJvmSummary(report: NativeJvmSupportReport): String {
        val features = report.features
            .map { it.name }
            .sorted()
            .joinToString(prefix = "[", postfix = "]")
        return if (report.isFullJvmLoweringReady) {
            "full JVM IR ready, readiness=${report.readiness}, features=$features"
        } else {
            val issues = report.issues
                .map { it.kind.name }
                .distinct()
                .sorted()
                .joinToString(prefix = "[", postfix = "]")
            "full JVM IR not ready, readiness=${report.readiness}, features=$features, issues=$issues"
        }
    }

    private data class ValidationResult(
        val jvmIr: NativeJvmMethodIr,
        val fullJvmSupport: NativeJvmSupportReport,
        val lowering: NativeLoweringKind?,
        val skip: NativeSkip?
    )
}
