package net.spartanb312.grunt.process

import net.spartanb312.grunt.config.Configurable
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.event.IListenerOwner
import net.spartanb312.grunt.event.Listener
import net.spartanb312.grunt.event.ParallelListener
import net.spartanb312.grunt.process.resource.ResourceCache
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

abstract class Transformer(name: String, val category: Category) : Configurable(name), IListenerOwner {
    open val enabled by setting("Enabled", false).listen { _, input ->
        if (input) subscribe() else unsubscribe()
    }
    open var order = 0
    override val listeners = ArrayList<Listener>()
    override val parallelListeners = ArrayList<ParallelListener>()
    abstract fun ResourceCache.transform()
    enum class Category {
        Encryption,
        Controlflow,
        Minecraft,
        Miscellaneous,
        Optimization,
        Redirect,
        Renaming
    }
}

infix fun Transformer.order(order: Int): Transformer {
    this.order = order
    return this
}

interface MethodProcessor {
    fun transformMethod(owner: ClassNode, method: MethodNode)
}