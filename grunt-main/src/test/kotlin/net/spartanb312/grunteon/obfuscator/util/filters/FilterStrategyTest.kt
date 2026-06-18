package net.spartanb312.grunteon.obfuscator.util.filters

import org.objectweb.asm.tree.ClassNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterStrategyTest {

    @Test
    fun includeExcludeUsesMappedNameFromAdaptor() {
        val predicate = ClassPredicate.IncludeExclude(
            includeStrategy = buildClassNamePredicates(listOf("test/Foo")),
            excludeStrategy = buildClassNamePredicates(emptyList())
        ).withMapping(mapOf("a/b/C" to "test/Foo"))
        val clazz = ClassNode().apply { name = "a/b/C" }

        assertTrue(predicate.test(clazz))
    }

    @Test
    fun excludeUsesMappedNameFromAdaptor() {
        val predicate = ClassPredicate.IncludeExclude(
            includeStrategy = buildClassNamePredicates(listOf("test/**")),
            excludeStrategy = buildClassNamePredicates(listOf("test/Foo"))
        ).withMapping(mapOf("a/b/C" to "test/Foo"))
        val clazz = ClassNode().apply { name = "a/b/C" }

        assertFalse(predicate.test(clazz))
    }
}
