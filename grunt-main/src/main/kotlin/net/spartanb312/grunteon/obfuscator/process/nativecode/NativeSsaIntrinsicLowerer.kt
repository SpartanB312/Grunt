package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunt.ir.ssa.core.SSACallInstruction
import net.spartanb312.grunt.ir.ssa.core.SSAExternalFunctionRef
import net.spartanb312.grunt.ir.ssa.jvm.JvmSSAMetadata
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

private typealias NativeSsaIntrinsicEmitter = (List<String>) -> String

internal object NativeSsaIntrinsicLowerer {
    private val emitters: Map<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter> = buildMap {
        math()
        strictMath()
        integer()
        long()
        float()
        double()
        short()
        byte()
        boolean()
        character()
    }

    val supportedKeys: Set<NativeJvmIntrinsicKey>
        get() = emitters.keys

    init {
        val missing = NativeJvmIntrinsicRegistry.keys - emitters.keys
        val extra = emitters.keys - NativeJvmIntrinsicRegistry.keys
        require(missing.isEmpty()) {
            "SSA intrinsic lowerer is missing registry keys: ${missing.joinToString { it.displayName }}"
        }
        require(extra.isEmpty()) {
            "SSA intrinsic lowerer has non-registry keys: ${extra.joinToString { it.displayName }}"
        }
    }

    fun key(call: SSACallInstruction, metadata: JvmSSAMetadata): NativeJvmIntrinsicKey? {
        val target = call.target as? SSAExternalFunctionRef ?: return null
        val method = metadata.methods[target.ref] ?: return null
        return NativeJvmIntrinsicKey(method.opcode, method.owner, method.name, method.desc)
    }

    fun isSupportedCall(call: SSACallInstruction, metadata: JvmSSAMetadata): Boolean {
        return key(call, metadata) in emitters
    }

    fun emit(
        call: SSACallInstruction,
        args: List<String>,
        metadata: JvmSSAMetadata,
        stats: NativeJvmIntrinsicStats?
    ): String {
        val key = key(call, metadata) ?: unsupported("SSA call target is not backed by JVM method metadata")
        val emitter = emitters[key] ?: unsupported("unsupported SSA intrinsic call ${key.displayName}")
        val expectedArgs = Type.getArgumentTypes(key.desc).size
        if (args.size != expectedArgs) {
            unsupported("SSA intrinsic argument count mismatch for ${key.displayName}: expected $expectedArgs, got ${args.size}")
        }
        stats?.record(key)
        return emitter(args)
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.putStatic(
        owner: String,
        name: String,
        desc: String,
        emitter: NativeSsaIntrinsicEmitter
    ) {
        put(NativeJvmIntrinsicKey(Opcodes.INVOKESTATIC, owner, name, desc), emitter)
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.math() {
        putMathLike("java/lang/Math")
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.strictMath() {
        putMathLike("java/lang/StrictMath")
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.putMathLike(owner: String) {
        putStatic(owner, "abs", "(I)I") { a -> i32("grt_math_abs_i32(grt_i32(${a[0]}))") }
        putStatic(owner, "abs", "(J)J") { a -> i64("grt_math_abs_i64(grt_i64(${a[0]}))") }
        putStatic(owner, "max", "(II)I") { a -> i32("(grt_i32(${a[0]}) >= grt_i32(${a[1]}) ? grt_i32(${a[0]}) : grt_i32(${a[1]}))") }
        putStatic(owner, "max", "(JJ)J") { a -> i64("(grt_i64(${a[0]}) >= grt_i64(${a[1]}) ? grt_i64(${a[0]}) : grt_i64(${a[1]}))") }
        putStatic(owner, "min", "(II)I") { a -> i32("(grt_i32(${a[0]}) <= grt_i32(${a[1]}) ? grt_i32(${a[0]}) : grt_i32(${a[1]}))") }
        putStatic(owner, "min", "(JJ)J") { a -> i64("(grt_i64(${a[0]}) <= grt_i64(${a[1]}) ? grt_i64(${a[0]}) : grt_i64(${a[1]}))") }
        putStatic(owner, "abs", "(F)F") { a -> "grt_math_abs_f32(${a[0]})" }
        putStatic(owner, "abs", "(D)D") { a -> "grt_math_abs_f64(${a[0]})" }
        putStatic(owner, "min", "(FF)F") { a -> "grt_math_min_f32(${a[0]}, ${a[1]})" }
        putStatic(owner, "max", "(FF)F") { a -> "grt_math_max_f32(${a[0]}, ${a[1]})" }
        putStatic(owner, "min", "(DD)D") { a -> "grt_math_min_f64(${a[0]}, ${a[1]})" }
        putStatic(owner, "max", "(DD)D") { a -> "grt_math_max_f64(${a[0]}, ${a[1]})" }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.integer() {
        putStatic("java/lang/Integer", "rotateLeft", "(II)I") { a -> i32("grt_integer_rotate_left(grt_i32(${a[0]}), grt_i32(${a[1]}))") }
        putStatic("java/lang/Integer", "rotateRight", "(II)I") { a -> i32("grt_integer_rotate_right(grt_i32(${a[0]}), grt_i32(${a[1]}))") }
        putStatic("java/lang/Integer", "reverse", "(I)I") { a -> i32("grt_integer_reverse(grt_i32(${a[0]}))") }
        putStatic("java/lang/Integer", "reverseBytes", "(I)I") { a -> i32("grt_integer_reverse_bytes(grt_i32(${a[0]}))") }
        putStatic("java/lang/Integer", "bitCount", "(I)I") { a -> i32("grt_integer_bit_count(grt_i32(${a[0]}))") }
        putStatic("java/lang/Integer", "numberOfLeadingZeros", "(I)I") { a -> i32("grt_integer_number_of_leading_zeros(grt_i32(${a[0]}))") }
        putStatic("java/lang/Integer", "numberOfTrailingZeros", "(I)I") { a -> i32("grt_integer_number_of_trailing_zeros(grt_i32(${a[0]}))") }
        putStatic("java/lang/Integer", "highestOneBit", "(I)I") { a -> i32("grt_integer_highest_one_bit(grt_i32(${a[0]}))") }
        putStatic("java/lang/Integer", "lowestOneBit", "(I)I") { a -> i32("grt_integer_lowest_one_bit(grt_i32(${a[0]}))") }
        putStatic("java/lang/Integer", "signum", "(I)I") { a -> i32("((grt_i32(${a[0]}) > 0) - (grt_i32(${a[0]}) < 0))") }
        putStatic("java/lang/Integer", "compare", "(II)I") { a -> i32("(grt_i32(${a[0]}) == grt_i32(${a[1]}) ? 0 : (grt_i32(${a[0]}) < grt_i32(${a[1]}) ? -1 : 1))") }
        putStatic("java/lang/Integer", "compareUnsigned", "(II)I") { a -> i32("grt_compare_u32(${a[0]}, ${a[1]})") }
        putStatic("java/lang/Integer", "sum", "(II)I") { a -> i32("((${a[0]}) + (${a[1]}))") }
        putStatic("java/lang/Integer", "max", "(II)I") { a -> i32("(grt_i32(${a[0]}) >= grt_i32(${a[1]}) ? grt_i32(${a[0]}) : grt_i32(${a[1]}))") }
        putStatic("java/lang/Integer", "min", "(II)I") { a -> i32("(grt_i32(${a[0]}) <= grt_i32(${a[1]}) ? grt_i32(${a[0]}) : grt_i32(${a[1]}))") }
        putStatic("java/lang/Integer", "hashCode", "(I)I") { a -> i32(a[0]) }
        putStatic("java/lang/Integer", "toUnsignedLong", "(I)J") { a -> i64(a[0]) }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.long() {
        putStatic("java/lang/Long", "rotateLeft", "(JI)J") { a -> i64("grt_long_rotate_left(grt_i64(${a[0]}), grt_i32(${a[1]}))") }
        putStatic("java/lang/Long", "rotateRight", "(JI)J") { a -> i64("grt_long_rotate_right(grt_i64(${a[0]}), grt_i32(${a[1]}))") }
        putStatic("java/lang/Long", "reverse", "(J)J") { a -> i64("grt_long_reverse(grt_i64(${a[0]}))") }
        putStatic("java/lang/Long", "reverseBytes", "(J)J") { a -> i64("grt_long_reverse_bytes(grt_i64(${a[0]}))") }
        putStatic("java/lang/Long", "bitCount", "(J)I") { a -> i32("grt_long_bit_count(grt_i64(${a[0]}))") }
        putStatic("java/lang/Long", "numberOfLeadingZeros", "(J)I") { a -> i32("grt_long_number_of_leading_zeros(grt_i64(${a[0]}))") }
        putStatic("java/lang/Long", "numberOfTrailingZeros", "(J)I") { a -> i32("grt_long_number_of_trailing_zeros(grt_i64(${a[0]}))") }
        putStatic("java/lang/Long", "highestOneBit", "(J)J") { a -> i64("grt_long_highest_one_bit(grt_i64(${a[0]}))") }
        putStatic("java/lang/Long", "lowestOneBit", "(J)J") { a -> i64("grt_long_lowest_one_bit(grt_i64(${a[0]}))") }
        putStatic("java/lang/Long", "signum", "(J)I") { a -> i32("((grt_i64(${a[0]}) > 0LL) - (grt_i64(${a[0]}) < 0LL))") }
        putStatic("java/lang/Long", "compare", "(JJ)I") { a -> i32("(grt_i64(${a[0]}) == grt_i64(${a[1]}) ? 0 : (grt_i64(${a[0]}) < grt_i64(${a[1]}) ? -1 : 1))") }
        putStatic("java/lang/Long", "compareUnsigned", "(JJ)I") { a -> i32("grt_compare_u64(${a[0]}, ${a[1]})") }
        putStatic("java/lang/Long", "sum", "(JJ)J") { a -> i64("((${a[0]}) + (${a[1]}))") }
        putStatic("java/lang/Long", "max", "(JJ)J") { a -> i64("(grt_i64(${a[0]}) >= grt_i64(${a[1]}) ? grt_i64(${a[0]}) : grt_i64(${a[1]}))") }
        putStatic("java/lang/Long", "min", "(JJ)J") { a -> i64("(grt_i64(${a[0]}) <= grt_i64(${a[1]}) ? grt_i64(${a[0]}) : grt_i64(${a[1]}))") }
        putStatic("java/lang/Long", "hashCode", "(J)I") { a -> i32("grt_long_hash_code(grt_i64(${a[0]}))") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.float() {
        putStatic("java/lang/Float", "floatToRawIntBits", "(F)I") { a -> i32("grt_float_to_raw_int_bits(${a[0]})") }
        putStatic("java/lang/Float", "floatToIntBits", "(F)I") { a -> i32("grt_float_to_int_bits(${a[0]})") }
        putStatic("java/lang/Float", "intBitsToFloat", "(I)F") { a -> "grt_float_int_bits_to_float(grt_i32(${a[0]}))" }
        putStatic("java/lang/Float", "isNaN", "(F)Z") { a -> bool("grt_float_is_nan(${a[0]})") }
        putStatic("java/lang/Float", "isInfinite", "(F)Z") { a -> bool("grt_float_is_infinite(${a[0]})") }
        putStatic("java/lang/Float", "isFinite", "(F)Z") { a -> bool("grt_float_is_finite(${a[0]})") }
        putStatic("java/lang/Float", "compare", "(FF)I") { a -> i32("grt_float_compare(${a[0]}, ${a[1]})") }
        putStatic("java/lang/Float", "hashCode", "(F)I") { a -> i32("grt_float_hash_code(${a[0]})") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.double() {
        putStatic("java/lang/Double", "doubleToRawLongBits", "(D)J") { a -> i64("grt_double_to_raw_long_bits(${a[0]})") }
        putStatic("java/lang/Double", "doubleToLongBits", "(D)J") { a -> i64("grt_double_to_long_bits(${a[0]})") }
        putStatic("java/lang/Double", "longBitsToDouble", "(J)D") { a -> "grt_double_long_bits_to_double(grt_i64(${a[0]}))" }
        putStatic("java/lang/Double", "isNaN", "(D)Z") { a -> bool("grt_double_is_nan(${a[0]})") }
        putStatic("java/lang/Double", "isInfinite", "(D)Z") { a -> bool("grt_double_is_infinite(${a[0]})") }
        putStatic("java/lang/Double", "isFinite", "(D)Z") { a -> bool("grt_double_is_finite(${a[0]})") }
        putStatic("java/lang/Double", "compare", "(DD)I") { a -> i32("grt_double_compare(${a[0]}, ${a[1]})") }
        putStatic("java/lang/Double", "hashCode", "(D)I") { a -> i32("grt_double_hash_code(${a[0]})") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.short() {
        putStatic("java/lang/Short", "reverseBytes", "(S)S") { a -> i32("grt_short_reverse_bytes(grt_i32(${a[0]}))") }
        putStatic("java/lang/Short", "toUnsignedInt", "(S)I") { a -> i32("((${a[0]}) & 0xffffu)") }
        putStatic("java/lang/Short", "toUnsignedLong", "(S)J") { a -> i64("((${a[0]}) & 0xffffu)") }
        putStatic("java/lang/Short", "hashCode", "(S)I") { a -> i32("static_cast<jint>(static_cast<jshort>(grt_i32(${a[0]})))") }
        putStatic("java/lang/Short", "compare", "(SS)I") { a -> i32("grt_short_compare(grt_i32(${a[0]}), grt_i32(${a[1]}))") }
        putStatic("java/lang/Short", "compareUnsigned", "(SS)I") { a -> i32("grt_short_compare_unsigned(grt_i32(${a[0]}), grt_i32(${a[1]}))") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.byte() {
        putStatic("java/lang/Byte", "toUnsignedInt", "(B)I") { a -> i32("((${a[0]}) & 0xffu)") }
        putStatic("java/lang/Byte", "toUnsignedLong", "(B)J") { a -> i64("((${a[0]}) & 0xffu)") }
        putStatic("java/lang/Byte", "hashCode", "(B)I") { a -> i32("static_cast<jint>(static_cast<jbyte>(grt_i32(${a[0]})))") }
        putStatic("java/lang/Byte", "compare", "(BB)I") { a -> i32("grt_byte_compare(grt_i32(${a[0]}), grt_i32(${a[1]}))") }
        putStatic("java/lang/Byte", "compareUnsigned", "(BB)I") { a -> i32("grt_byte_compare_unsigned(grt_i32(${a[0]}), grt_i32(${a[1]}))") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.boolean() {
        putStatic("java/lang/Boolean", "compare", "(ZZ)I") { a -> i32("((${a[0]}) == (${a[1]}) ? 0 : ((${a[0]}) != 0u ? 1 : -1))") }
        putStatic("java/lang/Boolean", "logicalAnd", "(ZZ)Z") { a -> bool("((${a[0]}) != 0u && (${a[1]}) != 0u)") }
        putStatic("java/lang/Boolean", "logicalOr", "(ZZ)Z") { a -> bool("((${a[0]}) != 0u || (${a[1]}) != 0u)") }
        putStatic("java/lang/Boolean", "logicalXor", "(ZZ)Z") { a -> bool("(((${a[0]}) != 0u) != ((${a[1]}) != 0u))") }
        putStatic("java/lang/Boolean", "hashCode", "(Z)I") { a -> i32("((${a[0]}) != 0u ? 1231 : 1237)") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeSsaIntrinsicEmitter>.character() {
        putStatic("java/lang/Character", "hashCode", "(C)I") { a -> i32("((${a[0]}) & 0xffffu)") }
        putStatic("java/lang/Character", "compare", "(CC)I") { a -> i32("grt_character_compare(grt_i32(${a[0]}), grt_i32(${a[1]}))") }
        putStatic("java/lang/Character", "reverseBytes", "(C)C") { a -> i32("grt_bswap16(static_cast<uint16_t>((${a[0]}) & 0xffffu))") }
        putStatic("java/lang/Character", "charCount", "(I)I") { a -> i32("(grt_i32(${a[0]}) >= 0x10000 ? 2 : 1)") }
        putStatic("java/lang/Character", "isHighSurrogate", "(C)Z") { a -> bool("grt_character_is_high_surrogate(grt_i32(${a[0]}))") }
        putStatic("java/lang/Character", "isLowSurrogate", "(C)Z") { a -> bool("grt_character_is_low_surrogate(grt_i32(${a[0]}))") }
        putStatic("java/lang/Character", "isSurrogate", "(C)Z") { a -> bool("grt_character_is_surrogate(grt_i32(${a[0]}))") }
        putStatic("java/lang/Character", "isValidCodePoint", "(I)Z") { a -> bool("(grt_i32(${a[0]}) >= 0 && grt_i32(${a[0]}) <= 0x10ffff)") }
        putStatic("java/lang/Character", "isSupplementaryCodePoint", "(I)Z") { a -> bool("(grt_i32(${a[0]}) >= 0x10000 && grt_i32(${a[0]}) <= 0x10ffff)") }
        putStatic("java/lang/Character", "toCodePoint", "(CC)I") { a -> i32("grt_character_to_code_point(grt_i32(${a[0]}), grt_i32(${a[1]}))") }
    }

    private fun i32(expression: String): String = "static_cast<uint32_t>($expression)"

    private fun i64(expression: String): String = "static_cast<uint64_t>($expression)"

    private fun bool(expression: String): String = "(($expression) ? 1u : 0u)"

    private fun unsupported(message: String): Nothing {
        throw UnsupportedNativeInstruction(NativeSkipReason.UnsupportedInstruction, message)
    }
}
