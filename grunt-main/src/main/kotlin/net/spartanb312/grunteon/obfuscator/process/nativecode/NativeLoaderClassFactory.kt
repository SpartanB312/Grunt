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
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

internal object NativeLoaderClassFactory {

    fun create(
        loaderInternalName: String,
        resourcePath: String,
        librarySuffix: String,
        proxyMethods: List<Pair<String, String>> = emptyList()
    ): ClassNode {
        return ClassNode(Opcodes.ASM9).apply {
            version = Opcodes.V1_8
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
            methods.add(createLoadMethod(loaderInternalName, resourcePath, librarySuffix))
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

    private fun createLoadMethod(
        loaderInternalName: String,
        resourcePath: String,
        librarySuffix: String
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

        method.instructions.apply {
            add(FieldInsnNode(Opcodes.GETSTATIC, loaderInternalName, "loaded", "Z"))
            add(JumpInsnNode(Opcodes.IFEQ, alreadyLoading))
            add(InsnNode(Opcodes.RETURN))

            add(alreadyLoading)
            add(LdcInsnNode(resourcePath))
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
            add(LdcInsnNode("grunteon-native-"))
            add(LdcInsnNode(librarySuffix))
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

            add(VarInsnNode(Opcodes.ALOAD, 1))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "close", "()V", false))
            add(VarInsnNode(Opcodes.ALOAD, 3))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/File", "getAbsolutePath", "()Ljava/lang/String;", false))
            add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "load", "(Ljava/lang/String;)V", false))
            add(InsnNode(Opcodes.ICONST_1))
            add(FieldInsnNode(Opcodes.PUTSTATIC, loaderInternalName, "loaded", "Z"))
            add(InsnNode(Opcodes.RETURN))
        }
        method.maxStack = 6
        method.maxLocals = 4
        return method
    }
}
