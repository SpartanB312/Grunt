package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmIrImporter
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmSupportAnalyzer
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeJvmCppMethodTranslatorTest {

    @Test
    fun backendEmitsClassloaderAwareRuntimeHelpers() {
        val source = NativeCppBackend.generate(
            methods = emptyList(),
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "static jclass grt_find_class(JNIEnv* env, jobject classloader, jint slot, const char* internalName)")
        assertContains(source, "struct GrtClassCacheEntry { jweak loader; jweak clazz; };")
        assertContains(source, "struct GrtClassSlot { std::mutex mutex; std::vector<GrtClassCacheEntry> entries; };")
        assertContains(source, "static constexpr jint grt_class_slot_count = 0;")
        assertContains(source, "static GrtClassSlot grt_class_slots[1];")
        assertContains(source, "jobject strongClass = env->NewLocalRef(it->clazz);")
        assertContains(source, "if (strongClass != nullptr) return (jclass) strongClass;")
        assertContains(source, "jweak classRef = env->NewWeakGlobalRef(localClass);")
        assertContains(source, "entries.push_back({ loaderRef, classRef });")
        assertContains(source, "return localClass;")
        assertContains(source, "struct GrtMethodCacheEntry { jweak clazz; jmethodID id; };")
        assertContains(source, "struct GrtFieldCacheEntry { jweak clazz; jfieldID id; };")
        assertContains(source, "struct GrtMethodSlot { std::mutex mutex; std::vector<GrtMethodCacheEntry> entries; };")
        assertContains(source, "static constexpr jint grt_method_slot_count = 0;")
        assertContains(source, "static GrtMethodSlot grt_method_slots[1];")
        assertContains(source, "static jmethodID grt_get_method_id(JNIEnv* env, jclass clazz, jint slot, const char* name, const char* desc, bool isStatic)")
        assertContains(source, "static jfieldID grt_get_field_id(JNIEnv* env, jclass clazz, jint slot, const char* name, const char* desc, bool isStatic)")
        assertFalse(source.contains("grt_member_cache_key"))
        assertFalse(source.contains("grt_class_cache"))
        assertFalse(source.contains("grt_method_cache"))
        assertFalse(source.contains("grt_field_cache"))
        assertContains(source, "grt_load_class_method = env->GetMethodID(grt_classloader_class, \"loadClass\", \"(Ljava/lang/String;)Ljava/lang/Class;\");")
        assertContains(source, "static bool grt_runtime_initialized = false;")
        assertContains(source, "if (grt_runtime_initialized) return true;")
        assertContains(source, "if (!grt_init_runtime(env)) return JNI_ERR;")
        assertContains(source, "static inline jint grt_i32(uint32_t value)")
        assertContains(source, "static inline jbyte grt_i8(uint32_t value)")
        assertContains(source, "static inline jshort grt_i16(uint32_t value)")
        assertContains(source, "static inline jlong grt_i64(uint64_t value)")
        assertContains(source, "static inline uint32_t grt_ishr32_bits(uint32_t bits, uint32_t distance)")
        assertContains(source, "static inline jint grt_ishr32(jint value, jint distance)")
        assertContains(source, "static inline uint64_t grt_lshr64_bits(uint64_t bits, uint32_t distance)")
        assertContains(source, "static inline jlong grt_lshr64(jlong value, jint distance)")
        assertContains(source, "env->ThrowNew(clazz, message);")
        assertContains(source, "env->DeleteLocalRef(clazz);")
        assertContains(source, "static inline bool grt_monitor_enter(JNIEnv* env, jobject lock, std::vector<jobject>& heldMonitors)")
        assertContains(source, "jobject held = env->NewLocalRef(lock);")
        assertContains(source, "heldMonitors.push_back(held);")
        assertContains(source, "static inline bool grt_monitor_exit(JNIEnv* env, jobject lock, std::vector<jobject>& heldMonitors)")
        assertContains(source, "static inline void grt_release_held_monitors(JNIEnv* env, std::vector<jobject>& heldMonitors)")
        assertContains(source, "grt_throw(env, \"java/lang/NullPointerException\", \"MONITORENTER npe\");")
        assertContains(source, "grt_throw(env, \"java/lang/NullPointerException\", \"MONITOREXIT npe\");")
        assertContains(source, "grt_throw(env, \"java/lang/IllegalMonitorStateException\", \"MonitorExit failed\");")
        assertContains(source, "using GrtLocalRefs = std::vector<jobject>;")
        assertContains(source, "static inline void grt_forget_ref(GrtLocalRefs& refs, jobject ref)")
        assertContains(source, "static inline void grt_track_ref(JNIEnv* env, GrtLocalRefs& refs, jobject ref)")
        assertContains(source, "static inline void grt_clear_refs(JNIEnv* env, GrtLocalRefs& refs)")
        assertFalse(source.contains("#include <unordered_set>"))
        assertContains(source, "grt_string_intern_method = env->GetMethodID(grt_string_class, \"intern\", \"()Ljava/lang/String;\");")
        assertContains(source, "static inline jstring grt_ldc_string(JNIEnv* env, const char* value)")
        assertContains(source, "static std::unordered_map<std::string, jstring> grt_string_cache;")
        assertContains(source, "auto cached = grt_string_cache.find(value);")
        assertContains(source, "static jclass grt_boolean_array_class = nullptr;")
        assertContains(source, "jclass localBooleanArray = env->FindClass(\"[Z\");")
        assertContains(source, "grt_boolean_array_class = (jclass) env->NewGlobalRef(localBooleanArray);")
        assertContains(source, "env->DeleteLocalRef(localBooleanArray);")
        assertContains(source, "env->IsInstanceOf(array, grt_boolean_array_class)")
    }

    @Test
    fun backendEmitsModifiedUtf8RegisterNativesNames() {
        val source = NativeCppBackend.generate(
            methods = listOf(validated(constantIntMethod("n\u0000\u00e9"))),
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "const_cast<char*>(\"n\\300\\200\\303\\251\")")
    }

    @Test
    fun emitsConditionalAndUnconditionalBranches() {
        val method = branchMethod()
        val translated = translate(method)

        assertContains(translated, "if (value >= 0) goto L_BC_5;")
        assertContains(translated, "goto L_BC_7;")
    }

    @Test
    fun emitsTableAndLookupSwitchBranches() {
        val tableSwitch = translate(tableSwitchMethod())
        assertContains(tableSwitch, "case 1: goto L_BC_3;")
        assertContains(tableSwitch, "case 2: goto L_BC_6;")
        assertContains(tableSwitch, "default: goto L_BC_9;")

        val lookupSwitch = translate(lookupSwitchMethod())
        assertContains(lookupSwitch, "case 7: goto L_BC_3;")
        assertContains(lookupSwitch, "case 9: goto L_BC_6;")
        assertContains(lookupSwitch, "default: goto L_BC_9;")
    }

    @Test
    fun emitsStaticMethodCallsWithIntAndObjectReturns() {
        val intCall = translate(invokeStaticIntMethod())
        assertContains(intCall, "jobject classloader = grt_get_classloader(env, clazz);")
        assertContains(intCall, "GrtLocalRefs ownedRefs;")
        assertContains(intCall, "grt_track_ref(env, ownedRefs, classloader);")
        assertContains(intCall, "grt_find_class(env, classloader, 0, \"java/lang/Integer\")")
        assertContains(intCall, "grt_track_ref(env, refs, ownerClass_1);")
        assertContains(intCall, "grt_get_method_id(env, ownerClass_1, 0, \"bitCount\", \"(I)I\", true)")
        assertContains(intCall, "cstack[sp++].i = static_cast<jint>(env->CallStaticIntMethodA(ownerClass_1, methodId_1, args_1));")

        val objectCall = translate(invokeStaticObjectMethod())
        assertContains(objectCall, "grt_find_class(env, classloader, 0, \"java/lang/String\")")
        assertContains(objectCall, "grt_get_method_id(env, ownerClass_1, 0, \"valueOf\", \"(I)Ljava/lang/String;\", true)")
        assertContains(objectCall, "cstack[sp++].l = env->CallStaticObjectMethodA(ownerClass_1, methodId_1, args_1);")
        assertContains(objectCall, "jobject result = cstack[--sp].l; grt_forget_ref(refs, result); grt_release_held_monitors(env, heldMonitors); grt_clear_refs(env, refs); grt_clear_refs(env, ownedRefs); return result;")
    }

    @Test
    fun emitsVectorRefTrackingForRepeatedObjectReturningCalls() {
        val repeated = translate(repeatedObjectReturningCallsMethod())

        assertContains(repeated, "GrtLocalRefs refs;")
        assertContains(repeated, "GrtLocalRefs ownedRefs;")
        assertFalse(repeated.contains("std::unordered_set<jobject>"))
        assertTrue(repeated.split("CallStaticObjectMethodA").size >= 4)
        assertTrue(repeated.split("grt_track_ref(env, refs, cstack[sp - 1].l);").size >= 4)
        assertContains(repeated, "grt_forget_ref(refs, result);")
    }

    @Test
    fun emitsFieldAccesses() {
        val getStatic = translate(getStaticFieldMethod())
        assertContains(getStatic, "grt_track_ref(env, refs, fieldOwner_0);")
        assertContains(getStatic, "grt_get_field_id(env, fieldOwner_0, 0, \"VALUE\", \"I\", true)")
        assertContains(getStatic, "cstack[sp++].i = static_cast<jint>(env->GetStaticIntField(fieldOwner_0, fieldId_0));")

        val putField = translate(putFieldMethod())
        assertContains(putField, "jint fieldValue_2 = static_cast<jint>(cstack[--sp].i);")
        assertContains(putField, "grt_get_field_id(env, fieldOwner_2, 0, \"value\", \"I\", false)")
        assertContains(putField, "env->SetIntField(receiver, fieldId_2, fieldValue_2);")
    }

    @Test
    fun emitsTypeAndArrayOperations() {
        val newObject = translate(newObjectMethod())
        assertContains(newObject, "grt_find_class(env, classloader, 0, \"java/lang/StringBuilder\")")
        assertContains(newObject, "grt_track_ref(env, refs, typeClass);")
        assertContains(newObject, "cstack[sp++].l = env->AllocObject(typeClass);")
        assertContains(newObject, "cstack[sp] = cstack[sp - 1]; ++sp;")

        val checkcast = translate(checkcastMethod())
        assertContains(checkcast, "grt_throw(env, \"java/lang/ClassCastException\", \"java/lang/String\");")

        val instanceOf = translate(instanceOfMethod())
        assertContains(instanceOf, "cstack[sp++].i = typeClass != nullptr && env->IsInstanceOf(value, typeClass) ? 1 : 0;")

        val newIntArray = translate(newIntArrayMethod())
        assertContains(newIntArray, "cstack[sp++].l = env->NewIntArray(count);")

        val newObjectArray = translate(newObjectArrayMethod())
        assertContains(newObjectArray, "cstack[sp++].l = env->NewObjectArray(count, elementClass, nullptr);")

        val arrayLength = translate(arrayLengthMethod())
        assertContains(arrayLength, "cstack[sp++].i = env->GetArrayLength((jarray) array);")

        val objectClassLiteral = translate(objectClassLiteralMethod())
        assertContains(objectClassLiteral, "grt_find_class(env, classloader, 0, \"java/lang/String\")")
        assertContains(objectClassLiteral, "grt_track_ref(env, refs, classLookup_0);")
        assertContains(objectClassLiteral, "cstack[sp++].l = classObject_0;")

        val primitiveClassLiteral = translate(primitiveClassLiteralMethod())
        assertContains(primitiveClassLiteral, "grt_find_class(env, classloader, 0, \"java/lang/Integer\")")
        assertContains(primitiveClassLiteral, "grt_track_ref(env, refs, wrapperClass_0);")
        assertContains(primitiveClassLiteral, "grt_get_field_id(env, wrapperClass_0, 0, \"TYPE\", \"Ljava/lang/Class;\", true)")
        assertContains(primitiveClassLiteral, "env->GetStaticObjectField(wrapperClass_0, typeField_0)")
    }

    @Test
    fun emitsArrayLoadAndStoreOperations() {
        val intLoad = translate(intArrayLoadMethod())
        assertContains(intLoad, "env->GetIntArrayRegion((jintArray) array, index, 1, &value);")

        val byteLoad = translate(byteArrayLoadMethod())
        assertContains(byteLoad, "cstack[sp++].i = grt_baload(env, (jarray) array, index);")

        val byteStore = translate(byteArrayStoreMethod())
        assertContains(byteStore, "grt_bastore(env, (jarray) array, index, value);")

        val charStore = translate(charArrayStoreMethod())
        assertContains(charStore, "jchar value = static_cast<jchar>(static_cast<uint32_t>(cstack[--sp].i) & 0xffffu);")
        assertContains(charStore, "env->SetCharArrayRegion((jcharArray) array, index, 1, &value);")

        val shortStore = translate(shortArrayStoreMethod())
        assertContains(shortStore, "jshort value = grt_i16(static_cast<uint32_t>(cstack[--sp].i));")
        assertContains(shortStore, "env->SetShortArrayRegion((jshortArray) array, index, 1, &value);")

        val objectStore = translate(objectArrayStoreMethod())
        assertContains(objectStore, "jobject value = cstack[--sp].l;")
        assertContains(objectStore, "env->SetObjectArrayElement((jobjectArray) array, index, value);")
    }

    @Test
    fun emitsLongOperationsWithCategoryTwoDup() {
        val materialKey = translate(materialKeyLongMethod())

        assertContains(materialKey, "env->GetStaticLongField(fieldOwner_0, fieldId_0)")
        assertContains(materialKey, "{ cstack[sp] = cstack[sp - 1]; ++sp; }")
        assertContains(materialKey, "grt_i64(static_cast<uint64_t>(lhs) >> rhs)")
        assertContains(materialKey, "cstack[sp++].i = grt_i32(static_cast<uint32_t>(value));")
    }

    @Test
    fun emitsIntShiftOperations() {
        val shifts = translate(intShiftMethod())

        assertContains(shifts, "// ISHL")
        assertContains(shifts, "grt_i32(static_cast<uint32_t>(lhs) << rhs)")
        assertContains(shifts, "// ISHR")
        assertContains(shifts, "cstack[sp++].i = grt_ishr32(lhs, rhs);")
        assertContains(shifts, "// IUSHR")
        assertContains(shifts, "grt_i32(static_cast<uint32_t>(lhs) >> rhs)")
    }

    @Test
    fun emitsLongSignedShiftThroughPortableHelper() {
        val shifts = translate(longShiftMethod())

        assertContains(shifts, "// LSHL")
        assertContains(shifts, "grt_i64(static_cast<uint64_t>(lhs) << rhs)")
        assertContains(shifts, "// LSHR")
        assertContains(shifts, "cstack[sp++].j = grt_lshr64(lhs, rhs);")
        assertContains(shifts, "// LUSHR")
        assertContains(shifts, "grt_i64(static_cast<uint64_t>(lhs) >> rhs)")
    }

    @Test
    fun emitsJavaIntWraparoundArithmeticWithoutSignedOverflow() {
        val arithmetic = translate(intWraparoundMethod())

        assertContains(arithmetic, "// IADD")
        assertContains(arithmetic, "grt_i32(static_cast<uint32_t>(lhs) + static_cast<uint32_t>(rhs))")
        assertContains(arithmetic, "// IMUL")
        assertContains(arithmetic, "grt_i32(static_cast<uint32_t>(lhs) * static_cast<uint32_t>(rhs))")
        assertContains(arithmetic, "// INEG")
        assertContains(arithmetic, "grt_i32(0u - static_cast<uint32_t>(value))")
        assertContains(arithmetic, "// IREM")
        assertContains(arithmetic, "else if (lhs == ((jint) 0x80000000) && rhs == -1) { cstack[sp++].i = 0; }")
    }

    @Test
    fun emitsIincLocalIncrement() {
        val iinc = translate(iincMethod())

        assertContains(iinc, "// IINC")
        assertContains(iinc, "clocal[0].i = grt_i32(static_cast<uint32_t>(clocal[0].i) + static_cast<uint32_t>(-3));")
    }

    @Test
    fun emitsLongCallsAndArrayOperations() {
        val longCall = translate(invokeStaticLongMethod())
        assertContains(longCall, "args_2[0].j = cstack[--sp].j;")
        assertContains(longCall, "env->CallStaticLongMethodA(ownerClass_2, methodId_2, args_2)")
        assertContains(longCall, "jlong result = cstack[--sp].j; grt_release_held_monitors(env, heldMonitors); grt_clear_refs(env, refs); grt_clear_refs(env, ownedRefs); return result;")

        val array = translate(longArrayStoreLoadMethod())
        assertContains(array, "jlong value = cstack[--sp].j;")
        assertContains(array, "env->SetLongArrayRegion((jlongArray) array, index, 1, &value);")
        assertContains(array, "env->GetLongArrayRegion((jlongArray) array, index, 1, &value);")
        assertContains(array, "cstack[sp++].j = static_cast<jlong>(value);")
    }

    @Test
    fun emitsInstanceMethodReceiverLocal() {
        val instanceMethod = translate(instanceFieldAddMethod())

        assertContains(instanceMethod, "static jint JNICALL grt_test(JNIEnv* env, jobject self, jint arg0)")
        assertContains(instanceMethod, "jclass clazz = env->GetObjectClass(self);")
        assertContains(instanceMethod, "jobject classloader = grt_get_classloader(env, clazz);")
        assertContains(instanceMethod, "grt_track_ref(env, ownedRefs, classloader);")
        assertContains(instanceMethod, "clocal[0].l = self;")
        assertContains(instanceMethod, "clocal[1].i = static_cast<jint>(arg0);")
        assertContains(instanceMethod, "jobject receiver = cstack[--sp].l;")
    }

    @Test
    fun emitsInterfaceProxyEntrySignatures() {
        val interfaceAccess = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
        val defaultMethod = NativeJvmCppMethodTranslator.translate(
            validated(interfaceDefaultMethod(), classAccess = interfaceAccess, className = "test/Face"),
            "grt_iface_default",
            NativeMethodCommitKind.InterfaceProxy
        )
        assertContains(defaultMethod, "static jint JNICALL grt_iface_default(JNIEnv* env, jclass loaderClazz, jobject proxyContext, jint arg0)")
        assertContains(defaultMethod, "(void) loaderClazz;")
        assertContains(defaultMethod, "jobject self = proxyContext;")
        assertContains(defaultMethod, "clocal[0].l = self;")
        assertContains(defaultMethod, "clocal[1].i = static_cast<jint>(arg0);")

        val staticMethod = NativeJvmCppMethodTranslator.translate(
            validated(interfaceStaticMethod(), classAccess = interfaceAccess, className = "test/Face"),
            "grt_iface_static",
            NativeMethodCommitKind.InterfaceProxy
        )
        assertContains(staticMethod, "static jint JNICALL grt_iface_static(JNIEnv* env, jclass loaderClazz, jobject proxyContext, jint arg0)")
        assertContains(staticMethod, "jclass clazz = (jclass) proxyContext;")
        assertContains(staticMethod, "clocal[0].i = static_cast<jint>(arg0);")

        val clinit = NativeJvmCppMethodTranslator.translate(
            validated(classInitializerMethod(), classAccess = interfaceAccess, className = "test/Face"),
            "grt_iface_clinit",
            NativeMethodCommitKind.InterfaceClassInitializerProxy
        )
        assertContains(clinit, "static void JNICALL grt_iface_clinit(JNIEnv* env, jclass loaderClazz, jobject proxyContext)")
        assertContains(clinit, "jclass clazz = (jclass) proxyContext;")
        assertContains(clinit, "jobject classloader = grt_get_classloader(env, clazz);")
        assertContains(clinit, "grt_track_ref(env, ownedRefs, classloader);")
    }

    @Test
    fun emitsThrowWithNativeExceptionDispatch() {
        val throwCatch = translate(throwCatchMethod())

        assertContains(throwCatch, "grt_throw(env, \"java/lang/NullPointerException\", \"ATHROW npe\");")
        assertContains(throwCatch, "env->Throw((jthrowable) exception);")
        assertContains(throwCatch, "jclass catchClass_0 = grt_find_class(env, classloader, 0, \"java/lang/Throwable\");")
        assertContains(throwCatch, "grt_track_ref(env, ownedRefs, catchClass_0);")
        assertContains(throwCatch, "if (env->IsInstanceOf(cstack[0].l, catchClass_0)) { goto L_BC_5; }")
        assertContains(throwCatch, "goto L_CATCH_0;")
        assertContains(throwCatch, "goto L_BC_5;")
    }

    @Test
    fun emitsArithmeticExceptionDispatchForProtectedTryCatchRegion() {
        val divCatch = translate(divisionCatchMethod())

        assertContains(divCatch, "grt_throw(env, \"java/lang/ArithmeticException\", \"/ by zero\");")
        assertContains(divCatch, "jthrowable exception = env->ExceptionOccurred();")
        assertContains(divCatch, "env->ExceptionClear();")
        assertContains(divCatch, "goto L_CATCH_0;")
        assertContains(divCatch, "jclass catchClass_0 = grt_find_class(env, classloader, 0, \"java/lang/ArithmeticException\");")
        assertContains(divCatch, "grt_track_ref(env, ownedRefs, catchClass_0);")
        assertContains(divCatch, "if (env->IsInstanceOf(cstack[0].l, catchClass_0)) { goto L_BC_7; }")
    }

    @Test
    fun emitsCatchAllAsDirectExceptionDispatchTarget() {
        val catchAll = translate(catchAllDivisionMethod())

        assertContains(catchAll, "goto L_CATCH_0;")
        assertContains(catchAll, "L_CATCH_0:")
        assertContains(catchAll, "goto L_BC_7;")
        assertFalse(catchAll.contains("catchClass_0"))
    }

    @Test
    fun emitsMonitorEnterExitThroughHelpersAndCleanupStack() {
        val monitor = translate(monitorMethod())

        assertContains(monitor, "std::vector<jobject> heldMonitors;")
        assertContains(monitor, "heldMonitors.reserve(1);")
        assertContains(monitor, "// MONITORENTER")
        assertContains(monitor, "grt_monitor_enter(env, lock, heldMonitors);")
        assertContains(monitor, "// MONITOREXIT")
        assertContains(monitor, "grt_monitor_exit(env, lock, heldMonitors);")
        assertContains(monitor, "grt_release_held_monitors(env, heldMonitors); grt_clear_refs(env, refs); grt_clear_refs(env, ownedRefs); return;")
    }

    @Test
    fun emitsMonitorCleanupForExceptionalSynchronizedShape() {
        val monitor = translate(monitorThrowMethod())

        assertContains(monitor, "heldMonitors.reserve(1);")
        assertContains(monitor, "grt_monitor_enter(env, lock, heldMonitors);")
        assertContains(monitor, "goto L_CATCH_0;")
        assertContains(monitor, "grt_monitor_exit(env, lock, heldMonitors);")
        assertContains(monitor, "grt_release_held_monitors(env, heldMonitors); grt_clear_refs(env, refs); grt_clear_refs(env, ownedRefs); return;")
    }

    @Test
    fun emitsNestedMonitorTrackingCapacity() {
        val nested = translate(nestedMonitorMethod())

        assertContains(nested, "heldMonitors.reserve(2);")
        assertContains(nested, "grt_monitor_enter(env, lock, heldMonitors);")
        assertContains(nested, "grt_monitor_exit(env, lock, heldMonitors);")
        assertTrue(nested.split("grt_monitor_enter(env, lock, heldMonitors);").size >= 3)
        assertTrue(nested.split("grt_monitor_exit(env, lock, heldMonitors);").size >= 3)
    }

    @Test
    fun emitsTypedDupStackOperations() {
        val dupX2 = translate(dupX2LongMethod())
        assertContains(dupX2, "// DUP_X2")
        assertContains(dupX2, "cstack[sp - 2] = value1; cstack[sp - 1] = value2; cstack[sp++] = value1;")

        val dup2X1 = translate(dup2X1LongMethod())
        assertContains(dup2X1, "// DUP2_X1")
        assertContains(dup2X1, "cstack[sp - 2] = value1; cstack[sp - 1] = value2; cstack[sp++] = value1;")

        val dup2X2 = translate(dup2X2LongMethod())
        assertContains(dup2X2, "// DUP2_X2")
        assertContains(dup2X2, "cstack[sp - 2] = value1; cstack[sp - 1] = value2; cstack[sp++] = value1;")
    }

    @Test
    fun emitsFloatAndDoubleOperations() {
        val floatArithmetic = translate(floatArithmeticMethod())
        assertContains(floatArithmetic, "static jfloat JNICALL grt_test(JNIEnv* env, jclass clazz, jfloat arg0, jfloat arg1)")
        assertContains(floatArithmetic, "clocal[0].f = arg0;")
        assertContains(floatArithmetic, "std::fmod(lhs, rhs)")
        assertContains(floatArithmetic, "cstack[sp++].f = static_cast<jfloat>(0x1.4p1f);")
        assertContains(floatArithmetic, "jfloat result = cstack[--sp].f; grt_release_held_monitors(env, heldMonitors); grt_clear_refs(env, refs); grt_clear_refs(env, ownedRefs); return result;")

        val doubleCompare = translate(doubleCompareMethod())
        assertContains(doubleCompare, "std::isnan(lhs) || std::isnan(rhs) ? 1")

        val doubleToInt = translate(doubleToIntMethod())
        assertContains(doubleToInt, "cstack[sp++].i = grt_d2i(value);")

        val doubleCall = translate(invokeStaticDoubleMethod())
        assertContains(doubleCall, "args_1[0].d = cstack[--sp].d;")
        assertContains(doubleCall, "env->CallStaticDoubleMethodA(ownerClass_1, methodId_1, args_1)")
        assertContains(doubleCall, "jdouble result = cstack[--sp].d; grt_release_held_monitors(env, heldMonitors); grt_clear_refs(env, refs); grt_clear_refs(env, ownedRefs); return result;")

        val doubleArray = translate(doubleArrayStoreLoadMethod())
        assertContains(doubleArray, "jdouble value = cstack[--sp].d;")
        assertContains(doubleArray, "env->SetDoubleArrayRegion((jdoubleArray) array, index, 1, &value);")
        assertContains(doubleArray, "env->GetDoubleArrayRegion((jdoubleArray) array, index, 1, &value);")

        val newFloatArray = translate(newFloatArrayMethod())
        assertContains(newFloatArray, "cstack[sp++].l = env->NewFloatArray(count);")
    }

    @Test
    fun emitsMultiANewArrayViaReflectArray() {
        val multiIntArray = translate(multiIntArrayMethod())
        assertContains(multiIntArray, "jint dimensions_2[2] = {};")
        assertContains(multiIntArray, "dimensions_2[1] = cstack[--sp].i;")
        assertContains(multiIntArray, "grt_find_class(env, classloader, 0, \"java/lang/Integer\")")
        assertContains(multiIntArray, "grt_find_class(env, classloader, 1, \"java/lang/reflect/Array\")")
        assertContains(multiIntArray, "grt_track_ref(env, refs, reflectArrayClass_2);")
        assertContains(multiIntArray, "grt_get_method_id(env, reflectArrayClass_2, 0, \"newInstance\", \"(Ljava/lang/Class;[I)Ljava/lang/Object;\", true)")
        assertContains(multiIntArray, "cstack[sp++].l = env->CallStaticObjectMethodA(reflectArrayClass_2, newInstance_2, args_2);")

        val partialObjectArray = translate(partialMultiObjectArrayMethod())
        assertContains(partialObjectArray, "grt_find_class(env, classloader, 0, \"[Ljava/lang/String;\")")
    }

    @Test
    fun emitsMethodTypeAndMethodHandleLdcConstants() {
        val methodType = translate(methodTypeLdcMethod())
        assertContains(methodType, "grt_find_class(env, classloader, 0, \"java/lang/invoke/MethodType\")")
        assertContains(methodType, "grt_track_ref(env, refs, methodTypeClass_constant_0);")
        assertContains(methodType, "grt_get_method_id(env, methodTypeClass_constant_0, 0, \"fromMethodDescriptorString\", \"(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;\", true)")
        assertContains(methodType, "descriptor_constant_0 = env->NewStringUTF(\"(I)Ljava/lang/String;\");")

        val methodHandle = translate(methodHandleLdcMethod())
        assertContains(methodHandle, "jclass currentClass = clazz;")
        assertContains(methodHandle, "jobject lookup = nullptr;")
        assertContains(methodHandle, "if (lookup == nullptr) { lookup = grt_get_lookup(env, currentClass); grt_track_ref(env, ownedRefs, lookup); }")
        assertContains(methodHandle, "ownerClass_handle_0 = grt_find_class(env, classloader, 0, \"java/lang/Integer\");")
        assertContains(methodHandle, "grt_track_ref(env, refs, ownerClass_handle_0);")
        assertContains(methodHandle, "grt_get_method_id(env, grt_methodhandles_lookup_class, 1, \"findStatic\", \"(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;\", false)")
        assertContains(methodHandle, "methodTypeArgs_handle_0_type[0].l = descriptor_handle_0_type;")
        assertContains(methodHandle, "methodHandle_0 = env->CallObjectMethodA(lookup, findMethod_handle_0, findArgs_handle_0);")
    }

    @Test
    fun emitsInternedStringLdcConstants() {
        val stringLiteral = translate(stringLiteralMethod())

        assertContains(stringLiteral, "cstack[sp++].l = grt_ldc_string(env, \"same\");")
        assertFalse(stringLiteral.contains("cstack[sp++].l = env->NewStringUTF(\"same\");"))
    }

    @Test
    fun emitsModifiedUtf8StringLdcConstants() {
        val stringLiteral = translate(modifiedUtf8StringLiteralMethod())

        assertContains(stringLiteral, "cstack[sp++].l = grt_ldc_string(env, \"\\300\\200A\\303\\251\");")
    }

    @Test
    fun derivesNativeStackCapacityWhenMethodMaxStackIsStale() {
        val translated = translate(staleMaxStackCharArrayInitMethod())

        assertFalse(translated.contains("jvalue cstack[1]"))
        assertContains(translated, "env->NewCharArray(count);")
        assertContains(translated, "env->SetCharArrayRegion((jcharArray) array, index, 1, &value);")
    }

    @Test
    fun backendRegistersClassInitializerProxyMethod() {
        val source = NativeCppBackend.generate(
            methods = listOf(validated(classInitializerMethod())),
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "const_cast<char*>(\"grt_native_clinit_0_0\")")
        assertContains(source, "const_cast<char*>(\"()V\")")
        assertFalse(source.contains("const_cast<char*>(\"<clinit>\")"))
    }

    @Test
    fun backendRegistersInterfaceProxyMethodsOnLoader() {
        val interfaceAccess = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
        val source = NativeCppBackend.generate(
            methods = listOf(validated(interfaceStaticMethod(), classAccess = interfaceAccess, className = "test/Face")),
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "const_cast<char*>(\"grt_native_interface_0_0\")")
        assertContains(source, "const_cast<char*>(\"(Ljava/lang/Object;I)I\")")
        assertContains(source, "env->RegisterNatives(loader, loaderMethods, static_cast<jint>(sizeof(loaderMethods) / sizeof(loaderMethods[0])));")
        assertFalse(source.contains("env->RegisterNatives(clazz, methods"))
    }

    @Test
    fun backendRegistersInterfaceClassInitializerProxyOnLoader() {
        val interfaceAccess = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
        val source = NativeCppBackend.generate(
            methods = listOf(validated(classInitializerMethod(), classAccess = interfaceAccess, className = "test/Face")),
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "const_cast<char*>(\"grt_native_clinit_0_0\")")
        assertContains(source, "const_cast<char*>(\"(Ljava/lang/Class;)V\")")
        assertContains(source, "env->RegisterNatives(loader, loaderMethods, static_cast<jint>(sizeof(loaderMethods) / sizeof(loaderMethods[0])));")
        assertFalse(source.contains("env->RegisterNatives(clazz, methods"))
    }

    @Test
    fun backendSplitsLargeNativeSourcesIntoRegisterAndChunkFiles() {
        val bundle = NativeCppBackend.generate(
            methods = listOf(
                validated(constantIntMethod("a"), className = "test/A"),
                validated(constantIntMethod("b"), className = "test/B"),
                validated(constantIntMethod("c"), className = "test/C")
            ),
            config = NativePipelineConfig(
                enabled = true,
                splitSourceFiles = true,
                maxMethodsPerSourceFile = 1
            ),
            classExists = { false }
        )

        val names = bundle.sourceFiles.map { it.path.fileName.toString() }
        assertTrue("grunteon_native_runtime.hpp" in names)
        assertTrue("grunteon_native_runtime.cpp" in names)
        assertTrue("grunteon_native_register.cpp" in names)
        assertTrue(names.count { it.startsWith("grunteon_native_chunk_") } >= 3)

        val headerSource = bundle.sourceFiles.single { it.path.fileName.toString() == "grunteon_native_runtime.hpp" }.text
        val runtimeSource = bundle.sourceFiles.single { it.path.fileName.toString() == "grunteon_native_runtime.cpp" }.text
        val registerSource = bundle.sourceFiles.single { it.path.fileName.toString() == "grunteon_native_register.cpp" }.text
        val chunkSource = bundle.sourceFiles.first { it.path.fileName.toString().startsWith("grunteon_native_chunk_") }.text

        assertContains(headerSource, "extern jclass grt_methodhandles_lookup_class;")
        assertContains(headerSource, "bool grt_init_runtime(JNIEnv* env);")
        assertContains(headerSource, "uint32_t grt_rotl32(uint32_t value, uint32_t distance);")
        assertContains(runtimeSource, "jclass grt_methodhandles_lookup_class = nullptr;")
        assertContains(runtimeSource, "bool grt_init_runtime(JNIEnv* env)")
        assertFalse(runtimeSource.contains("static jclass grt_methodhandles_lookup_class = nullptr;"))
        assertContains(registerSource, "#include \"grunteon_native_runtime.hpp\"")
        assertContains(registerSource, "extern void grt_register_class_0(JNIEnv* env, jclass clazz);")
        assertContains(registerSource, "grt_register_loader_proxies_chunk_0(env, loader);")
        assertFalse(registerSource.contains("grt_class_slots["))
        assertContains(chunkSource, "#include \"grunteon_native_runtime.hpp\"")
        assertContains(chunkSource, "void grt_register_class_")
        assertContains(chunkSource, "if (!grt_init_runtime(env)) return;")
        assertFalse(chunkSource.contains("using GrtLocalRefs = std::vector<jobject>;"))
        assertFalse(chunkSource.contains("grt_class_slots["))
    }

    private fun translate(method: MethodNode): String {
        return NativeJvmCppMethodTranslator.translate(validated(method), "grt_test")
    }

    private fun validated(
        method: MethodNode,
        classAccess: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
        className: String = "test/Example"
    ): NativeValidatedMethod {
        val ir = NativeJvmIrImporter.import(className, method)
        val support = NativeJvmSupportAnalyzer.analyze(ir)
        return NativeValidatedMethod(
            candidate = NativeCandidate(
                classNode = org.objectweb.asm.tree.ClassNode().apply {
                    version = Opcodes.V1_8
                    access = classAccess
                    name = className
                    superName = "java/lang/Object"
                    methods.add(method)
                },
                methodNode = method,
                source = NativeCandidateSource.MethodAnnotation
            ),
            jvmIr = ir,
            fullJvmSupport = support,
            lowering = NativeLoweringKind.FullJvm
        )
    }

    private fun branchMethod(): MethodNode {
        val nonNegative = LabelNode()
        val end = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "branch", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(JumpInsnNode(Opcodes.IFGE, nonNegative))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(JumpInsnNode(Opcodes.GOTO, end))
            instructions.add(nonNegative)
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(end)
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun tableSwitchMethod(): MethodNode {
        val one = LabelNode()
        val two = LabelNode()
        val default = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "tableSwitch", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(TableSwitchInsnNode(1, 2, default, one, two))
            instructions.add(one)
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(two)
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(default)
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun lookupSwitchMethod(): MethodNode {
        val seven = LabelNode()
        val nine = LabelNode()
        val default = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "lookupSwitch", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(LookupSwitchInsnNode(default, intArrayOf(7, 9), arrayOf(seven, nine)))
            instructions.add(seven)
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(nine)
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(default)
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun invokeStaticIntMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "invokeStaticInt", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "bitCount", "(I)I", false))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun invokeStaticObjectMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "invokeStaticObject",
            "(I)Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun repeatedObjectReturningCallsMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "repeatedObjectReturningCalls",
            "(III)Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 3
        }
    }

    private fun getStaticFieldMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "getStaticField", "()I", null, null).apply {
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "test/Fields", "VALUE", "I"))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun putFieldMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "putField", "(Ltest/Fields;I)V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(FieldInsnNode(Opcodes.PUTFIELD, "test/Fields", "value", "I"))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun newObjectMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "newObject",
            "()Ljava/lang/StringBuilder;",
            null,
            null
        ).apply {
            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 2
            maxLocals = 0
        }
    }

    private fun checkcastMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "checkcast",
            "(Ljava/lang/Object;)Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun instanceOfMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "instanceOf", "(Ljava/lang/Object;)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(TypeInsnNode(Opcodes.INSTANCEOF, "java/lang/String"))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun newIntArrayMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "newIntArray", "(I)[I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun newObjectArrayMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "newObjectArray", "(I)[Ljava/lang/String;", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun arrayLengthMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "arrayLength", "([I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ARRAYLENGTH))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun objectClassLiteralMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "objectClassLiteral", "()Ljava/lang/Class;", null, null).apply {
            instructions.add(LdcInsnNode(Type.getObjectType("java/lang/String")))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun primitiveClassLiteralMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "primitiveClassLiteral", "()Ljava/lang/Class;", null, null).apply {
            instructions.add(LdcInsnNode(Type.INT_TYPE))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun intArrayLoadMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "intArrayLoad", "([II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IALOAD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun byteArrayLoadMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "byteArrayLoad", "([BI)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.BALOAD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun charArrayStoreMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "charArrayStore", "([CII)V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.CASTORE))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 3
            maxLocals = 3
        }
    }

    private fun byteArrayStoreMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "byteArrayStore", "([BII)V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.BASTORE))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 3
            maxLocals = 3
        }
    }

    private fun shortArrayStoreMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "shortArrayStore", "([SII)V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.SASTORE))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 3
            maxLocals = 3
        }
    }

    private fun objectArrayStoreMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "objectArrayStore",
            "([Ljava/lang/String;ILjava/lang/String;)V",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 2))
            instructions.add(InsnNode(Opcodes.AASTORE))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 3
            maxLocals = 3
        }
    }

    private fun materialKeyLongMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "materialKeyLong", "()I", null, null).apply {
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "test/RuntimeMaterial", "KEY", "J"))
            instructions.add(InsnNode(Opcodes.DUP2))
            instructions.add(IntInsnNode(Opcodes.BIPUSH, 32))
            instructions.add(InsnNode(Opcodes.LUSHR))
            instructions.add(InsnNode(Opcodes.LXOR))
            instructions.add(InsnNode(Opcodes.L2I))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 6
            maxLocals = 0
        }
    }

    private fun intShiftMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "intShift", "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.ISHL))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.ISHR))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IUSHR))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 4
            maxLocals = 2
        }
    }

    private fun longShiftMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "longShift", "(JI)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.LSHL))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.LSHR))
            instructions.add(InsnNode(Opcodes.LADD))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.LUSHR))
            instructions.add(InsnNode(Opcodes.LADD))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 8
            maxLocals = 3
        }
    }

    private fun intWraparoundMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "intWraparound", "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IMUL))
            instructions.add(InsnNode(Opcodes.INEG))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IREM))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun iincMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "iinc", "(I)I", null, null).apply {
            instructions.add(IincInsnNode(0, -3))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun constantIntMethod(name: String): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, "()I", null, null).apply {
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun invokeStaticLongMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "invokeStaticLong", "(JI)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "rotateLeft", "(JI)J", false))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 3
            maxLocals = 3
        }
    }

    private fun longArrayStoreLoadMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "longArrayStoreLoad", "([JIJ)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 2))
            instructions.add(InsnNode(Opcodes.LASTORE))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.LALOAD))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 4
            maxLocals = 4
        }
    }

    private fun instanceFieldAddMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC, "instanceFieldAdd", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(FieldInsnNode(Opcodes.GETFIELD, "test/Example", "value", "I"))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun interfaceDefaultMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC, "defaultAdd", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun interfaceStaticMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "staticAdd", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 1
        }
    }

    private fun throwCatchMethod(): MethodNode {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "throwCatch", "(Ljava/lang/Throwable;)I", null, null).apply {
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ATHROW))
            instructions.add(end)
            instructions.add(handler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, "java/lang/Throwable"))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun divisionCatchMethod(): MethodNode {
        return divisionCatchMethod("divisionCatch", "java/lang/ArithmeticException")
    }

    private fun catchAllDivisionMethod(): MethodNode {
        return divisionCatchMethod("catchAllDivision", null)
    }

    private fun divisionCatchMethod(name: String, caughtType: String?): MethodNode {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, "(II)I", null, null).apply {
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IDIV))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(end)
            instructions.add(handler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, caughtType))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun monitorMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "monitor", "(Ljava/lang/Object;)V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITORENTER))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITOREXIT))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun monitorThrowMethod(): MethodNode {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "monitorThrow",
            "(Ljava/lang/Object;Ljava/lang/Throwable;)V",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITORENTER))
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.ATHROW))
            instructions.add(end)
            instructions.add(handler)
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITOREXIT))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.ATHROW))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, null))
            maxStack = 1
            maxLocals = 2
        }
    }

    private fun nestedMonitorMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "nestedMonitor",
            "(Ljava/lang/Object;Ljava/lang/Object;)V",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITORENTER))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.MONITORENTER))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(InsnNode(Opcodes.MONITOREXIT))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITOREXIT))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 2
        }
    }

    private fun dupX2LongMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "dupX2Long", "(JI)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.DUP_X2))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.L2I))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 4
            maxLocals = 3
        }
    }

    private fun dup2X1LongMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "dup2X1Long", "(IJ)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 1))
            instructions.add(InsnNode(Opcodes.DUP2_X1))
            instructions.add(VarInsnNode(Opcodes.LSTORE, 3))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 5))
            instructions.add(InsnNode(Opcodes.POP2))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 5))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 5
            maxLocals = 6
        }
    }

    private fun dup2X2LongMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "dup2X2Long", "(JJ)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 2))
            instructions.add(InsnNode(Opcodes.DUP2_X2))
            instructions.add(VarInsnNode(Opcodes.LSTORE, 4))
            instructions.add(VarInsnNode(Opcodes.LSTORE, 6))
            instructions.add(VarInsnNode(Opcodes.LSTORE, 8))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 8))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 6
            maxLocals = 10
        }
    }

    private fun floatArithmeticMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "floatArithmetic", "(FF)F", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.FLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.FLOAD, 1))
            instructions.add(InsnNode(Opcodes.FREM))
            instructions.add(LdcInsnNode(2.5f))
            instructions.add(InsnNode(Opcodes.FADD))
            instructions.add(InsnNode(Opcodes.FRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun doubleCompareMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "doubleCompare", "(DD)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.DLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.DLOAD, 2))
            instructions.add(InsnNode(Opcodes.DCMPG))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 4
            maxLocals = 4
        }
    }

    private fun doubleToIntMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "doubleToInt", "(D)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.DLOAD, 0))
            instructions.add(InsnNode(Opcodes.D2I))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun invokeStaticDoubleMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "invokeStaticDouble", "(D)D", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.DLOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(D)D", false))
            instructions.add(InsnNode(Opcodes.DRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun doubleArrayStoreLoadMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "doubleArrayStoreLoad", "([DID)D", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.DLOAD, 2))
            instructions.add(InsnNode(Opcodes.DASTORE))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.DALOAD))
            instructions.add(InsnNode(Opcodes.DRETURN))
            maxStack = 4
            maxLocals = 4
        }
    }

    private fun newFloatArrayMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "newFloatArray", "(I)[F", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_FLOAT))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun multiIntArrayMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "multiIntArray", "(II)[[I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(MultiANewArrayInsnNode("[[I", 2))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun partialMultiObjectArrayMethod(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "partialMultiObjectArray",
            "(II)[[[Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(MultiANewArrayInsnNode("[[[Ljava/lang/String;", 2))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun methodTypeLdcMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "methodTypeLdc", "()V", null, null).apply {
            instructions.add(LdcInsnNode(Type.getMethodType("(I)Ljava/lang/String;")))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun stringLiteralMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "stringLiteral", "()Ljava/lang/String;", null, null).apply {
            instructions.add(LdcInsnNode("same"))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun modifiedUtf8StringLiteralMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "modifiedUtf8StringLiteral", "()V", null, null).apply {
            instructions.add(LdcInsnNode("\u0000A\u00e9"))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun methodHandleLdcMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "methodHandleLdc", "()V", null, null).apply {
            instructions.add(
                LdcInsnNode(
                    Handle(
                        Opcodes.H_INVOKESTATIC,
                        "java/lang/Integer",
                        "bitCount",
                        "(I)I",
                        false
                    )
                )
            )
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun staleMaxStackCharArrayInitMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "staleMaxStackCharArrayInit", "()V", null, null).apply {
            instructions.add(IntInsnNode(Opcodes.BIPUSH, 2))
            instructions.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_CHAR))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(IntInsnNode(Opcodes.SIPUSH, 1234))
            instructions.add(InsnNode(Opcodes.CASTORE))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun classInitializerMethod(): MethodNode {
        return MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).apply {
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 0
            maxLocals = 0
        }
    }
}
