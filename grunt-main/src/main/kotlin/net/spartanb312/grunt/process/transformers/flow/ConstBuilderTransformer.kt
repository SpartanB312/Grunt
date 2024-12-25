package net.spartanb312.grunt.process.transformers.flow

import net.spartanb312.genesis.kotlin.extensions.INT
import net.spartanb312.genesis.kotlin.extensions.LABEL
import net.spartanb312.genesis.kotlin.extensions.LONG
import net.spartanb312.genesis.kotlin.extensions.getLabelNode
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.grunt.annotation.DISABLE_SCRAMBLE
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.extensions.*
import net.spartanb312.grunt.utils.getRandomString
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import kotlin.random.Random

/**
 * Using control flow expr to build constant
 * Coming in 2.5.0
 */
object ConstBuilderTransformer : Transformer("ConstBuilder", Category.Controlflow) {

    /// Number
    private val number by setting("NumberSwitchBuilder", true)
    private val splitLong by setting("SplitLong", true)
    private val heavy by setting("HeavyEncrypt", false)
    private val flowSkip by setting("SkipControlFlow", true)
    private val replaceRate by setting("ReplacePercentage", 10) // avoid method too large
    private val maxCases by setting("MaxCases", 5)

    // String
    // TODO

    // Common
    private val exclusion by setting("Exclusion", listOf())

    private val dataClasses = mutableMapOf<ClassNode, ClassNode>()

    override fun ResourceCache.transform() {
        if (number) {
            Logger.info(" - Generating number builders...")
            if (replaceRate * maxCases > 100) Logger.warn("Excessive replacement ratio will seriously affect performance. Please make sure ReplacePercentage * MaxCases <= 100")
            val count = count {
                nonExcluded.asSequence()
                    .filter { c -> exclusion.none { c.name.startsWith(it) } }
                    .forEach { classNode ->
                        classNode.methods.asSequence()
                            .filter { !it.isAbstract && !it.isNative }
                            .forEach { methodNode: MethodNode ->
                                transformSingle(classNode, methodNode)
                            }
                    }
            }.get()
            Logger.info("    Generated $count number builders")
        }
    }

    fun ResourceCache.transformSingle(classNode: ClassNode, methodNode: MethodNode) {
        val newInsn = instructions {
            var remains = 16384 - methodNode.instructions.size()
            methodNode.instructions.forEach {
                if ((0..99).random() < replaceRate && remains > 32) {
                    if (it.opcode in 0x2..0x8) {
                        val value = it.opcode - 0x3
                        val list = insertSwitch(classNode, methodNode, value)
                        remains -= list.size()
                        +list
                    } else if (it is IntInsnNode && it.opcode != Opcodes.NEWARRAY) {
                        val value = it.operand
                        val list = insertSwitch(classNode, methodNode, value)
                        remains -= list.size()
                        +list
                    } else if (it is LdcInsnNode && it.cst is Int) {
                        val value = it.cst as Int
                        val list = insertSwitch(classNode, methodNode, value)
                        remains -= list.size()
                        +list
                    } else if (it is LdcInsnNode && it.cst is Long && splitLong) {
                        val value = it.cst as Long
                        val head = (value shr 32).toInt()
                        val tail = (value and 0x000000FFFFFFFFL).toInt()
                        // Head
                        val list1 = insertSwitch(classNode, methodNode, head)
                        remains -= list1.size()
                        +list1
                        I2L
                        LONG(0x000000FFFFFFFFL)
                        LAND
                        INT(32)
                        LSHL
                        // Tail
                        val list2 = insertSwitch(classNode, methodNode, tail)
                        remains -= list2.size()
                        +list2
                        I2L
                        LONG(0x00000000FFFFFFFFL)
                        LAND
                        // Combine
                        LOR
                        remains -= 9
                    } else +it
                } else +it
            }
        }
        methodNode.instructions = newInsn
    }


    private fun ResourceCache.insertSwitch(classNode: ClassNode, methodNode: MethodNode, value: Int): InsnList =
        with(classNode) {
            val isInitializer = methodNode.isInitializer
            instructions {
                val exitLabel = Label()
                // Prepare cases
                val seeds = buildList {
                    repeat((1..maxOf(maxCases, 1)).random()) {
                        add(Random.nextInt())
                    }
                }.sorted().toMutableList()
                val key = seeds.random()
                val pairs: MutableList<Pair<Label, InsnList>> = seeds.map {
                    if (key == it) {
                        val label = Label()
                        label to instructions {
                            LABEL(label)
                            +xor(this@insertSwitch, value, heavy)
                            if (isInitializer || flowSkip) DUMMY
                            GOTO(exitLabel)
                        }
                    } else {
                        val label = Label()
                        label to instructions {
                            LABEL(label)
                            when (Random.nextInt(0, 8)) {
                                0 -> +xor(this@insertSwitch, value - 1, heavy)
                                1 -> +xor(this@insertSwitch, value + 1, heavy)
                                2 -> +xor(this@insertSwitch, value * 2, heavy)
                                3 -> +xor(this@insertSwitch, value / 2, heavy)
                                4 -> +xor(this@insertSwitch, 0, heavy)
                                5 -> +xor(this@insertSwitch, 1, heavy)
                                6 -> +xor(this@insertSwitch, value * 5, heavy)
                                7 -> +xor(this@insertSwitch, value * 10, heavy)
                            }
                            if (isInitializer || flowSkip) DUMMY
                            GOTO(exitLabel)
                        }
                    }
                }.toMutableList()

                // Default
                val defaultIndex = seeds.indices.random()
                val defaultPair = pairs[defaultIndex]
                seeds.removeAt(defaultIndex)
                pairs.removeAt(defaultIndex)

                // Insn
                +xor(this@insertSwitch, key, true)
                +LookupSwitchInsnNode(
                    getLabelNode(defaultPair.first),
                    seeds.toIntArray(),
                    pairs.map { getLabelNode(it.first) }.toTypedArray()
                )

                // Cases
                pairs.forEach { +it.second }
                +defaultPair.second
                LABEL(exitLabel)
            }
        }

    private fun ClassNode.xor(resourceCache: ResourceCache, value: Int, heavy: Boolean) = instructions {
        val random = Random.nextInt(Short.MAX_VALUE.toInt())
        if (heavy) {
            val first = Random.nextInt(Short.MAX_VALUE.toInt()) + value
            val second = -Random.nextInt(Short.MAX_VALUE.toInt()) + value
            +putInt(resourceCache, first xor value)
            +putInt(resourceCache, second xor value + random)
            IXOR
            +putInt(resourceCache, first xor value + random)
            IXOR
            +putInt(resourceCache, second)
            IXOR
        } else {
            val encrypted = value xor random
            +putInt(resourceCache, encrypted)
            +putInt(resourceCache, random)
            IXOR
        }
    }

    private fun ClassNode.putInt(resourceCache: ResourceCache, value: Int) = instructions {
        val owner = resourceCache.getOrPutDataSwap(this@putInt)
        val field = owner.createRandomConstField(value)
        GETSTATIC(owner.name, field.name, field.desc)
    }

    private fun ClassNode.createRandomConstField(initialValue: Int): FieldNode {
        val field = FieldNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "number_${getRandomString(15)}",
            "I",
            null,
            initialValue
        )
        fields.add(field)
        return field
    }

    private fun ResourceCache.getOrPutDataSwap(classNode: ClassNode): ClassNode {
        return synchronized(dataClasses) {
            dataClasses.getOrPut(classNode) {
                ClassNode().apply {
                    visit(
                        classNode.version,
                        Opcodes.ACC_PUBLIC,
                        "${classNode.name}\$NumberData",
                        null,
                        "java/lang/Object",
                        null
                    )
                    addClass(this)
                    appendAnnotation(DISABLE_SCRAMBLE)
                }
            }
            dataClasses.entries.random().value
        }
    }

}