package net.spartanb312.grunt.patch

import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

object ClassPatchTransformer : Transformer("ClassPatcher", Category.Miscellaneous) {

    override fun ResourceCache.transform() {
        nonExcluded.forEach { classNode ->
            if (classNode.version >= Opcodes.V1_6) {
                classNode.version = Opcodes.V1_5
                println("Patched class version for ${classNode.name}")
            }

            var addArrayUtil = false
            classNode.methods.forEach { methodNode ->
                patchLocale(classNode, methodNode)
                if (patchArrayCopyOf(classNode, methodNode)) addArrayUtil = true
            }
            if (addArrayUtil) addTrashClass(generateArrayUtil())
        }
    }

    private fun patchLocale(classNode: ClassNode, methodNode: MethodNode): Boolean {
        var patched = false
        methodNode.instructions.forEach { insn ->
            if (insn is FieldInsnNode && insn.opcode == Opcodes.GETSTATIC) {
                if (insn.name == "ROOT") {
                    insn.name = "US"
                    patched = true
                }
            }
        }
        if (patched) println("Patched Locale.ROOT for: ${classNode.name}")
        return patched
    }

    private fun patchArrayCopyOf(classNode: ClassNode, methodNode: MethodNode): Boolean {
        var patched = false
        methodNode.instructions.forEach { insn ->
            if (insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESTATIC) {
                if (insn.owner == "java/util/Arrays" && insn.name == "copyOf") {
                    insn.owner = "net/spartanb312/grunt/patch/ArrayUtil"
                    patched = true
                }
            }
        }
        if (patched) println("Patched Arrays.copyOf for: ${classNode.name}")
        return patched
    }

    private fun generateArrayUtil() = clazz(
        access = PUBLIC + SYNCHRONIZED,
        name = "net/spartanb312/grunt/patch/ArrayUtil",
        superName = "java/lang/Object",
        interfaces = listOf(),
        signature = null,
        version = Java5
    ) {
        val method0 = method(access = PUBLIC, name = "<init>", desc = "()V", signature = null, exceptions = null) {
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ALOAD(0)
                INVOKESPECIAL("java/lang/Object", "<init>", "()V", false)
                RETURN
                LABEL(label1)
                LOCALVAR("this", "Lnet/spartanb312/grunt/patch/ArrayUtil;", null, label0, label1, 0)
                MAXS(1, 1)
            }
        }
        +method0
        val method1 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([Ljava/lang/Object;I)[Ljava/lang/Object;",
            signature = "<T:Ljava/lang/Object;>([TT;I)[TT;",
            exceptions = null
        ) {
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ALOAD(0)
                ILOAD(1)
                ALOAD(0)
                INVOKEVIRTUAL("java/lang/Object", "getClass", "()Ljava/lang/Class;", false)
                INVOKESTATIC(
                    "net/spartanb312/grunt/patch/ArrayUtil",
                    "copyOf",
                    "([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;",
                    false
                )
                CHECKCAST("[Ljava/lang/Object;")
                ARETURN
                LABEL(label1)
                LOCALVAR("original", "[Ljava/lang/Object;", "[TT;", label0, label1, 0)
                LOCALVAR("newLength", "I", null, label0, label1, 1)
                MAXS(3, 2)
            }
        }
        +method1
        val method2 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;",
            signature = "<T:Ljava/lang/Object;U:Ljava/lang/Object;>([TU;ILjava/lang/Class<+[TT;>;)[TT;",
            exceptions = null
        ) {
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ALOAD(2)
                LDC(Type.getType("[Ljava/lang/Object;"))
                IF_ACMPNE(label1)
                ILOAD(1)
                ANEWARRAY("java/lang/Object")
                CHECKCAST("[Ljava/lang/Object;")
                GOTO(label2)
                LABEL(label1)
                ALOAD(2)
                LABEL(label3)
                INVOKEVIRTUAL("java/lang/Class", "getComponentType", "()Ljava/lang/Class;", false)
                ILOAD(1)
                INVOKESTATIC(
                    "java/lang/reflect/Array",
                    "newInstance",
                    "(Ljava/lang/Class;I)Ljava/lang/Object;",
                    false
                )
                CHECKCAST("[Ljava/lang/Object;")
                CHECKCAST("[Ljava/lang/Object;")
                LABEL(label2)
                ASTORE(3)
                LABEL(label4)
                ALOAD(0)
                ICONST_0
                ALOAD(3)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                LABEL(label5)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label6)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label7)
                ALOAD(3)
                ARETURN
                LABEL(label8)
                LOCALVAR("original", "[Ljava/lang/Object;", "[TU;", label0, label8, 0)
                LOCALVAR("newLength", "I", null, label0, label8, 1)
                LOCALVAR("newType", "Ljava/lang/Class;", "Ljava/lang/Class<+[TT;>;", label0, label8, 2)
                LOCALVAR("copy", "[Ljava/lang/Object;", "[TT;", label4, label8, 3)
                MAXS(6, 4)
            }
        }
        +method2
        val method3 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([BI)[B",
            signature = null,
            exceptions = null
        ) {
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(1)
                NEWARRAY(8)
                ASTORE(2)
                LABEL(label1)
                ALOAD(0)
                ICONST_0
                ALOAD(2)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                LABEL(label2)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label3)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label4)
                ALOAD(2)
                ARETURN
                LABEL(label5)
                LOCALVAR("original", "[B", null, label0, label5, 0)
                LOCALVAR("newLength", "I", null, label0, label5, 1)
                LOCALVAR("copy", "[B", null, label1, label5, 2)
                MAXS(6, 3)
            }
        }
        +method3
        val method4 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([SI)[S",
            signature = null,
            exceptions = null
        ) {
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(1)
                NEWARRAY(9.toInt())
                ASTORE(2)
                LABEL(label1)
                ALOAD(0)
                ICONST_0
                ALOAD(2)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                LABEL(label2)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label3)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label4)
                ALOAD(2)
                ARETURN
                LABEL(label5)
                LOCALVAR("original", "[S", null, label0, label5, 0)
                LOCALVAR("newLength", "I", null, label0, label5, 1)
                LOCALVAR("copy", "[S", null, label1, label5, 2)
                MAXS(6, 3)
            }
        }
        +method4
        val method5 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([II)[I",
            signature = null,
            exceptions = null
        ) {
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(1)
                NEWARRAY(10.toInt())
                ASTORE(2)
                LABEL(label1)
                ALOAD(0)
                ICONST_0
                ALOAD(2)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                LABEL(label2)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label3)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label4)
                ALOAD(2)
                ARETURN
                LABEL(label5)
                LOCALVAR("original", "[I", null, label0, label5, 0)
                LOCALVAR("newLength", "I", null, label0, label5, 1)
                LOCALVAR("copy", "[I", null, label1, label5, 2)
                MAXS(6, 3)
            }
        }
        +method5
        val method6 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([JI)[J",
            signature = null,
            exceptions = null
        ) {
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(1)
                NEWARRAY(11.toInt())
                ASTORE(2)
                LABEL(label1)
                ALOAD(0)
                ICONST_0
                ALOAD(2)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                LABEL(label2)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label3)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label4)
                ALOAD(2)
                ARETURN
                LABEL(label5)
                LOCALVAR("original", "[J", null, label0, label5, 0)
                LOCALVAR("newLength", "I", null, label0, label5, 1)
                LOCALVAR("copy", "[J", null, label1, label5, 2)
                MAXS(6, 3)
            }
        }
        +method6
        val method7 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([CI)[C",
            signature = null,
            exceptions = null
        ) {
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(1)
                NEWARRAY(5.toInt())
                ASTORE(2)
                LABEL(label1)
                ALOAD(0)
                ICONST_0
                ALOAD(2)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                LABEL(label2)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label3)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label4)
                ALOAD(2)
                ARETURN
                LABEL(label5)
                LOCALVAR("original", "[C", null, label0, label5, 0)
                LOCALVAR("newLength", "I", null, label0, label5, 1)
                LOCALVAR("copy", "[C", null, label1, label5, 2)
                MAXS(6, 3)
            }
        }
        +method7
        val method8 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([FI)[F",
            signature = null,
            exceptions = null
        ) {
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(1)
                NEWARRAY(6.toInt())
                ASTORE(2)
                LABEL(label1)
                ALOAD(0)
                ICONST_0
                ALOAD(2)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                LABEL(label2)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label3)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label4)
                ALOAD(2)
                ARETURN
                LABEL(label5)
                LOCALVAR("original", "[F", null, label0, label5, 0)
                LOCALVAR("newLength", "I", null, label0, label5, 1)
                LOCALVAR("copy", "[F", null, label1, label5, 2)
                MAXS(6, 3)
            }
        }
        +method8
        val method9 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([DI)[D",
            signature = null,
            exceptions = null
        ) {
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(1)
                NEWARRAY(7)
                ASTORE(2)
                LABEL(label1)
                ALOAD(0)
                ICONST_0
                ALOAD(2)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                LABEL(label2)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label3)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label4)
                ALOAD(2)
                ARETURN
                LABEL(label5)
                LOCALVAR("original", "[D", null, label0, label5, 0)
                LOCALVAR("newLength", "I", null, label0, label5, 1)
                LOCALVAR("copy", "[D", null, label1, label5, 2)
                MAXS(6, 3)
            }
        }
        +method9
        val method10 = method(
            access = PUBLIC + STATIC,
            name = "copyOf",
            desc = "([ZI)[Z",
            signature = null,
            exceptions = null
        ) {
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(1)
                NEWARRAY(4)
                ASTORE(2)
                LABEL(label1)
                ALOAD(0)
                ICONST_0
                ALOAD(2)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                LABEL(label2)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label3)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label4)
                ALOAD(2)
                ARETURN
                LABEL(label5)
                LOCALVAR("original", "[Z", null, label0, label5, 0)
                LOCALVAR("newLength", "I", null, label0, label5, 1)
                LOCALVAR("copy", "[Z", null, label1, label5, 2)
                MAXS(6, 3)
            }
        }
        +method10
        val method11 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([Ljava/lang/Object;II)[Ljava/lang/Object;",
            signature = "<T:Ljava/lang/Object;>([TT;II)[TT;",
            exceptions = null
        ) {
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ALOAD(0)
                ILOAD(1)
                ILOAD(2)
                ALOAD(0)
                INVOKEVIRTUAL("java/lang/Object", "getClass", "()Ljava/lang/Class;", false)
                INVOKESTATIC(
                    "net/spartanb312/grunt/patch/ArrayUtil",
                    "copyOfRange",
                    "([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;",
                    false
                )
                ARETURN
                LABEL(label1)
                LOCALVAR("original", "[Ljava/lang/Object;", "[TT;", label0, label1, 0)
                LOCALVAR("from", "I", null, label0, label1, 1)
                LOCALVAR("to", "I", null, label0, label1, 2)
                MAXS(4, 3)
            }
        }
        +method11
        val method12 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;",
            signature = "<T:Ljava/lang/Object;U:Ljava/lang/Object;>([TU;IILjava/lang/Class<+[TT;>;)[TT;",
            exceptions = null
        ) {
            val label11 = Label()
            val label10 = Label()
            val label9 = Label()
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(2)
                ILOAD(1)
                ISUB
                ISTORE(4)
                LABEL(label1)
                ILOAD(4)
                IFGE(label2)
                LABEL(label3)
                NEW("java/lang/IllegalArgumentException")
                DUP
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ILOAD(1)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                LDC(" > ")
                INVOKEVIRTUAL(
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
                ILOAD(2)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKESPECIAL("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false)
                ATHROW
                LABEL(label2)
                ALOAD(3)
                LDC(Type.getType("[Ljava/lang/Object;"))
                IF_ACMPNE(label4)
                ILOAD(4)
                ANEWARRAY("java/lang/Object")
                CHECKCAST("[Ljava/lang/Object;")
                GOTO(label5)
                LABEL(label4)
                ALOAD(3)
                LABEL(label6)
                INVOKEVIRTUAL("java/lang/Class", "getComponentType", "()Ljava/lang/Class;", false)
                ILOAD(4)
                INVOKESTATIC(
                    "java/lang/reflect/Array",
                    "newInstance",
                    "(Ljava/lang/Class;I)Ljava/lang/Object;",
                    false
                )
                CHECKCAST("[Ljava/lang/Object;")
                CHECKCAST("[Ljava/lang/Object;")
                LABEL(label5)
                ASTORE(5)
                LABEL(label7)
                ALOAD(0)
                ILOAD(1)
                ALOAD(5)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                ISUB
                ILOAD(4)
                LABEL(label8)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label9)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label10)
                ALOAD(5)
                ARETURN
                LABEL(label11)
                LOCALVAR("original", "[Ljava/lang/Object;", "[TU;", label0, label11, 0)
                LOCALVAR("from", "I", null, label0, label11, 1)
                LOCALVAR("to", "I", null, label0, label11, 2)
                LOCALVAR("newType", "Ljava/lang/Class;", "Ljava/lang/Class<+[TT;>;", label0, label11, 3)
                LOCALVAR("newLength", "I", null, label1, label11, 4)
                LOCALVAR("copy", "[Ljava/lang/Object;", "[TT;", label7, label11, 5)
                MAXS(6, 6)
            }
        }
        +method12
        val method13 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([BII)[B",
            signature = null,
            exceptions = null
        ) {
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(2)
                ILOAD(1)
                ISUB
                ISTORE(3)
                LABEL(label1)
                ILOAD(3)
                IFGE(label2)
                LABEL(label3)
                NEW("java/lang/IllegalArgumentException")
                DUP
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ILOAD(1)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                LDC(" > ")
                INVOKEVIRTUAL(
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
                ILOAD(2)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKESPECIAL("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false)
                ATHROW
                LABEL(label2)
                ILOAD(3)
                NEWARRAY(8.toInt())
                ASTORE(4)
                LABEL(label4)
                ALOAD(0)
                ILOAD(1)
                ALOAD(4)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                ISUB
                ILOAD(3)
                LABEL(label5)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label6)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label7)
                ALOAD(4)
                ARETURN
                LABEL(label8)
                LOCALVAR("original", "[B", null, label0, label8, 0)
                LOCALVAR("from", "I", null, label0, label8, 1)
                LOCALVAR("to", "I", null, label0, label8, 2)
                LOCALVAR("newLength", "I", null, label1, label8, 3)
                LOCALVAR("copy", "[B", null, label4, label8, 4)
                MAXS(6, 5)
            }
        }
        +method13
        val method14 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([SII)[S",
            signature = null,
            exceptions = null
        ) {
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(2)
                ILOAD(1)
                ISUB
                ISTORE(3)
                LABEL(label1)
                ILOAD(3)
                IFGE(label2)
                LABEL(label3)
                NEW("java/lang/IllegalArgumentException")
                DUP
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ILOAD(1)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                LDC(" > ")
                INVOKEVIRTUAL(
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
                ILOAD(2)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKESPECIAL("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false)
                ATHROW
                LABEL(label2)
                ILOAD(3)
                NEWARRAY(9)
                ASTORE(4)
                LABEL(label4)
                ALOAD(0)
                ILOAD(1)
                ALOAD(4)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                ISUB
                ILOAD(3)
                LABEL(label5)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label6)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label7)
                ALOAD(4)
                ARETURN
                LABEL(label8)
                LOCALVAR("original", "[S", null, label0, label8, 0)
                LOCALVAR("from", "I", null, label0, label8, 1)
                LOCALVAR("to", "I", null, label0, label8, 2)
                LOCALVAR("newLength", "I", null, label1, label8, 3)
                LOCALVAR("copy", "[S", null, label4, label8, 4)
                MAXS(6, 5)
            }
        }
        +method14
        val method15 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([III)[I",
            signature = null,
            exceptions = null
        ) {
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(2)
                ILOAD(1)
                ISUB
                ISTORE(3)
                LABEL(label1)
                ILOAD(3)
                IFGE(label2)
                LABEL(label3)
                NEW("java/lang/IllegalArgumentException")
                DUP
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ILOAD(1)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                LDC(" > ")
                INVOKEVIRTUAL(
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
                ILOAD(2)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKESPECIAL("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false)
                ATHROW
                LABEL(label2)
                ILOAD(3)
                NEWARRAY(10.toInt())
                ASTORE(4)
                LABEL(label4)
                ALOAD(0)
                ILOAD(1)
                ALOAD(4)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                ISUB
                ILOAD(3)
                LABEL(label5)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label6)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label7)
                ALOAD(4)
                ARETURN
                LABEL(label8)
                LOCALVAR("original", "[I", null, label0, label8, 0)
                LOCALVAR("from", "I", null, label0, label8, 1)
                LOCALVAR("to", "I", null, label0, label8, 2)
                LOCALVAR("newLength", "I", null, label1, label8, 3)
                LOCALVAR("copy", "[I", null, label4, label8, 4)
                MAXS(6, 5)
            }
        }
        +method15
        val method16 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([JII)[J",
            signature = null,
            exceptions = null
        ) {
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(2)
                ILOAD(1)
                ISUB
                ISTORE(3)
                LABEL(label1)
                ILOAD(3)
                IFGE(label2)
                LABEL(label3)
                NEW("java/lang/IllegalArgumentException")
                DUP
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ILOAD(1)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                LDC(" > ")
                INVOKEVIRTUAL(
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
                ILOAD(2)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKESPECIAL("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false)
                ATHROW
                LABEL(label2)
                ILOAD(3)
                NEWARRAY(11.toInt())
                ASTORE(4)
                LABEL(label4)
                ALOAD(0)
                ILOAD(1)
                ALOAD(4)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                ISUB
                ILOAD(3)
                LABEL(label5)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label6)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label7)
                ALOAD(4)
                ARETURN
                LABEL(label8)
                LOCALVAR("original", "[J", null, label0, label8, 0)
                LOCALVAR("from", "I", null, label0, label8, 1)
                LOCALVAR("to", "I", null, label0, label8, 2)
                LOCALVAR("newLength", "I", null, label1, label8, 3)
                LOCALVAR("copy", "[J", null, label4, label8, 4)
                MAXS(6, 5)
            }
        }
        +method16
        val method17 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([CII)[C",
            signature = null,
            exceptions = null
        ) {
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(2)
                ILOAD(1)
                ISUB
                ISTORE(3)
                LABEL(label1)
                ILOAD(3)
                IFGE(label2)
                LABEL(label3)
                NEW("java/lang/IllegalArgumentException")
                DUP
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ILOAD(1)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                LDC(" > ")
                INVOKEVIRTUAL(
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
                ILOAD(2)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKESPECIAL("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false)
                ATHROW
                LABEL(label2)
                ILOAD(3)
                NEWARRAY(5.toInt())
                ASTORE(4)
                LABEL(label4)
                ALOAD(0)
                ILOAD(1)
                ALOAD(4)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                ISUB
                ILOAD(3)
                LABEL(label5)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label6)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label7)
                ALOAD(4)
                ARETURN
                LABEL(label8)
                LOCALVAR("original", "[C", null, label0, label8, 0)
                LOCALVAR("from", "I", null, label0, label8, 1)
                LOCALVAR("to", "I", null, label0, label8, 2)
                LOCALVAR("newLength", "I", null, label1, label8, 3)
                LOCALVAR("copy", "[C", null, label4, label8, 4)
                MAXS(6, 5)
            }
        }
        +method17
        val method18 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([FII)[F",
            signature = null,
            exceptions = null
        ) {
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(2)
                ILOAD(1)
                ISUB
                ISTORE(3)
                LABEL(label1)
                ILOAD(3)
                IFGE(label2)
                LABEL(label3)
                NEW("java/lang/IllegalArgumentException")
                DUP
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ILOAD(1)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                LDC(" > ")
                INVOKEVIRTUAL(
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
                ILOAD(2)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKESPECIAL("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false)
                ATHROW
                LABEL(label2)
                ILOAD(3)
                NEWARRAY(6.toInt())
                ASTORE(4)
                LABEL(label4)
                ALOAD(0)
                ILOAD(1)
                ALOAD(4)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                ISUB
                ILOAD(3)
                LABEL(label5)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label6)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label7)
                ALOAD(4)
                ARETURN
                LABEL(label8)
                LOCALVAR("original", "[F", null, label0, label8, 0)
                LOCALVAR("from", "I", null, label0, label8, 1)
                LOCALVAR("to", "I", null, label0, label8, 2)
                LOCALVAR("newLength", "I", null, label1, label8, 3)
                LOCALVAR("copy", "[F", null, label4, label8, 4)
                MAXS(6, 5)
            }
        }
        +method18
        val method19 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([DII)[D",
            signature = null,
            exceptions = null
        ) {
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(2)
                ILOAD(1)
                ISUB
                ISTORE(3)
                LABEL(label1)
                ILOAD(3)
                IFGE(label2)
                LABEL(label3)
                NEW("java/lang/IllegalArgumentException")
                DUP
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ILOAD(1)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                LDC(" > ")
                INVOKEVIRTUAL(
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
                ILOAD(2)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKESPECIAL("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false)
                ATHROW
                LABEL(label2)
                ILOAD(3)
                NEWARRAY(7.toInt())
                ASTORE(4)
                LABEL(label4)
                ALOAD(0)
                ILOAD(1)
                ALOAD(4)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                ISUB
                ILOAD(3)
                LABEL(label5)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label6)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label7)
                ALOAD(4)
                ARETURN
                LABEL(label8)
                LOCALVAR("original", "[D", null, label0, label8, 0)
                LOCALVAR("from", "I", null, label0, label8, 1)
                LOCALVAR("to", "I", null, label0, label8, 2)
                LOCALVAR("newLength", "I", null, label1, label8, 3)
                LOCALVAR("copy", "[D", null, label4, label8, 4)
                MAXS(6, 5)
            }
        }
        +method19
        val method20 = method(
            access = PUBLIC + STATIC,
            name = "copyOfRange",
            desc = "([ZII)[Z",
            signature = null,
            exceptions = null
        ) {
            val label8 = Label()
            val label7 = Label()
            val label6 = Label()
            val label5 = Label()
            val label4 = Label()
            val label3 = Label()
            val label2 = Label()
            val label1 = Label()
            val label0 = Label()
            INSTRUCTIONS {
                LABEL(label0)
                ILOAD(2)
                ILOAD(1)
                ISUB
                ISTORE(3)
                LABEL(label1)
                ILOAD(3)
                IFGE(label2)
                LABEL(label3)
                NEW("java/lang/IllegalArgumentException")
                DUP
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V", false)
                ILOAD(1)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                LDC(" > ")
                INVOKEVIRTUAL(
                    "java/lang/StringBuilder",
                    "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                    false
                )
                ILOAD(2)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false)
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                INVOKESPECIAL("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false)
                ATHROW
                LABEL(label2)
                ILOAD(3)
                NEWARRAY(4.toInt())
                ASTORE(4)
                LABEL(label4)
                ALOAD(0)
                ILOAD(1)
                ALOAD(4)
                ICONST_0
                ALOAD(0)
                ARRAYLENGTH
                ILOAD(1)
                ISUB
                ILOAD(3)
                LABEL(label5)
                INVOKESTATIC("java/lang/Math", "min", "(II)I", false)
                LABEL(label6)
                INVOKESTATIC("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false)
                LABEL(label7)
                ALOAD(4)
                ARETURN
                LABEL(label8)
                LOCALVAR("original", "[Z", null, label0, label8, 0)
                LOCALVAR("from", "I", null, label0, label8, 1)
                LOCALVAR("to", "I", null, label0, label8, 2)
                LOCALVAR("newLength", "I", null, label1, label8, 3)
                LOCALVAR("copy", "[Z", null, label4, label8, 4)
                MAXS(6, 5)
            }
        }
        +method20
    }

}