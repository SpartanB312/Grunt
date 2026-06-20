package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmIrImporter
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmSupportAnalyzer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeJvmIntrinsicRegistryTest {

    @Test
    fun registryContainsExactlyPriorityOneWhitelist() {
        assertEquals(expectedPriorityOneKeys(), NativeJvmIntrinsicRegistry.keys)
    }

    @Test
    fun emitsEveryWhitelistedIntrinsicWithoutJniStaticCallPath() {
        NativeJvmIntrinsicRegistry.keys.forEach { key ->
            val source = translate(intrinsicMethod(key))

            assertContains(source, "intrinsic ${key.owner}.${key.name}${key.desc}", message = key.displayName)
            assertFalse(source.contains("CallStatic"), key.displayName)
            assertFalse(source.contains("grt_get_method_id"), key.displayName)
            assertFalse(source.contains("jvalue args_"), key.displayName)
        }
    }

    @Test
    fun primitiveIntrinsicStatsRecordsGeneratedHits() {
        val stats = NativeJvmIntrinsicStats()
        val key = NativeJvmIntrinsicKey(Opcodes.INVOKESTATIC, "java/lang/Integer", "bitCount", "(I)I")

        translate(intrinsicMethod(key), stats = stats)

        assertEquals(1, stats.total)
        assertEquals(1, stats.byKey[key])
    }

    @Test
    fun nonWhitelistedInvokeShapesFallBackToJniCallPath() {
        val wrongDescriptor = translate(
            invokeMethod(
                methodName = "wrongDescriptor",
                methodDesc = "(J)I",
                opcode = Opcodes.INVOKESTATIC,
                owner = "java/lang/Integer",
                name = "bitCount",
                invokeDesc = "(J)I"
            )
        )
        assertContains(wrongDescriptor, "CallStaticIntMethodA")
        assertContains(wrongDescriptor, "grt_get_method_id")

        val nonStaticOpcode = translate(
            invokeMethod(
                methodName = "nonStaticOpcode",
                methodDesc = "(Ljava/lang/Integer;I)I",
                opcode = Opcodes.INVOKEVIRTUAL,
                owner = "java/lang/Integer",
                name = "bitCount",
                invokeDesc = "(I)I"
            )
        )
        assertContains(nonStaticOpcode, "CallIntMethodA")
        assertContains(nonStaticOpcode, "grt_get_method_id")

        val postponedDivideUnsigned = translate(
            invokeMethod(
                methodName = "postponedDivideUnsigned",
                methodDesc = "(II)I",
                opcode = Opcodes.INVOKESTATIC,
                owner = "java/lang/Integer",
                name = "divideUnsigned",
                invokeDesc = "(II)I"
            )
        )
        assertContains(postponedDivideUnsigned, "CallStaticIntMethodA")
        assertContains(postponedDivideUnsigned, "grt_get_method_id")

        val postponedLongDivideUnsigned = translate(
            invokeMethod(
                methodName = "postponedLongDivideUnsigned",
                methodDesc = "(JJ)J",
                opcode = Opcodes.INVOKESTATIC,
                owner = "java/lang/Long",
                name = "divideUnsigned",
                invokeDesc = "(JJ)J"
            )
        )
        assertContains(postponedLongDivideUnsigned, "CallStaticLongMethodA")
        assertContains(postponedLongDivideUnsigned, "grt_get_method_id")

        val postponedMathAddExact = translate(
            invokeMethod(
                methodName = "postponedMathAddExact",
                methodDesc = "(II)I",
                opcode = Opcodes.INVOKESTATIC,
                owner = "java/lang/Math",
                name = "addExact",
                invokeDesc = "(II)I"
            )
        )
        assertContains(postponedMathAddExact, "CallStaticIntMethodA")
        assertContains(postponedMathAddExact, "grt_get_method_id")

        val postponedObjectsRequireNonNull = translate(
            invokeMethod(
                methodName = "postponedObjectsRequireNonNull",
                methodDesc = "(Ljava/lang/Object;)Ljava/lang/Object;",
                opcode = Opcodes.INVOKESTATIC,
                owner = "java/util/Objects",
                name = "requireNonNull",
                invokeDesc = "(Ljava/lang/Object;)Ljava/lang/Object;"
            )
        )
        assertContains(postponedObjectsRequireNonNull, "CallStaticObjectMethodA")
        assertContains(postponedObjectsRequireNonNull, "grt_get_method_id")

        val postponedSystemArrayCopy = translate(
            invokeMethod(
                methodName = "postponedSystemArrayCopy",
                methodDesc = "(Ljava/lang/Object;ILjava/lang/Object;II)V",
                opcode = Opcodes.INVOKESTATIC,
                owner = "java/lang/System",
                name = "arraycopy",
                invokeDesc = "(Ljava/lang/Object;ILjava/lang/Object;II)V"
            )
        )
        assertContains(postponedSystemArrayCopy, "CallStaticVoidMethodA")
        assertContains(postponedSystemArrayCopy, "grt_get_method_id")

        val postponedStringLength = translate(
            invokeMethod(
                methodName = "postponedStringLength",
                methodDesc = "(Ljava/lang/String;)I",
                opcode = Opcodes.INVOKEVIRTUAL,
                owner = "java/lang/String",
                name = "length",
                invokeDesc = "()I"
            )
        )
        assertContains(postponedStringLength, "CallIntMethodA")
        assertContains(postponedStringLength, "grt_get_method_id")

        val postponedIntegerIntValue = translate(
            invokeMethod(
                methodName = "postponedIntegerIntValue",
                methodDesc = "(Ljava/lang/Integer;)I",
                opcode = Opcodes.INVOKEVIRTUAL,
                owner = "java/lang/Integer",
                name = "intValue",
                invokeDesc = "()I"
            )
        )
        assertContains(postponedIntegerIntValue, "CallIntMethodA")
        assertContains(postponedIntegerIntValue, "grt_get_method_id")

        val postponedUnsafeGetInt = translate(
            invokeMethod(
                methodName = "postponedUnsafeGetInt",
                methodDesc = "(Lsun/misc/Unsafe;Ljava/lang/Object;J)I",
                opcode = Opcodes.INVOKEVIRTUAL,
                owner = "sun/misc/Unsafe",
                name = "getInt",
                invokeDesc = "(Ljava/lang/Object;J)I"
            )
        )
        assertContains(postponedUnsafeGetInt, "CallIntMethodA")
        assertContains(postponedUnsafeGetInt, "grt_get_method_id")
    }

    @Test
    fun configFlagCanDisablePrimitiveIntrinsics() {
        val key = NativeJvmIntrinsicKey(Opcodes.INVOKESTATIC, "java/lang/Integer", "bitCount", "(I)I")
        val source = translate(intrinsicMethod(key), enablePrimitiveIntrinsics = false)

        assertContains(source, "CallStaticIntMethodA")
        assertContains(source, "grt_get_method_id")
        assertFalse(source.contains("intrinsic java/lang/Integer.bitCount(I)I"))
    }

    private fun expectedPriorityOneKeys(): Set<NativeJvmIntrinsicKey> {
        return buildSet {
            fun static(owner: String, name: String, desc: String) {
                add(NativeJvmIntrinsicKey(Opcodes.INVOKESTATIC, owner, name, desc))
            }

            static("java/lang/Math", "abs", "(I)I")
            static("java/lang/Math", "abs", "(J)J")
            static("java/lang/Math", "max", "(II)I")
            static("java/lang/Math", "max", "(JJ)J")
            static("java/lang/Math", "min", "(II)I")
            static("java/lang/Math", "min", "(JJ)J")
            static("java/lang/Math", "abs", "(F)F")
            static("java/lang/Math", "abs", "(D)D")
            static("java/lang/Math", "min", "(FF)F")
            static("java/lang/Math", "max", "(FF)F")
            static("java/lang/Math", "min", "(DD)D")
            static("java/lang/Math", "max", "(DD)D")

            static("java/lang/StrictMath", "abs", "(I)I")
            static("java/lang/StrictMath", "abs", "(J)J")
            static("java/lang/StrictMath", "max", "(II)I")
            static("java/lang/StrictMath", "max", "(JJ)J")
            static("java/lang/StrictMath", "min", "(II)I")
            static("java/lang/StrictMath", "min", "(JJ)J")
            static("java/lang/StrictMath", "abs", "(F)F")
            static("java/lang/StrictMath", "abs", "(D)D")
            static("java/lang/StrictMath", "min", "(FF)F")
            static("java/lang/StrictMath", "max", "(FF)F")
            static("java/lang/StrictMath", "min", "(DD)D")
            static("java/lang/StrictMath", "max", "(DD)D")

            listOf(
                "rotateLeft" to "(II)I",
                "rotateRight" to "(II)I",
                "reverse" to "(I)I",
                "reverseBytes" to "(I)I",
                "bitCount" to "(I)I",
                "numberOfLeadingZeros" to "(I)I",
                "numberOfTrailingZeros" to "(I)I",
                "highestOneBit" to "(I)I",
                "lowestOneBit" to "(I)I",
                "signum" to "(I)I",
                "compare" to "(II)I",
                "compareUnsigned" to "(II)I",
                "sum" to "(II)I",
                "max" to "(II)I",
                "min" to "(II)I",
                "hashCode" to "(I)I",
                "toUnsignedLong" to "(I)J"
            ).forEach { (name, desc) -> static("java/lang/Integer", name, desc) }

            listOf(
                "rotateLeft" to "(JI)J",
                "rotateRight" to "(JI)J",
                "reverse" to "(J)J",
                "reverseBytes" to "(J)J",
                "bitCount" to "(J)I",
                "numberOfLeadingZeros" to "(J)I",
                "numberOfTrailingZeros" to "(J)I",
                "highestOneBit" to "(J)J",
                "lowestOneBit" to "(J)J",
                "signum" to "(J)I",
                "compare" to "(JJ)I",
                "compareUnsigned" to "(JJ)I",
                "sum" to "(JJ)J",
                "max" to "(JJ)J",
                "min" to "(JJ)J",
                "hashCode" to "(J)I"
            ).forEach { (name, desc) -> static("java/lang/Long", name, desc) }

            listOf(
                "floatToRawIntBits" to "(F)I",
                "floatToIntBits" to "(F)I",
                "intBitsToFloat" to "(I)F",
                "isNaN" to "(F)Z",
                "isInfinite" to "(F)Z",
                "isFinite" to "(F)Z",
                "compare" to "(FF)I",
                "hashCode" to "(F)I"
            ).forEach { (name, desc) -> static("java/lang/Float", name, desc) }

            listOf(
                "doubleToRawLongBits" to "(D)J",
                "doubleToLongBits" to "(D)J",
                "longBitsToDouble" to "(J)D",
                "isNaN" to "(D)Z",
                "isInfinite" to "(D)Z",
                "isFinite" to "(D)Z",
                "compare" to "(DD)I",
                "hashCode" to "(D)I"
            ).forEach { (name, desc) -> static("java/lang/Double", name, desc) }

            static("java/lang/Short", "reverseBytes", "(S)S")
            static("java/lang/Short", "toUnsignedInt", "(S)I")
            static("java/lang/Short", "toUnsignedLong", "(S)J")
            static("java/lang/Short", "hashCode", "(S)I")
            static("java/lang/Short", "compare", "(SS)I")
            static("java/lang/Short", "compareUnsigned", "(SS)I")
            static("java/lang/Byte", "toUnsignedInt", "(B)I")
            static("java/lang/Byte", "toUnsignedLong", "(B)J")
            static("java/lang/Byte", "hashCode", "(B)I")
            static("java/lang/Byte", "compare", "(BB)I")
            static("java/lang/Byte", "compareUnsigned", "(BB)I")
            static("java/lang/Boolean", "compare", "(ZZ)I")
            static("java/lang/Boolean", "logicalAnd", "(ZZ)Z")
            static("java/lang/Boolean", "logicalOr", "(ZZ)Z")
            static("java/lang/Boolean", "logicalXor", "(ZZ)Z")
            static("java/lang/Boolean", "hashCode", "(Z)I")

            static("java/lang/Character", "hashCode", "(C)I")
            static("java/lang/Character", "compare", "(CC)I")
            static("java/lang/Character", "reverseBytes", "(C)C")
            static("java/lang/Character", "charCount", "(I)I")
            static("java/lang/Character", "isHighSurrogate", "(C)Z")
            static("java/lang/Character", "isLowSurrogate", "(C)Z")
            static("java/lang/Character", "isSurrogate", "(C)Z")
            static("java/lang/Character", "isValidCodePoint", "(I)Z")
            static("java/lang/Character", "isSupplementaryCodePoint", "(I)Z")
            static("java/lang/Character", "toCodePoint", "(CC)I")
        }
    }

    private fun intrinsicMethod(key: NativeJvmIntrinsicKey): MethodNode {
        require(key.opcode == Opcodes.INVOKESTATIC)
        return invokeMethod(
            methodName = methodName(key),
            methodDesc = key.desc,
            opcode = key.opcode,
            owner = key.owner,
            name = key.name,
            invokeDesc = key.desc
        )
    }

    private fun invokeMethod(
        methodName: String,
        methodDesc: String,
        opcode: Int,
        owner: String,
        name: String,
        invokeDesc: String
    ): MethodNode {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, methodName, methodDesc, null, null)
        var local = 0
        Type.getArgumentTypes(methodDesc).forEach { argument ->
            method.instructions.add(VarInsnNode(loadOpcode(argument), local))
            local += argument.size
        }
        method.instructions.add(MethodInsnNode(opcode, owner, name, invokeDesc, false))
        method.instructions.add(InsnNode(returnOpcode(Type.getReturnType(invokeDesc))))
        method.maxLocals = maxOf(local, 1)
        method.maxStack = maxOf(local + Type.getReturnType(invokeDesc).size, 1)
        return method
    }

    private fun translate(
        method: MethodNode,
        enablePrimitiveIntrinsics: Boolean = true,
        stats: NativeJvmIntrinsicStats? = null
    ): String {
        return NativeJvmCppMethodTranslator.translate(
            validated(method),
            "grt_test",
            enablePrimitiveIntrinsics = enablePrimitiveIntrinsics,
            intrinsicStats = stats
        )
    }

    private fun validated(method: MethodNode): NativeValidatedMethod {
        val className = "test/IntrinsicRegistry"
        val ir = NativeJvmIrImporter.import(className, method)
        val support = NativeJvmSupportAnalyzer.analyze(ir)
        assertTrue(support.isFullJvmLoweringReady)
        return NativeValidatedMethod(
            candidate = NativeCandidate(
                classNode = ClassNode().apply {
                    version = Opcodes.V1_8
                    access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
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

    private fun methodName(key: NativeJvmIntrinsicKey): String {
        return (key.owner + "_" + key.name + "_" + key.desc)
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
    }

    private fun loadOpcode(type: Type): Int {
        return when (type.sort) {
            Type.LONG -> Opcodes.LLOAD
            Type.FLOAT -> Opcodes.FLOAD
            Type.DOUBLE -> Opcodes.DLOAD
            Type.OBJECT,
            Type.ARRAY -> Opcodes.ALOAD
            else -> Opcodes.ILOAD
        }
    }

    private fun returnOpcode(type: Type): Int {
        return when (type.sort) {
            Type.VOID -> Opcodes.RETURN
            Type.LONG -> Opcodes.LRETURN
            Type.FLOAT -> Opcodes.FRETURN
            Type.DOUBLE -> Opcodes.DRETURN
            Type.OBJECT,
            Type.ARRAY -> Opcodes.ARETURN
            else -> Opcodes.IRETURN
        }
    }
}
