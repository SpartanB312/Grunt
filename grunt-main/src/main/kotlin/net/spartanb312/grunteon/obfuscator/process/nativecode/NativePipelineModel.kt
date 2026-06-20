package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmMethodIr
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmSupportReport
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.nio.file.Path

internal data class NativeCandidate(
    val classNode: ClassNode,
    val methodNode: MethodNode,
    val source: NativeCandidateSource
) {
    val displayName: String
        get() = "${classNode.name}.${methodNode.name}${methodNode.desc}"
}

internal enum class NativeCandidateSource {
    ClassAnnotation,
    MethodAnnotation
}

internal data class NativeValidatedMethod(
    val candidate: NativeCandidate,
    val jvmIr: NativeJvmMethodIr,
    val fullJvmSupport: NativeJvmSupportReport,
    val lowering: NativeLoweringKind
) {
    val classNode: ClassNode
        get() = candidate.classNode

    val methodNode: MethodNode
        get() = candidate.methodNode

    val displayName: String
        get() = candidate.displayName
}

internal enum class NativeLoweringKind {
    SsaPrimitive,
    SsaPrimitiveInt,
    PrimitiveInt,
    FullJvm
}

internal enum class NativeSkipReason {
    AbstractMethod,
    NativeMethod,
    Constructor,
    TryCatch,
    Monitor,
    InvokeDynamic,
    ConstantDynamic,
    NonStaticMethod,
    UnsupportedDescriptor,
    UnsupportedInstruction,
    EmptyInstructions,
    UnsupportedBackend
}

internal data class NativeSkip(
    val candidate: NativeCandidate,
    val reason: NativeSkipReason,
    val detail: String? = null,
    val jvmIr: NativeJvmMethodIr? = null,
    val fullJvmSupport: NativeJvmSupportReport? = null
) {
    val displayName: String
        get() = candidate.displayName
}

internal data class NativeMethodBinding(
    val method: NativeValidatedMethod,
    val functionName: String,
    val registeredName: String,
    val registeredDesc: String,
    val commitKind: NativeMethodCommitKind
)

internal enum class NativeMethodCommitKind {
    Direct,
    ClassInitializerProxy,
    InterfaceProxy,
    InterfaceClassInitializerProxy
}

internal fun nativeClinitProxyName(classId: Int, methodId: Int): String {
    return "grt_native_clinit_${classId}_$methodId"
}

internal fun nativeInterfaceProxyName(classId: Int, methodId: Int): String {
    return "grt_native_interface_${classId}_$methodId"
}

internal fun nativeInterfaceProxyDesc(methodDesc: String): String {
    val arguments = Type.getArgumentTypes(methodDesc)
    val returnType = Type.getReturnType(methodDesc)
    return Type.getMethodDescriptor(returnType, Type.getType("Ljava/lang/Object;"), *arguments)
}

internal fun nativeInterfaceClinitProxyDesc(): String {
    return "(Ljava/lang/Class;)V"
}

internal data class NativeClassPlan(
    val classNode: ClassNode,
    val classId: Int,
    val methods: List<NativeMethodBinding>
)

internal data class NativeBuildPlan(
    val loaderInternalName: String,
    val resourceName: String,
    val libraryFileName: String,
    val platform: NativePlatform,
    val classes: List<NativeClassPlan>,
    val referenceSlots: NativeReferenceSlots = NativeReferenceSlots()
)

internal data class NativeMethodRef(
    val owner: String,
    val name: String,
    val desc: String,
    val isStatic: Boolean
)

internal data class NativeFieldRef(
    val owner: String,
    val name: String,
    val desc: String,
    val isStatic: Boolean
)

internal data class NativeClassRef(
    val internalName: String
)

internal data class NativeStringRef(
    val value: String
)

internal class NativeReferenceSlots {
    private val classSlots = linkedMapOf<NativeClassRef, Int>()
    private val methodSlots = linkedMapOf<NativeMethodRef, Int>()
    private val fieldSlots = linkedMapOf<NativeFieldRef, Int>()
    private val stringSlots = linkedMapOf<NativeStringRef, Int>()

    val classSlotCount: Int
        get() = classSlots.size

    val methodSlotCount: Int
        get() = methodSlots.size

    val fieldSlotCount: Int
        get() = fieldSlots.size

    val stringSlotCount: Int
        get() = stringSlots.size

    fun classSlot(internalName: String): Int {
        return classSlots.getOrPut(NativeClassRef(internalName)) { classSlots.size }
    }

    fun methodSlot(owner: String, name: String, desc: String, isStatic: Boolean): Int {
        return methodSlots.getOrPut(NativeMethodRef(owner, name, desc, isStatic)) { methodSlots.size }
    }

    fun fieldSlot(owner: String, name: String, desc: String, isStatic: Boolean): Int {
        return fieldSlots.getOrPut(NativeFieldRef(owner, name, desc, isStatic)) { fieldSlots.size }
    }

    fun stringSlot(value: String): Int {
        return stringSlots.getOrPut(NativeStringRef(value)) { stringSlots.size }
    }
}

internal data class NativeSourceBundle(
    val plan: NativeBuildPlan,
    val sourceText: String,
    val sourcePath: Path,
    val libraryPath: Path,
    val sourceFiles: List<NativeSourceFile> = listOf(NativeSourceFile(sourcePath, sourceText)),
    val libraryTargets: List<NativeLibraryTarget> = emptyList(),
    val intrinsicStats: NativeJvmIntrinsicStats = NativeJvmIntrinsicStats(),
    val ssaIntrinsicStats: NativeJvmIntrinsicStats = NativeJvmIntrinsicStats()
) {
    val resolvedLibraryTargets: List<NativeLibraryTarget>
        get() = libraryTargets.ifEmpty {
            listOf(
                NativeLibraryTarget(
                    platform = plan.platform,
                    resourceName = plan.resourceName,
                    libraryFileName = plan.libraryFileName,
                    libraryPath = libraryPath
                )
            )
        }
}

internal data class NativeSourceFile(
    val path: Path,
    val text: String
)

internal data class NativeLibraryTarget(
    val platform: NativePlatform,
    val resourceName: String,
    val libraryFileName: String,
    val libraryPath: Path
)

internal data class NativeCompiledLibrary(
    val platform: NativePlatform,
    val resourceName: String,
    val libraryPath: Path
)

internal data class NativeCompileResult(
    val success: Boolean,
    val libraryPath: Path? = null,
    val output: String = "",
    val compileTimeMillis: Long = 0L,
    val libraries: List<NativeCompiledLibrary> = emptyList()
)

internal class NativeValidationException(
    val skips: List<NativeSkip>
) : IllegalStateException("Native validation failed for ${skips.size} candidate(s)")

internal class NativeCompileException(
    message: String
) : IllegalStateException(message)

internal class UnsupportedNativeInstruction(
    val reason: NativeSkipReason,
    override val message: String
) : IllegalArgumentException(message)
