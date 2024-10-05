package net.spartanb312.genesis

import org.objectweb.asm.tree.AnnotationNode

class InvisibleAnnotationNode(node: AnnotationNode) : AnnotationNode(node.desc) {
    init {
        node.accept(this)
    }
}