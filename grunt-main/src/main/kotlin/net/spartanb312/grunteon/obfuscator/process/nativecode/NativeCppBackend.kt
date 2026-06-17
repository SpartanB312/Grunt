package net.spartanb312.grunteon.obfuscator.process.nativecode

import org.objectweb.asm.Opcodes
import java.nio.file.Path

internal object NativeCppBackend {
    private const val LoaderBaseInternalName = "net/spartanb312/grunteon/runtime/NativeLoader"
    private const val LibraryBaseName = "grunteon_native"

    fun generate(
        methods: List<NativeValidatedMethod>,
        config: NativePipelineConfig,
        classExists: (String) -> Boolean
    ): NativeSourceBundle {
        val platform = NativePlatform.current()
        val loaderInternalName = uniqueLoaderName(classExists)
        val libraryFileName = platform.libraryPrefix + LibraryBaseName + platform.librarySuffix
        val resourceName = "grunteon/native/${platform.resourceDirectory}/$libraryFileName"
        val workDir = Path.of(config.workDir)
        val sourcePath = workDir.resolve("src").resolve("grunteon_native.cpp")
        val libraryPath = workDir.resolve("lib").resolve(platform.resourceDirectory).resolve(libraryFileName)

        val grouped = methods
            .groupBy { it.classNode }
            .toList()
            .sortedBy { it.first.name }
        val classPlans = grouped.mapIndexed { classIndex, (classNode, classMethods) ->
            val bindings = classMethods
                .sortedWith(compareBy({ it.methodNode.name }, { it.methodNode.desc }))
                .mapIndexed { methodIndex, method ->
                    val isClassInitializer = method.methodNode.name == "<clinit>"
                    val isInterface = classNode.access and Opcodes.ACC_INTERFACE != 0
                    val isInterfaceProxy = isInterface && !isClassInitializer
                    val commitKind = when {
                        isClassInitializer && isInterface -> NativeMethodCommitKind.InterfaceClassInitializerProxy
                        isClassInitializer -> NativeMethodCommitKind.ClassInitializerProxy
                        isInterfaceProxy -> NativeMethodCommitKind.InterfaceProxy
                        else -> NativeMethodCommitKind.Direct
                    }
                    NativeMethodBinding(
                        method = method,
                        functionName = nativeFunctionName(classIndex, methodIndex, method),
                        registeredName = when (commitKind) {
                            NativeMethodCommitKind.ClassInitializerProxy -> nativeClinitProxyName(classIndex, methodIndex)
                            NativeMethodCommitKind.InterfaceClassInitializerProxy -> nativeClinitProxyName(classIndex, methodIndex)
                            NativeMethodCommitKind.InterfaceProxy -> nativeInterfaceProxyName(classIndex, methodIndex)
                            NativeMethodCommitKind.Direct -> method.methodNode.name
                        },
                        registeredDesc = when (commitKind) {
                            NativeMethodCommitKind.ClassInitializerProxy -> "()V"
                            NativeMethodCommitKind.InterfaceClassInitializerProxy -> nativeInterfaceClinitProxyDesc()
                            NativeMethodCommitKind.InterfaceProxy -> nativeInterfaceProxyDesc(method.methodNode.desc)
                            NativeMethodCommitKind.Direct -> method.methodNode.desc
                        },
                        commitKind = commitKind
                    )
                }
            NativeClassPlan(classNode, classIndex, bindings)
        }
        val plan = NativeBuildPlan(
            loaderInternalName = loaderInternalName,
            resourceName = resourceName,
            libraryFileName = libraryFileName,
            platform = platform,
            classes = classPlans
        )
        return NativeSourceBundle(
            plan = plan,
            sourceText = emitSource(plan),
            sourcePath = sourcePath,
            libraryPath = libraryPath
        )
    }

    private fun uniqueLoaderName(classExists: (String) -> Boolean): String {
        var name = LoaderBaseInternalName
        var index = 0
        while (classExists(name)) {
            index++
            name = "$LoaderBaseInternalName\$$index"
        }
        return name
    }

    private fun nativeFunctionName(classIndex: Int, methodIndex: Int, method: NativeValidatedMethod): String {
        val raw = "grt_${classIndex}_${methodIndex}_${method.classNode.name}_${method.methodNode.name}"
        return raw
            .map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9') it else '_' }
            .joinToString("")
    }

    private fun emitSource(plan: NativeBuildPlan): String {
        return buildString {
            appendLine("#include <jni.h>")
            appendLine("#include <cmath>")
            appendLine("#include <cstdint>")
            appendLine("#include <cstring>")
            appendLine("#include <limits>")
            appendLine("#include <string>")
            appendLine()
            appendLine("static jclass grt_class_class = nullptr;")
            appendLine("static jmethodID grt_get_classloader_method = nullptr;")
            appendLine("static jclass grt_classloader_class = nullptr;")
            appendLine("static jmethodID grt_load_class_method = nullptr;")
            appendLine("static jclass grt_no_class_def_found_class = nullptr;")
            appendLine("static jmethodID grt_no_class_def_found_init = nullptr;")
            appendLine("static jclass grt_throwable_class = nullptr;")
            appendLine("static jmethodID grt_throwable_get_message_method = nullptr;")
            appendLine("static jmethodID grt_throwable_init_cause_method = nullptr;")
            appendLine("static jclass grt_methodhandles_lookup_class = nullptr;")
            appendLine("static jmethodID grt_lookup_init_method = nullptr;")
            appendLine()
            appendLine("static bool grt_init_runtime(JNIEnv* env) {")
            appendLine("    jclass localClass = env->FindClass(\"java/lang/Class\");")
            appendLine("    if (localClass == nullptr) return false;")
            appendLine("    grt_class_class = (jclass) env->NewGlobalRef(localClass);")
            appendLine("    env->DeleteLocalRef(localClass);")
            appendLine("    if (grt_class_class == nullptr) return false;")
            appendLine("    grt_get_classloader_method = env->GetMethodID(grt_class_class, \"getClassLoader\", \"()Ljava/lang/ClassLoader;\");")
            appendLine("    if (grt_get_classloader_method == nullptr) return false;")
            appendLine()
            appendLine("    jclass localClassLoader = env->FindClass(\"java/lang/ClassLoader\");")
            appendLine("    if (localClassLoader == nullptr) return false;")
            appendLine("    grt_classloader_class = (jclass) env->NewGlobalRef(localClassLoader);")
            appendLine("    env->DeleteLocalRef(localClassLoader);")
            appendLine("    if (grt_classloader_class == nullptr) return false;")
            appendLine("    grt_load_class_method = env->GetMethodID(grt_classloader_class, \"loadClass\", \"(Ljava/lang/String;)Ljava/lang/Class;\");")
            appendLine("    if (grt_load_class_method == nullptr) return false;")
            appendLine()
            appendLine("    jclass localNoClassDef = env->FindClass(\"java/lang/NoClassDefFoundError\");")
            appendLine("    if (localNoClassDef == nullptr) return false;")
            appendLine("    grt_no_class_def_found_class = (jclass) env->NewGlobalRef(localNoClassDef);")
            appendLine("    env->DeleteLocalRef(localNoClassDef);")
            appendLine("    if (grt_no_class_def_found_class == nullptr) return false;")
            appendLine("    grt_no_class_def_found_init = env->GetMethodID(grt_no_class_def_found_class, \"<init>\", \"(Ljava/lang/String;)V\");")
            appendLine("    if (grt_no_class_def_found_init == nullptr) return false;")
            appendLine()
            appendLine("    jclass localThrowable = env->FindClass(\"java/lang/Throwable\");")
            appendLine("    if (localThrowable == nullptr) return false;")
            appendLine("    grt_throwable_class = (jclass) env->NewGlobalRef(localThrowable);")
            appendLine("    env->DeleteLocalRef(localThrowable);")
            appendLine("    if (grt_throwable_class == nullptr) return false;")
            appendLine("    grt_throwable_get_message_method = env->GetMethodID(grt_throwable_class, \"getMessage\", \"()Ljava/lang/String;\");")
            appendLine("    if (grt_throwable_get_message_method == nullptr) return false;")
            appendLine("    grt_throwable_init_cause_method = env->GetMethodID(grt_throwable_class, \"initCause\", \"(Ljava/lang/Throwable;)Ljava/lang/Throwable;\");")
            appendLine("    if (grt_throwable_init_cause_method == nullptr) return false;")
            appendLine()
            appendLine("    jclass localLookup = env->FindClass(\"java/lang/invoke/MethodHandles${'$'}Lookup\");")
            appendLine("    if (localLookup == nullptr) return false;")
            appendLine("    grt_methodhandles_lookup_class = (jclass) env->NewGlobalRef(localLookup);")
            appendLine("    env->DeleteLocalRef(localLookup);")
            appendLine("    if (grt_methodhandles_lookup_class == nullptr) return false;")
            appendLine("    grt_lookup_init_method = env->GetMethodID(grt_methodhandles_lookup_class, \"<init>\", \"(Ljava/lang/Class;)V\");")
            appendLine("    return grt_lookup_init_method != nullptr;")
            appendLine("}")
            appendLine("static inline jobject grt_get_classloader(JNIEnv* env, jclass clazz) {")
            appendLine("    if (clazz == nullptr || grt_get_classloader_method == nullptr) return nullptr;")
            appendLine("    return env->CallObjectMethod(clazz, grt_get_classloader_method);")
            appendLine("}")
            appendLine("static void grt_rethrow_class_not_found(JNIEnv* env, jthrowable exception) {")
            appendLine("    jobject message = nullptr;")
            appendLine("    if (exception != nullptr && grt_throwable_get_message_method != nullptr) {")
            appendLine("        message = env->CallObjectMethod(exception, grt_throwable_get_message_method);")
            appendLine("        if (env->ExceptionCheck()) { env->DeleteLocalRef(exception); return; }")
            appendLine("    }")
            appendLine("    jobject wrapped = nullptr;")
            appendLine("    if (grt_no_class_def_found_class != nullptr && grt_no_class_def_found_init != nullptr) {")
            appendLine("        wrapped = env->NewObject(grt_no_class_def_found_class, grt_no_class_def_found_init, message);")
            appendLine("    }")
            appendLine("    if (!env->ExceptionCheck() && wrapped != nullptr && exception != nullptr && grt_throwable_init_cause_method != nullptr) {")
            appendLine("        env->CallObjectMethod(wrapped, grt_throwable_init_cause_method, exception);")
            appendLine("    }")
            appendLine("    if (!env->ExceptionCheck() && wrapped != nullptr) env->Throw((jthrowable) wrapped);")
            appendLine("    if (message != nullptr) env->DeleteLocalRef(message);")
            appendLine("    if (exception != nullptr) env->DeleteLocalRef(exception);")
            appendLine("}")
            appendLine("static jclass grt_find_class(JNIEnv* env, jobject classloader, const char* internalName) {")
            appendLine("    if (internalName == nullptr) return nullptr;")
            appendLine("    if (internalName[0] == '[' || classloader == nullptr || grt_load_class_method == nullptr) {")
            appendLine("        return env->FindClass(internalName);")
            appendLine("    }")
            appendLine("    std::string binaryName(internalName);")
            appendLine("    for (char& ch : binaryName) if (ch == '/') ch = '.';")
            appendLine("    jstring name = env->NewStringUTF(binaryName.c_str());")
            appendLine("    if (name == nullptr) return nullptr;")
            appendLine("    jobject clazz = env->CallObjectMethod(classloader, grt_load_class_method, name);")
            appendLine("    env->DeleteLocalRef(name);")
            appendLine("    if (env->ExceptionCheck()) {")
            appendLine("        jthrowable exception = env->ExceptionOccurred();")
            appendLine("        env->ExceptionClear();")
            appendLine("        grt_rethrow_class_not_found(env, exception);")
            appendLine("        return nullptr;")
            appendLine("    }")
            appendLine("    return (jclass) clazz;")
            appendLine("}")
            appendLine("static inline jobject grt_get_lookup(JNIEnv* env, jclass clazz) {")
            appendLine("    if (clazz == nullptr || grt_methodhandles_lookup_class == nullptr || grt_lookup_init_method == nullptr) return nullptr;")
            appendLine("    return env->NewObject(grt_methodhandles_lookup_class, grt_lookup_init_method, clazz);")
            appendLine("}")
            appendLine("static inline uint32_t grt_rotl32(uint32_t value, uint32_t distance) {")
            appendLine("    distance &= 31u;")
            appendLine("    return distance == 0u ? value : ((value << distance) | (value >> (32u - distance)));")
            appendLine("}")
            appendLine("static inline jint grt_i32(uint32_t value) {")
            appendLine("    jint result;")
            appendLine("    std::memcpy(&result, &value, sizeof(result));")
            appendLine("    return result;")
            appendLine("}")
            appendLine("static inline jbyte grt_i8(uint32_t value) {")
            appendLine("    uint8_t bits = static_cast<uint8_t>(value);")
            appendLine("    jbyte result;")
            appendLine("    std::memcpy(&result, &bits, sizeof(result));")
            appendLine("    return result;")
            appendLine("}")
            appendLine("static inline jshort grt_i16(uint32_t value) {")
            appendLine("    uint16_t bits = static_cast<uint16_t>(value);")
            appendLine("    jshort result;")
            appendLine("    std::memcpy(&result, &bits, sizeof(result));")
            appendLine("    return result;")
            appendLine("}")
            appendLine("static inline jlong grt_i64(uint64_t value) {")
            appendLine("    jlong result;")
            appendLine("    std::memcpy(&result, &value, sizeof(result));")
            appendLine("    return result;")
            appendLine("}")
            appendLine("static inline uint32_t grt_ishr32_bits(uint32_t bits, uint32_t distance) {")
            appendLine("    uint32_t shift = distance & 31u;")
            appendLine("    if (shift == 0u) return bits;")
            appendLine("    uint32_t result = bits >> shift;")
            appendLine("    if ((bits & 0x80000000u) != 0u) result |= (~static_cast<uint32_t>(0) << (32u - shift));")
            appendLine("    return result;")
            appendLine("}")
            appendLine("static inline jint grt_ishr32(jint value, jint distance) {")
            appendLine("    return grt_i32(grt_ishr32_bits(static_cast<uint32_t>(value), static_cast<uint32_t>(distance)));")
            appendLine("}")
            appendLine("static inline uint64_t grt_lshr64_bits(uint64_t bits, uint32_t distance) {")
            appendLine("    uint32_t shift = distance & 63u;")
            appendLine("    if (shift == 0u) return bits;")
            appendLine("    uint64_t result = bits >> shift;")
            appendLine("    if ((bits & 0x8000000000000000ULL) != 0ULL) result |= (~static_cast<uint64_t>(0) << (64u - shift));")
            appendLine("    return result;")
            appendLine("}")
            appendLine("static inline jlong grt_lshr64(jlong value, jint distance) {")
            appendLine("    return grt_i64(grt_lshr64_bits(static_cast<uint64_t>(value), static_cast<uint32_t>(distance)));")
            appendLine("}")
            appendLine("static inline void grt_throw(JNIEnv* env, const char* className, const char* message) {")
            appendLine("    jclass clazz = env->FindClass(className);")
            appendLine("    if (clazz != nullptr) env->ThrowNew(clazz, message);")
            appendLine("}")
            appendLine("static inline jint grt_baload(JNIEnv* env, jarray array, jint index) {")
            appendLine("    jclass booleanArrayClass = env->FindClass(\"[Z\");")
            appendLine("    if (booleanArrayClass != nullptr && env->IsInstanceOf(array, booleanArrayClass)) {")
            appendLine("        jboolean value = 0;")
            appendLine("        env->GetBooleanArrayRegion((jbooleanArray) array, index, 1, &value);")
            appendLine("        return value ? 1 : 0;")
            appendLine("    }")
            appendLine("    jbyte value = 0;")
            appendLine("    env->GetByteArrayRegion((jbyteArray) array, index, 1, &value);")
            appendLine("    return static_cast<jint>(value);")
            appendLine("}")
            appendLine("static inline void grt_bastore(JNIEnv* env, jarray array, jint index, jint value) {")
            appendLine("    jclass booleanArrayClass = env->FindClass(\"[Z\");")
            appendLine("    if (booleanArrayClass != nullptr && env->IsInstanceOf(array, booleanArrayClass)) {")
            appendLine("        jboolean stored = value != 0;")
            appendLine("        env->SetBooleanArrayRegion((jbooleanArray) array, index, 1, &stored);")
            appendLine("    } else {")
            appendLine("        jbyte stored = grt_i8(static_cast<uint32_t>(value));")
            appendLine("        env->SetByteArrayRegion((jbyteArray) array, index, 1, &stored);")
            appendLine("    }")
            appendLine("}")
            appendLine("static inline jint grt_f2i(jfloat value) {")
            appendLine("    if (std::isnan(value)) return 0;")
            appendLine("    if (value <= static_cast<jfloat>(std::numeric_limits<jint>::min())) return std::numeric_limits<jint>::min();")
            appendLine("    if (value >= static_cast<jfloat>(std::numeric_limits<jint>::max())) return std::numeric_limits<jint>::max();")
            appendLine("    return static_cast<jint>(value);")
            appendLine("}")
            appendLine("static inline jlong grt_f2l(jfloat value) {")
            appendLine("    if (std::isnan(value)) return 0;")
            appendLine("    if (value <= static_cast<jfloat>(std::numeric_limits<jlong>::min())) return std::numeric_limits<jlong>::min();")
            appendLine("    if (value >= static_cast<jfloat>(std::numeric_limits<jlong>::max())) return std::numeric_limits<jlong>::max();")
            appendLine("    return static_cast<jlong>(value);")
            appendLine("}")
            appendLine("static inline jint grt_d2i(jdouble value) {")
            appendLine("    if (std::isnan(value)) return 0;")
            appendLine("    if (value <= static_cast<jdouble>(std::numeric_limits<jint>::min())) return std::numeric_limits<jint>::min();")
            appendLine("    if (value >= static_cast<jdouble>(std::numeric_limits<jint>::max())) return std::numeric_limits<jint>::max();")
            appendLine("    return static_cast<jint>(value);")
            appendLine("}")
            appendLine("static inline jlong grt_d2l(jdouble value) {")
            appendLine("    if (std::isnan(value)) return 0;")
            appendLine("    if (value <= static_cast<jdouble>(std::numeric_limits<jlong>::min())) return std::numeric_limits<jlong>::min();")
            appendLine("    if (value >= static_cast<jdouble>(std::numeric_limits<jlong>::max())) return std::numeric_limits<jlong>::max();")
            appendLine("    return static_cast<jlong>(value);")
            appendLine("}")
            appendLine()

            plan.classes.forEach { classPlan ->
                classPlan.methods.forEach { binding ->
                    appendLine("// ${binding.method.displayName}")
                    appendLine(
                        when (binding.method.lowering) {
                            NativeLoweringKind.SsaPrimitive,
                            NativeLoweringKind.SsaPrimitiveInt -> NativeSsaIntMethodTranslator.translate(
                                binding.method,
                                binding.functionName
                            )
                            NativeLoweringKind.PrimitiveInt -> NativeIntMethodTranslator.translate(
                                binding.method.methodNode,
                                binding.functionName
                            )
                            NativeLoweringKind.FullJvm -> NativeJvmCppMethodTranslator.translate(
                                binding.method,
                                binding.functionName,
                                binding.commitKind
                            )
                        }
                    )
                }
            }

            plan.classes.forEach { classPlan ->
                val registeredMethods = classPlan.methods.filter {
                    it.commitKind != NativeMethodCommitKind.InterfaceProxy &&
                        it.commitKind != NativeMethodCommitKind.InterfaceClassInitializerProxy
                }
                appendLine("static void grt_register_class_${classPlan.classId}(JNIEnv* env, jclass clazz) {")
                if (registeredMethods.isEmpty()) {
                    appendLine("    (void) env;")
                    appendLine("    (void) clazz;")
                } else {
                    appendLine("    JNINativeMethod methods[] = {")
                    registeredMethods.forEach { binding ->
                        append("        { const_cast<char*>(\"")
                            .append(cppString(binding.registeredName))
                            .append("\"), const_cast<char*>(\"")
                            .append(cppString(binding.registeredDesc))
                            .append("\"), reinterpret_cast<void*>(")
                            .append(binding.functionName)
                            .append(") },")
                            .appendLine()
                    }
                    appendLine("    };")
                    appendLine("    env->RegisterNatives(clazz, methods, static_cast<jint>(sizeof(methods) / sizeof(methods[0])));")
                }
                appendLine("}")
                appendLine()
            }

            appendLine("using GrtRegistrar = void (*)(JNIEnv*, jclass);")
            appendLine("static GrtRegistrar grt_registrars[] = {")
            plan.classes.forEach { classPlan ->
                appendLine("    grt_register_class_${classPlan.classId},")
            }
            appendLine("};")
            appendLine()
            appendLine("static void JNICALL grt_register_natives_for_class(JNIEnv* env, jclass, jint classId, jclass clazz) {")
            appendLine("    if (classId < 0) return;")
            appendLine("    const jint count = static_cast<jint>(sizeof(grt_registrars) / sizeof(grt_registrars[0]));")
            appendLine("    if (classId >= count) return;")
            appendLine("    grt_registrars[classId](env, clazz);")
            appendLine("}")
            appendLine()
            appendLine("extern \"C\" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {")
            appendLine("    JNIEnv* env = nullptr;")
            appendLine("    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8) != JNI_OK) return JNI_ERR;")
            append("    jclass loader = env->FindClass(\"")
                .append(cppString(plan.loaderInternalName))
                .appendLine("\");")
            appendLine("    if (loader == nullptr) return JNI_ERR;")
            appendLine("    if (!grt_init_runtime(env)) return JNI_ERR;")
            val loaderProxyMethods = plan.classes
                .flatMap { it.methods }
                .filter {
                    it.commitKind == NativeMethodCommitKind.InterfaceProxy ||
                        it.commitKind == NativeMethodCommitKind.InterfaceClassInitializerProxy
                }
            appendLine("    JNINativeMethod loaderMethods[] = {")
            appendLine("        { const_cast<char*>(\"registerNativesForClass\"), const_cast<char*>(\"(ILjava/lang/Class;)V\"), reinterpret_cast<void*>(grt_register_natives_for_class) },")
            loaderProxyMethods.forEach { binding ->
                append("        { const_cast<char*>(\"")
                    .append(cppString(binding.registeredName))
                    .append("\"), const_cast<char*>(\"")
                    .append(cppString(binding.registeredDesc))
                    .append("\"), reinterpret_cast<void*>(")
                    .append(binding.functionName)
                    .append(") },")
                    .appendLine()
            }
            appendLine("    };")
            appendLine("    env->RegisterNatives(loader, loaderMethods, static_cast<jint>(sizeof(loaderMethods) / sizeof(loaderMethods[0])));")
            appendLine("    if (env->ExceptionCheck()) return JNI_ERR;")
            appendLine("    return JNI_VERSION_1_8;")
            appendLine("}")
        }
    }

    private fun cppString(value: String): String {
        return buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }
}
