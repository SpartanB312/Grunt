package net.spartanb312.grunteon.obfuscator.util;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

@SuppressWarnings({"unused", "SameParameterValue", "DuplicatedCode", "SpellCheckingInspection"})
public class ImplLookupGetter {
    private static final long PTR_SIZE = ValueLayout.ADDRESS.byteSize();
    private static final String PROPERTY_NAME = "IMPL_LOOKUP_FROM_JNI";

    private static final int JNI_OK = 0;
    private static final int JNI_ERR = -1;
    private static final int JNI_EDETACHED = -2;
    private static final int JNI_EVERSION = -3;
    private static final int JNI_ENOMEM = -4;
    private static final int JNI_EEXIST = -5;
    private static final int JNI_EINVAL = -6;

    public static MethodHandles.Lookup getLookup() {
        try (Arena arena = Arena.ofConfined()) {
            final SymbolLookup lookup = jvmLookup(arena);
            final MemorySegment vm = getJavaVm(arena, lookup);
            final MemorySegment env = getJniEnv(arena, vm);
            final MemorySegment lookupClass = getJniClass(arena, env, MethodHandles.Lookup.class);
            final MemorySegment implLookupField = getStaticFieldId(arena, env, lookupClass, "IMPL_LOOKUP", MethodHandles.Lookup.class.descriptorString());
            final MemorySegment implLookupRef = getStaticObjectField(arena, env, lookupClass, implLookupField);

            final MemorySegment systemClass = getJniClass(arena, env, System.class);
            final MemorySegment getPropertiesMethod = getStaticMethodId(arena, env, systemClass, "getProperties", "()Ljava/util/Properties;");
            final MemorySegment propertiesRef = callStaticObjectMethod(arena, env, systemClass, getPropertiesMethod);
            final MemorySegment propertiesClass = getJniClass(arena, env, Properties.class);
            final MemorySegment putMethod = getMethodId(arena, env, propertiesClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            final MemorySegment prevValue = callObjectMethod(arena, env, propertiesRef, putMethod, newStringUTF(arena, env, PROPERTY_NAME), implLookupRef);

            return (MethodHandles.Lookup) System.getProperties().get(PROPERTY_NAME);
        } catch (Throwable t) {
            switch (t) {
                case RuntimeException re -> throw re;
                case Error e -> throw e;
                default -> throw new RuntimeException(t);
            }
        }
    }

    private static SymbolLookup jvmLookup(Arena arena) {
        // FFM libraryLookup(String) does not map "jvm" to libjvm.so on Unix.
        final String library = System.mapLibraryName("jvm");
        final Path home = Path.of(System.getProperty("java.home"));
        for (Path candidate : new Path[]{home.resolve("lib/server").resolve(library),
                home.resolve("bin/server").resolve(library)}) {
            if (Files.isRegularFile(candidate)) return SymbolLookup.libraryLookup(candidate, arena);
        }
        return SymbolLookup.libraryLookup(library, arena);
    }

    private static MemorySegment getJavaVm(Arena arena, SymbolLookup lookup) throws Throwable {
        final MethodHandle getCreatedJavaVms = Linker.nativeLinker().downcallHandle(
            lookup.find("JNI_GetCreatedJavaVMs").orElseThrow(), FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
            )
        );
        final MemorySegment vmCount = arena.allocate(ValueLayout.JAVA_INT);
        final MemorySegment vmRef = arena.allocate(ValueLayout.ADDRESS);
        checkError((int) getCreatedJavaVms.invokeExact(vmRef, 1, vmCount));
        if (vmCount.get(ValueLayout.JAVA_INT, 0) < 1) {
            throw new IllegalStateException("No JavaVM available");
        }
        return vmRef.get(ValueLayout.ADDRESS, 0);
    }

    private static MemorySegment getJniEnv(Arena arena, MemorySegment vm) throws Throwable {
        final MethodHandle getEnv = Linker.nativeLinker().downcallHandle(
            getFunction(vm, 6), FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
            )
        );
        final MemorySegment envRef = arena.allocate(ValueLayout.ADDRESS);
        checkError((int) getEnv.invokeExact(vm, envRef, getJniVersion(21, 0)));
        return envRef.get(ValueLayout.ADDRESS, 0);
    }

    private static MemorySegment getJniClass(Arena arena, MemorySegment env, Class<?> clazz) throws Throwable {
        final MethodHandle findClass = Linker.nativeLinker().downcallHandle(
            getFunction(env, 6), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
        final MethodHandle newGlobalRef = Linker.nativeLinker().downcallHandle(
            getFunction(env, 21), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );

        MethodHandle chain = MethodHandles.filterReturnValue(findClass, newGlobalRef.bindTo(env));
        return (MemorySegment) chain.invokeExact(env, arena.allocateFrom(clazz.getName().replace('.', '/')));
    }

    private static MemorySegment getStaticFieldId(
        Arena arena,
        MemorySegment env,
        MemorySegment clazz,
        String name,
        String sig
    ) throws Throwable {
        final MethodHandle getStaticFieldId = Linker.nativeLinker().downcallHandle(
            getFunction(env, 144), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
        return (MemorySegment) getStaticFieldId.invokeExact(env, clazz, arena.allocateFrom(name), arena.allocateFrom(sig));
    }

    private static MemorySegment getStaticObjectField(
        Arena arena,
        MemorySegment env,
        MemorySegment clazz,
        MemorySegment fieldId
    ) throws Throwable {
        final MethodHandle getStaticObjectField = Linker.nativeLinker().downcallHandle(
            getFunction(env, 145), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
        final MethodHandle newGlobalRef = Linker.nativeLinker().downcallHandle(
            getFunction(env, 21), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );

        MethodHandle chain = MethodHandles.filterReturnValue(getStaticObjectField, newGlobalRef.bindTo(env));
        return (MemorySegment) chain.invokeExact(env, clazz, fieldId);
    }

    private static MemorySegment setStaticObjectField(
        Arena arena,
        MemorySegment env,
        MemorySegment clazz,
        MemorySegment fieldId,
        MemorySegment obj
    ) throws Throwable {
        final MethodHandle setStaticObjectField = Linker.nativeLinker().downcallHandle(
            getFunction(env, 154), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
        return (MemorySegment) setStaticObjectField.invokeExact(env, clazz, fieldId, obj);
    }

    private static MemorySegment getStaticMethodId(
        Arena arena,
        MemorySegment env,
        MemorySegment clazz,
        String name,
        String sig
    ) throws Throwable {
        final MethodHandle getStaticMethodId = Linker.nativeLinker().downcallHandle(
            getFunction(env, 113), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
        return (MemorySegment) getStaticMethodId.invokeExact(env, clazz, arena.allocateFrom(name), arena.allocateFrom(sig));
    }

    private static MemorySegment callStaticObjectMethod(
        Arena arena,
        MemorySegment env,
        MemorySegment clazz,
        MemorySegment methodId,
        Object... args
    ) throws Throwable {
        ValueLayout[] argsLayout = new ValueLayout[args.length + 3/*env, clazz, methodId*/];
        Arrays.fill(argsLayout, ValueLayout.ADDRESS);
        final MethodHandle callStaticObjectMethod = Linker.nativeLinker().downcallHandle(
            getFunction(env, 114), FunctionDescriptor.of(
                ValueLayout.ADDRESS, argsLayout
            )
        );
        final MethodHandle newGlobalRef = Linker.nativeLinker().downcallHandle(
            getFunction(env, 21), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );

        MethodHandle chain = MethodHandles.filterReturnValue(callStaticObjectMethod, newGlobalRef.bindTo(env));
        return (MemorySegment) chain.asSpreader(Object[].class, args.length).invoke(env, clazz, methodId, args);
    }

    private static MemorySegment getMethodId(
        Arena arena,
        MemorySegment env,
        MemorySegment clazz,
        String name,
        String sig
    ) throws Throwable {
        final MethodHandle getMethodId = Linker.nativeLinker().downcallHandle(
            getFunction(env, 33), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
        return (MemorySegment) getMethodId.invokeExact(env, clazz, arena.allocateFrom(name), arena.allocateFrom(sig));
    }

    private static MemorySegment callObjectMethod(
        Arena arena,
        MemorySegment env,
        MemorySegment object,
        MemorySegment methodId,
        Object... args
    ) throws Throwable {
        ValueLayout[] argsLayout = new ValueLayout[args.length + 3/*env, object, methodId*/];
        Arrays.fill(argsLayout, ValueLayout.ADDRESS);
        final MethodHandle callObjectMethod = Linker.nativeLinker().downcallHandle(
            getFunction(env, 34), FunctionDescriptor.of(
                ValueLayout.ADDRESS, argsLayout
            )
        );
        final MethodHandle newGlobalRef = Linker.nativeLinker().downcallHandle(
            getFunction(env, 21), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );

        MethodHandle chain = MethodHandles.filterReturnValue(callObjectMethod, newGlobalRef.bindTo(env));
        return (MemorySegment) chain.asSpreader(Object[].class, args.length).invoke(env, object, methodId, args);
    }

    private static MemorySegment allocObject(Arena arena, MemorySegment env, MemorySegment clazz) throws Throwable {
        final MethodHandle allocObject = Linker.nativeLinker().downcallHandle(
            getFunction(env, 27), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
        return (MemorySegment) allocObject.invoke(env, clazz);
    }

    private static MemorySegment newStringUTF(Arena arena, MemorySegment env, String s) throws Throwable {
        final MethodHandle newStringUTF = Linker.nativeLinker().downcallHandle(
            getFunction(env, 167), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
        final MethodHandle newGlobalRef = Linker.nativeLinker().downcallHandle(
            getFunction(env, 21), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );

        MethodHandle chain = MethodHandles.filterReturnValue(newStringUTF, newGlobalRef.bindTo(env));
        return (MemorySegment) chain.invoke(env, arena.allocateFrom(s));
    }

    private static MemorySegment getFunction(MemorySegment obj, int function) {
        return obj.reinterpret(PTR_SIZE)
            .get(ValueLayout.ADDRESS, 0)
            .reinterpret((function + PTR_SIZE) * PTR_SIZE)
            .getAtIndex(ValueLayout.ADDRESS, function);
    }

    private static int throw_(Arena arena, MemorySegment env, MemorySegment throwable) throws Throwable {
        final MethodHandle throw_ = Linker.nativeLinker().downcallHandle(
            getFunction(env, 13), FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
            )
        );
        return (int) throw_.invokeExact(env, throwable);
    }

    private static int getJniVersion(int major, int minor) {
        return (major << 16) | minor;
    }

    private static void checkError(int err) {
        if (err != JNI_OK) {
            switch (err) {
                case JNI_ERR -> throw new RuntimeException("Unknown JNI error");
                case JNI_EDETACHED -> throw new IllegalStateException("Thread detached");
                case JNI_EVERSION -> throw new IllegalArgumentException("Unknown JNI version");
                case JNI_ENOMEM -> throw new OutOfMemoryError("JNI out of memory");
                case JNI_EEXIST -> throw new IllegalStateException("VM already created");
                case JNI_EINVAL -> throw new IllegalArgumentException("Invalid arguments");
                default -> throw new RuntimeException("Unknown JNI error code " + err);
            }
        }
    }

}