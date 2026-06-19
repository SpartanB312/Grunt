package net.spartanb312.grunteon.obfuscator.util.filters

import org.objectweb.asm.tree.ClassNode

abstract class ClassPredicate {
    abstract fun testImpl(name: String): Boolean

    fun test(classNode: ClassNode): Boolean {
        return testImpl(classNode.name ?: throw Exception("Class name is null"))
    }

    fun and(next: ClassPredicate): ClassPredicate {
        return object : ClassPredicate() {
            override fun testImpl(name: String): Boolean {
                return this@ClassPredicate.testImpl(name) && next.testImpl(name)
            }
        }
    }

    fun andIf(cond: Boolean, next: ClassPredicate): ClassPredicate {
        return if (cond) and(next) else this
    }

    fun withMapping(mapping: Map<String, String>): ClassPredicate {
        return ClassPredicate.MappingAdaptor(mapping, this)
    }


    class IncludeExclude(
        private val includeStrategy: NamePredicates = emptyList(),
        private val excludeStrategy: NamePredicates = emptyList()
    ) : ClassPredicate() {
        override fun testImpl(name: String): Boolean {
            val include = includeStrategy.matchedAnyBy(name)
            val exclude = excludeStrategy.matchedAnyBy(name)
            return include && !exclude
        }
    }

    class MappingAdaptor(
        private val mapping: Map<String, String>,
        private val delegate: ClassPredicate
    ) : ClassPredicate() {
        override fun testImpl(name: String): Boolean {
            return delegate.testImpl(mapping.getOrDefault(name, name))
        }
    }
}

fun Sequence<ClassNode>.filter(classPredicate: ClassPredicate): Sequence<ClassNode> {
    return sequence {
        for (classNode in this@filter) {
            if (classPredicate.test(classNode)) yield(classNode)
        }
    }
}

fun Iterable<ClassNode>.filter(classPredicate: ClassPredicate): List<ClassNode> {
    val result = ArrayList<ClassNode>()
    for (classNode in this) {
        if (classPredicate.test(classNode)) result += classNode
    }
    return result
}