package net.spartanb312.grunteon.asm.tree

/**
 * Exception thrown in [AnnotationNode.check], [ClassNode.check], [ ][FieldNode.check] and [MethodNode.check] when these nodes (or their children, recursively)
 * contain elements that were introduced in more recent versions of the ASM API than version passed
 * to these methods.
 *
 * @author Eric Bruneton
 */
object UnsupportedClassVersionException : RuntimeException() {
    private val serialVersionUID = -3502347765891805831L
}
