package net.spartanb312.grunt.event.events

import net.spartanb312.grunt.event.CancellableEvent
import net.spartanb312.grunt.event.EventBus
import net.spartanb312.grunt.event.EventPosting
import org.objectweb.asm.tree.ClassNode

class WritingClassEvent(
    val name: String,
    val classNode: ClassNode
) : CancellableEvent(), EventPosting by Companion {
    companion object : EventBus()
}
