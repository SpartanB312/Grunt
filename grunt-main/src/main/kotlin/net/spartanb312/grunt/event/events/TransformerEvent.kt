package net.spartanb312.grunt.event.events

import net.spartanb312.grunt.event.CancellableEvent
import net.spartanb312.grunt.event.EventBus
import net.spartanb312.grunt.event.EventPosting
import net.spartanb312.grunt.process.Transformer

// Replaceable transformer
sealed class TransformerEvent(var transformer: Transformer) : CancellableEvent() {

    class Before(transformer: Transformer) : TransformerEvent(transformer), EventPosting by Companion {
        companion object : EventBus()
    }

    class After(transformer: Transformer) : TransformerEvent(transformer), EventPosting by Companion {
        companion object : EventBus()
    }

}