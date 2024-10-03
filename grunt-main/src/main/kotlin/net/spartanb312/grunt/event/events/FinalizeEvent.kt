package net.spartanb312.grunt.event.events

import net.spartanb312.grunt.event.Event
import net.spartanb312.grunt.event.EventBus
import net.spartanb312.grunt.event.EventPosting
import net.spartanb312.grunt.process.resource.ResourceCache

sealed class FinalizeEvent(val resourceCache: ResourceCache) : Event {

    class Before(resourceCache: ResourceCache) : FinalizeEvent(resourceCache), EventPosting by Companion {
        companion object : EventBus()
    }

    class After(resourceCache: ResourceCache) : FinalizeEvent(resourceCache), EventPosting by Companion {
        companion object : EventBus()
    }

}