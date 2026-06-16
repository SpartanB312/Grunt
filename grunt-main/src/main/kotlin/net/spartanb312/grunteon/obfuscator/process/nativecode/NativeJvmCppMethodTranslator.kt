package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmExceptionDispatchPlanner
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmExceptionDispatchPlan
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmInstruction
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmMethodIr
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmSupportReport
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import java.util.Locale

internal object NativeJvmCppMethodTranslator {

    fun validate(
        methodNode: MethodNode,
        ir: NativeJvmMethodIr,
        support: NativeJvmSupportReport,
        commitKind: NativeMethodCommitKind = NativeMethodCommitKind.Direct
    ) {
        translate(methodNode, ir, support, "grt_validate", commitKind)
    }

    fun translate(
        method: NativeValidatedMethod,
        functionName: String,
        commitKind: NativeMethodCommitKind = NativeMethodCommitKind.Direct
    ): String {
        return translate(method.methodNode, method.jvmIr, method.fullJvmSupport, functionName, commitKind)
    }

    private fun translate(
        methodNode: MethodNode,
        ir: NativeJvmMethodIr,
        support: NativeJvmSupportReport,
        functionName: String,
        commitKind: NativeMethodCommitKind
    ): String {
        if (!support.isFullJvmLoweringReady) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "full JVM C++ lowering requires a ready NativeJvmMethodIr"
            )
        }
        val arguments = Type.getArgumentTypes(methodNode.desc)
        val returnType = Type.getReturnType(methodNode.desc)
        validateDescriptor(arguments, returnType, methodNode.desc)

        val argumentLocalSlots = arguments.sumOf { it.size } + if (ir.isStatic) 0 else 1
        val maxLocals = maxOf(ir.maxLocals, argumentLocalSlots, 1)
        val maxStack = maxOf(ir.maxStack, 1)
        val dispatchPlan = NativeJvmExceptionDispatchPlanner.plan(ir)
        val catchClassBindings = dispatchPlan.catchClassBindings()
        val needsMethodHandleLookup = ir.instructions.any {
            (it.node as? LdcInsnNode)?.cst is Handle
        }
        val labels = LabelTargetResolver(methodNode)
        val stackShape = StackShapeAnalyzer(ir.ownerInternalName, methodNode)
        val isLoaderProxy =
            commitKind == NativeMethodCommitKind.InterfaceProxy ||
                commitKind == NativeMethodCommitKind.InterfaceClassInitializerProxy

        return buildString {
            append(cppReturnType(returnType))
                .append(" JNICALL ")
                .append(functionName)
                .append("(JNIEnv* env, ")
            if (isLoaderProxy) {
                append("jclass loaderClazz, jobject proxyContext")
            } else {
                append(if (ir.isStatic) "jclass clazz" else "jobject self")
            }
            arguments.forEachIndexed { index, argument ->
                append(", ")
                    .append(cppArgumentType(argument))
                    .append(" arg")
                    .append(index)
            }
            appendLine(") {")
            appendLine("    jvalue cstack[$maxStack] = {};")
            appendLine("    jvalue clocal[$maxLocals] = {};")
            appendLine("    jint sp = 0;")
            appendLine("    (void) sp;")
            if (isLoaderProxy) {
                appendLine("    (void) loaderClazz;")
                if (ir.isStatic) {
                    appendLine("    jclass clazz = (jclass) proxyContext;")
                    appendLine("    jobject classloader = grt_get_classloader(env, clazz);")
                    appendLine("    if (env->ExceptionCheck()) { ${defaultReturn(returnType)} }")
                } else {
                    appendLine("    jobject self = proxyContext;")
                    appendLine("    if (self == nullptr) { grt_throw(env, \"java/lang/NullPointerException\", \"interface receiver is null\"); ${defaultReturn(returnType)} }")
                    appendLine("    jclass clazz = env->GetObjectClass(self);")
                    appendLine("    if (clazz == nullptr || env->ExceptionCheck()) { ${defaultReturn(returnType)} }")
                    appendLine("    jobject classloader = grt_get_classloader(env, clazz);")
                    appendLine("    env->DeleteLocalRef(clazz);")
                    appendLine("    if (env->ExceptionCheck()) { ${defaultReturn(returnType)} }")
                }
            } else {
                if (ir.isStatic) {
                    appendLine("    jobject classloader = grt_get_classloader(env, clazz);")
                    appendLine("    if (env->ExceptionCheck()) { ${defaultReturn(returnType)} }")
                } else {
                    appendLine("    jclass clazz = env->GetObjectClass(self);")
                    appendLine("    if (clazz == nullptr || env->ExceptionCheck()) { ${defaultReturn(returnType)} }")
                    appendLine("    jobject classloader = grt_get_classloader(env, clazz);")
                    appendLine("    env->DeleteLocalRef(clazz);")
                    appendLine("    if (env->ExceptionCheck()) { ${defaultReturn(returnType)} }")
                }
            }
            appendLine("    (void) classloader;")
            if (needsMethodHandleLookup) {
                if (ir.isStatic) {
                    appendLine("    jclass currentClass = clazz;")
                } else {
                    append("    jclass currentClass = grt_find_class(env, classloader, \"")
                        .append(cppString(ir.ownerInternalName))
                        .appendLine("\");")
                    appendLine("    if (currentClass == nullptr || env->ExceptionCheck()) { ${defaultReturn(returnType)} }")
                }
                appendLine("    jobject lookup = nullptr;")
            }
            catchClassBindings.forEach { (caughtType, variableName) ->
                append("    jclass ")
                    .append(variableName)
                    .append(" = grt_find_class(env, classloader, \"")
                    .append(cppString(caughtType))
                    .appendLine("\");")
                appendLine("    if ($variableName == nullptr || env->ExceptionCheck()) { ${defaultReturn(returnType)} }")
            }
            appendLine()

            var localIndex = if (ir.isStatic) {
                0
            } else {
                appendLine("    clocal[0].l = self;")
                1
            }
            arguments.forEachIndexed { argumentIndex, argument ->
                when (argument.sort) {
                    in IntLikeSorts -> appendLine("    clocal[$localIndex].i = static_cast<jint>(arg$argumentIndex);")
                    Type.LONG -> appendLine("    clocal[$localIndex].j = arg$argumentIndex;")
                    Type.FLOAT -> appendLine("    clocal[$localIndex].f = arg$argumentIndex;")
                    Type.DOUBLE -> appendLine("    clocal[$localIndex].d = arg$argumentIndex;")
                    Type.OBJECT,
                    Type.ARRAY -> appendLine("    clocal[$localIndex].l = arg$argumentIndex;")
                    else -> unsupportedDescriptor(methodNode.desc)
                }
                localIndex += argument.size
            }
            if (arguments.isNotEmpty()) appendLine()

            ir.instructions.forEach { instruction ->
                appendLine("L_BC_${instruction.instructionIndex}:")
                appendLine("    // ${opcodeName(instruction.opcode)}")
                emitInstruction(instruction, returnType, labels, stackShape)
                if (!isTerminal(instruction.opcode)) {
                    appendExceptionCheck(instruction, dispatchPlan.labelFor(instruction), returnType)
                    if (instruction.opcode == Opcodes.ATHROW) {
                        appendLine("    ${defaultReturn(returnType)}")
                    }
                }
            }

            appendLine("    ${defaultReturn(returnType)}")
            if (!dispatchPlan.isEmpty) {
                appendLine()
                appendLine("    // Native JVM exception dispatch")
                dispatchPlan.dispatches.forEach { dispatch ->
                    appendLine("${dispatch.label}:")
                    dispatch.catches.forEach { catchHandler ->
                        if (catchHandler.isCatchAll) {
                            appendLine("    goto L_BC_${catchHandler.handlerInstructionIndex};")
                        } else {
                            val caughtType = requireNotNull(catchHandler.caughtType)
                            val catchClass = catchClassBindings.getValue(caughtType)
                            appendLine("    if (env->IsInstanceOf(cstack[0].l, $catchClass)) { goto L_BC_${catchHandler.handlerInstructionIndex}; }")
                        }
                    }
                    appendLine("    env->Throw((jthrowable) cstack[0].l);")
                    appendLine("    ${defaultReturn(returnType)}")
                }
            }
            appendLine("}")
        }
    }

    private fun NativeJvmExceptionDispatchPlan.catchClassBindings(): Map<String, String> {
        return dispatches
            .asSequence()
            .flatMap { dispatch -> dispatch.catches.asSequence() }
            .mapNotNull { it.caughtType }
            .distinct()
            .withIndex()
            .associate { (index, caughtType) -> caughtType to "catchClass_$index" }
    }

    private fun StringBuilder.emitInstruction(
        instruction: NativeJvmInstruction,
        returnType: Type,
        labels: LabelTargetResolver,
        stackShape: StackShapeAnalyzer
    ) {
        when (val opcode = instruction.opcode) {
            Opcodes.NOP -> Unit
            Opcodes.ACONST_NULL -> appendLine("    cstack[sp++].l = nullptr;")
            Opcodes.ICONST_M1 -> pushInt(-1)
            Opcodes.ICONST_0 -> pushInt(0)
            Opcodes.ICONST_1 -> pushInt(1)
            Opcodes.ICONST_2 -> pushInt(2)
            Opcodes.ICONST_3 -> pushInt(3)
            Opcodes.ICONST_4 -> pushInt(4)
            Opcodes.ICONST_5 -> pushInt(5)
            Opcodes.LCONST_0 -> pushLong(0L)
            Opcodes.LCONST_1 -> pushLong(1L)
            Opcodes.FCONST_0 -> pushFloat(0.0f)
            Opcodes.FCONST_1 -> pushFloat(1.0f)
            Opcodes.FCONST_2 -> pushFloat(2.0f)
            Opcodes.DCONST_0 -> pushDouble(0.0)
            Opcodes.DCONST_1 -> pushDouble(1.0)
            Opcodes.IADD -> binaryInt("+")
            Opcodes.ISUB -> binaryInt("-")
            Opcodes.IMUL -> binaryInt("*")
            Opcodes.IAND -> binaryInt("&")
            Opcodes.IOR -> binaryInt("|")
            Opcodes.IXOR -> binaryInt("^")
            Opcodes.INEG -> appendLine("    { jint value = cstack[--sp].i; cstack[sp++].i = static_cast<jint>(-value); }")
            Opcodes.IDIV -> appendLine(
                "    { jint rhs = cstack[--sp].i; jint lhs = cstack[--sp].i; " +
                    "if (rhs == 0) { grt_throw(env, \"java/lang/ArithmeticException\", \"/ by zero\"); } " +
                    "else if (lhs == ((jint) 0x80000000) && rhs == -1) { cstack[sp++].i = lhs; } " +
                    "else { cstack[sp++].i = static_cast<jint>(lhs / rhs); } }"
            )
            Opcodes.IREM -> appendLine(
                "    { jint rhs = cstack[--sp].i; jint lhs = cstack[--sp].i; " +
                    "if (rhs == 0) { grt_throw(env, \"java/lang/ArithmeticException\", \"/ by zero\"); } " +
                    "else { cstack[sp++].i = static_cast<jint>(lhs % rhs); } }"
            )
            Opcodes.ISHL -> appendLine(
                "    { jint rhs = cstack[--sp].i & 31; jint lhs = cstack[--sp].i; " +
                    "cstack[sp++].i = static_cast<jint>(static_cast<uint32_t>(lhs) << rhs); }"
            )
            Opcodes.ISHR -> appendLine(
                "    { jint rhs = cstack[--sp].i & 31; jint lhs = cstack[--sp].i; " +
                    "cstack[sp++].i = static_cast<jint>(lhs >> rhs); }"
            )
            Opcodes.IUSHR -> appendLine(
                "    { jint rhs = cstack[--sp].i & 31; jint lhs = cstack[--sp].i; " +
                    "cstack[sp++].i = static_cast<jint>(static_cast<uint32_t>(lhs) >> rhs); }"
            )
            Opcodes.LADD -> binaryLongWrapped("+")
            Opcodes.LSUB -> binaryLongWrapped("-")
            Opcodes.LMUL -> binaryLongWrapped("*")
            Opcodes.LAND -> binaryLong("&")
            Opcodes.LOR -> binaryLong("|")
            Opcodes.LXOR -> binaryLong("^")
            Opcodes.LNEG -> appendLine(
                "    { jlong value = cstack[--sp].j; " +
                    "cstack[sp++].j = value == ((jlong) -9223372036854775807LL - 1LL) ? value : static_cast<jlong>(-value); }"
            )
            Opcodes.LDIV -> appendLine(
                "    { jlong rhs = cstack[--sp].j; jlong lhs = cstack[--sp].j; " +
                    "if (rhs == 0) { grt_throw(env, \"java/lang/ArithmeticException\", \"/ by zero\"); } " +
                    "else if (lhs == ((jlong) -9223372036854775807LL - 1LL) && rhs == -1LL) { cstack[sp++].j = lhs; } " +
                    "else { cstack[sp++].j = static_cast<jlong>(lhs / rhs); } }"
            )
            Opcodes.LREM -> appendLine(
                "    { jlong rhs = cstack[--sp].j; jlong lhs = cstack[--sp].j; " +
                    "if (rhs == 0) { grt_throw(env, \"java/lang/ArithmeticException\", \"/ by zero\"); } " +
                    "else if (lhs == ((jlong) -9223372036854775807LL - 1LL) && rhs == -1LL) { cstack[sp++].j = 0LL; } " +
                    "else { cstack[sp++].j = static_cast<jlong>(lhs % rhs); } }"
            )
            Opcodes.LSHL -> appendLine(
                "    { jint rhs = cstack[--sp].i & 63; jlong lhs = cstack[--sp].j; " +
                    "cstack[sp++].j = static_cast<jlong>(static_cast<uint64_t>(lhs) << rhs); }"
            )
            Opcodes.LSHR -> appendLine(
                "    { jint rhs = cstack[--sp].i & 63; jlong lhs = cstack[--sp].j; " +
                    "cstack[sp++].j = static_cast<jlong>(lhs >> rhs); }"
            )
            Opcodes.LUSHR -> appendLine(
                "    { jint rhs = cstack[--sp].i & 63; jlong lhs = cstack[--sp].j; " +
                    "cstack[sp++].j = static_cast<jlong>(static_cast<uint64_t>(lhs) >> rhs); }"
            )
            Opcodes.LCMP -> appendLine(
                "    { jlong rhs = cstack[--sp].j; jlong lhs = cstack[--sp].j; " +
                    "cstack[sp++].i = lhs == rhs ? 0 : (lhs < rhs ? -1 : 1); }"
            )
            Opcodes.I2L -> appendLine("    { jint value = cstack[--sp].i; cstack[sp++].j = static_cast<jlong>(value); }")
            Opcodes.L2I -> appendLine("    { jlong value = cstack[--sp].j; cstack[sp++].i = static_cast<jint>(value); }")
            Opcodes.I2F -> appendLine("    { jint value = cstack[--sp].i; cstack[sp++].f = static_cast<jfloat>(value); }")
            Opcodes.I2D -> appendLine("    { jint value = cstack[--sp].i; cstack[sp++].d = static_cast<jdouble>(value); }")
            Opcodes.L2F -> appendLine("    { jlong value = cstack[--sp].j; cstack[sp++].f = static_cast<jfloat>(value); }")
            Opcodes.L2D -> appendLine("    { jlong value = cstack[--sp].j; cstack[sp++].d = static_cast<jdouble>(value); }")
            Opcodes.F2I -> appendLine("    { jfloat value = cstack[--sp].f; cstack[sp++].i = grt_f2i(value); }")
            Opcodes.F2L -> appendLine("    { jfloat value = cstack[--sp].f; cstack[sp++].j = grt_f2l(value); }")
            Opcodes.F2D -> appendLine("    { jfloat value = cstack[--sp].f; cstack[sp++].d = static_cast<jdouble>(value); }")
            Opcodes.D2I -> appendLine("    { jdouble value = cstack[--sp].d; cstack[sp++].i = grt_d2i(value); }")
            Opcodes.D2L -> appendLine("    { jdouble value = cstack[--sp].d; cstack[sp++].j = grt_d2l(value); }")
            Opcodes.D2F -> appendLine("    { jdouble value = cstack[--sp].d; cstack[sp++].f = static_cast<jfloat>(value); }")
            Opcodes.I2B -> appendLine("    { jint value = cstack[--sp].i; cstack[sp++].i = static_cast<jint>(static_cast<jbyte>(value)); }")
            Opcodes.I2C -> appendLine("    { jint value = cstack[--sp].i; cstack[sp++].i = static_cast<jint>(static_cast<jchar>(value)); }")
            Opcodes.I2S -> appendLine("    { jint value = cstack[--sp].i; cstack[sp++].i = static_cast<jint>(static_cast<jshort>(value)); }")
            Opcodes.FADD -> binaryFloat("+")
            Opcodes.FSUB -> binaryFloat("-")
            Opcodes.FMUL -> binaryFloat("*")
            Opcodes.FDIV -> binaryFloat("/")
            Opcodes.FREM -> appendLine("    { jfloat rhs = cstack[--sp].f; jfloat lhs = cstack[--sp].f; cstack[sp++].f = static_cast<jfloat>(std::fmod(lhs, rhs)); }")
            Opcodes.FNEG -> appendLine("    { jfloat value = cstack[--sp].f; cstack[sp++].f = -value; }")
            Opcodes.DADD -> binaryDouble("+")
            Opcodes.DSUB -> binaryDouble("-")
            Opcodes.DMUL -> binaryDouble("*")
            Opcodes.DDIV -> binaryDouble("/")
            Opcodes.DREM -> appendLine("    { jdouble rhs = cstack[--sp].d; jdouble lhs = cstack[--sp].d; cstack[sp++].d = std::fmod(lhs, rhs); }")
            Opcodes.DNEG -> appendLine("    { jdouble value = cstack[--sp].d; cstack[sp++].d = -value; }")
            Opcodes.FCMPL -> compareFloat(nanResult = -1)
            Opcodes.FCMPG -> compareFloat(nanResult = 1)
            Opcodes.DCMPL -> compareDouble(nanResult = -1)
            Opcodes.DCMPG -> compareDouble(nanResult = 1)
            Opcodes.POP -> appendLine("    --sp;")
            Opcodes.POP2 -> emitPop2(instruction, stackShape)
            Opcodes.DUP -> appendLine("    { cstack[sp] = cstack[sp - 1]; ++sp; }")
            Opcodes.DUP_X1 -> emitDupX1(instruction, stackShape)
            Opcodes.DUP_X2 -> emitDupX2(instruction, stackShape)
            Opcodes.DUP2 -> emitDup2(instruction, stackShape)
            Opcodes.DUP2_X1 -> emitDup2X1(instruction, stackShape)
            Opcodes.DUP2_X2 -> emitDup2X2(instruction, stackShape)
            Opcodes.SWAP -> appendLine("    { jvalue tmp = cstack[sp - 1]; cstack[sp - 1] = cstack[sp - 2]; cstack[sp - 2] = tmp; }")
            Opcodes.IALOAD -> emitPrimitiveArrayLoad("jint", "jintArray", "GetIntArrayRegion")
            Opcodes.BALOAD -> emitByteBooleanArrayLoad()
            Opcodes.CALOAD -> emitPrimitiveArrayLoad("jchar", "jcharArray", "GetCharArrayRegion")
            Opcodes.SALOAD -> emitPrimitiveArrayLoad("jshort", "jshortArray", "GetShortArrayRegion")
            Opcodes.LALOAD -> emitPrimitiveArrayLoad("jlong", "jlongArray", "GetLongArrayRegion", "j")
            Opcodes.FALOAD -> emitPrimitiveArrayLoad("jfloat", "jfloatArray", "GetFloatArrayRegion", "f")
            Opcodes.DALOAD -> emitPrimitiveArrayLoad("jdouble", "jdoubleArray", "GetDoubleArrayRegion", "d")
            Opcodes.AALOAD -> emitObjectArrayLoad()
            Opcodes.IASTORE -> emitPrimitiveArrayStore("jint", "jintArray", "SetIntArrayRegion")
            Opcodes.BASTORE -> emitByteBooleanArrayStore()
            Opcodes.CASTORE -> emitPrimitiveArrayStore("jchar", "jcharArray", "SetCharArrayRegion")
            Opcodes.SASTORE -> emitPrimitiveArrayStore("jshort", "jshortArray", "SetShortArrayRegion")
            Opcodes.LASTORE -> emitPrimitiveArrayStore("jlong", "jlongArray", "SetLongArrayRegion", "j")
            Opcodes.FASTORE -> emitPrimitiveArrayStore("jfloat", "jfloatArray", "SetFloatArrayRegion", "f")
            Opcodes.DASTORE -> emitPrimitiveArrayStore("jdouble", "jdoubleArray", "SetDoubleArrayRegion", "d")
            Opcodes.AASTORE -> emitObjectArrayStore()
            Opcodes.ARRAYLENGTH -> appendLine(
                "    { jobject array = cstack[--sp].l; " +
                    "if (array == nullptr) { grt_throw(env, \"java/lang/NullPointerException\", \"ARRAYLENGTH npe\"); } " +
                    "else { cstack[sp++].i = env->GetArrayLength((jarray) array); } }"
            )
            Opcodes.IRETURN -> {
                ensureIntLikeReturn(returnType)
                appendLine("    return static_cast<${cppType(returnType)}>(cstack[--sp].i);")
            }
            Opcodes.LRETURN -> {
                ensureReturnSort(returnType, Type.LONG)
                appendLine("    return cstack[--sp].j;")
            }
            Opcodes.FRETURN -> {
                ensureReturnSort(returnType, Type.FLOAT)
                appendLine("    return cstack[--sp].f;")
            }
            Opcodes.DRETURN -> {
                ensureReturnSort(returnType, Type.DOUBLE)
                appendLine("    return cstack[--sp].d;")
            }
            Opcodes.ARETURN -> {
                ensureReferenceReturn(returnType)
                appendLine("    return cstack[--sp].l;")
            }
            Opcodes.RETURN -> {
                ensureReturnSort(returnType, Type.VOID)
                appendLine("    return;")
            }
            Opcodes.ATHROW -> appendLine(
                "    { jobject exception = cstack[--sp].l; " +
                    "if (exception == nullptr) { grt_throw(env, \"java/lang/NullPointerException\", \"ATHROW npe\"); } " +
                    "else { env->Throw((jthrowable) exception); } }"
            )
            Opcodes.MONITORENTER -> appendLine(
                "    { jobject lock = cstack[--sp].l; " +
                    "if (lock == nullptr) { grt_throw(env, \"java/lang/NullPointerException\", \"MONITORENTER npe\"); } " +
                    "else { env->MonitorEnter(lock); } }"
            )
            Opcodes.MONITOREXIT -> appendLine(
                "    { jobject lock = cstack[--sp].l; " +
                    "if (lock == nullptr) { grt_throw(env, \"java/lang/NullPointerException\", \"MONITOREXIT npe\"); } " +
                    "else { env->MonitorExit(lock); } }"
            )
            else -> emitTypedInstruction(instruction, labels)
        }
    }

    private fun StringBuilder.emitTypedInstruction(
        instruction: NativeJvmInstruction,
        labels: LabelTargetResolver
    ) {
        when (val node = instruction.node) {
            is JumpInsnNode -> emitJumpInstruction(instruction, node, labels)
            is TableSwitchInsnNode -> emitTableSwitchInstruction(node, labels)
            is LookupSwitchInsnNode -> emitLookupSwitchInstruction(node, labels)
            is MethodInsnNode -> emitMethodInstruction(instruction, node)
            is FieldInsnNode -> emitFieldInstruction(instruction, node)
            is TypeInsnNode -> emitTypeInstruction(instruction, node)
            is MultiANewArrayInsnNode -> emitMultiANewArrayInstruction(instruction, node)
            is VarInsnNode -> when (node.opcode) {
                Opcodes.ILOAD -> appendLine("    cstack[sp++].i = clocal[${node.`var`}].i;")
                Opcodes.ISTORE -> appendLine("    clocal[${node.`var`}].i = cstack[--sp].i;")
                Opcodes.LLOAD -> appendLine("    cstack[sp++].j = clocal[${node.`var`}].j;")
                Opcodes.LSTORE -> appendLine("    clocal[${node.`var`}].j = cstack[--sp].j;")
                Opcodes.FLOAD -> appendLine("    cstack[sp++].f = clocal[${node.`var`}].f;")
                Opcodes.FSTORE -> appendLine("    clocal[${node.`var`}].f = cstack[--sp].f;")
                Opcodes.DLOAD -> appendLine("    cstack[sp++].d = clocal[${node.`var`}].d;")
                Opcodes.DSTORE -> appendLine("    clocal[${node.`var`}].d = cstack[--sp].d;")
                Opcodes.ALOAD -> appendLine("    cstack[sp++].l = clocal[${node.`var`}].l;")
                Opcodes.ASTORE -> appendLine("    clocal[${node.`var`}].l = cstack[--sp].l;")
                else -> unsupportedInstruction(instruction)
            }
            is IntInsnNode -> when (node.opcode) {
                Opcodes.BIPUSH,
                Opcodes.SIPUSH -> pushInt(node.operand)
                Opcodes.NEWARRAY -> emitNewPrimitiveArray(instruction, node.operand)
                else -> unsupportedInstruction(instruction)
            }
            is LdcInsnNode -> when (val cst = node.cst) {
                is Int -> pushInt(cst)
                is Long -> pushLong(cst)
                is Float -> pushFloat(cst)
                is Double -> pushDouble(cst)
                is String -> {
                    append("    cstack[sp++].l = env->NewStringUTF(\"")
                        .append(cppString(cst))
                        .appendLine("\");")
                }
                is Type -> {
                    if (cst.sort == Type.METHOD) {
                        emitMethodTypeConstant(instruction, cst)
                    } else {
                        emitClassConstant(instruction, cst)
                    }
                }
                is Handle -> emitMethodHandleConstant(instruction, cst)
                else -> throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedInstruction,
                    "full JVM C++ translator does not support LDC constant ${cst?.javaClass?.name ?: "null"}"
                )
            }
            else -> unsupportedInstruction(instruction)
        }
    }

    private fun StringBuilder.emitFieldInstruction(
        instruction: NativeJvmInstruction,
        node: FieldInsnNode
    ) {
        val fieldType = Type.getType(node.desc)
        validateFieldType(fieldType, "${node.owner}.${node.name}:${node.desc}")
        val ownerClassName = "fieldOwner_${instruction.instructionIndex}"
        val fieldIdName = "fieldId_${instruction.instructionIndex}"
        val isStatic = node.opcode == Opcodes.GETSTATIC || node.opcode == Opcodes.PUTSTATIC

        appendLine("    {")
        if (!isStatic) {
            if (node.opcode == Opcodes.PUTFIELD) popToLocal("fieldValue_${instruction.instructionIndex}", fieldType)
            appendLine("        jobject receiver = cstack[--sp].l;")
            appendLine("        if (receiver == nullptr) {")
            appendLine("            grt_throw(env, \"java/lang/NullPointerException\", \"field receiver npe\");")
            appendLine("        } else {")
        } else if (node.opcode == Opcodes.PUTSTATIC) {
            popToLocal("fieldValue_${instruction.instructionIndex}", fieldType)
        }

        append("        jclass ")
            .append(ownerClassName)
            .append(" = grt_find_class(env, classloader, \"")
            .append(cppString(node.owner))
            .appendLine("\");")
        appendLine("        if ($ownerClassName != nullptr) {")
        append("            jfieldID ")
            .append(fieldIdName)
            .append(" = env->")
            .append(if (isStatic) "GetStaticFieldID" else "GetFieldID")
            .append("(")
            .append(ownerClassName)
            .append(", \"")
            .append(cppString(node.name))
            .append("\", \"")
            .append(cppString(node.desc))
            .appendLine("\");")
        appendLine("            if ($fieldIdName != nullptr) {")
        when (node.opcode) {
            Opcodes.GETSTATIC -> pushFieldValue("env->GetStatic${jniFieldType(fieldType)}Field($ownerClassName, $fieldIdName)", fieldType)
            Opcodes.PUTSTATIC -> appendLine("                env->SetStatic${jniFieldType(fieldType)}Field($ownerClassName, $fieldIdName, fieldValue_${instruction.instructionIndex});")
            Opcodes.GETFIELD -> pushFieldValue("env->Get${jniFieldType(fieldType)}Field(receiver, $fieldIdName)", fieldType)
            Opcodes.PUTFIELD -> appendLine("                env->Set${jniFieldType(fieldType)}Field(receiver, $fieldIdName, fieldValue_${instruction.instructionIndex});")
            else -> unsupportedInstruction(instruction)
        }
        appendLine("            }")
        appendLine("        }")
        if (!isStatic) {
            appendLine("        }")
        }
        appendLine("    }")
    }

    private fun StringBuilder.emitTypeInstruction(
        instruction: NativeJvmInstruction,
        node: TypeInsnNode
    ) {
        when (node.opcode) {
            Opcodes.NEW -> {
                appendLine("    {")
                append("        jclass typeClass = grt_find_class(env, classloader, \"")
                    .append(cppString(node.desc))
                    .appendLine("\");")
                appendLine("        if (typeClass != nullptr) { cstack[sp++].l = env->AllocObject(typeClass); }")
                appendLine("    }")
            }
            Opcodes.CHECKCAST -> {
                appendLine("    {")
                appendLine("        jobject value = cstack[sp - 1].l;")
                appendLine("        if (value != nullptr) {")
                append("            jclass typeClass = grt_find_class(env, classloader, \"")
                    .append(cppString(node.desc))
                    .appendLine("\");")
                appendLine("            if (typeClass != nullptr && !env->IsInstanceOf(value, typeClass)) {")
                append("                grt_throw(env, \"java/lang/ClassCastException\", \"")
                    .append(cppString(node.desc))
                    .appendLine("\");")
                appendLine("            }")
                appendLine("        }")
                appendLine("    }")
            }
            Opcodes.INSTANCEOF -> {
                appendLine("    {")
                appendLine("        jobject value = cstack[--sp].l;")
                appendLine("        if (value == nullptr) {")
                appendLine("            cstack[sp++].i = 0;")
                appendLine("        } else {")
                append("            jclass typeClass = grt_find_class(env, classloader, \"")
                    .append(cppString(node.desc))
                    .appendLine("\");")
                appendLine("            cstack[sp++].i = typeClass != nullptr && env->IsInstanceOf(value, typeClass) ? 1 : 0;")
                appendLine("        }")
                appendLine("    }")
            }
            Opcodes.ANEWARRAY -> {
                appendLine("    {")
                appendLine("        jint count = cstack[--sp].i;")
                appendLine("        if (count < 0) {")
                appendLine("            grt_throw(env, \"java/lang/NegativeArraySizeException\", \"negative array size\");")
                appendLine("        } else {")
                append("            jclass elementClass = grt_find_class(env, classloader, \"")
                    .append(cppString(node.desc))
                    .appendLine("\");")
                appendLine("            if (elementClass != nullptr) { cstack[sp++].l = env->NewObjectArray(count, elementClass, nullptr); }")
                appendLine("        }")
                appendLine("    }")
            }
            else -> unsupportedInstruction(instruction)
        }
    }

    private fun StringBuilder.emitNewPrimitiveArray(
        instruction: NativeJvmInstruction,
        operand: Int
    ) {
        val factory = when (operand) {
            Opcodes.T_BOOLEAN -> "NewBooleanArray"
            Opcodes.T_BYTE -> "NewByteArray"
            Opcodes.T_CHAR -> "NewCharArray"
            Opcodes.T_SHORT -> "NewShortArray"
            Opcodes.T_INT -> "NewIntArray"
            Opcodes.T_LONG -> "NewLongArray"
            Opcodes.T_FLOAT -> "NewFloatArray"
            Opcodes.T_DOUBLE -> "NewDoubleArray"
            else -> unsupportedInstruction(instruction)
        }
        appendLine("    {")
        appendLine("        jint count = cstack[--sp].i;")
        appendLine("        if (count < 0) {")
        appendLine("            grt_throw(env, \"java/lang/NegativeArraySizeException\", \"negative array size\");")
        appendLine("        } else {")
        appendLine("            cstack[sp++].l = env->$factory(count);")
        appendLine("        }")
        appendLine("    }")
    }

    private fun StringBuilder.emitClassConstant(
        instruction: NativeJvmInstruction,
        type: Type
    ) {
        val classObjectName = "classObject_${instruction.instructionIndex}"
        appendLine("    {")
        emitClassObjectLookup(instruction, type, classObjectName, "        ")
        appendLine("        if ($classObjectName != nullptr) { cstack[sp++].l = $classObjectName; }")
        appendLine("    }")
    }

    private fun StringBuilder.emitClassObjectLookup(
        instruction: NativeJvmInstruction,
        type: Type,
        targetName: String,
        indent: String
    ) {
        appendLine("${indent}jobject $targetName = nullptr;")
        when (type.sort) {
            Type.OBJECT,
            Type.ARRAY -> {
                val className = if (type.sort == Type.ARRAY) type.descriptor else type.internalName
                append("${indent}jclass classLookup_${instruction.instructionIndex} = grt_find_class(env, classloader, \"")
                    .append(cppString(className))
                    .appendLine("\");")
                appendLine("${indent}if (classLookup_${instruction.instructionIndex} != nullptr) { $targetName = classLookup_${instruction.instructionIndex}; }")
            }
            Type.VOID,
            Type.BOOLEAN,
            Type.CHAR,
            Type.BYTE,
            Type.SHORT,
            Type.INT,
            Type.LONG,
            Type.FLOAT,
            Type.DOUBLE -> emitPrimitiveClassObjectLookup(instruction, type, targetName, indent)
            else -> throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "full JVM C++ translator does not support class constant Type sort ${type.sort}"
            )
        }
    }

    private fun StringBuilder.emitPrimitiveClassObjectLookup(
        instruction: NativeJvmInstruction,
        type: Type,
        targetName: String,
        indent: String
    ) {
        val wrapper = when (type.sort) {
            Type.VOID -> "java/lang/Void"
            Type.BOOLEAN -> "java/lang/Boolean"
            Type.CHAR -> "java/lang/Character"
            Type.BYTE -> "java/lang/Byte"
            Type.SHORT -> "java/lang/Short"
            Type.INT -> "java/lang/Integer"
            Type.LONG -> "java/lang/Long"
            Type.FLOAT -> "java/lang/Float"
            Type.DOUBLE -> "java/lang/Double"
            else -> unsupportedDescriptor(type.descriptor)
        }
        append("${indent}jclass wrapperClass_${instruction.instructionIndex} = grt_find_class(env, classloader, \"")
            .append(cppString(wrapper))
            .appendLine("\");")
        appendLine("${indent}if (wrapperClass_${instruction.instructionIndex} != nullptr) {")
        appendLine("${indent}    jfieldID typeField_${instruction.instructionIndex} = env->GetStaticFieldID(wrapperClass_${instruction.instructionIndex}, \"TYPE\", \"Ljava/lang/Class;\");")
        appendLine("${indent}    if (typeField_${instruction.instructionIndex} != nullptr) {")
        appendLine("${indent}        $targetName = env->GetStaticObjectField(wrapperClass_${instruction.instructionIndex}, typeField_${instruction.instructionIndex});")
        appendLine("${indent}    }")
        appendLine("${indent}}")
    }

    private fun StringBuilder.emitMethodTypeConstant(
        instruction: NativeJvmInstruction,
        type: Type
    ) {
        if (type.sort != Type.METHOD) unsupportedDescriptor(type.descriptor)
        val methodTypeName = "methodTypeConstant_${instruction.instructionIndex}"
        appendLine("    {")
        emitMethodTypeObject(
            suffix = "constant_${instruction.instructionIndex}",
            descriptor = type.descriptor,
            targetName = methodTypeName,
            indent = "        "
        )
        appendLine("        if ($methodTypeName != nullptr) { cstack[sp++].l = $methodTypeName; }")
        appendLine("    }")
    }

    private fun StringBuilder.emitMethodTypeObject(
        suffix: String,
        descriptor: String,
        targetName: String,
        indent: String
    ) {
        appendLine("${indent}jobject $targetName = nullptr;")
        appendLine("${indent}jclass methodTypeClass_$suffix = grt_find_class(env, classloader, \"java/lang/invoke/MethodType\");")
        appendLine("${indent}if (methodTypeClass_$suffix != nullptr) {")
        appendLine("${indent}    jmethodID fromDescriptor_$suffix = env->GetStaticMethodID(methodTypeClass_$suffix, \"fromMethodDescriptorString\", \"(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;\");")
        appendLine("${indent}    if (fromDescriptor_$suffix != nullptr) {")
        append("${indent}        jstring descriptor_$suffix = env->NewStringUTF(\"")
            .append(cppString(descriptor))
            .appendLine("\");")
        appendLine("${indent}        if (descriptor_$suffix != nullptr) {")
        appendLine("${indent}            jvalue methodTypeArgs_$suffix[2] = {};")
        appendLine("${indent}            methodTypeArgs_$suffix[0].l = descriptor_$suffix;")
        appendLine("${indent}            methodTypeArgs_$suffix[1].l = classloader;")
        appendLine("${indent}            $targetName = env->CallStaticObjectMethodA(methodTypeClass_$suffix, fromDescriptor_$suffix, methodTypeArgs_$suffix);")
        appendLine("${indent}            env->DeleteLocalRef(descriptor_$suffix);")
        appendLine("${indent}        }")
        appendLine("${indent}    }")
        appendLine("${indent}}")
    }

    private fun StringBuilder.emitMethodHandleConstant(
        instruction: NativeJvmInstruction,
        handle: Handle
    ) {
        val suffix = "handle_${instruction.instructionIndex}"
        val resultName = "methodHandle_${instruction.instructionIndex}"
        appendLine("    {")
        appendLine("        jobject $resultName = nullptr;")
        appendLine("        if (lookup == nullptr) { lookup = grt_get_lookup(env, currentClass); }")
        appendLine("        if (lookup != nullptr) {")
        append("            jclass ownerClass_$suffix = grt_find_class(env, classloader, \"")
            .append(cppString(handle.owner))
            .appendLine("\");")
        appendLine("            if (ownerClass_$suffix != nullptr) {")
        when (handle.tag) {
            Opcodes.H_GETFIELD,
            Opcodes.H_GETSTATIC,
            Opcodes.H_PUTFIELD,
            Opcodes.H_PUTSTATIC -> emitFieldMethodHandleLookup(instruction, handle, suffix, resultName)
            Opcodes.H_INVOKEVIRTUAL,
            Opcodes.H_INVOKEINTERFACE,
            Opcodes.H_INVOKESTATIC,
            Opcodes.H_INVOKESPECIAL -> emitMethodMethodHandleLookup(instruction, handle, suffix, resultName)
            Opcodes.H_NEWINVOKESPECIAL -> emitConstructorMethodHandleLookup(instruction, handle, suffix, resultName)
            else -> throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "unsupported MethodHandle tag ${handle.tag}"
            )
        }
        appendLine("            }")
        appendLine("        }")
        appendLine("        if ($resultName != nullptr) { cstack[sp++].l = $resultName; }")
        appendLine("    }")
    }

    private fun StringBuilder.emitFieldMethodHandleLookup(
        instruction: NativeJvmInstruction,
        handle: Handle,
        suffix: String,
        resultName: String
    ) {
        val fieldTypeName = "fieldType_$suffix"
        val findName = when (handle.tag) {
            Opcodes.H_GETFIELD -> "findGetter"
            Opcodes.H_GETSTATIC -> "findStaticGetter"
            Opcodes.H_PUTFIELD -> "findSetter"
            Opcodes.H_PUTSTATIC -> "findStaticSetter"
            else -> throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "unsupported field MethodHandle tag ${handle.tag}"
            )
        }
        append("                jstring memberName_$suffix = env->NewStringUTF(\"")
            .append(cppString(handle.name))
            .appendLine("\");")
        emitClassObjectLookup(instruction, Type.getType(handle.desc), fieldTypeName, "                ")
        appendLine("                if (memberName_$suffix != nullptr && $fieldTypeName != nullptr) {")
        appendLine("                    jmethodID findMethod_$suffix = env->GetMethodID(grt_methodhandles_lookup_class, \"$findName\", \"(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;\");")
        appendLine("                    if (findMethod_$suffix != nullptr) {")
        appendLine("                        jvalue findArgs_$suffix[3] = {};")
        appendLine("                        findArgs_$suffix[0].l = ownerClass_$suffix;")
        appendLine("                        findArgs_$suffix[1].l = memberName_$suffix;")
        appendLine("                        findArgs_$suffix[2].l = $fieldTypeName;")
        appendLine("                        $resultName = env->CallObjectMethodA(lookup, findMethod_$suffix, findArgs_$suffix);")
        appendLine("                    }")
        appendLine("                }")
        appendLine("                if (memberName_$suffix != nullptr) env->DeleteLocalRef(memberName_$suffix);")
    }

    private fun StringBuilder.emitMethodMethodHandleLookup(
        instruction: NativeJvmInstruction,
        handle: Handle,
        suffix: String,
        resultName: String
    ) {
        val methodTypeName = "methodType_$suffix"
        val findName = when (handle.tag) {
            Opcodes.H_INVOKEVIRTUAL,
            Opcodes.H_INVOKEINTERFACE -> "findVirtual"
            Opcodes.H_INVOKESTATIC -> "findStatic"
            Opcodes.H_INVOKESPECIAL -> "findSpecial"
            else -> throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedInstruction,
                "unsupported invoke MethodHandle tag ${handle.tag}"
            )
        }
        val findDesc = if (handle.tag == Opcodes.H_INVOKESPECIAL) {
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;"
        } else {
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
        }
        append("                jstring memberName_$suffix = env->NewStringUTF(\"")
            .append(cppString(handle.name))
            .appendLine("\");")
        emitMethodTypeObject(
            suffix = "${suffix}_type",
            descriptor = handle.desc,
            targetName = methodTypeName,
            indent = "                "
        )
        appendLine("                if (memberName_$suffix != nullptr && $methodTypeName != nullptr) {")
        appendLine("                    jmethodID findMethod_$suffix = env->GetMethodID(grt_methodhandles_lookup_class, \"$findName\", \"$findDesc\");")
        appendLine("                    if (findMethod_$suffix != nullptr) {")
        val argCount = if (handle.tag == Opcodes.H_INVOKESPECIAL) 4 else 3
        appendLine("                        jvalue findArgs_$suffix[$argCount] = {};")
        appendLine("                        findArgs_$suffix[0].l = ownerClass_$suffix;")
        appendLine("                        findArgs_$suffix[1].l = memberName_$suffix;")
        appendLine("                        findArgs_$suffix[2].l = $methodTypeName;")
        if (handle.tag == Opcodes.H_INVOKESPECIAL) {
            appendLine("                        findArgs_$suffix[3].l = currentClass;")
        }
        appendLine("                        $resultName = env->CallObjectMethodA(lookup, findMethod_$suffix, findArgs_$suffix);")
        appendLine("                    }")
        appendLine("                }")
        appendLine("                if (memberName_$suffix != nullptr) env->DeleteLocalRef(memberName_$suffix);")
    }

    private fun StringBuilder.emitConstructorMethodHandleLookup(
        instruction: NativeJvmInstruction,
        handle: Handle,
        suffix: String,
        resultName: String
    ) {
        val methodTypeName = "methodType_$suffix"
        emitMethodTypeObject(
            suffix = "${suffix}_ctor_type",
            descriptor = handle.desc,
            targetName = methodTypeName,
            indent = "                "
        )
        appendLine("                if ($methodTypeName != nullptr) {")
        appendLine("                    jmethodID findMethod_$suffix = env->GetMethodID(grt_methodhandles_lookup_class, \"findConstructor\", \"(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;\");")
        appendLine("                    if (findMethod_$suffix != nullptr) {")
        appendLine("                        jvalue findArgs_$suffix[2] = {};")
        appendLine("                        findArgs_$suffix[0].l = ownerClass_$suffix;")
        appendLine("                        findArgs_$suffix[1].l = $methodTypeName;")
        appendLine("                        $resultName = env->CallObjectMethodA(lookup, findMethod_$suffix, findArgs_$suffix);")
        appendLine("                    }")
        appendLine("                }")
    }

    private fun StringBuilder.emitMultiANewArrayInstruction(
        instruction: NativeJvmInstruction,
        node: MultiANewArrayInsnNode
    ) {
        val componentType = multiArrayComponentType(node.desc, node.dims)
        val dimsArrayName = "dimensions_${instruction.instructionIndex}"
        val dimsJniArrayName = "dimensionsArray_${instruction.instructionIndex}"
        val componentClassName = "componentClass_${instruction.instructionIndex}"
        appendLine("    {")
        appendLine("        jint $dimsArrayName[${node.dims}] = {};")
        for (index in node.dims - 1 downTo 0) {
            appendLine("        $dimsArrayName[$index] = cstack[--sp].i;")
        }
        appendLine("        jintArray $dimsJniArrayName = env->NewIntArray(${node.dims});")
        appendLine("        if ($dimsJniArrayName != nullptr) {")
        appendLine("            env->SetIntArrayRegion($dimsJniArrayName, 0, ${node.dims}, $dimsArrayName);")
        appendLine("            if (!env->ExceptionCheck()) {")
        emitClassObjectLookup(instruction, componentType, componentClassName, "                ")
        appendLine("                if ($componentClassName != nullptr) {")
        appendLine("                    jclass reflectArrayClass_${instruction.instructionIndex} = grt_find_class(env, classloader, \"java/lang/reflect/Array\");")
        appendLine("                    if (reflectArrayClass_${instruction.instructionIndex} != nullptr) {")
        appendLine("                        jmethodID newInstance_${instruction.instructionIndex} = env->GetStaticMethodID(reflectArrayClass_${instruction.instructionIndex}, \"newInstance\", \"(Ljava/lang/Class;[I)Ljava/lang/Object;\");")
        appendLine("                        if (newInstance_${instruction.instructionIndex} != nullptr) {")
        appendLine("                            jvalue args_${instruction.instructionIndex}[2] = {};")
        appendLine("                            args_${instruction.instructionIndex}[0].l = $componentClassName;")
        appendLine("                            args_${instruction.instructionIndex}[1].l = $dimsJniArrayName;")
        appendLine("                            cstack[sp++].l = env->CallStaticObjectMethodA(reflectArrayClass_${instruction.instructionIndex}, newInstance_${instruction.instructionIndex}, args_${instruction.instructionIndex});")
        appendLine("                        }")
        appendLine("                    }")
        appendLine("                }")
        appendLine("            }")
        appendLine("        }")
        appendLine("    }")
    }

    private fun multiArrayComponentType(desc: String, dims: Int): Type {
        if (dims <= 0 || dims > desc.length || !desc.take(dims).all { it == '[' }) {
            throw UnsupportedNativeInstruction(
                NativeSkipReason.UnsupportedDescriptor,
                "invalid MULTIANEWARRAY descriptor=$desc dims=$dims"
            )
        }
        return Type.getType(desc.substring(dims))
    }

    private fun StringBuilder.emitMethodInstruction(
        instruction: NativeJvmInstruction,
        node: MethodInsnNode
    ) {
        val argumentTypes = Type.getArgumentTypes(node.desc)
        val returnType = Type.getReturnType(node.desc)
        validateInvokeDescriptor(argumentTypes, returnType, "${node.owner}.${node.name}${node.desc}")

        val argumentCount = argumentTypes.size
        val argsName = "args_${instruction.instructionIndex}"
        appendLine("    {")
        if (argumentCount > 0) {
            appendLine("        jvalue $argsName[$argumentCount] = {};")
            for (index in argumentTypes.indices.reversed()) {
                val argument = argumentTypes[index]
                when (argument.sort) {
                    in IntLikeSorts -> appendLine("        $argsName[$index].${jvalueField(argument)} = static_cast<${cppType(argument)}>(cstack[--sp].i);")
                    Type.LONG -> appendLine("        $argsName[$index].j = cstack[--sp].j;")
                    Type.FLOAT -> appendLine("        $argsName[$index].f = cstack[--sp].f;")
                    Type.DOUBLE -> appendLine("        $argsName[$index].d = cstack[--sp].d;")
                    Type.OBJECT,
                    Type.ARRAY -> appendLine("        $argsName[$index].l = cstack[--sp].l;")
                    else -> unsupportedDescriptor(node.desc)
                }
            }
        }

        val argsExpression = if (argumentCount > 0) argsName else "nullptr"
        val isStatic = node.opcode == Opcodes.INVOKESTATIC
        val ownerClassName = "ownerClass_${instruction.instructionIndex}"
        val methodIdName = "methodId_${instruction.instructionIndex}"
        if (!isStatic) {
            appendLine("        jobject receiver = cstack[--sp].l;")
            appendLine("        if (receiver == nullptr) {")
            appendLine("            grt_throw(env, \"java/lang/NullPointerException\", \"invoke receiver npe\");")
            appendLine("        } else {")
        }
        append("        jclass ")
            .append(ownerClassName)
            .append(" = grt_find_class(env, classloader, \"")
            .append(cppString(node.owner))
            .appendLine("\");")
        appendLine("        if ($ownerClassName != nullptr) {")
        append("            jmethodID ")
            .append(methodIdName)
            .append(" = env->")
            .append(if (isStatic) "GetStaticMethodID" else "GetMethodID")
            .append("(")
            .append(ownerClassName)
            .append(", \"")
            .append(cppString(node.name))
            .append("\", \"")
            .append(cppString(node.desc))
            .appendLine("\");")
        appendLine("            if ($methodIdName != nullptr) {")
        emitJniMethodCall(node, returnType, ownerClassName, methodIdName, argsExpression)
        appendLine("            }")
        appendLine("        }")
        if (!isStatic) {
            appendLine("        }")
        }
        appendLine("    }")
    }

    private fun StringBuilder.emitJniMethodCall(
        node: MethodInsnNode,
        returnType: Type,
        ownerClassName: String,
        methodIdName: String,
        argsExpression: String
    ) {
        val isStatic = node.opcode == Opcodes.INVOKESTATIC
        val callPrefix = when (node.opcode) {
            Opcodes.INVOKESTATIC -> "CallStatic"
            Opcodes.INVOKESPECIAL -> "CallNonvirtual"
            Opcodes.INVOKEVIRTUAL,
            Opcodes.INVOKEINTERFACE -> "Call"
            else -> unsupportedInstruction(
                NativeJvmInstruction(-1, node.opcode, node.type, node, emptyList())
            )
        }
        val receiverPrefix = when (node.opcode) {
            Opcodes.INVOKESTATIC -> "$ownerClassName, $methodIdName"
            Opcodes.INVOKESPECIAL -> "receiver, $ownerClassName, $methodIdName"
            Opcodes.INVOKEVIRTUAL,
            Opcodes.INVOKEINTERFACE -> "receiver, $methodIdName"
            else -> unsupportedDescriptor(node.desc)
        }
        val call = "env->$callPrefix${jniCallType(returnType)}MethodA($receiverPrefix, $argsExpression)"
        when (returnType.sort) {
            Type.VOID -> appendLine("                $call;")
            in IntLikeSorts -> appendLine("                cstack[sp++].i = static_cast<jint>($call);")
            Type.LONG -> appendLine("                cstack[sp++].j = $call;")
            Type.FLOAT -> appendLine("                cstack[sp++].f = $call;")
            Type.DOUBLE -> appendLine("                cstack[sp++].d = $call;")
            Type.OBJECT,
            Type.ARRAY -> appendLine("                cstack[sp++].l = $call;")
            else -> unsupportedDescriptor(node.desc)
        }
        if (isStatic) {
            appendLine("                (void) $ownerClassName;")
        }
    }

    private fun StringBuilder.emitJumpInstruction(
        instruction: NativeJvmInstruction,
        node: JumpInsnNode,
        labels: LabelTargetResolver
    ) {
        val target = labels.cppLabel(node.label)
        when (node.opcode) {
            Opcodes.GOTO -> appendLine("    goto $target;")
            Opcodes.IFEQ -> unaryIntJump("==", "0", target)
            Opcodes.IFNE -> unaryIntJump("!=", "0", target)
            Opcodes.IFLT -> unaryIntJump("<", "0", target)
            Opcodes.IFGE -> unaryIntJump(">=", "0", target)
            Opcodes.IFGT -> unaryIntJump(">", "0", target)
            Opcodes.IFLE -> unaryIntJump("<=", "0", target)
            Opcodes.IF_ICMPEQ -> binaryIntJump("==", target)
            Opcodes.IF_ICMPNE -> binaryIntJump("!=", target)
            Opcodes.IF_ICMPLT -> binaryIntJump("<", target)
            Opcodes.IF_ICMPGE -> binaryIntJump(">=", target)
            Opcodes.IF_ICMPGT -> binaryIntJump(">", target)
            Opcodes.IF_ICMPLE -> binaryIntJump("<=", target)
            Opcodes.IF_ACMPEQ -> binaryRefJump(expectSame = true, target)
            Opcodes.IF_ACMPNE -> binaryRefJump(expectSame = false, target)
            Opcodes.IFNULL -> nullRefJump(expectNull = true, target)
            Opcodes.IFNONNULL -> nullRefJump(expectNull = false, target)
            else -> unsupportedInstruction(instruction)
        }
    }

    private fun StringBuilder.emitTableSwitchInstruction(
        node: TableSwitchInsnNode,
        labels: LabelTargetResolver
    ) {
        appendLine("    switch (cstack[--sp].i) {")
        node.labels.forEachIndexed { index, label ->
            appendLine("        case ${node.min + index}: goto ${labels.cppLabel(label)};")
        }
        appendLine("        default: goto ${labels.cppLabel(node.dflt)};")
        appendLine("    }")
    }

    private fun StringBuilder.emitLookupSwitchInstruction(
        node: LookupSwitchInsnNode,
        labels: LabelTargetResolver
    ) {
        appendLine("    switch (cstack[--sp].i) {")
        node.keys.forEachIndexed { index, key ->
            appendLine("        case $key: goto ${labels.cppLabel(node.labels[index])};")
        }
        appendLine("        default: goto ${labels.cppLabel(node.dflt)};")
        appendLine("    }")
    }

    private fun StringBuilder.appendExceptionCheck(
        instruction: NativeJvmInstruction,
        dispatchLabel: String?,
        returnType: Type
    ) {
        if (dispatchLabel != null) {
            appendLine("    if (env->ExceptionCheck()) {")
            appendLine("        jthrowable exception = env->ExceptionOccurred();")
            appendLine("        env->ExceptionClear();")
            appendLine("        sp = 1;")
            appendLine("        cstack[0].l = exception;")
            appendLine("        goto $dispatchLabel;")
            appendLine("    }")
        } else if (canThrow(instruction.opcode)) {
            appendLine("    if (env->ExceptionCheck()) { ${defaultReturn(returnType)} }")
        }
    }

    private fun StringBuilder.pushInt(value: Int) {
        append("    cstack[sp++].i = static_cast<jint>(")
            .append(String.format(Locale.US, "%d", value))
            .appendLine(");")
    }

    private fun StringBuilder.pushLong(value: Long) {
        append("    cstack[sp++].j = static_cast<jlong>(")
            .append(longLiteral(value))
            .appendLine(");")
    }

    private fun StringBuilder.pushFloat(value: Float) {
        append("    cstack[sp++].f = static_cast<jfloat>(")
            .append(floatLiteral(value))
            .appendLine(");")
    }

    private fun StringBuilder.pushDouble(value: Double) {
        append("    cstack[sp++].d = static_cast<jdouble>(")
            .append(doubleLiteral(value))
            .appendLine(");")
    }

    private fun StringBuilder.binaryInt(operator: String) {
        appendLine("    { jint rhs = cstack[--sp].i; jint lhs = cstack[--sp].i; cstack[sp++].i = static_cast<jint>(lhs $operator rhs); }")
    }

    private fun StringBuilder.binaryLong(operator: String) {
        appendLine("    { jlong rhs = cstack[--sp].j; jlong lhs = cstack[--sp].j; cstack[sp++].j = static_cast<jlong>(lhs $operator rhs); }")
    }

    private fun StringBuilder.binaryLongWrapped(operator: String) {
        appendLine(
            "    { jlong rhs = cstack[--sp].j; jlong lhs = cstack[--sp].j; " +
                "cstack[sp++].j = static_cast<jlong>(static_cast<uint64_t>(lhs) $operator static_cast<uint64_t>(rhs)); }"
        )
    }

    private fun StringBuilder.binaryFloat(operator: String) {
        appendLine("    { jfloat rhs = cstack[--sp].f; jfloat lhs = cstack[--sp].f; cstack[sp++].f = static_cast<jfloat>(lhs $operator rhs); }")
    }

    private fun StringBuilder.binaryDouble(operator: String) {
        appendLine("    { jdouble rhs = cstack[--sp].d; jdouble lhs = cstack[--sp].d; cstack[sp++].d = static_cast<jdouble>(lhs $operator rhs); }")
    }

    private fun StringBuilder.compareFloat(nanResult: Int) {
        appendLine(
            "    { jfloat rhs = cstack[--sp].f; jfloat lhs = cstack[--sp].f; " +
                "cstack[sp++].i = std::isnan(lhs) || std::isnan(rhs) ? $nanResult : (lhs == rhs ? 0 : (lhs < rhs ? -1 : 1)); }"
        )
    }

    private fun StringBuilder.compareDouble(nanResult: Int) {
        appendLine(
            "    { jdouble rhs = cstack[--sp].d; jdouble lhs = cstack[--sp].d; " +
                "cstack[sp++].i = std::isnan(lhs) || std::isnan(rhs) ? $nanResult : (lhs == rhs ? 0 : (lhs < rhs ? -1 : 1)); }"
        )
    }

    private fun StringBuilder.emitPop2(
        instruction: NativeJvmInstruction,
        stackShape: StackShapeAnalyzer
    ) {
        val topSize = stackShape.stackValueSize(instruction, depthFromTop = 0)
        if (topSize == 2) {
            appendLine("    --sp;")
        } else {
            val nextSize = stackShape.stackValueSize(instruction, depthFromTop = 1)
            if (nextSize == 1) {
                appendLine("    sp -= 2;")
            } else {
                unsupportedStackShape(instruction, "POP2")
            }
        }
    }

    private fun StringBuilder.emitDupX1(
        instruction: NativeJvmInstruction,
        stackShape: StackShapeAnalyzer
    ) {
        val topSize = stackShape.stackValueSize(instruction, depthFromTop = 0)
        val nextSize = stackShape.stackValueSize(instruction, depthFromTop = 1)
        if (topSize == 1 && nextSize == 1) {
            appendLine(
                "    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; " +
                    "cstack[sp - 2] = value1; cstack[sp - 1] = value2; cstack[sp++] = value1; }"
            )
        } else {
            unsupportedStackShape(instruction, "DUP_X1")
        }
    }

    private fun StringBuilder.emitDupX2(
        instruction: NativeJvmInstruction,
        stackShape: StackShapeAnalyzer
    ) {
        val topSize = stackShape.stackValueSize(instruction, depthFromTop = 0)
        if (topSize != 1) unsupportedStackShape(instruction, "DUP_X2")

        val nextSize = stackShape.stackValueSize(instruction, depthFromTop = 1)
        if (nextSize == 2) {
            appendLine(
                "    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; " +
                    "cstack[sp - 2] = value1; cstack[sp - 1] = value2; cstack[sp++] = value1; }"
            )
            return
        }

        val thirdSize = stackShape.stackValueSize(instruction, depthFromTop = 2)
        if (nextSize == 1 && thirdSize == 1) {
            appendLine(
                "    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; jvalue value3 = cstack[sp - 3]; " +
                    "cstack[sp - 3] = value1; cstack[sp - 2] = value3; cstack[sp - 1] = value2; cstack[sp++] = value1; }"
            )
        } else {
            unsupportedStackShape(instruction, "DUP_X2")
        }
    }

    private fun StringBuilder.emitDup2(
        instruction: NativeJvmInstruction,
        stackShape: StackShapeAnalyzer
    ) {
        val topSize = stackShape.stackValueSize(instruction, depthFromTop = 0)
        if (topSize == 2) {
            appendLine("    { cstack[sp] = cstack[sp - 1]; ++sp; }")
        } else {
            val nextSize = stackShape.stackValueSize(instruction, depthFromTop = 1)
            if (nextSize == 1) {
                appendLine("    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; cstack[sp++] = value2; cstack[sp++] = value1; }")
            } else {
                unsupportedStackShape(instruction, "DUP2")
            }
        }
    }

    private fun StringBuilder.emitDup2X1(
        instruction: NativeJvmInstruction,
        stackShape: StackShapeAnalyzer
    ) {
        val topSize = stackShape.stackValueSize(instruction, depthFromTop = 0)
        if (topSize == 2) {
            val nextSize = stackShape.stackValueSize(instruction, depthFromTop = 1)
            if (nextSize == 1) {
                appendLine(
                    "    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; " +
                        "cstack[sp - 2] = value1; cstack[sp - 1] = value2; cstack[sp++] = value1; }"
                )
            } else {
                unsupportedStackShape(instruction, "DUP2_X1")
            }
            return
        }

        val secondSize = stackShape.stackValueSize(instruction, depthFromTop = 1)
        val thirdSize = stackShape.stackValueSize(instruction, depthFromTop = 2)
        if (topSize == 1 && secondSize == 1 && thirdSize == 1) {
            appendLine(
                "    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; jvalue value3 = cstack[sp - 3]; " +
                    "cstack[sp - 3] = value2; cstack[sp - 2] = value1; cstack[sp - 1] = value3; " +
                    "cstack[sp++] = value2; cstack[sp++] = value1; }"
            )
        } else {
            unsupportedStackShape(instruction, "DUP2_X1")
        }
    }

    private fun StringBuilder.emitDup2X2(
        instruction: NativeJvmInstruction,
        stackShape: StackShapeAnalyzer
    ) {
        val topSize = stackShape.stackValueSize(instruction, depthFromTop = 0)
        if (topSize == 2) {
            val secondSize = stackShape.stackValueSize(instruction, depthFromTop = 1)
            if (secondSize == 2) {
                appendLine(
                    "    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; " +
                        "cstack[sp - 2] = value1; cstack[sp - 1] = value2; cstack[sp++] = value1; }"
                )
                return
            }

            val thirdSize = stackShape.stackValueSize(instruction, depthFromTop = 2)
            if (secondSize == 1 && thirdSize == 1) {
                appendLine(
                    "    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; jvalue value3 = cstack[sp - 3]; " +
                        "cstack[sp - 3] = value1; cstack[sp - 2] = value3; cstack[sp - 1] = value2; cstack[sp++] = value1; }"
                )
                return
            }
            unsupportedStackShape(instruction, "DUP2_X2")
        }

        val secondSize = stackShape.stackValueSize(instruction, depthFromTop = 1)
        if (topSize == 1 && secondSize == 1) {
            val thirdSize = stackShape.stackValueSize(instruction, depthFromTop = 2)
            if (thirdSize == 2) {
                appendLine(
                    "    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; jvalue value3 = cstack[sp - 3]; " +
                        "cstack[sp - 3] = value2; cstack[sp - 2] = value1; cstack[sp - 1] = value3; " +
                        "cstack[sp++] = value2; cstack[sp++] = value1; }"
                )
                return
            }

            val fourthSize = stackShape.stackValueSize(instruction, depthFromTop = 3)
            if (thirdSize == 1 && fourthSize == 1) {
                appendLine(
                    "    { jvalue value1 = cstack[sp - 1]; jvalue value2 = cstack[sp - 2]; " +
                        "jvalue value3 = cstack[sp - 3]; jvalue value4 = cstack[sp - 4]; " +
                        "cstack[sp - 4] = value2; cstack[sp - 3] = value1; cstack[sp - 2] = value4; cstack[sp - 1] = value3; " +
                        "cstack[sp++] = value2; cstack[sp++] = value1; }"
                )
                return
            }
        }

        unsupportedStackShape(instruction, "DUP2_X2")
    }

    private fun StringBuilder.emitPrimitiveArrayLoad(
        elementType: String,
        arrayType: String,
        getter: String,
        stackField: String = "i"
    ) {
        appendLine("    {")
        appendLine("        jint index = cstack[--sp].i;")
        appendLine("        jobject array = cstack[--sp].l;")
        appendLine("        if (array == nullptr) {")
        appendLine("            grt_throw(env, \"java/lang/NullPointerException\", \"array load npe\");")
        appendLine("        } else {")
        appendLine("            $elementType value = 0;")
        appendLine("            env->$getter(($arrayType) array, index, 1, &value);")
        if (stackField == "j") {
            appendLine("            cstack[sp++].j = static_cast<jlong>(value);")
        } else if (stackField == "f") {
            appendLine("            cstack[sp++].f = static_cast<jfloat>(value);")
        } else if (stackField == "d") {
            appendLine("            cstack[sp++].d = static_cast<jdouble>(value);")
        } else {
            appendLine("            cstack[sp++].i = static_cast<jint>(value);")
        }
        appendLine("        }")
        appendLine("    }")
    }

    private fun StringBuilder.emitByteBooleanArrayLoad() {
        appendLine("    {")
        appendLine("        jint index = cstack[--sp].i;")
        appendLine("        jobject array = cstack[--sp].l;")
        appendLine("        if (array == nullptr) {")
        appendLine("            grt_throw(env, \"java/lang/NullPointerException\", \"array load npe\");")
        appendLine("        } else {")
        appendLine("            cstack[sp++].i = grt_baload(env, (jarray) array, index);")
        appendLine("        }")
        appendLine("    }")
    }

    private fun StringBuilder.emitObjectArrayLoad() {
        appendLine("    {")
        appendLine("        jint index = cstack[--sp].i;")
        appendLine("        jobject array = cstack[--sp].l;")
        appendLine("        if (array == nullptr) {")
        appendLine("            grt_throw(env, \"java/lang/NullPointerException\", \"array load npe\");")
        appendLine("        } else {")
        appendLine("            cstack[sp++].l = env->GetObjectArrayElement((jobjectArray) array, index);")
        appendLine("        }")
        appendLine("    }")
    }

    private fun StringBuilder.emitPrimitiveArrayStore(
        elementType: String,
        arrayType: String,
        setter: String,
        stackField: String = "i"
    ) {
        appendLine("    {")
        appendLine("        $elementType value = static_cast<$elementType>(cstack[--sp].$stackField);")
        appendLine("        jint index = cstack[--sp].i;")
        appendLine("        jobject array = cstack[--sp].l;")
        appendLine("        if (array == nullptr) {")
        appendLine("            grt_throw(env, \"java/lang/NullPointerException\", \"array store npe\");")
        appendLine("        } else {")
        appendLine("            env->$setter(($arrayType) array, index, 1, &value);")
        appendLine("        }")
        appendLine("    }")
    }

    private fun StringBuilder.emitByteBooleanArrayStore() {
        appendLine("    {")
        appendLine("        jint value = cstack[--sp].i;")
        appendLine("        jint index = cstack[--sp].i;")
        appendLine("        jobject array = cstack[--sp].l;")
        appendLine("        if (array == nullptr) {")
        appendLine("            grt_throw(env, \"java/lang/NullPointerException\", \"array store npe\");")
        appendLine("        } else {")
        appendLine("            grt_bastore(env, (jarray) array, index, value);")
        appendLine("        }")
        appendLine("    }")
    }

    private fun StringBuilder.emitObjectArrayStore() {
        appendLine("    {")
        appendLine("        jobject value = cstack[--sp].l;")
        appendLine("        jint index = cstack[--sp].i;")
        appendLine("        jobject array = cstack[--sp].l;")
        appendLine("        if (array == nullptr) {")
        appendLine("            grt_throw(env, \"java/lang/NullPointerException\", \"array store npe\");")
        appendLine("        } else {")
        appendLine("            env->SetObjectArrayElement((jobjectArray) array, index, value);")
        appendLine("        }")
        appendLine("    }")
    }

    private fun StringBuilder.popToLocal(name: String, type: Type) {
        when (type.sort) {
            in IntLikeSorts -> appendLine("        ${cppType(type)} $name = static_cast<${cppType(type)}>(cstack[--sp].i);")
            Type.LONG -> appendLine("        jlong $name = cstack[--sp].j;")
            Type.FLOAT -> appendLine("        jfloat $name = cstack[--sp].f;")
            Type.DOUBLE -> appendLine("        jdouble $name = cstack[--sp].d;")
            Type.OBJECT,
            Type.ARRAY -> appendLine("        jobject $name = cstack[--sp].l;")
            else -> unsupportedDescriptor(type.descriptor)
        }
    }

    private fun StringBuilder.pushFieldValue(expression: String, type: Type) {
        when (type.sort) {
            in IntLikeSorts -> appendLine("                cstack[sp++].i = static_cast<jint>($expression);")
            Type.LONG -> appendLine("                cstack[sp++].j = $expression;")
            Type.FLOAT -> appendLine("                cstack[sp++].f = $expression;")
            Type.DOUBLE -> appendLine("                cstack[sp++].d = $expression;")
            Type.OBJECT,
            Type.ARRAY -> appendLine("                cstack[sp++].l = $expression;")
            else -> unsupportedDescriptor(type.descriptor)
        }
    }

    private fun StringBuilder.unaryIntJump(operator: String, rhs: String, target: String) {
        appendLine("    { jint value = cstack[--sp].i; if (value $operator $rhs) goto $target; }")
    }

    private fun StringBuilder.binaryIntJump(operator: String, target: String) {
        appendLine("    { jint rhs = cstack[--sp].i; jint lhs = cstack[--sp].i; if (lhs $operator rhs) goto $target; }")
    }

    private fun StringBuilder.binaryRefJump(expectSame: Boolean, target: String) {
        val condition = if (expectSame) "" else "!"
        appendLine("    { jobject rhs = cstack[--sp].l; jobject lhs = cstack[--sp].l; if (${condition}env->IsSameObject(lhs, rhs)) goto $target; }")
    }

    private fun StringBuilder.nullRefJump(expectNull: Boolean, target: String) {
        val condition = if (expectNull) "" else "!"
        appendLine("    { jobject value = cstack[--sp].l; if (${condition}env->IsSameObject(value, nullptr)) goto $target; }")
    }

    private fun validateDescriptor(arguments: Array<Type>, returnType: Type, descriptor: String) {
        arguments.forEach { argument ->
            when (argument.sort) {
                in IntLikeSorts,
                Type.LONG,
                Type.FLOAT,
                Type.DOUBLE,
                Type.OBJECT,
                Type.ARRAY -> Unit
                else -> unsupportedDescriptor(descriptor)
            }
        }
        when (returnType.sort) {
            Type.VOID,
            in IntLikeSorts,
            Type.LONG,
            Type.FLOAT,
            Type.DOUBLE,
            Type.OBJECT,
            Type.ARRAY -> Unit
            else -> unsupportedDescriptor(descriptor)
        }
    }

    private fun cppReturnType(type: Type): String {
        return "static ${cppType(type)}"
    }

    private fun cppArgumentType(type: Type): String {
        return cppType(type)
    }

    private fun cppType(type: Type): String {
        return when (type.sort) {
            Type.VOID -> "void"
            Type.BOOLEAN -> "jboolean"
            Type.CHAR -> "jchar"
            Type.BYTE -> "jbyte"
            Type.SHORT -> "jshort"
            Type.INT -> "jint"
            Type.LONG -> "jlong"
            Type.FLOAT -> "jfloat"
            Type.DOUBLE -> "jdouble"
            Type.OBJECT,
            Type.ARRAY -> "jobject"
            else -> unsupportedDescriptor(type.descriptor)
        }
    }

    private fun validateInvokeDescriptor(arguments: Array<Type>, returnType: Type, descriptor: String) {
        arguments.forEach { argument ->
            when (argument.sort) {
                in IntLikeSorts,
                Type.LONG,
                Type.FLOAT,
                Type.DOUBLE,
                Type.OBJECT,
                Type.ARRAY -> Unit
                else -> unsupportedDescriptor(descriptor)
            }
        }
        when (returnType.sort) {
            Type.VOID,
            in IntLikeSorts,
            Type.LONG,
            Type.FLOAT,
            Type.DOUBLE,
            Type.OBJECT,
            Type.ARRAY -> Unit
            else -> unsupportedDescriptor(descriptor)
        }
    }

    private fun validateFieldType(type: Type, field: String) {
        when (type.sort) {
            in IntLikeSorts,
            Type.LONG,
            Type.FLOAT,
            Type.DOUBLE,
            Type.OBJECT,
            Type.ARRAY -> Unit
            else -> unsupportedDescriptor(field)
        }
    }

    private fun ensureReturnSort(returnType: Type, expectedSort: Int) {
        if (returnType.sort != expectedSort) {
            unsupportedDescriptor(returnType.descriptor)
        }
    }

    private fun ensureIntLikeReturn(returnType: Type) {
        if (returnType.sort !in IntLikeSorts) {
            unsupportedDescriptor(returnType.descriptor)
        }
    }

    private fun ensureReferenceReturn(returnType: Type) {
        if (returnType.sort != Type.OBJECT && returnType.sort != Type.ARRAY) {
            unsupportedDescriptor(returnType.descriptor)
        }
    }

    private fun defaultReturn(returnType: Type): String {
        return when (returnType.sort) {
            Type.VOID -> "return;"
            in IntLikeSorts -> "return static_cast<${cppType(returnType)}>(0);"
            Type.LONG -> "return static_cast<jlong>(0);"
            Type.FLOAT -> "return static_cast<jfloat>(0);"
            Type.DOUBLE -> "return static_cast<jdouble>(0);"
            Type.OBJECT,
            Type.ARRAY -> "return nullptr;"
            else -> unsupportedDescriptor(returnType.descriptor)
        }
    }

    private fun canThrow(opcode: Int): Boolean {
        return opcode == Opcodes.IDIV ||
            opcode == Opcodes.IREM ||
            opcode == Opcodes.LDIV ||
            opcode == Opcodes.LREM ||
            opcode == Opcodes.ATHROW ||
            opcode == Opcodes.MONITORENTER ||
            opcode == Opcodes.MONITOREXIT ||
            opcode == Opcodes.INVOKESTATIC ||
            opcode == Opcodes.INVOKESPECIAL ||
            opcode == Opcodes.INVOKEVIRTUAL ||
            opcode == Opcodes.INVOKEINTERFACE ||
            opcode == Opcodes.LDC ||
            opcode == Opcodes.GETSTATIC ||
            opcode == Opcodes.PUTSTATIC ||
            opcode == Opcodes.GETFIELD ||
            opcode == Opcodes.PUTFIELD ||
            opcode == Opcodes.NEW ||
            opcode == Opcodes.CHECKCAST ||
            opcode == Opcodes.INSTANCEOF ||
            opcode == Opcodes.ANEWARRAY ||
            opcode == Opcodes.MULTIANEWARRAY ||
            opcode == Opcodes.NEWARRAY ||
            opcode == Opcodes.ARRAYLENGTH ||
            opcode == Opcodes.IALOAD ||
            opcode == Opcodes.BALOAD ||
            opcode == Opcodes.CALOAD ||
            opcode == Opcodes.SALOAD ||
            opcode == Opcodes.LALOAD ||
            opcode == Opcodes.FALOAD ||
            opcode == Opcodes.DALOAD ||
            opcode == Opcodes.AALOAD ||
            opcode == Opcodes.IASTORE ||
            opcode == Opcodes.BASTORE ||
            opcode == Opcodes.CASTORE ||
            opcode == Opcodes.SASTORE ||
            opcode == Opcodes.LASTORE ||
            opcode == Opcodes.FASTORE ||
            opcode == Opcodes.DASTORE ||
            opcode == Opcodes.AASTORE
    }

    private fun isTerminal(opcode: Int): Boolean {
        return opcode == Opcodes.RETURN ||
            opcode == Opcodes.IRETURN ||
            opcode == Opcodes.LRETURN ||
            opcode == Opcodes.FRETURN ||
            opcode == Opcodes.DRETURN ||
            opcode == Opcodes.ARETURN
    }

    private fun opcodeName(opcode: Int): String {
        return when (opcode) {
            Opcodes.NOP -> "NOP"
            Opcodes.ACONST_NULL -> "ACONST_NULL"
            Opcodes.ICONST_M1 -> "ICONST_M1"
            Opcodes.ICONST_0 -> "ICONST_0"
            Opcodes.ICONST_1 -> "ICONST_1"
            Opcodes.ICONST_2 -> "ICONST_2"
            Opcodes.ICONST_3 -> "ICONST_3"
            Opcodes.ICONST_4 -> "ICONST_4"
            Opcodes.ICONST_5 -> "ICONST_5"
            Opcodes.LCONST_0 -> "LCONST_0"
            Opcodes.LCONST_1 -> "LCONST_1"
            Opcodes.FCONST_0 -> "FCONST_0"
            Opcodes.FCONST_1 -> "FCONST_1"
            Opcodes.FCONST_2 -> "FCONST_2"
            Opcodes.DCONST_0 -> "DCONST_0"
            Opcodes.DCONST_1 -> "DCONST_1"
            Opcodes.ILOAD -> "ILOAD"
            Opcodes.ISTORE -> "ISTORE"
            Opcodes.LLOAD -> "LLOAD"
            Opcodes.LSTORE -> "LSTORE"
            Opcodes.FLOAD -> "FLOAD"
            Opcodes.FSTORE -> "FSTORE"
            Opcodes.DLOAD -> "DLOAD"
            Opcodes.DSTORE -> "DSTORE"
            Opcodes.ALOAD -> "ALOAD"
            Opcodes.ASTORE -> "ASTORE"
            Opcodes.IADD -> "IADD"
            Opcodes.ISUB -> "ISUB"
            Opcodes.IMUL -> "IMUL"
            Opcodes.IDIV -> "IDIV"
            Opcodes.IREM -> "IREM"
            Opcodes.IAND -> "IAND"
            Opcodes.IOR -> "IOR"
            Opcodes.IXOR -> "IXOR"
            Opcodes.INEG -> "INEG"
            Opcodes.ISHL -> "ISHL"
            Opcodes.ISHR -> "ISHR"
            Opcodes.IUSHR -> "IUSHR"
            Opcodes.LADD -> "LADD"
            Opcodes.LSUB -> "LSUB"
            Opcodes.LMUL -> "LMUL"
            Opcodes.LDIV -> "LDIV"
            Opcodes.LREM -> "LREM"
            Opcodes.LAND -> "LAND"
            Opcodes.LOR -> "LOR"
            Opcodes.LXOR -> "LXOR"
            Opcodes.LNEG -> "LNEG"
            Opcodes.LSHL -> "LSHL"
            Opcodes.LSHR -> "LSHR"
            Opcodes.LUSHR -> "LUSHR"
            Opcodes.LCMP -> "LCMP"
            Opcodes.I2L -> "I2L"
            Opcodes.L2I -> "L2I"
            Opcodes.I2F -> "I2F"
            Opcodes.I2D -> "I2D"
            Opcodes.L2F -> "L2F"
            Opcodes.L2D -> "L2D"
            Opcodes.F2I -> "F2I"
            Opcodes.F2L -> "F2L"
            Opcodes.F2D -> "F2D"
            Opcodes.D2I -> "D2I"
            Opcodes.D2L -> "D2L"
            Opcodes.D2F -> "D2F"
            Opcodes.I2B -> "I2B"
            Opcodes.I2C -> "I2C"
            Opcodes.I2S -> "I2S"
            Opcodes.FADD -> "FADD"
            Opcodes.FSUB -> "FSUB"
            Opcodes.FMUL -> "FMUL"
            Opcodes.FDIV -> "FDIV"
            Opcodes.FREM -> "FREM"
            Opcodes.FNEG -> "FNEG"
            Opcodes.DADD -> "DADD"
            Opcodes.DSUB -> "DSUB"
            Opcodes.DMUL -> "DMUL"
            Opcodes.DDIV -> "DDIV"
            Opcodes.DREM -> "DREM"
            Opcodes.DNEG -> "DNEG"
            Opcodes.FCMPL -> "FCMPL"
            Opcodes.FCMPG -> "FCMPG"
            Opcodes.DCMPL -> "DCMPL"
            Opcodes.DCMPG -> "DCMPG"
            Opcodes.POP -> "POP"
            Opcodes.POP2 -> "POP2"
            Opcodes.DUP -> "DUP"
            Opcodes.DUP_X1 -> "DUP_X1"
            Opcodes.DUP_X2 -> "DUP_X2"
            Opcodes.DUP2 -> "DUP2"
            Opcodes.DUP2_X1 -> "DUP2_X1"
            Opcodes.DUP2_X2 -> "DUP2_X2"
            Opcodes.SWAP -> "SWAP"
            Opcodes.IALOAD -> "IALOAD"
            Opcodes.BALOAD -> "BALOAD"
            Opcodes.CALOAD -> "CALOAD"
            Opcodes.SALOAD -> "SALOAD"
            Opcodes.LALOAD -> "LALOAD"
            Opcodes.FALOAD -> "FALOAD"
            Opcodes.DALOAD -> "DALOAD"
            Opcodes.AALOAD -> "AALOAD"
            Opcodes.IASTORE -> "IASTORE"
            Opcodes.BASTORE -> "BASTORE"
            Opcodes.CASTORE -> "CASTORE"
            Opcodes.SASTORE -> "SASTORE"
            Opcodes.LASTORE -> "LASTORE"
            Opcodes.FASTORE -> "FASTORE"
            Opcodes.DASTORE -> "DASTORE"
            Opcodes.AASTORE -> "AASTORE"
            Opcodes.IRETURN -> "IRETURN"
            Opcodes.LRETURN -> "LRETURN"
            Opcodes.FRETURN -> "FRETURN"
            Opcodes.DRETURN -> "DRETURN"
            Opcodes.ARETURN -> "ARETURN"
            Opcodes.RETURN -> "RETURN"
            Opcodes.ATHROW -> "ATHROW"
            Opcodes.ARRAYLENGTH -> "ARRAYLENGTH"
            Opcodes.MONITORENTER -> "MONITORENTER"
            Opcodes.MONITOREXIT -> "MONITOREXIT"
            Opcodes.GOTO -> "GOTO"
            Opcodes.IFEQ -> "IFEQ"
            Opcodes.IFNE -> "IFNE"
            Opcodes.IFLT -> "IFLT"
            Opcodes.IFGE -> "IFGE"
            Opcodes.IFGT -> "IFGT"
            Opcodes.IFLE -> "IFLE"
            Opcodes.IF_ICMPEQ -> "IF_ICMPEQ"
            Opcodes.IF_ICMPNE -> "IF_ICMPNE"
            Opcodes.IF_ICMPLT -> "IF_ICMPLT"
            Opcodes.IF_ICMPGE -> "IF_ICMPGE"
            Opcodes.IF_ICMPGT -> "IF_ICMPGT"
            Opcodes.IF_ICMPLE -> "IF_ICMPLE"
            Opcodes.IF_ACMPEQ -> "IF_ACMPEQ"
            Opcodes.IF_ACMPNE -> "IF_ACMPNE"
            Opcodes.IFNULL -> "IFNULL"
            Opcodes.IFNONNULL -> "IFNONNULL"
            Opcodes.TABLESWITCH -> "TABLESWITCH"
            Opcodes.LOOKUPSWITCH -> "LOOKUPSWITCH"
            Opcodes.INVOKESTATIC -> "INVOKESTATIC"
            Opcodes.INVOKESPECIAL -> "INVOKESPECIAL"
            Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL"
            Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE"
            Opcodes.GETSTATIC -> "GETSTATIC"
            Opcodes.PUTSTATIC -> "PUTSTATIC"
            Opcodes.GETFIELD -> "GETFIELD"
            Opcodes.PUTFIELD -> "PUTFIELD"
            Opcodes.NEW -> "NEW"
            Opcodes.CHECKCAST -> "CHECKCAST"
            Opcodes.INSTANCEOF -> "INSTANCEOF"
            Opcodes.ANEWARRAY -> "ANEWARRAY"
            Opcodes.MULTIANEWARRAY -> "MULTIANEWARRAY"
            Opcodes.NEWARRAY -> "NEWARRAY"
            Opcodes.LDC -> "LDC"
            else -> "OPCODE_$opcode"
        }
    }

    private fun jniCallType(type: Type): String {
        return when (type.sort) {
            Type.VOID -> "Void"
            Type.BOOLEAN -> "Boolean"
            Type.CHAR -> "Char"
            Type.BYTE -> "Byte"
            Type.SHORT -> "Short"
            Type.INT -> "Int"
            Type.LONG -> "Long"
            Type.FLOAT -> "Float"
            Type.DOUBLE -> "Double"
            Type.OBJECT,
            Type.ARRAY -> "Object"
            else -> unsupportedDescriptor(type.descriptor)
        }
    }

    private fun jniFieldType(type: Type): String {
        return when (type.sort) {
            Type.BOOLEAN -> "Boolean"
            Type.CHAR -> "Char"
            Type.BYTE -> "Byte"
            Type.SHORT -> "Short"
            Type.INT -> "Int"
            Type.LONG -> "Long"
            Type.FLOAT -> "Float"
            Type.DOUBLE -> "Double"
            Type.OBJECT,
            Type.ARRAY -> "Object"
            else -> unsupportedDescriptor(type.descriptor)
        }
    }

    private fun jvalueField(type: Type): String {
        return when (type.sort) {
            Type.BOOLEAN -> "z"
            Type.CHAR -> "c"
            Type.BYTE -> "b"
            Type.SHORT -> "s"
            Type.INT -> "i"
            Type.LONG -> "j"
            Type.FLOAT -> "f"
            Type.DOUBLE -> "d"
            Type.OBJECT,
            Type.ARRAY -> "l"
            else -> unsupportedDescriptor(type.descriptor)
        }
    }

    private fun unsupportedInstruction(instruction: NativeJvmInstruction): Nothing {
        throw UnsupportedNativeInstruction(
            NativeSkipReason.UnsupportedInstruction,
            "full JVM C++ translator does not support opcode ${instruction.opcode} (${instruction.node.javaClass.simpleName})"
        )
    }

    private fun unsupportedStackShape(instruction: NativeJvmInstruction, opcode: String): Nothing {
        throw UnsupportedNativeInstruction(
            NativeSkipReason.UnsupportedInstruction,
            "$opcode has an unsupported stack shape at instruction ${instruction.instructionIndex}"
        )
    }

    private fun unsupportedDescriptor(descriptor: String): Nothing {
        throw UnsupportedNativeInstruction(
            NativeSkipReason.UnsupportedDescriptor,
            "initial full JVM C++ lowering supports static methods with int/long/object arguments and void/int/long/object return; descriptor=$descriptor"
        )
    }

    private fun longLiteral(value: Long): String {
        return if (value == Long.MIN_VALUE) {
            "(-9223372036854775807LL - 1LL)"
        } else {
            "${value}LL"
        }
    }

    private fun floatLiteral(value: Float): String {
        return when {
            value.isNaN() -> "std::numeric_limits<jfloat>::quiet_NaN()"
            value == Float.POSITIVE_INFINITY -> "std::numeric_limits<jfloat>::infinity()"
            value == Float.NEGATIVE_INFINITY -> "-std::numeric_limits<jfloat>::infinity()"
            else -> java.lang.Float.toHexString(value) + "f"
        }
    }

    private fun doubleLiteral(value: Double): String {
        return when {
            value.isNaN() -> "std::numeric_limits<jdouble>::quiet_NaN()"
            value == Double.POSITIVE_INFINITY -> "std::numeric_limits<jdouble>::infinity()"
            value == Double.NEGATIVE_INFINITY -> "-std::numeric_limits<jdouble>::infinity()"
            else -> java.lang.Double.toHexString(value)
        }
    }

    private fun cppString(value: String): String {
        return buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private val IntLikeSorts = setOf(
        Type.BOOLEAN,
        Type.CHAR,
        Type.BYTE,
        Type.SHORT,
        Type.INT
    )

    private class StackShapeAnalyzer(ownerInternalName: String, methodNode: MethodNode) {
        private val instructions = methodNode.instructions?.toArray()?.toList().orEmpty()
        private val frames: Array<Frame<BasicValue>?>?
        private val failure: Throwable?

        init {
            var analyzedFrames: Array<Frame<BasicValue>?>? = null
            var analyzedFailure: Throwable? = null
            try {
                analyzedFrames = Analyzer(BasicInterpreter()).analyze(ownerInternalName, methodNode)
            } catch (throwable: Throwable) {
                analyzedFailure = throwable
            }
            frames = analyzedFrames
            failure = analyzedFailure
        }

        fun stackValueSize(instruction: NativeJvmInstruction, depthFromTop: Int): Int {
            val frame = frames?.getOrNull(instruction.instructionIndex)
                ?: throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedInstruction,
                    "stack shape is unavailable for opcode ${instruction.opcode}" +
                        failure?.message?.let { ": $it" }.orEmpty()
                )
            val stackIndex = frame.stackSize - 1 - depthFromTop
            if (stackIndex < 0) {
                throw UnsupportedNativeInstruction(
                    NativeSkipReason.UnsupportedInstruction,
                    "stack shape is too shallow for opcode ${instruction.opcode}"
                )
            }
            val value = frame.getStack(stackIndex)
            return value.size
        }

        @Suppress("unused")
        fun instructionAt(index: Int) = instructions.getOrNull(index)
    }

    private class LabelTargetResolver(methodNode: MethodNode) {
        private val instructions = methodNode.instructions?.toArray()?.toList().orEmpty()
        private val targetIndexByLabel = linkedMapOf<LabelNode, Int>()

        fun cppLabel(label: LabelNode): String {
            val target = targetIndexByLabel.getOrPut(label) {
                nextExecutableIndex(label)
                    ?: throw UnsupportedNativeInstruction(
                        NativeSkipReason.UnsupportedInstruction,
                        "branch target label has no executable instruction"
                    )
            }
            return "L_BC_$target"
        }

        private fun nextExecutableIndex(label: LabelNode): Int? {
            val labelIndex = instructions.indexOf(label)
            if (labelIndex < 0) return null
            for (index in labelIndex until instructions.size) {
                if (instructions[index].opcode >= 0) return index
            }
            return null
        }
    }
}
