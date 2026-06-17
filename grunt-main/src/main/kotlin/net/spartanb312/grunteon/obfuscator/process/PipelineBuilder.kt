package net.spartanb312.grunteon.obfuscator.process

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.LWWSP
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.filters.ClassPredicate
import org.objectweb.asm.tree.ClassNode

class ScopeValueAccess(
    internal val globals: Array<Any?>,
    internal val reducibleGlobal: Array<Mergeable<*>?>,
    internal val reducibleLocal: Array<Mergeable<*>?>,
    internal val locals: Array<Any?>
) {
    constructor(global: ScopeValueGlobal) : this(
        global.globals,
        global.reducibleGlobal,
        arrayOfNulls(global.reducibleGlobal.size),
        arrayOfNulls(global.localCount)
    )

    fun fork(): ScopeValueAccess {
        return ScopeValueAccess(globals, reducibleGlobal, arrayOfNulls(reducibleGlobal.size), arrayOfNulls(locals.size))
    }

    fun mergeToLocal(other: ScopeValueAccess) {
        for (i in reducibleGlobal.indices) {
            val mine = reducibleLocal[i]
            val yours = other.reducibleLocal[i]
            if (mine != null) {
                if (yours != null) {
                    mine.merge(yours)
                }
            } else if (yours != null) {
                reducibleLocal[i] = yours
            }
        }
    }
}

class ScopeValueGlobal(
    internal val globals: Array<Any?>,
    internal val reducibleGlobal: Array<Mergeable<*>?>,
    internal val localCount: Int
) {
    fun mergeToGlobal(other: ScopeValueAccess) {
        for (i in reducibleGlobal.indices) {
            val mine = reducibleGlobal[i]
            val yours = other.reducibleLocal[i]
            if (yours != null) {
                mine?.merge(yours)
            }
        }
    }
}

sealed interface Instruction {
    object Barrier : Instruction
    class InitGlobal(val index: Int) : Instruction
    class InitReducible(val index: Int) : Instruction
    class Seq(val block: context(Grunteon, ScopeValueAccess) CoroutineScope.() -> Unit) : Instruction
    class Pre(val block: context(Grunteon, ScopeValueAccess) () -> Unit) : Instruction
    class Post(val block: context(Grunteon, ScopeValueAccess) () -> Unit) : Instruction
    class SeqForEach(val block: context(Grunteon, ScopeValueAccess)  (classNode: ClassNode) -> Unit) : Instruction
    class ParForEach(
        val batchSize: Int,
        val block: context(Grunteon, ScopeValueAccess)  (classNode: ClassNode) -> Unit
    ) : Instruction
}

sealed interface ScopeValueKey<T> {
    sealed interface Global<T> : ScopeValueKey<T> {
        context(_: Grunteon, _: ScopeValueAccess)
        val global: T
    }

    sealed interface Reducible<T : Mergeable<*>> : ScopeValueKey<T> {
        context(_: Grunteon, _: ScopeValueAccess)
        val global: T

        context(_: Grunteon, _: ScopeValueAccess)
        val local: T
    }

    sealed interface Local<T> : ScopeValueKey<T> {
        context(_: Grunteon, _: ScopeValueAccess)
        val local: T
    }
}

internal class GlobalScopeValueKeyImpl<T>(val init: context(Grunteon) () -> T, val index: Int) :
    ScopeValueKey.Global<T> {
    @Suppress("UNCHECKED_CAST")
    context(_: Grunteon, access: ScopeValueAccess)
    override val global: T
        get() = access.globals[index] as T
}

internal class ReducibleScopeValueKeyImpl<T : Mergeable<*>>(val init: context(Grunteon) () -> T, val index: Int) :
    ScopeValueKey.Reducible<T> {
    @Suppress("UNCHECKED_CAST")
    context(_: Grunteon, access: ScopeValueAccess)
    override val global: T
        get() = access.reducibleGlobal[index] as T

    @Suppress("UNCHECKED_CAST")
    context(_: Grunteon, access: ScopeValueAccess)
    override val local: T
        get() {
            var value = access.reducibleLocal[index] as T?
            if (value == null) {
                value = init()
                access.reducibleLocal[index] = value
            }
            return value
        }
}

internal class LocalScopeValueKeyImpl<T : Any>(val init: context(Grunteon) () -> T, val index: Int) :
    ScopeValueKey.Local<T> {
    @Suppress("UNCHECKED_CAST")
    context(_: Grunteon, access: ScopeValueAccess)
    override val local: T
        get() {
            var value = access.locals[index] as T?
            if (value == null) {
                value = init()
                access.locals[index] = value
            }
            return value
        }
}

class PipelineBuilder {
    internal val instructions = mutableListOf<Instruction>()
    internal val globalScopeValueKeys = mutableListOf<GlobalScopeValueKeyImpl<*>>()
    internal val localScopeValueKeys = mutableListOf<LocalScopeValueKeyImpl<*>>()
    internal val reducibleScopeValueKeys = mutableListOf<ReducibleScopeValueKeyImpl<*>>()
}

context(pb: PipelineBuilder)
fun barrier() {
    pb.instructions += Instruction.Barrier
}

context(pb: PipelineBuilder)
fun seq(block: context(Grunteon, ScopeValueAccess) CoroutineScope.() -> Unit) {
    pb.instructions += Instruction.Seq(block)
}

context(pb: PipelineBuilder)
fun pre(block: context(Grunteon, ScopeValueAccess) () -> Unit) {
    pb.instructions += Instruction.Pre(block)
}

context(pb: PipelineBuilder)
fun post(block: context(Grunteon, ScopeValueAccess) () -> Unit) {
    pb.instructions += Instruction.Post(block)
}

context(pb: PipelineBuilder)
fun seqForEachClasses(block: context(Grunteon, ScopeValueAccess) (classNode: ClassNode) -> Unit) {
    pb.instructions += Instruction.SeqForEach(block)
}

context(pb: PipelineBuilder)
fun parForEachClasses(
    batchSize: Int = 32,
    block: context(Grunteon, ScopeValueAccess
    ) (classNode: ClassNode) -> Unit
) {
    pb.instructions += Instruction.ParForEach(batchSize, block)
}

context(_: PipelineBuilder)
fun parForEachClassesFiltered(
    predicate: ClassPredicate,
    batchSize: Int = 32,
    action: context(Grunteon, ScopeValueAccess) (ClassNode) -> Unit
) {
    parForEachClasses(batchSize) {
        if (predicate.testImpl(it, it.name)) action(it)
    }
}

context(pb: PipelineBuilder)
fun <T> globalScopeValue(init: context(Grunteon) () -> T): ScopeValueKey.Global<T> {
    val key = GlobalScopeValueKeyImpl(init, pb.globalScopeValueKeys.size)
    pb.globalScopeValueKeys += key
    pb.instructions += Instruction.InitGlobal(key.index)
    return key
}

context(pb: PipelineBuilder)
fun <T : Any> localScopeValue(init: context(Grunteon) () -> T): ScopeValueKey.Local<T> {
    val key = LocalScopeValueKeyImpl(init, pb.localScopeValueKeys.size)
    pb.localScopeValueKeys += key
    return key
}

context(pb: PipelineBuilder)
fun <T : Mergeable<*>> reducibleScopeValue(init: context(Grunteon) () -> T): ScopeValueKey.Reducible<T> {
    val key = ReducibleScopeValueKeyImpl(init, pb.reducibleScopeValueKeys.size)
    pb.reducibleScopeValueKeys += key
    pb.instructions += Instruction.InitReducible(key.index)
    return key
}

private val lwwsp = LWWSP(Runtime.getRuntime().availableProcessors()) {
    it.isDaemon = true
}

internal class WorkerContext {
    val globalKeys = Reference2ObjectOpenHashMap<GlobalScopeValueKeyImpl<*>, Any>()
    val reducibleKeys = Reference2ObjectOpenHashMap<ReducibleScopeValueKeyImpl<*>, Mergeable<*>>()

    fun execute(instance: Grunteon, pipelineBuilder: PipelineBuilder) {
        runBlocking {
            val global = arrayOfNulls<Any>(pipelineBuilder.globalScopeValueKeys.size)
            val reducible = arrayOfNulls<Mergeable<*>>(pipelineBuilder.reducibleScopeValueKeys.size)
            val scopeValueGlobal = ScopeValueGlobal(global, reducible, pipelineBuilder.localScopeValueKeys.size)

            val pendingParallelTasks = ObjectArrayList<Instruction.ParForEach>()
            val postTasks = ObjectArrayList<Instruction.Post>()

            suspend fun flushParallelTasks() {
                if (!pendingParallelTasks.isEmpty) {
                    Logger.debug("Flushing ${pendingParallelTasks.size} parallel tasks...")
                    val minBatchSize = pendingParallelTasks.minOfOrNull { it.batchSize } ?: 32
                    val tasks = pendingParallelTasks.toTypedArray()
                    pendingParallelTasks.clear()
                    val classArray = instance.workRes.inputClassCollection.toTypedArray()
                    val access = LWWSP.iterativeTask(
                        size = classArray.size,
                        batchSize = minBatchSize,
                        newScope = { ScopeValueAccess(scopeValueGlobal) },
                        action = { start, end ->
                            for (i in start..<end) {
                                val node = classArray[i]
                                @Suppress("ReplaceManualRangeWithIndicesCalls")
                                for (j in 0..<tasks.size) {
                                    tasks[j].block.invoke(
                                        instance,
                                        this,
                                        node
                                    )
                                }
                            }
                        }
                    ).submit(lwwsp).await().getOrThrow().reduce { a, b ->
                        a.mergeToLocal(b)
                        a
                    }
                    scopeValueGlobal.mergeToGlobal(access)
                }
                postTasks.forEach {
                    val access = ScopeValueAccess(scopeValueGlobal)
                    it.block.invoke(instance, access)
                    scopeValueGlobal.mergeToGlobal(access)
                }
                postTasks.clear()
            }

            pipelineBuilder.instructions.forEach { inst ->
                when (inst) {
                    is Instruction.InitGlobal -> {
                        val key = pipelineBuilder.globalScopeValueKeys[inst.index]
                        global[inst.index] = globalKeys.computeIfAbsent(
                            key,
                            java.util.function.Function { it.init(instance) }
                        )
                    }

                    is Instruction.InitReducible -> {
                        val key = pipelineBuilder.reducibleScopeValueKeys[inst.index]
                        reducible[inst.index] = reducibleKeys.computeIfAbsent(
                            key,
                            java.util.function.Function { it.init(instance) }
                        )
                    }

                    is Instruction.Seq -> {
                        flushParallelTasks()
                        val access = ScopeValueAccess(scopeValueGlobal)
                        coroutineScope {
                            inst.block.invoke(instance, access, this)
                        }
                        scopeValueGlobal.mergeToGlobal(access)
                    }

                    is Instruction.Pre -> {
                        val access = ScopeValueAccess(scopeValueGlobal)
                        inst.block.invoke(instance, access)
                        scopeValueGlobal.mergeToGlobal(access)
                    }

                    is Instruction.SeqForEach -> {
                        flushParallelTasks()
                        val access = ScopeValueAccess(scopeValueGlobal)
                        instance.workRes.inputClassCollection.forEach { classNode ->
                            inst.block.invoke(instance, access, classNode)
                        }
                        scopeValueGlobal.mergeToGlobal(access)
                    }

                    is Instruction.ParForEach -> {
                        pendingParallelTasks.add(inst)
                    }

                    is Instruction.Post -> {
                        postTasks.add(inst)
                    }

                    is Instruction.Barrier -> {
                        flushParallelTasks()
                    }
                }
            }
            flushParallelTasks()
        }
    }
}