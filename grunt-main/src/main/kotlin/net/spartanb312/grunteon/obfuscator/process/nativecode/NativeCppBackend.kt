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
        val singleSource = emitSource(plan)
        return NativeSourceBundle(
            plan = plan,
            sourceText = singleSource,
            sourcePath = sourcePath,
            libraryPath = libraryPath,
            sourceFiles = emitSourceFiles(plan, sourcePath, singleSource, config)
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

    private fun emitSourceFiles(
        plan: NativeBuildPlan,
        sourcePath: Path,
        singleSource: String,
        config: NativePipelineConfig
    ): List<NativeSourceFile> {
        val methodCount = plan.classes.sumOf { it.methods.size }
        if (!config.splitSourceFiles || methodCount <= config.maxMethodsPerSourceFile) {
            return listOf(NativeSourceFile(sourcePath, singleSource))
        }

        val sourceRoot = sourcePath.parent
        val commonPrefix = commonSourcePrefix(plan, singleSource)
        val chunks = splitClassPlans(plan.classes, config.maxMethodsPerSourceFile)
        return buildList {
            add(
                NativeSourceFile(
                    sourceRoot.resolve("grunteon_native_register.cpp"),
                    emitSplitRegisterSource(plan, chunks.indices.toList(), commonPrefix)
                )
            )
            chunks.forEachIndexed { index, classPlans ->
                add(
                    NativeSourceFile(
                        sourceRoot.resolve("grunteon_native_chunk_${index.toString().padStart(4, '0')}.cpp"),
                        emitSplitChunkSource(index, classPlans, commonPrefix)
                    )
                )
            }
        }
    }

    private fun commonSourcePrefix(plan: NativeBuildPlan, singleSource: String): String {
        val firstMethod = plan.classes.firstOrNull()?.methods?.firstOrNull() ?: return singleSource
        val marker = "// ${firstMethod.method.displayName}"
        return singleSource.substringBefore(marker)
    }

    private fun splitClassPlans(
        classes: List<NativeClassPlan>,
        maxMethodsPerSourceFile: Int
    ): List<List<NativeClassPlan>> {
        val chunks = mutableListOf<List<NativeClassPlan>>()
        var current = mutableListOf<NativeClassPlan>()
        var currentMethodCount = 0
        val maxMethods = maxOf(1, maxMethodsPerSourceFile)
        classes.forEach { classPlan ->
            val classMethodCount = maxOf(1, classPlan.methods.size)
            if (current.isNotEmpty() && currentMethodCount + classMethodCount > maxMethods) {
                chunks += current
                current = mutableListOf()
                currentMethodCount = 0
            }
            current += classPlan
            currentMethodCount += classMethodCount
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    private fun emitSplitChunkSource(
        chunkIndex: Int,
        classPlans: List<NativeClassPlan>,
        commonPrefix: String
    ): String {
        return buildString {
            append(commonPrefix)
            classPlans.forEach { classPlan ->
                classPlan.methods.forEach { binding ->
                    appendLine("// ${binding.method.displayName}")
                    appendLine(emitNativeMethod(binding))
                }
            }
            classPlans.forEach { classPlan ->
                appendClassRegistrar(classPlan, externalLinkage = true, initializeRuntime = true)
            }
            val loaderProxyMethods = classPlans
                .flatMap { it.methods }
                .filter {
                    it.commitKind == NativeMethodCommitKind.InterfaceProxy ||
                        it.commitKind == NativeMethodCommitKind.InterfaceClassInitializerProxy
                }
            appendLine("void grt_register_loader_proxies_chunk_$chunkIndex(JNIEnv* env, jclass loader) {")
            if (loaderProxyMethods.isEmpty()) {
                appendLine("    (void) env;")
                appendLine("    (void) loader;")
            } else {
                appendLine("    if (!grt_init_runtime(env)) return;")
                appendLine("    JNINativeMethod loaderMethods[] = {")
                loaderProxyMethods.forEach { binding ->
                    append("        { const_cast<char*>(\"")
                        .append(cppModifiedUtf8String(binding.registeredName))
                        .append("\"), const_cast<char*>(\"")
                        .append(cppModifiedUtf8String(binding.registeredDesc))
                        .append("\"), reinterpret_cast<void*>(")
                        .append(binding.functionName)
                        .append(") },")
                        .appendLine()
                }
                appendLine("    };")
                appendLine("    env->RegisterNatives(loader, loaderMethods, static_cast<jint>(sizeof(loaderMethods) / sizeof(loaderMethods[0])));")
            }
            appendLine("}")
            appendLine()
        }
    }

    private fun emitSplitRegisterSource(
        plan: NativeBuildPlan,
        chunkIndices: List<Int>,
        commonPrefix: String
    ): String {
        return buildString {
            append(commonPrefix)
            plan.classes.forEach { classPlan ->
                appendLine("extern void grt_register_class_${classPlan.classId}(JNIEnv* env, jclass clazz);")
            }
            chunkIndices.forEach { chunkIndex ->
                appendLine("extern void grt_register_loader_proxies_chunk_$chunkIndex(JNIEnv* env, jclass loader);")
            }
            appendLine()
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
            appendLine("    JNINativeMethod loaderMethods[] = {")
            appendLine("        { const_cast<char*>(\"registerNativesForClass\"), const_cast<char*>(\"(ILjava/lang/Class;)V\"), reinterpret_cast<void*>(grt_register_natives_for_class) },")
            appendLine("    };")
            appendLine("    env->RegisterNatives(loader, loaderMethods, static_cast<jint>(sizeof(loaderMethods) / sizeof(loaderMethods[0])));")
            appendLine("    if (env->ExceptionCheck()) return JNI_ERR;")
            chunkIndices.forEach { chunkIndex ->
                appendLine("    grt_register_loader_proxies_chunk_$chunkIndex(env, loader);")
                appendLine("    if (env->ExceptionCheck()) return JNI_ERR;")
            }
            appendLine("    return JNI_VERSION_1_8;")
            appendLine("}")
        }
    }

    private fun emitSource(plan: NativeBuildPlan): String {
        return buildString {
            appendLine("#include <jni.h>")
            appendLine("#include <cmath>")
            appendLine("#include <cstdint>")
            appendLine("#include <cstring>")
            appendLine("#include <limits>")
            appendLine("#include <mutex>")
            appendLine("#include <string>")
            appendLine("#include <unordered_map>")
            appendLine("#include <unordered_set>")
            appendLine("#include <vector>")
            appendLine()
            appendLine("static jclass grt_class_class = nullptr;")
            appendLine("static jmethodID grt_get_classloader_method = nullptr;")
            appendLine("static jclass grt_classloader_class = nullptr;")
            appendLine("static jmethodID grt_load_class_method = nullptr;")
            appendLine("static jclass grt_boolean_array_class = nullptr;")
            appendLine("struct GrtClassCacheEntry { jweak loader; jweak clazz; };")
            appendLine("static std::mutex grt_class_cache_mutex;")
            appendLine("static std::unordered_map<std::string, std::vector<GrtClassCacheEntry>> grt_class_cache;")
            appendLine("struct GrtMethodCacheEntry { jweak clazz; jmethodID id; };")
            appendLine("struct GrtFieldCacheEntry { jweak clazz; jfieldID id; };")
            appendLine("static std::mutex grt_method_cache_mutex;")
            appendLine("static std::mutex grt_field_cache_mutex;")
            appendLine("static std::unordered_map<std::string, std::vector<GrtMethodCacheEntry>> grt_method_cache;")
            appendLine("static std::unordered_map<std::string, std::vector<GrtFieldCacheEntry>> grt_field_cache;")
            appendLine("static jclass grt_no_class_def_found_class = nullptr;")
            appendLine("static jmethodID grt_no_class_def_found_init = nullptr;")
            appendLine("static jclass grt_throwable_class = nullptr;")
            appendLine("static jmethodID grt_throwable_get_message_method = nullptr;")
            appendLine("static jmethodID grt_throwable_init_cause_method = nullptr;")
            appendLine("static jclass grt_string_class = nullptr;")
            appendLine("static jmethodID grt_string_intern_method = nullptr;")
            appendLine("static std::mutex grt_string_cache_mutex;")
            appendLine("static std::unordered_map<std::string, jstring> grt_string_cache;")
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
            appendLine("    jclass localBooleanArray = env->FindClass(\"[Z\");")
            appendLine("    if (localBooleanArray == nullptr) return false;")
            appendLine("    grt_boolean_array_class = (jclass) env->NewGlobalRef(localBooleanArray);")
            appendLine("    env->DeleteLocalRef(localBooleanArray);")
            appendLine("    if (grt_boolean_array_class == nullptr) return false;")
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
            appendLine("    jclass localString = env->FindClass(\"java/lang/String\");")
            appendLine("    if (localString == nullptr) return false;")
            appendLine("    grt_string_class = (jclass) env->NewGlobalRef(localString);")
            appendLine("    env->DeleteLocalRef(localString);")
            appendLine("    if (grt_string_class == nullptr) return false;")
            appendLine("    grt_string_intern_method = env->GetMethodID(grt_string_class, \"intern\", \"()Ljava/lang/String;\");")
            appendLine("    if (grt_string_intern_method == nullptr) return false;")
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
            appendLine("    std::string cacheName(internalName);")
            appendLine("    {")
            appendLine("        std::lock_guard<std::mutex> lock(grt_class_cache_mutex);")
            appendLine("        auto cached = grt_class_cache.find(cacheName);")
            appendLine("        if (cached != grt_class_cache.end()) {")
            appendLine("            for (const GrtClassCacheEntry& entry : cached->second) {")
            appendLine("                if (entry.clazz == nullptr || env->IsSameObject(entry.clazz, nullptr)) continue;")
            appendLine("                const bool sameLoader = (entry.loader == nullptr && classloader == nullptr) ||")
            appendLine("                    (entry.loader != nullptr && classloader != nullptr && !env->IsSameObject(entry.loader, nullptr) && env->IsSameObject(entry.loader, classloader));")
            appendLine("                if (sameLoader) {")
            appendLine("                    jobject strongClass = env->NewLocalRef(entry.clazz);")
            appendLine("                    if (strongClass == nullptr) continue;")
            appendLine("                    return (jclass) strongClass;")
            appendLine("                }")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine("    jclass localClass = nullptr;")
            appendLine("    if (internalName[0] == '[' || classloader == nullptr || grt_load_class_method == nullptr) {")
            appendLine("        localClass = env->FindClass(internalName);")
            appendLine("    } else {")
            appendLine("    std::string binaryName(internalName);")
            appendLine("    for (char& ch : binaryName) if (ch == '/') ch = '.';")
            appendLine("    jstring name = env->NewStringUTF(binaryName.c_str());")
            appendLine("    if (name == nullptr) return nullptr;")
            appendLine("    localClass = (jclass) env->CallObjectMethod(classloader, grt_load_class_method, name);")
            appendLine("    env->DeleteLocalRef(name);")
            appendLine("    if (env->ExceptionCheck()) {")
            appendLine("        jthrowable exception = env->ExceptionOccurred();")
            appendLine("        env->ExceptionClear();")
            appendLine("        grt_rethrow_class_not_found(env, exception);")
            appendLine("        return nullptr;")
            appendLine("    }")
            appendLine("    }")
            appendLine("    if (localClass == nullptr || env->ExceptionCheck()) return localClass;")
            appendLine("    jweak classRef = env->NewWeakGlobalRef(localClass);")
            appendLine("    if (classRef == nullptr) { env->DeleteLocalRef(localClass); return nullptr; }")
            appendLine("    jweak loaderRef = classloader == nullptr ? nullptr : env->NewWeakGlobalRef(classloader);")
            appendLine("    if (classloader != nullptr && loaderRef == nullptr) { env->DeleteWeakGlobalRef(classRef); env->DeleteLocalRef(localClass); return nullptr; }")
            appendLine("    {")
            appendLine("        std::lock_guard<std::mutex> lock(grt_class_cache_mutex);")
            appendLine("        auto& entries = grt_class_cache[cacheName];")
            appendLine("        for (const GrtClassCacheEntry& entry : entries) {")
            appendLine("            if (entry.clazz == nullptr || env->IsSameObject(entry.clazz, nullptr)) continue;")
            appendLine("            const bool sameLoader = (entry.loader == nullptr && classloader == nullptr) ||")
            appendLine("                (entry.loader != nullptr && classloader != nullptr && !env->IsSameObject(entry.loader, nullptr) && env->IsSameObject(entry.loader, classloader));")
            appendLine("            if (sameLoader) {")
            appendLine("                env->DeleteWeakGlobalRef(classRef);")
            appendLine("                if (loaderRef != nullptr) env->DeleteWeakGlobalRef(loaderRef);")
            appendLine("                return localClass;")
            appendLine("            }")
            appendLine("        }")
            appendLine("        entries.push_back({ loaderRef, classRef });")
            appendLine("    }")
            appendLine("    return localClass;")
            appendLine("}")
            appendLine("static inline void grt_track_ref(JNIEnv* env, std::unordered_set<jobject>& refs, jobject ref) {")
            appendLine("    if (ref != nullptr && env->GetObjectRefType(ref) == JNILocalRefType) refs.insert(ref);")
            appendLine("}")
            appendLine("static inline void grt_clear_refs(JNIEnv* env, std::unordered_set<jobject>& refs) {")
            appendLine("    for (jobject ref : refs) {")
            appendLine("        if (ref != nullptr && env->GetObjectRefType(ref) == JNILocalRefType) env->DeleteLocalRef(ref);")
            appendLine("    }")
            appendLine("    refs.clear();")
            appendLine("}")
            appendLine("static inline std::string grt_member_cache_key(const char* name, const char* desc, bool isStatic) {")
            appendLine("    std::string key(isStatic ? \"S:\" : \"I:\");")
            appendLine("    key += name == nullptr ? \"\" : name;")
            appendLine("    key += '\\n';")
            appendLine("    key += desc == nullptr ? \"\" : desc;")
            appendLine("    return key;")
            appendLine("}")
            appendLine("static jmethodID grt_get_method_id(JNIEnv* env, jclass clazz, const char* name, const char* desc, bool isStatic) {")
            appendLine("    if (clazz == nullptr || name == nullptr || desc == nullptr) return nullptr;")
            appendLine("    std::string key = grt_member_cache_key(name, desc, isStatic);")
            appendLine("    {")
            appendLine("        std::lock_guard<std::mutex> lock(grt_method_cache_mutex);")
            appendLine("        auto cached = grt_method_cache.find(key);")
            appendLine("        if (cached != grt_method_cache.end()) {")
            appendLine("            for (const GrtMethodCacheEntry& entry : cached->second) {")
            appendLine("                if (entry.id != nullptr && entry.clazz != nullptr && !env->IsSameObject(entry.clazz, nullptr) && env->IsSameObject(entry.clazz, clazz)) return entry.id;")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine("    jmethodID resolved = isStatic ? env->GetStaticMethodID(clazz, name, desc) : env->GetMethodID(clazz, name, desc);")
            appendLine("    if (resolved == nullptr || env->ExceptionCheck()) return resolved;")
            appendLine("    jweak classRef = env->NewWeakGlobalRef(clazz);")
            appendLine("    if (classRef == nullptr) return nullptr;")
            appendLine("    {")
            appendLine("        std::lock_guard<std::mutex> lock(grt_method_cache_mutex);")
            appendLine("        auto& entries = grt_method_cache[key];")
            appendLine("        for (const GrtMethodCacheEntry& entry : entries) {")
            appendLine("            if (entry.id != nullptr && entry.clazz != nullptr && !env->IsSameObject(entry.clazz, nullptr) && env->IsSameObject(entry.clazz, clazz)) {")
            appendLine("                env->DeleteWeakGlobalRef(classRef);")
            appendLine("                return entry.id;")
            appendLine("            }")
            appendLine("        }")
            appendLine("        entries.push_back({ classRef, resolved });")
            appendLine("    }")
            appendLine("    return resolved;")
            appendLine("}")
            appendLine("static jfieldID grt_get_field_id(JNIEnv* env, jclass clazz, const char* name, const char* desc, bool isStatic) {")
            appendLine("    if (clazz == nullptr || name == nullptr || desc == nullptr) return nullptr;")
            appendLine("    std::string key = grt_member_cache_key(name, desc, isStatic);")
            appendLine("    {")
            appendLine("        std::lock_guard<std::mutex> lock(grt_field_cache_mutex);")
            appendLine("        auto cached = grt_field_cache.find(key);")
            appendLine("        if (cached != grt_field_cache.end()) {")
            appendLine("            for (const GrtFieldCacheEntry& entry : cached->second) {")
            appendLine("                if (entry.id != nullptr && entry.clazz != nullptr && !env->IsSameObject(entry.clazz, nullptr) && env->IsSameObject(entry.clazz, clazz)) return entry.id;")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine("    jfieldID resolved = isStatic ? env->GetStaticFieldID(clazz, name, desc) : env->GetFieldID(clazz, name, desc);")
            appendLine("    if (resolved == nullptr || env->ExceptionCheck()) return resolved;")
            appendLine("    jweak classRef = env->NewWeakGlobalRef(clazz);")
            appendLine("    if (classRef == nullptr) return nullptr;")
            appendLine("    {")
            appendLine("        std::lock_guard<std::mutex> lock(grt_field_cache_mutex);")
            appendLine("        auto& entries = grt_field_cache[key];")
            appendLine("        for (const GrtFieldCacheEntry& entry : entries) {")
            appendLine("            if (entry.id != nullptr && entry.clazz != nullptr && !env->IsSameObject(entry.clazz, nullptr) && env->IsSameObject(entry.clazz, clazz)) {")
            appendLine("                env->DeleteWeakGlobalRef(classRef);")
            appendLine("                return entry.id;")
            appendLine("            }")
            appendLine("        }")
            appendLine("        entries.push_back({ classRef, resolved });")
            appendLine("    }")
            appendLine("    return resolved;")
            appendLine("}")
            appendLine("static inline jstring grt_ldc_string(JNIEnv* env, const char* value) {")
            appendLine("    if (value == nullptr) return nullptr;")
            appendLine("    std::lock_guard<std::mutex> lock(grt_string_cache_mutex);")
            appendLine("    auto cached = grt_string_cache.find(value);")
            appendLine("    if (cached != grt_string_cache.end()) return cached->second;")
            appendLine("    jstring raw = env->NewStringUTF(value);")
            appendLine("    if (raw == nullptr) return nullptr;")
            appendLine("    jstring interned = raw;")
            appendLine("    if (grt_string_intern_method != nullptr) {")
            appendLine("        interned = (jstring) env->CallObjectMethod(raw, grt_string_intern_method);")
            appendLine("        if (env->ExceptionCheck()) { env->DeleteLocalRef(raw); return nullptr; }")
            appendLine("    }")
            appendLine("    if (interned == nullptr) { env->DeleteLocalRef(raw); return nullptr; }")
            appendLine("    jstring global = (jstring) env->NewGlobalRef(interned);")
            appendLine("    if (interned != raw) env->DeleteLocalRef(interned);")
            appendLine("    env->DeleteLocalRef(raw);")
            appendLine("    if (global == nullptr) return nullptr;")
            appendLine("    grt_string_cache.emplace(value, global);")
            appendLine("    return global;")
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
            appendLine("    if (clazz != nullptr) {")
            appendLine("        env->ThrowNew(clazz, message);")
            appendLine("        env->DeleteLocalRef(clazz);")
            appendLine("    }")
            appendLine("}")
            appendLine("static inline jint grt_baload(JNIEnv* env, jarray array, jint index) {")
            appendLine("    if (grt_boolean_array_class != nullptr && env->IsInstanceOf(array, grt_boolean_array_class)) {")
            appendLine("        jboolean value = 0;")
            appendLine("        env->GetBooleanArrayRegion((jbooleanArray) array, index, 1, &value);")
            appendLine("        return value ? 1 : 0;")
            appendLine("    }")
            appendLine("    jbyte value = 0;")
            appendLine("    env->GetByteArrayRegion((jbyteArray) array, index, 1, &value);")
            appendLine("    return static_cast<jint>(value);")
            appendLine("}")
            appendLine("static inline void grt_bastore(JNIEnv* env, jarray array, jint index, jint value) {")
            appendLine("    if (grt_boolean_array_class != nullptr && env->IsInstanceOf(array, grt_boolean_array_class)) {")
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
                    appendLine(emitNativeMethod(binding))
                }
            }

            plan.classes.forEach { classPlan ->
                appendClassRegistrar(classPlan, externalLinkage = false, initializeRuntime = false)
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
                    .append(cppModifiedUtf8String(binding.registeredName))
                    .append("\"), const_cast<char*>(\"")
                    .append(cppModifiedUtf8String(binding.registeredDesc))
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

    private fun StringBuilder.appendClassRegistrar(
        classPlan: NativeClassPlan,
        externalLinkage: Boolean,
        initializeRuntime: Boolean
    ) {
        val registeredMethods = classPlan.methods.filter {
            it.commitKind != NativeMethodCommitKind.InterfaceProxy &&
                it.commitKind != NativeMethodCommitKind.InterfaceClassInitializerProxy
        }
        append(if (externalLinkage) "void " else "static void ")
            .append("grt_register_class_")
            .append(classPlan.classId)
            .appendLine("(JNIEnv* env, jclass clazz) {")
        if (registeredMethods.isEmpty()) {
            appendLine("    (void) env;")
            appendLine("    (void) clazz;")
        } else {
            if (initializeRuntime) appendLine("    if (!grt_init_runtime(env)) return;")
            appendLine("    JNINativeMethod methods[] = {")
            registeredMethods.forEach { binding ->
                append("        { const_cast<char*>(\"")
                    .append(cppModifiedUtf8String(binding.registeredName))
                    .append("\"), const_cast<char*>(\"")
                    .append(cppModifiedUtf8String(binding.registeredDesc))
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

    private fun emitNativeMethod(binding: NativeMethodBinding): String {
        return when (binding.method.lowering) {
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
    }

    private fun cppModifiedUtf8String(value: String): String {
        return buildString {
            value.forEach { char ->
                val code = char.code
                when {
                    code in 0x0001..0x007f -> appendCppStringByte(code)
                    code > 0x07ff -> {
                        appendCppStringByte(0xe0 or ((code shr 12) and 0x0f))
                        appendCppStringByte(0x80 or ((code shr 6) and 0x3f))
                        appendCppStringByte(0x80 or (code and 0x3f))
                    }
                    else -> {
                        appendCppStringByte(0xc0 or ((code shr 6) and 0x1f))
                        appendCppStringByte(0x80 or (code and 0x3f))
                    }
                }
            }
        }
    }

    private fun StringBuilder.appendCppStringByte(byte: Int) {
        when (byte) {
            '\\'.code -> append("\\\\")
            '"'.code -> append("\\\"")
            '\n'.code -> append("\\n")
            '\r'.code -> append("\\r")
            '\t'.code -> append("\\t")
            in 0x20..0x7e -> append(byte.toChar())
            else -> append("\\").append(byte.toString(8).padStart(3, '0'))
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
