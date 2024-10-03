package net.spartanb312.grunt.event.events

import net.spartanb312.grunt.event.CancellableEvent
import net.spartanb312.grunt.event.EventBus
import net.spartanb312.grunt.event.EventPosting

class WritingResourceEvent(
    val name: String,
    val byteArray: ByteArray
) : CancellableEvent(), EventPosting by Companion {
    companion object : EventBus()
}
