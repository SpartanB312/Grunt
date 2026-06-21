package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.genesis.kotlin.InsnListBuilder
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.method
import org.objectweb.asm.Label
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

internal object NativeLoaderClassFactory {

    data class NativeLoaderLibrary(
        val resourceDirectory: String,
        val resourcePath: String,
        val librarySuffix: String
    )

    fun create(
        loaderInternalName: String,
        resourcePath: String,
        librarySuffix: String,
        proxyMethods: List<Pair<String, String>> = emptyList()
    ): ClassNode {
        val normalizedResourcePath = resourcePath.removePrefix("/")
        val resourceDirectory = normalizedResourcePath
            .substringAfter("grunteon/native/", "")
            .substringBefore("/", NativePlatform.current().resourceDirectory)
        return create(
            loaderInternalName = loaderInternalName,
            libraries = listOf(
                NativeLoaderLibrary(
                    resourceDirectory = resourceDirectory,
                    resourcePath = "/$normalizedResourcePath",
                    librarySuffix = librarySuffix
                )
            ),
            proxyMethods = proxyMethods
        )
    }

    fun create(
        loaderInternalName: String,
        libraries: List<NativeLoaderLibrary>,
        proxyMethods: List<Pair<String, String>> = emptyList()
    ): ClassNode {
        return clazz(
            access = PUBLIC + FINAL + SUPER,
            name = loaderInternalName,
            version = Java6
        ) {
            +field(PRIVATE + STATIC + VOLATILE, "loaded", "Z")
            +createConstructor()
            +createRegisterNative()
            proxyMethods.forEach { (name, desc) ->
                +createProxyNative(name, desc)
            }
            +createNormalizeOsMethod()
            +createNormalizeArchMethod()
            +createSelectNativeLibraryResourceMethod(loaderInternalName, libraries)
            +createSelectNativeLibrarySuffixMethod(loaderInternalName, libraries)
            +createLoadMethod(loaderInternalName)
        }
    }

    private fun createConstructor(): MethodNode {
        return method(PRIVATE, "<init>", "()V") {
            INSTRUCTIONS {
                ALOAD(0)
                INVOKESPECIAL("java/lang/Object", "<init>", "()V")
                RETURN
            }
            MAXS(1, 1)
        }
    }

    private fun createRegisterNative(): MethodNode {
        return method(
            PUBLIC + STATIC + NATIVE,
            "registerNativesForClass",
            "(ILjava/lang/Class;)V"
        )
    }

    private fun createProxyNative(name: String, desc: String): MethodNode {
        return method(
            PUBLIC + STATIC + NATIVE + SYNTHETIC + BRIDGE,
            name,
            desc
        ) {
            +AnnotationNode("Ljava/lang/invoke/LambdaForm\$Hidden;")
            +AnnotationNode("Ljdk/internal/vm/annotation/Hidden;")
        }
    }

    private fun createNormalizeOsMethod(): MethodNode {
        val notNull = Label()
        val mac = Label()
        val darwin = Label()
        val linux = Label()
        val nix = Label()
        val nux = Label()
        val aix = Label()
        val unknown = Label()
        return method(PRIVATE + STATIC, "normalizeOs", "(Ljava/lang/String;)Ljava/lang/String;") {
            INSTRUCTIONS {
                ALOAD(0)
                IFNONNULL(notNull)
                LDC("")
                ASTORE(0)
                LABEL(notNull)
                ALOAD(0)
                INVOKEVIRTUAL("java/lang/String", "toLowerCase", "()Ljava/lang/String;")
                ASTORE(1)
                addContainsBranch("win", mac)
                LDC("windows")
                ARETURN
                LABEL(mac)
                addContainsBranch("mac", darwin)
                LDC("macos")
                ARETURN
                LABEL(darwin)
                addContainsBranch("darwin", linux)
                LDC("macos")
                ARETURN
                LABEL(linux)
                addContainsBranch("linux", nix)
                LDC("linux")
                ARETURN
                LABEL(nix)
                addContainsBranch("nix", nux)
                LDC("linux")
                ARETURN
                LABEL(nux)
                addContainsBranch("nux", aix)
                LDC("linux")
                ARETURN
                LABEL(aix)
                addContainsBranch("aix", unknown)
                LDC("linux")
                ARETURN
                LABEL(unknown)
                LDC("unknown")
                ARETURN
            }
            MAXS(2, 2)
        }
    }

    private fun createNormalizeArchMethod(): MethodNode {
        val notNull = Label()
        val x86_64 = Label()
        val aarch64 = Label()
        val x86 = Label()
        val i386 = Label()
        val i686 = Label()
        val unknown = Label()
        return method(PRIVATE + STATIC, "normalizeArch", "(Ljava/lang/String;)Ljava/lang/String;") {
            INSTRUCTIONS {
                ALOAD(0)
                IFNONNULL(notNull)
                LDC("")
                ASTORE(0)
                LABEL(notNull)
                ALOAD(0)
                INVOKEVIRTUAL("java/lang/String", "toLowerCase", "()Ljava/lang/String;")
                ASTORE(1)
                addEqualsBranch("amd64", x86_64)
                LDC("x86_64")
                ARETURN
                LABEL(x86_64)
                addEqualsBranch("x86_64", aarch64)
                LDC("x86_64")
                ARETURN
                LABEL(aarch64)
                addEqualsBranch("aarch64", x86)
                LDC("aarch64")
                ARETURN
                LABEL(x86)
                addEqualsBranch("arm64", i386)
                LDC("aarch64")
                ARETURN
                LABEL(i386)
                addEqualsBranch("x86", i686)
                LDC("x86")
                ARETURN
                LABEL(i686)
                addEqualsBranch("i386", unknown)
                LDC("x86")
                ARETURN
                ALOAD(1)
                LDC("i686")
                INVOKEVIRTUAL("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
                IFEQ(unknown)
                LDC("x86")
                ARETURN
                LABEL(unknown)
                LDC("unknown")
                ARETURN
            }
            MAXS(2, 2)
        }
    }

    private fun createSelectNativeLibraryResourceMethod(
        loaderInternalName: String,
        libraries: List<NativeLoaderLibrary>
    ): MethodNode {
        return createSelectNativeLibraryMethod(
            loaderInternalName = loaderInternalName,
            libraries = libraries,
            name = "selectNativeLibraryResource",
            selectedValue = { it.resourcePath }
        )
    }

    private fun createSelectNativeLibrarySuffixMethod(
        loaderInternalName: String,
        libraries: List<NativeLoaderLibrary>
    ): MethodNode {
        return createSelectNativeLibraryMethod(
            loaderInternalName = loaderInternalName,
            libraries = libraries,
            name = "selectNativeLibrarySuffix",
            selectedValue = { it.librarySuffix }
        )
    }

    private fun createSelectNativeLibraryMethod(
        loaderInternalName: String,
        libraries: List<NativeLoaderLibrary>,
        name: String,
        selectedValue: (NativeLoaderLibrary) -> String
    ): MethodNode {
        val available = libraries.joinToString { it.resourceDirectory }
        return method(PRIVATE + STATIC, name, "()Ljava/lang/String;") {
            INSTRUCTIONS {
                LDC("os.name")
                LDC("")
                INVOKESTATIC("java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
                INVOKESTATIC(loaderInternalName, "normalizeOs", "(Ljava/lang/String;)Ljava/lang/String;")
                ASTORE(0)
                LDC("os.arch")
                LDC("")
                INVOKESTATIC("java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
                INVOKESTATIC(loaderInternalName, "normalizeArch", "(Ljava/lang/String;)Ljava/lang/String;")
                ASTORE(1)
                ALOAD(0)
                LDC("-")
                INVOKEVIRTUAL("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;")
                ALOAD(1)
                INVOKEVIRTUAL("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;")
                ASTORE(2)
                libraries.forEach { library ->
                    val next = Label()
                    ALOAD(2)
                    LDC(library.resourceDirectory)
                    INVOKEVIRTUAL("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
                    IFEQ(next)
                    LDC(selectedValue(library))
                    ARETURN
                    LABEL(next)
                }
                NEW("java/lang/UnsatisfiedLinkError")
                DUP
                LDC("Unsupported native library platform: ")
                ALOAD(2)
                INVOKEVIRTUAL("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;")
                LDC(", available: $available")
                INVOKEVIRTUAL("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;")
                INVOKESPECIAL("java/lang/UnsatisfiedLinkError", "<init>", "(Ljava/lang/String;)V")
                ATHROW
            }
            MAXS(4, 3)
        }
    }

    private fun createLoadMethod(loaderInternalName: String): MethodNode {
        return method(PUBLIC + STATIC + SYNCHRONIZED, "load", "()V") {
            val alreadyLoading = Label()
            val hasStream = Label()
            val copyStart = Label()
            val copyEnd = Label()
            val closeFailure = Label()
            val rethrow = Label()

            INSTRUCTIONS {
                GETSTATIC(loaderInternalName, "loaded", "Z")
                IFEQ(alreadyLoading)
                RETURN

                LABEL(alreadyLoading)
                INVOKESTATIC(loaderInternalName, "selectNativeLibraryResource", "()Ljava/lang/String;")
                ASTORE(0)

                LDC(Type.getObjectType(loaderInternalName))
                ALOAD(0)
                INVOKEVIRTUAL("java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;")
                ASTORE(1)
                ALOAD(1)
                IFNONNULL(hasStream)

                NEW("java/lang/UnsatisfiedLinkError")
                DUP
                LDC("Failed to open native library resource: ")
                ALOAD(0)
                INVOKEVIRTUAL("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;")
                INVOKESPECIAL("java/lang/UnsatisfiedLinkError", "<init>", "(Ljava/lang/String;)V")
                ATHROW

                LABEL(hasStream)
                LABEL(copyStart)
                LDC("grunteon-native-")
                INVOKESTATIC(loaderInternalName, "selectNativeLibrarySuffix", "()Ljava/lang/String;")
                ICONST_0
                ANEWARRAY("java/nio/file/attribute/FileAttribute")
                INVOKESTATIC(
                    "java/nio/file/Files",
                    "createTempFile",
                    "(Ljava/lang/String;Ljava/lang/String;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;"
                )
                ASTORE(2)

                ALOAD(2)
                INVOKEINTERFACE("java/nio/file/Path", "toFile", "()Ljava/io/File;")
                ASTORE(3)
                ALOAD(3)
                INVOKEVIRTUAL("java/io/File", "deleteOnExit", "()V")

                ALOAD(1)
                ALOAD(2)
                ICONST_1
                ANEWARRAY("java/nio/file/CopyOption")
                DUP
                ICONST_0
                GETSTATIC("java/nio/file/StandardCopyOption", "REPLACE_EXISTING", "Ljava/nio/file/StandardCopyOption;")
                AASTORE
                INVOKESTATIC(
                    "java/nio/file/Files",
                    "copy",
                    "(Ljava/io/InputStream;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)J"
                )
                POP2
                LABEL(copyEnd)

                ALOAD(1)
                INVOKEVIRTUAL("java/io/InputStream", "close", "()V")
                ALOAD(3)
                INVOKEVIRTUAL("java/io/File", "getAbsolutePath", "()Ljava/lang/String;")
                INVOKESTATIC("java/lang/System", "load", "(Ljava/lang/String;)V")
                ICONST_1
                PUTSTATIC(loaderInternalName, "loaded", "Z")
                RETURN

                LABEL(closeFailure)
                ASTORE(4)
                ALOAD(1)
                IFNULL(rethrow)
                ALOAD(1)
                INVOKEVIRTUAL("java/io/InputStream", "close", "()V")
                LABEL(rethrow)
                ALOAD(4)
                ATHROW
            }
            TRYCATCH(copyStart, copyEnd, closeFailure, "java/lang/Throwable")
            MAXS(6, 5)
        }
    }

    private fun InsnListBuilder.addContainsBranch(value: String, falseTarget: Label) {
        ALOAD(1)
        LDC(value)
        INVOKEVIRTUAL("java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z")
        IFEQ(falseTarget)
    }

    private fun InsnListBuilder.addEqualsBranch(value: String, falseTarget: Label) {
        ALOAD(1)
        LDC(value)
        INVOKEVIRTUAL("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
        IFEQ(falseTarget)
    }
}
