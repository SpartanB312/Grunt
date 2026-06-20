package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.util.NATIVE_INCLUDED
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NativeJvmIntrinsicSemanticIntegrationTest {

    @Test
    fun primitiveIntrinsicsMatchJavaResultsForFullMatrixWhenToolchainExists() {
        val compiler = findHostCompiler()
        if (compiler == null) {
            println("Skipping intrinsic semantic E2E: no C++ compiler found on PATH")
            return
        }
        val includeRoot = resolveJniIncludeRoot(Path.of(System.getProperty("java.home")))
        val includeOs = includeRoot.resolve(NativePlatform.current().jniIncludeOs)
        if (!includeRoot.resolve("jni.h").exists() || !includeOs.exists()) {
            println("Skipping intrinsic semantic E2E: JNI headers not found under $includeRoot and $includeOs")
            return
        }

        val keys = NativeJvmIntrinsicRegistry.keys.sortedWith(
            compareBy<NativeJvmIntrinsicKey> { it.owner }
                .thenBy { it.name }
                .thenBy { it.desc }
        )
        keys.forEach { key ->
            val count = semanticCaseCount(key)
            assertTrue(count > 0, "${key.displayName} has no semantic cases")
            assertMinimumCoverage(key, count)
        }

        val candidateClass = classNode(
            "test/NativeIntrinsicCandidate",
            keys.map { intrinsicMethod(it).appendAnnotation(NATIVE_INCLUDED) }
        )
        val instance = instanceWith(candidateClass)

        context(instance) {
            NativePipelineRunner.run(
                NativePipelineConfig(
                    enabled = true,
                    workDir = createTempDirectory("grunteon-native-intrinsic").pathString,
                    splitSourceFiles = false,
                    compilerExecutable = compiler.absolutePath,
                    optimizationLevel = NativeOptimizationLevel.O0,
                    failOnCompileError = true,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true,
                    enablePrimitiveIntrinsics = true
                )
            )
        }

        val classes = instance.workRes.inputClassMap.values.associate { classNode ->
            classNode.name.replace('/', '.') to classNode.toBytes()
        }
        val loader = NativeIntrinsicClassLoader(
            parent = javaClass.classLoader,
            classes = classes,
            resources = instance.workRes.generatedResources.toMap()
        )
        val native = Class.forName("test.NativeIntrinsicCandidate", true, loader)

        for (key in keys) {
            val methodName = methodName(key)
            val parameterTypes = Type.getArgumentTypes(key.desc).map { it.toPrimitiveClass() }.toTypedArray()
            val nativeMethod = native.getDeclaredMethod(methodName, *parameterTypes)

            semanticCases(key).forEach { inputs ->
                val expected = expectedIntrinsicResult(key, inputs)
                val actual = invokeStatic(nativeMethod, inputs)
                assertIntrinsicResultEquals(key, inputs, expected, actual)
            }
        }
    }

    @Test
    fun ssaDirectIntegerDivRemMatchesJavaExceptionSemanticsWhenToolchainExists() {
        val compiler = findHostCompiler()
        if (compiler == null) {
            println("Skipping SSA div/rem semantic E2E: no C++ compiler found on PATH")
            return
        }
        val includeRoot = resolveJniIncludeRoot(Path.of(System.getProperty("java.home")))
        val includeOs = includeRoot.resolve(NativePlatform.current().jniIncludeOs)
        if (!includeRoot.resolve("jni.h").exists() || !includeOs.exists()) {
            println("Skipping SSA div/rem semantic E2E: JNI headers not found under $includeRoot and $includeOs")
            return
        }

        val candidateClass = classNode(
            "test/NativeSsaDivRemCandidate",
            listOf(
                intDivRemMethod("idiv", Opcodes.IDIV).appendAnnotation(NATIVE_INCLUDED),
                intDivRemMethod("irem", Opcodes.IREM).appendAnnotation(NATIVE_INCLUDED),
                longDivRemMethod("ldiv", Opcodes.LDIV).appendAnnotation(NATIVE_INCLUDED),
                longDivRemMethod("lrem", Opcodes.LREM).appendAnnotation(NATIVE_INCLUDED)
            )
        )
        val instance = instanceWith(candidateClass)

        context(instance) {
            NativePipelineRunner.run(
                NativePipelineConfig(
                    enabled = true,
                    workDir = createTempDirectory("grunteon-native-ssa-divrem").pathString,
                    splitSourceFiles = false,
                    compilerExecutable = compiler.absolutePath,
                    optimizationLevel = NativeOptimizationLevel.O0,
                    failOnCompileError = true,
                    failOnValidationError = true,
                    cleanNativeAnnotations = true
                )
            )
        }

        val transformedClass = instance.workRes.inputClassMap.getValue("test/NativeSsaDivRemCandidate")
        transformedClass.methods
            .filter { it.name in setOf("idiv", "irem", "ldiv", "lrem") }
            .forEach { method ->
                assertTrue(method.access and Opcodes.ACC_NATIVE != 0, "${method.name} was not nativeized")
            }

        val classes = instance.workRes.inputClassMap.values.associate { classNode ->
            classNode.name.replace('/', '.') to classNode.toBytes()
        }
        val loader = NativeIntrinsicClassLoader(
            parent = javaClass.classLoader,
            classes = classes,
            resources = instance.workRes.generatedResources.toMap()
        )
        val native = Class.forName("test.NativeSsaDivRemCandidate", true, loader)
        val idiv = native.getDeclaredMethod("idiv", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val irem = native.getDeclaredMethod("irem", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val ldiv = native.getDeclaredMethod("ldiv", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        val lrem = native.getDeclaredMethod("lrem", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)

        assertEquals(4, invokeStatic(idiv, arrayOf(8, 2)))
        assertEquals(-4, invokeStatic(idiv, arrayOf(8, -2)))
        assertEquals(Int.MIN_VALUE, invokeStatic(idiv, arrayOf(Int.MIN_VALUE, -1)))
        assertEquals(1, invokeStatic(irem, arrayOf(7, 3)))
        assertEquals(0, invokeStatic(irem, arrayOf(Int.MIN_VALUE, -1)))
        assertFailsWith<ArithmeticException> { invokeStatic(idiv, arrayOf(1, 0)) }
        assertFailsWith<ArithmeticException> { invokeStatic(irem, arrayOf(1, 0)) }

        assertEquals(4L, invokeStatic(ldiv, arrayOf(8L, 2L)))
        assertEquals(-4L, invokeStatic(ldiv, arrayOf(8L, -2L)))
        assertEquals(Long.MIN_VALUE, invokeStatic(ldiv, arrayOf(Long.MIN_VALUE, -1L)))
        assertEquals(1L, invokeStatic(lrem, arrayOf(7L, 3L)))
        assertEquals(0L, invokeStatic(lrem, arrayOf(Long.MIN_VALUE, -1L)))
        assertFailsWith<ArithmeticException> { invokeStatic(ldiv, arrayOf(1L, 0L)) }
        assertFailsWith<ArithmeticException> { invokeStatic(lrem, arrayOf(1L, 0L)) }
    }

    private fun expectedIntrinsicResult(key: NativeJvmIntrinsicKey, inputs: Array<Any>): Any {
        fun i(index: Int) = inputs[index] as Int
        fun j(index: Int) = inputs[index] as Long
        fun f(index: Int) = inputs[index] as Float
        fun d(index: Int) = inputs[index] as Double
        fun s(index: Int) = inputs[index] as Short
        fun b(index: Int) = inputs[index] as Byte
        fun z(index: Int) = inputs[index] as Boolean
        fun c(index: Int) = inputs[index] as Char

        return when ("${key.owner}.${key.name}${key.desc}") {
            "java/lang/Boolean.compare(ZZ)I" -> java.lang.Boolean.compare(z(0), z(1))
            "java/lang/Boolean.logicalAnd(ZZ)Z" -> java.lang.Boolean.logicalAnd(z(0), z(1))
            "java/lang/Boolean.logicalOr(ZZ)Z" -> java.lang.Boolean.logicalOr(z(0), z(1))
            "java/lang/Boolean.logicalXor(ZZ)Z" -> java.lang.Boolean.logicalXor(z(0), z(1))
            "java/lang/Boolean.hashCode(Z)I" -> java.lang.Boolean.hashCode(z(0))

            "java/lang/Byte.toUnsignedInt(B)I" -> java.lang.Byte.toUnsignedInt(b(0))
            "java/lang/Byte.toUnsignedLong(B)J" -> java.lang.Byte.toUnsignedLong(b(0))
            "java/lang/Byte.hashCode(B)I" -> java.lang.Byte.hashCode(b(0))
            "java/lang/Byte.compare(BB)I" -> java.lang.Byte.compare(b(0), b(1))
            "java/lang/Byte.compareUnsigned(BB)I" -> java.lang.Byte.compareUnsigned(b(0), b(1))

            "java/lang/Character.hashCode(C)I" -> java.lang.Character.hashCode(c(0))
            "java/lang/Character.compare(CC)I" -> java.lang.Character.compare(c(0), c(1))
            "java/lang/Character.reverseBytes(C)C" -> java.lang.Character.reverseBytes(c(0))
            "java/lang/Character.charCount(I)I" -> java.lang.Character.charCount(i(0))
            "java/lang/Character.isHighSurrogate(C)Z" -> java.lang.Character.isHighSurrogate(c(0))
            "java/lang/Character.isLowSurrogate(C)Z" -> java.lang.Character.isLowSurrogate(c(0))
            "java/lang/Character.isSurrogate(C)Z" -> java.lang.Character.isSurrogate(c(0))
            "java/lang/Character.isValidCodePoint(I)Z" -> java.lang.Character.isValidCodePoint(i(0))
            "java/lang/Character.isSupplementaryCodePoint(I)Z" -> java.lang.Character.isSupplementaryCodePoint(i(0))
            "java/lang/Character.toCodePoint(CC)I" -> java.lang.Character.toCodePoint(c(0), c(1))

            "java/lang/Double.doubleToRawLongBits(D)J" -> java.lang.Double.doubleToRawLongBits(d(0))
            "java/lang/Double.doubleToLongBits(D)J" -> java.lang.Double.doubleToLongBits(d(0))
            "java/lang/Double.longBitsToDouble(J)D" -> java.lang.Double.longBitsToDouble(j(0))
            "java/lang/Double.isNaN(D)Z" -> java.lang.Double.isNaN(d(0))
            "java/lang/Double.isInfinite(D)Z" -> java.lang.Double.isInfinite(d(0))
            "java/lang/Double.isFinite(D)Z" -> java.lang.Double.isFinite(d(0))
            "java/lang/Double.compare(DD)I" -> java.lang.Double.compare(d(0), d(1))
            "java/lang/Double.hashCode(D)I" -> java.lang.Double.hashCode(d(0))

            "java/lang/Float.floatToRawIntBits(F)I" -> java.lang.Float.floatToRawIntBits(f(0))
            "java/lang/Float.floatToIntBits(F)I" -> java.lang.Float.floatToIntBits(f(0))
            "java/lang/Float.intBitsToFloat(I)F" -> java.lang.Float.intBitsToFloat(i(0))
            "java/lang/Float.isNaN(F)Z" -> java.lang.Float.isNaN(f(0))
            "java/lang/Float.isInfinite(F)Z" -> java.lang.Float.isInfinite(f(0))
            "java/lang/Float.isFinite(F)Z" -> java.lang.Float.isFinite(f(0))
            "java/lang/Float.compare(FF)I" -> java.lang.Float.compare(f(0), f(1))
            "java/lang/Float.hashCode(F)I" -> java.lang.Float.hashCode(f(0))

            "java/lang/Integer.rotateLeft(II)I" -> java.lang.Integer.rotateLeft(i(0), i(1))
            "java/lang/Integer.rotateRight(II)I" -> java.lang.Integer.rotateRight(i(0), i(1))
            "java/lang/Integer.reverse(I)I" -> java.lang.Integer.reverse(i(0))
            "java/lang/Integer.reverseBytes(I)I" -> java.lang.Integer.reverseBytes(i(0))
            "java/lang/Integer.bitCount(I)I" -> java.lang.Integer.bitCount(i(0))
            "java/lang/Integer.numberOfLeadingZeros(I)I" -> java.lang.Integer.numberOfLeadingZeros(i(0))
            "java/lang/Integer.numberOfTrailingZeros(I)I" -> java.lang.Integer.numberOfTrailingZeros(i(0))
            "java/lang/Integer.highestOneBit(I)I" -> java.lang.Integer.highestOneBit(i(0))
            "java/lang/Integer.lowestOneBit(I)I" -> java.lang.Integer.lowestOneBit(i(0))
            "java/lang/Integer.signum(I)I" -> java.lang.Integer.signum(i(0))
            "java/lang/Integer.compare(II)I" -> java.lang.Integer.compare(i(0), i(1))
            "java/lang/Integer.compareUnsigned(II)I" -> java.lang.Integer.compareUnsigned(i(0), i(1))
            "java/lang/Integer.sum(II)I" -> java.lang.Integer.sum(i(0), i(1))
            "java/lang/Integer.max(II)I" -> java.lang.Integer.max(i(0), i(1))
            "java/lang/Integer.min(II)I" -> java.lang.Integer.min(i(0), i(1))
            "java/lang/Integer.hashCode(I)I" -> java.lang.Integer.hashCode(i(0))
            "java/lang/Integer.toUnsignedLong(I)J" -> java.lang.Integer.toUnsignedLong(i(0))

            "java/lang/Long.rotateLeft(JI)J" -> java.lang.Long.rotateLeft(j(0), i(1))
            "java/lang/Long.rotateRight(JI)J" -> java.lang.Long.rotateRight(j(0), i(1))
            "java/lang/Long.reverse(J)J" -> java.lang.Long.reverse(j(0))
            "java/lang/Long.reverseBytes(J)J" -> java.lang.Long.reverseBytes(j(0))
            "java/lang/Long.bitCount(J)I" -> java.lang.Long.bitCount(j(0))
            "java/lang/Long.numberOfLeadingZeros(J)I" -> java.lang.Long.numberOfLeadingZeros(j(0))
            "java/lang/Long.numberOfTrailingZeros(J)I" -> java.lang.Long.numberOfTrailingZeros(j(0))
            "java/lang/Long.highestOneBit(J)J" -> java.lang.Long.highestOneBit(j(0))
            "java/lang/Long.lowestOneBit(J)J" -> java.lang.Long.lowestOneBit(j(0))
            "java/lang/Long.signum(J)I" -> java.lang.Long.signum(j(0))
            "java/lang/Long.compare(JJ)I" -> java.lang.Long.compare(j(0), j(1))
            "java/lang/Long.compareUnsigned(JJ)I" -> java.lang.Long.compareUnsigned(j(0), j(1))
            "java/lang/Long.sum(JJ)J" -> java.lang.Long.sum(j(0), j(1))
            "java/lang/Long.max(JJ)J" -> java.lang.Long.max(j(0), j(1))
            "java/lang/Long.min(JJ)J" -> java.lang.Long.min(j(0), j(1))
            "java/lang/Long.hashCode(J)I" -> java.lang.Long.hashCode(j(0))

            "java/lang/Math.abs(I)I" -> java.lang.Math.abs(i(0))
            "java/lang/Math.abs(J)J" -> java.lang.Math.abs(j(0))
            "java/lang/Math.abs(F)F" -> java.lang.Math.abs(f(0))
            "java/lang/Math.abs(D)D" -> java.lang.Math.abs(d(0))
            "java/lang/Math.max(II)I" -> java.lang.Math.max(i(0), i(1))
            "java/lang/Math.max(JJ)J" -> java.lang.Math.max(j(0), j(1))
            "java/lang/Math.max(FF)F" -> expectedMathMax(f(0), f(1))
            "java/lang/Math.max(DD)D" -> expectedMathMax(d(0), d(1))
            "java/lang/Math.min(II)I" -> java.lang.Math.min(i(0), i(1))
            "java/lang/Math.min(JJ)J" -> java.lang.Math.min(j(0), j(1))
            "java/lang/Math.min(FF)F" -> expectedMathMin(f(0), f(1))
            "java/lang/Math.min(DD)D" -> expectedMathMin(d(0), d(1))

            "java/lang/Short.reverseBytes(S)S" -> java.lang.Short.reverseBytes(s(0))
            "java/lang/Short.toUnsignedInt(S)I" -> java.lang.Short.toUnsignedInt(s(0))
            "java/lang/Short.toUnsignedLong(S)J" -> java.lang.Short.toUnsignedLong(s(0))
            "java/lang/Short.hashCode(S)I" -> java.lang.Short.hashCode(s(0))
            "java/lang/Short.compare(SS)I" -> java.lang.Short.compare(s(0), s(1))
            "java/lang/Short.compareUnsigned(SS)I" -> java.lang.Short.compareUnsigned(s(0), s(1))

            "java/lang/StrictMath.abs(I)I" -> java.lang.StrictMath.abs(i(0))
            "java/lang/StrictMath.abs(J)J" -> java.lang.StrictMath.abs(j(0))
            "java/lang/StrictMath.abs(F)F" -> java.lang.StrictMath.abs(f(0))
            "java/lang/StrictMath.abs(D)D" -> java.lang.StrictMath.abs(d(0))
            "java/lang/StrictMath.max(II)I" -> java.lang.StrictMath.max(i(0), i(1))
            "java/lang/StrictMath.max(JJ)J" -> java.lang.StrictMath.max(j(0), j(1))
            "java/lang/StrictMath.max(FF)F" -> expectedMathMax(f(0), f(1))
            "java/lang/StrictMath.max(DD)D" -> expectedMathMax(d(0), d(1))
            "java/lang/StrictMath.min(II)I" -> java.lang.StrictMath.min(i(0), i(1))
            "java/lang/StrictMath.min(JJ)J" -> java.lang.StrictMath.min(j(0), j(1))
            "java/lang/StrictMath.min(FF)F" -> expectedMathMin(f(0), f(1))
            "java/lang/StrictMath.min(DD)D" -> expectedMathMin(d(0), d(1))

            else -> error("No direct semantic baseline for ${key.displayName}")
        }
    }

    private fun expectedMathMin(lhs: Float, rhs: Float): Float {
        if (java.lang.Float.isNaN(lhs)) return lhs
        if (java.lang.Float.isNaN(rhs)) return rhs
        if (lhs == 0.0f && rhs == 0.0f) {
            return if ((java.lang.Float.floatToRawIntBits(lhs) or java.lang.Float.floatToRawIntBits(rhs)) < 0) -0.0f else 0.0f
        }
        return if (lhs <= rhs) lhs else rhs
    }

    private fun expectedMathMax(lhs: Float, rhs: Float): Float {
        if (java.lang.Float.isNaN(lhs)) return lhs
        if (java.lang.Float.isNaN(rhs)) return rhs
        if (lhs == 0.0f && rhs == 0.0f) {
            return if ((java.lang.Float.floatToRawIntBits(lhs) and java.lang.Float.floatToRawIntBits(rhs)) < 0) -0.0f else 0.0f
        }
        return if (lhs >= rhs) lhs else rhs
    }

    private fun expectedMathMin(lhs: Double, rhs: Double): Double {
        if (java.lang.Double.isNaN(lhs)) return lhs
        if (java.lang.Double.isNaN(rhs)) return rhs
        if (lhs == 0.0 && rhs == 0.0) {
            return if ((java.lang.Double.doubleToRawLongBits(lhs) or java.lang.Double.doubleToRawLongBits(rhs)) < 0L) -0.0 else 0.0
        }
        return if (lhs <= rhs) lhs else rhs
    }

    private fun expectedMathMax(lhs: Double, rhs: Double): Double {
        if (java.lang.Double.isNaN(lhs)) return lhs
        if (java.lang.Double.isNaN(rhs)) return rhs
        if (lhs == 0.0 && rhs == 0.0) {
            return if ((java.lang.Double.doubleToRawLongBits(lhs) and java.lang.Double.doubleToRawLongBits(rhs)) < 0L) -0.0 else 0.0
        }
        return if (lhs >= rhs) lhs else rhs
    }

    private fun assertMinimumCoverage(key: NativeJvmIntrinsicKey, count: Int) {
        when {
            key.owner == "java/lang/Character" && key.name == "toCodePoint" ->
                assertTrue(count >= 1024 * 1024 + 8192, "${key.displayName} needs surrogate pair coverage")
            key.owner == "java/lang/Character" && key.name == "compare" ->
                assertTrue(count >= charEdges().size * charEdges().size + 8192, "${key.displayName} needs char pair coverage")
            key.owner == "java/lang/Character" && key.desc.startsWith("(C") && key.name != "toCodePoint" ->
                assertTrue(count >= 65536, "${key.displayName} needs exhaustive char coverage")
            key.owner == "java/lang/Character" && key.desc == "(I)I" || key.owner == "java/lang/Character" && key.desc == "(I)Z" ->
                assertTrue(count >= 8192, "${key.displayName} needs code point coverage")
            key.desc == "(F)I" || key.desc == "(F)Z" || key.desc == "(F)F" ->
                assertTrue(count >= 4096, "${key.displayName} needs float raw-bit coverage")
            key.desc == "(FF)I" || key.desc == "(FF)F" ->
                assertTrue(count >= floatEdgeBits().size * floatEdgeBits().size + 8192, "${key.displayName} needs float pair coverage")
            key.desc == "(D)J" || key.desc == "(D)Z" || key.desc == "(D)I" || key.desc == "(D)D" ->
                assertTrue(count >= 4096, "${key.displayName} needs double raw-bit coverage")
            key.desc == "(DD)I" || key.desc == "(DD)D" ->
                assertTrue(count >= doubleEdgeBits().size * doubleEdgeBits().size + 8192, "${key.displayName} needs double pair coverage")
            key.desc == "(I)F" ->
                assertTrue(count >= 4096, "${key.displayName} needs int raw-bit coverage")
            key.desc == "(J)D" ->
                assertTrue(count >= 4096, "${key.displayName} needs long raw-bit coverage")
            else -> when (key.desc) {
            "(I)I" -> assertTrue(count >= 4096, "${key.displayName} needs at least 4096 int cases")
            "(I)J" -> assertTrue(count >= 4096, "${key.displayName} needs at least 4096 int cases")
            "(II)I" -> {
                val minimum = if (key.name.startsWith("rotate")) 16 * 257 else intEdges().size * intEdges().size + 8192
                assertTrue(count >= minimum, "${key.displayName} needs at least $minimum int pair cases")
            }
            "(J)J", "(J)I" -> assertTrue(count >= 4096, "${key.displayName} needs at least 4096 long cases")
            "(JJ)J", "(JJ)I" -> assertTrue(count >= longEdges().size * longEdges().size + 8192, "${key.displayName} needs long pair cases")
            "(JI)J" -> assertTrue(count >= 16 * 257, "${key.displayName} needs full rotate distance coverage")
            "(ZZ)I", "(ZZ)Z" -> assertEquals(4, count, "${key.displayName} needs exhaustive boolean pairs")
            "(Z)I" -> assertEquals(2, count, "${key.displayName} needs exhaustive boolean cases")
            "(B)I", "(B)J" -> assertEquals(256, count, "${key.displayName} needs exhaustive byte cases")
            "(BB)I" -> assertEquals(256 * 256, count, "${key.displayName} needs exhaustive byte pairs")
            "(S)S", "(S)I", "(S)J" -> assertEquals(65536, count, "${key.displayName} needs exhaustive short cases")
            "(SS)I" -> assertTrue(count >= shortEdges().size * shortEdges().size + 8192, "${key.displayName} needs short pair cases")
            else -> error("No semantic coverage rule for ${key.displayName}")
            }
        }
    }

    private fun semanticCaseCount(key: NativeJvmIntrinsicKey): Int {
        return semanticCases(key).count()
    }

    private fun semanticCases(key: NativeJvmIntrinsicKey): Sequence<Array<Any>> {
        return when {
            key.owner == "java/lang/Float" && key.name == "intBitsToFloat" ->
                floatRawBits().asSequence().map { arrayOf<Any>(it) }
            key.desc == "(F)I" || key.desc == "(F)Z" || key.desc == "(F)F" ->
                floatValues().asSequence().map { arrayOf<Any>(it) }
            key.desc == "(FF)I" || key.desc == "(FF)F" ->
                floatPairs()
            key.owner == "java/lang/Double" && key.name == "longBitsToDouble" ->
                doubleRawBits().asSequence().map { arrayOf<Any>(it) }
            key.desc == "(D)J" || key.desc == "(D)Z" || key.desc == "(D)I" || key.desc == "(D)D" ->
                doubleValues().asSequence().map { arrayOf<Any>(it) }
            key.desc == "(DD)I" || key.desc == "(DD)D" ->
                doublePairs()
            key.owner == "java/lang/Character" && key.name == "toCodePoint" ->
                characterToCodePointCases()
            key.owner == "java/lang/Character" && key.desc == "(CC)I" ->
                charPairs()
            key.owner == "java/lang/Character" && key.desc.startsWith("(C") ->
                charValues().map { arrayOf<Any>(it) }
            key.owner == "java/lang/Character" && (key.desc == "(I)I" || key.desc == "(I)Z") ->
                codePointValues().asSequence().map { arrayOf<Any>(it) }
            else -> when (key.desc) {
            "(I)I", "(I)J" -> intValues(4096).asSequence().map { arrayOf<Any>(it) }
            "(II)I" -> if (key.name.startsWith("rotate")) {
                rotateIntCases()
            } else {
                intPairs().map { (a, b) -> arrayOf<Any>(a, b) }
            }
            "(J)J", "(J)I" -> longValues(4096).asSequence().map { arrayOf<Any>(it) }
            "(JJ)J", "(JJ)I" -> longPairs().map { (a, b) -> arrayOf<Any>(a, b) }
            "(JI)J" -> rotateLongCases()
            "(S)S", "(S)I", "(S)J" -> shortValues().map { arrayOf<Any>(it) }
            "(SS)I" -> shortPairs()
            "(B)I", "(B)J" -> byteValues().asSequence().map { arrayOf<Any>(it) }
            "(BB)I" -> bytePairs()
            "(Z)I" -> booleanValues().asSequence().map { arrayOf<Any>(it) }
            "(ZZ)I", "(ZZ)Z" -> booleanPairs()
            else -> error("No semantic cases for ${key.displayName}")
            }
        }
    }

    private fun intEdges(): List<Int> {
        return listOf(
            0,
            1,
            -1,
            2,
            -2,
            Int.MIN_VALUE,
            Int.MIN_VALUE + 1,
            Int.MAX_VALUE,
            Int.MAX_VALUE - 1,
            0x55555555,
            0xaaaaaaaa.toInt(),
            0x40000000,
            0x80000000.toInt(),
            0x0000ffff,
            0xffff0000.toInt(),
            0x12345678,
            -0x12345678
        )
    }

    private fun intValues(minimum: Int): List<Int> {
        val values = linkedSetOf<Int>()
        values += intEdges()
        var state = 0x13579bdf
        while (values.size < minimum) {
            state = state * 1664525 + 1013904223
            values += state
            values += state xor (state ushr 16)
        }
        return values.take(minimum)
    }

    private fun intPairs(): Sequence<Pair<Int, Int>> {
        return sequence {
            val values = intEdges()
            values.forEach { a -> values.forEach { b -> yield(a to b) } }
            var state = 0x2468ace1
            repeat(8192) {
                state = state * 1103515245 + 12345
                val a = state
                state = state * 1103515245 + 12345
                yield(a to state)
            }
        }
    }

    private fun rotateIntCases(): Sequence<Array<Any>> {
        val values = intValues(16)
        return sequence {
            values.forEach { value ->
                (-128..128).forEach { distance -> yield(arrayOf<Any>(value, distance)) }
            }
        }
    }

    private fun longEdges(): List<Long> {
        return listOf(
            0L,
            1L,
            -1L,
            2L,
            -2L,
            Long.MIN_VALUE,
            Long.MIN_VALUE + 1L,
            Long.MAX_VALUE,
            Long.MAX_VALUE - 1L,
            0x5555555555555555L,
            -0x5555555555555556L,
            0x4000000000000000L,
            Long.MIN_VALUE,
            0x00000000ffffffffL,
            -0x100000000L,
            0x123456789abcdefL,
            -0x123456789abcdefL
        )
    }

    private fun longValues(minimum: Int): List<Long> {
        val values = linkedSetOf<Long>()
        values += longEdges()
        var state = 0x123456789abcdefL
        while (values.size < minimum) {
            state = state * 6364136223846793005L + 1442695040888963407L
            values += state
            values += state xor (state ushr 33)
        }
        return values.take(minimum)
    }

    private fun longPairs(): Sequence<Pair<Long, Long>> {
        return sequence {
            val values = longEdges()
            values.forEach { a -> values.forEach { b -> yield(a to b) } }
            var state = 0x123456789abcdefL
            repeat(8192) {
                state = state * 6364136223846793005L + 1442695040888963407L
                val a = state
                state = state * 6364136223846793005L + 1442695040888963407L
                yield(a to state)
            }
        }
    }

    private fun rotateLongCases(): Sequence<Array<Any>> {
        val values = longValues(16)
        return sequence {
            values.forEach { value ->
                (-128..128).forEach { distance -> yield(arrayOf<Any>(value, distance)) }
            }
        }
    }

    private fun shortEdges(): List<Short> {
        return listOf(
            Short.MIN_VALUE,
            (Short.MIN_VALUE + 1).toShort(),
            (-257).toShort(),
            (-256).toShort(),
            (-129).toShort(),
            (-128).toShort(),
            (-1).toShort(),
            0.toShort(),
            1.toShort(),
            2.toShort(),
            127.toShort(),
            128.toShort(),
            255.toShort(),
            256.toShort(),
            (Short.MAX_VALUE - 1).toShort(),
            Short.MAX_VALUE
        )
    }

    private fun shortValues(): Sequence<Short> {
        return (Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()).asSequence().map { it.toShort() }
    }

    private fun shortPairs(): Sequence<Array<Any>> {
        return sequence {
            val values = shortEdges()
            values.forEach { a -> values.forEach { b -> yield(arrayOf<Any>(a, b)) } }
            var state = 0x6d2b79f5
            repeat(8192) {
                state = state * 1664525 + 1013904223
                val a = (state and 0xffff).toShort()
                state = state * 1664525 + 1013904223
                yield(arrayOf<Any>(a, (state and 0xffff).toShort()))
            }
        }
    }

    private fun byteValues(): List<Byte> {
        return (-128..127).map { it.toByte() }
    }

    private fun bytePairs(): Sequence<Array<Any>> {
        return sequence {
            byteValues().forEach { a ->
                byteValues().forEach { b -> yield(arrayOf<Any>(a, b)) }
            }
        }
    }

    private fun booleanValues(): List<Boolean> {
        return listOf(false, true)
    }

    private fun booleanPairs(): Sequence<Array<Any>> {
        return sequence {
            booleanValues().forEach { a ->
                booleanValues().forEach { b -> yield(arrayOf<Any>(a, b)) }
            }
        }
    }

    private fun u32(hex: String): Int = java.lang.Integer.parseUnsignedInt(hex, 16)

    private fun u64(hex: String): Long = java.lang.Long.parseUnsignedLong(hex, 16)

    private fun floatEdgeBits(): List<Int> {
        return listOf(
            u32("00000000"),
            u32("80000000"),
            u32("3f800000"),
            u32("bf800000"),
            u32("7f800000"),
            u32("ff800000"),
            u32("7fc00000"),
            u32("7fa00000"),
            u32("7f800001"),
            u32("ffc00000"),
            u32("00000001"),
            u32("80000001"),
            u32("007fffff"),
            u32("807fffff"),
            u32("7f7fffff"),
            u32("ff7fffff")
        )
    }

    private fun floatRawBits(minimum: Int = 4096): List<Int> {
        val values = linkedSetOf<Int>()
        values += floatEdgeBits()
        for (payload in 1..256) {
            values += 0x7f800000 or payload
            values += 0xff800000.toInt() or payload
            values += 0x7fc00000 or payload
            values += 0xffc00000.toInt() or payload
        }
        var state = 0x4752545f
        while (values.size < minimum) {
            state = state * 1664525 + 1013904223
            values += state
        }
        return values.take(minimum)
    }

    private fun floatValues(): List<Float> {
        return floatRawBits().map(java.lang.Float::intBitsToFloat)
    }

    private fun floatPairs(): Sequence<Array<Any>> {
        return sequence {
            val edges = floatEdgeBits().map(java.lang.Float::intBitsToFloat)
            edges.forEach { a -> edges.forEach { b -> yield(arrayOf<Any>(a, b)) } }
            val bits = floatRawBits(4096)
            var state = 0x1f123bb5
            repeat(8192) {
                state = state * 1664525 + 1013904223
                val a = java.lang.Float.intBitsToFloat(bits[(state ushr 1) % bits.size])
                state = state * 1664525 + 1013904223
                val b = java.lang.Float.intBitsToFloat(bits[(state ushr 1) % bits.size])
                yield(arrayOf<Any>(a, b))
            }
        }
    }

    private fun doubleEdgeBits(): List<Long> {
        return listOf(
            u64("0000000000000000"),
            u64("8000000000000000"),
            u64("3ff0000000000000"),
            u64("bff0000000000000"),
            u64("7ff0000000000000"),
            u64("fff0000000000000"),
            u64("7ff8000000000000"),
            u64("7ff4000000000000"),
            u64("7ff0000000000001"),
            u64("fff8000000000000"),
            u64("0000000000000001"),
            u64("8000000000000001"),
            u64("000fffffffffffff"),
            u64("800fffffffffffff"),
            u64("7fefffffffffffff"),
            u64("ffefffffffffffff")
        )
    }

    private fun doubleRawBits(minimum: Int = 4096): List<Long> {
        val values = linkedSetOf<Long>()
        values += doubleEdgeBits()
        for (payload in 1L..256L) {
            values += 0x7ff0000000000000L or payload
            values += u64("fff0000000000000") or payload
            values += 0x7ff8000000000000L or payload
            values += u64("fff8000000000000") or payload
        }
        var state = 0x4752545f494e5452L
        while (values.size < minimum) {
            state = state * 6364136223846793005L + 1442695040888963407L
            values += state
        }
        return values.take(minimum)
    }

    private fun doubleValues(): List<Double> {
        return doubleRawBits().map(java.lang.Double::longBitsToDouble)
    }

    private fun doublePairs(): Sequence<Array<Any>> {
        return sequence {
            val edges = doubleEdgeBits().map(java.lang.Double::longBitsToDouble)
            edges.forEach { a -> edges.forEach { b -> yield(arrayOf<Any>(a, b)) } }
            val bits = doubleRawBits(4096)
            var state = 0x6a09e667f3bcc909L
            repeat(8192) {
                state = state * 6364136223846793005L + 1442695040888963407L
                val a = java.lang.Double.longBitsToDouble(bits[((state ushr 1) % bits.size).toInt()])
                state = state * 6364136223846793005L + 1442695040888963407L
                val b = java.lang.Double.longBitsToDouble(bits[((state ushr 1) % bits.size).toInt()])
                yield(arrayOf<Any>(a, b))
            }
        }
    }

    private fun charEdges(): List<Char> {
        return listOf(
            0x0000,
            0x0001,
            0x007f,
            0x0080,
            0x00ff,
            0x0100,
            0xd7ff,
            0xd800,
            0xdbff,
            0xdc00,
            0xdfff,
            0xe000,
            0xfffe,
            0xffff
        ).map { it.toChar() }
    }

    private fun charValues(): Sequence<Char> {
        return (0..0xffff).asSequence().map { it.toChar() }
    }

    private fun charPairs(): Sequence<Array<Any>> {
        return sequence {
            val edges = charEdges()
            edges.forEach { a -> edges.forEach { b -> yield(arrayOf<Any>(a, b)) } }
            var state = 0x43b0d7e5
            repeat(8192) {
                state = state * 1664525 + 1013904223
                val a = (state and 0xffff).toChar()
                state = state * 1664525 + 1013904223
                yield(arrayOf<Any>(a, (state and 0xffff).toChar()))
            }
        }
    }

    private fun characterToCodePointCases(): Sequence<Array<Any>> {
        return sequence {
            for (high in 0xd800..0xdbff) {
                for (low in 0xdc00..0xdfff) {
                    yield(arrayOf<Any>(high.toChar(), low.toChar()))
                }
            }
            val edges = charEdges()
            edges.forEach { a -> edges.forEach { b -> yield(arrayOf<Any>(a, b)) } }
            var state = 0x10203040
            repeat(8192) {
                state = state * 1664525 + 1013904223
                val high = (state and 0xffff).toChar()
                state = state * 1664525 + 1013904223
                yield(arrayOf<Any>(high, (state and 0xffff).toChar()))
            }
        }
    }

    private fun codePointValues(): List<Int> {
        val values = linkedSetOf(
            Int.MIN_VALUE,
            -2,
            -1,
            0,
            1,
            2,
            0xd7fe,
            0xd7ff,
            0xd800,
            0xd801,
            0xdbfe,
            0xdbff,
            0xdc00,
            0xdc01,
            0xdffe,
            0xdfff,
            0xe000,
            0xe001,
            0xfffe,
            0xffff,
            0x10000,
            0x10001,
            0x10fffe,
            0x10ffff,
            0x110000,
            0x110001,
            Int.MAX_VALUE
        )
        var state = 0x5f3759df
        while (values.size < 8192) {
            state = state * 1664525 + 1013904223
            values += state
        }
        return values.toList()
    }

    private fun assertIntrinsicResultEquals(
        key: NativeJvmIntrinsicKey,
        inputs: Array<Any>,
        expected: Any?,
        actual: Any?
    ) {
        val message = "${key.displayName} inputs=${formatInputs(key, inputs)} java=$expected native=$actual"
        when (Type.getReturnType(key.desc).sort) {
            Type.FLOAT -> {
                val expectedBits = java.lang.Float.floatToRawIntBits(expected as Float)
                val actualBits = java.lang.Float.floatToRawIntBits(actual as Float)
                assertEquals(expectedBits, actualBits, "$message javaBits=${hex32(expectedBits)} nativeBits=${hex32(actualBits)}")
            }
            Type.DOUBLE -> {
                val expectedBits = java.lang.Double.doubleToRawLongBits(expected as Double)
                val actualBits = java.lang.Double.doubleToRawLongBits(actual as Double)
                assertEquals(expectedBits, actualBits, "$message javaBits=${hex64(expectedBits)} nativeBits=${hex64(actualBits)}")
            }
            Type.CHAR -> {
                assertEquals((expected as Char).code, (actual as Char).code, message)
            }
            else -> assertEquals(expected, actual, message)
        }
    }

    private fun formatInputs(key: NativeJvmIntrinsicKey, inputs: Array<Any>): String {
        val argumentTypes = Type.getArgumentTypes(key.desc)
        return inputs.mapIndexed { index, input ->
            when (argumentTypes[index].sort) {
                Type.FLOAT -> {
                    val value = input as Float
                    "$value/${hex32(java.lang.Float.floatToRawIntBits(value))}"
                }
                Type.DOUBLE -> {
                    val value = input as Double
                    "$value/${hex64(java.lang.Double.doubleToRawLongBits(value))}"
                }
                Type.CHAR -> {
                    val value = input as Char
                    "'$value'/${hex32(value.code)}"
                }
                else -> input.toString()
            }
        }.joinToString(prefix = "[", postfix = "]")
    }

    private fun hex32(value: Int): String {
        return "0x" + java.lang.Integer.toUnsignedString(value, 16).padStart(8, '0')
    }

    private fun hex64(value: Long): String {
        return "0x" + java.lang.Long.toUnsignedString(value, 16).padStart(16, '0')
    }

    private fun intrinsicMethod(key: NativeJvmIntrinsicKey): MethodNode {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, methodName(key), key.desc, null, null)
        var local = 0
        var maxStack = 0
        Type.getArgumentTypes(key.desc).forEach { argument ->
            method.instructions.add(VarInsnNode(argument.loadOpcode(), local))
            local += argument.size
            maxStack += argument.size
        }
        method.instructions.add(MethodInsnNode(key.opcode, key.owner, key.name, key.desc, false))
        method.instructions.add(InsnNode(Type.getReturnType(key.desc).returnOpcode()))
        method.maxStack = maxOf(maxStack, Type.getReturnType(key.desc).size, 1)
        method.maxLocals = maxOf(local, 1)
        return method
    }

    private fun intDivRemMethod(name: String, opcode: Int): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, "(II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(opcode))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun longDivRemMethod(name: String, opcode: Int): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, "(JJ)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 2))
            instructions.add(InsnNode(opcode))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 4
            maxLocals = 4
        }
    }

    private fun methodName(key: NativeJvmIntrinsicKey): String {
        return "m_" + (key.owner + "_" + key.name + "_" + key.desc)
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
    }

    private fun classNode(name: String, methods: List<MethodNode>): ClassNode {
        return ClassNode().apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
            this.name = name
            superName = "java/lang/Object"
            this.methods.addAll(methods)
        }
    }

    private fun instanceWith(vararg classes: ClassNode): Grunteon {
        val root = createTempDirectory("grunteon-native-intrinsic-input")
        classes.forEach { writeClass(root, it) }
        return Grunteon.create(
            ObfConfig(
                globalConfig = GlobalConfig(
                    input = root.pathString,
                    output = null,
                    dumpMappings = false,
                    exclusions = emptyList(),
                    mixinExclusions = emptyList(),
                    missingCheck = false
                )
            )
        )
    }

    private fun writeClass(root: Path, classNode: ClassNode) {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        val file = root.resolve("${classNode.name}.class")
        file.parent.createDirectories()
        file.writeBytes(writer.toByteArray())
    }

    private fun ClassNode.toBytes(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        accept(writer)
        return writer.toByteArray()
    }

    private fun Type.loadOpcode(): Int {
        return when (sort) {
            Type.LONG -> Opcodes.LLOAD
            Type.FLOAT -> Opcodes.FLOAD
            Type.DOUBLE -> Opcodes.DLOAD
            Type.OBJECT,
            Type.ARRAY -> Opcodes.ALOAD
            else -> Opcodes.ILOAD
        }
    }

    private fun Type.returnOpcode(): Int {
        return when (sort) {
            Type.VOID -> Opcodes.RETURN
            Type.LONG -> Opcodes.LRETURN
            Type.FLOAT -> Opcodes.FRETURN
            Type.DOUBLE -> Opcodes.DRETURN
            Type.OBJECT,
            Type.ARRAY -> Opcodes.ARETURN
            else -> Opcodes.IRETURN
        }
    }

    private fun Type.toPrimitiveClass(): Class<*> {
        return when (sort) {
            Type.BOOLEAN -> Boolean::class.javaPrimitiveType!!
            Type.BYTE -> Byte::class.javaPrimitiveType!!
            Type.CHAR -> Char::class.javaPrimitiveType!!
            Type.SHORT -> Short::class.javaPrimitiveType!!
            Type.INT -> Int::class.javaPrimitiveType!!
            Type.LONG -> Long::class.javaPrimitiveType!!
            Type.FLOAT -> Float::class.javaPrimitiveType!!
            Type.DOUBLE -> Double::class.javaPrimitiveType!!
            else -> error("Unsupported intrinsic parameter type $descriptor")
        }
    }

    private fun invokeStatic(method: Method, inputs: Array<Any>): Any? {
        return try {
            method.invoke(null, *inputs)
        } catch (throwable: InvocationTargetException) {
            throw throwable.targetException
        }
    }

    private fun findHostCompiler(): File? {
        return sequenceOf("clang++", "g++", "clang-cl", "cl")
            .mapNotNull(::findExecutable)
            .firstOrNull()
    }

    private fun findExecutable(name: String): File? {
        val path = System.getenv("PATH") ?: return null
        val extensions = if (File.separatorChar == '\\') {
            val pathext = System.getenv("PATHEXT")
                ?.split(File.pathSeparatorChar)
                ?.filter { it.isNotBlank() }
                ?: listOf(".COM", ".EXE", ".BAT", ".CMD")
            listOf("") + pathext
        } else {
            listOf("")
        }
        return path
            .split(File.pathSeparatorChar)
            .asSequence()
            .flatMap { dir -> extensions.asSequence().map { ext -> File(dir, name + ext) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    private fun resolveJniIncludeRoot(javaHome: Path): Path {
        val direct = javaHome.resolve("include")
        if (direct.exists()) return direct
        return javaHome.parent?.resolve("include") ?: direct
    }

    private class NativeIntrinsicClassLoader(
        parent: ClassLoader,
        private val classes: Map<String, ByteArray>,
        private val resources: Map<String, ByteArray>
    ) : ClassLoader(parent) {

        override fun findClass(name: String): Class<*> {
            val bytes = classes[name] ?: return super.findClass(name)
            return defineClass(name, bytes, 0, bytes.size)
        }

        override fun getResourceAsStream(name: String): InputStream? {
            resources[name]?.let { return ByteArrayInputStream(it) }
            return super.getResourceAsStream(name)
        }
    }
}
