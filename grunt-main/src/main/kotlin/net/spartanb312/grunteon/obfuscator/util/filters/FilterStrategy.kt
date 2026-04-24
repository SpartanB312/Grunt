package net.spartanb312.grunteon.obfuscator.util.filters

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.extensions.isExcluded
import org.objectweb.asm.tree.ClassNode

class FilterStrategy(
    val includeStrategy: NamePredicates,
    val excludeStrategy: NamePredicates
) {

    context(_: Grunteon)
    fun testClass(classNode: ClassNode, obfName: String): Boolean {
        return testClass(obfName) && !classNode.isExcluded
    }

    context(_: Grunteon)
    fun testClass(classNode: ClassNode): Boolean {
        return testClass(classNode.name) && !classNode.isExcluded
    }

    fun testClass(className: String): Boolean {
        val include = includeStrategy.matchedAnyBy(className)
        val exclude = excludeStrategy.matchedAnyBy(className)
        return include && !exclude
    }

}