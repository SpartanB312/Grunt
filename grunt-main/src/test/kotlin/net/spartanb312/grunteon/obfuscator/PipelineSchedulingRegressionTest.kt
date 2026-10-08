package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class PipelineSchedulingRegressionTest {
    @Test
    fun costBatchesKeepOriginalOrderAndIsolateHeavyClasses() {
        val classes = Array(100) { index -> node("Class" + index, if (index == 40) 10_000 else 2) }
        val boundaries = classBatchBoundaries(classes, 32, 4)
        assertEquals(0, boundaries.first())
        assertEquals(classes.size, boundaries.last())
        var hasCheapBatch = false
        for (i in 0 until boundaries.lastIndex) {
            val start = boundaries[i]
            val end = boundaries[i + 1]
            assertTrue(end > start && end - start <= 32)
            if (40 in start until end) {
                assertEquals(40, start)
                assertEquals(41, end)
            } else if (end - start > 1) hasCheapBatch = true
        }
        assertTrue(hasCheapBatch)
        assertContentEquals(intArrayOf(0), classBatchBoundaries(emptyArray(), 32, 4))
        assertContentEquals(IntArray(101) { it }, classBatchBoundaries(classes, 1, 4))
        // Estimates belong to this snapshot, not to a forever-cache of mutable ClassNodes.
        classes[40].methods.single().instructions.clear()
        assertFalse(classBatchBoundaries(classes, 32, 4).contains(41))
    }

    @Test
    fun fusedPassesKeepScopePerWorkerAndMergeOnlyAfterFlush() {
        val input = Files.createTempDirectory("grunt-pipeline-scopes")
        try {
            val instance = Grunteon.create(ObfConfig(globalConfig = GlobalConfig(
                input = input.toString(), output = null, dumpMappings = false
            )))
            repeat(120) { index ->
                val node = node("Class" + index, if (index % 29 == 0) 6000 else 1)
                instance.workRes.addGeneratedClass(node)
            }
            val scopes = ConcurrentHashMap<String, Any>()
            val visits = ConcurrentHashMap<String, Int>()
            val builder = PipelineBuilder()
            context(instance, builder) {
                val local = localScopeValue { Thread.currentThread() to Any() }
                val counter = reducibleScopeValue { MergeableCounter() }
                parForEachClasses { node ->
                    assertSame(Thread.currentThread(), local.local.first)
                    assertNull(scopes.put(node.name, local.local.second))
                    assertNull(visits.put(node.name, 1))
                    node.methods.single().instructions.add(InsnNode(Opcodes.NOP))
                    counter.local.add()
                }
                pre { assertTrue(visits.isEmpty(), "pre must not flush the fused passes") }
                parForEachClasses { node ->
                    assertSame(scopes[node.name], local.local.second)
                    assertSame(Thread.currentThread(), local.local.first)
                    assertEquals(1, visits.replace(node.name, 2))
                    assertEquals(Opcodes.NOP, node.methods.single().instructions.last.opcode)
                    counter.local.add()
                }
                post { assertEquals(240, counter.global.get()) }
                barrier()
                seq { assertEquals(120, visits.size) }
                parForEachClasses { node ->
                    assertNotSame(scopes[node.name], local.local.second, "New flush needs fresh worker-local scopes")
                    assertEquals(2, visits.replace(node.name, 3))
                    counter.local.add()
                }
                post { assertEquals(360, counter.global.get()) }
            }
            WorkerContext().execute(instance, builder)
            assertEquals(setOf(3), visits.values.toSet())
        } finally {
            Files.deleteIfExists(input)
        }
    }

    private fun node(name: String, instructions: Int) = ClassNode().apply {
        this.name = name
        superName = "java/lang/Object"
        methods.add(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null).apply {
            repeat(instructions) { this.instructions.add(InsnNode(Opcodes.NOP)) }
        })
    }
}
