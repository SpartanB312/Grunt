package net.spartanb312.grunteon.obfuscator.util.filters

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.extensions.isGlobalExcluded
import net.spartanb312.grunteon.obfuscator.util.extensions.isMixinClass
import org.objectweb.asm.tree.ClassNode

class FilterStrategy(
    val includeStrategy: NamePredicates,
    val excludeStrategy: NamePredicates
) {

    context(_: Grunteon)
    fun testClass(classNode: ClassNode, obfName: String, excludeMixins: Boolean = true): Boolean {
        return testClass(obfName) &&
                !classNode.isGlobalExcluded &&
                (!excludeMixins || !classNode.isMixinClass)
    }

    context(_: Grunteon)
    fun testClass(classNode: ClassNode, excludeMixins: Boolean = true): Boolean {
        return testClass(classNode.name) &&
                !classNode.isGlobalExcluded &&
                (!excludeMixins || !classNode.isMixinClass)
    }

    fun testClass(className: String): Boolean {
        val include = includeStrategy.matchedAnyBy(className)
        val exclude = excludeStrategy.matchedAnyBy(className)
        return include && !exclude
    }

}
