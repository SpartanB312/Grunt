package net.spartanb312.grunteon.obfuscator.util.filters

import org.objectweb.asm.tree.ClassNode

fun interface ClassPredicate {
    fun testImpl(classNode: ClassNode, name: String): Boolean

    fun and(next: ClassPredicate): ClassPredicate {
        return { classNode, name ->
            this.testImpl(classNode, name) && next.testImpl(classNode, name)
        }
    }

    fun andIf(cond: Boolean, next: ClassPredicate): ClassPredicate {
        return if (cond) and(next) else this
    }

    class IncludeExclude(
        private val includeStrategy: NamePredicates = emptyList(),
        private val excludeStrategy: NamePredicates = emptyList()
    ) : ClassPredicate {
        override fun testImpl(classNode: ClassNode, name: String): Boolean {
            val include = includeStrategy.matchedAnyBy(classNode.name)
            val exclude = excludeStrategy.matchedAnyBy(classNode.name)
            return include && !exclude
        }
    }

    class MappingAdaptor(
        private val mapping: Map<String, String>,
        private val delegate: ClassPredicate
    ) : ClassPredicate {
        override fun testImpl(classNode: ClassNode, name: String): Boolean {
            return delegate.testImpl(classNode, mapping.getOrDefault(classNode.name, classNode.name))
        }
    }
}

fun ClassPredicate.test(classNode: ClassNode): Boolean {
    return testImpl(classNode, classNode.name)
}

fun Sequence<ClassNode>.filter(classPredicate: ClassPredicate): Sequence<ClassNode> {
    return filter { classPredicate.test(it) }
}

fun Iterable<ClassNode>.filter(classPredicate: ClassPredicate): List<ClassNode> {
    return filter { classPredicate.test(it) }
}

fun ClassPredicate.withMapping(mapping: Map<String, String>): ClassPredicate {
    return ClassPredicate.MappingAdaptor(mapping, this)
}