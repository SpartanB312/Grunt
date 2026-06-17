package net.spartanb312.grunteon.obfuscator.process.nativecode

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.nativecode.ir.NativeJvmFeature
import net.spartanb312.grunteon.obfuscator.util.NATIVE_EXCLUDED
import net.spartanb312.grunteon.obfuscator.util.NATIVE_INCLUDED
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativePipelineScannerValidatorTest {

    @Test
    fun scannerFindsMethodLevelIncludedAnnotation() {
        val method = intHelper("selected").appendAnnotation(NATIVE_INCLUDED)
        val instance = instanceWith(classNode("test/NativeMethod", method))

        val candidates = context(instance) {
            NativeCandidateScanner.scan(NativePipelineConfig(enabled = true))
        }

        assertEquals(listOf("selected"), candidates.map { it.methodNode.name })
        assertEquals(NativeCandidateSource.MethodAnnotation, candidates.single().source)
    }

    @Test
    fun scannerTreatsClassLevelIncludedAnnotationAsAllMethodsCandidate() {
        val clazz = classNode("test/NativeClass", intHelper("a"), intHelper("b"))
            .appendAnnotation(NATIVE_INCLUDED)
        val instance = instanceWith(clazz)

        val candidates = context(instance) {
            NativeCandidateScanner.scan(NativePipelineConfig(enabled = true))
        }

        assertEquals(listOf("a", "b"), candidates.map { it.methodNode.name })
        assertTrue(candidates.all { it.source == NativeCandidateSource.ClassAnnotation })
    }

    @Test
    fun scannerLetsExcludeOverrideIncludesAndSupportsCustomAnnotationNames() {
        val custom = "com.example.NativeMe"
        val classExcluded = classNode("test/ClassExcluded", intHelper("a"))
            .appendAnnotation(NATIVE_INCLUDED)
            .appendAnnotation(NATIVE_EXCLUDED)
        val methodExcluded = intHelper("b")
            .appendAnnotation("Lcom/example/NativeMe;")
            .appendAnnotation(NATIVE_EXCLUDED)
        val customIncluded = intHelper("c").appendAnnotation("Lcom/example/NativeMe;")
        val instance = instanceWith(
            classExcluded,
            classNode("test/CustomIncluded", methodExcluded, customIncluded)
        )

        val candidates = context(instance) {
            NativeCandidateScanner.scan(
                NativePipelineConfig(
                    enabled = true,
                    includedAnnotationList = listOf(custom)
                )
            )
        }

        assertEquals(listOf("c"), candidates.map { it.methodNode.name })
    }

    @Test
    fun validatorAcceptsStaticIntHelper() {
        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/Accept", intHelper("ok"))),
            NativeBackend.Cpp
        )

        assertEquals(1, accepted.size)
        assertEquals(0, skipped.size)
        assertEquals(NativeLoweringKind.SsaPrimitive, accepted.single().lowering)
    }

    @Test
    fun validatorSkipsUnsupportedMethodShapes() {
        assertSkip(NativeSkipReason.Constructor, method = intHelper("<init>"))
        assertSkip(
            NativeSkipReason.AbstractMethod,
            classAccess = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT,
            method = abstractInterfaceHelper()
        )
        assertSkip(NativeSkipReason.InvokeDynamic, method = indyHelper())
        assertSkip(NativeSkipReason.ConstantDynamic, method = condyHelper())
    }

    @Test
    fun validatorAcceptsInterfaceDefaultAndStaticMethodsAsFullJvmProxyCandidates() {
        val interfaceAccess = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT

        val defaultMethod = assertAccepted(classAccess = interfaceAccess, method = interfaceDefaultHelper())
        assertEquals(NativeLoweringKind.FullJvm, defaultMethod.lowering)

        val staticMethod = assertAccepted(classAccess = interfaceAccess, method = interfaceStaticHelper())
        assertEquals(NativeLoweringKind.FullJvm, staticMethod.lowering)
    }

    @Test
    fun validatorAcceptsInitialFullJvmTryCatchAndMonitorMethods() {
        val tryCatch = assertAccepted(method = tryCatchHelper())
        assertEquals(NativeLoweringKind.FullJvm, tryCatch.lowering)
        assertTrue(NativeJvmFeature.TryCatch in tryCatch.fullJvmSupport.features)

        val monitor = assertAccepted(method = monitorHelper())
        assertEquals(NativeLoweringKind.FullJvm, monitor.lowering)
        assertTrue(NativeJvmFeature.Monitor in monitor.fullJvmSupport.features)
    }

    @Test
    fun validatorAcceptsInitialFullJvmBranchAndSwitchMethods() {
        assertEquals(NativeLoweringKind.SsaPrimitive, assertAccepted(method = branchHelper()).lowering)
        assertEquals(NativeLoweringKind.SsaPrimitive, assertAccepted(method = tableSwitchHelper()).lowering)
        assertEquals(NativeLoweringKind.SsaPrimitive, assertAccepted(method = lookupSwitchHelper()).lowering)
    }

    @Test
    fun validatorAcceptsInitialFullJvmStaticMethodCalls() {
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = invokeStaticIntHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = invokeStaticObjectHelper()).lowering)
    }

    @Test
    fun validatorAcceptsInitialFullJvmFieldAndTypeMethods() {
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = getStaticFieldHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = putStaticFieldHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = getFieldHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = putFieldHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = newObjectHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = checkcastHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = instanceOfHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = newIntArrayHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = newObjectArrayHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = arrayLengthHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = objectClassLiteralHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = primitiveClassLiteralHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = multiIntArrayHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = partialMultiObjectArrayHelper()).lowering)
    }

    @Test
    fun validatorAcceptsMethodTypeAndMethodHandleLdcConstants() {
        val methodType = assertAccepted(method = methodTypeLdcHelper())
        assertEquals(NativeLoweringKind.FullJvm, methodType.lowering)
        assertTrue(NativeJvmFeature.MethodTypeConstant in methodType.fullJvmSupport.features)

        val methodHandle = assertAccepted(method = methodHandleLdcHelper())
        assertEquals(NativeLoweringKind.FullJvm, methodHandle.lowering)
        assertTrue(NativeJvmFeature.MethodHandleConstant in methodHandle.fullJvmSupport.features)
    }

    @Test
    fun validatorAcceptsClassInitializerForProxyLowering() {
        val clinit = assertAccepted(method = classInitializerHelper())
        assertEquals(NativeLoweringKind.FullJvm, clinit.lowering)

        val interfaceClinit = assertAccepted(
            classAccess = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT,
            method = classInitializerHelper()
        )
        assertEquals(NativeLoweringKind.FullJvm, interfaceClinit.lowering)
    }

    @Test
    fun validatorAcceptsInitialFullJvmArrayLoadStoreMethods() {
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = intArrayLoadHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = byteArrayLoadHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = charArrayStoreHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = objectArrayStoreHelper()).lowering)
    }

    @Test
    fun validatorAcceptsInitialFullJvmLongMethods() {
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = materialKeyLongHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = invokeStaticLongHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = longArrayStoreLoadHelper()).lowering)
    }

    @Test
    fun validatorAcceptsInitialFullJvmInstanceMethods() {
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = instanceFieldAddHelper()).lowering)
    }

    @Test
    fun validatorAcceptsInitialFullJvmThrowAndTypedStackMethods() {
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = throwCatchHelper()).lowering)
        assertEquals(NativeLoweringKind.SsaPrimitive, assertAccepted(method = dupX2LongHelper()).lowering)
        assertEquals(NativeLoweringKind.SsaPrimitive, assertAccepted(method = dup2X1LongHelper()).lowering)
        assertEquals(NativeLoweringKind.SsaPrimitive, assertAccepted(method = dup2X2LongHelper()).lowering)
    }

    @Test
    fun validatorAcceptsInitialFullJvmFloatAndDoubleMethods() {
        assertEquals(NativeLoweringKind.SsaPrimitive, assertAccepted(method = floatArithmeticHelper()).lowering)
        assertEquals(NativeLoweringKind.SsaPrimitive, assertAccepted(method = doubleCompareHelper()).lowering)
        assertEquals(NativeLoweringKind.SsaPrimitive, assertAccepted(method = doubleToIntHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = invokeStaticDoubleHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = doubleArrayStoreLoadHelper()).lowering)
        assertEquals(NativeLoweringKind.FullJvm, assertAccepted(method = newFloatArrayHelper()).lowering)
    }

    private fun assertAccepted(
        classAccess: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
        method: MethodNode = intHelper("accepted")
    ): NativeValidatedMethod {
        val (accepted, skipped) = NativeValidator.validate(
            listOf(candidate("test/Accept${method.name}", method, classAccess)),
            NativeBackend.Cpp
        )
        assertEquals(0, skipped.size)
        assertEquals(1, accepted.size)
        return accepted.single()
    }

    private fun assertSkip(
        reason: NativeSkipReason,
        classAccess: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
        method: MethodNode = intHelper("skip")
    ): NativeSkip {
        val (_, skipped) = NativeValidator.validate(
            listOf(candidate("test/Skip$reason", method, classAccess)),
            NativeBackend.Cpp
        )
        assertEquals(reason, skipped.single().reason)
        return skipped.single()
    }

    private fun instanceWith(vararg classes: ClassNode): Grunteon {
        val root = createTempDirectory("grunteon-native-scan")
        classes.forEach { writeClass(root, it) }
        return Grunteon.create(
            ObfConfig(
                globalConfig = GlobalConfig(
                    input = root.pathString,
                    output = null,
                    dumpMappings = false,
                    exclusions = emptyList(),
                    mixinExclusions = emptyList()
                )
            )
        )
    }

    private fun writeClass(root: Path, classNode: ClassNode) {
        val writer = ClassWriter(0)
        classNode.accept(writer)
        val file = root.resolve("${classNode.name}.class")
        file.parent.createDirectories()
        file.writeBytes(writer.toByteArray())
    }

    private fun classNode(
        name: String,
        vararg methods: MethodNode,
        access: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
    ): ClassNode {
        return ClassNode().apply {
            version = Opcodes.V1_8
            this.access = access
            this.name = name
            superName = "java/lang/Object"
            this.methods.addAll(methods)
        }
    }

    private fun candidate(
        className: String,
        method: MethodNode,
        classAccess: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
    ): NativeCandidate {
        return NativeCandidate(
            classNode = classNode(className, method, access = classAccess),
            methodNode = method,
            source = NativeCandidateSource.MethodAnnotation
        )
    }

    private fun intHelper(name: String): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            name,
            "(II)I",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun tryCatchHelper(): MethodNode {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "tryCatch",
            "(II)I",
            null,
            null
        ).apply {
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IDIV))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(end)
            instructions.add(handler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, "java/lang/Throwable"))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun monitorHelper(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "monitor",
            "(Ljava/lang/Object;)V",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITORENTER))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.MONITOREXIT))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun abstractInterfaceHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "abstractHelper", "()V", null, null)
    }

    private fun interfaceDefaultHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC, "defaultAdd", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun interfaceStaticHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "staticAdd", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 1
        }
    }

    private fun branchHelper(): MethodNode {
        val nonNegative = LabelNode()
        val end = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "branch",
            "(I)I",
            null,
            null
        ).apply {
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
    }

    private fun tableSwitchHelper(): MethodNode {
        val one = LabelNode()
        val two = LabelNode()
        val default = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "tableSwitch",
            "(I)I",
            null,
            null
        ).apply {
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
    }

    private fun lookupSwitchHelper(): MethodNode {
        val seven = LabelNode()
        val nine = LabelNode()
        val default = LabelNode()
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "lookupSwitch",
            "(I)I",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(LookupSwitchInsnNode(default, intArrayOf(7, 9), arrayOf(seven, nine)))
            instructions.add(seven)
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(nine)
            instructions.add(InsnNode(Opcodes.ICONST_2))
            instructions.add(InsnNode(Opcodes.IRETURN))
            instructions.add(default)
            instructions.add(InsnNode(Opcodes.ICONST_M1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun invokeStaticIntHelper(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "invokeStaticInt",
            "(I)I",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "bitCount", "(I)I", false))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun invokeStaticObjectHelper(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "invokeStaticObject",
            "(I)Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun getStaticFieldHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "getStaticField", "()I", null, null).apply {
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "test/Fields", "VALUE", "I"))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun putStaticFieldHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "putStaticField", "(I)V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(FieldInsnNode(Opcodes.PUTSTATIC, "test/Fields", "VALUE", "I"))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun getFieldHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "getField", "(Ltest/Fields;)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(FieldInsnNode(Opcodes.GETFIELD, "test/Fields", "value", "I"))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun putFieldHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "putField", "(Ltest/Fields;I)V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(FieldInsnNode(Opcodes.PUTFIELD, "test/Fields", "value", "I"))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun newObjectHelper(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "newObject",
            "()Ljava/lang/StringBuilder;",
            null,
            null
        ).apply {
            instructions.add(TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"))
            instructions.add(InsnNode(Opcodes.DUP))
            instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 2
            maxLocals = 0
        }
    }

    private fun checkcastHelper(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "checkcast",
            "(Ljava/lang/Object;)Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun instanceOfHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "instanceOf", "(Ljava/lang/Object;)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(TypeInsnNode(Opcodes.INSTANCEOF, "java/lang/String"))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun newIntArrayHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "newIntArray", "(I)[I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun newObjectArrayHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "newObjectArray", "(I)[Ljava/lang/String;", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun arrayLengthHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "arrayLength", "([I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ARRAYLENGTH))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun objectClassLiteralHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "objectClassLiteral", "()Ljava/lang/Class;", null, null).apply {
            instructions.add(LdcInsnNode(Type.getObjectType("java/lang/String")))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun primitiveClassLiteralHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "primitiveClassLiteral", "()Ljava/lang/Class;", null, null).apply {
            instructions.add(LdcInsnNode(Type.INT_TYPE))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun multiIntArrayHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "multiIntArray", "(II)[[I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(MultiANewArrayInsnNode("[[I", 2))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun partialMultiObjectArrayHelper(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "partialMultiObjectArray",
            "(II)[[[Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(MultiANewArrayInsnNode("[[[Ljava/lang/String;", 2))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun intArrayLoadHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "intArrayLoad", "([II)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IALOAD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun byteArrayLoadHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "byteArrayLoad", "([BI)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.BALOAD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun charArrayStoreHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "charArrayStore", "([CII)V", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.CASTORE))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 3
            maxLocals = 3
        }
    }

    private fun objectArrayStoreHelper(): MethodNode {
        return MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "objectArrayStore",
            "([Ljava/lang/String;ILjava/lang/String;)V",
            null,
            null
        ).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 2))
            instructions.add(InsnNode(Opcodes.AASTORE))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 3
            maxLocals = 3
        }
    }

    private fun materialKeyLongHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "materialKeyLong", "()I", null, null).apply {
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "test/RuntimeMaterial", "KEY", "J"))
            instructions.add(InsnNode(Opcodes.DUP2))
            instructions.add(IntInsnNode(Opcodes.BIPUSH, 32))
            instructions.add(InsnNode(Opcodes.LUSHR))
            instructions.add(InsnNode(Opcodes.LXOR))
            instructions.add(InsnNode(Opcodes.L2I))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 6
            maxLocals = 0
        }
    }

    private fun invokeStaticLongHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "invokeStaticLong", "(JI)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "rotateLeft", "(JI)J", false))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 3
            maxLocals = 3
        }
    }

    private fun longArrayStoreLoadHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "longArrayStoreLoad", "([JIJ)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 2))
            instructions.add(InsnNode(Opcodes.LASTORE))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.LALOAD))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 4
            maxLocals = 4
        }
    }

    private fun instanceFieldAddHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC, "instanceFieldAdd", "(I)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(FieldInsnNode(Opcodes.GETFIELD, "test/AcceptinstanceFieldAdd", "value", "I"))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun throwCatchHelper(): MethodNode {
        val start = LabelNode()
        val end = LabelNode()
        val handler = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "throwCatch", "(Ljava/lang/Throwable;)I", null, null).apply {
            instructions.add(start)
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(InsnNode(Opcodes.ATHROW))
            instructions.add(end)
            instructions.add(handler)
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.ICONST_1))
            instructions.add(InsnNode(Opcodes.IRETURN))
            tryCatchBlocks.add(TryCatchBlockNode(start, end, handler, "java/lang/Throwable"))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun dupX2LongHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "dupX2Long", "(JI)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
            instructions.add(InsnNode(Opcodes.DUP_X2))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.L2I))
            instructions.add(InsnNode(Opcodes.IADD))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 4
            maxLocals = 3
        }
    }

    private fun dup2X1LongHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "dup2X1Long", "(IJ)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 1))
            instructions.add(InsnNode(Opcodes.DUP2_X1))
            instructions.add(VarInsnNode(Opcodes.LSTORE, 3))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 5))
            instructions.add(InsnNode(Opcodes.POP2))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 5))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 5
            maxLocals = 6
        }
    }

    private fun dup2X2LongHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "dup2X2Long", "(JJ)J", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.LLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 2))
            instructions.add(InsnNode(Opcodes.DUP2_X2))
            instructions.add(VarInsnNode(Opcodes.LSTORE, 4))
            instructions.add(VarInsnNode(Opcodes.LSTORE, 6))
            instructions.add(VarInsnNode(Opcodes.LSTORE, 8))
            instructions.add(VarInsnNode(Opcodes.LLOAD, 8))
            instructions.add(InsnNode(Opcodes.LRETURN))
            maxStack = 6
            maxLocals = 10
        }
    }

    private fun floatArithmeticHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "floatArithmetic", "(FF)F", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.FLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.FLOAD, 1))
            instructions.add(InsnNode(Opcodes.FREM))
            instructions.add(LdcInsnNode(2.5f))
            instructions.add(InsnNode(Opcodes.FADD))
            instructions.add(InsnNode(Opcodes.FRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun doubleCompareHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "doubleCompare", "(DD)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.DLOAD, 0))
            instructions.add(VarInsnNode(Opcodes.DLOAD, 2))
            instructions.add(InsnNode(Opcodes.DCMPG))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 4
            maxLocals = 4
        }
    }

    private fun doubleToIntHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "doubleToInt", "(D)I", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.DLOAD, 0))
            instructions.add(InsnNode(Opcodes.D2I))
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun invokeStaticDoubleHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "invokeStaticDouble", "(D)D", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.DLOAD, 0))
            instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(D)D", false))
            instructions.add(InsnNode(Opcodes.DRETURN))
            maxStack = 2
            maxLocals = 2
        }
    }

    private fun doubleArrayStoreLoadHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "doubleArrayStoreLoad", "([DID)D", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(VarInsnNode(Opcodes.DLOAD, 2))
            instructions.add(InsnNode(Opcodes.DASTORE))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
            instructions.add(InsnNode(Opcodes.DALOAD))
            instructions.add(InsnNode(Opcodes.DRETURN))
            maxStack = 4
            maxLocals = 4
        }
    }

    private fun newFloatArrayHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "newFloatArray", "(I)[F", null, null).apply {
            instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
            instructions.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_FLOAT))
            instructions.add(InsnNode(Opcodes.ARETURN))
            maxStack = 1
            maxLocals = 1
        }
    }

    private fun indyHelper(): MethodNode {
        return intHelper("indy").apply {
            instructions.insert(
                InvokeDynamicInsnNode(
                    "dyn",
                    "()I",
                    Handle(Opcodes.H_INVOKESTATIC, "test/Bootstrap", "bsm", "()V", false)
                )
            )
        }
    }

    private fun condyHelper(): MethodNode {
        return intHelper("condy").apply {
            instructions.insert(
                LdcInsnNode(
                    ConstantDynamic(
                        "dyn",
                        "I",
                        Handle(Opcodes.H_INVOKESTATIC, "test/Bootstrap", "bsm", "()V", false)
                    )
                )
            )
        }
    }

    private fun methodTypeLdcHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "methodTypeLdc", "()V", null, null).apply {
            instructions.add(LdcInsnNode(Type.getMethodType("(I)Ljava/lang/String;")))
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun methodHandleLdcHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "methodHandleLdc", "()V", null, null).apply {
            instructions.add(
                LdcInsnNode(
                    Handle(
                        Opcodes.H_INVOKESTATIC,
                        "java/lang/Integer",
                        "bitCount",
                        "(I)I",
                        false
                    )
                )
            )
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 1
            maxLocals = 0
        }
    }

    private fun classInitializerHelper(): MethodNode {
        return MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).apply {
            instructions.add(InsnNode(Opcodes.RETURN))
            maxStack = 0
            maxLocals = 0
        }
    }
}
