package net.spartanb312.grunteon.obfuscator.util.filters

import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

typealias NamePredicate = (String?) -> Boolean
typealias NamePredicates = List<NamePredicate>

// class
fun buildClassNamePredicate(rule: String): NamePredicate {
    if (rule.endsWith("**")) {
        val packageName = rule.removeSuffix("**")
        return { it?.startsWith(packageName) ?: false }
    } // package
    else return { it == rule } // name
}

fun buildClassNamePredicates(rules: List<String>): NamePredicates {
    return rules.map { buildClassNamePredicate(it) }
}

// method
fun buildMethodNamePredicate(rule: String): NamePredicate {
    if (rule.endsWith("**")) return {
        val packageName = rule.removeSuffix("**")
        it?.startsWith(packageName) ?: false
    } // package
    else if (rule.contains(".") && rule.contains("(")) return { it == rule } // method with desc
    else if (rule.contains(".")) return { it?.substringBefore("(") == rule } // method name
    else return { it?.substringBefore(".") == rule } // class name
}

fun buildMethodNamePredicates(rules: List<String>): NamePredicates {
    return rules.map { buildMethodNamePredicate(it) }
}

fun NamePredicate.matchedBy(name: String): Boolean = invoke(name)

fun NamePredicates.matchedAllBy(name: String): Boolean = all { it.invoke(name) }

fun NamePredicates.matchedAnyBy(name: String): Boolean = any { it.invoke(name) }

fun NamePredicates.matchedNoneBy(name: String): Boolean = none { it.invoke(name) }

fun ClassNode.isExcluded(annotation: String): Boolean = hasAnnotation(annotation)
fun MethodNode.isExcluded(annotation: String): Boolean = hasAnnotation(annotation)
fun FieldNode.isExcluded(annotation: String): Boolean = hasAnnotation(annotation)

