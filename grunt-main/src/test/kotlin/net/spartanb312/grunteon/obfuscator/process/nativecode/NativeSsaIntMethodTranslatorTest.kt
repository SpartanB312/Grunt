package net.spartanb312.grunteon.obfuscator.process.nativecode

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.VarInsnNode

class NativeSsaIntMethodTranslatorTest {

    @Test
    fun validatorPrefersSsaPrimitiveIntForStraightLineIntHelpers() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "add", "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaAdd", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaPrimitive, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "uint32_t v")
        assertContains(source, "static_cast<uint32_t>(arg0)")
        assertContains(source, "static_cast<uint32_t>(arg1)")
        assertContains(source, "return grt_i32(")
    }

    @Test
    fun ssaPrimitiveIntLowersSignedShiftWithoutNativeSignedRightShift() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "shift", "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.ISHR))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaShift", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaPrimitive, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "grt_ishr32_bits")
        assertContains(source, "return grt_i32(")
    }

    @Test
    fun ssaPrimitiveIntLowersSimpleBranchWithBlockArgs() {
        val nonNegative = LabelNode()
        val end = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "sign", "(I)I", null, null).apply {
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

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaBranch", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaPrimitive, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "L_SSA_")
        assertContains(source, "if ((")
        assertContains(source, "edge_")
        assertContains(source, "barg_")
        assertContains(source, "return grt_i32(")
    }

    @Test
    fun ssaPrimitiveIntLowersTableSwitch() {
        val one = LabelNode()
        val two = LabelNode()
        val default = LabelNode()
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "switcher", "(I)I", null, null).apply {
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

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaSwitch", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaPrimitive, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "switch (")
        assertContains(source, "case 0x00000001U")
        assertContains(source, "case 0x00000002U")
        assertContains(source, "default:")
        assertContains(source, "return grt_i32(")
    }

    @Test
    fun ssaPrimitiveIntLowersIntegerRotateLeftIntrinsic() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "rotate", "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Integer",
                "rotateLeft",
                "(II)I",
                false
            ))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaRotate", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaPrimitive, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "grt_rotl32")
    }

    @Test
    fun ssaPrimitiveLowersLongArithmeticAndReturn() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "mixLong", "(JJ)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 2))
            instructions.add(InsnNode(Opcodes.LADD))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(IntInsnNode(Opcodes.BIPUSH, 7))
            instructions.add(InsnNode(Opcodes.LSHR))
            instructions.add(InsnNode(Opcodes.LXOR))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 6
            maxLocals = 4
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaLong", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaPrimitive, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "static jlong JNICALL")
        assertContains(source, "uint64_t v")
        assertContains(source, "static_cast<uint64_t>(arg0)")
        assertContains(source, "grt_lshr64_bits")
        assertContains(source, "return grt_i64(")
    }

    @Test
    fun ssaPrimitiveLowersFloatArithmeticAndReturn() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "mixFloat", "(FF)F", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.FLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.FLOAD, 1))
            instructions.add(InsnNode(Opcodes.FREM))
            instructions.add(LdcInsnNode(2.5f))
            instructions.add(InsnNode(Opcodes.FADD))
            instructions.add(InsnNode(Opcodes.FRETURN))
            maxStack = 2
            maxLocals = 2
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaFloat", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaPrimitive, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "static jfloat JNICALL")
        assertContains(source, "jfloat v")
        assertContains(source, "std::fmod")
        assertContains(source, "return static_cast<jfloat>(")
    }

    @Test
    fun ssaPrimitiveLowersDoubleCompareIntrinsic() {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "compareDouble", "(DD)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.DLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.DLOAD, 2))
            instructions.add(InsnNode(Opcodes.DCMPG))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 4
            maxLocals = 4
        }

        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/SsaDoubleCompare", method)),
            NativeBackend.Cpp
        )

        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaPrimitive, accepted.single().lowering)

        val source = NativeCppBackend.generate(
            methods = accepted,
            config = NativePipelineConfig(enabled = true),
            classExists = { false }
        ).sourceText

        assertContains(source, "static jint JNICALL")
        assertContains(source, "std::isnan")
        assertContains(source, "0xffffffffU")
        assertContains(source, "return grt_i32(")
    }

    private fun candidate(className: String, method: MethodNode): NativeCandidate {
        return NativeCandidate(
            classNode = org.objectweb.asm.tree.ClassNode().apply {
                version = Opcodes.V1_8
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
                name = className
                superName = "java/lang/Object"
                methods.add(method)
            },
            methodNode = method,
            source = NativeCandidateSource.MethodAnnotation
        )
    }
}
