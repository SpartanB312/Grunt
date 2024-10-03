package net.spartanb312.grunt.event.events

import net.spartanb312.grunt.event.CancellableEvent
import net.spartanb312.grunt.event.EventBus
import net.spartanb312.grunt.event.EventPosting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache

// Replaceable transformer
sealed class TransformerEvent(var transformer: Transformer, val resourceCache: ResourceCache) : CancellableEvent() {

    class Before(transformer: Transformer, resourceCache: ResourceCache) : TransformerEvent(transformer, resourceCache),
        EventPosting by Companion {
        companion object : EventBus()
    }

    class After(transformer: Transformer, resourceCache: ResourceCache) : TransformerEvent(transformer, resourceCache),
        EventPosting by Companion {
        companion object : EventBus()
    }

}