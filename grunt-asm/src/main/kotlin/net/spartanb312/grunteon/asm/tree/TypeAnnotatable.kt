package net.spartanb312.grunteon.asm.tree

interface TypeAnnotatable {
    /**
     * The runtime visible type annotations of this instruction. This field is only used for real
     * instructions (i.e. not for labels, frames, or line number nodes). This list is a list of [ ] objects. May be null.
     */
    val visibleTypeAnnotations: List<TypeAnnotationNode>

    /**
     * The runtime invisible type annotations of this instruction. This field is only used for real
     * instructions (i.e. not for labels, frames, or line number nodes). This list is a list of [ ] objects. May be null.
     */
    val invisibleTypeAnnotations: List<TypeAnnotationNode>
}