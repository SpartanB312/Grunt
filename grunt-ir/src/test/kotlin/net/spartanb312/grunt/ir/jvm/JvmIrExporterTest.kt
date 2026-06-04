package net.spartanb312.grunt.ir.jvm

import net.spartanb312.grunt.ir.core.IrVerifier
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmIrExporterTest {
    @Test
    fun exportsSimpleBranchMethodNode() {
        val elseLabel = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "sign", "(I)I", null, null).apply {
            instructions = InsnList().apply {
                add(VarInsnNode(Opcodes.ILOAD, 0))
                add(JumpInsnNode(Opcodes.IFLT, elseLabel))
                add(InsnNode(Opcodes.ICONST_1))
                add(InsnNode(Opcodes.IRETURN))
                add(elseLabel)
                add(InsnNode(Opcodes.ICONST_M1))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 1
            maxStack = 1
        }
        val imported = JvmIrImporter().import("example/Test", method)

        val exported = JvmIrExporter(imported.metadata).export(imported.function)
        Analyzer(BasicInterpreter()).analyze("example/Test", exported)
        val reimported = JvmIrImporter().import("example/Test", exported)
        val verification = IrVerifier.verify(reimported.function)

        assertTrue(verification.isValid, verification.issues.joinToString("\n"))
        assertEquals("sign", exported.name)
        assertEquals("(I)I", exported.desc)
    }

    @Test
    fun exportsConstantDynamicMethodNode() {
        val bootstrap = Handle(
            Opcodes.H_INVOKESTATIC,
            "example/Test",
            "bootstrap",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;)I",
            false
        )
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "dynamicValue", "()I", null, null).apply {
            instructions = InsnList().apply {
                add(LdcInsnNode(ConstantDynamic("answer", "I", bootstrap)))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 0
            maxStack = 1
        }
        val imported = JvmIrImporter().import("example/Test", method)

        val exported = JvmIrExporter(imported.metadata).export(imported.function)
        val hasCondy = exported.instructions.toArray().any {
            it is LdcInsnNode && it.cst is ConstantDynamic
        }

        Analyzer(BasicInterpreter()).analyze("example/Test", exported)
        assertTrue(hasCondy)
    }

    @Test
    fun preservesReferenceCastOnRoundTrip() {
        val method = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "castToString",
            "(Ljava/lang/Object;)Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions = InsnList().apply {
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"))
                add(InsnNode(Opcodes.ARETURN))
            }
            maxLocals = 1
            maxStack = 1
        }
        val imported = JvmIrImporter().import("example/Test", method)

        val exported = JvmIrExporter(imported.metadata).export(imported.function)
        val hasCheckcast = exported.instructions.toArray().any {
            it is TypeInsnNode && it.opcode == Opcodes.CHECKCAST && it.desc == "java/lang/String"
        }

        Analyzer(BasicInterpreter()).analyze("example/Test", exported)
        assertTrue(hasCheckcast)
    }

    @Test
    fun preservesPrimitiveByteArrayTypeOnRoundTrip() {
        val method = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "readBytes",
            "(Ljavax/sound/sampled/AudioInputStream;I)I",
            null,
            null
        ).apply {
            instructions = InsnList().apply {
                add(VarInsnNode(Opcodes.ILOAD, 1))
                add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE))
                add(VarInsnNode(Opcodes.ASTORE, 2))
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(VarInsnNode(Opcodes.ALOAD, 2))
                add(InsnNode(Opcodes.ICONST_0))
                add(VarInsnNode(Opcodes.ILOAD, 1))
                add(
                    MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "javax/sound/sampled/AudioInputStream",
                        "read",
                        "([BII)I",
                        false
                    )
                )
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = 3
            maxStack = 4
        }
        val imported = JvmIrImporter().import("example/Test", method)

        val exported = JvmIrExporter(imported.metadata).export(imported.function)
        val hasByteArrayAllocation = exported.instructions.toArray().any {
            it is IntInsnNode && it.opcode == Opcodes.NEWARRAY && it.operand == Opcodes.T_BYTE
        }

        Analyzer(BasicInterpreter()).analyze("example/Test", exported)
        assertTrue(hasByteArrayAllocation)
    }

    @Test
    fun exportsMethodsWhoseCallStackExceedsDefaultExporterStackCap() {
        val argCount = 70
        val descriptor = "(${"I".repeat(argCount)})I"
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "wideCall", descriptor, null, null).apply {
            instructions = InsnList().apply {
                repeat(argCount) { index ->
                    add(VarInsnNode(Opcodes.ILOAD, index))
                }
                add(MethodInsnNode(Opcodes.INVOKESTATIC, "example/Test", "sink", descriptor, false))
                add(InsnNode(Opcodes.IRETURN))
            }
            maxLocals = argCount
            maxStack = argCount
        }
        val imported = JvmIrImporter().import("example/Test", method)

        val exported = JvmIrExporter(imported.metadata).export(imported.function)

        assertTrue(exported.maxStack >= argCount)
        Analyzer(BasicInterpreter()).analyze("example/Test", exported)
    }

    @Test
    fun preservesPrimitiveArrayLoadOpcodesWhenArrayTypeIsErasedOnRoundTrip() {
        val cases = listOf(
            "[B" to Opcodes.BALOAD,
            "[C" to Opcodes.CALOAD,
            "[S" to Opcodes.SALOAD,
            "[I" to Opcodes.IALOAD
        )

        for ((descriptor, opcode) in cases) {
            val method = MethodNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "load$opcode",
                "($descriptor" + "I)I",
                null,
                null
            ).apply {
                instructions = InsnList().apply {
                    add(VarInsnNode(Opcodes.ALOAD, 0))
                    add(VarInsnNode(Opcodes.ILOAD, 1))
                    add(InsnNode(opcode))
                    add(InsnNode(Opcodes.IRETURN))
                }
                maxLocals = 2
                maxStack = 2
            }
            val imported = JvmIrImporter().import("example/Test", method)

            val exported = JvmIrExporter(imported.metadata).export(imported.function)
            val actualOpcode = exported.instructions.toArray()
                .map { it.opcode }
                .single { it in listOf(Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD, Opcodes.IALOAD) }

            Analyzer(BasicInterpreter()).analyze("example/Test", exported)
            assertEquals(opcode, actualOpcode, "$descriptor load opcode")
        }
    }

    @Test
    fun preservesPrimitiveArrayStoreOpcodesWhenArrayTypeIsErasedOnRoundTrip() {
        val cases = listOf(
            "[B" to Opcodes.BASTORE,
            "[C" to Opcodes.CASTORE,
            "[S" to Opcodes.SASTORE,
            "[I" to Opcodes.IASTORE
        )

        for ((descriptor, opcode) in cases) {
            val method = MethodNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "store$opcode",
                "($descriptor" + "II)V",
                null,
                null
            ).apply {
                instructions = InsnList().apply {
                    add(VarInsnNode(Opcodes.ALOAD, 0))
                    add(VarInsnNode(Opcodes.ILOAD, 1))
                    add(VarInsnNode(Opcodes.ILOAD, 2))
                    add(InsnNode(opcode))
                    add(InsnNode(Opcodes.RETURN))
                }
                maxLocals = 3
                maxStack = 3
            }
            val imported = JvmIrImporter().import("example/Test", method)

            val exported = JvmIrExporter(imported.metadata).export(imported.function)
            val actualOpcode = exported.instructions.toArray()
                .map { it.opcode }
                .single { it in listOf(Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE, Opcodes.IASTORE) }

            Analyzer(BasicInterpreter()).analyze("example/Test", exported)
            assertEquals(opcode, actualOpcode, "$descriptor store opcode")
        }
    }

    @Test
    fun preservesPrimitiveMultidimensionalArrayLoadsUpToFiveDimensionsOnRoundTrip() {
        for (dimensions in 2..5) {
            val arrayDescriptor = "[".repeat(dimensions) + "I"
            val method = MethodNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "get${dimensions}d",
                "($arrayDescriptor${"I".repeat(dimensions)})I",
                null,
                null
            ).apply {
                instructions = InsnList().apply {
                    add(VarInsnNode(Opcodes.ALOAD, 0))
                    for (index in 1 until dimensions) {
                        add(VarInsnNode(Opcodes.ILOAD, index))
                        add(InsnNode(Opcodes.AALOAD))
                    }
                    add(VarInsnNode(Opcodes.ILOAD, dimensions))
                    add(InsnNode(Opcodes.IALOAD))
                    add(InsnNode(Opcodes.IRETURN))
                }
                maxLocals = dimensions + 1
                maxStack = 2
            }
            val imported = JvmIrImporter().import("example/Test", method)

            val exported = JvmIrExporter(imported.metadata).export(imported.function)
            val actualArrayLoads = exported.instructions.toArray()
                .map { it.opcode }
                .filter { it == Opcodes.AALOAD || it == Opcodes.IALOAD }
            val expectedArrayLoads = List(dimensions - 1) { Opcodes.AALOAD } + Opcodes.IALOAD

            Analyzer(BasicInterpreter()).analyze("example/Test", exported)
            assertEquals(expectedArrayLoads, actualArrayLoads, "${dimensions}D array load sequence")
        }
    }
}
