package net.spartanb312.grunt.event.events

import net.spartanb312.grunt.event.CancellableEvent
import net.spartanb312.grunt.event.EventBus
import net.spartanb312.grunt.event.EventPosting
import net.spartanb312.grunt.process.Transformer

class TransformerEvent(val transformer: Transformer) : CancellableEvent(), EventPosting by Companion {
    companion object : EventBus()
}