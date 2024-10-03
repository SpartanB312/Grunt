package net.spartanb312.grunt.event.events

import net.spartanb312.grunt.event.CancellableEvent
import net.spartanb312.grunt.event.EventBus
import net.spartanb312.grunt.event.EventPosting

sealed class ConfigEvent(val path: String) : CancellableEvent() {

    class Load(path: String) : ConfigEvent(path), EventPosting by Companion {
        companion object : EventBus()
    }

    class Save(path: String) : ConfigEvent(path), EventPosting by Companion {
        companion object : EventBus()
    }

}