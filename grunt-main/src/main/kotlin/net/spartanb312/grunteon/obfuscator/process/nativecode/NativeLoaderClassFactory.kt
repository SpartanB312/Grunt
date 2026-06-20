package net.spartanb312.grunteon.obfuscator.process.nativecode

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

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
        return ClassNode(Opcodes.ASM9).apply {
            version = Opcodes.V1_6
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
            name = loaderInternalName
            superName = "java/lang/Object"
            fields.add(
                FieldNode(
                    Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_VOLATILE,
                    "loaded",
                    "Z",
                    null,
                    null
                )
            )
            methods.add(createConstructor())
            methods.add(createRegisterNative())
            proxyMethods.forEach { (name, desc) ->
                methods.add(createProxyNative(name, desc))
            }
            methods.add(createNormalizeOsMethod())
            methods.add(createNormalizeArchMethod())
            methods.add(createSelectNativeLibraryResourceMethod(loaderInternalName, libraries))
            methods.add(createSelectNativeLibrarySuffixMethod(loaderInternalName, libraries))
            methods.add(createLoadMethod(loaderInternalName))
        }
    }

    private fun createConstructor(): MethodNode {
        return MethodNode(
            Opcodes.ASM9,
            Opcodes.ACC_PRIVATE,
            "<init>",
            "()V",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun createRegisterNative(): MethodNode {
        return MethodNode(
            Opcodes.ASM9,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_NATIVE,
            "registerNativesForClass",
            "(ILjava/lang/Class;)V",
            null,
            null
        )
    }

    private fun createProxyNative(name: String, desc: String): MethodNode {
        return MethodNode(
            Opcodes.ASM9,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_NATIVE or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE,
            name,
            desc,
            null,
            null
        ).apply {
            visibleAnnotations = mutableListOf(
                AnnotationNode("Ljava/lang/invoke/LambdaForm\$Hidden;"),
                AnnotationNode("Ljdk/internal/vm/annotation/Hidden;")
            )
        }
    }

    private fun createNormalizeOsMethod(): MethodNode {
        val method = MethodNode(
            Opcodes.ASM9,
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
            "normalizeOs",
            "(Ljava/lang/String;)Ljava/lang/String;",
            null,
            null
        )
        val notNull = LabelNode()
        val mac = LabelNode()
        val darwin = LabelNode()
        val linux = LabelNode()
        val nix = LabelNode()
        val nux = LabelNode()
        val aix = LabelNode()
        val unknown = LabelNode()
        method.instructions.apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(JumpInsnNode(Opcodes.IFNONNULL, notNull))
            add(LdcInsnNode(""))
            add(VarInsnNode(Opcodes.ASTORE, 0))
            add(notNull)
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false))
            add(VarInsnNode(Opcodes.ASTORE, 1))
            addContainsBranch("win", mac)
            add(LdcInsnNode("windows"))
            add(InsnNode(Opcodes.ARETURN))
            add(mac)
            addContainsBranch("mac", darwin)
            add(LdcInsnNode("macos"))
            add(InsnNode(Opcodes.ARETURN))
            add(darwin)
            addContainsBranch("darwin", linux)
            add(LdcInsnNode("macos"))
            add(InsnNode(Opcodes.ARETURN))
            add(linux)
            addContainsBranch("linux", nix)
            add(LdcInsnNode("linux"))
            add(InsnNode(Opcodes.ARETURN))
            add(nix)
            addContainsBranch("nix", nux)
            add(LdcInsnNode("linux"))
            add(InsnNode(Opcodes.ARETURN))
            add(nux)
            addContainsBranch("nux", aix)
            add(LdcInsnNode("linux"))
            add(InsnNode(Opcodes.ARETURN))
            add(aix)
            addContainsBranch("aix", unknown)
            add(LdcInsnNode("linux"))
            add(InsnNode(Opcodes.ARETURN))
            add(unknown)
            add(LdcInsnNode("unknown"))
            add(InsnNode(Opcodes.ARETURN))
        }
        method.maxStack = 2
        method.maxLocals = 2
        return method
    }

    private fun createNormalizeArchMethod(): MethodNode {
        val method = MethodNode(
            Opcodes.ASM9,
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
            "normalizeArch",
            "(Ljava/lang/String;)Ljava/lang/String;",
            null,
            null
        )
        val notNull = LabelNode()
        val x86_64 = LabelNode()
        val aarch64 = LabelNode()
        val x86 = LabelNode()
        val i386 = LabelNode()
        val i686 = LabelNode()
        val unknown = LabelNode()
        method.instructions.apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(JumpInsnNode(Opcodes.IFNONNULL, notNull))
            add(LdcInsnNode(""))
            add(VarInsnNode(Opcodes.ASTORE, 0))
            add(notNull)
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false))
            add(VarInsnNode(Opcodes.ASTORE, 1))
            addEqualsBranch("amd64", x86_64)
            add(LdcInsnNode("x86_64"))
            add(InsnNode(Opcodes.ARETURN))
            add(x86_64)
            addEqualsBranch("x86_64", aarch64)
            add(LdcInsnNode("x86_64"))
            add(InsnNode(Opcodes.ARETURN))
            add(aarch64)
            addEqualsBranch("aarch64", x86)
            add(LdcInsnNode("aarch64"))
            add(InsnNode(Opcodes.ARETURN))
            add(x86)
            addEqualsBranch("arm64", i386)
            add(LdcInsnNode("aarch64"))
            add(InsnNode(Opcodes.ARETURN))
            add(i386)
            addEqualsBranch("x86", i686)
            add(LdcInsnNode("x86"))
            add(InsnNode(Opcodes.ARETURN))
            add(i686)
            addEqualsBranch("i386", unknown)
            add(LdcInsnNode("x86"))
            add(InsnNode(Opcodes.ARETURN))
            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(LdcInsnNode("i686"))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false))
            add(JumpInsnNode(Opcodes.IFEQ, unknown))
            add(LdcInsnNode("x86"))
            add(InsnNode(Opcodes.ARETURN))
            add(unknown)
            add(LdcInsnNode("unknown"))
            add(InsnNode(Opcodes.ARETURN))
        }
        method.maxStack = 2
        method.maxLocals = 2
        return method
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
        val method = MethodNode(
            Opcodes.ASM9,
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
            name,
            "()Ljava/lang/String;",
            null,
            null
        )
        val available = libraries.joinToString { it.resourceDirectory }
        method.instructions.apply {
            add(LdcInsnNode("os.name"))
            add(LdcInsnNode(""))
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false
            ))
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                loaderInternalName,
                "normalizeOs",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            ))
            add(VarInsnNode(Opcodes.ASTORE, 0))
            add(LdcInsnNode("os.arch"))
            add(LdcInsnNode(""))
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "getProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false
            ))
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                loaderInternalName,
                "normalizeArch",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            ))
            add(VarInsnNode(Opcodes.ASTORE, 1))
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(LdcInsnNode("-"))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false))
            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false))
            add(VarInsnNode(Opcodes.ASTORE, 2))
            libraries.forEach { library ->
                val next = LabelNode()
                add(VarInsnNode(Opcodes.ALOAD, 2))
                add(LdcInsnNode(library.resourceDirectory))
                add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false))
                add(JumpInsnNode(Opcodes.IFEQ, next))
                add(LdcInsnNode(selectedValue(library)))
                add(InsnNode(Opcodes.ARETURN))
                add(next)
            }
            add(TypeInsnNode(Opcodes.NEW, "java/lang/UnsatisfiedLinkError"))
            add(InsnNode(Opcodes.DUP))
            add(LdcInsnNode("Unsupported native library platform: "))
            add(VarInsnNode(Opcodes.ALOAD, 2))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false))
            add(LdcInsnNode(", available: $available"))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false))
            add(MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "java/lang/UnsatisfiedLinkError",
                "<init>",
                "(Ljava/lang/String;)V",
                false
            ))
            add(InsnNode(Opcodes.ATHROW))
        }
        method.maxStack = 4
        method.maxLocals = 3
        return method
    }

    private fun createLoadMethod(
        loaderInternalName: String
    ): MethodNode {
        val method = MethodNode(
            Opcodes.ASM9,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNCHRONIZED,
            "load",
            "()V",
            null,
            null
        )
        val alreadyLoading = LabelNode()
        val hasStream = LabelNode()
        val copyStart = LabelNode()
        val copyEnd = LabelNode()
        val closeFailure = LabelNode()
        val rethrow = LabelNode()

        method.instructions.apply {
            add(FieldInsnNode(Opcodes.GETSTATIC, loaderInternalName, "loaded", "Z"))
            add(JumpInsnNode(Opcodes.IFEQ, alreadyLoading))
            add(InsnNode(Opcodes.RETURN))

            add(alreadyLoading)
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                loaderInternalName,
                "selectNativeLibraryResource",
                "()Ljava/lang/String;",
                false
            ))
            add(VarInsnNode(Opcodes.ASTORE, 0))

            add(LdcInsnNode(Type.getObjectType(loaderInternalName)))
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Class",
                "getResourceAsStream",
                "(Ljava/lang/String;)Ljava/io/InputStream;",
                false
            ))
            add(VarInsnNode(Opcodes.ASTORE, 1))
            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(JumpInsnNode(Opcodes.IFNONNULL, hasStream))

            add(TypeInsnNode(Opcodes.NEW, "java/lang/UnsatisfiedLinkError"))
            add(InsnNode(Opcodes.DUP))
            add(LdcInsnNode("Failed to open native library resource: "))
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "concat",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            ))
            add(MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "java/lang/UnsatisfiedLinkError",
                "<init>",
                "(Ljava/lang/String;)V",
                false
            ))
            add(InsnNode(Opcodes.ATHROW))

            add(hasStream)
            add(copyStart)
            add(LdcInsnNode("grunteon-native-"))
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                loaderInternalName,
                "selectNativeLibrarySuffix",
                "()Ljava/lang/String;",
                false
            ))
            add(InsnNode(Opcodes.ICONST_0))
            add(TypeInsnNode(Opcodes.ANEWARRAY, "java/nio/file/attribute/FileAttribute"))
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/nio/file/Files",
                "createTempFile",
                "(Ljava/lang/String;Ljava/lang/String;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;",
                false
            ))
            add(VarInsnNode(Opcodes.ASTORE, 2))

            add(VarInsnNode(Opcodes.ALOAD, 2))
            add(MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "toFile", "()Ljava/io/File;", true))
            add(VarInsnNode(Opcodes.ASTORE, 3))
            add(VarInsnNode(Opcodes.ALOAD, 3))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/File", "deleteOnExit", "()V", false))

            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(VarInsnNode(Opcodes.ALOAD, 2))
            add(InsnNode(Opcodes.ICONST_1))
            add(TypeInsnNode(Opcodes.ANEWARRAY, "java/nio/file/CopyOption"))
            add(InsnNode(Opcodes.DUP))
            add(InsnNode(Opcodes.ICONST_0))
            add(FieldInsnNode(
                Opcodes.GETSTATIC,
                "java/nio/file/StandardCopyOption",
                "REPLACE_EXISTING",
                "Ljava/nio/file/StandardCopyOption;"
            ))
            add(InsnNode(Opcodes.AASTORE))
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/nio/file/Files",
                "copy",
                "(Ljava/io/InputStream;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)J",
                false
            ))
            add(InsnNode(Opcodes.POP2))
            add(copyEnd)

            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "close", "()V", false))
            add(VarInsnNode(Opcodes.ALOAD, 3))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/File", "getAbsolutePath", "()Ljava/lang/String;", false))
            add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "load", "(Ljava/lang/String;)V", false))
            add(InsnNode(Opcodes.ICONST_1))
            add(FieldInsnNode(Opcodes.PUTSTATIC, loaderInternalName, "loaded", "Z"))
            add(InsnNode(Opcodes.RETURN))

            add(closeFailure)
            add(VarInsnNode(Opcodes.ASTORE, 4))
            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(JumpInsnNode(Opcodes.IFNULL, rethrow))
            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "close", "()V", false))
            add(rethrow)
            add(VarInsnNode(Opcodes.ALOAD, 4))
            add(InsnNode(Opcodes.ATHROW))
        }
        method.tryCatchBlocks.add(TryCatchBlockNode(copyStart, copyEnd, closeFailure, "java/lang/Throwable"))
        method.maxStack = 6
        method.maxLocals = 5
        return method
    }

    private fun org.objectweb.asm.tree.InsnList.addContainsBranch(value: String, falseTarget: LabelNode) {
        add(VarInsnNode(Opcodes.ALOAD, 1))
        add(LdcInsnNode(value))
        add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false))
        add(JumpInsnNode(Opcodes.IFEQ, falseTarget))
    }

    private fun org.objectweb.asm.tree.InsnList.addEqualsBranch(value: String, falseTarget: LabelNode) {
        add(VarInsnNode(Opcodes.ALOAD, 1))
        add(LdcInsnNode(value))
        add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false))
        add(JumpInsnNode(Opcodes.IFEQ, falseTarget))
    }
}
