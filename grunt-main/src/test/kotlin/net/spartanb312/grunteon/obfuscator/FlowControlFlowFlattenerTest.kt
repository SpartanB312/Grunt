package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunt.ir.flow.core.FlowBlockKind
import net.spartanb312.grunt.ir.flow.core.FlowEdgeSemantics
import net.spartanb312.grunt.ir.flow.core.FlowFrameValue
import net.spartanb312.grunt.ir.flow.core.FlowPredicateGuarantee
import net.spartanb312.grunt.ir.flow.core.FlowSwitchJump
import net.spartanb312.grunt.ir.flow.core.FlowVerifier
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowExporter
import net.spartanb312.grunt.ir.flow.jvm.JvmFlowImporter
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.junkcode.JunkCallPool
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.CffKeyProcessorOptions
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.CffKeyProcessorRegistry
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.FlowControlFlowFlattenOptions
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.FlowControlFlowFlattener
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.FlowStateKeyMode
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.OpaquePredicateProcessorOptions
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.OpaquePredicateProcessorRegistry
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.process.RandomBoundOpaquePredicateProcessorCall
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FlowControlFlowFlattenerTest {
    @Test
    fun flattensSimpleLinearRegion() {
        val imported = JvmFlowImporter().import(Owner, linearMethod())
        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                minFlattenedBlocks = 2,
                fakeCasesPerDispatcher = 2
            ),
            testRandom("linear")
        ).flatten(imported.method)

        assertTrue(result.changed, result.reason ?: "not changed")
        assertEquals(1, result.flattenedRegions)
        assertEquals(3, result.flattenedBlocks)
        assertEquals(2, result.fakeCases)
        assertTrue(imported.method.blocks.any { it.kind == FlowBlockKind.Dispatcher })
        assertTrue(imported.method.blocks.any { it.kind == FlowBlockKind.StateSet })
        assertTrue(imported.method.blocks.any { it.kind == FlowBlockKind.Bogus })
        assertTrue(imported.method.edges.any { it.semantics == FlowEdgeSemantics.Bogus })
        val stateBlocks = imported.method.blocks.filter {
            it.kind == FlowBlockKind.StateSet || it.kind == FlowBlockKind.RegionEntry
        }
        assertTrue(stateBlocks.all { it.body.instructions.size > 3 })
        assertTrue(stateBlocks.any { block ->
            block.body.instructions.any {
                it.opcode in listOf(Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IXOR) ||
                        it.opcode == Opcodes.INVOKESTATIC
            }
        })
        val dispatcherJump = imported.method.blocks.single { it.kind == FlowBlockKind.Dispatcher }.jump as FlowSwitchJump
        val caseKeys = dispatcherJump.keyPorts.keys.sorted()
        assertEquals(5, caseKeys.size)
        assertEquals(caseKeys.size - 1, caseKeys.last() - caseKeys.first())
        assertTrue(caseKeys.first() != 1)
        FlowVerifier.verify(imported.method).requireValid()

        val exported = JvmFlowExporter(imported.metadata).export(imported.method)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
        assertTrue(
            exported.instructions.toArray()
                .any { it.opcode == Opcodes.LOOKUPSWITCH || it.opcode == Opcodes.TABLESWITCH })
    }

    @Test
    fun flattensThroughNonEmptyStackFrames() {
        val imported = JvmFlowImporter().import(Owner, nonEmptyStackMethod())
        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                minFlattenedBlocks = 2,
                fakeCasesPerDispatcher = 1
            ),
            testRandom("stacky")
        ).flatten(imported.method)

        assertTrue(result.changed, result.reason ?: "not changed")
        assertEquals(1, result.flattenedRegions)
        assertEquals(2, result.flattenedBlocks)
        assertTrue(result.dispatcherIslands >= 2)
        assertEquals(result.dispatcherIslands, result.fakeCases)
        FlowVerifier.verify(imported.method).requireValid()

        val exported = JvmFlowExporter(imported.metadata).export(imported.method)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
    }

    @Test
    fun shufflesFlattenedRegionBlocksWhenEnabled() {
        val imported = JvmFlowImporter().import(Owner, linearMethod())
        val originalRegionOrder = imported.method.layout.order.toList()
        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                minFlattenedBlocks = 2,
                fakeCasesPerDispatcher = 0,
                shuffleRegionBlocks = true
            ),
            testRandom("shuffle")
        ).flatten(imported.method)

        assertTrue(result.changed, result.reason ?: "not changed")
        val shuffledRegionOrder = imported.method.layout.order.filter { it in originalRegionOrder }
        assertEquals(originalRegionOrder.toSet(), shuffledRegionOrder.toSet())
        assertTrue(shuffledRegionOrder != originalRegionOrder)
        FlowVerifier.verify(imported.method).requireValid()

        val exported = JvmFlowExporter(imported.metadata).export(imported.method)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
    }

    @Test
    fun placesSuitableRealBlockAfterDispatcherSwitchWhenEnabled() {
        val imported = JvmFlowImporter().import(Owner, trailingCandidateMethod())
        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                minFlattenedBlocks = 2,
                maxFlattenedBlocks = 3,
                fakeCasesPerDispatcher = 0,
                junkCases = false,
                dispatcherTrailingRealBlock = true,
                dispatcherTrailingRealBlockChance = 1.0
            ),
            testRandom("trailing-real")
        ).flatten(imported.method)

        assertTrue(result.changed, result.reason ?: "not changed")
        assertEquals(1, result.dispatcherTrailingRealBlocks)

        val dispatcher = imported.method.blocks.single { it.kind == FlowBlockKind.Dispatcher }
        val layout = imported.method.layout.order
        val dispatcherIndex = layout.indexOf(dispatcher)
        assertTrue(dispatcherIndex >= 0 && dispatcherIndex + 1 < layout.size)
        val trailing = layout[dispatcherIndex + 1]
        assertTrue(trailing.kind == FlowBlockKind.Original || trailing.kind == FlowBlockKind.Split)

        val switch = dispatcher.jump as FlowSwitchJump
        val switchTargets = switch.ports
            .mapNotNull { port -> imported.method.edgeFrom(dispatcher, port)?.to }
            .toSet()
        assertTrue(trailing !in switchTargets)

        FlowVerifier.verify(imported.method).requireValid()
        val exported = JvmFlowExporter(imported.metadata).export(imported.method)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)

        val instructions = exported.instructions.toArray()
        val switchIndex = instructions.indexOfFirst {
            it.opcode == Opcodes.LOOKUPSWITCH || it.opcode == Opcodes.TABLESWITCH
        }
        assertTrue(switchIndex >= 0)
        val firstOpcodeAfterSwitch = instructions.drop(switchIndex + 1).first { it.opcode >= 0 }.opcode
        val trailingFirstOpcode = trailing.body.instructions.first { it.opcode >= 0 }.opcode
        assertEquals(trailingFirstOpcode, firstOpcodeAfterSwitch)
    }

    @Test
    fun cffJunkCasesShareCompatibleTerminatorsAcrossDispatchers() {
        val imported = JvmFlowImporter().import(Owner, linearMethod())
        imported.method.layout.order
            .filter { it.kind == FlowBlockKind.Original || it.kind == FlowBlockKind.Split }
            .drop(1)
            .first()
            .let { block ->
                block.entryFrame = block.entryFrame.copy(locals = block.entryFrame.locals + FlowFrameValue.Top)
                block.bodyExitFrame = block.bodyExitFrame.copy(locals = block.bodyExitFrame.locals + FlowFrameValue.Top)
            }
        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                minFlattenedBlocks = 2,
                fakeCasesPerDispatcher = 1,
                junkCases = true,
                junkCaseChance = 1.0,
                sharedFakeCaseTerminatorChance = 1.0
            ),
            testRandom("cff-shared-terminator"),
            hierarchy = ClassHierarchy.build(emptyList()),
            junkCallPool = JunkCallPool.build(emptyList())
        ).flatten(imported.method)

        assertTrue(result.changed, result.reason ?: "not changed")
        assertTrue(result.dispatcherIslands >= 2)

        val junkCaseEdges = imported.method.edges.filter {
            it.from.kind == FlowBlockKind.Dispatcher && it.semantics == FlowEdgeSemantics.Junk
        }
        assertTrue(junkCaseEdges.map { it.from }.distinct().size >= 2)
        val sharedTargets = junkCaseEdges
            .groupBy { it.to }
            .filterValues { edges -> edges.map { it.from }.distinct().size >= 2 }
        assertTrue(sharedTargets.isNotEmpty())
        assertTrue(sharedTargets.keys.all { it.kind == FlowBlockKind.Junk })

        FlowVerifier.verify(imported.method).requireValid()
        val exported = JvmFlowExporter(imported.metadata).export(imported.method)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
    }

    @Test
    fun excludesConstructorPreInitFlowFromFlattenedRegion() {
        val originalClass = preInitBranchConstructorClass()
        val originalConstructor = originalClass.methods.single { it.name == "<init>" && it.desc == "(I)V" }
        val imported = JvmFlowImporter().import(ConstructorOwner, originalConstructor)
        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                includeMethodEntry = true,
                includeUninitializedFrames = false,
                minFlattenedBlocks = 2,
                fakeCasesPerDispatcher = 1,
                junkCases = false
            ),
            testRandom("constructor-preinit"),
            constructorInitOwners = setOf(ConstructorOwner, "java/lang/Object")
        ).flatten(imported.method)

        assertTrue(result.changed, result.reason ?: "not changed")
        FlowVerifier.verify(imported.method).requireValid()

        val exported = JvmFlowExporter(imported.metadata).export(imported.method)
        Analyzer(BasicInterpreter()).analyze(ConstructorOwner, exported)

        val transformedClass = preInitBranchConstructorClass()
        val constructorIndex = transformedClass.methods.indexOfFirst { it.name == "<init>" && it.desc == "(I)V" }
        transformedClass.methods.set(constructorIndex, exported)
        val generated = GeneratedClassLoader().defineComputed(transformedClass)
        generated.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(0)
        generated.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(1)
    }

    @Test
    fun emitsProcessorStateProgramsWithSaltDataFlow() {
        val imported = JvmFlowImporter().import(Owner, linearMethod())
        val registry = CffKeyProcessorRegistry("TestSalt", classExists = { false })
        val result = FlowControlFlowFlattener(
            FlowControlFlowFlattenOptions(
                minFlattenedBlocks = 2,
                fakeCasesPerDispatcher = 0,
                stateKeyMode = FlowStateKeyMode.Processor
            ),
            testRandom("processor"),
            stateKeyProcessor = registry.methodProcessor(Owner, Opcodes.V17, "MethodSalt")
        ).flatten(imported.method)

        assertTrue(result.changed, result.reason ?: "not changed")
        val stateBlocks = imported.method.blocks.filter {
            it.kind == FlowBlockKind.StateSet || it.kind == FlowBlockKind.RegionEntry
        }
        assertTrue(stateBlocks.isNotEmpty())
        assertTrue(stateBlocks.all { block ->
            block.body.instructions.any {
                it is MethodInsnNode &&
                        it.opcode == Opcodes.INVOKESTATIC &&
                        it.owner.contains("\$KeyProcessor\$") &&
                        it.desc == CffKeyProcessorRegistry.ActionDesc
            }
        })

        val processor = registry.materialize().single()
        assertTrue(processor.name.contains("\$KeyProcessor\$TestSalt"))
        assertEquals(Opcodes.V17, processor.version)
        val actions = processor.methods.filter { it.name.startsWith("action_") }
        assertEquals(stateBlocks.size, actions.size)
        assertTrue(actions.all { it.desc == CffKeyProcessorRegistry.ActionDesc })
        assertTrue(actions.all { it.access and Opcodes.ACC_PUBLIC != 0 })
        assertTrue(actions.all { it.access and Opcodes.ACC_STATIC != 0 })
        assertTrue(actions.all { it.instructions.toArray().count { insn -> insn.isSaltLoad() } >= 2 })
        assertFalse(JunkCallPool.build(listOf(processor)).isEmpty())

        FlowVerifier.verify(imported.method).requireValid()
        val exported = JvmFlowExporter(imported.metadata).export(imported.method)
        Analyzer(BasicInterpreter()).analyze(Owner, exported)
        actions.forEach { Analyzer(BasicInterpreter()).analyze(processor.name, it) }
    }

    @Test
    fun processorFallsBackFromMissingOwnerVersion() {
        val registry = CffKeyProcessorRegistry("VersionFallback", classExists = { false })
        registry.methodProcessor(Owner, 0, "MethodSalt")
            .reserve(0, 1, 2, testRandom("version-fallback"))

        assertEquals(Opcodes.V1_8, registry.materialize().single().version)
    }

    @Test
    fun processorWithoutReservedActionsDoesNotMaterializeClass() {
        val registry = CffKeyProcessorRegistry("Empty", classExists = { false })
        registry.methodProcessor(Owner, Opcodes.V17, "Unused")

        assertEquals(0, registry.classCount)
        assertEquals(0, registry.actionCount)
        assertTrue(registry.materialize().isEmpty())
    }

    @Test
    fun processorActionsComputeReservedTargetKeys() {
        val registry = CffKeyProcessorRegistry("Executable", classExists = { false })
        val processor = registry.methodProcessor(Owner, Opcodes.V17, "MethodSalt")
        val targets = listOf(0, 1, -1, 0x13579BDF, Int.MIN_VALUE)
        val calls = targets.mapIndexed { index, target ->
            processor.reserve(
                siteId = index,
                inputKey = 0x10203040 + index,
                targetKey = target,
                random = testRandom("processor-exec-$index")
            )
        }

        val classNode = registry.materialize().single()
        val generated = GeneratedClassLoader().define(classNode)
        calls.zip(targets).forEach { (call, target) ->
            val action = generated.getDeclaredMethod(
                call.name,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            assertEquals(target, action.invoke(null, call.inputKey, call.salt) as Int)
        }
    }

    @Test
    fun processorOptionsAllowMinimalComplexity() {
        val registry = CffKeyProcessorRegistry(
            classMarker = "Minimal",
            classExists = { false },
            options = CffKeyProcessorOptions(
                minMainSteps = 1,
                maxMainSteps = 1,
                minExtraSteps = 0,
                maxExtraSteps = 0,
                minChainSteps = 0,
                maxChainSteps = 0
            )
        )
        val call = registry.methodProcessor(Owner, Opcodes.V17, "MethodSalt")
            .reserve(0, inputKey = 7, targetKey = 42, random = testRandom("minimal-processor"))
        val classNode = registry.materialize().single()
        val action = classNode.methods.single { it.name == call.name }

        assertTrue(action.instructions.toArray().count { it.isSaltLoad() } >= 2)
        val generated = GeneratedClassLoader().define(classNode)
        val method = generated.getDeclaredMethod(
            call.name,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        assertEquals(42, method.invoke(null, call.inputKey, call.salt) as Int)
    }

    @Test
    fun opaquePredicateProcessorActionsAreExecutable() {
        val registry = OpaquePredicateProcessorRegistry("OpaqueExec", classExists = { false })
        val processor = registry.methodProcessor(Owner, Opcodes.V17, "MethodSalt")
        val calls = (0 until 32).map { index ->
            processor.reserveAlwaysTrue(index, testRandom("opaque-predicate-$index"))
        }

        val classNode = registry.materialize().single()
        val generated = GeneratedClassLoader().define(classNode)
        calls.forEach { call ->
            val action = generated.getDeclaredMethod(
                call.name,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val result = action.invoke(null, call.left, call.right, call.salt) as Int
            assertTrue(compareOpcode(call.opcode, result, call.compareValue.value))
        }
    }

    @Test
    fun randomBoundOpaquePredicateActionsAreExecutable() {
        val registry = OpaquePredicateProcessorRegistry(
            classMarker = "RandomBoundExec",
            classExists = { false },
            options = OpaquePredicateProcessorOptions(randomBoundChance = 1.0)
        )
        val processor = registry.methodProcessor(Owner, Opcodes.V17, "MethodSalt")
        val calls = (0 until 32).map { index ->
            assertIs<RandomBoundOpaquePredicateProcessorCall>(
                processor.reserveGate(index, testRandom("random-bound-predicate-$index"))
            )
        }

        val classNode = registry.materialize().single()
        val generated = GeneratedClassLoader().define(classNode)
        calls.forEach { call ->
            val valueAction = generated.getDeclaredMethod(
                call.valueAction.name,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val boundAction = generated.getDeclaredMethod(
                call.boundAction.name,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val value = valueAction.invoke(
                null,
                call.valueAction.left,
                call.valueAction.right,
                call.valueAction.salt
            ) as Int
            val bound = boundAction.invoke(
                null,
                call.boundAction.left,
                call.boundAction.right,
                call.boundAction.salt
            ) as Int
            val expected = call.guarantee == FlowPredicateGuarantee.AlwaysTrue

            assertEquals(call.valueAction.result, value)
            assertEquals(call.boundAction.result, bound)
            assertTrue(bound > 0)
            repeat(bound) { sample ->
                assertEquals(expected, compareOpcode(call.opcode, value, sample))
            }
        }
    }

    private fun linearMethod(): MethodNode {
        val first = LabelNode()
        val second = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null).apply {
            instructions = InsnList().apply {
                add(JumpInsnNode(Opcodes.GOTO, first))
                add(first)
                add(InsnNode(Opcodes.NOP))
                add(JumpInsnNode(Opcodes.GOTO, second))
                add(second)
                add(InsnNode(Opcodes.NOP))
                add(InsnNode(Opcodes.RETURN))
            }
            maxLocals = 0
            maxStack = 0
        }
    }

    private fun nonEmptyStackMethod(): MethodNode {
        val target = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "stacky", "()V", null, null).apply {
            instructions = InsnList().apply {
                add(InsnNode(Opcodes.ICONST_1))
                add(JumpInsnNode(Opcodes.GOTO, target))
                add(target)
                add(InsnNode(Opcodes.POP))
                add(InsnNode(Opcodes.RETURN))
            }
            maxLocals = 0
            maxStack = 1
        }
    }

    private fun trailingCandidateMethod(): MethodNode {
        val first = LabelNode()
        val second = LabelNode()
        val trailing = LabelNode()
        return MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "trailing", "()V", null, null).apply {
            instructions = InsnList().apply {
                add(JumpInsnNode(Opcodes.GOTO, first))
                add(first)
                add(InsnNode(Opcodes.ICONST_0))
                add(InsnNode(Opcodes.POP))
                add(JumpInsnNode(Opcodes.GOTO, second))
                add(second)
                add(InsnNode(Opcodes.ICONST_1))
                add(InsnNode(Opcodes.POP))
                add(JumpInsnNode(Opcodes.GOTO, trailing))
                add(trailing)
                add(InsnNode(Opcodes.ICONST_5))
                add(InsnNode(Opcodes.POP))
                add(InsnNode(Opcodes.RETURN))
            }
            maxLocals = 0
            maxStack = 1
        }
    }

    private fun preInitBranchConstructorClass(): ClassNode {
        val classNode = ClassNode()
        ClassReader(preInitBranchConstructorBytes()).accept(classNode, ClassReader.EXPAND_FRAMES)
        return classNode
    }

    private fun preInitBranchConstructorBytes(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, ConstructorOwner, null, "java/lang/Object", null)

        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null).apply {
            val init = Label()
            val done = Label()
            visitCode()
            visitVarInsn(Opcodes.ILOAD, 1)
            visitJumpInsn(Opcodes.IFEQ, init)
            visitInsn(Opcodes.NOP)
            visitLabel(init)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitVarInsn(Opcodes.ILOAD, 1)
            visitJumpInsn(Opcodes.IFLE, done)
            visitInsn(Opcodes.NOP)
            visitLabel(done)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun testRandom(suffix: String) = Xoshiro256PPRandom(
        ByteArray(32) { index -> (Owner + suffix + index).sumOf { it.code }.toByte() }
    )

    private fun AbstractInsnNode.isSaltLoad(): Boolean {
        return this is VarInsnNode && opcode == Opcodes.ILOAD && `var` == 1
    }

    private fun compareOpcode(opcode: Int, left: Int, right: Int): Boolean {
        return when (opcode) {
            Opcodes.IF_ICMPEQ -> left == right
            Opcodes.IF_ICMPNE -> left != right
            Opcodes.IF_ICMPLT -> left < right
            Opcodes.IF_ICMPLE -> left <= right
            Opcodes.IF_ICMPGT -> left > right
            Opcodes.IF_ICMPGE -> left >= right
            else -> false
        }
    }

    private class GeneratedClassLoader : ClassLoader(FlowControlFlowFlattenerTest::class.java.classLoader) {
        fun define(classNode: ClassNode): Class<*> {
            val writer = ClassWriter(0)
            classNode.accept(writer)
            val bytes = writer.toByteArray()
            return defineClass(classNode.name.replace('/', '.'), bytes, 0, bytes.size)
        }

        fun defineComputed(classNode: ClassNode): Class<*> {
            val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
            classNode.accept(writer)
            val bytes = writer.toByteArray()
            return defineClass(classNode.name.replace('/', '.'), bytes, 0, bytes.size)
        }
    }

    private companion object {
        const val Owner = "example/Test"
        const val ConstructorOwner = "example/ConstructorPreInitCase"
    }
}
