package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.MethodInliner
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ReferenceObfuscate
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.*

class TransformerMetadataRegressionTest {
    @Test
    fun cachedInlineMetadataKeepsWideLocalsAndRepeatedCallSemantics() {
        val owner = ClassNode().apply {
            version = V1_8
            access = ACC_PUBLIC
            name = "regression/WideInline"
            superName = "java/lang/Object"
        }
        owner.methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, "mix", "(JD)J", null, null).apply {
            instructions.add(VarInsnNode(LLOAD, 0))
            instructions.add(VarInsnNode(DLOAD, 2))
            instructions.add(InsnNode(D2L))
            instructions.add(InsnNode(LADD))
            instructions.add(InsnNode(LRETURN))
            maxLocals = 4
            maxStack = 4
        })
        owner.methods.add(MethodNode(ACC_PUBLIC or ACC_STATIC, "run", "()J", null, null).apply {
            instructions.add(InsnNode(LCONST_0))
            repeat(64) {
                instructions.add(LdcInsnNode(5L))
                instructions.add(LdcInsnNode(2.0))
                instructions.add(MethodInsnNode(INVOKESTATIC, owner.name, "mix", "(JD)J", false))
                instructions.add(InsnNode(LADD))
            }
            instructions.add(InsnNode(LRETURN))
            maxStack = 6
        })
        withGeneratedTransformerClasses(listOf(owner), listOf(MethodInliner.Config())) { instance ->
            val transformed = instance.workRes.inputClassMap.getValue(owner.name)
            val caller = transformed.methods.single { it.name == "run" }
            assertFalse(caller.instructions.any { it is MethodInsnNode && it.name == "mix" })
            assertEquals(64 * 4, caller.maxLocals)
            Analyzer(BasicInterpreter()).analyzeAndComputeMaxs(transformed.name, caller)
            assertEquals(448L, generatedClassLoader(listOf(transformed)).loadClass(owner.name.replace('/', '.'))
                .getMethod("run").invoke(null))
        }
    }

    @Test
    fun indexedAnchorsMatchLinearSelectionAndRngConsumption() {
        val transformer = ReferenceObfuscate()
        for (distance in listOf(1, 2, 17)) {
            val oldRandom = Xoshiro256PPRandom("anchors".toByteArray())
            val newRandom = Xoshiro256PPRandom("anchors".toByteArray())
            val anchors = IntArray(200) { it * 3 }
            for (goto in 0 until 620) {
                val eligible = anchors.filter { abs(it - goto) >= distance }
                val expected = if (eligible.isEmpty()) -1 else eligible[oldRandom.nextInt(eligible.size)]
                assertEquals(expected, transformer.distantAnchorIndex(anchors, goto, distance, newRandom))
            }
            assertEquals(oldRandom.nextLong(), newRandom.nextLong())
        }
        val random = Xoshiro256PPRandom("empty".toByteArray())
        val untouched = Xoshiro256PPRandom("empty".toByteArray())
        assertEquals(-1, transformer.distantAnchorIndex(intArrayOf(4, 5, 6), 5, 2, random))
        assertEquals(untouched.nextLong(), random.nextLong())
    }

    @Test
    fun gotoBudgetKeepsLegacyBytecodeAndRngAfterExhaustion() {
        val oldMethod = gotoChain()
        val newMethod = gotoChain()
        val oldRandom = Xoshiro256PPRandom("bridges".toByteArray())
        val newRandom = Xoshiro256PPRandom("bridges".toByteArray())
        legacyBridges(oldMethod, oldRandom)
        val method = ReferenceObfuscate::class.java.getDeclaredMethod(
            "reobfuscateGotoEdges", MethodNode::class.java, String::class.java, UniformRandomProvider::class.java
        ).apply { isAccessible = true }
        assertEquals(3, method.invoke(ReferenceObfuscate(), newMethod, "regression/Bridges", newRandom))
        assertEquals(3, newMethod.tryCatchBlocks.size)
        assertEquals(oldRandom.nextLong(), newRandom.nextLong(), "Budget must not truncate RNG draws")
        val oldOwner = bridgeOwner(oldMethod)
        val newOwner = bridgeOwner(newMethod)
        assertContentEquals(classBytes(oldOwner), classBytes(newOwner))
        assertEquals(7, generatedClassLoader(listOf(newOwner)).loadClass("regression.Bridges").getMethod("run").invoke(null))
    }

    private fun gotoChain() = MethodNode(ACC_PUBLIC or ACC_STATIC, "run", "()I", null, null).apply {
        repeat(80) {
            val next = LabelNode()
            instructions.add(JumpInsnNode(GOTO, next))
            instructions.add(next)
            instructions.add(InsnNode(NOP))
        }
        val result = LabelNode()
        instructions.add(IntInsnNode(BIPUSH, 7))
        instructions.add(JumpInsnNode(GOTO, result)) // Non-empty stack: anchor, not bridge candidate.
        instructions.add(result)
        instructions.add(InsnNode(IRETURN))
        maxStack = 1
    }

    private fun bridgeOwner(method: MethodNode) = ClassNode().apply {
        version = V1_8
        access = ACC_PUBLIC
        name = "regression/Bridges"
        superName = "java/lang/Object"
        methods.add(method)
    }

    private fun legacyBridges(method: MethodNode, random: UniformRandomProvider) {
        val frames = Analyzer(BasicInterpreter()).analyze("regression/Bridges", method)
        val array = method.instructions.toArray()
        val candidates = array.withIndex().filter { (i, instruction) ->
            instruction is JumpInsnNode && instruction.opcode == GOTO && frames[i]?.stackSize == 0 &&
                    frames.drop(array.indexOf(instruction.label)).firstOrNull { it != null }?.stackSize == 0
        }.map { it.value as JumpInsnNode }.toMutableList()
        for (i in candidates.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val saved = candidates[i]
            candidates[i] = candidates[j]
            candidates[j] = saved
        }
        val candidateSet = candidates.toSet()
        val plans = candidates.mapNotNull { goto ->
            val index = array.indexOf(goto)
            val anchors = array.withIndex().filter { (i, instruction) ->
                frames[i] != null && instruction !in candidateSet && abs(i - index) >= 1 &&
                        (instruction.opcode == GOTO || instruction.opcode == ATHROW || instruction.opcode in IRETURN..RETURN)
            }.map { it.value }
            if (anchors.isEmpty()) null else goto to anchors[random.nextInt(anchors.size)]
        }
        for ((goto, anchor) in plans.take(3)) {
            val start = LabelNode()
            val end = LabelNode()
            val handler = LabelNode()
            val exception = "java/lang/RuntimeException"
            method.instructions.insertBefore(goto, InsnList().apply {
                add(start)
                add(TypeInsnNode(NEW, exception))
                add(InsnNode(DUP))
                add(MethodInsnNode(INVOKESPECIAL, exception, "<init>", "()V", false))
                add(InsnNode(ATHROW))
                add(end)
            })
            method.instructions.remove(goto)
            method.instructions.insert(anchor, InsnList().apply {
                add(handler)
                add(InsnNode(POP))
                add(JumpInsnNode(GOTO, goto.label))
            })
            method.tryCatchBlocks.add(0, TryCatchBlockNode(start, end, handler, exception))
        }
    }
}

internal fun withGeneratedTransformerClasses(
    classes: List<ClassNode>,
    configs: List<TransformerConfig>,
    check: (Grunteon) -> Unit
) {
    val input = Files.createTempDirectory("grunt-generated-regression")
    try {
        val instance = Grunteon.create(ObfConfig(
            globalConfig = GlobalConfig(input = input.toString(), output = null, dumpMappings = false),
            transformers = configs.map { TransformerEntry(config = it) }
        ))
        classes.forEach(instance.workRes::addGeneratedClass)
        instance.run()
        check(instance)
    } finally {
        Files.deleteIfExists(input)
    }
}

internal fun classBytes(node: ClassNode): ByteArray = ClassWriter(ClassWriter.COMPUTE_FRAMES).apply {
    node.accept(this)
}.toByteArray()

internal fun generatedClassLoader(classes: Collection<ClassNode>): ClassLoader {
    val bytes = classes.associate { it.name.replace('/', '.') to classBytes(it) }
    return object : ClassLoader(TransformerMetadataRegressionTest::class.java.classLoader) {
        override fun findClass(name: String): Class<*> {
            val value = bytes[name] ?: return super.findClass(name)
            return defineClass(name, value, 0, value.size)
        }
    }
}
