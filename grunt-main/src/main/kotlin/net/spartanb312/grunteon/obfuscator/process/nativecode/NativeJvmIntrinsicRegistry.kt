package net.spartanb312.grunteon.obfuscator.process.nativecode

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodInsnNode

internal data class NativeJvmIntrinsicKey(
    val opcode: Int,
    val owner: String,
    val name: String,
    val desc: String
) {
    val displayName: String
        get() = "${opcodeName(opcode)} $owner.$name$desc"

    private fun opcodeName(opcode: Int): String {
        return when (opcode) {
            Opcodes.INVOKESTATIC -> "INVOKESTATIC"
            Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL"
            Opcodes.INVOKESPECIAL -> "INVOKESPECIAL"
            Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE"
            else -> "OPCODE_$opcode"
        }
    }
}

internal class NativeJvmIntrinsicStats {
    private val counts = linkedMapOf<NativeJvmIntrinsicKey, Int>()

    val total: Int
        get() = counts.values.sum()

    val unique: Int
        get() = counts.size

    val byKey: Map<NativeJvmIntrinsicKey, Int>
        get() = counts.toMap()

    fun record(key: NativeJvmIntrinsicKey) {
        counts[key] = (counts[key] ?: 0) + 1
    }
}

private typealias NativeJvmIntrinsicEmitter = StringBuilder.(NativeJvmIntrinsicKey) -> Unit

internal object NativeJvmIntrinsicRegistry {
    private val emitters: Map<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter> = buildMap {
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

    val keys: Set<NativeJvmIntrinsicKey>
        get() = emitters.keys

    fun key(node: MethodInsnNode): NativeJvmIntrinsicKey {
        return NativeJvmIntrinsicKey(node.opcode, node.owner, node.name, node.desc)
    }

    fun isIntrinsic(node: MethodInsnNode): Boolean {
        return key(node) in emitters
    }

    fun emit(
        builder: StringBuilder,
        node: MethodInsnNode,
        stats: NativeJvmIntrinsicStats?
    ): Boolean {
        val key = key(node)
        val emitter = emitters[key] ?: return false
        builder.emitter(key)
        stats?.record(key)
        return true
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.putStatic(
        owner: String,
        name: String,
        desc: String,
        emitter: NativeJvmIntrinsicEmitter
    ) {
        put(NativeJvmIntrinsicKey(Opcodes.INVOKESTATIC, owner, name, desc), emitter)
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.math() {
        putStatic("java/lang/Math", "abs", "(I)I") {
            intrinsicBlock(it) {
                appendLine("        jint value = cstack[--sp].i;")
                appendLine("        cstack[sp++].i = grt_math_abs_i32(value);")
            }
        }
        putStatic("java/lang/Math", "abs", "(J)J") {
            intrinsicBlock(it) {
                appendLine("        jlong value = cstack[--sp].j;")
                appendLine("        cstack[sp++].j = grt_math_abs_i64(value);")
            }
        }
        putStatic("java/lang/Math", "max", "(II)I") { binaryInt(it, "lhs >= rhs ? lhs : rhs") }
        putStatic("java/lang/Math", "max", "(JJ)J") { binaryLongToLong(it, "lhs >= rhs ? lhs : rhs") }
        putStatic("java/lang/Math", "min", "(II)I") { binaryInt(it, "lhs <= rhs ? lhs : rhs") }
        putStatic("java/lang/Math", "min", "(JJ)J") { binaryLongToLong(it, "lhs <= rhs ? lhs : rhs") }
        putStatic("java/lang/Math", "abs", "(F)F") { unaryFloatToFloat(it, "grt_math_abs_f32(value)") }
        putStatic("java/lang/Math", "abs", "(D)D") { unaryDoubleToDouble(it, "grt_math_abs_f64(value)") }
        putStatic("java/lang/Math", "min", "(FF)F") { binaryFloatToFloat(it, "grt_math_min_f32(lhs, rhs)") }
        putStatic("java/lang/Math", "max", "(FF)F") { binaryFloatToFloat(it, "grt_math_max_f32(lhs, rhs)") }
        putStatic("java/lang/Math", "min", "(DD)D") { binaryDoubleToDouble(it, "grt_math_min_f64(lhs, rhs)") }
        putStatic("java/lang/Math", "max", "(DD)D") { binaryDoubleToDouble(it, "grt_math_max_f64(lhs, rhs)") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.strictMath() {
        putStatic("java/lang/StrictMath", "abs", "(I)I") {
            intrinsicBlock(it) {
                appendLine("        jint value = cstack[--sp].i;")
                appendLine("        cstack[sp++].i = grt_math_abs_i32(value);")
            }
        }
        putStatic("java/lang/StrictMath", "abs", "(J)J") {
            intrinsicBlock(it) {
                appendLine("        jlong value = cstack[--sp].j;")
                appendLine("        cstack[sp++].j = grt_math_abs_i64(value);")
            }
        }
        putStatic("java/lang/StrictMath", "max", "(II)I") { binaryInt(it, "lhs >= rhs ? lhs : rhs") }
        putStatic("java/lang/StrictMath", "max", "(JJ)J") { binaryLongToLong(it, "lhs >= rhs ? lhs : rhs") }
        putStatic("java/lang/StrictMath", "min", "(II)I") { binaryInt(it, "lhs <= rhs ? lhs : rhs") }
        putStatic("java/lang/StrictMath", "min", "(JJ)J") { binaryLongToLong(it, "lhs <= rhs ? lhs : rhs") }
        putStatic("java/lang/StrictMath", "abs", "(F)F") { unaryFloatToFloat(it, "grt_math_abs_f32(value)") }
        putStatic("java/lang/StrictMath", "abs", "(D)D") { unaryDoubleToDouble(it, "grt_math_abs_f64(value)") }
        putStatic("java/lang/StrictMath", "min", "(FF)F") { binaryFloatToFloat(it, "grt_math_min_f32(lhs, rhs)") }
        putStatic("java/lang/StrictMath", "max", "(FF)F") { binaryFloatToFloat(it, "grt_math_max_f32(lhs, rhs)") }
        putStatic("java/lang/StrictMath", "min", "(DD)D") { binaryDoubleToDouble(it, "grt_math_min_f64(lhs, rhs)") }
        putStatic("java/lang/StrictMath", "max", "(DD)D") { binaryDoubleToDouble(it, "grt_math_max_f64(lhs, rhs)") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.integer() {
        putStatic("java/lang/Integer", "rotateLeft", "(II)I") {
            binaryIntIntToInt(it, "grt_integer_rotate_left(value, distance)", "value", "distance")
        }
        putStatic("java/lang/Integer", "rotateRight", "(II)I") {
            binaryIntIntToInt(it, "grt_integer_rotate_right(value, distance)", "value", "distance")
        }
        putStatic("java/lang/Integer", "reverse", "(I)I") { unaryInt(it, "grt_integer_reverse(value)") }
        putStatic("java/lang/Integer", "reverseBytes", "(I)I") { unaryInt(it, "grt_integer_reverse_bytes(value)") }
        putStatic("java/lang/Integer", "bitCount", "(I)I") { unaryInt(it, "grt_integer_bit_count(value)") }
        putStatic("java/lang/Integer", "numberOfLeadingZeros", "(I)I") {
            unaryInt(it, "grt_integer_number_of_leading_zeros(value)")
        }
        putStatic("java/lang/Integer", "numberOfTrailingZeros", "(I)I") {
            unaryInt(it, "grt_integer_number_of_trailing_zeros(value)")
        }
        putStatic("java/lang/Integer", "highestOneBit", "(I)I") { unaryInt(it, "grt_integer_highest_one_bit(value)") }
        putStatic("java/lang/Integer", "lowestOneBit", "(I)I") { unaryInt(it, "grt_integer_lowest_one_bit(value)") }
        putStatic("java/lang/Integer", "signum", "(I)I") { unaryInt(it, "(value > 0) - (value < 0)") }
        putStatic("java/lang/Integer", "compare", "(II)I") { binaryInt(it, "lhs == rhs ? 0 : (lhs < rhs ? -1 : 1)") }
        putStatic("java/lang/Integer", "compareUnsigned", "(II)I") {
            binaryInt(it, "grt_compare_u32(static_cast<uint32_t>(lhs), static_cast<uint32_t>(rhs))")
        }
        putStatic("java/lang/Integer", "sum", "(II)I") {
            binaryInt(it, "grt_i32(static_cast<uint32_t>(lhs) + static_cast<uint32_t>(rhs))")
        }
        putStatic("java/lang/Integer", "max", "(II)I") { binaryInt(it, "lhs >= rhs ? lhs : rhs") }
        putStatic("java/lang/Integer", "min", "(II)I") { binaryInt(it, "lhs <= rhs ? lhs : rhs") }
        putStatic("java/lang/Integer", "hashCode", "(I)I") { unaryInt(it, "value") }
        putStatic("java/lang/Integer", "toUnsignedLong", "(I)J") {
            intrinsicBlock(it) {
                appendLine("        jint value = cstack[--sp].i;")
                appendLine("        cstack[sp++].j = static_cast<jlong>(static_cast<uint32_t>(value));")
            }
        }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.long() {
        putStatic("java/lang/Long", "rotateLeft", "(JI)J") {
            binaryLongIntToLong(it, "grt_long_rotate_left(value, distance)", "value", "distance")
        }
        putStatic("java/lang/Long", "rotateRight", "(JI)J") {
            binaryLongIntToLong(it, "grt_long_rotate_right(value, distance)", "value", "distance")
        }
        putStatic("java/lang/Long", "reverse", "(J)J") { unaryLongToLong(it, "grt_long_reverse(value)") }
        putStatic("java/lang/Long", "reverseBytes", "(J)J") { unaryLongToLong(it, "grt_long_reverse_bytes(value)") }
        putStatic("java/lang/Long", "bitCount", "(J)I") { unaryLongToInt(it, "grt_long_bit_count(value)") }
        putStatic("java/lang/Long", "numberOfLeadingZeros", "(J)I") {
            unaryLongToInt(it, "grt_long_number_of_leading_zeros(value)")
        }
        putStatic("java/lang/Long", "numberOfTrailingZeros", "(J)I") {
            unaryLongToInt(it, "grt_long_number_of_trailing_zeros(value)")
        }
        putStatic("java/lang/Long", "highestOneBit", "(J)J") { unaryLongToLong(it, "grt_long_highest_one_bit(value)") }
        putStatic("java/lang/Long", "lowestOneBit", "(J)J") { unaryLongToLong(it, "grt_long_lowest_one_bit(value)") }
        putStatic("java/lang/Long", "signum", "(J)I") { unaryLongToInt(it, "(value > 0LL) - (value < 0LL)") }
        putStatic("java/lang/Long", "compare", "(JJ)I") { binaryLongToInt(it, "lhs == rhs ? 0 : (lhs < rhs ? -1 : 1)") }
        putStatic("java/lang/Long", "compareUnsigned", "(JJ)I") {
            binaryLongToInt(it, "grt_compare_u64(static_cast<uint64_t>(lhs), static_cast<uint64_t>(rhs))")
        }
        putStatic("java/lang/Long", "sum", "(JJ)J") {
            binaryLongToLong(it, "grt_i64(static_cast<uint64_t>(lhs) + static_cast<uint64_t>(rhs))")
        }
        putStatic("java/lang/Long", "max", "(JJ)J") { binaryLongToLong(it, "lhs >= rhs ? lhs : rhs") }
        putStatic("java/lang/Long", "min", "(JJ)J") { binaryLongToLong(it, "lhs <= rhs ? lhs : rhs") }
        putStatic("java/lang/Long", "hashCode", "(J)I") {
            unaryLongToInt(it, "grt_long_hash_code(value)")
        }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.float() {
        putStatic("java/lang/Float", "floatToRawIntBits", "(F)I") {
            unaryFloatToInt(it, "grt_float_to_raw_int_bits(value)")
        }
        putStatic("java/lang/Float", "floatToIntBits", "(F)I") {
            unaryFloatToInt(it, "grt_float_to_int_bits(value)")
        }
        putStatic("java/lang/Float", "intBitsToFloat", "(I)F") {
            intrinsicBlock(it) {
                appendLine("        jint value = cstack[--sp].i;")
                appendLine("        cstack[sp++].f = grt_float_int_bits_to_float(value);")
            }
        }
        putStatic("java/lang/Float", "isNaN", "(F)Z") { unaryFloatToBoolean(it, "grt_float_is_nan(value)") }
        putStatic("java/lang/Float", "isInfinite", "(F)Z") { unaryFloatToBoolean(it, "grt_float_is_infinite(value)") }
        putStatic("java/lang/Float", "isFinite", "(F)Z") { unaryFloatToBoolean(it, "grt_float_is_finite(value)") }
        putStatic("java/lang/Float", "compare", "(FF)I") { binaryFloatToInt(it, "grt_float_compare(lhs, rhs)") }
        putStatic("java/lang/Float", "hashCode", "(F)I") { unaryFloatToInt(it, "grt_float_hash_code(value)") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.double() {
        putStatic("java/lang/Double", "doubleToRawLongBits", "(D)J") {
            unaryDoubleToLong(it, "grt_double_to_raw_long_bits(value)")
        }
        putStatic("java/lang/Double", "doubleToLongBits", "(D)J") {
            unaryDoubleToLong(it, "grt_double_to_long_bits(value)")
        }
        putStatic("java/lang/Double", "longBitsToDouble", "(J)D") {
            intrinsicBlock(it) {
                appendLine("        jlong value = cstack[--sp].j;")
                appendLine("        cstack[sp++].d = grt_double_long_bits_to_double(value);")
            }
        }
        putStatic("java/lang/Double", "isNaN", "(D)Z") { unaryDoubleToBoolean(it, "grt_double_is_nan(value)") }
        putStatic("java/lang/Double", "isInfinite", "(D)Z") { unaryDoubleToBoolean(it, "grt_double_is_infinite(value)") }
        putStatic("java/lang/Double", "isFinite", "(D)Z") { unaryDoubleToBoolean(it, "grt_double_is_finite(value)") }
        putStatic("java/lang/Double", "compare", "(DD)I") { binaryDoubleToInt(it, "grt_double_compare(lhs, rhs)") }
        putStatic("java/lang/Double", "hashCode", "(D)I") { unaryDoubleToInt(it, "grt_double_hash_code(value)") }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.short() {
        putStatic("java/lang/Short", "reverseBytes", "(S)S") {
            intrinsicBlock(it) {
                appendLine("        jint value = cstack[--sp].i;")
                appendLine("        cstack[sp++].i = static_cast<jint>(grt_short_reverse_bytes(value));")
            }
        }
        putStatic("java/lang/Short", "toUnsignedInt", "(S)I") {
            unaryInt(it, "static_cast<jint>(static_cast<uint32_t>(value) & 0xffffu)")
        }
        putStatic("java/lang/Short", "toUnsignedLong", "(S)J") {
            intrinsicBlock(it) {
                appendLine("        jint value = cstack[--sp].i;")
                appendLine("        cstack[sp++].j = static_cast<jlong>(static_cast<uint32_t>(value) & 0xffffu);")
            }
        }
        putStatic("java/lang/Short", "hashCode", "(S)I") { unaryInt(it, "static_cast<jint>(static_cast<jshort>(value))") }
        putStatic("java/lang/Short", "compare", "(SS)I") {
            binaryInt(it, "grt_short_compare(lhs, rhs)")
        }
        putStatic("java/lang/Short", "compareUnsigned", "(SS)I") {
            binaryInt(it, "grt_short_compare_unsigned(lhs, rhs)")
        }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.byte() {
        putStatic("java/lang/Byte", "toUnsignedInt", "(B)I") {
            unaryInt(it, "static_cast<jint>(static_cast<uint32_t>(value) & 0xffu)")
        }
        putStatic("java/lang/Byte", "toUnsignedLong", "(B)J") {
            intrinsicBlock(it) {
                appendLine("        jint value = cstack[--sp].i;")
                appendLine("        cstack[sp++].j = static_cast<jlong>(static_cast<uint32_t>(value) & 0xffu);")
            }
        }
        putStatic("java/lang/Byte", "hashCode", "(B)I") { unaryInt(it, "static_cast<jint>(static_cast<jbyte>(value))") }
        putStatic("java/lang/Byte", "compare", "(BB)I") {
            binaryInt(it, "grt_byte_compare(lhs, rhs)")
        }
        putStatic("java/lang/Byte", "compareUnsigned", "(BB)I") {
            binaryInt(it, "grt_byte_compare_unsigned(lhs, rhs)")
        }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.boolean() {
        putStatic("java/lang/Boolean", "compare", "(ZZ)I") {
            intrinsicBlock(it) {
                appendLine("        bool rhs = cstack[--sp].i != 0;")
                appendLine("        bool lhs = cstack[--sp].i != 0;")
                appendLine("        cstack[sp++].i = lhs == rhs ? 0 : (lhs ? 1 : -1);")
            }
        }
        putStatic("java/lang/Boolean", "logicalAnd", "(ZZ)Z") {
            booleanBinary(it, "lhs && rhs")
        }
        putStatic("java/lang/Boolean", "logicalOr", "(ZZ)Z") {
            booleanBinary(it, "lhs || rhs")
        }
        putStatic("java/lang/Boolean", "logicalXor", "(ZZ)Z") {
            booleanBinary(it, "lhs != rhs")
        }
        putStatic("java/lang/Boolean", "hashCode", "(Z)I") {
            intrinsicBlock(it) {
                appendLine("        bool value = cstack[--sp].i != 0;")
                appendLine("        cstack[sp++].i = value ? 1231 : 1237;")
            }
        }
    }

    private fun MutableMap<NativeJvmIntrinsicKey, NativeJvmIntrinsicEmitter>.character() {
        putStatic("java/lang/Character", "hashCode", "(C)I") {
            unaryInt(it, "static_cast<jint>(static_cast<uint32_t>(value) & 0xffffu)")
        }
        putStatic("java/lang/Character", "compare", "(CC)I") {
            binaryInt(it, "grt_character_compare(lhs, rhs)")
        }
        putStatic("java/lang/Character", "reverseBytes", "(C)C") {
            unaryInt(it, "static_cast<jint>(grt_bswap16(static_cast<uint16_t>(static_cast<uint32_t>(value) & 0xffffu)))")
        }
        putStatic("java/lang/Character", "charCount", "(I)I") {
            unaryInt(it, "value >= 0x10000 ? 2 : 1")
        }
        putStatic("java/lang/Character", "isHighSurrogate", "(C)Z") {
            unaryInt(it, "grt_character_is_high_surrogate(value) ? 1 : 0")
        }
        putStatic("java/lang/Character", "isLowSurrogate", "(C)Z") {
            unaryInt(it, "grt_character_is_low_surrogate(value) ? 1 : 0")
        }
        putStatic("java/lang/Character", "isSurrogate", "(C)Z") {
            unaryInt(it, "grt_character_is_surrogate(value) ? 1 : 0")
        }
        putStatic("java/lang/Character", "isValidCodePoint", "(I)Z") {
            unaryInt(it, "(value >= 0 && value <= 0x10ffff) ? 1 : 0")
        }
        putStatic("java/lang/Character", "isSupplementaryCodePoint", "(I)Z") {
            unaryInt(it, "(value >= 0x10000 && value <= 0x10ffff) ? 1 : 0")
        }
        putStatic("java/lang/Character", "toCodePoint", "(CC)I") {
            binaryInt(it, "grt_character_to_code_point(lhs, rhs)")
        }
    }

    private fun StringBuilder.intrinsicBlock(
        key: NativeJvmIntrinsicKey,
        body: StringBuilder.() -> Unit
    ) {
        appendLine("    {")
        appendLine("        // intrinsic ${key.owner}.${key.name}${key.desc}")
        body()
        appendLine("    }")
    }

    private fun StringBuilder.unaryInt(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jint value = cstack[--sp].i;")
            appendLine("        cstack[sp++].i = $expression;")
        }
    }

    private fun StringBuilder.unaryLongToInt(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jlong value = cstack[--sp].j;")
            appendLine("        cstack[sp++].i = $expression;")
        }
    }

    private fun StringBuilder.unaryLongToLong(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jlong value = cstack[--sp].j;")
            appendLine("        cstack[sp++].j = $expression;")
        }
    }

    private fun StringBuilder.binaryInt(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jint rhs = cstack[--sp].i;")
            appendLine("        jint lhs = cstack[--sp].i;")
            appendLine("        cstack[sp++].i = $expression;")
        }
    }

    private fun StringBuilder.binaryIntIntToInt(
        key: NativeJvmIntrinsicKey,
        expression: String,
        lhsName: String,
        rhsName: String
    ) {
        intrinsicBlock(key) {
            appendLine("        jint $rhsName = cstack[--sp].i;")
            appendLine("        jint $lhsName = cstack[--sp].i;")
            appendLine("        cstack[sp++].i = $expression;")
        }
    }

    private fun StringBuilder.binaryLongIntToLong(
        key: NativeJvmIntrinsicKey,
        expression: String,
        lhsName: String,
        rhsName: String
    ) {
        intrinsicBlock(key) {
            appendLine("        jint $rhsName = cstack[--sp].i;")
            appendLine("        jlong $lhsName = cstack[--sp].j;")
            appendLine("        cstack[sp++].j = $expression;")
        }
    }

    private fun StringBuilder.binaryLongToInt(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jlong rhs = cstack[--sp].j;")
            appendLine("        jlong lhs = cstack[--sp].j;")
            appendLine("        cstack[sp++].i = $expression;")
        }
    }

    private fun StringBuilder.binaryLongToLong(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jlong rhs = cstack[--sp].j;")
            appendLine("        jlong lhs = cstack[--sp].j;")
            appendLine("        cstack[sp++].j = $expression;")
        }
    }

    private fun StringBuilder.booleanBinary(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        bool rhs = cstack[--sp].i != 0;")
            appendLine("        bool lhs = cstack[--sp].i != 0;")
            appendLine("        cstack[sp++].i = $expression ? 1 : 0;")
        }
    }

    private fun StringBuilder.unaryFloatToInt(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jfloat value = cstack[--sp].f;")
            appendLine("        cstack[sp++].i = $expression;")
        }
    }

    private fun StringBuilder.unaryFloatToBoolean(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jfloat value = cstack[--sp].f;")
            appendLine("        cstack[sp++].i = $expression ? 1 : 0;")
        }
    }

    private fun StringBuilder.unaryFloatToFloat(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jfloat value = cstack[--sp].f;")
            appendLine("        cstack[sp++].f = $expression;")
        }
    }

    private fun StringBuilder.binaryFloatToInt(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jfloat rhs = cstack[--sp].f;")
            appendLine("        jfloat lhs = cstack[--sp].f;")
            appendLine("        cstack[sp++].i = $expression;")
        }
    }

    private fun StringBuilder.binaryFloatToFloat(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jfloat rhs = cstack[--sp].f;")
            appendLine("        jfloat lhs = cstack[--sp].f;")
            appendLine("        cstack[sp++].f = $expression;")
        }
    }

    private fun StringBuilder.unaryDoubleToInt(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jdouble value = cstack[--sp].d;")
            appendLine("        cstack[sp++].i = $expression;")
        }
    }

    private fun StringBuilder.unaryDoubleToLong(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jdouble value = cstack[--sp].d;")
            appendLine("        cstack[sp++].j = $expression;")
        }
    }

    private fun StringBuilder.unaryDoubleToBoolean(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jdouble value = cstack[--sp].d;")
            appendLine("        cstack[sp++].i = $expression ? 1 : 0;")
        }
    }

    private fun StringBuilder.unaryDoubleToDouble(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jdouble value = cstack[--sp].d;")
            appendLine("        cstack[sp++].d = $expression;")
        }
    }

    private fun StringBuilder.binaryDoubleToInt(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jdouble rhs = cstack[--sp].d;")
            appendLine("        jdouble lhs = cstack[--sp].d;")
            appendLine("        cstack[sp++].i = $expression;")
        }
    }

    private fun StringBuilder.binaryDoubleToDouble(key: NativeJvmIntrinsicKey, expression: String) {
        intrinsicBlock(key) {
            appendLine("        jdouble rhs = cstack[--sp].d;")
            appendLine("        jdouble lhs = cstack[--sp].d;")
            appendLine("        cstack[sp++].d = $expression;")
        }
    }
}
