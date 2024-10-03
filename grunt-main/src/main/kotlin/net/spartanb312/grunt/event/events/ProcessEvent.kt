package net.spartanb312.grunt.event.events

import net.spartanb312.grunt.event.Event
import net.spartanb312.grunt.event.EventBus
import net.spartanb312.grunt.event.EventPosting

sealed class ProcessEvent : Event {
    object Before : ProcessEvent(), EventPosting by EventBus()
    object After : ProcessEvent(), EventPosting by EventBus()
}