package net.spartanb312.grunt.process.transformers.flow.process

import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.PUBLIC
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.extensions.isRecord
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.genesis.kotlin.modify
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer
import net.spartanb312.grunt.process.transformers.misc.NativeCandidateTransformer
import net.spartanb312.grunt.process.transformers.rename.ClassRenameTransformer
import net.spartanb312.grunt.utils.ChainNode
import net.spartanb312.grunt.utils.extensions.isPublic
import net.spartanb312.grunt.utils.getRandomString
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import kotlin.random.Random

/**
 * AntiSimulation
 * Lightweight prevention of seeds-simulation (like Recaf and some deobfuscator)
 */
object ArithmeticExpr {

    private val cachedOwner = mutableMapOf<String, ClassNode>()
    private lateinit var res: ResourceCache

    fun refresh(resourceCache: ResourceCache) {
        cachedOwner.clear()
        res = resourceCache
    }

    fun process(indyReobf: Boolean, caller: ClassNode, action: InsnList): InsnList {
        val allowedRange: List<ClassNode>
        val owner: ClassNode
        synchronized(cachedOwner) {
            allowedRange = res.nonExcluded.filter { !it.access.isRecord && it.isPublic }
            owner = cachedOwner.getOrPut(caller.name) {
                val created = clazz(PUBLIC, "${caller.name}\$processor")
                if (indyReobf && ClassRenameTransformer.enabled) {
                    created.name = ClassRenameTransformer.nextAppendClassName(created)
                }
                res.addClass(created)
                created
            }
        }
        val dummy1 = if (ControlflowTransformer.junkParameter) "L${allowedRange.random().name};" else ""
        val dummy2 = if (ControlflowTransformer.junkParameter) "L${allowedRange.random().name};" else ""
        val dedicateMethod = method(
            PUBLIC + STATIC,
            getRandomString(15),
            "(II$dummy1$dummy2)I"
        ) {
            INSTRUCTIONS { +action }
            MAXS(2, 3)
        }
        if (ControlflowTransformer.annotationOnBuilder) {
            NativeCandidateTransformer.appendedMethods.add(dedicateMethod)
            dedicateMethod.visitAnnotation(NativeCandidateTransformer.annotation, false)
        }
        owner.modify { +dedicateMethod }
        return instructions {
            if (ControlflowTransformer.junkParameter) {
                ACONST_NULL
                ACONST_NULL
            }
            INVOKESTATIC(owner, dedicateMethod)
        }
    }

    val actions = mutableListOf(
        // param: v1, magic
        // post: v3
        { indyReobf: Boolean, val1: Int, val2: Int, val3: Int, owner: ClassNode, action: InsnList ->
            instructions {
                val (key, builder) = getKeyAndBuilder(ControlflowTransformer.asIntensity)
                LDC(val1)
                LDC(Random.nextInt())
                +process(indyReobf, owner, instructions {
                    ILOAD(0)
                    +builder
                    LDC(val2 xor key)
                    IXOR
                    +action
                    IRETURN
                })
                LDC(val3)
            }
        },
        // param: v1, v2
        // post: v3
        { indyReobf: Boolean, val1: Int, val2: Int, val3: Int, owner: ClassNode, action: InsnList ->
            instructions {
                val (key, builder) = getKeyAndBuilder(ControlflowTransformer.asIntensity)
                LDC(val1 xor key)
                LDC(val2)
                +process(indyReobf, owner, instructions {
                    ILOAD(0)
                    +builder
                    IXOR
                    ILOAD(1)
                    +action
                    IRETURN
                })
                LDC(val3)
            }
        },
    )

    private fun getKeyAndBuilder(node: Int = 1): Pair<Int, InsnList> {
        val nodes = mutableListOf<ChainNode>()
        val start = Random.nextInt()
        repeat(node) {
            while (true) {
                val lastValue = nodes.lastOrNull()?.endValue ?: start
                val new = ChainNode.nextChainNode(lastValue, 1..3)
                if (nodes.none { it.startValue == new.endValue }) {
                    nodes.add(new)
                    break
                }
            }
        }
        return nodes.last().endValue to instructions {
            LDC(start)
            nodes.forEach { node ->
                node.actions.forEach { (action, value) ->
                    LDC(value)
                    +InsnNode(action.opCode)
                }
            }
        }
    }

}